package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.List;

/**
 * The shared layout metrics of the XMLSpy-style grid: the geometry constants used by both
 * {@link GridCanvasView} and {@link RepeatingElementsTable}, plus the {@link TextMeasurer}
 * and wrap width that turn text into {@link TextBlock}s.
 *
 * <p>All values are unscaled model pixels; the canvas applies its zoom factor at draw time.</p>
 *
 * @param measurer  measures text widths
 * @param wrapWidth the width at which cell/value text starts wrapping onto further lines
 */
public record GridMetrics(TextMeasurer measurer, double wrapWidth) {

    /** Height of a single-line row. */
    public static final double ROW_HEIGHT = 24;
    /** Extra height per additional wrapped line inside a row. */
    public static final double LINE_HEIGHT = 16;
    /** Indentation per depth level. */
    public static final double INDENT = 20;
    /** Width reserved for the row icon. */
    public static final double ICON_AREA_WIDTH = 24;
    /** Width of the expand/collapse bar left of container rows. */
    public static final double EXPAND_BAR_WIDTH = 12;
    /** Horizontal padding inside a table cell. */
    public static final double CELL_PADDING = 6;
    /** Gap between the label and the value column inside an expanded-cell sub-row. */
    public static final double SUB_ROW_LABEL_VALUE_GAP = 20;
    /** Gap between a label and its "(n)" child-count suffix. */
    public static final double CHILD_COUNT_GAP = 4;
    /** Horizontal offset of a complex cell's text (room for the expand arrow). */
    public static final double COMPLEX_ARROW_OFFSET = 14;
    /** Default wrap width for values (about 66 monospace glyphs). */
    public static final double DEFAULT_WRAP_WIDTH = 480;

    /** @return metrics with the toolkit-free estimating measurer and the default wrap width */
    public static GridMetrics estimated() {
        return new GridMetrics(TextMeasurer.estimating(), DEFAULT_WRAP_WIDTH);
    }

    /**
     * @param lines number of text lines in the row
     * @return the row height: {@link #ROW_HEIGHT} plus {@link #LINE_HEIGHT} per extra line
     */
    public static double rowHeight(int lines) {
        return ROW_HEIGHT + Math.max(0, lines - 1) * LINE_HEIGHT;
    }

    /** @return the width of {@code text} in {@code font} (0 for {@code null}) */
    public double width(String text, GridFont font) {
        return measurer.width(text, font);
    }

    /** @return {@code text} wrapped to {@code maxWidth} in {@code font} */
    public List<String> wrap(String text, GridFont font, double maxWidth) {
        return TextWrap.wrap(text, font, maxWidth, measurer);
    }
}
