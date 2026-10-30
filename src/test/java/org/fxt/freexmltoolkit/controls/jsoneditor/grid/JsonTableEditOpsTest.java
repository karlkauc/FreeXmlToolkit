package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TableEditOps.Placement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Table submenu of the JSON grid: rows and columns of an array of objects. */
@ExtendWith(ApplicationExtension.class)
class JsonTableEditOpsTest {

    private JsonEditorContext context;
    private RepeatingElementsTable table;
    private Pane anchor;

    @Start
    void start(Stage stage) {
        anchor = new Pane();
        stage.setScene(new Scene(anchor, 200, 200));
        stage.show();
    }

    private void load(String json) {
        RepeatingElementsTable.clearAllCaches();
        context = new JsonEditorContext();
        context.loadDocumentFromString(json);
        JsonGridAdapter adapter = new JsonGridAdapter(context);
        List<FlatRow> rows = adapter.flatten();
        adapter.attachTables(rows, () -> { });
        table = rows.stream().filter(FlatRow::hasRepeatingTable).findFirst().orElseThrow().getRepeatingTable();
    }

    private JsonArray people() {
        return (JsonArray) ((JsonObject) context.getDocument().getRootValue()).getProperty("people");
    }

    private List<String> keys(JsonNode object) {
        return object.getChildren().stream().map(JsonNode::getKey).toList();
    }

    @Test
    void insertedRowsHaveEveryColumn() {
        load("{\"people\": [{\"a\": 1}, {\"a\": 2, \"b\": 3}]}");
        assertTrue(context.executeCommand(JsonTableEditOps.insertRow(table, 0, Placement.BEFORE)));
        assertEquals(3, people().size());
        assertEquals(List.of("a", "b"), keys(people().get(0)));
        assertTrue(context.executeCommand(JsonTableEditOps.insertRow(table, 0, Placement.APPEND)));
        assertEquals(List.of("a", "b"), keys(people().get(3)));
    }

    @Test
    void insertedColumnLandsNextToTheReferenceInEveryRow() {
        load("{\"people\": [{\"a\": 1, \"c\": 0}, {\"c\": 2}]}");
        assertTrue(context.executeCommand(JsonTableEditOps.insertColumn(table, "b", "a", Placement.AFTER)));
        assertEquals(List.of("a", "b", "c"), keys(people().get(0)));
        assertEquals(List.of("c", "b"), keys(people().get(1)), "appended where the reference is missing");
        context.undo();
        assertEquals(List.of("c"), keys(people().get(1)), "one undo step");
    }

    @Test
    void renameColumnIsAllOrNothing() {
        load("{\"people\": [{\"a\": 1}, {\"a\": 2, \"b\": 3}]}");
        assertTrue(context.executeCommand(JsonTableEditOps.renameColumn(table, "a", "id")));
        assertEquals(List.of("id"), keys(people().get(0)));
        context.undo();

        assertFalse(context.executeCommand(JsonTableEditOps.renameColumn(table, "a", "b")), "b is taken in row 2");
        assertEquals(List.of("a"), keys(people().get(0)), "the first row is rolled back");
    }

    @Test
    void tableSubmenuShowsForATableCellAndAppendsARow() {
        load("{\"people\": [{\"a\": 1}, {\"a\": 2}]}");
        int[] refreshes = {0};
        JsonGridContextMenu menu = new JsonGridContextMenu(context, () -> refreshes[0]++);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            menu.show(anchor, 10, 10, people().get(0), table, 0, "a");
            return null;
        });
        assertTrue(menu.tableMenuForTest().isVisible());
        MenuItem append = menu.tableMenuForTest().getItems().stream()
                .filter(i -> "Append Row".equals(i.getText())).findFirst().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            menu.hide();
            append.fire();
            return null;
        });
        assertEquals(3, people().size());
        assertEquals(1, refreshes[0]);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            menu.show(anchor, 10, 10, people().get(0));
            menu.hide();
            return null;
        });
        assertFalse(menu.tableMenuForTest().isVisible(), "no table context, no Table submenu");
    }
}
