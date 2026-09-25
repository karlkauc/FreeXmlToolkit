package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pure-Java tests for {@link GridMetrics}, {@link TextBlock} and the estimating measurer. */
class GridMetricsTest {

    @Test
    void rowHeightGrowsByLineHeightPerExtraLine() {
        assertEquals(24.0, GridMetrics.rowHeight(1), 0.001);
        assertEquals(24.0, GridMetrics.rowHeight(0), 0.001, "zero lines still reserve one row");
        assertEquals(24.0 + 2 * 16.0, GridMetrics.rowHeight(3), 0.001);
    }

    @Test
    void estimatingMeasurerReproducesLegacyGlyphFactors() {
        GridMetrics m = GridMetrics.estimated();
        assertEquals(4 * 7.2, m.width("abcd", GridFont.ROW), 0.001, "ROW: 12 pt * 0.6");
        assertEquals(4 * 7.2, m.width("abcd", GridFont.ROW_BOLD), 0.001);
        assertEquals(4 * 6.0, m.width("abcd", GridFont.SMALL), 0.001, "SMALL: 10 pt * 0.6");
        assertEquals(0.0, m.width("", GridFont.ROW), 0.001);
        assertEquals(0.0, m.width(null, GridFont.ROW), 0.001);
    }

    @Test
    void textBlockReportsWidestLineAndTotalHeight() {
        GridMetrics m = GridMetrics.estimated();
        TextBlock block = TextBlock.of(m, "aaa bbbbb cc", GridFont.ROW, 5 * 7.2);
        assertEquals(List.of("aaa", "bbbbb", "cc"), block.lines());
        assertEquals(5 * 7.2, block.width(), 0.001);
        assertEquals(GridMetrics.rowHeight(3), block.height(), 0.001);
        assertEquals(3, block.lineCount());
    }

    @Test
    void textBlockOfNullIsOneEmptyLine() {
        TextBlock block = TextBlock.of(GridMetrics.estimated(), null, GridFont.ROW, 100);
        assertEquals(List.of(""), block.lines());
        assertEquals(0.0, block.width(), 0.001);
        assertEquals(GridMetrics.ROW_HEIGHT, block.height(), 0.001);
    }

    @Test
    void defaultWrapWidthIsUsedByConvenienceWrap() {
        GridMetrics m = GridMetrics.estimated();
        assertEquals(480.0, GridMetrics.DEFAULT_WRAP_WIDTH, 0.001);
        assertEquals(480.0, m.wrapWidth(), 0.001);
    }
}
