/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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
package org.fxt.freexmltoolkit.controls.v2.xmleditor.schema;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.XsdType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XmlSchemaProvider types and utilities.
 */
@DisplayName("XmlSchemaProvider Tests")
class XmlSchemaProviderTest {

    @Nested
    @DisplayName("XsdType Tests")
    class XsdTypeTests {

        @ParameterizedTest
        @CsvSource({
                "string, STRING",
                "normalizedString, NORMALIZED_STRING",
                "token, TOKEN",
                "boolean, BOOLEAN",
                "integer, INTEGER",
                "int, INTEGER",
                "long, INTEGER",
                "short, INTEGER",
                "byte, INTEGER",
                "decimal, DECIMAL",
                "float, FLOAT",
                "double, DOUBLE",
                "date, DATE",
                "time, TIME",
                "dateTime, DATE_TIME",
                "duration, DURATION",
                "gYear, G_YEAR",
                "gYearMonth, G_YEAR_MONTH",
                "gMonth, G_MONTH",
                "gMonthDay, G_MONTH_DAY",
                "gDay, G_DAY",
                "anyURI, ANY_URI",
                "QName, QNAME",
                "hexBinary, HEX_BINARY",
                "base64Binary, BASE64_BINARY"
        })
        @DisplayName("Should map type name to correct XsdType")
        void shouldMapTypeNameToXsdType(String typeName, XsdType expected) {
            assertEquals(expected, XsdType.fromTypeName(typeName));
        }

        @ParameterizedTest
        @CsvSource({
                "xs:string, STRING",
                "xsd:integer, INTEGER",
                "ns:boolean, BOOLEAN",
                "tns:date, DATE"
        })
        @DisplayName("Should handle prefixed type names")
        void shouldHandlePrefixedTypeNames(String typeName, XsdType expected) {
            assertEquals(expected, XsdType.fromTypeName(typeName));
        }

        @ParameterizedTest
        @ValueSource(strings = {"customType", "myType", "unknownType", "foo"})
        @DisplayName("Should return UNKNOWN for custom types")
        void shouldReturnUnknownForCustomTypes(String typeName) {
            assertEquals(XsdType.UNKNOWN, XsdType.fromTypeName(typeName));
        }

        @Test
        @DisplayName("Should return UNKNOWN for null type name")
        void shouldReturnUnknownForNull() {
            assertEquals(XsdType.UNKNOWN, XsdType.fromTypeName(null));
        }

        @Test
        @DisplayName("Should return UNKNOWN for empty type name")
        void shouldReturnUnknownForEmpty() {
            assertEquals(XsdType.UNKNOWN, XsdType.fromTypeName(""));
        }

        @Test
        @DisplayName("Should correctly identify date/time types")
        void shouldIdentifyDateTimeTypes() {
            assertTrue(XsdType.DATE.isDateTimeType());
            assertTrue(XsdType.TIME.isDateTimeType());
            assertTrue(XsdType.DATE_TIME.isDateTimeType());
            assertTrue(XsdType.DURATION.isDateTimeType());
            assertTrue(XsdType.G_YEAR.isDateTimeType());
            assertTrue(XsdType.G_YEAR_MONTH.isDateTimeType());
            assertTrue(XsdType.G_MONTH.isDateTimeType());
            assertTrue(XsdType.G_MONTH_DAY.isDateTimeType());
            assertTrue(XsdType.G_DAY.isDateTimeType());

            assertFalse(XsdType.STRING.isDateTimeType());
            assertFalse(XsdType.INTEGER.isDateTimeType());
            assertFalse(XsdType.BOOLEAN.isDateTimeType());
        }

        @Test
        @DisplayName("Should correctly identify numeric types")
        void shouldIdentifyNumericTypes() {
            assertTrue(XsdType.INTEGER.isNumericType());
            assertTrue(XsdType.DECIMAL.isNumericType());
            assertTrue(XsdType.FLOAT.isNumericType());
            assertTrue(XsdType.DOUBLE.isNumericType());

            assertFalse(XsdType.STRING.isNumericType());
            assertFalse(XsdType.DATE.isNumericType());
            assertFalse(XsdType.BOOLEAN.isNumericType());
        }

        @Test
        @DisplayName("Should correctly identify string types")
        void shouldIdentifyStringTypes() {
            assertTrue(XsdType.STRING.isStringType());
            assertTrue(XsdType.NORMALIZED_STRING.isStringType());
            assertTrue(XsdType.TOKEN.isStringType());

            assertFalse(XsdType.INTEGER.isStringType());
            assertFalse(XsdType.DATE.isStringType());
            assertFalse(XsdType.BOOLEAN.isStringType());
        }
    }

    @Nested
    @DisplayName("ValidationResult Tests")
    class ValidationResultTests {

        @Test
        @DisplayName("Should create valid result")
        void shouldCreateValidResult() {
            ValidationResult result = ValidationResult.valid();

            assertTrue(result.isValid());
            assertNull(result.errorMessage());
            assertEquals(ValidationSeverity.INFO, result.severity());
        }

        @Test
        @DisplayName("Should create warning result")
        void shouldCreateWarningResult() {
            String message = "This is a warning";
            ValidationResult result = ValidationResult.warning(message);

            assertTrue(result.isValid()); // Warnings are still valid
            assertEquals(message, result.errorMessage());
            assertEquals(ValidationSeverity.WARNING, result.severity());
        }

        @Test
        @DisplayName("Should create error result")
        void shouldCreateErrorResult() {
            String message = "This is an error";
            ValidationResult result = ValidationResult.error(message);

            assertFalse(result.isValid());
            assertEquals(message, result.errorMessage());
            assertEquals(ValidationSeverity.ERROR, result.severity());
        }
    }

    @Nested
    @DisplayName("ValidationSeverity Tests")
    class ValidationSeverityTests {

        @Test
        @DisplayName("Should have correct severity values")
        void shouldHaveCorrectSeverityValues() {
            assertEquals(3, ValidationSeverity.values().length);
            assertNotNull(ValidationSeverity.INFO);
            assertNotNull(ValidationSeverity.WARNING);
            assertNotNull(ValidationSeverity.ERROR);
        }
    }
}
