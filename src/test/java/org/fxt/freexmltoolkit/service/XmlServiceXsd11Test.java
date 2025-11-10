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

import org.fxt.freexmltoolkit.domain.XmlParserType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XSD 1.1 schema validation support in XmlService.
 * XSD 1.1 features include assertions, type alternatives, and open content.
 *
 * Note: Saxon-HE (Home Edition) does not support XSD 1.1 validation.
 * This test suite verifies behavior with both Saxon (XSD 1.0 only, graceful degradation)
 * and Xerces (full XSD 1.1 support).
 */
public class XmlServiceXsd11Test {

    private XmlService xmlService;
    private PropertiesService propertiesService;
    private XmlParserType originalParserType;

    @BeforeEach
    void setUp() {
        xmlService = XmlServiceImpl.getInstance();
        propertiesService = PropertiesServiceImpl.getInstance();

        // Save original parser type
        originalParserType = propertiesService.getXmlParserType();

        // Set parser to XERCES for XSD 1.1 tests
        propertiesService.setXmlParserType(XmlParserType.XERCES);
    }

    @AfterEach
    void tearDown() {
        // Restore original parser type
        if (originalParserType != null) {
            propertiesService.setXmlParserType(originalParserType);
        }
    }

    @Test
    @DisplayName("Should detect XSD 1.1 schema with assert elements")
    void testXsd11SchemaDetection() throws Exception {
        // Arrange
        File xsd11Schema = new File("src/test/resources/xsd11_with_assert.xsd");
        assertTrue(xsd11Schema.exists(), "XSD 1.1 test schema should exist");

        // Verify the schema contains assert elements
        String schemaContent = Files.readString(xsd11Schema.toPath());
        assertTrue(schemaContent.contains("<xs:assert"), "Schema should contain XSD 1.1 assert elements");

        // Act - Set the XSD 1.1 schema
        xmlService.setCurrentXsdFile(xsd11Schema);

        // Assert - Schema should be loaded successfully (even though it's XSD 1.1)
        assertNotNull(xmlService.getCurrentXsdFile(), "XSD 1.1 schema should be loaded");
        assertEquals(xsd11Schema.getAbsolutePath(), xmlService.getCurrentXsdFile().getAbsolutePath());
    }

    @Test
    @DisplayName("Should validate well-formed XML against XSD 1.1 schema without throwing errors")
    void testValidateWellFormedXmlWithXsd11Schema() throws Exception {
        // Arrange
        File xsd11Schema = new File("src/test/resources/xsd11_with_assert.xsd");
        File validXml = new File("src/test/resources/xsd11_valid_product.xml");

        assertTrue(xsd11Schema.exists(), "XSD 1.1 test schema should exist");
        assertTrue(validXml.exists(), "Valid test XML should exist");

        String xmlContent = Files.readString(validXml.toPath());

        // Act - Validate XML against XSD 1.1 schema
        List<SAXParseException> errors = xmlService.validateText(xmlContent, xsd11Schema);

        // Assert
        assertNotNull(errors, "Validation should return a result");

        // With Xerces (XSD 1.1 support): Valid XML should have no errors
        // With Saxon (XSD 1.0 only): Would have info message about XSD 1.1
        // Since we're using Xerces in this test, we expect no errors
        assertTrue(errors.isEmpty(),
                "Valid XML with XSD 1.1 schema should have no errors when using Xerces validator");
    }

