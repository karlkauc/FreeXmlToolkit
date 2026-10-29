package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Laid-out text blocks: wrapping, the no-wrap mode and the cell-line limit (toolkit-free). */
class TextBlockTest {

    private static final String LONG = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi";

    @Test
    void wrapsAtTheWrapWidthWithoutLosingText() {
        GridMetrics m = new GridMetrics(TextMeasurer.estimating(), 120);
        TextBlock block = TextBlock.of(m, LONG, GridFont.ROW, m.wrapWidth());
        assertTrue(block.lineCount() > 1);
        assertEquals(LONG, String.join(" ", block.lines()));
        assertEquals(GridMetrics.rowHeight(block.lineCount()), block.height(), 0.001);
    }

    @Test
    void noWrapKeepsEverythingOnOneLine() {
        GridMetrics m = new GridMetrics(TextMeasurer.estimating(), GridMetrics.NO_WRAP);
        assertFalse(m.wraps());
        TextBlock block = TextBlock.of(m, LONG, GridFont.ROW, m.wrapWidth());
        assertEquals(1, block.lineCount());
        assertEquals(LONG, block.lastLine());
        assertEquals(GridMetrics.ROW_HEIGHT, block.height(), 0.001);
    }

    @Test
    void cellLineLimitCutsOffWithAnEllipsis() {
        GridMetrics limited = new GridMetrics(TextMeasurer.estimating(), 120, 2);
        TextBlock block = TextBlock.of(limited, LONG, GridFont.ROW, limited.wrapWidth());
        assertEquals(2, block.lineCount());
        assertTrue(block.lastLine().endsWith(TextBlock.ELLIPSIS), block.lastLine());
        assertEquals(GridMetrics.rowHeight(2), block.height(), 0.001);

        GridMetrics unlimited = new GridMetrics(TextMeasurer.estimating(), 120, 0);
        TextBlock full = TextBlock.of(unlimited, LONG, GridFont.ROW, unlimited.wrapWidth());
        assertTrue(full.lineCount() > 2, "0 means unlimited");
        assertFalse(full.lastLine().endsWith(TextBlock.ELLIPSIS));

        TextBlock shortText = TextBlock.of(limited, "short", GridFont.ROW, limited.wrapWidth());
        assertEquals("short", shortText.lastLine(), "text within the limit is untouched");
    }
}
