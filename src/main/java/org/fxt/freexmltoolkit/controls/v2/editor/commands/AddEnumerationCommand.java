package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to add an enumeration value to an XSD element.
 * <p>
 * Enumerations define a fixed set of allowed values for an element.
 * For example: "red", "green", "blue" for a color element.
 * <p>
 * Supports full undo/redo functionality.
 *
 * @since 2.0
 */
public class AddEnumerationCommand extends AbstractConstraintCommand {

    /**
     * Creates a new add enumeration command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param enumeration   the enumeration value to add
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or enumeration is empty
     */
    public AddEnumerationCommand(XsdEditorContext editorContext, XsdNode node, String enumeration) {
        super(editorContext, node, enumeration);
    }

    @Override
    protected String getConstraintTypeName() {
        return "enumeration";
    }

    @Override
    protected boolean performAction(String value) {
        element.addEnumeration(value);
        return true;
    }

    @Override
    protected boolean performUndoAction(String value) {
        return element.removeEnumeration(value);
    }

    /**
     * Gets the enumeration value being added.
     *
     * @return the enumeration value
     */
    public String getEnumeration() {
        return value;
    }
}
