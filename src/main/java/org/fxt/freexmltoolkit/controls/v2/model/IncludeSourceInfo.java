package org.fxt.freexmltoolkit.controls.v2.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable value object tracking the source file origin of an XsdNode.
 * Used to track which file a node belongs to in multi-file XSD schemas
 * that use xs:include statements.
 * <p>
 * When an XSD schema includes other schema files via xs:include, this class
 * maintains the information about which file each node originally came from,
 * enabling proper serialization back to the original file structure.
 *
 * @since 2.0
 */
public final class IncludeSourceInfo {

    private final Path sourceFile;
    private final String schemaLocation;
    private final String includeNodeId;
    private final boolean mainSchema;

    /**
     * Creates source info for a node from the main schema file.
     *
     * @param sourceFile the absolute path to the main schema file
     * @return source info indicating main schema origin
     */
    public static IncludeSourceInfo forMainSchema(Path sourceFile) {
        return new IncludeSourceInfo(sourceFile, null, null, true);
    }

    /**
     * Creates source info for a node from an included schema file.
     *
     * @param sourceFile     the absolute path to the included schema file
     * @param schemaLocation the original schemaLocation attribute value
     * @param includeNode    the XsdInclude node that brought this node in
     * @return source info indicating included schema origin
     */
    public static IncludeSourceInfo forIncludedSchema(Path sourceFile, String schemaLocation, XsdInclude includeNode) {
        String includeId = includeNode != null ? includeNode.getId() : null;
        return new IncludeSourceInfo(sourceFile, schemaLocation, includeId, false);
    }

    /**
     * Creates source info for a node from an imported schema file.
     *
     * @param sourceFile     the absolute path to the imported schema file
     * @param schemaLocation the original schemaLocation attribute value
     * @param importNode     the XsdImport node that brought this node in
     * @return source info indicating imported schema origin
     */
    public static IncludeSourceInfo forImportedSchema(Path sourceFile, String schemaLocation, XsdImport importNode) {
        String importId = importNode != null ? importNode.getId() : null;
        return new IncludeSourceInfo(sourceFile, schemaLocation, importId, false);
    }

    /**
     * Private constructor - use factory methods instead.
     *
     * @param sourceFile     the absolute path to the source file
     * @param schemaLocation the original schemaLocation attribute (null for main schema)
     * @param includeNodeId  the ID of the XsdInclude node (null for main schema)
     * @param mainSchema     true if from main schema, false if from include
     */
    private IncludeSourceInfo(Path sourceFile, String schemaLocation, String includeNodeId, boolean mainSchema) {
        this.sourceFile = sourceFile;
        this.schemaLocation = schemaLocation;
        this.includeNodeId = includeNodeId;
        this.mainSchema = mainSchema;
    }

    /**
     * Gets the absolute path to the source file.
     *
     * @return the source file path, or null if not set
     */
    public Path getSourceFile() {
        return sourceFile;
    }

    /**
     * Gets the original schemaLocation attribute value.
     * This is the relative or absolute path as specified in the xs:include element.
     *
     * @return the schema location, or null for main schema nodes
     */
    public String getSchemaLocation() {
        return schemaLocation;
    }

    /**
     * Gets the ID of the XsdInclude node that brought this node into the schema.
     * This can be used to look up the XsdInclude node in the schema tree.
     *
     * @return the include node ID, or null for main schema nodes
     */
    public String getIncludeNodeId() {
        return includeNodeId;
    }

    /**
     * Checks if this node is from the main schema file (not from an include).
     *
     * @return true if from main schema, false if from an included file
     */
    public boolean isMainSchema() {
        return mainSchema;
    }

    /**
     * Checks if this node is from an included file.
     *
     * @return true if from an included file, false if from main schema
     */
    public boolean isFromInclude() {
        return !mainSchema;
    }

    /**
     * Gets the file name (without path) for display purposes.
     *
     * @return the file name, or "unknown" if source file is not set
     */
    public String getFileName() {
        if (sourceFile != null) {
            Path fileName = sourceFile.getFileName();
            return fileName != null ? fileName.toString() : "unknown";
        }
        return "unknown";
    }

    /**
     * Creates a copy of this source info with a different source file.
     * Useful when moving a node to a different file.
     *
     * @param newSourceFile the new source file path
     * @return a new IncludeSourceInfo with the updated file
     */
    public IncludeSourceInfo withSourceFile(Path newSourceFile) {
        return new IncludeSourceInfo(newSourceFile, this.schemaLocation, this.includeNodeId, this.mainSchema);
    }

    /**
     * Creates source info indicating the node should be part of the main schema.
     *
     * @param mainSchemaFile the main schema file path
     * @return a new IncludeSourceInfo for main schema
     */
    public IncludeSourceInfo moveToMainSchema(Path mainSchemaFile) {
        return forMainSchema(mainSchemaFile);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IncludeSourceInfo that = (IncludeSourceInfo) o;
        return mainSchema == that.mainSchema &&
               Objects.equals(sourceFile, that.sourceFile) &&
               Objects.equals(schemaLocation, that.schemaLocation) &&
               Objects.equals(includeNodeId, that.includeNodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile, schemaLocation, includeNodeId, mainSchema);
    }

    @Override
    public String toString() {
        if (mainSchema) {
            return "IncludeSourceInfo[main=" + getFileName() + "]";
        } else {
            return "IncludeSourceInfo[include=" + schemaLocation + ", file=" + getFileName() + "]";
        }
    }
}
