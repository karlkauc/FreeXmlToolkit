package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "XSD: analysis and documentation" (storyboard
 * {@code videos/xsd-analysis.json}, after {@code docs/guides/design-schemas/analysis.md} and
 * {@code documentation.md}).
 *
 * <pre>{@code
 * python3 scripts/make-video.py xsd-analysis --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XsdAnalysisDocVideo {

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
        Path xsd = folder.resolve("order.xsd");
        doc.onFx(() -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("OrderType")).orElse(false));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("open", () -> {
                video.click(doc.<Node>find("#activity-schema"));
                video.pause(1000);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                Node analysis = doc.find("#schema-tool-analysis");
                video.spoken(0.25);
                video.click(analysis);
                video.pause(2500);
                video.zoomOut();
                video.spoken(0.95);
            });

            video.scene("statistics", () -> {
                tab("SchemaAnalysisView.statisticsTab.text", video);
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("types", () -> {
                tab("SchemaAnalysisView.typesTab.text", video);
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("quality", () -> {
                tab("SchemaAnalysisView.qualityTab.text", video);
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("quality-filter", () -> {
                Node chips = doc.find("#analysis-quality-category-chips");
                java.util.List<Node> list = chips instanceof javafx.scene.Parent parent
                        ? doc.fx(() -> parent.getChildrenUnmodifiable().stream().filter(Node::isVisible).toList())
                        : java.util.List.of();
                Node missingDocs = doc.fx(() -> list.stream().filter(n -> n instanceof javafx.scene.control.Labeled l
                        && l.getText() != null && l.getText().contains(org.fxt.freexmltoolkit.i18n.I18n.t(
                                "QualityIssue.category.MISSING_DOCUMENTATION")))
                        .findFirst().orElse(list.size() > 1 ? list.get(1) : null));
                if (missingDocs != null) {
                    video.click(missingDocs);
                    video.pause(1200);
                }
                zoomEditor(video);
                video.spoken(0.4);
                Node table = doc.find("#analysis-quality-table");
                Node row = table == null ? null : doc.fx(() -> table.lookupAll(".table-row-cell").stream()
                        .filter(r -> r instanceof javafx.scene.control.IndexedCell<?> c && c.getIndex() == 0 && !c.isEmpty())
                        .findFirst().orElse(null));
                if (row != null) {
                    Bounds b = video.screenBounds(row);
                    video.zoomOut();
                    video.click(b.getMinX() + 200, b.getCenterY());
                    video.pause(2000);
                    video.spoken(0.7);
                    // selecting a finding reveals it in the schema's tree view: go back to the analysis tab
                    Node analysisTab = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".tab").stream()
                            .filter(t -> t.lookupAll(".tab-label").stream().anyMatch(l -> l instanceof javafx.scene.control.Labeled lb
                                    && org.fxt.freexmltoolkit.controls.shell.editor.analysis.SchemaAnalysisView.TITLE.equals(lb.getText())))
                            .findFirst().orElse(null));
                    if (analysisTab != null) {
                        video.click(analysisTab);
                        video.pause(1200);
                    }
                    zoomEditor(video);
                }
                video.spoken(0.95);
                if (missingDocs != null) {
                    video.click(missingDocs);
                }
                video.zoomOut();
            });

            video.scene("quality-checks", () -> {
                Node limit = doc.find("#analysis-quality-nesting-limit");
                video.spoken(0.75);
                if (limit != null) {
                    video.moveTo(limit);
                }
                video.spoken(0.95);
            });

            video.scene("constraints", () -> {
                tab("SchemaAnalysisView.constraintsTab.text", video);
                video.pause(1200);
                video.spoken(0.5);
                tab("SchemaAnalysisView.xpathTab.text", video);
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("report", () -> {
                Node export = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".menu-button, .button").stream()
                        .filter(n -> n instanceof Labeled l && l.getText() != null && n.isVisible()
                                && n.localToScreen(n.getBoundsInLocal()) != null
                                && (l.getText().startsWith("Export") || l.getText().startsWith("Bericht")))
                        .findFirst().orElse(null));
                if (export != null) {
                    video.click(export);
                    video.pause(1500);
                    keys.hidePopups();
                } else {
                    System.out.println("[video] no report export button");
                }
                video.spoken(0.95);
            });

            video.scene("docs", () -> {
                doc.onFx(() -> host.openFile(xsd));
                video.pause(600);
                video.click(doc.<Node>find("#menu-tools"));
                video.pause(600);
                video.click(video.inPopup("#menu-tools-generate-docs"));
                video.pause(2000);
                zoomEditor(video);
                video.spoken(0.4);
                for (String id : new String[] {"#html", "#pdf", "#word"}) {
                    Node n = doc.find(id);
                    if (n != null && doc.fx(n::isVisible)) {
                        video.moveTo(n);
                        video.pause(500);
                    }
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("doc-options", () -> {
                for (String id : new String[] {"#docgen-show-constraints", "#docgen-page-size", "#docgen-watermark",
                        "#docgen-scan-languages", "#docgen-markdown-mode", "#docgen-generate"}) {
                    Node n = doc.find(id);
                    if (n != null && doc.fx(() -> n.isVisible() && n.localToScreen(n.getBoundsInLocal()) != null)) {
                        video.moveTo(n);
                        video.pause(700);
                    }
                }
                video.spoken(0.95);
            });

            video.scene("cleanup", () -> {
                video.click(doc.<Node>find("#activity-schema"));
                video.pause(800);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                Node flatten = doc.find("#schema-tool-flatten");
                if (flatten != null) {
                    video.moveTo(flatten);
                }
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    private void zoomEditor(DocVideoRecorder video) {
        Bounds editor = video.screenBounds(doc.find("#editorHost"));
        video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.75));
    }

    /** Selects a tab of the analysis page by its label key. */
    private void tab(String key, DocVideoRecorder video) {
        String text = I18n.t(key);
        Node header = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".tab").stream()
                .filter(t -> t.localToScreen(t.getBoundsInLocal()) != null && t.lookupAll(".tab-label").stream()
                        .anyMatch(l -> l instanceof Labeled lb && text.equals(lb.getText())))
                .findFirst().orElse(null));
        if (header != null) {
            video.click(header);
            video.pause(1200);
        } else {
            System.out.println("[video] no tab " + text);
        }
    }
}
