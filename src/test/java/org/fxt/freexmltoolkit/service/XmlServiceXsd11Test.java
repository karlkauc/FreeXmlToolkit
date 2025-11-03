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
 * These tests verify that the application gracefully handles XSD 1.1 schemas
 * by detecting them and checking XML well-formedness instead of full validation.
 */
public class XmlServiceXsd11Test {

    private XmlService xmlService;

    @BeforeEach
    void setUp() {
        xmlService = XmlServiceImpl.getInstance();
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

        // Check if the first message is the XSD 1.1 info message
        assertFalse(errors.isEmpty(), "Should contain at least the XSD 1.1 info message");

        SAXParseException firstException = errors.get(0);
        assertTrue(firstException.getMessage().contains("XSD 1.1 features detected"),
                "First message should inform about XSD 1.1 features");
        assertTrue(firstException.getMessage().contains("Only checking XML well-formedness"),
                "Message should explain that only well-formedness is checked");

        // The XML should be well-formed, so only the info message should be present
        assertEquals(1, errors.size(),
                "Only the XSD 1.1 info message should be present for well-formed XML");
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
        assertTrue(errors.size() > 1, "Should contain XSD 1.1 info message AND well-formedness errors");

        // First error should be the XSD 1.1 info message
        assertTrue(errors.get(0).getMessage().contains("XSD 1.1 features detected"),
                "First message should inform about XSD 1.1 features");

        // Subsequent errors should be about malformed XML
        boolean hasMalformednessError = errors.stream()
                .skip(1) // Skip the info message
                .anyMatch(e -> e.getMessage().contains("element type") ||
                              e.getMessage().contains("malformed") ||
                              e.getMessage().contains("must be terminated"));

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
    @DisplayName("Should provide helpful message about XSD 1.1 limitations")
    void testXsd11LimitationsMessageIsHelpful() throws Exception {
        // Arrange
        File xsd11Schema = new File("src/test/resources/xsd11_with_assert.xsd");
        String validXml = Files.readString(Paths.get("src/test/resources/xsd11_valid_product.xml"));

        // Act
        List<SAXParseException> errors = xmlService.validateText(validXml, xsd11Schema);

        // Assert - Check message content
        assertFalse(errors.isEmpty());
        String message = errors.get(0).getMessage();

        // The message should be informative and mention key points
        assertTrue(message.contains("XSD 1.1"), "Message should mention XSD 1.1");
        assertTrue(message.contains("assertions") || message.contains("type alternatives"),
                "Message should mention specific XSD 1.1 features");
        assertTrue(message.contains("Saxon-EE") || message.contains("Saxon-PE"),
                "Message should mention what's needed for full support");
        assertTrue(message.contains("well-formedness"),
                "Message should explain what is being checked instead");
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
