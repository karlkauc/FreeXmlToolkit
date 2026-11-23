package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;

/**
 * Where things are on a grid canvas, for the tutorial video scenarios (they live in the
 * {@code screenshots} package; the canvas geometry is package-private here). Every method returns
 * screen coordinates and must be called on the FX thread.
 */
public final class GridVideoProbe {

    private GridVideoProbe() {
    }

    /** @return the XML grid canvas showing below {@code root} */
    public static XmlCanvasView view(Node root) {
        return root.lookupAll("*").stream()
                .filter(n -> n instanceof XmlCanvasView && n.isVisible() && n.getScene() != null)
                .map(XmlCanvasView.class::cast).findFirst().orElseThrow();
    }

    /** @return the whole canvas */
    public static Bounds canvas(XmlCanvasView view) {
        return view.canvas.localToScreen(view.canvas.getBoundsInLocal());
    }

    /** @return the first table of repeating elements in the grid, header row included */
    public static Bounds table(XmlCanvasView view) {
        RepeatingElementsTable table = firstTable(view);
        int rows = table.drawnRowCount();
        int cols = table.drawnColumnCount();
        return screen(view, table, table.drawnColumnX(0), 0,
                table.drawnColumnX(cols - 1) + table.drawnColumnWidth(cols - 1) - table.drawnColumnX(0),
                table.drawnRowTop(rows - 1) + table.drawnRowHeight(rows - 1));
    }

    /** @return the data cell of a record (0-based) in the column whose name ends with {@code column} */
    public static Bounds cell(XmlCanvasView view, int row, String column) {
        RepeatingElementsTable table = firstTable(view);
        RepeatingElementsTable.CellRect r = table.cellRect(row, columnIndex(table, column));
        return screen(view, table, r.x(), r.y(), r.width(), r.height());
    }

    /** @return the sort arrow at the right of a column header */
    public static Bounds sortArrow(XmlCanvasView view, String column) {
        RepeatingElementsTable table = firstTable(view);
        int dc = columnIndex(table, column) + 1;
        double right = table.drawnColumnX(dc) + table.drawnColumnWidth(dc);
        return screen(view, table, right - RepeatingElementsTable.SORT_ZONE, table.drawnRowTop(0),
                RepeatingElementsTable.SORT_ZONE, table.drawnRowHeight(0));
    }

    private static RepeatingElementsTable firstTable(XmlCanvasView view) {
        return view.visibleRowList().stream().filter(FlatRow::hasRepeatingTable)
                .map(FlatRow::getRepeatingTable).findFirst().orElseThrow();
    }

    private static int columnIndex(RepeatingElementsTable table, String column) {
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (table.getColumns().get(i).getName().endsWith(column)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No column " + column + " in " + table.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName).toList());
    }

    /** Model rectangle (x absolute, y relative to the table top) → screen. */
    private static Bounds screen(XmlCanvasView view, RepeatingElementsTable table,
                                 double x, double y, double width, double height) {
        double[] system = view.rowOrigin(table.getOwnerRow());
        if (system == null) {
            throw new IllegalStateException("The table is not laid out");
        }
        double zoom = view.getZoom();
        return view.canvas.localToScreen(new BoundingBox((system[0] + x - view.scrollOffsetX) * zoom,
                (system[1] + y) * zoom, width * zoom, height * zoom));
    }
}
