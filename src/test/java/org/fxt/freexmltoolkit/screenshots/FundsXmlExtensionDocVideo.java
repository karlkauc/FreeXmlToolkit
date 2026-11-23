package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of the FundsXML tutorial video "Set up FreeXmlToolkit for FundsXML" (storyboard
 * {@code videos/fundsxml/fundsxml-extension.json}): the extension is off, a FundsXML document offers
 * it in the hint bar, the status-bar mark and the FundsXML activity appear, the panel's tools, and the
 * data-quality report. The content cache is filled beforehand, so turning the extension on downloads
 * nothing.
 *
 * <pre>{@code
 * python3 scripts/make-video.py fundsxml-extension --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlExtensionDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        FundsXmlVideoFiles.seedCache();
        FundsXmlVideoFiles.disableExtension();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
        // DocShell hides the one-time hint for its screenshots; this film shows it
        FundsXmlVideoFiles.disableExtension();
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path folder = FundsXmlVideoFiles.folder("fundsxml-extension");
        doc.settle(1500);

        // turning the extension on registers the cached examples as templates in release/examples/templates
        // (the application's templates folder, found from the working directory): those are removed afterwards
        Path templates = Path.of("release/examples/templates");
        java.util.Set<Path> before = listTemplates(templates);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("open", () -> {
                video.spoken(0.55);
                doc.onFx(() -> host.openFile(folder.resolve("Mixed-Fund_Positions.xml")));
                doc.waitUntilFx(30, () -> doc.find("#fundsxml-hint") != null && doc.<Node>find("#fundsxml-hint").isVisible());
                video.pause(800);
                video.zoom(hintRegion(video));
            });

            video.scene("enable", () -> {
                video.spoken(0.08);
                video.click(doc.<Node>find("#fundsxml-hint-enable"));
                doc.waitUntilFx(20, () -> doc.find("#activity-fundsxml") != null);
                video.pause(600);
                video.zoomOut();
            });

            video.scene("mark", () -> {
                video.spoken(0.1);
                Node badge = doc.find(".fxt-status-document-badge");
                Bounds b = video.screenBounds(badge);
                video.zoom(new BoundingBox(b.getMinX() - 500, b.getMinY() - 160, 900, 200));
                video.spoken(0.6);
                Bounds rail = video.screenBounds(doc.find("#activityBarHost"));
                video.zoom(new BoundingBox(rail.getMinX(), rail.getMinY(), 420, Math.min(rail.getHeight(), 560)));
                video.moveTo(doc.<Node>find("#activity-fundsxml"));
            });

            video.scene("panel", () -> {
                video.spoken(0.1);
                video.click(doc.<Node>find("#activity-fundsxml"));
                video.pause(800);
                Bounds panel = video.screenBounds(doc.find("#leftPanelWrapper"));
                video.zoom(new BoundingBox(panel.getMinX(), panel.getMinY(), panel.getWidth(), 260));
            });

            video.scene("tools", () -> {
                Bounds first = video.screenBounds(doc.find("#fundsxml-validate"));
                Bounds last = video.screenBounds(doc.find("#fundsxml-percentages"));
                video.zoom(new BoundingBox(first.getMinX() - 20, first.getMinY() - 40, Math.max(first.getWidth(), 360) + 40,
                        last.getMaxY() - first.getMinY() + 80));
                video.spoken(0.3);
                video.moveTo(doc.<Node>find("#fundsxml-validate"));
                video.spoken(0.45);
                video.moveTo(doc.<Node>find("#fundsxml-dq"));
                video.spoken(0.6);
                video.moveTo(doc.<Node>find("#fundsxml-identifiers-online"));
                video.spoken(0.8);
                video.moveTo(doc.<Node>find("#fundsxml-percentages"));
            });

            video.scene("more", () -> {
                Node field = doc.find("#industry-identifier");
                Bounds f = video.screenBounds(field);
                Bounds docs = video.screenBounds(doc.find("#fundsxml-online-docs"));
                video.zoom(new BoundingBox(f.getMinX() - 30, f.getMinY() - 80, Math.max(f.getWidth(), 360) + 60,
                        Math.max(docs.getMaxY(), f.getMaxY()) - f.getMinY() + 120));
                video.spoken(0.5);
                video.moveTo(doc.<Node>find("#fundsxml-open-schema"));
                video.spoken(0.8);
                video.moveTo(doc.<Node>find("#fundsxml-examples-folder"));
            });

            video.scene("dq", () -> {
                Bounds button = video.screenBounds(doc.find("#fundsxml-dq"));
                video.zoom(new BoundingBox(button.getMinX() - 20, button.getMinY() - 60, 520, 160));
                video.spoken(0.1);
                video.click(doc.<Node>find("#fundsxml-dq"));
                doc.waitUntilFx(60, () -> host.getActiveDocument()
                        .map(d -> d.getDisplayName().endsWith("-dq.html")).orElse(false));
                video.pause(1500);
                video.zoomOut();
                video.spoken(0.5);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.8, editor.getHeight() * 0.7));
                video.spoken(1);
                video.zoomOut();
            });

            video.scene("welcome", () -> {
                video.spoken(0.1);
                // the generated report counts as unsaved: remove the tabs without the save question
                doc.onFx(() -> {
                    if (doc.shell().lookup(".fxt-editor-tabpane") instanceof javafx.scene.control.TabPane tabs) {
                        tabs.getTabs().clear();
                    }
                });
                doc.waitUntilFx(10, () -> doc.find("#welcome-fundsxml-example") != null
                        && doc.<Node>find("#welcome-fundsxml-example").isVisible());
                video.pause(600);
                Bounds first = video.screenBounds(doc.find("#welcome-fundsxml-example"));
                Bounds last = video.screenBounds(doc.find("#welcome-fundsxml-browse"));
                video.zoom(new BoundingBox(first.getMinX() - 40, first.getMinY() - 70, last.getMaxX() - first.getMinX() + 80,
                        last.getMaxY() - first.getMinY() + 110));
                video.spoken(0.35);
                video.moveTo(doc.<Node>find("#welcome-fundsxml-example"));
                video.spoken(0.5);
                video.moveTo(doc.<Node>find("#welcome-fundsxml-schema"));
                video.spoken(0.62);
                video.moveTo(doc.<Node>find("#welcome-fundsxml-browse"));
                video.spoken(0.75);
                video.click(doc.<Node>find("#welcome-fundsxml-example"));
                doc.waitUntilFx(30, () -> host.getActiveText().map(t -> t.contains("FundsXML4")).orElse(false));
                video.pause(800);
                video.zoomOut();
            });
        } finally {
            video.stop();
            for (Path added : listTemplates(templates)) {
                if (!before.contains(added)) {
                    java.nio.file.Files.deleteIfExists(added);
                }
            }
        }
    }

    private static java.util.Set<Path> listTemplates(Path dir) throws java.io.IOException {
        if (!java.nio.file.Files.isDirectory(dir)) {
            return java.util.Set.of();
        }
        try (var files = java.nio.file.Files.list(dir)) {
            return files.collect(java.util.stream.Collectors.toSet());
        }
    }

    /** The hint bar above the editor, with the tab row. */
    private Bounds hintRegion(DocVideoRecorder video) {
        Bounds b = video.screenBounds(doc.find("#fundsxml-hint"));
        return new BoundingBox(b.getMinX(), b.getMinY() - 60, Math.min(b.getWidth(), 1500), b.getHeight() + 160);
    }
}
