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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileFavorite")
class FileFavoriteTest {

    private FileFavorite favorite;

    @BeforeEach
    void setUp() {
        favorite = new FileFavorite();
    }

    @Nested
    @DisplayName("FileType Enum")
    class FileTypeEnumTests {

        @Test
        @DisplayName("All seven file types are defined")
        void allFileTypesAreDefined() {
            assertEquals(7, FileFavorite.FileType.values().length);
        }

        @ParameterizedTest
        @EnumSource(FileFavorite.FileType.class)
        @DisplayName("All file types have display names")
        void allFileTypesHaveDisplayNames(FileFavorite.FileType type) {
            assertNotNull(type.getDisplayName());
            assertFalse(type.getDisplayName().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(FileFavorite.FileType.class)
        @DisplayName("All file types have icon literals")
        void allFileTypesHaveIconLiterals(FileFavorite.FileType type) {
            assertNotNull(type.getIconLiteral());
            assertTrue(type.getIconLiteral().startsWith("bi-"));
        }

        @ParameterizedTest
        @EnumSource(FileFavorite.FileType.class)
        @DisplayName("All file types have default colors")
        void allFileTypesHaveDefaultColors(FileFavorite.FileType type) {
            assertNotNull(type.getDefaultColor());
            assertTrue(type.getDefaultColor().startsWith("#"));
        }

        @Test
        @DisplayName("XML type has correct properties")
        void xmlTypeHasCorrectProperties() {
            assertEquals("XML Document", FileFavorite.FileType.XML.getDisplayName());
            assertEquals("bi-file-earmark-code", FileFavorite.FileType.XML.getIconLiteral());
            assertEquals("#28a745", FileFavorite.FileType.XML.getDefaultColor());
        }

        @Test
        @DisplayName("XSD type has correct properties")
        void xsdTypeHasCorrectProperties() {
            assertEquals("XSD Schema", FileFavorite.FileType.XSD.getDisplayName());
            assertEquals("bi-file-earmark-check", FileFavorite.FileType.XSD.getIconLiteral());
            assertEquals("#007bff", FileFavorite.FileType.XSD.getDefaultColor());
        }

        @Test
        @DisplayName("SCHEMATRON type has correct properties")
        void schematronTypeHasCorrectProperties() {
            assertEquals("Schematron Rules", FileFavorite.FileType.SCHEMATRON.getDisplayName());
            assertEquals("bi-file-earmark-ruled", FileFavorite.FileType.SCHEMATRON.getIconLiteral());
            assertEquals("#dc3545", FileFavorite.FileType.SCHEMATRON.getDefaultColor());
        }

        @Test
        @DisplayName("XSLT type has correct properties")
        void xsltTypeHasCorrectProperties() {
            assertEquals("XSLT Stylesheet", FileFavorite.FileType.XSLT.getDisplayName());
            assertEquals("bi-file-earmark-arrow-right", FileFavorite.FileType.XSLT.getIconLiteral());
            assertEquals("#fd7e14", FileFavorite.FileType.XSLT.getDefaultColor());
        }
    }

    @Nested
    @DisplayName("fromExtension")
    class FromExtensionTests {

        @ParameterizedTest
        @CsvSource({
                "test.xml, XML",
                "TEST.XML, XML",
                "schema.xsd, XSD",
                "SCHEMA.XSD, XSD",
                "rules.sch, SCHEMATRON",
                "transform.xsl, XSLT",
                "transform.xslt, XSLT",
                "unknown.txt, OTHER",
                "noext, OTHER"
        })
        @DisplayName("Correctly detects file type from extension")
        void correctlyDetectsFileTypeFromExtension(String filePath, String expectedType) {
            assertEquals(
                    FileFavorite.FileType.valueOf(expectedType),
                    FileFavorite.FileType.fromExtension(filePath)
            );
        }

        @Test
        @DisplayName("Returns OTHER for null path")
        void returnsOtherForNullPath() {
            assertEquals(FileFavorite.FileType.OTHER, FileFavorite.FileType.fromExtension(null));
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor sets ID and addedDate")
        void defaultConstructorSetsIdAndAddedDate() {
            FileFavorite newFav = new FileFavorite();

            assertNotNull(newFav.getId());
            assertFalse(newFav.getId().isEmpty());
            assertNotNull(newFav.getAddedDate());
        }

        @Test
        @DisplayName("Two-arg constructor sets name, path, and auto-detects type")
        void twoArgConstructorSetsNamePathAndType() {
            FileFavorite fav = new FileFavorite("My Schema", "/path/to/schema.xsd");

            assertEquals("My Schema", fav.getName());
            assertEquals("/path/to/schema.xsd", fav.getFilePath());
            assertEquals(FileFavorite.FileType.XSD, fav.getFileType());
            assertEquals("#007bff", fav.getIconColor());
        }

        @Test
        @DisplayName("Three-arg constructor sets folder name")
        void threeArgConstructorSetsFolderName() {
            FileFavorite fav = new FileFavorite("My XML", "/path/to/file.xml", "Project A");

            assertEquals("My XML", fav.getName());
            assertEquals("/path/to/file.xml", fav.getFilePath());
            assertEquals("Project A", fav.getFolderName());
            assertEquals(FileFavorite.FileType.XML, fav.getFileType());
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("Set and get ID")
        void setAndGetId() {
            favorite.setId("custom-id-123");
            assertEquals("custom-id-123", favorite.getId());
        }

        @Test
        @DisplayName("Set and get name")
        void setAndGetName() {
            favorite.setName("FundsXML Schema");
            assertEquals("FundsXML Schema", favorite.getName());
        }

        @Test
        @DisplayName("setFilePath also sets fileType")
        void setFilePathAlsoSetsFileType() {
            favorite.setFilePath("/path/to/stylesheet.xslt");

            assertEquals("/path/to/stylesheet.xslt", favorite.getFilePath());
            assertEquals(FileFavorite.FileType.XSLT, favorite.getFileType());
        }

        @Test
        @DisplayName("Set and get description")
        void setAndGetDescription() {
            favorite.setDescription("Main schema for fund data");
            assertEquals("Main schema for fund data", favorite.getDescription());
        }

        @Test
        @DisplayName("Set and get icon color")
        void setAndGetIconColor() {
            favorite.setIconColor("#ff0000");
            assertEquals("#ff0000", favorite.getIconColor());
        }

        @Test
        @DisplayName("Set and get alias")
        void setAndGetAlias() {
            favorite.setAlias("Main Schema");
            assertEquals("Main Schema", favorite.getAlias());
        }

        @Test
        @DisplayName("Set and get category")
        void setAndGetCategory() {
            favorite.setCategory("Production");
            assertEquals("Production", favorite.getCategory());
        }

        @Test
        @DisplayName("getLastAccessed returns addedDate as fallback")
        void getLastAccessedReturnsAddedDateAsFallback() {
            assertNotNull(favorite.getLastAccessed());
            assertEquals(favorite.getAddedDate(), favorite.getLastAccessed());
        }

        @Test
        @DisplayName("Set and get lastAccessed")
        void setAndGetLastAccessed() {
            LocalDateTime now = LocalDateTime.now();
            favorite.setLastAccessed(now);
            assertEquals(now, favorite.getLastAccessed());
        }

        @Test
        @DisplayName("Set and get accessCount")
        void setAndGetAccessCount() {
            favorite.setAccessCount(42);
            assertEquals(42, favorite.getAccessCount());
        }

        @Test
        @DisplayName("Set and get fileSize")
        void setAndGetFileSize() {
            favorite.setFileSize(1024 * 1024); // 1 MB
            assertEquals(1024 * 1024, favorite.getFileSize());
        }

        @Test
        @DisplayName("Set and get checksum")
        void setAndGetChecksum() {
            favorite.setChecksum("abc123def456");
            assertEquals("abc123def456", favorite.getChecksum());
        }

        @Test
        @DisplayName("Set and get notes")
        void setAndGetNotes() {
            favorite.setNotes("Important schema for quarterly reports");
            assertEquals("Important schema for quarterly reports", favorite.getNotes());
        }

        @Test
        @DisplayName("Set and get tags")
        void setAndGetTags() {
            String[] tags = {"production", "fundsxml", "v4"};
            favorite.setTags(tags);
            assertArrayEquals(tags, favorite.getTags());
        }

        @Test
        @DisplayName("Set and get projectName")
        void setAndGetProjectName() {
            favorite.setProjectName("FundsXML Project");
            assertEquals("FundsXML Project", favorite.getProjectName());
        }

        @Test
        @DisplayName("Set and get relatedFiles")
        void setAndGetRelatedFiles() {
            String[] related = {"/path/to/schema.xsd", "/path/to/transform.xslt"};
            favorite.setRelatedFiles(related);
            assertArrayEquals(related, favorite.getRelatedFiles());
        }

        @Test
        @DisplayName("Set and get isTemplate")
        void setAndGetIsTemplate() {
            favorite.setTemplate(true);
            assertTrue(favorite.isTemplate());

            favorite.setTemplate(false);
            assertFalse(favorite.isTemplate());
        }

        @Test
        @DisplayName("Set and get isPinned")
        void setAndGetIsPinned() {
            favorite.setPinned(true);
            assertTrue(favorite.isPinned());

            favorite.setPinned(false);
            assertFalse(favorite.isPinned());
        }

        @Test
        @DisplayName("Set and get colorCode")
        void setAndGetColorCode() {
            favorite.setColorCode(5);
            assertEquals(5, favorite.getColorCode());
        }

        @Test
        @DisplayName("Set and get validationStatus")
        void setAndGetValidationStatus() {
            favorite.setValidationStatus("Valid");
            assertEquals("Valid", favorite.getValidationStatus());
        }

        @Test
        @DisplayName("dateAdded alias works correctly")
        void dateAddedAliasWorksCorrectly() {
            LocalDateTime date = LocalDateTime.of(2024, 1, 15, 10, 30);
            favorite.setDateAdded(date);
            assertEquals(date, favorite.getDateAdded());
            assertEquals(date, favorite.getAddedDate());
        }
    }

    @Nested
    @DisplayName("fileExists")
    class FileExistsTests {

        @Test
        @DisplayName("Returns false for null path")
        void returnsFalseForNullPath() {
            favorite.setFilePath(null);
            assertFalse(favorite.fileExists());
        }

        @Test
        @DisplayName("Returns false for non-existent file")
        void returnsFalseForNonExistentFile() {
            favorite.setFilePath("/non/existent/path/file.xml");
            assertFalse(favorite.fileExists());
        }
    }

    @Nested
    @DisplayName("getFileName")
    class GetFileNameTests {

        @Test
        @DisplayName("Returns empty string for null path")
        void returnsEmptyStringForNullPath() {
            favorite.setFilePath(null);
            assertEquals("", favorite.getFileName());
        }

        @Test
        @DisplayName("Extracts file name from path")
        void extractsFileNameFromPath() {
            favorite.setFilePath("/path/to/schema.xsd");
            assertEquals("schema.xsd", favorite.getFileName());
        }

        @Test
        @DisplayName("Works with simple file name")
        void worksWithSimpleFileName() {
            favorite.setFilePath("schema.xsd");
            assertEquals("schema.xsd", favorite.getFileName());
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("Create favorite for FundsXML schema")
        void createFavoriteForFundsXmlSchema() {
            FileFavorite fundsXml = new FileFavorite(
                    "FundsXML 4.0 Schema",
                    "/schemas/FundsXML4.xsd",
                    "FundsXML Project"
            );
            fundsXml.setDescription("Main FundsXML 4.0 schema definition");
            fundsXml.setCategory("Production");
            fundsXml.setPinned(true);
            fundsXml.setTags(new String[]{"fundsxml", "v4", "production"});

            assertEquals(FileFavorite.FileType.XSD, fundsXml.getFileType());
            assertTrue(fundsXml.isPinned());
            assertEquals(3, fundsXml.getTags().length);
        }

        @Test
        @DisplayName("Create favorite for XSLT transform")
        void createFavoriteForXsltTransform() {
            FileFavorite transform = new FileFavorite(
                    "PDF Export Transform",
                    "/transforms/to-pdf.xslt"
            );
            transform.setTemplate(true);
            transform.setRelatedFiles(new String[]{"/schemas/input.xsd", "/schemas/output.xsd"});

            assertEquals(FileFavorite.FileType.XSLT, transform.getFileType());
            assertTrue(transform.isTemplate());
            assertEquals(2, transform.getRelatedFiles().length);
        }

        @Test
        @DisplayName("Track access statistics")
        void trackAccessStatistics() {
            FileFavorite tracked = new FileFavorite("Frequently Used", "/data/common.xml");
            tracked.setAccessCount(100);
            tracked.setLastAccessed(LocalDateTime.now());
            tracked.setFileSize(50 * 1024); // 50 KB

            assertEquals(100, tracked.getAccessCount());
            assertEquals(50 * 1024, tracked.getFileSize());
        }
    }
}
