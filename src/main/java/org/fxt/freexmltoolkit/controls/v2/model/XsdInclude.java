package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD include element (xs:include).
 * Include brings components from another schema document with the same target namespace
 * into the current schema. It allows splitting a large schema into multiple files
 * while maintaining the same target namespace.
 *
 * Unlike import (which is for different namespaces), include is for schemas with the
 * same target namespace or no target namespace.
 *
 * @since 2.0
 */
public class XsdInclude extends XsdSchemaReference {

    private XsdSchema includedSchema;  // The loaded schema content

    /**
     * Creates a new XSD include element with default name.
     */
    public XsdInclude() {
        super("include");
    }

    /**
     * Creates a new XSD include element with schema location.
     *
     * @param schemaLocation the URI of the schema to include
     */
    public XsdInclude(String schemaLocation) {
        super("include");
        this.schemaLocation = schemaLocation;
    }

    /**
     * Gets the included schema content (if resolved).
     *
     * @return the included schema, or null if not yet resolved
     */
    public XsdSchema getIncludedSchema() {
        return includedSchema;
    }

    /**
     * Sets the included schema content after resolution.
     *
     * @param includedSchema the loaded schema content
     */
    public void setIncludedSchema(XsdSchema includedSchema) {
        XsdSchema oldValue = this.includedSchema;
        this.includedSchema = includedSchema;
        this.resolved = (includedSchema != null);
        pcs.firePropertyChange("includedSchema", oldValue, includedSchema);
        pcs.firePropertyChange("resolved", !this.resolved, this.resolved);
    }

    /**
     * Gets the file name of the included schema (extracted from schemaLocation).
     *
     * @return the file name, or "unknown" if schemaLocation is not set
     */
    public String getIncludeFileName() {
        return extractFileName();
    }

    @Override
    public XsdSchema getReferencedSchema() {
        return includedSchema;
    }

    @Override
    public void setReferencedSchema(XsdSchema schema) {
        setIncludedSchema(schema);
    }

    @Override
    protected void clearReferencedSchema() {
        this.includedSchema = null;
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.INCLUDE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdInclude copy = new XsdInclude(this.schemaLocation);

        // Apply name suffix
        if (suffix != null && !suffix.isEmpty()) {
            copy.setName(getName() + suffix);
        }

        // Copy multi-file support properties
        // Note: includedSchema is NOT copied - it's a reference to the loaded schema
        // and should be resolved independently for the copy
        copy.resolvedPath = this.resolvedPath;
        copy.resolved = this.resolved;
        copy.resolutionError = this.resolutionError;
        // The includedSchema reference can be shared since it's the same schema content
        copy.includedSchema = this.includedSchema;

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
