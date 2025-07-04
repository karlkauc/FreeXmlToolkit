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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
        xsdDocumentationHtmlService.generateComplexTypePages();
        xsdDocumentationHtmlService.generateSimpleTypePages();

        if (parallelProcessing) {
            xsdDocumentationHtmlService.generateDetailsPagesInParallel();
        } else {
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

        switch (xsdAbstractElement) {
            case XsdElement xsdElement -> {
                logger.debug("ELEMENT: {}", xsdElement.getRawName());

                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = parentXpath + "/" + xsdElement.getName();
                if (prevElementPath.isEmpty()) {
                    currentXpath = "/" + xsdElement.getName();
                } else {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                    logger.debug("Added current Node {} to parent {}", currentXpath, parentXpath);
                }

                logger.debug("Current XPath = {}", currentXpath);
                extendedXsdElement.setParentXpath(parentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);
                extendedXsdElement.setLevel(level);
                extendedXsdElement.setElementName(xsdElement.getRawName());
                extendedXsdElement.setXsdElement(xsdElement);

                if (xsdElement.getAnnotation() != null && xsdElement.getAnnotation().getDocumentations() != null) {
                    extendedXsdElement.setXsdDocumentation(xsdElement.getAnnotation().getDocumentations());
                }

                if (xsdElement.getAnnotation() != null && xsdElement.getAnnotation().getAppInfoList() != null) {
                    List<XsdAppInfo> appInfoList = xsdElement.getAnnotation().getAppInfoList();
                    /*
                     <xs:appinfo>
                        <altova:exampleValues>
                            <altova:example value="WBAH"/>
                        </altova:exampleValues>
                    </xs:appinfo>
                     */
                    for (XsdAppInfo appInfo : appInfoList) {
                        if (appInfo.getSource() != null) {
                            logger.debug("SOURCE: {}", appInfo.getSource());
                        }
                        if (appInfo.getContent() != null) {
                            logger.debug("CONTENT: {}", appInfo.getContent());
                            var d = convertStringToDocument(appInfo.getContent());

                            String temp = convertDocumentToString(d);
                            logger.debug("TEMP: {}", temp);
                        }
                    }

                    // extendedXsdElement.setXsdAppInfo(xsdElement.getAnnotation().getAppInfoList());
                }

                if (xsdElement.getTypeAsBuiltInDataType() != null) {
                    logger.debug("BUILD IN DATATYPE {}", xsdElement.getTypeAsBuiltInDataType().getRawName());

                    extendedXsdElement.setElementType(xsdElement.getType());
                    extendedXsdElement.setSampleData(generateSampleData(extendedXsdElement, 0));
                    xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);
                }
                if (xsdElement.getTypeAsComplexType() != null) {
                    logger.debug("Complex DATATYPE {}", xsdElement.getTypeAsComplexType().getRawName());
                    // ToDo: als ENUM abspeichern
                    extendedXsdElement.setElementType(xsdElement.getTypeAsComplexType().getName());
                }
                if (xsdElement.getTypeAsSimpleType() != null) {
                    logger.debug("Simple DATATYPE {}", xsdElement.getTypeAsSimpleType().getRawName());
                    extendedXsdElement.setElementType("SIMPLE");
                }

                var currentType = xsdElement.getType();

                if (currentType == null) {
                    /* reines element - kein complex/simple - aber children. in doku aufnehmen und mit kinder weiter machen */
                    Node nodeFromXpath = xmlService.getNodeFromXpath("//" + this.schemaPrefix + ":element[@name='" + xsdElement.getRawName() + "']", parentNode);
                    String elementString = xmlService.getNodeAsString(nodeFromXpath);

                    extendedXsdElement.setCurrentNode(nodeFromXpath);
                    extendedXsdElement.setSourceCode(elementString);

                    if (xsdElement.getXsdComplexType() != null) {
                        ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                        prevTemp.add(xsdElement.getName());

                        ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                        prevPathTemp.add(xsdElement.getName());

                        extendedXsdElement.setElementType(""); // CURRENT - NULL
                        xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);
                        if (xsdElement.getXsdComplexType().getElements() != null) {
                            for (ReferenceBase referenceBase : xsdElement.getXsdComplexType().getElements()) {
                                getXsdAbstractElementInfo(level + 1, referenceBase.getElement(), prevTemp, prevPathTemp, nodeFromXpath);
                            }

                            // NEU: Verarbeite auch die Attribute des ComplexType
                            if (xsdElement.getXsdComplexType().getAllXsdAttributes() != null) {
                                xsdElement.getXsdComplexType().getAllXsdAttributes().forEach(xsdAttribute -> {
                                    // Wir rufen die Methode direkt mit dem xsdAttribute auf
                                    getXsdAbstractElementInfo(level + 1, xsdAttribute, prevTemp, prevPathTemp, nodeFromXpath);
                                });
                            }
                            return;
                        }
                    }
                }

                if (prevElementTypes.stream().anyMatch(str -> str.trim().equals(currentType))) {
                    // System.out.println("ELEMENT SCHON BEARBEITET: " + currentType);
                    logger.warn("Element {} schon bearbeitet.", currentType);
                    return;
                } else {
                    logger.debug("noch nicht bearbeitet: {}", currentType);
                }
                ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                prevTemp.add(currentType);

                ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                prevPathTemp.add(xsdElement.getName());

                // complex oder simple type
                if (xsdElement.getXsdComplexType() != null) {
                    XsdComplexType xsdComplexType = xsdElement.getXsdComplexType();
                    System.out.println("xsdComplexType.getName() = " + xsdComplexType.getName());

                    var currentNode = xmlService.getNodeFromXpath("//" + this.schemaPrefix + ":complexType[@name='" + xsdComplexType.getRawName() + "']", parentNode);
                    var s = xmlService.getXmlFromXpath("//" + this.schemaPrefix + ":complexType[@name='" + xsdComplexType.getRawName() + "']");

                    if (currentNode == null) {
                        currentNode = parentNode;
                    }

                    extendedXsdElement.setSourceCode(s);
                    extendedXsdElement.setCurrentNode(currentNode);
                    extendedXsdElement.setLevel(level);
                    extendedXsdElement.setXsdElement(xsdElement);
                    // NEU
                    // extendedXsdElement.setSampleData(generateSampleData(extendedXsdElement));
                    extendedXsdElement.setElementType(xsdComplexType.getName());

                    xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);

                    if (xsdElement.getXsdComplexType().getElements() != null) {
                        for (ReferenceBase referenceBase : xsdElement.getXsdComplexType().getElements()) {
                            getXsdAbstractElementInfo(level + 1, referenceBase.getElement(), prevTemp, prevPathTemp, currentNode);
                        }
                        return;
                    }
                }
                if (xsdElement.getXsdSimpleType() != null) {
                    final XsdSimpleType xsdSimpleType = xsdElement.getXsdSimpleType();
                    logger.debug("xsdSimpleType = {}", xsdSimpleType.getName());

                    var currentNode = xmlService.getNodeFromXpath("//" + this.schemaPrefix + ":simpleType[@name='" + xsdSimpleType.getRawName() + "']", parentNode);
                    final var xmlFromXpath = xmlService.getXmlFromXpath("//" + this.schemaPrefix + ":simpleType[@name='" + xsdSimpleType.getRawName() + "']");

                    if (currentNode == null) {
                        currentNode = parentNode;
                    }

                    if (xsdSimpleType.getRestriction() != null) {
                        extendedXsdElement.setXsdRestriction(xsdSimpleType.getRestriction());
                    }

                    if (xsdSimpleType.getRawName() != null) {
                        extendedXsdElement.setElementType(xsdSimpleType.getName());
                    } else {
                        if (xsdSimpleType.getRestriction().getBase() != null) {
                            extendedXsdElement.setElementType(xsdSimpleType.getRestriction().getBase());
                        }
                    }

                    extendedXsdElement.setSourceCode(xmlFromXpath);
                    extendedXsdElement.setCurrentNode(currentNode);
                    extendedXsdElement.setLevel(level);
                    extendedXsdElement.setXsdElement(xsdElement);
                    extendedXsdElement.setSampleData(generateSampleData(extendedXsdElement, 0));

                    xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);
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
            case XsdAll xsdAll -> {
                logger.debug("xsdAll = {}", xsdAll);
                for (ReferenceBase x : xsdAll.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdGroup xsdGroup -> {
                logger.debug("xsdGroup = {}", xsdGroup);
                for (ReferenceBase x : xsdGroup.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdAttributeGroup xsdAttributeGroup -> {
                logger.debug("xsdAttributeGroup = {}", xsdAttributeGroup);
                for (ReferenceBase x : xsdAttributeGroup.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdExtension xsdExtension -> {
                logger.debug("XsdExtension = {}", xsdExtension);
                for (ReferenceBase x : xsdExtension.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }

            case XsdAny xsdAny -> {
                logger.debug("XsdAny = {}", xsdAny);

                // Erstellt ein neues Dokumentations-Element für den xs:any Platzhalter.
                extendedXsdElement.setCounter(counter++);
                extendedXsdElement.setUseMarkdownRenderer(useMarkdownRenderer);
                extendedXsdElement.setLevel(level);
                extendedXsdElement.setElementName(this.schemaPrefix + ":any"); // Ein deskriptiver Name für die Anzeige

                // Da <xs:any> keinen 'name'-Attribut hat, erstellen wir einen eindeutigen XPath.
                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = parentXpath + "/any_" + extendedXsdElement.getCounter();
                extendedXsdElement.setParentXpath(parentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);

                // Verknüpft dieses Element mit seinem Eltern-Element in der Hierarchie.
                if (!prevElementPath.isEmpty()) {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                    logger.debug("Added current Node {} to parent {}", currentXpath, parentXpath);
                }

                // Extrahiert eine eventuell vorhandene Dokumentation (Annotation).
                if (xsdAny.getAnnotation() != null && xsdAny.getAnnotation().getDocumentations() != null) {
                    extendedXsdElement.setXsdDocumentation(xsdAny.getAnnotation().getDocumentations());
                }

                // Beschreibt den Typ des Elements basierend auf seinen Eigenschaften.
                String namespaceInfo = xsdAny.getNamespace() != null ? xsdAny.getNamespace() : "##any";
                String processContentsInfo = xsdAny.getProcessContents() != null ? xsdAny.getProcessContents() : "strict";
                extendedXsdElement.setElementType(String.format("Wildcard (namespace: %s, process: %s)", namespaceInfo, processContentsInfo));

                // Versucht, den Quellcode-Ausschnitt des <xs:any>-Tags zu finden.
                Node anyNode = xmlService.getNodeFromXpath(this.schemaPrefix + ":any", parentNode);
                if (anyNode != null) {
                    extendedXsdElement.setCurrentNode(anyNode);
                    extendedXsdElement.setSourceCode(xmlService.getNodeAsString(anyNode));
                }

                // Fügt das neue Element zur globalen Map hinzu.
                // extendedXsdElement.setSampleData(generateSampleData(extendedXsdElement));

                xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);

                // Keine rekursive Verarbeitung, da <xs:any> ein Endpunkt ist.
            }

            case XsdAttribute xsdAttribute -> {
                logger.debug("XsdAttribute = {}", xsdAttribute.getName());

                // Erstellt ein neues Dokumentations-Element für das Attribut.
                extendedXsdElement.setCounter(counter++);
                extendedXsdElement.setUseMarkdownRenderer(useMarkdownRenderer);
                extendedXsdElement.setLevel(level);
                // Das '@' ist eine gängige Konvention, um Attribute zu kennzeichnen.
                extendedXsdElement.setElementName("@" + xsdAttribute.getName());

                // Erstellt einen eindeutigen XPath für das Attribut.
                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = parentXpath + "/@" + xsdAttribute.getName();
                extendedXsdElement.setParentXpath(parentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);

                // Verknüpft dieses Attribut mit seinem Eltern-Element in der Hierarchie.
                if (!prevElementPath.isEmpty()) {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                    logger.debug("Added current Node {} to parent {}", currentXpath, parentXpath);
                }

                // Extrahiert eine eventuell vorhandene Dokumentation (Annotation).
                if (xsdAttribute.getAnnotation() != null && xsdAttribute.getAnnotation().getDocumentations() != null) {
                    extendedXsdElement.setXsdDocumentation(xsdAttribute.getAnnotation().getDocumentations());
                }

                // Setzt den Typ des Attributs.
                extendedXsdElement.setElementType(xsdAttribute.getType());
                if (xsdAttribute.getXsdSimpleType() != null && xsdAttribute.getXsdSimpleType().getRestriction() != null) {
                    extendedXsdElement.setXsdRestriction(xsdAttribute.getXsdSimpleType().getRestriction());
                    if (extendedXsdElement.getElementType() == null) {
                        extendedXsdElement.setElementType(xsdAttribute.getXsdSimpleType().getRestriction().getBase());
                    }
                }

                // Versucht, den Quellcode-Ausschnitt des <xs:attribute>-Tags zu finden.
                Node attributeNode = xmlService.getNodeFromXpath(this.schemaPrefix + ":attribute[@name='" + xsdAttribute.getName() + "']", parentNode);
                if (attributeNode != null) {
                    extendedXsdElement.setCurrentNode(attributeNode);
                    extendedXsdElement.setSourceCode(xmlService.getNodeAsString(attributeNode));
                }

                // extendedXsdElement.setSampleData(generateSampleData(extendedXsdElement));
                // Fügt das neue Element zur globalen Map hinzu.
                xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);

                // Keine rekursive Verarbeitung, da Attribute Endpunkte sind.
            }
            case XsdComplexContent xsdComplexContent -> {
                logger.debug("XsdComplexContent = {}", xsdComplexContent);
                // Dies ist ein Wrapper, der die Rekursion an seinen Inhalt (extension oder restriction) weiterleitet.
                // Die Ebene (level) wird beibehalten, da dies nur ein logischer Container ist.
                if (xsdComplexContent.getXsdExtension() != null) {
                    getXsdAbstractElementInfo(level, xsdComplexContent.getXsdExtension(), prevElementTypes, prevElementPath, parentNode);
                }
                if (xsdComplexContent.getXsdRestriction() != null) {
                    getXsdAbstractElementInfo(level, xsdComplexContent.getXsdRestriction(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdSimpleContent xsdSimpleContent -> {
                logger.debug("XsdSimpleContent = {}", xsdSimpleContent);
                // Dies ist ein Wrapper, der die Rekursion an seinen Inhalt (extension oder restriction) weiterleitet.
                if (xsdSimpleContent.getXsdExtension() != null) {
                    getXsdAbstractElementInfo(level, xsdSimpleContent.getXsdExtension(), prevElementTypes, prevElementPath, parentNode);
                }
                if (xsdSimpleContent.getXsdRestriction() != null) {
                    getXsdAbstractElementInfo(level, xsdSimpleContent.getXsdRestriction(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdRestriction xsdRestriction -> {
                logger.debug("XsdRestriction = {}", xsdRestriction);
                // Eine Restriction kann Attribute und Kind-Elemente (z.B. in einem Kompositor) enthalten.
                // Wir leiten die Verarbeitung an diese weiter.

                // Verarbeite Attribute in der Restriction
                if (xsdRestriction.getXsdAttributes() != null) {
                    xsdRestriction.getXsdAttributes().forEach(xsdAttribute -> {
                        getXsdAbstractElementInfo(level + 1, xsdAttribute, prevElementTypes, prevElementPath, parentNode);
                    });
                }

                // Verarbeite Kind-Elemente in der Restriction (falls vorhanden)
                for (ReferenceBase x : xsdRestriction.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
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

    /**
     * Generiert Beispieldaten für ein gegebenes XSD-Element unter Berücksichtigung
     * von Datentypen und Einschränkungen.
     *
     * @param element Das ExtendedXsdElement, für das Daten generiert werden sollen.
     * @return Ein String mit den passenden Beispieldaten.
     */
    private String generateSampleData(ExtendedXsdElement element, int recursionDepth) {
        if (element == null || recursionDepth > 10) { // Maximale Rekursionstiefe
            return "";
        }

        // Priorität 1: AppInfo mit Altova-Beispieldaten
        if (element.getXsdElement() != null && element.getXsdElement().getAnnotation() != null) {
            List<XsdAppInfo> appInfoList = element.getXsdElement().getAnnotation().getAppInfoList();
            if (appInfoList != null) {
                for (XsdAppInfo appInfo : appInfoList) {
                    if (appInfo.getContent() != null) {
                        Document appInfoDoc = convertStringToDocument(appInfo.getContent());
                        if (appInfoDoc != null) {
                            // Das Wurzelelement sollte <...:exampleValues> sein
                            Element root = appInfoDoc.getDocumentElement();
                            // Wir prüfen nur den lokalen Namen, um Namespace-Präfixe zu ignorieren
                            if (root != null && "altova:exampleValues".equals(root.getNodeName())) {
                                // Finde das <...:example> Kind-Element
                                NodeList children = root.getChildNodes();
                                for (int i = 0; i < children.getLength(); i++) {
                                    Node child = children.item(i);
                                    if (child.getNodeType() == Node.ELEMENT_NODE && "altova:example".equals(child.getNodeName())) {
                                        Element exampleElement = (Element) child;
                                        if (exampleElement.hasAttribute("value")) {
                                            String exampleValue = exampleElement.getAttribute("value");
                                            if (!exampleValue.isBlank()) {
                                                logger.debug("Found sample data in appinfo for {}: {}", element.getElementName(), exampleValue);
                                                return exampleValue; // Wert gefunden, sofort zurückgeben
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        // Priorität 2: Enumerations (Auswahllisten)
        XsdRestriction restriction = element.getXsdRestriction();
        if (restriction != null && restriction.getEnumeration() != null && !restriction.getEnumeration().isEmpty()) {
            return restriction.getEnumeration().get(0).getValue();
        }

        // Priorität 3: Datentyp-basierte Generierung
        String elementType = element.getElementType();
        if (elementType == null && restriction != null) {
            elementType = restriction.getBase();
        }

        // Für komplexe Typen ohne einfachen Inhalt gibt es keine direkten Beispieldaten
        if (elementType == null || element.getXsdElement() != null && element.getXsdElement().getXsdComplexType() != null) {
            return "...";
        }

        // Normalisiere den Typnamen (z.B. "xs:string" -> "string")
        String finalType = elementType.substring(elementType.lastIndexOf(":") + 1);

        switch (finalType.toLowerCase()) {
            case "string", "token", "normalizedstring", "language", "name", "ncname":
                String sample = "Beispieltext";
                if ("language".equalsIgnoreCase(finalType)) {
                    sample = "DE"; // Spezifisches Beispiel für 'language'
                }
                if (restriction != null) {
                    if (restriction.getLength() != null) {
                        int len = restriction.getLength().getValue();
                        sample = "x".repeat(Math.max(0, len));
                    } else {
                        if (restriction.getMinLength() != null) {
                            int min = restriction.getMinLength().getValue();
                            while (sample.length() < min) {
                                sample += " Text";
                            }
                        }
                        if (restriction.getMaxLength() != null) {
                            int max = restriction.getMaxLength().getValue();
                            if (sample.length() > max) {
                                sample = sample.substring(0, max);
                            }
                        }
                    }
                }
                return sample;

            case "decimal":
                if (restriction != null && restriction.getMinInclusive() != null) {
                    return restriction.getMinInclusive().getValue();
                }
                return "123.45";

            case "integer", "positiveinteger", "nonnegativeinteger", "negativeinteger", "nonpositiveinteger", "long", "int", "short", "byte", "unsignedlong", "unsignedint", "unsignedshort", "unsignedbyte":
                if (restriction != null && restriction.getMinInclusive() != null) {
                    return restriction.getMinInclusive().getValue();
                }
                return "100";

            case "date":
                return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE); // z.B. "2024-10-26"
            case "datetime":
                return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); // z.B. "2024-10-26T10:00:00"
            case "time":
                return LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME); // z.B. "10:00:00"
            case "gyear":
                return String.valueOf(LocalDate.now().getYear()); // z.B. "2024"

            case "boolean":
                return "true";

            default:
                if (restriction != null && restriction.getBase() != null) {
                    ExtendedXsdElement tempElement = new ExtendedXsdElement();
                    tempElement.setElementType(restriction.getBase());
                    tempElement.setXsdRestriction(restriction);
                    return generateSampleData(tempElement, recursionDepth + 1);
                }
                return "";
        }
    }
}