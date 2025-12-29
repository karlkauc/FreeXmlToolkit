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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XsdNodeInfo")
class XsdNodeInfoTest {

    @Nested
    @DisplayName("NodeType Enum")
    class NodeTypeTests {

        @Test
        @DisplayName("All 13 node types are defined")
        void allNodeTypesAreDefined() {
            assertEquals(13, XsdNodeInfo.NodeType.values().length);
        }

        @Test
        @DisplayName("XSD 1.0 node types are not XSD 1.1 features")
        void xsd10NodeTypesAreNotXsd11Features() {
            assertFalse(XsdNodeInfo.NodeType.ELEMENT.isXsd11Feature());
            assertFalse(XsdNodeInfo.NodeType.ATTRIBUTE.isXsd11Feature());
            assertFalse(XsdNodeInfo.NodeType.SEQUENCE.isXsd11Feature());
            assertFalse(XsdNodeInfo.NodeType.CHOICE.isXsd11Feature());
            assertFalse(XsdNodeInfo.NodeType.ANY.isXsd11Feature());
            assertFalse(XsdNodeInfo.NodeType.SIMPLE_TYPE.isXsd11Feature());
            assertFalse(XsdNodeInfo.NodeType.COMPLEX_TYPE.isXsd11Feature());
            assertFalse(XsdNodeInfo.NodeType.SCHEMA.isXsd11Feature());
            assertFalse(XsdNodeInfo.NodeType.ALL.isXsd11Feature());
        }

        @Test
        @DisplayName("XSD 1.1 node types are XSD 1.1 features")
        void xsd11NodeTypesAreXsd11Features() {
            assertTrue(XsdNodeInfo.NodeType.ASSERT.isXsd11Feature());
            assertTrue(XsdNodeInfo.NodeType.ALTERNATIVE.isXsd11Feature());
            assertTrue(XsdNodeInfo.NodeType.OPEN_CONTENT.isXsd11Feature());
            assertTrue(XsdNodeInfo.NodeType.OVERRIDE.isXsd11Feature());
        }

