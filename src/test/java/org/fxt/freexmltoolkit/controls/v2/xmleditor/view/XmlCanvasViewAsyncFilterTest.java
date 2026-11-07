package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A filter over a large container is evaluated in the background; the grid follows when it is done. */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewAsyncFilterTest {

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        StringBuilder xml = new StringBuilder("<Orders>");
        for (int i = 0; i < AsyncFormulas.ASYNC_NODES; i++) {
            xml.append("<Order id=\"").append(i).append("\"/>");
        }
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml.append("</Orders>").toString());
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 900, 500));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        return WaitForAsyncUtils.waitForAsyncFx(10_000, action);
    }

    private FlatRow row(String label) {
        return view.visibleRowList().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    private String filterLine() {
        return view.visibleRowList().stream().filter(r -> r.getType() == FlatRow.RowType.FILTER)
                .map(FlatRow::getValue).findFirst().orElse(null);
    }

    @Test
    void theFilterLineSaysEvaluatingUntilTheBackgroundResultFiltersTheTable() throws Exception {
        String expression = "Order[@id = ('3', '7')]";
        fx(() -> {
            view.expandAll();
            view.setFilter(row("Orders"), expression);
            // Set on the FX thread without waiting for the worker: pending, nothing filtered yet
            assertEquals(expression + "  (evaluating…)", filterLine());
            assertEquals(AsyncFormulas.ASYNC_NODES, row("Order").getRepeatingTable().getRows().size());
            return null;
        });

        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> fx(() -> expression.equals(filterLine())));
        int rows = fx(() -> row("Order").getRepeatingTable().getRows().size());
        assertEquals(2, rows, "the table shrinks to the matches once the worker is done");
        assertTrue(fx(() -> view.visibleRowList().stream().noneMatch(r -> r.getValue() != null
                && r.getValue().contains("evaluating"))));
    }
}
