package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;

/**
 * Parses XML text/files into XML model.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Parse from string, file, or InputStream</li>
 *   <li>Namespace-aware parsing</li>
 *   <li>Preserves document encoding and standalone</li>
 *   <li>Converts all node types (elements, text, comments, etc.)</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * XmlParser parser = new XmlParser();
 * XmlDocument doc = parser.parse("<root><child/></root>");
 *
 * // From file
 * XmlDocument doc2 = parser.parseFile("input.xml");
 * }</pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlParser {

    /**
     * Whether to parse with namespace awareness.
     */
    private boolean namespaceAware = true;

    /**
     * Whether to validate against DTD/Schema.
     */
    private boolean validating = false;

    /**
     * Constructs a new XmlParser with default settings.
     */
    public XmlParser() {
    }

    /**
     * Constructs a new XmlParser with custom settings.
     *
     * @param namespaceAware whether to be namespace-aware
     * @param validating     whether to validate
     */
    public XmlParser(boolean namespaceAware, boolean validating) {
        this.namespaceAware = namespaceAware;
        this.validating = validating;
    }

    // ==================== Parsing Methods ====================

    /**
     * Parses XML string into model.
     *
     * @param xmlString the XML string
     * @return the parsed document
     * @throws XmlParseException if parsing fails
     */
    public XmlDocument parse(String xmlString) throws XmlParseException {
        try {
            InputSource source = new InputSource(new StringReader(xmlString));
            return parse(source);
        } catch (Exception e) {
            throw new XmlParseException("Failed to parse XML string", e);
        }
    }

    /**
     * Parses XML file into model.
     *
     * @param filePath the file path
     * @return the parsed document
     * @throws XmlParseException if parsing fails
     */
    public XmlDocument parseFile(String filePath) throws XmlParseException {
        try {
            return parse(new InputSource(new FileInputStream(filePath)));
        } catch (FileNotFoundException e) {
            throw new XmlParseException("File not found: " + filePath, e);
        } catch (Exception e) {
            throw new XmlParseException("Failed to parse XML file: " + filePath, e);
        }
    }

    /**
     * Parses XML from InputStream into model.
     *
     * @param inputStream the input stream
     * @return the parsed document
     * @throws XmlParseException if parsing fails
     */
    public XmlDocument parse(InputStream inputStream) throws XmlParseException {
        try {
            return parse(new InputSource(inputStream));
        } catch (Exception e) {
            throw new XmlParseException("Failed to parse XML from stream", e);
        }
    }

    /**
     * Parses XML from InputSource into model.
     *
     * @param source the input source
     * @return the parsed document
     * @throws XmlParseException if parsing fails
     */
    private XmlDocument parse(InputSource source) throws XmlParseException {
        try {
            // Create DOM parser
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(namespaceAware);
            factory.setValidating(validating);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document domDoc = builder.parse(source);

            // Convert DOM to our model
            return convertDomToModel(domDoc);

        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new XmlParseException("Failed to parse XML", e);
        }
    }

    // ==================== DOM to Model Conversion ====================

    /**
     * Converts a DOM Document to our XmlDocument model.
     *
     * @param domDoc the DOM document
     * @return the model document
     */
    private XmlDocument convertDomToModel(Document domDoc) {
        XmlDocument xmlDoc = new XmlDocument();

        // Extract XML declaration info
        xmlDoc.setVersion(domDoc.getXmlVersion() != null ? domDoc.getXmlVersion() : "1.0");
        xmlDoc.setEncoding(domDoc.getXmlEncoding() != null ? domDoc.getXmlEncoding() : "UTF-8");
        xmlDoc.setStandalone(domDoc.getXmlStandalone() ? Boolean.TRUE : null);

        // Convert all child nodes
        NodeList children = domDoc.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node domNode = children.item(i);
            XmlNode xmlNode = convertDomNodeToModel(domNode);
            if (xmlNode != null) {
                if (xmlNode instanceof XmlElement) {
                    xmlDoc.setRootElement((XmlElement) xmlNode);
                } else {
                    xmlDoc.addChild(xmlNode);
                }
            }
        }

        return xmlDoc;
    }

    /**
     * Converts a DOM Node to our XmlNode model.
     *
     * @param domNode the DOM node
     * @return the model node, or null if not supported
     */
    private XmlNode convertDomNodeToModel(Node domNode) {
        if (domNode == null) {
            return null;
        }

        switch (domNode.getNodeType()) {
            case Node.ELEMENT_NODE:
                return convertElement((Element) domNode);

            case Node.TEXT_NODE:
                return convertText((Text) domNode);

            case Node.CDATA_SECTION_NODE:
                return convertCData((CDATASection) domNode);

            case Node.COMMENT_NODE:
                return convertComment((Comment) domNode);

            case Node.PROCESSING_INSTRUCTION_NODE:
                return convertProcessingInstruction((ProcessingInstruction) domNode);

            case Node.DOCUMENT_TYPE_NODE:
                // DTD not supported yet
                return null;

            default:
                // Ignore other node types
                return null;
        }
    }

    /**
     * Converts a DOM Element to XmlElement.
     *
     * @param domElement the DOM element
     * @return the model element
     */
    private XmlElement convertElement(Element domElement) {
        // Create element with namespace if present
        String prefix = domElement.getPrefix();
        String namespaceURI = domElement.getNamespaceURI();
        String localName = domElement.getLocalName() != null ? domElement.getLocalName() : domElement.getNodeName();

        XmlElement xmlElement = new XmlElement(localName, prefix, namespaceURI);

        // Convert attributes
        NamedNodeMap attrs = domElement.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String attrName = attr.getName();
            String attrValue = attr.getValue();
            xmlElement.setAttribute(attrName, attrValue);
        }

        // Convert child nodes
        NodeList children = domElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node domChild = children.item(i);
            XmlNode xmlChild = convertDomNodeToModel(domChild);
            if (xmlChild != null) {
                xmlElement.addChild(xmlChild);
            }
        }

        return xmlElement;
    }

    /**
     * Converts a DOM Text node to XmlText.
     *
     * @param domText the DOM text node
     * @return the model text node, or null if whitespace-only
     */
    private XmlText convertText(Text domText) {
        String text = domText.getNodeValue();
        if (text == null) {
            return null;
        }

        // Skip whitespace-only text nodes between elements
        // (but keep them if they're the only child or have non-whitespace siblings)
        if (text.trim().isEmpty() && shouldSkipWhitespace(domText)) {
            return null;
        }

        return new XmlText(text);
    }

    /**
     * Checks if a whitespace-only text node should be skipped.
     *
     * @param textNode the text node
     * @return true if should skip
     */
    private boolean shouldSkipWhitespace(Text textNode) {
        Node parent = textNode.getParentNode();
        if (parent == null) {
            return true;
        }

        // Keep whitespace if parent has no element children
        NodeList siblings = parent.getChildNodes();
        boolean hasElementSiblings = false;
        for (int i = 0; i < siblings.getLength(); i++) {
            if (siblings.item(i).getNodeType() == Node.ELEMENT_NODE) {
                hasElementSiblings = true;
                break;
            }
        }

        return hasElementSiblings;
    }

    /**
     * Converts a DOM CDATASection to XmlCData.
     *
     * @param domCData the DOM CDATA section
     * @return the model CDATA node
     */
    private XmlCData convertCData(CDATASection domCData) {
        String text = domCData.getNodeValue();
        return new XmlCData(text != null ? text : "");
    }

    /**
     * Converts a DOM Comment to XmlComment.
     *
     * @param domComment the DOM comment
     * @return the model comment node
     */
    private XmlComment convertComment(Comment domComment) {
        String text = domComment.getNodeValue();
        return new XmlComment(text != null ? text : "");
    }

    /**
     * Converts a DOM ProcessingInstruction to XmlProcessingInstruction.
     *
     * @param domPI the DOM processing instruction
     * @return the model PI node
     */
    private XmlProcessingInstruction convertProcessingInstruction(ProcessingInstruction domPI) {
        String target = domPI.getTarget();
        String data = domPI.getData();
        return new XmlProcessingInstruction(target, data);
    }

    // ==================== Configuration Methods ====================

    /**
     * Sets whether to parse with namespace awareness.
     *
     * @param namespaceAware true for namespace-aware parsing
     */
    public void setNamespaceAware(boolean namespaceAware) {
        this.namespaceAware = namespaceAware;
    }

    /**
     * Returns whether parser is namespace-aware.
     *
     * @return true if namespace-aware
     */
    public boolean isNamespaceAware() {
        return namespaceAware;
    }

    /**
     * Sets whether to validate against DTD/Schema.
     *
     * @param validating true to validate
     */
    public void setValidating(boolean validating) {
        this.validating = validating;
    }

    /**
     * Returns whether parser validates.
     *
     * @return true if validating
     */
    public boolean isValidating() {
        return validating;
    }

    // ==================== Exception Class ====================

    /**
     * Exception thrown when XML parsing fails.
     */
    public static class XmlParseException extends RuntimeException {
        public XmlParseException(String message) {
            super(message);
        }

        public XmlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
