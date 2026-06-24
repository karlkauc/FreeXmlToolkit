package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the {@code ref} attribute of an XSD element or attribute.
 * <p>
 * A {@code ref} turns a local declaration into a reference to a global element/attribute. This
 * command only edits the value; it does not validate that the referenced declaration exists.
 *
 * @since 2.0
 */
public class ChangeRefCommand extends AbstractNodePropertyCommand<String> {

    /**
     * Creates a new change-ref command.
     *
     * @param editorContext the editor context
     * @param node          the target node (must be an {@link XsdElement} or {@link XsdAttribute})
     * @param ref           the new ref value (blank/null clears it)
     * @throws IllegalArgumentException if the node is not an element or attribute
     */
    public ChangeRefCommand(XsdEditorContext editorContext, XsdNode node, String ref) {
        super(editorContext, requireElementOrAttribute(node), normalize(ref));
    }

    private static XsdNode requireElementOrAttribute(XsdNode node) {
        if (!(node instanceof XsdElement) && !(node instanceof XsdAttribute)) {
            throw new IllegalArgumentException("Ref can only be set on elements or attributes, not on "
                    + (node == null ? "null" : node.getClass().getSimpleName()));
        }
        return node;
    }

    private static String normalize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    @Override
    protected String readValue() {
        return node instanceof XsdElement el ? el.getRef() : ((XsdAttribute) node).getRef();
    }

    @Override
    protected void writeValue(String value) {
        if (node instanceof XsdElement el) {
            el.setRef(value);
        } else {
            ((XsdAttribute) node).setRef(value);
        }
    }

    @Override
    protected String propertyName() {
        return "ref";
    }
}
