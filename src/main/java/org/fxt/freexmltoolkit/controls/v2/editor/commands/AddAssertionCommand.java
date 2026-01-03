package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to add an XSD 1.1 assertion to an XSD element.
 * <p>
 * Assertions are XPath-based validation rules that allow complex constraints
 * beyond what facets can express. For example: "@price > 0" or "count(item) >= 1".
 * <p>
 * Note: Assertions are an XSD 1.1 feature and may not be supported by all validators.
 * <p>
 * Supports full undo/redo functionality.
 *
 * @since 2.0
 */
public class AddAssertionCommand extends AbstractConstraintCommand {

    /**
     * Creates a new add assertion command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param assertion     the XPath assertion expression to add
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or assertion is empty
     */
    public AddAssertionCommand(XsdEditorContext editorContext, XsdNode node, String assertion) {
        super(editorContext, node, assertion);
    }

    @Override
    protected String getConstraintTypeName() {
        return "assertion";
    }

    @Override
    protected boolean performAction(String value) {
        element.addAssertion(value);
        return true;
    }

    @Override
    protected boolean performUndoAction(String value) {
        return element.removeAssertion(value);
    }

    /**
     * Gets the assertion expression being added.
     *
     * @return the assertion expression
     */
    public String getAssertion() {
        return value;
    }
}
