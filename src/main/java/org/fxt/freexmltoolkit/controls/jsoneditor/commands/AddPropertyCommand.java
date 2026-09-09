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

package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;

/**
 * Adds a new property to a {@link JsonObject}.
 *
 * <p>An index of {@code -1} appends the property; otherwise it is inserted at
 * the given position. The command is rejected (returns {@code false}) when the
 * parent already has a property with the same key or the index is out of range.
 * Undo removes the property again; a subsequent redo re-inserts it at the same
 * position.</p>
 *
 * @since 2.0
 */
public class AddPropertyCommand implements JsonCommand {

    private final JsonObject parent;
    private final String key;
    private final JsonNode value;
    private final int requestedIndex;
    private int insertedIndex = -1;

    /**
     * Creates an add-property command.
     *
     * @param parent the object to add the property to
     * @param key    the property key
     * @param value  the property value node (its key is set to {@code key})
     * @param index  the insertion index, or {@code -1} to append
     * @throws IllegalArgumentException if parent, key or value is null
     */
    public AddPropertyCommand(JsonObject parent, String key, JsonNode value, int index) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent object must not be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        this.parent = parent;
        this.key = key;
        this.value = value;
        this.requestedIndex = index;
    }

    @Override
    public boolean execute() {
        if (parent.hasProperty(key)) {
            return false;
        }
        int index = insertedIndex >= 0 ? insertedIndex : requestedIndex;
        if (index == -1) {
            index = parent.getChildCount();
        }
        if (index < 0 || index > parent.getChildCount()) {
            return false;
        }
        value.setKey(key);
        parent.addChild(index, value);
        insertedIndex = index;
        return true;
    }

    @Override
    public boolean undo() {
        return parent.removeChild(value);
    }

    @Override
    public String getDescription() {
        return "Add property \"" + key + "\"";
    }
}
