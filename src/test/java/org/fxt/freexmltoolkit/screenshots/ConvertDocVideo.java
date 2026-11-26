package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Convert XML, JSON, YAML and Excel" (storyboard
 * {@code videos/convert.json}, after {@code docs/guides/convert.md}).
 *
 * <pre>{@code
 * python3 scripts/make-video.py convert --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ConvertDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        Path xml = folder.resolve("order.xml");
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("why", () -> {
                video.spoken(0.6);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.7, editor.getHeight() * 0.7));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("to-json", () -> {
                menuItem(video, "#menu-tools-convert-json");
                video.pause(1500);
                Window dialog = keys.dialog();
                if (dialog != null) {
                    video.zoom(doc.fx(() -> new BoundingBox(dialog.getX(), dialog.getY(), dialog.getWidth(), dialog.getHeight())));
                    video.spoken(0.4);
                    Node typed = doc.fx(() -> dialog.getScene().getRoot().lookup("#convert-typed-values"));
                    if (typed != null) {
                        video.click(typed);
                    }
                    video.spoken(0.6);
                    clickDefault(video, dialog);
                }
                video.pause(2000);
                video.zoomOut();
                video.spoken(0.95);
            });

            video.scene("rules", () -> video.spoken(0.95));

            video.scene("mapping", () -> {
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.7, editor.getHeight() * 0.7));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("to-yaml", () -> {
                doc.onFx(() -> host.openFile(xml));
                video.pause(800);
                menuItem(video, "#menu-tools-convert-yaml");
                video.pause(1500);
                Window dialog = keys.dialog();
                if (dialog != null) {
                    clickDefault(video, dialog);
                }
                video.pause(2000);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.7, editor.getHeight() * 0.7));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("back", () -> video.spoken(0.95));

            video.scene("json-views", () -> {
                Node jsonTab = tab(".json");
                if (jsonTab != null) {
                    video.click(jsonTab);
                    video.pause(800);
                }
                List<Node> modes = doc.fx(() -> host.getViewSwitch().lookupAll(".fxt-view-seg").stream()
                        .filter(n -> n.isVisible() && n.isManaged()).toList());
                if (modes.size() >= 3) {
                    video.click(modes.get(1));
                    video.pause(1800);
                    video.spoken(0.5);
                    video.click(modes.get(2));
                    video.pause(2000);
                    video.spoken(0.9);
                    video.click(modes.get(0));
                }
            });

            video.scene("spreadsheet", () -> {
                doc.onFx(() -> host.openFile(xml));
                video.pause(800);
                menuItem(video, "#menu-tools-spreadsheet");
                video.pause(2500);
                Window dialog = keys.dialog();
                if (dialog != null) {
                    video.zoom(doc.fx(() -> new BoundingBox(dialog.getX(), dialog.getY(), dialog.getWidth(), dialog.getHeight())));
                }
                video.spoken(0.95);
            });

            video.scene("layouts", () -> {
                Window dialog = keys.dialog();
                if (dialog != null) {
                    clickIn(video, dialog, "#spreadsheet-layout-list");
                    video.pause(2000);
                    video.spoken(0.5);
                    clickIn(video, dialog, "#spreadsheet-layout-table");
                    video.pause(2000);
                }
                video.spoken(0.95);
            });

            video.scene("excel-style", () -> {
                Window dialog = keys.dialog();
                if (dialog != null) {
                    Node format = doc.fx(() -> dialog.getScene().getRoot().lookup("#spreadsheet-format"));
                    if (format != null) {
                        video.moveTo(format);
                    }
                }
                video.spoken(0.95);
            });

            video.scene("import", () -> {
                Window dialog = keys.dialog();
                if (dialog != null) {
                    clickIn(video, dialog, "#spreadsheet-to-xml");
                    video.pause(1500);
                    video.spoken(0.5);
                    Node root = doc.fx(() -> dialog.getScene().getRoot().lookup("#spreadsheet-root-name"));
                    if (root != null && doc.fx(root::isVisible)) {
                        video.moveTo(root);
                    }
                    video.spoken(0.9);
                    doc.onFx(dialog::hide);
                }
                video.zoomOut();
            });

            video.scene("cli", () -> video.spoken(0.95));
        } finally {
            video.stop();
        }
    }

    private void menuItem(DocVideoRecorder video, String id) throws Exception {
        video.click(doc.<Node>find("#menu-tools"));
        video.pause(600);
        video.click(video.inPopup(id));
    }

    private void clickIn(DocVideoRecorder video, Window dialog, String selector) {
        Node node = doc.fx(() -> dialog.getScene().getRoot().lookup(selector));
        if (node != null && doc.fx(node::isVisible)) {
            video.click(node);
        } else {
            System.out.println("[video] not in dialog: " + selector);
        }
    }

    private void clickDefault(DocVideoRecorder video, Window dialog) {
        Node ok = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && b.isDefaultButton() && !b.isDisabled())
                .findFirst().orElse(null));
        if (ok != null) {
            video.click(ok);
        } else {
            doc.onFx(dialog::hide);
        }
    }

    /** The header of the first editor tab whose title ends with {@code suffix}, or {@code null}. */
    private Node tab(String suffix) {
        return doc.fx(() -> doc.host().lookupAll(".tab").stream()
                .filter(t -> t.localToScreen(t.getBoundsInLocal()) != null && t.lookupAll(".tab-label").stream()
                        .anyMatch(l -> l instanceof javafx.scene.control.Labeled lb && lb.getText() != null
                                && lb.getText().contains(suffix)))
                .findFirst().orElse(null));
    }
}
