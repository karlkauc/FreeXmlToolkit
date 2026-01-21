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
 *
 * <p>This class models a JSON array, which is an ordered collection of JSON values.
 * Arrays can contain any combination of JSON nodes including objects, other arrays,
 * and primitive values. The class provides methods for accessing, modifying,
 * and iterating over array elements.</p>
 *
 * <p>Example JSON array: {@code [1, "hello", true, null, {"key": "value"}]}</p>
 */
public class JsonArray extends JsonNode {

    /**
     * Creates a new empty JSON array.
     *
     * <p>The array is initialized with no elements and can be populated
     * using the {@link #add(JsonNode)} or {@link #add(int, JsonNode)} methods.</p>
     */
    public JsonArray() {
    }

    /**
     * Returns the node type for this JSON array.
     *
     * @return {@link JsonNodeType#ARRAY} indicating this is an array node
     */
    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.ARRAY;
    }

    /**
     * Gets an element at a specific index in the array.
     *
     * @param index the zero-based index of the element to retrieve
     * @return the JSON node at the specified index, or null if the index is out of bounds
     */
    public JsonNode get(int index) {
        return getChild(index);
    }

    /**
     * Sets an element at a specific index in the array, replacing any existing element.
     *
     * <p>If the index is valid (within the current array bounds), the existing element
     * at that position is replaced with the new value. The old element's parent reference
     * is cleared, and the new element's parent is set to this array.</p>
     *
     * @param index the zero-based index at which to set the element
     * @param value the JSON node to place at the specified index
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
     *
     * <p>The element is appended after all existing elements in the array.</p>
     *
     * @param value the JSON node to add to the array
     */
    public void add(JsonNode value) {
        addChild(value);
    }

    /**
     * Adds an element at a specific index in the array.
     *
     * <p>Elements at or after the specified index are shifted to make room
     * for the new element.</p>
     *
     * @param index the zero-based index at which to insert the element
     * @param value the JSON node to insert into the array
     */
    public void add(int index, JsonNode value) {
        addChild(index, value);
    }

    /**
     * Removes and returns the element at a specific index in the array.
     *
     * <p>Elements after the removed element are shifted to fill the gap.</p>
     *
     * @param index the zero-based index of the element to remove
     * @return the removed JSON node, or null if the index is out of bounds
     */
    public JsonNode remove(int index) {
        return removeChild(index);
    }

    /**
     * Gets the number of elements in the array.
     *
     * @return the count of elements in this array
     */
    public int size() {
        return getChildCount();
    }

    /**
     * Checks if the array contains no elements.
     *
     * @return true if the array has no elements, false otherwise
     */
    public boolean isEmpty() {
        return children.isEmpty();
    }

    /**
     * Creates a deep copy of this JSON array and all its contained elements.
     *
     * <p>The copy includes recursively copied versions of all child nodes,
     * ensuring that modifications to the copy do not affect the original.</p>
     *
     * @return a new JsonArray instance that is a deep copy of this array
     */
    @Override
    public JsonNode deepCopy() {
        JsonArray copy = new JsonArray();
        copy.setKey(getKey());
        for (JsonNode child : children) {
            copy.addChild(child.deepCopy());
        }
        return copy;
    }

    /**
     * Serializes this JSON array to a formatted JSON string.
     *
     * <p>The output format depends on the array contents:
     * <ul>
     *   <li>Empty arrays are serialized as {@code []}</li>
     *   <li>Small arrays (5 or fewer elements) containing only primitives use compact format</li>
     *   <li>Other arrays use multi-line format with proper indentation</li>
     * </ul>
     *
     * @param indent the number of spaces to use for each indentation level
     * @param currentIndent the current indentation level (number of spaces from left margin)
     * @return the JSON string representation of this array
     */
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

    /**
     * Returns a display label for this array suitable for use in tree views.
     *
     * <p>The label includes the array's key (if set) followed by {@code [...]}
     * to indicate that this is an array node.</p>
     *
     * @return a human-readable label for this array, e.g., "items [...]" or "[...]"
     */
    @Override
    public String getDisplayLabel() {
        String key = getKey();
        if (key != null) {
            return key + " [...]";
        }
        return "[...]";
    }

    /**
     * Returns a string representation of the array's value for display purposes.
     *
     * <p>Shows the element count rather than the actual contents.</p>
     *
     * @return a string indicating the number of items, e.g., "[5 items]"
     */
    @Override
    public String getValueAsString() {
        return "[" + getChildCount() + " items]";
    }
}
