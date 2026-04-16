package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NullValueStrategy")
class NullValueStrategyTest {

    @Test
    @DisplayName("Returns NIL sentinel")
    void returnsSentinel() {
        var strategy = new NullValueStrategy();
        assertEquals(ValueStrategy.NIL_SENTINEL, strategy.resolve(null, Map.of(), new GenerationContext()));
    }
}
