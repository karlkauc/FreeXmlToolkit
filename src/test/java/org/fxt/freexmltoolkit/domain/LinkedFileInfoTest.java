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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LinkedFileInfo")
class LinkedFileInfoTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("LinkType Enum")
    class LinkTypeTests {

        @Test
        @DisplayName("All link types have display names")
        void allTypesHaveDisplayNames() {
            for (LinkedFileInfo.LinkType type : LinkedFileInfo.LinkType.values()) {
                assertNotNull(type.getDisplayName());
                assertFalse(type.getDisplayName().isEmpty());
            }
        }

        @Test
        @DisplayName("XSD_SCHEMA_LOCATION has correct display name")
        void xsdSchemaLocationDisplayName() {
            assertEquals("Schema Location", LinkedFileInfo.LinkType.XSD_SCHEMA_LOCATION.getDisplayName());
        }

        @Test
        @DisplayName("XSD_IMPORT has correct display name")
        void xsdImportDisplayName() {
            assertEquals("XSD Import", LinkedFileInfo.LinkType.XSD_IMPORT.getDisplayName());
        }

        @Test
        @DisplayName("XSLT_INCLUDE has correct display name")
        void xsltIncludeDisplayName() {
            assertEquals("XSLT Include", LinkedFileInfo.LinkType.XSLT_INCLUDE.getDisplayName());
        }

        @Test
        @DisplayName("All link types exist")
        void allLinkTypesExist() {
            assertEquals(8, LinkedFileInfo.LinkType.values().length);
            assertNotNull(LinkedFileInfo.LinkType.XSD_SCHEMA_LOCATION);
            assertNotNull(LinkedFileInfo.LinkType.XSD_NO_NAMESPACE_LOCATION);
            assertNotNull(LinkedFileInfo.LinkType.XSD_IMPORT);
            assertNotNull(LinkedFileInfo.LinkType.XSD_INCLUDE);
            assertNotNull(LinkedFileInfo.LinkType.XSD_REDEFINE);
            assertNotNull(LinkedFileInfo.LinkType.XSLT_IMPORT);
            assertNotNull(LinkedFileInfo.LinkType.XSLT_INCLUDE);
            assertNotNull(LinkedFileInfo.LinkType.XML_STYLESHEET);
        }
    }

    @Nested
    @DisplayName("Record Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Creates record with all values")
        void createsRecordWithAllValues() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();
            File resolvedFile = tempDir.resolve("schema.xsd").toFile();
            resolvedFile.createNewFile();

            LinkedFileInfo info = new LinkedFileInfo(
                    sourceFile,
                    "schema.xsd",
                    resolvedFile,
                    LinkedFileInfo.LinkType.XSD_SCHEMA_LOCATION,
                    true,
                    "http://example.com/ns"
            );

            assertEquals(sourceFile, info.sourceFile());
            assertEquals("schema.xsd", info.referencePath());
            assertEquals(resolvedFile, info.resolvedFile());
            assertEquals(LinkedFileInfo.LinkType.XSD_SCHEMA_LOCATION, info.linkType());
            assertTrue(info.isResolved());
            assertEquals("http://example.com/ns", info.namespace());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("resolved creates resolved LinkedFileInfo")
        void resolvedCreatesResolvedInfo() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();
            File resolvedFile = tempDir.resolve("schema.xsd").toFile();
            resolvedFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.resolved(
                    sourceFile,
                    "schema.xsd",
                    resolvedFile,
                    LinkedFileInfo.LinkType.XSD_IMPORT
            );

            assertEquals(sourceFile, info.sourceFile());
            assertEquals("schema.xsd", info.referencePath());
            assertEquals(resolvedFile, info.resolvedFile());
            assertEquals(LinkedFileInfo.LinkType.XSD_IMPORT, info.linkType());
            assertTrue(info.isResolved());
            assertNull(info.namespace());
        }

        @Test
        @DisplayName("resolved with namespace creates resolved LinkedFileInfo")
        void resolvedWithNamespaceCreatesResolvedInfo() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();
            File resolvedFile = tempDir.resolve("types.xsd").toFile();
            resolvedFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.resolved(
                    sourceFile,
                    "types.xsd",
                    resolvedFile,
                    LinkedFileInfo.LinkType.XSD_IMPORT,
                    "http://example.com/types"
            );

            assertTrue(info.isResolved());
            assertEquals("http://example.com/types", info.namespace());
        }

        @Test
        @DisplayName("unresolved creates unresolved LinkedFileInfo")
        void unresolvedCreatesUnresolvedInfo() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "missing.xsd",
                    LinkedFileInfo.LinkType.XSD_INCLUDE
            );

            assertEquals(sourceFile, info.sourceFile());
            assertEquals("missing.xsd", info.referencePath());
            assertNull(info.resolvedFile());
            assertEquals(LinkedFileInfo.LinkType.XSD_INCLUDE, info.linkType());
            assertFalse(info.isResolved());
            assertNull(info.namespace());
        }

        @Test
        @DisplayName("unresolved with namespace creates unresolved LinkedFileInfo")
        void unresolvedWithNamespaceCreatesUnresolvedInfo() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "missing.xsd",
                    LinkedFileInfo.LinkType.XSD_IMPORT,
                    "http://example.com/missing"
            );

            assertFalse(info.isResolved());
            assertEquals("http://example.com/missing", info.namespace());
        }
    }

    @Nested
    @DisplayName("getDisplayName")
    class GetDisplayNameTests {

        @Test
        @DisplayName("Returns resolved file name when resolved")
        void returnsResolvedFileName() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();
            File resolvedFile = tempDir.resolve("schema.xsd").toFile();
            resolvedFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.resolved(
                    sourceFile,
                    "../schemas/schema.xsd",
                    resolvedFile,
                    LinkedFileInfo.LinkType.XSD_SCHEMA_LOCATION
            );

            assertEquals("schema.xsd", info.getDisplayName());
        }

        @Test
        @DisplayName("Returns extracted filename from path when unresolved")
        void returnsExtractedFilenameWhenUnresolved() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "../schemas/missing.xsd",
                    LinkedFileInfo.LinkType.XSD_INCLUDE
            );

            assertEquals("missing.xsd", info.getDisplayName());
        }

        @Test
        @DisplayName("Handles forward slashes in path")
        void handlesForwardSlashes() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "http://example.com/schemas/types.xsd",
                    LinkedFileInfo.LinkType.XSD_IMPORT
            );

            assertEquals("types.xsd", info.getDisplayName());
        }

        @Test
        @DisplayName("Handles backslashes in path")
        void handlesBackslashes() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "..\\schemas\\types.xsd",
                    LinkedFileInfo.LinkType.XSD_IMPORT
            );

            assertEquals("types.xsd", info.getDisplayName());
        }

        @Test
        @DisplayName("Returns full path when no slashes")
        void returnsFullPathWhenNoSlashes() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "schema.xsd",
                    LinkedFileInfo.LinkType.XSD_INCLUDE
            );

            assertEquals("schema.xsd", info.getDisplayName());
        }
    }

    @Nested
    @DisplayName("getTooltipText")
    class GetTooltipTextTests {

        @Test
        @DisplayName("Includes link type in tooltip")
        void includesLinkTypeInTooltip() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "schema.xsd",
                    LinkedFileInfo.LinkType.XSD_IMPORT
            );

            String tooltip = info.getTooltipText();
            assertTrue(tooltip.contains("XSD Import"));
        }

        @Test
        @DisplayName("Includes namespace when present")
        void includesNamespaceWhenPresent() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "types.xsd",
                    LinkedFileInfo.LinkType.XSD_IMPORT,
                    "http://example.com/types"
            );

            String tooltip = info.getTooltipText();
            assertTrue(tooltip.contains("Namespace: http://example.com/types"));
        }

        @Test
        @DisplayName("Includes path in tooltip")
        void includesPathInTooltip() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "../schemas/schema.xsd",
                    LinkedFileInfo.LinkType.XSD_INCLUDE
            );

            String tooltip = info.getTooltipText();
            assertTrue(tooltip.contains("Path: ../schemas/schema.xsd"));
        }

        @Test
        @DisplayName("Shows resolved path when resolved")
        void showsResolvedPathWhenResolved() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();
            File resolvedFile = tempDir.resolve("schema.xsd").toFile();
            resolvedFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.resolved(
                    sourceFile,
                    "schema.xsd",
                    resolvedFile,
                    LinkedFileInfo.LinkType.XSD_SCHEMA_LOCATION
            );

            String tooltip = info.getTooltipText();
            assertTrue(tooltip.contains("Resolved:"));
            assertTrue(tooltip.contains(resolvedFile.getAbsolutePath()));
        }

        @Test
        @DisplayName("Shows not found status when unresolved")
        void showsNotFoundWhenUnresolved() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "missing.xsd",
                    LinkedFileInfo.LinkType.XSD_INCLUDE
            );

            String tooltip = info.getTooltipText();
            assertTrue(tooltip.contains("Status: Not found"));
        }
    }

    @Nested
    @DisplayName("getFileType")
    class GetFileTypeTests {

        @Test
        @DisplayName("Returns XSD for .xsd files")
        void returnsXsdForXsdFiles() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();
            File resolvedFile = tempDir.resolve("schema.xsd").toFile();
            resolvedFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.resolved(
                    sourceFile,
                    "schema.xsd",
                    resolvedFile,
                    LinkedFileInfo.LinkType.XSD_IMPORT
            );

            assertEquals(UnifiedEditorFileType.XSD, info.getFileType());
        }

        @Test
        @DisplayName("Returns XSLT for .xslt files")
        void returnsXsltForXsltFiles() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "transform.xslt",
                    LinkedFileInfo.LinkType.XSLT_IMPORT
            );

            assertEquals(UnifiedEditorFileType.XSLT, info.getFileType());
        }

        @Test
        @DisplayName("Returns XML for .xml files")
        void returnsXmlForXmlFiles() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info = LinkedFileInfo.unresolved(
                    sourceFile,
                    "data.xml",
                    LinkedFileInfo.LinkType.XML_STYLESHEET
            );

            assertEquals(UnifiedEditorFileType.XML, info.getFileType());
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equal records are equal")
        void equalRecordsAreEqual() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info1 = LinkedFileInfo.unresolved(
                    sourceFile,
                    "schema.xsd",
                    LinkedFileInfo.LinkType.XSD_IMPORT
            );

            LinkedFileInfo info2 = LinkedFileInfo.unresolved(
                    sourceFile,
                    "schema.xsd",
                    LinkedFileInfo.LinkType.XSD_IMPORT
            );

            assertEquals(info1, info2);
            assertEquals(info1.hashCode(), info2.hashCode());
        }

        @Test
        @DisplayName("Different reference paths are not equal")
        void differentPathsNotEqual() throws IOException {
            File sourceFile = tempDir.resolve("source.xml").toFile();
            sourceFile.createNewFile();

            LinkedFileInfo info1 = LinkedFileInfo.unresolved(
                    sourceFile,
                    "schema1.xsd",
                    LinkedFileInfo.LinkType.XSD_IMPORT
            );

            LinkedFileInfo info2 = LinkedFileInfo.unresolved(
                    sourceFile,
                    "schema2.xsd",
                    LinkedFileInfo.LinkType.XSD_IMPORT
            );

            assertNotEquals(info1, info2);
        }
    }
}
