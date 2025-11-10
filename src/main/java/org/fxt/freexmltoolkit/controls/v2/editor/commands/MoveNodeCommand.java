package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to move a node to a different position in the XSD schema tree.
 * Supports undo by restoring the original position.
 * <p>
 * Updates the underlying XsdNode model, which automatically triggers
 * view refresh via PropertyChangeListener (Phase 2.3).
 *
 * @since 2.0
 */
public class MoveNodeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(MoveNodeCommand.class);

    private final XsdNode nodeToMove;
    private final XsdNode newParent;
    private final int newIndex;

    private final XsdNode oldParent;
    private final int oldIndex;

    /**
     * Creates a new move node command.
     *
     * @param nodeToMove the node to move
     * @param newParent  the new parent node
     * @param newIndex   the index in the new parent's children list (-1 to append at end)
     */
    public MoveNodeCommand(XsdNode nodeToMove, XsdNode newParent, int newIndex) {
        if (nodeToMove == null) {
            throw new IllegalArgumentException("Node to move cannot be null");
        }
        if (newParent == null) {
            throw new IllegalArgumentException("New parent cannot be null");
        }

        this.nodeToMove = nodeToMove;
        this.newParent = newParent;
        this.newIndex = newIndex;

        // Store old position for undo
        this.oldParent = nodeToMove.getParent();
        this.oldIndex = oldParent != null ? oldParent.getChildren().indexOf(nodeToMove) : -1;
    }

    @Override
    public boolean execute() {
        if (oldParent == null) {
            logger.warn("Cannot move root node");
            return false;
        }

        // Validate: don't move a node into itself or its descendants
        if (newParent.isDescendantOf(nodeToMove) || newParent == nodeToMove) {
            logger.warn("Cannot move node into itself or its descendants");
            return false;
        }

        // Update the model - these methods fire PropertyChangeEvent
        // which triggers automatic view refresh via VisualNode's listener
        oldParent.removeChild(nodeToMove);

        // Add to new parent at specified index
        if (newIndex >= 0 && newIndex <= newParent.getChildren().size()) {
            newParent.addChild(newIndex, nodeToMove);
        } else {
            // Append at end if index is invalid
            newParent.addChild(nodeToMove);
        }

        logger.info("Moved node '{}' from '{}' to '{}' at index {}",
                nodeToMove.getName(),
                oldParent.getName(),
                newParent.getName(),
                newIndex);
        return true;
    }

    @Override
    public boolean undo() {
        if (oldParent == null) {
            logger.warn("Cannot undo: old parent is null");
            return false;
        }

        // Update the model - these methods fire PropertyChangeEvent
        newParent.removeChild(nodeToMove);

        // Restore to old parent at old index
        if (oldIndex >= 0 && oldIndex <= oldParent.getChildren().size()) {
            oldParent.addChild(oldIndex, nodeToMove);
        } else {
            // Fallback: add at the end
            oldParent.addChild(nodeToMove);
        }

        logger.info("Restored node '{}' to original position in '{}'",
                nodeToMove.getName(), oldParent.getName());
        return true;
    }

    @Override
    public String getDescription() {
        return "Move '" + nodeToMove.getName() + "' from '" +
                (oldParent != null ? oldParent.getName() : "?") +
                "' to '" + newParent.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return oldParent != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Move commands should not be merged
        return false;
    }
}
