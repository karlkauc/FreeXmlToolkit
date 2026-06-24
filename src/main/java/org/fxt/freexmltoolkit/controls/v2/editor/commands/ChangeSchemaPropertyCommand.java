package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Command to change a schema-level string attribute of the {@code xs:schema} root:
 * {@code targetNamespace}, {@code version}, {@code elementFormDefault} or
 * {@code attributeFormDefault}.
 *
 * @since 2.0
 */
public class ChangeSchemaPropertyCommand extends AbstractNodePropertyCommand<String> {

    /** Which schema-level attribute this command edits. */
    public enum Property {
        TARGET_NAMESPACE("target namespace"),
        VERSION("version"),
        ELEMENT_FORM_DEFAULT("elementFormDefault"),
        ATTRIBUTE_FORM_DEFAULT("attributeFormDefault");

        private final String label;

        Property(String label) {
            this.label = label;
        }
    }

    private final Property property;

    /**
     * Creates a new change-schema-property command.
     *
     * @param editorContext the editor context
     * @param schema        the schema root
     * @param property      which schema attribute to change
     * @param value         the new value (blank/null clears it)
     * @throws IllegalArgumentException if {@code schema} is not an {@link XsdSchema}
     */
    public ChangeSchemaPropertyCommand(XsdEditorContext editorContext, XsdNode schema,
                                       Property property, String value) {
        super(editorContext, requireSchema(schema), normalize(value));
        this.property = property;
    }

    private static XsdNode requireSchema(XsdNode schema) {
        if (!(schema instanceof XsdSchema)) {
            throw new IllegalArgumentException("Schema properties can only be set on the schema root, not on "
                    + (schema == null ? "null" : schema.getClass().getSimpleName()));
        }
        return schema;
    }

    private static String normalize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    @Override
    protected String readValue() {
        XsdSchema schema = (XsdSchema) node;
        return switch (property) {
            case TARGET_NAMESPACE -> schema.getTargetNamespace();
            case VERSION -> schema.getVersion();
            case ELEMENT_FORM_DEFAULT -> schema.getElementFormDefault();
            case ATTRIBUTE_FORM_DEFAULT -> schema.getAttributeFormDefault();
        };
    }

    @Override
    protected void writeValue(String value) {
        XsdSchema schema = (XsdSchema) node;
        switch (property) {
            case TARGET_NAMESPACE -> schema.setTargetNamespace(value);
            case VERSION -> schema.setVersion(value);
            case ELEMENT_FORM_DEFAULT -> schema.setElementFormDefault(value);
            case ATTRIBUTE_FORM_DEFAULT -> schema.setAttributeFormDefault(value);
        }
    }

    @Override
    protected String propertyName() {
        return property.label;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Only merge edits to the SAME schema attribute (the base would otherwise merge any two
        // schema-property edits on the same node).
        return other instanceof ChangeSchemaPropertyCommand o
                && this.property == o.property
                && super.canMergeWith(other);
    }
}
