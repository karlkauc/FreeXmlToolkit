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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TemplateValidationRule")
class TemplateValidationRuleTest {

    @Nested
    @DisplayName("RuleType Enum")
    class RuleTypeTests {

        @Test
        @DisplayName("All rule types exist")
        void allRuleTypesExist() {
            assertEquals(5, TemplateValidationRule.RuleType.values().length);
            assertNotNull(TemplateValidationRule.RuleType.REQUIRED_IF);
            assertNotNull(TemplateValidationRule.RuleType.MUTUALLY_EXCLUSIVE);
            assertNotNull(TemplateValidationRule.RuleType.DEPENDENCY);
            assertNotNull(TemplateValidationRule.RuleType.PATTERN_MATCH);
            assertNotNull(TemplateValidationRule.RuleType.CUSTOM);
        }

        @Test
        @DisplayName("valueOf returns correct enum")
        void valueOfReturnsCorrectEnum() {
            assertEquals(TemplateValidationRule.RuleType.REQUIRED_IF,
                    TemplateValidationRule.RuleType.valueOf("REQUIRED_IF"));
            assertEquals(TemplateValidationRule.RuleType.PATTERN_MATCH,
                    TemplateValidationRule.RuleType.valueOf("PATTERN_MATCH"));
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor creates active rule")
        void defaultConstructorCreatesActiveRule() {
            TemplateValidationRule rule = new TemplateValidationRule();

            assertTrue(rule.isActive());
        }

        @Test
        @DisplayName("Parameterized constructor sets values")
        void parameterizedConstructorSetsValues() {
            TemplateValidationRule rule = new TemplateValidationRule(
                    "Test Rule",
                    "targetParam",
                    "Error message"
            );

            assertEquals("Test Rule", rule.getName());
            assertEquals("targetParam", rule.getTargetParameter());
            assertEquals("Error message", rule.getErrorMessage());
            assertEquals(TemplateValidationRule.RuleType.CUSTOM, rule.getRuleType());
            assertTrue(rule.isActive());
        }
    }

    @Nested
    @DisplayName("Validation - Inactive Rule")
    class InactiveRuleTests {

        @Test
        @DisplayName("Inactive rule always returns true")
        void inactiveRuleAlwaysReturnsTrue() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setActive(false);
            rule.setRuleType(TemplateValidationRule.RuleType.CUSTOM);
            rule.setCustomValidator(params -> false); // Would fail if active

            Map<String, String> params = new HashMap<>();
            params.put("param1", "value1");

            assertTrue(rule.validate(params));
        }
    }

    @Nested
    @DisplayName("Validation - Pattern Match")
    class PatternMatchTests {

        @Test
        @DisplayName("Pattern match validates matching value")
        void patternMatchValidatesMatchingValue() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.PATTERN_MATCH);
            rule.setTargetParameter("email");
            rule.setValidationPattern(Pattern.compile("^[\\w.]+@[\\w.]+\\.[a-z]{2,}$"));

            Map<String, String> params = new HashMap<>();
            params.put("email", "test@example.com");

