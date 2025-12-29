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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentationOutputFormat")
class DocumentationOutputFormatTest {

    @Test
    @DisplayName("All enum values exist")
    void allValuesExist() {
        assertEquals(3, DocumentationOutputFormat.values().length);
        assertNotNull(DocumentationOutputFormat.HTML);
        assertNotNull(DocumentationOutputFormat.WORD);
        assertNotNull(DocumentationOutputFormat.PDF);
    }

    @ParameterizedTest
    @EnumSource(DocumentationOutputFormat.class)
    @DisplayName("All formats have file extension")
    void allHaveFileExtension(DocumentationOutputFormat format) {
        assertNotNull(format.getFileExtension());
        assertFalse(format.getFileExtension().isEmpty());
        assertFalse(format.getFileExtension().startsWith(".")); // Should not include dot
    }

    @ParameterizedTest
    @EnumSource(DocumentationOutputFormat.class)
    @DisplayName("All formats have display name")
    void allHaveDisplayName(DocumentationOutputFormat format) {
        assertNotNull(format.getDisplayName());
        assertFalse(format.getDisplayName().isEmpty());
    }

    @Nested
    @DisplayName("HTML format")
    class HtmlTests {

        @Test
        @DisplayName("Has correct values")
        void hasCorrectValues() {
            DocumentationOutputFormat html = DocumentationOutputFormat.HTML;

            assertEquals("html", html.getFileExtension());
            assertEquals("HTML Documentation", html.getDisplayName());
            assertTrue(html.outputsDirectory());
        }

        @Test
        @DisplayName("getDefaultFileName returns schema name for directory output")
        void getDefaultFileNameReturnsSchemaName() {
            String fileName = DocumentationOutputFormat.HTML.getDefaultFileName("MySchema");
            assertEquals("MySchema", fileName);
        }
    }

    @Nested
    @DisplayName("WORD format")
    class WordTests {

        @Test
        @DisplayName("Has correct values")
        void hasCorrectValues() {
            DocumentationOutputFormat word = DocumentationOutputFormat.WORD;

            assertEquals("docx", word.getFileExtension());
            assertEquals("Word Document", word.getDisplayName());
            assertFalse(word.outputsDirectory());
        }

        @Test
        @DisplayName("getDefaultFileName returns filename with extension")
        void getDefaultFileNameReturnsFilenameWithExtension() {
            String fileName = DocumentationOutputFormat.WORD.getDefaultFileName("MySchema");
            assertEquals("MySchema.docx", fileName);
        }
    }

    @Nested
    @DisplayName("PDF format")
    class PdfTests {

        @Test
        @DisplayName("Has correct values")
        void hasCorrectValues() {
            DocumentationOutputFormat pdf = DocumentationOutputFormat.PDF;

            assertEquals("pdf", pdf.getFileExtension());
            assertEquals("PDF Document", pdf.getDisplayName());
            assertFalse(pdf.outputsDirectory());
        }

        @Test
        @DisplayName("getDefaultFileName returns filename with extension")
        void getDefaultFileNameReturnsFilenameWithExtension() {
            String fileName = DocumentationOutputFormat.PDF.getDefaultFileName("MySchema");
            assertEquals("MySchema.pdf", fileName);
        }
    }

    @Nested
    @DisplayName("outputsDirectory")
    class OutputsDirectoryTests {

        @Test
        @DisplayName("Only HTML outputs directory")
        void onlyHtmlOutputsDirectory() {
            assertTrue(DocumentationOutputFormat.HTML.outputsDirectory());
            assertFalse(DocumentationOutputFormat.WORD.outputsDirectory());
            assertFalse(DocumentationOutputFormat.PDF.outputsDirectory());
        }
    }

    @Nested
    @DisplayName("getDefaultFileName")
    class GetDefaultFileNameTests {

        @Test
        @DisplayName("Handles schema names with special characters")
        void handlesSchemaWithSpecialChars() {
            String fileName = DocumentationOutputFormat.PDF.getDefaultFileName("My-Schema_v1.0");
            assertEquals("My-Schema_v1.0.pdf", fileName);
        }

        @Test
        @DisplayName("Handles empty schema name")
        void handlesEmptySchemaName() {
            String fileName = DocumentationOutputFormat.WORD.getDefaultFileName("");
            assertEquals(".docx", fileName);
        }

        @Test
        @DisplayName("Directory format returns name only")
        void directoryFormatReturnsNameOnly() {
            String fileName = DocumentationOutputFormat.HTML.getDefaultFileName("TestSchema");
            assertEquals("TestSchema", fileName);
            assertFalse(fileName.contains("."));
        }
    }

    @Test
    @DisplayName("valueOf returns correct enum")
    void valueOfReturnsCorrectEnum() {
        assertEquals(DocumentationOutputFormat.HTML, DocumentationOutputFormat.valueOf("HTML"));
        assertEquals(DocumentationOutputFormat.WORD, DocumentationOutputFormat.valueOf("WORD"));
        assertEquals(DocumentationOutputFormat.PDF, DocumentationOutputFormat.valueOf("PDF"));
    }
}
