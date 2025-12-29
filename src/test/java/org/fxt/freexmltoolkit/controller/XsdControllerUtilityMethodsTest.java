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
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdController utility methods.
 * Tests private utility methods using reflection to ensure correct behavior
 * without requiring JavaFX initialization.
 */
@DisplayName("XsdController Utility Methods")
class XsdControllerUtilityMethodsTest {

    private XsdController controller;

    @BeforeEach
    void setUp() {
        controller = new XsdController();
    }

    /**
     * Helper method to invoke private methods using reflection.
     */
    private Object invokePrivateMethod(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = XsdController.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(controller, args);
    }

    @Nested
    @DisplayName("formatElapsedTime tests")
    class FormatElapsedTimeTests {

        @Test
        @DisplayName("Should format zero milliseconds as 00:00")
        void formatZeroMilliseconds() throws Exception {
            String result = (String) invokePrivateMethod("formatElapsedTime",
                new Class<?>[]{long.class}, 0L);
            assertEquals("00:00", result);
        }

        @Test
        @DisplayName("Should format 30 seconds correctly")
        void format30Seconds() throws Exception {
            String result = (String) invokePrivateMethod("formatElapsedTime",
                new Class<?>[]{long.class}, 30_000L);
            assertEquals("00:30", result);
        }

        @Test
        @DisplayName("Should format 1 minute 30 seconds correctly")
        void format1Minute30Seconds() throws Exception {
            String result = (String) invokePrivateMethod("formatElapsedTime",
                new Class<?>[]{long.class}, 90_000L);
            assertEquals("01:30", result);
        }

        @Test
        @DisplayName("Should format 59 minutes 59 seconds correctly")
        void format59Minutes59Seconds() throws Exception {
            String result = (String) invokePrivateMethod("formatElapsedTime",
                new Class<?>[]{long.class}, 3599_000L);
            assertEquals("59:59", result);
        }

        @Test
        @DisplayName("Should format 1 hour correctly with HH:MM:SS format")
        void format1Hour() throws Exception {
            String result = (String) invokePrivateMethod("formatElapsedTime",
                new Class<?>[]{long.class}, 3600_000L);
            assertEquals("01:00:00", result);
        }

        @Test
        @DisplayName("Should format 2 hours 30 minutes 45 seconds correctly")
        void format2Hours30Minutes45Seconds() throws Exception {
            long millis = (2 * 3600 + 30 * 60 + 45) * 1000L;
            String result = (String) invokePrivateMethod("formatElapsedTime",
                new Class<?>[]{long.class}, millis);
            assertEquals("02:30:45", result);
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
        void formatVariousElapsedTimes(long millis, String expected) throws Exception {
            String result = (String) invokePrivateMethod("formatElapsedTime",
                new Class<?>[]{long.class}, millis);
            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("escapeCsvField tests")
    class EscapeCsvFieldTests {

        @Test
        @DisplayName("Should return empty string for null input")
        void escapeCsvFieldNull() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, (Object) null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should return empty string for empty input")
        void escapeCsvFieldEmpty() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, "");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should return unchanged string without special characters")
        void escapeCsvFieldSimple() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, "simple text");
            assertEquals("simple text", result);
        }

