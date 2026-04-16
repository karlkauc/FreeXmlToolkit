package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SequenceValueStrategy")
class SequenceValueStrategyTest {

    private final SequenceValueStrategy strategy = new SequenceValueStrategy();

    @Test
    @DisplayName("Simple sequence with pattern")
    void simplePattern() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/id");
        assertEquals("ID-0001", strategy.resolve(null, Map.of("pattern", "ID-{seq:4}", "start", "1"), ctx));
        assertEquals("ID-0002", strategy.resolve(null, Map.of("pattern", "ID-{seq:4}", "start", "1"), ctx));
    }

    @Test
    @DisplayName("Custom start and step")
    void customStartAndStep() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/num");
        assertEquals("NUM-0100", strategy.resolve(null, Map.of("pattern", "NUM-{seq:4}", "start", "100", "step", "10"), ctx));
        assertEquals("NUM-0110", strategy.resolve(null, Map.of("pattern", "NUM-{seq:4}", "start", "100", "step", "10"), ctx));
    }

    @Test
    @DisplayName("Pattern without padding")
    void noPadding() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/v");
        assertEquals("v1", strategy.resolve(null, Map.of("pattern", "v{seq}"), ctx));
        assertEquals("v2", strategy.resolve(null, Map.of("pattern", "v{seq}"), ctx));
    }

    @Test
    @DisplayName("No pattern defaults to plain number")
    void noPattern() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/n");
        assertEquals("1", strategy.resolve(null, Map.of(), ctx));
        assertEquals("2", strategy.resolve(null, Map.of(), ctx));
    }

    @Test
    @DisplayName("Multiple seq placeholders in one pattern")
    void multiplePlaceholders() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/multi");
        // Both placeholders get the same counter value
        String result = strategy.resolve(null, Map.of("pattern", "{seq:2}-{seq:3}"), ctx);
        assertEquals("01-001", result);
    }

    @Test
    @DisplayName("replaceSequencePlaceholders static method")
    void staticReplace() {
        assertEquals("ID-0042", SequenceValueStrategy.replaceSequencePlaceholders("ID-{seq:4}", 42));
        assertEquals("42", SequenceValueStrategy.replaceSequencePlaceholders("{seq}", 42));
        assertEquals("PRE-007-SUF", SequenceValueStrategy.replaceSequencePlaceholders("PRE-{seq:3}-SUF", 7));
    }

    @Test
    @DisplayName("Invalid start/step defaults gracefully")
    void invalidConfigDefaults() {
        var ctx = new GenerationContext();
        ctx.setCurrentXPath("/bad");
        assertEquals("1", strategy.resolve(null, Map.of("start", "abc", "step", "xyz"), ctx));
    }
}
