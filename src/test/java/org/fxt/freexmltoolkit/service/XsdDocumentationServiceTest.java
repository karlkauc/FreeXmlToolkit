package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class XsdDocumentationServiceTest {

    @Test
    void testSetIncludedLanguages_FiltersNullValues() {
        XsdDocumentationService service = new XsdDocumentationService();

        // Create a set with null values
        Set<String> languagesWithNulls = new LinkedHashSet<>();
        languagesWithNulls.add("en");
        languagesWithNulls.add(null);  // Should be filtered out
        languagesWithNulls.add("de");
        languagesWithNulls.add(null);  // Should be filtered out
        languagesWithNulls.add("fr");

        // Set the languages
        service.setIncludedLanguages(languagesWithNulls);

        // Get the included languages
        Set<String> result = service.getIncludedLanguages();

        // Verify nulls were filtered
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains("en"));
        assertTrue(result.contains("de"));
        assertTrue(result.contains("fr"));
        assertFalse(result.contains(null));
    }

    @Test
    void testSetIncludedLanguages_HandlesNull() {
        XsdDocumentationService service = new XsdDocumentationService();

        // Set null should be allowed (means all languages)
        service.setIncludedLanguages(null);

        assertNull(service.getIncludedLanguages());
    }

    @Test
    void testSetIncludedLanguages_HandlesEmptySet() {
        XsdDocumentationService service = new XsdDocumentationService();

        Set<String> empty = new LinkedHashSet<>();
        service.setIncludedLanguages(empty);

        Set<String> result = service.getIncludedLanguages();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSetIncludedLanguages_HandlesAllNulls() {
        XsdDocumentationService service = new XsdDocumentationService();

        // Create a set with only null values
        Set<String> allNulls = new LinkedHashSet<>();
        allNulls.add(null);
        allNulls.add(null);

        service.setIncludedLanguages(allNulls);

        Set<String> result = service.getIncludedLanguages();
        assertNotNull(result);
        assertTrue(result.isEmpty());  // All nulls should be filtered out
    }
}
