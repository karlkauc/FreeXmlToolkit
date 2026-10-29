package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * TestFX verification of the XMLSpy-style column handling in the tree: dragging the hairline
 * between a block's name column and its value column resizes that block only, a double-click
 * returns it to automatic width, Optimal widths resets every block and every table column,
 * word wrap can be switched off (one line per value), and the cell-height limit cuts values.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewNameColumnResizeTest {

    private static final String LONG = "lorem ipsum dolor sit amet ".repeat(12).trim();

    private static final String XML = """
            <root>
              <a>1</a>
              <b><c>2</c><long>%s</long></b>
              <items>
                <item><x>1</x><y>%s</y></item>
                <item><x>2</x><y>y</y></item>
              </items>
            </root>
            """.formatted(LONG, LONG);

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 900, 600));
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
        mouse(MouseEvent.MOUSE_CLICKED, toX, y, 1, false);
    }

    private void doubleClick(double x, double y) {
        mouse(MouseEvent.MOUSE_PRESSED, x, y, 1, true);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, 1, true);
        mouse(MouseEvent.MOUSE_CLICKED, x, y, 1, true);
        mouse(MouseEvent.MOUSE_PRESSED, x, y, 2, true);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, 2, true);
        mouse(MouseEvent.MOUSE_CLICKED, x, y, 2, true);
    }

    private FlatRow row(String label) {
        return view.visibleRowList().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    private double lineCenterY(FlatRow r) {
        return view.rowTopAt(view.visibleRowList().indexOf(r)) + GridMetrics.ROW_HEIGHT / 2;
    }

    @Test
    void draggingTheSeparatorResizesOnlyThatBlockAndDoubleClickResets() {
        fx(view::expandAll);
        FlatRow a = row("a");
        FlatRow c = row("c");
        double startWidth = view.valueXOf(a) - view.labelXOf(a);
        double nestedWidth = view.valueXOf(c) - view.labelXOf(c);
        double sep = view.valueXOf(a);
        double y = lineCenterY(a);

        mouse(MouseEvent.MOUSE_MOVED, sep, y, 0, true);
        assertEquals(Cursor.H_RESIZE, view.canvasNode().getCursor(), "the separator shows the resize cursor");

        drag(sep, sep + 60, y);
        a = row("a");
        c = row("c");
        assertEquals(startWidth + 60, view.valueXOf(a) - view.labelXOf(a), 0.5, "the root block grew by the drag");
        assertEquals(nestedWidth, view.valueXOf(c) - view.labelXOf(c), 0.5, "the nested block is untouched");
        assertTrue(view.getViewState().hasNameColumnWidths());

        doubleClick(view.valueXOf(a), y);
        a = row("a");
        assertEquals(startWidth, view.valueXOf(a) - view.labelXOf(a), 0.5, "double-click restores automatic width");
        assertFalse(view.getViewState().hasNameColumnWidths());
    }

    @Test
    void optimalWidthsResetsBlocksAndTableColumns() {
        fx(view::expandAll);
        FlatRow a = row("a");
        double natural = view.valueXOf(a) - view.labelXOf(a);
        RepeatingElementsTable table = row("item").getRepeatingTable();
        double naturalColumn = table.getColumn("x").getWidth();

        String rootChildrenBlock = view.getAdapter().stableKey(row("root")); // the block of root's children
        fx(() -> {
            view.getViewState().setNameColumnWidth(rootChildrenBlock, natural + 80);
            table.setColumnUserWidth("x", naturalColumn + 50);
            view.rebuild();
        });
        assertEquals(natural + 80, view.valueXOf(row("a")) - view.labelXOf(row("a")), 0.5);
        assertEquals(naturalColumn + 50, row("item").getRepeatingTable().getColumn("x").getWidth(), 0.5);

        fx(view::optimalWidths);
        assertEquals(natural, view.valueXOf(row("a")) - view.labelXOf(row("a")), 0.5);
        assertEquals(naturalColumn, row("item").getRepeatingTable().getColumn("x").getWidth(), 0.5);
        assertFalse(row("item").getRepeatingTable().getColumn("x").hasUserWidth());
    }

    @Test
    void wordWrapOffKeepsValuesOnOneLineAndTheLimitCutsThem() {
        fx(view::expandAll);
        FlatRow longRow = row("long");
        assertTrue(view.valueBlockOf(longRow).lineCount() > 1, "wraps by default");
        assertTrue(view.isWordWrap());

        fx(() -> view.setWordWrap(false));
        longRow = row("long");
        assertFalse(view.isWordWrap());
        assertEquals(1, view.valueBlockOf(longRow).lineCount(), "no wrapping: one line");
        assertEquals(GridMetrics.ROW_HEIGHT, view.rowHeightAt(view.visibleRowList().indexOf(longRow)), 0.001);
        RepeatingElementsTable table = row("item").getRepeatingTable();
        assertEquals(1, table.getCellLayout(table.getRows().get(0), "y").summary().lineCount(),
                "tables follow the word-wrap setting");
        assertTrue(view.contentWidth() > 900, "the content grows to the right instead");

        fx(() -> {
            view.setWordWrap(true);
            view.setMaxCellLines(2);
        });
        longRow = row("long");
        assertEquals(2, view.getMaxCellLines());
        TextBlock block = view.valueBlockOf(longRow);
        assertNotNull(block);
        assertEquals(2, block.lineCount(), "cut to the limit");
        assertTrue(block.lastLine().endsWith(TextBlock.ELLIPSIS));
        assertEquals(GridMetrics.rowHeight(2), view.rowHeightAt(view.visibleRowList().indexOf(longRow)), 0.001);
    }
}
