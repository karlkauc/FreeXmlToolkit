package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Round-trip tests for the persisted grid zoom factor ({@code grid.zoom}). */
class PropertiesServiceGridZoomTest {

    @Test
    @DisplayName("grid.zoom defaults to 100 %, round-trips, clamps and survives garbage")
    void gridZoomRoundTripClampAndFallback() {
        PropertiesService p = PropertiesServiceImpl.getInstance();
        String original = p.get(PropertiesService.GRID_ZOOM);
        try {
            p.set(PropertiesService.GRID_ZOOM, "");
            assertEquals(1.0, p.getGridZoom(), 0.001, "blank means default");

            p.setGridZoom(1.3);
            assertEquals(1.3, p.getGridZoom(), 0.001);

            p.setGridZoom(9.0);
            assertEquals(3.0, p.getGridZoom(), 0.001, "clamped to the maximum");

            p.setGridZoom(0.1);
            assertEquals(0.5, p.getGridZoom(), 0.001, "clamped to the minimum");

            p.set(PropertiesService.GRID_ZOOM, "garbage");
            assertEquals(1.0, p.getGridZoom(), 0.001, "unparseable falls back to default");
        } finally {
            p.set(PropertiesService.GRID_ZOOM, original == null ? "" : original);
        }
    }
}
