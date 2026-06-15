package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the toolbar display settings. The getters normalize the
 * stored value, so an unknown icon-size value falls back to "small".
 */
class PropertiesServiceToolbarDisplayTest {

    @Test
    @DisplayName("toolbar.icon.size round-trips and falls back to 'small' for unknown values")
    void iconSizeRoundTripAndFallback() {
        PropertiesService p = PropertiesServiceImpl.getInstance();
        String original = p.getToolbarIconSize();
        try {
            p.setToolbarIconSize("large");
            assertEquals("large", p.getToolbarIconSize());
            p.setToolbarIconSize("nonsense");
            assertEquals("small", p.getToolbarIconSize());
        } finally {
            p.setToolbarIconSize(original);
        }
    }

    @Test
    @DisplayName("toolbar.show.labels round-trips as a boolean")
    void showLabelsRoundTrip() {
        PropertiesService p = PropertiesServiceImpl.getInstance();
        boolean original = p.isToolbarShowLabels();
        try {
            p.setToolbarShowLabels(true);
            assertTrue(p.isToolbarShowLabels());
            p.setToolbarShowLabels(false);
            assertFalse(p.isToolbarShowLabels());
        } finally {
            p.setToolbarShowLabels(original);
        }
    }
}
