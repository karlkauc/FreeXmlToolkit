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
import java.util.Locale;
import java.util.Map;

/**
 * Service for generating images and SVG diagrams from XSD documentation.
 */
public class XsdDocumentationImageService {

    private final static Logger logger = LogManager.getLogger(XsdDocumentationImageService.class);

    // A ThreadLocal that provides a reusable Transformer instance for each thread.
    // This avoids the high overhead of creating a new instance on every call to asString().
    private static final ThreadLocal<Transformer> transformerThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer trans = factory.newTransformer();
            // Configure properties that are the same for all transformations
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            trans.setOutputProperty(OutputKeys.VERSION, "1.0");
            return trans;
        } catch (TransformerConfigurationException e) {
            logger.error("Fatal error during thread-local Transformer initialization", e);
            // This is a serious configuration error that should stop the application.
            throw new IllegalStateException("Failed to create thread-local Transformer", e);
        }
    });

    // Modern spacing and layout parameters
    final int margin = 16;
    final int gapBetweenSides = 120;
    final int boxPadding = 12;
    final int borderRadius = 8;

    // XMLSpy-inspired color palette
    private static final String COLOR_BG = "#ffffff";                    // Pure white background
    private static final String COLOR_BOX_FILL_ELEMENT = "#f0f8ff";      // Light blue for elements
    private static final String COLOR_BOX_FILL_ATTRIBUTE = "#fffef7";    // Light yellow for attributes
    private static final String COLOR_BOX_FILL_STRUCTURAL = "#f8f9fa";   // Light gray for structural
    private static final String COLOR_BOX_FILL_HOVER = "#e2e8f0";        // Hover color
    private static final String COLOR_STROKE_ELEMENT = "#4a90e2";        // Blue for elements
    private static final String COLOR_STROKE_ATTRIBUTE = "#d4a147";      // Orange for attributes
    private static final String COLOR_STROKE_STRUCTURAL = "#6c757d";     // Gray for structural
    private static final String COLOR_STROKE_OPTIONAL = "#94a3b8";       // Lighter for optional
    private static final String COLOR_STROKE_SEPARATOR = "#dee2e6";      // Separator lines
    private static final String COLOR_TEXT_PRIMARY = "#2c5aa0";          // Blue text for elements
    private static final String COLOR_TEXT_SECONDARY = "#6c757d";        // Gray secondary text
    private static final String COLOR_TEXT_ATTRIBUTE = "#8b6914";        // Brown for attributes
    private static final String COLOR_ICON = "#4a90e2";                  // Blue icons
    private static final String COLOR_ACCENT = "#ff8c00";                // Orange accent
    private static final String COLOR_SHADOW = "rgba(0, 0, 0, 0.15)";    // Deeper shadow

    // XMLSpy-inspired style strings with gradients
    final static String ELEMENT_MANDATORY_FORMAT = "stroke: " + COLOR_STROKE_ELEMENT + "; stroke-width: 2; fill: url(#elementGradient);";
    final static String ELEMENT_OPTIONAL_FORMAT = "stroke: " + COLOR_STROKE_OPTIONAL + "; stroke-width: 1.5; stroke-dasharray: 5, 5; fill: url(#elementGradient);";
    final static String ATTRIBUTE_MANDATORY_FORMAT = "stroke: " + COLOR_STROKE_ATTRIBUTE + "; stroke-width: 1.5; fill: url(#attributeGradient);";
    final static String ATTRIBUTE_OPTIONAL_FORMAT = "stroke: " + COLOR_STROKE_OPTIONAL + "; stroke-width: 1; stroke-dasharray: 3, 3; fill: url(#attributeGradient);";
    final static String STRUCTURAL_FORMAT = "stroke: " + COLOR_STROKE_STRUCTURAL + "; stroke-width: 2; fill: url(#structuralGradient);";
    final static String CONNECTION_LINE = "stroke: " + COLOR_STROKE_ELEMENT + "; stroke-width: 1.5; fill: none;";
    final static String CONNECTION_LINE_OPTIONAL = "stroke: " + COLOR_STROKE_OPTIONAL + "; stroke-width: 1; stroke-dasharray: 5, 5; fill: none;";

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

        // Modern font with better readability
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

            // 1. Generate SVG DOM document as before
            Document svgDocument = generateSvgDocument(rootElement);
            if (svgDocument.getDocumentElement() == null || !svgDocument.getDocumentElement().hasChildNodes()) {
                logger.warn("Generated SVG for {} is empty, skipping image creation.", rootElement.getCurrentXpath());
                return null;
            }

            // 2. Optimize SVG for PNG rendering (remove CSS styles, disable interactive elements)
            Document pngOptimizedSvg = optimizeSvgForPngRendering(svgDocument);

            // 3. Initialize PNG transcoder
            PNGTranscoder transcoder = new PNGTranscoder();

            // Configure transcoder for better compatibility
            transcoder.addTranscodingHint(PNGTranscoder.KEY_FORCE_TRANSPARENT_WHITE, false);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);

            // 4. Create input for transcoder directly from optimized DOM document
            TranscoderInput input = new TranscoderInput(pngOptimizedSvg);

            // 5. Safely manage resources with try-with-resources
            try (OutputStream outputStream = new FileOutputStream(file)) {
                TranscoderOutput output = new TranscoderOutput(outputStream);

                // 6. Perform conversion
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

        // XMLSpy-inspired defs section with gradients and effects
        Element defs = document.createElementNS(svgNS, "defs");

        // Gradient definitions for different element types
        addGradientDefinitions(document, defs);

        // Modern CSS styles with smooth transitions
        Element styleElement = document.createElementNS(svgNS, "style");
        String css = ".hoverable-rect { transition: all 0.3s ease; cursor: pointer; } " +
                ".hoverable-rect:hover { transform: translateY(-1px); " +
                "filter: drop-shadow(0 3px 6px " + COLOR_SHADOW + "); } " +
                ".connection-line { transition: stroke-width 0.2s ease; } " +
                ".connection-line:hover { stroke-width: 2.5; } " +
                ".cardinality-text { font-weight: 600; font-size: 10px; } " +
                ".element-icon { fill: " + COLOR_ICON + "; } " +
                ".attribute-icon { fill: " + COLOR_STROKE_ATTRIBUTE + "; }";
        styleElement.appendChild(document.createTextNode(css));
        defs.appendChild(styleElement);

        // Enhanced drop shadow filter for XMLSpy look
        Element filter = document.createElementNS(svgNS, "filter");
        filter.setAttribute("id", "xmlspy-shadow");
        filter.setAttribute("x", "-50%");
        filter.setAttribute("y", "-50%");
        filter.setAttribute("width", "200%");
        filter.setAttribute("height", "200%");
        
        Element feGaussianBlur = document.createElementNS(svgNS, "feGaussianBlur");
        feGaussianBlur.setAttribute("in", "SourceAlpha");
        feGaussianBlur.setAttribute("stdDeviation", "2");
        filter.appendChild(feGaussianBlur);

        Element feOffset = document.createElementNS(svgNS, "feOffset");
        feOffset.setAttribute("dx", "1");
        feOffset.setAttribute("dy", "1");
        feOffset.setAttribute("result", "offsetblur");
        filter.appendChild(feOffset);

        Element feFlood = document.createElementNS(svgNS, "feFlood");
        feFlood.setAttribute("flood-color", "black");
        feFlood.setAttribute("flood-opacity", "0.2");
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

        // Modern plus icon
        Element plusIcon = document.createElementNS(svgNS, "g");
        plusIcon.setAttribute("id", "modern-plus-icon");
        plusIcon.setAttribute("style", "stroke: " + COLOR_ICON + "; stroke-width: 2; stroke-linecap: round;");
        
        Element circle = document.createElementNS(svgNS, "circle");
        circle.setAttribute("cx", "12");
        circle.setAttribute("cy", "12");
        circle.setAttribute("r", "10");
        circle.setAttribute("fill", COLOR_BOX_FILL_ELEMENT);
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

        // Collect child elements
        List<XsdExtendedElement> childElements = new ArrayList<>();
        if (rootElement.getChildren() != null) {
            for (String temp : rootElement.getChildren()) {
                if (extendedXsdElements.get(temp) != null) {
                    childElements.add(extendedXsdElements.get(temp));
                }
            }
        }

        // Calculate symmetric sizes
        double maxChildWidth = calculateMaxChildWidth(childElements);

        var rootElementBounds = font.getStringBounds(rootElement.getElementName(), frc);
        var rootElementHeight = rootElementBounds.getBounds2D().getHeight();
        var rootElementWidth = rootElementBounds.getBounds2D().getWidth();

        // Calculate the actual total height of child elements
        double totalChildElementsHeight = calculateTotalChildElementsHeight(childElements);

        // Calculate documentation height before positioning
        double docHeightTotal = calculateDocumentationHeight(rootElement.getDocumentations(), rootElementWidth);

        // Center the root element vertically in the middle of child elements
        int rootStartX = margin * 2;
        int rootStartY = (int) ((totalChildElementsHeight / 2) - ((boxPadding * 2 + rootElementHeight) / 2));

        // Draw the root element with modern design
        Element leftRootLink = document.createElementNS(svgNS, "a");
        leftRootLink.setAttribute("href", "#");

        Element rect1 = createModernSvgRect(document, rootElement.getCurrentXpath(),
                rootElementHeight, rootElementWidth,
                String.valueOf(rootStartX), String.valueOf(rootStartY));
        rect1.setAttribute("filter", "url(#xmlspy-shadow)");
        rect1.setAttribute("style", rootElement.isMandatory() ? ELEMENT_MANDATORY_FORMAT : ELEMENT_OPTIONAL_FORMAT);
        rect1.setAttribute("class", "hoverable-rect");

        Element text1 = createSvgTextElement(document, rootElement.getElementName(),
                String.valueOf(rootStartX + boxPadding),
                String.valueOf(rootStartY + boxPadding + rootElementHeight),
                COLOR_TEXT_PRIMARY, font.getSize());
        
        leftRootLink.appendChild(rect1);
        leftRootLink.appendChild(text1);
        svgRoot.appendChild(leftRootLink);

        // Add documentation
        generateModernDocumentationElement(document, rootElement.getDocumentations(),
                rootElementWidth, rootElementHeight, rootStartX, rootStartY);

        // Symmetric positioning of child elements with more space for cardinality
        final double rightStartX = rootStartX + boxPadding * 2 + rootElementWidth + gapBetweenSides + 40;
        final double rootPathEndX = rootStartX + boxPadding * 2 + rootElementWidth;
        final double rootPathCenterY = rootStartY + (boxPadding * 2 + rootElementHeight) / 2;

        // Determine sequence/choice type
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

        // Draw modern symbols for sequence/choice
        double childPathStartX = rootPathEndX + gapBetweenSides;
        double childPathStartY = rootPathCenterY;

        // Calculate symbol position explicitly
        final double symbolCenterX = rootPathEndX + (gapBetweenSides / 2.0);
        final double symbolCenterY = rootPathCenterY; // Y-Position der Symbol-Mitte
        final double symbolWidth = isSequence ? 50 : (isChoice ? 30 : 0);
        final double finalSymbolWidth = symbolWidth;
        final double finalSymbolCenterX = symbolCenterX;

        double symbolEndX = rootPathEndX; // Default to root element end
        if (isSequence || isChoice) {
            symbolEndX = drawModernSequenceChoiceSymbol(document, rootPathEndX, rootPathCenterY,
                    gapBetweenSides, isSequence, isChoice, childElements, svgRoot);
        }

        // Draw child elements with symmetric layout
        double actualHeight = margin * 2;
        final double finalRightStartX = rightStartX;

        // Make sequence/choice flags and symbol position available in the loop
        final boolean hasSequenceOrChoice = isSequence || isChoice;
        final double finalSymbolEndX = symbolEndX; // Use the actual returned value
        final double finalSymbolCenterY = symbolCenterY;

        for (int i = 0; i < childElements.size(); i++) {
            XsdExtendedElement childElement = childElements.get(i);
            String elementName = childElement.getElementName();
            String elementType = childElement.getElementType() != null ? childElement.getElementType() : "";

            var nameBounds = font.getStringBounds(elementName, frc);
            var typeBounds = font.getStringBounds(elementType, frc);

            double nameHeight = nameBounds.getBounds2D().getHeight();
            double typeHeight = elementType.isBlank() ? 0 : typeBounds.getBounds2D().getHeight() + 8;
            double totalContentHeight = nameHeight + typeHeight;

            // Center the element vertically if it's the only one
            double elementY = actualHeight;
            if (childElements.size() == 1) {
                double maxChildHeight = calculateMaxChildHeight(childElements);
                elementY = (maxChildHeight / 2) - ((boxPadding * 2 + totalContentHeight) / 2);
            }

            // Modern box for child element
            Element rightBox = createModernSvgRect(document, childElement.getCurrentXpath(), totalContentHeight,
                    maxChildWidth, String.valueOf(finalRightStartX), String.valueOf(elementY));
            rightBox.setAttribute("filter", "url(#xmlspy-shadow)");
            rightBox.setAttribute("style", childElement.isMandatory() ? ELEMENT_MANDATORY_FORMAT : ELEMENT_OPTIONAL_FORMAT);
            rightBox.setAttribute("class", "hoverable-rect");

            // Text group with modern layout
            Element textGroup = document.createElementNS(svgNS, "g");
            double nameY = elementY + boxPadding + nameHeight;
            Element nameTextNode = createSvgTextElement(document, elementName,
                    String.valueOf(finalRightStartX + boxPadding), String.valueOf(nameY),
                    COLOR_TEXT_PRIMARY, font.getSize());
            textGroup.appendChild(nameTextNode);

            if (!elementType.isBlank()) {
                // Modern separator line
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

            // Modern plus icon
            Element useIcon = null;
            if (childElement.hasChildren()) {
                useIcon = document.createElementNS(svgNS, "use");
                useIcon.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#modern-plus-icon");
                useIcon.setAttribute("x", String.valueOf(finalRightStartX + boxPadding + maxChildWidth + margin));
                double boxCenterY = elementY + (boxPadding * 2 + totalContentHeight) / 2;
                useIcon.setAttribute("y", String.valueOf(boxCenterY - 12));
            }

            // Link around the box
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

            // L-shaped connection line from sequence/choice symbol to child element
            double childElementCenterY = elementY + (boxPadding * 2 + totalContentHeight) / 2;

            // Start coordinates based on whether we have a sequence/choice symbol
            double lineStartX, lineStartY;
            if (hasSequenceOrChoice) {
                // Start from the CENTER of the sequence/choice symbol
                lineStartX = finalSymbolCenterX;
                lineStartY = finalSymbolCenterY; // Center of the Sequence/Choice symbol
            } else {
                // Start from root element
                lineStartX = rootPathEndX;
                lineStartY = rootPathCenterY;
            }

            // Create L-shaped path: horizontal -> vertical -> horizontal
            double horizontalSegmentLength = 30; // Distance to go right before turning
            double turnX = lineStartX + horizontalSegmentLength;
            
            Element path = document.createElementNS(svgNS, "path");
            String pathData = String.format(Locale.ROOT, "M %.1f %.1f H %.1f V %.1f H %.1f",
                    lineStartX, lineStartY,              // Start at symbol right edge
                    turnX,                               // Go right 30px
                    childElementCenterY,                 // Go up/down to child center
                    finalRightStartX);                   // Go right to child left edge

            path.setAttribute("d", pathData);
            path.setAttribute("class", "connection-line");
            path.setAttribute("style", childElement.isMandatory() ? CONNECTION_LINE : CONNECTION_LINE_OPTIONAL);
            path.setAttribute("fill", "none");
            svgRoot.appendChild(path);

            // Modern cardinality
            Node childNode = childElement.getCurrentNode();
            String cardinality = formatCardinality(
                    getAttributeValue(childNode, "minOccurs", "1"),
                    getAttributeValue(childNode, "maxOccurs", "1")
            );

            if (!cardinality.isEmpty()) {
                int cardinalityFontSize = font.getSize() - 4;
                var cardinalityBounds = font.getStringBounds(cardinality, frc);

                // Position cardinality closer to the child element for better association
                double cardinalityX = turnX + (finalRightStartX - turnX) * 0.75 - (cardinalityBounds.getWidth() / 2);
                double cardinalityY = childElementCenterY - 15; // Above the horizontal line to child

                // Background rect for cardinality
                Element cardinalityBg = document.createElementNS(svgNS, "rect");
                cardinalityBg.setAttribute("x", String.valueOf(cardinalityX - 4));
                cardinalityBg.setAttribute("y", String.valueOf(cardinalityY - cardinalityBounds.getHeight()));
                cardinalityBg.setAttribute("width", String.valueOf(cardinalityBounds.getWidth() + 8));
                cardinalityBg.setAttribute("height", String.valueOf(cardinalityBounds.getHeight() + 4));
                cardinalityBg.setAttribute("fill", COLOR_BOX_FILL_ELEMENT);
                cardinalityBg.setAttribute("stroke", COLOR_STROKE_SEPARATOR);
                cardinalityBg.setAttribute("stroke-width", "1");
                cardinalityBg.setAttribute("rx", "3");
                svgRoot.appendChild(cardinalityBg);

                Element cardinalityTextNode = createSvgTextElement(document, cardinality,
                        String.valueOf(cardinalityX), String.valueOf(cardinalityY - 2),
                        COLOR_TEXT_SECONDARY, cardinalityFontSize);
                cardinalityTextNode.setAttribute("class", "cardinality-text");
                cardinalityTextNode.setAttribute("font-weight", "600");
                svgRoot.appendChild(cardinalityTextNode);
            }

            actualHeight += boxPadding * 2 + totalContentHeight + margin * 2;
        }

        // Calculate total height of root element including documentation
        double rootElementTotalHeight = rootStartY + (boxPadding * 2 + rootElementHeight) + docHeightTotal;

        // Finalize SVG size with symmetric layout - considers both root and child elements
        var imageHeight = Math.max(rootElementTotalHeight, actualHeight);
        svgRoot.setAttribute("height", String.valueOf(imageHeight + margin * 2));
        svgRoot.setAttribute("width", String.valueOf(finalRightStartX + boxPadding * 2 + maxChildWidth + margin * 3));
        svgRoot.setAttribute("style", "background-color: " + COLOR_BG);

        return document;
    }

    /**
     * Calculates the height of the documentation for layout planning
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
     * Calculates the total height of all child elements for centering
     */
    private double calculateTotalChildElementsHeight(List<XsdExtendedElement> childElements) {
        if (childElements.isEmpty()) {
            return 0;
        }

        double totalHeight = margin * 2; // Starting margin

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
     * Calculates the maximum height of child elements for symmetric layout
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
     * Calculates the maximum width of child elements for symmetric layout
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
     * Draws modern sequence/choice symbols
     */
    private double drawModernSequenceChoiceSymbol(Document document, double rootPathEndX, double rootPathCenterY,
                                                  double gapBetweenSides, boolean isSequence, boolean isChoice,
                                                  List<XsdExtendedElement> childElements, Element svgRoot) {

        final double symbolWidth = isSequence ? 50 : 30;
        final double symbolHeight = 30;
        final double symbolCenterX = rootPathEndX + (gapBetweenSides / 2);
        final double symbolCenterY = rootPathCenterY;

        // Connection line from root element to sequence/choice symbol
        Element pathToSymbol = document.createElementNS(svgNS, "line");
        pathToSymbol.setAttribute("x1", String.valueOf(rootPathEndX));
        pathToSymbol.setAttribute("y1", String.valueOf(rootPathCenterY));
        pathToSymbol.setAttribute("x2", String.valueOf(symbolCenterX - symbolWidth / 2));
        pathToSymbol.setAttribute("y2", String.valueOf(rootPathCenterY));
        pathToSymbol.setAttribute("class", "connection-line");
        pathToSymbol.setAttribute("style", CONNECTION_LINE);
        svgRoot.appendChild(pathToSymbol);

        // Group cardinality
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

        // Show cardinality above symbol with background
        if (!groupCardinality.isEmpty()) {
            int cardinalityFontSize = font.getSize() - 4;
            var cardinalityBounds = font.getStringBounds(groupCardinality, frc);
            double cardinalityX = symbolCenterX - (cardinalityBounds.getWidth() / 2);
            double cardinalityY = symbolCenterY - (symbolHeight / 2) - 8;

            // Background rect for group cardinality
            Element groupCardinalityBg = document.createElementNS(svgNS, "rect");
            groupCardinalityBg.setAttribute("x", String.valueOf(cardinalityX - 4));
            groupCardinalityBg.setAttribute("y", String.valueOf(cardinalityY - cardinalityBounds.getHeight()));
            groupCardinalityBg.setAttribute("width", String.valueOf(cardinalityBounds.getWidth() + 8));
            groupCardinalityBg.setAttribute("height", String.valueOf(cardinalityBounds.getHeight() + 4));
            groupCardinalityBg.setAttribute("fill", COLOR_BOX_FILL_STRUCTURAL);
            groupCardinalityBg.setAttribute("stroke", COLOR_STROKE_SEPARATOR);
            groupCardinalityBg.setAttribute("stroke-width", "1");
            groupCardinalityBg.setAttribute("rx", "3");
            svgRoot.appendChild(groupCardinalityBg);
            
            Element cardinalityTextNode = createSvgTextElement(document, groupCardinality,
                    String.valueOf(cardinalityX), String.valueOf(cardinalityY - 2),
                    COLOR_TEXT_SECONDARY, cardinalityFontSize);
            cardinalityTextNode.setAttribute("class", "cardinality-text");
            cardinalityTextNode.setAttribute("font-weight", "600");
            svgRoot.appendChild(cardinalityTextNode);
        }

        if (isSequence) {
            // Modern sequence symbol
            Element seqGroup = document.createElementNS(svgNS, "g");
            seqGroup.setAttribute("id", "seq-group_" + System.currentTimeMillis());

            // Modern box with rounded corners
            Element seqRect = document.createElementNS(svgNS, "rect");
            seqRect.setAttribute("x", String.valueOf(symbolCenterX - symbolWidth / 2));
            seqRect.setAttribute("y", String.valueOf(symbolCenterY - symbolHeight / 2));
            seqRect.setAttribute("width", String.valueOf(symbolWidth));
            seqRect.setAttribute("height", String.valueOf(symbolHeight));
            seqRect.setAttribute("fill", COLOR_BOX_FILL_STRUCTURAL);

            // Add sequence icon
            Element seqIconUse = document.createElementNS(svgNS, "use");
            seqIconUse.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#sequence-icon");
            seqIconUse.setAttribute("x", String.valueOf(symbolCenterX - symbolWidth / 2 + 5));
            seqIconUse.setAttribute("y", String.valueOf(symbolCenterY - symbolHeight / 2 + 5));
            seqGroup.appendChild(seqIconUse);
            seqRect.setAttribute("stroke", COLOR_ACCENT);
            seqRect.setAttribute("stroke-width", "2");
            seqRect.setAttribute("rx", String.valueOf(borderRadius));
            seqRect.setAttribute("ry", String.valueOf(borderRadius));
            seqGroup.appendChild(seqRect);

            // Modern separator line
            Element line = document.createElementNS(svgNS, "line");
            line.setAttribute("x1", String.valueOf(symbolCenterX - symbolWidth / 2 + 8));
            line.setAttribute("y1", String.valueOf(symbolCenterY));
            line.setAttribute("x2", String.valueOf(symbolCenterX + symbolWidth / 2 - 8));
            line.setAttribute("y2", String.valueOf(symbolCenterY));
            line.setAttribute("stroke", COLOR_TEXT_SECONDARY);
            line.setAttribute("stroke-width", "1.5");
            seqGroup.appendChild(line);

            // Modern dots
            double dotSpacing = 10;
            seqGroup.appendChild(createModernDot(document, symbolCenterX - dotSpacing, symbolCenterY));
            seqGroup.appendChild(createModernDot(document, symbolCenterX, symbolCenterY));
            seqGroup.appendChild(createModernDot(document, symbolCenterX + dotSpacing, symbolCenterY));

            svgRoot.appendChild(seqGroup);

        } else { // isChoice
            // Modern choice symbol (diamond with icon)
            Element choiceDiamond = document.createElementNS(svgNS, "polygon");
            String points = (symbolCenterX) + "," + (symbolCenterY - symbolHeight / 2) + " " +
                    (symbolCenterX + symbolWidth / 2) + "," + (symbolCenterY) + " " +
                    (symbolCenterX) + "," + (symbolCenterY + symbolHeight / 2) + " " +
                    (symbolCenterX - symbolWidth / 2) + "," + (symbolCenterY);
            choiceDiamond.setAttribute("points", points);
            choiceDiamond.setAttribute("fill", COLOR_BOX_FILL_STRUCTURAL);
            choiceDiamond.setAttribute("stroke", COLOR_ACCENT);
            choiceDiamond.setAttribute("stroke-width", "2");
            svgRoot.appendChild(choiceDiamond);

            // Add choice icon
            Element choiceIconUse = document.createElementNS(svgNS, "use");
            choiceIconUse.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#choice-icon");
            choiceIconUse.setAttribute("x", String.valueOf(symbolCenterX - 8));
            choiceIconUse.setAttribute("y", String.valueOf(symbolCenterY - 8));
            svgRoot.appendChild(choiceIconUse);
        }

        // Return the right edge X position and center Y position of the symbol
        return symbolCenterX + symbolWidth / 2;
    }

    /**
     * Creates a modern SVG rectangle with rounded corners
     */
    private Element createModernSvgRect(Document document, String id, double contentHeight, double contentWidth, String x, String y) {
        Element rect = document.createElementNS(svgNS, "rect");
        rect.setAttribute("fill", COLOR_BOX_FILL_ELEMENT);
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
     * Creates a modern dot for sequence symbols
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
     * Uses XMLSpy-style formatting with infinity symbol and smart defaults.
     *
     * @param minOccurs The minOccurs value from the XSD.
     * @param maxOccurs The maxOccurs value from the XSD.
     * @return A formatted string representing the cardinality.
     */
    private String formatCardinality(String minOccurs, String maxOccurs) {
        String min = (minOccurs == null) ? "1" : minOccurs;
        String max = (maxOccurs == null) ? "1" : maxOccurs;
        if ("unbounded".equalsIgnoreCase(max)) {
            max = "∞";
        }

        // Special formatting for common cases (matching XsdDiagramView)
        if ("1".equals(min) && "1".equals(max)) {
            return ""; // Don't show [1..1] as it's the default
        } else if ("0".equals(min) && "1".equals(max)) {
            return "0..1";
        } else if ("1".equals(min) && "∞".equals(max)) {
            return "1..∞";
        } else if ("0".equals(min) && "∞".equals(max)) {
            return "0..∞";
        } else {
            return String.format("%s..%s", min, max);
        }
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
        rect.setAttribute("fill", COLOR_BOX_FILL_ELEMENT);
        rect.setAttribute("id", id);
        rect.setAttribute("height", String.valueOf(margin + contentHeight + margin));
        rect.setAttribute("width", String.valueOf(margin + contentWidth + margin));
        rect.setAttribute("x", x);
        rect.setAttribute("y", y);
        rect.setAttribute("rx", "4"); // Slightly rounded corners
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
        // Modern font for documentation
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
                        rect.setAttribute("style", ELEMENT_OPTIONAL_FORMAT);
                    } else {
                        rect.setAttribute("style", ELEMENT_MANDATORY_FORMAT);
                    }
                } else {
                    // Fallback: check if the current style indicates optional
                    String currentStyle = rect.getAttribute("style");
                    if (currentStyle != null && currentStyle.contains("stroke-dasharray")) {
                        rect.setAttribute("style", ELEMENT_OPTIONAL_FORMAT);
                    } else {
                        rect.setAttribute("style", ELEMENT_MANDATORY_FORMAT);
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
        // try-with-resources ensures that the Writer is always closed.
        try (StringWriter writer = new StringWriter()) {
            // Get the reusable Transformer instance for the current thread.
            Transformer trans = transformerThreadLocal.get();

            // Adjust the one dynamic property for this specific call.
            // This is crucial since the Transformer instance is reused.
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
            // StringWriter should not throw IOException, but it's good practice to handle it.
            logger.error("IO error during node serialization", ex);
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Adds gradient definitions for XMLSpy-like appearance
     */
    private void addGradientDefinitions(Document document, Element defs) {
        // Element gradient (light blue)
        Element elementGradient = document.createElementNS(svgNS, "linearGradient");
        elementGradient.setAttribute("id", "elementGradient");
        elementGradient.setAttribute("x1", "0%");
        elementGradient.setAttribute("y1", "0%");
        elementGradient.setAttribute("x2", "0%");
        elementGradient.setAttribute("y2", "100%");

        Element elementStop1 = document.createElementNS(svgNS, "stop");
        elementStop1.setAttribute("offset", "0%");
        elementStop1.setAttribute("stop-color", "#ffffff");
        elementGradient.appendChild(elementStop1);

        Element elementStop2 = document.createElementNS(svgNS, "stop");
        elementStop2.setAttribute("offset", "100%");
        elementStop2.setAttribute("stop-color", COLOR_BOX_FILL_ELEMENT);
        elementGradient.appendChild(elementStop2);
        defs.appendChild(elementGradient);

        // Attribute gradient (light yellow)
        Element attributeGradient = document.createElementNS(svgNS, "linearGradient");
        attributeGradient.setAttribute("id", "attributeGradient");
        attributeGradient.setAttribute("x1", "0%");
        attributeGradient.setAttribute("y1", "0%");
        attributeGradient.setAttribute("x2", "0%");
        attributeGradient.setAttribute("y2", "100%");

        Element attrStop1 = document.createElementNS(svgNS, "stop");
        attrStop1.setAttribute("offset", "0%");
        attrStop1.setAttribute("stop-color", "#ffffff");
        attributeGradient.appendChild(attrStop1);

        Element attrStop2 = document.createElementNS(svgNS, "stop");
        attrStop2.setAttribute("offset", "100%");
        attrStop2.setAttribute("stop-color", COLOR_BOX_FILL_ATTRIBUTE);
        attributeGradient.appendChild(attrStop2);
        defs.appendChild(attributeGradient);

        // Structural gradient (light gray)
        Element structuralGradient = document.createElementNS(svgNS, "linearGradient");
        structuralGradient.setAttribute("id", "structuralGradient");
        structuralGradient.setAttribute("x1", "0%");
        structuralGradient.setAttribute("y1", "0%");
        structuralGradient.setAttribute("x2", "0%");
        structuralGradient.setAttribute("y2", "100%");

        Element structStop1 = document.createElementNS(svgNS, "stop");
        structStop1.setAttribute("offset", "0%");
        structStop1.setAttribute("stop-color", "#ffffff");
        structuralGradient.appendChild(structStop1);

        Element structStop2 = document.createElementNS(svgNS, "stop");
        structStop2.setAttribute("offset", "100%");
        structStop2.setAttribute("stop-color", COLOR_BOX_FILL_STRUCTURAL);
        structuralGradient.appendChild(structStop2);
        defs.appendChild(structuralGradient);
    }

}
