/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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
 */

package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

public class XsdDocumentationSvgService {

    private static final Logger logger = LogManager.getLogger(XsdDocumentationSvgService.class);

    ClassLoaderTemplateResolver resolver;
    TemplateEngine templateEngine;

    private File outputDirectory;
    XsdDocumentationData xsdDocumentationData;

    // SVG Layout Constants
    private final int NODE_WIDTH = 200;
    private final int NODE_HEIGHT = 80;
    private final int HORIZONTAL_SPACING = 250;
    private final int VERTICAL_SPACING = 120;
    private final int START_X = 50;
    private final int START_Y = 50;
    private final int FONT_SIZE = 12;
    private final int TITLE_FONT_SIZE = 14;

    // Colors matching XsdDiagramView styles
    private final String COLOR_ROOT = "#4A90E2";
    private final String COLOR_ELEMENT = "#4a90e2"; // NODE_LABEL_STYLE
    private final String COLOR_ATTRIBUTE = "#d4a147"; // ATTRIBUTE_LABEL_STYLE
    private final String COLOR_SEQUENCE = "#6c757d"; // SEQUENCE_NODE_STYLE
    private final String COLOR_CHOICE = "#ff8c00"; // CHOICE_NODE_STYLE
    private final String COLOR_ANY = "#adb5bd"; // ANY_NODE_STYLE
    private final String COLOR_OPTIONAL = "#FF6B6B";
    private final String COLOR_MANDATORY = "#4CAF50";
    private final String COLOR_BORDER = "#333333";
    private final String COLOR_TEXT = "#2C3E50";
    private final String COLOR_BACKGROUND = "#f0f8ff";

    // Type-specific colors from XsdDiagramView
    private final String COLOR_STRING_TYPE = "#28a745";
    private final String COLOR_NUMERIC_TYPE = "#007bff";
    private final String COLOR_DATE_TYPE = "#fd7e14";
    private final String COLOR_BOOLEAN_TYPE = "#6f42c1";
    private final String COLOR_BINARY_TYPE = "#6c757d";
    private final String COLOR_URI_TYPE = "#17a2b8";
    private final String COLOR_QNAME_TYPE = "#e83e8c";
    private final String COLOR_LANGUAGE_TYPE = "#20c997";
    private final String COLOR_COMPLEX_TYPE = "#dc3545";
    private final String COLOR_DEFAULT_TYPE = "#4a90e2";

