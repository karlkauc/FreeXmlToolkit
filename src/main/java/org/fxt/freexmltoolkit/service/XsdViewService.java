package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo.NodeType;
import org.w3c.dom.Document;
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
 * Ein Service, der spezialisierte Daten für die UI-Ansichten, insbesondere
 * für das XsdDiagramView, bereitstellt. Diese Klasse enthält die Logik zum
 * Erstellen eines leichtgewichtigen Baumes aus einem XSD unter Verwendung
 * nativer Java XML (JAXP/DOM) APIs.
 */
public class XsdViewService {

    private static final Logger logger = LogManager.getLogger(XsdViewService.class);
    private static final String NS_PREFIX = "xs";
    private static final String NS_URI = "http://www.w3.org/2001/XMLSchema";

    // Caches für schnellen Zugriff auf globale XSD-Definitionen als DOM-Knoten.
    private Map<String, Node> complexTypeMap;
    private Map<String, Node> simpleTypeMap;
    private Map<String, Node> globalElementMap;
    private Map<String, Node> groupMap;
    private Map<String, Node> attributeGroupMap;

    private XPath xpath;

    // Ein Record, um die extrahierten Dokumentationsteile strukturiert zurückzugeben.
    public record DocumentationParts(String mainDocumentation, String javadocContent) {
    }

    /**
     * Erstellt einen leichtgewichtigen Baum für die Diagrammansicht aus dem XSD-Inhalt.
     *
     * @param xsdContent Der Inhalt der XSD-Datei als String.
     * @return Der Wurzelknoten des Baumes (XsdNodeInfo) oder null bei einem Fehler.
     */
    public XsdNodeInfo buildLightweightTree(String xsdContent) {
        try {
            Document doc = parseXsdContent(xsdContent);
            initializeXPath();
            initializeCaches(doc);

            // Finde das erste globale Element, das als Wurzelelement angenommen wird.
            NodeList rootElements = (NodeList) xpath.evaluate("/xs:schema/xs:element", doc, XPathConstants.NODESET);
            if (rootElements.getLength() == 0) {
                logger.error("Kein Wurzelelement im Schema gefunden.");
                return null;
            }
            Node rootElementNode = rootElements.item(0);
            String rootName = getAttributeValue(rootElementNode, "name");

            // Starte die Rekursion mit einem leeren Set für den Pfad
            return buildLightweightNodeRecursive(rootElementNode, "/" + rootName, new HashSet<>());

        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
            logger.error("Fehler beim Parsen des XSD oder Erstellen des Baumes.", e);
            return null;
        }
    }

