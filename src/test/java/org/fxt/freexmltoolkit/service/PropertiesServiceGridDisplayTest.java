package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Round-trip tests for the grid display preferences ({@code grid.wordWrap}, {@code grid.maxCellLines}). */
class PropertiesServiceGridDisplayTest {

    @Test
    @DisplayName("grid.wordWrap defaults to on, round-trips and treats garbage as on")
    void wordWrapRoundTrip() {
        PropertiesService p = PropertiesServiceImpl.getInstance();
        String original = p.get(PropertiesService.GRID_WORD_WRAP);
        try {
            p.set(PropertiesService.GRID_WORD_WRAP, "");
            assertTrue(p.isGridWordWrap(), "blank means the default (on)");
            p.setGridWordWrap(false);
            assertFalse(p.isGridWordWrap());
            p.setGridWordWrap(true);
            assertTrue(p.isGridWordWrap());
            p.set(PropertiesService.GRID_WORD_WRAP, "garbage");
            assertTrue(p.isGridWordWrap(), "only an explicit false switches wrapping off");
        } finally {
            p.set(PropertiesService.GRID_WORD_WRAP, original == null ? "" : original);
        }
    }

    @Test
    @DisplayName("grid.maxCellLines defaults to unlimited (0), round-trips, clamps and survives garbage")
    void maxCellLinesRoundTrip() {
        PropertiesService p = PropertiesServiceImpl.getInstance();
        String original = p.get(PropertiesService.GRID_MAX_CELL_LINES);
        try {
            p.set(PropertiesService.GRID_MAX_CELL_LINES, "");
            assertEquals(0, p.getGridMaxCellLines(), "blank means unlimited");
            p.setGridMaxCellLines(6);
            assertEquals(6, p.getGridMaxCellLines());
            p.setGridMaxCellLines(-3);
            assertEquals(0, p.getGridMaxCellLines(), "negative is unlimited");
            p.setGridMaxCellLines(5000);
            assertEquals(PropertiesService.GRID_MAX_CELL_LINES_MAX, p.getGridMaxCellLines(), "clamped");
            p.set(PropertiesService.GRID_MAX_CELL_LINES, "garbage");
            assertEquals(0, p.getGridMaxCellLines(), "unparseable falls back to unlimited");
        } finally {
            p.set(PropertiesService.GRID_MAX_CELL_LINES, original == null ? "" : original);
        }
    }
}
