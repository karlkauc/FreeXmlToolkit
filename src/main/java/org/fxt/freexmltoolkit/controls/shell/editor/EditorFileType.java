package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.Locale;

/**
 * Classifies a file opened in the Unified editor host by its extension, so the
 * editor (syntax, IntelliSense) and the inspector can adapt to the content.
 */
public enum EditorFileType {
    XML("XML", "bi-code-slash", "#3b5bdb", "xml"),
    XSD("XSD", "bi-diagram-3", "#2f9e44", "xsd"),
    XSLT("XSLT", "bi-arrow-repeat", "#f08c00", "xsl", "xslt"),
    SCHEMATRON("Schematron", "bi-check2-square", "#e8590c", "sch", "schematron"),
    JSON("JSON", "bi-braces", "#1098ad", "json"),
    OTHER("Text", "bi-file-earmark-text", "#8a93a0");

    private final String label;
    private final String icon;
    private final String color;
    private final List<String> extensions;

    EditorFileType(String label, String icon, String color, String... extensions) {
        this.label = label;
        this.icon = icon;
        this.color = color;
        this.extensions = List.of(extensions);
    }

    /** @return the human-readable type label (shown as a badge). */
    public String label() {
        return label;
    }

    /** @return the Bootstrap icon literal for this type. */
    public String icon() {
        return icon;
    }

    /** @return the per-type icon color (hex), used to tint file icons in the Explorer. */
    public String color() {
        return color;
    }

    /** @return the lowercase file extensions mapped to this type. */
    public List<String> extensions() {
        return extensions;
    }

    /**
     * Classifies a file by its name's extension (case-insensitive).
     *
     * @param fileName the file name (may be {@code null})
     * @return the matching type, or {@link #OTHER} if unknown / none
     */
    public static EditorFileType fromFileName(String fileName) {
        if (fileName == null) {
            return OTHER;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return OTHER;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (EditorFileType type : values()) {
            if (type.extensions.contains(ext)) {
                return type;
            }
        }
        return OTHER;
    }
}
