package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
     * Random high half of every id in this JVM, so ids of different runs do not collide.
     * Drawn once: {@code UUID.randomUUID()} per node went through {@code SecureRandom} and
     * dominated parsing of documents with millions of nodes (telemetry 2.10.0).
     */
    private static final long SESSION_BITS = UUID.randomUUID().getMostSignificantBits();

    /** Source of the per-node serial numbers (unique within this JVM). */
    private static final AtomicLong SERIALS = new AtomicLong();

    /**
     * Immutable unique serial number of this node; {@link #getId()} turns it into a UUID.
     * Never changes, even when node is duplicated (deep copy gets a new one).
     */
    private final long serial;

    /**
     * Parent node in the XML tree.
     * Null for root nodes (XmlDocument).
     */
    private XmlNode parent;

    /**
     * PropertyChangeSupport for observable properties, created with the first listener: most of
     * the millions of nodes of a large document never get one, and the support object (with its
     * listener map) per node cost hundreds of MB.
     */
    private PropertyChangeSupport pcs;

    /**
     * Counts the changes of this node and its descendants (every fired property change bumps the
     * node and all its ancestors). Views compare it to reuse what they derived from an unchanged
     * subtree, e.g. the grid's table records after a sort or a cell edit elsewhere.
     */
    private int subtreeVersion;

    /**
     * Constructs a new XmlNode with a unique UUID.
     */
    protected XmlNode() {
        this.serial = SERIALS.incrementAndGet();
        this.parent = null;
    }

    /**
     * Copy constructor for deep copy operations.
     * Creates a new node with a NEW UUID.
     *
     * @param original the original node to copy from
     */
    protected XmlNode(XmlNode original) {
        this.serial = SERIALS.incrementAndGet(); // new id for the copy
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
        support().addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener from this node.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (pcs != null) {
            pcs.removePropertyChangeListener(listener);
        }
    }

    /**
     * Adds a PropertyChangeListener for a specific property.
     *
     * @param propertyName the name of the property
     * @param listener     the listener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        support().addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a PropertyChangeListener for a specific property.
     *
     * @param propertyName the name of the property
     * @param listener     the listener to remove
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (pcs != null) {
            pcs.removePropertyChangeListener(propertyName, listener);
        }
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
        for (XmlNode node = this; node != null; node = node.parent) {
            node.subtreeVersion++;
        }
        if (pcs != null) {
            pcs.firePropertyChange(propertyName, oldValue, newValue);
        }
    }

    /**
     * @return a counter that changes whenever this node or one of its descendants changes (any
     *         property change event, fired or not); equal values mean an unchanged subtree
     */
    public int getSubtreeVersion() {
        return subtreeVersion;
    }

    /** @return the change support, created on first use */
    private PropertyChangeSupport support() {
        if (pcs == null) {
            pcs = new PropertyChangeSupport(this);
        }
        return pcs;
    }

    // ==================== Identity and Relationships ====================

    /**
     * Returns the unique identifier for this node.
     * This ID never changes and is unique across the entire application.
     *
     * @return the immutable UUID
     */
    public UUID getId() {
        return new UUID(SESSION_BITS, serial);
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
     * Serializes this node to XML text with configurable indentation size.
     *
     * <p>Subclasses that use indentation should override this method.
     * The default implementation delegates to {@link #serialize(int)}.</p>
     *
     * @param indent     the current indentation level (0 = no indent)
     * @param indentSize the number of spaces per indentation level
     * @return the XML representation of this node
     */
    public String serialize(int indent, int indentSize) {
        return serialize(indent);
    }

    /**
     * Appends {@link #serialize(int, int)} to {@code out}. Containers override it to write their
     * children into the same builder: building a string per element and copying it into the
     * parent's copied a 150-MB document several times over (once per nesting level).
     *
     * @param out        the builder to append to
     * @param indent     the current indentation level
     * @param indentSize the number of spaces per indentation level
     */
    public void serializeTo(StringBuilder out, int indent, int indentSize) {
        out.append(serialize(indent, indentSize));
    }

    /**
     * {@link #serializeTo(StringBuilder, int, int)} that also records the line of every element
     * start tag in {@code lines} (when not {@code null}).
     */
    public void serializeTo(StringBuilder out, int indent, int indentSize, LineRecorder lines) {
        serializeTo(out, indent, indentSize);
    }

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
        return getClass().getSimpleName() + "[id=" + getId() + "]";
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
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        XmlNode other = (XmlNode) obj;
        return serial == other.serial;
    }

    /**
     * Returns the hash code based on UUID.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        long bits = SESSION_BITS ^ serial; // same value as getId().hashCode(), without the object
        return (int) (bits >> 32) ^ (int) bits;
    }
}
