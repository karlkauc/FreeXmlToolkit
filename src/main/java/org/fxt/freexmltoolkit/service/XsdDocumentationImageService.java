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
import org.apache.batik.transcoder.image.PNGTranscoder;
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
     * Generates a PNG image from the SVG representation of the XSD element.
     * This version uses the PNGTranscoder for lossless image quality.
     *
     * @param rootXpath the root XPath of the XSD element
     * @param file      the file to save the generated image (should have a .png extension)
     * @return the file path of the generated image, or null on failure
     */
    public String generateImage(String rootXpath, File file) {
        try {
            // 1. SVG-Daten wie zuvor generieren
            String svgString = generateSvgString(rootXpath);

            // 2. PNG-Transcoder initialisieren (ersetzt JPEGTranscoder)
            PNGTranscoder transcoder = new PNGTranscoder();

            // Hinweis: Die Qualitätseinstellung (KEY_QUALITY) wird für PNG nicht benötigt,
            // da es ein verlustfreies Format ist.

            // 3. Input für den Transcoder vorbereiten
            TranscoderInput input = new TranscoderInput(new StringReader(svgString));

            // 4. Ressourcen sicher mit try-with-resources verwalten und direkt in die Datei schreiben
            try (OutputStream outputStream = new FileOutputStream(file)) {
                TranscoderOutput output = new TranscoderOutput(outputStream);

                // 5. Konvertierung durchführen
                transcoder.transcode(input, output);
            } // outputStream wird hier automatisch geschlossen

            logger.debug("Successfully created PNG image: {}", file.getAbsolutePath());
            logger.debug("File Size: {}", file.length());

            return file.getAbsolutePath();

        } catch (IOException | TranscoderException e) {
            // 6. Verbessertes Fehler-Logging mit vollständigem Stack Trace
            logger.error("Failed to generate image for file '{}'", file.getAbsolutePath(), e);
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

        // Definiere wiederverwendbare Elemente im <defs>-Block
        Element defs = document.createElementNS(svgNS, "defs");

        // 1. Drop-Shadow-Filter
        Element filter = document.createElementNS(svgNS, "filter");
        filter.setAttribute("id", "drop-shadow");
        filter.setAttribute("x", "-20%");
        filter.setAttribute("y", "-20%");
        filter.setAttribute("width", "140%");
        filter.setAttribute("height", "140%");
        // HINWEIS: setInnerHtml() oder setTextContent() mit XML-Tags ist nicht standardkonform.
        // Wir bauen den Filter stattdessen korrekt mit DOM-Methoden auf.
        Element feGaussianBlur = document.createElementNS(svgNS, "feGaussianBlur");
        feGaussianBlur.setAttribute("in", "SourceAlpha");
        feGaussianBlur.setAttribute("stdDeviation", "2");
        filter.appendChild(feGaussianBlur);

        Element feOffset = document.createElementNS(svgNS, "feOffset");
        feOffset.setAttribute("dx", "3");
        feOffset.setAttribute("dy", "5");
        feOffset.setAttribute("result", "offsetblur");
        filter.appendChild(feOffset);

        Element feFlood = document.createElementNS(svgNS, "feFlood");
        feFlood.setAttribute("flood-color", "black");
        feFlood.setAttribute("flood-opacity", "0.4");
        filter.appendChild(feFlood);

        Element feComposite = document.createElementNS(svgNS, "feComposite");
        feComposite.setAttribute("in2", "offsetblur");
        feComposite.setAttribute("operator", "in");
        filter.appendChild(feComposite);

        Element feMerge = document.createElementNS(svgNS, "feMerge");
        Element feMergeNode1 = document.createElementNS(svgNS, "feMergeNode");
        Element feMergeNode2 = document.createElementNS(svgNS, "feMergeNode");
        feMergeNode2.setAttribute("in", "SourceGraphic");
        feMerge.appendChild(feMergeNode1);
        feMerge.appendChild(feMergeNode2);
        filter.appendChild(feMerge);

        defs.appendChild(filter);

        // 2. Natives, umrandetes Plus-Symbol
        Element plusIcon = document.createElementNS(svgNS, "g");
        plusIcon.setAttribute("id", "plus-icon");
        plusIcon.setAttribute("style", "stroke: #096574; stroke-width: 1.5; stroke-linecap: round;");
        Element circle = document.createElementNS(svgNS, "circle");
        circle.setAttribute("cx", "10");
        circle.setAttribute("cy", "10");
        circle.setAttribute("r", "9");
        circle.setAttribute("fill", "none");
        plusIcon.appendChild(circle);
        Element line1 = document.createElementNS(svgNS, "line");
        line1.setAttribute("x1", "10");
        line1.setAttribute("y1", "5");
        line1.setAttribute("x2", "10");
        line1.setAttribute("y2", "15");
        plusIcon.appendChild(line1);
        Element line2 = document.createElementNS(svgNS, "line");
        line2.setAttribute("x1", "5");
        line2.setAttribute("y1", "10");
        line2.setAttribute("x2", "15");
        line2.setAttribute("y2", "10");
        plusIcon.appendChild(line2);
        defs.appendChild(plusIcon);

        svgRoot.appendChild(defs);

        var rootElement = extendedXsdElements.get(rootXpath);
        if (rootElement == null) {
            logger.warn("Could not find root element for XPath: {}. Cannot generate diagram.", rootXpath);
            return document; // Leeres Dokument zurückgeben, um Fehler zu vermeiden
        }
        List<ExtendedXsdElement> childElements = new ArrayList<>();
        if (rootElement.getChildren() != null) {
            for (String temp : rootElement.getChildren()) {
                if (extendedXsdElements.get(temp) != null) {
                    childElements.add(extendedXsdElements.get(temp));
                }
            }
        }

        double rightBoxHeight = 20;
        double rightBoxWidth = 0;
        for (ExtendedXsdElement childElement : childElements) {

            // Schütze vor null-Werten, genau wie es schon beim Typ gemacht wird.
            String elementName = childElement.getElementName() != null ? childElement.getElementName() : "";
            String elementType = childElement.getElementType() != null ? childElement.getElementType() : "";

            var nameBounds = font.getStringBounds(elementName, frc);
            var typeBounds = font.getStringBounds(elementType, frc);

            rightBoxHeight += margin + nameBounds.getBounds2D().getHeight() + typeBounds.getBounds2D().getHeight() + margin + 20;
            rightBoxWidth = Math.max(rightBoxWidth, nameBounds.getBounds2D().getWidth());
            rightBoxWidth = Math.max(rightBoxWidth, typeBounds.getBounds2D().getWidth());
        }

        var z = font.getStringBounds(rootElement.getElementName(), frc);
        var rootElementHeight = z.getBounds2D().getHeight();
        var rootElementWidth = z.getBounds2D().getWidth();
        int rootStartX = 20;
        int rootStartY = (int) ((rightBoxHeight / 2) - ((margin + rootElementHeight + margin) / 2));

        // Linkes Wurzelelement
        Element leftRootLink = document.createElementNS(svgNS, "a");
        leftRootLink.setAttribute("href", "#"); // Platzhalter

        Element rect1 = createSvgRect(document, rootElement.getElementName(), rootElementHeight, rootElementWidth, String.valueOf(rootStartX), String.valueOf(rootStartY));
        rect1.setAttribute("filter", "url(#drop-shadow)");
        // WIEDERHERGESTELLT: Style-Logik für Root-Element
        rect1.setAttribute("style", rootElement.getXsdElement() != null &&
                rootElement.getXsdElement().getMinOccurs() > 0 ? MANDATORY_FORMAT_NO_SHADOW : OPTIONAL_FORMAT_NO_SHADOW);


        Element text1 = createSvgTextElement(document, rootElement.getElementName(), String.valueOf(rootStartX + margin), String.valueOf(rootStartY + margin + rootElementHeight), "#096574", font.getSize());

        leftRootLink.appendChild(rect1);
        leftRootLink.appendChild(text1);
        svgRoot.appendChild(leftRootLink);

        double docHeightTotal = generateDocumentationElement(document, rootElement.getXsdDocumentation(), rootElementWidth, rootElementHeight, rootStartX, rootStartY);
        final double rightStartX = rootStartX + margin + rootElementWidth + margin + gapBetweenSides;
        final double pathStartX = rootStartX + margin + rootElementWidth;
        final double pathStartY = rootStartY + (margin + rootElementHeight + margin) / 2;

        double actualHeight = 20;
        for (ExtendedXsdElement childElement : childElements) {
            String elementName = childElement.getElementName();
            String elementType = childElement.getElementType() != null ? childElement.getElementType() : "";
            var nameBounds = font.getStringBounds(elementName, frc);
            var typeBounds = font.getStringBounds(elementType, frc);

            double nameHeight = nameBounds.getBounds2D().getHeight();
            double typeHeight = elementType.isBlank() ? 0 : typeBounds.getBounds2D().getHeight() + 8; // +8 für Linie und Abstand
            double totalContentHeight = nameHeight + typeHeight;

            Element rightBox = createSvgRect(document, elementName, totalContentHeight, rightBoxWidth, String.valueOf(rightStartX), String.valueOf(actualHeight));
            rightBox.setAttribute("filter", "url(#drop-shadow)");
            // WIEDERHERGESTELLT: Style-Logik für Child-Elemente
            rightBox.setAttribute("style", childElement.getXsdElement() != null && childElement.getXsdElement().getMinOccurs() > 0 ? MANDATORY_FORMAT_NO_SHADOW : OPTIONAL_FORMAT_NO_SHADOW);

            // WIEDERHERGESTELLT: Text-Logik für Child-Elemente
            Element textGroup = document.createElementNS(svgNS, "g");
            double nameY = actualHeight + margin + nameHeight;
            Element nameTextNode = createSvgTextElement(document, elementName, String.valueOf(rightStartX + margin), String.valueOf(nameY), "#096574", font.getSize());
            textGroup.appendChild(nameTextNode);

            if (!elementType.isBlank()) {
                double lineY = nameY + 4;
                Element line = document.createElementNS(svgNS, "line");
                line.setAttribute("x1", String.valueOf(rightStartX + margin));
                line.setAttribute("y1", String.valueOf(lineY));
                line.setAttribute("x2", String.valueOf(rightStartX + margin + rightBoxWidth));
                line.setAttribute("y2", String.valueOf(lineY));
                line.setAttribute("stroke", "#8cb5c2");
                line.setAttribute("stroke-width", "1");
                textGroup.appendChild(line);

                double typeY = lineY + typeBounds.getBounds2D().getHeight() + 2;
                Element typeTextNode = createSvgTextElement(document, elementType, String.valueOf(rightStartX + margin), String.valueOf(typeY), "#54828d", font.getSize() - 2);
                textGroup.appendChild(typeTextNode);
            }

            Element useIcon = null;
            if (childElement.hasChildren()) {
                useIcon = document.createElementNS(svgNS, "use");
                useIcon.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#plus-icon");
                useIcon.setAttribute("x", String.valueOf(rightStartX + margin + rightBoxWidth + margin + 2));
                double boxCenterY = actualHeight + (margin + totalContentHeight + margin) / 2;
                useIcon.setAttribute("y", String.valueOf(boxCenterY - 10));
            }

            Element rightLink = document.createElementNS(svgNS, "a");
            if (childElement.hasChildren() && childElement.getPageName() != null) {
                rightLink.setAttribute("href", childElement.getPageName());
                rightLink.appendChild(rightBox);
                rightLink.appendChild(textGroup);
                if (useIcon != null) {
                    rightLink.appendChild(useIcon);
                }
                svgRoot.appendChild(rightLink);
            } else {
                svgRoot.appendChild(rightBox);
                svgRoot.appendChild(textGroup);
            }

            double pathEndY = actualHeight + (margin + totalContentHeight + margin) / 2;
            Element path = document.createElementNS(svgNS, "path");
            path.setAttribute("d", "M " + pathStartX + " " + pathStartY + " h " + (gapBetweenSides / 2) + " V " + pathEndY + " h " + (gapBetweenSides / 2));
            path.setAttribute("fill", "none");
            path.setAttribute("style", childElement.getXsdElement() != null && childElement.getXsdElement().getMinOccurs() > 0 ? MANDATORY_FORMAT_NO_SHADOW : OPTIONAL_FORMAT_NO_SHADOW);
            svgRoot.appendChild(path);

            actualHeight += margin + totalContentHeight + margin + 20;
        }

        var imageHeight = Math.max(docHeightTotal, actualHeight);
        svgRoot.setAttribute("height", String.valueOf(imageHeight));
        svgRoot.setAttribute("width", String.valueOf(rightStartX + margin + rightBoxWidth + margin + 20 + 10));
        svgRoot.setAttribute("style", "background-color: rgb(235, 252, 241)");

        return document;
    }

    /**
     * Creates an SVG rectangle element in the correct namespace.
     * (Previously named createSvgElement)
     */
    private Element createSvgRect(Document document, String id, double contentHeight, double contentWidth, String x, String y) {
        // KORREKTUR: Element im SVG-Namespace erstellen
        Element rect = document.createElementNS(svgNS, "rect");
        rect.setAttribute("fill", BOX_COLOR);
        rect.setAttribute("id", id);
        // Die Höhe und Breite basieren auf dem Inhalt plus Rändern
        rect.setAttribute("height", String.valueOf(margin + contentHeight + margin));
        rect.setAttribute("width", String.valueOf(margin + contentWidth + margin));
        rect.setAttribute("x", x);
        rect.setAttribute("y", y);
        rect.setAttribute("rx", "2");
        rect.setAttribute("ry", "2");
        return rect;
    }

    /**
     * Creates an SVG text element in the correct namespace.
     */
    private Element createSvgTextElement(Document document, String textContent, String x, String y, String fill, int fontSize) {
        // KORREKTUR: Element im SVG-Namespace erstellen
        Element textElement = document.createElementNS(svgNS, "text");
        textElement.setAttribute("fill", fill);
        textElement.setAttribute("font-family", font.getFontName());
        textElement.setAttribute("font-size", String.valueOf(fontSize));
        textElement.setAttribute("x", x);
        textElement.setAttribute("y", y);
        textElement.setTextContent(textContent);
        return textElement;
    }

    /**
     * Generates and appends the documentation block to the SVG.
     */
    private double generateDocumentationElement(Document document, List<XsdDocumentation> xsdDocumentation, double rootElementWidth, double rootElementHeight, int startX, int startY) {
        // KORREKTUR: Alle Elemente im SVG-Namespace erstellen
        final var docTextGroup = document.createElementNS(svgNS, "g");
        docTextGroup.setAttribute("id", "comment");

        final var docText = document.createElementNS(svgNS, "text");
        docText.setAttribute("x", String.valueOf(startX + margin));
        docText.setAttribute("y", String.valueOf(startY + (margin * 3) + rootElementHeight + (margin / 2.0)));

        double docHeightTotal = 0;
        for (XsdDocumentation documentation : xsdDocumentation) {
            StringWriter writer = new StringWriter();
            int length = 0;

            for (String word : documentation.getContent().split(" ")) {
                var rectangle2D = font.getStringBounds(word + " ", frc);
                var docHeight = rectangle2D.getBounds2D().getHeight();
                var docWidth = rectangle2D.getBounds2D().getWidth();

                if ((docWidth + length) > (rootElementWidth + (margin * 2))) {
                    final var tspan = document.createElementNS(svgNS, "tspan");
                    tspan.setAttribute("x", String.valueOf(margin + 15));
                    tspan.setAttribute("dy", "1.2em");
                    tspan.setTextContent(writer.toString()); // writer + " " ist nicht nötig
                    docText.appendChild(tspan);
                    writer = new StringWriter();
                    length = 0;
                    docHeightTotal += docHeight;
                }
                length += (int) docWidth;
                writer.append(word).append(" ");
            }
            // Append remaining text
            if (!writer.toString().isBlank()) {
                final var tspan = document.createElementNS(svgNS, "tspan");
                tspan.setAttribute("x", String.valueOf(margin + 15));
                tspan.setAttribute("dy", "1.2em");
                tspan.setTextContent(writer.toString());
                docText.appendChild(tspan);
            }
        }

        docTextGroup.appendChild(docText);
        document.getDocumentElement().appendChild(docTextGroup);

        return startY + margin + docHeightTotal + margin;
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
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);

            Transformer trans = factory.newTransformer();
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            trans.setOutputProperty(OutputKeys.VERSION, "1.0");
            if (!(node instanceof Document)) {
                trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }
            trans.transform(new DOMSource(node), new StreamResult(writer));
        } catch (final TransformerConfigurationException ex) {
            // Verwende spezifischere Exceptions, wenn möglich
            logger.error("Transformer configuration error during serialization", ex);
            throw new IllegalStateException("Failed to configure XML Transformer", ex);
        } catch (final TransformerException ex) {
            logger.error("Failed to transform DOM node to string", ex);
            throw new IllegalArgumentException("Failed to transform node", ex);
        }
        return writer.toString();
    }
}