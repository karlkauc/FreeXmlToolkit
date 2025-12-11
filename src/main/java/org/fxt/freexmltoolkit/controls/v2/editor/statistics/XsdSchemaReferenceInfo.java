package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import java.nio.file.Path;

/**
 * Immutable data model representing information about a schema reference
 * (xs:include or xs:import) in an XSD schema.
 * <p>
 * Used by the schema analysis feature to display the status and statistics
 * of all referenced schemas.
 *
 * @since 2.0
 */
public record XsdSchemaReferenceInfo(
        /**
         * The type of schema reference (INCLUDE or IMPORT).
         */
        ReferenceType type,

        /**
         * The original schemaLocation attribute value.
         */
        String schemaLocation,

        /**
         * The target namespace for imports (null for includes).
         */
        String namespace,

        /**
         * The resolved absolute path to the schema file (null if unresolved or remote).
         */
        Path resolvedPath,

        /**
         * Whether the reference was successfully resolved.
         */
        boolean resolved,

        /**
         * Error message if resolution failed (null if resolved).
         */
        String errorMessage,

        /**
         * Number of elements found in the referenced schema.
         */
        int elementCount,

        /**
         * Number of types (complex and simple) found in the referenced schema.
         */
        int typeCount,

        /**
         * Number of groups (group and attributeGroup) found in the referenced schema.
         */
        int groupCount
) {
    /**
     * Type of schema reference.
     */
    public enum ReferenceType {
        /**
         * xs:include - includes schema with same or no target namespace.
         */
        INCLUDE,

        /**
         * xs:import - imports schema with different target namespace.
         */
        IMPORT
    }

    /**
     * Creates a reference info for a successfully resolved include.
     *
     * @param schemaLocation the schema location attribute
     * @param resolvedPath   the resolved absolute path
     * @param elementCount   number of elements
     * @param typeCount      number of types
     * @param groupCount     number of groups
     * @return the reference info
     */
    public static XsdSchemaReferenceInfo forResolvedInclude(
            String schemaLocation, Path resolvedPath,
            int elementCount, int typeCount, int groupCount) {
        return new XsdSchemaReferenceInfo(
                ReferenceType.INCLUDE, schemaLocation, null,
                resolvedPath, true, null,
                elementCount, typeCount, groupCount);
    }

    /**
     * Creates a reference info for a failed include.
     *
     * @param schemaLocation the schema location attribute
     * @param errorMessage   the error message
     * @return the reference info
     */
    public static XsdSchemaReferenceInfo forFailedInclude(String schemaLocation, String errorMessage) {
        return new XsdSchemaReferenceInfo(
                ReferenceType.INCLUDE, schemaLocation, null,
                null, false, errorMessage,
                0, 0, 0);
    }

    /**
     * Creates a reference info for a successfully resolved import.
     *
     * @param schemaLocation the schema location attribute
     * @param namespace      the target namespace
     * @param resolvedPath   the resolved absolute path (may be null for remote)
     * @param elementCount   number of elements
     * @param typeCount      number of types
     * @param groupCount     number of groups
     * @return the reference info
     */
    public static XsdSchemaReferenceInfo forResolvedImport(
            String schemaLocation, String namespace, Path resolvedPath,
            int elementCount, int typeCount, int groupCount) {
        return new XsdSchemaReferenceInfo(
                ReferenceType.IMPORT, schemaLocation, namespace,
                resolvedPath, true, null,
                elementCount, typeCount, groupCount);
    }

    /**
     * Creates a reference info for a failed import.
     *
     * @param schemaLocation the schema location attribute
     * @param namespace      the target namespace
     * @param errorMessage   the error message
     * @return the reference info
     */
    public static XsdSchemaReferenceInfo forFailedImport(
            String schemaLocation, String namespace, String errorMessage) {
        return new XsdSchemaReferenceInfo(
                ReferenceType.IMPORT, schemaLocation, namespace,
                null, false, errorMessage,
                0, 0, 0);
    }

    /**
     * Gets the file name extracted from the schema location.
     *
     * @return the file name, or the full schema location if not a path
     */
    public String getFileName() {
        if (schemaLocation == null || schemaLocation.isEmpty()) {
            return "unknown";
        }
        int lastSlash = Math.max(schemaLocation.lastIndexOf('/'), schemaLocation.lastIndexOf('\\'));
        return lastSlash >= 0 ? schemaLocation.substring(lastSlash + 1) : schemaLocation;
    }

    /**
     * Gets the total count of all components (elements + types + groups).
     *
     * @return total component count
     */
    public int getTotalComponentCount() {
        return elementCount + typeCount + groupCount;
    }

    /**
     * Checks if this is an include reference.
     *
     * @return true if INCLUDE type
     */
    public boolean isInclude() {
        return type == ReferenceType.INCLUDE;
    }

    /**
     * Checks if this is an import reference.
     *
     * @return true if IMPORT type
     */
    public boolean isImport() {
        return type == ReferenceType.IMPORT;
    }

    /**
     * Gets a display string for the type.
     *
     * @return "Include" or "Import"
     */
    public String getTypeDisplayName() {
        return type == ReferenceType.INCLUDE ? "Include" : "Import";
    }

    /**
     * Gets a status display string.
     *
     * @return "Resolved" or "Failed"
     */
    public String getStatusDisplayName() {
        return resolved ? "Resolved" : "Failed";
    }
}