    /**
     * Extrahiert den allgemeinen Dokumentationstext und die Javadoc-Tags
     * aus der Annotation eines XSD-Schemas.
     *
     * @param xsdContent Der Inhalt der XSD-Datei als String.
     * @return Ein DocumentationParts-Objekt mit den getrennten Inhalten.
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
            logger.error("Konnte Schema-Dokumentation nicht extrahieren.", e);
            return new DocumentationParts("", "");
        }
    }

    // =================================================================================
    // Rekursive Baum-Erstellung
    // =================================================================================

    /**
     * Rekursive Kernmethode zur Erstellung des XsdNodeInfo-Baumes aus DOM-Knoten.
     */
    private XsdNodeInfo buildLightweightNodeRecursive(Node node, String currentXPath, Set<Node> visitedOnPath) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }

        // Rekursionsschutz: Wenn wir einen Knoten auf dem aktuellen Pfad erneut besuchen, stoppen wir.
        if (visitedOnPath.contains(node)) {
            logger.warn("Rekursion bei Knoten '{}' auf Pfad '{}' entdeckt. Breche diesen Zweig ab.", getAttributeValue(node, "name"), currentXPath);
            String nodeName = getAttributeValue(node, "name", node.getLocalName());
            return new XsdNodeInfo(nodeName + " (rekursiv)", "rekursiv", currentXPath, "Rekursive Definition, Verarbeitung gestoppt.", Collections.emptyList(), Collections.emptyList(), "1", "1", NodeType.ELEMENT);
        }
        visitedOnPath.add(node);

        try {
            // Behandelt Referenzen (ref="...") für Elemente, Attribute und Gruppen
            String ref = getAttributeValue(node, "ref");
            if (!ref.isEmpty()) {
                Node referencedNode = findReferencedNode(node.getLocalName(), ref);
                if (referencedNode != null) {
                    // Verarbeite den referenzierten Knoten anstelle des aktuellen.
                    return buildLightweightNodeRecursive(referencedNode, currentXPath, visitedOnPath);
                } else {
                    logger.warn("Referenz '{}' für Knoten '{}' konnte nicht aufgelöst werden.", ref, node.getLocalName());
                    return new XsdNodeInfo(ref + " (ungelöst)", "unbekannt", currentXPath, "Konnte Referenz nicht auflösen.", Collections.emptyList(), Collections.emptyList(), "1", "1", NodeType.ELEMENT);
                }
            }

            // Hauptverarbeitung basierend auf dem lokalen Namen des XML-Knotens
            return switch (node.getLocalName()) {
                case "element" -> processElement(node, currentXPath, visitedOnPath);
                case "attribute" -> processAttribute(node, currentXPath);
                case "sequence", "choice", "all" -> processParticle(node, currentXPath, visitedOnPath);
                case "any" -> processAny(node, currentXPath);
                default -> {
                    logger.trace("Nicht behandelter Knotentyp im Baum: {}", node.getLocalName());
                    yield null;
                }
            };
        } finally {
            // Entferne den Knoten vom Pfad, damit er in anderen Zweigen wieder besucht werden kann.
            visitedOnPath.remove(node);
        }
    }

    // =================================================================================
    // Verarbeitungslogik für spezifische XSD-Knotentypen
    // =================================================================================

    private XsdNodeInfo processElement(Node elementNode, String currentXPath, Set<Node> visitedOnPath) {
        String name = getAttributeValue(elementNode, "name");
        String type = getAttributeValue(elementNode, "type");
        String minOccurs = getAttributeValue(elementNode, "minOccurs", "1");
        String maxOccurs = getAttributeValue(elementNode, "maxOccurs", "1");

        DocumentationParts docParts = extractDocumentationFromAnnotation(getDirectChildElement(elementNode, "annotation"));
        List<XsdNodeInfo> children = new ArrayList<>();

        // Finde die Typdefinition (entweder inline oder global referenziert)
        Node complexTypeNode = getDirectChildElement(elementNode, "complexType");
        if (complexTypeNode == null && !type.isEmpty()) {
            complexTypeNode = complexTypeMap.get(stripNamespace(type));
        }

        if (complexTypeNode != null) {
            // Verarbeite den Inhalt des komplexen Typs (Partikel und Attribute)
            children.addAll(processComplexTypeContent(complexTypeNode, currentXPath, visitedOnPath));
        } else if (type.isEmpty()) {
            // Element ohne Typ und ohne inline-Definition ist implizit 'xs:anyType'
            type = "xs:anyType";
        }

        return new XsdNodeInfo(name, type, currentXPath, docParts.mainDocumentation(), children, Collections.emptyList(), minOccurs, maxOccurs, NodeType.ELEMENT);
    }

    private List<XsdNodeInfo> processComplexTypeContent(Node complexTypeNode, String parentXPath, Set<Node> visitedOnPath) {
        List<XsdNodeInfo> children = new ArrayList<>();

        // 1. Verarbeite Vererbung (xs:extension oder xs:restriction)
        Node complexContent = getDirectChildElement(complexTypeNode, "complexContent");
        if (complexContent != null) {
            Node extension = getDirectChildElement(complexContent, "extension");
            if (extension != null) {
                String baseType = getAttributeValue(extension, "base");
                Node baseTypeNode = findTypeNode(baseType);
                if (baseTypeNode != null) {
                    // Füge zuerst die Kinder des Basistyps hinzu
                    children.addAll(processComplexTypeContent(baseTypeNode, parentXPath, visitedOnPath));
                }
                // Füge dann die Kinder der Erweiterung hinzu
                children.addAll(processParticleAndAttributes(extension, parentXPath, visitedOnPath));
            }
            Node restriction = getDirectChildElement(complexContent, "restriction");
            if (restriction != null) {
                // Bei einer Beschränkung werden nur die hier definierten Kinder verwendet
                children.addAll(processParticleAndAttributes(restriction, parentXPath, visitedOnPath));
            }
        } else {
            // 2. Keine Vererbung, verarbeite direkte Kinder (Partikel und Attribute)
            children.addAll(processParticleAndAttributes(complexTypeNode, parentXPath, visitedOnPath));
        }

        return children;
    }

    /**
     * Verarbeitet Partikel (sequence, choice, all) und Attribute innerhalb eines gegebenen Eltern-Knotens.
     */
    private List<XsdNodeInfo> processParticleAndAttributes(Node parentNode, String parentXPath, Set<Node> visitedOnPath) {
        List<XsdNodeInfo> children = new ArrayList<>();
        for (Node child : getDirectChildElements(parentNode)) {
            String localName = child.getLocalName();
            if ("sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName)) {
                // Partikel-Container direkt als Kind hinzufügen
                children.add(buildLightweightNodeRecursive(child, parentXPath, visitedOnPath));
            } else if ("attribute".equals(localName) || "attributeGroup".equals(localName)) {
                String attrName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
                XsdNodeInfo attrNode = buildLightweightNodeRecursive(child, parentXPath + "/@" + attrName, visitedOnPath);
                if (attrNode != null) {
                    children.add(attrNode);
                }
            }
        }
        return children;
    }

    private XsdNodeInfo processAttribute(Node attributeNode, String currentXPath) {
        String name = "@" + getAttributeValue(attributeNode, "name");
        String type = getAttributeValue(attributeNode, "type");
        String use = getAttributeValue(attributeNode, "use", "optional");
        String minOccurs = "required".equals(use) ? "1" : "0";

        DocumentationParts docParts = extractDocumentationFromAnnotation(getDirectChildElement(attributeNode, "annotation"));

        return new XsdNodeInfo(name, type, currentXPath, docParts.mainDocumentation(), Collections.emptyList(), Collections.emptyList(), minOccurs, "1", NodeType.ATTRIBUTE);
    }

    private XsdNodeInfo processParticle(Node particleNode, String currentXPath, Set<Node> visitedOnPath) {
        String name = particleNode.getLocalName();
        String minOccurs = getAttributeValue(particleNode, "minOccurs", "1");
        String maxOccurs = getAttributeValue(particleNode, "maxOccurs", "1");
        NodeType nodeType = NodeType.valueOf(name.toUpperCase());

        List<XsdNodeInfo> children = new ArrayList<>();
        for (Node child : getDirectChildElements(particleNode)) {
            String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
            String childXPath = currentXPath;
            if ("element".equals(child.getLocalName())) {
                childXPath += "/" + childName;
            }
            XsdNodeInfo childInfo = buildLightweightNodeRecursive(child, childXPath, visitedOnPath);
            if (childInfo != null) {
                children.add(childInfo);
            }
        }
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

    // =================================================================================
    // Initialisierung und Hilfsmethoden
    // =================================================================================

    private Document parseXsdContent(String xsdContent) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); // Wichtig für die Arbeit mit Namespaces (xs:)
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

    // =================================================================================
    // DOM-Hilfsmethoden
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