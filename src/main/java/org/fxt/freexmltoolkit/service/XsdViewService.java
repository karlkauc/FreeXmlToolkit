package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.Xsd11Feature;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo.NodeType;
import org.fxt.freexmltoolkit.domain.XsdVersion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * A service that provides specialized data for UI views, especially
 * for the XsdDiagramView. This class contains the logic for
 * creating a lightweight tree from an XSD using
 * native Java XML (JAXP/DOM) APIs.
 *
 * Extended with XSD 1.1 support and version detection.
 */
public class XsdViewService {

    private static final Logger logger = LogManager.getLogger(XsdViewService.class);
    private static final String NS_PREFIX = "xs";
    private static final String NS_URI = "http://www.w3.org/2001/XMLSchema";
    private static final String VC_NS_URI = "http://www.w3.org/2007/XMLSchema-versioning";

    // Caches for fast access to global XSD definitions as DOM nodes.
    private Map<String, Node> complexTypeMap;
    private Map<String, Node> simpleTypeMap;
    private Map<String, Node> globalElementMap;
    private Map<String, Node> groupMap;
    private Map<String, Node> attributeGroupMap;

    private XPath xpath;

    // XSD version context for this schema
    private XsdVersion xsdVersion;

    // A record to return the extracted documentation parts in a structured way.
    public record DocumentationParts(String mainDocumentation, String javadocContent) {
    }

    /**
     * Creates a lightweight tree for the diagram view from the XSD content.
     *
     * @param xsdContent The content of the XSD file as a string.
     * @return The root node of the tree (XsdNodeInfo) or null in case of an error.
     */
    public XsdNodeInfo buildLightweightTree(String xsdContent) {
        try {
            Document doc = parseXsdContent(xsdContent);
            initializeXPath();
            initializeCaches(doc);

            // Detect XSD version
            xsdVersion = detectXsdVersion(doc);
            logger.info("Detected XSD version: {}", xsdVersion);

            // Find the first global element, which is assumed to be the root element.
            NodeList rootElements = (NodeList) xpath.evaluate("/xs:schema/xs:element", doc, XPathConstants.NODESET);
            if (rootElements.getLength() == 0) {
                logger.error("No root element found in schema.");
                return null;
            }
            Node rootElementNode = rootElements.item(0);
            String rootName = getAttributeValue(rootElementNode, "name");

            // Start recursion with an empty set for the path
            return buildLightweightNodeRecursive(rootElementNode, "/" + rootName, new HashSet<>());

        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
            logger.error("Error parsing XSD or creating the tree.", e);
            return null;
        }
    }

    /**
     * Detects the XSD version from the schema root element.
     *
     * @param doc The parsed XSD document
     * @return The detected XSD version
     */
    private XsdVersion detectXsdVersion(Document doc) {
        try {
            Element schemaElement = doc.getDocumentElement();
            if (schemaElement == null || !"schema".equals(schemaElement.getLocalName())) {
                logger.warn("Invalid schema document - no schema root element");
                return XsdVersion.VERSION_1_0;
            }

            // Check for vc:minVersion attribute
            String minVersion = schemaElement.getAttributeNS(VC_NS_URI, "minVersion");
            String maxVersion = schemaElement.getAttributeNS(VC_NS_URI, "maxVersion");

            XsdVersion version = XsdVersion.fromAttributes(minVersion, maxVersion);

            // If minVersion="1.1" is explicitly set, register the version indicator feature
            if ("1.1".equals(minVersion)) {
                version.registerFeature(Xsd11Feature.VERSION_INDICATOR);
            }

            // Always scan for XSD 1.1 features to register them
            // This is important even when vc:minVersion is set, to track which features are used
            version = detectXsd11FeaturesHeuristically(doc, version);

            return version;

        } catch (Exception e) {
            logger.error("Error detecting XSD version", e);
            return XsdVersion.VERSION_1_0;
        }
    }

