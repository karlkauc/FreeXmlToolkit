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
import org.apache.logging.log4j.util.Assert;
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
import java.nio.file.StandardCopyOption;
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

    public List<XsdComplexType> getXsdComplexTypes() {
        return xsdComplexTypes;
    }

    public List<XsdSimpleType> getXsdSimpleTypes() {
        return xsdSimpleTypes;
    }

    public List<XsdElement> getElements() {
        return elements;
    }

    public List<XsdSchema> getXmlSchema() {
        return xmlSchema;
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

    public void generateXsdDocumentation(File outputDirectory) throws IOException {
        // assertNotNull(outputDirectory);

        Files.createDirectories(outputDirectory.toPath());
        Files.createDirectories(Paths.get(outputDirectory.getPath(), "assets"));

        try {
            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/bootstrap.bundle.min.js"), Paths.get(outputDirectory.getPath(), "assets", "bootstrap.bundle.min.js"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/bootstrap.min.css"), Paths.get(outputDirectory.getPath(), "assets", "bootstrap.min.css"), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/prism.css"), Paths.get(outputDirectory.getPath(), "assets", "prism.css"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/prism.js"), Paths.get(outputDirectory.getPath(), "assets", "prism.js"), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/freeXmlTookit.css"), Paths.get(outputDirectory.getPath(), "assets", "freeXmlTookit.css"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        generateRootHtml(outputDirectory);

    }


    private void generateRootHtml(File outputDirectory) {

        var resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/xsdDocumentation/");
        resolver.setSuffix(".html");

        var templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        var context = new Context();
        context.setVariable("date", LocalDateTime.now().toString());

        var rootElementName = ((XsdElement) xmlSchema.get(0).getElements().get(0).getElement()).getName();

        context.setVariable("rootElement", xmlSchema.get(0));
        context.setVariable("rootElementName", rootElementName);

        context.setVariable("filename", xsdFilePath);
        context.setVariable("xmlSchema", xmlSchema.get(0));

        if (extendedXsdElements.get(rootElementName) != null &&
                extendedXsdElements.get(rootElementName).getSourceCode() != null) {
            context.setVariable("code", extendedXsdElements.get(rootElementName).getSourceCode());
        } else {
            context.setVariable("code", "NA");
        }

        context.setVariable("xsdElements", elements);
        context.setVariable("xsdComplexTypes", xsdComplexTypes);
        context.setVariable("xsdSimpleTypes", xsdSimpleTypes);
        context.setVariable("extendedXsdElements", extendedXsdElements);

        var result = templateEngine.process("rootElement", context);

        var outputFileName = "index.html";

        try {
            Files.write(Paths.get(outputDirectory + File.separator + outputFileName), result.getBytes());

            logger.debug("Written {} bytes", new File(outputFileName).length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void generateDocumentation(String outputFileName) {
        Assert.requireNonEmpty(this.xsdFilePath);

        processXsd();
        generateHtml(outputFileName);
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
            final Node startNode = xmlService.getNodeFromXpath("//xs:element[@name='" + elementName + "']");
            getXsdAbstractElementInfo(0, xsdElement, List.of(), List.of(), startNode);
        }
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

                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = parentXpath + "/" + xsdElement.getName();
                if (prevElementPath.isEmpty()) {
                    currentXpath = "/" + xsdElement.getName();
                } else {
                    extendedXsdElements.get(parentXpath).getChildren().add(currentXpath);
                    logger.debug("Added current Node {} to parent {}", currentXpath, parentXpath);
                }

                logger.debug("Current XPath = {}", currentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);
                extendedXsdElement.setLevel(level);
                extendedXsdElement.setElementName(xsdElement.getRawName());
                extendedXsdElement.setXsdElement(xsdElement);

                if (xsdElement.getTypeAsBuiltInDataType() != null) {
                    logger.debug("BUILD IN DATATYPE {}", xsdElement.getTypeAsBuiltInDataType().getRawName());

                    // hier setzten!!!
                    extendedXsdElement.setElementName(xsdElement.getRawName());
                    extendedXsdElement.setLevel(level);
                    extendedXsdElement.setXsdElement(xsdElement);
                    extendedXsdElement.setElementType("BUILDIN");
                    extendedXsdElements.put(currentXpath, extendedXsdElement);
                }
                if (xsdElement.getTypeAsComplexType() != null) {
                    logger.debug("Complex DATATYPE {}", xsdElement.getTypeAsComplexType().getRawName());
                    // ToDo: als ENUM abspeichern
                    extendedXsdElement.setElementType("COMPLEX");
                }
                if (xsdElement.getTypeAsSimpleType() != null) {
                    logger.debug("Simple DATATYPE {}", xsdElement.getTypeAsSimpleType().getRawName());
                    extendedXsdElement.setElementType("SIMPLE");
                }

                var currentType = xsdElement.getType();

                if (currentType == null) {
                    /* reines element - kein complex/simple - aber children. in doku aufnehmen und mit kinder weiter machen */
                    Node n = xmlService.getNodeFromXpath("//xs:element[@name='" + xsdElement.getRawName() + "']", parentNode);
                    String elementString = xmlService.getNodeAsString(n);

                    extendedXsdElement.setCurrentNode(n);
                    // ZU VIEL: HTML FILE RENDERED NICHT MEHR
                    extendedXsdElement.setSourceCode(elementString);

                    if (xsdElement.getAnnotation() != null && xsdElement.getAnnotation().getDocumentations() != null) {
                        extendedXsdElement.setXsdDocumentation(xsdElement.getAnnotation().getDocumentations());
                    }

                    if (xsdElement.getXsdComplexType() != null) {
                        ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                        prevTemp.add(xsdElement.getName());

                        ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                        prevPathTemp.add(xsdElement.getName());

                        extendedXsdElement.setElementType("CURRENT - NULL");
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
                    extendedXsdElement.setElementType("COMPLEX");
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
                    extendedXsdElement.setElementType("SIMPLE");
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
}
