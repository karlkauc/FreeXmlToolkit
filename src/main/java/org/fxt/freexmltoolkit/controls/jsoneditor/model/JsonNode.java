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

    /**
     * Property change support for notifying listeners of property changes.
     */
    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * Unique identifier for this node, generated as a UUID string.
     */
    private final String id = UUID.randomUUID().toString();

    /**
     * The parent node of this node in the JSON tree hierarchy.
     */
    private JsonNode parent;

    /**
     * The property key when this node is a child of a JSON object.
     */
    private String key;

    /**
     * The child nodes of this node.
     */
    protected final List<JsonNode> children = new ArrayList<>();

    /**
     * Protected constructor for subclasses of JsonNode.
     * This abstract class cannot be instantiated directly.
     */
    protected JsonNode() {
        // Abstract class constructor
    }

    // Position in the source text (for navigation)
    private int startPosition = -1;
    private int endPosition = -1;

    /**
     * Gets the unique identifier for this node.
     * Each node is assigned a UUID upon creation that remains constant throughout its lifecycle.
     *
     * @return the unique identifier string for this node
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the type of this JSON node.
     * The node type indicates whether this is an object, array, string, number, boolean, or null value.
     *
     * @return the node type enumeration value
     */
    public abstract JsonNodeType getNodeType();

    /**
     * Gets the parent node of this node in the JSON tree hierarchy.
     * Root nodes will return null.
     *
     * @return the parent node, or null if this is a root node
     */
    public JsonNode getParent() {
        return parent;
    }

    /**
     * Sets the parent node of this node.
     * This method fires a property change event for the "parent" property.
     *
     * @param parent the new parent node, or null to make this a root node
     */
    public void setParent(JsonNode parent) {
        JsonNode oldParent = this.parent;
        this.parent = parent;
        pcs.firePropertyChange("parent", oldParent, parent);
    }

    /**
     * Gets the property key for this node when it is a child of a JSON object.
     * For array elements, this may be null.
     *
     * @return the property key string, or null if not set
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the property key for this node.
     * This method fires a property change event for the "key" property.
     *
     * @param key the property key string to set
     */
    public void setKey(String key) {
        String oldKey = this.key;
        this.key = key;
        pcs.firePropertyChange("key", oldKey, key);
    }

    /**
     * Gets the start position of this node in the source JSON text.
     * This is used for navigation and source mapping.
     *
     * @return the start position as a character offset, or -1 if not set
     */
    public int getStartPosition() {
        return startPosition;
    }

    /**
     * Sets the start position of this node in the source JSON text.
     *
     * @param startPosition the start position as a character offset
     */
    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    /**
     * Gets the end position of this node in the source JSON text.
     * This is used for navigation and source mapping.
     *
     * @return the end position as a character offset, or -1 if not set
     */
    public int getEndPosition() {
        return endPosition;
    }

    /**
     * Sets the end position of this node in the source JSON text.
     *
     * @param endPosition the end position as a character offset
     */
    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }

    /**
     * Gets an unmodifiable view of this node's children.
     * Modifications to the returned list will throw an UnsupportedOperationException.
     *
     * @return an unmodifiable list of child nodes
     */
    public List<JsonNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Adds a child node to the end of this node's children list.
     * The child's parent will be set to this node automatically.
     * This method fires a property change event for the "children" property.
     *
     * @param child the child node to add; if null, no action is taken
     */
    public void addChild(JsonNode child) {
        if (child != null) {
            children.add(child);
            child.setParent(this);
            pcs.firePropertyChange("children", null, children);
        }
    }

    /**
     * Adds a child node at a specific index in this node's children list.
     * The child's parent will be set to this node automatically.
     * This method fires a property change event for the "children" property.
     *
     * @param index the index at which to insert the child (0-based)
     * @param child the child node to add; if null or index is invalid, no action is taken
     */
    public void addChild(int index, JsonNode child) {
        if (child != null && index >= 0 && index <= children.size()) {
            children.add(index, child);
            child.setParent(this);
            pcs.firePropertyChange("children", null, children);
        }
    }

    /**
     * Removes a child node from this node's children list.
     * The removed child's parent will be set to null.
     * This method fires a property change event for the "children" property if removal is successful.
     *
     * @param child the child node to remove
     * @return true if the child was found and removed, false otherwise
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
     * Removes a child node at a specific index from this node's children list.
     * The removed child's parent will be set to null.
     * This method fires a property change event for the "children" property if removal is successful.
     *
     * @param index the index of the child to remove (0-based)
     * @return the removed child node, or null if the index is invalid
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
     * Returns the number of children this node has.
     *
     * @return the count of child nodes
     */
    public int getChildCount() {
        return children.size();
    }

    /**
     * Checks if this node has any children.
     *
     * @return true if this node has one or more children, false otherwise
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Gets a child node at a specific index.
     *
     * @param index the index of the child to retrieve (0-based)
     * @return the child node at the specified index, or null if the index is invalid
     */
    public JsonNode getChild(int index) {
        if (index >= 0 && index < children.size()) {
            return children.get(index);
        }
        return null;
    }

    /**
     * Returns the index of a child node within this node's children list.
     *
     * @param child the child node to find
     * @return the index of the child (0-based), or -1 if not found
     */
    public int indexOf(JsonNode child) {
        return children.indexOf(child);
    }

    /**
     * Gets the depth of this node in the JSON tree hierarchy.
     * The root node has depth 0.
     *
     * @return the depth level of this node
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
     * The root node returns "$". Object properties use dot notation for simple keys
     * and bracket notation for keys with special characters. Array elements use bracket notation with indices.
     *
     * @return the JSONPath-like path string to this node
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
     * Creates a deep copy of this node and all its descendants.
     * The copy will have new unique identifiers and no parent set.
     *
     * @return a new JsonNode that is a deep copy of this node
     */
    public abstract JsonNode deepCopy();

    /**
     * Serializes this node and its descendants to a formatted JSON string.
     *
     * @param indent        the number of spaces to use for each indentation level
     * @param currentIndent the current indentation level (number of spaces)
     * @return the serialized JSON string representation
     */
    public abstract String serialize(int indent, int currentIndent);

    /**
     * Serializes this node to a JSON string with default indentation.
     * Uses 2 spaces for indentation starting at level 0.
     *
     * @return the serialized JSON string representation
     */
    public String serialize() {
        return serialize(2, 0);
    }

    /**
     * Gets a display label for this node suitable for UI presentation.
     * The format varies by node type.
     *
     * @return a human-readable label string for this node
     */
    public abstract String getDisplayLabel();

    /**
     * Gets the value of this node as a string for display purposes.
     * The format varies by node type.
     *
     * @return the string representation of this node's value
     */
    public abstract String getValueAsString();

    // ==================== PropertyChangeSupport ====================

    /**
     * Adds a listener to receive property change events for all properties.
     *
     * @param listener the PropertyChangeListener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener that was registered for all properties.
     *
     * @param listener the PropertyChangeListener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Adds a listener to receive property change events for a specific property.
     *
     * @param propertyName the name of the property to listen for changes on
     * @param listener     the PropertyChangeListener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a property change listener that was registered for a specific property.
     *
     * @param propertyName the name of the property the listener was registered for
     * @param listener     the PropertyChangeListener to remove
     */
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
