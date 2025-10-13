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

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XsdDocumentationSvgChoiceTest {

    private XsdDocumentationService service;
    private XsdDocumentationSvgService svgService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new XsdDocumentationService();
        svgService = new XsdDocumentationSvgService();
    }

    @Test
    void testChoiceElementRendering() throws Exception {
        // Create test XSD content
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" 
                           elementFormDefault="qualified">
                
                    <xs:simpleType name="Text256Type">
                        <xs:restriction base="xs:string">
                            <xs:maxLength value="256"/>
                        </xs:restriction>
                    </xs:simpleType>
                
                    <xs:complexType name="TransactionType">
                        <xs:sequence>
                            <xs:element name="TransactionID" type="Text256Type"/>
                            <xs:element minOccurs="0" name="CancellationFlag" type="xs:boolean"/>
                            <xs:choice minOccurs="0">
                                <xs:element name="Derivatives">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element minOccurs="0" name="HedgingFlag" type="xs:boolean"/>
                                        </xs:sequence>
                                    </xs:complexType>
                                </xs:element>
                                <xs:element name="Bonds">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element minOccurs="0" name="ExCouponFlag" type="xs:boolean"/>
                                        </xs:sequence>
                                    </xs:complexType>
                                </xs:element>
                            </xs:choice>
                        </xs:sequence>
                    </xs:complexType>
                
                    <xs:element name="Transaction" type="TransactionType"/>
                
                </xs:schema>
                """;

        // Write XSD to temp file
        Path xsdFile = tempDir.resolve("test-choice.xsd");
        Files.writeString(xsdFile, xsdContent);

        // Process XSD
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        // Get the processed elements
        Map<String, XsdExtendedElement> elements = service.xsdDocumentationData.getExtendedXsdElementMap();

        // Verify that choice element was created as a separate node
        boolean choiceElementFound = false;
        for (XsdExtendedElement element : elements.values()) {
            if (element.getElementName().startsWith("CHOICE")) {
                choiceElementFound = true;
                assertEquals("(container)", element.getElementType());
                assertFalse(element.getChildren().isEmpty(), "Choice element should have children");
                break;
            }
        }
        assertTrue(choiceElementFound, "Choice element should be created as a separate container node");

        // Verify that Derivatives and Bonds are children of the choice element, not direct children of TransactionType
        boolean derivativesIsChildOfChoice = false;
        boolean bondsIsChildOfChoice = false;

        for (XsdExtendedElement element : elements.values()) {
            if (element.getElementName().startsWith("CHOICE")) {
                for (String childPath : element.getChildren()) {
                    XsdExtendedElement child = elements.get(childPath);
                    if (child != null) {
                        if ("Derivatives".equals(child.getElementName())) {
                            derivativesIsChildOfChoice = true;
                        } else if ("Bonds".equals(child.getElementName())) {
                            bondsIsChildOfChoice = true;
                        }
                    }
                }
            }
        }

        assertTrue(derivativesIsChildOfChoice, "Derivatives should be a child of the choice element");
        assertTrue(bondsIsChildOfChoice, "Bonds should be a child of the choice element");

        // Test SVG generation
        svgService.setDocumentationData(service.xsdDocumentationData);
        svgService.setOutputDirectory(tempDir.toFile());

        // This should not throw an exception
        assertDoesNotThrow(() -> svgService.generateSvgPage());

        // Verify SVG file was created
        File svgFile = tempDir.resolve("schema-svg.html").toFile();
        assertTrue(svgFile.exists(), "SVG file should be created");
        assertTrue(svgFile.length() > 0, "SVG file should not be empty");
    }

    @Test
    void testSequenceElementRendering() throws Exception {
        // Test with sequence element as well
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" 
                           elementFormDefault="qualified">
                
                    <xs:complexType name="TestType">
                        <xs:sequence>
                            <xs:element name="Element1" type="xs:string"/>
                            <xs:sequence minOccurs="0">
                                <xs:element name="NestedElement1" type="xs:string"/>
                                <xs:element name="NestedElement2" type="xs:string"/>
                            </xs:sequence>
                            <xs:element name="Element2" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                
                    <xs:element name="TestElement" type="TestType"/>
                
                </xs:schema>
                """;

        Path xsdFile = tempDir.resolve("test-sequence.xsd");
        Files.writeString(xsdFile, xsdContent);

        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        Map<String, XsdExtendedElement> elements = service.xsdDocumentationData.getExtendedXsdElementMap();

        // Debug: Print all elements to see what was created
        System.out.println("Elements found:");
        for (XsdExtendedElement element : elements.values()) {
            System.out.println("- " + element.getElementName() + " (level=" + element.getLevel() + ", type=" + element.getElementType() + ", path=" + element.getCurrentXpath() + ")");
        }

        // Verify that nested sequence element was created
        boolean nestedSequenceFound = false;
        for (XsdExtendedElement element : elements.values()) {
            if (element.getElementName().startsWith("SEQUENCE")) {
                // Check if this is a nested sequence by looking at the path - should contain SEQUENCE in the middle
                String xpath = element.getCurrentXpath();
                if (xpath != null && xpath.contains("/SEQUENCE") && !xpath.endsWith("/TestElement")) {
                    nestedSequenceFound = true;
                    assertEquals("(container)", element.getElementType());
                    break;
                }
            }
        }
        assertTrue(nestedSequenceFound, "Nested sequence element should be created as a separate container node");
    }
}