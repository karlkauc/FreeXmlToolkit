package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Cursor;
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
 * TestFX verification of XMLSpy-style column resizing in embedded tables: dragging a
 * column separator in the column-header row sets a user width (text re-wraps, the row
 * grows), the cursor shows the resize handle, a resize never sorts the column, the
 * handle honours the zoom, and double-clicking it returns to automatic sizing.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewColumnResizeTest {

    private static final String LONG = "alpha beta gamma delta ".repeat(14).trim(); // ≈ 330 chars

    private static final String XML = """
            <root>
              <items>
                <item><a>1</a><b>%s</b></item>
                <item><a>2</a><b>y</b></item>
              </items>
            </root>
            """.formatted(LONG);

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 800, 600));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void mouse(EventType<MouseEvent> type, double x, double y, int clicks, boolean still) {
        fx(() -> Event.fireEvent(view.canvasNode(), new MouseEvent(type, x, y, x, y,
                MouseButton.PRIMARY, clicks, false, false, false, false,
                type != MouseEvent.MOUSE_RELEASED && type != MouseEvent.MOUSE_CLICKED && type != MouseEvent.MOUSE_MOVED,
                false, false, true, false, still, null)));
    }

    private void drag(double fromX, double toX, double y) {
        mouse(MouseEvent.MOUSE_PRESSED, fromX, y, 1, true);
        mouse(MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, y, 1, false);
        mouse(MouseEvent.MOUSE_DRAGGED, toX, y, 1, false);
        mouse(MouseEvent.MOUSE_RELEASED, toX, y, 1, false);
        mouse(MouseEvent.MOUSE_CLICKED, toX, y, 1, false); // JavaFX fires this after a drag too
    }

    private FlatRow tableRow() {
        return view.visibleRowList().stream()
                .filter(r -> "item".equals(r.getLabel()) && r.hasRepeatingTable())
                .findFirst().orElseThrow();
    }

    /** @return the y (unscaled viewport units) of the column-header row's centre */
    private double headerCenterY() {
        int idx = view.visibleRowList().indexOf(tableRow());
        return view.rowTopAt(idx) + GridMetrics.ROW_HEIGHT
                + RepeatingElementsTable.HEADER_HEIGHT + RepeatingElementsTable.ROW_HEIGHT / 2;
    }

    /** @return the x (model units) of column b's right edge */
    private double separatorX() {
        RepeatingElementsTable table = tableRow().getRepeatingTable();
        return table.getColumnX("b") + table.getColumn("b").getWidth();
    }

    @Test
    void draggingASeparatorSetsTheUserWidthAndRewraps() {
        fx(view::expandAll);
        RepeatingElementsTable table = tableRow().getRepeatingTable();
        double startWidth = table.getColumn("b").getWidth();
        double startRowHeight = table.calculateRowHeight(table.getRows().get(0));
        double y = headerCenterY();
        double sep = separatorX();

        mouse(MouseEvent.MOUSE_MOVED, sep, y, 0, true);
        assertEquals(Cursor.H_RESIZE, view.canvasNode().getCursor(), "hovering the handle shows the resize cursor");

        drag(sep, sep - 120, y);

        table = tableRow().getRepeatingTable();
        assertNotNull(table.getColumn("b").getUserWidth(), "drag sets a user width");
        assertEquals(startWidth - 120, table.getColumn("b").getWidth(), 0.5);
        assertTrue(table.calculateRowHeight(table.getRows().get(0)) > startRowHeight,
                "the narrower column wraps into more lines");
        assertNull(table.getSortedColumnName(), "a resize must not sort the column");

        mouse(MouseEvent.MOUSE_MOVED, 300, 5, 0, true);
        assertEquals(Cursor.DEFAULT, view.canvasNode().getCursor());
    }

    @Test
    void handleHonoursTheZoomAndDoubleClickRestoresAutoFit() {
        fx(view::expandAll);
        RepeatingElementsTable table = tableRow().getRepeatingTable();
        double natural = table.getColumn("b").getWidth();
        double y = headerCenterY();
        double sep = separatorX();

        fx(() -> view.setZoom(2.0));
        drag(sep * 2, (sep - 40) * 2, y * 2);
        table = tableRow().getRepeatingTable();
        assertEquals(natural - 40, table.getColumn("b").getWidth(), 0.5, "screen deltas are divided by the zoom");

        double newSep = separatorX();
        mouse(MouseEvent.MOUSE_PRESSED, newSep * 2, y * 2, 1, true);
        mouse(MouseEvent.MOUSE_RELEASED, newSep * 2, y * 2, 1, true);
        mouse(MouseEvent.MOUSE_CLICKED, newSep * 2, y * 2, 1, true);
        mouse(MouseEvent.MOUSE_PRESSED, newSep * 2, y * 2, 2, true);
        mouse(MouseEvent.MOUSE_RELEASED, newSep * 2, y * 2, 2, true);
        mouse(MouseEvent.MOUSE_CLICKED, newSep * 2, y * 2, 2, true);

        table = tableRow().getRepeatingTable();
        assertFalse(table.getColumn("b").hasUserWidth(), "double-click on the handle restores auto sizing");
        assertEquals(natural, table.getColumn("b").getWidth(), 0.5);
        assertNull(table.getSortedColumnName(), "clicks on the handle never sort");
    }
}