        @ParameterizedTest
        @EnumSource(XsdNodeInfo.NodeType.class)
        @DisplayName("valueOf works for all node types")
        void valueOfWorksForAllNodeTypes(XsdNodeInfo.NodeType nodeType) {
            assertEquals(nodeType, XsdNodeInfo.NodeType.valueOf(nodeType.name()));
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Full constructor sets all values")
        void fullConstructorSetsAllValues() {
            XsdNodeInfo node = new XsdNodeInfo(
                    "testElement",
                    "xs:string",
                    "/root/test",
                    "Test documentation",
                    List.of(),
                    List.of("example1", "example2"),
                    "1",
                    "unbounded",
                    XsdNodeInfo.NodeType.ELEMENT,
                    "$value > 0",
                    Map.of("notQName", "##defined")
            );

            assertEquals("testElement", node.name());
            assertEquals("xs:string", node.type());
            assertEquals("/root/test", node.xpath());
            assertEquals("Test documentation", node.documentation());
            assertTrue(node.children().isEmpty());
            assertEquals(2, node.exampleValues().size());
            assertEquals("1", node.minOccurs());
            assertEquals("unbounded", node.maxOccurs());
            assertEquals(XsdNodeInfo.NodeType.ELEMENT, node.nodeType());
            assertEquals("$value > 0", node.xpathExpression());
            assertEquals(1, node.xsd11Attributes().size());
        }

        @Test
        @DisplayName("9-argument constructor for XSD 1.0 compatibility")
        void nineArgConstructorForXsd10() {
            XsdNodeInfo node = new XsdNodeInfo(
                    "element",
                    "xs:integer",
                    "/root/element",
                    "An integer element",
                    null,
                    List.of("42"),
                    "0",
                    "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );

            assertEquals("element", node.name());
            assertNull(node.xpathExpression());
            assertTrue(node.xsd11Attributes().isEmpty());
        }

        @Test
        @DisplayName("10-argument constructor with xpath expression")
        void tenArgConstructorWithXpathExpression() {
            XsdNodeInfo node = new XsdNodeInfo(
                    "assertElement",
                    "xs:string",
                    "/root/assert",
                    "Assert element",
                    null,
                    null,
                    "1",
                    "1",
                    XsdNodeInfo.NodeType.ASSERT,
                    ". > 0"
            );

            assertEquals(". > 0", node.xpathExpression());
            assertTrue(node.xsd11Attributes().isEmpty());
        }
    }

    @Nested
    @DisplayName("isXsd11")
    class IsXsd11Tests {

        @Test
        @DisplayName("Returns true for XSD 1.1 node type")
        void returnsTrueForXsd11NodeType() {
            XsdNodeInfo node = new XsdNodeInfo(
                    "assert",
                    null,
                    "/root/assert",
                    null,
                    null,
                    null,
                    "1",
                    "1",
                    XsdNodeInfo.NodeType.ASSERT
            );

            assertTrue(node.isXsd11());
        }

        @Test
        @DisplayName("Returns true when xpath expression is present")
        void returnsTrueWhenXpathExpressionPresent() {
            XsdNodeInfo node = new XsdNodeInfo(
                    "element",
                    "xs:string",
                    "/root/element",
                    null,
                    null,
                    null,
                    "1",
                    "1",
                    XsdNodeInfo.NodeType.ELEMENT,
                    "$value > 0"
            );

            assertTrue(node.isXsd11());
        }

        @Test
        @DisplayName("Returns true when XSD 1.1 attributes are present")
        void returnsTrueWhenXsd11AttributesPresent() {
            XsdNodeInfo node = new XsdNodeInfo(
                    "element",
                    "xs:string",
                    "/root/element",
                    null,
                    null,
                    null,
                    "1",
                    "1",
                    XsdNodeInfo.NodeType.ELEMENT,
                    null,
                    Map.of("targetNamespace", "http://example.com")
            );

            assertTrue(node.isXsd11());
        }

        @Test
        @DisplayName("Returns false for simple XSD 1.0 element")
        void returnsFalseForSimpleXsd10Element() {
            XsdNodeInfo node = new XsdNodeInfo(
                    "element",
                    "xs:string",
                    "/root/element",
                    "Simple element",
                    null,
                    null,
                    "1",
                    "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );

            assertFalse(node.isXsd11());
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equal records are equal")
        void equalRecordsAreEqual() {
            XsdNodeInfo node1 = new XsdNodeInfo(
                    "test", "xs:string", "/test", "doc",
                    List.of(), List.of("ex"), "1", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );
            XsdNodeInfo node2 = new XsdNodeInfo(
                    "test", "xs:string", "/test", "doc",
                    List.of(), List.of("ex"), "1", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );

            assertEquals(node1, node2);
            assertEquals(node1.hashCode(), node2.hashCode());
        }

        @Test
        @DisplayName("Different names are not equal")
        void differentNamesNotEqual() {
            XsdNodeInfo node1 = new XsdNodeInfo(
                    "test1", "xs:string", "/test", null,
                    null, null, "1", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );
            XsdNodeInfo node2 = new XsdNodeInfo(
                    "test2", "xs:string", "/test", null,
                    null, null, "1", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );

            assertNotEquals(node1, node2);
        }
    }

    @Nested
    @DisplayName("Children Hierarchy")
    class HierarchyTests {

        @Test
        @DisplayName("Node can have children")
        void nodeCanHaveChildren() {
            XsdNodeInfo child1 = new XsdNodeInfo(
                    "child1", "xs:string", "/root/child1", null,
                    Collections.emptyList(), null, "1", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );
            XsdNodeInfo child2 = new XsdNodeInfo(
                    "child2", "xs:integer", "/root/child2", null,
                    Collections.emptyList(), null, "0", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );

            XsdNodeInfo parent = new XsdNodeInfo(
                    "root", "xs:complexType", "/root", "Root element",
                    List.of(child1, child2), null, "1", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );

            assertEquals(2, parent.children().size());
            assertEquals("child1", parent.children().get(0).name());
            assertEquals("child2", parent.children().get(1).name());
        }

        @Test
        @DisplayName("Deep hierarchy works")
        void deepHierarchyWorks() {
            XsdNodeInfo grandchild = new XsdNodeInfo(
                    "grandchild", "xs:string", "/root/child/grandchild", null,
                    Collections.emptyList(), null, "1", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );
            XsdNodeInfo child = new XsdNodeInfo(
                    "child", "xs:complexType", "/root/child", null,
                    List.of(grandchild), null, "1", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );
            XsdNodeInfo root = new XsdNodeInfo(
                    "root", "xs:complexType", "/root", null,
                    List.of(child), null, "1", "1",
                    XsdNodeInfo.NodeType.SCHEMA
            );

            assertEquals("grandchild", root.children().get(0).children().get(0).name());
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("XSD 1.0 element with attributes")
        void xsd10ElementWithAttributes() {
            XsdNodeInfo attr = new XsdNodeInfo(
                    "id", "xs:ID", "/root/@id", "Unique identifier",
                    null, null, "1", "1",
                    XsdNodeInfo.NodeType.ATTRIBUTE
            );
            XsdNodeInfo element = new XsdNodeInfo(
                    "root", "RootType", "/root", "Root element",
                    List.of(attr), null, "1", "1",
                    XsdNodeInfo.NodeType.ELEMENT
            );

            assertFalse(element.isXsd11());
            assertEquals(1, element.children().size());
            assertEquals(XsdNodeInfo.NodeType.ATTRIBUTE, element.children().get(0).nodeType());
        }

        @Test
        @DisplayName("XSD 1.1 element with assertion")
        void xsd11ElementWithAssertion() {
            XsdNodeInfo assertion = new XsdNodeInfo(
                    "ageAssertion", null, "/person/xs:assert", "Age must be positive",
                    null, null, null, null,
                    XsdNodeInfo.NodeType.ASSERT,
                    "age >= 0"
            );

            assertTrue(assertion.isXsd11());
            assertEquals("age >= 0", assertion.xpathExpression());
        }

        @Test
        @DisplayName("Schema with open content")
        void schemaWithOpenContent() {
            XsdNodeInfo openContent = new XsdNodeInfo(
                    "openContent", null, "/schema/openContent", null,
                    null, null, null, null,
                    XsdNodeInfo.NodeType.OPEN_CONTENT
            );

            assertTrue(openContent.isXsd11());
            assertEquals(XsdNodeInfo.NodeType.OPEN_CONTENT, openContent.nodeType());
        }
    }
}
