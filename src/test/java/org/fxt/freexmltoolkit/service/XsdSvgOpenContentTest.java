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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for Open Content (xs:openContent) visualization in SVG diagrams.
 */
public class XsdSvgOpenContentTest {

    private static final String TEST_XSD_WITH_OPEN_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       targetNamespace="http://example.com/test"
                       elementFormDefault="qualified"
                       vc:minVersion="1.1">
            
                <xs:complexType name="ExtensibleType">
                    <xs:openContent mode="interleave">
                        <xs:any namespace="##any" processContents="lax"/>
                    </xs:openContent>
                    <xs:sequence>
                        <xs:element name="Name" type="xs:string"/>
                        <xs:element name="Value" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>
            
                <xs:complexType name="SuffixExtensibleType">
                    <xs:openContent mode="suffix">
                        <xs:any namespace="##other" processContents="strict"/>
                    </xs:openContent>
                    <xs:sequence>
                        <xs:element name="ID" type="xs:int"/>
                        <xs:element name="Description" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>
            
                <xs:element name="TestRoot">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="ExtensibleData" type="ExtensibleType"/>
                            <xs:element name="SuffixData" type="SuffixExtensibleType"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @Test
    void testOpenContentIsRecognized(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-open-content.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_OPEN_CONTENT);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify ExtensibleData element has open content
        var extensibleElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "ExtensibleData".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(extensibleElement, "ExtensibleData element should exist");
        assertNotNull(extensibleElement.getOpenContent(), "Open content should not be null");
    }

    @Test
    void testOpenContentInterleaveMode(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-open-content-interleave.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_OPEN_CONTENT);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify ExtensibleData element has interleave mode
        var extensibleElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "ExtensibleData".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(extensibleElement, "ExtensibleData element should exist");
        assertNotNull(extensibleElement.getOpenContent(), "Open content should not be null");
        assertEquals("interleave", extensibleElement.getOpenContent().getMode().toString().toLowerCase(),
                "Open content mode should be 'interleave'");
    }

    @Test
    void testOpenContentSuffixMode(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-open-content-suffix.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_OPEN_CONTENT);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify SuffixData element has suffix mode
        var suffixElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "SuffixData".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(suffixElement, "SuffixData element should exist");
        assertNotNull(suffixElement.getOpenContent(), "Open content should not be null");
        assertEquals("suffix", suffixElement.getOpenContent().getMode().toString().toLowerCase(),
                "Open content mode should be 'suffix'");
    }

    @Test
    void testOpenContentWildcard(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test XSD file
        Path xsdFile = tempDir.resolve("test-open-content-wildcard.xsd");
        Files.writeString(xsdFile, TEST_XSD_WITH_OPEN_CONTENT);

        // Act: Process XSD
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.toString());
        service.processXsd(false);

        XsdDocumentationData data = service.xsdDocumentationData;

        // Assert: Verify ExtensibleData element has wildcard in open content
        var extensibleElement = data.getExtendedXsdElementMap().values().stream()
                .filter(elem -> "ExtensibleData".equals(elem.getElementName()))
                .findFirst()
                .orElse(null);

        assertNotNull(extensibleElement, "ExtensibleData element should exist");
        assertNotNull(extensibleElement.getOpenContent(), "Open content should not be null");
        assertNotNull(extensibleElement.getOpenContent().getNamespace(),
                "Open content namespace should not be null");
        assertEquals("##any", extensibleElement.getOpenContent().getNamespace(),
                "Namespace should be '##any'");
    }
}
