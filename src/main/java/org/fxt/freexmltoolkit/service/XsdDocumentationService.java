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

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.extendedXsd.ExtendedXsdElement;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.elementswrapper.ReferenceBase;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
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

    public enum ImageOutputMethod {
        SVG,
        PNG
    }

    ImageOutputMethod method;

    XsdParser parser;
    List<XsdSchema> xmlSchema;
    Map<String, ExtendedXsdElement> extendedXsdElements;

    XmlService xmlService = XmlServiceImpl.getInstance();

    final int margin = 10;
    final int gapBetweenSides = 100;

    final static String BOX_COLOR = "#d5e3e8";
    final static String OPTIONAL_FORMAT = "stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7; filter: drop-shadow(3px 5px 2px rgb(0 0 0 / 0.4));";
    final static String OPTIONAL_FORMAT_NO_SHADOW = "stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;";
    final static String MANDATORY_FORMAT = "stroke: rgb(2,23,23); stroke-width: 1.5; filter: drop-shadow(3px 5px 2px rgb(0 0 0 / 0.4));";
    final static String MANDATORY_FORMAT_NO_SHADOW = "stroke: rgb(2,23,23); stroke-width: 1.5;";

    Font font;
    FontRenderContext frc;
    DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
    final String svgNS = "http://www.w3.org/2000/svg";
    Document document;

    Boolean useMarkdownRenderer = true;

    StringBuilder xpath;
    ClassLoaderTemplateResolver resolver;
    TemplateEngine templateEngine;

    public XsdDocumentationService() {
        font = new Font("Arial", Font.PLAIN, 16);
        frc = new FontRenderContext(
                null,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
                RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);

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

    public void copyResources(File outputDirectory) {
        try {
            Files.createDirectories(outputDirectory.toPath());
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "assets"));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "details"));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "complexTypes"));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "simpleTypes"));

            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/bootstrap.bundle.min.js"), Paths.get(outputDirectory.getPath(), "assets", "bootstrap.bundle.min.js"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/prism.js"), Paths.get(outputDirectory.getPath(), "assets", "prism.js"), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/freeXmlToolkit.css"), Paths.get(outputDirectory.getPath(), "assets", "freeXmlToolkit.css"), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/plus.png"), Paths.get(outputDirectory.getPath(), "assets", "plus.png"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/logo.png"), Paths.get(outputDirectory.getPath(), "assets", "logo.png"), StandardCopyOption.REPLACE_EXISTING);

            Files.copy(getClass().getResourceAsStream("/xsdDocumentation/assets/Roboto-Regular.ttf"), Paths.get(outputDirectory.getPath(), "assets", "Roboto-Regular.ttf"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void generateRootPage(File outputDirectory) {
        final var rootElementName = elements.get(0).getName();

        var context = new Context();
        context.setVariable("date", LocalDate.now());
        context.setVariable("filename", this.getXsdFilePath());
        context.setVariable("rootElementName", rootElementName);
        context.setVariable("rootElement", getXmlSchema().get(0));
        context.setVariable("xsdElements", elements.get(0));
        context.setVariable("xsdComplexTypes", getXsdComplexTypes());
        context.setVariable("xsdSimpleTypes", getXsdSimpleTypes());
        context.setVariable("rootElementLink", "details/" + getExtendedXsdElements().get("/" + rootElementName).getPageName());

        final var result = templateEngine.process("templateRootElement", context);
        final var outputFileName = Paths.get(outputDirectory.getPath(), "index.html").toFile().getAbsolutePath();
        logger.debug("Root File: " + outputFileName);

        try {
            Files.write(Paths.get(outputFileName), result.getBytes());
            logger.debug("Written {} bytes in File '{}'", new File(outputFileName).length(), outputFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void generateComplexTypePages(File outputDirectory) {
        logger.debug("Complex Types");

        for (var complexType : getXsdComplexTypes()) {
            var context = new Context();
            context.setVariable("complexType", complexType);

            if (complexType.getAnnotation() != null) {
                context.setVariable("documentations", complexType.getAnnotation().getDocumentations());
            }

            final var result = templateEngine.process("complexTypes/templateComplexType", context);
            final var outputFilePath = Paths.get(outputDirectory.getPath(), "complexTypes", complexType.getRawName() + ".html");
            logger.debug("File: " + outputFilePath.toFile().getAbsolutePath());

            try {
                Files.write(outputFilePath, result.getBytes());
                logger.debug("Written {} bytes in File '{}'", new File(outputFilePath.toFile().getAbsolutePath()).length(), outputFilePath.toFile().getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String getChildInfo(String xpath) {
        try {
            return getExtendedXsdElements().get(xpath).getXsdDocumentation()
                    .stream()
                    .map(XsdAnnotationChildren::getContent)
                    .collect(Collectors.joining());
        } catch (Exception e) {
            logger.debug("ERROR: {}", e.getMessage());
        }
        return "";
    }

    public void generateDetailPages(File outputDirectory) {
        for (String key : this.getExtendedXsdElements().keySet()) {
            var currentElement = this.getExtendedXsdElements().get(key);

            if (!currentElement.getChildren().isEmpty()) {
                var context = new Context();
                if (this.method == ImageOutputMethod.SVG) {
                    String svgDiagram = generateSvgString(key);
                    context.setVariable("svg", svgDiagram);
                } else {
                    String fileName = currentElement.getPageName().replace(".html", ".jpg");
                    File pngFile = new File(String.valueOf(Paths.get(outputDirectory.getPath(), "details", fileName).toFile()));
                    String filePath = generateImage(key, pngFile);

                    context.setVariable("img", filePath);
                }

                context.setVariable("xpath", getBreadCrumbs(currentElement));
                context.setVariable("code", currentElement.getSourceCode());
                context.setVariable("element", currentElement);
                context.setVariable("namespace", getXmlSchema().get(0).getTargetNamespace());
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
                logger.debug("File: " + outputFileName);

                try {
                    Files.write(Paths.get(outputFileName), result.getBytes());
                    logger.debug("Written {} bytes in File '{}'", new File(outputFileName).length(), outputFileName);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public String generateImage(String rootXpath, File file) {
        try {
            var svgString = generateSvgString(rootXpath);
            var transcoder = new JPEGTranscoder();
            TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgString.getBytes()));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            TranscoderOutput output = new TranscoderOutput(outputStream);

            transcoder.transcode(input, output);
            Files.write(file.toPath(), outputStream.toByteArray());

            outputStream.flush();
            outputStream.close();

            logger.debug("File: {}", file.getAbsolutePath());
            logger.debug("File Size: {}", file.length());

            return file.getAbsolutePath();
        } catch (IOException | TranscoderException ioException) {
            logger.error(ioException.getMessage());
        }

        return null;
    }

    public String generateSvgString(String rootXpath) {
        var element = generateSvgDiagramms(rootXpath);
        return asString(element.getDocumentElement());
    }

    public Document generateSvgDiagramms(String rootXpath) {
        Document document = domImpl.createDocument(svgNS, "svg", null);
        var svgRoot = document.getDocumentElement();

        var rootElement = this.getExtendedXsdElements().get(rootXpath);
        var rootElementName = rootElement.getElementName();
        logger.debug("rootElementName = {}", rootElementName);
        logger.debug("rootElement.getParentXpath() = {}", rootElement.getParentXpath());

        java.util.List<ExtendedXsdElement> childElements = new ArrayList<>();
        for (String temp : rootElement.getChildren()) {
            childElements.add(this.getExtendedXsdElements().get(temp));
        }

        double rightBoxHeight = 20;
        double rightBoxWidth = 0;

        // größe der rechten Box ermitteln
        // um linkes element in der mitte zu platzieren
        for (ExtendedXsdElement r : childElements) {
            String elementName = "";

            if (r != null && r.getXsdElement() != null) {
                elementName = r.getXsdElement().getName();
            }
            logger.debug("Element Name = " + elementName);

            var z = font.getStringBounds(elementName, frc);
            var height = z.getBounds2D().getHeight();
            var width = z.getBounds2D().getWidth();

            rightBoxHeight = rightBoxHeight + margin + height + margin + 20; // inkl. 20 abstand zwischen boxen
            rightBoxWidth = Math.max(rightBoxWidth, width);
        }

        logger.debug("height = {}", rightBoxHeight);
        logger.debug("width = {}", rightBoxWidth);

        // erstes Element - Abstände Berechnen
        var z = font.getStringBounds(rootElementName, frc);
        var rootElementHeight = z.getBounds2D().getHeight();
        var rootElementWidth = z.getBounds2D().getWidth();
        // root node sollte genau in der mitte der rechten boxen sein.
        // also rightBoxHeight / 2 minus boxgröße / 2

        int startX = 20;
        int startY = (int) ((rightBoxHeight / 2) - ((margin + rootElementHeight + margin) / 2));

        Element a = document.createElement("a");
        String parentPageUrl = "#";
        if (this.getExtendedXsdElements().get(rootElement.getParentXpath()) != null) {
            parentPageUrl = this.getExtendedXsdElements().get(rootElement.getParentXpath()).getPageName();
        }
        a.setAttribute("href", parentPageUrl);

        Element rect1 = document.createElement("rect");
        rect1.setAttribute("fill", BOX_COLOR);
        rect1.setAttribute("id", rootElementName);
        rect1.setAttribute("height", (margin + rootElementHeight + margin) + "");
        rect1.setAttribute("width", (margin + rootElementWidth + margin) + "");
        rect1.setAttribute("x", startX + "");
        rect1.setAttribute("y", startY + "");
        rect1.setAttribute("rx", "2");
        rect1.setAttribute("ry", "2");
        if (rootElement.getXsdElement().getMinOccurs() > 0) {
            rect1.setAttribute("style", MANDATORY_FORMAT);
        } else {
            rect1.setAttribute("style", OPTIONAL_FORMAT);
        }

        Element text = document.createElement("text");
        text.setAttribute("fill", "#096574");
        text.setAttribute("font-family", font.getFontName());
        text.setAttribute("font-size", font.getSize() + "");
        text.setAttribute("textLength", "0");
        text.setAttribute("x", margin + startX + "");
        text.setAttribute("y", startY + rootElementHeight + (margin / 2) + "");
        text.setTextContent(rootElementName);

        a.appendChild(rect1);
        a.appendChild(text);
        svgRoot.appendChild(a);

        final double rightStartX = margin + rootElementWidth + margin + gapBetweenSides;

        final double pathStartX = startX + margin + rootElementWidth + margin;
        final double pathStartY = startY + rootElementHeight;

        double actualHeight = 20;
        for (ExtendedXsdElement childElement : childElements) {
            String elementName = "";
            String css = OPTIONAL_FORMAT;

            if (childElement != null) {
                elementName = childElement.getElementName();
            }

            Element minMaxOccurs = document.createElement("text");
            if (childElement != null && childElement.getXsdElement() != null) {
                if (childElement.getXsdElement().getMinOccurs() > 0) {
                    css = MANDATORY_FORMAT;
                }

                final String minOccurs = childElement.getXsdElement().getMinOccurs().toString();
                final String maxOccurs = childElement.getXsdElement().getMaxOccurs().equals("unbounded") ? "∞" : childElement.getXsdElement().getMaxOccurs();
                logger.debug("Min/Max Occurs: {}/{}", minOccurs, maxOccurs);

                minMaxOccurs.setAttribute("fill", "#096574");
                minMaxOccurs.setAttribute("font-family", font.getFontName());
                minMaxOccurs.setAttribute("font-size", font.getSize() - 2 + "");
                minMaxOccurs.setAttribute("textLength", "0");
                minMaxOccurs.setAttribute("x", rightStartX + margin - 35 + "");
                minMaxOccurs.setAttribute("y", actualHeight + (margin / 2) + 10 + "");
                minMaxOccurs.setTextContent(minOccurs + ":" + maxOccurs);
                svgRoot.appendChild(minMaxOccurs);
            }

            logger.debug("Element Name = " + elementName);

            var z2 = font.getStringBounds(elementName, frc);
            var height = z2.getBounds2D().getHeight();
            var width = z2.getBounds2D().getWidth();

            Element rect2 = document.createElement("rect");
            rect2.setAttribute("fill", BOX_COLOR);
            rect2.setAttribute("id", elementName);
            rect2.setAttribute("height", (margin + height + margin) + "");
            rect2.setAttribute("width", (margin + rightBoxWidth + margin) + "");
            rect2.setAttribute("x", rightStartX + "");
            rect2.setAttribute("y", actualHeight + "");
            rect2.setAttribute("rx", "2");
            rect2.setAttribute("ry", "2");
            rect2.setAttribute("style", css);

            Element image = null;
            if (childElement != null && childElement.getChildren() != null
                    && !childElement.getChildren().isEmpty()) {
                image = document.createElement("image");
                image.setAttribute("href", "../assets/plus.png");
                image.setAttribute("height", "20");
                image.setAttribute("width", "20");
                image.setAttribute("x", rightStartX + (margin + rightBoxWidth + margin) + 2 + "");
                image.setAttribute("y", actualHeight + (height / 2) + "");
            }

            Element text2 = document.createElement("text");
            text2.setAttribute("fill", "#096574");
            text2.setAttribute("font-family", font.getFontName());
            text2.setAttribute("font-size", font.getSize() + "");
            text2.setAttribute("textLength", "0");
            text2.setAttribute("x", rightStartX + margin + "");
            text2.setAttribute("y", actualHeight + height + (margin / 2) + "");
            text2.setTextContent(elementName);

            Element a2 = document.createElement("a");
            if (childElement != null && childElement.getChildren() != null && !childElement.getChildren().isEmpty()) {
                // link erstellen
                a2.setAttribute("href", childElement.getPageName());
                a2.appendChild(rect2);
                a2.appendChild(text2);
                a2.appendChild(image);

                svgRoot.appendChild(a2);
            } else {
                svgRoot.appendChild(rect2);
                svgRoot.appendChild(text2);
            }

            Element path2 = document.createElement("path");
            path2.setAttribute("d", "M " + pathStartX + " " + pathStartY +
                    " h " + ((gapBetweenSides / 2) - margin) +
                    " V " + (actualHeight + ((margin + height + margin) / 2)) +
                    " h " + ((gapBetweenSides / 2) - margin));
            path2.setAttribute("fill", "none");
            if (childElement != null && childElement.getXsdElement() != null && childElement.getXsdElement().getMinOccurs() > 0) {
                path2.setAttribute("style", MANDATORY_FORMAT_NO_SHADOW);
            } else {
                path2.setAttribute("style", OPTIONAL_FORMAT_NO_SHADOW);
            }
            svgRoot.appendChild(path2);

            actualHeight = actualHeight + margin + height + margin + 20; // 20 pixel abstand zwischen boxen
        }

        // ToDo: größe automatisch anpassen
        svgRoot.setAttributeNS(svgNS, "height", rightBoxHeight + (margin * 2) + "");
        svgRoot.setAttributeNS(svgNS, "width", rootElementWidth + rightBoxWidth + gapBetweenSides + (margin * 2) + (20 * 2) + 10 + ""); // 50 für icon
        svgRoot.setAttributeNS(svgNS, "style", "background-color: rgb(235, 252, 241)");

        return document;
    }

    private String asString(Node node) {
        StringWriter writer = new StringWriter();
        try {
            Transformer trans = TransformerFactory.newInstance().newTransformer();
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            trans.setOutputProperty(OutputKeys.VERSION, "1.0");
            if (!(node instanceof Document)) {
                trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }
            trans.transform(new DOMSource(node), new StreamResult(writer));
        } catch (final TransformerConfigurationException ex) {
            throw new IllegalStateException(ex);
        } catch (final TransformerException ex) {
            throw new IllegalArgumentException(ex);
        }
        return writer.toString();
    }

    public void processXsd(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
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
                final String elementRawName = xsdElement.getRawName();
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
            case XsdExtension xsdExtension -> {
                logger.debug("XsdExtension = " + xsdExtension);
                for (ReferenceBase x : xsdExtension.getElements()) {
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode);
                }
            }

            default -> throw new IllegalStateException("Unexpected value: " + xsdAbstractElement);
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
}

