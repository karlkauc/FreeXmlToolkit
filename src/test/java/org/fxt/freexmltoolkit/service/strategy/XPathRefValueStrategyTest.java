package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("XPathRefValueStrategy")
class XPathRefValueStrategyTest {

    private final XPathRefValueStrategy strategy = new XPathRefValueStrategy();

    @Test
    @DisplayName("Returns value from referenced XPath")
    void returnsReferencedValue() {
        var ctx = new GenerationContext();
        ctx.recordGeneratedValue("/order/@id", "ORD-001");
        assertEquals("ORD-001", strategy.resolve(null, Map.of("ref", "/order/@id"), ctx));
    }

    @Test
    @DisplayName("Returns empty for not-yet-generated reference")
    void returnsEmptyForMissing() {
        var ctx = new GenerationContext();
        assertEquals("", strategy.resolve(null, Map.of("ref", "/future/path"), ctx));
    }

    @Test
    @DisplayName("Returns empty for missing ref config")
    void returnsEmptyForMissingConfig() {
        var ctx = new GenerationContext();
        assertEquals("", strategy.resolve(null, Map.of(), ctx));
    }

    @Test
    @DisplayName("Returns empty for blank ref config")
    void returnsEmptyForBlankConfig() {
        var ctx = new GenerationContext();
        assertEquals("", strategy.resolve(null, Map.of("ref", "  "), ctx));
    }
}
