package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FixedValueStrategy")
class FixedValueStrategyTest {

    private final FixedValueStrategy strategy = new FixedValueStrategy();
    private final GenerationContext context = new GenerationContext();

    @Test
    @DisplayName("Returns configured value")
    void returnsConfiguredValue() {
        assertEquals("AT", strategy.resolve(null, Map.of("value", "AT"), context));
    }

    @Test
    @DisplayName("Returns empty string for missing value key")
    void returnsEmptyForMissingKey() {
        assertEquals("", strategy.resolve(null, Map.of(), context));
    }

    @Test
    @DisplayName("Returns empty string for null config value")
    void returnsEmptyForNullValue() {
        var config = new java.util.HashMap<String, String>();
        config.put("value", null);
        assertEquals("", strategy.resolve(null, config, context));
    }

    @Test
    @DisplayName("Preserves special characters in value")
    void preservesSpecialCharacters() {
        assertEquals("<test>&amp;", strategy.resolve(null, Map.of("value", "<test>&amp;"), context));
    }
}
