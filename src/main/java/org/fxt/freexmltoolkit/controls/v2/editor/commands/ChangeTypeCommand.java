package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
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

    private final XsdEditorContext editorContext;
    private final XsdNode node;
    private final String oldType;
    private final String newType;

    /**
     * Creates a new change type command.
     *
     * @param editorContext the editor context
     * @param node          the node whose type to change (must be XsdElement or XsdAttribute)
     * @param newType       the new type reference (e.g., "xs:string", "MyCustomType", or null/empty to remove)
     */
    public ChangeTypeCommand(XsdEditorContext editorContext, XsdNode node, String newType) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement) && !(node instanceof XsdAttribute)) {
            throw new IllegalArgumentException("Node must be an element or attribute to have a type");
        }

        this.editorContext = editorContext;
        this.node = node;
        this.oldType = getTypeFromNode(node);
        this.newType = (newType == null || newType.trim().isEmpty()) ? null : newType.trim();
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Changing type of {} '{}' from '{}' to '{}'",
                    node.getClass().getSimpleName(), node.getName(), oldType, newType);

            // Update the model - this will fire PropertyChangeEvent
            // which triggers automatic view refresh via VisualNode's listener
            setTypeOnNode(node, newType);
            editorContext.setDirty(true);

            logger.info("Successfully changed type of {} '{}'", node.getClass().getSimpleName(), node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to change type of {} '{}'", node.getClass().getSimpleName(), node.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Undoing type change of {} '{}' back to '{}'",
                    node.getClass().getSimpleName(), node.getName(), oldType);

            // Update the model - this will fire PropertyChangeEvent
            setTypeOnNode(node, oldType);
            editorContext.setDirty(true);

            logger.info("Successfully undone type change of {} '{}'", node.getClass().getSimpleName(), node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to undo type change of {} '{}'", node.getClass().getSimpleName(), node.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String nodeName = node.getName() != null ? node.getName() : "(unnamed)";
        String nodeType = node instanceof XsdElement ? "element" : "attribute";

        if (newType == null || newType.isEmpty()) {
            return "Remove type from " + nodeType + " " + nodeName;
        } else if (oldType == null || oldType.isEmpty()) {
            return "Set type of " + nodeType + " " + nodeName + " to " + newType;
        } else {
            return "Change type of " + nodeType + " " + nodeName + " from " + oldType + " to " + newType;
        }
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
