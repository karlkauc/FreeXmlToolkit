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

    // Colors
    private final String COLOR_ROOT = "#4A90E2";
    private final String COLOR_ELEMENT = "#7ED321";
    private final String COLOR_ATTRIBUTE = "#F5A623";
    private final String COLOR_COMPLEX_TYPE = "#9013FE";
    private final String COLOR_SIMPLE_TYPE = "#50E3C2";
    private final String COLOR_OPTIONAL = "#FF6B6B";
    private final String COLOR_MANDATORY = "#4CAF50";
    private final String COLOR_BORDER = "#333333";
    private final String COLOR_TEXT = "#2C3E50";
    private final String COLOR_BACKGROUND = "#FFFFFF";

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

        // Add zoom and pan functionality
        svgBuilder.append("<defs>");
        svgBuilder.append("<style>");
        svgBuilder.append(".node { cursor: pointer; }");
        svgBuilder.append(".node:hover { opacity: 0.8; }");
        svgBuilder.append(".connection { stroke-width: 2; }");
        svgBuilder.append(".text { font-family: Arial, sans-serif; font-size: ").append(FONT_SIZE).append("px; }");
        svgBuilder.append(".title { font-family: Arial, sans-serif; font-size: ").append(TITLE_FONT_SIZE).append("px; font-weight: bold; }");
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

        // Determine element color based on type
        String elementColor = getElementColor(element);

        // Draw element box
        svgBuilder.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"8\" ry=\"8\" fill=\"%s\" stroke=\"%s\" stroke-width=\"2\" class=\"node\"/>",
                x - NODE_WIDTH / 2, y - NODE_HEIGHT / 2, NODE_WIDTH, NODE_HEIGHT, elementColor, COLOR_BORDER
        ));

        // Draw element name
        String elementName = element.getElementName();
        if (elementName.startsWith("@")) {
            elementName = elementName.substring(1); // Remove @ for attributes
        }
        svgBuilder.append(String.format(
                "<text x=\"%d\" y=\"%d\" class=\"title\" fill=\"%s\" text-anchor=\"middle\">%s</text>",
                x, y - 15, COLOR_TEXT, escapeXml(elementName)
        ));

        // Draw data type
        String dataType = element.getElementType();
        if (dataType != null && !dataType.isEmpty()) {
            svgBuilder.append(String.format(
                    "<text x=\"%d\" y=\"%d\" class=\"text\" fill=\"%s\" text-anchor=\"middle\">Type: %s</text>",
                    x, y + 5, COLOR_TEXT, escapeXml(dataType)
            ));
        }

        // Draw cardinality
        String cardinality = getCardinality(element);
        if (!cardinality.isEmpty()) {
            svgBuilder.append(String.format(
                    "<text x=\"%d\" y=\"%d\" class=\"text\" fill=\"%s\" text-anchor=\"middle\">%s</text>",
                    x, y + 20, getCardinalityColor(element), cardinality
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

        // Draw connection to parent
        if (parent != null) {
            Position parentPos = layoutInfo.elementPositions.get(parent.getCurrentXpath());
            if (parentPos != null) {
                svgBuilder.append(String.format(
                        "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"2\" class=\"connection\"/>",
                        parentPos.x + NODE_WIDTH / 2, parentPos.y, x - NODE_WIDTH / 2, y, COLOR_BORDER
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

        return y + NODE_HEIGHT + VERTICAL_SPACING;
    }

    private String getElementColor(XsdExtendedElement element) {
        if (element.getLevel() == 0) {
            return COLOR_ROOT;
        } else if (element.getElementName().startsWith("@")) {
            return COLOR_ATTRIBUTE;
        } else if (element.getElementType() != null && element.getElementType().contains("complexType")) {
            return COLOR_COMPLEX_TYPE;
        } else if (element.getElementType() != null && element.getElementType().contains("simpleType")) {
            return COLOR_SIMPLE_TYPE;
        } else {
            return COLOR_ELEMENT;
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

    private String getCardinalityColor(XsdExtendedElement element) {
        return element.isMandatory() ? COLOR_MANDATORY : COLOR_OPTIONAL;
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
