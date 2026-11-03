package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to toggle the {@code abstract} flag of an XSD complex type.
 * <p>
 * An abstract type cannot be used directly by an instance; only types derived from it can.
 * (The flag of an element is changed with {@link ChangeConstraintsCommand}.)
 *
 * @since 2.9
 */
public class ChangeAbstractCommand extends AbstractNodePropertyCommand<Boolean> {

    /**
     * Creates a new change-abstract command.
     *
     * @param editorContext the editor context
     * @param node          the target node (must be an {@link XsdComplexType})
     * @param abstractType  the new abstract flag
     * @throws IllegalArgumentException if the node is not a complex type
     */
    public ChangeAbstractCommand(XsdEditorContext editorContext, XsdNode node, boolean abstractType) {
        super(editorContext, requireComplexType(node), abstractType);
    }

    private static XsdNode requireComplexType(XsdNode node) {
        if (!(node instanceof XsdComplexType)) {
            throw new IllegalArgumentException("Abstract can only be set on complex types here, not on "
                    + (node == null ? "null" : node.getClass().getSimpleName()));
        }
        return node;
    }

    @Override
    protected Boolean readValue() {
        return ((XsdComplexType) node).isAbstract();
    }

    @Override
    protected void writeValue(Boolean value) {
        ((XsdComplexType) node).setAbstract(Boolean.TRUE.equals(value));
    }

    @Override
    protected String propertyName() {
        return "abstract";
    }
}
