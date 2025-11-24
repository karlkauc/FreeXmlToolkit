package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Command to delete a node from its parent.
 *
 * <p>This command:</p>
 * <ul>
 *   <li>Removes a node from its parent</li>
 *   <li>Stores the parent and index for undo</li>
 *   <li>Can be undone by re-inserting the node</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class DeleteNodeCommand implements XmlCommand {

    private final XmlNode nodeToDelete;
    private XmlNode parent;
    private int originalIndex;
    private boolean executed = false;

    /**
     * Constructs a command to delete a node.
     *
     * @param nodeToDelete the node to delete (must have a parent)
     */
    public DeleteNodeCommand(XmlNode nodeToDelete) {
        this.nodeToDelete = nodeToDelete;
        this.parent = nodeToDelete.getParent();

        // Store original index
        if (parent instanceof XmlElement) {
            XmlElement parentElement = (XmlElement) parent;
            this.originalIndex = parentElement.indexOf(nodeToDelete);
        } else if (parent instanceof XmlDocument) {
            XmlDocument doc = (XmlDocument) parent;
            this.originalIndex = doc.getChildren().indexOf(nodeToDelete);
        } else {
            this.originalIndex = -1;
        }
    }

    @Override
    public boolean execute() {
        if (parent == null) {
            return false;
        }

        if (parent instanceof XmlElement) {
            XmlElement parentElement = (XmlElement) parent;
            parentElement.removeChild(nodeToDelete);
            executed = true;
            return true;
        } else if (parent instanceof XmlDocument) {
            XmlDocument doc = (XmlDocument) parent;
            doc.removeChild(nodeToDelete);
            executed = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean undo() {
        if (!executed || parent == null) {
            return false;
        }

        if (parent instanceof XmlElement) {
            XmlElement parentElement = (XmlElement) parent;
            if (originalIndex >= 0 && originalIndex <= parentElement.getChildCount()) {
                parentElement.addChild(originalIndex, nodeToDelete);
            } else {
                parentElement.addChild(nodeToDelete);
            }
            executed = false;
            return true;
        } else if (parent instanceof XmlDocument) {
            XmlDocument doc = (XmlDocument) parent;
            if (originalIndex >= 0 && originalIndex <= doc.getChildCount()) {
                doc.addChild(originalIndex, nodeToDelete);
            } else {
                doc.addChild(nodeToDelete);
            }
            executed = false;
            return true;
        }

        return false;
    }

    @Override
    public String getDescription() {
        String nodeName = nodeToDelete.getClass().getSimpleName();
        if (nodeToDelete instanceof XmlElement) {
            nodeName = "Element '" + ((XmlElement) nodeToDelete).getName() + "'";
        }
        return "Delete " + nodeName;
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
