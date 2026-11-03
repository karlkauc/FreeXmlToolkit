package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.shell.editor.XmlGridView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A large grid in the shell: when a table of the root grid grows because its cells open (by a
 * click, restored after a rebuild, or from inside a cell), the grid's scroll range follows. Before,
 * a root table had no one to tell, so the grid ran behind the Properties pane while the scroll bars
 * stopped at the old size and the vertical one did not appear.
 */
@ExtendWith(ApplicationExtension.class)
class ShellGridScrollRangeTest {

    @TempDir
    Path tmp;

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1200, 720));
        stage.show();
    }

    private static String document() {
        StringBuilder sb = new StringBuilder("<Root><Items>");
        for (int i = 0; i < 8; i++) {
            sb.append("<Item a=\"value-").append(i).append("-a\" b=\"value-").append(i).append("-b\"><Name>n").append(i)
                    .append("</Name>");
            for (int p = 0; p < 4; p++) {
                sb.append("<Position nr=\"").append(p).append("\" isin=\"AT000000000").append(p)
                        .append("\" description=\"A long position description number ").append(p)
                        .append("\"><Detail>detail ").append(p).append("</Detail></Position>");
            }
            sb.append("</Item>");
        }
        return sb.append("</Items></Root>").toString();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private XmlCanvasView openGrid() throws Exception {
        Path xml = tmp.resolve("wide.xml");
        Files.writeString(xml, document());
        fx(() -> {
            shell.openFile(xml);
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("Position")).orElse(false));
        fx(() -> {
            shell.getEditorHost().setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() -> shell.lookup(".fxt-xml-grid") != null));
        XmlCanvasView canvas = fx(() -> ((XmlGridView) shell.lookup(".fxt-xml-grid")).getCanvasView());
        assertNotNull(canvas);
        fx(() -> {
            canvas.expandAll();
            return null;
        });
        return canvas;
    }

    private RepeatingElementsTable itemTable(XmlCanvasView canvas) {
        return fx(() -> canvas.visibleRowList().stream()
                .filter(r -> r.hasRepeatingTable() && "Item".equals(r.getLabel()))
                .findFirst().orElseThrow().getRepeatingTable());
    }

    /** Opens every Position cell the way a restore after a rebuild does: on the table, not by a click. */
    private void openAllCells(XmlCanvasView canvas, RepeatingElementsTable table) {
        fx(() -> {
            for (RepeatingElementsTable.TableRow row : table.getRows()) {
                if (!row.isColumnExpanded("Position")) {
                    row.toggleColumnExpanded("Position");
                }
            }
            canvas.render();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void theScrollRangeFollowsATableWhoseCellsOpen() throws Exception {
        XmlCanvasView canvas = openGrid();
        RepeatingElementsTable table = itemTable(canvas);
        openAllCells(canvas, table);

        double right = fx(() -> table.getX() + table.getWidth());
        double bottom = fx(() -> table.getY() + table.getHeight());
        assertTrue(fx(canvas::contentWidth) >= right, "scroll width covers the table: " + right);
        assertTrue(fx(canvas::contentHeight) >= bottom, "scroll height covers the table: " + bottom);
        assertTrue(fx(() -> canvas.scrollBarsShown()[0]), "the vertical scroll bar appears");
        assertFalse(fx(canvas::relayoutPendingForTest), "the re-layout settles instead of repeating");
    }

    @Test
    void atTheScrollEndsTheLastColumnAndRowAreVisible() throws Exception {
        XmlCanvasView canvas = openGrid();
        RepeatingElementsTable table = itemTable(canvas);
        openAllCells(canvas, table);
        ScrollBar[] bars = fx(canvas::scrollBarsForTest);

        fx(() -> {
            bars[1].setValue(bars[1].getMax());
            bars[0].setValue(bars[0].getMax());
            return null;
        });

        double right = fx(() -> table.getX() + table.getWidth());
        double bottom = fx(() -> table.getY() + table.getHeight());
        double viewW = fx(() -> canvas.canvasNode().getWidth() / canvas.getZoom());
        double viewH = fx(() -> canvas.canvasNode().getHeight() / canvas.getZoom());
        assertTrue(right - fx(canvas::scrollOffsetXValue) <= viewW + 0.5, "the table's right edge is reachable");
        assertTrue(bottom - fx(canvas::scrollOffsetYValue) <= viewH + 0.5, "the table's bottom is reachable");
    }

    @Test
    void theGridAndItsScrollBarsStayLeftOfTheProperties() throws Exception {
        XmlCanvasView canvas = openGrid();
        openAllCells(canvas, itemTable(canvas));
        Node inspector = fx(() -> shell.lookup("#inspectorWrapper"));
        double inspectorLeft = fx(() -> inspector.localToScene(inspector.getLayoutBounds()).getMinX());

        for (ScrollBar bar : fx(canvas::scrollBarsForTest)) {
            double barRight = fx(() -> bar.localToScene(bar.getLayoutBounds()).getMaxX());
            assertTrue(barRight <= inspectorLeft + 0.5, "scroll bar ends at " + barRight + " ≤ " + inspectorLeft);
        }
        // Cause guard: no container between the grid and the editor column demands more width than it has
        Node node = canvas;
        while (node != null && !"editorCenter".equals(node.getId())) {
            Node current = node;
            if (current instanceof Region region) {
                assertTrue(fx(() -> region.minWidth(-1) <= region.getWidth() + 0.5),
                        current.getClass().getSimpleName() + " min width exceeds its width");
            }
            node = fx(current::getParent);
        }
    }
}
