package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to add a regex pattern constraint to an XSD element.
 * <p>
 * Patterns define regular expressions that element values must match.
 * For example: "[0-9]{3}-[0-9]{2}-[0-9]{4}" for US Social Security numbers.
 * <p>
 * Supports full undo/redo functionality.
 *
 * @since 2.0
 */
public class AddPatternCommand extends AbstractConstraintCommand {

    /**
     * Creates a new add pattern command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param pattern       the regex pattern to add
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or pattern is empty
     */
    public AddPatternCommand(XsdEditorContext editorContext, XsdNode node, String pattern) {
        super(editorContext, node, pattern);
    }

    @Override
    protected String getConstraintTypeName() {
        return "pattern";
    }

    @Override
    protected boolean performAction(String value) {
        element.addPattern(value);
        return true;
    }

    @Override
    protected boolean performUndoAction(String value) {
        return element.removePattern(value);
    }

    /**
     * Gets the pattern being added.
     *
     * @return the pattern
     */
    public String getPattern() {
        return value;
    }
}
