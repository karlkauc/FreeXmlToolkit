package org.fxt.freexmltoolkit.controls.v2.editor.clipboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Clipboard for storing XSD nodes for copy/cut/paste operations.
 * Maintains a reference to a copied or cut node and provides paste functionality.
 * <p>
 * For paste operations, returns a deep copy of the stored node to avoid
 * reference issues.
 *
 * @since 2.0
 */
public class XsdClipboard {

    private static final Logger logger = LogManager.getLogger(XsdClipboard.class);

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * The node currently in the clipboard.
     */
    private XsdNode clipboardNode;

    /**
     * Whether the clipboard content was from a cut operation.
     * If true, the original node should be deleted on paste.
     */
    private boolean isCut;

    /**
     * Creates a new empty clipboard.
     */
    public XsdClipboard() {
        this.clipboardNode = null;
        this.isCut = false;
    }

    /**
     * Copies a node to the clipboard.
     * The original node remains unchanged.
     *
     * @param node the node to copy
     */
    public void copy(XsdNode node) {
        if (node == null) {
            logger.warn("Cannot copy null node to clipboard");
            return;
        }

        XsdNode oldNode = this.clipboardNode;
        this.clipboardNode = node;
        this.isCut = false;

        logger.info("Copied node '{}' to clipboard", node.getName());
        pcs.firePropertyChange("content", oldNode, clipboardNode);
    }

    /**
     * Cuts a node to the clipboard.
     * The original node will be deleted when paste is performed.
     *
     * @param node the node to cut
     */
    public void cut(XsdNode node) {
        if (node == null) {
            logger.warn("Cannot cut null node to clipboard");
            return;
        }

        XsdNode oldNode = this.clipboardNode;
        this.clipboardNode = node;
        this.isCut = true;

        logger.info("Cut node '{}' to clipboard", node.getName());
        pcs.firePropertyChange("content", oldNode, clipboardNode);
    }

    /**
     * Creates a deep copy of the clipboard content for pasting.
     * The returned node is a complete copy with a new name suffix to avoid duplicates.
     *
     * @return a deep copy of the clipboard node, or null if clipboard is empty
     */
    public XsdNode paste() {
        if (clipboardNode == null) {
            logger.debug("Cannot paste: clipboard is empty");
            return null;
        }

        // Create a deep copy with "_copy" suffix
        XsdNode copy = clipboardNode.deepCopy("_copy");
        logger.info("Created paste copy of node '{}'", clipboardNode.getName());

        return copy;
    }

    /**
     * Checks if the clipboard has content.
     *
     * @return true if there is content to paste
     */
    public boolean hasContent() {
        return clipboardNode != null;
    }

    /**
     * Checks if the clipboard content is from a cut operation.
     *
     * @return true if the content was cut (original should be deleted on paste)
     */
    public boolean isCut() {
        return isCut;
    }

    /**
     * Gets the original node in the clipboard.
     * Used for cut operations to determine what to delete after paste.
     *
     * @return the original clipboard node, or null if empty
     */
    public XsdNode getClipboardNode() {
        return clipboardNode;
    }

    /**
     * Clears the clipboard.
     */
    public void clear() {
        XsdNode oldNode = this.clipboardNode;
        this.clipboardNode = null;
        this.isCut = false;

        logger.debug("Clipboard cleared");
        pcs.firePropertyChange("content", oldNode, null);
    }

    /**
     * Adds a property change listener for clipboard changes.
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
}
