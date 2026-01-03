package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to delete an enumeration value from an XSD element.
 * <p>
 * Supports full undo/redo functionality by storing the deleted enumeration.
 *
 * @since 2.0
 */
public class DeleteEnumerationCommand extends AbstractConstraintCommand {

    /**
     * Creates a new delete enumeration command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param enumeration   the enumeration value to delete
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or enumeration is empty
     */
    public DeleteEnumerationCommand(XsdEditorContext editorContext, XsdNode node, String enumeration) {
        super(editorContext, node, enumeration);
    }

    @Override
    protected String getConstraintTypeName() {
        return "enumeration";
    }

    @Override
    protected String getActionVerb() {
        return "Delete";
    }

    @Override
    protected boolean performAction(String value) {
        return element.removeEnumeration(value);  // Delete removes the enumeration
    }

    @Override
    protected boolean performUndoAction(String value) {
        element.addEnumeration(value);     // Undo adds it back
        return true;
    }

    /**
     * Gets the enumeration value being deleted.
     *
     * @return the enumeration value
     */
    public String getEnumeration() {
        return value;
    }
}
