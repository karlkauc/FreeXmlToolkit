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

/**
 * Represents a JSON array (ordered list of values).
 */
public class JsonArray extends JsonNode {

    public JsonArray() {
    }

    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.ARRAY;
    }

    /**
     * Gets an element at a specific index.
     */
    public JsonNode get(int index) {
        return getChild(index);
    }

    /**
     * Sets an element at a specific index.
     */
    public void set(int index, JsonNode value) {
        if (index >= 0 && index < children.size()) {
            JsonNode old = children.set(index, value);
            old.setParent(null);
            value.setParent(this);
            pcs.firePropertyChange("children", null, children);
        }
    }

    /**
     * Adds an element to the end of the array.
     */
    public void add(JsonNode value) {
        addChild(value);
    }

    /**
     * Adds an element at a specific index.
     */
    public void add(int index, JsonNode value) {
        addChild(index, value);
    }

    /**
     * Removes an element at a specific index.
     */
    public JsonNode remove(int index) {
        return removeChild(index);
    }

    /**
     * Gets the size of the array.
     */
    public int size() {
        return getChildCount();
    }

    /**
     * Checks if the array is empty.
     */
    public boolean isEmpty() {
        return children.isEmpty();
    }

    @Override
    public JsonNode deepCopy() {
        JsonArray copy = new JsonArray();
        copy.setKey(getKey());
        for (JsonNode child : children) {
            copy.addChild(child.deepCopy());
        }
        return copy;
    }

    @Override
    public String serialize(int indent, int currentIndent) {
        if (children.isEmpty()) {
            return "[]";
        }

        // Check if all children are primitives for compact display
        boolean allPrimitives = children.stream()
                .allMatch(c -> c instanceof JsonPrimitive);

        if (allPrimitives && children.size() <= 5) {
            // Compact array for small primitive arrays
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (JsonNode child : children) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(child.serialize(indent, 0));
            }
            sb.append("]");
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        String indentStr = " ".repeat(currentIndent + indent);
        String closingIndent = " ".repeat(currentIndent);

        boolean first = true;
        for (JsonNode child : children) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;

            sb.append(indentStr);
            sb.append(child.serialize(indent, currentIndent + indent));
        }

        sb.append("\n").append(closingIndent).append("]");
        return sb.toString();
    }

    @Override
    public String getDisplayLabel() {
        String key = getKey();
        if (key != null) {
            return key + " [...]";
        }
        return "[...]";
    }

    @Override
    public String getValueAsString() {
        return "[" + getChildCount() + " items]";
    }
}
