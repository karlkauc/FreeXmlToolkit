package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Command to move a node to a different parent or different position.
 *
 * <p>This command:</p>
 * <ul>
 *   <li>Removes node from old parent</li>
 *   <li>Adds node to new parent at specified index</li>
 *   <li>Can be undone by moving back to original position</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class MoveNodeCommand implements XmlCommand {

    private final XmlNode nodeToMove;
    private final XmlNode newParent;
    private final int newIndex;

    private XmlNode oldParent;
    private int oldIndex;
    private boolean executed = false;

    /**
     * Constructs a command to move a node to end of new parent.
     *
     * @param nodeToMove the node to move
     * @param newParent  the new parent
     */
    public MoveNodeCommand(XmlNode nodeToMove, XmlNode newParent) {
        this(nodeToMove, newParent, -1);
    }

    /**
     * Constructs a command to move a node to a specific index in new parent.
     *
     * @param nodeToMove the node to move
     * @param newParent  the new parent
     * @param newIndex   the index in new parent (-1 for end)
     */
    public MoveNodeCommand(XmlNode nodeToMove, XmlNode newParent, int newIndex) {
        this.nodeToMove = nodeToMove;
        this.newParent = newParent;
        this.newIndex = newIndex;

        // Store old parent and index
        this.oldParent = nodeToMove.getParent();
        if (oldParent instanceof XmlElement) {
            this.oldIndex = ((XmlElement) oldParent).indexOf(nodeToMove);
        } else if (oldParent instanceof XmlDocument) {
            this.oldIndex = ((XmlDocument) oldParent).getChildren().indexOf(nodeToMove);
        } else {
            this.oldIndex = -1;
        }
    }

    @Override
    public boolean execute() {
        if (oldParent == null || newParent == null) {
            return false;
        }

        // Remove from old parent
        removeFromParent(nodeToMove, oldParent);

        // Add to new parent
        addToParent(nodeToMove, newParent, newIndex);

        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }

        // Remove from new parent
        removeFromParent(nodeToMove, newParent);

        // Add back to old parent at old index
        addToParent(nodeToMove, oldParent, oldIndex);

        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        String nodeName = nodeToMove.getClass().getSimpleName();
        if (nodeToMove instanceof XmlElement) {
            nodeName = "Element '" + ((XmlElement) nodeToMove).getName() + "'";
        }
        return "Move " + nodeName;
    }

    @Override
    public String toString() {
        return getDescription();
    }

    /**
     * Removes a node from its parent.
     */
    private void removeFromParent(XmlNode node, XmlNode parent) {
        if (parent instanceof XmlElement) {
            ((XmlElement) parent).removeChild(node);
        } else if (parent instanceof XmlDocument) {
            ((XmlDocument) parent).removeChild(node);
        }
    }

    /**
     * Adds a node to a parent at specified index.
     */
    private void addToParent(XmlNode node, XmlNode parent, int index) {
        if (parent instanceof XmlElement) {
            XmlElement parentElement = (XmlElement) parent;
            if (index >= 0 && index <= parentElement.getChildCount()) {
                parentElement.addChild(index, node);
            } else {
                parentElement.addChild(node);
            }
        } else if (parent instanceof XmlDocument) {
            XmlDocument doc = (XmlDocument) parent;
            if (index >= 0 && index <= doc.getChildCount()) {
                doc.addChild(index, node);
            } else {
                doc.addChild(node);
            }
        }
    }
}
