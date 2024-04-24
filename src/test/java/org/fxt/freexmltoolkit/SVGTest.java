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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SVGTest {

    private final static Logger logger = LogManager.getLogger(SVGTest.class);

    final XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();

    File outputDir = new File("output//svg//");

    @Test
    public void generateTemplate() throws IOException {
        logger.debug("DRINNEN!!!");

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

    @Test
    public void createFromString() {
        String xmlString = """
                <svg xmlns="http://www.w3.org/2000/svg"
                     width="252.0"
                     height="215.1953125"
                     style="background-color: rgb(235, 252, 241)">
                    <rect x="20"
                          y="78"
                          fill="#d5e3e8"
                          width="62.0"
                          rx="2"
                          ry="2"
                          height="38.3984375"
                          id="BillTo"/>
                    <text x="30"
                          fill="#096574"
                          y="101.3984375"
                          font-size="16"
                          font-family="ArialMT"
                          textLength="0">BillTo</text>
                  <text xmlns=""
                        x="137.0"
                        fill="#096574"
                        y="35.0"
                        font-size="14"
                        font-family="ArialMT"
                        textLength="0">1:1</text>
                  <rect xmlns=""
                        x="162.0"
                        y="20.0"
                        fill="#d5e3e8"
                        width="60.0"
                        rx="2"
                        ry="2"
                        height="38.3984375"
                        id="name"/>
                  <text xmlns=""
                        x="172.0"
                        fill="#096574"
                        y="43.3984375"
                        font-size="16"
                        font-family="ArialMT"
                        textLength="0">name</text>
                  <path xmlns=""
                        fill="none"
                        d="M 82.0 96.3984375 h 40 V 39.19921875 h 40"/>
                  <text xmlns=""
                        x="137.0"
                        fill="#096574"
                        y="93.3984375"
                        font-size="14"
                        font-family="ArialMT"
                        textLength="0">1:1</text>
                  <rect xmlns=""
                        x="162.0"
                        y="78.3984375"
                        fill="#d5e3e8"
                        width="60.0"
                        rx="2"
                        ry="2"
                        height="38.3984375"
                        id="street"/>
                  <text xmlns=""
                        x="172.0"
                        fill="#096574"
                        y="101.796875"
                        font-size="16"
                        font-family="ArialMT"
                        textLength="0">street</text>
                  <path xmlns=""
                        fill="none"
                        d="M 82.0 96.3984375 h 40 V 97.59765625 h 40"/>
                  <text xmlns=""
                        x="137.0"
                        fill="#096574"
                        y="151.796875"
                        font-size="14"
                        font-family="ArialMT"
                        textLength="0">1:1</text>
                  <rect xmlns=""
                        x="162.0"
                        y="136.796875"
                        fill="#d5e3e8"
                        width="60.0"
                        rx="2"
                        ry="2"
                        height="38.3984375"
                        id="city"/>
                  <text xmlns=""
                        x="172.0"
                        fill="#096574"
                        y="160.1953125"
                        font-size="16"
                        font-family="ArialMT"
                        textLength="0">city</text>
                  <path xmlns=""
                        fill="none"
                        d="M 82.0 96.3984375 h 40 V 155.99609375 h 40"/>
                </svg>""";

        try {
            TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(xmlString.getBytes()));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            TranscoderOutput output = new TranscoderOutput(outputStream);

            var transcoder = new PNGTranscoder();
            transcoder.transcode(input, output);
            Files.write(Paths.get("output2.png"), outputStream.toByteArray());

        } catch (IOException | TranscoderException ioException) {
            logger.error(ioException.getMessage());
        }
    }

}
