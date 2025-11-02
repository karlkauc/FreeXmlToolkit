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
 * Tests for XSD 1.1 explicitTimezone facet handling.
 * Verifies that the explicitTimezone facet is correctly parsed and displayed.
 */
public class XsdExplicitTimezoneTest {

    /**
     * Test XSD 1.1 schema with explicitTimezone facet.
     */
    private static final String TEST_XSD_WITH_EXPLICIT_TIMEZONE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       targetNamespace="http://example.com/test"
                       elementFormDefault="qualified"
                       vc:minVersion="1.1">
            
                <!-- Simple type with required timezone -->
                <xs:simpleType name="DateTimeWithTimezone">
                    <xs:restriction base="xs:dateTime">
                        <xs:explicitTimezone value="required"/>
                    </xs:restriction>
                </xs:simpleType>
            
                <!-- Simple type with prohibited timezone -->
                <xs:simpleType name="DateTimeWithoutTimezone">
                    <xs:restriction base="xs:dateTime">
                        <xs:explicitTimezone value="prohibited"/>
                    </xs:restriction>
                </xs:simpleType>
            
                <!-- Simple type with optional timezone (default) -->
                <xs:simpleType name="DateTimeOptionalTimezone">
                    <xs:restriction base="xs:dateTime">
                        <xs:explicitTimezone value="optional"/>
                    </xs:restriction>
                </xs:simpleType>
            
                <xs:element name="TestRoot">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="CreatedAt" type="DateTimeWithTimezone">
                                <xs:annotation>
                                    <xs:documentation>DateTime with required timezone</xs:documentation>
                                </xs:annotation>
                            </xs:element>
                            <xs:element name="LocalTime" type="DateTimeWithoutTimezone">
                                <xs:annotation>
                                    <xs:documentation>DateTime without timezone</xs:documentation>
                                </xs:annotation>
                            </xs:element>
                            <xs:element name="FlexibleTime" type="DateTimeOptionalTimezone">
                                <xs:annotation>
                                    <xs:documentation>DateTime with optional timezone</xs:documentation>
                                </xs:annotation>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @Test
    void testExplicitTimezoneRequired(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_EXPLICIT_TIMEZONE);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        // Assert: Find the CreatedAt element
        XsdExtendedElement createdAtElement = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "CreatedAt".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(createdAtElement, "CreatedAt element should exist");
        assertNotNull(createdAtElement.getRestrictionInfo(), "Restriction info should exist");

        // Verify explicitTimezone facet is parsed
        assertTrue(createdAtElement.getRestrictionInfo().facets().containsKey("explicitTimezone"),
                "explicitTimezone facet should be present");

        // Verify the value is "required"
        assertEquals("required", createdAtElement.getRestrictionInfo().facets().get("explicitTimezone").get(0),
                "explicitTimezone value should be 'required'");

        // Verify HTML output contains correct badge and explanation
        String restrictionString = createdAtElement.getXsdRestrictionString();
        assertTrue(restrictionString.contains("Timezone"), "Restriction string should contain 'Timezone'");
        assertTrue(restrictionString.contains("Required"), "Restriction string should contain 'Required'");
        assertTrue(restrictionString.contains("bg-emerald-100"), "Should have green badge for required");
        assertTrue(restrictionString.contains("must be present"), "Should have explanation for required");
    }

    @Test
    void testExplicitTimezoneProhibited(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_EXPLICIT_TIMEZONE);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        // Assert: Find the LocalTime element
        XsdExtendedElement localTimeElement = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "LocalTime".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(localTimeElement, "LocalTime element should exist");
        assertNotNull(localTimeElement.getRestrictionInfo(), "Restriction info should exist");

        // Verify the value is "prohibited"
        assertEquals("prohibited", localTimeElement.getRestrictionInfo().facets().get("explicitTimezone").get(0),
                "explicitTimezone value should be 'prohibited'");

        // Verify HTML output contains correct badge and explanation
        String restrictionString = localTimeElement.getXsdRestrictionString();
        assertTrue(restrictionString.contains("Prohibited"), "Restriction string should contain 'Prohibited'");
        assertTrue(restrictionString.contains("bg-red-100"), "Should have red badge for prohibited");
        assertTrue(restrictionString.contains("must not be present"), "Should have explanation for prohibited");
    }

    @Test
    void testExplicitTimezoneOptional(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_EXPLICIT_TIMEZONE);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        // Assert: Find the FlexibleTime element
        XsdExtendedElement flexibleTimeElement = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "FlexibleTime".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(flexibleTimeElement, "FlexibleTime element should exist");
        assertNotNull(flexibleTimeElement.getRestrictionInfo(), "Restriction info should exist");

        // Verify the value is "optional"
        assertEquals("optional", flexibleTimeElement.getRestrictionInfo().facets().get("explicitTimezone").get(0),
                "explicitTimezone value should be 'optional'");

        // Verify HTML output contains correct badge and explanation
        String restrictionString = flexibleTimeElement.getXsdRestrictionString();
        assertTrue(restrictionString.contains("Optional"), "Restriction string should contain 'Optional'");
        assertTrue(restrictionString.contains("bg-slate-100"), "Should have gray badge for optional");
        assertTrue(restrictionString.contains("is optional"), "Should have explanation for optional");
    }
}
