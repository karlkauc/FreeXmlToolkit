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
 * Tests for List and Union type visualization in SVG diagrams.
 */
public class XsdSvgListUnionTest {

    private static final String TEST_XSD_WITH_LIST_UNION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/test"
                       elementFormDefault="qualified">
            
                <!-- List type -->
                <xs:simpleType name="IntegerList">
                    <xs:list itemType="xs:int"/>
                </xs:simpleType>
            
                <!-- Union type -->
                <xs:simpleType name="StringOrInt">
                    <xs:union memberTypes="xs:string xs:int"/>
                </xs:simpleType>
            
                <xs:element name="TestRoot">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Numbers" type="IntegerList">
                                <xs:annotation>
                                    <xs:documentation>List of integers</xs:documentation>
                                </xs:annotation>
                            </xs:element>
                            <xs:element name="Value" type="StringOrInt">
                                <xs:annotation>
                                    <xs:documentation>Either string or integer</xs:documentation>
                                </xs:annotation>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @Test
    void testListTypeSvgVisualization(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-list-union.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_LIST_UNION);

        // Act: Process XSD and verify that list/union types are correctly identified
        XsdDocumentationService docService = new XsdDocumentationService();
        docService.setXsdFilePath(xsdFile.toString());
        docService.processXsd(false);

        // Assert: Verify that elements with list/union types can be processed for SVG
        var numbersElement = docService.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Numbers".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        var valueElement = docService.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Value".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        // Verify elements exist and have correct type info for SVG rendering
        assertNotNull(numbersElement, "Numbers element should exist");
        assertTrue(numbersElement.isListType(), "Numbers should be list type");
        assertEquals("List of xs:int", numbersElement.getTypeDisplayString(),
                "List type should have correct display string for SVG");

        assertNotNull(valueElement, "Value element should exist");
        assertTrue(valueElement.isUnionType(), "Value should be union type");
        String unionDisplay = valueElement.getTypeDisplayString();
        assertTrue(unionDisplay.contains("|"), "Union type display should contain pipe separator");
        assertTrue(unionDisplay.contains("xs:string") && unionDisplay.contains("xs:int"),
                "Union type display should contain both member types");
    }

    @Test
    void testListTypeElementProperties(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-list.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_LIST_UNION);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify Numbers element is recognized as List type
        var numbersElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Numbers".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(numbersElement, "Numbers element should exist");
        assertTrue(numbersElement.isListType(), "Numbers should be recognized as list type");
        assertEquals("xs:int", numbersElement.getListItemType(), "List item type should be xs:int");
        assertEquals("List of xs:int", numbersElement.getTypeDisplayString(),
                "Type display string should be 'List of xs:int'");
    }

    @Test
    void testUnionTypeElementProperties(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-union.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_LIST_UNION);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify Value element is recognized as Union type
        var valueElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "Value".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(valueElement, "Value element should exist");
        assertTrue(valueElement.isUnionType(), "Value should be recognized as union type");
        assertNotNull(valueElement.getUnionMemberTypes(), "Union member types should not be null");
        assertEquals(2, valueElement.getUnionMemberTypes().size(), "Union should have 2 member types");
        assertTrue(valueElement.getUnionMemberTypes().contains("xs:string"),
                "Union should contain xs:string");
        assertTrue(valueElement.getUnionMemberTypes().contains("xs:int"),
                "Union should contain xs:int");
    }
}
