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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdSortOrder enum.
 */
class XsdSortOrderTest {

    @Test
    @DisplayName("Should have TYPE_BEFORE_NAME value")
    void testTypeBeforeNameValue() {
        XsdSortOrder order = XsdSortOrder.TYPE_BEFORE_NAME;
        assertNotNull(order);
        assertEquals("TYPE_BEFORE_NAME", order.name());
    }

    @Test
    @DisplayName("Should have NAME_BEFORE_TYPE value")
    void testNameBeforeTypeValue() {
        XsdSortOrder order = XsdSortOrder.NAME_BEFORE_TYPE;
        assertNotNull(order);
        assertEquals("NAME_BEFORE_TYPE", order.name());
    }

    @Test
    @DisplayName("TYPE_BEFORE_NAME should have correct display name")
    void testTypeBeforeNameDisplayName() {
        XsdSortOrder order = XsdSortOrder.TYPE_BEFORE_NAME;
        assertEquals("Type before Name", order.getDisplayName());
    }

    @Test
    @DisplayName("NAME_BEFORE_TYPE should have correct display name")
    void testNameBeforeTypeDisplayName() {
        XsdSortOrder order = XsdSortOrder.NAME_BEFORE_TYPE;
        assertEquals("Name before Type", order.getDisplayName());
    }

    @Test
    @DisplayName("Should have exactly two values")
    void testEnumValuesCount() {
        XsdSortOrder[] values = XsdSortOrder.values();
        assertEquals(2, values.length);
    }

    @Test
    @DisplayName("valueOf should return correct enum constant")
    void testValueOf() {
        assertEquals(XsdSortOrder.TYPE_BEFORE_NAME, XsdSortOrder.valueOf("TYPE_BEFORE_NAME"));
        assertEquals(XsdSortOrder.NAME_BEFORE_TYPE, XsdSortOrder.valueOf("NAME_BEFORE_TYPE"));
    }

    @Test
    @DisplayName("valueOf should throw exception for invalid value")
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> XsdSortOrder.valueOf("INVALID"));
    }

    @Test
    @DisplayName("toString should return display name")
    void testToString() {
        assertEquals("Type before Name", XsdSortOrder.TYPE_BEFORE_NAME.toString());
        assertEquals("Name before Type", XsdSortOrder.NAME_BEFORE_TYPE.toString());
    }

    @Test
    @DisplayName("Should find order by display name")
    void testFindByDisplayName() {
        assertEquals(XsdSortOrder.TYPE_BEFORE_NAME, XsdSortOrder.findByDisplayName("Type before Name"));
        assertEquals(XsdSortOrder.NAME_BEFORE_TYPE, XsdSortOrder.findByDisplayName("Name before Type"));
    }

    @Test
    @DisplayName("findByDisplayName should return null for unknown display name")
    void testFindByDisplayNameUnknown() {
        assertNull(XsdSortOrder.findByDisplayName("Unknown"));
        assertNull(XsdSortOrder.findByDisplayName(null));
    }
}
