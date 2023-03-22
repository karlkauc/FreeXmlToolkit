/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.xsdrestrictions.XsdEnumeration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class XsdDocumentationService {
    String xsdFilePath;
    static final int MAX_ALLOWED_DEPTH = 99;
    private final static Logger logger = LogManager.getLogger(XsdDocumentationService.class);

    private List<XsdComplexType> xsdComplexTypes;
    private List<XsdSimpleType> xsdSimpleTypes;
    private List<XsdElement> elements;

    XsdParser parser;
    List<XsdSchema> xmlSchema;
    List<ExtendedXsdElement> extendedXsdElements;

    XmlService xmlService = XmlServiceImpl.getInstance();

    public String getXsdFilePath() {
        return xsdFilePath;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
    }

    public void generateDocumentation(String outputFileName) {
        processXsd();
        generateHtml(outputFileName);
    }


    public void generateDocumentation() {
        processXsd();
        generateHtml("schemadoc.html");
    }

    private void processXsd() {
        parser = new XsdParser(xsdFilePath);

        xmlService.setCurrentXsdFile(new File(xsdFilePath));

        elements = parser.getResultXsdElements().collect(Collectors.toList());
        xmlSchema = parser.getResultXsdSchemas().toList();

        xsdComplexTypes = xmlSchema.get(0).getChildrenComplexTypes().collect(Collectors.toList());
        xsdSimpleTypes = xmlSchema.get(0).getChildrenSimpleTypes().collect(Collectors.toList());

        extendedXsdElements = new ArrayList<>();

        for (XsdElement xsdElement : elements) {
            getXsdAbstractElementInfo(0, xsdElement, List.of(xsdElement.getName()), List.of());
        }
    }

    private void generateHtml(String outputFileName) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/template/");
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
            Files.write(Paths.get("output//" + outputFileName), result.getBytes());

            logger.debug("Written {} bytes", new File(outputFileName).length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void getXsdAbstractElementInfo(int level, XsdAbstractElement xsdAbstractElement, List<String> prevElementTypes, List<String> prevElementPath) {
        logger.debug("prevElementTypes = {}", prevElementTypes);
        if (level > MAX_ALLOWED_DEPTH) {
            logger.error("Too many elements");
            System.err.println("Too many elements");
            return;
        }

        if (xsdAbstractElement instanceof XsdElement currentXsdElement) {
            final String currentXpath = "/" + String.join("/", prevElementPath) + "/" + currentXsdElement.getName();
            logger.debug("Current XPath = {}", currentXpath);

            var currentType = currentXsdElement.getType();
            logger.debug("Current Type: " + currentType);
            logger.debug("Current Name: {}", currentXsdElement.getRawName());

            ExtendedXsdElement extendedXsdElement = new ExtendedXsdElement();
            extendedXsdElement.setXsdElement(currentXsdElement);
            extendedXsdElement.setLevel(level);
            extendedXsdElement.setCurrentXpath(currentXpath);

            extendedXsdElement.setSourceCode(xmlService.getXmlFromXpath(currentXpath));

            if (currentXsdElement.getAnnotation() != null && currentXsdElement.getAnnotation().getDocumentations() != null) {
                extendedXsdElement.setXsdDocumentation(currentXsdElement.getAnnotation().getDocumentations());

                for (XsdAppInfo xsdAppInfo : currentXsdElement.getAnnotation().getAppInfoList()) {
                    logger.debug("App Info: {}", xsdAppInfo.getContent());
                }

                for (XsdDocumentation xsdDocumentation : currentXsdElement.getAnnotation().getDocumentations()) {
                    logger.debug("Documentation: {}", xsdDocumentation.getContent());
                    logger.debug("Documentation Attributest: {}", xsdDocumentation.getAttributesMap());
                }
            }

            extendedXsdElements.add(extendedXsdElement);

            if (prevElementTypes.stream().anyMatch(str -> str.trim().equals(currentType))) {
                System.out.println("ELEMENT SCHON BEARBEITET: " + currentType);
                logger.warn("Element {} schon bearbeitet.", currentType);
                return;
            } else {
                logger.debug("noch nicht bearbeitet: {}", currentType);
            }
            System.out.println("LEVEL: " + level + " - RAW NAME: " + currentXsdElement.getRawName());

            if (currentXsdElement.getXsdComplexType() != null) {
                System.out.println("TYPE: " + currentXsdElement.getXsdComplexType().getRawName()); // entweder null oder Type (IdentifiersType)

                if (currentXsdElement.getXsdComplexType() != null && currentXsdElement.getXsdComplexType().getXsdChildElement() != null) {
                    logger.debug("Attributes Complex Type: {}", currentXsdElement.getXsdComplexType().getAttributesMap());

                    XsdAbstractElement element = currentXsdElement.getXsdComplexType().getXsdChildElement();
                    var allElements = element.getXsdElements().toList();
                    for (XsdAbstractElement allElement : allElements) {
                        ArrayList<String> prevElementTypeList = new ArrayList<>(prevElementTypes);
                        if (currentXsdElement.getXsdComplexType().getRawName() != null) {
                            prevElementTypeList.add(currentXsdElement.getXsdComplexType().getRawName());
                        }

                        ArrayList<String> prevElementPathList = new ArrayList<>(prevElementPath);
                        if (currentXsdElement.getRawName() != null) {
                            prevElementPathList.add(currentXsdElement.getRawName());
                        }

                        getXsdAbstractElementInfo(level + 1, allElement, prevElementTypeList, prevElementPathList);
                    }
                }
            }
            if (currentXsdElement.getXsdSimpleType() != null) {
                logger.debug("SIMPLE: " + currentXsdElement.getXsdSimpleType().getAttributesMap());
                var simpleType = currentXsdElement.getXsdSimpleType();

                // xsdHtmlGenerator.generateSimpleType(currentXsdElement, level, currentXpath);

                logger.debug("Attributes Simple Type: {}", currentXsdElement.getXsdSimpleType().getAttributesMap());
                logger.debug("current type: {}", currentType);
                logger.debug("simple Type base: {}", simpleType.getRestriction().getBase());

                if (currentType != null && simpleType.getRestriction().getBase() != null) {
                    // documentation.append("Build in Type: [").append(currentType).append("] ").append(System.lineSeparator());
                }
                if (currentType != null && simpleType.getRestriction().getBase() == null) {
                    // documentation.append("Custom Type: [").append(currentType).append("] ").append(System.lineSeparator());
                }
                if (simpleType.getRestriction().getBase() != null) {
                    // documentation.append("BaseType: ").append(simpleType.getRestriction().getBase()).append(System.lineSeparator());
                }

                if (simpleType.getRestriction().getEnumeration().size() > 0) {
                    /*documentation.append(System.lineSeparator())
                            .append("ENUMERATION: ")
                            .append(System.lineSeparator());
                     */

                    var elementEnum = simpleType.getRestriction().getEnumeration();
                    for (XsdEnumeration xsdEnumeration : elementEnum) {
                        logger.debug("xsdEnumeration.getValue() = " + xsdEnumeration.getValue());
                    }

                    var t = xsdSimpleTypes.stream().filter(x -> x.getRawName().equals(currentXsdElement.getType())).findFirst();
                    if (t.isPresent()) {
                        /*
                        documentation.append(" Restricition: ")
                                .append(t.get().getRestriction().getAttributesMap())
                                .append(System.lineSeparator())
                                .append(System.lineSeparator());

                        var restriction = t.get().getRestriction();
                        documentation.append("| KEY | Value | ")
                                .append(System.lineSeparator())
                                .append("| --- | --- | ")
                                .append(System.lineSeparator());

                        documentation.append("| Max Length | ")
                                .append((restriction.getMaxLength() == null ? "" : restriction.getMaxLength().getValue()))
                                .append(" |")
                                .append(System.lineSeparator());

                        documentation.append("| Min Length | ")
                                .append((restriction.getMinLength() == null ? "" : Integer.valueOf(restriction.getMinLength().getValue())))
                                .append(" |")
                                .append(System.lineSeparator());

                        documentation.append("| Total Digets | ")
                                .append((restriction.getTotalDigits() == null ? "" : restriction.getTotalDigits().getValue()))
                                .append(" |")
                                .append(System.lineSeparator());

                        documentation.append("| Pattern | ")
                                .append((restriction.getPattern() == null ? "" : "`" + restriction.getPattern().getValue() + "`"))
                                .append(" |")
                                .append(System.lineSeparator());
                         */
                    }

                    // documentation.append(System.lineSeparator()).append(System.lineSeparator());
                }
            }
        }

        if (xsdAbstractElement instanceof XsdChoice xsdChoice) {
            System.out.println("XsdChoice = " + xsdAbstractElement.getClass().getName());
            System.out.println("xsdChoice = " + xsdChoice.getAttributesMap());
        }

        if (xsdAbstractElement instanceof XsdSequence xsdSequence) {
            System.out.println("XsdSequence = " + xsdAbstractElement.getClass().getName());

            System.out.println("((XsdSequence) xsdAbstractElement).getId() = " + xsdSequence.getId());
            System.out.println("sequence = " + xsdSequence.getAttributesMap());
        }
    }

}
