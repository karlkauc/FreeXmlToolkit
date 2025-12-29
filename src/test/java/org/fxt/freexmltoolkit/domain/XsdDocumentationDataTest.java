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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XsdDocumentationData")
class XsdDocumentationDataTest {

    private XsdDocumentationData docData;

    @BeforeEach
    void setUp() {
        docData = new XsdDocumentationData();
    }

    @Nested
    @DisplayName("Default State")
    class DefaultStateTests {

        @Test
        @DisplayName("New instance has empty extended element map")
        void newInstanceHasEmptyExtendedElementMap() {
            assertNotNull(docData.getExtendedXsdElementMap());
            assertTrue(docData.getExtendedXsdElementMap().isEmpty());
        }

        @Test
        @DisplayName("New instance has empty type usage map")
        void newInstanceHasEmptyTypeUsageMap() {
            assertNotNull(docData.getTypeUsageMap());
            assertTrue(docData.getTypeUsageMap().isEmpty());
        }

        @Test
        @DisplayName("New instance has empty global elements list")
        void newInstanceHasEmptyGlobalElementsList() {
            assertNotNull(docData.getGlobalElements());
            assertTrue(docData.getGlobalElements().isEmpty());
        }

        @Test
        @DisplayName("New instance has empty global complex types list")
        void newInstanceHasEmptyGlobalComplexTypesList() {
            assertNotNull(docData.getGlobalComplexTypes());
            assertTrue(docData.getGlobalComplexTypes().isEmpty());
        }

        @Test
        @DisplayName("New instance has empty global simple types list")
        void newInstanceHasEmptyGlobalSimpleTypesList() {
            assertNotNull(docData.getGlobalSimpleTypes());
            assertTrue(docData.getGlobalSimpleTypes().isEmpty());
        }

        @Test
        @DisplayName("New instance has empty namespaces map")
        void newInstanceHasEmptyNamespacesMap() {
            assertNotNull(docData.getNamespaces());
            assertTrue(docData.getNamespaces().isEmpty());
        }

        @Test
        @DisplayName("New instance has null version")
        void newInstanceHasNullVersion() {
            assertNull(docData.getVersion());
        }

        @Test
        @DisplayName("New instance has null target namespace")
        void newInstanceHasNullTargetNamespace() {
            assertNull(docData.getTargetNamespace());
        }
    }

    @Nested
    @DisplayName("Setters and Getters")
    class SettersGettersTests {

        @Test
        @DisplayName("Set and get version")
        void setAndGetVersion() {
            docData.setVersion("1.1");
            assertEquals("1.1", docData.getVersion());
        }

        @Test
        @DisplayName("Set and get target namespace")
        void setAndGetTargetNamespace() {
            docData.setTargetNamespace("http://example.com/schema");
            assertEquals("http://example.com/schema", docData.getTargetNamespace());
        }

        @Test
        @DisplayName("Set and get XSD file path")
        void setAndGetXsdFilePath() {
            docData.setXsdFilePath("/path/to/schema.xsd");
            assertEquals("/path/to/schema.xsd", docData.getXsdFilePath());
        }

        @Test
        @DisplayName("Set and get attribute form default")
        void setAndGetAttributeFormDefault() {
            docData.setAttributeFormDefault("qualified");
            assertEquals("qualified", docData.getAttributeFormDefault());
        }

        @Test
        @DisplayName("Set and get element form default")
        void setAndGetElementFormDefault() {
            docData.setElementFormDefault("qualified");
            assertEquals("qualified", docData.getElementFormDefault());
        }

        @Test
        @DisplayName("Set and get default open content")
        void setAndGetDefaultOpenContent() {
            OpenContent openContent = new OpenContent(OpenContent.Mode.INTERLEAVE);
            docData.setDefaultOpenContent(openContent);

            assertNotNull(docData.getDefaultOpenContent());
            assertEquals(OpenContent.Mode.INTERLEAVE, docData.getDefaultOpenContent().getMode());
        }

        @Test
        @DisplayName("Set and get extended element map")
        void setAndGetExtendedElementMap() {
            Map<String, XsdExtendedElement> elementMap = new HashMap<>();
            docData.setExtendedXsdElementMap(elementMap);

            assertSame(elementMap, docData.getExtendedXsdElementMap());
        }

