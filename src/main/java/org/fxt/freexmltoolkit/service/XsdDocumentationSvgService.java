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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XsdDocumentationSvgService {

    private static final Logger logger = LogManager.getLogger(XsdDocumentationSvgService.class);

    ClassLoaderTemplateResolver resolver;
    TemplateEngine templateEngine;

    private File outputDirectory;

    XsdDocumentationData xsdDocumentationData;

    private final int xOffset = 50;
    private final int ySpacing = 40;
    private final int xSpacing = 200;
    private final int nodeWidth = 150;
    private final int nodeHeight = 30;


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

        final var svgContent = generateSvgContent();
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

    private String generateSvgContent() {
        StringBuilder svgBuilder = new StringBuilder();
        int nextY = 50;

        Set<Node> drawnGlobalElements = new HashSet<>();

        for (XsdExtendedElement element : xsdDocumentationData.getExtendedXsdElementMap().values()) {
            Node domElement = element.getCurrentNode();
            if (domElement != null && xsdDocumentationData.getGlobalElements().contains(domElement) && !drawnGlobalElements.contains(domElement)) {
                Set<String> visitedElementsInTree = new HashSet<>();
                nextY = generateSvgForElement(element, svgBuilder, visitedElementsInTree, xOffset, nextY, -1, -1);
                nextY += ySpacing * 2; // Add vertical space between root element trees
                drawnGlobalElements.add(domElement);
            }
        }

        if (svgBuilder.length() == 0) {
            svgBuilder.append("<text x='10' y='20' fill='red'>No global elements found to generate SVG.</text>");
        }

        return String.format("<svg width=\"8000\" height=\"%d\">%s</svg>", nextY, svgBuilder);
    }

    private int generateSvgForElement(XsdExtendedElement element, StringBuilder svgBuilder, Set<String> visitedElements, int x, int y, int parentX, int parentY) {
        if (visitedElements.contains(element.getCurrentXpath())) {
            svgBuilder.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"red\">Recursion: %s</text>", x, y, element.getElementName()));
            if (parentX != -1) {
                svgBuilder.append(String.format("<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"red\" stroke-dasharray=\"4\" />", parentX + (nodeWidth / 2), parentY, x - (nodeWidth / 2), y));
            }
            return y + ySpacing;
        }

        svgBuilder.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"5\" ry=\"5\" fill=\"#f0f0f0\" stroke=\"#333\" />", x - (nodeWidth / 2), y - (nodeHeight / 2), nodeWidth, nodeHeight));
        svgBuilder.append(String.format("<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" alignment-baseline=\"middle\">%s</text>", x, y, element.getElementName()));

        if (parentX != -1) {
            svgBuilder.append(String.format("<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"black\" />", parentX + (nodeWidth / 2), parentY, x - (nodeWidth / 2), y));
        }

        visitedElements.add(element.getCurrentXpath());

        int childX = x + xSpacing;
        int nextAvailableY = y;
        List<String> children = element.getChildren();

        if (!children.isEmpty()) {
            int childrenTotalHeight = 0;
            int[] childrenHeights = new int[children.size()];

            // First pass: calculate heights of all children subtrees
            for (int i = 0; i < children.size(); i++) {
                XsdExtendedElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(children.get(i));
                if (childElement != null) {
                    // A temporary, non-drawing run to calculate height
                    int height = calculateHeight(childElement, new HashSet<>(visitedElements));
                    childrenHeights[i] = height;
                    childrenTotalHeight += height;
                }
            }

            int childStartY = y - (childrenTotalHeight / 2) + (children.size() > 1 ? ySpacing / 2 : 0);
            nextAvailableY = childStartY;

            // Second pass: draw the children
            for (int i = 0; i < children.size(); i++) {
                XsdExtendedElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(children.get(i));
                if (childElement != null) {
                    int childY = nextAvailableY + (childrenHeights[i] / 2);
                    generateSvgForElement(childElement, svgBuilder, visitedElements, childX, childY, x, y);
                    nextAvailableY += childrenHeights[i];
                }
            }
        }

        visitedElements.remove(element.getCurrentXpath());
        return Math.max(y + nodeHeight, nextAvailableY);
    }

    private int calculateHeight(XsdExtendedElement element, Set<String> visitedElements) {
        if (visitedElements.contains(element.getCurrentXpath())) {
            return ySpacing;
        }
        visitedElements.add(element.getCurrentXpath());

        List<String> children = element.getChildren();
        if (children.isEmpty()) {
            return ySpacing;
        }

        int totalHeight = 0;
        for (String childXPath : children) {
            XsdExtendedElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
            if (childElement != null) {
                totalHeight += calculateHeight(childElement, visitedElements);
            }
        }
        visitedElements.remove(element.getCurrentXpath());
        return totalHeight;
    }
}
