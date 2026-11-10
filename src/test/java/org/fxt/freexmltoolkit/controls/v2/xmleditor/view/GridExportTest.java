package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.view.render.ExportableDiagram;
import org.fxt.freexmltoolkit.controls.v2.view.render.SvgDiagramGraphics;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions;
import org.fxt.freexmltoolkit.service.export.DiagramExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The grid exports all its rows, not only the scrolled-in ones, and leaves the view as it was. */
@ExtendWith(ApplicationExtension.class)
class GridExportTest {

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        StringBuilder xml = new StringBuilder("<Root>");
        for (int i = 0; i < 200; i++) {
            xml.append("<Entry").append(i).append(">value-").append(i).append("</Entry").append(i).append('>');
        }
        xml.append("</Root>");
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml.toString());
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 640, 300));
        stage.show();
    }

    @Test
    void wholeGridIsExportedWhileScrolledAndTheViewKeepsItsState() throws Exception {
        ExportableDiagram grid = WaitForAsyncUtils.asyncFx(() -> {
            view.expandAll();
            view.recalculateVisibleRows();
            view.render();
            view.scrollOffsetY = 400;
            view.render();
            return view.exportable();
        }).get(5, TimeUnit.SECONDS);

        Rectangle2D content = WaitForAsyncUtils.asyncFx(grid::exportContentBounds).get(5, TimeUnit.SECONDS);
        assertTrue(content.getHeight() > 1000, "200 rows are taller than the viewport: " + content);

        DiagramExportOptions options = DiagramExportOptions.defaults(DiagramExportOptions.Format.SVG);
        String svg = WaitForAsyncUtils.asyncFx(() -> {
            SvgDiagramGraphics g = DiagramExporter.renderSvg(grid, options);
            grid.exportFinished();
            return g.toXml();
        }).get(10, TimeUnit.SECONDS);
        assertTrue(svg.contains(">Entry0<"), "the first row, scrolled away on screen");
        assertTrue(svg.contains(">\"value-199\"<"), "the last row");
        assertFalse(svg.contains("rows ("), "no info overlay");

        double scroll = WaitForAsyncUtils.asyncFx(() -> view.scrollOffsetY).get(5, TimeUnit.SECONDS);
        assertEquals(400, scroll, "scroll position restored");
        assertTrue(WaitForAsyncUtils.asyncFx(() -> view.gc
                        instanceof org.fxt.freexmltoolkit.controls.v2.view.render.FxDiagramGraphics)
                .get(5, TimeUnit.SECONDS), "drawing goes back to the canvas");
    }

    @Test
    void visibleAreaFollowsTheScrollPosition() throws Exception {
        Rectangle2D visible = WaitForAsyncUtils.asyncFx(() -> {
            view.expandAll();
            view.recalculateVisibleRows();
            view.scrollOffsetY = 120;
            return view.exportable().exportVisibleBounds();
        }).get(5, TimeUnit.SECONDS);
        assertEquals(120, visible.getMinY());
        assertTrue(visible.getHeight() > 0);
    }
}
