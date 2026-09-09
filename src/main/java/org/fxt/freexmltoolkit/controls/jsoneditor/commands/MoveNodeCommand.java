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

/**
 * Moves a node to a different position within the same parent.
 *
 * <p>{@code newIndex} is the index the node occupies after the move. Moving a
 * detached node, moving to an out-of-range index, or moving to the current
 * index is rejected (returns {@code false}). Undo restores the old index.</p>
 *
 * @since 2.0
 */
public class MoveNodeCommand implements JsonCommand {

    private final JsonNode node;
    private final int newIndex;
    private int oldIndex = -1;

    /**
     * Creates a move command.
     *
     * @param node     the node to move
     * @param newIndex the target index within the node's parent
     * @throws IllegalArgumentException if {@code node} is null
     */
    public MoveNodeCommand(JsonNode node, int newIndex) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null");
        }
        this.node = node;
        this.newIndex = newIndex;
    }

    @Override
    public boolean execute() {
        JsonNode parent = node.getParent();
        if (parent == null) {
            return false;
        }
        int currentIndex = parent.indexOf(node);
        if (currentIndex < 0 || newIndex < 0 || newIndex >= parent.getChildCount() || newIndex == currentIndex) {
            return false;
        }
        oldIndex = currentIndex;
        return relocate(parent, newIndex);
    }

    @Override
    public boolean undo() {
        JsonNode parent = node.getParent();
        if (parent == null || oldIndex < 0) {
            return false;
        }
        return relocate(parent, oldIndex);
    }

    /**
     * Removes the node from its parent and re-inserts it so that it ends up at
     * {@code targetIndex}.
     */
    private boolean relocate(JsonNode parent, int targetIndex) {
        if (!parent.removeChild(node)) {
            return false;
        }
        parent.addChild(Math.min(targetIndex, parent.getChildCount()), node);
        return true;
    }

    @Override
    public String getDescription() {
        String key = node.getKey();
        return "Move " + (key != null ? "\"" + key + "\"" : "item") + " to position " + newIndex;
    }
}
