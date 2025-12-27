package org.fxt.freexmltoolkit.controls.unified;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;

import java.io.File;

/**
 * Factory for creating Unified Editor tabs based on file type.
 * <p>
 * This factory provides a centralized way to create the appropriate tab type
 * based on the file being opened or the type of new file being created.
 * <p>
 * Each tab type provides full-featured editing:
 * <ul>
 *   <li>XML - Text/Graphic views with sidebar</li>
 *   <li>XSD - Text/Graphic views with XsdGraphView</li>
 *   <li>XSLT - XSLT editor with live transform and output preview</li>
 *   <li>Schematron - Multi-tab editor (Code, Visual Builder, Test, Documentation)</li>
 * </ul>
 *
 * @since 2.0
 */
public class UnifiedEditorTabFactory {

    private static final Logger logger = LogManager.getLogger(UnifiedEditorTabFactory.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private UnifiedEditorTabFactory() {
        // Factory class - no instances
    }

    /**
     * Creates a new tab for the specified file.
     * <p>
     * The file type is automatically detected from the file extension.
     *
     * @param file the file to open (must not be null)
     * @return the appropriate tab for the file type
     * @throws IllegalArgumentException if file is null or type cannot be determined
     */
    public static AbstractUnifiedEditorTab createTab(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        UnifiedEditorFileType fileType = UnifiedEditorFileType.fromFile(file);
        return createTab(file, fileType);
    }

    /**
     * Creates a new tab for the specified file and type.
     *
     * @param file the file to edit (can be null for new files)
     * @param type the type of file
     * @return the appropriate tab for the file type
     */
    public static AbstractUnifiedEditorTab createTab(File file, UnifiedEditorFileType type) {
        logger.debug("Creating tab for type: {} file: {}", type, file != null ? file.getName() : "new");

        return switch (type) {
            case XML -> new XmlUnifiedTab(file);
            case XSD -> new XsdUnifiedTab(file);
            case XSLT -> new XsltUnifiedTab(file);
            case SCHEMATRON -> new SchematronUnifiedTab(file);
        };
    }

    /**
     * Creates a new (unsaved) tab for the specified type.
     *
     * @param type the type of new file to create
     * @return a new tab with template content
     */
    public static AbstractUnifiedEditorTab createNewTab(UnifiedEditorFileType type) {
        return createTab(null, type);
    }

    /**
     * Creates an XML tab.
     *
     * @param file the file to open (or null for new file)
     * @return XML unified tab
     */
    public static XmlUnifiedTab createXmlTab(File file) {
        return new XmlUnifiedTab(file);
    }

    /**
     * Creates an XSD tab.
     *
     * @param file the file to open (or null for new file)
     * @return XSD unified tab
     */
    public static XsdUnifiedTab createXsdTab(File file) {
        return new XsdUnifiedTab(file);
    }

    /**
     * Creates an XSLT tab.
     *
     * @param file the file to open (or null for new file)
     * @return XSLT unified tab
     */
    public static XsltUnifiedTab createXsltTab(File file) {
        return new XsltUnifiedTab(file);
    }

    /**
     * Creates a Schematron tab.
     *
     * @param file the file to open (or null for new file)
     * @return Schematron unified tab
     */
    public static SchematronUnifiedTab createSchematronTab(File file) {
        return new SchematronUnifiedTab(file);
    }

    /**
     * Checks if a file is supported by the Unified Editor.
     *
     * @param file the file to check
     * @return true if the file type is supported
     */
    public static boolean isSupported(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }

        String name = file.getName().toLowerCase();
        return name.endsWith(".xml") ||
                name.endsWith(".xsd") ||
                name.endsWith(".xsl") ||
                name.endsWith(".xslt") ||
                name.endsWith(".sch") ||
                name.endsWith(".schematron");
    }

    /**
     * Gets the file type for a file.
     *
     * @param file the file to check
     * @return the file type, or null if not supported
     */
    public static UnifiedEditorFileType getFileType(File file) {
        if (file == null) {
            return null;
        }

        try {
            return UnifiedEditorFileType.fromFile(file);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Gets the file type for a filename.
     *
     * @param filename the filename to check
     * @return the file type, or null if not supported
     */
    public static UnifiedEditorFileType getFileType(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        String lower = filename.toLowerCase();
        if (lower.endsWith(".xml")) {
            return UnifiedEditorFileType.XML;
        } else if (lower.endsWith(".xsd")) {
            return UnifiedEditorFileType.XSD;
        } else if (lower.endsWith(".xsl") || lower.endsWith(".xslt")) {
            return UnifiedEditorFileType.XSLT;
        } else if (lower.endsWith(".sch") || lower.endsWith(".schematron")) {
            return UnifiedEditorFileType.SCHEMATRON;
        }

        return null;
    }
}
