package org.fxt.freexmltoolkit.service.telemetry;

import java.nio.file.Path;
import java.util.Locale;

import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;

/**
 * Coarse document kind reported with telemetry events ({@code doc_kind}).
 *
 * <p>This is the <b>only</b> information about a file that ever leaves the app: it is
 * derived from the file extension locally, and the file name / path itself is never
 * sent.
 */
public enum DocKind {
    XML, XSD, XSLT, SCHEMATRON, JSON, OTHER;

    /** Wire value as defined by the ingest API ({@code xml|xsd|xslt|schematron|json|other}). */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Maps a file extension (with or without leading dot, case-insensitive).
     * <ul>
     *   <li>{@code xml}, {@code xpl}, {@code xhtml}, {@code fo}, {@code wsdl}, {@code svg} → XML</li>
     *   <li>{@code xsd} → XSD</li>
     *   <li>{@code xsl}, {@code xslt} → XSLT</li>
     *   <li>{@code sch}, {@code schematron} → SCHEMATRON</li>
     *   <li>{@code json}, {@code jsonc}, {@code json5} → JSON</li>
     *   <li>anything else (incl. blank) → OTHER</li>
     * </ul>
     *
     * @return the kind, {@code null} only when {@code extension} is null
     */
    public static DocKind fromExtension(String extension) {
        if (extension == null) {
            return null;
        }
        String ext = extension.strip().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        return switch (ext) {
            case "xml", "xpl", "xhtml", "fo", "wsdl", "svg" -> XML;
            case "xsd" -> XSD;
            case "xsl", "xslt" -> XSLT;
            case "sch", "schematron" -> SCHEMATRON;
            case "json", "jsonc", "json5" -> JSON;
            default -> OTHER;
        };
    }

    /** Maps a file name by its extension; a name without extension yields OTHER, null yields null. */
    public static DocKind fromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        int sep = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (dot < 0 || dot < sep) {
            return OTHER;
        }
        return fromExtension(fileName.substring(dot + 1));
    }

    /** Maps a path by its file name's extension; null yields null (e.g. an unsaved document). */
    public static DocKind fromPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        return fromFileName(path.getFileName().toString());
    }

    /** Maps the editor's file type; null yields null. */
    public static DocKind from(UnifiedEditorFileType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case XML -> XML;
            case XSD -> XSD;
            case XSLT -> XSLT;
            case SCHEMATRON -> SCHEMATRON;
            case JSON -> JSON;
        };
    }
}
