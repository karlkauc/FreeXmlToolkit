package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.controls.shell.editor.QueryConsole;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "XPath and XQuery in the Query Console" (storyboard
 * {@code videos/query-console.json}, after {@code docs/guides/transform-query/query-console.md}).
 *
 * <pre>{@code
 * python3 scripts/make-video.py query-console --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class QueryConsoleDocVideo {

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
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("open", () -> {
                video.spoken(0.15);
                video.moveTo(doc.<Node>find("#searchPill"));
                video.spoken(0.3);
                video.showKey(keys.ctrl() + "+K");
                doc.onFx(shell::openQueryConsole);
                video.pause(1500);
                video.zoom(console());
                video.spoken(0.95);
            });

            video.scene("target", () -> {
                video.moveTo(doc.<Node>find("#target"));
                video.spoken(0.9);
            });

            video.scene("xpath", () -> {
                xpathMode(video);
                CodeArea input = input(false);
                video.click(input);
                setInput(input, "");
                keys.type(input, "//item");
                video.spoken(0.4);
                run(video);
                video.pause(1500);
                video.spoken(0.95);
            });

            video.scene("completion", () -> {
                CodeArea input = input(false);
                video.click(input);
                doc.onFx(() -> input.moveTo(input.getLength()));
                keys.type(input, "[@");
                video.pause(2000);
                video.spoken(0.55);
                keys.press(input, KeyCode.ESCAPE, "Esc");
                keys.hidePopups();
                keys.type(input, "price > 20]");
                video.pause(800);
                run(video);
                video.pause(1200);
            });

            video.scene("hits", () -> {
                Node hits = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".fxt-query-hits").stream()
                        .filter(n -> n.isVisible() && n.localToScreen(n.getBoundsInLocal()) != null).findFirst().orElse(null));
                if (hits != null) {
                    Node cell = doc.fx(() -> hits.lookupAll(".list-cell").stream()
                            .filter(c -> c instanceof javafx.scene.control.ListCell<?> lc && !lc.isEmpty())
                            .skip(1).findFirst().orElse(null));
                    if (cell != null) {
                        video.click(cell);
                        video.pause(1200);
                    }
                }
                video.zoomOut();
                video.spoken(0.6);
                video.zoom(console());
                video.spoken(0.95);
            });

            video.scene("hits-views", () -> {
                video.zoomOut();
                doc.onFx(() -> host.setActiveViewMode(org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.GRAPHIC));
                video.pause(2500);
                Node hits = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".fxt-query-hits").stream()
                        .filter(n -> n.isVisible() && n.localToScreen(n.getBoundsInLocal()) != null).findFirst().orElse(null));
                if (hits != null) {
                    Node cell = doc.fx(() -> hits.lookupAll(".list-cell").stream()
                            .filter(c -> c instanceof javafx.scene.control.ListCell<?> lc && !lc.isEmpty())
                            .findFirst().orElse(null));
                    if (cell != null) {
                        video.click(cell);
                        video.pause(1500);
                    }
                }
                video.spoken(0.75);
                doc.onFx(() -> host.setActiveViewMode(org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.TEXT));
                video.pause(1200);
                video.spoken(0.95);
            });

            video.scene("functions", () -> {
                CodeArea input = input(false);
                setInput(input, "");
                keys.type(input, "count(//item)");
                run(video);
                video.pause(1200);
                video.spoken(0.4);
                setInput(input, "");
                keys.type(input, "sum(//item/(@quantity * @price))");
                run(video);
                video.pause(1500);
                video.spoken(0.95);
            });

            video.scene("error", () -> {
                CodeArea input = input(false);
                setInput(input, "");
                keys.type(input, "//item[@price > 20");
                run(video);
                video.pause(2000);
                video.spoken(0.9);
                setInput(input, "//item[@price > 20]");
            });

            video.scene("xquery", () -> {
                video.click(doc.<Node>find("#query-mode-xquery"));
                video.pause(600);
                CodeArea input = input(true);
                video.click(input);
                setInput(input, "");
                String query = """
                        for $item in //item
                        where $item/@price > 20
                        order by number($item/@price)
                        return <line title="{$item}" amount="{$item/@quantity * $item/@price}"/>""";
                for (String line : query.split("\n")) {
                    doc.onFx(() -> input.appendText((input.getLength() == 0 ? "" : "\n") + line));
                    video.pause(900);
                }
                video.spoken(0.6);
                run(video);
                video.pause(1500);
                video.spoken(0.95);
            });

            video.scene("table", () -> {
                video.click(doc.<Node>find("#query-show-table"));
                video.pause(1800);
                video.spoken(0.7);
                video.click(doc.<Node>find("#query-show-table"));
                video.pause(500);
            });

            video.scene("plan", () -> {
                video.moveTo(doc.<Node>find("#query-trace"));
                video.pause(800);
                video.moveTo(doc.<Node>find("#query-variables"));
                video.spoken(0.45);
                video.click(doc.<Node>find("#query-show-plan"));
                video.pause(2000);
                javafx.stage.Window w = keys.dialog();
                if (w != null) {
                    video.zoom(doc.fx(() -> new BoundingBox(w.getX(), w.getY(), w.getWidth(), w.getHeight())));
                }
                video.spoken(0.95);
                if (w != null) {
                    doc.onFx(w::hide);
                }
                video.zoom(console());
            });

            video.scene("snippets", () -> {
                Node snippets = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".menu-button").stream()
                        .filter(n -> n instanceof javafx.scene.control.MenuButton m
                                && I18n.t("QueryConsole.snippetsMenu.text").equals(m.getText()) && n.isVisible())
                        .findFirst().orElse(null));
                if (snippets != null) {
                    video.moveTo(snippets);
                }
                video.spoken(0.4);
                video.click(doc.<Node>find("#query-examples"));
                video.pause(2000);
                keys.hidePopups();
                video.spoken(0.95);
                video.zoomOut();
            });
            video.scene("query-docs", () -> {
                java.nio.file.Path example = java.nio.file.Path.of("release/examples/xquery/02-currency-consistency.xq").toAbsolutePath();
                if (java.nio.file.Files.exists(example)) {
                    doc.onFx(() -> host.openFile(example));
                    video.pause(1800);
                    Bounds editor = video.screenBounds(doc.find("#editorHost"));
                    video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.8, editor.getHeight() * 0.6));
                    video.spoken(0.45);
                    video.zoomOut();
                    Node input = doc.find("#doc-query-target");
                    if (input != null && doc.fx(input::isVisible)) {
                        video.click(input);
                        video.pause(1500);
                        video.spoken(0.8);
                        new VideoTyping(doc, video).hidePopups();
                    }
                }
                video.spoken(0.95);
            });

            video.scene("jsonpath", () -> video.spoken(0.95));
        } finally {
            video.stop();
        }
    }

    private Node console() {
        return doc.findType(QueryConsole.class);
    }

    private void xpathMode(DocVideoRecorder video) {
        video.click(doc.<Node>find("#query-mode-xpath"));
        video.pause(400);
    }

    private CodeArea input(boolean xquery) {
        QueryConsole console = doc.findType(QueryConsole.class);
        return doc.fx(() -> console.lookupAll(".fxt-query-input").stream()
                .filter(n -> n instanceof CodeArea c && c.isVisible())
                .map(CodeArea.class::cast).findFirst().orElseThrow());
    }

    private void setInput(CodeArea input, String text) {
        doc.onFx(() -> {
            input.replaceText(text);
            input.moveTo(text.length());
        });
    }

    /** Glides to the Run button (the first tool button of the console) and runs the query. */
    private void run(DocVideoRecorder video) {
        QueryConsole console = doc.findType(QueryConsole.class);
        Node runButton = doc.fx(() -> console.lookupAll(".fxt-tool-button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button && n.isVisible()
                        && n.localToScreen(n.getBoundsInLocal()) != null)
                .findFirst().orElse(null));
        if (runButton != null) {
            video.moveTo(runButton);
            video.pause(250);
        }
        doc.onFx(console::run);
    }
}
