package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the type reference of an XSD element or attribute.
 * Supports undo by restoring the original type.
 * <p>
 * Updates the underlying XsdNode model, which automatically triggers
 * view refresh via PropertyChangeListener (Phase 2.3).
 *
 * @since 2.0
 */
public class ChangeTypeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeTypeCommand.class);

    private final XsdNode node;
    private final String oldType;
    private final String newType;

    /**
     * Creates a new change type command.
     *
     * @param node    the node whose type to change (must be XsdElement or XsdAttribute)
     * @param newType the new type reference (e.g., "xs:string", "MyCustomType")
     */
    public ChangeTypeCommand(XsdNode node, String newType) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement) && !(node instanceof XsdAttribute)) {
            throw new IllegalArgumentException("Node must be an element or attribute to have a type");
        }
        if (newType == null || newType.trim().isEmpty()) {
            throw new IllegalArgumentException("New type cannot be null or empty");
        }

        this.node = node;
        this.oldType = getTypeFromNode(node);
        this.newType = newType.trim();
    }

    @Override
    public boolean execute() {
        // Update the model - this will fire PropertyChangeEvent
        // which triggers automatic view refresh via VisualNode's listener
        setTypeOnNode(node, newType);

        logger.info("Changed type of '{}' from '{}' to '{}'",
                node.getName(), oldType, newType);
        return true;
    }

    @Override
    public boolean undo() {
        // Update the model - this will fire PropertyChangeEvent
        setTypeOnNode(node, oldType);

        logger.info("Restored type of '{}' from '{}' back to '{}'",
                node.getName(), newType, oldType);
        return true;
    }

    @Override
    public String getDescription() {
        return "Change type of '" + node.getName() + "' from '" +
                (oldType != null ? oldType : "?") + "' to '" + newType + "'";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Consecutive type changes on the same node can be merged
        if (!(other instanceof ChangeTypeCommand otherChange)) {
            return false;
        }

        return this.node.getId().equals(otherChange.node.getId()) &&
                this.newType.equals(otherChange.oldType);
    }

    /**
     * Gets the node being modified.
     *
     * @return the node
     */
    public XsdNode getNode() {
        return node;
    }

    /**
     * Gets the old type.
     *
     * @return the old type
     */
    public String getOldType() {
        return oldType;
    }

    /**
     * Gets the new type.
     *
     * @return the new type
     */
    public String getNewType() {
        return newType;
    }

    /**
     * Gets the type from a node (supports XsdElement and XsdAttribute).
     *
     * @param node the node
     * @return the type, or null if not set
     */
    private String getTypeFromNode(XsdNode node) {
        if (node instanceof XsdElement element) {
            return element.getType();
        } else if (node instanceof XsdAttribute attribute) {
            return attribute.getType();
        }
        return null;
    }

    /**
     * Sets the type on a node (supports XsdElement and XsdAttribute).
     *
     * @param node the node
     * @param type the type to set
     */
    private void setTypeOnNode(XsdNode node, String type) {
        if (node instanceof XsdElement element) {
            element.setType(type);
        } else if (node instanceof XsdAttribute attribute) {
            attribute.setType(type);
        }
    }
}
