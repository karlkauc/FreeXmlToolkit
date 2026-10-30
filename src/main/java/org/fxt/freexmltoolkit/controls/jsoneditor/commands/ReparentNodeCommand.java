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

package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;

/**
 * Moves a node into another container (or to another place in its own), e.g. by drag and
 * drop in the grid. Inside an object the node gets the given key, inside an array it has none.
 *
 * <p>{@code index} is the index the node occupies after the move ({@code -1} = append).
 * Moving the root, into the node itself or one of its descendants, or onto a key the target
 * object already uses is rejected (returns {@code false}). Undo restores parent, index and key.</p>
 */
public class ReparentNodeCommand implements JsonCommand {

    private final JsonNode node;
    private final JsonNode newParent;
    private final int index;
    private final String newKey;
    private JsonNode oldParent;
    private int oldIndex = -1;
    private String oldKey;

    /**
     * @param node      the node to move
     * @param newParent the target object or array
     * @param index     the index after the move, {@code -1} = append
     * @param newKey    the key inside an object target (ignored for an array target)
     */
    public ReparentNodeCommand(JsonNode node, JsonNode newParent, int index, String newKey) {
        if (node == null || newParent == null) {
            throw new IllegalArgumentException("Node and target must not be null");
        }
        if (!(newParent instanceof JsonObject) && !(newParent instanceof JsonArray)) {
            throw new IllegalArgumentException("Target must be an object or an array");
        }
        this.node = node;
        this.newParent = newParent;
        this.index = index;
        this.newKey = newKey;
    }

    @Override
    public boolean execute() {
        JsonNode parent = node.getParent();
        if (parent == null || !(parent instanceof JsonObject || parent instanceof JsonArray)
                || isSelfOrAncestor(node, newParent)) {
            return false;
        }
        String key = newParent instanceof JsonObject ? newKey : null;
        if (newParent instanceof JsonObject object) {
            JsonNode existing = key != null ? object.getProperty(key) : null;
            if (key == null || (existing != null && existing != node)) {
                return false;
            }
        }
        oldParent = parent;
        oldIndex = parent.indexOf(node);
        oldKey = node.getKey();
        parent.removeChild(node);
        node.setKey(key);
        int at = index < 0 ? newParent.getChildCount() : Math.min(index, newParent.getChildCount());
        newParent.addChild(at, node);
        return true;
    }

    @Override
    public boolean undo() {
        if (oldParent == null || !newParent.removeChild(node)) {
            return false;
        }
        node.setKey(oldKey);
        oldParent.addChild(Math.min(oldIndex, oldParent.getChildCount()), node);
        return true;
    }

    /** @return whether {@code candidate} is {@code node} itself or lies below it */
    public static boolean isSelfOrAncestor(JsonNode node, JsonNode candidate) {
        for (JsonNode n = candidate; n != null; n = n.getParent()) {
            if (n == node) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getDescription() {
        String key = node.getKey();
        return "Move " + (key != null ? "\"" + key + "\"" : "item");
    }
}
