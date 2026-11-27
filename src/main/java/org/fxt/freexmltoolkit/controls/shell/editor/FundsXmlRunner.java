package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlValidator;

/**
 * UI-free wrappers over the existing FundsXML services for the shell FundsXML activity.
 * Mirrors the logic the legacy MainController menu handlers used.
 */
public final class FundsXmlRunner {

    private FundsXmlRunner() {
    }

    public static boolean isEnabled() {
        return FundsXmlActionRunner.isEnabled();
    }

    public static List<String> installedVersions() {
        try {
            return FundsXmlExtensionService.getInstance().getInstalledVersions();
        } catch (Throwable t) {
            return List.of();
        }
    }

    public static String activeVersion() {
        try {
            return FundsXmlCache.getInstance().loadMetadata().getActiveSchemaVersion();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean setActiveVersion(String version) {
        try {
            return FundsXmlExtensionService.getInstance().setActiveVersion(version);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Validates {@code xml} against the active FundsXML schema; returns a human-readable summary. */
    public static String validateSummary(String xml) {
        return validate(xml).summary();
    }

    /**
     * The result of a validation against the active FundsXML schema.
     *
     * @param summary  the human-readable summary for the panel's status line
     * @param problems the schema problems for the PROBLEMS panel (empty when valid or when it could not run)
     * @param ran      whether the document was checked against a schema (valid or invalid)
     */
    public record Validation(String summary, List<ValidationProblem> problems, boolean ran) {
        public Validation {
            problems = List.copyOf(problems);
        }

        /** @return whether the document was checked and no problem was found */
        public boolean valid() {
            return ran && problems.isEmpty();
        }
    }

    /**
     * Validates {@code xml} against the active FundsXML schema - also when the document is bound to no schema
     * (or to another one): the problems belong in the PROBLEMS panel, the summary in the panel's status.
     */
    public static Validation validate(String xml) {
        try {
            FundsXmlValidator validator = new FundsXmlValidator(
                    FundsXmlCache.getInstance(), ServiceRegistry.get(XmlService.class));
            var outcome = validator.validate(xml);
            return of(outcome.status(), outcome.schemaVersion(), outcome.errors(), outcome.errorMessage());
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportError(t, "fundsxml.validate");
            return new Validation(I18n.t("FundsXmlRunner.validation-error", t.getMessage()), List.of(), false);
        }
    }

    /** @return the validation result of a {@link FundsXmlValidator} outcome */
    static Validation of(FundsXmlValidator.Status status, String schemaVersion,
                         List<org.xml.sax.SAXParseException> errors, String errorMessage) {
        String version = schemaVersion == null ? "" : schemaVersion;
        return switch (status) {
            case NO_ACTIVE_SCHEMA -> new Validation(I18n.t("FundsXmlRunner.no-active-fundsxml-schema"), List.of(), false);
            case NO_XML_CONTENT -> new Validation(I18n.t("FundsXmlRunner.no-xml-document-open"), List.of(), false);
            case VALID -> new Validation(I18n.t("FundsXmlRunner.valid-against-fundsxml-schema", version), List.of(), true);
            case INVALID -> new Validation(I18n.t("FundsXmlRunner.invalid-issues-against-schema", errors.size(), version),
                    errors.stream().map(e -> new ValidationProblem("XSD", "error", e.getLineNumber(), e.getMessage())
                            .withColumn(e.getColumnNumber())).toList(), true);
            case ERROR -> new Validation(I18n.t("FundsXmlRunner.validation-error", errorMessage == null
                    ? I18n.t("FundsXmlRunner.unexpected-error") : errorMessage), List.of(), false);
        };
    }

    public static Path examplesDir() {
        try {
            return FundsXmlCache.getInstance().getExamplesDir();
        } catch (Throwable t) {
            return null;
        }
    }

    public static Path schemaDir() {
        try {
            return FundsXmlCache.getInstance().getSchemaDir();
        } catch (Throwable t) {
            return null;
        }
    }

    public static Path schematronDir() {
        try {
            return FundsXmlCache.getInstance().getSchematronDir();
        } catch (Throwable t) {
            return null;
        }
    }

    public static Path activeSchemaFile() {
        try {
            return FundsXmlCache.getInstance().getActiveSchemaFile();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Generates schema documentation for the active schema into the cache's docs dir; returns the dir. */
    public static Path generateDocumentation() throws Exception {
        return generateDocumentation(org.fxt.freexmltoolkit.service.progress.Progress.NONE);
    }

    /** Like {@link #generateDocumentation()}, reporting each pipeline step as the progress message. */
    public static Path generateDocumentation(org.fxt.freexmltoolkit.service.progress.Progress progress) throws Exception {
        Path activeSchema = FundsXmlCache.getInstance().getActiveSchemaFile();
        if (activeSchema == null) {
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.schemaDocGenerated("html", 0,
                    org.fxt.freexmltoolkit.service.telemetry.QueryFailure.invalidInput("no_active_schema"));
            throw new IllegalStateException("No active FundsXML schema");
        }
        String version = FundsXmlCache.getInstance().loadMetadata().getActiveSchemaVersion();
        Path outputDir = FundsXmlCache.getInstance().getBaseDir()
                .resolve("docs").resolve(version == null ? "current" : version);
        java.nio.file.Files.createDirectories(outputDir);
        long t0 = System.nanoTime();
        try {
            var docService = new org.fxt.freexmltoolkit.service.XsdDocumentationService();
            docService.setXsdFilePath(activeSchema.toString());
            docService.setProgressListener(update -> {
                if (update.status() == org.fxt.freexmltoolkit.service.TaskProgressListener.ProgressUpdate.Status.STARTED) {
                    progress.message(org.fxt.freexmltoolkit.i18n.I18n.t("DocumentationRunner.step", update.taskName()));
                }
            });
            docService.generateXsdDocumentation(outputDir.toFile());
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.schemaDocGenerated("html", t0,
                    org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(t));
            throw t;
        }
        org.fxt.freexmltoolkit.service.telemetry.UsageEvents.schemaDocGenerated("html", t0, null);
        return outputDir;
    }
}
