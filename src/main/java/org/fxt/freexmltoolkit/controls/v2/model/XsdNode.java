package org.fxt.freexmltoolkit.controls.v2.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Base class for all XSD model nodes.
 * Provides common functionality like property change support, parent-child relationships,
 * and unique identification.
 * <p>
 * This is the foundation of the Model layer that separates the logical XSD structure
 * from its visual representation (VisualNode).
 *
 * @since 2.0
 */
public abstract class XsdNode {

    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private final String id;
    private String name;
    private XsdNode parent;
    private final List<XsdNode> children = new ArrayList<>();

    // Cardinality
    private int minOccurs = 1;
    private int maxOccurs = 1;
    public static final int UNBOUNDED = -1;

    // Documentation
    private String documentation;
    private String appinfo;

    /**
     * Creates a new XSD node with a unique ID.
     *
     * @param name the node name
     */
    protected XsdNode(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    /**
     * Gets the unique ID of this node.
     *
     * @return the unique ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the node name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the node name and fires a property change event.
     *
     * @param name the new name
     */
    public void setName(String name) {
        String oldName = this.name;
        this.name = name;
        pcs.firePropertyChange("name", oldName, name);
    }

    /**
     * Gets the parent node.
     *
     * @return the parent node, or null if this is a root node
     */
    public XsdNode getParent() {
        return parent;
    }

    /**
     * Sets the parent node (internal use only).
     *
     * @param parent the parent node
     */
    protected void setParent(XsdNode parent) {
        this.parent = parent;
    }

    /**
     * Gets an unmodifiable list of children.
     *
     * @return the children
     */
    public List<XsdNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Adds a child node.
     *
     * @param child the child to add
     */
    public void addChild(XsdNode child) {
        List<XsdNode> oldChildren = new ArrayList<>(children);
        children.add(child);
        child.setParent(this);
        pcs.firePropertyChange("children", oldChildren, new ArrayList<>(children));
    }

    /**
     * Adds a child node at a specific index.
     *
     * @param index the index at which to insert the child
     * @param child the child to add
     */
    public void addChild(int index, XsdNode child) {
        List<XsdNode> oldChildren = new ArrayList<>(children);
        children.add(index, child);
        child.setParent(this);
        pcs.firePropertyChange("children", oldChildren, new ArrayList<>(children));
    }

    /**
     * Removes a child node.
     *
     * @param child the child to remove
     * @return true if the child was removed
     */
    public boolean removeChild(XsdNode child) {
        List<XsdNode> oldChildren = new ArrayList<>(children);
        boolean removed = children.remove(child);
        if (removed) {
            child.setParent(null);
            pcs.firePropertyChange("children", oldChildren, new ArrayList<>(children));
        }
        return removed;
    }

    /**
     * Checks if this node has children.
     *
     * @return true if this node has children
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Gets the minimum occurrence.
     *
     * @return the minOccurs value
     */
    public int getMinOccurs() {
        return minOccurs;
    }

    /**
     * Sets the minimum occurrence.
     *
     * @param minOccurs the new minOccurs value
     */
    public void setMinOccurs(int minOccurs) {
        int oldValue = this.minOccurs;
        this.minOccurs = minOccurs;
        pcs.firePropertyChange("minOccurs", oldValue, minOccurs);
    }

    /**
     * Gets the maximum occurrence.
     *
     * @return the maxOccurs value, or UNBOUNDED (-1)
     */
    public int getMaxOccurs() {
        return maxOccurs;
    }

    /**
     * Sets the maximum occurrence.
     *
     * @param maxOccurs the new maxOccurs value, or UNBOUNDED (-1)
     */
    public void setMaxOccurs(int maxOccurs) {
        int oldValue = this.maxOccurs;
        this.maxOccurs = maxOccurs;
        pcs.firePropertyChange("maxOccurs", oldValue, maxOccurs);
    }

    /**
     * Gets the documentation annotation.
     *
     * @return the documentation, or null if not set
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation annotation.
     *
     * @param documentation the documentation text
     */
    public void setDocumentation(String documentation) {
        String oldValue = this.documentation;
        this.documentation = documentation;
        pcs.firePropertyChange("documentation", oldValue, documentation);
    }

    /**
     * Gets the appinfo annotation.
     *
     * @return the appinfo, or null if not set
     */
    public String getAppinfo() {
        return appinfo;
    }

    /**
     * Sets the appinfo annotation.
     *
     * @param appinfo the appinfo text
     */
    public void setAppinfo(String appinfo) {
        String oldValue = this.appinfo;
        this.appinfo = appinfo;
        pcs.firePropertyChange("appinfo", oldValue, appinfo);
    }

    /**
     * Adds a property change listener.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Gets the node type (for subclasses to override).
     *
     * @return the node type
     */
    public abstract XsdNodeType getNodeType();

    /**
     * Checks if this node is a descendant of the given node.
     *
     * @param potentialAncestor the potential ancestor node
     * @return true if this node is a descendant of the given node
     */
    public boolean isDescendantOf(XsdNode potentialAncestor) {
        XsdNode current = this.parent;
        while (current != null) {
            if (current == potentialAncestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Creates a deep copy of this node and all its children.
     * Each subclass should override this method to copy type-specific properties.
     *
     * @param suffix suffix to append to the node name (e.g., "_copy")
     * @return a deep copy of this node
     */
    public abstract XsdNode deepCopy(String suffix);

    /**
     * Helper method to copy base properties from this node to a target node.
     * Used by subclasses in their deepCopy() implementations.
     *
     * @param target the target node to copy properties to
     */
    protected void copyBasicPropertiesTo(XsdNode target) {
        target.setMinOccurs(this.minOccurs);
        target.setMaxOccurs(this.maxOccurs);
        target.setDocumentation(this.documentation);
        target.setAppinfo(this.appinfo);

        // Recursively copy children
        for (XsdNode child : this.children) {
            XsdNode copiedChild = child.deepCopy(null);  // No suffix for children
            target.addChild(copiedChild);
        }
    }

    @Override
    public String toString() {
        return getNodeType() + " '" + name + "'";
    }
}
