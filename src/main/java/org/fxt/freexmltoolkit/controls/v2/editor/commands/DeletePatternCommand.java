package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to delete a regex pattern constraint from an XSD element.
 * <p>
 * Supports full undo/redo functionality by storing the deleted pattern.
 *
 * @since 2.0
 */
public class DeletePatternCommand extends AbstractConstraintCommand {

    /**
     * Creates a new delete pattern command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param pattern       the pattern to delete
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or pattern is empty
     */
    public DeletePatternCommand(XsdEditorContext editorContext, XsdNode node, String pattern) {
        super(editorContext, node, pattern);
    }

    @Override
    protected String getConstraintTypeName() {
        return "pattern";
    }

    @Override
    protected String getActionVerb() {
        return "Delete";
    }

    @Override
    protected boolean performAction(String value) {
        return element.removePattern(value);  // Delete removes the pattern
    }

    @Override
    protected boolean performUndoAction(String value) {
        element.addPattern(value);     // Undo adds it back
        return true;
    }

    /**
     * Gets the pattern being deleted.
     *
     * @return the pattern
     */
    public String getPattern() {
        return value;
    }
}
