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
import org.fxt.freexmltoolkit.extendedXsd.ExtendedXsdElement;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.core.utils.NamespaceInfo;
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
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class XsdDocumentationService {

    public static final String ASSETS_PATH = "assets";
    String xsdFilePath;
    static final int MAX_ALLOWED_DEPTH = 99;
    private final static Logger logger = LogManager.getLogger(XsdDocumentationService.class);

    private List<XsdComplexType> xsdComplexTypes;
    private List<XsdSimpleType> xsdSimpleTypes;
    private List<XsdElement> elements;

    int counter;

    boolean debug = false;

    public enum ImageOutputMethod {
        SVG,
        PNG
    }

    ImageOutputMethod method;
    Boolean generateSampleXml = false;
    Boolean useMarkdownRenderer = true;

    XsdParser parser;
    List<XsdSchema> xmlSchema;
    Map<String, ExtendedXsdElement> extendedXsdElements;
    Map<String, NamespaceInfo> namespaces = new HashMap<>();

    XmlService xmlService = XmlServiceImpl.getInstance();

    StringBuilder xpath;
    ClassLoaderTemplateResolver resolver;
    TemplateEngine templateEngine;

    public XsdDocumentationService() {
        resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("xsdDocumentation/");
        resolver.setSuffix(".html");

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        method = ImageOutputMethod.SVG;
    }

    public ImageOutputMethod getMethod() {
        return method;
    }

    public void setMethod(ImageOutputMethod method) {
        this.method = method;
    }

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

    public void setGenerateSampleXml(Boolean generateSampleXml) {
        this.generateSampleXml = generateSampleXml;
    }

    public Boolean getGenerateSampleXml() {
        return generateSampleXml;
    }

    public void setUseMarkdownRenderer(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
    }

    public void generateXsdDocumentation(File outputDirectory, ImageOutputMethod method) {
        this.method = method;

        copyResources(outputDirectory);
        processXsd(this.useMarkdownRenderer);
        generateRootPage(outputDirectory);
        generateComplexTypePages(outputDirectory);
        generateDetailPages(outputDirectory);
    }

    public void generateXsdDocumentation(File outputDirectory) {
        logger.debug("Bin in generateXsdDocumentation");

        copyResources(outputDirectory);
        processXsd(this.useMarkdownRenderer);
        generateRootPage(outputDirectory);
        generateComplexTypePages(outputDirectory);
        generateDetailPages(outputDirectory);
    }

    void processXsd(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
        parser = new XsdParser(xsdFilePath);
        xmlService.setCurrentXmlFile(new File(xsdFilePath));

        elements = parser.getResultXsdElements().collect(Collectors.toList());
        xmlSchema = parser.getResultXsdSchemas().toList();

        xsdComplexTypes = xmlSchema.getFirst().getChildrenComplexTypes().collect(Collectors.toList());
        xsdSimpleTypes = xmlSchema.getFirst().getChildrenSimpleTypes().collect(Collectors.toList());

        extendedXsdElements = new LinkedHashMap<>();
        counter = 0;

        parser.getResultXsdSchemas().forEach(n -> namespaces.putAll(n.getNamespaces()));

        for (XsdElement xsdElement : elements) {
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
                    extendedXsdElements.get(parentXpath).getChildren().add(currentXpath);
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
                    Node nodeFromXpath = xmlService.getNodeFromXpath("//xs:element[@name='" + xsdElement.getRawName() + "']", parentNode);
                    String elementString = xmlService.getNodeAsString(nodeFromXpath);

                    extendedXsdElement.setCurrentNode(nodeFromXpath);
                    extendedXsdElement.setSourceCode(elementString);

                    if (xsdElement.getXsdComplexType() != null) {
                        ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                        prevTemp.add(xsdElement.getName());

                        ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                        prevPathTemp.add(xsdElement.getName());

                        extendedXsdElement.setElementType("CURRENT - NULL");
                        extendedXsdElements.put(currentXpath, extendedXsdElement);
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

                // current type beginnt mit xs: oder nicht ...
                logger.debug("currentType = {}", currentType);
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

                    extendedXsdElement.setSourceCode(xmlFromXpath);
                    extendedXsdElement.setCurrentNode(currentNode);
                    extendedXsdElement.setLevel(level);
                    extendedXsdElement.setXsdElement(xsdElement);
                    extendedXsdElement.setElementType("SIMPLE");
                    extendedXsdElements.put(currentXpath, extendedXsdElement);
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

    void copyResources(File outputDirectory) {
        try {
            Files.createDirectories(outputDirectory.toPath());
            Files.createDirectories(Paths.get(outputDirectory.getPath(), ASSETS_PATH));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "details"));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "complexTypes"));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "simpleTypes"));


            copyAssets("/xsdDocumentation/assets/bootstrap.bundle.min.js", outputDirectory);
            copyAssets("/xsdDocumentation/assets/prism.js", outputDirectory);
            copyAssets("/xsdDocumentation/assets/freeXmlToolkit.css", outputDirectory);
            copyAssets("/xsdDocumentation/assets/plus.png", outputDirectory);
            copyAssets("/xsdDocumentation/assets/logo.png", outputDirectory);
            copyAssets("/xsdDocumentation/assets/Roboto-Regular.ttf", outputDirectory);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void copyAssets(String resourcePath, File assetsDirectory) throws Exception {
        copyResource(resourcePath, assetsDirectory, ASSETS_PATH);
    }

    private void copyResource(String resourcePath, File outputDirectory, String targetPath) throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream(resourcePath)), Paths.get(outputDirectory.getPath(), targetPath, new File(resourcePath).getName()), StandardCopyOption.REPLACE_EXISTING);
    }

    void generateRootPage(File outputDirectory) {
        final var rootElementName = elements.getFirst().getName();

        var context = new Context();
        context.setVariable("date", LocalDate.now());
        context.setVariable("filename", this.getXsdFilePath());
        context.setVariable("rootElementName", rootElementName);
        context.setVariable("rootElement", getXmlSchema().getFirst());
        context.setVariable("xsdElements", elements.getFirst());
        context.setVariable("xsdComplexTypes", getXsdComplexTypes());
        context.setVariable("xsdSimpleTypes", getXsdSimpleTypes());
        context.setVariable("namespace", getNameSpacesAsString());
        context.setVariable("targetNamespace", getXmlSchema().getFirst().getTargetNamespace());
        context.setVariable("rootElementLink", "details/" + getExtendedXsdElements().get("/" + rootElementName).getPageName());

        final var result = templateEngine.process("templateRootElement", context);
        final var outputFileName = Paths.get(outputDirectory.getPath(), "index.html").toFile().getAbsolutePath();
        logger.debug("Root File: {}", outputFileName);

        try {
            Files.write(Paths.get(outputFileName), result.getBytes());
            logger.debug("Written {} bytes in File '{}'", new File(outputFileName).length(), outputFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void generateComplexTypePages(File outputDirectory) {
        logger.debug("Complex Types");

        for (var complexType : getXsdComplexTypes()) {
            var context = new Context();
            context.setVariable("complexType", complexType);

            if (complexType.getAnnotation() != null) {
                context.setVariable("documentations", complexType.getAnnotation().getDocumentations());
            }

            final var result = templateEngine.process("complexTypes/templateComplexType", context);
            final var outputFilePath = Paths.get(outputDirectory.getPath(), "complexTypes", complexType.getRawName() + ".html");
            logger.debug("File: {}", outputFilePath.toFile().getAbsolutePath());

            try {
                Files.write(outputFilePath, result.getBytes());
                logger.debug("Written {} bytes in File '{}'", new File(outputFilePath.toFile().getAbsolutePath()).length(), outputFilePath.toFile().getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String getNameSpacesAsString() {
        StringBuilder result = new StringBuilder();
        for (var ns : namespaces.keySet()) {
            result.append(ns)
                    .append("=")
                    .append("'")
                    .append(namespaces.get(ns).getName())
                    .append("'")
                    .append("<br />");
        }

        return result.toString();
    }

    public String getNodeTypeNameFromNodeType(short nodeType) {
        return switch (nodeType) {
            case Node.ELEMENT_NODE -> "Element";
            case Node.ATTRIBUTE_NODE -> "Attribute";
            case Node.TEXT_NODE -> "Text";
            case Node.CDATA_SECTION_NODE -> "CDATA Section";
            case Node.ENTITY_REFERENCE_NODE -> "Entity Reference";
            case Node.ENTITY_NODE -> "Entity";
            case Node.PROCESSING_INSTRUCTION_NODE -> "Processing Instruction";
            case Node.COMMENT_NODE -> "Comment";
            case Node.DOCUMENT_NODE -> "Document";
            case Node.DOCUMENT_TYPE_NODE -> "Document Type";
            case Node.DOCUMENT_FRAGMENT_NODE -> "Document Fragment";
            case Node.NOTATION_NODE -> "Notation";
            default -> "Unknown";
        };
    }

    public Node getChildNodeFromXpath(String xpath) {
        try {
            return getExtendedXsdElements().get(xpath).getCurrentNode();
        } catch (Exception e) {
            logger.debug("ERROR in getting Node: {}", e.getMessage());
        }
        return null;
    }

    public String getChildInfo(String xpath) {
        if (getExtendedXsdElements().get(xpath).getXsdDocumentation() != null) {
            return getExtendedXsdElements().get(xpath).getXsdDocumentation()
                    .stream()
                    .map(XsdAnnotationChildren::getContent)
                    .collect(Collectors.joining());
        }
        return "";
    }

    void generateDetailPages(File outputDirectory) {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        final XsdDocumentationImageService xsdDocumentationImageService = new XsdDocumentationImageService(extendedXsdElements);

        for (String key : this.getExtendedXsdElements().keySet()) {
            var currentElement = this.getExtendedXsdElements().get(key);

            if (!currentElement.getChildren().isEmpty()) {
                executor.submit(() -> {
                    var context = new Context();

                    if (this.method == ImageOutputMethod.SVG) {
                        final String svgDiagram = xsdDocumentationImageService.generateSvgString(key);
                        context.setVariable("svg", svgDiagram);
                    } else {
                        final String fileName = currentElement.getPageName().replace(".html", ".jpg");
                        final File pngFile = new File(String.valueOf(Paths.get(outputDirectory.getPath(), "details", fileName).toFile()));
                        final String filePath = xsdDocumentationImageService.generateImage(key, pngFile);

                        context.setVariable("img", filePath);
                    }

                    context.setVariable("xpath", getBreadCrumbs(currentElement));
                    context.setVariable("code", currentElement.getSourceCode());
                    context.setVariable("element", currentElement);
                    context.setVariable("namespace", getNameSpacesAsString());
                    context.setVariable("this", this);

                    if (currentElement.getXsdElement() != null && currentElement.getXsdElement().getType() != null) {
                        context.setVariable("type", currentElement.getElementType());
                    } else {
                        context.setVariable("type", "NULL");
                    }

                    if (currentElement.getXsdElement() != null && currentElement.getXsdElement().getAnnotation() != null) {
                        context.setVariable("appInfos", currentElement.getXsdElement().getAnnotation().getAppInfoList());
                    }

                    Map<String, String> docTemp = new LinkedHashMap<>();
                    if (currentElement.getLanguageDocumentation() != null && !currentElement.getLanguageDocumentation().isEmpty()) {
                        docTemp = currentElement.getLanguageDocumentation();
                    }
                    context.setVariable("documentation", docTemp);

                    final var result = templateEngine.process("details/templateDetail", context);
                    final var outputFileName = Paths.get(outputDirectory.getPath(), "details", currentElement.getPageName()).toFile().getAbsolutePath();
                    logger.debug("File: {}", outputFileName);

                    try {
                        Files.write(Paths.get(outputFileName), result.getBytes());
                        logger.debug("Written {} bytes in File '{}'", new File(outputFileName).length(), outputFileName);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    Map<String, String> getBreadCrumbs(ExtendedXsdElement currentElement) {
        xpath = new StringBuilder();
        Map<String, String> breadCrumbs = new LinkedHashMap<>();

        final var t = currentElement.getCurrentXpath().split("/");
        for (String element : t) {
            if (!element.isEmpty()) {
                xpath.append("/").append(element);
                String link = "#";
                if (this.getExtendedXsdElements() != null && this.getExtendedXsdElements().get(xpath.toString()) != null) {
                    link = this.getExtendedXsdElements().get(xpath.toString()).getPageName();
                }
                breadCrumbs.put(element, link);
            }
        }
        return breadCrumbs;
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

