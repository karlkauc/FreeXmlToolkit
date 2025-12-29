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

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnippetValidationRule")
class SnippetValidationRuleTest {

    @Nested
    @DisplayName("RuleType Enum")
    class RuleTypeTests {

        @Test
        @DisplayName("All rule types have display names")
        void allTypesHaveDisplayNames() {
            for (SnippetValidationRule.RuleType type : SnippetValidationRule.RuleType.values()) {
                assertNotNull(type.getDisplayName());
                assertFalse(type.getDisplayName().isEmpty());
            }
        }

        @Test
        @DisplayName("All rule types have descriptions")
        void allTypesHaveDescriptions() {
            for (SnippetValidationRule.RuleType type : SnippetValidationRule.RuleType.values()) {
                assertNotNull(type.getDescription());
                assertFalse(type.getDescription().isEmpty());
            }
        }

        @Test
        @DisplayName("SYNTAX type has correct values")
        void syntaxTypeValues() {
            SnippetValidationRule.RuleType type = SnippetValidationRule.RuleType.SYNTAX;
            assertEquals("Syntax Validation", type.getDisplayName());
            assertTrue(type.getDescription().contains("syntax"));
        }

        @Test
        @DisplayName("SECURITY type has correct values")
        void securityTypeValues() {
            SnippetValidationRule.RuleType type = SnippetValidationRule.RuleType.SECURITY;
            assertEquals("Security Check", type.getDisplayName());
            assertTrue(type.getDescription().contains("security"));
        }
    }

    @Nested
    @DisplayName("Severity Enum")
    class SeverityTests {

        @Test
        @DisplayName("All severities have display names")
        void allSeveritiesHaveDisplayNames() {
            for (SnippetValidationRule.Severity severity : SnippetValidationRule.Severity.values()) {
                assertNotNull(severity.getDisplayName());
                assertFalse(severity.getDisplayName().isEmpty());
            }
        }

        @Test
        @DisplayName("All severities have colors")
        void allSeveritiesHaveColors() {
            for (SnippetValidationRule.Severity severity : SnippetValidationRule.Severity.values()) {
                assertNotNull(severity.getColor());
                assertTrue(severity.getColor().startsWith("#"));
            }
        }

        @Test
        @DisplayName("ERROR severity has correct color")
        void errorSeverityColor() {
            assertEquals("#dc3545", SnippetValidationRule.Severity.ERROR.getColor());
        }

        @Test
        @DisplayName("WARNING severity has correct color")
        void warningSeverityColor() {
            assertEquals("#ffc107", SnippetValidationRule.Severity.WARNING.getColor());
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor sets defaults")
        void defaultConstructorSetsDefaults() {
            SnippetValidationRule rule = new SnippetValidationRule();

            assertTrue(rule.isEnabled());
            assertEquals(SnippetValidationRule.Severity.WARNING, rule.getSeverity());
        }

        @Test
        @DisplayName("Parameterized constructor sets values")
        void parameterizedConstructorSetsValues() {
            SnippetValidationRule rule = new SnippetValidationRule(
                    "Test Rule",
                    SnippetValidationRule.RuleType.SYNTAX,
                    SnippetValidationRule.Severity.ERROR,
                    "Test description"
            );

            assertEquals("Test Rule", rule.getName());
            assertEquals(SnippetValidationRule.RuleType.SYNTAX, rule.getType());
            assertEquals(SnippetValidationRule.Severity.ERROR, rule.getSeverity());
            assertEquals("Test description", rule.getDescription());
            assertTrue(rule.isEnabled());
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates rule with pattern")
        void builderCreatesRuleWithPattern() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test Rule")
                    .type(SnippetValidationRule.RuleType.PERFORMANCE)
                    .severity(SnippetValidationRule.Severity.WARNING)
                    .pattern("//")
                    .build();

            assertEquals("Test Rule", rule.getName());
            assertEquals(SnippetValidationRule.RuleType.PERFORMANCE, rule.getType());
            assertNotNull(rule.getPattern());
        }

        @Test
        @DisplayName("Builder creates rule with custom validator")
        void builderCreatesRuleWithValidator() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test Rule")
                    .type(SnippetValidationRule.RuleType.CUSTOM)
                    .validator(query -> new SnippetValidationRule.ValidationResult())
                    .build();

            assertNotNull(rule.getCustomValidator());
        }

