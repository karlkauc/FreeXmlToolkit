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
 * Replaces a node with another node at the same position in the same parent.
 *
 * <p>Used e.g. to turn a primitive into an object or array. The replacement
 * inherits the property key of the replaced node. Works for object
 * properties, array items and the document root. Undo swaps the original
 * node back in.</p>
 *
 * @since 2.0
 */
public class ReplaceNodeCommand implements JsonCommand {

    private final JsonNode oldNode;
    private final JsonNode replacement;

    /**
     * Creates a replace command.
     *
     * @param oldNode     the node to replace (must be attached to a parent)
     * @param replacement the node to put in its place
     * @throws IllegalArgumentException if either node is null or both are the same
     */
    public ReplaceNodeCommand(JsonNode oldNode, JsonNode replacement) {
        if (oldNode == null || replacement == null) {
            throw new IllegalArgumentException("Nodes must not be null");
        }
        if (oldNode == replacement) {
            throw new IllegalArgumentException("Replacement must differ from the replaced node");
        }
        this.oldNode = oldNode;
        this.replacement = replacement;
    }

    @Override
    public boolean execute() {
        return swap(oldNode, replacement);
    }

    @Override
    public boolean undo() {
        return swap(replacement, oldNode);
    }

    private static boolean swap(JsonNode current, JsonNode incoming) {
        JsonNode parent = current.getParent();
        if (parent == null) {
            return false;
        }
        int index = parent.indexOf(current);
        if (index < 0) {
            return false;
        }
        incoming.setKey(current.getKey());
        parent.removeChild(index);
        parent.addChild(index, incoming);
        return true;
    }

    @Override
    public String getDescription() {
        String key = oldNode.getKey();
        return "Replace " + (key != null ? "\"" + key + "\"" : oldNode.getNodeType().name().toLowerCase())
                + " with " + replacement.getNodeType().name().toLowerCase();
    }
}
