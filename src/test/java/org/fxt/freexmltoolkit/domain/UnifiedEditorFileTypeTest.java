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

package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnifiedEditorFileType")
class UnifiedEditorFileTypeTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("All enum values exist")
    void allValuesExist() {
        assertEquals(5, UnifiedEditorFileType.values().length);
        assertNotNull(UnifiedEditorFileType.XML);
        assertNotNull(UnifiedEditorFileType.XSD);
        assertNotNull(UnifiedEditorFileType.XSLT);
        assertNotNull(UnifiedEditorFileType.SCHEMATRON);
        assertNotNull(UnifiedEditorFileType.JSON);
    }

    @ParameterizedTest
    @EnumSource(UnifiedEditorFileType.class)
    @DisplayName("All file types have icons")
    void allHaveIcons(UnifiedEditorFileType fileType) {
        assertNotNull(fileType.getIcon());
        assertTrue(fileType.getIcon().startsWith("bi-"));
    }

    @ParameterizedTest
    @EnumSource(UnifiedEditorFileType.class)
    @DisplayName("All file types have colors")
    void allHaveColors(UnifiedEditorFileType fileType) {
        assertNotNull(fileType.getColor());
        assertTrue(fileType.getColor().startsWith("#"));
    }

    @ParameterizedTest
    @EnumSource(UnifiedEditorFileType.class)
    @DisplayName("All file types have style classes")
    void allHaveStyleClasses(UnifiedEditorFileType fileType) {
        assertNotNull(fileType.getStyleClass());
        assertTrue(fileType.getStyleClass().endsWith("-tab"));
    }

    @ParameterizedTest
    @EnumSource(UnifiedEditorFileType.class)
    @DisplayName("All file types have extensions")
    void allHaveExtensions(UnifiedEditorFileType fileType) {
        assertNotNull(fileType.getExtensions());
        assertFalse(fileType.getExtensions().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(UnifiedEditorFileType.class)
    @DisplayName("All file types have display names")
    void allHaveDisplayNames(UnifiedEditorFileType fileType) {
        assertNotNull(fileType.getDisplayName());
        assertFalse(fileType.getDisplayName().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(UnifiedEditorFileType.class)
    @DisplayName("All file types have default extensions")
    void allHaveDefaultExtensions(UnifiedEditorFileType fileType) {
        assertNotNull(fileType.getDefaultExtension());
        assertFalse(fileType.getDefaultExtension().isEmpty());
        assertFalse(fileType.getDefaultExtension().startsWith(".")); // No dot
    }

    @Nested
    @DisplayName("XML file type")
    class XmlTests {

        @Test
        @DisplayName("Has correct properties")
        void hasCorrectProperties() {
            UnifiedEditorFileType xml = UnifiedEditorFileType.XML;

            assertEquals("bi-file-earmark-code", xml.getIcon());
            assertEquals("#007bff", xml.getColor());
            assertEquals("xml-tab", xml.getStyleClass());
            assertTrue(xml.getExtensions().contains("xml"));
            assertEquals("XML", xml.getDisplayName());
            assertEquals("xml", xml.getDefaultExtension());
        }
    }

    @Nested
    @DisplayName("XSD file type")
    class XsdTests {

        @Test
        @DisplayName("Has correct properties")
        void hasCorrectProperties() {
            UnifiedEditorFileType xsd = UnifiedEditorFileType.XSD;

            assertEquals("bi-diagram-3", xsd.getIcon());
            assertEquals("#28a745", xsd.getColor());
            assertEquals("xsd-tab", xsd.getStyleClass());
            assertTrue(xsd.getExtensions().contains("xsd"));
            assertEquals("XSD Schema", xsd.getDisplayName());
            assertEquals("xsd", xsd.getDefaultExtension());
        }
    }

    @Nested
    @DisplayName("XSLT file type")
    class XsltTests {

        @Test
        @DisplayName("Has correct properties")
        void hasCorrectProperties() {
            UnifiedEditorFileType xslt = UnifiedEditorFileType.XSLT;

            assertEquals("bi-arrow-repeat", xslt.getIcon());
            assertEquals("#fd7e14", xslt.getColor());
            assertEquals("xslt-tab", xslt.getStyleClass());
            assertTrue(xslt.getExtensions().contains("xsl"));
            assertTrue(xslt.getExtensions().contains("xslt"));
            assertEquals("XSLT Stylesheet", xslt.getDisplayName());
            assertEquals("xslt", xslt.getDefaultExtension());
        }
    }

    @Nested
    @DisplayName("SCHEMATRON file type")
    class SchematronTests {

        @Test
        @DisplayName("Has correct properties")
        void hasCorrectProperties() {
            UnifiedEditorFileType sch = UnifiedEditorFileType.SCHEMATRON;

            assertEquals("bi-shield-check", sch.getIcon());
            assertEquals("#6f42c1", sch.getColor());
            assertEquals("schematron-tab", sch.getStyleClass());
            assertTrue(sch.getExtensions().contains("sch"));
            assertTrue(sch.getExtensions().contains("schematron"));
            assertEquals("Schematron", sch.getDisplayName());
            assertEquals("sch", sch.getDefaultExtension());
        }
    }

    @Nested
    @DisplayName("JSON file type")
    class JsonTests {

        @Test
        @DisplayName("Has correct properties")
        void hasCorrectProperties() {
            UnifiedEditorFileType json = UnifiedEditorFileType.JSON;

            assertEquals("bi-braces", json.getIcon());
            assertEquals("#f57c00", json.getColor());
            assertEquals("json-tab", json.getStyleClass());
            assertTrue(json.getExtensions().contains("json"));
            assertTrue(json.getExtensions().contains("jsonc"));
            assertTrue(json.getExtensions().contains("json5"));
            assertEquals("JSON", json.getDisplayName());
            assertEquals("json", json.getDefaultExtension());
        }
    }

    @Nested
    @DisplayName("fromFile")
    class FromFileTests {

        @Test
        @DisplayName("Returns XML for null file")
        void returnsXmlForNullFile() {
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFile(null));
        }

        @Test
        @DisplayName("Returns XML for file with null name")
        void returnsXmlForFileWithNullName() {
            File file = new File("") {
                @Override
                public String getName() {
                    return null;
                }
            };
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFile(file));
        }

        @Test
        @DisplayName("Returns correct type for .xml file")
        void returnsCorrectTypeForXmlFile() throws IOException {
            File file = tempDir.resolve("test.xml").toFile();
            file.createNewFile();
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFile(file));
        }

        @Test
        @DisplayName("Returns correct type for .xsd file")
        void returnsCorrectTypeForXsdFile() throws IOException {
            File file = tempDir.resolve("schema.xsd").toFile();
            file.createNewFile();
            assertEquals(UnifiedEditorFileType.XSD, UnifiedEditorFileType.fromFile(file));
        }

        @Test
        @DisplayName("Returns correct type for .xslt file")
        void returnsCorrectTypeForXsltFile() throws IOException {
            File file = tempDir.resolve("transform.xslt").toFile();
            file.createNewFile();
            assertEquals(UnifiedEditorFileType.XSLT, UnifiedEditorFileType.fromFile(file));
        }

        @Test
        @DisplayName("Returns correct type for .xsl file")
        void returnsCorrectTypeForXslFile() throws IOException {
            File file = tempDir.resolve("transform.xsl").toFile();
            file.createNewFile();
            assertEquals(UnifiedEditorFileType.XSLT, UnifiedEditorFileType.fromFile(file));
        }

        @Test
        @DisplayName("Returns correct type for .sch file")
        void returnsCorrectTypeForSchFile() throws IOException {
            File file = tempDir.resolve("rules.sch").toFile();
            file.createNewFile();
            assertEquals(UnifiedEditorFileType.SCHEMATRON, UnifiedEditorFileType.fromFile(file));
        }

        @Test
        @DisplayName("Returns correct type for .schematron file")
        void returnsCorrectTypeForSchematronFile() throws IOException {
            File file = tempDir.resolve("rules.schematron").toFile();
            file.createNewFile();
            assertEquals(UnifiedEditorFileType.SCHEMATRON, UnifiedEditorFileType.fromFile(file));
        }

        @Test
        @DisplayName("Returns correct type for .json file")
        void returnsCorrectTypeForJsonFile() throws IOException {
            File file = tempDir.resolve("data.json").toFile();
            file.createNewFile();
            assertEquals(UnifiedEditorFileType.JSON, UnifiedEditorFileType.fromFile(file));
        }

        @Test
        @DisplayName("Returns correct type for .jsonc file")
        void returnsCorrectTypeForJsoncFile() throws IOException {
            File file = tempDir.resolve("config.jsonc").toFile();
            file.createNewFile();
            assertEquals(UnifiedEditorFileType.JSON, UnifiedEditorFileType.fromFile(file));
        }

        @Test
        @DisplayName("Returns correct type for .json5 file")
        void returnsCorrectTypeForJson5File() throws IOException {
            File file = tempDir.resolve("modern.json5").toFile();
            file.createNewFile();
            assertEquals(UnifiedEditorFileType.JSON, UnifiedEditorFileType.fromFile(file));
        }
    }

    @Nested
    @DisplayName("fromFileName")
    class FromFileNameTests {

        @Test
        @DisplayName("Returns XML for null filename")
        void returnsXmlForNullFilename() {
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFileName(null));
        }

        @Test
        @DisplayName("Returns XML for empty filename")
        void returnsXmlForEmptyFilename() {
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFileName(""));
        }

        @Test
        @DisplayName("Returns XML for filename without extension")
        void returnsXmlForFilenameWithoutExtension() {
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFileName("noextension"));
        }

        @Test
        @DisplayName("Returns XML for filename ending with dot")
        void returnsXmlForFilenameEndingWithDot() {
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFileName("file."));
        }

        @ParameterizedTest
        @ValueSource(strings = {"test.xml", "TEST.XML", "Test.Xml", "file.XML"})
        @DisplayName("Returns XML for various XML filenames (case insensitive)")
        void returnsXmlForVariousXmlFilenames(String filename) {
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFileName(filename));
        }

        @ParameterizedTest
        @ValueSource(strings = {"schema.xsd", "SCHEMA.XSD", "Types.XSD"})
        @DisplayName("Returns XSD for various XSD filenames (case insensitive)")
        void returnsXsdForVariousXsdFilenames(String filename) {
            assertEquals(UnifiedEditorFileType.XSD, UnifiedEditorFileType.fromFileName(filename));
        }

        @ParameterizedTest
        @ValueSource(strings = {"transform.xsl", "TRANSFORM.XSLT", "style.xslt"})
        @DisplayName("Returns XSLT for various XSLT filenames (case insensitive)")
        void returnsXsltForVariousXsltFilenames(String filename) {
            assertEquals(UnifiedEditorFileType.XSLT, UnifiedEditorFileType.fromFileName(filename));
        }

        @ParameterizedTest
        @ValueSource(strings = {"rules.sch", "RULES.SCHEMATRON", "validation.sch"})
        @DisplayName("Returns SCHEMATRON for various Schematron filenames (case insensitive)")
        void returnsSchematronForVariousSchematronFilenames(String filename) {
            assertEquals(UnifiedEditorFileType.SCHEMATRON, UnifiedEditorFileType.fromFileName(filename));
        }

        @Test
        @DisplayName("Returns XML for unknown extension")
        void returnsXmlForUnknownExtension() {
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFileName("file.txt"));
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFileName("file.html"));
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFileName("file.csv"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"data.json", "CONFIG.JSON", "test.jsonc", "modern.json5"})
        @DisplayName("Returns JSON for various JSON filenames (case insensitive)")
        void returnsJsonForVariousJsonFilenames(String filename) {
            assertEquals(UnifiedEditorFileType.JSON, UnifiedEditorFileType.fromFileName(filename));
        }

        @Test
        @DisplayName("Handles filenames with multiple dots")
        void handlesFilenamesWithMultipleDots() {
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.fromFileName("my.file.name.xml"));
            assertEquals(UnifiedEditorFileType.XSD, UnifiedEditorFileType.fromFileName("schema.v2.xsd"));
            assertEquals(UnifiedEditorFileType.XSLT, UnifiedEditorFileType.fromFileName("transform.2024.01.xslt"));
        }
    }

    @Nested
    @DisplayName("valueOf")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf returns correct enum")
        void valueOfReturnsCorrectEnum() {
            assertEquals(UnifiedEditorFileType.XML, UnifiedEditorFileType.valueOf("XML"));
            assertEquals(UnifiedEditorFileType.XSD, UnifiedEditorFileType.valueOf("XSD"));
            assertEquals(UnifiedEditorFileType.XSLT, UnifiedEditorFileType.valueOf("XSLT"));
            assertEquals(UnifiedEditorFileType.SCHEMATRON, UnifiedEditorFileType.valueOf("SCHEMATRON"));
            assertEquals(UnifiedEditorFileType.JSON, UnifiedEditorFileType.valueOf("JSON"));
        }

        @Test
        @DisplayName("valueOf throws for invalid name")
        void valueOfThrowsForInvalidName() {
            assertThrows(IllegalArgumentException.class, () ->
                    UnifiedEditorFileType.valueOf("INVALID"));
        }
    }
}
