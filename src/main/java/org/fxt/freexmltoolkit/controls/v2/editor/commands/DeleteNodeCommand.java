package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to delete a node from the XSD schema.
 * Supports undo by storing the deleted node and its position.
 * <p>
 * Updates the underlying XsdNode model, which automatically triggers
 * view refresh via PropertyChangeListener (Phase 2.3).
 *
 * @since 2.0
 */
public class DeleteNodeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeleteNodeCommand.class);

    private final XsdNode nodeToDelete;
    private final XsdNode parentNode;
    private int originalIndex = -1;

    /**
     * Creates a new delete node command.
     *
     * @param nodeToDelete the node to delete
     */
    public DeleteNodeCommand(XsdNode nodeToDelete) {
        if (nodeToDelete == null) {
            throw new IllegalArgumentException("Node to delete cannot be null");
        }

        this.nodeToDelete = nodeToDelete;
        this.parentNode = nodeToDelete.getParent();

        if (parentNode != null) {
            // Store original index for undo
            this.originalIndex = parentNode.getChildren().indexOf(nodeToDelete);
        }
    }

    @Override
    public boolean execute() {
        if (parentNode == null) {
            logger.warn("Cannot delete root node");
            return false;
        }

        // Update the model - this will fire PropertyChangeEvent
        // which triggers automatic view refresh via VisualNode's listener
        parentNode.removeChild(nodeToDelete);
        logger.info("Deleted node: {}", nodeToDelete.getName());
        return true;
    }

    @Override
    public boolean undo() {
        if (parentNode == null) {
            logger.warn("Cannot undo deletion of root node");
            return false;
        }

        // Restore node at original position in the model
        if (originalIndex >= 0 && originalIndex <= parentNode.getChildren().size()) {
            parentNode.addChild(originalIndex, nodeToDelete);
        } else {
            // Fallback: add at the end
            parentNode.addChild(nodeToDelete);
        }

        logger.info("Restored deleted node: {}", nodeToDelete.getName());
        return true;
    }

    @Override
    public String getDescription() {
        String nodeType = nodeToDelete.getClass().getSimpleName().replace("Xsd", "").toLowerCase();
        return "Delete " + nodeType + " '" + nodeToDelete.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return parentNode != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Delete commands should not be merged
        return false;
    }
}
