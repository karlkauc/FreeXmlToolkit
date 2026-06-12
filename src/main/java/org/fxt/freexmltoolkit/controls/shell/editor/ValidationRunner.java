package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.SchematronService;
import org.fxt.freexmltoolkit.service.SchematronServiceImpl;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

/**
 * UI-free validation orchestration for the Validation activity: validates XML
 * against an XSD (Xerces via {@link XmlService}) and/or a Schematron file
 * ({@link SchematronService}), returning a unified {@link ValidationProblem} list.
 * Both stages degrade gracefully on errors so one failing stage never hides the
 * other.
 */
public final class ValidationRunner {

    private ValidationRunner() {
    }

    /**
     * @param xml        the XML content
     * @param xsd        the XSD to validate against, or {@code null} (well-formedness only)
     * @param schematron the Schematron file, or {@code null} to skip Schematron
     * @return all problems found (empty if valid)
     */
    /**
     * Validates JSON for well-formedness and (optionally) against a JSON Schema,
     * reusing {@link org.fxt.freexmltoolkit.service.JsonService}.
     *
     * @param json       the JSON content
     * @param jsonSchema the JSON Schema file, or {@code null} (well-formedness only)
     * @return all problems found (empty if valid)
     */
    public static List<ValidationProblem> validateJson(String json, File jsonSchema) {
        List<ValidationProblem> problems = new ArrayList<>();
        var service = new org.fxt.freexmltoolkit.service.JsonService();
        String wellFormed = service.validateJson(json);
        if (wellFormed != null) {
            problems.add(new ValidationProblem("JSON", "error", -1, wellFormed));
            return problems;
        }
        if (jsonSchema != null) {
            for (String error : service.validateAgainstSchema(json, jsonSchema)) {
                problems.add(new ValidationProblem("JSON Schema", "error", -1, error));
            }
        }
        return problems;
    }

    public static List<ValidationProblem> run(String xml, File xsd, File schematron) {
        List<ValidationProblem> problems = new ArrayList<>();
        // Without an XSD this is a well-formedness (structural) check; with one it also
        // validates against the schema. Label problems by their actual source.
        String source = xsd != null ? "XSD" : "Well-formed";
        try {
            for (SAXParseException e : ServiceRegistry.get(XmlService.class).validateText(xml, xsd)) {
                problems.add(new ValidationProblem(source, "error", e.getLineNumber(), e.getMessage()));
            }
        } catch (Throwable ignored) {
            // validation unavailable (e.g. no service registry) — skip
        }
        if (schematron != null) {
            try {
                SchematronService service = new SchematronServiceImpl();
                List<SchematronService.SchematronValidationError> errors = service.validateXml(xml, schematron);
                // SVRL carries the failing node's XPath (context) but no line number;
                // resolve it against the document so problems navigate to their line.
                SchematronLineResolver resolver = errors.stream().anyMatch(e -> e.lineNumber() <= 0)
                        ? new SchematronLineResolver(xml) : null;
                for (SchematronService.SchematronValidationError e : errors) {
                    int line = e.lineNumber() > 0 ? e.lineNumber()
                            : (resolver != null ? resolver.lineOf(e.context()) : 0);
                    problems.add(new ValidationProblem("Schematron",
                            e.severity() != null ? e.severity() : "error", line, e.message()));
                }
            } catch (Exception ignored) {
                // invalid/unloadable schematron — skip its stage
            }
        }
        return problems;
    }

    /**
     * The per-file outcome of a batch validation run (one entry per input file).
     * Backs the Validation panel's RESULTS list.
     *
     * @param file      the validated file
     * @param problems  the problems found (empty when valid or unreadable)
     * @param readError a message when the file could not be read, else {@code null}
     */
    public record FileValidationResult(File file, List<ValidationProblem> problems, String readError) {

        /** @return {@code true} when the file is unreadable or has at least one non-warning problem */
        public boolean failed() {
            return readError != null || errorCount() > 0;
        }

        /** @return the number of error-severity problems (an unreadable file counts as one) */
        public long errorCount() {
            if (readError != null) {
                return 1;
            }
            return problems.stream().filter(p -> !isWarning(p)).count();
        }

        /** @return the number of warning-severity problems */
        public long warningCount() {
            return problems.stream().filter(FileValidationResult::isWarning).count();
        }

        private static boolean isWarning(ValidationProblem p) {
            return "warning".equalsIgnoreCase(p.severity());
        }
    }

    /**
     * Validates several XML files against the given XSD and/or Schematron,
     * returning one structured {@link FileValidationResult} per file (in input order).
     */
    public static List<FileValidationResult> batch(List<File> xmlFiles, File xsd, File schematron) {
        List<FileValidationResult> results = new ArrayList<>();
        for (File file : xmlFiles) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                results.add(new FileValidationResult(file, run(content, xsd, schematron), null));
            } catch (Exception e) {
                results.add(new FileValidationResult(file, List.of(), String.valueOf(e.getMessage())));
            }
        }
        return results;
    }

    /**
     * Validates several XML files against the given XSD and/or Schematron and
     * returns a plain-text report (one line per file). Delegates to {@link #batch}.
     */
    public static String batchReport(List<File> xmlFiles, File xsd, File schematron) {
        return report(batch(xmlFiles, xsd, schematron), xsd, schematron);
    }

    /** Renders already-computed batch results as the plain-text report. */
    public static String report(List<FileValidationResult> results, File xsd, File schematron) {
        StringBuilder report = new StringBuilder("Batch Validation Report\n=======================\n");
        if (xsd != null) {
            report.append("XSD: ").append(xsd.getName()).append('\n');
        }
        if (schematron != null) {
            report.append("Schematron: ").append(schematron.getName()).append('\n');
        }
        report.append('\n');
        for (FileValidationResult result : results) {
            report.append(result.file().getName()).append(": ");
            if (result.readError() != null) {
                report.append("ERROR ").append(result.readError());
            } else if (result.problems().isEmpty()) {
                report.append("valid");
            } else {
                report.append(result.problems().size()).append(" problem(s)");
            }
            report.append('\n');
        }
        return report.toString();
    }
}
