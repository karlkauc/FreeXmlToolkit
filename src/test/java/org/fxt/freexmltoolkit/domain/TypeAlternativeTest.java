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

@DisplayName("TypeAlternative")
class TypeAlternativeTest {

    private TypeAlternative alternative;

    @BeforeEach
    void setUp() {
        alternative = new TypeAlternative();
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor creates empty alternative")
        void defaultConstructorCreatesEmptyAlternative() {
            TypeAlternative empty = new TypeAlternative();

            assertNull(empty.getTest());
            assertNull(empty.getType());
            assertNull(empty.getDocumentation());
            assertNull(empty.getXpathDefaultNamespace());
        }

        @Test
        @DisplayName("Parameterized constructor sets test and type")
        void parameterizedConstructorSetsTestAndType() {
            TypeAlternative withParams = new TypeAlternative(
                    "@country = 'US'",
                    "USAddressType"
            );

            assertEquals("@country = 'US'", withParams.getTest());
            assertEquals("USAddressType", withParams.getType());
            assertNull(withParams.getDocumentation());
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("Set and get test")
        void setAndGetTest() {
            alternative.setTest("@type = 'premium'");
            assertEquals("@type = 'premium'", alternative.getTest());
        }

        @Test
        @DisplayName("Set and get type")
        void setAndGetType() {
            alternative.setType("PremiumCustomerType");
            assertEquals("PremiumCustomerType", alternative.getType());
        }

        @Test
        @DisplayName("Set and get documentation")
        void setAndGetDocumentation() {
            alternative.setDocumentation("Type for premium customers");
            assertEquals("Type for premium customers", alternative.getDocumentation());
        }

        @Test
        @DisplayName("Set and get xpath default namespace")
        void setAndGetXpathDefaultNamespace() {
            alternative.setXpathDefaultNamespace("http://example.com/ns");
            assertEquals("http://example.com/ns", alternative.getXpathDefaultNamespace());
        }
    }

    @Nested
    @DisplayName("isDefault")
    class IsDefaultTests {

        @Test
        @DisplayName("Returns true when test is null")
        void returnsTrueWhenTestIsNull() {
            alternative.setTest(null);
            assertTrue(alternative.isDefault());
        }

        @Test
        @DisplayName("Returns true when test is empty")
        void returnsTrueWhenTestIsEmpty() {
            alternative.setTest("");
            assertTrue(alternative.isDefault());
        }

        @Test
        @DisplayName("Returns false when test has value")
        void returnsFalseWhenTestHasValue() {
            alternative.setTest("@country = 'DE'");
            assertFalse(alternative.isDefault());
        }
    }

    @Nested
    @DisplayName("getDisplayCondition")
    class GetDisplayConditionTests {

        @Test
        @DisplayName("Returns 'Default (otherwise)' for default alternative")
        void returnsDefaultOtherwiseForDefaultAlternative() {
            alternative.setTest(null);
            assertEquals("Default (otherwise)", alternative.getDisplayCondition());
        }

        @Test
        @DisplayName("Returns 'When: <test>' for conditional alternative")
        void returnsWhenTestForConditionalAlternative() {
            alternative.setTest("@lang = 'en'");
            assertEquals("When: @lang = 'en'", alternative.getDisplayCondition());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains test and type for conditional")
        void toStringContainsTestAndTypeForConditional() {
            alternative.setTest("@region = 'EU'");
            alternative.setType("EuropeanType");

            String str = alternative.toString();
            assertTrue(str.contains("test='@region = 'EU'"));
            assertTrue(str.contains("type='EuropeanType'"));
        }

        @Test
        @DisplayName("toString indicates default when no test")
        void toStringIndicatesDefaultWhenNoTest() {
            alternative.setTest(null);
            alternative.setType("DefaultType");

            String str = alternative.toString();
            assertTrue(str.contains("default"));
            assertTrue(str.contains("type='DefaultType'"));
        }
    }

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Can serialize and deserialize")
        void canSerializeAndDeserialize() throws IOException, ClassNotFoundException {
            alternative.setTest("@version >= 2");
            alternative.setType("Version2Type");
            alternative.setDocumentation("Type for version 2 and above");
            alternative.setXpathDefaultNamespace("http://example.com/v2");

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(alternative);
            oos.close();

            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            TypeAlternative deserialized = (TypeAlternative) ois.readObject();
            ois.close();

            assertEquals(alternative.getTest(), deserialized.getTest());
            assertEquals(alternative.getType(), deserialized.getType());
            assertEquals(alternative.getDocumentation(), deserialized.getDocumentation());
            assertEquals(alternative.getXpathDefaultNamespace(), deserialized.getXpathDefaultNamespace());
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("Address type based on country")
        void addressTypeBasedOnCountry() {
            TypeAlternative usAddress = new TypeAlternative("@country = 'US'", "USAddressType");
            usAddress.setDocumentation("Address format for United States");

            assertFalse(usAddress.isDefault());
            assertEquals("When: @country = 'US'", usAddress.getDisplayCondition());
        }

        @Test
        @DisplayName("Default type alternative")
        void defaultTypeAlternative() {
            TypeAlternative defaultType = new TypeAlternative(null, "GenericAddressType");
            defaultType.setDocumentation("Default address format for all other countries");

            assertTrue(defaultType.isDefault());
            assertEquals("Default (otherwise)", defaultType.getDisplayCondition());
        }

        @Test
        @DisplayName("Complex XPath condition")
        void complexXPathCondition() {
            TypeAlternative complex = new TypeAlternative(
                    "xs:date(@startDate) < xs:date('2020-01-01')",
                    "LegacyOrderType"
            );

            assertFalse(complex.isDefault());
            assertTrue(complex.getTest().contains("xs:date"));
        }

        @Test
        @DisplayName("Alternative with namespace")
        void alternativeWithNamespace() {
            TypeAlternative nsAlternative = new TypeAlternative("ord:status = 'active'", "ActiveOrderType");
            nsAlternative.setXpathDefaultNamespace("http://example.com/order");

            assertEquals("http://example.com/order", nsAlternative.getXpathDefaultNamespace());
        }
    }
}
