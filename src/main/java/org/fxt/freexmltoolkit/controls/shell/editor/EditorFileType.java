package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.stage.FileChooser;

/**
 * Classifies a file opened in the Unified editor host by its extension, so the
 * editor (syntax, IntelliSense) and the inspector can adapt to the content.
 */
public enum EditorFileType {
    XML("XML", "bi-code-slash", "#1373d9", "xml", "wsdl"),
    XSD("XSD", "bi-diagram-3", "#2f9e44", "xsd"),
    XSLT("XSLT", "bi-arrow-repeat", "#f08c00", "xsl", "xslt"),
    SCHEMATRON("Schematron", "bi-check2-square", "#e8590c", "sch", "schematron"),
    JSON("JSON", "bi-braces", "#1098ad", "json", "jsonc", "json5"),
    XQUERY("XQuery", "bi-code-square", "#6f42c1", "xq", "xquery", "xqm", "xqy"),
    XPATH("XPath", "bi-slash-square", "#d63384", "xpath"),
    XPROC("XProc", "bi-diagram-2", "#0ca678", "xpl", "xproc"),
    HTML("HTML", "bi-filetype-html", "#e34c26", "html", "htm", "xhtml"),
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

    /** @return the primary (first) file extension for this type, or {@code "txt"} if none. */
    public String primaryExtension() {
        return extensions.isEmpty() ? "txt" : extensions.get(0);
    }

    /**
     * Minimal starter content for a brand-new document of this type, used by the
     * "New File" flow when neither a schema nor a template was chosen.
     *
     * @return a sensible boilerplate skeleton (empty string for {@link #OTHER})
     */
    public String defaultContent() {
        return switch (this) {
            case XML -> "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
            case XSD -> """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                               elementFormDefault="qualified">

                    </xs:schema>
                    """;
            case XSLT -> """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <xsl:stylesheet version="3.0"
                                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                    xmlns:xs="http://www.w3.org/2001/XMLSchema"
                                    exclude-result-prefixes="xs">

                        <xsl:output method="xml" encoding="UTF-8" indent="yes"/>

                        <xsl:template match="/">

                        </xsl:template>

                    </xsl:stylesheet>
                    """;
            case SCHEMATRON -> """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <schema xmlns="http://purl.oclc.org/dsdl/schematron">

                        <pattern>
                            <rule context="/">
                                <!-- <assert test="...">message</assert> -->
                            </rule>
                        </pattern>

                    </schema>
                    """;
            case JSON -> "{\n}\n";
            case XQUERY -> "xquery version \"3.1\";\n\n";
            case XPATH -> "";
            case XPROC -> """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

                        <p:input port="source"/>
                        <p:output port="result"/>

                        <p:identity/>

                    </p:declare-step>
                    """;
            case HTML -> """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title></title>
                    </head>
                    <body>

                    </body>
                    </html>
                    """;
            case OTHER -> "";
        };
    }

    /**
     * Every file extension (with leading dot, e.g. {@code ".json"}) that the editor host
     * can open, derived from the typed entries of this enum. This is the single source of
     * truth for "what may be dropped onto / opened in the shell", so a newly supported type
     * automatically becomes droppable too.
     *
     * @return the lowercase, dot-prefixed extensions of every type except {@link #OTHER}
     */
    public static List<String> openableExtensions() {
        return OPENABLE_EXTENSIONS;
    }

    /**
     * The same set as {@link #openableExtensions()} as file-chooser / file-search globs
     * (e.g. {@code "*.json"}).
     *
     * @return one {@code "*.<ext>"} glob per openable extension, in enum order
     */
    public static List<String> openableGlobs() {
        return OPENABLE_GLOBS;
    }

    /**
     * Builds the extension filters for an "Open"-style {@link FileChooser}: an
     * "All supported files" filter first, then one filter per type (in enum order) and
     * finally "All files". Every filter is derived from this enum, so a newly supported
     * type shows up in every file chooser without further registration.
     *
     * @return a fresh, mutable list of filters (a filter cannot be shared between choosers)
     */
    public static List<FileChooser.ExtensionFilter> fileChooserFilters() {
        List<FileChooser.ExtensionFilter> filters = new ArrayList<>();
        filters.add(new FileChooser.ExtensionFilter("All supported files", OPENABLE_GLOBS));
        for (EditorFileType type : values()) {
            if (type != OTHER) {
                filters.add(new FileChooser.ExtensionFilter(
                        type.label + " (" + String.join(", ", type.globs()) + ")", type.globs()));
            }
        }
        filters.add(new FileChooser.ExtensionFilter("All files", "*.*"));
        return filters;
    }

    private List<String> globs() {
        return extensions.stream().map(ext -> "*." + ext).toList();
    }

    private static final List<String> OPENABLE_EXTENSIONS = java.util.Arrays.stream(values())
            .filter(type -> type != OTHER)
            .flatMap(type -> type.extensions.stream())
            .map(ext -> "." + ext)
            .toList();

    private static final List<String> OPENABLE_GLOBS = OPENABLE_EXTENSIONS.stream()
            .map(ext -> "*" + ext)
            .toList();

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