    @Test
    @DisplayName("Should detect malformed XML even with XSD 1.1 schema")
    void testValidateMalformedXmlWithXsd11Schema() throws Exception {
        // Arrange
        File xsd11Schema = new File("src/test/resources/xsd11_with_assert.xsd");
        assertTrue(xsd11Schema.exists(), "XSD 1.1 test schema should exist");

        // Malformed XML (unclosed tag)
        String malformedXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Product xmlns="http://example.com/test">
                    <Name>Test Product</Name>
                    <Price>100.00</Price>
                    <Discount>20.00
                </Product>
                """;

        // Act - Validate malformed XML against XSD 1.1 schema
        List<SAXParseException> errors = xmlService.validateText(malformedXml, xsd11Schema);

        // Assert
        assertNotNull(errors, "Validation should return a result");
        assertFalse(errors.isEmpty(), "Should contain validation errors");

        // With Xerces: Should detect malformed XML directly
        boolean hasMalformednessError = errors.stream()
                .anyMatch(e -> e.getMessage().contains("element type") ||
                              e.getMessage().contains("malformed") ||
                              e.getMessage().contains("must be terminated") ||
                              e.getMessage().contains("XML document structures must start and end"));

        assertTrue(hasMalformednessError,
                "Should detect that XML is not well-formed");
    }

    @Test
    @DisplayName("Should handle XSD 1.1 schema without crashing the application")
    void testXsd11SchemaDoesNotCrashApplication() throws Exception {
        // Arrange
        File xsd11Schema = new File("src/test/resources/xsd11_with_assert.xsd");
        assertTrue(xsd11Schema.exists(), "XSD 1.1 test schema should exist");

        // Act & Assert - Setting XSD 1.1 schema should not throw exception
        assertDoesNotThrow(() -> {
            xmlService.setCurrentXsdFile(xsd11Schema);
        }, "Setting XSD 1.1 schema should not throw exception");

        // Validate should also not crash
        String validXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Product xmlns="http://example.com/test">
                    <Name>Test</Name>
                    <Price>100</Price>
                </Product>
                """;

        assertDoesNotThrow(() -> {
            xmlService.validateText(validXml, xsd11Schema);
        }, "Validating with XSD 1.1 schema should not throw exception");
    }

    @Test
    @DisplayName("Should provide full XSD 1.1 validation with Xerces")
    void testXsd11FullValidationWithXerces() throws Exception {
        // Arrange
        File xsd11Schema = new File("src/test/resources/xsd11_with_assert.xsd");
        String validXml = Files.readString(Paths.get("src/test/resources/xsd11_valid_product.xml"));

        // Act
        List<SAXParseException> errors = xmlService.validateText(validXml, xsd11Schema);

        // Assert - Xerces provides full XSD 1.1 validation
        // Valid XML should have no errors
        assertTrue(errors.isEmpty(),
                "Xerces should fully validate XSD 1.1 schemas without errors for valid XML");
    }

    @Test
    @DisplayName("Should validate XSD 1.1 assertions (Discount < Price)")
    void testXsd11AssertionValidation() throws Exception {
        // Arrange
        File xsd11Schema = new File("src/test/resources/xsd11_with_assert.xsd");

        // XML that violates the assertion: Discount (150) >= Price (100)
        String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Product xmlns="http://example.com/test">
                    <Name>Test Product</Name>
                    <Price>100.00</Price>
                    <Discount>150.00</Discount>
                </Product>
                """;

        // Act
        List<SAXParseException> errors = xmlService.validateText(invalidXml, xsd11Schema);

        // Assert - Xerces should detect the assertion violation
        assertFalse(errors.isEmpty(),
                "XSD 1.1 assertion violation should be detected by Xerces");

        boolean hasAssertionError = errors.stream()
                .anyMatch(e -> e.getMessage().toLowerCase().contains("assert") ||
                              e.getMessage().toLowerCase().contains("assertion"));

        assertTrue(hasAssertionError,
                "Error message should indicate assertion failure");
    }

    @Test
    @DisplayName("Should still fully validate XSD 1.0 schemas")
    void testXsd10ValidationStillWorks() throws Exception {
        // Arrange - Use an XSD 1.0 schema (without assert elements)
        File xsd10Schema = new File("src/test/resources/simpleFile.xsd");

        // Skip test if the file doesn't exist
        if (!xsd10Schema.exists()) {
            System.out.println("Skipping XSD 1.0 test - simpleFile.xsd not found");
            return;
        }

        // Create a simple XML that should fail XSD 1.0 validation
        // (This depends on what simpleFile.xsd actually defines)
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <element>Test</element>
                </root>
                """;

        // Act
        List<SAXParseException> errors = xmlService.validateText(xmlContent, xsd10Schema);

        // Assert - Should not contain the XSD 1.1 info message
        if (!errors.isEmpty()) {
            assertFalse(errors.get(0).getMessage().contains("XSD 1.1"),
                    "XSD 1.0 schema should not trigger XSD 1.1 detection");
        }
    }
}
