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
 */

package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.XsdDocInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XSD documentation info extraction functionality in XmlService
 */
public class XmlServiceXsdDocInfoTest {

    private XmlService xmlService;
    private File testXsdFile;

    @BeforeEach
    void setUp() throws IOException {
        xmlService = XmlServiceImpl.getInstance();

        // Create a temporary XSD file with test annotations
        Path tempFile = Files.createTempFile("test-xsd-doc", ".xsd");
        testXsdFile = tempFile.toFile();
        testXsdFile.deleteOnExit();

        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" 
                           elementFormDefault="qualified"
                           targetNamespace="http://example.com/test"
                           xmlns:tns="http://example.com/test">
                
                    <xs:element name="TestElement" type="xs:string">
                        <xs:annotation>
                            <xs:documentation>Test element documentation</xs:documentation>
                            <xs:appinfo source="@since 1.0.0"/>
                            <xs:appinfo source="@see {@link TestType}"/>
                            <xs:appinfo source="@see {@link /root/element}"/>
                            <xs:appinfo source="@deprecated Use NewElement instead"/>
                        </xs:annotation>
                    </xs:element>
                
                    <xs:element name="SimpleElement" type="xs:string">
                        <xs:annotation>
                            <xs:documentation>Simple element with only @since</xs:documentation>
                            <xs:appinfo source="@since 2.0.0"/>
                        </xs:annotation>
                    </xs:element>
                
                    <xs:element name="NoAnnotationsElement" type="xs:string">
                        <xs:annotation>
                            <xs:documentation>Element without doc annotations</xs:documentation>
                        </xs:annotation>
                    </xs:element>
                
                </xs:schema>
                """;

        Files.write(tempFile, xsdContent.getBytes());
    }

    @Test
    void testGetElementDocInfo_WithAllAnnotations() throws Exception {
        XsdDocInfo docInfo = xmlService.getElementDocInfo(testXsdFile, "/TestElement");

        assertNotNull(docInfo);
        assertEquals("1.0.0", docInfo.getSince());
        assertTrue(docInfo.getSee().contains("{@link TestType}"));
        assertTrue(docInfo.getSee().contains("{@link /root/element}"));
        assertEquals("Use NewElement instead", docInfo.getDeprecated());
    }

    @Test
    void testGetElementDocInfo_WithOnlyMajorMinorAnnotation() throws Exception {
        XsdDocInfo docInfo = xmlService.getElementDocInfo(testXsdFile, "/SimpleElement");

        assertNotNull(docInfo);
        assertEquals("2.0.0", docInfo.getSince());
        assertTrue(docInfo.getSee().isEmpty());
        assertNull(docInfo.getDeprecated());
    }

    @Test
    void testGetElementDocInfo_WithNoAnnotations() throws Exception {
        XsdDocInfo docInfo = xmlService.getElementDocInfo(testXsdFile, "/NoAnnotationsElement");

        assertNull(docInfo); // Should return null when no doc annotations found
    }

    @Test
    void testGetElementDocInfo_NonExistentElement() throws Exception {
        XsdDocInfo docInfo = xmlService.getElementDocInfo(testXsdFile, "/NonExistentElement");

        assertNull(docInfo);
    }

    @Test
    void testUpdateAndRetrieveElementDocumentation() throws Exception {
        // Test saving structured annotations and retrieving them
        String javadoc = "@since 3.0.0\n@see {@link UpdatedType}\n@deprecated This is deprecated\n";

        xmlService.updateElementDocumentation(testXsdFile, "/SimpleElement", "Updated documentation", javadoc);

        XsdDocInfo docInfo = xmlService.getElementDocInfo(testXsdFile, "/SimpleElement");

        assertNotNull(docInfo);
        assertEquals("3.0.0", docInfo.getSince());
        assertTrue(docInfo.getSee().contains("{@link UpdatedType}"));
        assertEquals("This is deprecated", docInfo.getDeprecated());
    }
}