/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service.jsonschema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaDocument.Node;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Answers "what does the schema say here" for a place in an instance, given as a path of
 * keys (String) and array indices (Integer). Follows {@code $ref} (also into other files),
 * merges {@code allOf}, offers {@code anyOf} / {@code oneOf} branches as alternatives (the
 * branches the instance clearly contradicts by {@code type}, {@code const} or {@code enum}
 * are left out), applies {@code if}/{@code then}/{@code else} when the instance decides the
 * condition and both branches otherwise. Cycle-safe (visited set + depth limit).
 */
public final class JsonSchemaResolver {

    private static final int MAX_DEPTH = 64;

    /** The keywords listed as constraints in a {@link SchemaView}, in display order. */
    private static final List<String> CONSTRAINT_KEYWORDS = List.of("minimum", "exclusiveMinimum", "maximum",
            "exclusiveMaximum", "multipleOf", "minLength", "maxLength", "pattern", "minItems", "maxItems",
            "uniqueItems", "minProperties", "maxProperties", "contentMediaType", "contentEncoding");

    private final JsonSchemaDocument document;

    /**
     * @param document the schema
     */
    public JsonSchemaResolver(JsonSchemaDocument document) {
        this.document = document;
    }

    /** @return the schema */
    public JsonSchemaDocument document() {
        return document;
    }

    /**
     * @param path     keys (String) and array indices (Integer) from the instance root
     * @param instance the instance root (decides {@code if} and prunes {@code oneOf}); may be {@code null}
     * @return what the schema says at the path ({@link SchemaView#UNKNOWN} when nothing)
     */
    public SchemaView at(List<?> path, JsonElement instance) {
        List<Node> current = List.of(document.root());
        JsonElement value = instance;
        for (Object segment : path == null ? List.of() : path) {
            List<Node> next = new ArrayList<>();
            for (Entry entry : expand(current, value)) {
                childSchemas(entry.node(), segment, next);
            }
            if (next.isEmpty()) {
                return SchemaView.UNKNOWN;
            }
            current = next;
            value = childOf(value, segment);
        }
        return view(current, value, false);
    }

    // ==================== Expansion ====================

    /** A schema that applies at a place; {@code all} = it must hold (not one of several alternatives). */
    private record Entry(Node node, boolean all) {
    }

    private List<Entry> expand(List<Node> nodes, JsonElement instance) {
        List<Entry> out = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (Node node : nodes) {
            expand(node, instance, true, visited, out, 0);
        }
        return out;
    }

    private void expand(Node node, JsonElement instance, boolean all, Set<String> visited, List<Entry> out, int depth) {
        if (node == null || depth > MAX_DEPTH || !visited.add(node.key() + all)) {
            return;
        }
        JsonObject schema = node.object();
        if (schema == null) {
            return; // boolean schema: says nothing to describe
        }
        out.add(new Entry(node, all));
        for (String refKey : new String[] {"$ref", "$dynamicRef", "$recursiveRef"}) {
            String ref = string(schema, refKey);
            if (ref != null) {
                expand(document.resolveRef(node, ref), instance, all, visited, out, depth + 1);
            }
        }
        JsonArray allOf = array(schema, "allOf");
        if (allOf != null) {
            allOf.forEach(sub -> expand(node.child(sub), instance, all, visited, out, depth + 1));
        }
        for (String alternatives : new String[] {"anyOf", "oneOf"}) {
            JsonArray branches = array(schema, alternatives);
            if (branches == null) {
                continue;
            }
            List<Node> fitting = new ArrayList<>();
            for (JsonElement branch : branches) {
                Node child = node.child(branch);
                if (!clashes(child, instance, 0)) {
                    fitting.add(child);
                }
            }
            // a single fitting branch is as good as "must hold"
            boolean single = fitting.size() == 1;
            fitting.forEach(child -> expand(child, instance, all && single, visited, out, depth + 1));
        }
        JsonElement condition = schema.get("if");
        if (condition != null) {
            Boolean holds = decide(node.child(condition), instance);
            JsonElement then = schema.get("then");
            JsonElement otherwise = schema.get("else");
            if (then != null && !Boolean.FALSE.equals(holds)) {
                expand(node.child(then), instance, all && holds != null, visited, out, depth + 1);
            }
            if (otherwise != null && !Boolean.TRUE.equals(holds)) {
                expand(node.child(otherwise), instance, all && holds != null, visited, out, depth + 1);
            }
        }
        if (instance != null && instance.isJsonObject()) {
            for (String dependent : new String[] {"dependentSchemas", "dependencies"}) {
                JsonObject deps = object(schema, dependent);
                if (deps == null) {
                    continue;
                }
                for (var dep : deps.entrySet()) {
                    if (dep.getValue().isJsonObject() && instance.getAsJsonObject().has(dep.getKey())) {
                        expand(node.child(dep.getValue()), instance, all, visited, out, depth + 1);
                    }
                }
            }
        }
    }

