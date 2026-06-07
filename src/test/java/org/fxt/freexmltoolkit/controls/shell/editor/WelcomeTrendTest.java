package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WelcomeTrendTest {

    @Test
    void sevenDaySeriesNormalizesToUnitRange() {
        // raw daily totals (oldest→newest)
        List<Integer> raw = List.of(0, 2, 4, 0, 8, 1, 4);
        List<Double> norm = WelcomeTrend.normalize(raw);
        assertEquals(7, norm.size());
        assertEquals(0.0, norm.get(0), 1e-9);     // min → 0
        assertEquals(1.0, norm.get(4), 1e-9);     // max (8) → 1
        assertTrue(norm.stream().allMatch(v -> v >= 0.0 && v <= 1.0));
    }

    @Test
    void singleElementSeriesIsFlatZero() {
        List<Double> norm = WelcomeTrend.normalize(List.of(5));
        assertEquals(1, norm.size());
        assertEquals(0.0, norm.get(0), 1e-9);
    }

    @Test
    void allZeroSeriesIsFlatZero() {
        List<Double> norm = WelcomeTrend.normalize(List.of(0, 0, 0));
        assertTrue(norm.stream().allMatch(v -> v == 0.0));
    }
}
