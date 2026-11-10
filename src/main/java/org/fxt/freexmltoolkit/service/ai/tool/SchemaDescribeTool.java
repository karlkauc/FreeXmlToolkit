package org.fxt.freexmltoolkit.service.ai.tool;

import org.fxt.freexmltoolkit.service.ai.context.SchemaCache;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator;
import org.fxt.freexmltoolkit.service.ai.context.SchemaSlice;

import com.google.gson.JsonObject;

/** {@code schema_describe}: what an XSD declares for one place in an instance document. */
public final class SchemaDescribeTool implements AiTool {

    private static final Set<String> ARGUMENTS = Set.of("xsd_path", "instance_path", "attribute");

    @Override
    public String name() {
        return "schema_describe";
    }

    @Override
    public String description() {
        return "Describe what an XSD declares for one place in an instance document, given as a path of element names "
                + "from the root (/Order/Item/Price; prefixes and [n] are ignored), or an attribute of that element: "
                + "type, built-in base type, min/max occurrences, nillable, default/fixed, facets, enumeration values, "
                + "documentation, allowed child elements (choice alternatives marked) and attributes, and the file "
                + "it is declared in when that is an included or imported schema. If the path does not exist, "
                + "'found' is false and the nearest path that does is returned with its children.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .string("xsd_path", "Path of the XSD.", true)
                .string("instance_path", "Element path from the document root, e.g. /Order/Item/Price.", true)
                .string("attribute", "Name of an attribute of that element to describe instead.", false)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        var file = SchemaTools.schemaFile(args, context);
        String instancePath = args.requiredString("instance_path");
        String attribute = args.optionalString("attribute").map(a -> a.startsWith("@") ? a.substring(1) : a)
                .orElse(null);
        List<String> path = XsdInstanceLocator.instancePath(instancePath);
        if (path.isEmpty()) {
            throw new ToolException("'instance_path' must name at least the root element, e.g. /Order");
        }
        SchemaCache.Loaded loaded = SchemaCache.SHARED.load(file);
        if (!loaded.ok()) {
            return SchemaTools.notParsed(loaded);
        }
        Optional<SchemaSlice> slice = SchemaSlice.describe(loaded.schema(), path, attribute);
        if (slice.isPresent()) {
            return ToolResult.ok(context.limits().truncate(slice.get().toText()), slice.get().toJson());
        }
        List<String> known = attribute != null && SchemaSlice.nearestKnownPath(loaded.schema(), path).size() == path.size()
                ? path : SchemaSlice.nearestKnownPath(loaded.schema(), path);
        JsonObject result = new JsonObject();
        result.addProperty("found", false);
        StringBuilder text = new StringBuilder("Nothing is declared at ").append(instancePath)
                .append(attribute != null ? " @" + attribute : "").append('.');
        if (known.isEmpty()) {
            result.add("nearest_known_path", com.google.gson.JsonNull.INSTANCE);
            text.append(" Global elements: ").append(String.join(", ", SchemaSlice.globalElementNames(loaded.schema())));
        } else {
            String knownPath = "/" + String.join("/", known);
            result.addProperty("nearest_known_path", knownPath);
            List<String> children = SchemaSlice.childNames(loaded.schema(), known);
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            children.forEach(array::add);
            result.add("children_of_nearest", array);
            text.append(" The nearest declared path is ").append(knownPath).append(", whose children are: ")
                    .append(children.isEmpty() ? "none" : String.join(", ", children));
        }
        return ToolResult.ok(text.toString(), result);
    }
}
