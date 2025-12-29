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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypeInfo")
class TypeInfoTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("simpleType() creates simple type info")
        void simpleTypeFactory() {
            TypeInfo info = TypeInfo.simpleType("MyString", "xs:string", 5, "A custom string", "/xs:schema/xs:simpleType");

            assertEquals("MyString", info.name());
            assertEquals(TypeInfo.TypeCategory.SIMPLE_TYPE, info.category());
            assertEquals("xs:string", info.baseType());
            assertEquals(5, info.usageCount());
            assertEquals("A custom string", info.documentation());
            assertEquals("/xs:schema/xs:simpleType", info.xpath());
            assertFalse(info.isAbstract());
            assertFalse(info.isMixed());
            assertNull(info.derivationType());
            assertNull(info.contentModel());
            assertTrue(info.usageXPaths().isEmpty());
        }

        @Test
        @DisplayName("simpleType() with usage XPaths")
        void simpleTypeWithUsage() {
            List<String> usages = List.of("/root/element1", "/root/element2");
            TypeInfo info = TypeInfo.simpleType("MyType", "xs:integer", 2, "Doc", "/path", usages);

            assertEquals(usages, info.usageXPaths());
        }

        @Test
        @DisplayName("complexType() creates complex type info")
        void complexTypeFactory() {
            TypeInfo info = TypeInfo.complexType("PersonType", "xs:anyType", 3, "Person data",
                    "/xs:schema/xs:complexType", true, true, "extension", "sequence");

            assertEquals("PersonType", info.name());
            assertEquals(TypeInfo.TypeCategory.COMPLEX_TYPE, info.category());
            assertEquals("xs:anyType", info.baseType());
            assertEquals(3, info.usageCount());
            assertTrue(info.isAbstract());
            assertTrue(info.isMixed());
            assertEquals("extension", info.derivationType());
            assertEquals("sequence", info.contentModel());
        }

        @Test
        @DisplayName("complexType() with usage XPaths")
        void complexTypeWithUsage() {
            List<String> usages = List.of("/root/person", "/root/employee");
            TypeInfo info = TypeInfo.complexType("PersonType", null, 2, null,
                    "/path", false, false, null, null, usages);

            assertEquals(usages, info.usageXPaths());
        }
    }

    @Nested
    @DisplayName("TypeCategory Enum")
    class TypeCategoryTests {

        @Test
        @DisplayName("SIMPLE_TYPE has correct display name")
        void simpleTypeDisplayName() {
            assertEquals("Simple Type", TypeInfo.TypeCategory.SIMPLE_TYPE.getDisplayName());
        }

        @Test
        @DisplayName("COMPLEX_TYPE has correct display name")
        void complexTypeDisplayName() {
            assertEquals("Complex Type", TypeInfo.TypeCategory.COMPLEX_TYPE.getDisplayName());
        }
    }

    @Nested
    @DisplayName("getTypeDescription")
    class TypeDescriptionTests {

        @Test
        @DisplayName("Simple type shows base type")
        void simpleTypeDescription() {
            TypeInfo info = TypeInfo.simpleType("MyType", "xs:string", 0, null, null);
            String desc = info.getTypeDescription();

            assertTrue(desc.contains("Simple Type"));
            assertTrue(desc.contains("xs:string"));
        }

        @Test
        @DisplayName("Complex type shows all attributes")
        void complexTypeDescription() {
            TypeInfo info = TypeInfo.complexType("PersonType", "BaseType", 0, null,
                    null, true, true, null, "sequence");
            String desc = info.getTypeDescription();

            assertTrue(desc.contains("Complex Type"));
            assertTrue(desc.contains("BaseType"));
            assertTrue(desc.contains("abstract"));
            assertTrue(desc.contains("mixed"));
            assertTrue(desc.contains("sequence"));
        }

        @Test
        @DisplayName("Type without base type omits extends")
        void typeWithoutBaseType() {
            TypeInfo info = TypeInfo.simpleType("MyType", null, 0, null, null);
            String desc = info.getTypeDescription();

            assertFalse(desc.contains("extends"));
        }

        @Test
        @DisplayName("Type with empty base type omits extends")
        void typeWithEmptyBaseType() {
            TypeInfo info = TypeInfo.simpleType("MyType", "", 0, null, null);
            String desc = info.getTypeDescription();

            assertFalse(desc.contains("extends"));
        }

        @Test
        @DisplayName("Non-abstract, non-mixed omits those labels")
        void nonAbstractNonMixed() {
            TypeInfo info = TypeInfo.complexType("SimpleType", null, 0, null,
                    null, false, false, null, null);
            String desc = info.getTypeDescription();

            assertFalse(desc.contains("abstract"));
            assertFalse(desc.contains("mixed"));
        }
    }

    @Nested
    @DisplayName("getUsageInfo")
    class UsageInfoTests {

        @Test
        @DisplayName("Zero usage shows 'Unused'")
        void zeroUsage() {
            TypeInfo info = TypeInfo.simpleType("Type", null, 0, null, null);
            assertEquals("Unused", info.getUsageInfo());
        }

        @Test
        @DisplayName("One usage shows singular")
        void oneUsage() {
            TypeInfo info = TypeInfo.simpleType("Type", null, 1, null, null);
            assertEquals("1 reference", info.getUsageInfo());
        }

        @Test
        @DisplayName("Multiple usages shows plural")
        void multipleUsages() {
            TypeInfo info = TypeInfo.simpleType("Type", null, 5, null, null);
            assertEquals("5 references", info.getUsageInfo());
        }
    }

    @Nested
    @DisplayName("Usage XPaths Formatting")
    class UsageXPathsTests {

        @Test
        @DisplayName("getUsageXPathsFormatted with empty list")
        void formattedEmpty() {
            TypeInfo info = TypeInfo.simpleType("Type", null, 0, null, null);
            assertEquals("", info.getUsageXPathsFormatted());
        }

        @Test
        @DisplayName("getUsageXPathsFormatted with null list")
        void formattedNull() {
            TypeInfo info = new TypeInfo("Type", TypeInfo.TypeCategory.SIMPLE_TYPE,
                    null, 0, null, null, false, false, null, null, null);
            assertEquals("", info.getUsageXPathsFormatted());
        }

        @Test
        @DisplayName("getUsageXPathsFormatted joins with semicolon")
        void formattedJoinsSemicolon() {
            List<String> usages = List.of("/path/a", "/path/b", "/path/c");
            TypeInfo info = TypeInfo.simpleType("Type", null, 3, null, null, usages);

            assertEquals("/path/a; /path/b; /path/c", info.getUsageXPathsFormatted());
        }

        @Test
        @DisplayName("getUsageXPathsForCsv with empty list")
        void csvEmpty() {
            TypeInfo info = TypeInfo.simpleType("Type", null, 0, null, null);
            assertEquals("", info.getUsageXPathsForCsv());
        }

        @Test
        @DisplayName("getUsageXPathsForCsv joins with newline")
        void csvJoinsNewline() {
            List<String> usages = List.of("/path/a", "/path/b");
            TypeInfo info = TypeInfo.simpleType("Type", null, 2, null, null, usages);

            assertEquals("/path/a\n/path/b", info.getUsageXPathsForCsv());
        }
    }

    @Nested
    @DisplayName("Record Methods")
    class RecordMethodTests {

        @Test
        @DisplayName("Record equality works")
        void recordEquality() {
            TypeInfo t1 = TypeInfo.simpleType("Type", "xs:string", 1, "Doc", "/path");
            TypeInfo t2 = TypeInfo.simpleType("Type", "xs:string", 1, "Doc", "/path");

            assertEquals(t1, t2);
            assertEquals(t1.hashCode(), t2.hashCode());
        }
    }
}
