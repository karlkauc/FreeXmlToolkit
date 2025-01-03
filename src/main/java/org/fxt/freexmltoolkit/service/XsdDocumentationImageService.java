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
import org.fxt.freexmltoolkit.domain.ExtendedXsdElement;
import org.w3c.dom.*;
import org.xmlet.xsdparser.xsdelements.XsdDocumentation;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for generating images and SVG diagrams from XSD documentation.
 */
public class XsdDocumentationImageService {

    private final static Logger logger = LogManager.getLogger(XsdDocumentationImageService.class);

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
    final static String svgNS = "http://www.w3.org/2000/svg";

    Map<String, ExtendedXsdElement> extendedXsdElements;

    /**
     * Constructs a new XsdDocumentationImageService with the given extended XSD elements.
     *
     * @param extendedXsdElements the extended XSD elements
     */
    public XsdDocumentationImageService(Map<String, ExtendedXsdElement> extendedXsdElements) {
        this.extendedXsdElements = extendedXsdElements;

        font = new Font("Arial", Font.PLAIN, 16);
        frc = new FontRenderContext(
                null,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
                RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
    }

    /**
     * Generates an image from the SVG representation of the XSD element.
     *
     * @param rootXpath the root XPath of the XSD element
     * @param file      the file to save the generated image
     * @return the file path of the generated image
     */
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

    /**
     * Generates an SVG string representation of the XSD element.
     *
     * @param rootXpath the root XPath of the XSD element
     * @return the SVG string representation
     */
    public String generateSvgString(String rootXpath) {
        var element = generateSvgDiagramms(rootXpath);
        return asString(element.getDocumentElement());
    }

    /**
     * Generates an SVG diagram from the given XML element.
     *
     * @param element the XML element
     * @return the SVG document
     */
    public Document generateSvgDiagram(Element element) {
        Document svgDocument = domImpl.createDocument(svgNS, "svg", null);
        var rootElementName = element.getLocalName();
        logger.debug("Root Element Name: {}", rootElementName);

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            var subNode = element.getChildNodes().item(i);
            if (subNode.getNodeType() == Node.ELEMENT_NODE) {
                logger.debug("Node: {} - {} - {}", subNode.getLocalName(), subNode.getNodeValue(), subNode.getNodeName());
            }
        }

        return svgDocument;
    }

