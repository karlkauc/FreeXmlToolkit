package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of the tutorial video "Automate with the command line fxt-cli" (storyboard
 * {@code videos/command-line.json}, after {@code docs/automate/command-line.md}). The recorder cannot film a
 * terminal: the film shows a real fxt-cli session (captured from fxt-cli, {@code videos/fxt-cli-session.txt}
 * in the test resources) and the GitHub Actions recipe of {@code docs/automate/ci-recipes.md} in the editor.
 *
 * <pre>{@code
 * python3 scripts/make-video.py command-line --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class CommandLineDocVideo {

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
        Path dir = DocFiles.work("command-line");
        Path session = dir.resolve("fxt-cli-session.txt");
        Path ci = dir.resolve("xml-checks.yml");
        try (var in = getClass().getResourceAsStream("/videos/fxt-cli-session.txt")) {
            Files.write(session, in.readAllBytes());
        }
        try (var in = getClass().getResourceAsStream("/videos/xml-checks.yml")) {
            Files.write(ci, in.readAllBytes());
        }
        doc.open(ci, "XML checks");
        doc.open(session, "fxt-cli validate");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("why", () -> {
                video.spoken(0.6);
                zoomTo(video, host, "$ fxt-cli validate", 3);
                video.spoken(0.95);
            });
            video.scene("validate", () -> {
                zoomTo(video, host, "$ fxt-cli validate", 5);
                video.spoken(0.95);
            });
            video.scene("exit-codes", () -> {
                zoomTo(video, host, "[exit code 1]", 2);
                video.spoken(0.95);
            });
            video.scene("query", () -> {
                zoomTo(video, host, "$ fxt-cli query -e \"count", 5);
                video.spoken(0.55);
                zoomTo(video, host, "$ fxt-cli query -e \"sum", 3);
                video.spoken(0.95);
            });
            video.scene("transform", () -> {
                zoomTo(video, host, "$ fxt-cli transform", 2);
                video.spoken(0.5);
                zoomTo(video, host, "$ fxt-cli convert", 2);
                video.spoken(0.7);
                zoomTo(video, host, "$ fxt-cli compare", 4);
                video.spoken(0.95);
            });
            video.scene("projects", () -> {
                video.zoomOut();
                video.spoken(0.95);
            });
            video.scene("ci", () -> {
                doc.open(ci, "XML checks");
                video.pause(800);
                zoomTo(video, host, "name: XML checks", 14);
                video.spoken(0.5);
                zoomTo(video, host, "- name: Validate XML", 6);
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    private void zoomTo(DocVideoRecorder video, EditorHost host, String snippet, int lines) {
        int at = host.getActiveText().orElse("").indexOf(snippet);
        if (at < 0) {
            System.out.println("[video] not in text: " + snippet);
            return;
        }
        CodeArea area = host.getActiveCodeArea();
        if (area == null) {
            return;
        }
        Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElse(null));
        if (line != null) {
            video.zoom(new BoundingBox(line.getMinX() - 60, line.getMinY() - 30, 1300, line.getHeight() * (lines + 2)));
        }
    }
}
