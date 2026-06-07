package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.List;

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
        try {
            FundsXmlValidator validator = new FundsXmlValidator(
                    FundsXmlCache.getInstance(), ServiceRegistry.get(XmlService.class));
            var outcome = validator.validate(xml);
            return switch (outcome.status()) {
                case NO_ACTIVE_SCHEMA -> "No active FundsXML schema — download content and pick a version.";
                case NO_XML_CONTENT -> "No XML document open.";
                case VALID -> "Valid against FundsXML schema "
                        + (outcome.schemaVersion() == null ? "" : outcome.schemaVersion()) + ".";
                case INVALID -> "Invalid: " + outcome.errors().size() + " issue(s) against schema "
                        + (outcome.schemaVersion() == null ? "" : outcome.schemaVersion()) + ".";
                case ERROR -> "Validation error: "
                        + (outcome.errorMessage() == null ? "unexpected error" : outcome.errorMessage());
            };
        } catch (Throwable t) {
            return "Validation error: " + t.getMessage();
        }
    }

    public static Path examplesDir() {
        return FundsXmlCache.getInstance().getExamplesDir();
    }

    public static Path schemaDir() {
        return FundsXmlCache.getInstance().getSchemaDir();
    }

    public static Path schematronDir() {
        return FundsXmlCache.getInstance().getSchematronDir();
    }

    public static Path activeSchemaFile() {
        return FundsXmlCache.getInstance().getActiveSchemaFile();
    }

    /** Generates schema documentation for the active schema into the cache's docs dir; returns the dir. */
    public static Path generateDocumentation() throws Exception {
        Path activeSchema = FundsXmlCache.getInstance().getActiveSchemaFile();
        if (activeSchema == null) {
            throw new IllegalStateException("No active FundsXML schema");
        }
        String version = FundsXmlCache.getInstance().loadMetadata().getActiveSchemaVersion();
        Path outputDir = FundsXmlCache.getInstance().getBaseDir()
                .resolve("docs").resolve(version == null ? "current" : version);
        java.nio.file.Files.createDirectories(outputDir);
        var docService = new org.fxt.freexmltoolkit.service.XsdDocumentationService();
        docService.setXsdFilePath(activeSchema.toString());
        docService.generateXsdDocumentation(outputDir.toFile());
        return outputDir;
    }
}
