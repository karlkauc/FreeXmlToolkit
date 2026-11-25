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
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("why", () -> {
                CodeArea area = host.getActiveCodeArea();
                String text = host.getActiveText().orElseThrow();
                int first = text.indexOf("<book>");
                Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(first, first + 5).orElseThrow());
                video.zoom(new BoundingBox(line.getMinX() - 120, line.getMinY() - 40, 900, 260));
                video.spoken(0.4);
                pointAtText(video, host, "<writer>");
                video.spoken(0.55);
                pointAtText(video, host, "978 3 16");
                video.spoken(0.7);
                pointAtText(video, host, "<legacy-note>");
                video.spoken(0.85);
                pointAtText(video, host, "<draft>");
                video.zoomOut();
            });

            video.scene("rules", () -> {
                doc.open(sch, "<sch:schema");
                video.pause(800);
                zoomText(video, host, "<sch:pattern id=\"book-rules\">", 12);
                video.spoken(0.35);
                pointAtText(video, host, "<sch:rule context=\"book\">");
                video.spoken(0.55);
                pointAtText(video, host, "<sch:assert test=\"@id\"");
                video.spoken(0.75);
                pointAtText(video, host, "<sch:report test=\"legacy-note\"");
            });

            video.scene("fixes-def", () -> {
                video.zoomOut();
                zoomText(video, host, "<sch:assert test=\"not(writer)\"", 3);
                video.spoken(0.3);
                zoomText(video, host, "<sqf:fix id=\"renameWriterToAuthor\">", 8);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("validate", () -> {
                doc.open(xml, "<library>");
                video.pause(600);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.moveTo(doc.<Node>find("#validation-schematron-row"));
                video.spoken(0.55);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> host.getActiveProblems().size() >= 5);
                video.pause(800);
                video.zoom(doc.<Node>find("#problems-panel-list"));
            });

            video.scene("tooltip", () -> {
                ListView<?> list = doc.find("#problems-panel-list");
                Bounds r = video.screenBounds(list);
                video.moveTo(r.getMinX() + 300, r.getMinY() + 14);
                video.pause(2500);
                video.spoken(0.6);
                video.click(r.getMinX() + 300, r.getMinY() + 14);
                video.pause(800);
                video.zoomOut();
            });

            video.scene("bulb", () -> {
                video.spoken(0.15);
                Node bulb = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".quickfix-gutter-icon").stream()
                        .filter(n -> n.localToScreen(n.getBoundsInLocal()) != null && n.isVisible())
                        .findFirst().orElseThrow());
                Bounds b = video.screenBounds(bulb);
                video.zoom(new BoundingBox(b.getMinX() - 20, b.getMinY() - 60, 900, 420));
                video.spoken(0.3);
                video.click(bulb);
                Node fix = popupCell("Rename writer to author");
                video.moveTo(fix);
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
                video.spoken(0.5);
                video.zoomOut();
                video.zoom(doc.<Node>find("#problems-panel-list"));
            });

            video.scene("undo", () -> {
                video.zoomOut();
                CodeArea area = host.getActiveCodeArea();
                video.spoken(0.1);
                video.showKey(keys.ctrl() + "+Z");
                keys.shortcutOn(area, KeyCode.Z, false);
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("<writer>"));
                zoomText(video, host, "<writer>", 2);
                video.spoken(0.45);
                video.showKey(keys.ctrl() + "+Y");
                keys.shortcutOn(area, KeyCode.Y, false);
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("<author>Ada"));
                video.pause(800);
                video.zoomOut();
            });

            video.scene("alt-enter", () -> {
                CodeArea area = host.getActiveCodeArea();
                int book = host.getActiveText().orElseThrow().indexOf("<book>") + 2;
                keys.clickAt(area, book);
                video.spoken(0.2);
                video.showKey("Alt+Enter");
                doc.onFx(() -> javafx.event.Event.fireEvent(area, new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, true, false)));
                video.pause(1200);
                Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(book, book + 1).orElseThrow());
                video.zoom(new BoundingBox(line.getMinX() - 100, line.getMinY() - 40, 900, 360));
                video.spoken(0.9);
                video.key(KeyCode.ESCAPE, "Esc");
                keys.hidePopups();
                video.zoomOut();
            });

            video.scene("list", () -> {
                video.spoken(0.1);
                quickFixFromList(video, "ISBN", "Replace spaces in ISBN with dashes");
                doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("978-3-16-148410-0"));
                video.pause(600);
                zoomText(video, host, "978-3-16-148410-0", 2);
            });

            video.scene("user-entry", () -> {
                video.zoomOut();
                video.spoken(0.05);
                quickFixFromList(video, "publisher", "Add publisher…");
                video.pause(1200);
                javafx.stage.Window dialog = keys.dialog();
                if (dialog != null) {
                    video.zoom(doc.fx(() -> new BoundingBox(dialog.getX(), dialog.getY(), dialog.getWidth(), dialog.getHeight())));
                    video.spoken(0.5);
                    Node ok = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".button").stream()
                            .filter(n -> n instanceof javafx.scene.control.Button b && b.isDefaultButton())
                            .findFirst().orElse(null));
                    if (ok != null) {
                        video.click(ok);
                    }
                }
                doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("Unknown Publisher"));
                video.zoomOut();
                zoomText(video, host, "Unknown Publisher", 2);
            });

            video.scene("report", () -> {
                video.zoomOut();
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.15);
                Node report = doc.find("#validation-schematron-report");
                video.click(report);
                video.pause(2500);
                video.zoomOut();
                video.spoken(0.85);
                doc.open(xml, "<library>");
            });

            video.scene("own", () -> {
                doc.open(sch, "<sch:schema");
                video.pause(600);
                String text = host.getActiveText().orElseThrow();
                String anchor = "<sch:assert test=\"@id\" sqf:fix=\"addBookId\">Every book needs an id attribute.</sch:assert>";
                int at = text.indexOf(anchor) + anchor.length();
                String rule = "\n            <sch:assert test=\"@lang\" sqf:fix=\"addLanguage\">Every book needs a language.</sch:assert>";
                zoomText(video, host, anchor, 8);
                video.spoken(0.15);
                doc.onFx(() -> host.replaceActiveTextRegion(at, at, rule));
                video.pause(1200);
                String now = host.getActiveText().orElseThrow();
                String fixAnchor = "<sqf:fix id=\"addBookId\">";
                int fixAt = now.indexOf(fixAnchor);
                String fix = "<sqf:fix id=\"addLanguage\">\n                <sqf:description>\n                    <sqf:title>Add language de</sqf:title>\n"
                        + "                </sqf:description>\n                <sqf:add node-type=\"attribute\" target=\"lang\">de</sqf:add>\n"
                        + "            </sqf:fix>\n\n            ";
                video.spoken(0.45);
                video.zoomOut();
                zoomText(video, host, fixAnchor, 8);
                doc.onFx(() -> host.replaceActiveTextRegion(fixAt, fixAt, fix));
                video.pause(1200);
                video.spoken(0.85);
                keys.shortcut(KeyCode.S, false, "S");
                video.pause(1200);
                video.zoomOut();
            });

            video.scene("own-apply", () -> {
                doc.open(xml, "<library>");
                video.pause(600);
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> host.getActiveProblems().stream()
                        .anyMatch(pr -> String.valueOf(pr).contains("language")));
                video.pause(800);
                video.zoom(doc.<Node>find("#problems-panel-list"));
                video.spoken(0.3);
                video.zoomOut();
                quickFixFromList(video, "language", "Add language de");
                doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("lang=\"de\""));
                zoomText(video, host, "lang=\"de\"", 3);
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    /** Right-click the first problem row containing {@code rowText} → Quick Fix → {@code fixTitle}. */
    private void quickFixFromList(DocVideoRecorder video, String rowText, String fixTitle) throws Exception {
        ListView<?> list = doc.find("#problems-panel-list");
        Node row = doc.fx(() -> list.lookupAll(".list-cell").stream()
                .filter(c -> c.localToScreen(c.getBoundsInLocal()) != null)
                .filter(c -> c instanceof Labeled l && l.getText() != null && l.getText().contains(rowText)
                        || c.lookupAll(".label").stream().anyMatch(x -> x instanceof Labeled l2
                                && l2.getText() != null && l2.getText().contains(rowText)))
                .findFirst().orElseThrow());
        Bounds r = video.screenBounds(row);
        video.rightClick(r.getMinX() + 240, r.getCenterY());
        video.pause(500);
        Node menu = video.menuItem(I18n.t("QuickFixMenuFactory.quick-fix"));
        Bounds m = video.screenBounds(menu);
        video.moveTo(m.getMinX() + 30, m.getCenterY());
        video.pause(800);
        Node item = menuItemContaining(fixTitle.replace("…", ""));
        Bounds i = video.screenBounds(item);
        video.moveTo(i.getMinX() + 12, m.getCenterY());
        video.moveTo(i.getMinX() + 12, i.getCenterY());
        video.click(i.getCenterX(), i.getCenterY());
    }

    /** The item of an open popup menu whose label contains {@code part}; waits up to 5 s and prints the labels on failure. */
    private Node menuItemContaining(String part) throws Exception {
        Node[] found = new Node[1];
        try {
            doc.waitUntilFx(5, () -> {
                for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                    if (w instanceof javafx.stage.PopupWindow && w.isShowing() && w.getScene() != null) {
                        for (Node item : w.getScene().getRoot().lookupAll(".menu-item")) {
                            boolean match = item.lookupAll(".label").stream().anyMatch(l -> l instanceof Labeled lb
                                    && lb.getText() != null && lb.getText().contains(part));
                            if (match) {
                                found[0] = item;
                                return true;
                            }
                        }
                    }
                }
                return false;
            });
        } catch (Exception e) {
            System.out.println("[video] menu items: " + doc.fx(() -> javafx.stage.Window.getWindows().stream()
                    .filter(w -> w instanceof javafx.stage.PopupWindow && w.isShowing() && w.getScene() != null)
                    .flatMap(w -> w.getScene().getRoot().lookupAll(".label").stream())
                    .map(l -> l instanceof Labeled lb ? lb.getText() : "").toList()));
            throw e;
        }
        return found[0];
    }

    private void zoomText(DocVideoRecorder video, EditorHost host, String snippet, int lines) {
        int at = host.getActiveText().orElseThrow().indexOf(snippet);
        if (at < 0) {
            return;
        }
        CodeArea area = host.getActiveCodeArea();
        Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElse(null));
        if (line != null) {
            video.zoom(new BoundingBox(line.getMinX() - 160, line.getMinY() - 60, 1100, line.getHeight() * (lines + 3)));
        }
    }

    private void pointAtText(DocVideoRecorder video, EditorHost host, String snippet) {
        int at = host.getActiveText().orElseThrow().indexOf(snippet);
        if (at < 0) {
            return;
        }
        CodeArea area = host.getActiveCodeArea();
        Bounds b = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + Math.min(snippet.length(), 12)).orElse(null));
        if (b != null) {
            video.moveTo(b.getCenterX(), b.getCenterY());
            video.pause(400);
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
