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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XsdAssertion")
class XsdAssertionTest {

    private XsdAssertion assertion;

    @BeforeEach
    void setUp() {
        assertion = new XsdAssertion();
    }

    @Nested
    @DisplayName("Type Enum")
    class TypeEnumTests {

        @Test
        @DisplayName("All two types are defined")
        void allTypesAreDefined() {
            assertEquals(2, XsdAssertion.Type.values().length);
        }

        @Test
        @DisplayName("ASSERT type exists")
        void assertTypeExists() {
            assertEquals(XsdAssertion.Type.ASSERT, XsdAssertion.Type.valueOf("ASSERT"));
        }

        @Test
        @DisplayName("ASSERTION type exists")
        void assertionTypeExists() {
            assertEquals(XsdAssertion.Type.ASSERTION, XsdAssertion.Type.valueOf("ASSERTION"));
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor creates empty assertion")
        void defaultConstructorCreatesEmptyAssertion() {
            XsdAssertion empty = new XsdAssertion();

            assertNull(empty.getType());
            assertNull(empty.getTest());
            assertNull(empty.getDocumentation());
            assertNull(empty.getXpathDefaultNamespace());
        }

        @Test
        @DisplayName("Parameterized constructor sets type and test")
        void parameterizedConstructorSetsTypeAndTest() {
            XsdAssertion withParams = new XsdAssertion(
                    XsdAssertion.Type.ASSERT,
                    "$value > 0"
            );

            assertEquals(XsdAssertion.Type.ASSERT, withParams.getType());
            assertEquals("$value > 0", withParams.getTest());
            assertNull(withParams.getDocumentation());
            assertNull(withParams.getXpathDefaultNamespace());
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("Set and get type")
        void setAndGetType() {
            assertion.setType(XsdAssertion.Type.ASSERT);
            assertEquals(XsdAssertion.Type.ASSERT, assertion.getType());

            assertion.setType(XsdAssertion.Type.ASSERTION);
            assertEquals(XsdAssertion.Type.ASSERTION, assertion.getType());
        }

        @Test
        @DisplayName("Set and get test")
        void setAndGetTest() {
            assertion.setTest("@age >= 18");
            assertEquals("@age >= 18", assertion.getTest());
        }

        @Test
        @DisplayName("Set and get documentation")
        void setAndGetDocumentation() {
            assertion.setDocumentation("Age must be at least 18 years");
            assertEquals("Age must be at least 18 years", assertion.getDocumentation());
        }

        @Test
        @DisplayName("Set and get xpath default namespace")
        void setAndGetXpathDefaultNamespace() {
            assertion.setXpathDefaultNamespace("http://example.com");
            assertEquals("http://example.com", assertion.getXpathDefaultNamespace());
        }
    }

    @Nested
    @DisplayName("getTypeDisplayName")
    class GetTypeDisplayNameTests {

        @Test
        @DisplayName("ASSERT returns 'Assert'")
        void assertReturnsAssert() {
            assertion.setType(XsdAssertion.Type.ASSERT);
            assertEquals("Assert", assertion.getTypeDisplayName());
        }

        @Test
        @DisplayName("ASSERTION returns 'Assertion'")
        void assertionReturnsAssertion() {
            assertion.setType(XsdAssertion.Type.ASSERTION);
            assertEquals("Assertion", assertion.getTypeDisplayName());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains type and test")
        void toStringContainsTypeAndTest() {
            assertion.setType(XsdAssertion.Type.ASSERT);
            assertion.setTest("$value > 0");

            String str = assertion.toString();
            assertTrue(str.contains("ASSERT"));
            assertTrue(str.contains("$value > 0"));
        }

        @Test
        @DisplayName("toString includes xpath namespace when present")
        void toStringIncludesXpathNamespaceWhenPresent() {
            assertion.setType(XsdAssertion.Type.ASSERT);
            assertion.setTest("test");
            assertion.setXpathDefaultNamespace("http://example.com");

            String str = assertion.toString();
            assertTrue(str.contains("xpathDefaultNamespace"));
            assertTrue(str.contains("http://example.com"));
        }

        @Test
        @DisplayName("toString excludes xpath namespace when null")
        void toStringExcludesXpathNamespaceWhenNull() {
            assertion.setType(XsdAssertion.Type.ASSERT);
            assertion.setTest("test");

            String str = assertion.toString();
            assertFalse(str.contains("xpathDefaultNamespace"));
        }
    }

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Can serialize and deserialize")
        void canSerializeAndDeserialize() throws IOException, ClassNotFoundException {
            assertion.setType(XsdAssertion.Type.ASSERT);
            assertion.setTest("@price > 0 and @quantity >= 1");
            assertion.setDocumentation("Price must be positive and quantity at least 1");
            assertion.setXpathDefaultNamespace("http://example.com/order");

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(assertion);
            oos.close();

            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            XsdAssertion deserialized = (XsdAssertion) ois.readObject();
            ois.close();

            assertEquals(assertion.getType(), deserialized.getType());
            assertEquals(assertion.getTest(), deserialized.getTest());
            assertEquals(assertion.getDocumentation(), deserialized.getDocumentation());
            assertEquals(assertion.getXpathDefaultNamespace(), deserialized.getXpathDefaultNamespace());
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("Complex type assertion for age validation")
        void complexTypeAssertionForAgeValidation() {
            XsdAssertion ageAssertion = new XsdAssertion(
                    XsdAssertion.Type.ASSERT,
                    "@age >= 0 and @age <= 150"
            );
            ageAssertion.setDocumentation("Age must be between 0 and 150 years");

            assertEquals(XsdAssertion.Type.ASSERT, ageAssertion.getType());
            assertEquals("Assert", ageAssertion.getTypeDisplayName());
            assertTrue(ageAssertion.getTest().contains("@age"));
        }

        @Test
        @DisplayName("Simple type assertion for pattern")
        void simpleTypeAssertionForPattern() {
            XsdAssertion patternAssertion = new XsdAssertion(
                    XsdAssertion.Type.ASSERTION,
                    "string-length(.) = 10"
            );
            patternAssertion.setDocumentation("Value must be exactly 10 characters");

            assertEquals(XsdAssertion.Type.ASSERTION, patternAssertion.getType());
            assertEquals("Assertion", patternAssertion.getTypeDisplayName());
        }

        @Test
        @DisplayName("Assertion with namespace context")
        void assertionWithNamespaceContext() {
            XsdAssertion nsAssertion = new XsdAssertion(
                    XsdAssertion.Type.ASSERT,
                    "sum(item/price) = total"
            );
            nsAssertion.setXpathDefaultNamespace("http://example.com/order");
            nsAssertion.setDocumentation("Sum of item prices must equal total");

            assertEquals("http://example.com/order", nsAssertion.getXpathDefaultNamespace());
            assertTrue(nsAssertion.toString().contains("xpathDefaultNamespace"));
        }

        @Test
        @DisplayName("Multiple assertions on same element")
        void multipleAssertionsOnSameElement() {
            XsdAssertion assertion1 = new XsdAssertion(
                    XsdAssertion.Type.ASSERT,
                    "@start-date <= @end-date"
            );
            assertion1.setDocumentation("Start date must be before or equal to end date");

            XsdAssertion assertion2 = new XsdAssertion(
                    XsdAssertion.Type.ASSERT,
                    "xs:yearMonthDuration(@end-date - @start-date) <= xs:yearMonthDuration('P1Y')"
            );
            assertion2.setDocumentation("Period must not exceed one year");

            assertNotEquals(assertion1.getTest(), assertion2.getTest());
            assertEquals(assertion1.getType(), assertion2.getType());
        }
    }
}
