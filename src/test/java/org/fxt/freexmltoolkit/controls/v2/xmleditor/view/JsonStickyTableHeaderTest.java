package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * JSON grid: an array of objects shown as a table keeps its header pinned while scrolling, also
 * a table inside an opened cell (stacked below the outer one), and the pinned header takes clicks.
 */
@ExtendWith(ApplicationExtension.class)
class JsonStickyTableHeaderTest {

    private JsonCanvasView view;
    private GridCanvasView<?> grid;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        StringBuilder json = new StringBuilder("{\"orders\": [");
        for (int i = 0; i < 3; i++) {
            json.append(i > 0 ? "," : "").append("{\"id\": ").append(i).append(", \"lines\": [");
            for (int j = 0; j < 150; j++) {
                json.append(j > 0 ? "," : "").append("{\"pos\": ").append(j).append(", \"amount\": ").append(j * 10).append('}');
            }
            json.append("]}");
        }
        json.append("]}");
        JsonEditorContext context = new JsonEditorContext();
        context.loadDocumentFromString(json.toString());
        view = new JsonCanvasView(context);
        grid = view;
        stage.setScene(new Scene(view, 1000, 600));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private FlatRow ordersOwner() {
        return grid.visibleRowList().stream().filter(FlatRow::hasRepeatingTable).findFirst().orElseThrow();
    }

    private void click(double x, double y) {
        fx(() -> {
            Event.fireEvent(grid.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, true, false, true, null));
            return null;
        });
    }

    @Test
    void headersOfOuterAndInnerTablesStickAndTakeClicks() {
        double[] geo = fx(() -> {
            grid.expandAll();
            FlatRow owner = ordersOwner();
            RepeatingElementsTable orders = owner.getRepeatingTable();
            RepeatingElementsTable.TableRow row = orders.getRows().getFirst();
            row.toggleColumnExpanded("lines");
            grid.rebuild();
            owner = ordersOwner();
            orders = owner.getRepeatingTable();
            row = orders.getRows().getFirst();
            double top = grid.rowTopAt(grid.visibleRowList().indexOf(owner));
            RepeatingElementsTable.CellRect rect = orders.cellRect(0, orders.getColumns().indexOf(orders.getColumn("lines")));
            double gx = rect.x() + RepeatingElementsTable.CELL_PADDING;
            double gy = top + rect.y() + orders.getCellLayout(row, "lines").summaryHeight();
            GridBlock cell = row.getCellGrid("lines");
            FlatRow innerOwner = cell.rows().stream().filter(FlatRow::hasRepeatingTable).findFirst().orElseThrow();
            return new double[] {gx, gy + cell.geometry().y()[cell.geometry().index().get(innerOwner)],
                    orders.drawnRowHeight(0)};
        });
        RepeatingElementsTable orders = fx(() -> ordersOwner().getRepeatingTable());
        RepeatingElementsTable lines = fx(() -> orders.getRows().getFirst().getCellGrid("lines").rows().stream()
                .filter(FlatRow::hasRepeatingTable).findFirst().orElseThrow().getRepeatingTable());

        fx(() -> {
            grid.render();
            return null;
        });
        assertFalse(grid.pinnedHeaders.containsKey(orders), "nothing pinned at the top");

        fx(() -> {
            grid.scrollToForTest(geo[1] + lines.drawnRowTop(70));
            grid.render();
            return null;
        });
        double sticky = fx(() -> grid.scrollHeaderRows().isEmpty() ? 0 : GridCanvasView.SCROLL_HEADER_HEIGHT);
        assertEquals(sticky, grid.pinnedHeaders.get(orders), 0.001, "outer header pinned below the scroll header");
        assertNotNull(grid.pinnedHeaders.get(lines), "inner header pinned too");

        // Click on the pinned inner header's first column: selects that column
        click(geo[0] + lines.drawnColumnX(1) + 8, sticky + geo[2] + lines.drawnRowHeight(0) / 2);
        assertTrue(grid.tables.isWholeColumnSelected(lines, 0), "click on the pinned inner header selects its column");
    }
}
