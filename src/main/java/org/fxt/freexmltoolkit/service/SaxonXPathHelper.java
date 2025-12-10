package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.transform.dom.DOMSource;
import java.util.*;

/**
 * Utility class for Saxon XPath 3.1 operations.
 * Provides convenient methods for XPath evaluation with namespace support.
 *
 * <p>This class replaces the standard Java XPath API (javax.xml.xpath)
 * with Saxon's s9api to enable XPath 3.1 features.</p>
 */
public final class SaxonXPathHelper {

    private static final Logger logger = LogManager.getLogger(SaxonXPathHelper.class);

    private static final Processor PROCESSOR = new Processor(false);

    // Standard namespace maps for common use cases

    /**
     * Standard XSD namespace prefixes (xs and xsd both map to XML Schema namespace)
     */
    public static final Map<String, String> XSD_NAMESPACES = Map.of(
            "xs", "http://www.w3.org/2001/XMLSchema",
            "xsd", "http://www.w3.org/2001/XMLSchema"
    );

    /**
     * SVRL (Schematron Validation Report Language) namespace
     */
    public static final Map<String, String> SVRL_NAMESPACES = Map.of(
            "svrl", "http://purl.oclc.org/dsdl/svrl"
    );

    /**
     * Common XML namespaces
     */
    public static final Map<String, String> COMMON_NAMESPACES = Map.of(
            "xml", "http://www.w3.org/XML/1998/namespace",
            "xsi", "http://www.w3.org/2001/XMLSchema-instance",
            "fn", "http://www.w3.org/2005/xpath-functions"
    );

    private SaxonXPathHelper() {
        // Utility class, prevent instantiation
    }

    /**
     * Evaluates an XPath expression and returns a list of matching nodes.
     *
     * @param document   the DOM document to query
     * @param xpath      the XPath expression
     * @param namespaces map of prefix to namespace URI (can be null or empty)
     * @return list of matching XdmNode objects (empty list if no matches)
     */
    public static List<XdmNode> evaluateNodes(Document document, String xpath,
                                               Map<String, String> namespaces) {
        List<XdmNode> result = new ArrayList<>();
        try {
            XdmValue value = evaluate(document, xpath, namespaces);
            for (XdmItem item : value) {
                if (item instanceof XdmNode node) {
                    result.add(node);
                }
            }
        } catch (SaxonApiException e) {
            logger.debug("XPath evaluation failed for '{}': {}", xpath, e.getMessage());
        }
        return result;
    }

    /**
     * Evaluates an XPath expression and returns a single matching node.
     *
     * @param document   the DOM document to query
     * @param xpath      the XPath expression
     * @param namespaces map of prefix to namespace URI (can be null or empty)
     * @return the first matching XdmNode, or null if no match
     */
    public static XdmNode evaluateSingleNode(Document document, String xpath,
                                              Map<String, String> namespaces) {
        try {
            XdmValue value = evaluate(document, xpath, namespaces);
            if (value.size() > 0) {
                XdmItem item = value.itemAt(0);
                if (item instanceof XdmNode node) {
                    return node;
                }
            }
        } catch (SaxonApiException e) {
            logger.debug("XPath evaluation failed for '{}': {}", xpath, e.getMessage());
        }
        return null;
    }

    /**
     * Evaluates an XPath expression and returns the string value of the first match.
     *
     * @param document   the DOM document to query
     * @param xpath      the XPath expression
     * @param namespaces map of prefix to namespace URI (can be null or empty)
     * @return the string value of the first match, or null if no match
     */
    public static String evaluateString(Document document, String xpath,
                                         Map<String, String> namespaces) {
        try {
            XdmValue value = evaluate(document, xpath, namespaces);
            if (value.size() > 0) {
                return value.itemAt(0).getStringValue();
            }
        } catch (SaxonApiException e) {
            logger.debug("XPath evaluation failed for '{}': {}", xpath, e.getMessage());
        }
        return null;
    }

    /**
     * Evaluates an XPath expression and returns string values of all matches.
     *
     * @param document   the DOM document to query
     * @param xpath      the XPath expression
     * @param namespaces map of prefix to namespace URI (can be null or empty)
     * @return list of string values (empty list if no matches)
     */
    public static List<String> evaluateStringList(Document document, String xpath,
                                                   Map<String, String> namespaces) {
        List<String> result = new ArrayList<>();
        try {
            XdmValue value = evaluate(document, xpath, namespaces);
            for (XdmItem item : value) {
                result.add(item.getStringValue());
            }
        } catch (SaxonApiException e) {
            logger.debug("XPath evaluation failed for '{}': {}", xpath, e.getMessage());
        }
        return result;
    }

