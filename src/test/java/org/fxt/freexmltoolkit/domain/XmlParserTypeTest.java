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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XmlParserType")
class XmlParserTypeTest {

    @Test
    @DisplayName("All enum values exist")
    void allValuesExist() {
        assertEquals(2, XmlParserType.values().length);
        assertNotNull(XmlParserType.SAXON);
        assertNotNull(XmlParserType.XERCES);
    }

    @ParameterizedTest
    @EnumSource(XmlParserType.class)
    @DisplayName("All parser types have display name")
    void allHaveDisplayName(XmlParserType parserType) {
        assertNotNull(parserType.getDisplayName());
        assertFalse(parserType.getDisplayName().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(XmlParserType.class)
    @DisplayName("All parser types have description")
    void allHaveDescription(XmlParserType parserType) {
        assertNotNull(parserType.getDescription());
        assertFalse(parserType.getDescription().isEmpty());
    }

    @Nested
    @DisplayName("SAXON parser type")
    class SaxonTests {

        @Test
        @DisplayName("Has correct display name")
        void hasCorrectDisplayName() {
            assertEquals("Saxon-HE", XmlParserType.SAXON.getDisplayName());
        }

        @Test
        @DisplayName("Has correct description")
        void hasCorrectDescription() {
            assertTrue(XmlParserType.SAXON.getDescription().contains("XSD 1.0"));
        }

        @Test
        @DisplayName("toString returns display name")
        void toStringReturnsDisplayName() {
            assertEquals("Saxon-HE", XmlParserType.SAXON.toString());
        }
    }

    @Nested
    @DisplayName("XERCES parser type")
    class XercesTests {

        @Test
        @DisplayName("Has correct display name")
        void hasCorrectDisplayName() {
            assertEquals("Apache Xerces", XmlParserType.XERCES.getDisplayName());
        }

        @Test
        @DisplayName("Has correct description")
        void hasCorrectDescription() {
            String description = XmlParserType.XERCES.getDescription();
            assertTrue(description.contains("XSD 1.0"));
            assertTrue(description.contains("1.1"));
        }

        @Test
        @DisplayName("toString returns display name")
        void toStringReturnsDisplayName() {
            assertEquals("Apache Xerces", XmlParserType.XERCES.toString());
        }
    }

    @Test
    @DisplayName("Can iterate over all values")
    void canIterateOverValues() {
        int count = 0;
        for (XmlParserType type : XmlParserType.values()) {
            count++;
            assertNotNull(type);
        }
        assertEquals(2, count);
    }

    @Test
    @DisplayName("valueOf returns correct enum")
    void valueOfReturnsCorrectEnum() {
        assertEquals(XmlParserType.SAXON, XmlParserType.valueOf("SAXON"));
        assertEquals(XmlParserType.XERCES, XmlParserType.valueOf("XERCES"));
    }

    @Test
    @DisplayName("valueOf throws for invalid name")
    void valueOfThrowsForInvalidName() {
        assertThrows(IllegalArgumentException.class, () ->
                XmlParserType.valueOf("INVALID"));
    }
}
