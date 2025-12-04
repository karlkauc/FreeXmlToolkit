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

package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdNodeSorter - utility class for sorting XSD schema children.
 */
class XsdNodeSorterTest {

    private List<XsdNode> children;

    @BeforeEach
    void setUp() {
        children = new ArrayList<>();
    }

    @Test
    @DisplayName("Should return empty list for null input")
    void testNullInput() {
        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(null, XsdSortOrder.TYPE_BEFORE_NAME);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for empty input")
    void testEmptyInput() {
        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(new ArrayList<>(), XsdSortOrder.TYPE_BEFORE_NAME);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should place imports before all other elements")
    void testImportsFirst() {
        XsdComplexType complexType = new XsdComplexType("MyType");

        XsdImport xsdImport = new XsdImport();
        xsdImport.setNamespace("http://example.com");
        xsdImport.setSchemaLocation("schema.xsd");

        XsdElement element = new XsdElement();
        element.setName("Root");

        children.add(complexType);
        children.add(xsdImport);
        children.add(element);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.TYPE_BEFORE_NAME);

        assertEquals(3, result.size());
        assertInstanceOf(XsdImport.class, result.get(0), "Import should be first");
    }

    @Test
    @DisplayName("Should place includes after imports")
    void testIncludesAfterImports() {
        XsdInclude include = new XsdInclude();
        include.setSchemaLocation("types.xsd");

        XsdImport xsdImport = new XsdImport();
        xsdImport.setNamespace("http://example.com");

        XsdComplexType complexType = new XsdComplexType("MyType");

        children.add(include);
        children.add(complexType);
        children.add(xsdImport);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.TYPE_BEFORE_NAME);

        assertEquals(3, result.size());
        assertInstanceOf(XsdImport.class, result.get(0), "Import should be first");
        assertInstanceOf(XsdInclude.class, result.get(1), "Include should be second");
    }

    @Test
    @DisplayName("Should place global elements after includes")
    void testGlobalElementsAfterIncludes() {
        XsdElement globalElement = new XsdElement();
        globalElement.setName("Root");

        XsdComplexType complexType = new XsdComplexType("MyType");

        XsdInclude include = new XsdInclude();
        include.setSchemaLocation("types.xsd");

        children.add(complexType);
        children.add(globalElement);
        children.add(include);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.TYPE_BEFORE_NAME);

