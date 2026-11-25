package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
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
 * The screen part of the tutorial video "Find, manage and create schemas" (storyboard
 * {@code videos/schema-library.json}, after {@code docs/guides/design-schemas/schema-library.md},
 * {@code generate-from-xml.md} and {@code sample-xml.md}).
 *
 * <pre>{@code
 * python3 scripts/make-video.py schema-library --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class SchemaLibraryDocVideo {

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
        Path xsd = folder.resolve("order.xsd");
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("lookup", () -> {
                String text = host.getActiveText().orElseThrow();
                int at = text.indexOf("xsi:noNamespaceSchemaLocation");
                var area = host.getActiveCodeArea();
                Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElse(null));
                if (line != null) {
                    video.zoom(new BoundingBox(line.getMinX() - 200, line.getMinY() - 60, 1000, 160));
                }
                video.spoken(0.3);
                video.zoomOut();
                video.moveTo(doc.<Node>find("#activity-schema-library"));
                video.spoken(0.7);
                Node schema = doc.find("#status-schema");
                Bounds sb = video.screenBounds(schema);
                video.zoom(new BoundingBox(0, sb.getMinY() - 8, sb.getMaxX() + 300, sb.getHeight() + 16));
                video.moveTo(schema);
                video.spoken(0.97);
                video.zoomOut();
            });

            video.scene("library", () -> {
                video.click(doc.<Node>find("#activity-schema-library"));
                video.pause(1800);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.5);
                video.zoomOut();
                video.spoken(0.95);
            });

            video.scene("mappings", () -> {
                selectLibraryTab(video, "mappingsTab");
                Node table = doc.find("#library-mappings-table");
                if (table != null) {
                    video.zoom(table);
                    video.moveTo(table);
                }
                video.spoken(0.35);
                video.zoomOut();
                video.moveTo(doc.<Node>find("#library-add"));
                video.spoken(0.6);
                video.moveTo(doc.<Node>find("#library-filter"));
                video.spoken(0.8);
                video.moveTo(doc.<Node>find("#library-edit"));
            });

            video.scene("catalogs", () -> {
                selectLibraryTab(video, "catalogsTab");
                video.pause(800);
                video.moveTo(doc.<Node>find("#library-catalog-add"));
                video.spoken(0.6);
                video.moveTo(doc.<Node>find("#library-catalog-reload"));
                video.spoken(0.85);
                video.moveTo(doc.<Node>find("#library-catalog-toggle"));
            });

            video.scene("schemastore", () -> {
                selectLibraryTab(video, "schemaStoreTab");
                video.pause(800);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.6));
                video.spoken(0.5);
                video.moveTo(doc.<Node>find("#library-schemastore-try"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("cache", () -> {
                selectLibraryTab(video, "cacheTab");
                video.pause(800);
                Node table = doc.find("#library-cache-table");
                if (table != null) {
                    video.moveTo(table);
                }
                video.spoken(0.5);
                video.moveTo(doc.<Node>find("#library-cache-open"));
                video.pause(600);
                video.moveTo(doc.<Node>find("#library-cache-delete"));
                video.spoken(0.85);
                video.moveTo(doc.<Node>find("#library-cache-clear"));
            });

            video.scene("generate-xsd", () -> {
                doc.onFx(() -> host.openFile(xml));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042"));
                video.click(doc.<Node>find("#activity-explorer"));
                video.pause(600);
                video.click(doc.<Node>find("#menu-tools"));
                video.pause(600);
                Node item = video.inPopup("#menu-tools-generate-xsd");
                video.moveTo(item);
                video.spoken(0.3);
                video.click(item);
                video.pause(2000);
                acceptDialogs(video, keys);
                try {
                    doc.waitUntilFx(30, () -> host.getActiveText().orElse("").contains("xs:schema"));
                } catch (Exception e) {
                    System.out.println("[video] no generated schema tab: " + host.getActiveDocument().map(d -> d.getDisplayName()).orElse("-"));
                }
                video.pause(1200);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.75, editor.getHeight() * 0.7));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("check-xsd", () -> {
                java.util.List<Node> modes = doc.fx(() -> host.getViewSwitch().lookupAll(".fxt-view-seg").stream()
                        .filter(n -> n.isVisible() && n.isManaged()).toList());
                if (modes.size() >= 3) {
                    video.click(modes.get(2));
                    video.pause(2000);
                }
                video.spoken(0.95);
            });

            video.scene("sample", () -> {
                doc.onFx(() -> host.openFile(xsd));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("OrderType"));
                video.pause(800);
                video.click(doc.<Node>find("#menu-tools"));
                video.pause(600);
                Node item = video.inPopup("#menu-tools-generate-sample");
                video.moveTo(item);
                video.click(item);
                video.pause(2000);
                Window dialog = keys.dialog();
                if (dialog != null) {
                    video.zoom(doc.fx(() -> new BoundingBox(dialog.getX(), dialog.getY(), dialog.getWidth(), dialog.getHeight())));
                    video.spoken(0.5);
                }
                acceptDialogs(video, keys);
                video.pause(2000);
                video.zoomOut();
                video.spoken(0.75);
                video.key(javafx.scene.input.KeyCode.F8, "F8");
                video.pause(1500);
            });
        } finally {
            video.stop();
        }
    }

    /** Selects a tab of the Schema Library page by its fx:id field name (via the tab's text). */
    private void selectLibraryTab(DocVideoRecorder video, String tabKey) {
        String text = I18n.t("SchemaLibraryView." + tabKey + ".text");
        Node header = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".tab").stream()
                .filter(t -> t.localToScreen(t.getBoundsInLocal()) != null && t.lookupAll(".tab-label").stream()
                        .anyMatch(l -> l instanceof Labeled lb && text.equals(lb.getText())))
                .findFirst().orElse(null));
        if (header != null) {
            video.click(header);
            video.pause(500);
        } else {
            System.out.println("[video] no library tab " + text);
        }
    }

    /** Presses OK in every dialog the action opens (options dialogs), up to three. */
    private void acceptDialogs(DocVideoRecorder video, VideoTyping keys) {
        for (int i = 0; i < 3; i++) {
            Window dialog = keys.dialog();
            if (dialog == null) {
                return;
            }
            Node ok = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".button").stream()
                    .filter(n -> n instanceof javafx.scene.control.Button b && b.isDefaultButton() && !b.isDisabled())
                    .findFirst().orElse(null));
            if (ok == null) {
                Platform.runLater(dialog::hide);
                return;
            }
            video.click(ok);
            video.pause(1500);
        }
    }
}
