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
                video.zoomOut();
            });

            video.scene("document", () -> {
                video.spoken(0.35);
                video.zoom(textBounds(host, "xsi:noNamespaceSchemaLocation=\"order.xsd\""));
                video.spoken(1);
                video.zoomOut();
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
            });
        } finally {
            video.stop();
        }
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
