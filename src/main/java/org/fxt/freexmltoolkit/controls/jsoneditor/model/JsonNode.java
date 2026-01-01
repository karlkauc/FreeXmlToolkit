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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base class for all JSON model nodes.
 * Uses PropertyChangeSupport for reactive UI updates.
 */
public abstract class JsonNode {

    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String id = UUID.randomUUID().toString();

    private JsonNode parent;
    private String key; // The property key (for object properties)
    protected final List<JsonNode> children = new ArrayList<>();

    /**
     * Gets the unique identifier for this node.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the node type.
     */
    public abstract JsonNodeType getNodeType();

    /**
     * Gets the parent node.
     */
    public JsonNode getParent() {
        return parent;
    }

    /**
     * Sets the parent node.
     */
    public void setParent(JsonNode parent) {
        JsonNode oldParent = this.parent;
        this.parent = parent;
        pcs.firePropertyChange("parent", oldParent, parent);
    }

    /**
     * Gets the property key (for object properties).
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the property key.
     */
    public void setKey(String key) {
        String oldKey = this.key;
        this.key = key;
        pcs.firePropertyChange("key", oldKey, key);
    }

    /**
     * Gets an unmodifiable view of children.
     */
    public List<JsonNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Adds a child node.
     */
    public void addChild(JsonNode child) {
        if (child != null) {
            children.add(child);
            child.setParent(this);
            pcs.firePropertyChange("children", null, children);
        }
    }

    /**
     * Adds a child at a specific index.
     */
    public void addChild(int index, JsonNode child) {
        if (child != null && index >= 0 && index <= children.size()) {
            children.add(index, child);
            child.setParent(this);
            pcs.firePropertyChange("children", null, children);
        }
    }

    /**
     * Removes a child node.
     */
    public boolean removeChild(JsonNode child) {
        boolean removed = children.remove(child);
        if (removed) {
            child.setParent(null);
            pcs.firePropertyChange("children", null, children);
        }
        return removed;
    }

    /**
     * Removes a child at a specific index.
     */
    public JsonNode removeChild(int index) {
        if (index >= 0 && index < children.size()) {
            JsonNode removed = children.remove(index);
            removed.setParent(null);
            pcs.firePropertyChange("children", null, children);
            return removed;
        }
        return null;
    }

    /**
     * Returns the number of children.
     */
    public int getChildCount() {
        return children.size();
    }

    /**
     * Checks if this node has children.
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Gets a child at a specific index.
     */
    public JsonNode getChild(int index) {
        if (index >= 0 && index < children.size()) {
            return children.get(index);
        }
        return null;
    }

    /**
     * Returns the index of a child.
     */
    public int indexOf(JsonNode child) {
        return children.indexOf(child);
    }

    /**
     * Gets the depth of this node in the tree.
     */
    public int getDepth() {
        int depth = 0;
        JsonNode p = parent;
        while (p != null) {
            depth++;
            p = p.getParent();
        }
        return depth;
    }

    /**
     * Gets the path to this node as a JSONPath-like string.
     */
    public String getPath() {
        if (parent == null) {
            return "$";
        }

        String parentPath = parent.getPath();

        if (parent.getNodeType() == JsonNodeType.ARRAY) {
            int index = parent.indexOf(this);
            return parentPath + "[" + index + "]";
        } else if (key != null) {
            // Use bracket notation if key has special chars
            if (key.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                return parentPath + "." + key;
            } else {
                return parentPath + "['" + key.replace("'", "\\'") + "']";
            }
        }

        return parentPath;
    }

    /**
     * Creates a deep copy of this node.
     */
    public abstract JsonNode deepCopy();

    /**
     * Serializes this node to a JSON string.
     */
    public abstract String serialize(int indent, int currentIndent);

    /**
     * Serializes with default indentation.
     */
    public String serialize() {
        return serialize(2, 0);
    }

    /**
     * Gets a display label for this node.
     */
    public abstract String getDisplayLabel();

    /**
     * Gets the value as a string (for display).
     */
    public abstract String getValueAsString();

    // ==================== PropertyChangeSupport ====================

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id='" + id + '\'' +
                ", key='" + key + '\'' +
                ", type=" + getNodeType() +
                ", children=" + children.size() +
                '}';
    }
}
