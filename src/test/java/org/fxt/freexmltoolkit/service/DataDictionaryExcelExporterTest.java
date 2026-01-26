package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DataDictionaryExcelExporterTest {
    @Test
    void testConstruction() {
        DataDictionaryExcelExporter exporter = new DataDictionaryExcelExporter(null, null);
        assertNotNull(exporter);
    }

    @Test
    void testNullLanguageFiltering() {
        // Test that null values are filtered from language collections
        Set<String> languagesWithNulls = new LinkedHashSet<>();
        languagesWithNulls.add("en");
        languagesWithNulls.add(null);  // This should be filtered out
        languagesWithNulls.add("de");
        languagesWithNulls.add(null);  // Another null to filter
        languagesWithNulls.add("fr");

        // Simulate the filtering that happens in the exporter
        Set<String> filtered = languagesWithNulls.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Verify nulls were filtered
        assertEquals(3, filtered.size());
        assertTrue(filtered.contains("en"));
        assertTrue(filtered.contains("de"));
        assertTrue(filtered.contains("fr"));
        assertFalse(filtered.contains(null));
    }

    @Test
    void testNullLanguageSorting() {
        // Test that null values are filtered before sorting
        Set<String> languagesWithNulls = new LinkedHashSet<>();
        languagesWithNulls.add("de");
        languagesWithNulls.add(null);
        languagesWithNulls.add("en");
        languagesWithNulls.add("default");

        // Simulate the filtering and sorting that happens in generateLanguagesJson
        List<String> sorted = new ArrayList<>();
        sorted.add("default");  // Always first

        languagesWithNulls.stream()
                .filter(Objects::nonNull)  // Filter nulls
                .filter(lang -> !"default".equalsIgnoreCase(lang))
                .sorted()
                .forEach(sorted::add);

        // Verify correct ordering and no nulls
        assertEquals(3, sorted.size());
        assertEquals("default", sorted.get(0));
        assertEquals("de", sorted.get(1));
        assertEquals("en", sorted.get(2));
    }
}