    /**
     * Detects XSD 1.1 features heuristically by scanning the document for 1.1-specific elements.
     * This helps identify schemas that use 1.1 features but don't declare the version.
     *
     * @param doc     The parsed XSD document
     * @param version The current version (will be upgraded to 1.1 if features are found)
     * @return Updated version with detected features
     */
    private XsdVersion detectXsd11FeaturesHeuristically(Document doc, XsdVersion version) {
        XsdVersion detectedVersion = version;
        boolean foundXsd11Features = false;

        // Check for xs:assert
        NodeList assertNodes = doc.getElementsByTagNameNS(NS_URI, "assert");
        if (assertNodes.getLength() > 0) {
            logger.info("Detected {} xs:assert element(s) - XSD 1.1 feature", assertNodes.getLength());
            if (version.isVersion10()) {
                detectedVersion = new XsdVersion("1.1", false);
            }
            detectedVersion.registerFeature(Xsd11Feature.ASSERTIONS);
            foundXsd11Features = true;
        }

        // Check for xs:alternative
        NodeList alternativeNodes = doc.getElementsByTagNameNS(NS_URI, "alternative");
        if (alternativeNodes.getLength() > 0) {
            logger.info("Detected {} xs:alternative element(s) - XSD 1.1 feature", alternativeNodes.getLength());
            if (version.isVersion10()) {
                detectedVersion = new XsdVersion("1.1", false);
            }
            detectedVersion.registerFeature(Xsd11Feature.ALTERNATIVES);
            foundXsd11Features = true;
        }

        // Check for xs:openContent
        NodeList openContentNodes = doc.getElementsByTagNameNS(NS_URI, "openContent");
        if (openContentNodes.getLength() > 0) {
            logger.info("Detected {} xs:openContent element(s) - XSD 1.1 feature", openContentNodes.getLength());
            if (version.isVersion10()) {
                detectedVersion = new XsdVersion("1.1", false);
            }
            detectedVersion.registerFeature(Xsd11Feature.OPEN_CONTENT);
            foundXsd11Features = true;
        }

        // Check for xs:override
        NodeList overrideNodes = doc.getElementsByTagNameNS(NS_URI, "override");
        if (overrideNodes.getLength() > 0) {
            logger.info("Detected {} xs:override element(s) - XSD 1.1 feature", overrideNodes.getLength());
            if (version.isVersion10()) {
                detectedVersion = new XsdVersion("1.1", false);
            }
            detectedVersion.registerFeature(Xsd11Feature.OVERRIDE);
            foundXsd11Features = true;
        }

        // Check for new built-in types
        if (detectNewBuiltinTypes(doc)) {
            if (version.isVersion10()) {
                detectedVersion = new XsdVersion("1.1", false);
            }
            detectedVersion.registerFeature(Xsd11Feature.NEW_BUILTIN_TYPES);
            foundXsd11Features = true;
        }

        if (foundXsd11Features && version.isVersion10()) {
            logger.warn("XSD 1.1 features detected but vc:minVersion not set - consider adding version declaration");
        }

        return detectedVersion;
    }

