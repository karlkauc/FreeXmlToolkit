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
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.w3c.dom.*;

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

    // Ein ThreadLocal, das für jeden Thread eine wiederverwendbare Transformer-Instanz bereitstellt.
    // Dies vermeidet den hohen Aufwand, bei jedem Aufruf von asString() eine neue Instanz zu erstellen.
    private static final ThreadLocal<Transformer> transformerThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer trans = factory.newTransformer();
            // Konfiguriere Eigenschaften, die für alle Transformationen gleich sind
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            trans.setOutputProperty(OutputKeys.VERSION, "1.0");
            return trans;
        } catch (TransformerConfigurationException e) {
            logger.error("Fatal error during thread-local Transformer initialization", e);
            // Dies ist ein schwerwiegender Konfigurationsfehler, der die Anwendung stoppen sollte.
            throw new IllegalStateException("Failed to create thread-local Transformer", e);
        }
    });

    // Modernere Abstände und Layout-Parameter
    final int margin = 16;
    final int gapBetweenSides = 120;
    final int boxPadding = 12;
    final int borderRadius = 8;

    // Moderne Farbpalette mit besseren Kontrasten und modernem Look
    private static final String COLOR_BG = "#ffffff";                    // Reines Weiß für modernen Look
    private static final String COLOR_BOX_FILL = "#f8fafc";             // Sehr helles Grau
    private static final String COLOR_BOX_FILL_HOVER = "#e2e8f0";       // Hover-Farbe
    private static final String COLOR_STROKE = "#334155";               // Dunkleres Grau für besseren Kontrast
    private static final String COLOR_STROKE_OPTIONAL = "#94a3b8";      // Hellere Farbe für optionale Elemente
    private static final String COLOR_STROKE_SEPARATOR = "#e2e8f0";     // Trennlinien
    private static final String COLOR_TEXT_PRIMARY = "#1e293b";         // Dunkler Text
    private static final String COLOR_TEXT_SECONDARY = "#64748b";       // Sekundärer Text
    private static final String COLOR_ICON = "#3b82f6";                 // Moderne Blaufarbe
    private static final String COLOR_ACCENT = "#8b5cf6";               // Akzentfarbe für Symbole
    private static final String COLOR_SHADOW = "rgba(0, 0, 0, 0.1)";   // Subtiler Schatten

    // Moderne Style-Strings
    final static String OPTIONAL_FORMAT = "stroke: " + COLOR_STROKE_OPTIONAL + "; stroke-width: 2; stroke-dasharray: 8, 8; fill: " + COLOR_BOX_FILL + ";";
    final static String MANDATORY_FORMAT = "stroke: " + COLOR_STROKE + "; stroke-width: 2; fill: " + COLOR_BOX_FILL + ";";
    final static String CONNECTION_LINE = "stroke: " + COLOR_STROKE + "; stroke-width: 2; fill: none;";
    final static String CONNECTION_LINE_OPTIONAL = "stroke: " + COLOR_STROKE_OPTIONAL + "; stroke-width: 2; stroke-dasharray: 8, 8; fill: none;";

    Font font;
    FontRenderContext frc;
    DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
    final static String svgNS = "http://www.w3.org/2000/svg";

    Map<String, XsdExtendedElement> extendedXsdElements;

    /**
     * Constructs a new XsdDocumentationImageService with the given extended XSD elements.
     *
     * @param extendedXsdElements the extended XSD elements
     */
    public XsdDocumentationImageService(Map<String, XsdExtendedElement> extendedXsdElements) {
        this.extendedXsdElements = extendedXsdElements;

        // Moderne Schriftart mit besserer Lesbarkeit
        font = new Font("Inter", Font.PLAIN, 14);
        frc = new FontRenderContext(
                null,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
    }

    /**
     * Generates a PNG image directly from the DOM representation of the XSD element.
     * This version is more efficient and avoids serialization warnings by passing the
     * DOM document directly to the transcoder.
     *
     * @param rootElement
     * @param file        the file to save the generated image (should have a .png extension)
     * @return the file path of the generated image, or null on failure
     */
    public String generateImage(XsdExtendedElement rootElement, File file) {
        try {
            // Check for null element first
            if (rootElement == null) {
                logger.warn("Root element is null. Cannot generate image.");
                return null;
            }
            
            // 1. SVG-DOM-Dokument wie zuvor generieren
            Document svgDocument = generateSvgDocument(rootElement);
            if (svgDocument.getDocumentElement() == null || !svgDocument.getDocumentElement().hasChildNodes()) {
                logger.warn("Generated SVG for {} is empty, skipping image creation.", rootElement.getCurrentXpath());
                return null;
            }

            // 2. SVG für PNG-Rendering optimieren (CSS-Styles entfernen, interaktive Elemente deaktivieren)
            Document pngOptimizedSvg = optimizeSvgForPngRendering(svgDocument);

            // 3. PNG-Transcoder initialisieren
            PNGTranscoder transcoder = new PNGTranscoder();

            // Configure transcoder for better compatibility
            transcoder.addTranscodingHint(PNGTranscoder.KEY_FORCE_TRANSPARENT_WHITE, false);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);

            // 4. Input für den Transcoder direkt aus dem optimierten DOM-Dokument erstellen.
            TranscoderInput input = new TranscoderInput(pngOptimizedSvg);

            // 5. Ressourcen sicher mit try-with-resources verwalten
            try (OutputStream outputStream = new FileOutputStream(file)) {
                TranscoderOutput output = new TranscoderOutput(outputStream);

                // 6. Konvertierung durchführen
                transcoder.transcode(input, output);
            }

            logger.debug("Successfully created PNG image: {}", file.getAbsolutePath());
            return file.getAbsolutePath();

        } catch (IOException | TranscoderException e) {
            logger.error("Failed to generate image for file '{}'", file.getAbsolutePath(), e);
        }

        return null;
    }

    /**
     * Generates an SVG string representation of the XSD element.
     *
     * @param rootElement
     * @return the SVG string representation
     */
    public String generateSvgString(XsdExtendedElement rootElement) {
        Document svgDocument = generateSvgDocument(rootElement);
        return asString(svgDocument.getDocumentElement());
    }

    private Document generateSvgDocument(XsdExtendedElement rootElement) {
        final Document document = domImpl.createDocument(svgNS, "svg", null);
        var svgRoot = document.getDocumentElement();

        // Moderne Defs-Sektion mit verbesserten Effekten
        Element defs = document.createElementNS(svgNS, "defs");

        // Moderne CSS-Styles mit sanften Übergängen
        Element styleElement = document.createElementNS(svgNS, "style");
        String css = ".hoverable-rect { transition: all 0.3s ease; cursor: pointer; } " +
                ".hoverable-rect:hover { fill: " + COLOR_BOX_FILL_HOVER + "; transform: translateY(-2px); " +
                "filter: drop-shadow(0 4px 8px " + COLOR_SHADOW + "); } " +
                ".connection-line { transition: stroke-width 0.2s ease; } " +
                ".connection-line:hover { stroke-width: 3; } " +
                ".cardinality-text { font-weight: 500; font-size: 11px; }";
        styleElement.appendChild(document.createTextNode(css));
        defs.appendChild(styleElement);

        // Moderne Drop-Shadow-Filter
        Element filter = document.createElementNS(svgNS, "filter");
        filter.setAttribute("id", "modern-shadow");
        filter.setAttribute("x", "-50%");
        filter.setAttribute("y", "-50%");
        filter.setAttribute("width", "200%");
        filter.setAttribute("height", "200%");
        
        Element feGaussianBlur = document.createElementNS(svgNS, "feGaussianBlur");
        feGaussianBlur.setAttribute("in", "SourceAlpha");
        feGaussianBlur.setAttribute("stdDeviation", "3");
        filter.appendChild(feGaussianBlur);

        Element feOffset = document.createElementNS(svgNS, "feOffset");
        feOffset.setAttribute("dx", "0");
        feOffset.setAttribute("dy", "2");
        feOffset.setAttribute("result", "offsetblur");
        filter.appendChild(feOffset);

        Element feFlood = document.createElementNS(svgNS, "feFlood");
        feFlood.setAttribute("flood-color", "black");
        feFlood.setAttribute("flood-opacity", "0.08");
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

        // Modernes Plus-Icon
        Element plusIcon = document.createElementNS(svgNS, "g");
        plusIcon.setAttribute("id", "modern-plus-icon");
        plusIcon.setAttribute("style", "stroke: " + COLOR_ICON + "; stroke-width: 2; stroke-linecap: round;");
        
        Element circle = document.createElementNS(svgNS, "circle");
        circle.setAttribute("cx", "12");
        circle.setAttribute("cy", "12");
        circle.setAttribute("r", "10");
        circle.setAttribute("fill", COLOR_BOX_FILL);
        circle.setAttribute("stroke", COLOR_ICON);
        circle.setAttribute("stroke-width", "1.5");
        plusIcon.appendChild(circle);

        Element line1 = document.createElementNS(svgNS, "line");
        line1.setAttribute("x1", "12");
        line1.setAttribute("y1", "7");
        line1.setAttribute("x2", "12");
        line1.setAttribute("y2", "17");
        plusIcon.appendChild(line1);

        Element line2 = document.createElementNS(svgNS, "line");
        line2.setAttribute("x1", "7");
        line2.setAttribute("y1", "12");
        line2.setAttribute("x2", "17");
        line2.setAttribute("y2", "12");
        plusIcon.appendChild(line2);
        defs.appendChild(plusIcon);

        svgRoot.appendChild(defs);

        if (rootElement == null) {
            logger.warn("Root element is null. Cannot generate diagram.");
            return document;
        }

        // Sammle Kind-Elemente
        List<XsdExtendedElement> childElements = new ArrayList<>();
        if (rootElement.getChildren() != null) {
            for (String temp : rootElement.getChildren()) {
                if (extendedXsdElements.get(temp) != null) {
                    childElements.add(extendedXsdElements.get(temp));
                }
            }
        }

        // Berechne symmetrische Größen
        double maxChildWidth = calculateMaxChildWidth(childElements);

        var rootElementBounds = font.getStringBounds(rootElement.getElementName(), frc);
        var rootElementHeight = rootElementBounds.getBounds2D().getHeight();
        var rootElementWidth = rootElementBounds.getBounds2D().getWidth();

        // Berechne die tatsächliche Gesamthöhe der Kind-Elemente
        double totalChildElementsHeight = calculateTotalChildElementsHeight(childElements);

        // Berechne Dokumentationshöhe vor der Positionierung
        double docHeightTotal = calculateDocumentationHeight(rootElement.getDocumentations(), rootElementWidth);

        // Zentriere das Root-Element vertikal in der Mitte der Kind-Elemente
        int rootStartX = margin * 2;
        int rootStartY = (int) ((totalChildElementsHeight / 2) - ((boxPadding * 2 + rootElementHeight) / 2));

        // Zeichne das Root-Element mit modernem Design
        Element leftRootLink = document.createElementNS(svgNS, "a");
        leftRootLink.setAttribute("href", "#");

        Element rect1 = createModernSvgRect(document, rootElement.getCurrentXpath(),
                rootElementHeight, rootElementWidth,
                String.valueOf(rootStartX), String.valueOf(rootStartY));
        rect1.setAttribute("filter", "url(#modern-shadow)");
        rect1.setAttribute("style", rootElement.isMandatory() ? MANDATORY_FORMAT : OPTIONAL_FORMAT);
        rect1.setAttribute("class", "hoverable-rect");

        Element text1 = createSvgTextElement(document, rootElement.getElementName(),
                String.valueOf(rootStartX + boxPadding),
                String.valueOf(rootStartY + boxPadding + rootElementHeight),
                COLOR_TEXT_PRIMARY, font.getSize());
        
        leftRootLink.appendChild(rect1);
        leftRootLink.appendChild(text1);
        svgRoot.appendChild(leftRootLink);

        // Dokumentation hinzufügen
        generateModernDocumentationElement(document, rootElement.getDocumentations(),
                rootElementWidth, rootElementHeight, rootStartX, rootStartY);

        // Symmetrische Positionierung der Kind-Elemente mit mehr Abstand für Kardinalität
        final double rightStartX = rootStartX + boxPadding * 2 + rootElementWidth + gapBetweenSides + 40;
        final double rootPathEndX = rootStartX + boxPadding * 2 + rootElementWidth;
        final double rootPathCenterY = rootStartY + (boxPadding * 2 + rootElementHeight) / 2;

        // Bestimme Sequenz/Choice-Typ
        boolean isSequence = false;
        boolean isChoice = false;
        if (!childElements.isEmpty()) {
            XsdExtendedElement firstChild = childElements.getFirst();
            if (firstChild.getCurrentNode() != null && firstChild.getCurrentNode().getParentNode() != null) {
                var parentOfChildren = firstChild.getCurrentNode().getParentNode();
                String parentName = parentOfChildren.getLocalName();
                if ("sequence".equals(parentName)) {
                    isSequence = true;
                } else if ("choice".equals(parentName)) {
                    isChoice = true;
                }
            }
        }

        // Zeichne moderne Symbole für Sequenz/Choice
        double childPathStartX = rootPathEndX + gapBetweenSides;
        double childPathStartY = rootPathCenterY;

        if (isSequence || isChoice) {
            childPathStartX = drawModernSequenceChoiceSymbol(document, rootPathEndX, rootPathCenterY,
                    gapBetweenSides, isSequence, isChoice, childElements, svgRoot);
        }

        // Zeichne Kind-Elemente mit symmetrischem Layout
        double actualHeight = margin * 2;
        final double finalRightStartX = rightStartX;

        for (int i = 0; i < childElements.size(); i++) {
            XsdExtendedElement childElement = childElements.get(i);
            String elementName = childElement.getElementName();
            String elementType = childElement.getElementType() != null ? childElement.getElementType() : "";

            var nameBounds = font.getStringBounds(elementName, frc);
            var typeBounds = font.getStringBounds(elementType, frc);

            double nameHeight = nameBounds.getBounds2D().getHeight();
            double typeHeight = elementType.isBlank() ? 0 : typeBounds.getBounds2D().getHeight() + 8;
            double totalContentHeight = nameHeight + typeHeight;

            // Zentriere das Element vertikal wenn es das einzige ist
            double elementY = actualHeight;
            if (childElements.size() == 1) {
                double maxChildHeight = calculateMaxChildHeight(childElements);
                elementY = (maxChildHeight / 2) - ((boxPadding * 2 + totalContentHeight) / 2);
            }

            // Moderne Box für Kind-Element
            Element rightBox = createModernSvgRect(document, childElement.getCurrentXpath(), totalContentHeight,
                    maxChildWidth, String.valueOf(finalRightStartX), String.valueOf(elementY));
            rightBox.setAttribute("filter", "url(#modern-shadow)");
            rightBox.setAttribute("style", childElement.isMandatory() ? MANDATORY_FORMAT : OPTIONAL_FORMAT);
            rightBox.setAttribute("class", "hoverable-rect");

            // Text-Gruppe mit modernem Layout
            Element textGroup = document.createElementNS(svgNS, "g");
            double nameY = elementY + boxPadding + nameHeight;
            Element nameTextNode = createSvgTextElement(document, elementName,
                    String.valueOf(finalRightStartX + boxPadding), String.valueOf(nameY),
                    COLOR_TEXT_PRIMARY, font.getSize());
            textGroup.appendChild(nameTextNode);

            if (!elementType.isBlank()) {
                // Moderne Trennlinie
                double lineY = nameY + 6;
                Element line = document.createElementNS(svgNS, "line");
                line.setAttribute("x1", String.valueOf(finalRightStartX + boxPadding));
                line.setAttribute("y1", String.valueOf(lineY));
                line.setAttribute("x2", String.valueOf(finalRightStartX + boxPadding + maxChildWidth));
                line.setAttribute("y2", String.valueOf(lineY));
                line.setAttribute("stroke", COLOR_STROKE_SEPARATOR);
                line.setAttribute("stroke-width", "1");
                textGroup.appendChild(line);

                double typeY = lineY + typeBounds.getBounds2D().getHeight() + 4;
                Element typeTextNode = createSvgTextElement(document, elementType,
                        String.valueOf(finalRightStartX + boxPadding), String.valueOf(typeY),
                        COLOR_TEXT_SECONDARY, font.getSize() - 1);
                textGroup.appendChild(typeTextNode);
            }

            // Modernes Plus-Icon
            Element useIcon = null;
            if (childElement.hasChildren()) {
                useIcon = document.createElementNS(svgNS, "use");
                useIcon.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#modern-plus-icon");
                useIcon.setAttribute("x", String.valueOf(finalRightStartX + boxPadding + maxChildWidth + margin));
                double boxCenterY = elementY + (boxPadding * 2 + totalContentHeight) / 2;
                useIcon.setAttribute("y", String.valueOf(boxCenterY - 12));
            }

            // Link um die Box
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

            // Moderne Verbindungslinie
            double pathEndY = elementY + (boxPadding * 2 + totalContentHeight) / 2;
            Element path = document.createElementNS(svgNS, "path");
            path.setAttribute("d", "M " + childPathStartX + " " + childPathStartY + " V " + pathEndY + " H " + finalRightStartX);
            path.setAttribute("class", "connection-line");
            path.setAttribute("style", childElement.isMandatory() ? CONNECTION_LINE : CONNECTION_LINE_OPTIONAL);
            svgRoot.appendChild(path);

            // Moderne Kardinalität
            Node childNode = childElement.getCurrentNode();
            String cardinality = formatCardinality(
                    getAttributeValue(childNode, "minOccurs", "1"),
                    getAttributeValue(childNode, "maxOccurs", "1")
            );

            if (!cardinality.isBlank()) {
                int cardinalityFontSize = font.getSize() - 3;
                var cardinalityBounds = font.getStringBounds(cardinality, frc);

                // Positioniere Kardinalität in der Mitte der Verbindungslinie mit mehr Abstand
                double horizontalLineCenter = childPathStartX + (finalRightStartX - childPathStartX) / 2;
                double cardinalityX = horizontalLineCenter - (cardinalityBounds.getWidth() / 2);
                double cardinalityY = pathEndY - 12; // Mehr Abstand zur Linie

                Element cardinalityTextNode = createSvgTextElement(document, cardinality,
                        String.valueOf(cardinalityX), String.valueOf(cardinalityY),
                        COLOR_TEXT_SECONDARY, cardinalityFontSize);
                cardinalityTextNode.setAttribute("class", "cardinality-text");
                svgRoot.appendChild(cardinalityTextNode);
            }

            actualHeight += boxPadding * 2 + totalContentHeight + margin * 2;
        }

        // Finalisiere SVG-Größe mit symmetrischem Layout
        var imageHeight = Math.max(docHeightTotal, actualHeight);
        svgRoot.setAttribute("height", String.valueOf(imageHeight + margin * 2));
        svgRoot.setAttribute("width", String.valueOf(finalRightStartX + boxPadding * 2 + maxChildWidth + margin * 3));
        svgRoot.setAttribute("style", "background-color: " + COLOR_BG);

        return document;
    }

    /**
     * Berechnet die Höhe der Dokumentation für die Layout-Planung
     */
    private double calculateDocumentationHeight(List<XsdExtendedElement.DocumentationInfo> xsdDocumentation, double rootElementWidth) {
        if (xsdDocumentation == null || xsdDocumentation.isEmpty()) {
            return 0;
        }

        final Font docFont = font.deriveFont((float) font.getSize() - 1);
        double totalHeight = 0;

        for (var documentation : xsdDocumentation) {
            StringWriter writer = new StringWriter();
            int length = 0;

            for (String word : documentation.content().split(" ")) {
                var rectangle2D = docFont.getStringBounds(word + " ", frc);
                var docHeight = rectangle2D.getBounds2D().getHeight();
                var docWidth = rectangle2D.getBounds2D().getWidth();

                if ((docWidth + length) > (rootElementWidth + (boxPadding * 2))) {
                    totalHeight += docHeight;
                    writer = new StringWriter();
                    length = 0;
                }
                length += (int) docWidth;
                writer.append(word).append(" ");
            }
            if (!writer.toString().isBlank()) {
                var rectangle2D = docFont.getStringBounds(" ", frc);
                totalHeight += rectangle2D.getBounds2D().getHeight();
            }
        }

        return totalHeight + boxPadding * 2;
    }

    /**
     * Berechnet die Gesamthöhe aller Kind-Elemente für die Zentrierung
     */
    private double calculateTotalChildElementsHeight(List<XsdExtendedElement> childElements) {
        if (childElements.isEmpty()) {
            return 0;
        }

        double totalHeight = margin * 2; // Start-Margin

        for (XsdExtendedElement childElement : childElements) {
            String elementName = childElement.getElementName();
            String elementType = childElement.getElementType() != null ? childElement.getElementType() : "";

            var nameBounds = font.getStringBounds(elementName, frc);
            var typeBounds = font.getStringBounds(elementType, frc);

            double nameHeight = nameBounds.getBounds2D().getHeight();
            double typeHeight = elementType.isBlank() ? 0 : typeBounds.getBounds2D().getHeight() + 8;
            double totalContentHeight = nameHeight + typeHeight;

            totalHeight += boxPadding * 2 + totalContentHeight + margin * 2;
        }

        return totalHeight;
    }

    /**
     * Berechnet die maximale Höhe der Kind-Elemente für symmetrisches Layout
     */
    private double calculateMaxChildHeight(List<XsdExtendedElement> childElements) {
        double maxHeight = 0;
        for (XsdExtendedElement childElement : childElements) {
            String elementName = childElement.getElementName();
            String elementType = childElement.getElementType() != null ? childElement.getElementType() : "";

            var nameBounds = font.getStringBounds(elementName, frc);
            var typeBounds = font.getStringBounds(elementType, frc);

            double nameHeight = nameBounds.getBounds2D().getHeight();
            double typeHeight = elementType.isBlank() ? 0 : typeBounds.getBounds2D().getHeight() + 8;
            double totalHeight = boxPadding * 2 + nameHeight + typeHeight;

            maxHeight = Math.max(maxHeight, totalHeight);
        }
        return maxHeight;
    }

    /**
     * Berechnet die maximale Breite der Kind-Elemente für symmetrisches Layout
     */
    private double calculateMaxChildWidth(List<XsdExtendedElement> childElements) {
        double maxWidth = 0;
        for (XsdExtendedElement childElement : childElements) {
            String elementName = childElement.getElementName();
            String elementType = childElement.getElementType() != null ? childElement.getElementType() : "";

            var nameBounds = font.getStringBounds(elementName, frc);
            var typeBounds = font.getStringBounds(elementType, frc);

            double nameWidth = nameBounds.getBounds2D().getWidth();
            double typeWidth = typeBounds.getBounds2D().getWidth();

            maxWidth = Math.max(maxWidth, Math.max(nameWidth, typeWidth));
        }
        return maxWidth + boxPadding * 2;
    }

    /**
     * Zeichnet moderne Sequenz/Choice-Symbole
     */
    private double drawModernSequenceChoiceSymbol(Document document, double rootPathEndX, double rootPathCenterY,
                                                  double gapBetweenSides, boolean isSequence, boolean isChoice,
                                                  List<XsdExtendedElement> childElements, Element svgRoot) {

        final double symbolWidth = isSequence ? 50 : 30;
        final double symbolHeight = 30;
        final double symbolCenterX = rootPathEndX + (gapBetweenSides / 2);
        final double symbolCenterY = rootPathCenterY;

        // Moderne Verbindungslinie zum Symbol
        Element pathToSymbol = document.createElementNS(svgNS, "path");
        pathToSymbol.setAttribute("d", "M " + rootPathEndX + " " + rootPathCenterY + " H " + (symbolCenterX - symbolWidth / 2));
        pathToSymbol.setAttribute("class", "connection-line");
        pathToSymbol.setAttribute("style", CONNECTION_LINE);
        svgRoot.appendChild(pathToSymbol);

        // Gruppierungskardinalität
        String groupCardinality = "";
        Node particleNode = null;
        if (!childElements.isEmpty() && childElements.getFirst().getCurrentNode() != null) {
            particleNode = childElements.getFirst().getCurrentNode().getParentNode();
        }

        if (particleNode != null) {
            groupCardinality = formatCardinality(
                    getAttributeValue(particleNode, "minOccurs", "1"),
                    getAttributeValue(particleNode, "maxOccurs", "1")
            );
        }

        // Zeige Kardinalität über dem Symbol (immer anzeigen)
        if (!groupCardinality.isBlank()) {
            int cardinalityFontSize = font.getSize() - 3;
            var cardinalityBounds = font.getStringBounds(groupCardinality, frc);
            double cardinalityX = symbolCenterX - (cardinalityBounds.getWidth() / 2);
            double cardinalityY = symbolCenterY - (symbolHeight / 2) - 12; // Mehr Abstand zum Symbol
            Element cardinalityTextNode = createSvgTextElement(document, groupCardinality,
                    String.valueOf(cardinalityX), String.valueOf(cardinalityY),
                    COLOR_TEXT_SECONDARY, cardinalityFontSize);
            cardinalityTextNode.setAttribute("class", "cardinality-text");
            svgRoot.appendChild(cardinalityTextNode);
        }

        if (isSequence) {
            // Modernes Sequenz-Symbol
            Element seqGroup = document.createElementNS(svgNS, "g");
            seqGroup.setAttribute("id", "seq-group_" + System.currentTimeMillis());

            // Moderne Box mit abgerundeten Ecken
            Element seqRect = document.createElementNS(svgNS, "rect");
            seqRect.setAttribute("x", String.valueOf(symbolCenterX - symbolWidth / 2));
            seqRect.setAttribute("y", String.valueOf(symbolCenterY - symbolHeight / 2));
            seqRect.setAttribute("width", String.valueOf(symbolWidth));
            seqRect.setAttribute("height", String.valueOf(symbolHeight));
            seqRect.setAttribute("fill", COLOR_BOX_FILL);
            seqRect.setAttribute("stroke", COLOR_ACCENT);
            seqRect.setAttribute("stroke-width", "2");
            seqRect.setAttribute("rx", String.valueOf(borderRadius));
            seqRect.setAttribute("ry", String.valueOf(borderRadius));
            seqGroup.appendChild(seqRect);

            // Moderne Trennlinie
            Element line = document.createElementNS(svgNS, "line");
            line.setAttribute("x1", String.valueOf(symbolCenterX - symbolWidth / 2 + 8));
            line.setAttribute("y1", String.valueOf(symbolCenterY));
            line.setAttribute("x2", String.valueOf(symbolCenterX + symbolWidth / 2 - 8));
            line.setAttribute("y2", String.valueOf(symbolCenterY));
            line.setAttribute("stroke", COLOR_TEXT_SECONDARY);
            line.setAttribute("stroke-width", "1.5");
            seqGroup.appendChild(line);

            // Moderne Punkte
            double dotSpacing = 10;
            seqGroup.appendChild(createModernDot(document, symbolCenterX - dotSpacing, symbolCenterY));
            seqGroup.appendChild(createModernDot(document, symbolCenterX, symbolCenterY));
            seqGroup.appendChild(createModernDot(document, symbolCenterX + dotSpacing, symbolCenterY));

            svgRoot.appendChild(seqGroup);

        } else { // isChoice
            // Modernes Choice-Symbol (Raute)
            Element choiceDiamond = document.createElementNS(svgNS, "polygon");
            String points = (symbolCenterX) + "," + (symbolCenterY - symbolHeight / 2) + " " +
                    (symbolCenterX + symbolWidth / 2) + "," + (symbolCenterY) + " " +
                    (symbolCenterX) + "," + (symbolCenterY + symbolHeight / 2) + " " +
                    (symbolCenterX - symbolWidth / 2) + "," + (symbolCenterY);
            choiceDiamond.setAttribute("points", points);
            choiceDiamond.setAttribute("fill", COLOR_BOX_FILL);
            choiceDiamond.setAttribute("stroke", COLOR_ACCENT);
            choiceDiamond.setAttribute("stroke-width", "2");
            svgRoot.appendChild(choiceDiamond);
        }

        // Moderne Trennlinie nach dem Symbol
        final double postSymbolLineLength = 25;
        Element postSymbolLine = document.createElementNS(svgNS, "path");
        postSymbolLine.setAttribute("d", "M " + (symbolCenterX + symbolWidth / 2) + " " + symbolCenterY + " h " + postSymbolLineLength);
        postSymbolLine.setAttribute("class", "connection-line");
        postSymbolLine.setAttribute("style", CONNECTION_LINE);
        svgRoot.appendChild(postSymbolLine);

        return symbolCenterX + symbolWidth / 2 + postSymbolLineLength;
    }

    /**
     * Erstellt ein modernes SVG-Rechteck mit abgerundeten Ecken
     */
    private Element createModernSvgRect(Document document, String id, double contentHeight, double contentWidth, String x, String y) {
        Element rect = document.createElementNS(svgNS, "rect");
        rect.setAttribute("fill", COLOR_BOX_FILL);
        rect.setAttribute("id", id);
        rect.setAttribute("height", String.valueOf(boxPadding * 2 + contentHeight));
        rect.setAttribute("width", String.valueOf(boxPadding * 2 + contentWidth));
        rect.setAttribute("x", x);
        rect.setAttribute("y", y);
        rect.setAttribute("rx", String.valueOf(borderRadius));
        rect.setAttribute("ry", String.valueOf(borderRadius));
        return rect;
    }

    /**
     * Erstellt einen modernen Punkt für Sequenz-Symbole
     */
    private Element createModernDot(Document document, double cx, double cy) {
        Element circle = document.createElementNS(svgNS, "circle");
        circle.setAttribute("cx", String.valueOf(cx));
        circle.setAttribute("cy", String.valueOf(cy));
        circle.setAttribute("r", "2");
        circle.setAttribute("fill", COLOR_ACCENT);
        return circle;
    }

    /**
     * Creates a small SVG circle element, used as a dot in diagrams.
     */
    private Element createDot(Document document, double cx, double cy) {
        Element circle = document.createElementNS(svgNS, "circle");
        circle.setAttribute("cx", String.valueOf(cx));
        circle.setAttribute("cy", String.valueOf(cy));
        circle.setAttribute("r", "1.5");
        circle.setAttribute("fill", COLOR_TEXT_SECONDARY);
        return circle;
    }

    /**
     * Formats minOccurs and maxOccurs into a human-readable cardinality string.
     * e.g., (1, 1) -> "1", (0, 1) -> "0..1", (1, unbounded) -> "1..*"
     *
     * @param minOccurs The minOccurs value from the XSD.
     * @param maxOccurs The maxOccurs value from the XSD.
     * @return A formatted string representing the cardinality.
     */
    private String formatCardinality(String minOccurs, String maxOccurs) {
        if (minOccurs == null || maxOccurs == null) {
            return ""; // Default to empty if data is missing
        }

        // Replace "unbounded" with a more common diagram symbol
        final String max = "unbounded".equalsIgnoreCase(maxOccurs) ? "*" : maxOccurs;

        // Otherwise, format as a range.
        return minOccurs + ".." + max;
    }

    /**
     * Helper to get an attribute's value from a DOM Node, with a default value.
     */
    private String getAttributeValue(Node node, String attrName, String defaultValue) {
        if (node == null || node.getAttributes() == null) {
            return defaultValue;
        }
        Node attrNode = node.getAttributes().getNamedItem(attrName);
        return (attrNode != null) ? attrNode.getNodeValue() : defaultValue;
    }

    /**
     * Creates an SVG rectangle element in the correct namespace.
     */
    private Element createSvgRect(Document document, String id, double contentHeight, double contentWidth, String x, String y) {
        Element rect = document.createElementNS(svgNS, "rect");
        rect.setAttribute("fill", COLOR_BOX_FILL);
        rect.setAttribute("id", id);
        rect.setAttribute("height", String.valueOf(margin + contentHeight + margin));
        rect.setAttribute("width", String.valueOf(margin + contentWidth + margin));
        rect.setAttribute("x", x);
        rect.setAttribute("y", y);
        rect.setAttribute("rx", "4"); // Etwas rundere Ecken
        rect.setAttribute("ry", "4");
        return rect;
    }

    /**
     * Creates an SVG text element in the correct namespace.
     */
    private Element createSvgTextElement(Document document, String textContent, String x, String y, String fill, int fontSize) {
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
     * Generates and appends the documentation block to the SVG with modern styling.
     */
    private double generateModernDocumentationElement(Document document, List<XsdExtendedElement.DocumentationInfo> xsdDocumentation,
                                                      double rootElementWidth, double rootElementHeight, int startX, int startY) {
        // Moderne Schriftart für Dokumentation
        final Font docFont = font.deriveFont((float) font.getSize() - 1);

        final var docTextGroup = document.createElementNS(svgNS, "g");
        docTextGroup.setAttribute("id", "modern-comment");

        final var docText = document.createElementNS(svgNS, "text");
        docText.setAttribute("x", String.valueOf(startX + boxPadding));
        docText.setAttribute("y", String.valueOf(startY + (boxPadding * 3) + rootElementHeight + (boxPadding / 2.0)));
        docText.setAttribute("fill", COLOR_TEXT_SECONDARY);
        docText.setAttribute("font-size", String.valueOf(docFont.getSize()));
        docText.setAttribute("font-family", docFont.getFontName());
        docText.setAttribute("font-style", "italic");

        double docHeightTotal = 0;
        for (var documentation : xsdDocumentation) {
            StringWriter writer = new StringWriter();
            int length = 0;

            for (String word : documentation.content().split(" ")) {
                var rectangle2D = docFont.getStringBounds(word + " ", frc);
                var docHeight = rectangle2D.getBounds2D().getHeight();
                var docWidth = rectangle2D.getBounds2D().getWidth();

                if ((docWidth + length) > (rootElementWidth + (boxPadding * 2))) {
                    final var tspan = document.createElementNS(svgNS, "tspan");
                    tspan.setAttribute("x", String.valueOf(boxPadding + 15));
                    tspan.setAttribute("dy", "1.3em");
                    tspan.setTextContent(writer.toString());
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
                tspan.setAttribute("x", String.valueOf(boxPadding + 15));
                tspan.setAttribute("dy", "1.3em");
                tspan.setTextContent(writer.toString());
                docText.appendChild(tspan);
            }
        }

        docTextGroup.appendChild(docText);
        document.getDocumentElement().appendChild(docTextGroup);

        return startY + boxPadding + docHeightTotal + boxPadding;
    }

    /**
     * Optimizes the SVG document for PNG rendering by removing interactive CSS styles
     * and hover effects that prevent proper transcoding.
     *
     * @param originalSvg the original SVG document
     * @return an optimized SVG document suitable for PNG transcoding
     */
    private Document optimizeSvgForPngRendering(Document originalSvg) {
        try {
            logger.debug("Starting SVG optimization for PNG rendering");

            // Clone the original document to avoid modifying it
            Document optimizedSvg = (Document) originalSvg.cloneNode(true);

            // Remove CSS styles that cause issues with PNG transcoding
            removeInteractiveStyles(optimizedSvg);

            // Remove hover effects and transitions
            removeHoverEffects(optimizedSvg);

            // Ensure all elements have explicit styling instead of CSS classes
            applyExplicitStyles(optimizedSvg);

            logger.debug("SVG optimization completed successfully");
            return optimizedSvg;
        } catch (Exception e) {
            logger.warn("Failed to optimize SVG for PNG rendering, using original: {}", e.getMessage(), e);
            return originalSvg;
        }
    }

    /**
     * Removes interactive CSS styles from the SVG document.
     */
    private void removeInteractiveStyles(Document svg) {
        Element root = svg.getDocumentElement();
        if (root == null) return;

        // Remove style elements that contain interactive CSS
        NodeList styleElements = root.getElementsByTagNameNS(svgNS, "style");
        for (int i = styleElements.getLength() - 1; i >= 0; i--) {
            Element styleElement = (Element) styleElements.item(i);
            String cssContent = styleElement.getTextContent();

            // Remove hover effects, transitions, and cursor styles
            if (cssContent.contains(":hover") || cssContent.contains("transition") ||
                    cssContent.contains("cursor") || cssContent.contains("transform")) {
                styleElement.getParentNode().removeChild(styleElement);
            }
        }
    }

    /**
     * Removes hover effects and interactive elements from the SVG.
     */
    private void removeHoverEffects(Document svg) {
        Element root = svg.getDocumentElement();
        if (root == null) return;

        // Remove class attributes that reference interactive styles
        removeClassAttributes(root);
    }

    /**
     * Recursively removes class attributes from all elements.
     */
    private void removeClassAttributes(Element element) {
        element.removeAttribute("class");

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                removeClassAttributes((Element) child);
            }
        }
    }

    /**
     * Applies explicit styles to elements that previously relied on CSS classes.
     */
    private void applyExplicitStyles(Document svg) {
        Element root = svg.getDocumentElement();
        if (root == null) return;

        // Apply explicit styles to rectangles (boxes)
        NodeList rects = root.getElementsByTagNameNS(svgNS, "rect");
        for (int i = 0; i < rects.getLength(); i++) {
            Element rect = (Element) rects.item(i);
            String id = rect.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                // Find the corresponding element in our data to determine if it's optional
                XsdExtendedElement element = extendedXsdElements.get(id);
                if (element != null) {
                    if (!element.isMandatory()) {
                        rect.setAttribute("style", OPTIONAL_FORMAT);
                    } else {
                        rect.setAttribute("style", MANDATORY_FORMAT);
                    }
                } else {
                    // Fallback: check if the current style indicates optional
                    String currentStyle = rect.getAttribute("style");
                    if (currentStyle != null && currentStyle.contains("stroke-dasharray")) {
                        rect.setAttribute("style", OPTIONAL_FORMAT);
                    } else {
                        rect.setAttribute("style", MANDATORY_FORMAT);
                    }
                }
            }
        }

        // Apply explicit styles to paths (connection lines)
        NodeList paths = root.getElementsByTagNameNS(svgNS, "path");
        for (int i = 0; i < paths.getLength(); i++) {
            Element path = (Element) paths.item(i);
            String d = path.getAttribute("d");
            if (d != null && !d.isEmpty()) {
                // Check if this path has the optional connection style class
                String currentStyle = path.getAttribute("style");
                if (currentStyle != null && currentStyle.contains("stroke-dasharray")) {
                    path.setAttribute("style", CONNECTION_LINE_OPTIONAL);
                } else {
                    path.setAttribute("style", CONNECTION_LINE);
                }
            }
        }

        // Apply explicit styles to text elements for cardinality
        NodeList texts = root.getElementsByTagNameNS(svgNS, "text");
        for (int i = 0; i < texts.getLength(); i++) {
            Element text = (Element) texts.item(i);
            String currentClass = text.getAttribute("class");
            if ("cardinality-text".equals(currentClass)) {
                text.setAttribute("style", "font-weight: 500; font-size: 11px; fill: " + COLOR_TEXT_SECONDARY + ";");
            }
        }

        // Remove any remaining class attributes that might cause issues
        removeAllClassAttributes(root);
    }

    /**
     * Recursively removes all class attributes from the SVG document.
     */
    private void removeAllClassAttributes(Element element) {
        element.removeAttribute("class");

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                removeAllClassAttributes((Element) child);
            }
        }
    }

    /**
     * Converts a DOM node to a string representation using a thread-safe, cached Transformer.
     *
     * @param node the DOM node
     * @return the string representation of the node
     */
    private static String asString(Node node) {
        // try-with-resources stellt sicher, dass der Writer immer geschlossen wird.
        try (StringWriter writer = new StringWriter()) {
            // Hole die wiederverwendbare Transformer-Instanz für den aktuellen Thread.
            Transformer trans = transformerThreadLocal.get();

            // Passe die eine dynamische Eigenschaft für diesen spezifischen Aufruf an.
            // Dies ist entscheidend, da die Transformer-Instanz wiederverwendet wird.
            if (node instanceof Document) {
                trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            } else {
                trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }

            trans.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (final TransformerException ex) {
            logger.error("Failed to transform DOM node to string", ex);
            throw new IllegalArgumentException("Failed to transform node", ex);
        } catch (final IOException ex) {
            // StringWriter sollte keine IOException werfen, aber es ist gute Praxis, sie zu behandeln.
            logger.error("IO error during node serialization", ex);
            throw new UncheckedIOException(ex);
        }
    }
}
