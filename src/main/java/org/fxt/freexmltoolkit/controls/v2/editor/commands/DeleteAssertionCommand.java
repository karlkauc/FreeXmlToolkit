package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to delete an XSD 1.1 assertion from an XSD element.
 * <p>
 * Supports full undo/redo functionality by storing the deleted assertion.
 *
 * @since 2.0
 */
public class DeleteAssertionCommand extends AbstractConstraintCommand {

    /**
     * Creates a new delete assertion command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param assertion     the assertion to delete
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or assertion is empty
     */
    public DeleteAssertionCommand(XsdEditorContext editorContext, XsdNode node, String assertion) {
        super(editorContext, node, assertion);
    }

    @Override
    protected String getConstraintTypeName() {
        return "assertion";
    }

    @Override
    protected String getActionVerb() {
        return "Delete";
    }

    @Override
    protected boolean performAction(String value) {
        return element.removeAssertion(value);  // Delete removes the assertion
    }

    @Override
    protected boolean performUndoAction(String value) {
        element.addAssertion(value);     // Undo adds it back
        return true;
    }

    /**
     * Gets the assertion being deleted.
     *
     * @return the assertion expression
     */
    public String getAssertion() {
        return value;
    }
}
