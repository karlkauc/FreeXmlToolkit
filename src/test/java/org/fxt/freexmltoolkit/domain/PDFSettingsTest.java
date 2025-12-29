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

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PDFSettings")
class PDFSettingsTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor sets all values")
        void constructorSetsAllValues() {
            HashMap<String, String> customParams = new HashMap<>();
            customParams.put("page-width", "210mm");
            customParams.put("page-height", "297mm");

            PDFSettings settings = new PDFSettings(
                    customParams,
                    "FreeXmlToolkit",
                    "Karl Kauc",
                    "FreeXmlToolkit PDF Generator",
                    "2024-12-29",
                    "XSD Documentation",
                    "XML, XSD, documentation"
            );

            assertEquals(customParams, settings.customParameter());
            assertEquals("FreeXmlToolkit", settings.producer());
            assertEquals("Karl Kauc", settings.author());
            assertEquals("FreeXmlToolkit PDF Generator", settings.creator());
            assertEquals("2024-12-29", settings.creationDate());
            assertEquals("XSD Documentation", settings.title());
            assertEquals("XML, XSD, documentation", settings.keywords());
        }

        @Test
        @DisplayName("Constructor with null values")
        void constructorWithNullValues() {
            PDFSettings settings = new PDFSettings(
                    null, null, null, null, null, null, null
            );

            assertNull(settings.customParameter());
            assertNull(settings.producer());
            assertNull(settings.author());
            assertNull(settings.creator());
            assertNull(settings.creationDate());
            assertNull(settings.title());
            assertNull(settings.keywords());
        }

        @Test
        @DisplayName("Constructor with empty custom parameters")
        void constructorWithEmptyCustomParameters() {
            PDFSettings settings = new PDFSettings(
                    new HashMap<>(),
                    "Producer",
                    "Author",
                    "Creator",
                    "2024-01-01",
                    "Title",
                    "Keywords"
            );

            assertNotNull(settings.customParameter());
            assertTrue(settings.customParameter().isEmpty());
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equal records are equal")
        void equalRecordsAreEqual() {
            HashMap<String, String> params1 = new HashMap<>();
            params1.put("key", "value");

            HashMap<String, String> params2 = new HashMap<>();
            params2.put("key", "value");

            PDFSettings settings1 = new PDFSettings(
                    params1, "producer", "author", "creator",
                    "date", "title", "keywords"
            );
            PDFSettings settings2 = new PDFSettings(
                    params2, "producer", "author", "creator",
                    "date", "title", "keywords"
            );

            assertEquals(settings1, settings2);
            assertEquals(settings1.hashCode(), settings2.hashCode());
        }

        @Test
        @DisplayName("Different producers are not equal")
        void differentProducersNotEqual() {
            PDFSettings settings1 = new PDFSettings(
                    null, "producer1", "author", "creator",
                    "date", "title", "keywords"
            );
            PDFSettings settings2 = new PDFSettings(
                    null, "producer2", "author", "creator",
                    "date", "title", "keywords"
            );

            assertNotEquals(settings1, settings2);
        }

        @Test
        @DisplayName("Different custom parameters are not equal")
        void differentCustomParametersNotEqual() {
            HashMap<String, String> params1 = new HashMap<>();
            params1.put("key1", "value1");

            HashMap<String, String> params2 = new HashMap<>();
            params2.put("key2", "value2");

            PDFSettings settings1 = new PDFSettings(
                    params1, "producer", "author", "creator",
                    "date", "title", "keywords"
            );
            PDFSettings settings2 = new PDFSettings(
                    params2, "producer", "author", "creator",
                    "date", "title", "keywords"
            );

            assertNotEquals(settings1, settings2);
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("XSD documentation PDF settings")
        void xsdDocumentationPdfSettings() {
            HashMap<String, String> customParams = new HashMap<>();
            customParams.put("page-size", "A4");
            customParams.put("margin", "2cm");
            customParams.put("font-family", "sans-serif");

            PDFSettings settings = new PDFSettings(
                    customParams,
                    "FreeXmlToolkit v1.2.0",
                    "Documentation Team",
                    "XsdDocumentationService",
                    "2024-12-29T10:30:00",
                    "FundsXML 4.0 Schema Documentation",
                    "FundsXML, XSD, schema, investment, funds"
            );

            assertEquals(3, settings.customParameter().size());
            assertEquals("A4", settings.customParameter().get("page-size"));
            assertTrue(settings.title().contains("FundsXML"));
        }

        @Test
        @DisplayName("Minimal PDF settings")
        void minimalPdfSettings() {
            PDFSettings settings = new PDFSettings(
                    new HashMap<>(),
                    "FreeXmlToolkit",
                    null,
                    null,
                    null,
                    "Untitled Document",
                    null
            );

            assertEquals("FreeXmlToolkit", settings.producer());
            assertEquals("Untitled Document", settings.title());
            assertNull(settings.author());
        }

        @Test
        @DisplayName("PDF settings with complex keywords")
        void pdfSettingsWithComplexKeywords() {
            PDFSettings settings = new PDFSettings(
                    null,
                    "Producer",
                    "Author",
                    "Creator",
                    "2024-12-29",
                    "Report",
                    "XML, XSD 1.1, validation, assertions, type alternatives, open content"
            );

            assertTrue(settings.keywords().contains("XSD 1.1"));
            assertTrue(settings.keywords().contains("assertions"));
        }
    }
}
