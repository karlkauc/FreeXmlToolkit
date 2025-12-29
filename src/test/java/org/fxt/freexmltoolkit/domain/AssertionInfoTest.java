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

@DisplayName("AssertionInfo")
class AssertionInfoTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Full constructor sets all values")
        void fullConstructorSetsAllValues() {
            AssertionInfo info = new AssertionInfo(
                    "$value > 0",
                    "/root/element/xs:assert",
                    "Value must be positive",
                    "http://example.com"
            );

            assertEquals("$value > 0", info.test());
            assertEquals("/root/element/xs:assert", info.xpath());
            assertEquals("Value must be positive", info.documentation());
            assertEquals("http://example.com", info.xpathDefaultNamespace());
        }

        @Test
        @DisplayName("Two-argument constructor sets defaults")
        void twoArgConstructorSetsDefaults() {
            AssertionInfo info = new AssertionInfo(
                    "count(child) > 0",
                    "/root/parent"
            );

            assertEquals("count(child) > 0", info.test());
            assertEquals("/root/parent", info.xpath());
            assertNull(info.documentation());
            assertNull(info.xpathDefaultNamespace());
        }
    }

    @Nested
    @DisplayName("hasDocumentation")
    class HasDocumentationTests {

        @Test
        @DisplayName("Returns true when documentation exists")
        void returnsTrueWhenDocumentationExists() {
            AssertionInfo info = new AssertionInfo(
                    "test", "/xpath", "Some documentation", null
            );

            assertTrue(info.hasDocumentation());
        }

        @Test
        @DisplayName("Returns false when documentation is null")
        void returnsFalseWhenDocumentationIsNull() {
            AssertionInfo info = new AssertionInfo("test", "/xpath");

            assertFalse(info.hasDocumentation());
        }

        @Test
        @DisplayName("Returns false when documentation is empty")
        void returnsFalseWhenDocumentationIsEmpty() {
            AssertionInfo info = new AssertionInfo(
                    "test", "/xpath", "", null
            );

            assertFalse(info.hasDocumentation());
        }
    }

    @Nested
    @DisplayName("hasDefaultNamespace")
    class HasDefaultNamespaceTests {

        @Test
        @DisplayName("Returns true when default namespace exists")
        void returnsTrueWhenDefaultNamespaceExists() {
            AssertionInfo info = new AssertionInfo(
                    "test", "/xpath", null, "http://example.com"
            );

            assertTrue(info.hasDefaultNamespace());
        }

        @Test
        @DisplayName("Returns false when default namespace is null")
        void returnsFalseWhenDefaultNamespaceIsNull() {
            AssertionInfo info = new AssertionInfo("test", "/xpath");

            assertFalse(info.hasDefaultNamespace());
        }

        @Test
        @DisplayName("Returns false when default namespace is empty")
        void returnsFalseWhenDefaultNamespaceIsEmpty() {
            AssertionInfo info = new AssertionInfo(
                    "test", "/xpath", null, ""
            );

            assertFalse(info.hasDefaultNamespace());
        }
    }

    @Nested
    @DisplayName("getDescription")
    class GetDescriptionTests {

        @Test
        @DisplayName("Returns documentation when available")
        void returnsDocumentationWhenAvailable() {
            AssertionInfo info = new AssertionInfo(
                    "$age >= 18", "/person/xs:assert", "Age must be at least 18", null
            );

            assertEquals("Age must be at least 18", info.getDescription());
        }

        @Test
        @DisplayName("Returns test prefixed with Assertion when no documentation")
        void returnsTestPrefixedWhenNoDocumentation() {
            AssertionInfo info = new AssertionInfo("$age >= 18", "/person");

            assertEquals("Assertion: $age >= 18", info.getDescription());
        }
    }

    @Nested
    @DisplayName("getShortTest")
    class GetShortTestTests {

        @Test
        @DisplayName("Returns full test when shorter than max length")
        void returnsFullTestWhenShorterThanMaxLength() {
            AssertionInfo info = new AssertionInfo("$value > 0", "/xpath");

            assertEquals("$value > 0", info.getShortTest(50));
        }

        @Test
        @DisplayName("Truncates test with ellipsis when longer than max length")
        void truncatesTestWhenLongerThanMaxLength() {
            AssertionInfo info = new AssertionInfo(
                    "count(descendant::item[category='A']) > count(descendant::item[category='B'])",
                    "/xpath"
            );

            String shortTest = info.getShortTest(20);
            assertEquals(20, shortTest.length());
            assertTrue(shortTest.endsWith("..."));
        }

        @Test
        @DisplayName("Returns empty string when test is null")
        void returnsEmptyStringWhenTestIsNull() {
            AssertionInfo info = new AssertionInfo(null, "/xpath");

            assertEquals("", info.getShortTest(50));
        }

        @Test
        @DisplayName("Returns exact length when at boundary")
        void returnsExactLengthWhenAtBoundary() {
            String test = "exactly15chars!";
            AssertionInfo info = new AssertionInfo(test, "/xpath");

            assertEquals(test, info.getShortTest(15));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("Contains test and xpath")
        void containsTestAndXpath() {
            AssertionInfo info = new AssertionInfo("$value > 0", "/root/element");

            String str = info.toString();
            assertTrue(str.contains("$value > 0"));
            assertTrue(str.contains("/root/element"));
        }

        @Test
        @DisplayName("Truncates long test in toString")
        void truncatesLongTestInToString() {
            AssertionInfo info = new AssertionInfo(
                    "count(child[status='active' and @type='primary']) > count(child[status='inactive'])",
                    "/xpath"
            );

            String str = info.toString();
            assertTrue(str.contains("..."));
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equal records are equal")
        void equalRecordsAreEqual() {
            AssertionInfo info1 = new AssertionInfo("test", "/xpath", "doc", "ns");
            AssertionInfo info2 = new AssertionInfo("test", "/xpath", "doc", "ns");

            assertEquals(info1, info2);
            assertEquals(info1.hashCode(), info2.hashCode());
        }

        @Test
        @DisplayName("Different test values are not equal")
        void differentTestValuesNotEqual() {
            AssertionInfo info1 = new AssertionInfo("test1", "/xpath");
            AssertionInfo info2 = new AssertionInfo("test2", "/xpath");

            assertNotEquals(info1, info2);
        }

        @Test
        @DisplayName("Different xpath values are not equal")
        void differentXpathValuesNotEqual() {
            AssertionInfo info1 = new AssertionInfo("test", "/xpath1");
            AssertionInfo info2 = new AssertionInfo("test", "/xpath2");

            assertNotEquals(info1, info2);
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("Age validation assertion")
        void ageValidationAssertion() {
            AssertionInfo info = new AssertionInfo(
                    "@age >= 0 and @age <= 150",
                    "/person/xs:assert[1]",
                    "Age must be between 0 and 150 years",
                    null
            );

            assertTrue(info.hasDocumentation());
            assertEquals("Age must be between 0 and 150 years", info.getDescription());
        }

        @Test
        @DisplayName("Price calculation assertion")
        void priceCalculationAssertion() {
            AssertionInfo info = new AssertionInfo(
                    "@total = sum(item/@price * item/@quantity)",
                    "/order/xs:assert",
                    "Total must equal sum of line items",
                    "http://example.com/order"
            );

            assertTrue(info.hasDefaultNamespace());
            assertTrue(info.hasDocumentation());
        }

        @Test
        @DisplayName("Complex XPath assertion without documentation")
        void complexXpathAssertionWithoutDocumentation() {
            String complexXpath = "every $p in product satisfies $p/@price > 0 and $p/@quantity >= 1";
            AssertionInfo info = new AssertionInfo(complexXpath, "/inventory");

            assertFalse(info.hasDocumentation());
            assertTrue(info.getDescription().startsWith("Assertion:"));
        }
    }
}
