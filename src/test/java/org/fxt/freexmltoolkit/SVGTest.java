/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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

package org.fxt.freexmltoolkit;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.extendedXsd.ExtendedXsdElement;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class SVGTest {

    private final static Logger logger = LogManager.getLogger(SVGTest.class);

    final XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();

    final int margin = 10;
    final int gapBetweenSides = 100;

    final static String BOX_COLOR = "#d5e3e8";
    final static String OPTIONAL_FORMAT = "stroke: rgb(2,23,23); stroke-width: 2; stroke-dasharray: 7, 7;";
    final static String MANDATORY_FORMAT = "stroke: rgb(2,23,23); stroke-width: 2;";

    public String generateSVGDiagram(String rootXpath) {
        Font font = new Font("Arial", Font.PLAIN, 16);
        FontRenderContext frc = new FontRenderContext(
                null,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
                RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);

        DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
        final String svgNS = "http://www.w3.org/2000/svg";
        Document document = domImpl.createDocument(svgNS, "svg", null);
        var svgRoot = document.getDocumentElement();

        // ToDo: größe automatisch anpassen
        svgRoot.setAttributeNS(svgNS, "width", "800");
        svgRoot.setAttributeNS(svgNS, "height", "800");
        svgRoot.setAttributeNS(svgNS, "style", "background-color: rgb(245, 235, 213)");

        var rootElement = xsdDocumentationService.getExtendedXsdElements().get(rootXpath);
        var rootElementName = rootElement.getElementName();
        System.out.println("rootElementName = " + rootElementName);

        java.util.List<ExtendedXsdElement> childElements = new ArrayList<>();
        for (String temp : rootElement.getChildren()) {
            childElements.add(xsdDocumentationService.getExtendedXsdElements().get(temp));
        }

        double rightBoxHeight = 20;
        double rightBoxWidth = 0;

        for (ExtendedXsdElement r : childElements) {
            String elementName = "";

            if (r != null && r.getXsdElement() != null) {
                elementName = r.getXsdElement().getName();
            }
            System.out.println("Element Name = " + elementName);

            var z = font.getStringBounds(elementName, frc);
            var height = z.getBounds2D().getHeight();
            var width = z.getBounds2D().getWidth();

            rightBoxHeight = rightBoxHeight + margin + height + margin + 20; // inkl. 20 abstand zwischen boxen
            rightBoxWidth = Math.max(rightBoxWidth, width);
        }

        System.out.println("height = " + rightBoxHeight);
        System.out.println("width = " + rightBoxWidth);

        // erstes Element - Abstände Berechnen
        var z = font.getStringBounds(rootElementName, frc);
        var rootElementHeight = z.getBounds2D().getHeight();
        var rootElementWidth = z.getBounds2D().getWidth();
        // root node sollte genau in der mitte der rechten boxen sein.
        // also rightBoxHeight / 2 minus boxgröße / 2

        int startX = 20;
        int startY = (int) ((rightBoxHeight / 2) - ((margin + rootElementHeight + margin) / 2));

        System.out.println("startY = " + startY);

        Element rect1 = document.createElement("rect");
        rect1.setAttribute("fill", BOX_COLOR);
        rect1.setAttribute("id", rootElementName);
        rect1.setAttribute("height", (margin + rootElementHeight + margin) + "");
        rect1.setAttribute("width", (margin + rootElementWidth + margin) + "");
        rect1.setAttribute("x", startX + "");
        rect1.setAttribute("y", startY + "");
        rect1.setAttribute("rx", "2");
        rect1.setAttribute("ry", "2");
        rect1.setAttribute("style", "stroke: rgb(2,23,23); stroke-width: 2;");
        svgRoot.appendChild(rect1);

        Element text = document.createElement("text");
        text.setAttribute("fill", "#096574");
        text.setAttribute("font-family", font.getFontName());
        text.setAttribute("font-size", font.getSize() + "");
        text.setAttribute("textLength", "0");
        text.setAttribute("x", margin + startX + "");
        text.setAttribute("y", startY + rootElementHeight + (margin / 2) + "");
        text.setTextContent(rootElementName);
        svgRoot.appendChild(text);

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

            if (childElement != null && childElement.getXsdElement() != null) {
                System.out.println("childElement.getXsdElement().getMaxOccurs() = " + childElement.getXsdElement().getMaxOccurs());
                System.out.println("childElement.getXsdElement().getMinOccurs() = " + childElement.getXsdElement().getMinOccurs());

                if (childElement.getXsdElement().getMinOccurs() > 0) {
                    css = MANDATORY_FORMAT;
                }
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

            Element text2 = document.createElement("text");
            text2.setAttribute("fill", "#096574");
            text2.setAttribute("font-family", font.getFontName());
            text2.setAttribute("font-size", font.getSize() + "");
            text2.setAttribute("textLength", "0");
            text2.setAttribute("x", rightStartX + margin + "");
            text2.setAttribute("y", actualHeight + height + (margin / 2) + "");
            text2.setTextContent(elementName);

            Element a = document.createElement("a");
            if (childElement != null && childElement.getChildren() != null && !childElement.getChildren().isEmpty()) {
                // link erstellen
                a.setAttribute("href", childElement.getPageName());
                a.appendChild(rect2);
                a.appendChild(text2);

                svgRoot.appendChild(a);
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
            path2.setAttribute("style", css);
            svgRoot.appendChild(path2);

            actualHeight = actualHeight + margin + height + margin + 20; // 20 pixel abstand zwischen boxen
        }

        return asString(svgRoot);
    }

    private String asString(Node node) {
        StringWriter writer = new StringWriter();
        try {
            Transformer trans = TransformerFactory.newInstance().newTransformer();
            // @checkstyle MultipleStringLiterals (1 line)
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

    @Test
    public void generateTemplate() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/");
        resolver.setSuffix(".html");

        var templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        xsdDocumentationService.setXsdFilePath("src/test/resources/FundsXML_306.xsd");
        xsdDocumentationService.processXsd();

        final var xsdSchema = xsdDocumentationService.getXmlSchema();
        final var complexTypes = xsdDocumentationService.getXsdComplexTypes();
        final var simpleTypes = xsdDocumentationService.getXsdSimpleTypes();
        final var elements = xsdDocumentationService.getElements();

        for (String key : xsdDocumentationService.getExtendedXsdElements().keySet()) {
            var currentElement = xsdDocumentationService.getExtendedXsdElements().get(key);

            String svgDiagram = generateSVGDiagram(key);

            var context = new Context();
            context.setVariable("var", svgDiagram);
            context.setVariable("xpath", currentElement.getCurrentXpath());

            final var result = templateEngine.process("svgTemplate", context);
            final var outputFileName = "output//svg//" + xsdDocumentationService.getExtendedXsdElements().get(key).getPageName();

            try {
                Files.write(Paths.get(outputFileName), result.getBytes());
                logger.debug("Written {} bytes", new File(outputFileName).length());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
