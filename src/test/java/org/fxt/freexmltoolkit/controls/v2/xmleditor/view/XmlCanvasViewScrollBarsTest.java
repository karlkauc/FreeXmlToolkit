package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The grid's scroll bars appear only when the content overflows, span the whole viewport
 * edge, and disappear again when the content fits (collapse, larger window).
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewScrollBarsTest {

    private StackPane host;
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        host = new StackPane();
        stage.setScene(new Scene(host, 600, 400));
        stage.show();
    }

    private XmlCanvasView show(String xml) {
        XmlCanvasView view = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            XmlEditorContext context = new XmlEditorContext();
            context.loadDocumentFromString(xml);
            XmlCanvasView v = new XmlCanvasView(context);
            host.getChildren().setAll(v);
            return v;
        });
        WaitForAsyncUtils.waitForFxEvents();
        return view;
    }

    private static <T> T fx(Supplier<T> s) {
        T value = WaitForAsyncUtils.waitForAsyncFx(5000, s::get);
        WaitForAsyncUtils.waitForFxEvents();
        return value;
    }

    private static void awaitBars(XmlCanvasView view, boolean vertical, boolean horizontal) throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() -> {
            boolean[] shown = view.scrollBarsShown();
            return shown[0] == vertical && shown[1] == horizontal;
        }));
    }

    private static String tallXml() {
        StringBuilder xml = new StringBuilder("<root>");
        for (int i = 0; i < 80; i++) {
            xml.append("<item>").append(i).append("</item>");
        }
        return xml.append("</root>").toString();
    }

    @Test
    void contentThatFitsShowsNoScrollBarsAndUsesTheWholeView() throws Exception {
        XmlCanvasView view = show("<root><a>1</a><b>2</b></root>");
        awaitBars(view, false, false);
        double[] sizes = fx(() -> new double[]{view.canvasNode().getWidth(), view.canvasNode().getHeight(),
                view.getWidth(), view.getHeight()});
        assertEquals(sizes[2], sizes[0], 0.5, "the canvas takes the full width");
        assertEquals(sizes[3], sizes[1], 0.5, "the canvas takes the full height");
    }

    @Test
    void tallContentGetsAFullHeightVerticalBarThatGoesAwayOnCollapse() throws Exception {
        XmlCanvasView view = show(tallXml());
        fx(() -> {
            view.expandAll();
            return null;
        });
        awaitBars(view, true, false);
        Bounds[] b = fx(() -> {
            ScrollBar v = view.scrollBarsForTest()[0];
            return new Bounds[]{v.getBoundsInParent(), view.getLayoutBounds()};
        });
        assertEquals(b[1].getHeight(), b[0].getHeight(), 0.5, "the vertical bar spans the whole height");
        assertEquals(b[1].getMaxX(), b[0].getMaxX(), 0.5, "the vertical bar sits at the right edge");
        assertTrue(b[0].getWidth() <= 20, "a bar, not a block: " + b[0].getWidth());

        fx(() -> {
            view.collapseAll();
            return null;
        });
        awaitBars(view, false, false);
    }

    @Test
    void wideContentGetsAFullWidthHorizontalBarThatGoesAwayInALargerWindow() throws Exception {
        XmlCanvasView view = show("<root><x>" + "wide ".repeat(200) + "</x></root>");
        awaitBars(view, false, true);
        Bounds[] b = fx(() -> {
            ScrollBar h = view.scrollBarsForTest()[1];
            return new Bounds[]{h.getBoundsInParent(), view.getLayoutBounds()};
        });
        assertEquals(b[1].getWidth(), b[0].getWidth(), 0.5, "the horizontal bar spans the whole width");
        assertEquals(b[1].getMaxY(), b[0].getMaxY(), 0.5, "the horizontal bar sits at the bottom edge");
        assertTrue(view.hScrollBarMax() > 0);

        fx(() -> {
            stage.setWidth(1600);
            return null;
        });
        awaitBars(view, false, false);
    }
}
