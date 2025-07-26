/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ExtendedXsdElement;
import org.fxt.freexmltoolkit.domain.ExtendedXsdElement.DocumentationInfo;
import org.fxt.freexmltoolkit.domain.ExtendedXsdElement.RestrictionInfo;
import org.fxt.freexmltoolkit.domain.JavadocInfo;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.service.TaskProgressListener.ProgressUpdate;
import org.fxt.freexmltoolkit.service.TaskProgressListener.ProgressUpdate.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class XsdDocumentationService {

    static final int MAX_ALLOWED_DEPTH = 99;
    private static final Logger logger = LogManager.getLogger(XsdDocumentationService.class);
    private static final String NS_PREFIX = "xs";
    private static final String NS_URI = "http://www.w3.org/2001/XMLSchema";
    private static final String ALTOVA_NS_URI = "http://www.altova.com/xml-schema-extensions";

    String xsdFilePath;
    public XsdDocumentationData xsdDocumentationData = new XsdDocumentationData();

    int counter;
    boolean parallelProcessing = false;

    public enum ImageOutputMethod {SVG, PNG}

    public ImageOutputMethod imageOutputMethod = ImageOutputMethod.SVG;
    Boolean useMarkdownRenderer = true;

    private TaskProgressListener progressListener;
    private final XsdSampleDataGenerator xsdSampleDataGenerator = new XsdSampleDataGenerator();
    XsdDocumentationHtmlService xsdDocumentationHtmlService = new XsdDocumentationHtmlService();

    // DOM/XPath related fields
    private XPath xpath;
    private Document doc;
    private Map<String, Node> complexTypeMap = new HashMap<>();
    private Map<String, Node> simpleTypeMap = new HashMap<>();
    private Map<String, Node> groupMap = new HashMap<>();
    private Map<String, Node> attributeGroupMap = new HashMap<>();

    public void setProgressListener(TaskProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
        this.xsdDocumentationData.setXsdFilePath(xsdFilePath);
    }

    public void setMethod(ImageOutputMethod method) {
        this.imageOutputMethod = method;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public void setUseMarkdownRenderer(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
    }

    public void generateXsdDocumentation(File outputDirectory) throws Exception {
        logger.debug("Starting documentation generation...");
        processXsd(this.useMarkdownRenderer);

        xsdDocumentationHtmlService.setOutputDirectory(outputDirectory);
        xsdDocumentationHtmlService.setDocumentationData(xsdDocumentationData);
        xsdDocumentationHtmlService.setXsdDocumentationService(this);

        executeAndTrack("Copying resources", xsdDocumentationHtmlService::copyResources);
        executeAndTrack("Generating root page", xsdDocumentationHtmlService::generateRootPage);
        executeAndTrack("Generating list of complex types", xsdDocumentationHtmlService::generateComplexTypesListPage);
        executeAndTrack("Generating list of simple types", xsdDocumentationHtmlService::generateSimpleTypesListPage);
        executeAndTrack("Generating data dictionary", xsdDocumentationHtmlService::generateDataDictionaryPage);
        executeAndTrack("Generating search index", xsdDocumentationHtmlService::generateSearchIndex);

        if (parallelProcessing) {
            executeAndTrack("Generating detail pages for complex types (parallel)", xsdDocumentationHtmlService::generateComplexTypePagesInParallel);
            executeAndTrack("Generating detail pages for simple types (parallel)", xsdDocumentationHtmlService::generateSimpleTypePagesInParallel);
            executeAndTrack("Generating detail pages for elements (parallel)", xsdDocumentationHtmlService::generateDetailsPagesInParallel);
        } else {
            executeAndTrack("Generating detail pages for complex types", xsdDocumentationHtmlService::generateComplexTypePages);
            executeAndTrack("Generating detail pages for simple types", xsdDocumentationHtmlService::generateSimpleTypePages);
            executeAndTrack("Generating detail pages for elements", xsdDocumentationHtmlService::generateDetailPages);
        }
    }

    public void processXsd(Boolean useMarkdownRenderer) throws Exception {
        this.useMarkdownRenderer = useMarkdownRenderer;
        initializeXmlTools();

        // Parse the main XSD file
        String xsdContent = Files.readString(new File(xsdFilePath).toPath(), StandardCharsets.UTF_8);
        this.doc = parseXsdContent(xsdContent);

        // Initialize caches for global definitions
        initializeCaches(this.doc);

        // Populate XsdDocumentationData
        populateDocumentationData();

        // Start traversal from global elements
        counter = 0;
        for (Node globalElement : xsdDocumentationData.getGlobalElements()) {
            String rootName = getAttributeValue(globalElement, "name");
            traverseNode(globalElement, "/" + rootName, null, 0, new HashSet<>());
        }

        // Build the type usage map after traversal
        buildTypeUsageMap();
    }

    private void traverseNode(Node node, String currentXPath, String parentXPath, int level, Set<Node> visitedOnPath) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE || level > MAX_ALLOWED_DEPTH) {
            return;
        }
        if (visitedOnPath.contains(node)) {
            logger.warn("Recursion detected at node '{}' on path '{}'. Aborting this branch.", getAttributeValue(node, "name"), currentXPath);
            return;
        }
        visitedOnPath.add(node);

        try {
            // Handle references (ref="...")
            String ref = getAttributeValue(node, "ref");
            if (ref != null && !ref.isEmpty()) {
                Node referencedNode = findReferencedNode(node.getLocalName(), ref);
                if (referencedNode != null) {
                    traverseNode(referencedNode, currentXPath, parentXPath, level, visitedOnPath);
                } else {
                    logger.warn("Reference '{}' for node '{}' could not be resolved.", ref, node.getLocalName());
                }
                return; // Stop processing the current ref-node
            }

            String localName = node.getLocalName();
            if ("element".equals(localName) || "attribute".equals(localName)) {
                processElementOrAttribute(node, currentXPath, parentXPath, level, visitedOnPath);
            } else if ("sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName) || "group".equals(localName)) {
                // For containers, just traverse their children
                for (Node child : getDirectChildElements(node)) {
                    String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
                    String childXPath = "element".equals(child.getLocalName()) ? currentXPath + "/" + childName : currentXPath;
                    traverseNode(child, childXPath, currentXPath, level, visitedOnPath);
                }
            }
        } finally {
            visitedOnPath.remove(node);
        }
    }

    private void processElementOrAttribute(Node node, String currentXPath, String parentXPath, int level, Set<Node> visitedOnPath) {
        ExtendedXsdElement extendedElem = new ExtendedXsdElement();
        extendedElem.setUseMarkdownRenderer(this.useMarkdownRenderer);
        extendedElem.setCurrentNode(node);
        extendedElem.setCounter(counter++);
        extendedElem.setLevel(level);
        extendedElem.setCurrentXpath(currentXPath);
        extendedElem.setParentXpath(parentXPath);

        boolean isAttribute = "attribute".equals(node.getLocalName());
        String name = getAttributeValue(node, "name");
        extendedElem.setElementName(isAttribute ? "@" + name : name);

        // Add to parent's children list
        if (parentXPath != null && xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
            xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).getChildren().add(currentXPath);
        }
        xsdDocumentationData.getExtendedXsdElementMap().put(currentXPath, extendedElem);

        // --- Type, Documentation, and Content Processing ---
        String typeName = getAttributeValue(node, "type");
        Node typeDefinitionNode = findTypeDefinition(node, typeName);

        extendedElem.setElementType(typeName);
        if (typeDefinitionNode != null && typeName == null) {
            // Inline type definition
            extendedElem.setElementType("(anonymous)");
        }

        // Process annotations from the element itself and its type definition
        processAnnotations(getDirectChildElement(node, "annotation"), extendedElem);
        if (typeDefinitionNode != null) {
            processAnnotations(getDirectChildElement(typeDefinitionNode, "annotation"), extendedElem);
        }

        // Process content (children for elements, restrictions for attributes/simple types)
        if (!isAttribute) {
            Node contentModel = findContentModel(node, typeDefinitionNode);
            if (contentModel != null) {
                // Handle complex content (sequence, choice, extension, etc.)
                processComplexContent(contentModel, currentXPath, level, visitedOnPath);
            }
        }

        // Process restrictions
        Node restrictionNode = findRestriction(node, typeDefinitionNode);
        if (restrictionNode != null) {
            extendedElem.setRestrictionInfo(parseRestriction(restrictionNode));
            if (extendedElem.getElementType() == null || extendedElem.getElementType().isEmpty()) {
                extendedElem.setElementType(getAttributeValue(restrictionNode, "base"));
            }
        }

        // Final steps
        extendedElem.setSourceCode(nodeToString(node));

        extendedElem.setSampleData(xsdSampleDataGenerator.generate(extendedElem));
    }

    private void processComplexContent(Node contentNode, String parentXPath, int level, Set<Node> visitedOnPath) {
        String localName = contentNode.getLocalName();

        // Handle extension
        if ("extension".equals(localName)) {
            String baseType = getAttributeValue(contentNode, "base");
            Node baseTypeNode = findTypeDefinition(null, baseType);
            if (baseTypeNode != null) {
                // First, process the base type's content
                Node baseContentModel = findContentModel(baseTypeNode, null);
                if (baseContentModel != null) {
                    processComplexContent(baseContentModel, parentXPath, level, visitedOnPath);
                }
            }
        }

        // Process all direct children (sequence, choice, element, attribute, etc.)
        for (Node child : getDirectChildElements(contentNode)) {
            String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
            String childXPath;
            if ("attribute".equals(child.getLocalName())) {
                childXPath = parentXPath + "/@" + childName;
            } else if ("element".equals(child.getLocalName())) {
                childXPath = parentXPath + "/" + childName;
            } else {
                childXPath = parentXPath; // For containers like sequence/choice
            }
            traverseNode(child, childXPath, parentXPath, level + 1, visitedOnPath);
        }
    }

    public String generateSampleXml(boolean mandatoryOnly, int maxOccurrences) {
        if (xsdDocumentationData.getExtendedXsdElementMap().isEmpty()) {
            try {
                processXsd(false);
            } catch (Exception e) {
                logger.error("Failed to process XSD for sample XML generation.", e);
                return "<!-- Error processing XSD: " + e.getMessage() + " -->";
            }
        }

        List<ExtendedXsdElement> rootElements = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> e.getParentXpath() == null || e.getParentXpath().equals("/"))
                .sorted(Comparator.comparing(ExtendedXsdElement::getCounter))
                .toList();

        if (rootElements.isEmpty()) {
            return "<!-- No root element found in XSD -->";
        }

        StringBuilder xmlBuilder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        ExtendedXsdElement root = rootElements.getFirst();
        buildXmlElement(xmlBuilder, root, mandatoryOnly, maxOccurrences, 0);
        return xmlBuilder.toString();
    }

    private void buildXmlElement(StringBuilder sb, ExtendedXsdElement element, boolean mandatoryOnly, int maxOccurrences, int indentLevel) {
        if (element == null || (mandatoryOnly && !element.isMandatory())) {
            return;
        }
        if (element.getElementName().startsWith("@")) {
            return; // Attributes are handled by their parent
        }

        String maxOccurs = getAttributeValue(element.getCurrentNode(), "maxOccurs", "1");
        int repeatCount = 1;
        if (!"1".equals(maxOccurs)) {
            if ("unbounded".equalsIgnoreCase(maxOccurs)) {
                repeatCount = maxOccurrences;
            } else {
                try {
                    repeatCount = Math.min(Integer.parseInt(maxOccurs), maxOccurrences);
                } catch (NumberFormatException e) {
                    repeatCount = 1;
                }
            }
        }

        for (int i = 0; i < repeatCount; i++) {
            String indent = "\t".repeat(indentLevel);
            sb.append(indent).append("<").append(element.getElementName());

            // Separate children into attributes and elements
            List<ExtendedXsdElement> attributes = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> e.getElementName().startsWith("@"))
                    .toList();

            List<ExtendedXsdElement> childElements = element.getChildren().stream()
                    .map(xsdDocumentationData.getExtendedXsdElementMap()::get)
                    .filter(Objects::nonNull)
                    .filter(e -> !e.getElementName().startsWith("@"))
                    .toList();

            // Render attributes
            for (ExtendedXsdElement attr : attributes) {
                if (mandatoryOnly && !attr.isMandatory()) continue;
                String attrName = attr.getElementName().substring(1);
                String attrValue = attr.getSampleData() != null ? attr.getSampleData() : "";
                sb.append(" ").append(attrName).append("=\"").append(escapeXml(attrValue)).append("\"");
            }

            String sampleData = element.getSampleData() != null ? element.getSampleData() : "";
            if (childElements.isEmpty() && sampleData.isEmpty()) {
                sb.append("/>\n"); // Self-closing tag
            } else {
                sb.append(">");
                sb.append(escapeXml(sampleData));

                if (!childElements.isEmpty()) {
                    sb.append("\n");
                    for (ExtendedXsdElement childElement : childElements) {
                        buildXmlElement(sb, childElement, mandatoryOnly, maxOccurrences, indentLevel + 1);
                    }
                    sb.append(indent);
                }
                sb.append("</").append(element.getElementName()).append(">\n");
            }
        }
    }

    // --- XML/DOM Processing Helper Methods ---
    private void initializeXmlTools() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        XPathFactory xPathFactory = XPathFactory.newInstance();
        xpath = xPathFactory.newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return NS_PREFIX.equals(prefix) ? NS_URI : null;
            }

            public String getPrefix(String uri) {
                return null;
            }

            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
    }

    private Document parseXsdContent(String xsdContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xsdContent)));
    }

    private void initializeCaches(Document doc) throws Exception {
        complexTypeMap = findAndCacheGlobalDefs(doc, "complexType");
        simpleTypeMap = findAndCacheGlobalDefs(doc, "simpleType");
        groupMap = findAndCacheGlobalDefs(doc, "group");
        attributeGroupMap = findAndCacheGlobalDefs(doc, "attributeGroup");
    }

    private void populateDocumentationData() throws Exception {
        Element schemaElement = doc.getDocumentElement();
        xsdDocumentationData.setTargetNamespace(getAttributeValue(schemaElement, "targetNamespace"));
        xsdDocumentationData.setVersion(getAttributeValue(schemaElement, "version"));

        // NEU: Die Schema-Attribute auslesen und im Datenobjekt speichern.
        // Der Standardwert "unqualified" wird verwendet, falls das Attribut nicht vorhanden ist.
        xsdDocumentationData.setAttributeFormDefault(getAttributeValue(schemaElement, "attributeFormDefault", "unqualified"));
        xsdDocumentationData.setElementFormDefault(getAttributeValue(schemaElement, "elementFormDefault", "unqualified"));

        xsdDocumentationData.setGlobalElements(nodeListToList((NodeList) xpath.evaluate("/xs:schema/xs:element[@name]", doc, XPathConstants.NODESET)));
        // Die Listen der globalen Typen erstellen und alphabetisch nach Namen sortieren.
        List<Node> complexTypes = new ArrayList<>(complexTypeMap.values());
        complexTypes.sort(Comparator.comparing(node -> getAttributeValue(node, "name")));
        xsdDocumentationData.setGlobalComplexTypes(complexTypes);

        List<Node> simpleTypes = new ArrayList<>(simpleTypeMap.values());
        simpleTypes.sort(Comparator.comparing(node -> getAttributeValue(node, "name")));
        xsdDocumentationData.setGlobalSimpleTypes(simpleTypes);

        // Extract namespaces
        Map<String, String> nsMap = new HashMap<>();
        var attributes = schemaElement.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (attr.getNodeName().startsWith("xmlns:")) {
                nsMap.put(attr.getLocalName(), attr.getNodeValue());
            }
        }
        xsdDocumentationData.setNamespaces(nsMap);
    }

    private void buildTypeUsageMap() {
        logger.debug("Building type usage index for faster lookups...");
        Map<String, List<ExtendedXsdElement>> typeUsageMap = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(element -> element.getElementType() != null && !element.getElementType().isEmpty())
                .collect(Collectors.groupingBy(ExtendedXsdElement::getElementType));

        typeUsageMap.values().parallelStream()
                .forEach(list -> list.sort(Comparator.comparing(ExtendedXsdElement::getCurrentXpath)));

        xsdDocumentationData.setTypeUsageMap(typeUsageMap);
        logger.debug("Type usage index built with {} types.", typeUsageMap.size());
    }

    private void processAnnotations(Node annotationNode, ExtendedXsdElement extendedElem) {
        if (annotationNode == null) {
            return;
        }

        // 1. Dokumentation extrahieren
        for (Node docNode : getDirectChildElements(annotationNode, "documentation")) {
            String lang = getAttributeValue(docNode, "xml:lang", "default");
            extendedElem.getDocumentations().add(new DocumentationInfo(lang, docNode.getTextContent()));
        }

        // 2. AppInfo-Tags verarbeiten (für Javadoc und Altova-Beispiele)
        JavadocInfo javadocInfo = extendedElem.getJavadocInfo() != null ? extendedElem.getJavadocInfo() : new JavadocInfo();
        List<String> genericAppInfos = extendedElem.getGenericAppInfos() != null ? extendedElem.getGenericAppInfos() : new ArrayList<>();
        List<String> exampleValues = extendedElem.getExampleValues(); // Liste für Beispielwerte holen

        for (Node appInfoNode : getDirectChildElements(annotationNode, "appinfo")) {
            // Javadoc-Style-Tags verarbeiten
            String source = getAttributeValue(appInfoNode, "source");
            if (source != null && !source.isBlank()) {
                if (source.startsWith("@since")) javadocInfo.setSince(source.substring("@since".length()).trim());
                else if (source.startsWith("@see")) javadocInfo.getSee().add(source.substring("@see".length()).trim());
                else if (source.startsWith("@deprecated"))
                    javadocInfo.setDeprecated(source.substring("@deprecated".length()).trim());
                else genericAppInfos.add(source);
            } else {
                // Altova-Beispielwerte extrahieren
                boolean isAltovaExample = false;
                for (Node appInfoChild : getDirectChildElements(appInfoNode)) {
                    // Prüfen, ob der Knoten zu Altova gehört und <exampleValues> ist
                    if (ALTOVA_NS_URI.equals(appInfoChild.getNamespaceURI()) && "exampleValues".equals(appInfoChild.getLocalName())) {
                        isAltovaExample = true;
                        // Alle <example>-Kinder durchlaufen
                        for (Node exampleNode : getDirectChildElements(appInfoChild)) {
                            if (ALTOVA_NS_URI.equals(exampleNode.getNamespaceURI()) && "example".equals(exampleNode.getLocalName())) {
                                String value = getAttributeValue(exampleNode, "value");
                                if (value != null) {
                                    exampleValues.add(value);
                                }
                            }
                        }
                    }
                }
                // Wenn es kein Altova-Beispiel war, als generische Info behandeln
                if (!isAltovaExample) {
                    genericAppInfos.add(appInfoNode.getTextContent());
                }
            }
        }

        // 3. Gesammelte Informationen im Element speichern
        if (javadocInfo.hasData()) {
            extendedElem.setJavadocInfo(javadocInfo);
        }
        if (!genericAppInfos.isEmpty()) {
            extendedElem.setGenericAppInfos(genericAppInfos);
        }
        // Die 'exampleValues'-Liste wurde direkt modifiziert, ein setExampleValues ist nicht nötig.
    }

    private RestrictionInfo parseRestriction(Node restrictionNode) {
        String base = getAttributeValue(restrictionNode, "base");
        // Die Map wird jetzt für Listen initialisiert
        Map<String, List<String>> facets = new LinkedHashMap<>();

        for (Node facetNode : getDirectChildElements(restrictionNode)) {
            String facetName = facetNode.getLocalName();
            if (!"annotation".equals(facetName)) {
                // Fügt den Wert zur Liste des entsprechenden Facet-Namens hinzu.
                // computeIfAbsent stellt sicher, dass die Liste existiert.
                facets.computeIfAbsent(facetName, k -> new ArrayList<>())
                        .add(getAttributeValue(facetNode, "value"));
            }
        }
        return new RestrictionInfo(base, facets);
    }

    private Node findTypeDefinition(Node elementNode, String typeName) {
        // Case 1: Inline definition
        if (elementNode != null) {
            Node inlineComplex = getDirectChildElement(elementNode, "complexType");
            if (inlineComplex != null) return inlineComplex;
            Node inlineSimple = getDirectChildElement(elementNode, "simpleType");
            if (inlineSimple != null) return inlineSimple;
        }
        // Case 2: Global reference
        if (typeName != null && !typeName.isEmpty()) {
            String cleanTypeName = stripNamespace(typeName);
            Node typeNode = complexTypeMap.get(cleanTypeName);
            if (typeNode != null) return typeNode;
            return simpleTypeMap.get(cleanTypeName);
        }
        return null;
    }

    /**
     * Finds a globally defined type (simple or complex) by its name.
     * This method is intended for use by other services that need to look up types.
     *
     * @param typeName The name of the type to find (e.g., "MyComplexType", "xs:string").
     * @return The corresponding DOM Node if a custom type is found, otherwise null.
     */
    public Node findTypeNodeByName(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }
        // Remove namespace prefix if present (e.g., "myNs:MyType" -> "MyType")
        String cleanTypeName = stripNamespace(typeName);

        // First, check the cache for complex types
        Node typeNode = complexTypeMap.get(cleanTypeName);
        if (typeNode != null) {
            return typeNode;
        }

        // If not found, check the cache for simple types
        return simpleTypeMap.get(cleanTypeName);
    }

    private Node findContentModel(Node elementNode, Node typeDefinitionNode) {
        Node contextNode = (typeDefinitionNode != null) ? typeDefinitionNode : elementNode;
        if (contextNode == null) return null;

        // Look inside <complexContent>
        Node complexContent = getDirectChildElement(contextNode, "complexContent");
        if (complexContent != null) {
            Node extension = getDirectChildElement(complexContent, "extension");
            if (extension != null) return extension;
            Node restriction = getDirectChildElement(complexContent, "restriction");
            if (restriction != null) return restriction;
        }

        // Look inside <simpleContent>
        Node simpleContent = getDirectChildElement(contextNode, "simpleContent");
        if (simpleContent != null) {
            Node extension = getDirectChildElement(simpleContent, "extension");
            if (extension != null) return extension;
            Node restriction = getDirectChildElement(simpleContent, "restriction");
            if (restriction != null) return restriction;
        }

        // Look for direct particles if no complex/simpleContent is present
        Node sequence = getDirectChildElement(contextNode, "sequence");
        if (sequence != null) return sequence;
        Node choice = getDirectChildElement(contextNode, "choice");
        if (choice != null) return choice;
        Node all = getDirectChildElement(contextNode, "all");
        if (all != null) return all;
        Node group = getDirectChildElement(contextNode, "group");
        return group;
    }

    private Node findRestriction(Node elementNode, Node typeDefinitionNode) {
        Node contextNode = (typeDefinitionNode != null) ? typeDefinitionNode : elementNode;
        if (contextNode == null) return null;

        // Direct <restriction> inside a <simpleType>
        Node restriction = getDirectChildElement(contextNode, "restriction");
        if (restriction != null) return restriction;

        // Restriction inside <simpleContent>
        Node simpleContent = getDirectChildElement(contextNode, "simpleContent");
        if (simpleContent != null) {
            return getDirectChildElement(simpleContent, "restriction");
        }
        return null;
    }

    public Node findReferencedNode(String localName, String ref) {
        String cleanRef = stripNamespace(ref);
        return switch (localName) {
            case "group" -> groupMap.get(cleanRef);
            case "attributeGroup" -> attributeGroupMap.get(cleanRef);
            // Note: element references are handled in the main traversal logic
            // by looking up in the global element map, but this could be centralized here too.
            default -> null;
        };
    }

    private Map<String, Node> findAndCacheGlobalDefs(Document doc, String tagName) throws Exception {
        Map<String, Node> map = new HashMap<>();
        NodeList nodes = (NodeList) xpath.evaluate("/xs:schema/xs:" + tagName + "[@name]", doc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String name = getAttributeValue(node, "name");
            if (name != null && !name.isEmpty()) {
                map.put(name, node);
            }
        }
        return map;
    }

    private void executeAndTrack(String taskName, Runnable task) {
        long startTime = System.currentTimeMillis();
        if (progressListener != null) {
            progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.RUNNING, 0));
        }
        try {
            task.run();
            long duration = System.currentTimeMillis() - startTime;
            if (progressListener != null) {
                progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.FINISHED, duration));
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            if (progressListener != null) {
                progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.FAILED, duration));
            }
            throw e; // Re-throw the exception after reporting
        }
    }

    // --- General DOM/String Helper Methods ---

    private String getAttributeValue(Node node, String attrName) {
        return getAttributeValue(node, attrName, null);
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

    private List<Node> getDirectChildElements(Node parent, String childName) {
        if (parent == null) return Collections.emptyList();
        List<Node> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && childName.equals(child.getLocalName())) {
                children.add(child);
            }
        }
        return children;
    }

    private List<Node> nodeListToList(NodeList nodeList) {
        List<Node> list = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            list.add(nodeList.item(i));
        }
        return list;
    }

    private String nodeToString(Node node) {
        try {
            StringWriter writer = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            logger.warn("Could not serialize node to string.", e);
            return "<!-- Error serializing node -->";
        }
    }

    public String stripNamespace(String value) {
        if (value == null) return "";
        int colonIndex = value.indexOf(':');
        return (colonIndex != -1) ? value.substring(colonIndex + 1) : value;
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}