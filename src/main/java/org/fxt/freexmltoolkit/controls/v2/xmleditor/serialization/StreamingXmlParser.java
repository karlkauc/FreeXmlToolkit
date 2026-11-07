package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Stack;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;

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

    private static final int MAX_SHARED_LENGTH = 32;
    private static final int MAX_SHARED_STRINGS = 8_192;

    /**
     * @return an equal string seen before in this parse, or {@code s}: names and repeated values
     *         ({@code status="open"}, {@code <city>Vienna</city>}) of a large file were a new string
     *         per occurrence. Only short strings, and only the first {@value #MAX_SHARED_STRINGS}
     *         distinct ones are remembered (they are looked up later still), so unique values such
     *         as ids cannot grow the table.
     */
    private static String shared(java.util.Map<String, String> sharedStrings, String s) {
        if (s == null || s.length() > MAX_SHARED_LENGTH) {
            return s;
        }
        String known = sharedStrings.get(s);
        if (known != null) {
            return known;
        }
        if (sharedStrings.size() < MAX_SHARED_STRINGS) {
            sharedStrings.put(s, s);
        }
        return s;
    }

    private XmlDocument parseFromStream(XMLStreamReader reader) throws XMLStreamException {
        XmlDocument doc = new XmlDocument();
        Stack<ParserState> stateStack = new Stack<>();
        // Short strings seen in this parse (bounded): one instance per repeated value
        java.util.Map<String, String> sharedStrings = new java.util.HashMap<>();
        
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
                        
                        XmlElement element = new XmlElement(shared(sharedStrings, localName), prefix, namespaceUri);
                        element.setSourceLineNumber(reader.getLocation().getLineNumber());

                        // Handle namespace declarations
                        int nsCount = reader.getNamespaceCount();
                        for (int i = 0; i < nsCount; i++) {
                            String nsPrefix = reader.getNamespacePrefix(i);
                            String nsUri = reader.getNamespaceURI(i);
                            if (nsPrefix != null && !nsPrefix.isEmpty()) {
                                element.setAttribute("xmlns:" + nsPrefix, nsUri);
                            } else {
                                element.setAttribute("xmlns", nsUri);
                            }
                        }

                        // Handle attributes
                        int attrCount = reader.getAttributeCount();
                        for (int i = 0; i < attrCount; i++) {
                            String attrName = reader.getAttributeLocalName(i);
                            String attrPrefix = reader.getAttributePrefix(i);
                            String fullAttrName = (attrPrefix != null && !attrPrefix.isEmpty()) ? attrPrefix + ":" + attrName : attrName;
                            element.setAttribute(shared(sharedStrings, fullAttrName), shared(sharedStrings, reader.getAttributeValue(i)));
                        }
                        
                        if (stateStack.isEmpty()) {
                            doc.setRootElement(element);
                        } else {
                            ParserState parentState = stateStack.peek();
                            parentState.hasElementChildren = true;
                            // Discard any pending whitespace as it's just formatting before this child element
                            parentState.pendingWhitespace.setLength(0);
                            parentState.element.addChild(element);
                        }
                        stateStack.push(new ParserState(element));
                        break;
                        
                    case XMLStreamConstants.END_ELEMENT:
                        if (!stateStack.isEmpty()) {
                            ParserState currentState = stateStack.pop();
                            currentState.element.compactChildren();
                            // If no element children were found, commit any pending whitespace
                            // because it might be the only content of the element.
                            if (!currentState.hasElementChildren && currentState.pendingWhitespace.length() > 0) {
                                currentState.element.addChild(new XmlText(currentState.pendingWhitespace.toString()));
                            }
                            // Discard pending whitespace if we had element children
                        }
                        break;
                        
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                        String text = reader.getText();
                        if (text != null && !stateStack.isEmpty()) {
                            ParserState currentState = stateStack.peek();
                            
                            if (eventType == XMLStreamConstants.CDATA) {
                                // CDATA is always significant, commit any pending whitespace first
                                if (currentState.pendingWhitespace.length() > 0) {
                                    currentState.element.addChild(new XmlText(currentState.pendingWhitespace.toString()));
                                    currentState.pendingWhitespace.setLength(0);
                                }
                                currentState.element.addChild(new XmlCData(text));
                            } else if (text.trim().isEmpty()) {
                                // Just whitespace - buffer it until we know if it's ignorable
                                currentState.pendingWhitespace.append(text);
                            } else {
                                // Actual content - commit any pending whitespace first as it's now significant
                                if (currentState.pendingWhitespace.length() > 0) {
                                    currentState.element.addChild(new XmlText(currentState.pendingWhitespace.toString()));
                                    currentState.pendingWhitespace.setLength(0);
                                }
                                currentState.element.addChild(new XmlText(shared(sharedStrings, text)));
                            }
                        }
                        break;
                        
                    case XMLStreamConstants.COMMENT:
                        XmlComment comment = new XmlComment(reader.getText());
                        if (stateStack.isEmpty()) {
                            doc.addChild(comment);
                        } else {
                            ParserState currentState = stateStack.peek();
                            currentState.hasElementChildren = true;
                            // Comments also count as siblings that make surrounding whitespace ignorable
                            currentState.pendingWhitespace.setLength(0);
                            currentState.element.addChild(comment);
                        }
                        break;
                        
                    case XMLStreamConstants.PROCESSING_INSTRUCTION:
                        XmlProcessingInstruction pi = new XmlProcessingInstruction(reader.getPITarget(), reader.getPIData());
                        if (stateStack.isEmpty()) {
                            doc.addChild(pi);
                        } else {
                            ParserState currentState = stateStack.peek();
                            currentState.hasElementChildren = true;
                            currentState.pendingWhitespace.setLength(0);
                            currentState.element.addChild(pi);
                        }
                        break;
                    default:
                        break;
                }
            }
        } finally {
            reader.close();
        }
        
        return doc;
    }

    /**
     * Internal state for the streaming parser to handle whitespace correctly.
     */
    private static class ParserState {
        final XmlElement element;
        boolean hasElementChildren = false;
        final StringBuilder pendingWhitespace = new StringBuilder();

        ParserState(XmlElement element) {
            this.element = element;
        }
    }
}
