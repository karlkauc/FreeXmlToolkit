package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the grid zoom: keyboard and Ctrl+wheel change the zoom factor,
 * hit-testing and the inline editor honour it, and the scroll extents follow it.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewZoomTest {

    private static final String XML = """
            <root>
              <child>value</child>
              <other>%s</other>
            </root>
            """.formatted("wide ".repeat(80).trim());

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

    private void key(KeyCode code, boolean control) {
        fx(() -> Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, control, false, false)));
    }

    private void click(double x, double y, int clicks) {
        fx(() -> Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                MouseButton.PRIMARY, clicks, false, false, false, false,
                true, false, false, true, false, true, null)));
    }

    private String selectedElementName() {
        var node = view.getSelectedNode();
        return (node instanceof XmlElement element) ? element.getName() : null;
    }

    @Test
    void ctrlPlusMinusZeroChangeTheZoom() {
        assertEquals(1.0, view.getZoom(), 0.001);
        key(KeyCode.PLUS, true);
        assertEquals(1.1, view.getZoom(), 0.001);
        key(KeyCode.MINUS, true);
        key(KeyCode.MINUS, true);
        assertEquals(0.9, view.getZoom(), 0.001);
        key(KeyCode.DIGIT0, true);
        assertEquals(1.0, view.getZoom(), 0.001);
        key(KeyCode.PLUS, false); // without Ctrl nothing happens
        assertEquals(1.0, view.getZoom(), 0.001);
    }

    @Test
    void zoomIsClampedAndStepped() {
        fx(() -> view.setZoom(9.0));
        assertEquals(GridCanvasView.ZOOM_MAX, view.getZoom(), 0.001);
        fx(() -> view.setZoom(0.01));
        assertEquals(GridCanvasView.ZOOM_MIN, view.getZoom(), 0.001);
        fx(() -> view.setZoom(1.2345));
        assertEquals(1.2, view.getZoom(), 0.001, "rounded to a tenth");
    }

    @Test
    void ctrlWheelZoomsInsteadOfScrolling() {
        fx(() -> Event.fireEvent(view.canvasNode(), new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10,
                false, true, false, false, false, false,
                0, 40, 0, 40, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null)));
        assertEquals(1.1, view.getZoom(), 0.001);
        assertEquals(0.0, view.scrollOffsetYValue(), 0.001, "Ctrl+wheel must not scroll");
        fx(() -> Event.fireEvent(view.canvasNode(), new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10,
                false, true, false, false, false, false,
                0, -40, 0, -40, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null)));
        assertEquals(1.0, view.getZoom(), 0.001);
    }

    @Test
    void hitTestingHonoursTheZoom() {
        WaitForAsyncUtils.waitForFxEvents();
        // Visible rows: root (0..24) · child (24..48) · other (48..)
        int childIdx = view.visibleRowList().indexOf(view.visibleRowList().stream()
                .filter(r -> "child".equals(r.getLabel())).findFirst().orElseThrow());
        double x = 120;
        double y = view.rowTopAt(childIdx) + GridMetrics.ROW_HEIGHT / 2;

        click(x, y, 1);
        assertEquals("child", selectedElementName());

        fx(() -> view.setZoom(2.0));
        click(x * 2, y * 2, 1);
        assertEquals("child", selectedElementName(), "screen coordinates scale with the zoom");
        click(x, y, 1);
        assertEquals("root", selectedElementName(), "unscaled coordinates now hit the row above");
    }

    @Test
    void inlineEditorIsPlacedAndScaledWithTheZoom() {
        WaitForAsyncUtils.waitForFxEvents();
        int childIdx = view.visibleRowList().indexOf(view.visibleRowList().stream()
                .filter(r -> "child".equals(r.getLabel())).findFirst().orElseThrow());
        double z = 1.5;
        fx(() -> view.setZoom(z));
        double modelX = view.nameColumnWidthValue() + 10;
        double modelY = view.rowTopAt(childIdx) + GridMetrics.ROW_HEIGHT / 2;

        click(modelX * z, modelY * z, 2); // double-click in the value column
        TextField field = view.editFieldForTest();
        assertNotNull(field, "double-click on a value must open the inline editor");
        assertEquals(view.nameColumnWidthValue() * z, field.getLayoutX(), 0.5);
        assertEquals(GridMetrics.ROW_HEIGHT * z, field.getPrefHeight(), 0.5);
        assertTrue(field.getStyle().contains("18.0px"), "font scales: " + field.getStyle());
    }

    @Test
    void ctrlWheelWhileEditingKeepsTheEditorAndTheZoom() {
        WaitForAsyncUtils.waitForFxEvents();
        int childIdx = view.visibleRowList().indexOf(view.visibleRowList().stream()
                .filter(r -> "child".equals(r.getLabel())).findFirst().orElseThrow());
        click(view.nameColumnWidthValue() + 10, view.rowTopAt(childIdx) + GridMetrics.ROW_HEIGHT / 2, 2);
        assertNotNull(view.editFieldForTest(), "double-click opens the inline editor");

        fx(() -> Event.fireEvent(view.canvasNode(), new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10,
                false, true, false, false, false, false,
                0, 40, 0, 40, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null)));

        assertNotNull(view.editFieldForTest(), "zooming must not silently discard a running edit");
        assertEquals(1.0, view.getZoom(), 0.001, "zoom is ignored while editing");
    }

    @Test
    void horizontalScrollExtentFollowsTheZoom() {
        WaitForAsyncUtils.waitForFxEvents();
        double canvasW = view.canvasNode().getWidth();
        double content = view.contentWidth();
        assertTrue(content > canvasW / 2, "fixture must be wider than half the viewport");

        fx(() -> view.setZoom(2.0));
        assertEquals(content - canvasW / 2.0, view.hScrollBarMax(), 0.5,
                "at 200% only half the content width fits into the viewport");
        fx(() -> view.setZoom(1.0));
        assertEquals(Math.max(0, content - canvasW), view.hScrollBarMax(), 0.5);
    }
}
