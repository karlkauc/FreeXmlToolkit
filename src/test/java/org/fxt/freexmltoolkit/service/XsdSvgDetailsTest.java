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

import org.fxt.freexmltoolkit.domain.Wildcard;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Wildcard Details and Enhanced Facets visualization in SVG diagrams.
 */
public class XsdSvgDetailsTest {

    private static final String TEST_XSD_WITH_WILDCARDS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/test"
                       elementFormDefault="qualified">
            
                <xs:element name="TestRoot">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Name" type="xs:string"/>
                            <xs:any namespace="##any" processContents="strict" minOccurs="0" maxOccurs="unbounded"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            
                <xs:element name="FlexibleRoot">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="ID" type="xs:int"/>
                            <xs:any namespace="##other" processContents="lax" minOccurs="1" maxOccurs="10"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            
                <xs:element name="SkipRoot">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Data" type="xs:string"/>
                            <xs:any namespace="##local" processContents="skip" minOccurs="0" maxOccurs="1"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    private static final String TEST_XSD_WITH_EXPLICIT_TIMEZONE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       targetNamespace="http://example.com/test"
                       elementFormDefault="qualified"
                       vc:minVersion="1.1">
            
                <xs:simpleType name="DateTimeRequired">
                    <xs:restriction base="xs:dateTime">
                        <xs:explicitTimezone value="required"/>
                    </xs:restriction>
                </xs:simpleType>
            
                <xs:simpleType name="DateTimeProhibited">
                    <xs:restriction base="xs:dateTime">
                        <xs:explicitTimezone value="prohibited"/>
                    </xs:restriction>
                </xs:simpleType>
            
                <xs:simpleType name="DateTimeOptional">
                    <xs:restriction base="xs:dateTime">
                        <xs:explicitTimezone value="optional"/>
                    </xs:restriction>
                </xs:simpleType>
            
                <xs:element name="TestRoot">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="CreatedAt" type="DateTimeRequired"/>
                            <xs:element name="LocalTime" type="DateTimeProhibited"/>
                            <xs:element name="FlexTime" type="DateTimeOptional"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    // Wildcard Tests

    @Test
    void testWildcardNamespaceDisplay(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-wildcard-namespace.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_WILDCARDS);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify TestRoot element or ANY child has wildcard information
        var elementWithWildcard = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> elem.getWildcards() != null && !elem.getWildcards().isEmpty())
                .findFirst()
                .orElse(null);

        assertNotNull(elementWithWildcard, "Element with wildcard should exist");
        assertNotNull(elementWithWildcard.getWildcards(), "Wildcards list should not be null");
        assertFalse(elementWithWildcard.getWildcards().isEmpty(), "Wildcards list should not be empty");

        var wildcard = elementWithWildcard.getWildcards().get(0);
        assertNotNull(wildcard.getNamespace(), "Wildcard namespace should not be null");
    }

    @Test
    void testWildcardProcessContentsStrict(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-wildcard-strict.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_WILDCARDS);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Find any element with wildcards that has STRICT processContents
        var elementWithStrictWildcard = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> elem.getWildcards() != null && !elem.getWildcards().isEmpty())
                .filter(elem -> elem.getWildcards().stream()
                        .anyMatch(w -> w.getProcessContents() == Wildcard.ProcessContents.STRICT))
                .findFirst()
                .orElse(null);

        assertNotNull(elementWithStrictWildcard, "Element with STRICT wildcard should exist");
        var strictWildcard = elementWithStrictWildcard.getWildcards().stream()
                .filter(w -> w.getProcessContents() == Wildcard.ProcessContents.STRICT)
                .findFirst()
                .orElse(null);
        assertNotNull(strictWildcard, "STRICT wildcard should exist");
        assertEquals(Wildcard.ProcessContents.STRICT, strictWildcard.getProcessContents(),
                "ProcessContents should be STRICT");
    }

    @Test
    void testWildcardProcessContentsLax(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-wildcard-lax.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_WILDCARDS);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Find any element with wildcards that has LAX processContents
        var elementWithLaxWildcard = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> elem.getWildcards() != null && !elem.getWildcards().isEmpty())
                .filter(elem -> elem.getWildcards().stream()
                        .anyMatch(w -> w.getProcessContents() == Wildcard.ProcessContents.LAX))
                .findFirst()
                .orElse(null);

        assertNotNull(elementWithLaxWildcard, "Element with LAX wildcard should exist");
        var laxWildcard = elementWithLaxWildcard.getWildcards().stream()
                .filter(w -> w.getProcessContents() == Wildcard.ProcessContents.LAX)
                .findFirst()
                .orElse(null);
        assertNotNull(laxWildcard, "LAX wildcard should exist");
        assertEquals(Wildcard.ProcessContents.LAX, laxWildcard.getProcessContents(),
                "ProcessContents should be LAX");
    }

    @Test
    void testWildcardProcessContentsSkip(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-wildcard-skip.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_WILDCARDS);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Find any element with wildcards that has SKIP processContents
        var elementWithSkipWildcard = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> elem.getWildcards() != null && !elem.getWildcards().isEmpty())
                .filter(elem -> elem.getWildcards().stream()
                        .anyMatch(w -> w.getProcessContents() == Wildcard.ProcessContents.SKIP))
                .findFirst()
                .orElse(null);

        assertNotNull(elementWithSkipWildcard, "Element with SKIP wildcard should exist");
        var skipWildcard = elementWithSkipWildcard.getWildcards().stream()
                .filter(w -> w.getProcessContents() == Wildcard.ProcessContents.SKIP)
                .findFirst()
                .orElse(null);
        assertNotNull(skipWildcard, "SKIP wildcard should exist");
        assertEquals(Wildcard.ProcessContents.SKIP, skipWildcard.getProcessContents(),
                "ProcessContents should be SKIP");
    }

    // Enhanced Facets Tests (explicitTimezone)

    @Test
    void testExplicitTimezoneRequired(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-timezone-required.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_EXPLICIT_TIMEZONE);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Find CreatedAt element
        var createdAtElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "CreatedAt".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(createdAtElement, "CreatedAt element should exist");
        assertNotNull(createdAtElement.getRestrictionInfo(), "Restriction info should exist");
        assertTrue(createdAtElement.getRestrictionInfo().facets().containsKey("explicitTimezone"),
                "Should have explicitTimezone facet");
        assertEquals("required", createdAtElement.getRestrictionInfo().facets().get("explicitTimezone").get(0),
                "explicitTimezone should be 'required'");
    }

    @Test
    void testExplicitTimezoneProhibited(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-timezone-prohibited.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_EXPLICIT_TIMEZONE);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Find LocalTime element
        var localTimeElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "LocalTime".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(localTimeElement, "LocalTime element should exist");
        assertNotNull(localTimeElement.getRestrictionInfo(), "Restriction info should exist");
        assertEquals("prohibited", localTimeElement.getRestrictionInfo().facets().get("explicitTimezone").get(0),
                "explicitTimezone should be 'prohibited'");
    }

    @Test
    void testExplicitTimezoneOptional(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-timezone-optional.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_EXPLICIT_TIMEZONE);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Find FlexTime element
        var flexTimeElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "FlexTime".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(flexTimeElement, "FlexTime element should exist");
        assertNotNull(flexTimeElement.getRestrictionInfo(), "Restriction info should exist");
        assertEquals("optional", flexTimeElement.getRestrictionInfo().facets().get("explicitTimezone").get(0),
                "explicitTimezone should be 'optional'");
    }
}
