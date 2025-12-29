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

package org.fxt.freexmltoolkit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FormattingUtils utility class.
 * These tests run without JavaFX dependencies.
 */
@DisplayName("FormattingUtils")
class FormattingUtilsTest {

    @Nested
    @DisplayName("formatElapsedTime tests")
    class FormatElapsedTimeTests {

        @Test
        @DisplayName("Should format zero milliseconds as 00:00")
        void formatZeroMilliseconds() {
            assertEquals("00:00", FormattingUtils.formatElapsedTime(0L));
        }

        @Test
        @DisplayName("Should format 30 seconds correctly")
        void format30Seconds() {
            assertEquals("00:30", FormattingUtils.formatElapsedTime(30_000L));
        }

        @Test
        @DisplayName("Should format 1 minute 30 seconds correctly")
        void format1Minute30Seconds() {
            assertEquals("01:30", FormattingUtils.formatElapsedTime(90_000L));
        }

        @Test
        @DisplayName("Should format 59 minutes 59 seconds correctly")
        void format59Minutes59Seconds() {
            assertEquals("59:59", FormattingUtils.formatElapsedTime(3599_000L));
        }

        @Test
        @DisplayName("Should format 1 hour correctly with HH:MM:SS format")
        void format1Hour() {
            assertEquals("01:00:00", FormattingUtils.formatElapsedTime(3600_000L));
        }

        @Test
        @DisplayName("Should format 2 hours 30 minutes 45 seconds correctly")
        void format2Hours30Minutes45Seconds() {
            long millis = (2 * 3600 + 30 * 60 + 45) * 1000L;
            assertEquals("02:30:45", FormattingUtils.formatElapsedTime(millis));
        }

        @ParameterizedTest
        @CsvSource({
            "0, 00:00",
            "1000, 00:01",
            "60000, 01:00",
            "61000, 01:01",
            "3600000, 01:00:00",
            "3661000, 01:01:01"
        })
        @DisplayName("Should format various elapsed times correctly")
        void formatVariousElapsedTimes(long millis, String expected) {
            assertEquals(expected, FormattingUtils.formatElapsedTime(millis));
        }

        @Test
        @DisplayName("Should handle maximum long value without overflow")
        void formatElapsedTimeMaxValue() {
            assertDoesNotThrow(() -> FormattingUtils.formatElapsedTime(Long.MAX_VALUE));
        }
    }

    @Nested
    @DisplayName("formatFileSize tests")
    class FormatFileSizeTests {

        @Test
        @DisplayName("Should format 0 bytes correctly")
        void formatZeroBytes() {
            assertEquals("0 B", FormattingUtils.formatFileSize(0L));
        }

        @Test
        @DisplayName("Should format bytes less than 1KB correctly")
        void formatBytesLessThan1KB() {
            assertEquals("512 B", FormattingUtils.formatFileSize(512L));
        }

        @Test
        @DisplayName("Should format exactly 1023 bytes correctly")
        void format1023Bytes() {
            assertEquals("1023 B", FormattingUtils.formatFileSize(1023L));
        }

        @Test
        @DisplayName("Should format 1KB correctly")
        void format1KB() {
            // Uses Locale.US so always dot separator
            assertEquals("1.00 KB", FormattingUtils.formatFileSize(1024L));
        }

        @Test
        @DisplayName("Should format 1.5KB correctly")
        void format1Point5KB() {
            assertEquals("1.50 KB", FormattingUtils.formatFileSize(1536L));
        }

        @Test
        @DisplayName("Should format 1MB correctly")
        void format1MB() {
            assertEquals("1.00 MB", FormattingUtils.formatFileSize(1024L * 1024L));
        }

        @Test
        @DisplayName("Should format 2.5MB correctly")
        void format2Point5MB() {
            long bytes = (long) (2.5 * 1024 * 1024);
            assertEquals("2.50 MB", FormattingUtils.formatFileSize(bytes));
        }

        @Test
        @DisplayName("Should format 1GB correctly")
        void format1GB() {
            assertEquals("1.00 GB", FormattingUtils.formatFileSize(1024L * 1024L * 1024L));
        }

        @Test
        @DisplayName("Should return correct unit for bytes")
        void formatFileSizeUnits() {
            assertTrue(FormattingUtils.formatFileSize(100L).endsWith(" B"));
            assertTrue(FormattingUtils.formatFileSize(1024L).endsWith(" KB"));
            assertTrue(FormattingUtils.formatFileSize(1024L * 1024L).endsWith(" MB"));
            assertTrue(FormattingUtils.formatFileSize(1024L * 1024L * 1024L).endsWith(" GB"));
        }

        @Test
        @DisplayName("Should handle maximum long value without overflow")
        void formatFileSizeMaxValue() {
            assertDoesNotThrow(() -> FormattingUtils.formatFileSize(Long.MAX_VALUE));
        }
    }

    @Nested
    @DisplayName("escapeCsvField tests")
    class EscapeCsvFieldTests {

