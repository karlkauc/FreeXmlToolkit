package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.Stack;

/**
 * High-performance streaming XML parser using StAX.
 * 
 * <p>Unlike {@link XmlParser}, this parser does not use DOM as an intermediate step,
 * significantly reducing memory footprint and improving speed for large files.</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Streaming-based parsing (StAX)</li>
 *   <li>Low memory overhead</li>
 *   <li>Support for large XML files (GB+)</li>
 *   <li>Maintains same model as {@link XmlParser}</li>
 * </ul>
 */
public class StreamingXmlParser {

    private static final Logger logger = LogManager.getLogger(StreamingXmlParser.class);

    private boolean namespaceAware = true;

    /**
     * Constructs a new StreamingXmlParser.
     */
    public StreamingXmlParser() {
    }

    /**
     * Sets whether the parser should be namespace-aware.
     * 
     * @param namespaceAware true for namespace-aware parsing
     */
    public void setNamespaceAware(boolean namespaceAware) {
        this.namespaceAware = namespaceAware;
    }

    /**
     * Parses an XML file into the model.
     * 
     * @param file the XML file
     * @return the parsed document
     * @throws XmlParser.XmlParseException if parsing fails
     */
    public XmlDocument parseFile(File file) throws XmlParser.XmlParseException {
        try (InputStream is = new FileInputStream(file)) {
            return parse(is, file.getAbsolutePath());
        } catch (IOException e) {
            throw new XmlParser.XmlParseException("Failed to open XML file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Parses an XML string into the model.
     * 
     * @param xmlContent the XML content
     * @return the parsed document
     * @throws XmlParser.XmlParseException if parsing fails
     */
    public XmlDocument parse(String xmlContent) throws XmlParser.XmlParseException {
        try (StringReader reader = new StringReader(xmlContent)) {
            XMLInputFactory factory = SecureXmlFactory.createSecureXMLInputFactory();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, namespaceAware);
            XMLStreamReader streamReader = factory.createXMLStreamReader(reader);
            return parseFromStream(streamReader);
        } catch (XMLStreamException e) {
            throw new XmlParser.XmlParseException("Failed to parse XML string", e);
        }
    }

    /**
     * Parses an XML stream into the model.
     * 
     * @param is the input stream
     * @param systemId the system identifier (optional)
     * @return the parsed document
     * @throws XmlParser.XmlParseException if parsing fails
     */
    public XmlDocument parse(InputStream is, String systemId) throws XmlParser.XmlParseException {
        try {
            XMLInputFactory factory = SecureXmlFactory.createSecureXMLInputFactory();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, namespaceAware);
            XMLStreamReader streamReader = factory.createXMLStreamReader(systemId, is);
            return parseFromStream(streamReader);
        } catch (XMLStreamException e) {
            throw new XmlParser.XmlParseException("Failed to parse XML from stream", e);
        }
    }

    private XmlDocument parseFromStream(XMLStreamReader reader) throws XMLStreamException {
        XmlDocument doc = new XmlDocument();
        Stack<XmlElement> elementStack = new Stack<>();
        
        try {
            while (reader.hasNext()) {
                int eventType = reader.next();
                
                switch (eventType) {
                    case XMLStreamConstants.START_DOCUMENT:
                        doc.setVersion(reader.getVersion() != null ? reader.getVersion() : "1.0");
                        doc.setEncoding(reader.getCharacterEncodingScheme() != null ? reader.getCharacterEncodingScheme() : "UTF-8");
                        doc.setStandalone(reader.isStandalone() ? Boolean.TRUE : null);
                        break;
                        
                    case XMLStreamConstants.START_ELEMENT:
                        String localName = reader.getLocalName();
                        String prefix = reader.getPrefix();
                        String namespaceUri = reader.getNamespaceURI();
                        
                        XmlElement element = new XmlElement(localName, prefix, namespaceUri);
                        
                        // Handle attributes
                        int attrCount = reader.getAttributeCount();
                        for (int i = 0; i < attrCount; i++) {
                            String attrName = reader.getAttributeLocalName(i);
                            String attrPrefix = reader.getAttributePrefix(i);
                            String fullAttrName = (attrPrefix != null && !attrPrefix.isEmpty()) ? attrPrefix + ":" + attrName : attrName;
                            element.setAttribute(fullAttrName, reader.getAttributeValue(i));
                        }
                        
                        if (elementStack.isEmpty()) {
                            doc.setRootElement(element);
                        } else {
                            elementStack.peek().addChild(element);
                        }
                        elementStack.push(element);
                        break;
                        
                    case XMLStreamConstants.END_ELEMENT:
                        if (!elementStack.isEmpty()) {
                            elementStack.pop();
                        }
                        break;
                        
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                        String text = reader.getText();
                        if (text != null && !elementStack.isEmpty()) {
                            // Trim if it's just whitespace between elements, 
                            // but keep it if it's actual content.
                            // Note: Simple heuristic, could be improved.
                            if (eventType == XMLStreamConstants.CDATA) {
                                elementStack.peek().addChild(new XmlCData(text));
                            } else {
                                // For normal text, we usually keep it as is in the model
                                // and let the view decide how to display it.
                                elementStack.peek().addChild(new XmlText(text));
                            }
                        }
                        break;
                        
                    case XMLStreamConstants.COMMENT:
                        XmlComment comment = new XmlComment(reader.getText());
                        if (elementStack.isEmpty()) {
                            doc.addChild(comment);
                        } else {
                            elementStack.peek().addChild(comment);
                        }
                        break;
                        
                    case XMLStreamConstants.PROCESSING_INSTRUCTION:
                        XmlProcessingInstruction pi = new XmlProcessingInstruction(reader.getPITarget(), reader.getPIData());
                        if (elementStack.isEmpty()) {
                            doc.addChild(pi);
                        } else {
                            elementStack.peek().addChild(pi);
                        }
                        break;
                }
            }
        } finally {
            reader.close();
        }
        
        return doc;
    }
}
