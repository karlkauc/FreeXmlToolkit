package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

/** The JSON grid shares the column selection and "+" on tables of objects. */
@ExtendWith(ApplicationExtension.class)
class JsonCanvasViewCellExpandTest {

    private JsonCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        JsonEditorContext context = new JsonEditorContext();
        context.loadDocumentFromString("{\"people\": ["
                + "{\"name\": \"a\", \"address\": {\"city\": \"X\", \"geo\": {\"lat\": 1}}},"
                + "{\"name\": \"b\", \"address\": {\"city\": \"Y\", \"geo\": {\"lat\": 2}}}]}");
        view = new JsonCanvasView(context);
        stage.setScene(new Scene(view, 1000, 600));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void aSelectedColumnOpensInEveryRow() {
        fx(view::expandAll);
        GridCanvasView<?> grid = view; // the test hooks are package-private in GridCanvasView
        FlatRow owner = grid.visibleRowList().stream().filter(FlatRow::hasRepeatingTable).findFirst().orElseThrow();
        RepeatingElementsTable table = owner.getRepeatingTable();
        RepeatingElementsTable.CellRect cell = table.cellRect(0, table.getColumns().indexOf(table.getColumn("address")));
        double x = cell.x() + 10;
        double y = grid.rowTopAt(grid.visibleRowList().indexOf(owner)) + RepeatingElementsTable.ROW_HEIGHT / 2;
        fx(() -> Event.fireEvent(grid.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                MouseButton.PRIMARY, 1, false, false, false, false,
                false, false, false, true, false, true, null)));
        fx(() -> Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_PRESSED, "+", "+", KeyCode.ADD,
                false, false, false, false)));
        assertTrue(table.getRows().get(0).isColumnExpanded("address"));
        assertTrue(table.getRows().get(1).isColumnExpanded("address"));
        assertNull(table.getSortedColumnName());
    }
}
