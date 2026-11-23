package org.fxt.freexmltoolkit.screenshots;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "A tour of the workspace" (storyboard
 * {@code videos/workspace-tour.json}, after {@code docs/get-started/workspace-tour.md}): open the
 * example from the start page, visit three activities, switch the views, put the caret on an
 * element for the Properties pane, look at the status bar and collapse both side panes.
 *
 * <pre>{@code
 * python3 scripts/make-video.py workspace-tour --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class WorkspaceTourDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        // a first start: without recent files the start page offers "Open an Example"
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        org.fxt.freexmltoolkit.i18n.I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        doc.settle(1500);
        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("welcome", () -> {
                video.spoken(0.5);
                Node card = doc.find("#welcome-example");
                video.zoom(card);
                video.click(card);
                WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                        () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
                video.pause(500);
                video.zoomOut();
            });

            video.scene("activities", () -> {
                Bounds bar = video.screenBounds(doc.find("#activityBarHost"));
                Bounds panel = video.screenBounds(doc.find("#leftPanelWrapper"));
                video.zoom(new BoundingBox(bar.getMinX(), bar.getMinY(), panel.getMaxX() - bar.getMinX(),
                        Math.min(bar.getHeight(), 640)));
                video.spoken(0.3);
                video.moveTo(doc.<Node>find("#activity-explorer"));
                video.spoken(0.5);
                video.click(doc.<Node>find("#activity-validation"));
                video.spoken(0.75);
                video.click(doc.<Node>find("#activity-transform"));
                video.spoken(0.95);
                video.click(doc.<Node>find("#activity-explorer"));
                video.zoomOut();
            });

            video.scene("editor", () -> {
                video.spoken(0.15);
                Bounds tabs = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(tabs.getMinX(), tabs.getMinY() - 50, tabs.getWidth(), 130));
                video.spoken(0.45);
                List<Node> modes = viewSwitch(host);
                video.click(modes.get(1)); // Tree
                video.pause(1500);
                video.click(modes.get(2)); // Graphic
                video.pause(1500);
                video.click(modes.get(0)); // Text
                video.pause(500);
                video.zoomOut();
            });

            video.scene("inspector", () -> {
                video.spoken(0.2);
                int at = host.getActiveText().orElseThrow().indexOf("<item sku=\"BK-101\"") + 3;
                CodeArea area = doc.findType(CodeArea.class);
                Bounds tag = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElseThrow());
                video.click(tag.getCenterX(), tag.getCenterY());
                video.pause(900);
                video.zoom(doc.<Node>find("#inspectorWrapper"));
                video.spoken(0.85);
                video.zoomOut();
            });

            video.scene("status", () -> {
                Bounds encoding = video.screenBounds(doc.find("#status-encoding"));
                video.zoom(new BoundingBox(0, encoding.getMinY() - 6, encoding.getMaxX() + 40, encoding.getHeight() + 12));
                video.spoken(0.3);
                video.moveTo(doc.<Node>find("#status-schema"));
                video.spoken(0.8);
                video.moveTo(encoding.getCenterX(), encoding.getCenterY());
            });

            video.scene("room", () -> {
                video.zoomOut();
                video.spoken(0.25);
                video.click(doc.<Node>find("#toggle-left-panel"));
                video.pause(900);
                video.click(doc.<Node>find("#toggle-inspector"));
                video.spoken(0.7);
                video.click(doc.<Node>find("#toggle-left-panel"));
                video.pause(700);
                video.click(doc.<Node>find("#toggle-inspector"));
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
