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

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for inline simple type handling in XsdDocumentationService.
 * Verifies that elements with inline simple types that have restrictions
 * correctly display their base type instead of "(anonymous)".
 */
public class XsdDocumentationServiceInlineSimpleTypeTest {

    /**
     * Test XSD schema with an inline simple type that has an enumeration restriction.
     * This represents the FundsXML Version element example from the user.
     */
    private static final String TEST_XSD_WITH_INLINE_SIMPLE_TYPE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/test"
                       elementFormDefault="qualified">
            
                <xs:element name="TestRoot">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element minOccurs="0" name="Version">
                                <xs:annotation>
                                    <xs:documentation>Version element with inline simple type restriction</xs:documentation>
                                </xs:annotation>
                                <xs:simpleType>
                                    <xs:restriction base="xs:string">
                                        <xs:enumeration value="4.0.0"/>
                                        <xs:enumeration value="4.0.1"/>
                                        <xs:enumeration value="4.1.0"/>
                                        <xs:enumeration value="4.2.0"/>
                                    </xs:restriction>
                                </xs:simpleType>
                            </xs:element>
                            <xs:element name="Count">
                                <xs:annotation>
                                    <xs:documentation>Element with inline integer restriction</xs:documentation>
                                </xs:annotation>
                                <xs:simpleType>
                                    <xs:restriction base="xs:integer">
                                        <xs:minInclusive value="1"/>
                                        <xs:maxInclusive value="100"/>
                                    </xs:restriction>
                                </xs:simpleType>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @Test
    void testInlineSimpleTypeWithEnumerationRestriction(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-inline-simple-type.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_INLINE_SIMPLE_TYPE);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        // Assert: Verify Version element has base type instead of "(anonymous)"
        // Note: XPath may include SEQUENCE container, so find by element name
        XsdExtendedElement versionElement = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> "Version".equals(e.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(versionElement, "Version element should be processed");
        assertNotNull(versionElement.getElementType(), "Version element should have a type");
        assertNotEquals("(anonymous)", versionElement.getElementType(),
                "Version element should not have '(anonymous)' as type");
        assertEquals("xs:string", versionElement.getElementType(),
                "Version element should have 'xs:string' as base type");

        // Verify restriction info is also captured
        assertNotNull(versionElement.getRestrictionInfo(),
                "Version element should have restriction info");
        assertEquals("xs:string", versionElement.getRestrictionInfo().base(),
                "Restriction base should be xs:string");
        assertTrue(versionElement.getRestrictionInfo().facets().containsKey("enumeration"),
                "Restriction should contain enumeration facets");
        assertEquals(4, versionElement.getRestrictionInfo().facets().get("enumeration").size(),
                "Should have 4 enumeration values");
    }

    @Test
    void testInlineSimpleTypeWithNumericRestriction(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-inline-simple-type.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_INLINE_SIMPLE_TYPE);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        // Assert: Verify Count element has base type instead of "(anonymous)"
        // Note: XPath may include SEQUENCE container, so find by element name
        XsdExtendedElement countElement = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> "Count".equals(e.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(countElement, "Count element should be processed");
        assertNotNull(countElement.getElementType(), "Count element should have a type");
        assertNotEquals("(anonymous)", countElement.getElementType(),
                "Count element should not have '(anonymous)' as type");
        assertEquals("xs:integer", countElement.getElementType(),
                "Count element should have 'xs:integer' as base type");

        // Verify restriction info is also captured
        assertNotNull(countElement.getRestrictionInfo(),
                "Count element should have restriction info");
        assertEquals("xs:integer", countElement.getRestrictionInfo().base(),
                "Restriction base should be xs:integer");
        assertTrue(countElement.getRestrictionInfo().facets().containsKey("minInclusive"),
                "Restriction should contain minInclusive facet");
        assertTrue(countElement.getRestrictionInfo().facets().containsKey("maxInclusive"),
                "Restriction should contain maxInclusive facet");
    }

    @Test
    void testInlineComplexTypeStillShowsAnonymous(@TempDir Path tempDir) throws Exception {
        // Arrange: Create XSD with inline complex type (not simple type)
        String xsdWithInlineComplexType = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/test"
                           elementFormDefault="qualified">
                
                    <xs:element name="TestRoot">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="ComplexElement">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element name="Child" type="xs:string"/>
                                        </xs:sequence>
                                    </xs:complexType>
                                </xs:element>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        Path xsdFile = tempDir.resolve("test-inline-complex-type.xsd");
        Files.writeString(xsdFile, xsdWithInlineComplexType);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        // Assert: Verify ComplexElement still shows as "(anonymous)" for complex types
        // Note: XPath may include SEQUENCE container, so find by element name
        XsdExtendedElement complexElement = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> "ComplexElement".equals(e.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(complexElement, "ComplexElement should be processed");
        assertNotNull(complexElement.getElementType(), "ComplexElement should have a type");
        assertEquals("(anonymous)", complexElement.getElementType(),
                "Inline complex types should still show as '(anonymous)'");
    }
}
