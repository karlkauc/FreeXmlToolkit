/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for language filtering in XsdExtendedElement.
 */
@DisplayName("XsdExtendedElement Language Filtering Tests")
class XsdExtendedElementLanguageTest {

    private XsdExtendedElement element;

    @BeforeEach
    void setUp() {
        element = new XsdExtendedElement();
        element.setUseMarkdownRenderer(false); // Disable markdown for predictable test output
    }

    @Test
    @DisplayName("Should return all languages when no filter is specified (null)")
    void testGetFilteredDocumentation_NoFilter() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "English doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("de", "German doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("fr", "French doc"));

        Map<String, String> result = element.getFilteredLanguageDocumentation(null);

        assertEquals(3, result.size());
        assertEquals("English doc", result.get("en"));
        assertEquals("German doc", result.get("de"));
        assertEquals("French doc", result.get("fr"));
    }

    @Test
    @DisplayName("Should return all languages when filter is empty set")
    void testGetFilteredDocumentation_EmptyFilter() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "English doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("de", "German doc"));

        Map<String, String> result = element.getFilteredLanguageDocumentation(Set.of());

        assertEquals(2, result.size());
        assertTrue(result.containsKey("en"));
        assertTrue(result.containsKey("de"));
    }

    @Test
    @DisplayName("Should filter to only selected languages")
    void testGetFilteredDocumentation_WithFilter() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "English doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("de", "German doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("fr", "French doc"));

        Map<String, String> result = element.getFilteredLanguageDocumentation(Set.of("en", "fr"));

        assertEquals(2, result.size());
        assertTrue(result.containsKey("en"));
        assertTrue(result.containsKey("fr"));
        assertFalse(result.containsKey("de"));
    }

    @Test
    @DisplayName("Should always include 'default' language as fallback")
    void testGetFilteredDocumentation_DefaultFallback() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("default", "Default doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "English doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("de", "German doc"));

        // Filter for only "en" - but "default" should also be included as fallback
        Map<String, String> result = element.getFilteredLanguageDocumentation(Set.of("en"));

        assertEquals(2, result.size());
        assertTrue(result.containsKey("en"));
        assertTrue(result.containsKey("default"));
        assertFalse(result.containsKey("de"));
    }

    @Test
    @DisplayName("Should handle null language code as 'default'")
    void testGetFilteredDocumentation_NullLangCode() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo(null, "No lang specified"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "English doc"));

        Map<String, String> result = element.getFilteredLanguageDocumentation(null);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("default"));
        assertTrue(result.containsKey("en"));
        assertEquals("No lang specified", result.get("default"));
    }

    @Test
    @DisplayName("Should return empty map when documentations list is null")
    void testGetFilteredDocumentation_NullDocumentations() {
        // Create a new element without adding any documentations
        XsdExtendedElement emptyElement = new XsdExtendedElement();
        emptyElement.setUseMarkdownRenderer(false);

        Map<String, String> result = emptyElement.getFilteredLanguageDocumentation(Set.of("en"));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty map when documentations list is empty")
    void testGetFilteredDocumentation_EmptyDocumentations() {
        // element has empty documentations list by default

        Map<String, String> result = element.getFilteredLanguageDocumentation(Set.of("en"));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should preserve insertion order when filtering (LinkedHashMap)")
    void testGetFilteredDocumentation_PreservesOrder() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "English doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("de", "German doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("fr", "French doc"));

        // Use a filter to trigger the LinkedHashMap code path
        Map<String, String> result = element.getFilteredLanguageDocumentation(Set.of("en", "de", "fr"));

        // Convert to array to check order
        String[] keys = result.keySet().toArray(new String[0]);
        assertEquals("en", keys[0]);
        assertEquals("de", keys[1]);
        assertEquals("fr", keys[2]);
    }

    @Test
    @DisplayName("Should keep first occurrence when duplicate language codes exist")
    void testGetFilteredDocumentation_DuplicateLangs() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "First English"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "Second English"));

        Map<String, String> result = element.getFilteredLanguageDocumentation(null);

        assertEquals(1, result.size());
        assertEquals("First English", result.get("en"));
    }

    @Test
    @DisplayName("Should handle filter for non-existent language gracefully")
    void testGetFilteredDocumentation_NonExistentLanguage() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "English doc"));

        // Filter for a language that doesn't exist
        Map<String, String> result = element.getFilteredLanguageDocumentation(Set.of("es"));

        // Should be empty because "es" doesn't exist and there's no "default" fallback
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should work correctly with single language in documentation")
    void testGetFilteredDocumentation_SingleLanguage() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("en", "Only English"));

        Map<String, String> result = element.getFilteredLanguageDocumentation(Set.of("en"));

        assertEquals(1, result.size());
        assertEquals("Only English", result.get("en"));
    }

    @Test
    @DisplayName("Should handle case-insensitive language matching")
    void testGetFilteredDocumentation_CaseInsensitive() {
        // Add documentation with mixed case languages
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("EN", "English uppercase"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("de", "German lowercase"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("Fr", "French mixed case"));

        // Filter with different case than source
        Map<String, String> result = element.getFilteredLanguageDocumentation(Set.of("en", "DE", "fr"));

        // All should match regardless of case (normalized to lowercase)
        assertEquals(3, result.size());
        assertTrue(result.containsKey("en"));
        assertTrue(result.containsKey("de"));
        assertTrue(result.containsKey("fr"));
    }

    @Test
    @DisplayName("Should normalize language codes to lowercase in output")
    void testGetFilteredDocumentation_NormalizedOutput() {
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("EN", "English doc"));
        element.getDocumentations().add(new XsdExtendedElement.DocumentationInfo("DE", "German doc"));

        Map<String, String> result = element.getFilteredLanguageDocumentation(null);

        // Output keys should be lowercase
        assertTrue(result.containsKey("en"));
        assertTrue(result.containsKey("de"));
        assertFalse(result.containsKey("EN"));
        assertFalse(result.containsKey("DE"));
    }
}