        @Test
        @DisplayName("Builder accepts Pattern object")
        void builderAcceptsPatternObject() {
            Pattern pattern = Pattern.compile("\\*");
            SnippetValidationRule rule = SnippetValidationRule.builder("Test Rule")
                    .pattern(pattern)
                    .build();

            assertEquals(pattern, rule.getPattern());
        }

        @Test
        @DisplayName("Builder throws for missing name")
        void builderThrowsForMissingName() {
            assertThrows(IllegalStateException.class, () ->
                    SnippetValidationRule.builder("")
                            .pattern("test")
                            .build()
            );
        }

        @Test
        @DisplayName("Builder throws for missing pattern and validator")
        void builderThrowsForMissingPatternAndValidator() {
            assertThrows(IllegalStateException.class, () ->
                    SnippetValidationRule.builder("Test Rule")
                            .build()
            );
        }

        @Test
        @DisplayName("Builder sets all optional fields")
        void builderSetsAllOptionalFields() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Full Rule")
                    .description("Full description")
                    .type(SnippetValidationRule.RuleType.BEST_PRACTICE)
                    .severity(SnippetValidationRule.Severity.INFO)
                    .enabled(false)
                    .pattern("test")
                    .errorMessage("Error message")
                    .suggestion("Try this instead")
                    .documentationUrl("http://docs.example.com")
                    .build();

            assertEquals("Full description", rule.getDescription());
            assertEquals(SnippetValidationRule.RuleType.BEST_PRACTICE, rule.getType());
            assertEquals(SnippetValidationRule.Severity.INFO, rule.getSeverity());
            assertFalse(rule.isEnabled());
            assertEquals("Error message", rule.getErrorMessage());
            assertEquals("Try this instead", rule.getSuggestion());
            assertEquals("http://docs.example.com", rule.getDocumentationUrl());
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Disabled rule returns empty result")
        void disabledRuleReturnsEmptyResult() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test")
                    .pattern("//")
                    .enabled(false)
                    .build();

            SnippetValidationRule.ValidationResult result = rule.validate("//element");

            assertTrue(result.isValid());
            assertFalse(result.hasAnyIssues());
        }

        @Test
        @DisplayName("Null query returns empty result")
        void nullQueryReturnsEmptyResult() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test")
                    .pattern("//")
                    .build();

            SnippetValidationRule.ValidationResult result = rule.validate(null);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Pattern match adds error for ERROR severity")
        void patternMatchAddsErrorForErrorSeverity() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test")
                    .pattern("//")
                    .severity(SnippetValidationRule.Severity.ERROR)
                    .errorMessage("Double slash detected")
                    .build();

