package org.fxt.freexmltoolkit.screenshots;

import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Validate an XML file against its schema"
 * (storyboard {@code videos/validate-xml.json}, the steps of {@code docs/get-started/first-steps.md}
 * section 2): open the example, validate, break a quantity, read the problem, repair it.
 *
 * <pre>{@code
 * python3 scripts/make-video.py validate-xml --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ValidateXmlDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        // a first start: without recent files the Welcome page offers "Open an Example"
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        // the interface language of this film (-Dfxt.locale, set by the docVideos task)
        org.fxt.freexmltoolkit.i18n.I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        java.nio.file.Path[] order = new java.nio.file.Path[1];
        String[] original = new String[1];
        doc.settle(1500);
        video.start();
        try {
            video.scene("welcome", () -> {
                video.spoken(0.55);
                Node card = doc.find("#welcome-example");
                video.zoom(card);
                video.click(card);
                WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                        () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
                order[0] = doc.fx(() -> host.getActiveDocument().orElseThrow().getPath());
                video.zoomOut();
            });

            video.scene("document", () -> {
                video.spoken(0.35);
                video.zoom(textBounds(host, "xsi:noNamespaceSchemaLocation=\"order.xsd\""));
                video.spoken(1);
                video.zoomOut();
            });

            video.scene("status-schema", () -> {
                Node schema = doc.find("#status-schema");
                Bounds b = video.screenBounds(schema);
                video.zoom(new BoundingBox(0, b.getMinY() - 8, b.getMaxX() + 300, b.getHeight() + 16));
                video.spoken(0.2);
                video.moveTo(schema);
                video.spoken(0.6);
                video.zoomOut();
                video.click(doc.<Node>find("#activity-schema-library"));
                video.pause(1800);
                video.spoken(0.95);
                video.click(doc.<Node>find("#activity-explorer"));
                doc.onFx(() -> host.openFile(order[0]));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042"));
            });

            video.scene("valid", () -> {
                video.spoken(0.3);
                // the result is a short-lived toast in the window's bottom right corner: be there first
                video.zoom(new BoundingBox(DocVideoRecorder.WIDTH - 420, DocVideoRecorder.HEIGHT - 190, 400, 150));
                video.pause(900);
                video.key(KeyCode.F8, "F8");
                video.spoken(1);
                video.zoomOut();
            });

            video.scene("wellformed", () -> {
                String text = host.getActiveText().orElseThrow();
                int at = text.indexOf("<note>") + "<note".length();
                video.zoom(textBounds(host, "<note>Please deliver before Friday.</note>"));
                video.spoken(0.2);
                video.retype(at, 1, "");
                video.spoken(0.35);
                video.key(KeyCode.F8, "F8");
                video.pause(1500);
                video.zoomOut();
                video.zoom(problemsArea());
                video.spoken(0.8);
                video.zoomOut();
                video.zoom(textBounds(host, "<note"));
                video.retype(at, 0, ">");
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(30, () -> host.getActiveProblems().isEmpty());
                video.zoomOut();
            });

            video.scene("break", () -> {
                video.spoken(0.25);
                int at = host.getActiveText().orElseThrow().indexOf("quantity=\"2\"") + "quantity=\"".length();
                Bounds value = charBounds(at, at + 1);
                video.zoom(textBounds(host, "<item sku=\"BK-101\" quantity=\"2\" price=\"24.90\">"));
                video.click(value.getMaxX(), value.getCenterY());
                video.spoken(0.6);
                video.retype(at, 1, "two");
            });

            video.scene("problems", () -> {
                video.spoken(0.15);
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(30, () -> !host.getActiveProblems().isEmpty());
                video.zoomOut();
                video.spoken(0.4);
                video.zoom(doc.<Node>find("#problems-panel-list"));
            });

            video.scene("jump", () -> {
                video.zoomOut();
                video.spoken(0.2);
                ListView<?> problems = doc.find("#problems-panel-list");
                Bounds list = video.screenBounds(problems);
                video.click(list.getMinX() + 260, list.getMinY() + 14);
            });

            video.scene("repair", () -> {
                video.spoken(0.1);
                int at = host.getActiveText().orElseThrow().indexOf("quantity=\"two\"") + "quantity=\"".length();
                video.zoom(textBounds(host, "<item sku=\"BK-101\" quantity=\"two\" price=\"24.90\">"));
                Bounds value = charBounds(at, at + 3);
                video.click(value.getMaxX(), value.getCenterY());
                video.retype(at, 3, "2");
                video.spoken(0.6);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                original[0] = host.getActiveText().orElseThrow();
            });

            video.scene("several", () -> {
                String text = host.getActiveText().orElseThrow();
                int id = text.indexOf("PO-1042") + 3;
                video.zoom(textBounds(host, "id=\"PO-1042\""));
                video.retype(id, 4, "42");
                video.spoken(0.4);
                text = host.getActiveText().orElseThrow();
                int country = text.indexOf("<country>GB") + "<country>".length();
                video.zoom(textBounds(host, "<country>GB</country>"));
                video.retype(country, 2, "GBR");
                video.spoken(0.65);
                text = host.getActiveText().orElseThrow();
                int price = text.indexOf("price=\"24.90\"") + "price=\"24.90".length();
                video.zoom(textBounds(host, "price=\"24.90\""));
                video.retype(price, 0, "5");
                video.spoken(0.85);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(30, () -> host.getActiveProblems().size() >= 3);
                video.pause(800);
                video.zoom(problemsArea());
            });

            video.scene("filter", () -> {
                video.spoken(0.2);
                video.click(doc.<Node>find("#problems-filter-errors"));
                video.pause(1200);
                video.click(doc.<Node>find("#problems-filter-errors"));
                video.spoken(0.55);
                video.click(doc.<Node>find("#problems-search"));
                video.typeIntoFocused("pattern");
                video.pause(1800);
                javafx.scene.control.TextField search = doc.find("#problems-search");
                doc.onFx(() -> search.clear());
            });

            video.scene("group", () -> {
                video.spoken(0.1);
                javafx.scene.control.ComboBox<?> group = doc.find("#problems-group-by");
                video.click(group);
                video.pause(900);
                keys.hidePopups();
                doc.onFx(() -> group.getSelectionModel().select(2));
                video.pause(2000);
                video.spoken(0.55);
                doc.onFx(() -> group.getSelectionModel().select(0));
                video.click(doc.<Node>find("#problems-export"));
                video.pause(1800);
                keys.hidePopups();
            });

            video.scene("grid-jump", () -> {
                video.zoomOut();
                java.util.List<Node> modes = doc.fx(() -> host.getViewSwitch().lookupAll(".fxt-view-seg").stream()
                        .filter(n -> n.isVisible() && n.isManaged()).toList());
                video.click(modes.get(2));
                video.pause(2000);
                video.spoken(0.3);
                ListView<?> problems = doc.find("#problems-panel-list");
                int priceRow = doc.fx(() -> {
                    for (int i = 0; i < problems.getItems().size(); i++) {
                        if (String.valueOf(problems.getItems().get(i)).contains("24.905")) {
                            return i;
                        }
                    }
                    return 0;
                });
                Node cell = doc.fx(() -> problems.lookupAll(".list-cell").stream()
                        .filter(n -> n instanceof javafx.scene.control.ListCell<?> c && c.getIndex() == priceRow)
                        .findFirst().orElse(null));
                if (cell != null) {
                    Bounds cb = video.screenBounds(cell);
                    video.click(cb.getMinX() + 260, cb.getCenterY());
                }
                video.pause(1500);
                video.spoken(0.9);
                video.click(modes.get(0));
                video.pause(800);
            });

            video.scene("fix-all", () -> {
                ListView<?> problems = doc.find("#problems-panel-list");
                Bounds list = video.screenBounds(problems);
                video.click(list.getMinX() + 260, list.getMinY() + 14);
                video.spoken(0.35);
                video.zoomOut();
                CodeArea area = keys.area("PO-");
                for (int i = 0; i < 40 && !host.getActiveText().orElse("").equals(original[0]); i++) {
                    keys.shortcutOn(area, KeyCode.Z, false);
                    video.pause(60);
                }
                video.showKey(keys.ctrl() + "+Z");
                if (!host.getActiveText().orElse("").equals(original[0])) {
                    String now = host.getActiveText().orElse("");
                    doc.onFx(() -> host.replaceActiveTextRegion(0, now.length(), original[0]));
                }
                video.spoken(0.75);
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(30, () -> host.getActiveProblems().isEmpty());
            });

            video.scene("autocomplete", () -> {
                String text = host.getActiveText().orElseThrow();
                int attr = text.indexOf(" price=\"24.90\"");
                video.zoom(textBounds(host, "<item sku=\"BK-101\" quantity=\"2\" price=\"24.90\">"));
                video.retype(attr, " price=\"24.90\"".length(), "");
                video.spoken(0.3);
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(30, () -> !host.getActiveProblems().isEmpty());
                video.zoomOut();
                video.zoom(problemsArea());
                video.spoken(0.6);
                video.zoomOut();
                CodeArea area = keys.area("PO-");
                video.zoom(textBounds(host, "<item sku=\"BK-101\" quantity=\"2\">"));
                keys.clickAt(area, attr);
                keys.type(area, " ");
                video.pause(1800);
                keys.press(area, KeyCode.ENTER, "Enter");
                video.pause(600);
                keys.type(area, "24.90");
                video.pause(500);
                System.out.println("[video] after completion: " + host.getActiveText().orElse("")
                        .substring(attr - 40, attr + 40).replace("\n", "⏎"));
                if (!host.getActiveText().orElse("").equals(original[0])) {
                    String now = host.getActiveText().orElse("");
                    doc.onFx(() -> host.replaceActiveTextRegion(0, now.length(), original[0]));
                }
                video.key(KeyCode.F8, "F8");
                video.zoomOut();
            });

            video.scene("panel", () -> {
                video.click(doc.<Node>find("#activity-validation"));
                video.pause(1000);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.35);
                video.moveTo(doc.<Node>find("#validation-xsd-row"));
                video.spoken(0.7);
                video.moveTo(doc.<Node>find("#validation-schematron-row"));
            });

            video.scene("live", () -> {
                video.spoken(0.1);
                video.click(doc.<Node>find("#validation-overflow"));
                video.pause(900);
                Node live = video.menuItem(org.fxt.freexmltoolkit.i18n.I18n.t("ValidationPanel.liveValidation.text"));
                video.moveTo(live);
                video.spoken(0.6);
                keys.hidePopups();
            });

            video.scene("batch", () -> {
                video.spoken(0.15);
                var panel = doc.findType(org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel.class);
                java.util.List<java.io.File> files = batchFiles(order[0]);
                Node batchToggle = doc.fx(() -> panel.lookupAll(".toggle-button").stream()
                        .filter(n -> n instanceof javafx.scene.control.ToggleButton t
                                && (t.getText().equals("Stapel") || t.getText().equals("Batch")))
                        .findFirst().orElseThrow());
                for (String id : new String[] {"#validation-reports-section", "#validation-authoring-section"}) {
                    Node section = doc.find(id);
                    if (section instanceof org.fxt.freexmltoolkit.controls.shell.editor.SidePanelSection sps) {
                        doc.onFx(() -> sps.setCollapsed(true));
                    }
                }
                video.click(batchToggle);
                video.pause(800);
                doc.onFx(() -> panel.runBatch(files));
                doc.waitUntilFx(60, () -> {
                    Node l = panel.lookup("#validation-results-list");
                    return l instanceof ListView<?> lv && lv.getItems().size() >= files.size();
                });
                video.pause(1500);
                video.spoken(0.55);
                ListView<?> results = doc.find("#validation-results-list");
                Node cell = null;
                for (int i = 0; i < 5 && cell == null; i++) {
                    int index = i;
                    doc.onFx(() -> results.scrollTo(index));
                    video.pause(150);
                    cell = doc.fx(() -> results.lookupAll(".list-cell").stream()
                            .filter(n -> n instanceof javafx.scene.control.ListCell<?> c && c.getIndex() == index
                                    && n.lookupAll(".label").stream().anyMatch(l -> l instanceof javafx.scene.control.Labeled lb
                                    && lb.getText() != null && lb.getText().contains("invalid")))
                            .findFirst().orElse(null));
                }
                System.out.println("[video] failing batch cell: " + cell);
                if (cell != null) {
                    video.click(cell);
                }
                video.pause(1500);
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    /** Five orders next to the example, two of them invalid, for the batch scene. */
    private static java.util.List<java.io.File> batchFiles(java.nio.file.Path example) throws Exception {
        String text = java.nio.file.Files.readString(example);
        java.nio.file.Path dir = example.getParent();
        java.util.List<java.io.File> files = new java.util.ArrayList<>();
        String[][] variants = {{"order-1043", text.replace("PO-1042", "PO-1043")},
                {"order-1044-invalid", text.replace("PO-1042", "PO-1044").replace("quantity=\"2\"", "quantity=\"two\"")},
                {"order-1045", text.replace("PO-1042", "PO-1045")},
                {"order-1046-invalid", text.replace("PO-1042", "PO-1046").replace("<country>GB", "<country>GBR")},
                {"order-1047", text.replace("PO-1042", "PO-1047")}};
        for (String[] v : variants) {
            java.nio.file.Path file = dir.resolve(v[0] + ".xml");
            java.nio.file.Files.writeString(file, v[1]);
            files.add(file.toFile());
        }
        return files;
    }

    /** The screen region of the problems list under the editor. */
    private Bounds problemsArea() {
        Node list = doc.find("#problems-panel-list");
        Bounds b = doc.fx(() -> list.localToScreen(list.getBoundsInLocal()));
        return new BoundingBox(b.getMinX(), b.getMinY() - 60, Math.min(b.getWidth(), 1300), b.getHeight() + 70);
    }

    /** The screen region of {@code snippet} in the active text. */
    private Bounds textBounds(EditorHost host, String snippet) {
        int at = host.getActiveText().orElseThrow().indexOf(snippet);
        if (at < 0) {
            throw new IllegalStateException("Not in the document: " + snippet);
        }
        return charBounds(at, at + snippet.length());
    }

    private Bounds charBounds(int from, int to) {
        CodeArea area = doc.findType(CodeArea.class);
        Bounds b = doc.fx(() -> area.getCharacterBoundsOnScreen(from, to).orElseThrow());
        return new BoundingBox(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
    }
}
