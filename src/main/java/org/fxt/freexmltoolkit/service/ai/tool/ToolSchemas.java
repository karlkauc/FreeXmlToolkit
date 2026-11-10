package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Builds the JSON Schemas (draft 2020-12) of tool arguments. */
public final class ToolSchemas {

    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();

    private ToolSchemas() {
    }

    /** An object schema without properties. */
    public static JsonObject object() {
        return builder().build();
    }

    public static ToolSchemas builder() {
        return new ToolSchemas();
    }

    public ToolSchemas string(String name, String description, boolean isRequired) {
        return property(name, "string", description, isRequired);
    }

    public ToolSchemas integer(String name, String description, Integer defaultValue, Integer min, Integer max) {
        JsonObject p = typed("integer", description);
        if (defaultValue != null) {
            p.addProperty("default", defaultValue);
        }
        if (min != null) {
            p.addProperty("minimum", min);
        }
        if (max != null) {
            p.addProperty("maximum", max);
        }
        properties.add(name, p);
        return this;
    }

    public ToolSchemas number(String name, String description) {
        return property(name, "number", description, false);
    }

    public ToolSchemas bool(String name, String description, boolean defaultValue) {
        JsonObject p = typed("boolean", description);
        p.addProperty("default", defaultValue);
        properties.add(name, p);
        return this;
    }

    public ToolSchemas enumeration(String name, String description, List<String> values, String defaultValue) {
        JsonObject p = typed("string", description);
        JsonArray allowed = new JsonArray();
        values.forEach(allowed::add);
        p.add("enum", allowed);
        if (defaultValue != null) {
            p.addProperty("default", defaultValue);
        }
        properties.add(name, p);
        return this;
    }

    /** An object whose values are strings. */
    public ToolSchemas stringMap(String name, String description) {
        JsonObject p = typed("object", description);
        JsonObject values = new JsonObject();
        values.addProperty("type", "string");
        p.add("additionalProperties", values);
        properties.add(name, p);
        return this;
    }

    public ToolSchemas stringList(String name, String description) {
        JsonObject p = typed("array", description);
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        p.add("items", items);
        properties.add(name, p);
        return this;
    }

    /**
     * The pair {@code name_path} / {@code name_content} (exactly one of them, see {@link ToolInput}).
     * The "exactly one" rule is stated in the descriptions and checked by {@link ToolInput}; it is not
     * expressed with {@code oneOf}, which several clients do not render.
     */
    public ToolSchemas input(String name, String what) {
        string(ToolInput.pathArgument(name), "Path of the " + what + " (inside the allowed folders). Give this or "
                + ToolInput.contentArgument(name) + ", not both.", false);
        string(ToolInput.contentArgument(name), "The " + what + " as text. Give this or "
                + ToolInput.pathArgument(name) + ", not both.", false);
        return this;
    }

    public JsonObject build() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties.deepCopy());
        if (!required.isEmpty()) {
            schema.add("required", required.deepCopy());
        }
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private ToolSchemas property(String name, String type, String description, boolean isRequired) {
        properties.add(name, typed(type, description));
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    private static JsonObject typed(String type, String description) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        if (description != null) {
            p.addProperty("description", description);
        }
        return p;
    }
}
