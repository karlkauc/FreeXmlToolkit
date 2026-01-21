/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.controls.jsoneditor.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a JSON object (key-value pairs).
 */
public class JsonObject extends JsonNode {

    /**
     * Creates a new empty JSON object.
     */
    public JsonObject() {
    }

    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.OBJECT;
    }

    /**
     * Gets a property value by key.
     *
     * @param key the property key to look up
     * @return the property value, or null if not found
     */
    public JsonNode getProperty(String key) {
        for (JsonNode child : children) {
            if (key.equals(child.getKey())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Sets a property, replacing any existing property with the same key.
     *
     * @param key   the property key
     * @param value the property value
     */
    public void setProperty(String key, JsonNode value) {
        // Remove existing property with same key
        children.removeIf(child -> key.equals(child.getKey()));

        // Add new property
        value.setKey(key);
        addChild(value);
    }

    /**
     * Removes a property by key.
     *
     * @param key the property key to remove
     * @return true if the property was found and removed, false otherwise
     */
    public boolean removeProperty(String key) {
        JsonNode toRemove = getProperty(key);
        if (toRemove != null) {
            return removeChild(toRemove);
        }
        return false;
    }

    /**
     * Checks if a property exists with the specified key.
     *
     * @param key the property key to check
     * @return true if a property with this key exists, false otherwise
     */
    public boolean hasProperty(String key) {
        return getProperty(key) != null;
    }

    /**
     * Gets all property keys in this object.
     *
     * @return an iterable of property key strings
     */
    public Iterable<String> getPropertyKeys() {
        return children.stream()
                .map(JsonNode::getKey)
                .filter(k -> k != null)
                .toList();
    }

    /**
     * Gets all properties as a map, preserving insertion order.
     *
     * @return a LinkedHashMap of property keys to their values
     */
    public Map<String, JsonNode> getProperties() {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode child : children) {
            if (child.getKey() != null) {
                result.put(child.getKey(), child);
            }
        }
        return result;
    }

    @Override
    public JsonNode deepCopy() {
        JsonObject copy = new JsonObject();
        copy.setKey(getKey());
        for (JsonNode child : children) {
            JsonNode childCopy = child.deepCopy();
            childCopy.setKey(child.getKey());
            copy.addChild(childCopy);
        }
        return copy;
    }

    @Override
    public String serialize(int indent, int currentIndent) {
        if (children.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        String indentStr = " ".repeat(currentIndent + indent);
        String closingIndent = " ".repeat(currentIndent);

        boolean first = true;
        for (JsonNode child : children) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;

            sb.append(indentStr);
            sb.append('"').append(escapeString(child.getKey())).append("\": ");
            sb.append(child.serialize(indent, currentIndent + indent));
        }

        sb.append("\n").append(closingIndent).append("}");
        return sb.toString();
    }

    private String escapeString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public String getDisplayLabel() {
        String key = getKey();
        if (key != null) {
            return key + " {...}";
        }
        return "{...}";
    }

    @Override
    public String getValueAsString() {
        return "{" + getChildCount() + " properties}";
    }
}
