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

package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XsdDocumentation")
class XsdDocumentationTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Single-arg constructor sets text only")
        void singleArgConstructor() {
            XsdDocumentation doc = new XsdDocumentation("Test documentation");

            assertEquals("Test documentation", doc.getText());
            assertNull(doc.getLang());
            assertNull(doc.getSource());
        }

        @Test
        @DisplayName("Two-arg constructor sets text and lang")
        void twoArgConstructor() {
            XsdDocumentation doc = new XsdDocumentation("German text", "de");

            assertEquals("German text", doc.getText());
            assertEquals("de", doc.getLang());
            assertNull(doc.getSource());
        }

        @Test
        @DisplayName("Three-arg constructor sets all values")
        void threeArgConstructor() {
            XsdDocumentation doc = new XsdDocumentation("Full text", "en", "http://example.com/docs");

            assertEquals("Full text", doc.getText());
            assertEquals("en", doc.getLang());
            assertEquals("http://example.com/docs", doc.getSource());
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GetterSetterTests {

        @Test
        @DisplayName("setText works correctly")
        void setText() {
            XsdDocumentation doc = new XsdDocumentation("Original");
            doc.setText("Modified");

            assertEquals("Modified", doc.getText());
        }

        @Test
        @DisplayName("setLang works correctly")
        void setLang() {
            XsdDocumentation doc = new XsdDocumentation("Text");
            doc.setLang("fr");

            assertEquals("fr", doc.getLang());
        }

        @Test
        @DisplayName("setSource works correctly")
        void setSource() {
            XsdDocumentation doc = new XsdDocumentation("Text");
            doc.setSource("http://source.com");

            assertEquals("http://source.com", doc.getSource());
        }
    }

    @Nested
    @DisplayName("hasLang")
    class HasLangTests {

        @Test
        @DisplayName("Returns true when lang is set")
        void returnsTrueWhenSet() {
            XsdDocumentation doc = new XsdDocumentation("Text", "en");
            assertTrue(doc.hasLang());
        }

        @Test
        @DisplayName("Returns false when lang is null")
        void returnsFalseWhenNull() {
            XsdDocumentation doc = new XsdDocumentation("Text");
            assertFalse(doc.hasLang());
        }

        @Test
        @DisplayName("Returns false when lang is empty")
        void returnsFalseWhenEmpty() {
            XsdDocumentation doc = new XsdDocumentation("Text", "");
            assertFalse(doc.hasLang());
        }
    }

    @Nested
    @DisplayName("deepCopy")
    class DeepCopyTests {

        @Test
        @DisplayName("Creates independent copy with all values")
        void createsIndependentCopy() {
            XsdDocumentation original = new XsdDocumentation("Text", "en", "http://source.com");
            XsdDocumentation copy = original.deepCopy();

            assertEquals(original.getText(), copy.getText());
            assertEquals(original.getLang(), copy.getLang());
            assertEquals(original.getSource(), copy.getSource());

            // Verify independence
            copy.setText("Modified");
            assertNotEquals(original.getText(), copy.getText());
        }

        @Test
        @DisplayName("Handles null values")
        void handlesNullValues() {
            XsdDocumentation original = new XsdDocumentation("Text");
            XsdDocumentation copy = original.deepCopy();

            assertEquals("Text", copy.getText());
            assertNull(copy.getLang());
            assertNull(copy.getSource());
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Equal objects are equal")
        void equalObjectsAreEqual() {
            XsdDocumentation d1 = new XsdDocumentation("Text", "en", "source");
            XsdDocumentation d2 = new XsdDocumentation("Text", "en", "source");

            assertEquals(d1, d2);
            assertEquals(d1.hashCode(), d2.hashCode());
        }

        @Test
        @DisplayName("Different text not equal")
        void differentTextNotEqual() {
            XsdDocumentation d1 = new XsdDocumentation("Text1", "en");
            XsdDocumentation d2 = new XsdDocumentation("Text2", "en");

            assertNotEquals(d1, d2);
        }

        @Test
        @DisplayName("Different lang not equal")
        void differentLangNotEqual() {
            XsdDocumentation d1 = new XsdDocumentation("Text", "en");
            XsdDocumentation d2 = new XsdDocumentation("Text", "de");

            assertNotEquals(d1, d2);
        }

        @Test
        @DisplayName("Different source not equal")
        void differentSourceNotEqual() {
            XsdDocumentation d1 = new XsdDocumentation("Text", "en", "source1");
            XsdDocumentation d2 = new XsdDocumentation("Text", "en", "source2");

            assertNotEquals(d1, d2);
        }

        @Test
        @DisplayName("Same instance is equal")
        void sameInstanceIsEqual() {
            XsdDocumentation doc = new XsdDocumentation("Text");
            assertEquals(doc, doc);
        }

        @Test
        @DisplayName("Null is not equal")
        void nullIsNotEqual() {
            XsdDocumentation doc = new XsdDocumentation("Text");
            assertNotEquals(null, doc);
        }

        @Test
        @DisplayName("Different type is not equal")
        void differentTypeNotEqual() {
            XsdDocumentation doc = new XsdDocumentation("Text");
            assertNotEquals("Text", doc);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("Includes lang when set")
        void includesLang() {
            XsdDocumentation doc = new XsdDocumentation("Text", "de");
            String str = doc.toString();

            assertTrue(str.contains("lang='de'"));
            assertTrue(str.contains("Text"));
        }

        @Test
        @DisplayName("Omits lang when null")
        void omitsLangWhenNull() {
            XsdDocumentation doc = new XsdDocumentation("Text");
            String str = doc.toString();

            assertFalse(str.contains("lang="));
            assertTrue(str.contains("Text"));
        }

        @Test
        @DisplayName("Truncates long text")
        void truncatesLongText() {
            String longText = "A".repeat(100);
            XsdDocumentation doc = new XsdDocumentation(longText);
            String str = doc.toString();

            assertTrue(str.contains("..."));
            assertTrue(str.length() < longText.length() + 50); // some overhead for formatting
        }

        @Test
        @DisplayName("Shows full short text")
        void showsFullShortText() {
            XsdDocumentation doc = new XsdDocumentation("Short text");
            String str = doc.toString();

            assertTrue(str.contains("Short text"));
            assertFalse(str.contains("..."));
        }
    }
}
