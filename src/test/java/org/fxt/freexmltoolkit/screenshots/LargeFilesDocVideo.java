package org.fxt.freexmltoolkit.screenshots;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of the tutorial video "Very large XML files" (storyboard {@code videos/large-files.json},
 * after {@code docs/guides/edit-xml/large-files.md}): a generated order of about 70 MB in the Large File Viewer.
 *
 * <pre>{@code
 * python3 scripts/make-video.py large-files --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class LargeFilesDocVideo {

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
        Path big = folder.resolve("orders-export.xml");
        writeLargeOrder(big, 800_000);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("open", () -> {
                video.spoken(0.15);
                doc.onFx(() -> host.openLargeFileViewer(big));
                video.pause(3000);
                Node info = doc.find("#large-file-info");
                if (info != null) {
                    video.zoom(info);
                }
                video.spoken(0.6);
                video.zoomOut();
                Node views = doc.find("#large-file-views");
                if (views != null) {
                    video.moveTo(views);
                }
                video.spoken(0.95);
            });

            video.scene("text", () -> {
                Node line = doc.find("#large-file-line");
                if (line != null) {
                    video.click(line);
                    video.typeIntoFocused("400000");
                    video.key(javafx.scene.input.KeyCode.ENTER, "Enter");
                    video.pause(1500);
                }
                video.spoken(0.35);
                Node find = doc.find("#large-file-find");
                if (find != null) {
                    video.click(find);
                    video.typeIntoFocused("BK-777777");
                    video.click(doc.<Node>find("#large-file-find-button"));
                    video.pause(4000);
                }
                video.spoken(0.95);
            });

            video.scene("structure", () -> {
                clickTab(video, "#large-file-tab-structure");
                video.pause(2500);
                Node tree = doc.find("#large-file-tree");
                if (tree != null) {
                    Node cell = doc.fx(() -> tree.lookupAll(".tree-cell").stream()
                            .filter(c -> c instanceof javafx.scene.control.TreeCell<?> tc && !tc.isEmpty()).skip(1)
                            .findFirst().orElse(null));
                    if (cell != null) {
                        video.click(cell);
                        video.pause(2000);
                    }
                }
                video.spoken(0.6);
                Node path = doc.find("#large-file-path");
                if (path != null) {
                    video.moveTo(path);
                }
                video.spoken(0.95);
            });

            video.scene("export", () -> {
                Node export = doc.find("#large-file-export");
                if (export != null) {
                    video.click(export);
                    video.pause(1200);
                    keys.hidePopups();
                }
                video.spoken(0.95);
            });

            video.scene("graphic", () -> {
                Node show = doc.find("#large-file-show-graphic");
                if (show != null && doc.fx(() -> show.isVisible() && !show.isDisabled())) {
                    video.click(show);
                } else {
                    clickTab(video, "#large-file-tab-graphic");
                }
                video.pause(3000);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.75));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("apply", () -> video.spoken(0.95));

            video.scene("statistics", () -> {
                clickTab(video, "#large-file-tab-statistics");
                video.pause(800);
                Node scan = doc.find("#large-file-stats-scan");
                if (scan != null) {
                    video.click(scan);
                }
                video.pause(6000);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.75));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("validate", () -> {
                Node validate = doc.find("#large-file-validate");
                if (validate != null) {
                    video.click(validate);
                    video.pause(1000);
                    Node declared = doc.find("#large-file-validate-declared");
                    if (declared == null) {
                        keys.hidePopups();
                    }
                }
                video.pause(8000);
                clickTab(video, "#large-file-tab-problems");
                video.spoken(0.95);
            });
            video.scene("limits", () -> video.spoken(0.95));
        } finally {
            video.stop();
        }
    }

    /** Selects a tab of the viewer: tab ids sit on Tab objects, so the header is found by the pane's tab id. */
    private void clickTab(DocVideoRecorder video, String id) {
        Node views = doc.find("#large-file-views");
        if (views instanceof javafx.scene.control.TabPane tp) {
            doc.onFx(() -> tp.getTabs().stream().filter(t -> id.substring(1).equals(t.getId())).findFirst()
                    .ifPresent(t -> tp.getSelectionModel().select(t)));
        } else {
            Node n = doc.find(id);
            if (n instanceof Labeled || n != null) {
                video.click(n);
            }
        }
        video.pause(600);
    }

    /** An order with {@code items} items (about 90 bytes each), bound to order.xsd. */
    private static void writeLargeOrder(Path file, int items) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            w.write("<order xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            w.write("       xsi:noNamespaceSchemaLocation=\"order.xsd\"\n       id=\"PO-9001\" date=\"2026-03-14\">\n");
            w.write("    <customer>\n        <name>Northwind Books</name>\n        <email>orders@northwind.example</email>\n");
            w.write("        <address>\n            <street>12 Harbour Lane</street>\n            <city>Portsmouth</city>\n");
            w.write("            <country>GB</country>\n        </address>\n    </customer>\n    <items>\n");
            for (int i = 1; i <= items; i++) {
                w.write("        <item sku=\"BK-" + i + "\" quantity=\"" + (1 + i % 5) + "\" price=\""
                        + (10 + i % 40) + ".90\">Book number " + i + "</item>\n");
            }
            w.write("    </items>\n    <note>Generated export for the large file viewer</note>\n</order>\n");
        }
    }
}
