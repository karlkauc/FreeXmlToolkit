package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** JSONPath filters in the JSON grid: the shared filter machinery on JSON objects and arrays. */
@ExtendWith(ApplicationExtension.class)
class JsonCanvasViewFilterTest {

    private JsonCanvasView view;
    private JsonEditorContext context;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        context = new JsonEditorContext();
        context.loadDocumentFromString("""
                {"meta": {"v": 1},
                 "people": [{"name": "Ann", "age": 30}, {"name": "Bob", "age": 12}, {"name": "Cid", "age": 50}]}
                """);
        view = new JsonCanvasView(context);
        stage.setScene(new Scene(view, 1000, 600));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> c) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, c);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    @Test
    void aJsonPathFilterReducesRowsAndTables() {
        GridCanvasView<?> grid = view;
        FlatRow root = fx(() -> grid.visibleRowList().get(0));
        fx(() -> {
            grid.setFilter(root, "$.people[?(@.age > 20)]");
            return null;
        });
        List<FlatRow> rows = fx(() -> List.copyOf(grid.visibleRowList()));
        assertTrue(rows.stream().anyMatch(r -> r.getType() == FlatRow.RowType.FILTER), "the funnel line shows the filter");
        assertTrue(rows.stream().noneMatch(r -> "meta".equals(r.getLabel())), "unmatched members are hidden");
        FlatRow table = rows.stream().filter(FlatRow::isTableGroupOwner).findFirst().orElseThrow();
        JsonArray people = (JsonArray) ((JsonObject) context.getDocument().getRootValue()).getProperty("people");
        assertEquals(List.of(people.get(0), people.get(2)), table.getTableMembers(), "the table keeps the matching rows");
    }

    @Test
    void filterIsOfferedOnJsonContainersAndValueRowsSuggestAnExpression() {
        GridCanvasView<?> grid = view;
        fx(() -> {
            grid.expandAll();
            return null;
        });
        FlatRow meta = fx(() -> grid.visibleRowList().stream().filter(r -> "meta".equals(r.getLabel())).findFirst()
                .orElseThrow());
        fx(() -> grid.selectRowMatching(r -> r == meta));
        assertTrue(fx(grid::canFilterSelection), "an object can carry a filter");
        FlatRow v = fx(() -> grid.visibleRowList().stream().filter(r -> "v".equals(r.getLabel())).findFirst()
                .orElseThrow());
        fx(() -> grid.selectRowMatching(r -> r == v));
        assertTrue(fx(grid::canFilterSelection), "a value row filters its object's holder");
        FlatRow target = fx(grid::prepareFilterTarget);
        assertEquals("$[?(@['v'] == 1)]", fx(() -> grid.initialFilterText(target)));
    }
}
