package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EmptyValueStrategy")
class EmptyValueStrategyTest {

    @Test
    @DisplayName("Returns empty string")
    void returnsEmptyString() {
        var strategy = new EmptyValueStrategy();
        assertEquals("", strategy.resolve(null, Map.of(), new GenerationContext()));
    }
}
