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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationError")
class ValidationErrorTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Four-arg constructor sets all values")
        void fourArgConstructor() {
            ValidationError error = new ValidationError(10, 5, "Test error", "WARNING");

            assertEquals(10, error.lineNumber());
            assertEquals(5, error.columnNumber());
            assertEquals("Test error", error.message());
            assertEquals("WARNING", error.severity());
        }

        @Test
        @DisplayName("Three-arg constructor defaults severity to ERROR")
        void threeArgConstructor() {
            ValidationError error = new ValidationError(10, 5, "Test error");

            assertEquals(10, error.lineNumber());
            assertEquals(5, error.columnNumber());
            assertEquals("Test error", error.message());
            assertEquals("ERROR", error.severity());
        }

        @Test
        @DisplayName("Negative line number normalized to 0")
        void negativeLineNormalized() {
            ValidationError error = new ValidationError(-5, 10, "Error", "ERROR");
            assertEquals(0, error.lineNumber());
        }

        @Test
        @DisplayName("Negative column number normalized to 0")
        void negativeColumnNormalized() {
            ValidationError error = new ValidationError(10, -5, "Error", "ERROR");
            assertEquals(0, error.columnNumber());
        }

        @Test
        @DisplayName("Null message defaults to unknown error")
        void nullMessageDefaulted() {
            ValidationError error = new ValidationError(1, 1, null, "ERROR");
            assertEquals("Unknown validation error", error.message());
        }

        @Test
        @DisplayName("Null severity defaults to ERROR")
        void nullSeverityDefaulted() {
            ValidationError error = new ValidationError(1, 1, "Error", null);
            assertEquals("ERROR", error.severity());
        }
    }

    @Nested
    @DisplayName("getDisplayText")
    class DisplayTextTests {

        @Test
        @DisplayName("Shows line and column with message")
        void displayWithLineAndColumn() {
            ValidationError error = new ValidationError(10, 5, "Element not found");

            String display = error.getDisplayText();
            assertTrue(display.contains("Line 10"));
            assertTrue(display.contains("Col 5"));
            assertTrue(display.contains("Element not found"));
        }

        @Test
        @DisplayName("Shows only line when column is 0")
        void displayWithLineOnly() {
            ValidationError error = new ValidationError(10, 0, "Error message");

            String display = error.getDisplayText();
            assertTrue(display.contains("Line 10"));
            assertFalse(display.contains("Col"));
            assertTrue(display.contains("Error message"));
        }

        @Test
        @DisplayName("Shows only message when line is 0")
        void displayWithMessageOnly() {
            ValidationError error = new ValidationError(0, 5, "Error message");

            String display = error.getDisplayText();
            assertFalse(display.contains("Line"));
            assertFalse(display.contains("Col"));
            assertEquals("Error message", display);
        }
    }

    @Nested
    @DisplayName("Record Methods")
    class RecordMethodTests {

        @Test
        @DisplayName("Equality works correctly")
        void equality() {
            ValidationError e1 = new ValidationError(10, 5, "Error", "WARNING");
            ValidationError e2 = new ValidationError(10, 5, "Error", "WARNING");
            ValidationError e3 = new ValidationError(10, 5, "Error", "ERROR");

            assertEquals(e1, e2);
            assertNotEquals(e1, e3);
        }

        @Test
        @DisplayName("HashCode is consistent")
        void hashCodeConsistent() {
            ValidationError e1 = new ValidationError(10, 5, "Error", "ERROR");
            ValidationError e2 = new ValidationError(10, 5, "Error", "ERROR");

            assertEquals(e1.hashCode(), e2.hashCode());
        }
    }
}
