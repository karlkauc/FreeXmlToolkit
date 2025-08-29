package org.fxt.freexmltoolkit.service;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.w3c.dom.*;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling XSD copy/paste operations with proper fragment serialization
 * Supports system clipboard integration and cross-schema operations
 */
public class XsdClipboardService {

    private static final Logger logger = LogManager.getLogger(XsdClipboardService.class);
    private static final String XSD_CLIPBOARD_FORMAT = "application/x-xsd-fragment";
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    // Internal clipboard for XSD-specific data
    private static XsdClipboardData clipboardData = null;

    /**
     * Data structure for clipboard content
     */
    public static class XsdClipboardData {
        private final String xmlFragment;
        private final XsdNodeInfo nodeInfo;
        private final Map<String, String> namespaceDeclarations;
        private final long timestamp;
        private final String sourceSchema;

        public XsdClipboardData(String xmlFragment, XsdNodeInfo nodeInfo,
                                Map<String, String> namespaceDeclarations, String sourceSchema) {
            this.xmlFragment = xmlFragment;
            this.nodeInfo = nodeInfo;
            this.namespaceDeclarations = namespaceDeclarations;
            this.sourceSchema = sourceSchema;
            this.timestamp = System.currentTimeMillis();
        }

        public String getXmlFragment() {
            return xmlFragment;
        }

        public XsdNodeInfo getNodeInfo() {
            return nodeInfo;
        }

        public Map<String, String> getNamespaceDeclarations() {
            return namespaceDeclarations;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSourceSchema() {
            return sourceSchema;
        }
    }

    /**
     * Copy a node and its subtree to clipboard
     */
    public static boolean copyNode(XsdNodeInfo nodeInfo, Document sourceDocument) {
        try {
            logger.info("Copying node: {} ({})", nodeInfo.name(), nodeInfo.nodeType());

            // Find the DOM element corresponding to this node
            Element element = findElementByPath(sourceDocument, nodeInfo.xpath());
            if (element == null) {
                logger.error("Could not find DOM element for xpath: {}", nodeInfo.xpath());
                return false;
            }

            // Serialize the element and its subtree
            String xmlFragment = serializeElement(element);
            if (xmlFragment == null) {
                return false;
            }

            // Collect namespace declarations from ancestors
            Map<String, String> namespaces = collectNamespaceDeclarations(element);

            // Get source schema identifier (for cross-schema paste)
            String sourceSchema = getSchemaIdentifier(sourceDocument);

            // Store in internal clipboard
            clipboardData = new XsdClipboardData(xmlFragment, nodeInfo, namespaces, sourceSchema);

            // Also put in system clipboard for external use
            putInSystemClipboard(xmlFragment, nodeInfo);

            logger.info("Successfully copied node to clipboard: {}", nodeInfo.name());
            return true;

        } catch (Exception e) {
            logger.error("Error copying node to clipboard", e);
            return false;
        }
    }

    /**
     * Check if there's content available for pasting
     */
    public static boolean hasClipboardContent() {
        return clipboardData != null || hasSystemClipboardContent();
    }

    /**
     * Get the current clipboard data
     */
    public static XsdClipboardData getClipboardData() {
        return clipboardData;
    }

    /**
     * Clear the clipboard
     */
    public static void clearClipboard() {
        clipboardData = null;
        Clipboard.getSystemClipboard().clear();
        logger.debug("Clipboard cleared");
    }

    /**
     * Get description of what's in clipboard for UI display
     */
    public static String getClipboardDescription() {
        if (clipboardData == null) {
            return "Clipboard empty";
        }

        XsdNodeInfo node = clipboardData.getNodeInfo();
        String typeName = getNodeTypeDisplayName(node.nodeType());
        long ageMinutes = (System.currentTimeMillis() - clipboardData.getTimestamp()) / (1000 * 60);

        if (ageMinutes < 1) {
            return String.format("%s '%s' (just now)", typeName, node.name());
        } else if (ageMinutes < 60) {
            return String.format("%s '%s' (%d min ago)", typeName, node.name(), ageMinutes);
        } else {
            return String.format("%s '%s' (%d hours ago)", typeName, node.name(), ageMinutes / 60);
        }
    }

    /**
     * Serialize an element to XML string
     */
    private static String serializeElement(Element element) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(writer));

            return writer.toString();

        } catch (Exception e) {
            logger.error("Error serializing element", e);
            return null;
        }
    }

    /**
     * Collect namespace declarations from element and ancestors
     */
    private static Map<String, String> collectNamespaceDeclarations(Element element) {
        Map<String, String> namespaces = new HashMap<>();

        Node current = element;
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element elem = (Element) current;
            NamedNodeMap attributes = elem.getAttributes();

            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                String name = attr.getName();

                if (name.equals("xmlns") || name.startsWith("xmlns:")) {
                    namespaces.putIfAbsent(name, attr.getValue());
                }
            }

            current = current.getParentNode();
        }

        return namespaces;
    }

    /**
     * Get schema identifier for cross-schema operations
     */
    private static String getSchemaIdentifier(Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            String targetNamespace = root.getAttribute("targetNamespace");
            if (targetNamespace != null && !targetNamespace.isEmpty()) {
                return targetNamespace;
            }
        }
        return "unknown-schema";
    }

    /**
     * Put content in system clipboard
     */
    private static void putInSystemClipboard(String xmlFragment, XsdNodeInfo nodeInfo) {
        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();

            // Put XML as plain text
            content.putString(xmlFragment);

            // Put as HTML for rich applications
            String htmlContent = String.format(
                    "<div data-xsd-type='%s' data-xsd-name='%s'>%s</div>",
                    nodeInfo.nodeType().name(),
                    nodeInfo.name(),
                    xmlFragment.replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            );
            content.putHtml(htmlContent);

            clipboard.setContent(content);

        } catch (Exception e) {
            logger.warn("Could not put content in system clipboard", e);
        }
    }

    /**
     * Check if system clipboard has XSD content
     */
    private static boolean hasSystemClipboardContent() {
        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            return clipboard.hasString() && clipboard.getString().contains("xs:");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Find element by XPath in document
     */
    private static Element findElementByPath(Document document, String xpath) {
        // This is a simplified implementation
        // In a real implementation, we'd use proper XPath evaluation
        return findElementBySimplePath(document.getDocumentElement(), xpath);
    }

    /**
     * Simple path resolution for XSD structure
     */
    private static Element findElementBySimplePath(Element root, String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return root;
        }

        // Remove leading slash and split path
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = cleanPath.split("/");

        Element current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;

            // Look for child element with matching name
            NodeList children = current.getChildNodes();
            boolean found = false;

            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    String name = childElement.getAttribute("name");

                    if (part.equals(name) || part.equals(childElement.getTagName())) {
                        current = childElement;
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                logger.warn("Could not find path component: {} in path: {}", part, path);
                return null;
            }
        }

        return current;
    }

    /**
     * Get display name for node type
     */
    private static String getNodeTypeDisplayName(XsdNodeInfo.NodeType nodeType) {
        return switch (nodeType) {
            case ELEMENT -> "Element";
            case ATTRIBUTE -> "Attribute";
            case SEQUENCE -> "Sequence";
            case CHOICE -> "Choice";
            case ANY -> "Any";
            case SIMPLE_TYPE -> "SimpleType";
            case COMPLEX_TYPE -> "ComplexType";
            case SCHEMA -> "Schema";
        };
    }
}