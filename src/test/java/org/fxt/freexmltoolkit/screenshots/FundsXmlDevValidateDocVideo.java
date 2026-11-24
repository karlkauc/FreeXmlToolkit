package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
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
 * The screen part of "Validate and check data quality", film 7 of "FundsXML for developers"
 * (storyboard {@code videos/fundsxml/dev-validate.json}): validate a FundsXML delivery against the
 * schema and repair a wrong data type, then a second, schema-valid delivery whose position weights add
 * up to 120 %, found by the FundsXML data-quality checks, and the data-quality report.
 *
 * <pre>{@code
 * python3 scripts/make-video.py dev-validate --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDevValidateDocVideo {

    private static final String XSD_ERROR = "Global-Equity-Fund_2025-10_xsd-error.xml";
    private static final String WORDS = "2.5 million";
    private static final String WEIGHTS = "Global-Equity-Fund_2025-10_weights.xml";

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
        // before the film starts: the first delivery is open
        Path folder = FundsXmlVideoFiles.folder("dev-validate");
        doc.open(folder.resolve(XSD_ERROR), WORDS);
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("delivery", () -> {
                video.spoken(0.35);
                video.zoom(textBounds(host, "<FundsXML4 xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""));
                video.spoken(0.65);
                video.zoom(textBounds(host, "xsi:noNamespaceSchemaLocation=\"FundsXML4.xsd\""));
                video.spoken(1);
                video.zoomOut();
            });

            video.scene("validate", () -> {
                video.spoken(0.1);
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> !host.getActiveProblems().isEmpty());
                video.spoken(0.6);
                video.zoom(doc.<Node>find("#problems-panel-list"));
            });

            video.scene("jump", () -> {
                video.zoomOut();
                video.spoken(0.1);
                video.click(problemRow(WORDS));
                video.pause(600);
                video.zoom(lineAround(host, "<Amount ccy=\"EUR\">" + WORDS + "</Amount>"));
            });

            video.scene("repair", () -> {
                String snippet = WORDS;
                int at = host.getActiveText().orElseThrow().indexOf(snippet);
                Bounds value = charBounds(at, at + snippet.length());
                video.click(value.getMaxX(), value.getCenterY());
                video.retype(at, snippet.length(), "2517995.74");
                video.spoken(0.6);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> !hasErrorsOrWarnings(host));
                // the "valid" toast in the bottom right corner
                video.zoom(new BoundingBox(DocVideoRecorder.WIDTH - 420, DocVideoRecorder.HEIGHT - 190, 400, 150));
                video.spoken(1);
                video.zoomOut();
            });

            video.scene("second", () -> {
                doc.open(folder.resolve(WEIGHTS), "<TotalPercentage>20.4182</TotalPercentage>");
                // F8 goes to the focused editor: a click into the text first
                Bounds root = textBounds(host, "<FundsXML4 ");
                video.click(root.getMaxX() + 40, root.getCenterY());
                video.spoken(0.3);
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> !host.getActiveProblems().isEmpty());
            });

            video.scene("weights", () -> {
                video.zoom(doc.<Node>find("#problems-panel-list"));
                video.spoken(1);
            });

            video.scene("fix", () -> {
                video.zoomOut();
                video.spoken(0.08);
                video.click(problemRow("20.4182"));
                video.pause(600);
                video.zoom(lineAround(host, "<TotalPercentage>20.4182</TotalPercentage>"));
                video.spoken(0.6);
                String snippet = "20.4182";
                int at = host.getActiveText().orElseThrow().indexOf("<TotalPercentage>20.4182<") + "<TotalPercentage>".length();
                Bounds value = charBounds(at, at + snippet.length());
                video.click(value.getMaxX(), value.getCenterY());
                video.retype(at, snippet.length(), "0.4182");
                video.spoken(0.8);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> !hasErrorsOrWarnings(host));
            });

            video.scene("checks", () -> {
                video.spoken(0.05);
                video.zoom(statusBarAround(doc.<Node>find(".fxt-status-document-badge")));
                video.spoken(0.35);
                video.zoomOut();
            });

            video.scene("report", () -> {
                video.spoken(0.1);
                video.click(doc.<Node>find("#activity-fundsxml"));
                doc.waitUntilFx(10, () -> doc.find("#fundsxml-dq") != null);
                video.pause(700);
                Bounds button = video.screenBounds(doc.find("#fundsxml-dq"));
                video.zoom(new BoundingBox(button.getMinX() - 20, button.getMinY() - 60, 520, 160));
                video.spoken(0.25);
                video.click(doc.<Node>find("#fundsxml-dq"));
                doc.waitUntilFx(60, () -> host.getActiveDocument()
                        .map(d -> d.getDisplayName().endsWith("-dq.html")).orElse(false));
                video.pause(1200);
                video.zoomOut();
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.8, editor.getHeight() * 0.7));
                video.spoken(0.97);
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

    /** The row of the problems list whose text contains {@code text}. */
    private Node problemRow(String text) throws Exception {
        ListView<?> list = doc.find("#problems-panel-list");
        Node[] found = new Node[1];
        doc.waitUntilFx(10, () -> {
            for (Node cell : list.lookupAll(".list-cell")) {
                boolean match = cell instanceof Labeled l && l.getText() != null && l.getText().contains(text)
                        || cell.lookupAll(".label").stream().anyMatch(x -> x instanceof Labeled l2
                                && l2.getText() != null && l2.getText().contains(text));
                if (match) {
                    found[0] = cell;
                    return true;
                }
            }
            return false;
        });
        return found[0];
    }

    /** A region around {@code snippet} in the active text: the line with some lines above and below. */
    private Bounds lineAround(EditorHost host, String snippet) {
        Bounds b = textBounds(host, snippet);
        return new BoundingBox(b.getMinX() - 220, b.getMinY() - 130, Math.max(900, b.getWidth() + 440), 300);
    }

    /** The status bar to the left and right of {@code badge}. */
    private Bounds statusBarAround(Node badge) {
        Bounds b = doc.fx(() -> badge.localToScreen(badge.getBoundsInLocal()));
        return new BoundingBox(b.getMinX() - 500, b.getMinY() - 160, 900, 200);
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
        CodeArea area = doc.fx(() -> doc.host().getActiveCodeArea());
        Bounds b = doc.fx(() -> area.getCharacterBoundsOnScreen(from, to).orElseThrow());
        return new BoundingBox(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
    }
}
