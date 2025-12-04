package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the form attribute of an XSD element or attribute.
 * <p>
 * The form attribute specifies whether the element/attribute name must be qualified
 * with a namespace prefix in instance documents.
 * <p>
 * Valid values:
 * <ul>
 *   <li>qualified: Must use namespace prefix</li>
 *   <li>unqualified: No namespace prefix required</li>
 *   <li>null: Use schema default (elementFormDefault/attributeFormDefault)</li>
 * </ul>
 * <p>
 * Supports full undo/redo functionality by storing both old and new values.
 *
 * @since 2.0
 */
public class ChangeFormCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeFormCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdNode node;
    private final String oldForm;
    private final String newForm;

    /**
     * Creates a new change form command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be XsdElement or XsdAttribute)
     * @param newForm       the new form value (qualified, unqualified, or null for default)
     * @throws IllegalArgumentException if editorContext is null, node is null, or node type is invalid
     */
    public ChangeFormCommand(XsdEditorContext editorContext, XsdNode node, String newForm) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement) && !(node instanceof XsdAttribute)) {
            throw new IllegalArgumentException("Form can only be set on elements or attributes, not on " +
                    node.getClass().getSimpleName());
        }

        this.editorContext = editorContext;
        this.node = node;

        // Get old value
        if (node instanceof XsdElement element) {
            this.oldForm = element.getForm();
        } else {
            XsdAttribute attribute = (XsdAttribute) node;
            this.oldForm = attribute.getForm();
        }

        // Normalize new value
        this.newForm = (newForm == null || newForm.trim().isEmpty()) ? null : newForm.trim();
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Changing form of {} '{}' from '{}' to '{}'",
                    node.getNodeType(), node.getName(), oldForm, newForm);

            if (node instanceof XsdElement element) {
                element.setForm(newForm);
            } else {
                XsdAttribute attribute = (XsdAttribute) node;
                attribute.setForm(newForm);
            }

            editorContext.markNodeDirty(node);

            logger.info("Successfully changed form of {} '{}'", node.getNodeType(), node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to change form of {} '{}'", node.getNodeType(), node.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Undoing form change of {} '{}' back to '{}'",
                    node.getNodeType(), node.getName(), oldForm);

            if (node instanceof XsdElement element) {
                element.setForm(oldForm);
            } else {
                XsdAttribute attribute = (XsdAttribute) node;
                attribute.setForm(oldForm);
            }

            editorContext.markNodeDirty(node);

            logger.info("Successfully undone form change of {} '{}'", node.getNodeType(), node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to undo form change of {} '{}'", node.getNodeType(), node.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String nodeName = node.getName() != null ? node.getName() : "(unnamed)";
        String formValue = newForm != null ? newForm : "default";
        return "Change form of " + nodeName + " to " + formValue;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Allow merging consecutive form changes on the same node
        if (!(other instanceof ChangeFormCommand otherCmd)) {
            return false;
        }

        // Only merge if it's the same node
        return this.node.getId().equals(otherCmd.node.getId());
    }

    /**
     * Gets the node being modified.
     *
     * @return the XSD node
     */
    public XsdNode getNode() {
        return node;
    }

    /**
     * Gets the old form value.
     *
     * @return the old form (can be null)
     */
    public String getOldForm() {
        return oldForm;
    }

    /**
     * Gets the new form value.
     *
     * @return the new form (can be null)
     */
    public String getNewForm() {
        return newForm;
    }
}
