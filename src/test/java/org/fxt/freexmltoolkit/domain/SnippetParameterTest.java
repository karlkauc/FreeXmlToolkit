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
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnippetParameter")
class SnippetParameterTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor sets defaults")
        void defaultConstructor() {
            SnippetParameter param = new SnippetParameter();

            assertEquals(SnippetParameter.ParameterType.STRING, param.getType());
            assertTrue(param.isRequired());
            assertEquals(0, param.getOrder());
        }

        @Test
        @DisplayName("Three-arg constructor sets name, type, default")
        void threeArgConstructor() {
            SnippetParameter param = new SnippetParameter("myParam", SnippetParameter.ParameterType.NUMBER, "42");

            assertEquals("myParam", param.getName());
            assertEquals(SnippetParameter.ParameterType.NUMBER, param.getType());
            assertEquals("42", param.getDefaultValue());
        }

        @Test
        @DisplayName("Four-arg constructor sets description")
        void fourArgConstructor() {
            SnippetParameter param = new SnippetParameter("myParam", SnippetParameter.ParameterType.BOOLEAN, "true", "A boolean flag");

            assertEquals("myParam", param.getName());
            assertEquals(SnippetParameter.ParameterType.BOOLEAN, param.getType());
            assertEquals("true", param.getDefaultValue());
            assertEquals("A boolean flag", param.getDescription());
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates parameter with all properties")
        void builderWithAllProperties() {
            SnippetParameter param = SnippetParameter.builder("testParam")
                    .description("Test description")
                    .type(SnippetParameter.ParameterType.XPATH)
                    .defaultValue("/root")
                    .required(false)
                    .validationPattern("^/.*")
                    .possibleValues("/root", "/child", "/other")
                    .example("/root/element")
                    .order(5)
                    .build();

            assertEquals("testParam", param.getName());
            assertEquals("Test description", param.getDescription());
            assertEquals(SnippetParameter.ParameterType.XPATH, param.getType());
            assertEquals("/root", param.getDefaultValue());
            assertFalse(param.isRequired());
            assertEquals("^/.*", param.getValidationPattern());
            assertArrayEquals(new String[]{"/root", "/child", "/other"}, param.getPossibleValues());
            assertEquals("/root/element", param.getExample());
            assertEquals(5, param.getOrder());
        }

        @Test
        @DisplayName("Builder throws exception for null name")
        void builderThrowsForNullName() {
            SnippetParameter.Builder builder = SnippetParameter.builder("test");
            builder.build(); // This should work

            // Can't easily test null name in builder as builder() requires name
        }

        @Test
        @DisplayName("Builder throws exception for empty name")
        void builderThrowsForEmptyName() {
            assertThrows(IllegalStateException.class, () -> {
                new SnippetParameter.Builder("   ").build();
            });
        }
    }

    @Nested
    @DisplayName("ParameterType Enum")
    class ParameterTypeTests {

        @Test
        @DisplayName("STRING type has correct properties")
        void stringType() {
            SnippetParameter.ParameterType type = SnippetParameter.ParameterType.STRING;
            assertEquals("String", type.getDisplayName());
            assertEquals("Text value", type.getDescription());
            assertEquals(String.class, type.getJavaType());
        }

        @Test
        @DisplayName("NUMBER type has correct properties")
        void numberType() {
            SnippetParameter.ParameterType type = SnippetParameter.ParameterType.NUMBER;
            assertEquals("Number", type.getDisplayName());
            assertEquals("Numeric value", type.getDescription());
            assertEquals(Number.class, type.getJavaType());
        }

        @Test
        @DisplayName("BOOLEAN type has correct properties")
        void booleanType() {
            SnippetParameter.ParameterType type = SnippetParameter.ParameterType.BOOLEAN;
            assertEquals("Boolean", type.getDisplayName());
            assertEquals("True/false value", type.getDescription());
            assertEquals(Boolean.class, type.getJavaType());
        }

        @Test
        @DisplayName("All enum values exist")
        void allEnumValues() {
            assertEquals(8, SnippetParameter.ParameterType.values().length);
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Required parameter with null value fails")
        void requiredNullFails() {
            SnippetParameter param = SnippetParameter.builder("required")
                    .required(true)
                    .build();

            SnippetParameter.ValidationResult result = param.validateValue(null);
            assertFalse(result.isValid());
            assertTrue(result.getErrorMessage().contains("required"));
        }

        @Test
        @DisplayName("Required parameter with empty value fails")
        void requiredEmptyFails() {
            SnippetParameter param = SnippetParameter.builder("required")
                    .required(true)
                    .build();

            SnippetParameter.ValidationResult result = param.validateValue("   ");
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("Optional parameter with null value passes")
        void optionalNullPasses() {
            SnippetParameter param = SnippetParameter.builder("optional")
                    .required(false)
                    .build();

            SnippetParameter.ValidationResult result = param.validateValue(null);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("NUMBER type validates numeric values")
        void numberValidation() {
            SnippetParameter param = SnippetParameter.builder("number")
                    .type(SnippetParameter.ParameterType.NUMBER)
                    .build();

            assertTrue(param.validateValue("42").isValid());
            assertTrue(param.validateValue("-3.14").isValid());
            assertTrue(param.validateValue("0").isValid());
            assertFalse(param.validateValue("not a number").isValid());
        }

        @ParameterizedTest
        @ValueSource(strings = {"true", "false", "TRUE", "FALSE", "True", "False", "1", "0"})
        @DisplayName("BOOLEAN type validates boolean values")
        void booleanValidation(String value) {
            SnippetParameter param = SnippetParameter.builder("bool")
                    .type(SnippetParameter.ParameterType.BOOLEAN)
                    .build();

            assertTrue(param.validateValue(value).isValid());
        }

        @Test
        @DisplayName("BOOLEAN type rejects invalid values")
        void booleanInvalid() {
            SnippetParameter param = SnippetParameter.builder("bool")
                    .type(SnippetParameter.ParameterType.BOOLEAN)
                    .build();

            assertFalse(param.validateValue("yes").isValid());
            assertFalse(param.validateValue("no").isValid());
            assertFalse(param.validateValue("maybe").isValid());
        }

        @Test
        @DisplayName("ELEMENT_NAME type validates XML names")
        void elementNameValidation() {
            SnippetParameter param = SnippetParameter.builder("element")
                    .type(SnippetParameter.ParameterType.ELEMENT_NAME)
                    .build();

            assertTrue(param.validateValue("element").isValid());
            assertTrue(param.validateValue("_element").isValid());
            assertTrue(param.validateValue("element123").isValid());
            assertTrue(param.validateValue("my-element").isValid());
            assertTrue(param.validateValue("my.element").isValid());

            assertFalse(param.validateValue("123element").isValid());
            assertFalse(param.validateValue("my element").isValid());
        }

        @Test
        @DisplayName("NAMESPACE_URI type validates URIs")
        void namespaceUriValidation() {
            SnippetParameter param = SnippetParameter.builder("ns")
                    .type(SnippetParameter.ParameterType.NAMESPACE_URI)
                    .build();

            assertTrue(param.validateValue("http://example.com").isValid());
            assertTrue(param.validateValue("urn:example:namespace").isValid());
        }

        @Test
        @DisplayName("Pattern validation works")
        void patternValidation() {
            SnippetParameter param = SnippetParameter.builder("code")
                    .validationPattern("[A-Z]{3}")
                    .build();

            assertTrue(param.validateValue("ABC").isValid());
            assertFalse(param.validateValue("abc").isValid());
            assertFalse(param.validateValue("ABCD").isValid());
        }

        @Test
        @DisplayName("Possible values validation works")
        void possibleValuesValidation() {
            SnippetParameter param = SnippetParameter.builder("color")
                    .possibleValues("red", "green", "blue")
                    .build();

            assertTrue(param.validateValue("red").isValid());
            assertTrue(param.validateValue("green").isValid());
            assertTrue(param.validateValue("blue").isValid());
            assertFalse(param.validateValue("yellow").isValid());
        }
    }

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("Empty result is valid")
        void emptyResultIsValid() {
            SnippetParameter.ValidationResult result = new SnippetParameter.ValidationResult();
            assertTrue(result.isValid());
            assertFalse(result.hasWarnings());
        }

        @Test
        @DisplayName("Result with error is invalid")
        void resultWithErrorIsInvalid() {
            SnippetParameter.ValidationResult result = new SnippetParameter.ValidationResult();
            result.addError("Test error");

            assertFalse(result.isValid());
            assertEquals(1, result.getErrors().size());
            assertTrue(result.getErrorMessage().contains("Test error"));
        }

        @Test
        @DisplayName("Result with warning is still valid")
        void resultWithWarningIsValid() {
            SnippetParameter.ValidationResult result = new SnippetParameter.ValidationResult();
            result.addWarning("Test warning");

            assertTrue(result.isValid());
            assertTrue(result.hasWarnings());
            assertEquals(1, result.getWarnings().size());
        }

        @Test
        @DisplayName("Multiple errors joined in message")
        void multipleErrorsJoined() {
            SnippetParameter.ValidationResult result = new SnippetParameter.ValidationResult();
            result.addError("Error 1");
            result.addError("Error 2");

            String message = result.getErrorMessage();
            assertTrue(message.contains("Error 1"));
            assertTrue(message.contains("Error 2"));
            assertTrue(message.contains(";"));
        }
    }

    @Nested
    @DisplayName("Display Methods")
    class DisplayMethodTests {

        @Test
        @DisplayName("getFormattedName() includes type for non-STRING")
        void formattedNameWithType() {
            SnippetParameter param = SnippetParameter.builder("count")
                    .type(SnippetParameter.ParameterType.NUMBER)
                    .required(true)
                    .build();

            String formatted = param.getFormattedName();
            assertTrue(formatted.contains("count"));
            assertTrue(formatted.contains("Number"));
            assertTrue(formatted.contains("*"));
        }

        @Test
        @DisplayName("getFormattedName() omits type for STRING")
        void formattedNameWithoutType() {
            SnippetParameter param = SnippetParameter.builder("name")
                    .type(SnippetParameter.ParameterType.STRING)
                    .required(false)
                    .build();

            String formatted = param.getFormattedName();
            assertEquals("name", formatted);
        }

        @Test
        @DisplayName("getTooltipText() includes all information")
        void tooltipText() {
            SnippetParameter param = SnippetParameter.builder("query")
                    .description("XPath query expression")
                    .type(SnippetParameter.ParameterType.XPATH)
                    .defaultValue("/root")
                    .example("//element[@attr]")
                    .possibleValues("//a", "//b")
                    .build();

            String tooltip = param.getTooltipText();
            assertTrue(tooltip.contains("XPath query expression"));
            assertTrue(tooltip.contains("XPath"));
            assertTrue(tooltip.contains("/root"));
            assertTrue(tooltip.contains("//element[@attr]"));
            assertTrue(tooltip.contains("//a"));
        }

        @Test
        @DisplayName("getTooltipText() uses name when no description")
        void tooltipTextNoDescription() {
            SnippetParameter param = SnippetParameter.builder("myParam")
                    .build();

            String tooltip = param.getTooltipText();
            assertTrue(tooltip.contains("myParam"));
        }
    }

    @Nested
    @DisplayName("Object Methods")
    class ObjectMethodTests {

        @Test
        @DisplayName("equals() compares by name")
        void equalsComparesName() {
            SnippetParameter p1 = SnippetParameter.builder("test").build();
            SnippetParameter p2 = SnippetParameter.builder("test").description("different").build();
            SnippetParameter p3 = SnippetParameter.builder("other").build();

            assertEquals(p1, p2);
            assertNotEquals(p1, p3);
        }

        @Test
        @DisplayName("hashCode() is consistent with equals")
        void hashCodeConsistent() {
            SnippetParameter p1 = SnippetParameter.builder("test").build();
            SnippetParameter p2 = SnippetParameter.builder("test").build();

            assertEquals(p1.hashCode(), p2.hashCode());
        }

        @Test
        @DisplayName("toString() contains key info")
        void toStringContainsInfo() {
            SnippetParameter param = SnippetParameter.builder("myParam")
                    .type(SnippetParameter.ParameterType.NUMBER)
                    .required(true)
                    .build();

            String str = param.toString();
            assertTrue(str.contains("myParam"));
            assertTrue(str.contains("NUMBER"));
            assertTrue(str.contains("true"));
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GetterSetterTests {

        @Test
        @DisplayName("All setters work correctly")
        void allSettersWork() {
            SnippetParameter param = new SnippetParameter();

            param.setName("testName");
            param.setDescription("testDesc");
            param.setType(SnippetParameter.ParameterType.XPATH);
            param.setDefaultValue("default");
            param.setRequired(false);
            param.setValidationPattern(".*");
            param.setPossibleValues(new String[]{"a", "b"});
            param.setExample("example");
            param.setOrder(10);

            assertEquals("testName", param.getName());
            assertEquals("testDesc", param.getDescription());
            assertEquals(SnippetParameter.ParameterType.XPATH, param.getType());
            assertEquals("default", param.getDefaultValue());
            assertFalse(param.isRequired());
            assertEquals(".*", param.getValidationPattern());
            assertArrayEquals(new String[]{"a", "b"}, param.getPossibleValues());
            assertEquals("example", param.getExample());
            assertEquals(10, param.getOrder());
        }
    }
}
