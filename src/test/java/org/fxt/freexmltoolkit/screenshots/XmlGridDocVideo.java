package org.fxt.freexmltoolkit.screenshots;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridVideoProbe;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "The XML grid" (storyboard {@code videos/xml-grid.json},
 * after {@code docs/guides/edit-xml/grid.md}): switch the example order to the Graphic view, expand
 * it, look at the table of repeating elements, edit a cell, sort a column, and find both changes in
 * the text.
 *
 * <pre>{@code
 * python3 scripts/make-video.py xml-grid --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XmlGridDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        org.fxt.freexmltoolkit.i18n.I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        var shell = doc.boot();
        EditorHost host = doc.host();
        // before the film starts: the example order is open as text
        doc.call(shell, "openExample");
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(2000);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("switch", () -> {
                video.spoken(0.4);
                List<Node> modes = viewSwitch(host);
                Bounds first = video.screenBounds(modes.getFirst());
                Bounds last = video.screenBounds(modes.getLast());
                video.zoom(new BoundingBox(first.getMinX(), first.getMinY(),
                        last.getMaxX() - first.getMinX(), first.getHeight()));
                video.spoken(0.7);
                video.click(modes.get(2)); // Text, Tree, Graphic
                doc.waitUntilFx(30, () -> GridVideoProbe.view(host) != null);
                video.pause(600);
                video.zoomOut();
            });
            XmlCanvasView grid = doc.fx(() -> GridVideoProbe.view(host));

            video.scene("blocks", () -> {
                video.spoken(0.55);
                video.click(doc.<Node>find("#grid-expand-all"));
                video.pause(700);
                Bounds canvas = doc.fx(() -> GridVideoProbe.canvas(grid));
                video.zoom(new BoundingBox(canvas.getMinX(), canvas.getMinY(), 1000, 520));
            });

            video.scene("table", () -> {
                video.spoken(0.1);
                video.zoom(doc.fx(() -> GridVideoProbe.table(grid)));
                video.spoken(0.5);
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 0, "sku"));
                Bounds end = doc.fx(() -> GridVideoProbe.cell(grid, 2, "sku"));
                video.moveTo(cell.getCenterX(), cell.getCenterY());
                video.moveTo(end.getCenterX(), end.getCenterY());
            });

            video.scene("edit", () -> {
                // at once: the cell editor takes a moment to open under the recording's software rendering
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 0, "quantity"));
                video.doubleClick(cell.getCenterX(), cell.getCenterY());
                video.spoken(0.5);
                video.typeIntoFocused("5");
                video.spoken(0.85);
                video.key(KeyCode.ENTER, "Enter");
                doc.waitUntilFx(15, () -> host.getActiveText().orElse("").contains("quantity=\"5\""));
            });

            video.scene("sort", () -> {
                video.spoken(0.35);
                Bounds arrow = doc.fx(() -> GridVideoProbe.sortArrow(grid, "price"));
                video.click(arrow.getCenterX(), arrow.getCenterY());
                doc.waitUntilFx(15, () -> {
                    String text = host.getActiveText().orElse("");
                    return text.indexOf("BK-318") < text.indexOf("BK-101");
                });
            });

            video.scene("text", () -> {
                video.zoomOut();
                video.spoken(0.3);
                video.click(viewSwitch(host).getFirst());
                video.pause(900);
                String text = host.getActiveText().orElseThrow();
                int from = text.indexOf("<items>");
                int to = text.indexOf("</items>") + "</items>".length();
                CodeArea area = doc.findType(CodeArea.class);
                Bounds lines = doc.fx(() -> area.getCharacterBoundsOnScreen(from, to).orElseThrow());
                video.zoom(new BoundingBox(lines.getMinX(), lines.getMinY(), 820, lines.getHeight()));
            });
        } finally {
            video.stop();
        }
    }

    /** The buttons of the view switch that are showing, in their order (Text, Tree, Graphic). */
    private List<Node> viewSwitch(EditorHost host) {
        return doc.fx(() -> host.getViewSwitch().lookupAll(".fxt-view-seg").stream()
                .filter(n -> n.isVisible() && n.isManaged()).toList());
    }
}