    public XsdDocumentationSvgService() {
        resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("xsdDocumentation/");
        resolver.setSuffix(".html");

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void setDocumentationData(XsdDocumentationData xsdDocumentationData) {
        this.xsdDocumentationData = xsdDocumentationData;
    }

    public void generateSvgPage() {
        final var context = new Context();
        context.setVariable("date", LocalDate.now());
        context.setVariable("filename", Paths.get(xsdDocumentationData.getXsdFilePath()).getFileName().toString());
        context.setVariable("version", xsdDocumentationData.getVersion());
        context.setVariable("targetNamespace", xsdDocumentationData.getTargetNamespace());

        final var svgContent = generateCompleteSvgDiagram();
        context.setVariable("svgContent", svgContent);

        final var result = templateEngine.process("templateSvg", context);
        final var outputFileName = Paths.get(outputDirectory.getPath(), "schema-svg.html").toFile().getAbsolutePath();
        logger.debug("SVG File: {}", outputFileName);

        try {
            Files.writeString(Paths.get(outputFileName), result, StandardCharsets.UTF_8);
            logger.info("Written {} bytes in File '{}'", new File(outputFileName).length(), outputFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String generateCompleteSvgDiagram() {
        StringBuilder svgBuilder = new StringBuilder();

        // Calculate layout
        LayoutInfo layoutInfo = calculateLayout();

        // Add SVG container with zoom and pan support
        svgBuilder.append(String.format(
                "<svg width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" xmlns=\"http://www.w3.org/2000/svg\">",
                layoutInfo.totalWidth, layoutInfo.totalHeight, layoutInfo.totalWidth, layoutInfo.totalHeight
        ));

        // Add zoom and pan functionality with enhanced styles
        svgBuilder.append("<defs>");

        // Define gradients for different element types
        svgBuilder.append("<linearGradient id='elementGrad' x1='0%' y1='0%' x2='0%' y2='100%'>");
        svgBuilder.append("<stop offset='0%' style='stop-color:#ffffff;stop-opacity:1' />");
        svgBuilder.append("<stop offset='100%' style='stop-color:#f0f8ff;stop-opacity:1' />");
        svgBuilder.append("</linearGradient>");

        svgBuilder.append("<linearGradient id='attributeGrad' x1='0%' y1='0%' x2='0%' y2='100%'>");
        svgBuilder.append("<stop offset='0%' style='stop-color:#fffef7;stop-opacity:1' />");
        svgBuilder.append("<stop offset='100%' style='stop-color:#f9f5e7;stop-opacity:1' />");
        svgBuilder.append("</linearGradient>");

        svgBuilder.append("<linearGradient id='sequenceGrad' x1='0%' y1='0%' x2='0%' y2='100%'>");
        svgBuilder.append("<stop offset='0%' style='stop-color:#f8f9fa;stop-opacity:1' />");
        svgBuilder.append("<stop offset='100%' style='stop-color:#e9ecef;stop-opacity:1' />");
        svgBuilder.append("</linearGradient>");

        svgBuilder.append("<linearGradient id='choiceGrad' x1='0%' y1='0%' x2='0%' y2='100%'>");
        svgBuilder.append("<stop offset='0%' style='stop-color:#fffbf0;stop-opacity:1' />");
        svgBuilder.append("<stop offset='100%' style='stop-color:#fff3cd;stop-opacity:1' />");
        svgBuilder.append("</linearGradient>");

        svgBuilder.append("<linearGradient id='anyGrad' x1='0%' y1='0%' x2='0%' y2='100%'>");
        svgBuilder.append("<stop offset='0%' style='stop-color:#f8f9fa;stop-opacity:1' />");
        svgBuilder.append("<stop offset='100%' style='stop-color:#dee2e6;stop-opacity:1' />");
        svgBuilder.append("</linearGradient>");

        // Define drop shadow filter
        svgBuilder.append("<filter id='dropShadow' x='-20%' y='-20%' width='140%' height='140%'>");
        svgBuilder.append("<feDropShadow dx='1' dy='1' stdDeviation='1' flood-color='rgba(0,0,0,0.2)'/>");
        svgBuilder.append("</filter>");
        
        svgBuilder.append("<style>");
        svgBuilder.append(".node { cursor: pointer; filter: url(#dropShadow); }");
        svgBuilder.append(".node:hover { opacity: 0.8; }");
        svgBuilder.append(".connection { stroke-width: 2; }");
        svgBuilder.append(".text { font-family: 'Segoe UI', Arial, sans-serif; font-size: ").append(FONT_SIZE).append("px; }");
        svgBuilder.append(".title { font-family: 'Segoe UI', Arial, sans-serif; font-size: ").append(TITLE_FONT_SIZE).append("px; font-weight: bold; }");
        svgBuilder.append(".cardinality { font-family: 'Consolas', 'Monaco', monospace; font-size: 10px; }");
        svgBuilder.append("</style>");
        svgBuilder.append("</defs>");

        // Add background
        svgBuilder.append(String.format(
                "<rect width=\"%d\" height=\"%d\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1\"/>",
                layoutInfo.totalWidth, layoutInfo.totalHeight, COLOR_BACKGROUND, COLOR_BORDER
        ));

        // Add title
        svgBuilder.append(String.format(
                "<text x=\"%d\" y=\"%d\" class=\"title\" fill=\"%s\">XSD Schema Diagram</text>",
                START_X, 30, COLOR_TEXT
        ));

        // Draw all elements
        Set<String> drawnElements = new HashSet<>();
        int currentY = START_Y;

        // Draw root elements first
        for (XsdExtendedElement element : xsdDocumentationData.getExtendedXsdElementMap().values()) {
            if (element.getLevel() == 0 && !drawnElements.contains(element.getCurrentXpath())) {
                currentY = drawElement(element, svgBuilder, drawnElements, START_X, currentY, null, layoutInfo);
                currentY += VERTICAL_SPACING;
            }
        }

        // Draw remaining elements
        for (XsdExtendedElement element : xsdDocumentationData.getExtendedXsdElementMap().values()) {
            if (!drawnElements.contains(element.getCurrentXpath())) {
                currentY = drawElement(element, svgBuilder, drawnElements, START_X, currentY, null, layoutInfo);
                currentY += VERTICAL_SPACING;
            }
        }

        // Add zoom and pan JavaScript
        svgBuilder.append("<script type=\"text/javascript\">");
        svgBuilder.append("var svg = document.querySelector('svg');");
        svgBuilder.append("var viewBox = { x: 0, y: 0, width: ").append(layoutInfo.totalWidth).append(", height: ").append(layoutInfo.totalHeight).append(" };");
        svgBuilder.append("var isPanning = false;");
        svgBuilder.append("var startPoint = { x: 0, y: 0 };");
        svgBuilder.append("var endPoint = { x: 0, y: 0 };");
        svgBuilder.append("var scale = 1;");
        svgBuilder.append("function updateViewBox() {");
        svgBuilder.append("  svg.setAttribute('viewBox', viewBox.x + ' ' + viewBox.y + ' ' + viewBox.width + ' ' + viewBox.height);");
        svgBuilder.append("}");
        svgBuilder.append("svg.addEventListener('mousedown', function(e) {");
        svgBuilder.append("  isPanning = true;");
        svgBuilder.append("  startPoint = { x: e.clientX, y: e.clientY };");
        svgBuilder.append("});");
        svgBuilder.append("svg.addEventListener('mousemove', function(e) {");
        svgBuilder.append("  if (isPanning) {");
        svgBuilder.append("    endPoint = { x: e.clientX, y: e.clientY };");
        svgBuilder.append("    var dx = (startPoint.x - endPoint.x) * scale;");
        svgBuilder.append("    var dy = (startPoint.y - endPoint.y) * scale;");
        svgBuilder.append("    viewBox.x += dx;");
        svgBuilder.append("    viewBox.y += dy;");
        svgBuilder.append("    updateViewBox();");
        svgBuilder.append("    startPoint = endPoint;");
        svgBuilder.append("  }");
        svgBuilder.append("});");
        svgBuilder.append("svg.addEventListener('mouseup', function(e) {");
        svgBuilder.append("  isPanning = false;");
        svgBuilder.append("});");
        svgBuilder.append("svg.addEventListener('wheel', function(e) {");
        svgBuilder.append("  e.preventDefault();");
        svgBuilder.append("  var zoom = e.deltaY > 0 ? 0.9 : 1.1;");
        svgBuilder.append("  var mouseX = e.clientX;");
        svgBuilder.append("  var mouseY = e.clientY;");
        svgBuilder.append("  var rect = svg.getBoundingClientRect();");
        svgBuilder.append("  var x = (mouseX - rect.left) / rect.width * viewBox.width + viewBox.x;");
        svgBuilder.append("  var y = (mouseY - rect.top) / rect.height * viewBox.height + viewBox.y;");
        svgBuilder.append("  viewBox.width *= zoom;");
        svgBuilder.append("  viewBox.height *= zoom;");
        svgBuilder.append("  viewBox.x = x - (x - viewBox.x) * zoom;");
        svgBuilder.append("  viewBox.y = y - (y - viewBox.y) * zoom;");
        svgBuilder.append("  scale *= zoom;");
        svgBuilder.append("  updateViewBox();");
        svgBuilder.append("});");
        svgBuilder.append("</script>");

        svgBuilder.append("</svg>");

        return svgBuilder.toString();
    }

    private static class LayoutInfo {
        int totalWidth = 2000;
        int totalHeight = 2000;
        Map<String, Position> elementPositions = new HashMap<>();
    }

    private static class Position {
        int x, y;

        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private LayoutInfo calculateLayout() {
        LayoutInfo layoutInfo = new LayoutInfo();
        Map<String, Integer> elementDepths = new HashMap<>();
        Map<String, Integer> elementWidths = new HashMap<>();

        // Calculate depths and widths for all elements
        for (XsdExtendedElement element : xsdDocumentationData.getExtendedXsdElementMap().values()) {
            calculateElementMetrics(element, elementDepths, elementWidths, new HashSet<>());
        }

        // Find maximum depth and width
        int maxDepth = elementDepths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int maxWidth = elementWidths.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        layoutInfo.totalWidth = Math.max(2000, (maxDepth + 1) * HORIZONTAL_SPACING + 100);
        layoutInfo.totalHeight = Math.max(2000, maxWidth * VERTICAL_SPACING + 200);

        return layoutInfo;
    }

    private void calculateElementMetrics(XsdExtendedElement element, Map<String, Integer> depths, Map<String, Integer> widths, Set<String> visited) {
        if (visited.contains(element.getCurrentXpath())) {
            return;
        }
        visited.add(element.getCurrentXpath());

        int currentDepth = element.getLevel();
        depths.put(element.getCurrentXpath(), currentDepth);
        
        List<String> children = element.getChildren();
        if (children.isEmpty()) {
            widths.put(element.getCurrentXpath(), 1);
        } else {
            int totalWidth = 0;
            for (String childXPath : children) {
                XsdExtendedElement child = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
                if (child != null) {
                    calculateElementMetrics(child, depths, widths, visited);
                    totalWidth += widths.getOrDefault(childXPath, 1);
                }
            }
            widths.put(element.getCurrentXpath(), Math.max(1, totalWidth));
        }

        visited.remove(element.getCurrentXpath());
    }

    private int drawElement(XsdExtendedElement element, StringBuilder svgBuilder, Set<String> drawnElements, int x, int y, XsdExtendedElement parent, LayoutInfo layoutInfo) {
        if (drawnElements.contains(element.getCurrentXpath())) {
            return y;
        }

        drawnElements.add(element.getCurrentXpath());
        layoutInfo.elementPositions.put(element.getCurrentXpath(), new Position(x, y));

        // Determine element visual properties based on type and cardinality
        ElementVisualInfo visualInfo = getElementVisualInfo(element);

        // Draw element box with appropriate styling
        svgBuilder.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"4\" ry=\"4\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\" stroke-dasharray=\"%s\" class=\"node\"/>",
                x - NODE_WIDTH / 2, y - NODE_HEIGHT / 2, NODE_WIDTH, NODE_HEIGHT,
                visualInfo.fillColor, visualInfo.borderColor, visualInfo.borderWidth, visualInfo.dashArray
        ));

        // Draw element name (centered, without icon)
        String elementName = element.getElementName();
        if (elementName.startsWith("@")) {
            elementName = elementName.substring(1); // Remove @ for attributes
        }
        svgBuilder.append(String.format(
                "<text x=\"%d\" y=\"%d\" class=\"title\" fill=\"%s\" text-anchor=\"middle\">%s</text>",
                x, y - 15, visualInfo.textColor, escapeXml(elementName)
        ));

        // Draw data type with icon before it
        String dataType = element.getElementType();
        if (dataType != null && !dataType.isEmpty()) {
            // Calculate text width to center icon + text combination
            int textWidth = dataType.length() * 7; // Approximate character width
            int iconWidth = 16;
            int totalWidth = iconWidth + 5 + textWidth; // icon + spacing + text
            int startX = x - totalWidth / 2;

            // Draw icon before the data type
            drawIcon(svgBuilder, startX, y + 5 - 6, visualInfo.iconPath, visualInfo.iconColor);

            // Draw data type text next to the icon
            svgBuilder.append(String.format(
                    "<text x=\"%d\" y=\"%d\" class=\"text\" fill=\"%s\" text-anchor=\"start\">%s</text>",
                    startX + iconWidth + 5, y + 5, COLOR_TEXT, escapeXml(dataType)
            ));
        }

        // Draw cardinality with proper styling
        String cardinality = getCardinality(element);
        if (!cardinality.isEmpty()) {
            // Draw cardinality background
            int cardWidth = cardinality.length() * 7 + 8;
            svgBuilder.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"16\" rx=\"3\" ry=\"3\" fill=\"#f8f9fa\" stroke=\"#dee2e6\" stroke-width=\"1\"/>",
                    x - cardWidth / 2, y + 12, cardWidth
            ));
            svgBuilder.append(String.format(
                    "<text x=\"%d\" y=\"%d\" class=\"cardinality\" fill=\"%s\" text-anchor=\"middle\">%s</text>",
                    x, y + 23, "#6c757d", cardinality
            ));
        }

        // Draw mandatory/optional indicator
        if (element.isMandatory()) {
            svgBuilder.append(String.format(
                    "<circle cx=\"%d\" cy=\"%d\" r=\"4\" fill=\"%s\"/>",
                    x + NODE_WIDTH / 2 - 10, y - NODE_HEIGHT / 2 + 10, COLOR_MANDATORY
            ));
        } else {
            svgBuilder.append(String.format(
                    "<circle cx=\"%d\" cy=\"%d\" r=\"4\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1\"/>",
                    x + NODE_WIDTH / 2 - 10, y - NODE_HEIGHT / 2 + 10, COLOR_OPTIONAL, COLOR_BORDER
            ));
        }

        // Draw connection to parent with styling based on parent structural type (Sequence/Choice)
        if (parent != null) {
            Position parentPos = layoutInfo.elementPositions.get(parent.getCurrentXpath());
            if (parentPos != null) {
                String connectorColor = COLOR_BORDER;
                String connectorDash = "";
                if ("SEQUENCE".equals(parent.getElementName())) {
                    connectorColor = COLOR_SEQUENCE;
                } else if ("CHOICE".equals(parent.getElementName())) {
                    connectorColor = COLOR_CHOICE;
                    connectorDash = " stroke-dasharray=\"5,5\"";
                }
                svgBuilder.append(String.format(
                        "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"2\" class=\"connection\"%s/>",
                        parentPos.x + NODE_WIDTH / 2, parentPos.y, x - NODE_WIDTH / 2, y, connectorColor, connectorDash
                ));
            }
        }

        // Draw children
        List<String> children = element.getChildren();
        if (!children.isEmpty()) {
            int childX = x + HORIZONTAL_SPACING;
            int childStartY = y - (children.size() - 1) * VERTICAL_SPACING / 2;
            
            for (int i = 0; i < children.size(); i++) {
                XsdExtendedElement child = xsdDocumentationData.getExtendedXsdElementMap().get(children.get(i));
                if (child != null) {
                    int childY = childStartY + i * VERTICAL_SPACING;
                    drawElement(child, svgBuilder, drawnElements, childX, childY, element, layoutInfo);
                }
            }
        }

        // If repeatable, render an inner secondary border (double border effect like REPEATABLE_NODE_LABEL_STYLE)
        boolean isAttribute = element.getElementName().startsWith("@");
        boolean isOptional = isElementOptional(element);
        boolean isRepeatable = isElementRepeatable(element);
        if (!isAttribute && isRepeatable) {
            int inset = 2;
            String secondaryColor = "#87ceeb"; // light blue like in JavaFX style
            svgBuilder.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"4\" ry=\"4\" fill=\"none\" stroke=\"%s\" stroke-width=\"1\"/>",
                    x - NODE_WIDTH / 2 + inset, y - NODE_HEIGHT / 2 + inset, NODE_WIDTH - inset * 2, NODE_HEIGHT - inset * 2, secondaryColor
            ));
        }

