package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the {@code block} attribute of an XSD element or complex type.
 * <p>
 * The {@code block} attribute restricts which derivation/substitution mechanisms may replace
 * this declaration in an instance document (values: {@code extension}, {@code restriction},
 * {@code substitution}, or {@code #all}; space-separated combinations are allowed).
 *
 * @since 2.0
 */
public class ChangeBlockCommand extends AbstractNodePropertyCommand<String> {

    /**
     * Creates a new change-block command.
     *
     * @param editorContext the editor context
     * @param node          the target node (must be an {@link XsdElement} or {@link XsdComplexType})
     * @param block         the new block value (blank/null clears it)
     * @throws IllegalArgumentException if the node is not an element or complex type
     */
    public ChangeBlockCommand(XsdEditorContext editorContext, XsdNode node, String block) {
        super(editorContext, requireSupported(node), normalize(block));
    }

    private static XsdNode requireSupported(XsdNode node) {
        if (!(node instanceof XsdElement) && !(node instanceof XsdComplexType)) {
            throw new IllegalArgumentException("Block can only be set on elements or complex types, not on "
                    + (node == null ? "null" : node.getClass().getSimpleName()));
        }
        return node;
    }

    private static String normalize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    @Override
    protected String readValue() {
        return node instanceof XsdElement el ? el.getBlock() : ((XsdComplexType) node).getBlock();
    }

    @Override
    protected void writeValue(String value) {
        if (node instanceof XsdElement el) {
            el.setBlock(value);
        } else {
            ((XsdComplexType) node).setBlock(value);
        }
    }

    @Override
    protected String propertyName() {
        return "block";
    }
}