            assertTrue(rule.validate(params));
        }

        @Test
        @DisplayName("Pattern match rejects non-matching value")
        void patternMatchRejectsNonMatchingValue() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.PATTERN_MATCH);
            rule.setTargetParameter("email");
            rule.setValidationPattern(Pattern.compile("^[\\w.]+@[\\w.]+\\.[a-z]{2,}$"));

            Map<String, String> params = new HashMap<>();
            params.put("email", "invalid-email");

            assertFalse(rule.validate(params));
        }

        @Test
        @DisplayName("Pattern match accepts null value")
        void patternMatchAcceptsNullValue() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.PATTERN_MATCH);
            rule.setTargetParameter("email");
            rule.setValidationPattern(Pattern.compile("^[\\w.]+@[\\w.]+\\.[a-z]{2,}$"));

            Map<String, String> params = new HashMap<>();
            // email not set

            assertTrue(rule.validate(params));
        }

        @Test
        @DisplayName("Pattern match returns true when no pattern set")
        void patternMatchReturnsTrueWhenNoPatternSet() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.PATTERN_MATCH);
            rule.setTargetParameter("email");
            // No pattern set

            Map<String, String> params = new HashMap<>();
            params.put("email", "any-value");

            assertTrue(rule.validate(params));
        }

        @Test
        @DisplayName("Pattern match returns true when no target parameter set")
        void patternMatchReturnsTrueWhenNoTargetSet() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.PATTERN_MATCH);
            rule.setValidationPattern(Pattern.compile(".*"));
            // No target parameter set

            Map<String, String> params = new HashMap<>();
            params.put("email", "any-value");

            assertTrue(rule.validate(params));
        }
    }

    @Nested
    @DisplayName("Validation - Custom")
    class CustomValidationTests {

        @Test
        @DisplayName("Custom validator is executed")
        void customValidatorIsExecuted() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.CUSTOM);
            rule.setCustomValidator(params ->
                    params.containsKey("required") && !params.get("required").isEmpty()
            );

            Map<String, String> validParams = new HashMap<>();
            validParams.put("required", "value");
            assertTrue(rule.validate(validParams));

            Map<String, String> invalidParams = new HashMap<>();
            invalidParams.put("required", "");
            assertFalse(rule.validate(invalidParams));

            Map<String, String> missingParams = new HashMap<>();
            assertFalse(rule.validate(missingParams));
        }

        @Test
        @DisplayName("Custom validation returns true when no validator set")
        void customValidationReturnsTrueWhenNoValidatorSet() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.CUSTOM);
            // No validator set

            Map<String, String> params = new HashMap<>();
            assertTrue(rule.validate(params));
        }
    }

    @Nested
    @DisplayName("Validation - Other Rule Types")
    class OtherRuleTypeTests {

        @Test
        @DisplayName("REQUIRED_IF always returns true (placeholder implementation)")
        void requiredIfReturnsTrue() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.REQUIRED_IF);

            Map<String, String> params = new HashMap<>();
            assertTrue(rule.validate(params));
        }

        @Test
        @DisplayName("MUTUALLY_EXCLUSIVE always returns true (placeholder implementation)")
        void mutuallyExclusiveReturnsTrue() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.MUTUALLY_EXCLUSIVE);

            Map<String, String> params = new HashMap<>();
            assertTrue(rule.validate(params));
        }

        @Test
        @DisplayName("DEPENDENCY always returns true (placeholder implementation)")
        void dependencyReturnsTrue() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.DEPENDENCY);

            Map<String, String> params = new HashMap<>();
            assertTrue(rule.validate(params));
        }
    }

    @Nested
    @DisplayName("Validation - Null Rule Type")
    class NullRuleTypeTests {

        @Test
        @DisplayName("Null rule type throws NullPointerException")
        void nullRuleTypeThrowsException() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(null);

            Map<String, String> params = new HashMap<>();
            // The switch statement doesn't handle null, so it throws NPE
            assertThrows(NullPointerException.class, () -> rule.validate(params));
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("All properties can be set and retrieved")
        void allPropertiesCanBeSetAndRetrieved() {
            TemplateValidationRule rule = new TemplateValidationRule();

            rule.setId("test-id");
            rule.setName("Test Rule");
            rule.setDescription("Test description");
            rule.setErrorMessage("Error message");
            rule.setRuleType(TemplateValidationRule.RuleType.PATTERN_MATCH);
            rule.setActive(false);
            rule.setTargetParameter("param1");
            rule.setTargetParameters(List.of("param1", "param2"));
            rule.setValidationExpression("expression");
            rule.setValidationPattern(Pattern.compile(".*"));
            rule.setCustomValidator(params -> true);

            assertEquals("test-id", rule.getId());
            assertEquals("Test Rule", rule.getName());
            assertEquals("Test description", rule.getDescription());
            assertEquals("Error message", rule.getErrorMessage());
            assertEquals(TemplateValidationRule.RuleType.PATTERN_MATCH, rule.getRuleType());
            assertFalse(rule.isActive());
            assertEquals("param1", rule.getTargetParameter());
            assertEquals(2, rule.getTargetParameters().size());
            assertEquals("expression", rule.getValidationExpression());
            assertNotNull(rule.getValidationPattern());
            assertNotNull(rule.getCustomValidator());
        }
    }

    @Nested
    @DisplayName("Complex Scenarios")
    class ComplexScenarioTests {

        @Test
        @DisplayName("Custom validator with multiple conditions")
        void customValidatorWithMultipleConditions() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.CUSTOM);
            rule.setCustomValidator(params -> {
                String minStr = params.get("min");
                String maxStr = params.get("max");

                if (minStr == null || maxStr == null) {
                    return true; // Optional params
                }

                try {
                    int min = Integer.parseInt(minStr);
                    int max = Integer.parseInt(maxStr);
                    return min < max;
                } catch (NumberFormatException e) {
                    return false;
                }
            });

            // Valid: min < max
            Map<String, String> validParams = new HashMap<>();
            validParams.put("min", "10");
            validParams.put("max", "100");
            assertTrue(rule.validate(validParams));

            // Invalid: min >= max
            Map<String, String> invalidParams = new HashMap<>();
            invalidParams.put("min", "100");
            invalidParams.put("max", "10");
            assertFalse(rule.validate(invalidParams));

            // Valid: missing params (optional)
            Map<String, String> missingParams = new HashMap<>();
            assertTrue(rule.validate(missingParams));

            // Invalid: non-numeric
            Map<String, String> nonNumericParams = new HashMap<>();
            nonNumericParams.put("min", "abc");
            nonNumericParams.put("max", "xyz");
            assertFalse(rule.validate(nonNumericParams));
        }

        @Test
        @DisplayName("Pattern validation with numeric pattern")
        void patternValidationWithNumericPattern() {
            TemplateValidationRule rule = new TemplateValidationRule();
            rule.setRuleType(TemplateValidationRule.RuleType.PATTERN_MATCH);
            rule.setTargetParameter("port");
            rule.setValidationPattern(Pattern.compile("^\\d{1,5}$")); // 1-5 digits

            Map<String, String> validParams = new HashMap<>();
            validParams.put("port", "8080");
            assertTrue(rule.validate(validParams));

            Map<String, String> invalidParams = new HashMap<>();
            invalidParams.put("port", "123456"); // Too many digits
            assertFalse(rule.validate(invalidParams));

            Map<String, String> letterParams = new HashMap<>();
            letterParams.put("port", "abc");
            assertFalse(rule.validate(letterParams));
        }
    }
}
