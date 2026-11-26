package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of "Generating FundsXML", film 4 of "FundsXML in production" (storyboard
 * {@code videos/fundsxml/prod-generating.json}): an export of the reference implementation
 * (github.com/fundsxml/integration-examples, {@code export/export.py}) whose share class names its
 * Currency before its Names, rejected by the schema; then the correct export, valid and without
 * data-quality errors.
 *
 * <pre>{@code
 * python3 scripts/make-video.py prod-generating --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlProdGeneratingDocVideo {

    private static final String ORDER_ERROR = "Example-Export_Multi-Asset_2025-10_order-error.xml";
    private static final String EXPORT = "Example-Export_Multi-Asset_2025-10.xml";
    private static final String SWAPPED = "<Currency>EUR</Currency>\n            <Names>";

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        FundsXmlVideoFiles.enableExtension();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        // before the film starts: the faulty export is open at the share class
        Path folder = FundsXmlVideoFiles.folder("prod-generating");
        doc.open(folder.resolve(ORDER_ERROR), SWAPPED);
        // the share class is far down in the file: show it in the upper third
        CodeArea area = doc.fx(() -> host.getActiveCodeArea());
        int at = host.getActiveText().orElseThrow().indexOf(SWAPPED);
        doc.onFx(() -> area.showParagraphAtTop(Math.max(0,
                area.offsetToPosition(at, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor() - 8)));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("order", () -> {
                video.spoken(0.08);
                video.zoom(lineAround(host, SWAPPED));
                video.spoken(0.3);
                Bounds line = textBounds(host, SWAPPED);
                video.zoomOut();
                // F8 goes to the focused editor: a click into the text first
                video.click(line.getMaxX() + 40, line.getCenterY());
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> !host.getActiveProblems().isEmpty());
                video.pause(500);
                video.zoom(doc.<Node>find("#problems-panel-list"));
                video.spoken(1);
                video.zoomOut();
            });

            video.scene("validate", () -> {
                doc.open(folder.resolve(EXPORT), "<!-- Synthetic sample");
                Bounds root = textBounds(host, "<FundsXML4 ");
                video.click(root.getMaxX() + 40, root.getCenterY());
                video.zoom(textBounds(host, "<!-- Synthetic sample: written by export/export.py"));
                video.spoken(0.25);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> !hasErrorsOrWarnings(host));
                video.pause(1500);
                // the "valid" toast in the bottom right corner
                video.zoom(new BoundingBox(DocVideoRecorder.WIDTH - 420, DocVideoRecorder.HEIGHT - 190, 400, 150));
                video.spoken(0.55);
                video.zoomOut();
                Node badge = doc.find(".fxt-status-document-badge");
                Bounds b = doc.fx(() -> badge.localToScreen(badge.getBoundsInLocal()));
                video.zoom(new BoundingBox(b.getMinX() - 500, b.getMinY() - 160, 900, 200));
                video.spoken(1);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    /** Whether the active document has problems other than hints. */
    private static boolean hasErrorsOrWarnings(EditorHost host) {
        return host.getActiveProblems().stream().anyMatch(p -> "error".equalsIgnoreCase(p.severity())
                || "warning".equalsIgnoreCase(p.severity()) || "fatal".equalsIgnoreCase(p.severity()));
    }

    /** A region around {@code snippet} in the active text: the lines with some lines above and below. */
    private Bounds lineAround(EditorHost host, String snippet) {
        Bounds b = textBounds(host, snippet);
        return new BoundingBox(b.getMinX() - 220, b.getMinY() - 130, Math.max(900, b.getWidth() + 440), 330);
    }

    /** The screen region of {@code snippet} in the active text. */
    private Bounds textBounds(EditorHost host, String snippet) {
        int at = host.getActiveText().orElseThrow().indexOf(snippet);
        if (at < 0) {
            throw new IllegalStateException("Not in the document: " + snippet);
        }
        CodeArea area = doc.fx(() -> host.getActiveCodeArea());
        Bounds b = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + Math.min(snippet.length(), snippet.indexOf('\n') < 0
                ? snippet.length() : snippet.indexOf('\n'))).orElseThrow());
        return new BoundingBox(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
    }
}
