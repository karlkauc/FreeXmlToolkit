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
 *
 */

package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Type Alternatives (xs:alternative) visualization in SVG diagrams.
 */
public class XsdSvgTypeAlternativesTest {

    private static final String TEST_XSD_WITH_TYPE_ALTERNATIVES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       targetNamespace="http://example.com/test"
                       elementFormDefault="qualified"
                       vc:minVersion="1.1">
            
                <xs:complexType name="ConditionalType">
                    <xs:sequence>
                        <xs:element name="Value">
                            <xs:alternative test="@type='integer'" type="xs:integer"/>
                            <xs:alternative test="@type='string'" type="xs:string"/>
                            <xs:alternative type="xs:anyType"/>
                        </xs:element>
                        <xs:element name="Status" type="xs:string"/>
                    </xs:sequence>
                    <xs:attribute name="type" type="xs:string"/>
                </xs:complexType>
            
                <xs:element name="TestRoot" type="ConditionalType"/>
            </xs:schema>
            """;

    @Test
    void testTypeAlternativesAreRecognized(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-type-alternatives.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_TYPE_ALTERNATIVES);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify Value element has type alternatives
        var valueElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Value".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(valueElement, "Value element should exist");
        assertNotNull(valueElement.getTypeAlternatives(), "Type alternatives list should not be null");
        assertFalse(valueElement.getTypeAlternatives().isEmpty(),
                "Value should have at least one type alternative");
    }

    @Test
    void testTypeAlternativeDetails(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-type-alt-details.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_TYPE_ALTERNATIVES);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify Value element type alternatives details
        var valueElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Value".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(valueElement, "Value element should exist");
        assertTrue(valueElement.getTypeAlternatives().size() >= 2,
                "Value should have at least 2 type alternatives");

        // Verify at least one alternative has a test condition
        boolean hasTestCondition = valueElement.getTypeAlternatives().stream()
                .anyMatch(alt -> alt.getTest() != null && !alt.getTest().isEmpty());
        assertTrue(hasTestCondition, "At least one alternative should have a test condition");
    }

    @Test
    void testMultipleTypeAlternatives(@TempDir Path tempDir) throws Exception {
        // Arrange: Create XSD with multiple type alternatives
        String xsdWithMultipleAlternatives = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           targetNamespace="http://example.com/test"
                           elementFormDefault="qualified"
                           vc:minVersion="1.1">
                
                    <xs:element name="FlexibleElement">
                        <xs:alternative test="@format='number'" type="xs:decimal"/>
                        <xs:alternative test="@format='date'" type="xs:date"/>
                        <xs:alternative test="@format='boolean'" type="xs:boolean"/>
                        <xs:alternative type="xs:string"/>
                    </xs:element>
                </xs:schema>
                """;

        Path xsdFile = tempDir.resolve("test-multiple-alternatives.xsd");
        Files.writeString(xsdFile, xsdWithMultipleAlternatives);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify FlexibleElement has multiple type alternatives
        var flexElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "FlexibleElement".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(flexElement, "FlexibleElement should exist");
        assertNotNull(flexElement.getTypeAlternatives(), "Type alternatives list should not be null");
        assertTrue(flexElement.getTypeAlternatives().size() >= 3,
                "FlexibleElement should have at least 3 type alternatives");
    }
}
