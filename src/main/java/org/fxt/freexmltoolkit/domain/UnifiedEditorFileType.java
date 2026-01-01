package org.fxt.freexmltoolkit.domain;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

/**
 * Enum representing the different file types supported by the Unified Editor.
 * Each file type has an associated icon and color for visual distinction in the UI.
 *
 * @since 2.0
 */
public enum UnifiedEditorFileType {

    /**
     * XML files (.xml)
     */
    XML("bi-file-earmark-code", "#007bff", "xml-tab", Set.of("xml")),

    /**
     * XSD Schema files (.xsd)
     */
    XSD("bi-diagram-3", "#28a745", "xsd-tab", Set.of("xsd")),

    /**
     * XSLT Stylesheet files (.xsl, .xslt)
     */
    XSLT("bi-arrow-repeat", "#fd7e14", "xslt-tab", Set.of("xsl", "xslt")),

    /**
     * Schematron files (.sch, .schematron)
     */
    SCHEMATRON("bi-shield-check", "#6f42c1", "schematron-tab", Set.of("sch", "schematron")),

    /**
     * JSON files (.json, .jsonc, .json5)
     */
    JSON("bi-braces", "#f57c00", "json-tab", Set.of("json", "jsonc", "json5"));

    private final String icon;
    private final String color;
    private final String styleClass;
    private final Set<String> extensions;

    UnifiedEditorFileType(String icon, String color, String styleClass, Set<String> extensions) {
        this.icon = icon;
        this.color = color;
        this.styleClass = styleClass;
        this.extensions = extensions;
    }

    /**
     * Gets the Ikonli Bootstrap icon literal for this file type.
     *
     * @return the icon literal (e.g., "bi-file-earmark-code")
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Gets the color associated with this file type (hex format).
     *
     * @return the color in hex format (e.g., "#007bff")
     */
    public String getColor() {
        return color;
    }

    /**
     * Gets the CSS style class for tab styling.
     *
     * @return the style class name (e.g., "xml-tab")
     */
    public String getStyleClass() {
        return styleClass;
    }

    /**
     * Gets the file extensions associated with this file type.
     *
     * @return set of file extensions (without dots)
     */
    public Set<String> getExtensions() {
        return extensions;
    }

    /**
     * Determines the file type from a file based on its extension.
     *
     * @param file the file to check
     * @return the determined file type, or XML as default
     */
    public static UnifiedEditorFileType fromFile(File file) {
        if (file == null || file.getName() == null) {
            return XML;
        }
        return fromFileName(file.getName());
    }

    /**
     * Determines the file type from a filename based on its extension.
     *
     * @param fileName the filename to check
     * @return the determined file type, or XML as default
     */
    public static UnifiedEditorFileType fromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return XML;
        }

        String lowerName = fileName.toLowerCase();
        int lastDot = lowerName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == lowerName.length() - 1) {
            return XML;
        }

        String extension = lowerName.substring(lastDot + 1);

        return Arrays.stream(values())
                .filter(type -> type.extensions.contains(extension))
                .findFirst()
                .orElse(XML);
    }

    /**
     * Gets a human-readable display name for this file type.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return switch (this) {
            case XML -> "XML";
            case XSD -> "XSD Schema";
            case XSLT -> "XSLT Stylesheet";
            case SCHEMATRON -> "Schematron";
            case JSON -> "JSON";
        };
    }

    /**
     * Gets the default file extension for this file type.
     *
     * @return the default extension (without dot)
     */
    public String getDefaultExtension() {
        return switch (this) {
            case XML -> "xml";
            case XSD -> "xsd";
            case XSLT -> "xslt";
            case SCHEMATRON -> "sch";
            case JSON -> "json";
        };
    }
}
