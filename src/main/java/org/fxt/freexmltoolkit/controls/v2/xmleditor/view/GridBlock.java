package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.List;

/**
 * The content of an opened table cell, shown as a grid of its own: the same rows, blocks, embedded
 * tables and opened cells as the root grid, laid out by {@link GridBlockLayout} in the cell's local
 * coordinates (x = 0 at the cell's content left, y = 0 below the cell's summary line). A table inside
 * the block may open cells again, so blocks nest to any depth.
 *
 * <p>Coordinates: everything a block lays out — its rows' {@code labelX}/{@code valueX}, the
 * {@code x}/{@code y} of embedded tables — is local to the block. Only drawing (by translating),
 * hit-testing and editor placement convert between a block and the canvas.
 *
 * <p>Invalidation runs upward only: a change inside the block marks its geometry stale and then the
 * host table row, whose table invalidates its owner's block, and so on up to the root.
 */
public final class GridBlock {

    private final List<FlatRow> rows;
    private final RepeatingElementsTable.TableRow host;
    private final String column;
    private GridBlockLayout.Result geometry;

    /**
     * @param rows the block's rows (the opened cell's content; the top rows hang off a virtual anchor)
     * @param host   the table row whose cell this block is (its layout is invalidated with the block's)
     * @param column the opened column of that row
     */
    GridBlock(List<FlatRow> rows, RepeatingElementsTable.TableRow host, String column) {
        this.rows = rows;
        this.host = host;
        this.column = column;
        for (FlatRow row : rows) {
            row.setBlock(this);
            FlatRow parent = row.getParentRow();
            if (parent != null && parent.getDepth() < 0) {
                parent.setBlock(this); // the virtual anchor: identifies the cell's top block
            }
        }
    }

    /** @return every row of the block, visible or not, in display order */
    public List<FlatRow> rows() {
        return rows;
    }

    /** @return the column of the host row whose cell this block is */
    public String column() {
        return column;
    }

    /** @return the table row whose cell this block is */
    public RepeatingElementsTable.TableRow host() {
        return host;
    }

    /** @return the geometry from the last layout, laying out first when stale */
    GridBlockLayout.Result geometry(GridBlockLayout.Measure measure) {
        if (geometry == null) {
            geometry = GridBlockLayout.layout(rows, 0, measure);
        }
        return geometry;
    }

    /** @return the geometry from the last layout, or {@code null} when stale */
    GridBlockLayout.Result geometry() {
        return geometry;
    }

    /** Marks the block's layout stale, and with it the host cell's row and everything above. */
    public void invalidate() {
        geometry = null;
        if (host != null) {
            host.invalidateLayout();
        }
    }

    /**
     * @param x block-local x
     * @param y block-local y
     * @return the index (into the visible rows) of the row whose cell contains the point: the row at
     *         that height, or — left of its name cell — the ancestor whose name cell spans there;
     *         {@code -1} outside the block or before the first layout
     */
    int rowIndexAt(double x, double y) {
        GridBlockLayout.Result g = geometry;
        if (g == null || g.visible().isEmpty() || y < 0 || y >= g.totalHeight()) {
            return -1;
        }
        int lo = 0;
        int hi = g.visible().size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (g.y()[mid] <= y) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (found < 0) {
            return -1;
        }
        // Rows of height 0 (expanded containers) start where their first child starts: take the
        // deepest row at that y, then walk out to the row whose name cell lies under x.
        FlatRow row = g.visible().get(found);
        while (row != null && x < row.getLabelX()) {
            FlatRow parent = row.getParentRow();
            Integer index = parent == null ? null : g.index().get(parent);
            if (index == null) {
                return found;
            }
            found = index;
            row = parent;
        }
        return found;
    }
}
