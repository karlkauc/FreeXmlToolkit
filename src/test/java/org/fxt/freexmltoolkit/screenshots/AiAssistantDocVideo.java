package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "AI assistant and MCP server" (storyboard
 * {@code videos/ai-assistant.json}, after {@code docs/guides/ai-assistant.md} and
 * {@code docs/automate/mcp-server.md}). No model answers here: the film shows the settings, the entry points
 * and a real {@code fxt-cli mcp --list-tools} output.
 *
 * <pre>{@code
 * python3 scripts/make-video.py ai-assistant --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class AiAssistantDocVideo {

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
        Path tools = DocFiles.work("mcp").resolve("fxt-cli-mcp-tools.txt");
        Path setup = DocFiles.work("mcp").resolve("mcp-setup.txt");
        try (var in = getClass().getResourceAsStream("/videos/mcp-setup.txt")) {
            Files.write(setup, in.readAllBytes());
        }
        Path guide = Path.of("docs/guides/ai-assistant.md").toAbsolutePath();
        try (var in = getClass().getResourceAsStream("/videos/fxt-cli-mcp-tools.txt")) {
            Files.write(tools, in.readAllBytes());
        }
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("what", () -> video.spoken(0.95));

            video.scene("enable", () -> {
                doc.onFx(shell::openSettings);
                video.pause(1500);
                SettingsPanel settings = doc.findType(SettingsPanel.class);
                if (settings != null) {
                    doc.onFx(() -> settings.showSection("ai"));
                    video.pause(800);
                }
                Node enabled = doc.find("#settings-ai-enabled");
                if (enabled != null) {
                    video.zoom(new BoundingBox(video.screenBounds(enabled).getMinX() - 40,
                            video.screenBounds(enabled).getMinY() - 80, 1100, 600));
                    video.spoken(0.4);
                    if (enabled instanceof javafx.scene.control.CheckBox box && !doc.fx(box::isSelected)) {
                        video.click(enabled);
                    }
                }
                video.spoken(0.95);
            });

            video.scene("provider", () -> {
                Node add = doc.find("#settings-ai-add");
                if (add != null && doc.fx(add::isVisible)) {
                    video.click(add);
                    video.pause(1500);
                    Window dialog = keys.dialog();
                    if (dialog != null) {
                        video.zoom(doc.fx(() -> new BoundingBox(dialog.getX(), dialog.getY(), dialog.getWidth(), dialog.getHeight())));
                        video.spoken(0.9);
                        doc.onFx(dialog::hide);
                    }
                }
                video.spoken(0.95);
            });

            video.scene("data", () -> {
                Node level = doc.find("#settings-ai-data-level");
                if (level != null) {
                    video.moveTo(level);
                    video.pause(800);
                    video.moveTo(doc.<Node>find("#settings-ai-data-level-remote"));
                }
                video.spoken(0.7);
                video.moveTo(doc.<Node>find("#settings-ai-preview"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("fix", () -> {
                doc.onFx(() -> host.openFile(xml));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042"));
                int at = host.getActiveText().orElseThrow().indexOf("quantity=\"2\"") + "quantity=\"".length();
                video.retype(at, 1, "two");
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(30, () -> !host.getActiveProblems().isEmpty());
                video.pause(800);
                ListView<?> problems = doc.find("#problems-panel-list");
                Bounds list = video.screenBounds(problems);
                video.rightClick(list.getMinX() + 300, list.getMinY() + 14);
                video.pause(1000);
                Node explain = null;
                try {
                    explain = video.menuItem(I18n.t("AiActions.explain"));
                } catch (Exception e) {
                    System.out.println("[video] no AI item in the problem menu");
                }
                if (explain != null) {
                    video.moveTo(explain);
                    video.pause(800);
                }
                video.spoken(0.6);
                keys.hidePopups();
                video.retype(at, 3, "2");
                video.spoken(0.95);
            });

            video.scene("query", () -> {
                doc.onFx(shell::openQueryConsole);
                video.pause(1500);
                Node ask = doc.find("#query-ask-ai");
                if (ask != null && doc.fx(ask::isVisible)) {
                    video.moveTo(ask);
                }
                video.spoken(0.95);
                doc.onFx(shell::toggleQueryConsole);
            });

            video.scene("stylesheet", () -> {
                video.click(doc.<Node>find("#activity-transform"));
                video.pause(1200);
                Node generate = doc.find("#transform-tool-ai-generate");
                if (generate != null && doc.fx(generate::isVisible)) {
                    video.zoom(doc.<Node>find("#leftPanelWrapper"));
                    video.moveTo(generate);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("followups", () -> video.spoken(0.95));

            video.scene("unverified", () -> video.spoken(0.95));

            video.scene("models", () -> {
                if (Files.exists(guide)) {
                    doc.open(guide, "# AI Assistant");
                    var area = host.getActiveCodeArea();
                    int at = host.getActiveText().orElse("").indexOf("| | Run 1 | Run 2 |");
                    if (area != null && at > 0) {
                        doc.onFx(() -> area.showParagraphAtTop(Math.max(0, area.offsetToPosition(at,
                                org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor() - 3)));
                        video.pause(600);
                        Bounds editor = video.screenBounds(doc.find("#editorHost"));
                        video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.5));
                    }
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("mcp", () -> {
                doc.open(tools, "fxt-cli mcp");
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.6));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("mcp-setup", () -> {
                doc.open(setup, "claude mcp add");
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.75, editor.getHeight() * 0.55));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("mcp-use", () -> {
                doc.open(tools, "fxt-cli mcp");
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.6));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("mcp-safety", () -> video.spoken(0.95));
        } finally {
            video.stop();
        }
    }
}
