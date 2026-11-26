package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Settings and appearance" (storyboard {@code videos/settings.json}).
 * Every appearance change is set back at the end of its scene, so later films start from the defaults.
 *
 * <pre>{@code
 * python3 scripts/make-video.py settings --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class SettingsDocVideo {

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
        var shell = doc.boot();
        EditorHost host = doc.host();
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        Path xml = folder.resolve("order.xml");
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("page", () -> {
                video.click(doc.<Node>find("#activity-settings"));
                video.pause(2000);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.6);
                Node filter = doc.fx(() -> doc.find("#leftPanelWrapper").lookup(".text-field"));
                if (filter != null) {
                    video.click(filter);
                    video.typeIntoFocused("Proxy");
                    video.pause(1500);
                    doc.onFx(() -> ((javafx.scene.control.TextField) filter).clear());
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("theme", () -> {
                show("appearance");
                zoomTo(video, "#settings-theme-system", 260);
                video.spoken(0.5);
                Node toggle = doc.find("#themeToggleButton");
                video.click(toggle);
                video.pause(2500);
                video.click(toggle);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("accent", () -> {
                Node swatches = doc.find("#settings-accent-swatches");
                if (swatches instanceof Parent p) {
                    zoomTo(video, "#settings-accent-swatches", 200);
                    var children = doc.fx(() -> p.getChildrenUnmodifiable().stream().filter(Node::isVisible).toList());
                    if (children.size() > 3) {
                        video.click(children.get(3));
                        video.pause(2000);
                        video.click(children.get(0));
                    }
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("size", () -> {
                zoomTo(video, "#settings-ui-font-large", 300);
                video.click(doc.<Node>find("#settings-ui-font-large"));
                video.pause(2000);
                video.click(doc.<Node>find("#settings-ui-font-normal"));
                video.spoken(0.5);
                video.moveTo(doc.<Node>find("#settings-density-compact"));
                video.pause(600);
                video.moveTo(doc.<Node>find("#settings-density-comfortable"));
                video.spoken(0.8);
                video.moveTo(doc.<Node>find("#settings-high-contrast"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("language", () -> {
                zoomTo(video, "#settings-language", 160);
                video.moveTo(doc.<Node>find("#settings-language"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("memory", () -> {
                zoomTo(video, "#settings-max-memory", 200);
                video.moveTo(doc.<Node>find("#settings-max-memory"));
                video.spoken(0.6);
                Node status = doc.find("#settings-memory-status");
                if (status != null) {
                    video.moveTo(status);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("editor", () -> {
                show("editor");
                zoomTo(video, "#settings-editor-font-family", 420);
                video.moveTo(doc.<Node>find("#settings-editor-font-size"));
                video.pause(600);
                video.moveTo(doc.<Node>find("#settings-editor-line-spacing"));
                video.spoken(0.6);
                video.moveTo(doc.<Node>find("#settings-syntax-scheme"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("schemas", () -> {
                show("schemas");
                zoomTo(video, "#settings-schema-library-autobind", 380);
                video.moveTo(doc.<Node>find("#settings-schema-library-autobind"));
                video.spoken(0.35);
                video.moveTo(doc.<Node>find("#settings-query-timeout"));
                video.pause(500);
                video.moveTo(doc.<Node>find("#settings-query-live-preview"));
                video.spoken(0.65);
                video.zoomOut();
                zoomTo(video, "#settings-standards-expert", 300);
                video.moveTo(doc.<Node>find("#settings-standards-expert"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("network", () -> {
                show("network");
                zoomTo(video, "#settings-proxy-mode", 420);
                video.moveTo(doc.<Node>find("#settings-proxy-mode"));
                video.spoken(0.5);
                video.moveTo(doc.<Node>find("#settings-proxy-test"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("privacy", () -> {
                show("privacy");
                zoomTo(video, "#settings-telemetry-usage", 220);
                video.moveTo(doc.<Node>find("#settings-telemetry-usage"));
                video.pause(600);
                video.moveTo(doc.<Node>find("#settings-telemetry-what"));
                video.spoken(0.95);
                video.zoomOut();
            });
            video.scene("files", () -> {
                show("files");
                zoomTo(video, "#settings-clear-temp", 420);
                video.moveTo(doc.<Node>find("#settings-export-confirm"));
                video.spoken(0.55);
                video.moveTo(doc.<Node>find("#settings-log-cleanup"));
                video.pause(600);
                video.moveTo(doc.<Node>find("#settings-clear-temp"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("extensions", () -> {
                show("extensions");
                zoomTo(video, "#settings-plugins", 360);
                video.moveTo(doc.<Node>find("#settings-plugins"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("save", () -> {
                Node bar = doc.find("#settings-save-bar");
                if (bar != null) {
                    Bounds b = video.screenBounds(bar);
                    video.zoom(new BoundingBox(b.getMinX(), b.getMinY() - 80, b.getWidth(), b.getHeight() + 120));
                }
                video.spoken(0.3);
                video.moveTo(doc.<Node>find("#settings-save"));
                video.spoken(0.6);
                video.moveTo(doc.<Node>find("#settings-reset"));
                video.spoken(0.95);
                video.zoomOut();
            });

        } finally {
            video.stop();
        }
    }

    private void show(String group) {
        SettingsPanel settings = doc.findType(SettingsPanel.class);
        if (settings != null) {
            doc.onFx(() -> settings.showSection(group));
            doc.settle(800);
        }
    }

    /** Zooms onto the area from the node's top, {@code height} pixels high, over the page's width. */
    private void zoomTo(DocVideoRecorder video, String id, double height) {
        Node n = doc.find(id);
        if (n == null) {
            return;
        }
        Bounds b = doc.fx(() -> n.localToScreen(n.getBoundsInLocal()));
        Bounds editor = video.screenBounds(doc.find("#editorHost"));
        if (b != null) {
            video.zoom(new BoundingBox(editor.getMinX(), Math.max(editor.getMinY(), b.getMinY() - 80), editor.getWidth(), height + 120));
        }
    }
}