    /**
     * Detects usage of new XSD 1.1 built-in types
     */
    private boolean detectNewBuiltinTypes(Document doc) {
        String[] xsd11Types = {
                "dateTimeStamp",
                "yearMonthDuration",
                "dayTimeDuration",
                "precisionDecimal"
        };

        NodeList allElements = doc.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String type = element.getAttribute("type");
            String base = element.getAttribute("base");

            for (String xsd11Type : xsd11Types) {
                if (type.contains(xsd11Type) || base.contains(xsd11Type)) {
                    logger.info("Detected XSD 1.1 built-in type: {}", xsd11Type);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the detected XSD version for the current schema
     */
    public XsdVersion getXsdVersion() {
        return xsdVersion != null ? xsdVersion : XsdVersion.VERSION_1_0;
    }

    /**
     * Extracts the general documentation text and Javadoc tags
     * from the annotation of an XSD schema.
     *
     * @param xsdContent The content of the XSD file as a string.
     * @return A DocumentationParts object with the separated contents.
     */
    public DocumentationParts extractDocumentationParts(String xsdContent) {
        if (xsdContent == null || xsdContent.isBlank()) {
            return new DocumentationParts("", "");
        }
        try {
            Document doc = parseXsdContent(xsdContent);
            initializeXPath();
            Node schemaAnnotation = (Node) xpath.evaluate("/xs:schema/xs:annotation", doc, XPathConstants.NODE);
            if (schemaAnnotation == null) {
                return new DocumentationParts("", "");
            }
            return extractDocumentationFromAnnotation(schemaAnnotation);
        } catch (Exception e) {
            logger.error("Could not extract schema documentation.", e);
            return new DocumentationParts("", "");
        }
    }

    // =================================================================================
    // Recursive tree creation
    // =================================================================================

    /**
     * Recursive core method for creating the XsdNodeInfo tree from DOM nodes.
     */
    private XsdNodeInfo buildLightweightNodeRecursive(Node node, String currentXPath, Set<Node> visitedOnPath) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }

        // Recursion protection: If we visit a node on the current path again, we stop.
        if (visitedOnPath.contains(node)) {
            logger.warn("Recursion detected at node '{}' on path '{}'. Aborting this branch.", getAttributeValue(node, "name"), currentXPath);
            String nodeName = getAttributeValue(node, "name", node.getLocalName());
            return new XsdNodeInfo(nodeName + " (recursive)", "recursive", currentXPath, "Recursive definition, processing stopped.", Collections.emptyList(), Collections.emptyList(), "1", "1", NodeType.ELEMENT);
        }
        visitedOnPath.add(node);

        try {
            // Handles references (ref="...") for elements, attributes and groups
            String ref = getAttributeValue(node, "ref");
            if (!ref.isEmpty()) {
                Node referencedNode = findReferencedNode(node.getLocalName(), ref);
                if (referencedNode != null) {
                    // Process the referenced node instead of the current one.
                    return buildLightweightNodeRecursive(referencedNode, currentXPath, visitedOnPath);
                } else {
                    logger.warn("Reference '{}' for node '{}' could not be resolved.", ref, node.getLocalName());
                    return new XsdNodeInfo(ref + " (unresolved)", "unknown", currentXPath, "Could not resolve reference.", Collections.emptyList(), Collections.emptyList(), "1", "1", NodeType.ELEMENT);
                }
            }

            // Main processing based on the local name of the XML node
            return switch (node.getLocalName()) {
                case "element" -> processElement(node, currentXPath, visitedOnPath);
                case "attribute" -> processAttribute(node, currentXPath);
                case "sequence", "choice", "all" -> processParticle(node, currentXPath, visitedOnPath);
                case "any" -> processAny(node, currentXPath);
                case "simpleType" -> processSimpleType(node, currentXPath);
                case "complexType" -> processComplexType(node, currentXPath, visitedOnPath);
                case "assert" -> processAssert(node, currentXPath);  // XSD 1.1
                case "alternative" -> processAlternative(node, currentXPath);  // XSD 1.1
                default -> {
                    logger.trace("Unhandled node type in tree: {}", node.getLocalName());
                    yield null;
                }
            };
        } finally {
            // Remove the node from the path so it can be visited again in other branches.
            visitedOnPath.remove(node);
        }
    }

    // =================================================================================
    // Processing logic for specific XSD node types
    // =================================================================================

    private XsdNodeInfo processElement(Node elementNode, String currentXPath, Set<Node> visitedOnPath) {
        String name = getAttributeValue(elementNode, "name");
        String type = getAttributeValue(elementNode, "type");
        String minOccurs = getAttributeValue(elementNode, "minOccurs", "1");
        String maxOccurs = getAttributeValue(elementNode, "maxOccurs", "1");

        DocumentationParts docParts = extractDocumentationFromAnnotation(getDirectChildElement(elementNode, "annotation"));
        List<String> exampleValues = extractExampleValues(getDirectChildElement(elementNode, "annotation"));
        List<XsdNodeInfo> children = new ArrayList<>();

        // Find the type definition (either inline or globally referenced)
        Node complexTypeNode = getDirectChildElement(elementNode, "complexType");
        if (complexTypeNode == null && !type.isEmpty()) {
            complexTypeNode = complexTypeMap.get(stripNamespace(type));
        }

        if (complexTypeNode != null) {
            // Process the content of the complex type (particles and attributes)
            children.addAll(processComplexTypeContent(complexTypeNode, currentXPath, visitedOnPath));
        } else if (type.isEmpty()) {
            // Element without type and without inline definition is implicitly 'xs:anyType'
            type = "xs:anyType";
        }

        // XSD 1.1: Process type alternatives (xs:alternative) that are direct children of element
        for (Node child : getDirectChildElements(elementNode)) {
            if ("alternative".equals(child.getLocalName())) {
                XsdNodeInfo altNode = buildLightweightNodeRecursive(child, currentXPath, visitedOnPath);
                if (altNode != null) {
                    children.add(altNode);
                }
            }
        }

        return new XsdNodeInfo(name, type, currentXPath, docParts.mainDocumentation(), children, exampleValues, minOccurs, maxOccurs, NodeType.ELEMENT);
    }

    private List<XsdNodeInfo> processComplexTypeContent(Node complexTypeNode, String parentXPath, Set<Node> visitedOnPath) {
        List<XsdNodeInfo> children = new ArrayList<>();

        // 1. Process inheritance (xs:extension or xs:restriction)
        Node complexContent = getDirectChildElement(complexTypeNode, "complexContent");
        if (complexContent != null) {
            Node extension = getDirectChildElement(complexContent, "extension");
            if (extension != null) {
                String baseType = getAttributeValue(extension, "base");
                Node baseTypeNode = findTypeNode(baseType);
                if (baseTypeNode != null) {
                    // First add the children of the base type
                    children.addAll(processComplexTypeContent(baseTypeNode, parentXPath, visitedOnPath));
                }
                // Then add the children of the extension
                children.addAll(processParticleAndAttributes(extension, parentXPath, visitedOnPath));
            }
            Node restriction = getDirectChildElement(complexContent, "restriction");
            if (restriction != null) {
                // In a restriction, only the children defined here are used
                children.addAll(processParticleAndAttributes(restriction, parentXPath, visitedOnPath));
            }
        } else {
            // 2. No inheritance, process direct children (particles and attributes)
            children.addAll(processParticleAndAttributes(complexTypeNode, parentXPath, visitedOnPath));
        }

        return children;
    }

    /**
     * Processes particles (sequence, choice, all), attributes, and XSD 1.1 features within a given parent node.
     */
    private List<XsdNodeInfo> processParticleAndAttributes(Node parentNode, String parentXPath, Set<Node> visitedOnPath) {
        List<XsdNodeInfo> children = new ArrayList<>();
        for (Node child : getDirectChildElements(parentNode)) {
            String localName = child.getLocalName();
            if ("sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName)) {
                // For particles in global complexTypes, use the complexType name as XPath base
                String particleXPath = parentXPath;

                // For particles, don't add the particle name to the XPath since particles are structural containers
                // Keep the parent's XPath as the base for child elements

                children.add(buildLightweightNodeRecursive(child, particleXPath, visitedOnPath));
            } else if ("attribute".equals(localName) || "attributeGroup".equals(localName)) {
                String attrName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
                XsdNodeInfo attrNode = buildLightweightNodeRecursive(child, parentXPath + "/@" + attrName, visitedOnPath);
                if (attrNode != null) {
                    children.add(attrNode);
                }
            } else if ("assert".equals(localName)) {
                // XSD 1.1: Process assertions
                XsdNodeInfo assertNode = buildLightweightNodeRecursive(child, parentXPath, visitedOnPath);
                if (assertNode != null) {
                    children.add(assertNode);
                }
            } else if ("alternative".equals(localName)) {
                // XSD 1.1: Process type alternatives (usually on elements, not complexTypes)
                XsdNodeInfo altNode = buildLightweightNodeRecursive(child, parentXPath, visitedOnPath);
                if (altNode != null) {
                    children.add(altNode);
                }
            }
        }
        return children;
    }

    /**
     * Finds the complexType parent of a given node
     */
    private Node findComplexTypeParent(Node node) {
        Node current = node;
        while (current != null) {
            if ("complexType".equals(current.getLocalName())) {
                return current;
            }
            current = current.getParentNode();
        }
        return null;
    }

    private XsdNodeInfo processAttribute(Node attributeNode, String currentXPath) {
        String name = "@" + getAttributeValue(attributeNode, "name");
        String type = getAttributeValue(attributeNode, "type");
        String use = getAttributeValue(attributeNode, "use", "optional");
        String minOccurs = "required".equals(use) ? "1" : "0";

        DocumentationParts docParts = extractDocumentationFromAnnotation(getDirectChildElement(attributeNode, "annotation"));
        List<String> exampleValues = extractExampleValues(getDirectChildElement(attributeNode, "annotation"));

        return new XsdNodeInfo(name, type, currentXPath, docParts.mainDocumentation(), Collections.emptyList(), exampleValues, minOccurs, "1", NodeType.ATTRIBUTE);
    }

    private XsdNodeInfo processParticle(Node particleNode, String currentXPath, Set<Node> visitedOnPath) {
        String name = particleNode.getLocalName();
        String minOccurs = getAttributeValue(particleNode, "minOccurs", "1");
        String maxOccurs = getAttributeValue(particleNode, "maxOccurs", "1");
        NodeType nodeType = NodeType.valueOf(name.toUpperCase());

        List<XsdNodeInfo> children = new ArrayList<>();
        for (Node child : getDirectChildElements(particleNode)) {
            String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
            // For particles, pass the parent's XPath to children, not the particle's own XPath
            String childXPath = currentXPath;
            if ("element".equals(child.getLocalName()) && !childName.isEmpty()) {
                childXPath += "/" + childName;
            }
            XsdNodeInfo childInfo = buildLightweightNodeRecursive(child, childXPath, visitedOnPath);
            if (childInfo != null) {
                children.add(childInfo);
            }
        }
        // Use parent's XPath for particles, not currentXPath which includes the particle name
        return new XsdNodeInfo(name, "Container", currentXPath, "", children, Collections.emptyList(), minOccurs, maxOccurs, nodeType);
    }

    private XsdNodeInfo processAny(Node anyNode, String currentXPath) {
        String minOccurs = getAttributeValue(anyNode, "minOccurs", "1");
        String maxOccurs = getAttributeValue(anyNode, "maxOccurs", "1");
        String namespace = getAttributeValue(anyNode, "namespace", "##any");
        String type = String.format("Wildcard (namespace: %s)", namespace);
        DocumentationParts docParts = extractDocumentationFromAnnotation(getDirectChildElement(anyNode, "annotation"));

        return new XsdNodeInfo("any", type, currentXPath, docParts.mainDocumentation(), Collections.emptyList(), Collections.emptyList(), minOccurs, maxOccurs, NodeType.ANY);
    }

    private XsdNodeInfo processSimpleType(Node simpleTypeNode, String currentXPath) {
        String name = getAttributeValue(simpleTypeNode, "name");
        if (name.isEmpty()) {
            name = "anonymous";
        }
        DocumentationParts docParts = extractDocumentationFromAnnotation(getDirectChildElement(simpleTypeNode, "annotation"));

        // Check if it's a restriction, list, or union
        String type = "simpleType";
        Node restriction = getDirectChildElement(simpleTypeNode, "restriction");
        if (restriction != null) {
            String base = getAttributeValue(restriction, "base");
            if (!base.isEmpty()) {
                type = "restriction of " + stripNamespace(base);
            }
        }
        Node list = getDirectChildElement(simpleTypeNode, "list");
        if (list != null) {
            String itemType = getAttributeValue(list, "itemType");
            if (!itemType.isEmpty()) {
                type = "list of " + stripNamespace(itemType);
            }
        }
        Node union = getDirectChildElement(simpleTypeNode, "union");
        if (union != null) {
            String memberTypes = getAttributeValue(union, "memberTypes");
            if (!memberTypes.isEmpty()) {
                type = "union of " + memberTypes;
            }
        }

        return new XsdNodeInfo(name, type, currentXPath, docParts.mainDocumentation(), Collections.emptyList(), Collections.emptyList(), "1", "1", NodeType.SIMPLE_TYPE);
    }

    private XsdNodeInfo processComplexType(Node complexTypeNode, String currentXPath, Set<Node> visitedOnPath) {
        String name = getAttributeValue(complexTypeNode, "name");
        if (name.isEmpty()) {
            name = "anonymous";
        }
        DocumentationParts docParts = extractDocumentationFromAnnotation(getDirectChildElement(complexTypeNode, "annotation"));

        // Process the content of the complex type
        List<XsdNodeInfo> children = processComplexTypeContent(complexTypeNode, currentXPath, visitedOnPath);

        String mixed = getAttributeValue(complexTypeNode, "mixed");
        String type = "complexType" + ("true".equals(mixed) ? " (mixed)" : "");

        return new XsdNodeInfo(name, type, currentXPath, docParts.mainDocumentation(), children, Collections.emptyList(), "1", "1", NodeType.COMPLEX_TYPE);
    }

    /**
     * Processes xs:assert elements (XSD 1.1 feature).
     * Assertions provide XPath 2.0-based validation constraints.
     *
     * @param assertNode   The assert node
     * @param currentXPath The current XPath location
     * @return XsdNodeInfo representing the assertion
     */
    private XsdNodeInfo processAssert(Node assertNode, String currentXPath) {
        String test = getAttributeValue(assertNode, "test");
        String xpathDefaultNs = getAttributeValue(assertNode, "xpath-default-namespace");

        DocumentationParts docParts = extractDocumentationFromAnnotation(
                getDirectChildElement(assertNode, "annotation")
        );

        Map<String, String> xsd11Attrs = new HashMap<>();
        if (!xpathDefaultNs.isEmpty()) {
            xsd11Attrs.put("xpath-default-namespace", xpathDefaultNs);
        }

        return new XsdNodeInfo(
                "assert",
                "Assertion",
                currentXPath + "/assert",
                docParts.mainDocumentation(),
                Collections.emptyList(),
                Collections.emptyList(),
                "1",
                "1",
                NodeType.ASSERT,
                test,  // XPath 2.0 expression
                xsd11Attrs
        );
    }

    /**
     * Processes xs:alternative elements (XSD 1.1 feature).
     * Type alternatives provide conditional type assignment based on XPath 2.0 expressions.
     *
     * @param alternativeNode The alternative node
     * @param currentXPath    The current XPath location
     * @return XsdNodeInfo representing the type alternative
     */
    private XsdNodeInfo processAlternative(Node alternativeNode, String currentXPath) {
        String test = getAttributeValue(alternativeNode, "test");
        String type = getAttributeValue(alternativeNode, "type");

        DocumentationParts docParts = extractDocumentationFromAnnotation(
                getDirectChildElement(alternativeNode, "annotation")
        );

        String displayType = type.isEmpty() ? "Type Alternative" : type;

        return new XsdNodeInfo(
                "alternative",
                displayType,
                currentXPath + "/alternative",
                docParts.mainDocumentation(),
                Collections.emptyList(),
                Collections.emptyList(),
                "1",
                "1",
                NodeType.ALTERNATIVE,
                test,  // XPath 2.0 expression (condition)
                Collections.emptyMap()
        );
    }

    // =================================================================================
    // Initialization and helper methods
    // =================================================================================

    private Document parseXsdContent(String xsdContent) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); // Important for working with namespaces (xs:)
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xsdContent)));
    }

    private void initializeXPath() {
        XPathFactory xPathFactory = XPathFactory.newInstance();
        xpath = xPathFactory.newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return NS_PREFIX.equals(prefix) ? NS_URI : null;
            }

            public String getPrefix(String namespaceURI) {
                return null;
            }

            public Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        });
    }

    private void initializeCaches(Document doc) throws XPathExpressionException {
        complexTypeMap = findAndCacheGlobalDefs(doc, "complexType");
        simpleTypeMap = findAndCacheGlobalDefs(doc, "simpleType");
        globalElementMap = findAndCacheGlobalDefs(doc, "element");
        groupMap = findAndCacheGlobalDefs(doc, "group");
        attributeGroupMap = findAndCacheGlobalDefs(doc, "attributeGroup");
    }

    private Map<String, Node> findAndCacheGlobalDefs(Document doc, String tagName) throws XPathExpressionException {
        Map<String, Node> map = new HashMap<>();
        NodeList nodes = (NodeList) xpath.evaluate("/xs:schema/xs:" + tagName, doc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String name = getAttributeValue(node, "name");
            if (!name.isEmpty()) {
                map.put(name, node);
            }
        }
        return map;
    }

    private Node findReferencedNode(String localName, String ref) {
        String cleanRef = stripNamespace(ref);
        return switch (localName) {
            case "element" -> globalElementMap.get(cleanRef);
            case "attribute" -> null; // Globale Attribute sind selten, aber hier erweiterbar
            case "group" -> groupMap.get(cleanRef);
            case "attributeGroup" -> attributeGroupMap.get(cleanRef);
            default -> null;
        };
    }

    private Node findTypeNode(String typeName) {
        String cleanTypeName = stripNamespace(typeName);
        Node typeNode = complexTypeMap.get(cleanTypeName);
        if (typeNode == null) {
            typeNode = simpleTypeMap.get(cleanTypeName);
        }
        return typeNode;
    }

    private DocumentationParts extractDocumentationFromAnnotation(Node annotationNode) {
        if (annotationNode == null) {
            return new DocumentationParts("", "");
        }
        Node docNode = getDirectChildElement(annotationNode, "documentation");
        if (docNode == null) {
            return new DocumentationParts("", "");
        }
        String fullDoc = docNode.getTextContent();
        if (fullDoc.isBlank()) {
            return new DocumentationParts("", "");
        }

        String[] lines = fullDoc.split("\\r?\\n");
        StringBuilder mainDocBuilder = new StringBuilder();
        StringBuilder javadocBuilder = new StringBuilder();

        for (String line : lines) {
            if (line.trim().startsWith("@")) {
                javadocBuilder.append(line).append("\n");
            } else {
                mainDocBuilder.append(line).append("\n");
            }
        }
        return new DocumentationParts(mainDocBuilder.toString().trim(), javadocBuilder.toString().trim());
    }

    /**
     * Extracts example values from altova:exampleValues elements in an annotation
     */
    private List<String> extractExampleValues(Node annotationNode) {
        List<String> exampleValues = new ArrayList<>();
        if (annotationNode == null) {
            return exampleValues;
        }

        // Look for xs:appinfo elements containing altova:exampleValues
        for (Node child : getDirectChildElements(annotationNode)) {
            if ("appinfo".equals(child.getLocalName())) {
                // Look for altova:exampleValues inside appinfo
                for (Node appinfoChild : getDirectChildElements(child)) {
                    if ("exampleValues".equals(appinfoChild.getLocalName()) &&
                            "http://www.altova.com".equals(appinfoChild.getNamespaceURI())) {
                        // Extract altova:example elements
                        for (Node exampleNode : getDirectChildElements(appinfoChild)) {
                            if ("example".equals(exampleNode.getLocalName()) &&
                                    "http://www.altova.com".equals(exampleNode.getNamespaceURI())) {
                                String value = getAttributeValue(exampleNode, "value");
                                if (!value.isEmpty()) {
                                    exampleValues.add(value);
                                }
                            }
                        }
                    }
                }
            }
        }
        return exampleValues;
    }

    // =================================================================================
    // DOM helper methods
    // =================================================================================

    private String getAttributeValue(Node node, String attrName) {
        return getAttributeValue(node, attrName, "");
    }

    private String getAttributeValue(Node node, String attrName, String defaultValue) {
        if (node == null || node.getAttributes() == null) return defaultValue;
        Node attrNode = node.getAttributes().getNamedItem(attrName);
        return (attrNode != null) ? attrNode.getNodeValue() : defaultValue;
    }

    private Node getDirectChildElement(Node parent, String childName) {
        if (parent == null) return null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && childName.equals(child.getLocalName())) {
                return child;
            }
        }
        return null;
    }

    private List<Node> getDirectChildElements(Node parent) {
        if (parent == null) return Collections.emptyList();
        List<Node> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                children.add(child);
            }
        }
        return children;
    }

    private String stripNamespace(String value) {
        if (value == null) return "";
        int colonIndex = value.indexOf(':');
        return (colonIndex != -1) ? value.substring(colonIndex + 1) : value;
    }
}