            SnippetValidationRule.ValidationResult result = rule.validate("/element"); // No match

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Double slash")));
        }

        @Test
        @DisplayName("Pattern match adds warning for WARNING severity")
        void patternMatchAddsWarningForWarningSeverity() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test")
                    .pattern("//")
                    .severity(SnippetValidationRule.Severity.WARNING)
                    .build();

            SnippetValidationRule.ValidationResult result = rule.validate("/element");

            assertTrue(result.isValid()); // Warnings don't make it invalid
            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("Pattern match adds info for INFO severity")
        void patternMatchAddsInfoForInfoSeverity() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test")
                    .pattern("//")
                    .severity(SnippetValidationRule.Severity.INFO)
                    .build();

            SnippetValidationRule.ValidationResult result = rule.validate("/element");

            assertTrue(result.hasInfo());
        }

        @Test
        @DisplayName("Pattern match adds suggestion for SUGGESTION severity")
        void patternMatchAddsSuggestionForSuggestionSeverity() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test")
                    .pattern("//")
                    .severity(SnippetValidationRule.Severity.SUGGESTION)
                    .build();

            SnippetValidationRule.ValidationResult result = rule.validate("/element");

            assertTrue(result.hasSuggestions());
        }

        @Test
        @DisplayName("Pattern match passes when pattern found")
        void patternMatchPassesWhenPatternFound() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test")
                    .pattern("//")
                    .severity(SnippetValidationRule.Severity.ERROR)
                    .build();

            SnippetValidationRule.ValidationResult result = rule.validate("//element");

            assertTrue(result.isValid());
            assertFalse(result.hasAnyIssues());
        }

        @Test
        @DisplayName("Custom validator is called")
        void customValidatorIsCalled() {
            SnippetValidationRule rule = SnippetValidationRule.builder("Test")
                    .validator(query -> {
                        SnippetValidationRule.ValidationResult result = new SnippetValidationRule.ValidationResult();
                        if (query.contains("forbidden")) {
                            result.addError("Forbidden word detected");
                        }
                        return result;
                    })
                    .build();

            SnippetValidationRule.ValidationResult result = rule.validate("query with forbidden word");

            assertFalse(result.isValid());
            assertTrue(result.getErrors().get(0).contains("Forbidden"));
        }
    }

    @Nested
    @DisplayName("CommonRules Factory")
    class CommonRulesTests {

        @Test
        @DisplayName("noDoubleSlash creates valid rule")
        void noDoubleSlashCreatesValidRule() {
            SnippetValidationRule rule = SnippetValidationRule.CommonRules.noDoubleSlash();

            assertNotNull(rule);
            assertEquals("Avoid Double Slash", rule.getName());
            assertEquals(SnippetValidationRule.RuleType.PERFORMANCE, rule.getType());
            assertEquals(SnippetValidationRule.Severity.WARNING, rule.getSeverity());
        }

        @Test
        @DisplayName("noWildcards creates valid rule")
        void noWildcardsCreatesValidRule() {
            SnippetValidationRule rule = SnippetValidationRule.CommonRules.noWildcards();

            assertNotNull(rule);
            assertEquals("Minimize Wildcards", rule.getName());
            assertEquals(SnippetValidationRule.RuleType.PERFORMANCE, rule.getType());
        }

        @Test
        @DisplayName("requireNamespaces creates valid rule")
        void requireNamespacesCreatesValidRule() {
            SnippetValidationRule rule = SnippetValidationRule.CommonRules.requireNamespaces();

            assertNotNull(rule);
            assertEquals(SnippetValidationRule.RuleType.BEST_PRACTICE, rule.getType());
        }

        @Test
        @DisplayName("syntaxCheck validates balanced brackets")
        void syntaxCheckValidatesBalancedBrackets() {
            SnippetValidationRule rule = SnippetValidationRule.CommonRules.syntaxCheck();

            // Unbalanced brackets
            SnippetValidationRule.ValidationResult result = rule.validate("//element[position() > 1");
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("brackets")));

            // Balanced brackets
            result = rule.validate("//element[position() > 1]");
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("syntaxCheck validates balanced parentheses")
        void syntaxCheckValidatesBalancedParentheses() {
            SnippetValidationRule rule = SnippetValidationRule.CommonRules.syntaxCheck();

            // Unbalanced parentheses
            SnippetValidationRule.ValidationResult result = rule.validate("//element[contains(text(), 'value']");
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("parentheses")));
        }

        @Test
        @DisplayName("syntaxCheck detects empty predicates")
        void syntaxCheckDetectsEmptyPredicates() {
            SnippetValidationRule rule = SnippetValidationRule.CommonRules.syntaxCheck();

            SnippetValidationRule.ValidationResult result = rule.validate("//element[]");
            assertTrue(result.hasWarnings());
            assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Empty predicate")));
        }

        @Test
        @DisplayName("securityCheck detects document access")
        void securityCheckDetectsDocumentAccess() {
            SnippetValidationRule rule = SnippetValidationRule.CommonRules.securityCheck();

            SnippetValidationRule.ValidationResult result = rule.validate("document('external.xml')//element");
            assertTrue(result.hasWarnings());
            assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("External document")));
        }

        @Test
        @DisplayName("securityCheck detects system functions")
        void securityCheckDetectsSystemFunctions() {
            SnippetValidationRule rule = SnippetValidationRule.CommonRules.securityCheck();

            SnippetValidationRule.ValidationResult result = rule.validate("system-property('xsl:version')");
            assertTrue(result.hasWarnings());
            assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("System function")));
        }

        @Test
        @DisplayName("getAllRules returns all common rules")
        void getAllRulesReturnsAllCommonRules() {
            List<SnippetValidationRule> rules = SnippetValidationRule.CommonRules.getAllRules();

            assertEquals(5, rules.size());
            assertTrue(rules.stream().anyMatch(r -> r.getName().contains("Syntax")));
            assertTrue(rules.stream().anyMatch(r -> r.getName().contains("Double Slash")));
            assertTrue(rules.stream().anyMatch(r -> r.getName().contains("Wildcards")));
            assertTrue(rules.stream().anyMatch(r -> r.getName().contains("Namespace")));
            assertTrue(rules.stream().anyMatch(r -> r.getName().contains("Security")));
        }
    }

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("Empty result is valid")
        void emptyResultIsValid() {
            SnippetValidationRule.ValidationResult result = new SnippetValidationRule.ValidationResult();

            assertTrue(result.isValid());
            assertFalse(result.hasWarnings());
            assertFalse(result.hasInfo());
            assertFalse(result.hasSuggestions());
            assertFalse(result.hasAnyIssues());
        }

        @Test
        @DisplayName("Result with error is invalid")
        void resultWithErrorIsInvalid() {
            SnippetValidationRule.ValidationResult result = new SnippetValidationRule.ValidationResult();
            result.addError("Test error");

            assertFalse(result.isValid());
            assertTrue(result.hasAnyIssues());
            assertEquals(1, result.getErrors().size());
        }

        @Test
        @DisplayName("Result with warning is valid but has warnings")
        void resultWithWarningIsValidButHasWarnings() {
            SnippetValidationRule.ValidationResult result = new SnippetValidationRule.ValidationResult();
            result.addWarning("Test warning");

            assertTrue(result.isValid());
            assertTrue(result.hasWarnings());
            assertTrue(result.hasAnyIssues());
        }

        @Test
        @DisplayName("getErrors returns defensive copy")
        void getErrorsReturnsDefensiveCopy() {
            SnippetValidationRule.ValidationResult result = new SnippetValidationRule.ValidationResult();
            result.addError("Error 1");

            List<String> errors = result.getErrors();
            errors.add("Error 2");

            assertEquals(1, result.getErrors().size()); // Original unchanged
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("All properties can be set and retrieved")
        void allPropertiesCanBeSetAndRetrieved() {
            SnippetValidationRule rule = new SnippetValidationRule();

            rule.setId("test-id");
            rule.setName("Test Name");
            rule.setDescription("Test Description");
            rule.setType(SnippetValidationRule.RuleType.CUSTOM);
            rule.setSeverity(SnippetValidationRule.Severity.ERROR);
            rule.setEnabled(false);
            rule.setPattern(Pattern.compile("test"));
            rule.setErrorMessage("Error");
            rule.setSuggestion("Suggestion");
            rule.setDocumentationUrl("http://example.com");

            assertEquals("test-id", rule.getId());
            assertEquals("Test Name", rule.getName());
            assertEquals("Test Description", rule.getDescription());
            assertEquals(SnippetValidationRule.RuleType.CUSTOM, rule.getType());
            assertEquals(SnippetValidationRule.Severity.ERROR, rule.getSeverity());
            assertFalse(rule.isEnabled());
            assertNotNull(rule.getPattern());
            assertEquals("Error", rule.getErrorMessage());
            assertEquals("Suggestion", rule.getSuggestion());
            assertEquals("http://example.com", rule.getDocumentationUrl());
        }

        @Test
        @DisplayName("toString returns meaningful string")
        void toStringReturnsMeaningfulString() {
            SnippetValidationRule rule = new SnippetValidationRule(
                    "Test Rule",
                    SnippetValidationRule.RuleType.SYNTAX,
                    SnippetValidationRule.Severity.ERROR,
                    "Description"
            );

            String str = rule.toString();
            assertTrue(str.contains("Test Rule"));
            assertTrue(str.contains("SYNTAX"));
            assertTrue(str.contains("ERROR"));
        }
    }
}
