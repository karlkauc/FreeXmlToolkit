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
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.elementswrapper.ReferenceBase;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class XsdDocumentationService {

    static final int MAX_ALLOWED_DEPTH = 99;
    private final static Logger logger = LogManager.getLogger(XsdDocumentationService.class);

    String xsdFilePath;
    XsdDocumentationData xsdDocumentationData = new XsdDocumentationData();

    int counter;
    boolean debug = false;
    boolean parallelProcessing = false;

    public enum ImageOutputMethod {
        SVG,
        PNG
    }

    public ImageOutputMethod imageOutputMethod;
    Boolean generateSampleXml = false;
    Boolean useMarkdownRenderer = true;

    XsdParser parser;
    XmlService xmlService = XmlServiceImpl.getInstance();

    String schemaPrefix;

    XsdDocumentationHtmlService xsdDocumentationHtmlService = new XsdDocumentationHtmlService();
    private final SampleDataGenerator sampleDataGenerator = new SampleDataGenerator();

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    public XsdDocumentationService() {
        imageOutputMethod = ImageOutputMethod.SVG;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
        this.xsdDocumentationData.setXsdFilePath(xsdFilePath);
        this.schemaPrefix = getSchemaPrefix();
    }

    public ImageOutputMethod getMethod() {
        return imageOutputMethod;
    }

    public void setMethod(ImageOutputMethod method) {
        this.imageOutputMethod = method;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public void setXmlService(XmlService xmlService) {
        this.xmlService = xmlService;
    }

    public void setGenerateSampleXml(Boolean generateSampleXml) {
        this.generateSampleXml = generateSampleXml;
    }

    public Boolean getGenerateSampleXml() {
        return generateSampleXml;
    }

    public void setUseMarkdownRenderer(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
    }

    public void generateXsdDocumentation(File outputDirectory) {
        logger.debug("Bin in generateXsdDocumentation");
        processXsd(this.useMarkdownRenderer);

        xsdDocumentationHtmlService.setOutputDirectory(outputDirectory);
        xsdDocumentationHtmlService.setDocumentationData(xsdDocumentationData);
        xsdDocumentationHtmlService.setXsdDocumentationService(this);

        xsdDocumentationHtmlService.copyResources();
        xsdDocumentationHtmlService.generateRootPage();

        xsdDocumentationHtmlService.generateComplexTypesListPage();
        xsdDocumentationHtmlService.generateSimpleTypesListPage();
        xsdDocumentationHtmlService.generateDataDictionaryPage();

        if (parallelProcessing) {
            xsdDocumentationHtmlService.generateComplexTypePagesInParallel();
            xsdDocumentationHtmlService.generateSimpleTypePagesInParallel();
            xsdDocumentationHtmlService.generateDetailsPagesInParallel();
        } else {
            xsdDocumentationHtmlService.generateComplexTypePages();
            xsdDocumentationHtmlService.generateSimpleTypePages();
            xsdDocumentationHtmlService.generateDetailPages();
        }
    }

    void processXsd(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
        parser = new XsdParser(xsdFilePath);
        xmlService.setCurrentXmlFile(new File(xsdFilePath));

        xsdDocumentationData.setElements(parser.getResultXsdElements().collect(Collectors.toList()));
        xsdDocumentationData.setXmlSchema(parser.getResultXsdSchemas().toList());
        parser.getResultXsdSchemas().forEach(n -> xsdDocumentationData.getNamespaces().putAll(n.getNamespaces()));

        if (!xsdDocumentationData.getXmlSchema().isEmpty()) {
            XsdSchema rootSchema = xsdDocumentationData.getXmlSchema().getFirst();
            if (rootSchema.getVersion() != null) {
                xsdDocumentationData.setVersion(rootSchema.getVersion());
            }
        }

        counter = 0;

        for (XsdElement xsdElement : xsdDocumentationData.getElements()) {
            var elementName = xsdElement.getRawName();
            final Node startNode = xmlService.getNodeFromXpath("//" + this.schemaPrefix + ":element[@name='" + elementName + "']");
            getXsdAbstractElementInfo(0, xsdElement, List.of(), List.of(), startNode);
        }
    }

    public void getXsdAbstractElementInfo(int level,
                                          XsdAbstractElement xsdAbstractElement,
                                          List<String> prevElementTypes,
                                          List<String> prevElementPath,
                                          Node parentNode) {
        logger.debug("prevElementTypes = {}", prevElementTypes);
        if (level > MAX_ALLOWED_DEPTH) {
            logger.error("Too many elements");
            System.err.println("Too many elements");
            return;
        }

        ExtendedXsdElement extendedXsdElement = new ExtendedXsdElement();
        extendedXsdElement.setCounter(counter++);
        extendedXsdElement.setUseMarkdownRenderer(useMarkdownRenderer);

        // =================================================================================
        // NEU: Zentrale Logik zum Finden des DOM-Knotens und des Quellcodes
        // Dies wird für die meisten Elementtypen benötigt und vermeidet Code-Duplizierung.
        // =================================================================================
        Node currentNode = null;
        String elementNameForXpath = null;
        String nodeTypeForXpath = null;

        if (xsdAbstractElement instanceof XsdElement xsdElement) {
            elementNameForXpath = xsdElement.getRawName();
            nodeTypeForXpath = "element";
        } else if (xsdAbstractElement instanceof XsdAttribute xsdAttribute) {
            elementNameForXpath = xsdAttribute.getName();
            nodeTypeForXpath = "attribute";
        } else if (xsdAbstractElement instanceof XsdAny) {
            nodeTypeForXpath = "any";
        }

        if (nodeTypeForXpath != null) {
            String xpathQuery;
            if (elementNameForXpath != null) {
                // Sucht nach einem Tag mit einem bestimmten Namen
                xpathQuery = this.schemaPrefix + ":" + nodeTypeForXpath + "[@name='" + elementNameForXpath + "']";
            } else {
                // Sucht nur nach dem Tag-Typ (z.B. für <xs:any>)
                xpathQuery = this.schemaPrefix + ":" + nodeTypeForXpath;
            }

            // Die Suche wird relativ zum parentNode ausgeführt, um Eindeutigkeit zu gewährleisten.
            currentNode = xmlService.getNodeFromXpath("//" + xpathQuery, parentNode);

            // Fallback: Wenn der Knoten nicht direkt im Parent gefunden wird (z.B. bei globalen Typen),
            // wird der Parent selbst als Referenz verwendet.
            if (currentNode == null) {
                currentNode = parentNode;
            }

            extendedXsdElement.setCurrentNode(currentNode);
            extendedXsdElement.setSourceCode(xmlService.getNodeAsString(currentNode));
        }
        // =================================================================================
        // Ende der neuen zentralen Logik
        // =================================================================================

        switch (xsdAbstractElement) {
            case XsdElement xsdElement -> {
                logger.debug("ELEMENT: {}", xsdElement.getRawName());

                // 1. XPath und grundlegende Eigenschaften festlegen
                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = parentXpath + "/" + xsdElement.getName();
                if (prevElementPath.isEmpty()) {
                    currentXpath = "/" + xsdElement.getName();
                } else {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                }

                extendedXsdElement.setParentXpath(parentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);
                extendedXsdElement.setLevel(level);
                extendedXsdElement.setElementName(xsdElement.getRawName());
                extendedXsdElement.setXsdElement(xsdElement);

                if (xsdElement.getAnnotation() != null) {
                    if (xsdElement.getAnnotation().getDocumentations() != null) {
                        extendedXsdElement.setXsdDocumentation(xsdElement.getAnnotation().getDocumentations());
                    }
                }

                // 2. Elementtyp für die Anzeige bestimmen
                if (xsdElement.getType() != null) {
                    extendedXsdElement.setElementType(xsdElement.getType());
                } else if (xsdElement.getXsdSimpleType() != null && xsdElement.getXsdSimpleType().getRestriction() != null) {
                    extendedXsdElement.setElementType(xsdElement.getXsdSimpleType().getRestriction().getBase());
                } else {
                    extendedXsdElement.setElementType("(Complex Content)");
                }

                // 3. Rekursionsschutz
                var currentType = xsdElement.getType();
                if (currentType != null && prevElementTypes.stream().anyMatch(str -> str.trim().equals(currentType))) {
                    logger.info("Element {} schon bearbeitet (Rekursion erkannt).", currentType);
                    xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);
                    return;
                }

                ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                if (currentType != null) {
                    prevTemp.add(currentType);
                }
                ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                prevPathTemp.add(xsdElement.getName());

                // Das Element zur Map hinzufügen, bevor die Rekursion beginnt
                xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);

                // 4. Logik zur Verarbeitung basierend auf dem Inhalt des Elements
                if (xsdElement.getXsdComplexType() != null) {
                    XsdComplexType xsdComplexType = xsdElement.getXsdComplexType();
                    // Rekursion für Kind-Elemente und Attribute
                    if (xsdComplexType.getElements() != null) {
                        for (ReferenceBase referenceBase : xsdComplexType.getElements()) {
                            // WICHTIG: Der 'currentNode' wird als neuer 'parentNode' für die Kinder übergeben
                            getXsdAbstractElementInfo(level + 1, referenceBase.getElement(), prevTemp, prevPathTemp, currentNode);
                        }
                    }
                    if (xsdComplexType.getAllXsdAttributes() != null) {
                        Node finalCurrentNode = currentNode;
                        xsdComplexType.getAllXsdAttributes().forEach(xsdAttribute -> {
                            getXsdAbstractElementInfo(level + 1, xsdAttribute, prevTemp, prevPathTemp, finalCurrentNode);
                        });
                    }
                } else if (xsdElement.getXsdSimpleType() != null) {
                    if (xsdElement.getXsdSimpleType().getRestriction() != null) {
                        extendedXsdElement.setXsdRestriction(xsdElement.getXsdSimpleType().getRestriction());
                    }
                    extendedXsdElement.setSampleData(sampleDataGenerator.generate(extendedXsdElement));
                } else if (xsdElement.getType() != null) {
                    // Element mit zugewiesenem Typ (z.B. xs:dateTime)
                    extendedXsdElement.setSampleData(sampleDataGenerator.generate(extendedXsdElement));
                } else {
                    logger.warn("Element {} has no defined content. Treating as a leaf node.", xsdElement.getRawName());
                }
            }
            case XsdChoice xsdChoice -> {
                logger.debug("xsdChoice = {}", xsdChoice);
                for (ReferenceBase x : xsdChoice.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdSequence xsdSequence -> {
                logger.debug("xsdSequence = {}", xsdSequence);
                for (ReferenceBase x : xsdSequence.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdAttribute xsdAttribute -> {
                logger.debug("XsdAttribute = {}", xsdAttribute.getName());
                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = parentXpath + "/@" + xsdAttribute.getName();

                extendedXsdElement.setParentXpath(parentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);
                extendedXsdElement.setLevel(level);
                extendedXsdElement.setElementName("@" + xsdAttribute.getName());

                if (!prevElementPath.isEmpty()) {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                }

                if (xsdAttribute.getAnnotation() != null && xsdAttribute.getAnnotation().getDocumentations() != null) {
                    extendedXsdElement.setXsdDocumentation(xsdAttribute.getAnnotation().getDocumentations());
                }

                extendedXsdElement.setElementType(xsdAttribute.getType());
                if (xsdAttribute.getXsdSimpleType() != null && xsdAttribute.getXsdSimpleType().getRestriction() != null) {
                    extendedXsdElement.setXsdRestriction(xsdAttribute.getXsdSimpleType().getRestriction());
                    if (extendedXsdElement.getElementType() == null) {
                        extendedXsdElement.setElementType(xsdAttribute.getXsdSimpleType().getRestriction().getBase());
                    }
                }

                xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);
            }
            // ... andere cases wie XsdAny, XsdGroup etc. bleiben weitgehend gleich,
            // da sie entweder keine eigene Entität sind (Compositors) oder bereits
            // korrekt behandelt wurden. Die neue Logik oben fängt die wichtigen Fälle ab.

            // Fallback für Container-Elemente, die nur die Rekursion weiterleiten
            case XsdAll xsdAll -> {
                for (ReferenceBase x : xsdAll.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdGroup xsdGroup -> {
                for (ReferenceBase x : xsdGroup.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdAttributeGroup xsdAttributeGroup -> {
                for (ReferenceBase x : xsdAttributeGroup.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdExtension xsdExtension -> {
                for (ReferenceBase x : xsdExtension.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdComplexContent xsdComplexContent -> {
                if (xsdComplexContent.getXsdExtension() != null) {
                    getXsdAbstractElementInfo(level, xsdComplexContent.getXsdExtension(), prevElementTypes, prevElementPath, parentNode);
                }
                if (xsdComplexContent.getXsdRestriction() != null) {
                    getXsdAbstractElementInfo(level, xsdComplexContent.getXsdRestriction(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdSimpleContent xsdSimpleContent -> {
                if (xsdSimpleContent.getXsdExtension() != null) {
                    getXsdAbstractElementInfo(level, xsdSimpleContent.getXsdExtension(), prevElementTypes, prevElementPath, parentNode);
                }
                if (xsdSimpleContent.getXsdRestriction() != null) {
                    getXsdAbstractElementInfo(level, xsdSimpleContent.getXsdRestriction(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdRestriction xsdRestriction -> {
                if (xsdRestriction.getXsdAttributes() != null) {
                    xsdRestriction.getXsdAttributes().forEach(xsdAttribute -> {
                        getXsdAbstractElementInfo(level + 1, xsdAttribute, prevElementTypes, prevElementPath, parentNode);
                    });
                }
                for (ReferenceBase x : xsdRestriction.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdAny xsdAny -> {
                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = parentXpath + "/any_" + extendedXsdElement.getCounter();
                extendedXsdElement.setParentXpath(parentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);
                extendedXsdElement.setLevel(level);
                extendedXsdElement.setElementName("any");

                if (!prevElementPath.isEmpty()) {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                }
                if (xsdAny.getAnnotation() != null && xsdAny.getAnnotation().getDocumentations() != null) {
                    extendedXsdElement.setXsdDocumentation(xsdAny.getAnnotation().getDocumentations());
                }
                String namespaceInfo = xsdAny.getNamespace() != null ? xsdAny.getNamespace() : "##any";
                String processContentsInfo = xsdAny.getProcessContents() != null ? xsdAny.getProcessContents() : "strict";
                extendedXsdElement.setElementType(String.format("Wildcard (namespace: %s, process: %s)", namespaceInfo, processContentsInfo));

                xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);
            }
            default -> throw new IllegalStateException("Unexpected value: " + xsdAbstractElement);
        }
    }


    private static Document convertStringToDocument(String xmlStr) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlStr)));
        } catch (Exception e) {
            logger.error("Error in converting String to Document: {}", e.getMessage());
        }
        return null;
    }

    private static String convertDocumentToString(Document doc) {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = tf.newTransformer();
            // below code to remove XML declaration
            // transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.getBuffer().toString();
        } catch (TransformerException e) {
            logger.error("Error in converting Document to String: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Hauptmethode, die den rekursiven Baum des Wurzelelements für die grafische Ansicht zurückgibt.
     */
    public XsdNodeInfo getRootNodeInfo() {
        try {
            File xsdFile = new File(this.xsdFilePath);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); // Wichtig für XSDs
            Document doc = factory.newDocumentBuilder().parse(xsdFile);
            doc.getDocumentElement().normalize();

            Map<String, Element> complexTypeMap = getComplexTypeMap(doc);
            NodeList schemaChildren = doc.getDocumentElement().getChildNodes();

            for (int i = 0; i < schemaChildren.getLength(); i++) {
                Node node = schemaChildren.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && "element".equals(node.getLocalName())) {
                    // Starte den rekursiven Aufbau mit einer neuen, leeren Rekursionssperre
                    return buildNodeTree((Element) node, complexTypeMap, new java.util.HashSet<>());
                }
            }

        } catch (Exception e) {
            logger.error("Fehler beim Parsen des XSD für die Diagrammansicht.", e);
        }
        return null;
    }

    /**
     * Baut den Baum für ein gegebenes Element rekursiv auf und verhindert Endlosschleifen.
     *
     * @param elementNode    Das aktuelle DOM-Element.
     * @param complexTypeMap Eine Map aller globalen complexTypes.
     * @param recursionGuard Ein Set der auf dem aktuellen Pfad besuchten Typ-Namen, um Zyklen zu erkennen.
     * @return Ein XsdNodeInfo-Objekt, das diesen Knoten und seine Kinder repräsentiert.
     */
    private XsdNodeInfo buildNodeTree(Element elementNode, Map<String, Element> complexTypeMap, java.util.Set<String> recursionGuard) {
        String name = elementNode.getAttribute("name");
        String type = elementNode.getAttribute("type");
        String documentation = getDocumentation(elementNode);
        List<XsdNodeInfo> children = new ArrayList<>();

        // --- REKURSIONSSPERRE ---
        // Wenn der Typ dieses Elements bereits auf dem aktuellen Pfad besucht wurde,
        // brechen wir ab, um einen StackOverflowError zu verhindern.
        if (!type.isEmpty() && recursionGuard.contains(type)) {
            // Wir erstellen einen speziellen Knoten, um die Rekursion im UI anzuzeigen.
            String recursiveDoc = "Rekursiver Verweis auf '" + type + "'";
            return new XsdNodeInfo(name, type, recursiveDoc, List.of()); // Leere Kinderliste
        }

        // Füge den aktuellen Typ zur Sperre für tiefere Aufrufe hinzu.
        // Wir erstellen eine Kopie, damit Geschwister-Knoten nicht beeinflusst werden.
        java.util.Set<String> nextRecursionGuard = new java.util.HashSet<>(recursionGuard);
        if (!type.isEmpty()) {
            nextRecursionGuard.add(type);
        }
        // --- ENDE REKURSIONSSPERRE ---

        Element complexTypeDefinition = null;

        // Fall 1: Inline <complexType>
        if (type.isEmpty()) {
            NodeList directChildren = elementNode.getChildNodes();
            for (int i = 0; i < directChildren.getLength(); i++) {
                Node child = directChildren.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && "complexType".equals(child.getLocalName())) {
                    complexTypeDefinition = (Element) child;
                    break;
                }
            }
        }
        // Fall 2: Referenzierter <complexType>
        else {
            complexTypeDefinition = complexTypeMap.get(type);
        }

        if (complexTypeDefinition != null) {
            NodeList compositors = complexTypeDefinition.getChildNodes();
            for (int i = 0; i < compositors.getLength(); i++) {
                Node compositorNode = compositors.item(i);
                if (compositorNode.getNodeType() == Node.ELEMENT_NODE) {
                    String localName = compositorNode.getLocalName();
                    if ("sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName)) {
                        NodeList elementNodes = compositorNode.getChildNodes();
                        for (int j = 0; j < elementNodes.getLength(); j++) {
                            Node element = elementNodes.item(j);
                            if (element.getNodeType() == Node.ELEMENT_NODE && "element".equals(element.getLocalName())) {
                                // WICHTIG: Gib die aktualisierte Rekursionssperre weiter
                                children.add(buildNodeTree((Element) element, complexTypeMap, nextRecursionGuard));
                            }
                        }
                        break;
                    }
                }
            }
        }

        return new XsdNodeInfo(name, type, documentation, children);
    }

    /**
     * Extrahiert die Dokumentation aus einem Element-Knoten auf effiziente Weise.
     */
    private String getDocumentation(Element element) {
        // Wir suchen nur nach direkten <xs:annotation> Kindern des Elements.
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "annotation".equals(node.getLocalName())) {
                // Annotation gefunden. Jetzt das direkte <documentation> Kind davon suchen.
                // Dies vermeidet eine tiefe und teure Rekursion durch getElementsByTagNameNS.
                NodeList annotationChildren = node.getChildNodes();
                for (int j = 0; j < annotationChildren.getLength(); j++) {
                    Node docNode = annotationChildren.item(j);
                    if (docNode.getNodeType() == Node.ELEMENT_NODE && "documentation".equals(docNode.getLocalName())) {
                        // Dokumentation gefunden, Inhalt zurückgeben.
                        return docNode.getTextContent().trim();
                    }
                }
                // Annotation wurde gefunden, aber sie enthielt kein <documentation>-Tag.
                return "";
            }
        }
        // Keine Annotation für dieses Element gefunden.
        return "";
    }


    /**
     * Erstellt eine Map aller globalen complexTypes mit ihrem Namen als Schlüssel.
     */
    private Map<String, Element> getComplexTypeMap(Document doc) {
        Map<String, Element> map = new HashMap<>();
        NodeList typeNodes = doc.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = 0; i < typeNodes.getLength(); i++) {
            Element typeElement = (Element) typeNodes.item(i);
            // Nur globale complexTypes (direkte Kinder des Schemas) berücksichtigen.
            if (typeElement.getParentNode().isSameNode(doc.getDocumentElement())) {
                String name = typeElement.getAttribute("name");
                if (!name.isEmpty()) {
                    map.put(name, typeElement);
                }
            }
        }
        return map;
    }

    public String getSchemaPrefix() {
        try {
            // 1. Factory und Builder erstellen
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Wichtig: Namespace-Unterstützung aktivieren
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // 2. XSD-Datei parsen
            Document doc = builder.parse(new File(xsdFilePath));

            // 3. Wurzelelement holen
            Element rootElement = doc.getDocumentElement();

            // 4. Überprüfen, ob es sich um das Schema-Element handelt
            // Der "local name" ist der Name ohne Präfix.
            if (rootElement != null && "schema".equals(rootElement.getLocalName())) {
                // 5. Das Präfix zurückgeben
                return rootElement.getPrefix();
            }

        } catch (Exception e) {
            logger.error("Fehler beim Parsen der XML-Datei: {}", e.getMessage());
        }

        return null; // Falls etwas schiefgeht oder es kein Schema ist
    }
}