/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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
import org.fxt.freexmltoolkit.extendedXsd.ExtendedXsdElement;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.Node;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.elementswrapper.ReferenceBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class XsdDocumentationService {
    String xsdFilePath;
    static final int MAX_ALLOWED_DEPTH = 99;
    private final static Logger logger = LogManager.getLogger(XsdDocumentationService.class);

    private List<XsdComplexType> xsdComplexTypes;
    private List<XsdSimpleType> xsdSimpleTypes;
    private List<XsdElement> elements;

    int counter;

    XsdParser parser;
    List<XsdSchema> xmlSchema;
    Map<String, ExtendedXsdElement> extendedXsdElements;

    XmlService xmlService = XmlServiceImpl.getInstance();

    public String getXsdFilePath() {
        return xsdFilePath;
    }

    public XmlService getXmlService() {
        return xmlService;
    }

    public Map<String, ExtendedXsdElement> getExtendedXsdElements() {
        return extendedXsdElements;
    }

    public void setExtendedXsdElements(Map<String, ExtendedXsdElement> extendedXsdElements) {
        this.extendedXsdElements = extendedXsdElements;
    }

    public void setXmlService(XmlService xmlService) {
        this.xmlService = xmlService;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
    }

    public void generateDocumentation() {
        processXsd();
        generateHtml("schema-doc.html");
    }

    public void generateDocumentation(String outputFileName) {
        processXsd();
        generateHtml(outputFileName);
    }

    public void processXsd() {
        parser = new XsdParser(xsdFilePath);
        xmlService.setCurrentXmlFile(new File(xsdFilePath));

        elements = parser.getResultXsdElements().collect(Collectors.toList());
        xmlSchema = parser.getResultXsdSchemas().toList();

        xsdComplexTypes = xmlSchema.get(0).getChildrenComplexTypes().collect(Collectors.toList());
        xsdSimpleTypes = xmlSchema.get(0).getChildrenSimpleTypes().collect(Collectors.toList());

        extendedXsdElements = new LinkedHashMap<>();
        counter = 0;

        for (XsdElement xsdElement : elements) {
            var elementName = xsdElement.getRawName();
            Node startNode = xmlService.getNodeFromXpath("//xs:element[@name='" + elementName + "']");
            getXsdAbstractElementInfo(0, xsdElement, List.of(), List.of(), startNode);
        }

        /*
        for (Map.Entry<String, ExtendedXsdElement> entry : extendedXsdElements.entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue().getXsdElement().getName());
        }
         */
    }


    void getXsdAbstractElementInfo(int level,
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

        switch (xsdAbstractElement) {
            case XsdElement xsdElement -> {
                logger.debug("ELEMENT: {}", xsdElement.getRawName());

                String currentXpath = "/" + String.join("/", prevElementPath) + "/" + xsdElement.getName();
                if (prevElementPath.size() == 0) {
                    currentXpath = "/" + xsdElement.getName();
                }

                logger.debug("Current XPath = {}", currentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);
                extendedXsdElement.setLevel(level);

                var currentType = xsdElement.getType();

                if (currentType == null) {
                    /* reines element - kein complex/simple - aber children. in doku aufnehmen und mit kinder weiter machen */
                    Node n = xmlService.getNodeFromXpath("//xs:element[@name='" + xsdElement.getRawName() + "']", parentNode);
                    String elementString = xmlService.getNodeAsString(n);

                    extendedXsdElement.setCurrentNode(n);
                    // ZU VIEL: HTML FILE RENDERD NICHT MEHR
                    // extendedXsdElement.setSourceCode(elementString);
                    extendedXsdElement.setXsdElement(xsdElement);

                    if (xsdElement.getAnnotation() != null && xsdElement.getAnnotation().getDocumentations() != null) {
                        extendedXsdElement.setXsdDocumentation(xsdElement.getAnnotation().getDocumentations());
                    }

                    if (xsdElement.getXsdComplexType() != null) {
                        ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                        prevTemp.add(xsdElement.getName());

                        ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                        prevPathTemp.add(xsdElement.getName());

                        if (extendedXsdElement.getXsdElement().getName() == null) {
                            throw new RuntimeException("NAME NULL");
                        }

                        if (extendedXsdElement.getXsdElement().getName() == null) {
                            throw new RuntimeException("NAME NULL");
                        }
                        extendedXsdElements.put(currentXpath, extendedXsdElement);

                        if (xsdElement.getXsdComplexType().getElements() != null) {
                            for (ReferenceBase referenceBase : xsdElement.getXsdComplexType().getElements()) {
                                getXsdAbstractElementInfo(level + 1, referenceBase.getElement(), prevTemp, prevPathTemp, n);
                            }
                            return;
                        }
                    }
                }

                if (prevElementTypes.stream().anyMatch(str -> str.trim().equals(currentType))) {
                    System.out.println("ELEMENT SCHON BEARBEITET: " + currentType);
                    logger.warn("Element {} schon bearbeitet.", currentType);
                    return;
                } else {
                    logger.debug("noch nicht bearbeitet: {}", currentType);
                }

                // current type beginnt mit xs: oder nicht...
                System.out.println("currentType = " + currentType);
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

                    if (xsdComplexType.getAnnotation() != null && xsdComplexType.getAnnotation().getDocumentations() != null) {
                        extendedXsdElement.setXsdDocumentation(xsdComplexType.getAnnotation().getDocumentations());
                    }

                    if (currentNode == null) {
                        currentNode = parentNode;
                    }

                    // extendedXsdElement.setSourceCode(s);
                    extendedXsdElement.setCurrentNode(currentNode);
                    extendedXsdElement.setLevel(level);
                    extendedXsdElement.setXsdElement(xsdElement);
                    extendedXsdElements.put(currentXpath, extendedXsdElement);

                    if (xsdElement.getXsdComplexType().getElements() != null) {
                        for (ReferenceBase referenceBase : xsdElement.getXsdComplexType().getElements()) {
                            getXsdAbstractElementInfo(level + 1, referenceBase.getElement(), prevTemp, prevPathTemp, currentNode);
                        }
                        return;
                    }
                }
                if (xsdElement.getXsdSimpleType() != null) {
                    XsdSimpleType xsdSimpleType = xsdElement.getXsdSimpleType();
                    System.out.println("xsdSimpleType = " + xsdSimpleType.getName());

                    var currentNode = xmlService.getNodeFromXpath("//xs:simpleType[@name='" + xsdSimpleType.getRawName() + "']", parentNode);
                    var s = xmlService.getXmlFromXpath("//xs:simpleType[@name='" + xsdSimpleType.getRawName() + "']");

                    if (currentNode == null) {
                        currentNode = parentNode;
                    }

                    if (xsdSimpleType.getAnnotation() != null && xsdSimpleType.getAnnotation().getDocumentations() != null) {
                        extendedXsdElement.setXsdDocumentation(xsdSimpleType.getAnnotation().getDocumentations());
                    }

                    extendedXsdElement.setSourceCode(s);
                    extendedXsdElement.setCurrentNode(currentNode);
                    extendedXsdElement.setLevel(level);
                    extendedXsdElement.setXsdElement(xsdElement);
                    extendedXsdElements.put(currentXpath, extendedXsdElement);
                }
            }
            case XsdChoice xsdChoice -> {
                logger.debug("xsdChoice = " + xsdChoice);
                for (ReferenceBase x : xsdChoice.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdSequence xsdSequence -> {
                logger.debug("xsdSequence = " + xsdSequence);
                for (ReferenceBase x : xsdSequence.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdAll xsdAll -> {
                logger.debug("xsdAll = " + xsdAll);
                for (ReferenceBase x : xsdAll.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdGroup xsdGroup -> {
                logger.debug("xsdGroup = " + xsdGroup);
                for (ReferenceBase x : xsdGroup.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }
            case XsdAttributeGroup xsdAttributeGroup -> {
                logger.debug("xsdAttributeGroup = " + xsdAttributeGroup);
                for (ReferenceBase x : xsdAttributeGroup.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }

            default -> throw new IllegalStateException("Unexpected value: " + xsdAbstractElement);
        }
    }


    private void generateHtml(String outputFileName) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/xsdDocumentation/");
        resolver.setSuffix(".html");

        var templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        var context = new Context();
        context.setVariable("date", LocalDateTime.now().toString());

        context.setVariable("filename", xsdFilePath);
        context.setVariable("xmlSchema", xmlSchema.get(0));
        context.setVariable("xsdElements", elements);
        context.setVariable("xsdComplexTypes", xsdComplexTypes);
        context.setVariable("xsdSimpleTypes", xsdSimpleTypes);
        context.setVariable("extendedXsdElements", extendedXsdElements);

        var result = templateEngine.process("xsdTemplate", context);

        try {
            Files.write(Paths.get("output" + File.separator + outputFileName), result.getBytes());

            logger.debug("Written {} bytes", new File(outputFileName).length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
