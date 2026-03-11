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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests that schema location attributes with whitespace are properly trimmed.
 * <p>
 * Regression tests for: Trailing space in xsi:noNamespaceSchemaLocation causes
 * InvalidPathException on Windows because the space propagates into the cache file path.
 */
public class SchemaLocationTrimmingTest {

    private XmlService xmlService;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initRegistry() {
        // Eagerly resolve services to avoid recursive ConcurrentHashMap.computeIfAbsent
        // during XmlServiceImpl static initialization
        ServiceRegistry.initialize();
        ServiceRegistry.get(PropertiesService.class);
        ServiceRegistry.get(ConnectionService.class);
    }

    @BeforeEach
    void setUp() {
        xmlService = new XmlServiceImpl();
    }

    private File createXmlFile(String name, String content) throws IOException {
        File file = tempDir.resolve(name).toFile();
        Files.writeString(file.toPath(), content);
        return file;
    }

    private File createXsdFile(String name) throws IOException {
        return createXmlFile(name,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                        "  <xs:element name=\"root\" type=\"xs:string\"/>\n" +
                        "</xs:schema>");
    }

    // ---------------------------------------------------------------
    // xsi:noNamespaceSchemaLocation trimming
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("noNamespaceSchemaLocation trimming")
    class NoNamespaceSchemaLocationTests {

        @Test
        @DisplayName("Should trim trailing space from noNamespaceSchemaLocation URL")
        void trailingSpace() throws IOException {
            File xmlFile = createXmlFile("trailing.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation="https://example.org/schema.xsd ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("https://example.org/schema.xsd", result.get(),
                    "Trailing space must be trimmed from URL");
            assertFalse(result.get().contains(" "),
                    "Result must not contain any spaces");
        }

        @Test
        @DisplayName("Should trim leading space from noNamespaceSchemaLocation URL")
        void leadingSpace() throws IOException {
            File xmlFile = createXmlFile("leading.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation=" https://example.org/schema.xsd">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("https://example.org/schema.xsd", result.get(),
                    "Leading space must be trimmed from URL");
        }

