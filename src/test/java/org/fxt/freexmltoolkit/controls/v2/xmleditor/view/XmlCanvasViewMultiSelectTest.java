package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

/** Multi-selection of sibling rows and Shift+click on an arrow that folds all selected rows. */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewMultiSelectTest {

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString("<root><p><x>1</x></p><q><x>2</x></q><r><x>3</x></r><s><x>4</x></s></root>");
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 800, 500));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void click(double x, double y, boolean shift, boolean ctrl) {
        fx(() -> Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                MouseButton.PRIMARY, 1, shift, ctrl, false, false,
                true, false, false, true, false, true, null)));
    }

    private FlatRow row(String label) {
        return view.visibleRowList().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    private double centerY(FlatRow r) {
        return view.rowTopAt(view.visibleRowList().indexOf(r)) + GridMetrics.ROW_HEIGHT / 2;
    }

    private void clickName(String label, boolean shift, boolean ctrl) {
        FlatRow r = row(label);
        click(view.labelXOf(r) + GridMetrics.EXPAND_BAR_WIDTH + GridMetrics.ICON_AREA_WIDTH + 8, centerY(r), shift, ctrl);
    }

    private void clickArrow(String label, boolean shift) {
        FlatRow r = row(label);
        click(view.labelXOf(r) + 5, centerY(r), shift, false);
    }

    @Test
    void shiftClickSelectsASiblingRangeAndCtrlClickToggles() {
        fx(view::expandAll);
        clickName("p", false, false);
        clickName("r", true, false);
        assertEquals(List.of("p", "q", "r"), view.multiSelectionForTest().stream().map(FlatRow::getLabel).toList());
        clickName("q", false, true);
        assertEquals(List.of("p", "r"), view.multiSelectionForTest().stream().map(FlatRow::getLabel).toList());
        clickName("s", false, true);
        assertTrue(view.multiSelectionForTest().stream().anyMatch(r -> "s".equals(r.getLabel())));
        clickName("q", false, false);
        assertEquals(1, view.multiSelectionForTest().size(), "a plain click selects one row again");
    }

    @Test
    void shiftClickOnASelectedArrowFoldsEverySelectedRow() {
        fx(view::expandAll);
        clickName("p", false, false);
        clickName("r", true, false);
        clickArrow("q", true);
        assertFalse(row("p").isExpanded());
        assertFalse(row("q").isExpanded());
        assertFalse(row("r").isExpanded());
        assertTrue(row("s").isExpanded(), "unselected rows keep their state");

        clickArrow("p", true);
        assertTrue(row("p").isExpanded() && row("q").isExpanded() && row("r").isExpanded());
    }
}
