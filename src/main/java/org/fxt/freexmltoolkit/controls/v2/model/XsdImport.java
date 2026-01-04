package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD import element (xs:import).
 * Import brings components from a different target namespace into the current schema.
 * It allows referencing types, elements, and other components defined in external schemas
 * with different target namespaces.
 *
 * @since 2.0
 */
public class XsdImport extends XsdSchemaReference {

    private String namespace; // Target namespace of the imported schema
    private XsdSchema importedSchema;  // The loaded schema content

    /**
     * Creates a new XSD import element with default name.
     */
    public XsdImport() {
        super("import");
    }

    /**
     * Creates a new XSD import element with namespace.
     *
     * @param namespace the target namespace of the imported schema
     */
    public XsdImport(String namespace) {
        super("import");
        this.namespace = namespace;
    }

    /**
     * Creates a new XSD import element with namespace and schema location.
     *
     * @param namespace      the target namespace of the imported schema
     * @param schemaLocation the URI of the schema to import (optional)
     */
    public XsdImport(String namespace, String schemaLocation) {
        super("import");
        this.namespace = namespace;
        this.schemaLocation = schemaLocation;
    }

    /**
     * Gets the namespace attribute.
     *
     * @return the target namespace, or null
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace attribute.
     *
     * @param namespace the target namespace of the imported schema
     */
    public void setNamespace(String namespace) {
        String oldValue = this.namespace;
        this.namespace = namespace;
        pcs.firePropertyChange("namespace", oldValue, namespace);
    }

    /**
     * Gets the imported schema content (if resolved).
     *
     * @return the imported schema, or null if not yet resolved
     */
    public XsdSchema getImportedSchema() {
        return importedSchema;
    }

    /**
     * Sets the imported schema content after resolution.
     *
     * @param importedSchema the loaded schema content
     */
    public void setImportedSchema(XsdSchema importedSchema) {
        XsdSchema oldValue = this.importedSchema;
        this.importedSchema = importedSchema;
        this.resolved = (importedSchema != null);
        pcs.firePropertyChange("importedSchema", oldValue, importedSchema);
        pcs.firePropertyChange("resolved", !this.resolved, this.resolved);
    }

    /**
     * Gets the file name of the imported schema (extracted from schemaLocation).
     *
     * @return the file name, or "unknown" if schemaLocation is not set
     */
    public String getImportFileName() {
        return extractFileName();
    }

    @Override
    public XsdSchema getReferencedSchema() {
        return importedSchema;
    }

    @Override
    public void setReferencedSchema(XsdSchema schema) {
        setImportedSchema(schema);
    }

    @Override
    protected void clearReferencedSchema() {
        this.importedSchema = null;
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.IMPORT;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdImport copy = new XsdImport(this.namespace, this.schemaLocation);

        // Apply name suffix
        if (suffix != null && !suffix.isEmpty()) {
            copy.setName(getName() + suffix);
        }

        // Copy multi-file support properties
        // Note: importedSchema is NOT copied - it's a reference to the loaded schema
        // and should be resolved independently for the copy
        copy.resolvedPath = this.resolvedPath;
        copy.resolved = this.resolved;
        copy.resolutionError = this.resolutionError;
        // The importedSchema reference can be shared since it's the same schema content
        copy.importedSchema = this.importedSchema;

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
