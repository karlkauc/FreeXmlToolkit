package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.Set;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;

import com.google.gson.JsonObject;

/** {@code sample_xml}: a sample instance generated from an XSD ({@link SampleXmlRunner}, as the Schema menu does). */
public final class SampleXmlTool implements AiTool {

    private static final Set<String> ARGUMENTS =
            Set.of("xsd_path", "mandatory_only", "max_occurrences", "realistic", "seed");

    @Override
    public String name() {
        return "sample_xml";
    }

    @Override
    public String description() {
        return "Generate a sample XML instance from an XSD, for its first global element: all elements or only the "
                + "mandatory ones, repeating elements up to max_occurrences, with realistic values (names, dates, "
                + "amounts matching the types and facets) or placeholders. The same seed gives the same sample. Use "
                + "it as test input for a stylesheet or a query.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .string("xsd_path", "Path of the XSD.", true)
                .bool("mandatory_only", "Only elements and attributes that are required.", false)
                .integer("max_occurrences", "Repetitions of repeating elements.", 2, 1, 10)
                .bool("realistic", "Realistic values instead of placeholders.", true)
                .integer("seed", "Seed for a reproducible sample.", null, null, null)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        var file = SchemaTools.schemaFile(args, context);
        boolean mandatoryOnly = args.optionalBoolean("mandatory_only", false);
        int maxOccurrences = args.optionalInt("max_occurrences", 2, 1, 10);
        boolean realistic = args.optionalBoolean("realistic", true);
        Long seed = args.has("seed") ? (long) args.optionalInt("seed", 0) : null;
        SampleXmlRunner.SampleRun run = SampleXmlRunner.generateResult(file.toFile(), mandatoryOnly, maxOccurrences,
                realistic, seed);
        JsonObject result = new JsonObject();
        boolean ok = run.failure() == null && !run.text().startsWith("ERROR:");
        result.addProperty("ok", ok);
        if (ok) {
            ToolJson.putText(result, "xml", run.text(), context.limits());
            result.add("error", com.google.gson.JsonNull.INSTANCE);
            return ToolResult.ok(context.limits().truncate(run.text()), result);
        }
        String message = run.text().startsWith("ERROR:") ? run.text().substring(6).strip() : run.text();
        result.addProperty("xml", "");
        result.addProperty("truncated", false);
        result.add("error", ToolJson.error(null, "input", -1, message));
        return ToolResult.failed("No sample: " + message, result);
    }
}