    /** The schemas that apply to a child (property or item) of a value described by {@code node}. */
    private void childSchemas(Node node, Object segment, List<Node> out) {
        JsonObject schema = node.object();
        if (schema == null) {
            return;
        }
        if (segment instanceof Integer index) {
            JsonArray prefix = array(schema, "prefixItems");
            JsonElement items = schema.get("items");
            JsonArray tuple = prefix != null ? prefix : items != null && items.isJsonArray() ? items.getAsJsonArray() : null;
            if (tuple != null && index < tuple.size()) {
                out.add(node.child(tuple.get(index)));
            } else if (tuple != null && prefix == null) {
                JsonElement additional = schema.get("additionalItems");
                if (additional != null && additional.isJsonObject()) {
                    out.add(node.child(additional));
                }
            } else if (items != null && items.isJsonObject()) {
                out.add(node.child(items));
            }
            return;
        }
        String key = String.valueOf(segment);
        boolean matched = false;
        JsonObject properties = object(schema, "properties");
        if (properties != null && properties.has(key)) {
            out.add(node.child(properties.get(key)));
            matched = true;
        }
        JsonObject patterns = object(schema, "patternProperties");
        if (patterns != null) {
            for (var pattern : patterns.entrySet()) {
                if (matches(pattern.getKey(), key)) {
                    out.add(node.child(pattern.getValue()));
                    matched = true;
                }
            }
        }
        if (!matched) {
            for (String fallback : new String[] {"additionalProperties", "unevaluatedProperties"}) {
                JsonElement additional = schema.get(fallback);
                if (additional != null && additional.isJsonObject()) {
                    out.add(node.child(additional));
                    break;
                }
            }
        }
    }

    // ==================== Instance checks ====================

