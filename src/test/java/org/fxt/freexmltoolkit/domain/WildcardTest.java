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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Wildcard")
class WildcardTest {

    private Wildcard wildcard;

    @BeforeEach
    void setUp() {
        wildcard = new Wildcard();
    }

    @Nested
    @DisplayName("Type Enum")
    class TypeEnumTests {

        @Test
        @DisplayName("All two types are defined")
        void allTypesAreDefined() {
            assertEquals(2, Wildcard.Type.values().length);
        }

        @Test
        @DisplayName("ANY type exists")
        void anyTypeExists() {
            assertEquals(Wildcard.Type.ANY, Wildcard.Type.valueOf("ANY"));
        }

        @Test
        @DisplayName("ANY_ATTRIBUTE type exists")
        void anyAttributeTypeExists() {
            assertEquals(Wildcard.Type.ANY_ATTRIBUTE, Wildcard.Type.valueOf("ANY_ATTRIBUTE"));
        }

        @ParameterizedTest
        @EnumSource(Wildcard.Type.class)
        @DisplayName("valueOf works for all types")
        void valueOfWorksForAllTypes(Wildcard.Type type) {
            assertEquals(type, Wildcard.Type.valueOf(type.name()));
        }
    }

    @Nested
    @DisplayName("ProcessContents Enum")
    class ProcessContentsEnumTests {

        @Test
        @DisplayName("All three process contents modes are defined")
        void allProcessContentsAreDefined() {
            assertEquals(3, Wildcard.ProcessContents.values().length);
        }

        @Test
        @DisplayName("STRICT mode exists")
        void strictModeExists() {
            assertEquals(Wildcard.ProcessContents.STRICT, Wildcard.ProcessContents.valueOf("STRICT"));
        }

        @Test
        @DisplayName("LAX mode exists")
        void laxModeExists() {
            assertEquals(Wildcard.ProcessContents.LAX, Wildcard.ProcessContents.valueOf("LAX"));
        }

        @Test
        @DisplayName("SKIP mode exists")
        void skipModeExists() {
            assertEquals(Wildcard.ProcessContents.SKIP, Wildcard.ProcessContents.valueOf("SKIP"));
        }

