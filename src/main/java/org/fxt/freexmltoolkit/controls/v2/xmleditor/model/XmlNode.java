package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.UUID;

/**
 * Base class for all XML nodes in the V2 model.
 *
 * <p>This class provides:</p>
 * <ul>
 *   <li>UUID-based immutable identification</li>
 *   <li>PropertyChangeSupport for observable properties</li>
 *   <li>Parent-child relationship management</li>
 *   <li>Deep copy support for node duplication</li>
 *   <li>Visitor pattern support for tree traversal</li>
 * </ul>
 *
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li>ZERO UI dependencies - pure model layer</li>
 *   <li>All mutable properties fire PropertyChangeEvents</li>
 *   <li>Immutable UUID for identity tracking</li>
 *   <li>Bidirectional parent-child relationships</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public abstract class XmlNode {

    /**
     * Immutable unique identifier for this node.
     * Never changes, even when node is duplicated (deep copy gets new UUID).
     */
    private final UUID id;

    /**
     * Parent node in the XML tree.
     * Null for root nodes (XmlDocument).
     */
    private XmlNode parent;

    /**
     * PropertyChangeSupport for observable properties.
     * Enables reactive UI updates without tight coupling.
     */
    private final PropertyChangeSupport pcs;

    /**
     * Constructs a new XmlNode with a unique UUID.
     */
    protected XmlNode() {
        this.id = UUID.randomUUID();
        this.pcs = new PropertyChangeSupport(this);
        this.parent = null;
    }

    /**
     * Copy constructor for deep copy operations.
     * Creates a new node with a NEW UUID.
     *
     * @param original the original node to copy from
     */
    protected XmlNode(XmlNode original) {
        this.id = UUID.randomUUID(); // New UUID for copy
        this.pcs = new PropertyChangeSupport(this);
        this.parent = null; // Parent set by caller
    }

    // ==================== Property Change Support ====================

    /**
     * Adds a PropertyChangeListener to this node.
     * The listener will be notified of all property changes.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener from this node.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Adds a PropertyChangeListener for a specific property.
     *
     * @param propertyName the name of the property
     * @param listener     the listener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a PropertyChangeListener for a specific property.
     *
     * @param propertyName the name of the property
     * @param listener     the listener to remove
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    /**
     * Fires a property change event.
     * Subclasses should call this for all mutable properties.
     *
     * @param propertyName the name of the property
     * @param oldValue     the old value
     * @param newValue     the new value
     */
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }

    // ==================== Identity and Relationships ====================

    /**
     * Returns the unique identifier for this node.
     * This ID never changes and is unique across the entire application.
     *
     * @return the immutable UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the parent node.
     *
     * @return the parent node, or null if this is a root node
     */
    public XmlNode getParent() {
        return parent;
    }

    /**
     * Sets the parent node.
     * Fires a "parent" property change event.
     *
     * <p><strong>Internal use only.</strong> This should only be called by
     * XmlElement when adding/removing children. External code should use
     * XmlElement.addChild() or removeChild() instead.</p>
     *
     * @param parent the new parent node
     */
    public void setParent(XmlNode parent) {
        XmlNode oldParent = this.parent;
        this.parent = parent;
        firePropertyChange("parent", oldParent, parent);
    }

    /**
     * Returns the root node of the tree.
     * Traverses up the parent chain until reaching the root.
     *
     * @return the root node (typically XmlDocument)
     */
    public XmlNode getRoot() {
        XmlNode node = this;
        while (node.getParent() != null) {
            node = node.getParent();
        }
        return node;
    }

    /**
     * Checks if this node is the root of the tree.
     *
     * @return true if this node has no parent
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Checks if this node is a descendant of the given node.
     *
     * @param ancestor the potential ancestor node
     * @return true if this node is a descendant of the given node
     */
    public boolean isDescendantOf(XmlNode ancestor) {
        XmlNode node = this.parent;
        while (node != null) {
            if (node == ancestor) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    // ==================== Abstract Methods ====================

    /**
     * Returns the type of this node.
     *
     * @return the node type
     */
    public abstract XmlNodeType getNodeType();

    /**
     * Creates a deep copy of this node.
     *
     * <p>The copy has:</p>
     * <ul>
     *   <li>A NEW UUID (different from original)</li>
     *   <li>All properties copied</li>
     *   <li>All children recursively copied</li>
     *   <li>No parent (must be set by caller)</li>
     * </ul>
     *
     * @param suffix optional suffix to append to names (for "Copy of X")
     * @return a deep copy of this node
     */
    public abstract XmlNode deepCopy(String suffix);

    /**
     * Serializes this node to XML text.
     *
     * @param indent the current indentation level (0 = no indent)
     * @return the XML representation of this node
     */
    public abstract String serialize(int indent);

    /**
     * Accepts a visitor for tree traversal.
     * Implements the Visitor pattern for extensible operations.
     *
     * @param visitor the visitor to accept
     */
    public abstract void accept(XmlNodeVisitor visitor);

    // ==================== Utility Methods ====================

    /**
     * Returns a string representation of this node.
     * Shows the node type and ID for debugging.
     *
     * @return a string representation
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + id + "]";
    }

    /**
     * Checks equality based on UUID.
     * Two nodes are equal if they have the same UUID.
     *
     * @param obj the object to compare
     * @return true if the UUIDs match
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        XmlNode other = (XmlNode) obj;
        return id.equals(other.id);
    }

    /**
     * Returns the hash code based on UUID.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
