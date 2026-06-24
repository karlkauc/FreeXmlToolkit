package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the {@code default} value of an XSD element or attribute.
 * <p>
 * The {@code default} attribute supplies a value when the element is present but empty, or
 * (for attributes) when absent. It is mutually exclusive with {@code fixed} in XSD, but this
 * command does not enforce that — the UI surfaces both fields independently.
 *
 * @since 2.0
 */
public class ChangeDefaultValueCommand extends AbstractNodePropertyCommand<String> {

    /**
     * Creates a new change-default-value command.
     *
     * @param editorContext the editor context
     * @param node          the target node (must be an {@link XsdElement} or {@link XsdAttribute})
     * @param defaultValue  the new default value (blank/null clears it)
     * @throws IllegalArgumentException if the node is not an element or attribute
     */
    public ChangeDefaultValueCommand(XsdEditorContext editorContext, XsdNode node, String defaultValue) {
        super(editorContext, requireElementOrAttribute(node), normalize(defaultValue));
    }

    private static XsdNode requireElementOrAttribute(XsdNode node) {
        if (!(node instanceof XsdElement) && !(node instanceof XsdAttribute)) {
            throw new IllegalArgumentException("Default value can only be set on elements or attributes, not on "
                    + (node == null ? "null" : node.getClass().getSimpleName()));
        }
        return node;
    }

    private static String normalize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    @Override
    protected String readValue() {
        return node instanceof XsdElement el ? el.getDefaultValue() : ((XsdAttribute) node).getDefaultValue();
    }

    @Override
    protected void writeValue(String value) {
        if (node instanceof XsdElement el) {
            el.setDefaultValue(value);
        } else {
            ((XsdAttribute) node).setDefaultValue(value);
        }
    }

    @Override
    protected String propertyName() {
        return "default value";
    }
}
