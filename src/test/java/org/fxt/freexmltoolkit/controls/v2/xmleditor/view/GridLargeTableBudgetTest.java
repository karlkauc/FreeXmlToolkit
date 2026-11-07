package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.shell.editor.XmlGridView;
import org.fxt.freexmltoolkit.test.FxThreadBudget;
import org.fxt.freexmltoolkit.test.LargeDocuments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * FX-thread budgets for the grid on a large table (a 9 MB document, above the size from which
 * grid edits are written into the text later: one table of about 40,000 records): switching to the grid, sorting, a cell edit, a filter on the root and switching back
 * to the text. Each step did work per record or over the whole document on the FX thread before
 * (an O(n²) sort, a serialization per edit, a filter over every node, string copies of the text).
 * Budgets are FX-thread CPU time ({@link FxThreadBudget}), several times what the step takes now.
 */
@ExtendWith(ApplicationExtension.class)
class GridLargeTableBudgetTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        RepeatingElementsTable.clearAllCaches();
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    private XmlCanvasView canvas() {
        return host.lookupAll("*").stream().filter(n -> n instanceof XmlGridView g && g.isVisible())
                .map(n -> ((XmlGridView) n).getCanvasView()).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    private static FlatRow tableRow(XmlCanvasView v) {
        return v.visibleRowList().stream().filter(r -> r.getRepeatingTable() != null).findFirst().orElse(null);
    }

    /** What the grid does after a sort click or a committed cell edit (GridTableInteraction, GridInlineEditor). */
    private static void refresh(XmlCanvasView v) {
        v.rebuildTree();
        v.notifyDocumentModified();
        tableRow(v).getRepeatingTable().getHeight(); // the next paint lays the table out
    }

    @Test
    void gridStepsOnALargeTableStayWithinTheirBudgets(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("large.xml");
        Files.writeString(xml, LargeDocuments.xml(9 * 1024 * 1024));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.length() > 9_000_000).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
        // Class loading of the grid is not what the budgets guard (see EditorHostLargeDocumentBudgetTest)
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            new XmlGridView().setXml("<warm-up><a/><a/></warm-up>");
            return null;
        });

        long toGrid = FxThreadBudget.cpuUntil(() -> host.setActiveViewMode(ViewMode.GRAPHIC),
                () -> canvas() != null && tableRow(canvas()) != null
                        && tableRow(canvas()).getRepeatingTable().getHeight() > 0, 120);
        int records = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> tableRow(canvas()).getRepeatingTable().getRows().size());
        assertTrue(records > 35_000, "a large table: " + records);
        report("switch to the grid, table laid out", toGrid);
        assertTrue(toGrid <= 2_500, "switching to the grid took " + toGrid + " ms of FX-thread CPU time");

        long sort = FxThreadBudget.assertFast(1_500, 1, "sorting " + records + " records", () -> {
            XmlCanvasView v = canvas();
            v.getAdapter().sortTable(tableRow(v).getRepeatingTable(), "id", false);
            refresh(v);
        });
        report("sort", sort);
        assertEquals("21", WaitForAsyncUtils.waitForAsyncFx(5000, () -> tableRow(canvas()).getRepeatingTable()
                .getRows().get(records - 22).getValue("id")), "sorted by id, descending");

        int[] row = {3};
        long edit = FxThreadBudget.assertFast(200, 3, "a cell edit in a table of " + records + " records", () -> {
            XmlCanvasView v = canvas();
            if (!v.getAdapter().commitCellEdit(tableRow(v).getRepeatingTable(), row[0]++, "note", "edited")) {
                throw new IllegalStateException("edit refused");
            }
            refresh(v);
        });
        report("cell edit", edit);

        long filter = FxThreadBudget.assertFast(100, 1, "setting a filter on the root", () -> {
            XmlCanvasView v = canvas();
            FlatRow root = v.visibleRowList().stream().filter(r -> "orders".equals(r.getLabel())).findFirst().orElseThrow();
            v.setFilter(root, "order[@id = ('5', '9')]");
        });
        report("filter (start)", filter);
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> tableRow(canvas()).getRepeatingTable().getRows().size() == 2));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            XmlCanvasView v = canvas();
            v.setFilter(v.visibleRowList().stream().filter(r -> "orders".equals(r.getLabel())).findFirst().orElseThrow(), "");
            return null;
        });

        long toText = FxThreadBudget.cpuUntil(() -> host.setActiveViewMode(ViewMode.TEXT),
                () -> host.getActiveCodeArea() != null && host.getActiveCodeArea().isEditable()
                        && host.getActiveCodeArea().getText(0, 300).contains("<orders"), 120);
        report("switch to the text after grid edits", toText);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.getActiveCodeArea().getText()).contains("edited"),
                "the grid edits are in the text");
        assertTrue(toText <= 1_000, "switching to the text took " + toText + " ms of FX-thread CPU time");
    }

    private static void report(String step, long millis) {
        System.out.println("BUDGET " + step + ": " + millis + " ms FX-thread CPU");
    }
}
