package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OmitValueStrategy")
class OmitValueStrategyTest {

    @Test
    @DisplayName("Returns OMIT sentinel")
    void returnsSentinel() {
        var strategy = new OmitValueStrategy();
        assertEquals(ValueStrategy.OMIT_SENTINEL, strategy.resolve(null, Map.of(), new GenerationContext()));
    }
}
