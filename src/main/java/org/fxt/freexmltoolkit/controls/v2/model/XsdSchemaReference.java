package org.fxt.freexmltoolkit.controls.v2.model;

import java.nio.file.Path;

/**
 * Abstract base class for XSD schema references (import, include, redefine, override).
 * <p>
 * Consolidates common functionality for schema references:
 * - schemaLocation attribute management
 * - Schema resolution state tracking (resolved, resolvedPath, resolutionError)
 * - File name extraction from schema location
 * - Multi-file support properties
 * <p>
 * Subclasses implement:
 * - Specific reference semantics (import vs include vs redefine)
 * - Reference-specific properties (namespace for import, etc.)
 * - Node type identification
 *
 * @since 2.0
 */
public abstract class XsdSchemaReference extends XsdNode {

    protected String schemaLocation; // URI of the referenced schema

    // Multi-file support properties
    protected boolean resolved;          // Whether this reference has been resolved
    protected Path resolvedPath;         // Absolute path after resolution
    protected String resolutionError;    // Error message if resolution failed

    /**
     * Creates a new schema reference with specified node name.
     *
     * @param nodeName the name for this reference node (e.g., "import", "include", "redefine")
     */
    protected XsdSchemaReference(String nodeName) {
        super(nodeName);
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
     * @param schemaLocation the URI of the referenced schema
     */
    public void setSchemaLocation(String schemaLocation) {
        String oldValue = this.schemaLocation;
        this.schemaLocation = schemaLocation;
        pcs.firePropertyChange("schemaLocation", oldValue, schemaLocation);
    }

    /**
     * Checks whether this schema reference has been resolved.
     *
     * @return true if the reference has been resolved
     */
    public boolean isResolved() {
        return resolved;
    }

    /**
     * Gets the resolved absolute path of the referenced schema file.
     *
     * @return the absolute path, or null if not resolved
     */
    public Path getResolvedPath() {
        return resolvedPath;
    }

    /**
     * Sets the resolved absolute path after resolution.
     *
     * @param resolvedPath the absolute path to the referenced schema
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
     * Marks this schema reference as failed to resolve.
     *
     * @param errorMessage the error message describing why resolution failed
     */
    public void markResolutionFailed(String errorMessage) {
        this.resolved = false;
        clearReferencedSchema();
        setResolutionError(errorMessage);
    }

    /**
     * Gets the file name of the referenced schema (extracted from schemaLocation).
     *
     * @return the file name, or "unknown" if schemaLocation is not set
     */
    protected String extractFileName() {
        if (schemaLocation == null || schemaLocation.isEmpty()) {
            return "unknown";
        }
        int lastSlash = Math.max(schemaLocation.lastIndexOf('/'),
                                 schemaLocation.lastIndexOf('\\'));
        return lastSlash >= 0 ? schemaLocation.substring(lastSlash + 1) : schemaLocation;
    }

    /**
     * Gets the referenced schema (import, include, redefine, or override).
     * Subclasses implement to return their specific schema reference type.
     *
     * @return the referenced schema, or null if not yet resolved
     */
    public abstract XsdSchema getReferencedSchema();

    /**
     * Sets the referenced schema and updates resolution state.
     * Subclasses implement to store their specific schema reference type.
     *
     * @param schema the loaded schema content
     */
    public abstract void setReferencedSchema(XsdSchema schema);

    /**
     * Clears the referenced schema (used during resolution failure).
     * Subclasses implement to clear their specific schema reference type.
     */
    protected abstract void clearReferencedSchema();
}