        // If optional, apply a dashed outline overlay (to mimic dashed border style)
        if (!isAttribute && isOptional) {
            svgBuilder.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"4\" ry=\"4\" fill=\"none\" stroke=\"%s\" stroke-width=\"2\" stroke-dasharray=\"5,2\"/>",
                    x - NODE_WIDTH / 2, y - NODE_HEIGHT / 2, NODE_WIDTH, NODE_HEIGHT, COLOR_ELEMENT
            ));
        }

        return y + NODE_HEIGHT + VERTICAL_SPACING;
    }

    /**
     * Visual information container for SVG elements
     */
    private static class ElementVisualInfo {
        String fillColor;
        String borderColor;
        String borderWidth;
        String dashArray;
        String textColor;
        String iconPath;
        String iconColor;

        ElementVisualInfo(String fillColor, String borderColor, String borderWidth, String dashArray,
                          String textColor, String iconPath, String iconColor) {
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.borderWidth = borderWidth;
            this.dashArray = dashArray;
            this.textColor = textColor;
            this.iconPath = iconPath;
            this.iconColor = iconColor;
        }
    }

    /**
     * Get comprehensive visual information for an element matching XsdDiagramView styles
     */
    private ElementVisualInfo getElementVisualInfo(XsdExtendedElement element) {
        boolean isAttribute = element.getElementName().startsWith("@");
        boolean isOptional = isElementOptional(element);
        boolean isRepeatable = isElementRepeatable(element);

        String fillColor;
        String borderColor;
        String borderWidth;
        String dashArray = "none";
        String textColor;
        String iconPath;
        String iconColor;

        // Determine element type and get appropriate icon/color
        IconInfo iconInfo = getTypeSpecificIconInfo(element.getElementType());
        iconPath = iconInfo.path;
        iconColor = iconInfo.color;
        
        if (element.getLevel() == 0) {
            // Root element
            fillColor = "url(#elementGrad)";
            borderColor = COLOR_ROOT;
            borderWidth = "2";
            textColor = "#2c5aa0";
        } else if (isAttribute) {
            // Attribute styling
            fillColor = "url(#attributeGrad)";
            borderColor = COLOR_ATTRIBUTE;
            borderWidth = isOptional ? "1" : "1.5";
            textColor = "#8b6914";
            dashArray = isOptional ? "5,2" : "none";
            iconPath = "M8,12H16V14H8V12M10,20H6V4H13V9H18V12.1L20,10.1V8L14,2H6A2,2 0 0,0 4,4V20A2,2 0 0,0 6,22H10V20Z"; // @ symbol path
            iconColor = COLOR_ATTRIBUTE;
        } else if (element.getElementName().equals("SEQUENCE")) {
            // Sequence styling matching XsdDiagramView (bi-list-ol icon)
            fillColor = "url(#sequenceGrad)";
            borderColor = COLOR_SEQUENCE;
            borderWidth = "2";
            textColor = "#495057";
            iconPath = "M2,17H4V19H2V17M2,13H4V15H2V13M2,9H4V11H2V9M2,5H4V7H2V5M7,5H22V7H7V5M7,9H22V11H7V9M7,13H22V15H7V13M7,17H22V19H7V17Z"; // bi-list-ol path
            iconColor = COLOR_SEQUENCE;
        } else if (element.getElementName().equals("CHOICE")) {
            // Choice styling matching XsdDiagramView (bi-option icon)
            fillColor = "url(#choiceGrad)";
            borderColor = COLOR_CHOICE;
            borderWidth = "2";
            textColor = "#b45309";
            dashArray = "5,5";
            iconPath = "M8,10V12H16V10H8M8,14V16H16V14H8M18,8C19.11,8 20,8.9 20,10V14C20,15.11 19.11,16 18,16H16V14H18V10H16V8H18M6,8V10H4V14H6V16H4C2.89,16 2,15.11 2,14V10C2,8.9 2.89,8 4,8H6Z"; // bi-option path
            iconColor = COLOR_CHOICE;
        } else if (element.getElementName().equals("ANY") || element.getElementName().contains("any")) {
            // ANY element styling matching XsdDiagramView (bi-asterisk icon)
            fillColor = "url(#anyGrad)";
            borderColor = COLOR_ANY;
            borderWidth = "1.5";
            textColor = "#6c757d";
            dashArray = "3,3"; // dotted style
            iconPath = "M12,2L13.09,8.26L22,9L13.09,9.74L12,16L10.91,9.74L2,9L10.91,8.26L12,2M12,6.5L11.3,9.5H12.7L12,6.5M8.5,11.5L11.5,12.5L8.5,13.5L11.5,12.5L8.5,11.5M15.5,11.5L12.5,12.5L15.5,13.5L12.5,12.5L15.5,11.5Z"; // bi-asterisk path
            iconColor = COLOR_ANY;
        } else {
            // Regular element styling
            fillColor = "url(#elementGrad)";
            borderColor = COLOR_ELEMENT;
            borderWidth = "2";
            textColor = "#2c5aa0";

            // Apply cardinality-based styling
            if (isOptional && isRepeatable) {
                dashArray = "5,2";
                borderColor = COLOR_ELEMENT + "," + "#87ceeb";
                borderWidth = "2";
            } else if (isOptional) {
                dashArray = "5,2";
            } else if (isRepeatable) {
                borderColor = COLOR_ELEMENT + "," + "#87ceeb";
                borderWidth = "2";
            }
        }

        return new ElementVisualInfo(fillColor, borderColor, borderWidth, dashArray, textColor, iconPath, iconColor);
    }

    /**
     * Icon information container
     */
    private static class IconInfo {
        String path;
        String color;

        IconInfo(String path, String color) {
            this.path = path;
            this.color = color;
        }
    }

    /**
     * Get type-specific icon info matching XsdDiagramView logic
     */
    private IconInfo getTypeSpecificIconInfo(String type) {
        if (type == null || type.isEmpty()) {
            return new IconInfo("M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2M11,16.5L6.5,12L7.91,10.59L11,13.67L16.59,8.09L18,9.5L11,16.5Z", COLOR_DEFAULT_TYPE);
        }

        String cleanType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;

        return switch (cleanType.toLowerCase()) {
            // String types
            case "string", "normalizedstring", "token", "nmtoken", "name", "ncname", "id", "idref", "idrefs",
                 "entity", "entities" ->
                    new IconInfo("M8,15H16V16.5H8V15M8,10.5H16V12H8V10.5M10,6H14V7.5H10V6M4,3A1,1 0 0,0 3,4V20A1,1 0 0,0 4,21H20A1,1 0 0,0 21,20V4A1,1 0 0,0 20,3H4M5,5H19V19H5V5Z", COLOR_STRING_TYPE);

            // Numeric types  
            case "int", "integer", "long", "short", "byte", "positiveinteger", "negativeinteger",
                 "nonpositiveinteger", "nonnegativeinteger", "unsignedlong", "unsignedint",
                 "unsignedshort", "unsignedbyte" ->
                    new IconInfo("M19,13H13V19H11V13H5V11H11V5H13V11H19V13Z", COLOR_NUMERIC_TYPE);

            // Decimal/Float types
            case "decimal", "float", "double" ->
                    new IconInfo("M7,2H17A2,2 0 0,1 19,4V20A2,2 0 0,1 17,22H7A2,2 0 0,1 5,20V4A2,2 0 0,1 7,2M7,4V8H17V4H7M7,10V14H17V10H7M7,16V20H17V16H7Z", COLOR_NUMERIC_TYPE);

            // Date/Time types
            case "date", "datetime", "time", "gyear", "gmonth", "gday", "gyearmonth", "gmonthday", "duration" ->
                    new IconInfo("M19,3H18V1H16V3H8V1H6V3H5A2,2 0 0,0 3,5V19A2,2 0 0,0 5,21H19A2,2 0 0,0 21,19V5A2,2 0 0,0 19,3M19,19H5V8H19V19M5,6V5H19V6H5Z", COLOR_DATE_TYPE);

            // Boolean type
            case "boolean" ->
                    new IconInfo("M10,17L5,12L6.41,10.59L10,14.17L17.59,6.58L19,8M19,3H5A2,2 0 0,0 3,5V19A2,2 0 0,0 5,21H19A2,2 0 0,0 21,19V5A2,2 0 0,0 19,3Z", COLOR_BOOLEAN_TYPE);

            // Binary types
            case "base64binary", "hexbinary" ->
                    new IconInfo("M14,2H6A2,2 0 0,0 4,4V20A2,2 0 0,0 6,22H18A2,2 0 0,0 20,20V8L14,2M18,20H6V4H13V9H18V20Z", COLOR_BINARY_TYPE);

            // URI type
            case "anyuri" ->
                    new IconInfo("M10.59,13.41C11,13.8 11,14.4 10.59,14.81C10.2,15.2 9.6,15.2 9.19,14.81L7.77,13.39L7.77,13.39L6.36,12L7.77,10.61L9.19,9.19C9.6,8.8 10.2,8.8 10.59,9.19C11,9.6 11,10.2 10.59,10.61L9.88,11.32L14.68,11.32L13.97,10.61C13.56,10.2 13.56,9.6 13.97,9.19C14.38,8.8 14.98,8.8 15.39,9.19L16.8,10.61L18.22,12L16.8,13.39L15.39,14.81C14.98,15.2 14.38,15.2 13.97,14.81C13.56,14.4 13.56,13.8 13.97,13.39L14.68,12.68L9.88,12.68L10.59,13.41Z", COLOR_URI_TYPE);

            // QName type
            case "qname" ->
                    new IconInfo("M5.5,7A1.5,1.5 0 0,1 4,5.5A1.5,1.5 0 0,1 5.5,4A1.5,1.5 0 0,1 7,5.5A1.5,1.5 0 0,1 5.5,7M21.41,11.58L12.41,2.58C12.05,2.22 11.55,2 11,2H4C2.9,2 2,2.9 2,4V11C2,11.55 2.22,12.05 2.59,12.41L11.58,21.41C11.95,21.78 12.45,22 13,22C13.55,22 14.05,21.78 14.41,21.41L21.41,14.41C21.78,14.05 22,13.55 22,13C22,12.45 21.78,11.95 21.41,11.58Z", COLOR_QNAME_TYPE);

            // Language type
            case "language" ->
                    new IconInfo("M17.9,17.39C17.64,16.59 16.89,16 16,16H15V13A1,1 0 0,0 14,12H8V10H10A1,1 0 0,0 11,9V7H13A2,2 0 0,0 15,5V4.59C17.93,5.77 20,8.64 20,12C20,14.08 19.2,15.97 17.9,17.39M11,19.93C7.05,19.44 4,16.08 4,12C4,11.38 4.08,10.78 4.21,10.21L9,15V16A2,2 0 0,0 11,18M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z", COLOR_LANGUAGE_TYPE);

            // Complex or custom types
            default -> {
                if (cleanType.endsWith("type") || cleanType.contains("complex")) {
                    yield new IconInfo("M13,9V3.5L18.5,9M6,2C4.89,2 4,2.89 4,4V20A2,2 0 0,0 6,22H18A2,2 0 0,0 20,20V8L14,2H6Z", COLOR_COMPLEX_TYPE);
                } else {
                    yield new IconInfo("M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2M11,16.5L6.5,12L7.91,10.59L11,13.67L16.59,8.09L18,9.5L11,16.5Z", COLOR_DEFAULT_TYPE);
                }
            }
        };
    }

    /**
     * Draw SVG icon using path data
     */
    private void drawIcon(StringBuilder svgBuilder, int x, int y, String iconPath, String iconColor) {
        svgBuilder.append(String.format(
                "<path d=\"%s\" transform=\"translate(%d,%d) scale(0.7,0.7)\" fill=\"%s\"/>",
                iconPath, x, y, iconColor
        ));
    }

    /**
     * Check if element is optional (minOccurs = 0 or use = optional for attributes)
     */
    private boolean isElementOptional(XsdExtendedElement element) {
        Node node = element.getCurrentNode();
        if (node == null) return false;

        if (element.getElementName().startsWith("@")) {
            String use = getAttributeValue(node, "use", "optional");
            return "optional".equals(use);
        } else {
            String minOccurs = getAttributeValue(node, "minOccurs", "1");
            return "0".equals(minOccurs);
        }
    }

    /**
     * Check if element can repeat (maxOccurs > 1 or unbounded)
     */
    private boolean isElementRepeatable(XsdExtendedElement element) {
        Node node = element.getCurrentNode();
        if (node == null) return false;

        if (element.getElementName().startsWith("@")) {
            return false; // Attributes cannot repeat
        }

        String maxOccurs = getAttributeValue(node, "maxOccurs", "1");
        if ("unbounded".equals(maxOccurs)) {
            return true;
        }

        try {
            int max = Integer.parseInt(maxOccurs);
            return max > 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String getCardinality(XsdExtendedElement element) {
        Node node = element.getCurrentNode();
        if (node == null) return "";

        String minOccurs = getAttributeValue(node, "minOccurs", "1");
        String maxOccurs = getAttributeValue(node, "maxOccurs", "1");

        if (element.getElementName().startsWith("@")) {
            String use = getAttributeValue(node, "use", "optional");
            return "required".equals(use) ? "1" : "0..1";
        }

        if ("1".equals(minOccurs) && "1".equals(maxOccurs)) {
            return "1";
        } else if ("0".equals(minOccurs) && "1".equals(maxOccurs)) {
            return "0..1";
        } else if ("unbounded".equals(maxOccurs)) {
            return minOccurs + "..*";
        } else {
            return minOccurs + ".." + maxOccurs;
        }
    }


    private String getAttributeValue(Node node, String attrName, String defaultValue) {
        if (node == null || node.getAttributes() == null) return defaultValue;
        Node attrNode = node.getAttributes().getNamedItem(attrName);
        return (attrNode != null) ? attrNode.getNodeValue() : defaultValue;
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
