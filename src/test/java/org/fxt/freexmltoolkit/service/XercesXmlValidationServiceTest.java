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

package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XercesXmlValidationService.
 */
class XercesXmlValidationServiceTest {

    private XercesXmlValidationService validationService;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        validationService = new XercesXmlValidationService();
        tempDir = Files.createTempDirectory("xerces-test");
    }

    @Test
    void testValidatorName() {
        assertEquals("Apache Xerces", validationService.getValidatorName());
    }

    @Test
    void testSupportsXsd11() {
        assertTrue(validationService.supportsXsd11());
    }

    @Test
    void testValidXmlAgainstXsd10Schema() throws Exception {
        // Create a simple XSD 1.0 schema
        String schemaContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="root">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="child" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("test-schema.xsd");
        Files.writeString(schemaFile, schemaContent);

        // Create valid XML
        String validXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
                <child>Test Value</child>
            </root>
            """;

        List<SAXParseException> errors = validationService.validateText(validXml, schemaFile.toFile());

        assertTrue(errors.isEmpty(), "Valid XML should have no errors");
    }

    @Test
    void testInvalidXmlAgainstXsd10Schema() throws Exception {
        // Create a simple XSD 1.0 schema
        String schemaContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="root">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="child" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("test-schema.xsd");
        Files.writeString(schemaFile, schemaContent);

        // Create invalid XML (missing required child element)
        String invalidXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
                <wrongElement>Test Value</wrongElement>
            </root>
            """;

        List<SAXParseException> errors = validationService.validateText(invalidXml, schemaFile.toFile());

        assertFalse(errors.isEmpty(), "Invalid XML should have errors");
    }

    @Test
    void testValidXmlAgainstXsd11SchemaWithAssert() throws Exception {
        // Create an XSD 1.1 schema with assertions
        String schemaContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       vc:minVersion="1.1">
                <xs:element name="person">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="age" type="xs:int"/>
                            <xs:element name="drivingLicense" type="xs:boolean"/>
                        </xs:sequence>
                        <xs:assert test="if (drivingLicense = true()) then age &gt;= 18 else true()"/>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("person-schema-11.xsd");
        Files.writeString(schemaFile, schemaContent);

        // Create valid XML (age >= 18 with driving license)
        String validXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <person>
                <age>25</age>
                <drivingLicense>true</drivingLicense>
            </person>
            """;

        List<SAXParseException> errors = validationService.validateText(validXml, schemaFile.toFile());

        assertTrue(errors.isEmpty(), "Valid XML with XSD 1.1 assertions should have no errors");
    }

    @Test
    void testInvalidXmlAgainstXsd11SchemaWithAssert() throws Exception {
        // Create an XSD 1.1 schema with assertions
        String schemaContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       vc:minVersion="1.1">
                <xs:element name="person">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="age" type="xs:int"/>
                            <xs:element name="drivingLicense" type="xs:boolean"/>
                        </xs:sequence>
                        <xs:assert test="if (drivingLicense = true()) then age &gt;= 18 else true()"/>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("person-schema-11.xsd");
        Files.writeString(schemaFile, schemaContent);

        // Create invalid XML (age < 18 but has driving license)
        String invalidXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <person>
                <age>16</age>
                <drivingLicense>true</drivingLicense>
            </person>
            """;

        List<SAXParseException> errors = validationService.validateText(invalidXml, schemaFile.toFile());

        assertFalse(errors.isEmpty(), "XML violating XSD 1.1 assertion should have errors");
    }

    @Test
    void testWellFormednessCheckWithNoSchema() {
        String wellFormedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
                <child>Test</child>
            </root>
            """;

        List<SAXParseException> errors = validationService.validateText(wellFormedXml, null);

        assertTrue(errors.isEmpty(), "Well-formed XML should have no errors when checking without schema");
    }

    @Test
    void testMalformedXmlWithNoSchema() {
        String malformedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
                <child>Test</child>
            <!-- Missing closing root tag -->
            """;

        List<SAXParseException> errors = validationService.validateText(malformedXml, null);

        assertFalse(errors.isEmpty(), "Malformed XML should have errors even without schema");
    }
}