        assertEquals(3, result.size());
        assertInstanceOf(XsdInclude.class, result.get(0), "Include should be first");
        assertInstanceOf(XsdElement.class, result.get(1), "Global element should be second");
        assertInstanceOf(XsdComplexType.class, result.get(2), "ComplexType should be last");
    }

    @Test
    @DisplayName("Should sort imports alphabetically by namespace+schemaLocation")
    void testImportsAlphabetical() {
        XsdImport importA = new XsdImport();
        importA.setNamespace("http://a.com");

        XsdImport importZ = new XsdImport();
        importZ.setNamespace("http://z.com");

        XsdImport importM = new XsdImport();
        importM.setNamespace("http://m.com");

        children.add(importZ);
        children.add(importA);
        children.add(importM);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.TYPE_BEFORE_NAME);

        assertEquals("http://a.com", ((XsdImport) result.get(0)).getNamespace());
        assertEquals("http://m.com", ((XsdImport) result.get(1)).getNamespace());
        assertEquals("http://z.com", ((XsdImport) result.get(2)).getNamespace());
    }

    @Test
    @DisplayName("Should sort includes alphabetically by schemaLocation")
    void testIncludesAlphabetical() {
        XsdInclude includeA = new XsdInclude();
        includeA.setSchemaLocation("a_types.xsd");

        XsdInclude includeZ = new XsdInclude();
        includeZ.setSchemaLocation("z_types.xsd");

        XsdInclude includeM = new XsdInclude();
        includeM.setSchemaLocation("m_types.xsd");

        children.add(includeZ);
        children.add(includeA);
        children.add(includeM);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.TYPE_BEFORE_NAME);

        assertEquals("a_types.xsd", ((XsdInclude) result.get(0)).getSchemaLocation());
        assertEquals("m_types.xsd", ((XsdInclude) result.get(1)).getSchemaLocation());
        assertEquals("z_types.xsd", ((XsdInclude) result.get(2)).getSchemaLocation());
    }

    @Test
    @DisplayName("Should sort global elements alphabetically by name")
    void testGlobalElementsAlphabetical() {
        XsdElement elementA = new XsdElement();
        elementA.setName("Apple");

        XsdElement elementZ = new XsdElement();
        elementZ.setName("Zebra");

        XsdElement elementM = new XsdElement();
        elementM.setName("Mango");

        children.add(elementZ);
        children.add(elementA);
        children.add(elementM);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.TYPE_BEFORE_NAME);

        assertEquals("Apple", result.get(0).getName());
        assertEquals("Mango", result.get(1).getName());
        assertEquals("Zebra", result.get(2).getName());
    }

    @Test
    @DisplayName("TYPE_BEFORE_NAME should sort by type first, then by name")
    void testTypeBeforeNameSorting() {
        XsdSimpleType simpleType = new XsdSimpleType("StringType");

        XsdComplexType complexTypeA = new XsdComplexType("AddressType");

        XsdComplexType complexTypeZ = new XsdComplexType("ZipType");

        XsdGroup group = new XsdGroup();
        group.setName("PersonGroup");

        children.add(simpleType);
        children.add(complexTypeZ);
        children.add(group);
        children.add(complexTypeA);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.TYPE_BEFORE_NAME);

        // Order should be: complextype (AddressType, ZipType), group, simpletype
        assertEquals(4, result.size());

        // First two should be ComplexTypes (alphabetically sorted)
        assertInstanceOf(XsdComplexType.class, result.get(0));
        assertEquals("AddressType", result.get(0).getName());

        assertInstanceOf(XsdComplexType.class, result.get(1));
        assertEquals("ZipType", result.get(1).getName());

        // Then Group
        assertInstanceOf(XsdGroup.class, result.get(2));

        // Then SimpleType
        assertInstanceOf(XsdSimpleType.class, result.get(3));
    }

    @Test
    @DisplayName("NAME_BEFORE_TYPE should sort by name only")
    void testNameBeforeTypeSorting() {
        XsdSimpleType simpleType = new XsdSimpleType("CurrencyType");

        XsdComplexType complexType = new XsdComplexType("AddressType");

        XsdGroup group = new XsdGroup();
        group.setName("BillingGroup");

        children.add(simpleType);
        children.add(complexType);
        children.add(group);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.NAME_BEFORE_TYPE);

        // Order should be purely alphabetical by name
        assertEquals(3, result.size());
        assertEquals("AddressType", result.get(0).getName());
        assertEquals("BillingGroup", result.get(1).getName());
        assertEquals("CurrencyType", result.get(2).getName());
    }

    @Test
    @DisplayName("Should handle mixed content correctly")
    void testMixedContent() {
        XsdImport xsdImport = new XsdImport();
        xsdImport.setNamespace("http://example.com");

        XsdInclude include = new XsdInclude();
        include.setSchemaLocation("types.xsd");

        XsdElement globalElement = new XsdElement();
        globalElement.setName("Root");

        XsdComplexType complexType = new XsdComplexType("AddressType");

        XsdSimpleType simpleType = new XsdSimpleType("StringType");

        // Add in random order
        children.add(simpleType);
        children.add(globalElement);
        children.add(xsdImport);
        children.add(complexType);
        children.add(include);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.TYPE_BEFORE_NAME);

        assertEquals(5, result.size());
        assertInstanceOf(XsdImport.class, result.get(0), "Import should be first");
        assertInstanceOf(XsdInclude.class, result.get(1), "Include should be second");
        assertInstanceOf(XsdElement.class, result.get(2), "Global element should be third");
        // Remaining types sorted by type then name
        assertInstanceOf(XsdComplexType.class, result.get(3));
        assertInstanceOf(XsdSimpleType.class, result.get(4));
    }

    @Test
    @DisplayName("Should not modify original list")
    void testOriginalListUnmodified() {
        XsdElement element = new XsdElement();
        element.setName("Root");

        XsdComplexType complexType = new XsdComplexType("MyType");

        children.add(element);
        children.add(complexType);
        List<XsdNode> originalOrder = new ArrayList<>(children);

        XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.TYPE_BEFORE_NAME);

        assertEquals(originalOrder, children, "Original list should not be modified");
    }

    @Test
    @DisplayName("hasSameOrder should return true for identical lists")
    void testHasSameOrderIdentical() {
        XsdElement element = new XsdElement();
        element.setName("Root");

        List<XsdNode> list1 = Arrays.asList(element);
        List<XsdNode> list2 = Arrays.asList(element);

        assertTrue(XsdNodeSorter.hasSameOrder(list1, list2));
    }

    @Test
    @DisplayName("hasSameOrder should return false for different lists")
    void testHasSameOrderDifferent() {
        XsdElement element1 = new XsdElement();
        element1.setName("Root");

        XsdElement element2 = new XsdElement();
        element2.setName("Another");

        List<XsdNode> list1 = Arrays.asList(element1, element2);
        List<XsdNode> list2 = Arrays.asList(element2, element1);

        assertFalse(XsdNodeSorter.hasSameOrder(list1, list2));
    }

    @Test
    @DisplayName("hasSameOrder should handle null inputs")
    void testHasSameOrderNull() {
        assertTrue(XsdNodeSorter.hasSameOrder(null, null));
        assertFalse(XsdNodeSorter.hasSameOrder(new ArrayList<>(), null));
        assertFalse(XsdNodeSorter.hasSameOrder(null, new ArrayList<>()));
    }

    @Test
    @DisplayName("Should handle nodes with null names gracefully")
    void testNullNames() {
        XsdComplexType typeWithName = new XsdComplexType("Named");

        XsdComplexType typeWithoutName = new XsdComplexType(null);
        // name is null

        children.add(typeWithName);
        children.add(typeWithoutName);

        // Should not throw exception
        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.NAME_BEFORE_TYPE);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should handle case-insensitive sorting")
    void testCaseInsensitiveSorting() {
        XsdElement elementLower = new XsdElement();
        elementLower.setName("apple");

        XsdElement elementUpper = new XsdElement();
        elementUpper.setName("Banana");

        children.add(elementUpper);
        children.add(elementLower);

        List<XsdNode> result = XsdNodeSorter.sortSchemaChildren(children, XsdSortOrder.NAME_BEFORE_TYPE);

        // 'apple' should come before 'Banana' (case-insensitive)
        assertEquals("apple", result.get(0).getName());
        assertEquals("Banana", result.get(1).getName());
    }
}
