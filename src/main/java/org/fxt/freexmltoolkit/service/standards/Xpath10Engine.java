package org.fxt.freexmltoolkit.service.standards;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Evaluates XPath 1.0 with the JDK's own XPath 1.0 engine ({@link XPathFactory#newDefaultInstance()}, independent
 * of the Saxon service provider on the class path), so an expression behaves exactly as on a pure XPath 1.0
 * processor: no sequences, no XPath 2.0 functions, 1.0 type conversions.
 */
public final class Xpath10Engine {

    /** A failed compile or evaluation; the message says what the XPath 1.0 engine reported. */
    public static final class Xpath10Exception extends RuntimeException {
        public Xpath10Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private Xpath10Engine() {
    }

    /** Compiles {@code expression} only; throws {@link Xpath10Exception} when it is no valid XPath 1.0. */
    public static void check(String expression, Map<String, String> namespaces) {
        try {
            newXPath(namespaces, null).compile(expression);
        } catch (XPathExpressionException e) {
            throw failure(e);
        }
    }

    /**
     * Evaluates {@code expression} on the document {@code xml} and formats the result like the console's XPath
     * output: elements serialized, text/attribute nodes as their value, numbers/strings/booleans as text.
     */
    public static String evaluate(String xml, String expression, Map<String, String> namespaces,
                                  Map<String, Object> variables) {
        Document doc = parse(xml);
        XPathExpression compiled;
        try {
            compiled = newXPath(namespaces, variables).compile(expression);
        } catch (XPathExpressionException e) {
            throw failure(e);
        }
        try {
            NodeList nodes = (NodeList) compiled.evaluate(doc, XPathConstants.NODESET);
            return formatNodes(nodes);
        } catch (XPathExpressionException notNodes) {
            // not a node-set: a string, number or boolean
            try {
                Object value = compiled.evaluate(doc, XPathConstants.STRING);
                return String.valueOf(value);
            } catch (XPathExpressionException e) {
                Xpath10Exception failure = failure(e);
                failure.addSuppressed(notNodes); // the node-set attempt failed first
                throw failure;
            }
        }
    }

    /**
     * Evaluates {@code expression} and returns each result item as text: a node set as one entry per node
     * (elements serialized, other nodes as their value), a string / number / boolean as one entry.
     *
     * @param xml the context document, or {@code null} / blank for none (an empty document)
     */
    public static java.util.List<String> evaluateItems(String xml, String expression, Map<String, String> namespaces) {
        Document doc = xml == null || xml.isBlank() ? emptyDocument() : parse(xml);
        XPathExpression compiled;
        try {
            compiled = newXPath(namespaces, null).compile(expression);
        } catch (XPathExpressionException e) {
            throw failure(e);
        }
        java.util.List<String> items = new java.util.ArrayList<>();
        try {
            NodeList nodes = (NodeList) compiled.evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                items.add(node.getNodeType() == Node.ELEMENT_NODE ? serialize(node)
                        : node.getNodeType() == Node.DOCUMENT_NODE
                                ? serialize(((Document) node).getDocumentElement()) : node.getTextContent());
            }
            return items;
        } catch (XPathExpressionException notNodes) {
            try {
                items.add(String.valueOf(compiled.evaluate(doc, XPathConstants.STRING)));
                return items;
            } catch (XPathExpressionException e) {
                Xpath10Exception failure = failure(e);
                failure.addSuppressed(notNodes); // the node-set attempt failed first
                throw failure;
            }
        }
    }

    private static Document emptyDocument() {
        try {
            return SecureXmlFactory.createSecureDocumentBuilder(true).newDocument();
        } catch (Exception e) {
            throw new Xpath10Exception("Cannot create a document: " + e.getMessage(), e);
        }
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilder builder = SecureXmlFactory.createSecureDocumentBuilder(true);
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new Xpath10Exception("The document is not well-formed: " + e.getMessage(), e);
        }
    }

    private static XPath newXPath(Map<String, String> namespaces, Map<String, Object> variables) {
        XPathFactory factory = XPathFactory.newDefaultInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (XPathFactoryConfigurationException e) {
            // the JDK factory supports it; nothing else to do
        }
        XPath xpath = factory.newXPath();
        if (namespaces != null && !namespaces.isEmpty()) {
            xpath.setNamespaceContext(new MapNamespaceContext(namespaces));
        }
        if (variables != null && !variables.isEmpty()) {
            xpath.setXPathVariableResolver(name -> variables.get(name.getLocalPart()));
        }
        return xpath;
    }

    private static String formatNodes(NodeList nodes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE -> out.append(serialize(node)).append(System.lineSeparator());
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    String text = node.getNodeValue();
                    if (text != null && !text.isBlank()) {
                        out.append(text.strip()).append(System.lineSeparator());
                    }
                }
                case Node.COMMENT_NODE -> out.append("<!--").append(node.getNodeValue()).append("-->")
                        .append(System.lineSeparator());
                case Node.PROCESSING_INSTRUCTION_NODE -> out.append("<?").append(node.getNodeName()).append(' ')
                        .append(node.getNodeValue()).append("?>").append(System.lineSeparator());
                case Node.DOCUMENT_NODE -> out.append(serialize(((Document) node).getDocumentElement()))
                        .append(System.lineSeparator());
                default -> out.append(node.getTextContent()).append(System.lineSeparator());
            }
        }
        return out.toString();
    }

    private static String serialize(Node node) {
        try {
            TransformerFactory tf = TransformerFactory.newDefaultInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter writer = new StringWriter();
            t.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString().strip();
        } catch (Exception e) {
            return node.getTextContent();
        }
    }

    private static Xpath10Exception failure(XPathExpressionException e) {
        Throwable cause = e;
        String message = e.getMessage();
        while ((message == null || message.isBlank()) && cause.getCause() != null) {
            cause = cause.getCause();
            message = cause.getMessage();
        }
        if (message != null) {
            message = message.replaceFirst("^javax\\.xml\\.transform\\.TransformerException: ", "");
        }
        return new Xpath10Exception("XPath 1.0: " + (message == null ? "invalid expression" : message), e);
    }

    /** A namespace context over a prefix → URI map. */
    private record MapNamespaceContext(Map<String, String> map) implements NamespaceContext {
        @Override
        public String getNamespaceURI(String prefix) {
            if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
                return XMLConstants.XML_NS_URI;
            }
            String uri = map.get(prefix);
            return uri == null ? XMLConstants.NULL_NS_URI : uri;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return map.entrySet().stream().filter(e -> e.getValue().equals(namespaceURI))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return map.entrySet().stream().filter(e -> e.getValue().equals(namespaceURI))
                    .map(Map.Entry::getKey).iterator();
        }
    }
}
