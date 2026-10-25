package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the {@code fixed} value of an XSD attribute.
 * <p>
 * Elements carry {@code fixed} together with {@code nillable} and {@code abstract} and are
 * edited through {@link ChangeConstraintsCommand}; an attribute has only {@code fixed}, so it
 * gets this single-property command.
 *
 * @since 2.5
 */
public class ChangeFixedValueCommand extends AbstractNodePropertyCommand<String> {

    /**
     * Creates a new change-fixed-value command.
     *
     * @param editorContext the editor context
     * @param node          the target node (must be an {@link XsdAttribute})
     * @param fixedValue    the new fixed value (blank/null clears it)
     * @throws IllegalArgumentException if the node is not an attribute
     */
    public ChangeFixedValueCommand(XsdEditorContext editorContext, XsdNode node, String fixedValue) {
        super(editorContext, requireAttribute(node), normalize(fixedValue));
    }

    private static XsdNode requireAttribute(XsdNode node) {
        if (!(node instanceof XsdAttribute)) {
            throw new IllegalArgumentException("This fixed value command applies to attributes only, not to "
                    + (node == null ? "null" : node.getClass().getSimpleName()));
        }
        return node;
    }

    /** @return the value as stored in the model: trimmed, blank as {@code null} */
    public static String normalize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    @Override
    protected String readValue() {
        return ((XsdAttribute) node).getFixed();
    }

    @Override
    protected void writeValue(String value) {
        ((XsdAttribute) node).setFixed(value);
    }

    @Override
    protected String propertyName() {
        return "fixed value";
    }
}
