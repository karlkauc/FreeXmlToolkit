package org.fxt.freexmltoolkit;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.svggen.SVGGraphics2DIOException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class SVGTest {

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

}