        @Test
        @DisplayName("Should trim both leading and trailing spaces from noNamespaceSchemaLocation URL")
        void leadingAndTrailingSpaces() throws IOException {
            File xmlFile = createXmlFile("both-spaces.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation="  https://example.org/schema.xsd  ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("https://example.org/schema.xsd", result.get());
        }

        @Test
        @DisplayName("Should trim tab characters from noNamespaceSchemaLocation URL")
        void tabCharacters() throws IOException {
            File xmlFile = createXmlFile("tab.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                            "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            "      xsi:noNamespaceSchemaLocation=\"https://example.org/schema.xsd\t\">\n" +
                            "    <data>test</data>\n" +
                            "</root>\n");
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("https://example.org/schema.xsd", result.get(),
                    "Tab character must be trimmed");
        }

        @Test
        @DisplayName("Should trim spaces from noNamespaceSchemaLocation with local filename")
        void localFilenameWithTrailingSpace() throws IOException {
            createXsdFile("local.xsd");

            File xmlFile = createXmlFile("local-trailing.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation="local.xsd ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent(), "Schema location should be found");
            assertTrue(result.get().startsWith("file:"),
                    "Local schema should be resolved to file URL, got: " + result.get());
            assertTrue(result.get().contains("local.xsd"),
                    "Should reference the correct schema file");
        }

        @Test
        @DisplayName("Should return trimmed value for non-existent local noNamespaceSchemaLocation")
        void nonExistentLocalFileWithTrailingSpace() throws IOException {
            File xmlFile = createXmlFile("missing-trailing.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation="missing-schema.xsd ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("missing-schema.xsd", result.get(),
                    "Returned filename must be trimmed");
        }
    }

    // ---------------------------------------------------------------
    // xsi:schemaLocation trimming
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("schemaLocation trimming")
    class SchemaLocationTests {

        @Test
        @DisplayName("Should trim trailing space from schemaLocation URL part")
        void trailingSpaceOnUrl() throws IOException {
            File xmlFile = createXmlFile("sl-trailing.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns="http://example.org/ns"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://example.org/ns https://example.org/schema.xsd ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("https://example.org/schema.xsd", result.get(),
                    "Trailing space must be trimmed from schemaLocation URL");
        }

        @Test
        @DisplayName("Should trim spaces from schemaLocation with non-existent local filename")
        void localFilenameWithTrailingSpace() throws IOException {
            File xmlFile = createXmlFile("sl-local-trailing.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns="http://example.org/ns"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://example.org/ns missing.xsd ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("missing.xsd", result.get(),
                    "Trailing space must be trimmed from local filename");
        }

        @Test
        @DisplayName("Should resolve local schemaLocation file even with trailing space")
        void localFileWithTrailingSpace() throws IOException {
            createXsdFile("schema.xsd");

            File xmlFile = createXmlFile("sl-local-exists.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns="http://example.org/ns"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://example.org/ns schema.xsd ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertTrue(result.get().startsWith("file:"),
                    "Local schema should be resolved to file URL, got: " + result.get());
            assertTrue(result.get().contains("schema.xsd"));
        }

        @Test
        @DisplayName("Should handle schemaLocation with multiple spaces between parts")
        void multipleSpacesBetweenParts() throws IOException {
            File xmlFile = createXmlFile("sl-multi-space.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns="http://example.org/ns"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://example.org/ns   https://example.org/schema.xsd">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("https://example.org/schema.xsd", result.get(),
                    "Multiple spaces between namespace and URL should be handled");
        }
    }

    // ---------------------------------------------------------------
    // xmlns trimming
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("xmlns trimming")
    class XmlnsTests {

        @Test
        @DisplayName("Should trim trailing space from xmlns ending with .xsd")
        void trailingSpaceOnXmlns() throws IOException {
            File xmlFile = createXmlFile("xmlns-trailing.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns="http://example.org/schema.xsd ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent(),
                    "Schema location should be found even with trailing space in xmlns");
            assertEquals("http://example.org/schema.xsd", result.get(),
                    "Trailing space must be trimmed from xmlns value");
        }

        @Test
        @DisplayName("Should trim leading space from xmlns ending with .xsd")
        void leadingSpaceOnXmlns() throws IOException {
            File xmlFile = createXmlFile("xmlns-leading.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns=" http://example.org/schema.xsd">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent(),
                    "Schema location should be found even with leading space in xmlns");
            assertEquals("http://example.org/schema.xsd", result.get());
        }
    }

    // ---------------------------------------------------------------
    // Path-safety: values must be valid as file path components
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Path safety — returned values must not cause InvalidPathException")
    class PathSafetyTests {

        @Test
        @DisplayName("Returned URL must be usable with Path.of for cache filename extraction")
        void returnedUrlIsPathSafe() throws IOException {
            File xmlFile = createXmlFile("path-safe.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation="https://example.org/FundsXML.xsd ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();
            assertTrue(result.isPresent());

            // This is what FilenameUtils.getName does internally — should not throw
            String filename = org.apache.commons.io.FilenameUtils.getName(result.get());
            assertNotNull(filename);
            assertFalse(filename.isBlank(), "Filename should not be blank");
            assertFalse(filename.contains(" "), "Filename must not contain spaces");

            // This is what the cache path construction does — should not throw
            assertDoesNotThrow(() -> Path.of(System.getProperty("java.io.tmpdir"), filename),
                    "Extracted filename must be valid for Path.of");
        }

        @Test
        @DisplayName("Returned URL must be valid for URI construction")
        void returnedUrlIsValidUri() throws IOException {
            File xmlFile = createXmlFile("uri-safe.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation="https://example.org/schema.xsd ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();
            assertTrue(result.isPresent());

            assertDoesNotThrow(() -> new java.net.URI(result.get()),
                    "Returned value must be a valid URI (no trailing space)");
        }

        @Test
        @DisplayName("Returned local filename must not have trailing whitespace for File construction")
        void returnedFilenameIsSafe() throws IOException {
            File xmlFile = createXmlFile("file-safe.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation="nonexistent.xsd\t">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();
            assertTrue(result.isPresent());
            assertEquals("nonexistent.xsd", result.get());

            // On Windows, trailing whitespace in filenames causes InvalidPathException
            assertDoesNotThrow(() -> Path.of(System.getProperty("java.io.tmpdir"), result.get()),
                    "Filename must not have trailing whitespace that would fail on Windows");
        }
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle value that is only whitespace as empty")
        void whitespaceOnlyValue() throws IOException {
            File xmlFile = createXmlFile("whitespace-only.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation="   ">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            // A whitespace-only location is semantically empty
            if (result.isPresent()) {
                assertFalse(result.get().isBlank(),
                        "If present, result must not be blank");
            }
        }

        @Test
        @DisplayName("URL without trailing space should remain unchanged")
        void cleanUrlRemainsUnchanged() throws IOException {
            File xmlFile = createXmlFile("clean.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:noNamespaceSchemaLocation="https://example.org/schema.xsd">
                        <data>test</data>
                    </root>
                    """);
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("https://example.org/schema.xsd", result.get(),
                    "Clean URL should remain unchanged");
        }

        @Test
        @DisplayName("Should handle newline in schema location value")
        void newlineInSchemaLocation() throws IOException {
            // Some XML formatters may wrap long attribute values
            File xmlFile = createXmlFile("newline.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                            "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            "      xsi:noNamespaceSchemaLocation=\"https://example.org/schema.xsd\n\">\n" +
                            "    <data>test</data>\n" +
                            "</root>\n");
            xmlService.setCurrentXmlFile(xmlFile);

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertTrue(result.isPresent());
            assertEquals("https://example.org/schema.xsd", result.get(),
                    "Newline characters must be trimmed");
        }

        @Test
        @DisplayName("Should handle missing XML file gracefully")
        void missingXmlFile() {
            xmlService.setCurrentXmlFile(new File("/nonexistent/path/file.xml"));

            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertFalse(result.isPresent(),
                    "Should return empty for non-existent file");
        }

        @Test
        @DisplayName("Should handle null XML file gracefully")
        void nullXmlFile() {
            Optional<String> result = xmlService.getSchemaNameFromCurrentXMLFile();

            assertFalse(result.isPresent(),
                    "Should return empty when no file is set");
        }
    }
}