        @Test
        @DisplayName("Set and get type usage map")
        void setAndGetTypeUsageMap() {
            Map<String, List<XsdExtendedElement>> typeUsageMap = new HashMap<>();
            docData.setTypeUsageMap(typeUsageMap);

            assertSame(typeUsageMap, docData.getTypeUsageMap());
        }

        @Test
        @DisplayName("Set and get namespaces")
        void setAndGetNamespaces() {
            Map<String, String> namespaces = new HashMap<>();
            namespaces.put("xs", "http://www.w3.org/2001/XMLSchema");
            namespaces.put("tns", "http://example.com/schema");
            docData.setNamespaces(namespaces);

            assertEquals(2, docData.getNamespaces().size());
            assertEquals("http://www.w3.org/2001/XMLSchema", docData.getNamespaces().get("xs"));
        }
    }

    @Nested
    @DisplayName("getNameSpacesAsString")
    class GetNameSpacesAsStringTests {

        @Test
        @DisplayName("Returns empty string for null namespaces")
        void returnsEmptyStringForNullNamespaces() {
            docData.setNamespaces(null);
            assertEquals("", docData.getNameSpacesAsString());
        }

        @Test
        @DisplayName("Returns empty string for empty namespaces")
        void returnsEmptyStringForEmptyNamespaces() {
            docData.setNamespaces(new HashMap<>());
            assertEquals("", docData.getNameSpacesAsString());
        }

        @Test
        @DisplayName("Returns formatted string for single namespace")
        void returnsFormattedStringForSingleNamespace() {
            Map<String, String> namespaces = new HashMap<>();
            namespaces.put("xs", "http://www.w3.org/2001/XMLSchema");
            docData.setNamespaces(namespaces);

            String result = docData.getNameSpacesAsString();
            assertTrue(result.contains("xs='http://www.w3.org/2001/XMLSchema'"));
        }

        @Test
        @DisplayName("Returns HTML formatted string for multiple namespaces")
        void returnsHtmlFormattedStringForMultipleNamespaces() {
            Map<String, String> namespaces = new LinkedHashMap<>(); // Preserve order
            namespaces.put("xs", "http://www.w3.org/2001/XMLSchema");
            namespaces.put("tns", "http://example.com/schema");
            docData.setNamespaces(namespaces);

            String result = docData.getNameSpacesAsString();
            assertTrue(result.contains("<br />"));
            assertTrue(result.contains("xs='http://www.w3.org/2001/XMLSchema'"));
            assertTrue(result.contains("tns='http://example.com/schema'"));
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("FundsXML schema documentation data")
        void fundsXmlSchemaDocumentationData() {
            docData.setVersion("1.1");
            docData.setTargetNamespace("http://www.fundsxml.org/FundsXML4");
            docData.setXsdFilePath("/schemas/FundsXML4.xsd");
            docData.setElementFormDefault("qualified");
            docData.setAttributeFormDefault("unqualified");

            Map<String, String> namespaces = new HashMap<>();
            namespaces.put("xs", "http://www.w3.org/2001/XMLSchema");
            namespaces.put("fxml", "http://www.fundsxml.org/FundsXML4");
            docData.setNamespaces(namespaces);

            assertEquals("1.1", docData.getVersion());
            assertEquals("qualified", docData.getElementFormDefault());
            assertEquals(2, docData.getNamespaces().size());
        }

        @Test
        @DisplayName("XSD 1.1 schema with open content")
        void xsd11SchemaWithOpenContent() {
            docData.setVersion("1.1");

            OpenContent defaultOpen = new OpenContent(OpenContent.Mode.INTERLEAVE);
            defaultOpen.setDefault(true);
            defaultOpen.setNamespace("##other");
            defaultOpen.setProcessContents("lax");
            docData.setDefaultOpenContent(defaultOpen);

            assertNotNull(docData.getDefaultOpenContent());
            assertTrue(docData.getDefaultOpenContent().isDefault());
        }

        @Test
        @DisplayName("Schema with multiple type usages")
        void schemaWithMultipleTypeUsages() {
            Map<String, List<XsdExtendedElement>> typeUsageMap = new HashMap<>();
            typeUsageMap.put("AddressType", new ArrayList<>());
            typeUsageMap.put("CustomerType", new ArrayList<>());
            docData.setTypeUsageMap(typeUsageMap);

            assertEquals(2, docData.getTypeUsageMap().size());
            assertTrue(docData.getTypeUsageMap().containsKey("AddressType"));
            assertTrue(docData.getTypeUsageMap().containsKey("CustomerType"));
        }
    }
}
