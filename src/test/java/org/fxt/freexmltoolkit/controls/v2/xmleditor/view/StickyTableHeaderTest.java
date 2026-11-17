package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A table running through the top of the viewport keeps its header row pinned below the scroll
 * header; clicks on the pinned header act like clicks on the real one.
 */
@ExtendWith(ApplicationExtension.class)
class StickyTableHeaderTest {

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        StringBuilder xml = new StringBuilder("<root>");
        for (int i = 0; i < 200; i++) {
            xml.append("<item><a>").append(i).append("</a><b>v").append(i).append("</b></item>");
        }
        xml.append("</root>");
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml.toString());
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 900, 500));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private FlatRow owner() {
        return view.visibleRowList().stream().filter(r -> "item".equals(r.getLabel())).findFirst().orElseThrow();
    }

    @Test
    void headerSticksWhileTheTableRunsThroughTheViewportAndTakesClicks() {
        fx(view::expandAll);
        FlatRow owner = owner();
        RepeatingElementsTable table = owner.getRepeatingTable();
        assertNotNull(table);
        double tableTop = view.rowTopAt(view.visibleRowList().indexOf(owner));

        fx(view::render);
        assertNull(view.pinnedHeaders.get(table), "header in place at the top");

        fx(() -> view.scrollToForTest(tableTop + table.drawnRowTop(60)));
        fx(view::render);
        double expected = view.scrollHeaderRows().isEmpty() ? 0 : GridCanvasView.SCROLL_HEADER_HEIGHT;
        Double pinned = view.pinnedHeaders.get(table);
        assertNotNull(pinned, "header pinned while scrolled into the table");
        assertEquals(expected, pinned, 0.001);

        // A click on the pinned header of column "a" selects the whole column, not a data cell
        double x = table.drawnColumnX(1) + 8;
        double y = pinned + table.drawnRowHeight(0) / 2;
        assertEquals(0, view.drawnRowAtView(table, y, tableTop - view.scrollOffsetY));
        fx(() -> Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, true, false, true, null)));
        assertTrue(view.tables.isWholeColumnSelected(table, 0), "click on the pinned header selects the column");

        // Back at the top: nothing pinned
        fx(() -> view.scrollToForTest(0));
        fx(view::render);
        assertFalse(view.pinnedHeaders.containsKey(table));
    }

    @Test
    void pinnedHeaderIsPushedUpByTheTableEnd() {
        RepeatingElementsTable.clearAllCaches();
        fx(view::expandAll);
        RepeatingElementsTable table = owner().getRepeatingTable();
        double h = table.getHeight();
        double h0 = table.drawnRowHeight(0);
        double last = table.drawnRowHeight(table.drawnRowCount() - 1);
        double sticky = GridCanvasView.SCROLL_HEADER_HEIGHT;

        assertTrue(Double.isNaN(GridCanvasView.pinnedHeaderTop(table, sticky, sticky)), "table starts in view");
        assertEquals(sticky, GridCanvasView.pinnedHeaderTop(table, -500, sticky), 0.001);
        // Table end close to the top: the header slides up instead of covering the last row
        double tableTop = sticky + 5 - (h - h0 - last);
        assertEquals(sticky + 5 - 10, GridCanvasView.pinnedHeaderTop(table, tableTop - 10, sticky), 0.001);
        // Scrolled past the table: nothing to pin
        assertTrue(Double.isNaN(GridCanvasView.pinnedHeaderTop(table, -h - 10, sticky)));
    }
}