        @Test
        @DisplayName("Should return empty string for null input")
        void escapeCsvFieldNull() {
            assertEquals("", FormattingUtils.escapeCsvField(null));
        }

        @Test
        @DisplayName("Should return empty string for empty input")
        void escapeCsvFieldEmpty() {
            assertEquals("", FormattingUtils.escapeCsvField(""));
        }

        @Test
        @DisplayName("Should return unchanged string without special characters")
        void escapeCsvFieldSimple() {
            assertEquals("simple text", FormattingUtils.escapeCsvField("simple text"));
        }

        @Test
        @DisplayName("Should escape double quotes by doubling them")
        void escapeCsvFieldWithQuotes() {
            assertEquals("text with \"\"quotes\"\"", FormattingUtils.escapeCsvField("text with \"quotes\""));
        }

        @Test
        @DisplayName("Should handle multiple quotes")
        void escapeCsvFieldWithMultipleQuotes() {
            assertEquals("\"\"a\"\" and \"\"b\"\"", FormattingUtils.escapeCsvField("\"a\" and \"b\""));
        }

        @Test
        @DisplayName("Should preserve commas (not escape them)")
        void escapeCsvFieldWithCommas() {
            assertEquals("text, with, commas", FormattingUtils.escapeCsvField("text, with, commas"));
        }

        @Test
        @DisplayName("Should preserve newlines")
        void escapeCsvFieldWithNewlines() {
            assertEquals("line1\nline2", FormattingUtils.escapeCsvField("line1\nline2"));
        }

        @Test
        @DisplayName("Should handle very long strings")
        void escapeCsvFieldLongString() {
            String longString = "a".repeat(10000);
            assertEquals(longString, FormattingUtils.escapeCsvField(longString));
        }

        @Test
        @DisplayName("Should handle string with only quotes")
        void escapeCsvFieldOnlyQuotes() {
            assertEquals("\"\"\"\"\"\"", FormattingUtils.escapeCsvField("\"\"\""));
        }

        @Test
        @DisplayName("Should handle unicode characters")
        void escapeCsvFieldUnicode() {
            assertEquals("Straße München 日本語", FormattingUtils.escapeCsvField("Straße München 日本語"));
        }
    }

    @Nested
    @DisplayName("escapeCsv tests")
    class EscapeCsvTests {

        @Test
        @DisplayName("Should return empty string for null input")
        void escapeCsvNull() {
            assertEquals("", FormattingUtils.escapeCsv(null));
        }

        @Test
        @DisplayName("Should return unchanged string without special characters")
        void escapeCsvSimple() {
            assertEquals("simple text", FormattingUtils.escapeCsv("simple text"));
        }

        @Test
        @DisplayName("Should wrap and escape string with commas")
        void escapeCsvWithComma() {
            assertEquals("\"text, with, commas\"", FormattingUtils.escapeCsv("text, with, commas"));
        }

        @Test
        @DisplayName("Should wrap and escape string with quotes")
        void escapeCsvWithQuotes() {
            assertEquals("\"text with \"\"quotes\"\"\"", FormattingUtils.escapeCsv("text with \"quotes\""));
        }

        @Test
        @DisplayName("Should wrap and escape string with newlines")
        void escapeCsvWithNewlines() {
            assertEquals("\"line1\nline2\"", FormattingUtils.escapeCsv("line1\nline2"));
        }

        @Test
        @DisplayName("Should handle string with all special characters")
        void escapeCsvWithAllSpecialChars() {
            assertEquals("\"text, with \"\"quotes\"\"\nand newline\"",
                FormattingUtils.escapeCsv("text, with \"quotes\"\nand newline"));
        }

        @Test
        @DisplayName("Should return empty string for empty input")
        void escapeCsvEmpty() {
            assertEquals("", FormattingUtils.escapeCsv(""));
        }

        @Test
        @DisplayName("Should handle very long strings with comma")
        void escapeCsvLongString() {
            String longString = "a".repeat(10000) + ",";
            assertEquals("\"" + "a".repeat(10000) + ",\"", FormattingUtils.escapeCsv(longString));
        }
    }

    @Nested
    @DisplayName("escapeJson tests")
    class EscapeJsonTests {

        @Test
        @DisplayName("Should return empty string for null input")
        void escapeJsonNull() {
            assertEquals("", FormattingUtils.escapeJson(null));
        }

        @Test
        @DisplayName("Should return unchanged string without special characters")
        void escapeJsonSimple() {
            assertEquals("simple text", FormattingUtils.escapeJson("simple text"));
        }

        @Test
        @DisplayName("Should escape backslashes")
        void escapeJsonBackslash() {
            assertEquals("path\\\\to\\\\file", FormattingUtils.escapeJson("path\\to\\file"));
        }

        @Test
        @DisplayName("Should escape double quotes")
        void escapeJsonQuotes() {
            assertEquals("text with \\\"quotes\\\"", FormattingUtils.escapeJson("text with \"quotes\""));
        }

