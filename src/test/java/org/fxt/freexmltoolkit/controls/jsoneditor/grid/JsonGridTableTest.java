package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Arrays of objects render as embedded tables in the JSON grid (like repeating elements in
 * the XML grid): columns are the merged keys, sorting reorders the model and round-trips.
 */
@ExtendWith(ApplicationExtension.class)
class JsonGridTableTest {

    private static final String JSON = """
            {"rows": [{"id": 2, "n": "b"}, {"id": 1, "n": "a", "extra": {"x": 1}}, {"id": 3, "n": "c"}]}
            """;

    private JsonCanvasView view;
    private JsonEditorContext context;
    private String roundTripped;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        context = new JsonEditorContext();
        context.loadDocumentFromString(JSON);
        view = new JsonCanvasView(context);
        view.setOnDocumentModified(json -> roundTripped = json);
        stage.setScene(new Scene(view, 800, 600));
        stage.show();
    }

    private RepeatingElementsTable rowsTable() {
        var rows = JsonGridAdapter.flatten(context.getDocument());
        new JsonGridAdapter(context).attachTables(rows, () -> { });
        return rows.stream().filter(r -> "rows".equals(r.getLabel())).findFirst().orElseThrow().getRepeatingTable();
    }

    @Test
    void arrayOfObjectsBecomesAnEmbeddedTable() {
        WaitForAsyncUtils.waitForFxEvents();
        RepeatingElementsTable table = rowsTable();
        assertNotNull(table);
        assertEquals(List.of("id", "n", "extra"),
                table.getColumns().stream().map(RepeatingElementsTable.TableColumn::getName).toList());
        assertEquals(3, table.getRows().size());
        assertTrue(table.getRows().get(1).hasComplexChild("extra"));
        assertFalse(table.isColumnSortable("extra"), "nested cells are not sortable");
        assertTrue(table.isColumnSortable("id"));
        assertEquals(RepeatingElementsTable.ColumnDataType.NUMERIC, table.detectColumnDataType("id"));
    }

    @Test
    void sortingReordersTheModelAndRoundTrips() {
        WaitForAsyncUtils.waitForFxEvents();
        RepeatingElementsTable table = rowsTable();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.getAdapter().sortTable(table, "id", true);
            view.refresh();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        JsonArray rows = (JsonArray) ((JsonObject) context.getDocument().getRootValue()).getProperty("rows");
        assertEquals("1", ((JsonPrimitive) ((JsonObject) rows.get(0)).getProperty("id")).getAsString());
        assertEquals("3", ((JsonPrimitive) ((JsonObject) rows.get(2)).getProperty("id")).getAsString());
        assertNotNull(roundTripped, "the grid pushed the serialized document to the round-trip callback");
        assertTrue(roundTripped.indexOf("\"id\": 1") < roundTripped.indexOf("\"id\": 2"));
        assertTrue(context.canUndo());
    }

    @Test
    void selectingTheArrayRowThenExpandingKeepsTheTable() {
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.expandAll();
            return null;
        });
        JsonArray rows = (JsonArray) ((JsonObject) context.getDocument().getRootValue()).getProperty("rows");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setSelectedNode(rows);
            return null;
        });
        assertSame(rows, view.getSelectedNode());
        assertEquals(1, WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.findAll("rows")));
    }
}
