package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the {@code final} attribute of an XSD complex type.
 * <p>
 * The {@code final} attribute controls which derivations of this type are forbidden (values:
 * {@code extension}, {@code restriction}, or {@code #all}; space-separated combinations are
 * allowed).
 *
 * @since 2.0
 */
public class ChangeFinalCommand extends AbstractNodePropertyCommand<String> {

    /**
     * Creates a new change-final command.
     *
     * @param editorContext the editor context
     * @param node          the target node (must be an {@link XsdComplexType})
     * @param finalValue    the new final value (blank/null clears it)
     * @throws IllegalArgumentException if the node is not a complex type
     */
    public ChangeFinalCommand(XsdEditorContext editorContext, XsdNode node, String finalValue) {
        super(editorContext, requireComplexType(node), normalize(finalValue));
    }

    private static XsdNode requireComplexType(XsdNode node) {
        if (!(node instanceof XsdComplexType)) {
            throw new IllegalArgumentException("Final can only be set on complex types, not on "
                    + (node == null ? "null" : node.getClass().getSimpleName()));
        }
        return node;
    }

    private static String normalize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    @Override
    protected String readValue() {
        return ((XsdComplexType) node).getFinal();
    }

    @Override
    protected void writeValue(String value) {
        ((XsdComplexType) node).setFinal(value);
    }

    @Override
    protected String propertyName() {
        return "final";
    }
}
