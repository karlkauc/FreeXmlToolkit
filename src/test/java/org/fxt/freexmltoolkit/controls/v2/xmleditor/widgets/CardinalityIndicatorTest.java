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
package org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.CardinalityIndicator.CardinalityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for CardinalityIndicator class.
 */
@DisplayName("CardinalityIndicator Tests")
class CardinalityIndicatorTest {

    @Nested
    @DisplayName("Cardinality Type Detection")
    class CardinalityTypeDetection {

        @Test
        @DisplayName("Should detect EXACTLY_ONE for [1]")
        void shouldDetectExactlyOne() {
            assertEquals(CardinalityType.EXACTLY_ONE,
                    CardinalityIndicator.getCardinalityType("1", "1"));
        }

        @Test
        @DisplayName("Should detect OPTIONAL for [0..1]")
        void shouldDetectOptional() {
            assertEquals(CardinalityType.OPTIONAL,
                    CardinalityIndicator.getCardinalityType("0", "1"));
        }

        @Test
        @DisplayName("Should detect REQUIRED_MULTI for [1..*]")
        void shouldDetectRequiredMulti() {
            assertEquals(CardinalityType.REQUIRED_MULTI,
                    CardinalityIndicator.getCardinalityType("1", "unbounded"));
        }

        @Test
        @DisplayName("Should detect OPTIONAL_MULTI for [0..*]")
        void shouldDetectOptionalMulti() {
            assertEquals(CardinalityType.OPTIONAL_MULTI,
                    CardinalityIndicator.getCardinalityType("0", "unbounded"));
        }

        @Test
        @DisplayName("Should detect BOUNDED for [2..5]")
        void shouldDetectBounded() {
            assertEquals(CardinalityType.BOUNDED,
                    CardinalityIndicator.getCardinalityType("2", "5"));
        }

        @Test
        @DisplayName("Should handle null values with defaults")
        void shouldHandleNullValues() {
            // Defaults are minOccurs=1, maxOccurs=1
            assertEquals(CardinalityType.EXACTLY_ONE,
                    CardinalityIndicator.getCardinalityType(null, null));
        }
    }

    @Nested
    @DisplayName("Required Detection")
    class RequiredDetection {

        @ParameterizedTest
        @CsvSource({
                "1, true",
                "2, true",
                "0, false",
                "'', true",    // Empty defaults to 1
                "null, true"   // Null defaults to 1 (treated as string "null" here)
        })
        @DisplayName("Should correctly detect required status")
        void shouldDetectRequired(String minOccurs, boolean expected) {
            if ("null".equals(minOccurs)) minOccurs = null;
            assertEquals(expected, CardinalityIndicator.isRequired(minOccurs));
        }
    }

    @Nested
    @DisplayName("Multiple Allowed Detection")
    class MultipleAllowedDetection {

        @ParameterizedTest
        @CsvSource({
                "unbounded, true",
                "UNBOUNDED, true",
                "2, true",
                "5, true",
                "1, false",
                "0, false",
                "'', false"
        })
        @DisplayName("Should correctly detect multiple allowed")
        void shouldDetectMultipleAllowed(String maxOccurs, boolean expected) {
            assertEquals(expected, CardinalityIndicator.allowsMultiple(maxOccurs));
        }

        @Test
        @DisplayName("Should return false for null maxOccurs")
        void shouldReturnFalseForNullMaxOccurs() {
            assertFalse(CardinalityIndicator.allowsMultiple(null));
        }
    }

    @Nested
    @DisplayName("Inline Text Generation")
    class InlineTextGeneration {

        @ParameterizedTest
        @CsvSource({
                "1, 1, '[1]'",
                "0, 1, '[0..1]'",
                "1, unbounded, '[1..*]'",
                "0, unbounded, '[0..*]'",
                "2, 5, '[2..5]'",
                "3, 3, '[3]'"
        })
        @DisplayName("Should generate correct inline text")
        void shouldGenerateCorrectInlineText(String minOccurs, String maxOccurs, String expected) {
            assertEquals(expected, CardinalityIndicator.createInlineText(minOccurs, maxOccurs));
        }
    }

    // Note: Tests for createRequiredIndicator(), createBadge(), createCompactIndicator(),
    // and createDotIndicator() are omitted because they create JavaFX components
    // which require the JavaFX platform to be initialized. These would need to be
    // tested using TestFX or a similar framework that initializes the JavaFX runtime.
}
