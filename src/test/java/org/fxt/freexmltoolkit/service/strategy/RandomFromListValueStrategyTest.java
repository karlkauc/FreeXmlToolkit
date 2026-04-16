package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RandomFromListValueStrategy")
class RandomFromListValueStrategyTest {

    private final RandomFromListValueStrategy strategy = new RandomFromListValueStrategy();
    private final GenerationContext context = new GenerationContext();

    @Test
    @DisplayName("Picks from comma-separated list")
    void picksFromList() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String result = strategy.resolve(null, Map.of("values", "A,B,C"), context);
            seen.add(result);
        }
        assertTrue(seen.contains("A"));
        assertTrue(seen.contains("B"));
        assertTrue(seen.contains("C"));
        assertEquals(3, seen.size());
    }

    @Test
    @DisplayName("Single value always returns that value")
    void singleValue() {
        assertEquals("Only", strategy.resolve(null, Map.of("values", "Only"), context));
    }

    @Test
    @DisplayName("Trims whitespace around values")
    void trimsWhitespace() {
        String result = strategy.resolve(null, Map.of("values", " A , B , C "), context);
        assertTrue(Set.of("A", "B", "C").contains(result));
    }

    @Test
    @DisplayName("Returns empty for empty values string")
    void emptyValues() {
        assertEquals("", strategy.resolve(null, Map.of("values", ""), context));
    }

    @Test
    @DisplayName("Returns empty for missing values key")
    void missingKey() {
        assertEquals("", strategy.resolve(null, Map.of(), context));
    }

    @Test
    @DisplayName("Filters empty entries from split")
    void filtersEmptyEntries() {
        String result = strategy.resolve(null, Map.of("values", "A,,B,,,C"), context);
        assertTrue(Set.of("A", "B", "C").contains(result));
    }
}
