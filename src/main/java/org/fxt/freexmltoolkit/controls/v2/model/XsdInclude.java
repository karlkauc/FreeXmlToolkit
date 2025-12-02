package org.fxt.freexmltoolkit.controls.v2.model;

import java.nio.file.Path;

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
public class XsdInclude extends XsdNode {

    private String schemaLocation; // URI of the schema to include

    // Multi-file support properties
    private XsdSchema includedSchema;  // The loaded schema content
    private boolean resolved;          // Whether this include has been resolved
    private Path resolvedPath;         // Absolute path after resolution
    private String resolutionError;    // Error message if resolution failed

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
     * Gets the schemaLocation attribute.
     *
     * @return the schema location URI, or null
     */
    public String getSchemaLocation() {
        return schemaLocation;
    }

    /**
     * Sets the schemaLocation attribute.
     *
     * @param schemaLocation the URI of the schema to include
     */
    public void setSchemaLocation(String schemaLocation) {
        String oldValue = this.schemaLocation;
        this.schemaLocation = schemaLocation;
        pcs.firePropertyChange("schemaLocation", oldValue, schemaLocation);
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
     * Checks whether this include has been resolved (schema loaded).
     *
     * @return true if the include has been resolved
     */
    public boolean isResolved() {
        return resolved;
    }

    /**
     * Gets the resolved absolute path of the included schema file.
     *
     * @return the absolute path, or null if not resolved
     */
    public Path getResolvedPath() {
        return resolvedPath;
    }

    /**
     * Sets the resolved absolute path after resolution.
     *
     * @param resolvedPath the absolute path to the included schema
     */
    public void setResolvedPath(Path resolvedPath) {
        Path oldValue = this.resolvedPath;
        this.resolvedPath = resolvedPath;
        pcs.firePropertyChange("resolvedPath", oldValue, resolvedPath);
    }

    /**
     * Gets the resolution error message (if resolution failed).
     *
     * @return the error message, or null if no error
     */
    public String getResolutionError() {
        return resolutionError;
    }

    /**
     * Sets the resolution error message.
     *
     * @param resolutionError the error message
     */
    public void setResolutionError(String resolutionError) {
        String oldValue = this.resolutionError;
        this.resolutionError = resolutionError;
        pcs.firePropertyChange("resolutionError", oldValue, resolutionError);
    }

    /**
     * Marks this include as failed to resolve.
     *
     * @param errorMessage the error message describing why resolution failed
     */
    public void markResolutionFailed(String errorMessage) {
        this.resolved = false;
        this.includedSchema = null;
        setResolutionError(errorMessage);
    }

    /**
     * Gets the file name of the included schema (extracted from schemaLocation).
     *
     * @return the file name, or "unknown" if schemaLocation is not set
     */
    public String getIncludeFileName() {
        if (schemaLocation == null || schemaLocation.isEmpty()) {
            return "unknown";
        }
        int lastSlash = Math.max(schemaLocation.lastIndexOf('/'), schemaLocation.lastIndexOf('\\'));
        return lastSlash >= 0 ? schemaLocation.substring(lastSlash + 1) : schemaLocation;
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
