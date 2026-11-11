package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Sorting a table column in the shell's grid keeps every expanded row expanded and the selection,
 * the sorted table's too: its state was keyed by its first member, which the sort replaced.
 */
@ExtendWith(ApplicationExtension.class)
class GridSortKeepsExpansionTest {

    private static final String XML = """
            <root>
              <header><title>T</title><meta><a>1</a><b>2</b></meta></header>
              <section>
                <orders>
                  <order><id>3</id><note>c</note></order>
                  <order><id>1</id><note>a</note></order>
                  <order><id>2</id><note>b</note></order>
                </orders>
              </section>
            </root>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        return WaitForAsyncUtils.waitForAsyncFx(5000, action);
    }

    private XmlCanvasView canvas() throws Exception {
        return fx(() -> (XmlCanvasView) host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlCanvasView).findFirst().orElseThrow());
    }

    private static List<String> expanded(XmlCanvasView c) {
        return c.visibleRowList().stream().filter(FlatRow::isExpandable)
                .map(r -> c.keyOf(r) + "=" + r.isExpanded()).toList();
    }

    @Test
    void sortingAColumnKeepsTheExpandedRowsAndTheSelection(@TempDir Path tmp) throws Exception {
        Path xml = Files.writeString(tmp.resolve("doc.xml"), XML);
        fx(() -> { host.openFile(xml); return null; });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("orders")).orElse(false));
        fx(() -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        XmlCanvasView before = canvas();
        fx(() -> {
            before.expandAll();
            return null;
        });
        List<String> expandedBefore = fx(() -> expanded(before));

        FlatRow tableRow = fx(() -> before.visibleRowList().stream().filter(r -> r.getRepeatingTable() != null)
                .findFirst().orElseThrow());
        RepeatingElementsTable table = tableRow.getRepeatingTable();
        fx(() -> {
            before.selectRow(tableRow);
            return null;
        });
        fx(() -> {
            before.sortTable(table, "id", true);
            return null;
        });
        WaitForAsyncUtils.sleep(1500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        XmlCanvasView after = canvas();
        List<String> expandedAfter = fx(() -> expanded(after));
        assertTrue(host.getActiveText().orElseThrow().indexOf("<id>1</id>")
                < host.getActiveText().orElseThrow().indexOf("<id>3</id>"), "sorted");
        assertEquals(expandedBefore, expandedAfter);
        FlatRow selected = fx(after::getSelectedRow);
        assertNotNull(selected, "the selected table row stays selected");
        assertEquals(tableRow.getTableGroupKey(), selected.getTableGroupKey());
    }
}
