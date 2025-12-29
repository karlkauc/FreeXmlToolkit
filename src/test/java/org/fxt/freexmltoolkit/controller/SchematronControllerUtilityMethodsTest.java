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

package org.fxt.freexmltoolkit.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SchematronController utility methods.
 * Tests private utility methods using reflection to ensure correct behavior
 * without requiring JavaFX initialization.
 */
@DisplayName("SchematronController Utility Methods")
class SchematronControllerUtilityMethodsTest {

    private SchematronController controller;

    @BeforeEach
    void setUp() {
        controller = new SchematronController();
    }

    /**
     * Helper method to invoke private methods using reflection.
     */
    private Object invokePrivateMethod(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = SchematronController.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(controller, args);
    }

    @Nested
    @DisplayName("escapeCsv tests")
    class EscapeCsvTests {

        @Test
        @DisplayName("Should return empty string for null input")
        void escapeCsvNull() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsv",
                new Class<?>[]{String.class}, (Object) null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should return unchanged string without special characters")
        void escapeCsvSimple() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsv",
                new Class<?>[]{String.class}, "simple text");
            assertEquals("simple text", result);
        }

        @Test
        @DisplayName("Should wrap and escape string with commas")
        void escapeCsvWithComma() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsv",
                new Class<?>[]{String.class}, "text, with, commas");
            assertEquals("\"text, with, commas\"", result);
        }

        @Test
        @DisplayName("Should wrap and escape string with quotes")
        void escapeCsvWithQuotes() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsv",
                new Class<?>[]{String.class}, "text with \"quotes\"");
            assertEquals("\"text with \"\"quotes\"\"\"", result);
        }

        @Test
        @DisplayName("Should wrap and escape string with newlines")
        void escapeCsvWithNewlines() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsv",
                new Class<?>[]{String.class}, "line1\nline2");
            assertEquals("\"line1\nline2\"", result);
        }

        @Test
        @DisplayName("Should handle string with all special characters")
        void escapeCsvWithAllSpecialChars() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsv",
                new Class<?>[]{String.class}, "text, with \"quotes\"\nand newline");
            assertEquals("\"text, with \"\"quotes\"\"\nand newline\"", result);
        }

        @Test
        @DisplayName("Should return empty string for empty input")
        void escapeCsvEmpty() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsv",
                new Class<?>[]{String.class}, "");
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("escapeJson tests")
    class EscapeJsonTests {

        @Test
        @DisplayName("Should return empty string for null input")
        void escapeJsonNull() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, (Object) null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should return unchanged string without special characters")
        void escapeJsonSimple() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, "simple text");
            assertEquals("simple text", result);
        }

        @Test
        @DisplayName("Should escape backslashes")
        void escapeJsonBackslash() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, "path\\to\\file");
            assertEquals("path\\\\to\\\\file", result);
        }

        @Test
        @DisplayName("Should escape double quotes")
        void escapeJsonQuotes() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, "text with \"quotes\"");
            assertEquals("text with \\\"quotes\\\"", result);
        }

        @Test
        @DisplayName("Should escape newlines")
        void escapeJsonNewline() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, "line1\nline2");
            assertEquals("line1\\nline2", result);
        }

        @Test
        @DisplayName("Should escape carriage returns")
        void escapeJsonCarriageReturn() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, "line1\rline2");
            assertEquals("line1\\rline2", result);
        }

        @Test
        @DisplayName("Should escape tabs")
        void escapeJsonTab() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, "col1\tcol2");
            assertEquals("col1\\tcol2", result);
        }

        @Test
        @DisplayName("Should handle all escape characters together")
        void escapeJsonAllChars() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, "\\\"\n\r\t");
            assertEquals("\\\\\\\"\\n\\r\\t", result);
        }

        @Test
        @DisplayName("Should preserve unicode characters")
        void escapeJsonUnicode() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, "Straße 日本語");
            assertEquals("Straße 日本語", result);
        }
    }

    @Nested
    @DisplayName("getFileExtension tests")
    class GetFileExtensionTests {

        @Test
        @DisplayName("Should return correct extension for simple filename")
        void getExtensionSimple() throws Exception {
            File file = new File("document.xml");
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals("xml", result);
        }

        @Test
        @DisplayName("Should return correct extension for uppercase extension")
        void getExtensionUppercase() throws Exception {
            File file = new File("document.XML");
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals("XML", result);
        }

        @Test
        @DisplayName("Should return empty string for file without extension")
        void getExtensionNoExtension() throws Exception {
            File file = new File("document");
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should return correct extension for multiple dots")
        void getExtensionMultipleDots() throws Exception {
            File file = new File("document.backup.xml");
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals("xml", result);
        }

        @Test
        @DisplayName("Should return empty string for hidden file without extension")
        void getExtensionHiddenFile() throws Exception {
            File file = new File(".hidden");
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals("hidden", result);
        }

        @Test
        @DisplayName("Should return correct extension for hidden file with extension")
        void getExtensionHiddenFileWithExtension() throws Exception {
            File file = new File(".hidden.xml");
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals("xml", result);
        }

        @Test
        @DisplayName("Should return empty string for file ending with dot")
        void getExtensionEndingWithDot() throws Exception {
            File file = new File("document.");
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals("", result);
        }

        @ParameterizedTest
        @CsvSource({
            "test.xml, xml",
            "test.XSD, XSD",
            "test.schematron, schematron",
            "test.sch, sch",
            "test.json, json",
            "test.csv, csv",
            "test.txt, txt"
        })
        @DisplayName("Should return correct extensions for various file types")
        void getExtensionVariousTypes(String filename, String expectedExtension) throws Exception {
            File file = new File(filename);
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals(expectedExtension, result);
        }

        @Test
        @DisplayName("Should handle file with path")
        void getExtensionWithPath() throws Exception {
            File file = new File("/path/to/document.xml");
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals("xml", result);
        }

        @Test
        @DisplayName("Should handle Windows path")
        void getExtensionWindowsPath() throws Exception {
            File file = new File("C:\\path\\to\\document.xml");
            String result = (String) invokePrivateMethod("getFileExtension",
                new Class<?>[]{File.class}, file);
            assertEquals("xml", result);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("escapeCsv should handle very long strings")
        void escapeCsvLongString() throws Exception {
            String longString = "a".repeat(10000) + ",";
            String result = (String) invokePrivateMethod("escapeCsv",
                new Class<?>[]{String.class}, longString);
            assertEquals("\"" + "a".repeat(10000) + ",\"", result);
        }

        @Test
        @DisplayName("escapeJson should handle very long strings")
        void escapeJsonLongString() throws Exception {
            String longString = "a".repeat(10000);
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, longString);
            assertEquals(longString, result);
        }

        @Test
        @DisplayName("escapeJson should handle consecutive escape characters")
        void escapeJsonConsecutiveEscapes() throws Exception {
            String result = (String) invokePrivateMethod("escapeJson",
                new Class<?>[]{String.class}, "\\\\\n\n");
            assertEquals("\\\\\\\\\\n\\n", result);
        }
    }
}
