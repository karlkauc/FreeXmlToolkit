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
                for (SchematronService.SchematronValidationError e : service.validateXml(xml, schematron)) {
                    problems.add(new ValidationProblem("Schematron",
                            e.severity() != null ? e.severity() : "error", e.lineNumber(), e.message()));
                }
            } catch (Exception ignored) {
                // invalid/unloadable schematron — skip its stage
            }
        }
        return problems;
    }

    /**
     * Validates several XML files against the given XSD and/or Schematron and
     * returns a plain-text report (one line per file).
     */
    public static String batchReport(List<File> xmlFiles, File xsd, File schematron) {
        StringBuilder report = new StringBuilder("Batch Validation Report\n=======================\n");
        if (xsd != null) {
            report.append("XSD: ").append(xsd.getName()).append('\n');
        }
        if (schematron != null) {
            report.append("Schematron: ").append(schematron.getName()).append('\n');
        }
        report.append('\n');
        for (File file : xmlFiles) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                List<ValidationProblem> problems = run(content, xsd, schematron);
                report.append(file.getName()).append(": ")
                        .append(problems.isEmpty() ? "valid" : problems.size() + " problem(s)").append('\n');
            } catch (Exception e) {
                report.append(file.getName()).append(": ERROR ").append(e.getMessage()).append('\n');
            }
        }
        return report.toString();
    }
}
