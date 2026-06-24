package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Command to change a schema-level string attribute of the {@code xs:schema} root:
 * {@code targetNamespace}, {@code version}, {@code elementFormDefault} or
 * {@code attributeFormDefault}.
 * <p>
 * Standalone (not built on {@code AbstractNodePropertyCommand}) so the {@code property} selector
 * is set before the previous value is read — the base would call the overridden read before the
 * subclass field is initialised.
 *
 * @since 2.0
 */
public class ChangeSchemaPropertyCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeSchemaPropertyCommand.class);

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

    private final XsdEditorContext editorContext;
    private final XsdSchema schema;
    private final Property property;
    private final String oldValue;
    private final String newValue;

    /**
     * Creates a new change-schema-property command.
     *
     * @param editorContext the editor context
     * @param schema        the schema root (must be an {@link XsdSchema})
     * @param property      which schema attribute to change
     * @param value         the new value (blank/null clears it)
     * @throws IllegalArgumentException if {@code schema} is not an {@link XsdSchema} or property is null
     */
    public ChangeSchemaPropertyCommand(XsdEditorContext editorContext, XsdNode schema,
                                       Property property, String value) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (!(schema instanceof XsdSchema)) {
            throw new IllegalArgumentException("Schema properties can only be set on the schema root, not on "
                    + (schema == null ? "null" : schema.getClass().getSimpleName()));
        }
        if (property == null) {
            throw new IllegalArgumentException("Property cannot be null");
        }
        this.editorContext = editorContext;
        this.schema = (XsdSchema) schema;
        this.property = property;
        this.oldValue = read();
        this.newValue = (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    /** Internal constructor for merging, carrying an explicit old/new value pair. */
    private ChangeSchemaPropertyCommand(XsdEditorContext editorContext, XsdSchema schema,
                                        Property property, String oldValue, String newValue) {
        this.editorContext = editorContext;
        this.schema = schema;
        this.property = property;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    private String read() {
        return switch (property) {
            case TARGET_NAMESPACE -> schema.getTargetNamespace();
            case VERSION -> schema.getVersion();
            case ELEMENT_FORM_DEFAULT -> schema.getElementFormDefault();
            case ATTRIBUTE_FORM_DEFAULT -> schema.getAttributeFormDefault();
        };
    }

    private void write(String value) {
        switch (property) {
            case TARGET_NAMESPACE -> schema.setTargetNamespace(value);
            case VERSION -> schema.setVersion(value);
            case ELEMENT_FORM_DEFAULT -> schema.setElementFormDefault(value);
            case ATTRIBUTE_FORM_DEFAULT -> schema.setAttributeFormDefault(value);
        }
        editorContext.markNodeDirty(schema);
    }

    @Override
    public boolean execute() {
        try {
            write(newValue);
            return true;
        } catch (Exception e) {
            logger.error("Failed to change schema {}", property.label, e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            write(oldValue);
            return true;
        } catch (Exception e) {
            logger.error("Failed to undo schema {} change", property.label, e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Change schema " + property.label;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return other instanceof ChangeSchemaPropertyCommand o
                && this.property == o.property
                && this.schema.getId().equals(o.schema.getId());
    }

    @Override
    public XsdCommand mergeWith(XsdCommand other) {
        ChangeSchemaPropertyCommand o = (ChangeSchemaPropertyCommand) other;
        return new ChangeSchemaPropertyCommand(editorContext, schema, property, oldValue, o.newValue);
    }

    /** @return the value applied on execute (for tests). */
    public String getNewValue() {
        return newValue;
    }

    /** @return the value restored on undo (for tests). */
    public String getOldValue() {
        return oldValue;
    }
}