        @ParameterizedTest
        @EnumSource(Wildcard.ProcessContents.class)
        @DisplayName("valueOf works for all process contents modes")
        void valueOfWorksForAllProcessContents(Wildcard.ProcessContents mode) {
            assertEquals(mode, Wildcard.ProcessContents.valueOf(mode.name()));
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor creates empty wildcard")
        void defaultConstructorCreatesEmptyWildcard() {
            Wildcard empty = new Wildcard();

            assertNull(empty.getType());
            assertNull(empty.getNamespace());
            assertNull(empty.getProcessContents());
            assertNull(empty.getMinOccurs());
            assertNull(empty.getMaxOccurs());
        }

        @Test
        @DisplayName("Parameterized constructor sets type and namespace")
        void parameterizedConstructorSetsTypeAndNamespace() {
            Wildcard withParams = new Wildcard(Wildcard.Type.ANY, "##other");

            assertEquals(Wildcard.Type.ANY, withParams.getType());
            assertEquals("##other", withParams.getNamespace());
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("Set and get type")
        void setAndGetType() {
            wildcard.setType(Wildcard.Type.ANY);
            assertEquals(Wildcard.Type.ANY, wildcard.getType());

            wildcard.setType(Wildcard.Type.ANY_ATTRIBUTE);
            assertEquals(Wildcard.Type.ANY_ATTRIBUTE, wildcard.getType());
        }

        @Test
        @DisplayName("Set and get namespace")
        void setAndGetNamespace() {
            wildcard.setNamespace("##any");
            assertEquals("##any", wildcard.getNamespace());
        }

        @Test
        @DisplayName("Set and get processContents")
        void setAndGetProcessContents() {
            wildcard.setProcessContents(Wildcard.ProcessContents.LAX);
            assertEquals(Wildcard.ProcessContents.LAX, wildcard.getProcessContents());
        }

        @Test
        @DisplayName("Set and get minOccurs")
        void setAndGetMinOccurs() {
            wildcard.setMinOccurs("0");
            assertEquals("0", wildcard.getMinOccurs());
        }

        @Test
        @DisplayName("Set and get maxOccurs")
        void setAndGetMaxOccurs() {
            wildcard.setMaxOccurs("unbounded");
            assertEquals("unbounded", wildcard.getMaxOccurs());
        }

        @Test
        @DisplayName("Set and get documentation")
        void setAndGetDocumentation() {
            wildcard.setDocumentation("Allows any element from other namespaces");
            assertEquals("Allows any element from other namespaces", wildcard.getDocumentation());
        }
    }

    @Nested
    @DisplayName("getTypeDisplayName")
    class GetTypeDisplayNameTests {

        @Test
        @DisplayName("ANY returns 'Any Element'")
        void anyReturnsAnyElement() {
            wildcard.setType(Wildcard.Type.ANY);
            assertEquals("Any Element", wildcard.getTypeDisplayName());
        }

        @Test
        @DisplayName("ANY_ATTRIBUTE returns 'Any Attribute'")
        void anyAttributeReturnsAnyAttribute() {
            wildcard.setType(Wildcard.Type.ANY_ATTRIBUTE);
            assertEquals("Any Attribute", wildcard.getTypeDisplayName());
        }
    }

    @Nested
    @DisplayName("getNamespaceDisplayName")
    class GetNamespaceDisplayNameTests {

        @Test
        @DisplayName("Null namespace returns ##any")
        void nullNamespaceReturnsAny() {
            wildcard.setNamespace(null);
            assertEquals("##any", wildcard.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("Empty namespace returns ##any")
        void emptyNamespaceReturnsAny() {
            wildcard.setNamespace("");
            assertEquals("##any", wildcard.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("##any returns Any namespace")
        void anyReturnsAnyNamespace() {
            wildcard.setNamespace("##any");
            assertEquals("Any namespace", wildcard.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("##other returns correct display")
        void otherReturnsCorrectDisplay() {
            wildcard.setNamespace("##other");
            assertEquals("Any namespace except target namespace", wildcard.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("##local returns correct display")
        void localReturnsCorrectDisplay() {
            wildcard.setNamespace("##local");
            assertEquals("No namespace", wildcard.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("##targetNamespace returns correct display")
        void targetNamespaceReturnsCorrectDisplay() {
            wildcard.setNamespace("##targetNamespace");
            assertEquals("Target namespace only", wildcard.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("Specific URI returns as is")
        void specificUriReturnsAsIs() {
            wildcard.setNamespace("http://example.com/extensions");
            assertEquals("http://example.com/extensions", wildcard.getNamespaceDisplayName());
        }
    }

    @Nested
    @DisplayName("getCardinality")
    class GetCardinalityTests {

        @Test
        @DisplayName("Returns null for ANY_ATTRIBUTE")
        void returnsNullForAnyAttribute() {
            wildcard.setType(Wildcard.Type.ANY_ATTRIBUTE);
            assertNull(wildcard.getCardinality());
        }

        @Test
        @DisplayName("Returns [1] for default cardinality")
        void returnsDefaultCardinality() {
            wildcard.setType(Wildcard.Type.ANY);
            assertEquals("[1]", wildcard.getCardinality());
        }

        @Test
        @DisplayName("Returns [0..1] for optional")
        void returnsOptionalCardinality() {
            wildcard.setType(Wildcard.Type.ANY);
            wildcard.setMinOccurs("0");
            wildcard.setMaxOccurs("1");
            assertEquals("[0..1]", wildcard.getCardinality());
        }

        @Test
        @DisplayName("Returns [0..*] for unbounded")
        void returnsUnboundedCardinality() {
            wildcard.setType(Wildcard.Type.ANY);
            wildcard.setMinOccurs("0");
            wildcard.setMaxOccurs("unbounded");
            assertEquals("[0..*]", wildcard.getCardinality());
        }

        @Test
        @DisplayName("Returns [1..*] for required unbounded")
        void returnsRequiredUnboundedCardinality() {
            wildcard.setType(Wildcard.Type.ANY);
            wildcard.setMinOccurs("1");
            wildcard.setMaxOccurs("unbounded");
            assertEquals("[1..*]", wildcard.getCardinality());
        }

        @Test
        @DisplayName("Returns [5] for exact count")
        void returnsExactCountCardinality() {
            wildcard.setType(Wildcard.Type.ANY);
            wildcard.setMinOccurs("5");
            wildcard.setMaxOccurs("5");
            assertEquals("[5]", wildcard.getCardinality());
        }

        @Test
        @DisplayName("Returns [2..5] for range")
        void returnsRangeCardinality() {
            wildcard.setType(Wildcard.Type.ANY);
            wildcard.setMinOccurs("2");
            wildcard.setMaxOccurs("5");
            assertEquals("[2..5]", wildcard.getCardinality());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains type and namespace")
        void toStringContainsTypeAndNamespace() {
            wildcard.setType(Wildcard.Type.ANY);
            wildcard.setNamespace("##other");
            wildcard.setProcessContents(Wildcard.ProcessContents.LAX);

            String str = wildcard.toString();
            assertTrue(str.contains("type=ANY"));
            assertTrue(str.contains("##other"));
            assertTrue(str.contains("LAX"));
        }

        @Test
        @DisplayName("toString includes occurrences when set")
        void toStringIncludesOccurrences() {
            wildcard.setType(Wildcard.Type.ANY);
            wildcard.setMinOccurs("0");
            wildcard.setMaxOccurs("unbounded");

            String str = wildcard.toString();
            assertTrue(str.contains("minOccurs='0'"));
            assertTrue(str.contains("maxOccurs='unbounded'"));
        }
    }

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Can serialize and deserialize")
        void canSerializeAndDeserialize() throws IOException, ClassNotFoundException {
            wildcard.setType(Wildcard.Type.ANY);
            wildcard.setNamespace("##other");
            wildcard.setProcessContents(Wildcard.ProcessContents.LAX);
            wildcard.setMinOccurs("0");
            wildcard.setMaxOccurs("unbounded");
            wildcard.setDocumentation("Extension point");

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(wildcard);
            oos.close();

            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            Wildcard deserialized = (Wildcard) ois.readObject();
            ois.close();

            assertEquals(wildcard.getType(), deserialized.getType());
            assertEquals(wildcard.getNamespace(), deserialized.getNamespace());
            assertEquals(wildcard.getProcessContents(), deserialized.getProcessContents());
            assertEquals(wildcard.getMinOccurs(), deserialized.getMinOccurs());
            assertEquals(wildcard.getMaxOccurs(), deserialized.getMaxOccurs());
            assertEquals(wildcard.getDocumentation(), deserialized.getDocumentation());
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("xs:any for extension elements")
        void xsAnyForExtensionElements() {
            Wildcard any = new Wildcard(Wildcard.Type.ANY, "##other");
            any.setProcessContents(Wildcard.ProcessContents.LAX);
            any.setMinOccurs("0");
            any.setMaxOccurs("unbounded");
            any.setDocumentation("Extension point for third-party elements");

            assertEquals("Any Element", any.getTypeDisplayName());
            assertEquals("[0..*]", any.getCardinality());
        }

        @Test
        @DisplayName("xs:anyAttribute for extension attributes")
        void xsAnyAttributeForExtensionAttributes() {
            Wildcard anyAttr = new Wildcard(Wildcard.Type.ANY_ATTRIBUTE, "##any");
            anyAttr.setProcessContents(Wildcard.ProcessContents.SKIP);

            assertEquals("Any Attribute", anyAttr.getTypeDisplayName());
            assertNull(anyAttr.getCardinality()); // Attributes don't have cardinality
        }

        @Test
        @DisplayName("Strict validation wildcard")
        void strictValidationWildcard() {
            Wildcard strict = new Wildcard(Wildcard.Type.ANY, "http://www.w3.org/1999/xhtml");
            strict.setProcessContents(Wildcard.ProcessContents.STRICT);
            strict.setMinOccurs("1");
            strict.setMaxOccurs("1");

            assertEquals(Wildcard.ProcessContents.STRICT, strict.getProcessContents());
            assertEquals("[1]", strict.getCardinality());
        }
    }
}