    /**
     * Generates an SVG diagram for the XSD element and its children.
     *
     * @param rootXpath the root XPath of the XSD element
     * @return the SVG document
     */
    public Document generateSvgDiagramms(String rootXpath) {
        final Document document = domImpl.createDocument(svgNS, "svg", null);
        var svgRoot = document.getDocumentElement();

        var rootElement = extendedXsdElements.get(rootXpath);
        var rootElementName = rootElement.getElementName();
        logger.debug("rootElementName = {}", rootElementName);
        logger.debug("rootElement.getParentXpath() = {}", rootElement.getParentXpath());

        List<ExtendedXsdElement> childElements = new ArrayList<>();
        for (String temp : rootElement.getChildren()) {
            childElements.add(extendedXsdElements.get(temp));
        }

        double rightBoxHeight = 20;
        double rightBoxWidth = 0;

        // Determine the size of the right box to center the left element
        for (ExtendedXsdElement childElement : childElements) {
            String elementName = "";

            if (childElement != null && childElement.getXsdElement() != null) {
                elementName = childElement.getXsdElement().getName();
            }
            logger.debug("Element Name = {}", elementName);

            var z = font.getStringBounds(elementName, frc);
            var height = z.getBounds2D().getHeight();
            var width = z.getBounds2D().getWidth();

            rightBoxHeight = rightBoxHeight + margin + height + margin + 20; // including 20 spacing between boxes
            rightBoxWidth = Math.max(rightBoxWidth, width);
        }

        logger.debug("height = {}", rightBoxHeight);
        logger.debug("width = {}", rightBoxWidth);

        // Calculate the position of the first element
        var z = font.getStringBounds(rootElementName, frc);
        var rootElementHeight = z.getBounds2D().getHeight();
        var rootElementWidth = z.getBounds2D().getWidth();
        // The root node should be exactly in the middle of the right boxes.
        // So rightBoxHeight / 2 minus box size / 2

        int rootStartX = 20;
        int rootStartY = (int) ((rightBoxHeight / 2) - ((margin + rootElementHeight + margin) / 2));

        Element leftRootElement = document.createElement("a");
        String parentPageUrl = "#";
        if (extendedXsdElements.get(rootElement.getParentXpath()) != null) {
            parentPageUrl = extendedXsdElements.get(rootElement.getParentXpath()).getPageName();
        }
        leftRootElement.setAttribute("href", parentPageUrl);

        Element rect1 = createSvgElement(document, rootElementName, rootElementHeight, rootElementWidth, rootStartX + "", rootStartY + "", rootStartX, rootStartY);
        if (rootElement.getXsdElement().getMinOccurs() > 0) {
            rect1.setAttribute("style", MANDATORY_FORMAT);
        } else {
            rect1.setAttribute("style", OPTIONAL_FORMAT);
        }

        final var text = createSvgTextElement(document, margin, rootStartY - (int) (rootElementHeight / 2) + ((double) margin / 2), rootElementName, rootElementHeight, rootStartX);
        leftRootElement.appendChild(rect1);
        leftRootElement.appendChild(text);
        svgRoot.appendChild(leftRootElement);

        double docHeightTotal = 0;
        if (extendedXsdElements.get(rootElement.getCurrentXpath()).getXsdDocumentation() != null) {
            docHeightTotal = generateDocumentationElement(document, extendedXsdElements.get(rootElement.getCurrentXpath()).getXsdDocumentation(), rootElementWidth, rootElementHeight, rootStartX, rootStartY);
        }

        final double rightStartX = margin + rootElementWidth + margin + gapBetweenSides;

        final double pathStartX = rootStartX + margin + rootElementWidth + margin;
        final double pathStartY = rootStartY + rootElementHeight;

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

            logger.debug("Element Name = {}", elementName);

            var z2 = font.getStringBounds(elementName, frc);
            var height = z2.getBounds2D().getHeight();
            var width = z2.getBounds2D().getWidth();

            var rect2 = createSvgElement(document, elementName, height, rightBoxWidth, rightStartX + "", actualHeight + "", rootStartX, rootStartY);
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

            Element text2 = createSvgTextElement(document, rightStartX, actualHeight, elementName, height, margin);

            Element a2 = document.createElement("a");
            if (childElement != null && childElement.getChildren() != null && !childElement.getChildren().isEmpty()) {
                // create link
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

            actualHeight = actualHeight + margin + height + margin + 20; // 20 pixels spacing between boxes
        }

        var imageHeight = Math.max(docHeightTotal, rightBoxHeight);
        if (rootXpath.equals("/FundsXML4/ControlData")) {
            logger.debug("Image Height = {}", imageHeight);
        }

        // ToDo: automatically adjust size
        svgRoot.setAttributeNS(svgNS, "height", imageHeight + "");
        svgRoot.setAttributeNS(svgNS, "width", rootElementWidth + rightBoxWidth + gapBetweenSides + (margin * 2) + (20 * 2) + 10 + ""); // 50 for icon
        svgRoot.setAttributeNS(svgNS, "style", "background-color: rgb(235, 252, 241)");

        return document;
    }

