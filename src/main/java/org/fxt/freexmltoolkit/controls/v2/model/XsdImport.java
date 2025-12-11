package org.fxt.freexmltoolkit.controls.v2.model;

import java.nio.file.Path;

/**
 * Represents an XSD import element (xs:import).
 * Import brings components from a different target namespace into the current schema.
 * It allows referencing types, elements, and other components defined in external schemas
 * with different target namespaces.
 *
 * @since 2.0
 */
public class XsdImport extends XsdNode {

    private String namespace; // Target namespace of the imported schema
    private String schemaLocation; // Optional URI of the schema to import

    // Multi-file support properties (matching XsdInclude)
    private XsdSchema importedSchema;  // The loaded schema content
    private boolean resolved;          // Whether this import has been resolved
    private Path resolvedPath;         // Absolute path after resolution
    private String resolutionError;    // Error message if resolution failed

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
     * @param schemaLocation the URI of the schema to import (optional)
     */
    public void setSchemaLocation(String schemaLocation) {
        String oldValue = this.schemaLocation;
        this.schemaLocation = schemaLocation;
        pcs.firePropertyChange("schemaLocation", oldValue, schemaLocation);
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
     * Checks whether this import has been resolved (schema loaded).
     *
     * @return true if the import has been resolved
     */
    public boolean isResolved() {
        return resolved;
    }

    /**
     * Gets the resolved absolute path of the imported schema file.
     *
     * @return the absolute path, or null if not resolved
     */
    public Path getResolvedPath() {
        return resolvedPath;
    }

    /**
     * Sets the resolved absolute path after resolution.
     *
     * @param resolvedPath the absolute path to the imported schema
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
     * Marks this import as failed to resolve.
     *
     * @param errorMessage the error message describing why resolution failed
     */
    public void markResolutionFailed(String errorMessage) {
        this.resolved = false;
        this.importedSchema = null;
        setResolutionError(errorMessage);
    }

    /**
     * Gets the file name of the imported schema (extracted from schemaLocation).
     *
     * @return the file name, or "unknown" if schemaLocation is not set
     */
    public String getImportFileName() {
        if (schemaLocation == null || schemaLocation.isEmpty()) {
            return "unknown";
        }
        int lastSlash = Math.max(schemaLocation.lastIndexOf('/'), schemaLocation.lastIndexOf('\\'));
        return lastSlash >= 0 ? schemaLocation.substring(lastSlash + 1) : schemaLocation;
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
