package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to toggle the {@code mixed} content flag of an XSD complex type.
 * <p>
 * When {@code mixed="true"}, character data may appear between the type's child elements.
 *
 * @since 2.0
 */
public class ChangeMixedCommand extends AbstractNodePropertyCommand<Boolean> {

    /**
     * Creates a new change-mixed command.
     *
     * @param editorContext the editor context
     * @param node          the target node (must be an {@link XsdComplexType})
     * @param mixed         the new mixed flag
     * @throws IllegalArgumentException if the node is not a complex type
     */
    public ChangeMixedCommand(XsdEditorContext editorContext, XsdNode node, boolean mixed) {
        super(editorContext, requireComplexType(node), mixed);
    }

    private static XsdNode requireComplexType(XsdNode node) {
        if (!(node instanceof XsdComplexType)) {
            throw new IllegalArgumentException("Mixed content can only be set on complex types, not on "
                    + (node == null ? "null" : node.getClass().getSimpleName()));
        }
        return node;
    }

    @Override
    protected Boolean readValue() {
        return ((XsdComplexType) node).isMixed();
    }

    @Override
    protected void writeValue(Boolean value) {
        ((XsdComplexType) node).setMixed(Boolean.TRUE.equals(value));
    }

    @Override
    protected String propertyName() {
        return "mixed content";
    }
}