        @Test
        @DisplayName("Should escape newlines")
        void escapeJsonNewline() {
            assertEquals("line1\\nline2", FormattingUtils.escapeJson("line1\nline2"));
        }

        @Test
        @DisplayName("Should escape carriage returns")
        void escapeJsonCarriageReturn() {
            assertEquals("line1\\rline2", FormattingUtils.escapeJson("line1\rline2"));
        }

        @Test
        @DisplayName("Should escape tabs")
        void escapeJsonTab() {
            assertEquals("col1\\tcol2", FormattingUtils.escapeJson("col1\tcol2"));
        }

        @Test
        @DisplayName("Should handle all escape characters together")
        void escapeJsonAllChars() {
            assertEquals("\\\\\\\"\\n\\r\\t", FormattingUtils.escapeJson("\\\"\n\r\t"));
        }

        @Test
        @DisplayName("Should preserve unicode characters")
        void escapeJsonUnicode() {
            assertEquals("Straße 日本語", FormattingUtils.escapeJson("Straße 日本語"));
        }

        @Test
        @DisplayName("Should handle very long strings")
        void escapeJsonLongString() {
            String longString = "a".repeat(10000);
            assertEquals(longString, FormattingUtils.escapeJson(longString));
        }

        @Test
        @DisplayName("Should handle consecutive escape characters")
        void escapeJsonConsecutiveEscapes() {
            assertEquals("\\\\\\\\\\n\\n", FormattingUtils.escapeJson("\\\\\n\n"));
        }
    }

    @Nested
    @DisplayName("getFileExtension(File) tests")
    class GetFileExtensionFileTests {

        @Test
        @DisplayName("Should return correct extension for simple filename")
        void getExtensionSimple() {
            assertEquals("xml", FormattingUtils.getFileExtension(new File("document.xml")));
        }

        @Test
        @DisplayName("Should return correct extension for uppercase extension")
        void getExtensionUppercase() {
            assertEquals("XML", FormattingUtils.getFileExtension(new File("document.XML")));
        }

        @Test
        @DisplayName("Should return empty string for file without extension")
        void getExtensionNoExtension() {
            assertEquals("", FormattingUtils.getFileExtension(new File("document")));
        }

        @Test
        @DisplayName("Should return correct extension for multiple dots")
        void getExtensionMultipleDots() {
            assertEquals("xml", FormattingUtils.getFileExtension(new File("document.backup.xml")));
        }

        @Test
        @DisplayName("Should return extension for hidden file (dot-prefixed)")
        void getExtensionHiddenFile() {
            assertEquals("hidden", FormattingUtils.getFileExtension(new File(".hidden")));
        }

        @Test
        @DisplayName("Should return correct extension for hidden file with extension")
        void getExtensionHiddenFileWithExtension() {
            assertEquals("xml", FormattingUtils.getFileExtension(new File(".hidden.xml")));
        }

        @Test
        @DisplayName("Should return empty string for file ending with dot")
        void getExtensionEndingWithDot() {
            assertEquals("", FormattingUtils.getFileExtension(new File("document.")));
        }

        @Test
        @DisplayName("Should return empty string for null file")
        void getExtensionNullFile() {
            assertEquals("", FormattingUtils.getFileExtension((File) null));
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
        void getExtensionVariousTypes(String filename, String expectedExtension) {
            assertEquals(expectedExtension, FormattingUtils.getFileExtension(new File(filename)));
        }

        @Test
        @DisplayName("Should handle file with path")
        void getExtensionWithPath() {
            assertEquals("xml", FormattingUtils.getFileExtension(new File("/path/to/document.xml")));
        }

        @Test
        @DisplayName("Should handle Windows path")
        void getExtensionWindowsPath() {
            assertEquals("xml", FormattingUtils.getFileExtension(new File("C:\\path\\to\\document.xml")));
        }
    }

    @Nested
    @DisplayName("getFileExtension(String) tests")
    class GetFileExtensionStringTests {

        @Test
        @DisplayName("Should return correct extension for simple filename")
        void getExtensionSimple() {
            assertEquals("xml", FormattingUtils.getFileExtension("document.xml"));
        }

        @Test
        @DisplayName("Should return empty string for null string")
        void getExtensionNullString() {
            assertEquals("", FormattingUtils.getFileExtension((String) null));
        }

        @Test
        @DisplayName("Should return empty string for empty string")
        void getExtensionEmptyString() {
            assertEquals("", FormattingUtils.getFileExtension(""));
        }

        @Test
        @DisplayName("Should return empty string for string without extension")
        void getExtensionNoExtension() {
            assertEquals("", FormattingUtils.getFileExtension("document"));
        }

        @Test
        @DisplayName("Should return correct extension for multiple dots")
        void getExtensionMultipleDots() {
            assertEquals("xml", FormattingUtils.getFileExtension("document.backup.xml"));
        }
    }
}
