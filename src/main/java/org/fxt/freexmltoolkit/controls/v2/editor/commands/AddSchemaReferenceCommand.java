package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdImport;
import org.fxt.freexmltoolkit.controls.v2.model.XsdInclude;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdOverride;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRedefine;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Command to add an {@code xs:import} or {@code xs:include} directive to the schema root.
 * <p>
 * The new directive is inserted directly after the last existing import/include/redefine/override
 * child (or at the top otherwise), since XSD requires these to precede the schema's type
 * definitions.
 *
 * @since 2.0
 */
public class AddSchemaReferenceCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddSchemaReferenceCommand.class);

    /** Whether to add an {@code xs:import} or an {@code xs:include}. */
    public enum Kind {
        IMPORT, INCLUDE
    }

    private final XsdEditorContext editorContext;
    private final XsdSchema schema;
    private final Kind kind;
    private final String namespace;
    private final String schemaLocation;

    private XsdNode created;
    private int insertedIndex = -1;

    /**
     * Creates a new add-schema-reference command.
     *
     * @param editorContext  the editor context
     * @param schema         the schema root (must be an {@link XsdSchema})
     * @param kind           import or include
     * @param namespace      the imported namespace (import only; optional, ignored for include)
     * @param schemaLocation the schema location (required)
     * @throws IllegalArgumentException if the node is not a schema or the schema location is empty
     */
    public AddSchemaReferenceCommand(XsdEditorContext editorContext, XsdNode schema, Kind kind,
                                     String namespace, String schemaLocation) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (!(schema instanceof XsdSchema)) {
            throw new IllegalArgumentException("Imports/includes can only be added to the schema root, not to "
                    + (schema == null ? "null" : schema.getClass().getSimpleName()));
        }
        if (kind == null) {
            throw new IllegalArgumentException("Kind cannot be null");
        }
        if (schemaLocation == null || schemaLocation.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema location cannot be empty");
        }
        this.editorContext = editorContext;
        this.schema = (XsdSchema) schema;
        this.kind = kind;
        this.namespace = namespace == null || namespace.trim().isEmpty() ? null : namespace.trim();
        this.schemaLocation = schemaLocation.trim();
    }

    private XsdNode build() {
        if (kind == Kind.IMPORT) {
            return new XsdImport(namespace, schemaLocation);
        }
        return new XsdInclude(schemaLocation);
    }

    /** @return the index just after the last import/include/redefine/override child, else 0. */
    private int headerInsertIndex() {
        int index = 0;
        for (int i = 0; i < schema.getChildren().size(); i++) {
            XsdNode child = schema.getChildren().get(i);
            if (child instanceof XsdImport || child instanceof XsdInclude
                    || child instanceof XsdRedefine || child instanceof XsdOverride) {
                index = i + 1;
            }
        }
        return index;
    }

    @Override
    public boolean execute() {
        try {
            if (created == null) {
                created = build();
            }
            insertedIndex = headerInsertIndex();
            schema.addChild(insertedIndex, created);
            editorContext.markNodeDirty(schema);
            return true;
        } catch (Exception e) {
            logger.error("Failed to add {} to schema", kind, e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (created != null) {
                schema.removeChild(created);
                editorContext.markNodeDirty(schema);
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to undo {} add on schema", kind, e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Add xs:" + kind.name().toLowerCase() + " " + schemaLocation;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    /** @return the directive created by this command (for tests); null before execute. */
    public XsdNode getCreated() {
        return created;
    }
}
