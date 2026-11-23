package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane;
import org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Run and debug an XSLT stylesheet" (storyboard
 * {@code videos/xslt-debug.json}, after {@code docs/guides/transform-query/run-xslt.md} and
 * {@code debug.md}): run the example stylesheet from the Transform panel, set a breakpoint in its
 * gutter, debug, continue to the next item, step, mute and run to the end.
 *
 * <pre>{@code
 * python3 scripts/make-video.py xslt-debug --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XsltDebugDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        org.fxt.freexmltoolkit.i18n.I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        // before the film starts: the example order is open, the Transform panel has it and the stylesheet
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        Path xml = folder.resolve("order.xml");
        Path xslt = folder.resolve("order-summary.xslt");
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.activity(Activity.TRANSFORM);
        doc.settle(800);
        TransformPanel panel = doc.findType(TransformPanel.class);
        doc.onFx(() -> {
            panel.setRunAutomatically(false);
            panel.setXsltFile(xslt.toFile());
            panel.setInputOverride(xml.toFile());
        });
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("run", () -> {
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.45);
                video.click(doc.<Node>find("#transform-run"));
                video.pause(1500);
                video.zoomOut();
                video.spoken(0.75);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getCenterY(), editor.getWidth(), editor.getHeight() / 2));
            });

            video.scene("breakpoint", () -> {
                video.zoomOut();
                video.spoken(0.2);
                doc.onFx(() -> host.openFile(xslt));
                doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("xsl:stylesheet"));
                video.pause(1200);
                String text = host.getActiveText().orElseThrow();
                int at = text.indexOf("select=\"@quantity\"");
                CodeArea area = activeArea(host);
                Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElseThrow());
                video.zoom(new BoundingBox(line.getMinX() - 420, line.getMinY() - 140, 900, 300));
                video.spoken(0.6);
                Node slot = doc.fx(() -> area.lookupAll("." + BreakpointGutterFactory.SLOT_STYLE_CLASS).stream()
                        .filter(n -> {
                            Bounds b = n.localToScreen(n.getBoundsInLocal());
                            return b != null && b.getMinY() <= line.getCenterY() && b.getMaxY() >= line.getCenterY();
                        }).findFirst().orElseThrow());
                video.click(slot);
                video.pause(600);
            });

            video.scene("debug", () -> {
                video.zoomOut();
                video.spoken(0.15);
                video.click(doc.<Node>find("#transform-debug"));
                doc.waitUntilFx(30, () -> paused());
                video.pause(800);
                Bounds pane = video.screenBounds(doc.findType(XsltDebugPane.class));
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), pane.getMaxY() - editor.getMinY()));
            });

            video.scene("step", () -> {
                video.spoken(0.1);
                video.click(doc.<Node>find("#debug-continue"));
                video.pause(600);
                doc.waitUntilFx(20, () -> paused());
                video.spoken(0.6);
                video.click(doc.<Node>find("#debug-step-over"));
                video.pause(400);
                doc.waitUntilFx(20, () -> paused());
                video.pause(900);
                video.click(doc.<Node>find("#debug-step-over"));
                doc.waitUntilFx(20, () -> paused());
            });

            video.scene("finish", () -> {
                video.spoken(0.1);
                video.click(doc.<Node>find("#debug-mute"));
                video.spoken(0.4);
                video.click(doc.<Node>find("#debug-continue"));
                Labeled status = doc.find("#debug-status");
                // the run is over when the status reports its time ("Finished · 17146 ms · …")
                doc.waitUntilFx(60, () -> status.getText() != null && status.getText().contains(" ms"));
                video.pause(1200);
                System.out.println("[video] debugger status: " + doc.fx(status::getText));
                video.click(doc.<Node>find("#bottom-dock-output"));
                video.pause(600);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getCenterY(), editor.getWidth(), editor.getHeight() / 2));
                video.pause(3000); // the output panel takes a moment to show under the recording's software rendering
            });
        } finally {
            video.stop();
        }
    }

    /** @return whether the debugger is paused (Continue is enabled), read on the FX thread */
    private boolean paused() {
        Node node = doc.stage().getScene().getRoot().lookup("#debug-continue");
        return node instanceof ButtonBase button && !button.isDisabled() && button.isVisible();
    }

    /** @return the code area of the active tab */
    private CodeArea activeArea(EditorHost host) {
        return doc.fx(() -> host.lookupAll(".code-area").stream()
                .filter(n -> n instanceof CodeArea && n.localToScreen(n.getBoundsInLocal()) != null && n.isVisible()
                        && treeVisible(n) && ((CodeArea) n).getText().contains("xsl:stylesheet"))
                .map(CodeArea.class::cast).findFirst().orElseThrow());
    }

    private static boolean treeVisible(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }
}