    private double generateDocumentationElement(Document document, List<XsdDocumentation> xsdDocumentation, double rootElementWidth, double rootElementHeight, int startX, int startY) {
        /*
        Hier probieren wir einmal die Dokumentation zu erweitern
         */
        final var docTextGroup = document.createElement("g");
        docTextGroup.setAttribute("id", "comment");

        final var docText = document.createElement("text");
        docText.setAttribute("x", startX + margin + "");
        docText.setAttribute("y", startY + (margin * 3) + rootElementHeight + (margin / 2) + "");

        double docHeightTotal = 0;
        for (XsdDocumentation documentation : xsdDocumentation) {
            int length = 0;
            StringWriter writer = new StringWriter();

            for (String word : documentation.getContent().split(" ")) {
                var rectangle2D = font.getStringBounds(word + " ", frc);
                var docHeight = rectangle2D.getBounds2D().getHeight();
                var docWidth = rectangle2D.getBounds2D().getWidth();

                var newSize = docWidth + length;
                logger.debug("DocWidth: {} - Length: {} - NewSize: {}, rootElementWidth: {}", docWidth, length, newSize, rootElementWidth);
                logger.debug("DocHeight: {} - docHeightTotal: {}", docHeight, docHeightTotal);

                if ((docWidth + length) > (rootElementWidth + (margin * 2))) {
                    final var tspan = document.createElement("tspan");
                    tspan.setAttribute("x", (margin + 15) + "");
                    tspan.setAttribute("dy", "1.2em");
                    tspan.setTextContent(writer + " ");
                    docText.appendChild(tspan);
                    length = 0;
                    docHeightTotal += docHeight;

                    if (writer.toString().equals("Meta data of ")) {
                        Object o = null;
                    }

                    writer = new StringWriter();
                } else {
                    length += (int) docWidth;
                }
                writer.append(word).append(" ");
            }
            final var tspan = document.createElement("tspan");
            tspan.setAttribute("x", (margin + 15) + "");
            tspan.setAttribute("dy", "1.2em");
            tspan.setTextContent(writer + " ");
            docText.appendChild(tspan);
        }

        docTextGroup.appendChild(docText);
        document.getDocumentElement().appendChild(docTextGroup);

        return startY + margin + docHeightTotal + margin;
    }

    /**
     * Creates an SVG text element.
     *
     * @param document     the SVG document
     * @param rightStartX  the x-coordinate of the text element
     * @param actualHeight the y-coordinate of the text element
     * @param elementName  the name of the element
     * @param height       the height of the text element
     * @param margin       the margin around the text element
     * @return the created SVG text element
     */
    private Element createSvgTextElement(Document document, double rightStartX, double actualHeight, String elementName, double height, int margin) {
        Element text2 = document.createElement("text");
        text2.setAttribute("fill", "#096574");
        text2.setAttribute("font-family", font.getFontName());
        text2.setAttribute("font-size", font.getSize() + "");
        text2.setAttribute("textLength", "0");
        text2.setAttribute("x", rightStartX + margin + "");
        text2.setAttribute("y", actualHeight + height + (margin / 2) + "");
        text2.setTextContent(elementName);

        return text2;
    }

    /**
     * Creates an SVG rectangle element.
     *
     * @param document          the SVG document
     * @param rootElementName   the name of the root element
     * @param rootElementHeight the height of the root element
     * @param rootElementWidth  the width of the root element
     * @param s                 the x-coordinate of the rectangle
     * @param s2                the y-coordinate of the rectangle
     * @param startX            the starting x-coordinate
     * @param startY            the starting y-coordinate
     * @return the created SVG rectangle element
     */
    private Element createSvgElement(Document document, String rootElementName, double rootElementHeight, double rootElementWidth, String s, String s2, int startX, int startY) {
        Element rect1 = document.createElement("rect");
        rect1.setAttribute("fill", BOX_COLOR);
        rect1.setAttribute("id", rootElementName);
        rect1.setAttribute("height", (margin + rootElementHeight + margin) + "");
        rect1.setAttribute("width", (margin + rootElementWidth + margin) + "");
        rect1.setAttribute("x", s);
        rect1.setAttribute("y", s2);
        rect1.setAttribute("rx", "2");
        rect1.setAttribute("ry", "2");

        return rect1;
    }

    /**
     * Converts a DOM node to a string representation.
     *
     * @param node the DOM node
     * @return the string representation of the node
     */
    private static String asString(Node node) {
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
}