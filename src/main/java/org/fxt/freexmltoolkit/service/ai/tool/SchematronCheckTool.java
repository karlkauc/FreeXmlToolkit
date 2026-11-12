package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.Set;

import org.fxt.freexmltoolkit.controls.shell.editor.SchematronCheckRunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * {@code schematron_check}: static checks of a Schematron — XML syntax, structure, XPath syntax of contexts and
 * tests, semantics and best practice ({@link SchematronCheckRunner}). Runs nothing against a document.
 */
public final class SchematronCheckTool implements AiTool {

    private static final Set<String> ARGUMENTS = Set.of("schematron_path", "schematron_content",
            "schematron_binding");

    @Override
    public String name() {
        return "schematron_check";
    }

    @Override
    public String description() {
        return "Check a Schematron schema without running it: XML well-formedness, structure (schema, pattern, rule, "
                + "assert/report), the XPath syntax of every context and test, and common mistakes. Returns the issues "
                + "with severity, line and message; 'ok' is true when there is no error. Use it after writing or "
                + "changing a Schematron, then validate a document with it. With schematron_binding (the default "
                + "binding when the schema declares no @queryBinding) every context and test is also compiled in the "
                + "binding's XPath version: xslt = XPath 1.0, xslt2 = XPath 2.0.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder().input("schematron", "Schematron schema")
                .version("schematron_binding", "Query binding to check the expressions against (the schema's own "
                        + "@queryBinding wins).", org.fxt.freexmltoolkit.service.standards.SchematronBinding.class)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        ToolInput schematron = ToolInput.required(args, "schematron", context);
        var binding = args.version("schematron_binding",
                org.fxt.freexmltoolkit.service.standards.SchematronBinding.class);
        SchematronCheckRunner.CheckResult result = SchematronCheckRunner.checkResult(schematron.text());
        JsonObject json = new JsonObject();
        if (result.failed()) {
            String message = result.error() != null ? result.error().getMessage() : "the check could not run";
            json.addProperty("ok", false);
            json.add("issues", new JsonArray());
            json.add("error", ToolJson.error(null, null, -1, message));
            return ToolResult.failed("The check could not run: " + message, json);
        }
        JsonArray issues = new JsonArray();
        StringBuilder text = new StringBuilder();
        int errors = 0;
        for (var issue : result.issues()) {
            String severity = ValidateTool.severity(issue.severity() == null ? null : issue.severity().name());
            if (severity.equals("error")) {
                errors++;
            }
            if (issues.size() < context.limits().maxItems()) {
                JsonObject row = new JsonObject();
                row.addProperty("severity", severity);
                row.addProperty("line", Math.max(0, issue.line()));
                row.addProperty("message", context.limits().truncate(issue.message()));
                issues.add(row);
                text.append("\n").append(Math.max(0, issue.line())).append(": ").append(severity)
                        .append(": ").append(issue.message());
            }
        }
        int total = result.issues().size();
        if (binding != null) {
            for (var issue : org.fxt.freexmltoolkit.service.standards.SchematronCompatibilityCheck.check(
                    schematron.text(), binding)) {
                errors++;
                total++;
                if (issues.size() < context.limits().maxItems()) {
                    JsonObject row = new JsonObject();
                    row.addProperty("severity", "error");
                    row.addProperty("line", Math.max(0, issue.line()));
                    row.addProperty("message", context.limits().truncate(issue.message()));
                    issues.add(row);
                    text.append("\n").append(Math.max(0, issue.line())).append(": error: ").append(issue.message());
                }
            }
        }
        json.addProperty("ok", errors == 0);
        json.addProperty("total", total);
        json.add("issues", issues);
        json.add("error", com.google.gson.JsonNull.INSTANCE);
        String summary = (errors == 0 ? "ok" : errors + " error" + (errors == 1 ? "" : "s")) + ", "
                + total + " issue" + (total == 1 ? "" : "s") + " in total";
        return errors == 0 ? ToolResult.ok(summary + text, json) : ToolResult.failed(summary + text, json);
    }
}