    /**
     * Evaluates an XPath expression and returns true if at least one match exists.
     *
     * @param document   the DOM document to query
     * @param xpath      the XPath expression
     * @param namespaces map of prefix to namespace URI (can be null or empty)
     * @return true if the expression matches at least one node
     */
    public static boolean evaluateBoolean(Document document, String xpath,
                                           Map<String, String> namespaces) {
        try {
            XdmValue value = evaluate(document, xpath, namespaces);
            return value.size() > 0;
        } catch (SaxonApiException e) {
            logger.debug("XPath evaluation failed for '{}': {}", xpath, e.getMessage());
            return false;
        }
    }

    /**
     * Evaluates an XPath expression and returns the count of matches.
     *
     * @param document   the DOM document to query
     * @param xpath      the XPath expression
     * @param namespaces map of prefix to namespace URI (can be null or empty)
     * @return the number of matching items
     */
    public static int evaluateCount(Document document, String xpath,
                                     Map<String, String> namespaces) {
        try {
            XdmValue value = evaluate(document, xpath, namespaces);
            return value.size();
        } catch (SaxonApiException e) {
            logger.debug("XPath evaluation failed for '{}': {}", xpath, e.getMessage());
            return 0;
        }
    }

    /**
     * Gets an attribute value from an XdmNode.
     *
     * @param node          the XdmNode
     * @param attributeName the attribute name (local name, no namespace)
     * @return the attribute value, or null if not found
     */
    public static String getAttributeValue(XdmNode node, String attributeName) {
        if (node == null || attributeName == null) {
            return null;
        }
        return node.getAttributeValue(new QName(attributeName));
    }

    /**
     * Gets the text content of an XdmNode.
     *
     * @param node the XdmNode
     * @return the text content, or null if node is null
     */
    public static String getTextContent(XdmNode node) {
        if (node == null) {
            return null;
        }
        return node.getStringValue();
    }

    /**
     * Evaluates an XPath expression with a Node context (instead of Document).
     *
     * @param contextNode the context node for XPath evaluation
     * @param xpath       the XPath expression
     * @param namespaces  map of prefix to namespace URI (can be null or empty)
     * @return list of matching XdmNode objects
     */
    public static List<XdmNode> evaluateNodesFromNode(Node contextNode, String xpath,
                                                       Map<String, String> namespaces) {
        List<XdmNode> result = new ArrayList<>();
        try {
            XdmValue value = evaluateFromNode(contextNode, xpath, namespaces);
            for (XdmItem item : value) {
                if (item instanceof XdmNode node) {
                    result.add(node);
                }
            }
        } catch (SaxonApiException e) {
            logger.debug("XPath evaluation failed for '{}': {}", xpath, e.getMessage());
        }
        return result;
    }

    /**
     * Creates a combined namespace map from multiple sources.
     *
     * @param namespaceMaps varargs of namespace maps to combine
     * @return combined map with all namespaces
     */
    @SafeVarargs
    public static Map<String, String> combineNamespaces(Map<String, String>... namespaceMaps) {
        Map<String, String> combined = new HashMap<>();
        for (Map<String, String> nsMap : namespaceMaps) {
            if (nsMap != null) {
                combined.putAll(nsMap);
            }
        }
        return combined;
    }

    // Internal helper methods

    private static XdmValue evaluate(Document document, String xpath,
                                      Map<String, String> namespaces) throws SaxonApiException {
        XPathCompiler compiler = PROCESSOR.newXPathCompiler();

        // Declare namespaces
        if (namespaces != null) {
            for (Map.Entry<String, String> ns : namespaces.entrySet()) {
                compiler.declareNamespace(ns.getKey(), ns.getValue());
            }
        }

        XPathExecutable executable = compiler.compile(xpath);
        XPathSelector selector = executable.load();

        // Wrap DOM document
        DocumentBuilder docBuilder = PROCESSOR.newDocumentBuilder();
        XdmNode xdmNode = docBuilder.wrap(document);
        selector.setContextItem(xdmNode);

        return selector.evaluate();
    }

    private static XdmValue evaluateFromNode(Node contextNode, String xpath,
                                              Map<String, String> namespaces) throws SaxonApiException {
        XPathCompiler compiler = PROCESSOR.newXPathCompiler();

        // Declare namespaces
        if (namespaces != null) {
            for (Map.Entry<String, String> ns : namespaces.entrySet()) {
                compiler.declareNamespace(ns.getKey(), ns.getValue());
            }
        }

        XPathExecutable executable = compiler.compile(xpath);
        XPathSelector selector = executable.load();

        // Wrap DOM node
        DocumentBuilder docBuilder = PROCESSOR.newDocumentBuilder();
        XdmNode xdmNode = docBuilder.wrap(contextNode);
        selector.setContextItem(xdmNode);

        return selector.evaluate();
    }

    /**
     * Gets the underlying Saxon Processor instance.
     * Use this only when you need direct access to Saxon APIs.
     *
     * @return the shared Saxon Processor
     */
    public static Processor getProcessor() {
        return PROCESSOR;
    }
}
