package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the use attribute of an XSD attribute.
 * <p>
 * The use attribute specifies whether the attribute is required, optional, or prohibited.
 * <p>
 * Valid values:
 * <ul>
 *   <li>required: Attribute must appear in instance documents</li>
 *   <li>optional: Attribute may appear (default)</li>
 *   <li>prohibited: Attribute must not appear (used in restrictions)</li>
 * </ul>
 * <p>
 * Note: This command is only applicable to XSD attributes, not elements.
 * <p>
 * Supports full undo/redo functionality by storing both old and new values.
 *
 * @since 2.0
 */
public class ChangeUseCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeUseCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdAttribute attribute;
    private final String oldUse;
    private final String newUse;

    /**
     * Creates a new change use command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdAttribute)
     * @param newUse        the new use value (required, optional, or prohibited)
     * @throws IllegalArgumentException if editorContext is null, node is null, or node is not an XsdAttribute
     */
    public ChangeUseCommand(XsdEditorContext editorContext, XsdNode node, String newUse) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdAttribute)) {
            throw new IllegalArgumentException("Use can only be set on attributes, not on " +
                    node.getClass().getSimpleName());
        }

        this.editorContext = editorContext;
        this.attribute = (XsdAttribute) node;

        // Get old value
        this.oldUse = attribute.getUse();

        // Normalize and validate new value
        this.newUse = (newUse == null || newUse.trim().isEmpty()) ? "optional" : newUse.trim();

        if (!isValidUse(this.newUse)) {
            throw new IllegalArgumentException("Invalid use value: " + newUse +
                    ". Must be one of: required, optional, prohibited");
        }
    }

    /**
     * Validates if a use value is valid.
     *
     * @param use the use value to validate
     * @return true if valid
     */
    private boolean isValidUse(String use) {
        return "required".equals(use) || "optional".equals(use) || "prohibited".equals(use);
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Changing use of attribute '{}' from '{}' to '{}'",
                    attribute.getName(), oldUse, newUse);

            attribute.setUse(newUse);
            editorContext.setDirty(true);

            logger.info("Successfully changed use of attribute '{}'", attribute.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to change use of attribute '{}'", attribute.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Undoing use change of attribute '{}' back to '{}'",
                    attribute.getName(), oldUse);

            attribute.setUse(oldUse);
            editorContext.setDirty(true);

            logger.info("Successfully undone use change of attribute '{}'", attribute.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to undo use change of attribute '{}'", attribute.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String attributeName = attribute.getName() != null ? attribute.getName() : "(unnamed)";
        return "Change use of " + attributeName + " to " + newUse;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Allow merging consecutive use changes on the same attribute
        if (!(other instanceof ChangeUseCommand otherCmd)) {
            return false;
        }

        // Only merge if it's the same attribute
        return this.attribute.getId().equals(otherCmd.attribute.getId());
    }

    /**
     * Gets the attribute being modified.
     *
     * @return the XSD attribute
     */
    public XsdAttribute getAttribute() {
        return attribute;
    }

    /**
     * Gets the old use value.
     *
     * @return the old use value
     */
    public String getOldUse() {
        return oldUse;
    }

    /**
     * Gets the new use value.
     *
     * @return the new use value
     */
    public String getNewUse() {
        return newUse;
    }
}