        @Test
        @DisplayName("Should escape double quotes by doubling them")
        void escapeCsvFieldWithQuotes() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, "text with \"quotes\"");
            assertEquals("text with \"\"quotes\"\"", result);
        }

        @Test
        @DisplayName("Should handle multiple quotes")
        void escapeCsvFieldWithMultipleQuotes() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, "\"a\" and \"b\"");
            assertEquals("\"\"a\"\" and \"\"b\"\"", result);
        }

        @Test
        @DisplayName("Should preserve commas (not escape them)")
        void escapeCsvFieldWithCommas() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, "text, with, commas");
            assertEquals("text, with, commas", result);
        }

        @Test
        @DisplayName("Should preserve newlines")
        void escapeCsvFieldWithNewlines() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, "line1\nline2");
            assertEquals("line1\nline2", result);
        }
    }

    @Nested
    @DisplayName("formatFileSize tests")
    class FormatFileSizeTests {

        @Test
        @DisplayName("Should format 0 bytes correctly")
        void formatZeroBytes() throws Exception {
            String result = (String) invokePrivateMethod("formatFileSize",
                new Class<?>[]{long.class}, 0L);
            assertEquals("0 B", result);
        }

        @Test
        @DisplayName("Should format bytes less than 1KB correctly")
        void formatBytesLessThan1KB() throws Exception {
            String result = (String) invokePrivateMethod("formatFileSize",
                new Class<?>[]{long.class}, 512L);
            assertEquals("512 B", result);
        }

        @Test
        @DisplayName("Should format exactly 1023 bytes correctly")
        void format1023Bytes() throws Exception {
            String result = (String) invokePrivateMethod("formatFileSize",
                new Class<?>[]{long.class}, 1023L);
            assertEquals("1023 B", result);
        }

        @Test
        @DisplayName("Should format 1KB correctly (locale-independent)")
        void format1KB() throws Exception {
            String result = (String) invokePrivateMethod("formatFileSize",
                new Class<?>[]{long.class}, 1024L);
            // Accept both . and , as decimal separator (locale-dependent)
            assertTrue(result.matches("1[.,]00 KB"), "Expected '1.00 KB' or '1,00 KB' but got: " + result);
        }

        @Test
        @DisplayName("Should format 1.5KB correctly (locale-independent)")
        void format1Point5KB() throws Exception {
            String result = (String) invokePrivateMethod("formatFileSize",
                new Class<?>[]{long.class}, 1536L);
            assertTrue(result.matches("1[.,]50 KB"), "Expected '1.50 KB' or '1,50 KB' but got: " + result);
        }

        @Test
        @DisplayName("Should format 1MB correctly (locale-independent)")
        void format1MB() throws Exception {
            String result = (String) invokePrivateMethod("formatFileSize",
                new Class<?>[]{long.class}, 1024L * 1024L);
            assertTrue(result.matches("1[.,]00 MB"), "Expected '1.00 MB' or '1,00 MB' but got: " + result);
        }

        @Test
        @DisplayName("Should format 2.5MB correctly (locale-independent)")
        void format2Point5MB() throws Exception {
            long bytes = (long) (2.5 * 1024 * 1024);
            String result = (String) invokePrivateMethod("formatFileSize",
                new Class<?>[]{long.class}, bytes);
            assertTrue(result.matches("2[.,]50 MB"), "Expected '2.50 MB' or '2,50 MB' but got: " + result);
        }

        @Test
        @DisplayName("Should format 1GB correctly (locale-independent)")
        void format1GB() throws Exception {
            String result = (String) invokePrivateMethod("formatFileSize",
                new Class<?>[]{long.class}, 1024L * 1024L * 1024L);
            assertTrue(result.matches("1[.,]00 GB"), "Expected '1.00 GB' or '1,00 GB' but got: " + result);
        }

        @Test
        @DisplayName("Should return correct unit for bytes")
        void formatFileSizeUnits() throws Exception {
            String resultB = (String) invokePrivateMethod("formatFileSize", new Class<?>[]{long.class}, 100L);
            String resultKB = (String) invokePrivateMethod("formatFileSize", new Class<?>[]{long.class}, 1024L);
            String resultMB = (String) invokePrivateMethod("formatFileSize", new Class<?>[]{long.class}, 1024L * 1024L);
            String resultGB = (String) invokePrivateMethod("formatFileSize", new Class<?>[]{long.class}, 1024L * 1024L * 1024L);

            assertTrue(resultB.endsWith(" B"), "Expected unit B but got: " + resultB);
            assertTrue(resultKB.endsWith(" KB"), "Expected unit KB but got: " + resultKB);
            assertTrue(resultMB.endsWith(" MB"), "Expected unit MB but got: " + resultMB);
            assertTrue(resultGB.endsWith(" GB"), "Expected unit GB but got: " + resultGB);
        }
    }

    @Nested
    @DisplayName("Edge cases and boundary tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("formatElapsedTime should handle maximum long value without overflow")
        void formatElapsedTimeMaxValue() throws Exception {
            // This tests that the method doesn't throw an exception for large values
            assertDoesNotThrow(() -> invokePrivateMethod("formatElapsedTime",
                new Class<?>[]{long.class}, Long.MAX_VALUE));
        }

        @Test
        @DisplayName("formatFileSize should handle maximum long value without overflow")
        void formatFileSizeMaxValue() throws Exception {
            // This tests that the method doesn't throw an exception for large values
            assertDoesNotThrow(() -> invokePrivateMethod("formatFileSize",
                new Class<?>[]{long.class}, Long.MAX_VALUE));
        }

        @Test
        @DisplayName("escapeCsvField should handle very long strings")
        void escapeCsvFieldLongString() throws Exception {
            String longString = "a".repeat(10000);
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, longString);
            assertEquals(longString, result);
        }

        @Test
        @DisplayName("escapeCsvField should handle string with only quotes")
        void escapeCsvFieldOnlyQuotes() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, "\"\"\"");
            assertEquals("\"\"\"\"\"\"", result);
        }

        @Test
        @DisplayName("escapeCsvField should handle unicode characters")
        void escapeCsvFieldUnicode() throws Exception {
            String result = (String) invokePrivateMethod("escapeCsvField",
                new Class<?>[]{String.class}, "Straße München 日本語");
            assertEquals("Straße München 日本語", result);
        }
    }
}
