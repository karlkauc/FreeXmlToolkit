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
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;

/**
 * Inserts a deep copy of a node directly after the original.
 *
 * <p>Inside a {@link JsonObject} the copy receives a unique key derived from
 * the original: {@code key_copy}, then {@code key_copy2}, {@code key_copy3},
 * and so on. The root value of a document cannot be duplicated. The same copy
 * instance is reused on redo so views can keep referring to it.</p>
 *
 * @since 2.0
 */
public class DuplicateNodeCommand implements JsonCommand {

    private final JsonNode node;
    private JsonNode duplicate;

    /**
     * Creates a duplicate command.
     *
     * @param node the node to duplicate
     * @throws IllegalArgumentException if {@code node} is null
     */
    public DuplicateNodeCommand(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null");
        }
        this.node = node;
    }

    /**
     * Returns the inserted copy.
     *
     * @return the duplicate node, or null before the first execution
     */
    public JsonNode getDuplicate() {
        return duplicate;
    }

    @Override
    public boolean execute() {
        JsonNode parent = node.getParent();
        if (parent == null || parent instanceof JsonDocument) {
            return false;
        }
        int index = parent.indexOf(node);
        if (index < 0) {
            return false;
        }
        if (duplicate == null) {
            duplicate = node.deepCopy();
            if (parent instanceof JsonObject object) {
                duplicate.setKey(uniqueCopyKey(object, node.getKey()));
            } else {
                duplicate.setKey(null);
            }
        }
        parent.addChild(index + 1, duplicate);
        return true;
    }

    @Override
    public boolean undo() {
        if (duplicate == null) {
            return false;
        }
        JsonNode parent = duplicate.getParent();
        return parent != null && parent.removeChild(duplicate);
    }

    @Override
    public String getDescription() {
        String key = node.getKey();
        return "Duplicate " + (key != null ? "\"" + key + "\"" : node.getNodeType().name().toLowerCase());
    }

    private static String uniqueCopyKey(JsonObject parent, String baseKey) {
        String base = (baseKey == null ? "" : baseKey) + "_copy";
        if (!parent.hasProperty(base)) {
            return base;
        }
        int n = 2;
        while (parent.hasProperty(base + n)) {
            n++;
        }
        return base + n;
    }
}
