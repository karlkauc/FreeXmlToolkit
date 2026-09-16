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

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests that nested compositor symbols (like CHOICE in SEQUENCE) in XSD documentation
 * SVG diagrams have outgoing connection lines starting precisely at the right edge of the symbol.
 */
class XsdDocumentationNestedChoiceSvgTest {

    private XsdDocumentationService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new XsdDocumentationService();
    }

    @Test
    void testNestedChoiceConnectionStartsAtChoiceRightEdge() throws Exception {
        // Schema mirroring the FundsXML Fund structure with sequence -> choice -> SingleFund / Subfunds
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
                    <xs:complexType name="FundType">
                        <xs:sequence>
                            <xs:element name="Names" type="xs:string"/>
                            <xs:choice>
                                <xs:element name="SingleFund" type="xs:string"/>
                                <xs:element name="Subfunds" type="xs:string"/>
                            </xs:choice>
                        </xs:sequence>
                    </xs:complexType>
                    <xs:element name="Fund" type="FundType"/>
                </xs:schema>
                """;

        Path xsdFile = tempDir.resolve("fund-choice.xsd");
        Files.writeString(xsdFile, xsdContent);

        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        Map<String, XsdExtendedElement> elements = service.xsdDocumentationData.getExtendedXsdElementMap();
        XsdExtendedElement fundElement = null;
        for (XsdExtendedElement el : elements.values()) {
            if ("Fund".equals(el.getElementName()) && el.getLevel() == 0) {
                fundElement = el;
                break;
            }
        }
        assertNotNull(fundElement, "Fund element must exist");

        XsdDocumentationImageService imageService = new XsdDocumentationImageService(elements);
        String svg = imageService.generateSvgString(fundElement);

        assertNotNull(svg, "SVG string should not be null");
        assertTrue(svg.contains("<svg"), "Output should contain SVG");

        // Parse generated SVG to verify choice rectangle and child connection path coordinates
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));

        // Find the choice rectangle: stroke="#ff8c00" and width="30.0" (or "30")
        NodeList rects = doc.getElementsByTagName("rect");
        Element choiceRect = null;
        for (int i = 0; i < rects.getLength(); i++) {
            Element r = (Element) rects.item(i);
            String stroke = r.getAttribute("stroke");
            String width = r.getAttribute("width");
            if ("#ff8c00".equalsIgnoreCase(stroke) && (width.startsWith("30"))) {
                choiceRect = r;
                break;
            }
        }
        assertNotNull(choiceRect, "Choice rectangle with orange stroke (#ff8c00) and width 30 should exist");

        double choiceX = Double.parseDouble(choiceRect.getAttribute("x"));
        double choiceWidth = Double.parseDouble(choiceRect.getAttribute("width"));
        double choiceRightEdge = choiceX + choiceWidth;
        double choiceY = Double.parseDouble(choiceRect.getAttribute("y"));
        double choiceHeight = Double.parseDouble(choiceRect.getAttribute("height"));
        double choiceCenterY = choiceY + choiceHeight / 2.0;

        // Find outgoing connection paths with stroke="#ff8c00" (connecting choice to SingleFund and Subfunds)
        NodeList paths = doc.getElementsByTagName("path");
        int outgoingChoicePathCount = 0;
        for (int i = 0; i < paths.getLength(); i++) {
            Element p = (Element) paths.item(i);
            String stroke = p.getAttribute("stroke");
            String d = p.getAttribute("d");
            if ("#ff8c00".equalsIgnoreCase(stroke) && d != null && !d.isEmpty()) {
                // Check if this path starts from the choice element (M <startX> <startY>)
                Matcher matcher = Pattern.compile("^M\\s+([0-9.]+)\\s+([0-9.]+)").matcher(d);
                if (matcher.find()) {
                    double startX = Double.parseDouble(matcher.group(1));
                    double startY = Double.parseDouble(matcher.group(2));
                    // If startX matches choice area (around choiceRightEdge), verify it is EXACTLY choiceRightEdge
                    if (Math.abs(startX - choiceRightEdge) < 30) {
                        outgoingChoicePathCount++;
                        assertEquals(choiceRightEdge, startX, 0.001,
                                "Connection line from CHOICE must start EXACTLY at the right edge of the CHOICE rectangle ("
                                        + choiceRightEdge + "), but started at " + startX + " (gap: " + (startX - choiceRightEdge) + ")");
                        assertEquals(choiceCenterY, startY, 0.001,
                                "Connection line from CHOICE must start at the vertical center of the CHOICE rectangle");
                    }
                }
            }
        }

        assertTrue(outgoingChoicePathCount >= 2,
                "Should have at least 2 connection paths starting at the right edge of the choice symbol (found: "
                        + outgoingChoicePathCount + ")");
    }
}
