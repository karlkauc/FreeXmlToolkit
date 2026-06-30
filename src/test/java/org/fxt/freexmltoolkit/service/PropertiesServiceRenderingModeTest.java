package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the JavaFX rendering-mode setting. The getter/setter normalize
 * the stored value to AUTO/HARDWARE/SOFTWARE, falling back to AUTO for unknown values.
 */
class PropertiesServiceRenderingModeTest {

    @Test
    @DisplayName("rendering.mode round-trips and falls back to AUTO for unknown values")
    void renderingModeRoundTripAndFallback() {
        PropertiesService p = PropertiesServiceImpl.getInstance();
        String original = p.getRenderingMode();
        try {
            p.setRenderingMode("HARDWARE");
            assertEquals("HARDWARE", p.getRenderingMode());
            p.setRenderingMode("SOFTWARE");
            assertEquals("SOFTWARE", p.getRenderingMode());
            p.setRenderingMode("AUTO");
            assertEquals("AUTO", p.getRenderingMode());

            // Case-insensitive normalization.
            p.setRenderingMode("hardware");
            assertEquals("HARDWARE", p.getRenderingMode());

            // Unknown / null fall back to AUTO.
            p.setRenderingMode("nonsense");
            assertEquals("AUTO", p.getRenderingMode());
            p.setRenderingMode(null);
            assertEquals("AUTO", p.getRenderingMode());
        } finally {
            p.setRenderingMode(original);
        }
    }
}
