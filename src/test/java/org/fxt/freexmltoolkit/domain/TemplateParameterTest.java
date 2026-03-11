package org.fxt.freexmltoolkit.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for TemplateParameter domain model.
 */
class TemplateParameterTest {

    // =========================================================================
    // Construction Tests
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Default constructor sets STRING type")
        void defaultConstructor() {
            TemplateParameter p = new TemplateParameter();
            assertEquals(TemplateParameter.ParameterType.STRING, p.getType());
            assertFalse(p.isRequired());
        }

        @Test
        @DisplayName("Name and type constructor")
        void nameTypeConstructor() {
            TemplateParameter p = new TemplateParameter("age", TemplateParameter.ParameterType.INTEGER);
            assertEquals("age", p.getName());
            assertEquals(TemplateParameter.ParameterType.INTEGER, p.getType());
        }

        @Test
        @DisplayName("Name, type, default constructor")
        void nameTypeDefaultConstructor() {
            TemplateParameter p = new TemplateParameter("name", TemplateParameter.ParameterType.STRING, "default");
            assertEquals("name", p.getName());
            assertEquals("default", p.getDefaultValue());
        }

        @Test
        @DisplayName("Name, type, required constructor")
        void nameTypeRequiredConstructor() {
            TemplateParameter p = new TemplateParameter("name", TemplateParameter.ParameterType.STRING, true);
            assertTrue(p.isRequired());
        }
    }

    // =========================================================================
    // Static Factory Methods
    // =========================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryTests {

        @Test
        @DisplayName("stringParam creates STRING type")
        void stringParam() {
            TemplateParameter p = TemplateParameter.stringParam("name");
            assertEquals("name", p.getName());
            assertEquals(TemplateParameter.ParameterType.STRING, p.getType());
        }

        @Test
        @DisplayName("stringParam with default value")
        void stringParamWithDefault() {
            TemplateParameter p = TemplateParameter.stringParam("name", "John");
            assertEquals("John", p.getDefaultValue());
        }

        @Test
        @DisplayName("requiredString creates required STRING")
        void requiredString() {
            TemplateParameter p = TemplateParameter.requiredString("name");
            assertTrue(p.isRequired());
            assertEquals(TemplateParameter.ParameterType.STRING, p.getType());
        }

        @Test
        @DisplayName("intParam creates INTEGER type")
        void intParam() {
            TemplateParameter p = TemplateParameter.intParam("count", 42);
            assertEquals(TemplateParameter.ParameterType.INTEGER, p.getType());
            assertEquals("42", p.getDefaultValue());
        }

        @Test
        @DisplayName("boolParam creates BOOLEAN type with CHECKBOX")
        void boolParam() {
            TemplateParameter p = TemplateParameter.boolParam("enabled", true);
            assertEquals(TemplateParameter.ParameterType.BOOLEAN, p.getType());
            assertEquals(TemplateParameter.InputType.CHECKBOX, p.getInputType());
            assertEquals("true", p.getDefaultValue());
        }

        @Test
        @DisplayName("enumParam creates ENUM type with DROPDOWN")
        void enumParam() {
            TemplateParameter p = TemplateParameter.enumParam("color", "Red", "Green", "Blue");
            assertEquals(TemplateParameter.ParameterType.ENUM, p.getType());
            assertEquals(TemplateParameter.InputType.DROPDOWN, p.getInputType());
            assertEquals(3, p.getAllowedValues().size());
            assertTrue(p.getAllowedValues().contains("Red"));
        }
    }

    // =========================================================================
    // Builder Pattern Tests
    // =========================================================================

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("Fluent builder chain")
        void fluentChain() {
            TemplateParameter p = TemplateParameter.stringParam("name")
                    .displayName("Full Name")
                    .description("Enter your name")
                    .placeholder("John Doe")
                    .helpText("Your full name")
                    .category("Personal")
                    .order(1)
                    .required(true)
                    .length(2, 100)
                    .pattern("[a-zA-Z ]+");

            assertEquals("Full Name", p.getDisplayName());
            assertEquals("Enter your name", p.getDescription());
            assertEquals("John Doe", p.getPlaceholder());
            assertEquals("Your full name", p.getHelpText());
            assertEquals("Personal", p.getCategory());
            assertEquals(1, p.getDisplayOrder());
            assertTrue(p.isRequired());
            assertEquals(2, p.getMinLength());
            assertEquals(100, p.getMaxLength());
            assertNotNull(p.getValidationPattern());
        }

        @Test
        @DisplayName("Range builder")
        void rangeBuilder() {
            TemplateParameter p = TemplateParameter.intParam("age", 25)
                    .range(0, 150);

            assertEquals(0.0, p.getMinValue());
            assertEquals(150.0, p.getMaxValue());
        }

        @Test
        @DisplayName("withType changes type")
        void withType() {
            TemplateParameter p = TemplateParameter.stringParam("field")
                    .withType(TemplateParameter.ParameterType.DECIMAL);

            assertEquals(TemplateParameter.ParameterType.DECIMAL, p.getType());
        }
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Required parameter with null value fails")
        void requiredNull() {
            TemplateParameter p = TemplateParameter.requiredString("name");
            List<String> errors = p.validateValue(null);
            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("Required parameter with empty value fails")
        void requiredEmpty() {
            TemplateParameter p = TemplateParameter.requiredString("name");
            List<String> errors = p.validateValue("");
            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("Optional parameter with null value passes")
        void optionalNull() {
            TemplateParameter p = TemplateParameter.stringParam("name");
            List<String> errors = p.validateValue(null);
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("String validation passes for valid string")
        void validString() {
            TemplateParameter p = TemplateParameter.stringParam("name");
            List<String> errors = p.validateValue("hello");
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Integer validation passes for valid integer")
        void validInteger() {
            TemplateParameter p = TemplateParameter.intParam("count", 0);
            List<String> errors = p.validateValue("42");
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Integer validation fails for non-integer")
        void invalidInteger() {
            TemplateParameter p = TemplateParameter.intParam("count", 0);
            List<String> errors = p.validateValue("abc");
            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("Decimal validation passes")
        void validDecimal() {
            TemplateParameter p = new TemplateParameter("price", TemplateParameter.ParameterType.DECIMAL);
            List<String> errors = p.validateValue("3.14");
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Decimal validation fails for non-number")
        void invalidDecimal() {
            TemplateParameter p = new TemplateParameter("price", TemplateParameter.ParameterType.DECIMAL);
            List<String> errors = p.validateValue("abc");
            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("Boolean validation passes for true/false")
        void validBoolean() {
            TemplateParameter p = TemplateParameter.boolParam("flag", true);
            assertTrue(p.validateValue("true").isEmpty());
            assertTrue(p.validateValue("false").isEmpty());
        }

        @Test
        @DisplayName("Boolean validation fails for non-boolean")
        void invalidBoolean() {
            TemplateParameter p = TemplateParameter.boolParam("flag", true);
            assertFalse(p.validateValue("maybe").isEmpty());
        }

        @Test
        @DisplayName("Enum validation passes for allowed value")
        void validEnum() {
            TemplateParameter p = TemplateParameter.enumParam("color", "Red", "Green", "Blue");
            assertTrue(p.validateValue("Red").isEmpty());
        }

        @Test
        @DisplayName("Enum validation fails for disallowed value")
        void invalidEnum() {
            TemplateParameter p = TemplateParameter.enumParam("color", "Red", "Green", "Blue");
            assertFalse(p.validateValue("Yellow").isEmpty());
        }

        @Test
        @DisplayName("Pattern validation")
        void patternValidation() {
            TemplateParameter p = TemplateParameter.stringParam("code")
                    .pattern("[A-Z]{3}");

            assertTrue(p.validateValue("ABC").isEmpty());
            assertFalse(p.validateValue("abc").isEmpty());
        }

        @Test
        @DisplayName("Length validation - too short")
        void lengthTooShort() {
            TemplateParameter p = TemplateParameter.stringParam("name")
                    .length(3, 10);

            assertFalse(p.validateValue("ab").isEmpty());
        }

        @Test
        @DisplayName("Length validation - too long")
        void lengthTooLong() {
            TemplateParameter p = TemplateParameter.stringParam("name")
                    .length(3, 10);

            assertFalse(p.validateValue("this is way too long").isEmpty());
        }

        @Test
        @DisplayName("Length validation - within bounds")
        void lengthValid() {
            TemplateParameter p = TemplateParameter.stringParam("name")
                    .length(3, 10);

            assertTrue(p.validateValue("hello").isEmpty());
        }

        @Test
        @DisplayName("Range validation - below min")
        void rangeBelowMin() {
            TemplateParameter p = TemplateParameter.intParam("age", 25)
                    .range(0, 150);

            assertFalse(p.validateValue("-1").isEmpty());
        }

        @Test
        @DisplayName("Range validation - above max")
        void rangeAboveMax() {
            TemplateParameter p = TemplateParameter.intParam("age", 25)
                    .range(0, 150);

            assertFalse(p.validateValue("200").isEmpty());
        }

        @Test
        @DisplayName("URL validation")
        void urlValidation() {
            TemplateParameter p = new TemplateParameter("url", TemplateParameter.ParameterType.URL);
            assertTrue(p.validateValue("http://example.com").isEmpty());
            assertTrue(p.validateValue("https://example.com/path").isEmpty());
        }

        @Test
        @DisplayName("Email validation")
        void emailValidation() {
            TemplateParameter p = new TemplateParameter("email", TemplateParameter.ParameterType.EMAIL);
            assertTrue(p.validateValue("user@example.com").isEmpty());
        }

        @Test
        @DisplayName("Date validation")
        void dateValidation() {
            TemplateParameter p = new TemplateParameter("date", TemplateParameter.ParameterType.DATE);
            assertTrue(p.validateValue("2024-01-15").isEmpty());
        }
    }

    // =========================================================================
    // Copy Tests
    // =========================================================================

    @Nested
    @DisplayName("Copy")
    class CopyTests {

        @Test
        @DisplayName("Copy preserves all properties")
        void copyPreservesProperties() {
            TemplateParameter original = TemplateParameter.stringParam("name", "default")
                    .displayName("Full Name")
                    .description("Enter name")
                    .required(true)
                    .length(1, 50);

            TemplateParameter copy = original.copy();

            assertEquals(original.getName(), copy.getName());
            assertEquals(original.getDisplayName(), copy.getDisplayName());
            assertEquals(original.getDescription(), copy.getDescription());
            assertEquals(original.getDefaultValue(), copy.getDefaultValue());
            assertEquals(original.isRequired(), copy.isRequired());
            assertEquals(original.getMinLength(), copy.getMinLength());
            assertEquals(original.getMaxLength(), copy.getMaxLength());
        }

        @Test
        @DisplayName("Copy has new timestamps")
        void copyNewTimestamps() {
            TemplateParameter original = TemplateParameter.stringParam("name");
            TemplateParameter copy = original.copy();

            assertNotNull(copy.getCreated());
            assertNotNull(copy.getLastModified());
        }
    }

    // =========================================================================
    // Enum Tests
    // =========================================================================

    @Nested
    @DisplayName("Enums")
    class EnumTests {

        @Test
        @DisplayName("ParameterType has expected values")
        void parameterTypeValues() {
            assertNotNull(TemplateParameter.ParameterType.valueOf("STRING"));
            assertNotNull(TemplateParameter.ParameterType.valueOf("INTEGER"));
            assertNotNull(TemplateParameter.ParameterType.valueOf("DECIMAL"));
            assertNotNull(TemplateParameter.ParameterType.valueOf("BOOLEAN"));
            assertNotNull(TemplateParameter.ParameterType.valueOf("DATE"));
            assertNotNull(TemplateParameter.ParameterType.valueOf("EMAIL"));
            assertNotNull(TemplateParameter.ParameterType.valueOf("URL"));
            assertNotNull(TemplateParameter.ParameterType.valueOf("ENUM"));
        }

        @Test
        @DisplayName("InputType has expected values")
        void inputTypeValues() {
            assertNotNull(TemplateParameter.InputType.valueOf("TEXT_FIELD"));
            assertNotNull(TemplateParameter.InputType.valueOf("TEXT_AREA"));
            assertNotNull(TemplateParameter.InputType.valueOf("DROPDOWN"));
            assertNotNull(TemplateParameter.InputType.valueOf("CHECKBOX"));
        }
    }

    // =========================================================================
    // Equals and HashCode Tests
    // =========================================================================

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Same name and type are equal")
        void equalParameters() {
            TemplateParameter p1 = TemplateParameter.stringParam("name");
            TemplateParameter p2 = TemplateParameter.stringParam("name");

            assertEquals(p1, p2);
            assertEquals(p1.hashCode(), p2.hashCode());
        }

        @Test
        @DisplayName("Different names are not equal")
        void differentNames() {
            TemplateParameter p1 = TemplateParameter.stringParam("name");
            TemplateParameter p2 = TemplateParameter.stringParam("other");

            assertNotEquals(p1, p2);
        }

        @Test
        @DisplayName("toString includes key info")
        void toStringInfo() {
            TemplateParameter p = TemplateParameter.stringParam("name", "default");
            String str = p.toString();
            assertTrue(str.contains("name"));
        }
    }

    // =========================================================================
    // Multi-value and Advanced Features
    // =========================================================================

    @Nested
    @DisplayName("Advanced Features")
    class AdvancedTests {

        @Test
        @DisplayName("Multi-value support")
        void multiValue() {
            TemplateParameter p = TemplateParameter.stringParam("tags");
            p.setMultiValue(true);
            p.setSeparator(",");

            assertTrue(p.isMultiValue());
            assertEquals(",", p.getSeparator());
        }

        @Test
        @DisplayName("Value prefix and suffix")
        void prefixSuffix() {
            TemplateParameter p = TemplateParameter.stringParam("ns");
            p.setValuePrefix("xmlns:");
            p.setValueSuffix("=\"http://example.com\"");

            assertEquals("xmlns:", p.getValuePrefix());
            assertEquals("=\"http://example.com\"", p.getValueSuffix());
        }

        @Test
        @DisplayName("Sensitive flag")
        void sensitiveFlag() {
            TemplateParameter p = TemplateParameter.stringParam("password");
            p.setSensitive(true);
            assertTrue(p.isSensitive());
        }

        @Test
        @DisplayName("Metadata map")
        void metadata() {
            TemplateParameter p = TemplateParameter.stringParam("field");
            Map<String, Object> meta = new HashMap<>();
            meta.put("source", "schema");
            p.setMetadata(meta);

            assertEquals("schema", p.getMetadata().get("source"));
        }
    }
}
