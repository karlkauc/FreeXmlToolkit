package org.fxt.freexmltoolkit;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.svggen.SVGGraphics2DIOException;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.awt.geom.Ellipse2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SVGTest {

    private final static Logger logger = LogManager.getLogger(SVGTest.class);

    @Test
    public void t() throws UnsupportedEncodingException, SVGGraphics2DIOException {
        System.out.println("TEST");

        // Get a DOMImplementation.
        DOMImplementation domImpl =
                GenericDOMImplementation.getDOMImplementation();

        // Create an instance of org.w3c.dom.Document.
        String svgNS = "http://www.w3.org/2000/svg";
        Document document = domImpl.createDocument(svgNS, "svg", null);

        // Create an instance of the SVG Generator.
        SVGGraphics2D svgGenerator = new SVGGraphics2D(document);

        Shape circle = new Ellipse2D.Double(10, 20, 50, 100);
        svgGenerator.setPaint(Color.red);
        svgGenerator.draw(circle);
        svgGenerator.setBackground(Color.BLUE);

        var text = document.createTextNode("this is a test");

        document.getDocumentElement().appendChild(text);

        boolean useCSS = true;
        Writer out = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        svgGenerator.stream(out, useCSS);

        svgGenerator.stream("file.svg", true);
    }

    public String getBatikTest() throws SVGGraphics2DIOException, TranscoderException {
        DOMImplementation domImpl = SVGDOMImplementation.getDOMImplementation();

        String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
        Document document = domImpl.createDocument(svgNS, "svg", null);
        var svgRoot = document.getDocumentElement();

        svgRoot.setAttributeNS(svgNS, "width", "800");
        svgRoot.setAttributeNS(svgNS, "height", "800");

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

        String t = asString(svgRoot);
        System.out.println("t = " + t);

        return t;
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
    public void generateTemplate() throws SVGGraphics2DIOException, TranscoderException {
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

        var = getBatikTest();

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
