package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.XmlGridView;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Sorting a large table runs on a worker thread: the grid stays usable, the result is one undoable
 * command, the table's rows are put into the new order without a rebuild, and an edit made while
 * the order was computed makes it compute again instead of applying a stale order.
 */
@ExtendWith(ApplicationExtension.class)
class GridBackgroundSortTest {

    private XmlGridView grid;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        RepeatingElementsTable.clearAllCaches();
        grid = new XmlGridView();
        stage.setScene(new Scene(grid, 900, 600));
        stage.show();
    }

    @AfterEach
    void threshold() {
        GridTableInteraction.backgroundSortRows = GridTableInteraction.BACKGROUND_SORT_ROWS;
    }

    private static String document(int n) {
        StringBuilder xml = new StringBuilder("<orders>");
        for (int i = 0; i < n; i++) {
            xml.append("<order id=\"").append((i * 7) % n).append("\"><note>n").append(i).append("</note></order>");
        }
        return xml.append("</orders>").toString();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        return WaitForAsyncUtils.waitForAsyncFx(10_000, action);
    }

    private RepeatingElementsTable table() {
        return grid.getCanvasView().visibleRowList().stream().filter(r -> r.getRepeatingTable() != null)
                .findFirst().orElseThrow().getRepeatingTable();
    }

    private List<String> ids() {
        return table().getRows().stream().map(r -> r.getValue("id")).toList();
    }

    @Test
    void largeTablesAreSortedInTheBackgroundAndReorderedInPlace() throws Exception {
        GridTableInteraction.backgroundSortRows = 10;
        fx(() -> {
            grid.setXml(document(50));
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() -> table().getHeight() > 0));
        RepeatingElementsTable before = fx(this::table);
        int rebuilds = fx(() -> grid.getCanvasView().rebuildCount);

        fx(() -> {
            grid.getCanvasView().sortTable(table(), "id", false);
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !fx(() -> grid.getCanvasView().isSorting()));
        List<String> ids = fx(this::ids);
        assertEquals("49", ids.getFirst());
        assertEquals("0", ids.getLast());
        assertTrue(before == fx(this::table), "the same table, its rows reordered");
        assertEquals(rebuilds, (int) fx(() -> grid.getCanvasView().rebuildCount), "no rebuild");
        List<String> modelIds = fx(() -> grid.getContext().getDocument().getRootElement().getChildElements().stream()
                .map(e -> e.getAttribute("id")).toList());
        assertEquals(ids, modelIds, "the model has the order the table shows");
        assertTrue(fx(() -> table().isSortedBy("id") && !table().isSortAscending()));

        // one undo step restores the document order
        fx(() -> grid.getContext().undo());
        assertEquals("0", fx(() -> grid.getContext().getDocument().getRootElement().getChildElements().getFirst()
                .getAttribute("id")));
    }

    @Test
    void anEditWhileSortingMakesTheOrderComputeAgain() throws Exception {
        fx(() -> {
            grid.setXml(document(30));
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> fx(() -> table().getHeight() > 0));
        var adapter = fx(() -> grid.getCanvasView().getAdapter());
        var job = fx(() -> adapter.prepareSort(table(), "id", true));
        var apply = job.call(); // the worker's part
        fx(() -> {
            XmlElement root = grid.getContext().getDocument().getRootElement();
            return grid.getContext().executeCommand(new RenameNodeCommand(root, "orders2"));
        });
        assertEquals(GridModelAdapter.SortOutcome.STALE, fx(apply::get));
        assertEquals("0", fx(() -> grid.getContext().getDocument().getRootElement().getChildElements().getFirst()
                .getAttribute("id")), "nothing applied");
    }
}
