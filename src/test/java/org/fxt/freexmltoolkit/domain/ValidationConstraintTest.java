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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationConstraint")
class ValidationConstraintTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("pattern() creates pattern constraint")
        void patternFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.pattern("[A-Z]+");

            assertEquals("pattern", constraint.constraintType());
            assertEquals("[A-Z]+", constraint.value());
            assertEquals("Regular expression pattern", constraint.description());
            assertNull(constraint.attributes());
        }

        @Test
        @DisplayName("enumeration() creates enumeration constraint")
        void enumerationFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.enumeration(List.of("A", "B", "C"));

            assertEquals("enumeration", constraint.constraintType());
            assertEquals("A,B,C", constraint.value());
            assertTrue(constraint.description().contains("3 items"));
        }

        @Test
        @DisplayName("minInclusive() creates min inclusive constraint")
        void minInclusiveFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.minInclusive("0");

            assertEquals("minInclusive", constraint.constraintType());
            assertEquals("0", constraint.value());
            assertTrue(constraint.description().contains("Minimum inclusive"));
        }

        @Test
        @DisplayName("maxInclusive() creates max inclusive constraint")
        void maxInclusiveFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.maxInclusive("100");

            assertEquals("maxInclusive", constraint.constraintType());
            assertEquals("100", constraint.value());
            assertTrue(constraint.description().contains("Maximum inclusive"));
        }

        @Test
        @DisplayName("minExclusive() creates min exclusive constraint")
        void minExclusiveFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.minExclusive("-1");

            assertEquals("minExclusive", constraint.constraintType());
            assertEquals("-1", constraint.value());
            assertTrue(constraint.description().contains("Minimum exclusive"));
        }

        @Test
        @DisplayName("maxExclusive() creates max exclusive constraint")
        void maxExclusiveFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.maxExclusive("101");

            assertEquals("maxExclusive", constraint.constraintType());
            assertEquals("101", constraint.value());
            assertTrue(constraint.description().contains("Maximum exclusive"));
        }

        @Test
        @DisplayName("length() creates length constraint")
        void lengthFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.length("10");

            assertEquals("length", constraint.constraintType());
            assertEquals("10", constraint.value());
            assertTrue(constraint.description().contains("Exact length"));
        }

        @Test
        @DisplayName("minLength() creates min length constraint")
        void minLengthFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.minLength("5");

            assertEquals("minLength", constraint.constraintType());
            assertEquals("5", constraint.value());
            assertTrue(constraint.description().contains("Minimum length"));
        }

        @Test
        @DisplayName("maxLength() creates max length constraint")
        void maxLengthFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.maxLength("50");

            assertEquals("maxLength", constraint.constraintType());
            assertEquals("50", constraint.value());
            assertTrue(constraint.description().contains("Maximum length"));
        }

        @Test
        @DisplayName("totalDigits() creates total digits constraint")
        void totalDigitsFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.totalDigits("8");

            assertEquals("totalDigits", constraint.constraintType());
            assertEquals("8", constraint.value());
            assertTrue(constraint.description().contains("Total digits"));
        }

        @Test
        @DisplayName("fractionDigits() creates fraction digits constraint")
        void fractionDigitsFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.fractionDigits("2");

            assertEquals("fractionDigits", constraint.constraintType());
            assertEquals("2", constraint.value());
            assertTrue(constraint.description().contains("Fraction digits"));
        }

        @Test
        @DisplayName("whitespace() creates whitespace constraint")
        void whitespaceFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.whitespace("collapse");

            assertEquals("whiteSpace", constraint.constraintType());
            assertEquals("collapse", constraint.value());
            assertTrue(constraint.description().contains("Whitespace action"));
        }

        @Test
        @DisplayName("custom() creates custom constraint")
        void customFactoryMethod() {
            ValidationConstraint constraint = ValidationConstraint.custom("myConstraint", "myValue", "My Description");

            assertEquals("myConstraint", constraint.constraintType());
            assertEquals("myValue", constraint.value());
            assertEquals("My Description", constraint.description());
        }
    }

    @Nested
    @DisplayName("Type Check Methods")
    class TypeCheckTests {

        @Test
        @DisplayName("isRangeConstraint() returns true for range constraints")
        void isRangeConstraintTrue() {
            assertTrue(ValidationConstraint.minInclusive("0").isRangeConstraint());
            assertTrue(ValidationConstraint.maxInclusive("100").isRangeConstraint());
            assertTrue(ValidationConstraint.minExclusive("-1").isRangeConstraint());
            assertTrue(ValidationConstraint.maxExclusive("101").isRangeConstraint());
        }

        @Test
        @DisplayName("isRangeConstraint() returns false for non-range constraints")
        void isRangeConstraintFalse() {
            assertFalse(ValidationConstraint.pattern("[A-Z]+").isRangeConstraint());
            assertFalse(ValidationConstraint.length("10").isRangeConstraint());
            assertFalse(ValidationConstraint.totalDigits("5").isRangeConstraint());
        }

        @Test
        @DisplayName("isLengthConstraint() returns true for length constraints")
        void isLengthConstraintTrue() {
            assertTrue(ValidationConstraint.length("10").isLengthConstraint());
            assertTrue(ValidationConstraint.minLength("5").isLengthConstraint());
            assertTrue(ValidationConstraint.maxLength("50").isLengthConstraint());
        }

        @Test
        @DisplayName("isLengthConstraint() returns false for non-length constraints")
        void isLengthConstraintFalse() {
            assertFalse(ValidationConstraint.pattern("[A-Z]+").isLengthConstraint());
            assertFalse(ValidationConstraint.minInclusive("0").isLengthConstraint());
            assertFalse(ValidationConstraint.totalDigits("5").isLengthConstraint());
        }

        @Test
        @DisplayName("isDecimalConstraint() returns true for decimal constraints")
        void isDecimalConstraintTrue() {
            assertTrue(ValidationConstraint.totalDigits("8").isDecimalConstraint());
            assertTrue(ValidationConstraint.fractionDigits("2").isDecimalConstraint());
        }

        @Test
        @DisplayName("isDecimalConstraint() returns false for non-decimal constraints")
        void isDecimalConstraintFalse() {
            assertFalse(ValidationConstraint.pattern("[0-9]+").isDecimalConstraint());
            assertFalse(ValidationConstraint.length("10").isDecimalConstraint());
            assertFalse(ValidationConstraint.minInclusive("0").isDecimalConstraint());
        }
    }

    @Nested
    @DisplayName("XSD Methods")
    class XsdMethodTests {

        @Test
        @DisplayName("getXsdFacetName() returns constraint type")
        void getXsdFacetName() {
            ValidationConstraint constraint = ValidationConstraint.pattern("[A-Z]+");
            assertEquals("pattern", constraint.getXsdFacetName());
        }

        @Test
        @DisplayName("toXsdFragment() returns valid XSD fragment")
        void toXsdFragment() {
            ValidationConstraint constraint = ValidationConstraint.minLength("5");
            String fragment = constraint.toXsdFragment();

            assertTrue(fragment.contains("<xs:restriction>"));
            assertTrue(fragment.contains("<xs:minLength"));
            assertTrue(fragment.contains("value=\"5\""));
            assertTrue(fragment.contains("</xs:restriction>"));
        }
    }

    @Nested
    @DisplayName("Record Methods")
    class RecordMethodTests {

        @Test
        @DisplayName("Record equality works correctly")
        void recordEquality() {
            ValidationConstraint c1 = ValidationConstraint.pattern("[A-Z]+");
            ValidationConstraint c2 = ValidationConstraint.pattern("[A-Z]+");
            ValidationConstraint c3 = ValidationConstraint.pattern("[a-z]+");

            assertEquals(c1, c2);
            assertNotEquals(c1, c3);
        }

        @Test
        @DisplayName("Record hashCode is consistent")
        void recordHashCode() {
            ValidationConstraint c1 = ValidationConstraint.pattern("[A-Z]+");
            ValidationConstraint c2 = ValidationConstraint.pattern("[A-Z]+");

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        @DisplayName("Direct constructor works")
        void directConstructor() {
            Map<String, String> attrs = Map.of("fixed", "true");
            ValidationConstraint constraint = new ValidationConstraint("pattern", "[0-9]+", "Digits only", attrs);

            assertEquals("pattern", constraint.constraintType());
            assertEquals("[0-9]+", constraint.value());
            assertEquals("Digits only", constraint.description());
            assertEquals(attrs, constraint.attributes());
        }
    }
}
