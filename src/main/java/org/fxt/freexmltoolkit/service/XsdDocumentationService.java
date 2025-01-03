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
import org.w3c.dom.Node;
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

    ImageOutputMethod method;
    Boolean generateSampleXml = false;
    Boolean useMarkdownRenderer = true;

    XsdParser parser;
    XmlService xmlService = XmlServiceImpl.getInstance();

    public XsdDocumentationService() {
        method = ImageOutputMethod.SVG;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
        this.xsdDocumentationData.setXsdFilePath(xsdFilePath);
    }

    public ImageOutputMethod getMethod() {
        return method;
    }

    public void setMethod(ImageOutputMethod method) {
        this.method = method;
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

        XsdDocumentationHtmlService xsdDocumentationHtmlService = new XsdDocumentationHtmlService();
        xsdDocumentationHtmlService.setOutputDirectory(outputDirectory);
        xsdDocumentationHtmlService.setDocumentationData(xsdDocumentationData);

        xsdDocumentationHtmlService.copyResources();
        xsdDocumentationHtmlService.generateRootPage();
        xsdDocumentationHtmlService.generateComplexTypePages();
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
            final Node startNode = xmlService.getNodeFromXpath("//xs:element[@name='" + elementName + "']");
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
                    Node nodeFromXpath = xmlService.getNodeFromXpath("//xs:element[@name='" + xsdElement.getRawName() + "']", parentNode);
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

                    var currentNode = xmlService.getNodeFromXpath("//xs:complexType[@name='" + xsdComplexType.getRawName() + "']", parentNode);
                    var s = xmlService.getXmlFromXpath("//xs:complexType[@name='" + xsdComplexType.getRawName() + "']");

                    if (currentNode == null) {
                        currentNode = parentNode;
                    }

                    extendedXsdElement.setSourceCode(s);
                    extendedXsdElement.setCurrentNode(currentNode);
                    extendedXsdElement.setLevel(level);
                    extendedXsdElement.setXsdElement(xsdElement);
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

                    var currentNode = xmlService.getNodeFromXpath("//xs:simpleType[@name='" + xsdSimpleType.getRawName() + "']", parentNode);
                    final var xmlFromXpath = xmlService.getXmlFromXpath("//xs:simpleType[@name='" + xsdSimpleType.getRawName() + "']");

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

}

