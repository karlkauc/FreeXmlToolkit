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

@DisplayName("XsdDocInfo")
class XsdDocInfoTest {

    private XsdDocInfo docInfo;

    @BeforeEach
    void setUp() {
        docInfo = new XsdDocInfo();
    }

    @Nested
    @DisplayName("Default State")
    class DefaultStateTests {

        @Test
        @DisplayName("New instance has null since")
        void newInstanceHasNullSince() {
            assertNull(docInfo.getSince());
        }

        @Test
        @DisplayName("New instance has empty see list")
        void newInstanceHasEmptySeeList() {
            assertNotNull(docInfo.getSee());
            assertTrue(docInfo.getSee().isEmpty());
        }

        @Test
        @DisplayName("New instance has null deprecated")
        void newInstanceHasNullDeprecated() {
            assertNull(docInfo.getDeprecated());
        }

        @Test
        @DisplayName("hasData returns false for empty instance")
        void hasDataReturnsFalseForEmptyInstance() {
            assertFalse(docInfo.hasData());
        }
    }

    @Nested
    @DisplayName("Setters and Getters")
    class SettersGettersTests {

        @Test
        @DisplayName("Set and get since")
        void setAndGetSince() {
            docInfo.setSince("1.0");
            assertEquals("1.0", docInfo.getSince());
        }

        @Test
        @DisplayName("Set and get deprecated")
        void setAndGetDeprecated() {
            docInfo.setDeprecated("Use newElement instead");
            assertEquals("Use newElement instead", docInfo.getDeprecated());
        }

        @Test
        @DisplayName("Add to see list")
        void addToSeeList() {
            docInfo.addSee("RelatedElement");
            docInfo.addSee("AnotherElement");

            assertEquals(2, docInfo.getSee().size());
            assertTrue(docInfo.getSee().contains("RelatedElement"));
            assertTrue(docInfo.getSee().contains("AnotherElement"));
        }
    }

    @Nested
    @DisplayName("hasData")
    class HasDataTests {

        @Test
        @DisplayName("Returns true when since is set")
        void returnsTrueWhenSinceIsSet() {
            docInfo.setSince("2.0");
            assertTrue(docInfo.hasData());
        }

        @Test
        @DisplayName("Returns true when deprecated is set")
        void returnsTrueWhenDeprecatedIsSet() {
            docInfo.setDeprecated("Deprecated in 3.0");
            assertTrue(docInfo.hasData());
        }

        @Test
        @DisplayName("Returns true when see list is not empty")
        void returnsTrueWhenSeeListIsNotEmpty() {
            docInfo.addSee("SeeAlso");
            assertTrue(docInfo.hasData());
        }

        @Test
        @DisplayName("Returns true when multiple fields are set")
        void returnsTrueWhenMultipleFieldsAreSet() {
            docInfo.setSince("1.0");
            docInfo.setDeprecated("Use v2 API");
            docInfo.addSee("NewElement");

            assertTrue(docInfo.hasData());
        }
    }

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Can serialize and deserialize")
        void canSerializeAndDeserialize() throws IOException, ClassNotFoundException {
            docInfo.setSince("1.5");
            docInfo.setDeprecated("Will be removed in 3.0");
            docInfo.addSee("AlternativeElement");
            docInfo.addSee("RelatedType");

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(docInfo);
            oos.close();

            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            XsdDocInfo deserialized = (XsdDocInfo) ois.readObject();
            ois.close();

            assertEquals(docInfo.getSince(), deserialized.getSince());
            assertEquals(docInfo.getDeprecated(), deserialized.getDeprecated());
            assertEquals(docInfo.getSee().size(), deserialized.getSee().size());
            assertTrue(deserialized.hasData());
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("Version information for schema element")
        void versionInformationForSchemaElement() {
            docInfo.setSince("FundsXML 4.0");

            assertTrue(docInfo.hasData());
            assertEquals("FundsXML 4.0", docInfo.getSince());
            assertNull(docInfo.getDeprecated());
        }

        @Test
        @DisplayName("Deprecated element with replacement")
        void deprecatedElementWithReplacement() {
            docInfo.setDeprecated("Use 'newAmount' element instead - removed in version 5.0");
            docInfo.addSee("newAmount");

            assertTrue(docInfo.hasData());
            assertNotNull(docInfo.getDeprecated());
            assertEquals(1, docInfo.getSee().size());
        }

        @Test
        @DisplayName("Multiple see references")
        void multipleSeeReferences() {
            docInfo.addSee("xs:complexType");
            docInfo.addSee("xs:simpleType");
            docInfo.addSee("xs:element");

            assertTrue(docInfo.hasData());
            assertEquals(3, docInfo.getSee().size());
        }
    }
}
