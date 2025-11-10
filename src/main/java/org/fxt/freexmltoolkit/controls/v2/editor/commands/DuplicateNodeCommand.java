package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to duplicate a node in the XSD schema.
 * Creates a deep copy of the node with all its properties and children.
 * Supports undo by removing the duplicated node.
 * <p>
 * Updates the underlying XsdNode model, which automatically triggers
 * view refresh via PropertyChangeListener (Phase 2.3).
 *
 * @since 2.0
 */
public class DuplicateNodeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DuplicateNodeCommand.class);

    private final XsdNode nodeToDuplicate;
    private final XsdNode parentNode;
    private XsdNode duplicatedNode;
    private int insertIndex = -1;

    /**
     * Creates a new duplicate node command.
     *
     * @param nodeToDuplicate the node to duplicate
     */
    public DuplicateNodeCommand(XsdNode nodeToDuplicate) {
        if (nodeToDuplicate == null) {
            throw new IllegalArgumentException("Node to duplicate cannot be null");
        }

        this.nodeToDuplicate = nodeToDuplicate;
        this.parentNode = nodeToDuplicate.getParent();

        if (parentNode != null) {
            // Insert after the original node
            this.insertIndex = parentNode.getChildren().indexOf(nodeToDuplicate) + 1;
        }
    }

    @Override
    public boolean execute() {
        if (parentNode == null) {
            logger.warn("Cannot duplicate root node");
            return false;
        }

        // Deep copy the node with all its properties and children
        // The "_copy" suffix is appended to the name
        duplicatedNode = nodeToDuplicate.deepCopy("_copy");

        // Update the model - this will fire PropertyChangeEvent
        // which triggers automatic view refresh via VisualNode's listener
        if (insertIndex >= 0 && insertIndex <= parentNode.getChildren().size()) {
            parentNode.addChild(insertIndex, duplicatedNode);
        } else {
            // Fallback: add at the end
            parentNode.addChild(duplicatedNode);
        }

        logger.info("Duplicated node '{}' as '{}'", nodeToDuplicate.getName(), duplicatedNode.getName());
        return true;
    }

    @Override
    public boolean undo() {
        if (duplicatedNode == null) {
            logger.warn("Cannot undo: no node was duplicated");
            return false;
        }

        if (parentNode == null) {
            logger.warn("Cannot undo duplication: parent node is null");
            return false;
        }

        // Update the model - this will fire PropertyChangeEvent
        parentNode.removeChild(duplicatedNode);
        logger.info("Removed duplicated node: {}", duplicatedNode.getName());
        return true;
    }

    @Override
    public String getDescription() {
        String nodeType = nodeToDuplicate.getNodeType().name().toLowerCase().replace("_", " ");
        return "Duplicate " + nodeType + " '" + nodeToDuplicate.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return duplicatedNode != null && parentNode != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Duplicate commands should not be merged
        return false;
    }

    /**
     * Gets the duplicated node (after execute() has been called).
     *
     * @return the duplicated node, or null if not yet executed
     */
    public XsdNode getDuplicatedNode() {
        return duplicatedNode;
    }
}
