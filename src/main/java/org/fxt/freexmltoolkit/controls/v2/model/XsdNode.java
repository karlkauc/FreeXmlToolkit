package org.fxt.freexmltoolkit.controls.v2.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.file.Path;
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
    private final List<XsdDocumentation> documentations = new ArrayList<>();
    private XsdAppInfo appinfo;

    // Source tracking for multi-file XSD support
    private IncludeSourceInfo sourceInfo;

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
        // Listen to child's changes and bubble them up
        child.addPropertyChangeListener(this::bubblePropertyChange);
        pcs.firePropertyChange("children", oldChildren, new ArrayList<>(children));
        // Also notify ancestors about the structural change
        notifyAncestors("descendantChanged");
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
        // Listen to child's changes and bubble them up
        child.addPropertyChangeListener(this::bubblePropertyChange);
        pcs.firePropertyChange("children", oldChildren, new ArrayList<>(children));
        // Also notify ancestors about the structural change
        notifyAncestors("descendantChanged");
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
            // Remove listener from child
            child.removePropertyChangeListener(this::bubblePropertyChange);
            pcs.firePropertyChange("children", oldChildren, new ArrayList<>(children));
            // Also notify ancestors about the structural change
            notifyAncestors("descendantChanged");
        }
        return removed;
    }

    /**
     * Bubbles property change events from children to this node.
     * This allows the root schema to be notified of changes anywhere in the tree.
     *
     * @param evt the property change event from a descendant
     */
    private void bubblePropertyChange(java.beans.PropertyChangeEvent evt) {
        // Forward structural changes to this node's listeners
        if ("children".equals(evt.getPropertyName()) || "descendantChanged".equals(evt.getPropertyName())) {
            pcs.firePropertyChange("descendantChanged", null, evt.getSource());
        }
    }

    /**
     * Notifies all ancestors about a structural change.
     *
     * @param propertyName the property name to use for the notification
     */
    private void notifyAncestors(String propertyName) {
        if (parent != null) {
            parent.pcs.firePropertyChange(propertyName, null, this);
        }
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
     * Gets the list of documentation entries with language support.
     *
     * @return the list of documentation entries (never null, may be empty)
     */
    public List<XsdDocumentation> getDocumentations() {
        return new ArrayList<>(documentations);
    }

    /**
     * Adds a documentation entry.
     *
     * @param doc the documentation to add
     */
    public void addDocumentation(XsdDocumentation doc) {
        if (doc != null) {
            List<XsdDocumentation> oldValue = new ArrayList<>(documentations);
            documentations.add(doc);
            pcs.firePropertyChange("documentations", oldValue, new ArrayList<>(documentations));
        }
    }

    /**
     * Sets the list of documentation entries.
     *
     * @param docs the documentation entries
     */
    public void setDocumentations(List<XsdDocumentation> docs) {
        List<XsdDocumentation> oldValue = new ArrayList<>(documentations);
        documentations.clear();
        if (docs != null) {
            documentations.addAll(docs);
        }
        pcs.firePropertyChange("documentations", oldValue, new ArrayList<>(documentations));
    }

    /**
     * Clears all documentation entries.
     */
    public void clearDocumentations() {
        List<XsdDocumentation> oldValue = new ArrayList<>(documentations);
        documentations.clear();
        pcs.firePropertyChange("documentations", oldValue, documentations);
    }

    /**
     * Checks if this node has any documentation entries.
     *
     * @return true if at least one documentation entry exists
     */
    public boolean hasDocumentations() {
        return !documentations.isEmpty();
    }

    /**
     * Gets the appinfo annotation (structured).
     *
     * @return the appinfo, or null if not set
     */
    public XsdAppInfo getAppinfo() {
        return appinfo;
    }

    /**
     * Sets the appinfo annotation (structured).
     *
     * @param appinfo the appinfo object
     */
    public void setAppinfo(XsdAppInfo appinfo) {
        XsdAppInfo oldValue = this.appinfo;
        this.appinfo = appinfo;
        pcs.firePropertyChange("appinfo", oldValue, appinfo);
    }

    /**
     * Sets the appinfo annotation from a string for backward compatibility.
     * This is a convenience method that wraps the string in an XsdAppInfo object.
     *
     * @param appinfoString the appinfo text (can be null to clear)
     */
    public void setAppinfo(String appinfoString) {
        setAppinfoFromString(appinfoString);
    }

    /**
     * Gets the appinfo as display string for backward compatibility.
     *
     * @return the appinfo as string, or empty string if not set
     */
    public String getAppinfoAsString() {
        return appinfo != null ? appinfo.toDisplayString() : "";
    }

    /**
     * Sets the appinfo from a display string for backward compatibility.
     *
     * @param appinfoString the appinfo text
     */
    public void setAppinfoFromString(String appinfoString) {
        XsdAppInfo oldValue = this.appinfo;
        this.appinfo = XsdAppInfo.fromDisplayString(appinfoString);
        pcs.firePropertyChange("appinfo", oldValue, this.appinfo);
    }

    /**
     * Gets the source information for multi-file XSD support.
     * This tracks which file this node belongs to in schemas using xs:include.
     *
     * @return the source info, or null if not set
     */
    public IncludeSourceInfo getSourceInfo() {
        return sourceInfo;
    }

    /**
     * Sets the source information for multi-file XSD support.
     *
     * @param sourceInfo the source info indicating which file this node belongs to
     */
    public void setSourceInfo(IncludeSourceInfo sourceInfo) {
        IncludeSourceInfo oldValue = this.sourceInfo;
        this.sourceInfo = sourceInfo;
        pcs.firePropertyChange("sourceInfo", oldValue, sourceInfo);
    }

    /**
     * Checks if this node is from an included file (not the main schema).
     *
     * @return true if this node came from an xs:include, false if from main schema or unknown
     */
    public boolean isFromInclude() {
        return sourceInfo != null && sourceInfo.isFromInclude();
    }

    /**
     * Gets the source file path for this node.
     *
     * @return the source file path, or null if not set
     */
    public Path getSourceFile() {
        return sourceInfo != null ? sourceInfo.getSourceFile() : null;
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
        copyBasicPropertiesTo(target, null);
    }

    /**
     * Helper method to copy base properties from this node to a target node.
     * Used by subclasses in their deepCopy() implementations.
     * Note: Children are copied WITHOUT the suffix - suffix only applies to the root node being copied.
     *
     * @param target the target node to copy properties to
     * @param suffix unused, kept for API compatibility
     */
    protected void copyBasicPropertiesTo(XsdNode target, String suffix) {
        target.setMinOccurs(this.minOccurs);
        target.setMaxOccurs(this.maxOccurs);
        target.setDocumentation(this.documentation);
        target.setAppinfo(this.appinfo);

        // Copy source info (immutable, so direct reference is fine)
        target.setSourceInfo(this.sourceInfo);

        // Copy documentation entries (deep copy)
        for (XsdDocumentation doc : this.documentations) {
            target.addDocumentation(doc.deepCopy());
        }

        // Recursively copy children WITHOUT suffix (suffix only applies to root node)
        for (XsdNode child : this.children) {
            XsdNode copiedChild = child.deepCopy(null);
            target.addChild(copiedChild);
        }
    }

    @Override
    public String toString() {
        return getNodeType() + " '" + name + "'";
    }
}
