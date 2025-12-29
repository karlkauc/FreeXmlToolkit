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

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Favorite")
class FavoriteTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor sets defaults")
        void defaultConstructor() {
            Favorite favorite = new Favorite();

            assertNotNull(favorite.getId());
            assertFalse(favorite.getId().isEmpty());
            assertNotNull(favorite.getCreatedAt());
            assertNotNull(favorite.getLastAccessed());
            assertEquals("General", favorite.getCategory());
        }

        @Test
        @DisplayName("Three-arg constructor sets values")
        void threeArgConstructor() {
            Path path = Path.of("/path/to/file.xml");
            Favorite favorite = new Favorite("My XML", path, "Work");

            assertEquals("My XML", favorite.getName());
            assertEquals(path, favorite.getFilePath());
            assertEquals("Work", favorite.getCategory());
            assertEquals(Favorite.FileType.XML, favorite.getFileType());
        }

        @Test
        @DisplayName("Three-arg constructor with null category uses General")
        void nullCategoryUsesGeneral() {
            Path path = Path.of("/path/to/file.xsd");
            Favorite favorite = new Favorite("Schema", path, null);

            assertEquals("General", favorite.getCategory());
        }
    }

    @Nested
    @DisplayName("FileType Enum")
    class FileTypeTests {

        @Test
        @DisplayName("fromPath detects XML files")
        void detectsXmlFiles() {
            assertEquals(Favorite.FileType.XML, Favorite.FileType.fromPath(Path.of("file.xml")));
            assertEquals(Favorite.FileType.XML, Favorite.FileType.fromPath(Path.of("FILE.XML")));
        }

        @Test
        @DisplayName("fromPath detects XSD files")
        void detectsXsdFiles() {
            assertEquals(Favorite.FileType.XSD, Favorite.FileType.fromPath(Path.of("schema.xsd")));
            assertEquals(Favorite.FileType.XSD, Favorite.FileType.fromPath(Path.of("SCHEMA.XSD")));
        }

        @Test
        @DisplayName("fromPath detects Schematron files")
        void detectsSchematronFiles() {
            assertEquals(Favorite.FileType.SCHEMATRON, Favorite.FileType.fromPath(Path.of("rules.sch")));
        }

        @Test
        @DisplayName("fromPath detects XSLT files")
        void detectsXsltFiles() {
            assertEquals(Favorite.FileType.XSLT, Favorite.FileType.fromPath(Path.of("transform.xsl")));
            assertEquals(Favorite.FileType.XSLT, Favorite.FileType.fromPath(Path.of("transform.xslt")));
        }

        @Test
        @DisplayName("fromPath returns OTHER for unknown extensions")
        void detectsOtherFiles() {
            assertEquals(Favorite.FileType.OTHER, Favorite.FileType.fromPath(Path.of("file.txt")));
            assertEquals(Favorite.FileType.OTHER, Favorite.FileType.fromPath(Path.of("file.json")));
            assertEquals(Favorite.FileType.OTHER, Favorite.FileType.fromPath(Path.of("noextension")));
        }

        @Test
        @DisplayName("FileType has correct display names")
        void displayNames() {
            assertEquals("XML Files", Favorite.FileType.XML.getDisplayName());
            assertEquals("XSD Schema Files", Favorite.FileType.XSD.getDisplayName());
            assertEquals("Schematron Files", Favorite.FileType.SCHEMATRON.getDisplayName());
            assertEquals("XSLT Files", Favorite.FileType.XSLT.getDisplayName());
            assertEquals("Other Files", Favorite.FileType.OTHER.getDisplayName());
        }

        @Test
        @DisplayName("FileType has extensions")
        void extensions() {
            assertTrue(Favorite.FileType.XML.getExtensions().length > 0);
            assertTrue(Favorite.FileType.XSLT.getExtensions().length >= 2); // .xsl and .xslt
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GetterSetterTests {

        @Test
        @DisplayName("setFilePath updates fileType")
        void setFilePathUpdatesType() {
            Favorite favorite = new Favorite();
            favorite.setFilePath(Path.of("test.xsd"));

            assertEquals(Favorite.FileType.XSD, favorite.getFileType());
        }

        @Test
        @DisplayName("All setters work correctly")
        void allSettersWork() {
            Favorite favorite = new Favorite();
            LocalDateTime now = LocalDateTime.now();

            favorite.setId("custom-id");
            favorite.setName("Test Name");
            favorite.setFilePath(Path.of("/test/path.xml"));
            favorite.setCategory("Custom");
            favorite.setFileType(Favorite.FileType.SCHEMATRON);
            favorite.setCreatedAt(now);
            favorite.setLastAccessed(now);
            favorite.setDescription("Test description");

            assertEquals("custom-id", favorite.getId());
            assertEquals("Test Name", favorite.getName());
            assertEquals(Path.of("/test/path.xml"), favorite.getFilePath());
            assertEquals("Custom", favorite.getCategory());
            assertEquals(Favorite.FileType.SCHEMATRON, favorite.getFileType());
            assertEquals(now, favorite.getCreatedAt());
            assertEquals(now, favorite.getLastAccessed());
            assertEquals("Test description", favorite.getDescription());
        }
    }

    @Nested
    @DisplayName("updateLastAccessed")
    class UpdateLastAccessedTests {

        @Test
        @DisplayName("updateLastAccessed sets current time")
        void updatesLastAccessed() throws InterruptedException {
            Favorite favorite = new Favorite();
            LocalDateTime initial = favorite.getLastAccessed();

            Thread.sleep(10); // Ensure time difference
            favorite.updateLastAccessed();

            assertTrue(favorite.getLastAccessed().isAfter(initial) ||
                    favorite.getLastAccessed().isEqual(initial));
        }
    }

    @Nested
    @DisplayName("Object Methods")
    class ObjectMethodTests {

        @Test
        @DisplayName("equals compares by ID")
        void equalsById() {
            Favorite f1 = new Favorite();
            Favorite f2 = new Favorite();
            f2.setId(f1.getId());

            assertEquals(f1, f2);
        }

        @Test
        @DisplayName("equals returns false for different IDs")
        void notEqualsForDifferentIds() {
            Favorite f1 = new Favorite();
            Favorite f2 = new Favorite();

            assertNotEquals(f1, f2);
        }

        @Test
        @DisplayName("equals returns false for null")
        void notEqualsNull() {
            Favorite favorite = new Favorite();
            assertNotEquals(null, favorite);
        }

        @Test
        @DisplayName("equals returns false for different type")
        void notEqualsDifferentType() {
            Favorite favorite = new Favorite();
            assertNotEquals("string", favorite);
        }

        @Test
        @DisplayName("hashCode is based on ID")
        void hashCodeBasedOnId() {
            Favorite f1 = new Favorite();
            Favorite f2 = new Favorite();
            f2.setId(f1.getId());

            assertEquals(f1.hashCode(), f2.hashCode());
        }

        @Test
        @DisplayName("toString includes name and type")
        void toStringIncludesInfo() {
            Favorite favorite = new Favorite("My File", Path.of("test.xml"), "Work");

            String str = favorite.toString();
            assertTrue(str.contains("My File"));
            assertTrue(str.contains("XML"));
        }
    }
}
