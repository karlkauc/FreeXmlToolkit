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

package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddPropertyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.ReplaceNodeCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.SetPrimitiveValueCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Entry;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Kind;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TableEditOps.Placement;
import org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver;
import org.fxt.freexmltoolkit.service.jsonschema.SchemaView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * The JSON grid's entry helpers from the bound JSON Schema: the properties the selected
 * value's object may get (Siblings), the properties the selected object may get (Children)
 * and the allowed values of the selected scalar (Values). UI-free; the panel shows the lists
 * and runs the commands.
 */
public final class JsonEntryHelperModel {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private JsonEntryHelperModel() {
    }

    /**
     * What the helpers refer to.
     *
     * @param owner         the selected object (Children), or {@code null}
     * @param siblingParent the object the selected value is a member of (Siblings), or {@code null}
     * @param reference     the selected node (new siblings go before / after it)
     * @param scalar        the selected scalar (Values), or {@code null}
     */
    public record Focus(JsonObject owner, JsonObject siblingParent, JsonNode reference, JsonPrimitive scalar) {

        /** @return the focus of a selected node */
        public static Focus of(JsonNode node) {
            if (node == null || node instanceof JsonDocument) {
                return new Focus(null, null, null, null);
            }
            return new Focus(node instanceof JsonObject o ? o : null,
                    node.getParent() instanceof JsonObject p ? p : null, node,
                    node instanceof JsonPrimitive s ? s : null);
        }
    }

    /**
     * @param node a model node
     * @return its instance path: keys of object members, indices of array items (empty = root)
     */
    public static List<Object> instancePath(JsonNode node) {
        LinkedList<Object> path = new LinkedList<>();
        for (JsonNode current = node; current != null && current.getParent() != null
                && !(current.getParent() instanceof JsonDocument); current = current.getParent()) {
            JsonNode parent = current.getParent();
            if (parent instanceof JsonArray) {
                path.addFirst(parent.indexOf(current));
            } else {
                path.addFirst(current.getKey());
            }
        }
        return path;
    }

    // ==================== Lists ====================

    /** @return the declared properties of the selected value's object (present ones unavailable) */
    public static List<Entry> siblings(JsonSchemaResolver schema, Focus focus) {
        return properties(schema, focus.siblingParent());
    }

    /** @return the declared properties of the selected object (present ones unavailable) */
    public static List<Entry> children(JsonSchemaResolver schema, Focus focus) {
        return properties(schema, focus.owner());
    }

    /** @return the allowed values of the selected scalar, as JSON text (the current one unavailable) */
    public static List<Entry> values(JsonSchemaResolver schema, Focus focus) {
        if (schema == null || focus.scalar() == null) {
            return List.of();
        }
        SchemaView view = schema.at(instancePath(focus.scalar()), null);
        String current = GSON.toJson(toGson(focus.scalar()));
        List<Entry> entries = new ArrayList<>();
        for (JsonElement value : view.allowedValues()) {
            String text = GSON.toJson(value);
            entries.add(new Entry(Kind.VALUE, text, false, !text.equals(current)));
        }
        return entries;
    }

    private static List<Entry> properties(JsonSchemaResolver schema, JsonObject object) {
        if (schema == null || object == null) {
            return List.of();
        }
        SchemaView view = schema.at(instancePath(object), null);
        List<Entry> entries = new ArrayList<>();
        for (SchemaView.PropertyInfo property : view.properties().values()) {
            entries.add(new Entry(Kind.PROPERTY, property.name(), property.required(),
                    !object.hasProperty(property.name())));
        }
        return entries;
    }

    // ==================== Commands ====================

    /**
     * @return the command adding the property next to the selected value ({@code null} when it
     *         cannot be added there)
     */
    public static JsonCommand insertSibling(JsonSchemaResolver schema, Focus focus, Entry entry, Placement placement) {
        JsonObject parent = focus.siblingParent();
        if (entry == null || !entry.available() || parent == null || parent.hasProperty(entry.name())) {
            return null;
        }
        int index = switch (placement) {
            case BEFORE -> parent.indexOf(focus.reference());
            case AFTER -> parent.indexOf(focus.reference()) + 1;
            case APPEND -> -1;
        };
        return new AddPropertyCommand(parent, entry.name(), initialValue(schema, parent, entry.name()), index);
    }

    /** @return the command adding the property to the selected object ({@code null} when not possible) */
    public static JsonCommand addChild(JsonSchemaResolver schema, Focus focus, Entry entry) {
        JsonObject owner = focus.owner();
        if (entry == null || !entry.available() || owner == null || owner.hasProperty(entry.name())) {
            return null;
        }
        return new AddPropertyCommand(owner, entry.name(), initialValue(schema, owner, entry.name()), -1);
    }

    /** @return the command setting the selected scalar to an allowed value ({@code null} when nothing to do) */
    public static JsonCommand setValue(Focus focus, Entry entry) {
        JsonPrimitive target = focus.scalar();
        if (target == null || entry == null || entry.kind() != Kind.VALUE || !entry.available()) {
            return null;
        }
        JsonNode value = JsonNodeFactory.fromGsonElement(JsonParser.parseString(entry.name()));
        if (value instanceof JsonPrimitive primitive && primitive.getNodeType() == target.getNodeType()) {
            return new SetPrimitiveValueCommand(target, primitive.getValue());
        }
        return new ReplaceNodeCommand(target, value);
    }

    /** The value a new property starts with: its const / single enum / default, else by type. */
    static JsonNode initialValue(JsonSchemaResolver schema, JsonObject object, String name) {
        List<Object> path = new ArrayList<>(instancePath(object));
        path.add(name);
        SchemaView view = schema != null ? schema.at(path, null) : SchemaView.UNKNOWN;
        JsonElement preset = view.constValue() != null ? view.constValue()
                : view.enumValues().size() == 1 ? view.enumValues().get(0)
                : view.defaultValue();
        if (preset != null) {
            return JsonNodeFactory.fromGsonElement(preset);
        }
        String type = view.types().size() == 1 ? view.types().iterator().next() : "string";
        return switch (type) {
            case "object" -> new JsonObject();
            case "array" -> new JsonArray();
            case "integer", "number" -> new JsonPrimitive(0);
            case "boolean" -> new JsonPrimitive(false);
            case "null" -> JsonPrimitive.nullValue();
            default -> new JsonPrimitive("");
        };
    }

    private static JsonElement toGson(JsonPrimitive primitive) {
        Object value = primitive.getValue();
        if (value == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        if (value instanceof Number n) {
            return new com.google.gson.JsonPrimitive(n);
        }
        if (value instanceof Boolean b) {
            return new com.google.gson.JsonPrimitive(b);
        }
        return new com.google.gson.JsonPrimitive(value.toString());
    }
}
