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

@DisplayName("OpenContent")
class OpenContentTest {

    private OpenContent openContent;

    @BeforeEach
    void setUp() {
        openContent = new OpenContent();
    }

    @Nested
    @DisplayName("Mode Enum")
    class ModeEnumTests {

        @Test
        @DisplayName("All two modes are defined")
        void allModesAreDefined() {
            assertEquals(2, OpenContent.Mode.values().length);
        }

        @Test
        @DisplayName("INTERLEAVE mode exists")
        void interleaveModeExists() {
            assertEquals(OpenContent.Mode.INTERLEAVE, OpenContent.Mode.valueOf("INTERLEAVE"));
        }

        @Test
        @DisplayName("SUFFIX mode exists")
        void suffixModeExists() {
            assertEquals(OpenContent.Mode.SUFFIX, OpenContent.Mode.valueOf("SUFFIX"));
        }

        @ParameterizedTest
        @EnumSource(OpenContent.Mode.class)
        @DisplayName("valueOf works for all modes")
        void valueOfWorksForAllModes(OpenContent.Mode mode) {
            assertEquals(mode, OpenContent.Mode.valueOf(mode.name()));
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor sets INTERLEAVE mode")
        void defaultConstructorSetsInterleaveMode() {
            OpenContent empty = new OpenContent();

            assertEquals(OpenContent.Mode.INTERLEAVE, empty.getMode());
            assertFalse(empty.isDefault());
            assertNull(empty.getNamespace());
            assertNull(empty.getProcessContents());
        }

        @Test
        @DisplayName("Parameterized constructor sets mode")
        void parameterizedConstructorSetsMode() {
            OpenContent suffix = new OpenContent(OpenContent.Mode.SUFFIX);

            assertEquals(OpenContent.Mode.SUFFIX, suffix.getMode());
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("Set and get isDefault")
        void setAndGetIsDefault() {
            openContent.setDefault(true);
            assertTrue(openContent.isDefault());

            openContent.setDefault(false);
            assertFalse(openContent.isDefault());
        }

        @Test
        @DisplayName("Set and get mode")
        void setAndGetMode() {
            openContent.setMode(OpenContent.Mode.SUFFIX);
            assertEquals(OpenContent.Mode.SUFFIX, openContent.getMode());

            openContent.setMode(OpenContent.Mode.INTERLEAVE);
            assertEquals(OpenContent.Mode.INTERLEAVE, openContent.getMode());
        }

        @Test
        @DisplayName("Set and get namespace")
        void setAndGetNamespace() {
            openContent.setNamespace("##any");
            assertEquals("##any", openContent.getNamespace());
        }

        @Test
        @DisplayName("Set and get processContents")
        void setAndGetProcessContents() {
            openContent.setProcessContents("lax");
            assertEquals("lax", openContent.getProcessContents());
        }

        @Test
        @DisplayName("Set and get documentation")
        void setAndGetDocumentation() {
            openContent.setDocumentation("Allows additional elements");
            assertEquals("Allows additional elements", openContent.getDocumentation());
        }
    }

    @Nested
    @DisplayName("getModeDisplayName")
    class GetModeDisplayNameTests {

        @Test
        @DisplayName("INTERLEAVE returns correct display name")
        void interleaveReturnsCorrectDisplayName() {
            openContent.setMode(OpenContent.Mode.INTERLEAVE);
            assertEquals("Interleave (elements can appear anywhere)", openContent.getModeDisplayName());
        }

        @Test
        @DisplayName("SUFFIX returns correct display name")
        void suffixReturnsCorrectDisplayName() {
            openContent.setMode(OpenContent.Mode.SUFFIX);
            assertEquals("Suffix (elements appear after defined content)", openContent.getModeDisplayName());
        }
    }

    @Nested
    @DisplayName("getNamespaceDisplayName")
    class GetNamespaceDisplayNameTests {

        @Test
        @DisplayName("Null namespace returns ##any")
        void nullNamespaceReturnsAny() {
            openContent.setNamespace(null);
            assertEquals("##any (any namespace)", openContent.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("Empty namespace returns ##any")
        void emptyNamespaceReturnsAny() {
            openContent.setNamespace("");
            assertEquals("##any (any namespace)", openContent.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("##any returns Any namespace")
        void anyReturnsAnyNamespace() {
            openContent.setNamespace("##any");
            assertEquals("Any namespace", openContent.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("##other returns correct display")
        void otherReturnsCorrectDisplay() {
            openContent.setNamespace("##other");
            assertEquals("Any namespace except target namespace", openContent.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("##local returns correct display")
        void localReturnsCorrectDisplay() {
            openContent.setNamespace("##local");
            assertEquals("No namespace", openContent.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("##targetNamespace returns correct display")
        void targetNamespaceReturnsCorrectDisplay() {
            openContent.setNamespace("##targetNamespace");
            assertEquals("Target namespace only", openContent.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("Specific URI returns as is")
        void specificUriReturnsAsIs() {
            openContent.setNamespace("http://example.com/ns");
            assertEquals("http://example.com/ns", openContent.getNamespaceDisplayName());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains all fields")
        void toStringContainsAllFields() {
            openContent.setDefault(true);
            openContent.setMode(OpenContent.Mode.SUFFIX);
            openContent.setNamespace("##other");
            openContent.setProcessContents("lax");

            String str = openContent.toString();
            assertTrue(str.contains("isDefault=true"));
            assertTrue(str.contains("SUFFIX"));
            assertTrue(str.contains("##other"));
            assertTrue(str.contains("lax"));
        }
    }

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Can serialize and deserialize")
        void canSerializeAndDeserialize() throws IOException, ClassNotFoundException {
            openContent.setDefault(true);
            openContent.setMode(OpenContent.Mode.SUFFIX);
            openContent.setNamespace("##any");
            openContent.setProcessContents("strict");
            openContent.setDocumentation("Default open content for schema");

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(openContent);
            oos.close();

            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            OpenContent deserialized = (OpenContent) ois.readObject();
            ois.close();

            assertEquals(openContent.isDefault(), deserialized.isDefault());
            assertEquals(openContent.getMode(), deserialized.getMode());
            assertEquals(openContent.getNamespace(), deserialized.getNamespace());
            assertEquals(openContent.getProcessContents(), deserialized.getProcessContents());
            assertEquals(openContent.getDocumentation(), deserialized.getDocumentation());
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("Schema-level default open content")
        void schemaLevelDefaultOpenContent() {
            openContent.setDefault(true);
            openContent.setMode(OpenContent.Mode.INTERLEAVE);
            openContent.setNamespace("##other");
            openContent.setProcessContents("lax");
            openContent.setDocumentation("Allow extension elements from other namespaces");

            assertTrue(openContent.isDefault());
            assertEquals("Any namespace except target namespace", openContent.getNamespaceDisplayName());
        }

        @Test
        @DisplayName("Complex type suffix open content")
        void complexTypeSuffixOpenContent() {
            openContent.setDefault(false);
            openContent.setMode(OpenContent.Mode.SUFFIX);
            openContent.setNamespace("http://example.com/extensions");
            openContent.setProcessContents("strict");

            assertFalse(openContent.isDefault());
            assertEquals("Suffix (elements appear after defined content)", openContent.getModeDisplayName());
        }

        @Test
        @DisplayName("Skip validation open content")
        void skipValidationOpenContent() {
            openContent.setMode(OpenContent.Mode.INTERLEAVE);
            openContent.setNamespace("##any");
            openContent.setProcessContents("skip");

            assertEquals("skip", openContent.getProcessContents());
            assertEquals("Any namespace", openContent.getNamespaceDisplayName());
        }
    }
}
