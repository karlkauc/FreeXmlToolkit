package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays out one grid of rows — the root grid or the content of an opened table cell — in its own
 * coordinates: blocks of siblings (rows sharing a parent row) get a name column as wide as their
 * widest label, each child block docks at its parent's value column, embedded tables sit beside their
 * owner's name cell, and every row gets its height (expanded containers have no own line).
 *
 * <p>The rows' top block hangs off {@code null} (the root) or off a virtual anchor row (depth
 * {@code < 0}, the parent of a cell's top rows); it starts at {@code originX}. Results are written to
 * the rows ({@link FlatRow#setCellGeometry}, value blocks) and returned as a {@link Result}.
 */
final class GridBlockLayout {

    static final double NAME_VALUE_GAP = 16;
    static final double MIN_NAME_COL_WIDTH = 120;
    static final double MIN_VALUE_COL_WIDTH = 100;
    static final double RIGHT_MARGIN = 8;
    static final double VALUE_PADDING = 6;

    /** What the layout needs to know about the rows, supplied by the view. */
    interface Measure {
        GridMetrics metrics();

        /** @return the width of the label and what follows it ({@code <n>}, "(n)", table glyph) */
        double labelWidth(FlatRow row);

        /** @return the row's formula, or {@code null} */
        GridModelAdapter.FormulaView formula(FlatRow row);

        /** @return the text a leaf row shows as its value (decorated, e.g. JSON quotes) */
        String leafValueText(FlatRow row);

        /** @return the one-line preview of a collapsed container, or {@code null} */
        String preview(FlatRow row);

        /** @return the user's width for the block under {@code parent} ({@code null} = top), or {@code null} */
        Double nameColumnOverride(FlatRow parent);

        /** @return the width available to embedded tables (the canvas width) */
        double availableWidth();
    }

    /**
     * The geometry of a laid-out grid.
     *
     * @param visible         the visible rows, in draw order
     * @param lastDescendant  per visible row, the index of its last visible descendant (itself if none)
     * @param index           visible row → its index
     * @param y               per visible row, its top (0 = the grid's top)
     * @param height          per visible row, its own height (0 for an expanded container)
     * @param nameColumnWidth the top block's name-column width
     * @param width           the grid's extent from x = 0 (including {@code originX})
     * @param totalHeight     the grid's height
     */
    record Result(List<FlatRow> visible, int[] lastDescendant, Map<FlatRow, Integer> index,
                  double[] y, double[] height, double nameColumnWidth, double width, double totalHeight) {
    }

    private GridBlockLayout() {
    }

    /** @return whether {@code parent} is the parent of a top-block row (the root or a cell's anchor) */
    static boolean isTop(FlatRow parent) {
        return parent == null || parent.getDepth() < 0;
    }

    static Result layout(List<FlatRow> rows, double originX, Measure m) {
        GridMetrics metrics = m.metrics();
        List<FlatRow> visible = new ArrayList<>();
        for (FlatRow row : rows) {
            if (row.isVisible()) {
                visible.add(row);
            }
        }
        int[] lastDescendant = GridCanvasView.lastDescendantIndices(visible);
        Map<FlatRow, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < visible.size(); i++) {
            index.put(visible.get(i), i);
        }

        // -- Pass 1: name-column width per block (keyed by the block's parent row; null = top) --
        Map<FlatRow, Double> blockWidths = new IdentityHashMap<>();
        for (FlatRow row : visible) {
            double w = GridMetrics.EXPAND_BAR_WIDTH + GridMetrics.ICON_AREA_WIDTH + m.labelWidth(row) + NAME_VALUE_GAP;
            blockWidths.merge(topOrParent(row), w, Math::max);
        }
        for (Map.Entry<FlatRow, Double> entry : blockWidths.entrySet()) {
            Double override = m.nameColumnOverride(entry.getKey());
            entry.setValue(override != null ? override : Math.max(MIN_NAME_COL_WIDTH, entry.getValue()));
        }
        double nameColumnWidth = blockWidths.getOrDefault(null, MIN_NAME_COL_WIDTH);

        // -- Pass 2: x positions (pre-order: a parent is laid out before its children) --
        for (FlatRow row : visible) {
            FlatRow parent = topOrParent(row);
            double labelX = parent == null ? originX : parent.getValueX();
            double blockWidth = blockWidths.getOrDefault(parent, MIN_NAME_COL_WIDTH);
            row.setCellGeometry(labelX, labelX + blockWidth);
        }

        // -- Pass 3: heights, y positions and extents --
        double[] y = new double[visible.size()];
        double[] height = new double[visible.size()];
        double currentY = 0;
        double maxWidth = originX + nameColumnWidth + MIN_VALUE_COL_WIDTH + RIGHT_MARGIN;
        for (int i = 0; i < visible.size(); i++) {
            FlatRow row = visible.get(i);
            y[i] = currentY;
            row.setValueBlock(null);
            double valueX = row.getValueX();
            maxWidth = Math.max(maxWidth, valueX + MIN_VALUE_COL_WIDTH + RIGHT_MARGIN);

            double rowHeight;
            GridModelAdapter.FormulaView formula;
            if (row.hasRepeatingTable() && row.isExpanded()) {
                // Table owner: the embedded table sits beside the name cell, at the value column
                RepeatingElementsTable table = row.getRepeatingTable();
                table.setX(valueX);
                table.setY(currentY);
                table.calculateWidth(m.availableWidth());
                rowHeight = Math.max(GridMetrics.ROW_HEIGHT, table.calculateHeight());
                maxWidth = Math.max(maxWidth, valueX + table.getWidth() + RIGHT_MARGIN);
            } else if (row.isExpandable() && row.isExpanded() && lastDescendant[i] > i) {
                // Expanded container: no own line, the name cell spans the child block
                rowHeight = 0;
            } else if ((formula = m.formula(row)) != null) {
                // Formula: the expression, then the result ("= …") below it
                TextBlock expr = TextBlock.of(metrics, formula.expression().isEmpty() ? " " : formula.expression(),
                        GridFont.ROW, metrics.wrapWidth());
                String resultText = formula.error() != null ? "Error: " + formula.error() : "= " + formula.result();
                TextBlock result = TextBlock.of(metrics, resultText, GridFont.ROW, metrics.wrapWidth());
                row.setValueBlock(expr);
                row.setResultBlock(result);
                rowHeight = expr.height() + result.lineCount() * GridMetrics.LINE_HEIGHT;
                maxWidth = Math.max(maxWidth, valueX + VALUE_PADDING
                        + Math.max(expr.width(), result.width()) + NAME_VALUE_GAP + RIGHT_MARGIN);
            } else if (row.getValue() != null) {
                String text = row.isLeafWithValue() ? m.leafValueText(row) : row.getValue();
                TextBlock block = TextBlock.of(metrics, text, GridFont.ROW, metrics.wrapWidth());
                row.setValueBlock(block);
                rowHeight = block.height();
                maxWidth = Math.max(maxWidth, valueX + VALUE_PADDING + block.width() + NAME_VALUE_GAP + RIGHT_MARGIN);
            } else if (row.isExpandable() && !row.isExpanded()) {
                // Collapsed container: one grey preview line (never wrapped)
                String preview = m.preview(row);
                if (preview != null && !preview.isEmpty()) {
                    double width = metrics.width(preview, GridFont.ROW);
                    row.setValueBlock(new TextBlock(List.of(preview), width, GridMetrics.ROW_HEIGHT));
                }
                rowHeight = GridMetrics.ROW_HEIGHT;
            } else {
                rowHeight = GridMetrics.ROW_HEIGHT;
            }
            height[i] = rowHeight;
            currentY += rowHeight;
        }
        return new Result(visible, lastDescendant, index, y, height, nameColumnWidth, maxWidth, currentY);
    }

    /** @return the row's parent, or {@code null} when the row belongs to the top block */
    static FlatRow topOrParent(FlatRow row) {
        FlatRow parent = row.getParentRow();
        return isTop(parent) ? null : parent;
    }
}
