package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to rename a node in the XSD schema.
 * Supports undo by storing the old name.
 * <p>
 * Updates the underlying XsdNode model, which automatically triggers
 * view refresh via PropertyChangeListener (Phase 2.3).
 *
 * @since 2.0
 */
public class RenameNodeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(RenameNodeCommand.class);

    private final XsdNode node;
    private final String oldName;
    private final String newName;

    /**
     * Creates a new rename node command.
     *
     * @param node    the XsdNode to rename
     * @param newName the new name
     */
    public RenameNodeCommand(XsdNode node, String newName) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("New name cannot be null or empty");
        }

        this.node = node;
        this.oldName = node.getName();
        this.newName = newName.trim();
    }

    @Override
    public boolean execute() {
        // Update the model - this will fire PropertyChangeEvent
        // which triggers automatic view refresh via VisualNode's listener
        node.setName(newName);
        logger.info("Renamed node from '{}' to '{}'", oldName, newName);
        return true;
    }

    @Override
    public boolean undo() {
        // Restore the old name in the model
        node.setName(oldName);
        logger.info("Restored node name from '{}' back to '{}'", newName, oldName);
        return true;
    }

    @Override
    public String getDescription() {
        return "Rename '" + oldName + "' to '" + newName + "'";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Consecutive rename commands on the same node can be merged
        if (!(other instanceof RenameNodeCommand otherRename)) {
            return false;
        }

        return this.node == otherRename.node &&
                this.newName.equals(otherRename.oldName);
    }

    // Note: mergeWith() implementation would require creating a new RenameNodeCommand
    // with the original oldName and the other command's newName. Since fields are final,
    // we would need to make them non-final or create a new instance.
    // For now, we use the default implementation which throws UnsupportedOperationException.

    /**
     * Gets the node being renamed.
     *
     * @return the node
     */
    public XsdNode getNode() {
        return node;
    }

    /**
     * Gets the new name.
     *
     * @return the new name
     */
    public String getNewName() {
        return newName;
    }
}
