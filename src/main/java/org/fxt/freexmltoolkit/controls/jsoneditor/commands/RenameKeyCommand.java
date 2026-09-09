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
 * Renames the property key of a node inside a {@link JsonObject}.
 *
 * <p>The node keeps its position within the parent. The command is rejected
 * (returns {@code false} and changes nothing) if the node is not a property of
 * a JSON object or the parent already has a different property with the new
 * key.</p>
 *
 * @since 2.0
 */
public class RenameKeyCommand implements JsonCommand {

    private final JsonNode node;
    private final String oldKey;
    private final String newKey;

    /**
     * Creates a rename command.
     *
     * @param node   the property node to rename
     * @param newKey the new key
     * @throws IllegalArgumentException if {@code node} or {@code newKey} is null
     */
    public RenameKeyCommand(JsonNode node, String newKey) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null");
        }
        if (newKey == null) {
            throw new IllegalArgumentException("New key must not be null");
        }
        this.node = node;
        this.oldKey = node.getKey();
        this.newKey = newKey;
    }

    @Override
    public boolean execute() {
        if (!(node.getParent() instanceof JsonObject parent)) {
            return false;
        }
        JsonNode existing = parent.getProperty(newKey);
        if (existing != null && existing != node) {
            return false;
        }
        node.setKey(newKey);
        return true;
    }

    @Override
    public boolean undo() {
        node.setKey(oldKey);
        return true;
    }

    @Override
    public String getDescription() {
        return "Rename \"" + oldKey + "\" to \"" + newKey + "\"";
    }
}
