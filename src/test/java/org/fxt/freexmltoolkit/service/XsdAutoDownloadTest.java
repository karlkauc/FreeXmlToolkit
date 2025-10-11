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
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for automatic XSD download functionality when loading XML files.
 */
public class XsdAutoDownloadTest {

    private XmlService xmlService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        xmlService = new XmlServiceImpl();
    }

    @Test
    @DisplayName("Should extract XSD URL from xsi:schemaLocation with namespace and URL")
    void testSchemaLocationWithUrlExtraction() throws IOException {
        // Create XML file with remote XSD reference
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <funds xmlns="http://www.fundsxml.org/XMLSchema/4.2.2"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.fundsxml.org/XMLSchema/4.2.2 https://www.fundsxml.org/schemas/FundsXML4.2.2.xsd">
                    <fund>
                        <fundname>Test Fund</fundname>
                    </fund>
                </funds>
                """;

        File xmlFile = tempDir.resolve("test-schema-url.xml").toFile();
        Files.writeString(xmlFile.toPath(), xmlContent);

        // Set current XML file
        xmlService.setCurrentXmlFile(xmlFile);

        // Get schema location
        Optional<String> schemaLocation = xmlService.getSchemaNameFromCurrentXMLFile();

        assertTrue(schemaLocation.isPresent(), "Schema location should be found");
        assertEquals("https://www.fundsxml.org/schemas/FundsXML4.2.2.xsd", schemaLocation.get());
    }

    @Test
    @DisplayName("Should extract XSD URL from xsi:noNamespaceSchemaLocation")
    void testNoNamespaceSchemaLocation() throws IOException {
        // Create XML file with noNamespaceSchemaLocation
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <funds xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:noNamespaceSchemaLocation="https://www.fundsxml.org/schemas/FundsXML4.2.2.xsd">
                    <fund>
                        <fundname>Test Fund</fundname>
                    </fund>
                </funds>
                """;

        File xmlFile = tempDir.resolve("test-no-namespace.xml").toFile();
        Files.writeString(xmlFile.toPath(), xmlContent);

        // Set current XML file
        xmlService.setCurrentXmlFile(xmlFile);

        // Get schema location
        Optional<String> schemaLocation = xmlService.getSchemaNameFromCurrentXMLFile();

        assertTrue(schemaLocation.isPresent(), "Schema location should be found");
        assertEquals("https://www.fundsxml.org/schemas/FundsXML4.2.2.xsd", schemaLocation.get());
    }

    @Test
    @DisplayName("Should find local XSD file when referenced in xsi:schemaLocation")
    void testLocalSchemaFileFound() throws IOException {
        // Create a local XSD file
        File localXsd = tempDir.resolve("local-schema.xsd").toFile();
        Files.writeString(localXsd.toPath(),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                        "</xs:schema>");

        // Create XML file that references local XSD
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <funds xmlns="http://example.org/schema"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://example.org/schema local-schema.xsd">
                    <fund>
                        <fundname>Test Fund</fundname>
                    </fund>
                </funds>
                """;

        File xmlFile = tempDir.resolve("test-local-schema.xml").toFile();
        Files.writeString(xmlFile.toPath(), xmlContent);

        // Set current XML file
        xmlService.setCurrentXmlFile(xmlFile);

        // Get schema location
        Optional<String> schemaLocation = xmlService.getSchemaNameFromCurrentXMLFile();

        assertTrue(schemaLocation.isPresent(), "Schema location should be found");
        assertTrue(schemaLocation.get().startsWith("file://"), "Should be a file URL for local schema");
        assertTrue(schemaLocation.get().contains("local-schema.xsd"), "Should reference the local schema file");
    }

    @Test
    @DisplayName("Should return filename when local XSD not found but filename contains dot")
    void testSchemaLocationWithFilenameContainingDot() throws IOException {
        // Create XML file that references non-existent local XSD with .xsd extension
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <funds xmlns="http://example.org/schema"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://example.org/schema missing-schema.xsd">
                    <fund>
                        <fundname>Test Fund</fundname>
                    </fund>
                </funds>
                """;

        File xmlFile = tempDir.resolve("test-missing-schema.xml").toFile();
        Files.writeString(xmlFile.toPath(), xmlContent);

        // Set current XML file
        xmlService.setCurrentXmlFile(xmlFile);

        // Get schema location
        Optional<String> schemaLocation = xmlService.getSchemaNameFromCurrentXMLFile();

        assertTrue(schemaLocation.isPresent(), "Schema location should be found (filename with dot)");
        assertEquals("missing-schema.xsd", schemaLocation.get());
    }

    @Test
    @DisplayName("Should not return filename when local XSD not found and filename has no dot")
    void testSchemaLocationWithPlainFilename() throws IOException {
        // Create XML file that references non-existent local file without extension
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <funds xmlns="http://example.org/schema"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://example.org/schema missingfile">
                    <fund>
                        <fundname>Test Fund</fundname>
                    </fund>
                </funds>
                """;

        File xmlFile = tempDir.resolve("test-plain-filename.xml").toFile();
        Files.writeString(xmlFile.toPath(), xmlContent);

        // Set current XML file
        xmlService.setCurrentXmlFile(xmlFile);

        // Get schema location
        Optional<String> schemaLocation = xmlService.getSchemaNameFromCurrentXMLFile();

        assertFalse(schemaLocation.isPresent(), "Schema location should not be found for plain filename without dot");
    }

    @Test
    @DisplayName("Should return empty when no schema references found")
    void testNoSchemaReferences() throws IOException {
        // Create XML file without any schema references
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <funds>
                    <fund>
                        <fundname>Test Fund</fundname>
                    </fund>
                </funds>
                """;

        File xmlFile = tempDir.resolve("test-no-schema.xml").toFile();
        Files.writeString(xmlFile.toPath(), xmlContent);

        // Set current XML file
        xmlService.setCurrentXmlFile(xmlFile);

        // Get schema location
        Optional<String> schemaLocation = xmlService.getSchemaNameFromCurrentXMLFile();

        assertFalse(schemaLocation.isPresent(), "No schema location should be found");
    }

    @Test
    @DisplayName("Should handle remote XSD location setup correctly")
    void testRemoteXsdLocationSetup() throws IOException {
        // Create XML file with remote XSD reference
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <funds xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:noNamespaceSchemaLocation="https://example.org/schema.xsd">
                    <fund>
                        <fundname>Test Fund</fundname>
                    </fund>
                </funds>
                """;

        File xmlFile = tempDir.resolve("test-remote-setup.xml").toFile();
        Files.writeString(xmlFile.toPath(), xmlContent);

        // Set current XML file (this should set remoteXsdLocation)
        xmlService.setCurrentXmlFile(xmlFile);

        // Check that remote XSD location is set
        String remoteLocation = xmlService.getRemoteXsdLocation();
        assertEquals("https://example.org/schema.xsd", remoteLocation);
    }
}