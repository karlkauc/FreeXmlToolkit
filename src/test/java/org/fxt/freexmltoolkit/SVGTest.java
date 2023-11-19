package org.fxt.freexmltoolkit;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2DIOException;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.xmlet.xsdparser.xsdelements.XsdElement;
import org.xmlet.xsdparser.xsdelements.elementswrapper.ReferenceBase;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SVGTest {

    private final static Logger logger = LogManager.getLogger(SVGTest.class);

    final XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();

    final int margin = 10;
    final int gapBetweenSides = 100;

    public String testBoxes() {
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

        xsdDocumentationService.setXsdFilePath("src/test/resources/simpleFile.xsd");
        xsdDocumentationService.processXsd();

        var xsdSchema = xsdDocumentationService.getXmlSchema();
        var complexTypes = xsdDocumentationService.getXsdComplexTypes();
        var simpleTypes = xsdDocumentationService.getXsdSimpleTypes();
        var elements = xsdDocumentationService.getElements();

        var rootElementName = elements.get(0).getName();
        System.out.println("rootElementName = " + rootElementName);
        String elementType = elements.get(0).getType();
        var compexType = elements.get(0).getTypeAsComplexType();
        var childElements = elements.get(0).getTypeAsComplexType().getElements();

        double rightBoxHeight = 20;
        double rightBoxWidth = 0;

        for (ReferenceBase r : childElements) {
            var elementName = ((XsdElement) r.getElement()).getName();
            System.out.println("Element Name = " + elementName);

            var z = font.getStringBounds(elementName, frc);
            var height = z.getBounds2D().getHeight();
            var width = z.getBounds2D().getWidth();

            rightBoxHeight = rightBoxHeight + margin + height + margin + 20; // inkl. 20 abstand zwischen boxen
            rightBoxWidth = Math.max(rightBoxWidth, width);
        }

        int childElementsAmount = childElements.size();

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
        rect1.setAttribute("fill", "#F2F2F2");
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
        for (ReferenceBase r : childElements) {
            var elementName = ((XsdElement) r.getElement()).getName();
            System.out.println("Element Name = " + elementName);

            var z2 = font.getStringBounds(elementName, frc);
            var height = z2.getBounds2D().getHeight();
            var width = z2.getBounds2D().getWidth();

            // box zeichnen und Text Einfügen
            Element rect2 = document.createElement("rect");
            rect2.setAttribute("fill", "#F2F2F2");
            rect2.setAttribute("id", elementName);
            rect2.setAttribute("height", (margin + height + margin) + "");
            rect2.setAttribute("width", (margin + rightBoxWidth + margin) + "");
            rect2.setAttribute("x", rightStartX + "");
            rect2.setAttribute("y", actualHeight + "");
            rect2.setAttribute("rx", "2");
            rect2.setAttribute("ry", "2");
            rect2.setAttribute("style", "stroke: rgb(2,23,23); stroke-width: 2;");
            svgRoot.appendChild(rect2);

            Element text2 = document.createElement("text");
            text2.setAttribute("fill", "#096574");
            text2.setAttribute("font-family", font.getFontName());
            text2.setAttribute("font-size", font.getSize() + "");
            text2.setAttribute("textLength", "0");
            text2.setAttribute("x", rightStartX + margin + "");
            text2.setAttribute("y", actualHeight + height + (margin / 2) + "");
            text2.setTextContent(elementName);
            svgRoot.appendChild(text2);

            Element path2 = document.createElement("path");
            path2.setAttribute("d", "M " + pathStartX + " " + pathStartY +
                    " h " + ((gapBetweenSides / 2) - margin) +
                    " V " + (actualHeight + ((margin + height + margin) / 2)) +
                    " h " + ((gapBetweenSides / 2) - margin));
            path2.setAttribute("fill", "none");
            path2.setAttribute("style", "stroke: black; stroke-width: 2;");
            svgRoot.appendChild(path2);

            actualHeight = actualHeight + margin + height + margin + 20; // 20 pixel abstand zwischen boxen
        }


        return asString(svgRoot);
    }

    public String getBatikTest() {
        DOMImplementation domImpl = SVGDOMImplementation.getDOMImplementation();

        String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
        Document document = domImpl.createDocument(svgNS, "svg", null);
        var svgRoot = document.getDocumentElement();

        svgRoot.setAttributeNS(svgNS, "width", "800");
        svgRoot.setAttributeNS(svgNS, "height", "800");
        svgRoot.setAttributeNS(svgNS, "style", "background-color:red");

        final String output = "FundsXML4";

        Font font = new Font("Arial", Font.PLAIN, 16);
        FontRenderContext frc = new FontRenderContext(
                null,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
                RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
        var z = font.getStringBounds(output, frc);
        var height = z.getBounds2D().getHeight();
        var width = z.getBounds2D().getWidth();

        System.out.println("height = " + height);
        System.out.println("width = " + width);

        final int margin = 10;

        int boxWidth = (int) (margin + width + margin);
        int boxHeight = (int) (margin + height + margin);

        Element rect1 = document.createElement("rect");
        rect1.setAttribute("fill", "#F2F2F2");
        rect1.setAttribute("id", "a");
        rect1.setAttribute("height", boxHeight + "");
        rect1.setAttribute("width", boxWidth + "");
        rect1.setAttribute("x", "10");
        rect1.setAttribute("y", "140");
        rect1.setAttribute("rx", "2");
        rect1.setAttribute("ry", "2");
        rect1.setAttribute("style", "stroke: rgb(2,23,23); stroke-width: 2;");
        svgRoot.appendChild(rect1);

        Element text = document.createElement("text");
        text.setAttribute("fill", "#096574");
        text.setAttribute("font-family", font.getFontName());
        text.setAttribute("font-size", font.getSize() + "");
        text.setAttribute("textLength", "0");
        text.setAttribute("x", margin + margin + "");
        text.setAttribute("y", String.valueOf(140 + ((double) margin / 2) + height));
        text.setTextContent(output);

        svgRoot.appendChild(text);
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
    public void generateTemplate() throws SVGGraphics2DIOException, TranscoderException, UnsupportedEncodingException {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/");
        resolver.setSuffix(".html");

        var templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        String var = """
                <g>
                        <rect fill="#F2F2F2" filter="url(#f13f4r0vdneneo)" height="30"
                              id="a"
                              width="200"
                              x="0"
                              y="140"
                              rx="2"
                              ry="2"
                              style="stroke: rgb(2,23,23); stroke-width: 2;"></rect>
                        <text fill="#096574" font-family="Arial" font-size="13" textLength="0" x="10" y="160">FundsXML4</text>
                    
                        <rect fill="#F2F2F2" filter="url(#f13f4r0vdneneo)" height="40"
                              id="fund"
                              rx="2"
                              ry="2"
                              x="300"
                              y="60"
                              width="200"
                              style="stroke: rgb(2,23,23); stroke-width: 2; stroke-dasharray: 7, 7;"></rect>
                    
                        <rect fill="#F2F2F2" filter="url(#f13f4r0vdneneo)" height="40"
                              id="controlData"
                              rx="2"
                              ry="2"
                              x="300"
                              y="10"
                              width="200"
                              style="stroke: rgb(2,23,23); stroke-width: 2;"></rect>
                    
                        <rect fill="#F2F2F2" filter="url(#f13f4r0vdneneo)" height="40"
                              id="AssetMgntCompanyData"
                              rx="2"
                              ry="2"
                              x="300"
                              y="110"
                              width="200"
                              style="stroke: rgb(2,23,23); stroke-width: 2; stroke-dasharray: 7, 7;"></rect>
                    
                        <rect fill="#F2F2F2" filter="url(#f13f4r0vdneneo)" height="40"
                              id="Documents"
                              rx="2"
                              ry="2"
                              x="300"
                              y="160"
                              width="200"
                              style="stroke: rgb(2,23,23); stroke-width: 2; stroke-dasharray: 7, 7;"></rect>
                    
                        <rect fill="#F2F2F2" filter="url(#f13f4r0vdneneo)" height="40"
                              id="RegulatoryReporting"
                              rx="2"
                              ry="2"
                              x="300"
                              y="210"
                              width="200"
                              style="stroke: rgb(2,23,23); stroke-width: 2; stroke-dasharray: 7, 7;"></rect>
                    
                        <rect fill="#F2F2F2" filter="url(#f13f4r0vdneneo)" height="40"
                              id="ContrySpecificData"
                              rx="2"
                              ry="2"
                              x="300"
                              y="260"
                              width="200"
                              style="stroke: rgb(2,23,23); stroke-width: 2; stroke-dasharray: 7, 7;"></rect>
                    
                        <path d="M 200 150 h 20 v -120 h 80" fill="none"
                              id="a"
                              style="stroke: rgb(229,41,41); stroke-width: 2;"></path>
                    
                        <!--fundsxml 2 fund -->
                        <path d="M 200 152 h 23 v -70 h 77 " fill="none"
                              id="a"
                              style="stroke: rgb(41,213,229); stroke-width: 2;"></path>
                    
                    
                        <text fill="#096574" font-family="Arial" font-size="13"
                              textLength="0" x="310" y="85">Funds
                        </text>
                        <text fill="#096574" font-family="Arial" font-size="13"
                              textLength="0" x="310" y="35">Control Data
                        </text>
                        <text fill="#096574" font-family="Arial" font-size="13"
                              textLength="0" x="310" y="135">AssetMgntCompanyData
                        </text>
                        <text fill="#096574" font-family="Arial" font-size="13"
                              textLength="0" x="310" y="185">Documents
                        </text>
                        <text fill="#096574" font-family="Arial" font-size="13"
                              textLength="0" x="310" y="235">RegulatoryReporting
                        </text>
                        <text fill="#096574" font-family="Arial" font-size="13"
                              textLength="0" x="310" y="285">ContrySpecificData
                        </text>
                    </g>""";

        var = testBoxes();

        var context = new Context();
        context.setVariable("var", var);

        var result = templateEngine.process("svgTemplate", context);

        var outputFileName = "index_svg.html";

        try {
            Files.write(Paths.get(outputFileName), result.getBytes());

            logger.debug("Written {} bytes", new File(outputFileName).length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
