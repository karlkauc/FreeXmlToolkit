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

package org.fxt.freexmltoolkit;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SVGTest {

    private final static Logger logger = LogManager.getLogger(SVGTest.class);

    final XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();

    File outputDir = new File("output//svg//");

    @Test
    public void generateTemplate() {
        xsdDocumentationService.setXsdFilePath("src/test/resources/FundsXML_306.xsd");
        xsdDocumentationService.generateXsdDocumentation(outputDir);
    }

    @Test
    public void createImage() throws TranscoderException, IOException {
        DOMImplementation impl = SVGDOMImplementation.getDOMImplementation();
        String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
        Document document = impl.createDocument(svgNS, "svg", null);
        Element root = document.getDocumentElement();
        root.setAttributeNS(null, "width", "450");
        root.setAttributeNS(null, "height", "500");

        Element e;
        e = document.createElementNS(svgNS, "rect");
        e.setAttributeNS(null, "x", "10");
        e.setAttributeNS(null, "y", "10");
        e.setAttributeNS(null, "width", "200");
        e.setAttributeNS(null, "height", "300");
        e.setAttributeNS(null, "style", "fill:red;stroke:black;stroke-width:4");
        root.appendChild(e);

        e = document.createElementNS(svgNS, "circle");
        e.setAttributeNS(null, "cx", "225");
        e.setAttributeNS(null, "cy", "250");
        e.setAttributeNS(null, "r", "100");
        e.setAttributeNS(null, "style", "fill:green;fill-opacity:.5");
        root.appendChild(e);

        System.out.println("document = " + document);

        var t = new PNGTranscoder();
        TranscoderInput input = new TranscoderInput(document);
        OutputStream outputStream = new FileOutputStream("out.png");
        TranscoderOutput output = new TranscoderOutput(outputStream);
        t.transcode(input, output);
        outputStream.flush();
        outputStream.close();
    }

    @Test
    public void createPngFromBlank() {
        xsdDocumentationService.setXsdFilePath("src/test/resources/purchageOrder.xsd");
        xsdDocumentationService.setMethod(XsdDocumentationService.ImageOutputMethod.PNG);
        xsdDocumentationService.generateXsdDocumentation(outputDir);
    }
}
