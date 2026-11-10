package org.fxt.freexmltoolkit.service.ai.tool;

import org.fxt.freexmltoolkit.service.ai.context.SchemaCache;

import java.util.Optional;
import java.util.Set;

import org.fxt.freexmltoolkit.service.ai.context.SchemaSlice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** {@code schema_outline}: the global components of an XSD and the content tree of one global element. */
public final class SchemaOutlineTool implements AiTool {

    static final int MAX_DEPTH = 6;
    private static final Set<String> ARGUMENTS = Set.of("xsd_path", "element", "depth");

    @Override
    public String name() {
        return "schema_outline";
    }

    @Override
    public String description() {
        return "Outline an XSD: target namespace, global elements, named types and imports. With 'element' (a global "
                + "element name) also its content tree to 'depth' levels: name, type, min, max (\"unbounded\"), "
                + "children; 'choice': true marks alternatives, 'more': true a cut subtree, 'recursive': true a type "
                + "that repeats. Start here to learn a schema, then use schema_describe for one place in detail.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .string("xsd_path", "Path of the XSD.", true)
                .string("element", "A global element whose content tree is returned.", false)
                .integer("depth", "Levels of the tree below the element.", 2, 1, MAX_DEPTH)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        var file = SchemaTools.schemaFile(args, context);
        int depth = args.optionalInt("depth", 2, 1, MAX_DEPTH);
        Optional<String> element = args.optionalString("element");
        SchemaCache.Loaded loaded = SchemaCache.SHARED.load(file);
        if (!loaded.ok()) {
            return SchemaTools.notParsed(loaded);
        }
        JsonObject result = SchemaSlice.outline(loaded.schema());
        StringBuilder text = new StringBuilder();
        text.append("target namespace: ").append(nullText(result.get("target_namespace")))
                .append("\nglobal elements: ").append(join(result.getAsJsonArray("global_elements")))
                .append("\nnamed types: ").append(join(result.getAsJsonArray("named_types")))
                .append("\nimports: ").append(join(result.getAsJsonArray("imports")));
        if (element.isPresent()) {
            Optional<JsonObject> tree = SchemaSlice.elementTree(loaded.schema(), element.get(), depth);
            if (tree.isEmpty()) {
                result.add("tree", com.google.gson.JsonNull.INSTANCE);
                return ToolResult.failed("No global element '" + element.get() + "'. Global elements: "
                        + String.join(", ", SchemaSlice.globalElementNames(loaded.schema())), result);
            }
            result.add("tree", tree.get());
            text.append("\n\n");
            appendTree(text, tree.get(), 0);
        }
        return ToolResult.ok(context.limits().truncate(text.toString()), result);
    }

    private static void appendTree(StringBuilder text, JsonObject node, int indent) {
        text.append("  ".repeat(indent)).append(node.get("name").getAsString());
        if (node.has("type") && !node.get("type").isJsonNull()) {
            text.append(": ").append(node.get("type").getAsString());
        }
        String max = node.get("max").getAsString();
        text.append(" [").append(node.get("min").getAsString()).append("..")
                .append(max.equals("unbounded") ? "*" : max).append(']');
        if (node.has("choice")) {
            text.append(" (choice)");
        }
        if (node.has("recursive")) {
            text.append(" (recursive)");
        }
        if (node.has("more")) {
            text.append(" …");
        }
        text.append('\n');
        if (node.has("children")) {
            for (JsonElement child : node.getAsJsonArray("children")) {
                appendTree(text, child.getAsJsonObject(), indent + 1);
            }
        }
    }

    private static String join(JsonArray array) {
        StringBuilder out = new StringBuilder();
        for (JsonElement e : array) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                out.append(o.has("name") ? o.get("name").getAsString()
                        : nullText(o.get("namespace")) + " (" + nullText(o.get("location")) + ")");
            } else {
                out.append(e.getAsString());
            }
        }
        return out.isEmpty() ? "none" : out.toString();
    }

    private static String nullText(JsonElement e) {
        return e == null || e.isJsonNull() ? "none" : e.getAsString();
    }
}
