package org.fxt.freexmltoolkit.domain;

import java.io.File;

/**
 * Record representing information about a linked file detected in an XML/XSD/XSLT document.
 * Used by the Unified Editor to discover and manage related files.
 *
 * @param sourceFile     the file containing the reference
 * @param referencePath  the original path/URL from the attribute
 * @param resolvedFile   the resolved file (may be null if not found)
 * @param linkType       the type of link/reference
 * @param isResolved     whether the file was successfully resolved and exists
 * @param namespace      optional namespace for imports (may be null)
 * @since 2.0
 */
public record LinkedFileInfo(
        File sourceFile,
        String referencePath,
        File resolvedFile,
        LinkType linkType,
        boolean isResolved,
        String namespace
) {

    /**
     * Creates a LinkedFileInfo for a resolved file.
     *
     * @param sourceFile    the file containing the reference
     * @param referencePath the original path/URL
     * @param resolvedFile  the resolved file
     * @param linkType      the type of link
     * @return a new LinkedFileInfo instance
     */
    public static LinkedFileInfo resolved(File sourceFile, String referencePath,
                                          File resolvedFile, LinkType linkType) {
        return new LinkedFileInfo(sourceFile, referencePath, resolvedFile, linkType, true, null);
    }

    /**
     * Creates a LinkedFileInfo for a resolved file with namespace.
     *
     * @param sourceFile    the file containing the reference
     * @param referencePath the original path/URL
     * @param resolvedFile  the resolved file
     * @param linkType      the type of link
     * @param namespace     the namespace URI
     * @return a new LinkedFileInfo instance
     */
    public static LinkedFileInfo resolved(File sourceFile, String referencePath,
                                          File resolvedFile, LinkType linkType, String namespace) {
        return new LinkedFileInfo(sourceFile, referencePath, resolvedFile, linkType, true, namespace);
    }

    /**
     * Creates a LinkedFileInfo for an unresolved file.
     *
     * @param sourceFile    the file containing the reference
     * @param referencePath the original path/URL
     * @param linkType      the type of link
     * @return a new LinkedFileInfo instance
     */
    public static LinkedFileInfo unresolved(File sourceFile, String referencePath, LinkType linkType) {
        return new LinkedFileInfo(sourceFile, referencePath, null, linkType, false, null);
    }

    /**
     * Creates a LinkedFileInfo for an unresolved file with namespace.
     *
     * @param sourceFile    the file containing the reference
     * @param referencePath the original path/URL
     * @param linkType      the type of link
     * @param namespace     the namespace URI
     * @return a new LinkedFileInfo instance
     */
    public static LinkedFileInfo unresolved(File sourceFile, String referencePath,
                                            LinkType linkType, String namespace) {
        return new LinkedFileInfo(sourceFile, referencePath, null, linkType, false, namespace);
    }

    /**
     * Gets the file type of the linked file based on the reference path or resolved file.
     *
     * @return the determined file type
     */
    public UnifiedEditorFileType getFileType() {
        if (resolvedFile != null) {
            return UnifiedEditorFileType.fromFile(resolvedFile);
        }
        return UnifiedEditorFileType.fromFileName(referencePath);
    }

    /**
     * Gets a display name for this linked file.
     *
     * @return the display name (filename if resolved, or reference path)
     */
    public String getDisplayName() {
        if (resolvedFile != null) {
            return resolvedFile.getName();
        }
        // Extract filename from reference path
        String name = referencePath;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < name.length() - 1) {
            name = name.substring(lastSlash + 1);
        }
        return name;
    }

    /**
     * Gets a tooltip text describing this linked file.
     *
     * @return the tooltip text
     */
    public String getTooltipText() {
        StringBuilder sb = new StringBuilder();
        sb.append(linkType.getDisplayName());
        if (namespace != null && !namespace.isEmpty()) {
            sb.append("\nNamespace: ").append(namespace);
        }
        sb.append("\nPath: ").append(referencePath);
        if (isResolved && resolvedFile != null) {
            sb.append("\nResolved: ").append(resolvedFile.getAbsolutePath());
        } else {
            sb.append("\nStatus: Not found");
        }
        return sb.toString();
    }

    /**
     * Enum representing the different types of file links/references.
     */
    public enum LinkType {
        /**
         * xsi:schemaLocation attribute in XML
         */
        XSD_SCHEMA_LOCATION("Schema Location"),

        /**
         * xsi:noNamespaceSchemaLocation attribute in XML
         */
        XSD_NO_NAMESPACE_LOCATION("No-Namespace Schema Location"),

        /**
         * xs:import element in XSD
         */
        XSD_IMPORT("XSD Import"),

        /**
         * xs:include element in XSD
         */
        XSD_INCLUDE("XSD Include"),

        /**
         * xs:redefine element in XSD
         */
        XSD_REDEFINE("XSD Redefine"),

        /**
         * xsl:import element in XSLT
         */
        XSLT_IMPORT("XSLT Import"),

        /**
         * xsl:include element in XSLT
         */
        XSLT_INCLUDE("XSLT Include"),

        /**
         * xml-stylesheet processing instruction
         */
        XML_STYLESHEET("XML Stylesheet");

        private final String displayName;

        LinkType(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Gets a human-readable display name for this link type.
         *
         * @return the display name
         */
        public String getDisplayName() {
            return displayName;
        }
    }
}
