package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of the tutorial video "Correct problems with Schematron quick fixes" (storyboard
 * {@code videos/schematron-quick-fixes.json}, after
 * {@code docs/guides/validate/schematron-quick-fixes.md}): validate the bundled quick-fix demo
 * against its Schematron, apply a fix from the editor's lightbulb and one from the problems list.
 *
 * <pre>{@code
 * python3 scripts/make-video.py schematron-quick-fixes --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class SchematronQuickFixDocVideo {

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
        // before the film starts: the demo document is open with its Schematron chosen
        Path folder = DocFiles.work("quick-fixes");
        Path xml = folder.resolve("quickfix-demo.xml");
        Path sch = folder.resolve("quickfix-demo.sch");
        Files.copy(Path.of("release/examples/schematron/quickfix-demo.xml"), xml);
        Files.copy(Path.of("release/examples/schematron/quickfix-demo.sch"), sch);
        doc.open(xml, "<library>");
        doc.activity(Activity.VALIDATION);
        ValidationPanel panel = doc.find(".fxt-validation-panel");
        doc.call(panel, "pickSchematron", sch.toFile());
        doc.settle(1200);
        doc.open(xml, "<library>");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("validate", () -> {
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.55);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> host.getActiveProblems().size() >= 5);
                video.pause(800);
                video.zoom(doc.<Node>find("#problems-panel-list"));
            });

            video.scene("bulb", () -> {
                video.zoomOut();
                video.spoken(0.15);
                Node bulb = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".quickfix-gutter-icon").stream()
                        .filter(n -> n.localToScreen(n.getBoundsInLocal()) != null && n.isVisible())
                        .findFirst().orElseThrow());
                Bounds b = video.screenBounds(bulb);
                video.zoom(new BoundingBox(b.getMinX() - 20, b.getMinY() - 60, 900, 420));
                video.spoken(0.3);
                video.click(bulb);
                Node fix = popupCell("Rename writer to author");
                video.spoken(0.75);
                Bounds f = video.screenBounds(fix);
                video.doubleClick(f.getMinX() + 120, f.getCenterY());
                doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("<author>Ada Lovelace</author>"));
            });

            video.scene("fixed", () -> {
                String text = host.getActiveText().orElseThrow();
                int at = text.indexOf("<author>Ada Lovelace</author>");
                CodeArea area = host.getActiveCodeArea();
                Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 10).orElseThrow());
                video.zoom(new BoundingBox(line.getMinX() - 160, line.getMinY() - 90, 820, 220));
                video.spoken(0.6);
                video.zoomOut();
                video.zoom(doc.<Node>find("#problems-panel-list"));
            });

            video.scene("list", () -> {
                video.spoken(0.1);
                ListView<?> list = doc.find("#problems-panel-list");
                Node row = doc.fx(() -> list.lookupAll(".list-cell").stream()
                        .filter(c -> c instanceof Labeled l && l.getText() != null && l.getText().contains("ISBN")
                                || c.lookupAll(".label").stream().anyMatch(x -> x instanceof Labeled l2
                                        && l2.getText() != null && l2.getText().contains("ISBN")))
                        .findFirst().orElseThrow());
                Bounds r = video.screenBounds(row);
                video.rightClick(r.getMinX() + 240, r.getCenterY());
                video.pause(500);
                video.moveTo(video.menuItem(I18n.t("QuickFixMenuFactory.quick-fix")));
                video.pause(800);
                Node item = video.menuItem("Replace spaces in ISBN with dashes");
                Bounds i = video.screenBounds(item);
                video.moveTo(i.getMinX() + 12, i.getCenterY());
                video.spoken(0.6);
                video.click(i.getCenterX(), i.getCenterY());
                doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("978-3-16-148410-0"));
                video.pause(600);
                String text = host.getActiveText().orElseThrow();
                int at = text.indexOf("978-3-16-148410-0");
                CodeArea area = host.getActiveCodeArea();
                Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 5).orElseThrow());
                video.zoom(new BoundingBox(line.getMinX() - 200, line.getMinY() - 90, 820, 220));
            });
        } finally {
            video.stop();
        }
    }

    /** The row of the open quick-fix chooser whose text contains {@code title}; waits up to 5 s. */
    private Node popupCell(String title) throws Exception {
        Node[] found = new Node[1];
        doc.waitUntilFx(5, () -> {
            for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
                if (window instanceof javafx.stage.PopupWindow && window.isShowing() && window.getScene() != null) {
                    for (Node cell : window.getScene().getRoot().lookupAll(".list-cell")) {
                        boolean match = cell instanceof Labeled l && l.getText() != null && l.getText().contains(title)
                                || cell.lookupAll(".label").stream().anyMatch(x -> x instanceof Labeled l2
                                        && l2.getText() != null && l2.getText().contains(title));
                        if (match) {
                            found[0] = cell;
                            return true;
                        }
                    }
                }
            }
            return false;
        });
        return found[0];
    }
}
