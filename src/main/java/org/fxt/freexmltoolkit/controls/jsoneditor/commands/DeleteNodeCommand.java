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

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;

/**
 * Removes a node from its parent object or array.
 *
 * <p>The document's root value cannot be deleted (a document always needs a
 * root); such commands are rejected with {@code false}. Undo re-inserts the
 * node at its original index.</p>
 *
 * @since 2.0
 */
public class DeleteNodeCommand implements JsonCommand {

    private final JsonNode node;
    private JsonNode parent;
    private int index = -1;

    /**
     * Creates a delete command.
     *
     * @param node the node to delete
     * @throws IllegalArgumentException if {@code node} is null
     */
    public DeleteNodeCommand(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null");
        }
        this.node = node;
    }

    @Override
    public boolean execute() {
        JsonNode currentParent = node.getParent();
        if (currentParent == null || currentParent instanceof JsonDocument) {
            return false;
        }
        int currentIndex = currentParent.indexOf(node);
        if (currentIndex < 0) {
            return false;
        }
        parent = currentParent;
        index = currentIndex;
        return parent.removeChild(node);
    }

    @Override
    public boolean undo() {
        if (parent == null || index < 0) {
            return false;
        }
        parent.addChild(Math.min(index, parent.getChildCount()), node);
        return true;
    }

    @Override
    public String getDescription() {
        String key = node.getKey();
        return key != null ? "Delete \"" + key + "\"" : "Delete " + node.getNodeType().name().toLowerCase();
    }
}
