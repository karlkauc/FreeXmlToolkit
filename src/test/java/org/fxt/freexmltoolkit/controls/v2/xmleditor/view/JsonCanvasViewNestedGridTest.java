package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** JSON: an array of objects inside a table cell is a table again, at any depth, and survives a rebuild. */
@ExtendWith(ApplicationExtension.class)
class JsonCanvasViewNestedGridTest {

    private JsonCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        JsonEditorContext context = new JsonEditorContext();
        context.loadDocumentFromString("{\"orders\": ["
                + "{\"id\": 1, \"lines\": [{\"tags\": [{\"k\": 1}, {\"k\": 2}]}, {\"tags\": [{\"k\": 3}, {\"k\": 4}]}]},"
                + "{\"id\": 2, \"lines\": [{\"tags\": [{\"k\": 5}, {\"k\": 6}]}, {\"tags\": [{\"k\": 7}, {\"k\": 8}]}]}]}");
        view = new JsonCanvasView(context);
        stage.setScene(new Scene(view, 1000, 600));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private RepeatingElementsTable orders() {
        GridCanvasView<?> grid = view;
        return grid.visibleRowList().stream().filter(FlatRow::hasRepeatingTable).findFirst().orElseThrow()
                .getRepeatingTable();
    }

    @Test
    void tablesOfObjectsNestInCellsAndComeBackAfterARebuild() {
        fx(() -> {
            view.expandAll();
            return null;
        });
        RepeatingElementsTable orders = fx(this::orders);
        GridBlock lines = fx(() -> {
            orders.getRows().getFirst().toggleColumnExpanded("lines");
            return orders.getRows().getFirst().getCellGrid("lines");
        });
        FlatRow linesOwner = lines.rows().getFirst();
        assertTrue(linesOwner.hasRepeatingTable(), "the lines are a table");
        RepeatingElementsTable lineTable = fx(linesOwner::getRepeatingTable);
        assertEquals(2, fx(() -> lineTable.getRows().size()));

        GridBlock tags = fx(() -> {
            lineTable.getRows().get(1).toggleColumnExpanded("tags");
            return lineTable.getRows().get(1).getCellGrid("tags");
        });
        RepeatingElementsTable tagTable = fx(() -> tags.rows().getFirst().getRepeatingTable());
        assertEquals(2, fx(() -> tagTable.getRows().size()));
        assertEquals("3", fx(() -> tagTable.getRows().getFirst().getValue("k")));

        fx(() -> {
            view.rebuild();
            return null;
        });
        RepeatingElementsTable orders2 = fx(this::orders);
        GridBlock lines2 = fx(() -> orders2.getRows().getFirst().getCellGrid("lines"));
        assertNotNull(lines2, "the outer cell is open again");
        GridBlock tags2 = fx(() -> lines2.rows().getFirst().getRepeatingTable().getRows().get(1).getCellGrid("tags"));
        assertNotNull(tags2, "the inner cell is open again");
        fx(() -> {
            view.render();
            return null;
        });
    }

    @Test
    void itemsInsideACellAreMovedByDragAndDrop() {
        fx(() -> {
            view.expandAll();
            orders().getRows().getFirst().toggleColumnExpanded("lines");
            view.rebuild();
            GridBlock lines = orders().getRows().getFirst().getCellGrid("lines");
            view.toggleTableDisplay(lines.rows().getFirst()); // the lines as rows
            return null;
        });
        GridBlock lines = fx(() -> orders().getRows().getFirst().getCellGrid("lines"));
        // As rows, the items hang directly off the cell's anchor
        java.util.List<FlatRow> items = lines.rows().stream()
                .filter(r -> r.getParentRow() != null && r.getParentRow().getDepth() < 0)
                .toList();
        assertEquals(2, items.size(), "the two line items as rows");

        assertTrue(fx(() -> view.getAdapter().dropRow(items.get(1), items.get(0), DropPosition.BEFORE, false)));
        fx(() -> {
            view.rebuild();
            return null;
        });
        GridBlock after = fx(() -> orders().getRows().getFirst().getCellGrid("lines"));
        assertNotNull(after, "the cell stays open after the drop");
        FlatRow firstItem = after.rows().stream()
                .filter(r -> r.getParentRow() != null && r.getParentRow().getDepth() < 0).findFirst().orElseThrow();
        GridBlock tagsOfFirst = fx(() -> {
            firstItem.setExpanded(true);
            return after;
        });
        assertTrue(tagsOfFirst.rows().stream().anyMatch(r -> r.getParentRow() == firstItem));
        assertTrue(fx(() -> view.getAdapter().dragText(firstItem)).contains("\"k\": 3"),
                "the former second item (tags 3, 4) is first now");
    }
}
