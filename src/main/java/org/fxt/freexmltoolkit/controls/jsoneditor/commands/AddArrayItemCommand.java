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

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;

/**
 * Inserts an item into a {@link JsonArray}.
 *
 * <p>An index of {@code -1} appends the item; otherwise it is inserted at the
 * given position. Out-of-range indices are rejected (returns {@code false}).
 * Undo removes the item; redo re-inserts it at the same position.</p>
 *
 * @since 2.0
 */
public class AddArrayItemCommand implements JsonCommand {

    private final JsonArray parent;
    private final JsonNode value;
    private final int requestedIndex;
    private int insertedIndex = -1;

    /**
     * Creates an add-item command.
     *
     * @param parent the array to add to
     * @param index  the insertion index, or {@code -1} to append
     * @param value  the item to insert
     * @throws IllegalArgumentException if parent or value is null
     */
    public AddArrayItemCommand(JsonArray parent, int index, JsonNode value) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent array must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        this.parent = parent;
        this.requestedIndex = index;
        this.value = value;
    }

    @Override
    public boolean execute() {
        int index = insertedIndex >= 0 ? insertedIndex : requestedIndex;
        if (index == -1) {
            index = parent.size();
        }
        if (index < 0 || index > parent.size()) {
            return false;
        }
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
        return requestedIndex == -1 ? "Append array item" : "Insert array item at " + requestedIndex;
    }
}
