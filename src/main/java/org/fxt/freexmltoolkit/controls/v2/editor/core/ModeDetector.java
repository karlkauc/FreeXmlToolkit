package org.fxt.freexmltoolkit.controls.v2.editor.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for detecting editor mode based on XML content.
 * Analyzes the root element and namespace to determine the appropriate editor mode.
 */
public class ModeDetector {

    private static final Logger logger = LogManager.getLogger(ModeDetector.class);

    // Patterns for detecting document types
    private static final Pattern ROOT_ELEMENT_PATTERN = Pattern.compile(
        "<\\s*([a-zA-Z_][\\w:.-]*)(?:\\s+[^>]*)?>",
        Pattern.DOTALL
    );

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(
        "xmlns(?::([a-zA-Z_][\\w.-]*))?\\s*=\\s*['\"]([^'\"]+)['\"]"
    );

    // Known namespace URIs
    private static final String XSLT_NS = "http://www.w3.org/1999/XSL/Transform";
    private static final String FO_NS = "http://www.w3.org/1999/XSL/Format";
    private static final String SCHEMATRON_NS = "http://purl.oclc.org/dsdl/schematron";
    private static final String ISO_SCHEMATRON_NS = "http://www.ascc.net/xml/schematron";

    /**
     * Detects the editor mode based on XML content.
     *
     * @param xmlContent the XML content to analyze
     * @param hasXsd whether an XSD schema is loaded
     * @return the detected editor mode
     */
    public static EditorMode detectMode(String xmlContent, boolean hasXsd) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return hasXsd ? EditorMode.XML_WITH_XSD : EditorMode.XML_WITHOUT_XSD;
        }

        // Remove XML declaration and comments for cleaner parsing
        String cleaned = removeXmlDeclarationAndComments(xmlContent);

        // Extract root element name
        String rootElement = extractRootElement(cleaned);
        if (rootElement == null) {
            logger.debug("Could not detect root element, defaulting to XML mode");
            return hasXsd ? EditorMode.XML_WITH_XSD : EditorMode.XML_WITHOUT_XSD;
        }

        logger.debug("Detected root element: {}", rootElement);

        // Check for XSLT
        if (isXsltDocument(rootElement, cleaned)) {
            logger.info("Detected XSLT document");
            return EditorMode.XSLT;
        }

        // Check for XSL-FO
        if (isXslFoDocument(rootElement, cleaned)) {
            logger.info("Detected XSL-FO document");
            return EditorMode.XSL_FO;
        }

        // Check for Schematron
        if (isSchematronDocument(rootElement, cleaned)) {
            logger.info("Detected Schematron document");
            return EditorMode.SCHEMATRON;
        }

        // Default to XML mode (with or without XSD)
        EditorMode mode = hasXsd ? EditorMode.XML_WITH_XSD : EditorMode.XML_WITHOUT_XSD;
        logger.debug("Detected standard XML document, mode: {}", mode);
        return mode;
    }

    /**
     * Removes XML declaration and comments from content.
     */
    private static String removeXmlDeclarationAndComments(String content) {
        // Remove XML declaration
        String result = content.replaceFirst("<\\?xml[^?]*\\?>", "");

        // Remove comments
        result = result.replaceAll("<!--.*?-->", "");

        return result.trim();
    }

    /**
     * Extracts the root element name from XML content.
     */
    private static String extractRootElement(String content) {
        Matcher matcher = ROOT_ELEMENT_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Checks if the document is an XSLT stylesheet.
     */
    private static boolean isXsltDocument(String rootElement, String content) {
        // Check root element name
        if (rootElement.endsWith(":stylesheet") || rootElement.endsWith(":transform") ||
            rootElement.equals("stylesheet") || rootElement.equals("transform")) {

            // Verify namespace
            String namespace = findNamespaceForPrefix(getPrefix(rootElement), content);
            if (namespace != null && namespace.equals(XSLT_NS)) {
                return true;
            }

            // Also check if xsl namespace is declared (common case)
            if (content.contains("xmlns:xsl") && content.contains(XSLT_NS)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the document is an XSL-FO document.
     */
    private static boolean isXslFoDocument(String rootElement, String content) {
        // Check root element name
        if (rootElement.endsWith(":root") || rootElement.equals("root")) {

            // Verify namespace
            String namespace = findNamespaceForPrefix(getPrefix(rootElement), content);
            if (namespace != null && namespace.equals(FO_NS)) {
                return true;
            }

            // Also check if fo namespace is declared
            if (content.contains("xmlns:fo") && content.contains(FO_NS)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the document is a Schematron document.
     */
    private static boolean isSchematronDocument(String rootElement, String content) {
        // Check root element name
        if (rootElement.endsWith(":schema") || rootElement.equals("schema")) {

            // Verify namespace
            String namespace = findNamespaceForPrefix(getPrefix(rootElement), content);
            if (namespace != null &&
                (namespace.equals(SCHEMATRON_NS) || namespace.equals(ISO_SCHEMATRON_NS))) {
                return true;
            }

            // Also check if sch namespace is declared
            if (content.contains("xmlns:sch") &&
                (content.contains(SCHEMATRON_NS) || content.contains(ISO_SCHEMATRON_NS))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the namespace prefix from a qualified name.
     */
    private static String getPrefix(String qualifiedName) {
        int colonIndex = qualifiedName.indexOf(':');
        if (colonIndex > 0) {
            return qualifiedName.substring(0, colonIndex);
        }
        return ""; // Default namespace
    }

    /**
     * Finds the namespace URI for a given prefix.
     */
    private static String findNamespaceForPrefix(String prefix, String content) {
        // Build the namespace declaration pattern
        String pattern;
        if (prefix.isEmpty()) {
            pattern = "xmlns\\s*=\\s*['\"]([^'\"]+)['\"]";
        } else {
            pattern = "xmlns:" + Pattern.quote(prefix) + "\\s*=\\s*['\"]([^'\"]+)['\"]";
        }

        Pattern nsPattern = Pattern.compile(pattern);
        Matcher matcher = nsPattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Checks if the given text represents a specific document type.
     * Convenience method for external callers.
     *
     * @param xmlContent the XML content
     * @return the simplified document type string
     */
    public static String detectDocumentType(String xmlContent) {
        EditorMode mode = detectMode(xmlContent, false);
        return switch (mode) {
            case XSLT -> "XSLT";
            case XSL_FO -> "XSL-FO";
            case SCHEMATRON -> "Schematron";
            case XML_WITH_XSD -> "XML with XSD";
            case XML_WITHOUT_XSD -> "XML";
        };
    }
}
