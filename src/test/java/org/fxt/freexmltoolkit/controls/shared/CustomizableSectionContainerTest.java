package org.fxt.freexmltoolkit.controls.shared;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the section ordering and migration logic used by CustomizableSectionContainer.
 * These tests verify the pure ordering algorithm without requiring JavaFX.
 */
class CustomizableSectionContainerTest {

    /**
     * Reproduces the order-resolution algorithm from CustomizableSectionContainer.loadPersistedState()
     * without requiring JavaFX or PropertiesService.
     */
    private List<String> resolveOrder(String persistedOrder,
                                      LinkedHashMap<String, Integer> registeredSections) {
        List<String> result;

        if (persistedOrder != null && !persistedOrder.isBlank()) {
            List<String> persisted = Arrays.asList(persistedOrder.split(","));
            result = new ArrayList<>();

            // Add persisted sections that still exist
            for (String id : persisted) {
                if (registeredSections.containsKey(id)) {
                    result.add(id);
                }
            }

            // Append new sections not in persisted order (sorted by defaultOrder)
            registeredSections.entrySet().stream()
                    .filter(e -> !result.contains(e.getKey()))
                    .sorted(Comparator.comparingInt(e -> e.getValue()))
                    .forEach(e -> result.add(e.getKey()));
        } else {
            result = registeredSections.entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> e.getValue()))
                    .map(e -> e.getKey())
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        return result;
    }

    @Test
    @DisplayName("Should use default order when no persisted state exists")
    void testDefaultOrder() {
        LinkedHashMap<String, Integer> sections = new LinkedHashMap<>();
        sections.put("structure", 1);
        sections.put("templates", 2);
        sections.put("xpathTester", 3);
        sections.put("quickHelp", 4);

        List<String> order = resolveOrder(null, sections);

        assertEquals(List.of("structure", "templates", "xpathTester", "quickHelp"), order);
    }

    @Test
    @DisplayName("Should restore persisted order")
    void testPersistedOrder() {
        LinkedHashMap<String, Integer> sections = new LinkedHashMap<>();
        sections.put("structure", 1);
        sections.put("templates", 2);
        sections.put("xpathTester", 3);
        sections.put("quickHelp", 4);

        List<String> order = resolveOrder("quickHelp,structure,xpathTester,templates", sections);

        assertEquals(List.of("quickHelp", "structure", "xpathTester", "templates"), order);
    }

    @Test
    @DisplayName("Should append new sections at end when upgrading")
    void testMigrationNewSections() {
        LinkedHashMap<String, Integer> sections = new LinkedHashMap<>();
        sections.put("structure", 1);
        sections.put("templates", 2);
        sections.put("xpathTester", 3);
        sections.put("quickHelp", 4);
        sections.put("newFeature", 5);

        // Persisted order from before newFeature existed
        List<String> order = resolveOrder("quickHelp,structure,templates,xpathTester", sections);

        assertEquals(5, order.size());
        // User's custom order preserved
        assertEquals("quickHelp", order.get(0));
        assertEquals("structure", order.get(1));
        assertEquals("templates", order.get(2));
        assertEquals("xpathTester", order.get(3));
        // New section appended at end
        assertEquals("newFeature", order.get(4));
    }

    @Test
    @DisplayName("Should append multiple new sections sorted by default order")
    void testMigrationMultipleNewSections() {
        LinkedHashMap<String, Integer> sections = new LinkedHashMap<>();
        sections.put("a", 1);
        sections.put("b", 2);
        sections.put("c", 3);
        sections.put("newLow", 2);   // defaultOrder 2
        sections.put("newHigh", 5);  // defaultOrder 5

        List<String> order = resolveOrder("c,a,b", sections);

        assertEquals(5, order.size());
        // User's custom order preserved
        assertEquals("c", order.get(0));
        assertEquals("a", order.get(1));
        assertEquals("b", order.get(2));
        // New sections appended, sorted by defaultOrder
        assertEquals("newLow", order.get(3));
        assertEquals("newHigh", order.get(4));
    }

    @Test
    @DisplayName("Should ignore removed sections from persisted order")
    void testMigrationRemovedSections() {
        LinkedHashMap<String, Integer> sections = new LinkedHashMap<>();
        sections.put("structure", 1);
        sections.put("templates", 2);

        // Persisted order includes a section that no longer exists
        List<String> order = resolveOrder("removedSection,structure,templates", sections);

        assertEquals(List.of("structure", "templates"), order);
    }

    @Test
    @DisplayName("Should handle empty persisted order as no persistence")
    void testEmptyPersistedOrder() {
        LinkedHashMap<String, Integer> sections = new LinkedHashMap<>();
        sections.put("a", 3);
        sections.put("b", 1);
        sections.put("c", 2);

        List<String> order = resolveOrder("", sections);

        assertEquals(List.of("b", "c", "a"), order);
    }

    @Test
    @DisplayName("Should handle combined migration: new sections added, old sections removed")
    void testMigrationCombined() {
        LinkedHashMap<String, Integer> sections = new LinkedHashMap<>();
        sections.put("kept1", 1);
        sections.put("kept2", 3);
        sections.put("brandNew", 2);

        // "removed" was in persisted order but no longer registered
        List<String> order = resolveOrder("kept2,removed,kept1", sections);

        assertEquals(3, order.size());
        assertEquals("kept2", order.get(0));
        assertEquals("kept1", order.get(1));
        assertEquals("brandNew", order.get(2));
    }

    @Test
    @DisplayName("Should produce serializable order string")
    void testOrderSerialization() {
        List<String> order = List.of("a", "b", "c");
        String serialized = String.join(",", order);
        assertEquals("a,b,c", serialized);

        List<String> deserialized = Arrays.asList(serialized.split(","));
        assertEquals(order, deserialized);
    }
}
