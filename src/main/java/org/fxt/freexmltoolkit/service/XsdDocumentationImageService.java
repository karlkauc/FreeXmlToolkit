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

    // Colors matching XsdDiagramView styles
    private static final String COLOR_BG = "#f0f8ff";                    // Background matching XsdDiagramView
    private static final String COLOR_BOX_FILL_ELEMENT = "#f0f8ff";      // Element background
    private static final String COLOR_BOX_FILL_ATTRIBUTE = "#f9f5e7";    // Attribute background
    private static final String COLOR_BOX_FILL_SEQUENCE = "#e9ecef";     // Sequence background
    private static final String COLOR_BOX_FILL_CHOICE = "#fff3cd";       // Choice background
    private static final String COLOR_BOX_FILL_ANY = "#dee2e6";          // Any background
    private static final String COLOR_STROKE_ELEMENT = "#4a90e2";        // Element border
    private static final String COLOR_STROKE_ATTRIBUTE = "#d4a147";      // Attribute border
    private static final String COLOR_STROKE_SEQUENCE = "#6c757d";       // Sequence border
    private static final String COLOR_STROKE_CHOICE = "#ff8c00";         // Choice border
    private static final String COLOR_STROKE_ANY = "#adb5bd";            // Any border
    private static final String COLOR_STROKE_OPTIONAL = "#94a3b8";       // Optional elements
    private static final String COLOR_STROKE_SEPARATOR = "#dee2e6";      // Separator lines
    private static final String COLOR_TEXT_PRIMARY = "#2c5aa0";          // Element text
    private static final String COLOR_TEXT_SECONDARY = "#6c757d";        // Secondary text
    private static final String COLOR_TEXT_ATTRIBUTE = "#8b6914";        // Attribute text
    private static final String COLOR_TEXT_SEQUENCE = "#495057";         // Sequence text
    private static final String COLOR_TEXT_CHOICE = "#b45309";           // Choice text
    private static final String COLOR_SHADOW = "rgba(0, 0, 0, 0.2)";     // Drop shadow

    // Type-specific colors from XsdDiagramView
    private static final String COLOR_STRING_TYPE = "#28a745";
    private static final String COLOR_NUMERIC_TYPE = "#007bff";
    private static final String COLOR_DATE_TYPE = "#fd7e14";
    private static final String COLOR_BOOLEAN_TYPE = "#6f42c1";
    private static final String COLOR_BINARY_TYPE = "#6c757d";
    private static final String COLOR_URI_TYPE = "#17a2b8";
    private static final String COLOR_QNAME_TYPE = "#e83e8c";
    private static final String COLOR_LANGUAGE_TYPE = "#20c997";
    private static final String COLOR_COMPLEX_TYPE = "#dc3545";
    private static final String COLOR_DEFAULT_TYPE = "#4a90e2";

    // Legacy compatibility
    private static final String COLOR_ICON = COLOR_STROKE_ELEMENT;
    private static final String COLOR_ACCENT = COLOR_STROKE_CHOICE;
    private static final String COLOR_BOX_FILL_STRUCTURAL = COLOR_BOX_FILL_SEQUENCE;

    // Style strings matching XsdDiagramView
    final static String ELEMENT_MANDATORY_FORMAT = "stroke: " + COLOR_STROKE_ELEMENT + "; stroke-width: 2; fill: url(#elementGradient); filter: url(#dropShadow);";
    final static String ELEMENT_OPTIONAL_FORMAT = "stroke: " + COLOR_STROKE_ELEMENT + "; stroke-width: 2; stroke-dasharray: 5, 2; fill: url(#elementGradient); filter: url(#dropShadow);";
    final static String ELEMENT_REPEATABLE_FORMAT = "stroke: " + COLOR_STROKE_ELEMENT + "; stroke-width: 2; fill: url(#elementGradient); filter: url(#dropShadow);";
    final static String ATTRIBUTE_MANDATORY_FORMAT = "stroke: " + COLOR_STROKE_ATTRIBUTE + "; stroke-width: 1.5; fill: url(#attributeGradient); filter: url(#dropShadow);";
    final static String ATTRIBUTE_OPTIONAL_FORMAT = "stroke: " + COLOR_STROKE_ATTRIBUTE + "; stroke-width: 1; stroke-dasharray: 5, 2; fill: url(#attributeGradient); filter: url(#dropShadow);";
    final static String SEQUENCE_FORMAT = "stroke: " + COLOR_STROKE_SEQUENCE + "; stroke-width: 2; fill: url(#sequenceGradient); filter: url(#dropShadow);";
    final static String CHOICE_FORMAT = "stroke: " + COLOR_STROKE_CHOICE + "; stroke-width: 2; stroke-dasharray: 5, 5; fill: url(#choiceGradient); filter: url(#dropShadow);";
    final static String ANY_FORMAT = "stroke: " + COLOR_STROKE_ANY + "; stroke-width: 1.5; stroke-dasharray: 3, 3; fill: url(#anyGradient); filter: url(#dropShadow);";
    final static String CONNECTION_LINE = "stroke: " + COLOR_STROKE_ELEMENT + "; stroke-width: 2; fill: none;";
    final static String CONNECTION_LINE_OPTIONAL = "stroke: " + COLOR_STROKE_OPTIONAL + "; stroke-width: 1; stroke-dasharray: 5, 5; fill: none;";
    final static String CONNECTION_LINE_SEQUENCE = "stroke: " + COLOR_STROKE_SEQUENCE + "; stroke-width: 2; fill: none;";
    final static String CONNECTION_LINE_CHOICE = "stroke: " + COLOR_STROKE_CHOICE + "; stroke-width: 2; stroke-dasharray: 5, 5; fill: none;";

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

        // Type-specific icon definitions from XsdDiagramView
        addTypeSpecificIcons(document, defs);

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
            // Apply cardinality-based styling
            rightBox.setAttribute("style", determineNodeLabelStyle(childElement));
            rightBox.setAttribute("class", "hoverable-rect");

            // Optional overlay dashed border for optional elements (like OPTIONAL_NODE_LABEL_STYLE)
            org.w3c.dom.Node childDomNode = childElement.getCurrentNode();
            String minOccurs = getAttributeValue(childDomNode, "minOccurs", "1");
            boolean isOptional = "0".equals(minOccurs);
            String maxOccurs = getAttributeValue(childDomNode, "maxOccurs", "1");
            boolean isRepeatable = "unbounded".equals(maxOccurs) || (maxOccurs != null && !"1".equals(maxOccurs) && !"0".equals(maxOccurs));

            if (isRepeatable) {
                // Inner secondary border (double border effect, light blue)
                Element innerBorder = document.createElementNS(svgNS, "rect");
                innerBorder.setAttribute("x", String.valueOf(finalRightStartX + 2));
                innerBorder.setAttribute("y", String.valueOf(elementY + 2));
                innerBorder.setAttribute("width", String.valueOf(boxPadding * 2 + maxChildWidth - 4));
                innerBorder.setAttribute("height", String.valueOf(boxPadding * 2 + totalContentHeight - 4));
                innerBorder.setAttribute("rx", String.valueOf(borderRadius));
                innerBorder.setAttribute("ry", String.valueOf(borderRadius));
                innerBorder.setAttribute("fill", "none");
                innerBorder.setAttribute("stroke", "#87ceeb");
                innerBorder.setAttribute("stroke-width", "1");
                svgRoot.appendChild(innerBorder);
            }

            if (isOptional) {
                // Dashed overlay border
                Element dashedOverlay = document.createElementNS(svgNS, "rect");
                dashedOverlay.setAttribute("x", String.valueOf(finalRightStartX));
                dashedOverlay.setAttribute("y", String.valueOf(elementY));
                dashedOverlay.setAttribute("width", String.valueOf(boxPadding * 2 + maxChildWidth));
                dashedOverlay.setAttribute("height", String.valueOf(boxPadding * 2 + totalContentHeight));
                dashedOverlay.setAttribute("rx", String.valueOf(borderRadius));
                dashedOverlay.setAttribute("ry", String.valueOf(borderRadius));
                dashedOverlay.setAttribute("fill", "none");
                dashedOverlay.setAttribute("stroke", COLOR_STROKE_ELEMENT);
                dashedOverlay.setAttribute("stroke-width", "2");
                dashedOverlay.setAttribute("stroke-dasharray", "5,2");
                svgRoot.appendChild(dashedOverlay);
            }

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

                // Type-specific icon before the type name
                Element typeIcon = createTypeSpecificIcon(document, elementType);
                if (typeIcon != null) {
                    typeIcon.setAttribute("x", String.valueOf(finalRightStartX + boxPadding));
                    double typeY = lineY + typeBounds.getBounds2D().getHeight() + 4;
                    // Align icon visually with the type text baseline (assume ~16px icon height)
                    double iconBaselineAlignedY = typeY - 10; // moved 2px further down
                    typeIcon.setAttribute("y", String.valueOf(iconBaselineAlignedY));
                    textGroup.appendChild(typeIcon);
                }

                double typeY = lineY + typeBounds.getBounds2D().getHeight() + 4;
                double typeTextX = finalRightStartX + boxPadding;
                if (typeIcon != null) {
                    typeTextX += 16; // Make space for icon
                }
                Element typeTextNode = createSvgTextElement(document, elementType,
                        String.valueOf(typeTextX), String.valueOf(typeY),
                        getTypeSpecificColor(elementType), font.getSize() - 1);
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
                // Start from the RIGHT EDGE of the sequence/choice symbol
                lineStartX = finalSymbolCenterX + (isSequence ? 25 : 15); // symbolWidth/2 (50/2 or 30/2)
                lineStartY = finalSymbolCenterY;
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
            // Style connectors: same color as root→symbol; dashed only if child is optional
            boolean childOptional;
            if (elementName != null && elementName.startsWith("@")) {
                childOptional = "optional".equals(getAttributeValue(childDomNode, "use", "optional"));
            } else {
                childOptional = "0".equals(getAttributeValue(childDomNode, "minOccurs", "1"));
            }
            if (hasSequenceOrChoice) {
                String groupStroke = isSequence ? COLOR_STROKE_SEQUENCE : COLOR_STROKE_CHOICE;
                String childStyle = "stroke: " + groupStroke + "; stroke-width: 2; fill: none;";
                if (childOptional) {
                    childStyle += " stroke-dasharray: 5,5;";
                }
                path.setAttribute("style", childStyle);
            } else {
                path.setAttribute("style", childElement.isMandatory() ? CONNECTION_LINE : CONNECTION_LINE_OPTIONAL);
            }
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

                // Position cardinality closer to the child element without overlapping its border
                double textWidth = cardinalityBounds.getWidth();
                double halfWidth = textWidth / 2.0;
                double desiredCenterX = turnX + (finalRightStartX - turnX) * 0.65; // slightly more to the left than before
                double maxCenterX = finalRightStartX - 10 - halfWidth; // keep at least 10px from child border
                double minCenterX = turnX + 10 + halfWidth;            // keep at least 10px from turn point
                double centerX = Math.max(minCenterX, Math.min(desiredCenterX, maxCenterX));
                double cardinalityX = centerX - halfWidth;
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

        // Connection line from root element to sequence/choice symbol (solid, group-colored)
        Element pathToSymbol = document.createElementNS(svgNS, "line");
        pathToSymbol.setAttribute("x1", String.valueOf(rootPathEndX));
        pathToSymbol.setAttribute("y1", String.valueOf(rootPathCenterY));
        pathToSymbol.setAttribute("x2", String.valueOf(symbolCenterX - symbolWidth / 2));
        pathToSymbol.setAttribute("y2", String.valueOf(rootPathCenterY));
        pathToSymbol.setAttribute("class", "connection-line");
        pathToSymbol.setAttribute("style", "stroke: " + COLOR_STROKE_SEQUENCE + "; stroke-width: 2; fill: none;");
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
            // Sequence as labeled rounded rectangle with list icon, like XsdDiagramView
            Element seqGroup = document.createElementNS(svgNS, "g");

            Element seqRect = document.createElementNS(svgNS, "rect");
            seqRect.setAttribute("x", String.valueOf(symbolCenterX - symbolWidth / 2));
            seqRect.setAttribute("y", String.valueOf(symbolCenterY - symbolHeight / 2));
            seqRect.setAttribute("width", String.valueOf(symbolWidth));
            seqRect.setAttribute("height", String.valueOf(symbolHeight));
            seqRect.setAttribute("fill", "url(#sequenceGradient)");
            seqRect.setAttribute("stroke", COLOR_STROKE_SEQUENCE);
            seqRect.setAttribute("stroke-width", "2");
            seqRect.setAttribute("rx", "4");
            seqRect.setAttribute("ry", "4");
            seqGroup.appendChild(seqRect);

            Element seqIconUse = document.createElementNS(svgNS, "use");
            seqIconUse.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#sequence-icon");
            // Center the sequence icon (~16x16) within the rectangle
            seqIconUse.setAttribute("x", String.valueOf(symbolCenterX - 8));
            seqIconUse.setAttribute("y", String.valueOf(symbolCenterY - 8));
            seqGroup.appendChild(seqIconUse);

            svgRoot.appendChild(seqGroup);
        } else { // CHOICE
            // Choice as labeled rounded rectangle with dashed border, like XsdDiagramView
            Element choiceGroup = document.createElementNS(svgNS, "g");

            Element choiceRect = document.createElementNS(svgNS, "rect");
            choiceRect.setAttribute("x", String.valueOf(symbolCenterX - symbolWidth / 2));
            choiceRect.setAttribute("y", String.valueOf(symbolCenterY - symbolHeight / 2));
            choiceRect.setAttribute("width", String.valueOf(symbolWidth));
            choiceRect.setAttribute("height", String.valueOf(symbolHeight));
            choiceRect.setAttribute("fill", "url(#choiceGradient)");
            choiceRect.setAttribute("stroke", COLOR_STROKE_CHOICE);
            choiceRect.setAttribute("stroke-width", "2");
            choiceRect.setAttribute("stroke-dasharray", "5,5");
            choiceRect.setAttribute("rx", "4");
            choiceRect.setAttribute("ry", "4");
            choiceGroup.appendChild(choiceRect);

            Element choiceIconUse = document.createElementNS(svgNS, "use");
            choiceIconUse.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#choice-icon");
            choiceIconUse.setAttribute("x", String.valueOf(symbolCenterX - symbolWidth / 2 + 6));
            choiceIconUse.setAttribute("y", String.valueOf(symbolCenterY - 8));
            choiceGroup.appendChild(choiceIconUse);

            svgRoot.appendChild(choiceGroup);
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

        // Sequence gradient (light gray)
        Element sequenceGradient = document.createElementNS(svgNS, "linearGradient");
        sequenceGradient.setAttribute("id", "sequenceGradient");
        sequenceGradient.setAttribute("x1", "0%");
        sequenceGradient.setAttribute("y1", "0%");
        sequenceGradient.setAttribute("x2", "0%");
        sequenceGradient.setAttribute("y2", "100%");

        Element seqStop1 = document.createElementNS(svgNS, "stop");
        seqStop1.setAttribute("offset", "0%");
        seqStop1.setAttribute("stop-color", "#ffffff");
        sequenceGradient.appendChild(seqStop1);

        Element seqStop2 = document.createElementNS(svgNS, "stop");
        seqStop2.setAttribute("offset", "100%");
        seqStop2.setAttribute("stop-color", COLOR_BOX_FILL_SEQUENCE);
        sequenceGradient.appendChild(seqStop2);
        defs.appendChild(sequenceGradient);

        // Choice gradient (light orange)
        Element choiceGradient = document.createElementNS(svgNS, "linearGradient");
        choiceGradient.setAttribute("id", "choiceGradient");
        choiceGradient.setAttribute("x1", "0%");
        choiceGradient.setAttribute("y1", "0%");
        choiceGradient.setAttribute("x2", "0%");
        choiceGradient.setAttribute("y2", "100%");

        Element choiceStop1 = document.createElementNS(svgNS, "stop");
        choiceStop1.setAttribute("offset", "0%");
        choiceStop1.setAttribute("stop-color", "#ffffff");
        choiceGradient.appendChild(choiceStop1);

        Element choiceStop2 = document.createElementNS(svgNS, "stop");
        choiceStop2.setAttribute("offset", "100%");
        choiceStop2.setAttribute("stop-color", COLOR_BOX_FILL_CHOICE);
        choiceGradient.appendChild(choiceStop2);
        defs.appendChild(choiceGradient);

        // Any gradient (light gray)
        Element anyGradient = document.createElementNS(svgNS, "linearGradient");
        anyGradient.setAttribute("id", "anyGradient");
        anyGradient.setAttribute("x1", "0%");
        anyGradient.setAttribute("y1", "0%");
        anyGradient.setAttribute("x2", "0%");
        anyGradient.setAttribute("y2", "100%");

        Element anyStop1 = document.createElementNS(svgNS, "stop");
        anyStop1.setAttribute("offset", "0%");
        anyStop1.setAttribute("stop-color", "#ffffff");
        anyGradient.appendChild(anyStop1);

        Element anyStop2 = document.createElementNS(svgNS, "stop");
        anyStop2.setAttribute("offset", "100%");
        anyStop2.setAttribute("stop-color", COLOR_BOX_FILL_ANY);
        anyGradient.appendChild(anyStop2);
        defs.appendChild(anyGradient);

        // Structural gradient (legacy compatibility)
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

    /**
     * Adds type-specific icon definitions matching XsdDiagramView
     */
    private void addTypeSpecificIcons(Document document, Element defs) {
        // String type icon (text)
        Element stringIcon = document.createElementNS(svgNS, "g");
        stringIcon.setAttribute("id", "string-icon");
        Element stringPath = document.createElementNS(svgNS, "path");
        stringPath.setAttribute("d", "M2.5 4v1.5c0 .83.67 1.5 1.5 1.5h1v1h-1c-.83 0-1.5.67-1.5 1.5V11c0 .55-.45 1-1 1H0v-1h.5c.28 0 .5-.22.5-.5V9s0-2.5 0-2.5c0-.83.67-1.5 1.5-1.5h1v-1h-1C1.67 4 1 3.33 1 2.5V1c0-.55.45-1 1-1H1v1h-.5c-.28 0-.5.22-.5.5V3s0 2.5 0 2.5c0 .83-.67 1.5-1.5 1.5h-1z");
        stringPath.setAttribute("fill", COLOR_STRING_TYPE);
        stringIcon.appendChild(stringPath);
        defs.appendChild(stringIcon);

        // Numeric type icon (hash)
        Element numericIcon = document.createElementNS(svgNS, "g");
        numericIcon.setAttribute("id", "numeric-icon");
        Element numPath = document.createElementNS(svgNS, "path");
        numPath.setAttribute("d", "M1.5 6l.5 2H1l-.5-2H0V5h.5L0 3h1l.5 2h1L2 3h1l.5 2H4v1h-.5L4 8H3l-.5-2h-1z");
        numPath.setAttribute("fill", COLOR_NUMERIC_TYPE);
        numericIcon.appendChild(numPath);
        defs.appendChild(numericIcon);

        // Date type icon (calendar)
        Element dateIcon = document.createElementNS(svgNS, "g");
        dateIcon.setAttribute("id", "date-icon");
        Element datePath = document.createElementNS(svgNS, "path");
        datePath.setAttribute("d", "M3.5 0c-.28 0-.5.22-.5.5v.5h-1v-.5c0-.28-.22-.5-.5-.5s-.5.22-.5.5v.5H0v8h8V1h-1v-.5c0-.28-.22-.5-.5-.5s-.5.22-.5.5v.5h-2v-.5c0-.28-.22-.5-.5-.5zM1 2h6v2H1V2zm0 3h6v3H1V5z");
        datePath.setAttribute("fill", COLOR_DATE_TYPE);
        dateIcon.appendChild(datePath);
        defs.appendChild(dateIcon);

        // Boolean type icon (check)
        Element booleanIcon = document.createElementNS(svgNS, "g");
        booleanIcon.setAttribute("id", "boolean-icon");
        Element boolPath = document.createElementNS(svgNS, "path");
        boolPath.setAttribute("d", "M7 1L3.5 4.5 1 2l-1 1 3.5 3.5L8 2z");
        boolPath.setAttribute("fill", COLOR_BOOLEAN_TYPE);
        booleanIcon.appendChild(boolPath);
        defs.appendChild(booleanIcon);

        // Binary type icon (file)
        Element binaryIcon = document.createElementNS(svgNS, "g");
        binaryIcon.setAttribute("id", "binary-icon");
        Element binPath = document.createElementNS(svgNS, "path");
        binPath.setAttribute("d", "M1 1v6h6V3L5 1H1zm4 0v2h2L5 1zm-3 3h2v1H2V4zm0 2h4v1H2V6z");
        binPath.setAttribute("fill", COLOR_BINARY_TYPE);
        binaryIcon.appendChild(binPath);
        defs.appendChild(binaryIcon);

        // URI type icon (link)
        Element uriIcon = document.createElementNS(svgNS, "g");
        uriIcon.setAttribute("id", "uri-icon");
        Element uriPath = document.createElementNS(svgNS, "path");
        uriPath.setAttribute("d", "M2 3h1v1H2V3zm3 0h1v1H5V3zM1.5 1C.67 1 0 1.67 0 2.5S.67 4 1.5 4h.25L2 3.75v-.5l-.25-.25H1.5c-.28 0-.5-.22-.5-.5s.22-.5.5-.5h1c.28 0 .5.22.5.5v.25l.25.25h.5l.25-.25V2.5c0-.83-.67-1.5-1.5-1.5h-1z");
        uriPath.setAttribute("fill", COLOR_URI_TYPE);
        uriIcon.appendChild(uriPath);
        defs.appendChild(uriIcon);

        // QName type icon (tag)
        Element qnameIcon = document.createElementNS(svgNS, "g");
        qnameIcon.setAttribute("id", "qname-icon");
        Element qnamePath = document.createElementNS(svgNS, "path");
        qnamePath.setAttribute("d", "M0 2v4c0 .55.45 1 1 1h2.5L7 3.5v-1L3.5 0H1C.45 0 0 .45 0 2zm5.5 1.5L3 5H1V2h2.5L5.5 3.5z");
        qnamePath.setAttribute("fill", COLOR_QNAME_TYPE);
        qnameIcon.appendChild(qnamePath);
        defs.appendChild(qnameIcon);

        // Language type icon (globe)
        Element languageIcon = document.createElementNS(svgNS, "g");
        languageIcon.setAttribute("id", "language-icon");
        Element langPath = document.createElementNS(svgNS, "path");
        langPath.setAttribute("d", "M4 0C1.79 0 0 1.79 0 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm2 7c-.02-.02-.05-.03-.08-.03H5c-.28 0-.5-.22-.5-.5 0-.25.19-.46.44-.5.02 0 .04-.01.06-.01h1c.83 0 1.5-.67 1.5-1.5 0-.78-.6-1.42-1.36-1.49C6.09 2.95 6.05 2.93 6 2.93c-.28 0-.5-.22-.5-.5s.22-.5.5-.5c.05 0 .09.02.14.03C6.36 1.94 6.6 2 6.5 2c0 0 .5.22.5.5s-.22.5-.5.5c-.28 0-.5-.22-.5-.5 0-.02.01-.04.01-.06h-1c-.83 0-1.5.67-1.5 1.5 0 .78.6 1.42 1.36 1.49C4.91 5.05 4.95 5.07 5 5.07c.28 0 .5.22.5.5s-.22.5-.5.5c-.05 0-.09-.02-.14-.03C4.64 6.06 4.4 6 4.5 6z");
        langPath.setAttribute("fill", COLOR_LANGUAGE_TYPE);
        languageIcon.appendChild(langPath);
        defs.appendChild(languageIcon);

        // Complex type icon (hierarchy/diagram style)
        Element complexIcon = document.createElementNS(svgNS, "g");
        complexIcon.setAttribute("id", "complex-icon");
        // Reduce overall icon size by 50%
        complexIcon.setAttribute("transform", "scale(0.5)");
        // Top box
        Element cTop = document.createElementNS(svgNS, "rect");
        cTop.setAttribute("x", "5");
        cTop.setAttribute("y", "0");
        cTop.setAttribute("width", "6");
        cTop.setAttribute("height", "4");
        cTop.setAttribute("fill", "none");
        cTop.setAttribute("stroke", COLOR_COMPLEX_TYPE);
        cTop.setAttribute("stroke-width", "1.5");
        complexIcon.appendChild(cTop);
        // Left bottom box
        Element cLeft = document.createElementNS(svgNS, "rect");
        cLeft.setAttribute("x", "0");
        cLeft.setAttribute("y", "12");
        cLeft.setAttribute("width", "6");
        cLeft.setAttribute("height", "4");
        cLeft.setAttribute("fill", "none");
        cLeft.setAttribute("stroke", COLOR_COMPLEX_TYPE);
        cLeft.setAttribute("stroke-width", "1.5");
        complexIcon.appendChild(cLeft);
        // Right bottom box
        Element cRight = document.createElementNS(svgNS, "rect");
        cRight.setAttribute("x", "10");
        cRight.setAttribute("y", "12");
        cRight.setAttribute("width", "6");
        cRight.setAttribute("height", "4");
        cRight.setAttribute("fill", "none");
        cRight.setAttribute("stroke", COLOR_COMPLEX_TYPE);
        cRight.setAttribute("stroke-width", "1.5");
        complexIcon.appendChild(cRight);
        // Connectors
        Element vLine = document.createElementNS(svgNS, "line");
        vLine.setAttribute("x1", "8");
        vLine.setAttribute("y1", "4");
        vLine.setAttribute("x2", "8");
        vLine.setAttribute("y2", "10");
        vLine.setAttribute("stroke", COLOR_COMPLEX_TYPE);
        vLine.setAttribute("stroke-width", "1.5");
        complexIcon.appendChild(vLine);
        Element hLine = document.createElementNS(svgNS, "line");
        hLine.setAttribute("x1", "3");
        hLine.setAttribute("y1", "10");
        hLine.setAttribute("x2", "13");
        hLine.setAttribute("y2", "10");
        hLine.setAttribute("stroke", COLOR_COMPLEX_TYPE);
        hLine.setAttribute("stroke-width", "1.5");
        complexIcon.appendChild(hLine);
        Element lStem = document.createElementNS(svgNS, "line");
        lStem.setAttribute("x1", "3");
        lStem.setAttribute("y1", "10");
        lStem.setAttribute("x2", "3");
        lStem.setAttribute("y2", "12");
        lStem.setAttribute("stroke", COLOR_COMPLEX_TYPE);
        lStem.setAttribute("stroke-width", "1.5");
        complexIcon.appendChild(lStem);
        Element rStem = document.createElementNS(svgNS, "line");
        rStem.setAttribute("x1", "13");
        rStem.setAttribute("y1", "10");
        rStem.setAttribute("x2", "13");
        rStem.setAttribute("y2", "12");
        rStem.setAttribute("stroke", COLOR_COMPLEX_TYPE);
        rStem.setAttribute("stroke-width", "1.5");
        complexIcon.appendChild(rStem);
        defs.appendChild(complexIcon);

        // Default type icon (generic)
        Element defaultIcon = document.createElementNS(svgNS, "g");
        defaultIcon.setAttribute("id", "default-icon");
        Element defaultPath = document.createElementNS(svgNS, "path");
        defaultPath.setAttribute("d", "M4 0C1.79 0 0 1.79 0 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm0 6c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z");
        defaultPath.setAttribute("fill", COLOR_DEFAULT_TYPE);
        defaultIcon.appendChild(defaultPath);
        defs.appendChild(defaultIcon);

        // Sequence structural icon (list-ol)
        Element sequenceIcon = document.createElementNS(svgNS, "g");
        sequenceIcon.setAttribute("id", "sequence-icon");
        Element sequencePath = document.createElementNS(svgNS, "path");
        sequencePath.setAttribute("d", "M2,17H4V19H2V17M2,13H4V15H2V13M2,9H4V11H2V9M2,5H4V7H2V5M7,5H22V7H7V5M7,9H22V11H7V9M7,13H22V15H7V13M7,17H22V19H7V17Z");
        sequencePath.setAttribute("fill", COLOR_STROKE_SEQUENCE);
        sequenceIcon.appendChild(sequencePath);
        defs.appendChild(sequenceIcon);

        // Choice structural icon (option)
        Element choiceIcon = document.createElementNS(svgNS, "g");
        choiceIcon.setAttribute("id", "choice-icon");
        Element choicePath = document.createElementNS(svgNS, "path");
        choicePath.setAttribute("d", "M8,10V12H16V10H8M8,14V16H16V14H8M18,8C19.11,8 20,8.9 20,10V14C20,15.11 19.11,16 18,16H16V14H18V10H16V8H18M6,8V10H4V14H6V16H4C2.89,16 2,15.11 2,14V10C2,8.9 2.89,8 4,8H6Z");
        choicePath.setAttribute("fill", COLOR_STROKE_CHOICE);
        choiceIcon.appendChild(choicePath);
        defs.appendChild(choiceIcon);
    }

    /**
     * Creates a type-specific icon based on the element type
     */
    private Element createTypeSpecificIcon(Document document, String elementType) {
        if (elementType == null || elementType.isBlank()) {
            return null;
        }

        String iconId = determineIconType(elementType);
        if (iconId != null) {
            Element iconUse = document.createElementNS(svgNS, "use");
            iconUse.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#" + iconId);
            return iconUse;
        }
        return null;
    }

    /**
     * Determines the appropriate icon type based on element type
     */
    private String determineIconType(String elementType) {
        if (elementType == null || elementType.isBlank()) {
            return "default-icon";
        }

        String lowerType = elementType.toLowerCase();

        // String types
        if (lowerType.contains("string") || lowerType.contains("token") ||
                lowerType.contains("normalizedstring") || lowerType.contains("name") ||
                lowerType.contains("ncname") || lowerType.contains("id") || lowerType.contains("idref")) {
            return "string-icon";
        }

        // Numeric types
        if (lowerType.contains("int") || lowerType.contains("decimal") ||
                lowerType.contains("float") || lowerType.contains("double") ||
                lowerType.contains("long") || lowerType.contains("short") ||
                lowerType.contains("byte") || lowerType.contains("unsignedint") ||
                lowerType.contains("positiveinteger") || lowerType.contains("negativeinteger") ||
                lowerType.contains("nonnegativeinteger") || lowerType.contains("nonpositiveinteger")) {
            return "numeric-icon";
        }

        // Date/Time types
        if (lowerType.contains("date") || lowerType.contains("time") ||
                lowerType.contains("datetime") || lowerType.contains("duration") ||
                lowerType.contains("gyear") || lowerType.contains("gmonth") ||
                lowerType.contains("gday")) {
            return "date-icon";
        }

        // Boolean types
        if (lowerType.contains("boolean")) {
            return "boolean-icon";
        }

        // Binary types
        if (lowerType.contains("base64binary") || lowerType.contains("hexbinary")) {
            return "binary-icon";
        }

        // URI types
        if (lowerType.contains("anyuri")) {
            return "uri-icon";
        }

        // QName types
        if (lowerType.contains("qname")) {
            return "qname-icon";
        }

        // Language types
        if (lowerType.contains("language")) {
            return "language-icon";
        }

        // Complex types (user-defined)
        if (!lowerType.startsWith("xs:") && !lowerType.startsWith("xsd:")) {
            return "complex-icon";
        }

        return "default-icon";
    }

    /**
     * Gets the appropriate color for a specific type
     */
    private String getTypeSpecificColor(String elementType) {
        if (elementType == null || elementType.isBlank()) {
            return COLOR_DEFAULT_TYPE;
        }

        String lowerType = elementType.toLowerCase();

        // String types
        if (lowerType.contains("string") || lowerType.contains("token") ||
                lowerType.contains("normalizedstring") || lowerType.contains("name") ||
                lowerType.contains("ncname") || lowerType.contains("id") || lowerType.contains("idref")) {
            return COLOR_STRING_TYPE;
        }

        // Numeric types
        if (lowerType.contains("int") || lowerType.contains("decimal") ||
                lowerType.contains("float") || lowerType.contains("double") ||
                lowerType.contains("long") || lowerType.contains("short") ||
                lowerType.contains("byte") || lowerType.contains("unsignedint") ||
                lowerType.contains("positiveinteger") || lowerType.contains("negativeinteger") ||
                lowerType.contains("nonnegativeinteger") || lowerType.contains("nonpositiveinteger")) {
            return COLOR_NUMERIC_TYPE;
        }

        // Date/Time types
        if (lowerType.contains("date") || lowerType.contains("time") ||
                lowerType.contains("datetime") || lowerType.contains("duration") ||
                lowerType.contains("gyear") || lowerType.contains("gmonth") ||
                lowerType.contains("gday")) {
            return COLOR_DATE_TYPE;
        }

        // Boolean types
        if (lowerType.contains("boolean")) {
            return COLOR_BOOLEAN_TYPE;
        }

        // Binary types
        if (lowerType.contains("base64binary") || lowerType.contains("hexbinary")) {
            return COLOR_BINARY_TYPE;
        }

        // URI types
        if (lowerType.contains("anyuri")) {
            return COLOR_URI_TYPE;
        }

        // QName types
        if (lowerType.contains("qname")) {
            return COLOR_QNAME_TYPE;
        }

        // Language types
        if (lowerType.contains("language")) {
            return COLOR_LANGUAGE_TYPE;
        }

        // Complex types (user-defined)
        if (!lowerType.startsWith("xs:") && !lowerType.startsWith("xsd:")) {
            return COLOR_COMPLEX_TYPE;
        }

        return COLOR_DEFAULT_TYPE;
    }

    /**
     * Determines the appropriate node label style based on cardinality
     * matching XsdDiagramView behavior
     */
    private String determineNodeLabelStyle(XsdExtendedElement element) {
        Node node = element.getCurrentNode();
        if (node == null) {
            return ELEMENT_MANDATORY_FORMAT;
        }

        String minOccurs = getAttributeValue(node, "minOccurs", "1");
        String maxOccurs = getAttributeValue(node, "maxOccurs", "1");

        boolean isOptional = "0".equals(minOccurs);
        boolean isRepeatable = "unbounded".equals(maxOccurs) ||
                (maxOccurs != null && !"1".equals(maxOccurs) && !"0".equals(maxOccurs));

        if (isRepeatable) {
            return ELEMENT_REPEATABLE_FORMAT;
        } else if (isOptional) {
            return ELEMENT_OPTIONAL_FORMAT;
        } else {
            return ELEMENT_MANDATORY_FORMAT;
        }
    }

}
