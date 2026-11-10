package org.fxt.freexmltoolkit.service.ai.tool;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * {@code validate}: well-formedness, XSD 1.1 / DTD / RELAX NG, Schematron and plugin validators on
 * {@link ValidationRunner#runWithReport(String, File, File, String, Path, boolean)} — the same run as the
 * Validation panel and {@code fxt-cli validate}.
 */
public final class ValidateTool implements AiTool {

    private static final Set<String> ARGUMENTS =
            Set.of("xml_path", "xml_content", "xsd_path", "schematron_path", "declared_schema");

    @Override
    public String name() {
        return "validate";
    }

    @Override
    public String description() {
        return "Validate an XML document: well-formedness, then the schema (XSD 1.0/1.1, DTD or RELAX NG), then an "
                + "optional Schematron, then the checks of enabled plugins (e.g. FundsXML, ISO 20022). The schema is "
                + "xsd_path if given, else the one the document declares (xsi:schemaLocation, DOCTYPE, Schema Library) "
                + "unless declared_schema is false. Returns every problem with source, severity, line, column and "
                + "message; 'valid' is true when the run had no errors (warnings allowed). 'not_run' means the "
                + "schema or Schematron itself could not be loaded, so the document was not checked. Use it after "
                + "every change to a document.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .input("xml", "XML document")
                .string("xsd_path", "Path of the schema (XSD, DTD, .rng or .rnc) to validate against.", false)
                .string("schematron_path", "Path of a Schematron (.sch) to check after the schema.", false)
                .bool("declared_schema", "Use the schema the document declares when xsd_path is not given.", true)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        ToolInput xml = ToolInput.required(args, "xml", context);
        File xsd = existingFile(args, "xsd_path", context);
        File schematron = existingFile(args, "schematron_path", context);
        boolean declared = args.optionalBoolean("declared_schema", true);
        if (xsd == null && declared) {
            Path baseDir = xml.file().map(Path::getParent).orElse(context.files().roots().getFirst());
            xsd = ValidationRunner.declaredSchema(xml.text(), baseDir.toFile()).orElse(null);
        }
        String name = xml.file().map(f -> f.getFileName().toString()).orElse("inline.xml");
        ValidationRunner.RunResult run = ValidationRunner.runWithReport(xml.text(), xsd, schematron, name,
                xml.file().orElse(null), declared);

        List<ValidationProblem> problems = new ArrayList<>(new LinkedHashSet<>(run.problems()));
        String notRunReason = run.failed() ? describeFailure(run) : schemaNotLoaded(problems);
        int errors = 0;
        int warnings = 0;
        JsonArray rows = new JsonArray();
        StringBuilder lines = new StringBuilder();
        int max = context.limits().maxItems();
        for (ValidationProblem p : problems) {
            String severity = severity(p.severity());
            if (severity.equals("error")) {
                errors++;
            } else if (severity.equals("warning")) {
                warnings++;
            }
            if (rows.size() < max) {
                rows.add(row(p, severity, context.limits()));
                lines.append('\n').append(Math.max(0, p.line())).append(':').append(Math.max(0, p.column()))
                        .append(": ").append(severity).append(": [").append(p.source()).append("] ")
                        .append(p.message());
            }
        }
        boolean notRun = notRunReason != null;
        boolean valid = !notRun && errors == 0;

        JsonObject result = new JsonObject();
        result.addProperty("valid", valid);
        result.addProperty("not_run", notRun);
        result.addProperty("not_run_reason", notRunReason);
        result.addProperty("schema", xsd == null ? null : xsd.getAbsolutePath());
        result.addProperty("error_count", errors);
        result.addProperty("warning_count", warnings);
        result.addProperty("total", problems.size());
        result.addProperty("truncated", problems.size() > rows.size());
        result.add("problems", rows);

        String summary = notRun ? "not validated: " + notRunReason
                : (valid ? "valid" : "invalid") + " (" + errors + " error" + (errors == 1 ? "" : "s") + ", "
                + warnings + " warning" + (warnings == 1 ? "" : "s") + ")"
                + (xsd != null ? " against " + xsd.getName() : "");
        if (problems.size() > rows.size()) {
            lines.append("\n… ").append(problems.size() - rows.size()).append(" more");
        }
        String text = summary + lines;
        return valid ? ToolResult.ok(text, result) : ToolResult.failed(text, result);
    }

    private static JsonObject row(ValidationProblem p, String severity, ToolLimits limits) {
        JsonObject row = new JsonObject();
        row.addProperty("source", p.source());
        row.addProperty("severity", severity);
        row.addProperty("line", Math.max(0, p.line()));
        row.addProperty("column", Math.max(0, p.column()));
        row.addProperty("message", limits.truncate(p.message()));
        row.addProperty("rule", p.ruleId());
        row.addProperty("context", p.context());
        return row;
    }

    /** error / warning / info, mapped like {@code fxt-cli validate} does. */
    static String severity(String text) {
        if (text == null) {
            return "error";
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "warning", "warn" -> "warning";
            case "info", "information", "informational", "note" -> "info";
            default -> "error";
        };
    }

    private static String schemaNotLoaded(List<ValidationProblem> problems) {
        return problems.stream().filter(p -> p.line() <= 0 && ValidationRunner.isSchemaNotLoaded(p.message()))
                .map(ValidationProblem::message).findFirst().orElse(null);
    }

    private static String describeFailure(ValidationRunner.RunResult run) {
        Throwable error = run.error();
        if (error != null && error.getMessage() != null) {
            return error.getMessage();
        }
        return run.problems().isEmpty() ? "the validation could not run" : run.problems().getFirst().message();
    }

    private static File existingFile(ToolArgs args, String name, ToolContext context) throws ToolException {
        Optional<String> path = args.optionalString(name);
        if (path.isEmpty()) {
            return null;
        }
        Path file = context.files().resolve(path.get());
        if (!Files.isRegularFile(file)) {
            throw new ToolException("'" + name + "': file not found: " + path.get());
        }
        return file.toFile();
    }
}
