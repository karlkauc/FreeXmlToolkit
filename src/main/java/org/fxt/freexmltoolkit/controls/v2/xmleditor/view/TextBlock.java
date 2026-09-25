package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.List;

/**
 * A piece of text laid out for the grid: its wrapped lines, the width of the widest line
 * and the row height needed to show all lines.
 *
 * @param lines  the wrapped lines (never empty)
 * @param width  the widest line's width in unscaled pixels
 * @param height the row height for {@link #lineCount()} lines (see {@link GridMetrics#rowHeight(int)})
 */
public record TextBlock(List<String> lines, double width, double height) {

    /**
     * Wraps {@code text} to {@code maxWidth} and measures the result.
     *
     * @param m        the metrics (measurer)
     * @param text     the text ({@code null} yields one empty line)
     * @param font     the font
     * @param maxWidth the wrap width in unscaled pixels
     * @return the laid-out block
     */
    public static TextBlock of(GridMetrics m, String text, GridFont font, double maxWidth) {
        List<String> lines = m.wrap(text, font, maxWidth);
        double widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, m.width(line, font));
        }
        return new TextBlock(lines, widest, GridMetrics.rowHeight(lines.size()));
    }

    /** @return the number of lines */
    public int lineCount() {
        return lines.size();
    }

    /** @return the last line (useful for placing a suffix after the text) */
    public String lastLine() {
        return lines.get(lines.size() - 1);
    }
}
