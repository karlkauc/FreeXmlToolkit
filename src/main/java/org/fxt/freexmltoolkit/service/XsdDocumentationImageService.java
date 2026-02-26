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

    // A ThreadLocal that provides a reusable Transformer instance for each thread.
    // This avoids the high overhead of creating a new instance on every call to asString().
    private static final ThreadLocal<Transformer> transformerThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            TransformerFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureTransformerFactory();
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

    // A ThreadLocal that provides a reusable PNGTranscoder instance for each thread.
    // This significantly improves PNG generation performance by avoiding repeated transcoder creation.
    private static final ThreadLocal<PNGTranscoder> pngTranscoderThreadLocal = ThreadLocal.withInitial(() -> {
        PNGTranscoder transcoder = new PNGTranscoder();
        // Configure transcoder with default settings
        transcoder.addTranscodingHint(PNGTranscoder.KEY_FORCE_TRANSPARENT_WHITE, false);
        transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);
        return transcoder;
    });

    // A ThreadLocal that provides a reusable JPEGTranscoder instance for each thread.
    // This significantly improves JPG generation performance by avoiding repeated transcoder creation.
    private static final ThreadLocal<JPEGTranscoder> jpegTranscoderThreadLocal = ThreadLocal.withInitial(() -> {
        JPEGTranscoder transcoder = new JPEGTranscoder();
        // Configure transcoder with default settings - high quality JPEG
        transcoder.addTranscodingHint(JPEGTranscoder.KEY_QUALITY, 0.95f);
        transcoder.addTranscodingHint(JPEGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);
        return transcoder;
    });

    // Modern spacing and layout parameters
    final int margin = 16;
    final int gapBetweenSides = 120;
    final int boxPadding = 12;
    final int borderRadius = 8;
    final int plusIconApproxWidth = 24;

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
     * Flag to control whether documentation is displayed in the SVG diagram.
     * When true (default), documentation from the root element is shown below the element box.
     */
    private boolean showDocumentation = true;

    /**
     * Sets whether documentation should be displayed in the SVG diagram.
     *
     * @param showDocumentation true to show documentation, false to hide it
     */
    public void setShowDocumentation(boolean showDocumentation) {
        this.showDocumentation = showDocumentation;
    }

    /**
     * Returns whether documentation is displayed in the SVG diagram.
     *
     * @return true if documentation is shown, false otherwise
     */
    public boolean isShowDocumentation() {
        return showDocumentation;
    }

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

            // 3. Get thread-local PNG transcoder (reused for performance)
            PNGTranscoder transcoder = pngTranscoderThreadLocal.get();

            // 4. Create input for transcoder directly from optimized DOM document
            TranscoderInput input = new TranscoderInput(pngOptimizedSvg);

            // 5. Safely manage resources with try-with-resources
            // Use BufferedOutputStream for better I/O performance
            try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file), 65536)) {
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
     * Generates a JPG image directly from the DOM representation of the XSD element.
     * This version is more efficient and avoids serialization warnings by passing the
     * DOM document directly to the transcoder.
     *
     * @param rootElement the root XSD element to generate the diagram from
     * @param file        the file to save the generated image (should have a .jpg extension)
     * @return the file path of the generated image, or null on failure
     */
    public String generateJpegImage(XsdExtendedElement rootElement, File file) {
        try {
            // Check for null element first
            if (rootElement == null) {
                logger.warn("Root element is null. Cannot generate JPEG image.");
                return null;
            }

            // 1. Generate SVG DOM document as before
            Document svgDocument = generateSvgDocument(rootElement);
            if (svgDocument.getDocumentElement() == null || !svgDocument.getDocumentElement().hasChildNodes()) {
                logger.warn("Generated SVG for {} is empty, skipping JPEG image creation.", rootElement.getCurrentXpath());
                return null;
            }

            // 2. Optimize SVG for raster rendering (remove CSS styles, disable interactive elements)
            Document optimizedSvg = optimizeSvgForPngRendering(svgDocument);

            // 3. Get thread-local JPEG transcoder (reused for performance)
            JPEGTranscoder transcoder = jpegTranscoderThreadLocal.get();

            // 4. Create input for transcoder directly from optimized DOM document
            TranscoderInput input = new TranscoderInput(optimizedSvg);

            // 5. Safely manage resources with try-with-resources
            // Use BufferedOutputStream for better I/O performance
            try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file), 65536)) {
                TranscoderOutput output = new TranscoderOutput(outputStream);

                // 6. Perform conversion
                transcoder.transcode(input, output);
            }

            logger.debug("Successfully created JPEG image: {}", file.getAbsolutePath());
            return file.getAbsolutePath();

        } catch (IOException | TranscoderException e) {
            logger.error("Failed to generate JPEG image for file '{}'", file.getAbsolutePath(), e);
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
        final double[] maxRightEdge = {0d};

        // XMLSpy-inspired defs section with gradients and effects
        Element defs = document.createElementNS(svgNS, "defs");

        // Gradient definitions for different element types
        addGradientDefinitions(document, defs);

        // Modern CSS styles with smooth transitions
        // Note: Inter font is loaded centrally in the HTML CSS, not embedded in each SVG
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
        filter.setAttribute("id", "dropShadow");
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

        // Collect child elements (including compositor containers)
        List<XsdExtendedElement> childElements = new ArrayList<>();
        if (rootElement.getChildren() != null) {
            for (String temp : rootElement.getChildren()) {
                if (extendedXsdElements.get(temp) != null) {
                    childElements.add(extendedXsdElements.get(temp));
                }
            }
        }

        // Check if there is exactly one child and it's a compositor container
        // In that case, we'll draw the compositor symbol and use its children
        // IMPORTANT: This must happen BEFORE calculating totalChildElementsHeight
        // so the root element is centered based on actual flattened children
        boolean isSequence = false;
        boolean isChoice = false;
        boolean isAll = false;

        if (childElements.size() == 1) {
            XsdExtendedElement onlyChild = childElements.get(0);
            String childName = onlyChild.getElementName();
            if (childName != null) {
                if (childName.startsWith("SEQUENCE")) {
                    isSequence = true;
                    // Replace childElements with the compositor's children
                    childElements = new ArrayList<>();
                    if (onlyChild.getChildren() != null) {
                        for (String compositorChildXpath : onlyChild.getChildren()) {
                            XsdExtendedElement compositorChild = extendedXsdElements.get(compositorChildXpath);
                            if (compositorChild != null) {
                                childElements.add(compositorChild);
                            }
                        }
                    }
                } else if (childName.startsWith("CHOICE")) {
                    isChoice = true;
                    // Replace childElements with the compositor's children
                    childElements = new ArrayList<>();
                    if (onlyChild.getChildren() != null) {
                        for (String compositorChildXpath : onlyChild.getChildren()) {
                            XsdExtendedElement compositorChild = extendedXsdElements.get(compositorChildXpath);
                            if (compositorChild != null) {
                                childElements.add(compositorChild);
                            }
                        }
                    }
                } else if (childName.startsWith("ALL")) {
                    isAll = true;
                    // Replace childElements with the compositor's children
                    childElements = new ArrayList<>();
                    if (onlyChild.getChildren() != null) {
                        for (String compositorChildXpath : onlyChild.getChildren()) {
                            XsdExtendedElement compositorChild = extendedXsdElements.get(compositorChildXpath);
                            if (compositorChild != null) {
                                childElements.add(compositorChild);
                            }
                        }
                    }
                }
            }
        }

        // Also check if root element has a type-level compositor (sequence/choice/all in complexType)
        if (!isSequence && !isChoice && !isAll && !childElements.isEmpty()) {
            String compositorType = detectTypeCompositor(rootElement);
            if (compositorType != null) {
                switch (compositorType) {
                    case "sequence":
                        isSequence = true;
                        break;
                    case "choice":
                        isChoice = true;
                        break;
                    case "all":
                        isAll = true;
                        break;
                }
            }
        }

        // Note: We keep childElements as-is (may contain nested compositor containers)
        // The recursive drawing method will handle nested compositors automatically

        // Calculate symmetric sizes - AFTER compositor flattening
        double maxChildWidth = calculateMaxChildWidth(childElements);

        var rootElementBounds = font.getStringBounds(rootElement.getElementName(), frc);
        var rootElementHeight = rootElementBounds.getBounds2D().getHeight();
        var rootElementWidth = rootElementBounds.getBounds2D().getWidth();

        // Calculate the actual total height of child elements (with flattened compositor children)
        double totalChildElementsHeight = calculateTotalChildElementsHeight(childElements);

        // Calculate documentation height before positioning (only if documentation should be shown)
        double docHeightTotal = showDocumentation
                ? calculateDocumentationHeight(rootElement.getDocumentations(), rootElementWidth)
                : 0;

        // Center the root element vertically in the middle of child elements, but ensure it's not positioned above the top margin
        int rootStartX = margin * 2;
        int rootStartY = Math.max(margin, (int) ((totalChildElementsHeight / 2) - ((boxPadding * 2 + rootElementHeight) / 2)));

        // Draw the root element with modern design
        Element leftRootLink = document.createElementNS(svgNS, "a");
        leftRootLink.setAttribute("href", "#");

        Element rect1 = createModernSvgRect(document, rootElement.getCurrentXpath(),
                rootElementHeight, rootElementWidth,
                String.valueOf(rootStartX), String.valueOf(rootStartY));
        rect1.setAttribute("filter", "url(#dropShadow)");
        rect1.setAttribute("style", rootElement.isMandatory() ? ELEMENT_MANDATORY_FORMAT : ELEMENT_OPTIONAL_FORMAT);
        rect1.setAttribute("class", "hoverable-rect");

        Element text1 = createSvgTextElement(document, rootElement.getElementName(),
                String.valueOf(rootStartX + boxPadding),
                String.valueOf(rootStartY + boxPadding + rootElementHeight),
                COLOR_TEXT_PRIMARY, font.getSize());

        leftRootLink.appendChild(rect1);
        leftRootLink.appendChild(text1);
        svgRoot.appendChild(leftRootLink);
        updateMaxRightEdge(maxRightEdge, rootStartX + boxPadding * 2 + rootElementWidth);

        // Add documentation (only if showDocumentation is enabled)
        if (showDocumentation) {
            generateModernDocumentationElement(document, rootElement.getDocumentations(),
                    rootElementWidth, rootElementHeight, rootStartX, rootStartY);
        }

        // Symmetric positioning of child elements with more space for cardinality
        final double rightStartX = rootStartX + boxPadding * 2 + rootElementWidth + gapBetweenSides + 40;
        final double rootPathEndX = rootStartX + boxPadding * 2 + rootElementWidth;
        final double rootPathCenterY = rootStartY + (boxPadding * 2 + rootElementHeight) / 2;

        // Calculate symbol position explicitly
        final double symbolCenterX = rootPathEndX + (gapBetweenSides / 2.0);
        final double symbolCenterY = rootPathCenterY; // Y-Position der Symbol-Mitte
        final double finalSymbolCenterX = symbolCenterX;

        if (isSequence || isChoice || isAll) {
            drawModernSequenceChoiceSymbol(document, rootPathEndX, rootPathCenterY,
                    gapBetweenSides, isSequence, isChoice, childElements, svgRoot, maxRightEdge);
        }

        // Draw child elements with symmetric layout
        double actualHeight = margin * 2;
        final double finalRightStartX = rightStartX;

        // Make sequence/choice/all flags and symbol position available in the loop
        final boolean hasCompositor = isSequence || isChoice || isAll;
        final boolean finalIsSequence = isSequence;
        final boolean finalIsChoice = isChoice;
        final double finalSymbolCenterY = symbolCenterY;

        // Calculate connection start point based on whether there's a root-level compositor
        double connectionStartX, connectionStartY;
        if (hasCompositor) {
            // Start from the RIGHT EDGE of the compositor symbol
            connectionStartX = finalSymbolCenterX + (finalIsSequence ? 25 : (finalIsChoice ? 15 : 20));
            connectionStartY = finalSymbolCenterY;
        } else {
            // Start from root element
            connectionStartX = rootPathEndX;
            connectionStartY = rootPathCenterY;
        }

        // Draw children recursively - this handles nested compositors automatically
        String childConnectionColor;
        if (hasCompositor) {
            if (isChoice) {
                childConnectionColor = COLOR_STROKE_CHOICE;
            } else if (isSequence) {
                childConnectionColor = COLOR_STROKE_SEQUENCE;
            } else {
                childConnectionColor = COLOR_STROKE_ANY;
            }
        } else {
            childConnectionColor = COLOR_STROKE_ELEMENT;
        }

        double childrenHeight = drawChildrenRecursive(document, svgRoot, childElements,
                actualHeight, finalRightStartX,
                connectionStartX, connectionStartY, maxRightEdge,
                hasCompositor,
                childConnectionColor);

        actualHeight += childrenHeight;

        // Calculate total height of root element including documentation
        double rootElementTotalHeight = rootStartY + (boxPadding * 2 + rootElementHeight) + docHeightTotal;

        // Finalize SVG size with symmetric layout - considers both root and child elements
        // Ensure minimum height to accommodate all content with proper margins
        var imageHeight = Math.max(rootElementTotalHeight + margin, actualHeight);
        svgRoot.setAttribute("height", String.valueOf(imageHeight + margin));
        double computedWidth = Math.max(
                finalRightStartX + boxPadding * 2 + maxChildWidth + margin * 3,
                maxRightEdge[0] + margin * 2);
        svgRoot.setAttribute("width", String.valueOf(computedWidth));
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
            double nameWidth = nameBounds.getBounds2D().getWidth();

            // Get cardinality string and calculate badge width
            org.w3c.dom.Node childDomNode = childElement.getCurrentNode();
            String minOccurs = getAttributeValue(childDomNode, "minOccurs", "1");
            String maxOccurs = getAttributeValue(childDomNode, "maxOccurs", "1");
            String cardinality = getCardinalityString(minOccurs, maxOccurs);
            double cardinalityWidth = font.getStringBounds(cardinality, frc).getBounds2D().getWidth() + 8; // +8 for badge padding

            // Calculate width of second line: Type + spacing + cardinality badge (right-aligned)
            double secondLineWidth = 0;
            if (!elementType.isBlank()) {
                var typeBounds = font.getStringBounds(elementType, frc);
                double typeWidth = typeBounds.getBounds2D().getWidth();

                // Minimum spacing between type and right-aligned cardinality
                double minSpacing = 20;
                secondLineWidth = typeWidth + minSpacing + cardinalityWidth;
            } else {
                // If no type, cardinality badge is right-aligned with name on first line
                secondLineWidth = nameWidth;
            }

            maxWidth = Math.max(maxWidth, Math.max(nameWidth, secondLineWidth));
        }
        return maxWidth + boxPadding * 2;
    }

    /**
     * Draws modern sequence/choice symbols
     */
    private void drawModernSequenceChoiceSymbol(Document document, double rootPathEndX, double rootPathCenterY,
                                                double gapBetweenSides, boolean isSequence, boolean isChoice,
                                                List<XsdExtendedElement> childElements, Element svgRoot,
                                                double[] maxRightEdge) {

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

        // Track the right edge of the compositor symbol
        updateMaxRightEdge(maxRightEdge, symbolCenterX + symbolWidth / 2);
    }

    private void updateMaxRightEdge(double[] maxRightEdge, double candidate) {
        if (maxRightEdge != null && candidate > maxRightEdge[0]) {
            maxRightEdge[0] = candidate;
        }
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
        textElement.setAttribute("font-family", getFontFamilyWithFallbacks());
        textElement.setAttribute("font-size", String.valueOf(fontSize));
        textElement.setAttribute("x", x);
        textElement.setAttribute("y", y);
        textElement.setTextContent(textContent);
        return textElement;
    }

    /**
     * Returns the font-family string with fallback fonts for maximum compatibility.
     * This ensures the SVG displays correctly even if the Inter font is not embedded or available.
     *
     * @return font-family string with fallbacks
     */
    private String getFontFamilyWithFallbacks() {
        // Use Inter as primary font (will be embedded in SVG),
        // with comprehensive fallbacks for different operating systems
        return "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', Arial, sans-serif";
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
        docText.setAttribute("font-family", getFontFamilyWithFallbacks());
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

            // Simplify font-families to be compatible with Apache Batik
            simplifyFontFamiliesForBatik(optimizedSvg);

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
     * Simplifies font-family attributes to be compatible with Apache Batik's CSS parser.
     * Batik cannot handle modern CSS font stacks like '-apple-system' or 'BlinkMacSystemFont'.
     */
    private void simplifyFontFamiliesForBatik(Document svg) {
        Element root = svg.getDocumentElement();
        if (root == null) return;

        // Simple, Batik-compatible font family
        final String batikSafeFontFamily = "Arial, Helvetica, sans-serif";

        // Process all text elements
        NodeList textElements = root.getElementsByTagNameNS(svgNS, "text");
        for (int i = 0; i < textElements.getLength(); i++) {
            Element textElement = (Element) textElements.item(i);
            String fontFamily = textElement.getAttribute("font-family");
            if (fontFamily != null && !fontFamily.isEmpty()) {
                // Replace complex font stack with simple one
                if (fontFamily.contains("-apple-system") || fontFamily.contains("BlinkMacSystemFont") ||
                        fontFamily.contains("Inter") || fontFamily.contains("Segoe UI")) {
                    textElement.setAttribute("font-family", batikSafeFontFamily);
                }
            }
        }

        // Process all tspan elements (nested text)
        NodeList tspanElements = root.getElementsByTagNameNS(svgNS, "tspan");
        for (int i = 0; i < tspanElements.getLength(); i++) {
            Element tspanElement = (Element) tspanElements.item(i);
            String fontFamily = tspanElement.getAttribute("font-family");
            if (fontFamily != null && !fontFamily.isEmpty()) {
                if (fontFamily.contains("-apple-system") || fontFamily.contains("BlinkMacSystemFont") ||
                        fontFamily.contains("Inter") || fontFamily.contains("Segoe UI")) {
                    tspanElement.setAttribute("font-family", batikSafeFontFamily);
                }
            }
        }

        // Also check style elements for embedded CSS with problematic fonts
        NodeList styleElements = root.getElementsByTagNameNS(svgNS, "style");
        for (int i = 0; i < styleElements.getLength(); i++) {
            Element styleElement = (Element) styleElements.item(i);
            String cssContent = styleElement.getTextContent();
            if (cssContent != null && (cssContent.contains("-apple-system") ||
                    cssContent.contains("BlinkMacSystemFont") || cssContent.contains("'Inter'"))) {
                // Replace problematic font references in CSS
                cssContent = cssContent.replaceAll(
                        "'Inter',\\s*-apple-system,\\s*BlinkMacSystemFont,\\s*'Segoe UI',\\s*'Helvetica Neue',\\s*Arial,\\s*sans-serif",
                        batikSafeFontFamily);
                cssContent = cssContent.replaceAll(
                        "-apple-system,\\s*BlinkMacSystemFont,\\s*'Segoe UI',\\s*'Helvetica Neue',\\s*Arial,\\s*sans-serif",
                        batikSafeFontFamily);
                styleElement.setTextContent(cssContent);
            }
        }

        logger.debug("Simplified font-families for Batik compatibility");
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
        // For element references, use the cardinality node (which has minOccurs/maxOccurs)
        // Otherwise, use the current node
        Node node = element.getCardinalityNode();
        if (node == null) {
            node = element.getCurrentNode();
        }

        if (node == null) {
            return ELEMENT_MANDATORY_FORMAT;
        }

        String minOccurs = getAttributeValue(node, "minOccurs", "1");
        String maxOccurs = getAttributeValue(node, "maxOccurs", "1");

        boolean isOptional = "0".equals(minOccurs);
        boolean isRepeatable = "unbounded".equals(maxOccurs) ||
                (maxOccurs != null && !"1".equals(maxOccurs) && !"0".equals(maxOccurs));

        // Check optional first - an element with minOccurs="0" should always have a dashed border
        // regardless of whether it's repeatable or not
        if (isOptional) {
            return ELEMENT_OPTIONAL_FORMAT;
        } else if (isRepeatable) {
            return ELEMENT_REPEATABLE_FORMAT;
        } else {
            return ELEMENT_MANDATORY_FORMAT;
        }
    }

    /**
     * Recursively draws children elements, including compositor containers (CHOICE/SEQUENCE/ALL).
     * Compositor containers are drawn as symbols with their children nested beneath them.
     *
     * @param document         SVG document
     * @param svgRoot          SVG root element
     * @param children         List of child elements to draw
     * @param startY           Starting Y position
     * @param startX           Starting X position for elements
     * @param connectionStartX X position where connections start from
     * @param connectionStartY Y position where connections start from
     * @param maxRightEdge     Mutable tracker for the rightmost X coordinate drawn so far
     * @param useElbowConnections whether to render connector lines using L-shaped elbows
     * @param connectionStrokeColor stroke color to use for connections from this parent to its children
     * @return Total height consumed by all drawn elements
     */
    private double drawChildrenRecursive(Document document, Element svgRoot,
                                         List<XsdExtendedElement> children,
                                         double startY, double startX,
                                         double connectionStartX, double connectionStartY,
                                         double[] maxRightEdge,
                                         boolean useElbowConnections,
                                         String connectionStrokeColor) {
        if (children == null || children.isEmpty()) {
            return 0;
        }

        double currentY = startY;
        double maxWidth = calculateMaxChildWidth(children);

        for (XsdExtendedElement child : children) {
            String childName = child.getElementName();

            // Check if this is a compositor container
            boolean isCompositor = childName != null &&
                    (childName.startsWith("CHOICE") ||
                            childName.startsWith("SEQUENCE") ||
                            childName.startsWith("ALL"));

            if (isCompositor) {
                // Draw compositor symbol and its children
                String compositorType = childName.startsWith("CHOICE") ? "choice" :
                        (childName.startsWith("SEQUENCE") ? "sequence" : "all");

                // Get compositor's children
                List<XsdExtendedElement> compositorChildren = new ArrayList<>();
                if (child.getChildren() != null) {
                    for (String compositorChildXpath : child.getChildren()) {
                        XsdExtendedElement compositorChild = extendedXsdElements.get(compositorChildXpath);
                        if (compositorChild != null) {
                            compositorChildren.add(compositorChild);
                        }
                    }
                }

                // Draw compositor symbol
                double symbolX = startX + 60; // Position for the symbol
                double symbolY = currentY + 20;
                double symbolWidth = compositorType.equals("choice") ? 30 : (compositorType.equals("sequence") ? 50 : 40);
                double symbolHeight = 30;

                // Draw the symbol
                drawCompositorSymbolInline(document, svgRoot, compositorType, symbolX, symbolY, symbolWidth, symbolHeight, maxRightEdge);

                // Draw connection from parent to compositor symbol
                String strokeColor = compositorType.equals("choice") ? COLOR_STROKE_CHOICE :
                        (compositorType.equals("sequence") ? COLOR_STROKE_SEQUENCE : COLOR_STROKE_ANY);
                double targetX = symbolX - symbolWidth / 2;
                double targetY = symbolY + symbolHeight / 2;

                if (useElbowConnections) {
                    Double elbowX = computeElbowX(connectionStartX, targetX);
                    if (elbowX != null) {
                        Element connectionPath = document.createElementNS(svgNS, "path");
                        String pathData = "M " + connectionStartX + " " + connectionStartY +
                                " H " + elbowX +
                                " V " + targetY +
                                " H " + targetX;
                        connectionPath.setAttribute("d", pathData);
                        connectionPath.setAttribute("fill", "none");
                        connectionPath.setAttribute("stroke", strokeColor);
                        connectionPath.setAttribute("stroke-width", "2");
                        svgRoot.appendChild(connectionPath);
                    } else {
                        svgRoot.appendChild(createStraightConnection(document, connectionStartX, connectionStartY,
                                targetX, targetY, false, strokeColor));
                    }
                } else {
                    svgRoot.appendChild(createStraightConnection(document, connectionStartX, connectionStartY,
                            symbolX, symbolY + symbolHeight / 2, false, strokeColor));
                }

                // Recursively draw compositor's children
                double compositorChildrenStartX = symbolX + symbolWidth + 40;
                double compositorConnectionStartX = symbolX + symbolWidth;
                double compositorConnectionStartY = symbolY + symbolHeight / 2;

                String childStrokeColor = compositorType.equals("choice") ? COLOR_STROKE_CHOICE :
                        (compositorType.equals("sequence") ? COLOR_STROKE_SEQUENCE : COLOR_STROKE_ANY);

                double childrenHeight = drawChildrenRecursive(document, svgRoot, compositorChildren,
                        symbolY, compositorChildrenStartX,
                        compositorConnectionStartX, compositorConnectionStartY, maxRightEdge,
                        true,
                        childStrokeColor);

                // Update currentY to account for the compositor and its children
                currentY += Math.max(symbolHeight + 40, childrenHeight);

            } else {
                // Regular element - draw it normally
                String elementName2 = child.getElementName();
                String elementType = child.getElementType() != null ? child.getElementType() : "";

                var nameBounds = font.getStringBounds(elementName2, frc);
                var typeBounds = font.getStringBounds(elementType, frc);

                double nameHeight = nameBounds.getBounds2D().getHeight();
                double typeHeight = elementType.isBlank() ? 0 : typeBounds.getBounds2D().getHeight() + 8;
                double totalContentHeight = nameHeight + typeHeight;

                // Draw the element box
                Element elementBox = createModernSvgRect(document, child.getCurrentXpath(), totalContentHeight,
                        maxWidth, String.valueOf(startX), String.valueOf(currentY));
                elementBox.setAttribute("filter", "url(#dropShadow)");
                elementBox.setAttribute("style", determineNodeLabelStyle(child));
                elementBox.setAttribute("class", "hoverable-rect");

                // Check for optional/repeatable
                // For element references, use the cardinality node (which has minOccurs/maxOccurs)
                org.w3c.dom.Node childDomNode = child.getCardinalityNode();
                if (childDomNode == null) {
                    childDomNode = child.getCurrentNode();
                }
                String minOccurs = getAttributeValue(childDomNode, "minOccurs", "1");
                boolean isOptional = "0".equals(minOccurs);
                String maxOccursVal = getAttributeValue(childDomNode, "maxOccurs", "1");
                boolean isRepeatable = "unbounded".equals(maxOccursVal) ||
                        (maxOccursVal != null && !"1".equals(maxOccursVal) && !"0".equals(maxOccursVal));

                if (isRepeatable) {
                    Element innerBorder = document.createElementNS(svgNS, "rect");
                    innerBorder.setAttribute("x", String.valueOf(startX + 2));
                    innerBorder.setAttribute("y", String.valueOf(currentY + 2));
                    innerBorder.setAttribute("width", String.valueOf(boxPadding * 2 + maxWidth - 4));
                    innerBorder.setAttribute("height", String.valueOf(boxPadding * 2 + totalContentHeight - 4));
                    innerBorder.setAttribute("rx", String.valueOf(borderRadius));
                    innerBorder.setAttribute("ry", String.valueOf(borderRadius));
                    innerBorder.setAttribute("fill", "none");
                    innerBorder.setAttribute("stroke", "#87ceeb");
                    innerBorder.setAttribute("stroke-width", "1");
                    svgRoot.appendChild(innerBorder);
                }

                if (isOptional) {
                    Element dashedOverlay = document.createElementNS(svgNS, "rect");
                    dashedOverlay.setAttribute("x", String.valueOf(startX));
                    dashedOverlay.setAttribute("y", String.valueOf(currentY));
                    dashedOverlay.setAttribute("width", String.valueOf(boxPadding * 2 + maxWidth));
                    dashedOverlay.setAttribute("height", String.valueOf(boxPadding * 2 + totalContentHeight));
                    dashedOverlay.setAttribute("rx", String.valueOf(borderRadius));
                    dashedOverlay.setAttribute("ry", String.valueOf(borderRadius));
                    dashedOverlay.setAttribute("fill", "none");
                    dashedOverlay.setAttribute("stroke", COLOR_STROKE_ELEMENT);
                    dashedOverlay.setAttribute("stroke-width", "2");
                    dashedOverlay.setAttribute("stroke-dasharray", "5,2");
                    svgRoot.appendChild(dashedOverlay);
                }

                // Text group - XMLSpy style with vertical layout
                Element textGroup = document.createElementNS(svgNS, "g");
                double textY = currentY + boxPadding + nameHeight;

                // Element name (bold, dark blue) - first line
                Element nameTextNode = createSvgTextElement(document, elementName2,
                        String.valueOf(startX + boxPadding), String.valueOf(textY),
                        COLOR_TEXT_PRIMARY, font.getSize());
                nameTextNode.setAttribute("font-weight", "600");
                textGroup.appendChild(nameTextNode);

                // Horizontal separator line below name
                if (!elementType.isBlank()) {
                    Element separatorLine = document.createElementNS(svgNS, "line");
                    separatorLine.setAttribute("x1", String.valueOf(startX + boxPadding));
                    separatorLine.setAttribute("y1", String.valueOf(textY + 4));
                    separatorLine.setAttribute("x2", String.valueOf(startX + boxPadding + maxWidth));
                    separatorLine.setAttribute("y2", String.valueOf(textY + 4));
                    separatorLine.setAttribute("stroke", "#e0e0e0");
                    separatorLine.setAttribute("stroke-width", "1");
                    textGroup.appendChild(separatorLine);

                    // Type information (normal, gray) - second line below separator
                    double typeY = textY + typeHeight;
                    Element typeTextNode = createSvgTextElement(document, elementType,
                            String.valueOf(startX + boxPadding), String.valueOf(typeY),
                            COLOR_TEXT_SECONDARY, font.getSize() - 1);
                    textGroup.appendChild(typeTextNode);

                    // Cardinality badge - XMLSpy style: [1], [0..1], [1..*] - right-aligned
                    String cardinality = getCardinalityString(minOccurs, maxOccursVal);
                    double cardinalityBadgeWidth = font.getStringBounds(cardinality, frc).getBounds2D().getWidth() + 8;
                    double cardinalityX = startX + boxPadding + maxWidth - cardinalityBadgeWidth;
                    Element cardinalityBadge = createCardinalityBadge(document, cardinality,
                            String.valueOf(cardinalityX), String.valueOf(typeY - nameHeight + 4));
                    textGroup.appendChild(cardinalityBadge);
                } else {
                    // If no type, show cardinality right-aligned
                    String cardinality = getCardinalityString(minOccurs, maxOccursVal);
                    double cardinalityBadgeWidth = font.getStringBounds(cardinality, frc).getBounds2D().getWidth() + 8;
                    double cardinalityX = startX + boxPadding + maxWidth - cardinalityBadgeWidth;
                    Element cardinalityBadge = createCardinalityBadge(document, cardinality,
                            String.valueOf(cardinalityX), String.valueOf(textY - nameHeight + 4));
                    textGroup.appendChild(cardinalityBadge);
                }

                // Modern plus icon for elements with children
                Element useIcon = null;
                if (child.hasChildren()) {
                    useIcon = document.createElementNS(svgNS, "use");
                    useIcon.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#modern-plus-icon");
                    useIcon.setAttribute("x", String.valueOf(startX + boxPadding + maxWidth + margin));
                    double boxCenterY = currentY + (boxPadding * 2 + totalContentHeight) / 2;
                    useIcon.setAttribute("y", String.valueOf(boxCenterY - 12));
                }

                // Link around the box for all elements with pageName
                if (child.getPageName() != null && !child.getPageName().isEmpty()) {
                    Element rightLink = document.createElementNS(svgNS, "a");
                    rightLink.setAttribute("href", child.getPageName());
                    rightLink.appendChild(elementBox);
                    rightLink.appendChild(textGroup);
                    if (useIcon != null) {
                        rightLink.appendChild(useIcon);
                    }
                    svgRoot.appendChild(rightLink);
                } else {
                    svgRoot.appendChild(elementBox);
                    svgRoot.appendChild(textGroup);
                    if (useIcon != null) {
                        svgRoot.appendChild(useIcon);
                    }
                }

                double elementRightEdge = startX + boxPadding * 2 + maxWidth;
                updateMaxRightEdge(maxRightEdge, elementRightEdge);
                if (useIcon != null) {
                    double plusIconRightEdge = startX + boxPadding + maxWidth + margin + plusIconApproxWidth;
                    updateMaxRightEdge(maxRightEdge, plusIconRightEdge);
                }

                // Draw connection line from parent to this element
                double childElementCenterY = currentY + (boxPadding * 2 + totalContentHeight) / 2;
                Element connectionElement;
                if (useElbowConnections) {
                    Double elbowX = computeElbowX(connectionStartX, startX);
                    if (elbowX != null) {
                        Element connectionPath = document.createElementNS(svgNS, "path");
                        String pathData = "M " + connectionStartX + " " + connectionStartY +
                                " H " + elbowX +
                                " V " + childElementCenterY +
                                " H " + startX;
                        connectionPath.setAttribute("d", pathData);
                        connectionPath.setAttribute("fill", "none");
                        connectionPath.setAttribute("stroke", connectionStrokeColor);
                        connectionPath.setAttribute("stroke-width", "2");
                        if (isOptional) {
                            connectionPath.setAttribute("stroke-dasharray", "5,5");
                        }
                        connectionElement = connectionPath;
                    } else {
                        connectionElement = createStraightConnection(document, connectionStartX, connectionStartY,
                                startX, childElementCenterY, isOptional, connectionStrokeColor);
                    }
                } else {
                    connectionElement = createStraightConnection(document, connectionStartX, connectionStartY,
                            startX, childElementCenterY, isOptional, connectionStrokeColor);
                }
                svgRoot.appendChild(connectionElement);

                // Update currentY for next element
                currentY += boxPadding * 2 + totalContentHeight + margin * 2;
            }
        }

        return currentY - startY;
    }

    /**
     * Detects if an element has a type-level compositor (sequence/choice/all in its complexType definition).
     * Returns "sequence", "choice", "all", or null if no compositor is found.
     */
    private String detectTypeCompositor(XsdExtendedElement element) {
        if (element == null || element.getCurrentNode() == null) {
            return null;
        }

        org.w3c.dom.Node node = element.getCurrentNode();

        try {
            // First, check for inline xs:complexType child
            org.w3c.dom.NodeList childNodes = node.getChildNodes();
            if (childNodes != null) {
                for (int i = 0; i < childNodes.getLength(); i++) {
                    org.w3c.dom.Node child = childNodes.item(i);
                    if (child != null && child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        String localName = child.getLocalName();
                        if ("complexType".equals(localName)) {
                            // Found inline complexType, check for compositor
                            String compositor = findCompositorInComplexType(child);
                            if (compositor != null) {
                                return compositor;
                            }
                        }
                    }
                }
            }

            // Second, check if element has a type attribute referencing a named type
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element elementNode = (org.w3c.dom.Element) node;
                String typeAttribute = elementNode.getAttribute("type");

                if (typeAttribute != null && !typeAttribute.isEmpty()) {
                    // Remove namespace prefix if present (e.g., "xs:string" -> "string")
                    String typeName = typeAttribute.contains(":") ?
                            typeAttribute.substring(typeAttribute.indexOf(":") + 1) :
                            typeAttribute;

                    // Skip built-in types
                    if (isBuiltInType(typeName)) {
                        return null;
                    }

                    // Find the named complexType definition in the schema
                    org.w3c.dom.Document document = node.getOwnerDocument();
                    if (document != null) {
                        org.w3c.dom.Node typeDefinition = findNamedComplexType(document, typeName);

                        if (typeDefinition != null) {
                            return findCompositorInComplexType(typeDefinition);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle any DOM exceptions (e.g., when working with mock nodes in tests or corrupted NodeList cache)
            logger.trace("Could not detect compositor for element: {}", element.getElementName(), e);
            return null;
        }

        return null;
    }

    /**
     * Searches for a compositor (sequence/choice/all) within a complexType node.
     */
    private String findCompositorInComplexType(org.w3c.dom.Node complexTypeNode) {
        if (complexTypeNode == null) {
            return null;
        }

        try {
            org.w3c.dom.NodeList children = complexTypeNode.getChildNodes();
            if (children == null) {
                return null;
            }

            int length = children.getLength();
            for (int i = 0; i < length; i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child != null && child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    String localName = child.getLocalName();
                    if ("sequence".equals(localName)) {
                        return "sequence";
                    } else if ("choice".equals(localName)) {
                        return "choice";
                    } else if ("all".equals(localName)) {
                        return "all";
                    }
                }
            }
        } catch (Exception e) {
            // Handle DOM exceptions (e.g., corrupted NodeList cache)
            logger.trace("Could not iterate children of complexType node", e);
            return null;
        }
        return null;
    }

    /**
     * Finds a named complexType definition in the schema document.
     */
    private org.w3c.dom.Node findNamedComplexType(org.w3c.dom.Document document, String typeName) {
        if (document == null || typeName == null) {
            return null;
        }

        try {
            // Get all complexType elements in the schema
            org.w3c.dom.NodeList complexTypes = document.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");

            if (complexTypes == null) {
                return null;
            }

            int length = complexTypes.getLength();
            for (int i = 0; i < length; i++) {
                org.w3c.dom.Node complexType = complexTypes.item(i);
                if (complexType != null && complexType.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    org.w3c.dom.Element complexTypeElement = (org.w3c.dom.Element) complexType;
                    String nameAttribute = complexTypeElement.getAttribute("name");

                    if (typeName.equals(nameAttribute)) {
                        return complexType;
                    }
                }
            }
        } catch (Exception e) {
            // Handle DOM exceptions (e.g., corrupted NodeList cache)
            logger.trace("Could not search for complexType: {}", typeName, e);
            return null;
        }

        return null;
    }

    /**
     * Checks if a type name is a built-in XSD type.
     */
    private boolean isBuiltInType(String typeName) {
        return typeName.equals("string") || typeName.equals("int") || typeName.equals("integer") ||
                typeName.equals("decimal") || typeName.equals("boolean") || typeName.equals("date") ||
                typeName.equals("dateTime") || typeName.equals("time") || typeName.equals("duration") ||
                typeName.equals("float") || typeName.equals("double") || typeName.equals("long") ||
                typeName.equals("short") || typeName.equals("byte") || typeName.equals("base64Binary") ||
                typeName.equals("hexBinary") || typeName.equals("anyURI") || typeName.equals("QName") ||
                typeName.equals("NOTATION") || typeName.equals("normalizedString") || typeName.equals("token") ||
                typeName.equals("language") || typeName.equals("Name") || typeName.equals("NCName") ||
                typeName.equals("ID") || typeName.equals("IDREF") || typeName.equals("IDREFS") ||
                typeName.equals("ENTITY") || typeName.equals("ENTITIES") || typeName.equals("NMTOKEN") ||
                typeName.equals("NMTOKENS") || typeName.equals("positiveInteger") || typeName.equals("negativeInteger") ||
                typeName.equals("nonPositiveInteger") || typeName.equals("nonNegativeInteger") ||
                typeName.equals("unsignedLong") || typeName.equals("unsignedInt") || typeName.equals("unsignedShort") ||
                typeName.equals("unsignedByte") || typeName.equals("gYearMonth") || typeName.equals("gYear") ||
                typeName.equals("gMonthDay") || typeName.equals("gDay") || typeName.equals("gMonth") ||
                typeName.equals("anyType") || typeName.equals("anySimpleType");
    }

    /**
     * Creates a cardinality string in XMLSpy format.
     * Examples: [1], [0..1], [1..*], [0..*]
     */
    private String getCardinalityString(String minOccurs, String maxOccurs) {
        String min = (minOccurs == null || minOccurs.isEmpty()) ? "1" : minOccurs;
        String max = (maxOccurs == null || maxOccurs.isEmpty()) ? "1" : maxOccurs;

        if ("unbounded".equals(max)) {
            max = "*";
        }

        if (min.equals(max)) {
            return "[" + min + "]";
        } else {
            return "[" + min + ".." + max + "]";
        }
    }

    /**
     * Creates an XMLSpy-style cardinality badge SVG element.
     * Example: [1], [0..1], [1..*]
     */
    private Element createCardinalityBadge(Document document, String cardinality, String x, String y) {
        Element group = document.createElementNS(svgNS, "g");

        // Background rectangle
        double textWidth = font.getStringBounds(cardinality, frc).getBounds2D().getWidth();
        Element rect = document.createElementNS(svgNS, "rect");
        rect.setAttribute("x", x);
        rect.setAttribute("y", y);
        rect.setAttribute("width", String.valueOf(textWidth + 8));
        rect.setAttribute("height", "16");
        rect.setAttribute("rx", "2");
        rect.setAttribute("ry", "2");
        rect.setAttribute("fill", "#e9ecef");
        rect.setAttribute("stroke", "#adb5bd");
        rect.setAttribute("stroke-width", "1");
        group.appendChild(rect);

        // Text
        Element text = createSvgTextElement(document, cardinality,
                String.valueOf(Double.parseDouble(x) + 4),
                String.valueOf(Double.parseDouble(y) + 11),
                "#495057", 10);
        text.setAttribute("font-family", "monospace");
        text.setAttribute("font-weight", "500");
        group.appendChild(text);

        return group;
    }

    @SuppressWarnings("unused")
    private Element createStraightConnection(Document document, double x1, double y1,
                                             double x2, double y2, boolean isOptional) {
        return createStraightConnection(document, x1, y1, x2, y2, isOptional, COLOR_STROKE_ELEMENT);
    }

    private Element createStraightConnection(Document document, double x1, double y1,
                                             double x2, double y2, boolean isOptional,
                                             String strokeColor) {
        Element connectionLine = document.createElementNS(svgNS, "line");
        connectionLine.setAttribute("x1", String.valueOf(x1));
        connectionLine.setAttribute("y1", String.valueOf(y1));
        connectionLine.setAttribute("x2", String.valueOf(x2));
        connectionLine.setAttribute("y2", String.valueOf(y2));
        connectionLine.setAttribute("stroke", strokeColor);
        connectionLine.setAttribute("stroke-width", "2");
        if (isOptional) {
            connectionLine.setAttribute("stroke-dasharray", "5,5");
        }
        return connectionLine;
    }

    private Double computeElbowX(double connectionStartX, double targetX) {
        double available = targetX - connectionStartX;
        if (available <= 6) {
            return null;
        }

        double elbowOffset = Math.min(Math.max(available * 0.25, 16), available - 6);
        if (elbowOffset <= 4 || elbowOffset >= available) {
            return null;
        }

        return connectionStartX + elbowOffset;
    }

    /**
     * Draws a compositor symbol (CHOICE, SEQUENCE, or ALL) at the specified position.
     */
    private void drawCompositorSymbolInline(Document document, Element svgRoot,
                                            String compositorType, double x, double y,
                                            double width, double height, double[] maxRightEdge) {
        Element group = document.createElementNS(svgNS, "g");

        if (compositorType.equals("sequence")) {
            // Sequence as filled rounded rectangle with icon
            Element rect = document.createElementNS(svgNS, "rect");
            rect.setAttribute("x", String.valueOf(x - width / 2));
            rect.setAttribute("y", String.valueOf(y));
            rect.setAttribute("width", String.valueOf(width));
            rect.setAttribute("height", String.valueOf(height));
            rect.setAttribute("fill", "url(#sequenceGradient)");
            rect.setAttribute("stroke", COLOR_STROKE_SEQUENCE);
            rect.setAttribute("stroke-width", "2");
            rect.setAttribute("rx", "4");
            rect.setAttribute("ry", "4");
            group.appendChild(rect);

            Element icon = document.createElementNS(svgNS, "use");
            icon.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#sequence-icon");
            icon.setAttribute("x", String.valueOf(x - width / 2 + 8));
            icon.setAttribute("y", String.valueOf(y + 5));
            group.appendChild(icon);

        } else if (compositorType.equals("choice")) {
            // Choice as dashed rounded rectangle with icon
            Element rect = document.createElementNS(svgNS, "rect");
            rect.setAttribute("x", String.valueOf(x - width / 2));
            rect.setAttribute("y", String.valueOf(y));
            rect.setAttribute("width", String.valueOf(width));
            rect.setAttribute("height", String.valueOf(height));
            rect.setAttribute("fill", "url(#choiceGradient)");
            rect.setAttribute("stroke", COLOR_STROKE_CHOICE);
            rect.setAttribute("stroke-width", "2");
            rect.setAttribute("stroke-dasharray", "5,5");
            rect.setAttribute("rx", "4");
            rect.setAttribute("ry", "4");
            group.appendChild(rect);

            Element icon = document.createElementNS(svgNS, "use");
            icon.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", "#choice-icon");
            icon.setAttribute("x", String.valueOf(x - width / 2 + 6));
            icon.setAttribute("y", String.valueOf(y + 7));
            group.appendChild(icon);

        } else { // "all"
            // All as filled rounded rectangle
            Element rect = document.createElementNS(svgNS, "rect");
            rect.setAttribute("x", String.valueOf(x - width / 2));
            rect.setAttribute("y", String.valueOf(y));
            rect.setAttribute("width", String.valueOf(width));
            rect.setAttribute("height", String.valueOf(height));
            rect.setAttribute("fill", COLOR_BOX_FILL_ANY);
            rect.setAttribute("stroke", COLOR_STROKE_ANY);
            rect.setAttribute("stroke-width", "2");
            rect.setAttribute("rx", "4");
            rect.setAttribute("ry", "4");
            group.appendChild(rect);
        }

        svgRoot.appendChild(group);
        updateMaxRightEdge(maxRightEdge, x + width / 2);
    }

}
