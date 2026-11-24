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
 * The screen part of "Set up FreeXmlToolkit for FundsXML", film 1 of "FundsXML for developers"
 * (storyboard {@code videos/fundsxml/dev-setup.json}): the extension is off, a FundsXML document offers
 * it in the hint bar, the status-bar mark and the FundsXML activity appear, the panel's tools, the
 * Properties panel with the schema's documentation of an element, and the Welcome page's FundsXML row.
 * The content cache is filled beforehand, so turning the extension on downloads nothing.
 *
 * <pre>{@code
 * python3 scripts/make-video.py dev-setup --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDevSetupDocVideo {

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
        Path folder = FundsXmlVideoFiles.folder("dev-setup");
        doc.settle(1500);

        // turning the extension on registers the cached examples as templates in release/examples/templates
        // (the application's templates folder, found from the working directory): those are removed afterwards
        Path templates = Path.of("release/examples/templates");
        java.util.Set<Path> before = listTemplates(templates);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            video.scene("open", () -> {
                video.spoken(0.55);
                doc.onFx(() -> host.openFile(folder.resolve(FundsXmlBizContentDocVideo.FILE)));
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

            video.scene("inspector", () -> {
                video.zoomOut();
                kit.scrollTo(kit.lineOf(kit.offset("<AssetType>EQ</AssetType>")) - 8);
                video.spoken(0.12);
                kit.clickInto("<AssetType>EQ</AssetType>", 0);
                video.pause(900);
                video.zoom(doc.<Node>find("#inspectorWrapper"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("schema-file", () -> {
                video.spoken(0.12);
                video.click(doc.<Node>find("#fundsxml-open-schema"));
                doc.waitUntilFx(120, () -> host.getActiveText().map(t -> t.contains("vc:minVersion")).orElse(false));
                video.pause(1500);
                video.spoken(0.3);
                String xsd11 = I18n.t("UnifiedShellView.xsd-version-schema", "1.1");
                Node badge = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".label").stream()
                        .filter(n -> n instanceof javafx.scene.control.Label l && xsd11.equals(l.getText()) && n.isVisible())
                        .findFirst().orElseThrow());
                Bounds b = video.screenBounds(badge);
                video.zoom(new BoundingBox(b.getMinX() - 300, b.getMinY() - 140, 760, 180));
                video.spoken(0.6);
                video.zoomOut();
                kit.scrollTo(kit.lineOf(kit.offset("<xs:documentation xml:lang=\"en\"")) - 3);
                int line = kit.lineOf(kit.offset("<xs:documentation xml:lang=\"en\""));
                video.zoom(kit.lines(line - 3, line + 4));
                video.spoken(0.97);
                video.zoomOut();
            });

            video.scene("welcome", () -> {
                video.spoken(0.1);
                // remove the tabs without a save question
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