    /** @return whether the instance clearly contradicts the schema (type, const, enum, member const/enum) */
    private boolean clashes(Node node, JsonElement instance, int depth) {
        if (instance == null || depth > 8) {
            return false;
        }
        JsonObject schema = node.object();
        if (schema == null) {
            return node.json() != null && node.json().isJsonPrimitive() && !node.json().getAsBoolean();
        }
        String ref = string(schema, "$ref");
        if (ref != null && clashes(document.resolveRef(node, ref), instance, depth + 1)) {
            return true;
        }
        Set<String> types = types(schema);
        if (!types.isEmpty() && !types.contains(typeOf(instance))
                && !(types.contains("number") && "integer".equals(typeOf(instance)))) {
            return true;
        }
        if (schema.has("const") && !schema.get("const").equals(instance)) {
            return true;
        }
        JsonArray enumValues = array(schema, "enum");
        if (enumValues != null && !enumValues.contains(instance)) {
            return true;
        }
        JsonObject properties = object(schema, "properties");
        if (properties != null && instance.isJsonObject()) {
            for (var property : properties.entrySet()) {
                JsonElement member = instance.getAsJsonObject().get(property.getKey());
                if (member != null && property.getValue().isJsonObject()) {
                    JsonObject sub = property.getValue().getAsJsonObject();
                    // only discriminators: a const / enum on a member decides the branch
                    if ((sub.has("const") || sub.has("enum")) && clashes(node.child(sub), member, depth + 1)) {
                        return true;
                    }
                }
            }
        }
        JsonArray allOf = array(schema, "allOf");
        if (allOf != null) {
            for (JsonElement sub : allOf) {
                if (clashes(node.child(sub), instance, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @return whether the instance satisfies an {@code if} schema: true / false, or null when unsure */
    private Boolean decide(Node condition, JsonElement instance) {
        if (instance == null) {
            return null;
        }
        if (clashes(condition, instance, 0)) {
            return false;
        }
        JsonObject schema = condition.object();
        if (schema == null) {
            return condition.json() != null && condition.json().isJsonPrimitive() && condition.json().getAsBoolean();
        }
        JsonArray required = array(schema, "required");
        if (required != null && instance.isJsonObject()) {
            for (JsonElement name : required) {
                if (!instance.getAsJsonObject().has(name.getAsString())) {
                    return false;
                }
            }
        }
        // only what clashes() checks decides; anything else (patterns, ranges) leaves it open
        for (String key : schema.keySet()) {
            if (!Set.of("type", "const", "enum", "required", "properties", "$comment", "title", "description")
                    .contains(key)) {
                return null;
            }
        }
        JsonObject properties = object(schema, "properties");
        if (properties != null) {
            for (var property : properties.entrySet()) {
                if (!property.getValue().isJsonObject()) {
                    return null;
                }
                for (String key : property.getValue().getAsJsonObject().keySet()) {
                    if (!Set.of("type", "const", "enum", "$comment", "title", "description").contains(key)) {
                        return null;
                    }
                }
            }
        }
        return true;
    }

    private static JsonElement childOf(JsonElement value, Object segment) {
        if (value == null) {
            return null;
        }
        if (segment instanceof Integer index && value.isJsonArray()) {
            return index >= 0 && index < value.getAsJsonArray().size() ? value.getAsJsonArray().get(index) : null;
        }
        return value.isJsonObject() ? value.getAsJsonObject().get(String.valueOf(segment)) : null;
    }

    /** @return the JSON type of a value ({@code integer} for whole numbers) */
    public static String typeOf(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonObject()) {
            return "object";
        }
        if (value.isJsonArray()) {
            return "array";
        }
        JsonPrimitive p = value.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return "boolean";
        }
        if (p.isNumber()) {
            try {
                return new java.math.BigDecimal(p.getAsString()).stripTrailingZeros().scale() <= 0 ? "integer" : "number";
            } catch (NumberFormatException e) {
                return "number";
            }
        }
        return "string";
    }

    // ==================== Merging into a view ====================

    private SchemaView view(List<Node> nodes, JsonElement instance, boolean shallow) {
        List<Entry> entries = expand(nodes, instance);
        if (entries.isEmpty()) {
            return SchemaView.UNKNOWN;
        }
        // the schemas that must hold first: their texts describe the place best
        entries.sort((a, b) -> Boolean.compare(b.all(), a.all()));
        Set<String> types = new LinkedHashSet<>();
        String title = null;
        String description = null;
        JsonElement defaultValue = null;
        List<JsonElement> examples = new ArrayList<>();
        List<JsonElement> enumValues = new ArrayList<>();
        JsonElement constValue = null;
        String format = null;
        boolean deprecated = false;
        Map<String, List<Node>> propertyNodes = new LinkedHashMap<>();
        List<String> patternProperties = new ArrayList<>();
        boolean closed = false;
        Set<String> required = new LinkedHashSet<>();
        Map<String, String> constraints = new LinkedHashMap<>();
        for (Entry entry : entries) {
            JsonObject schema = entry.node().object();
            types.addAll(types(schema));
            title = title != null ? title : string(schema, "title");
            description = description != null ? description : string(schema, "description");
            defaultValue = defaultValue != null ? defaultValue : schema.get("default");
            JsonArray ex = array(schema, "examples");
            if (ex != null) {
                ex.forEach(e -> addUnique(examples, e));
            } else if (schema.has("example")) {
                addUnique(examples, schema.get("example")); // OpenAPI style
            }
            JsonArray en = array(schema, "enum");
            if (en != null) {
                en.forEach(e -> addUnique(enumValues, e));
            }
            constValue = constValue != null ? constValue : schema.get("const");
            format = format != null ? format : string(schema, "format");
            deprecated |= schema.has("deprecated") && schema.get("deprecated").isJsonPrimitive()
                    && schema.get("deprecated").getAsJsonPrimitive().isBoolean() && schema.get("deprecated").getAsBoolean();
            for (String keyword : CONSTRAINT_KEYWORDS) {
                JsonElement value = schema.get(keyword);
                if (value != null && value.isJsonPrimitive()) {
                    constraints.putIfAbsent(keyword, value.getAsString());
                }
            }
            if (shallow) {
                continue;
            }
            JsonObject properties = object(schema, "properties");
            if (properties != null) {
                for (var property : properties.entrySet()) {
                    propertyNodes.computeIfAbsent(property.getKey(), k -> new ArrayList<>())
                            .add(entry.node().child(property.getValue()));
                }
            }
            JsonObject patterns = object(schema, "patternProperties");
            if (patterns != null) {
                patterns.keySet().forEach(p -> {
                    if (!patternProperties.contains(p)) {
                        patternProperties.add(p);
                    }
                });
            }
            if (entry.all()) {
                JsonArray req = array(schema, "required");
                if (req != null) {
                    req.forEach(r -> {
                        if (r.isJsonPrimitive()) {
                            required.add(r.getAsString());
                        }
                    });
                }
                JsonElement additional = schema.has("additionalProperties") ? schema.get("additionalProperties")
                        : schema.get("unevaluatedProperties");
                closed |= additional != null && additional.isJsonPrimitive() && !additional.getAsBoolean()
                        && patterns == null;
            }
        }
        Map<String, SchemaView.PropertyInfo> properties = new LinkedHashMap<>();
        propertyNodes.forEach((name, propertyNodesOfName) -> properties.put(name, new SchemaView.PropertyInfo(name,
                required.contains(name),
                view(propertyNodesOfName, instance != null && instance.isJsonObject()
                        ? instance.getAsJsonObject().get(name) : null, true))));
        return new SchemaView(Set.copyOf(types), title, description, defaultValue, List.copyOf(examples),
                List.copyOf(enumValues), constValue, format, deprecated, java.util.Collections.unmodifiableMap(properties),
                List.copyOf(patternProperties), closed, java.util.Collections.unmodifiableSet(required),
                java.util.Collections.unmodifiableMap(constraints), true);
    }

    private static void addUnique(List<JsonElement> list, JsonElement value) {
        if (value != null && !list.contains(value)) {
            list.add(value);
        }
    }

    // ==================== JSON helpers ====================

    private static Set<String> types(JsonObject schema) {
        JsonElement type = schema.get("type");
        Set<String> out = new LinkedHashSet<>();
        if (type == null) {
            return out;
        }
        if (type.isJsonArray()) {
            type.getAsJsonArray().forEach(t -> {
                if (t.isJsonPrimitive()) {
                    out.add(t.getAsString());
                }
            });
        } else if (type.isJsonPrimitive()) {
            out.add(type.getAsString());
        }
        return out;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static boolean matches(String regex, String key) {
        try {
            return Pattern.compile(regex).matcher(key).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
