package org.fxt.freexmltoolkit.screenshots;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.AdminPolicyTestAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of the tutorial video "Manage FreeXmlToolkit in a company" (storyboard
 * {@code videos/administration.json}, after {@code docs/administer/policies.md}). The policy and defaults
 * files of the test resources are shown in the editor and installed as the current {@code AdminPolicy}
 * before the shell starts, so the settings page shows the notice and the locked controls.
 *
 * <pre>{@code
 * python3 scripts/make-video.py administration --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class AdministrationDocVideo {

    private final DocShell doc = new DocShell("video");
    private Path policy;
    private Path defaults;

    @Start
    void start(Stage stage) throws Exception {
        Path dir = DocFiles.work("administration");
        policy = dir.resolve("policy.properties");
        defaults = dir.resolve("defaults.properties");
        String policyText = resource("/videos/policy.properties");
        String defaultsText = resource("/videos/defaults.properties");
        Files.writeString(policy, policyText);
        Files.writeString(defaults, defaultsText);
        AdminPolicyTestAccess.setCurrent(Path.of("/etc/freexmltoolkit/policy.properties"),
                properties(policyText), properties(defaultsText));
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @AfterEach
    void resetPolicy() {
        AdminPolicyTestAccess.reset();
    }

    @Test
    void record() throws Exception {
        var shell = doc.boot();
        EditorHost host = doc.host();
        Path guide = Path.of("docs/administer/policies.md").toAbsolutePath();
        doc.open(defaults, "defaults.properties");
        doc.open(policy, "policy.properties");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("files", () -> {
                zoomTo(video, host, "# /etc/freexmltoolkit/policy.properties", 8);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("where", () -> {
                zoomTo(video, host, "# /etc/freexmltoolkit/policy.properties", 3);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("defaults", () -> {
                doc.open(defaults, "defaults.properties");
                zoomTo(video, host, "useSystemProxy", 7);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("proxy", () -> {
                doc.open(defaults, "defaults.properties");
                zoomTo(video, host, "manualProxy", 5);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("locked", () -> {
                doc.onFx(shell::openSettings);
                video.pause(1800);
                Node notice = doc.find("#settings-policy-notice");
                if (notice != null && doc.fx(notice::isVisible)) {
                    Bounds b = video.screenBounds(notice);
                    video.zoom(new BoundingBox(b.getMinX() - 30, b.getMinY() - 40, 1200, 160));
                } else {
                    System.out.println("[video] no policy notice");
                }
                video.spoken(0.35);
                video.zoomOut();
                show("appearance");
                Node swatches = doc.find("#settings-accent-swatches");
                if (swatches != null) {
                    Bounds b = video.screenBounds(swatches);
                    video.zoom(new BoundingBox(b.getMinX() - 200, b.getMinY() - 60, 1200, 240));
                    video.moveTo(swatches);
                }
                video.spoken(0.65);
                video.zoomOut();
                show("privacy");
                Node usage = doc.find("#settings-telemetry-usage");
                if (usage != null) {
                    Bounds b = video.screenBounds(usage);
                    video.zoom(new BoundingBox(b.getMinX() - 200, b.getMinY() - 60, 1200, 240));
                    video.moveTo(usage);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("check", () -> {
                doc.onFx(shell::openSettings);
                video.pause(1200);
                Node notice = doc.find("#settings-policy-notice");
                if (notice != null && doc.fx(notice::isVisible)) {
                    Bounds b = video.screenBounds(notice);
                    video.zoom(new BoundingBox(b.getMinX() - 30, b.getMinY() - 40, 1200, 160));
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("features", () -> {
                show("extensions");
                video.spoken(0.45);
                doc.open(policy, "policy.properties");
                zoomTo(video, host, "# New versions", 14);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("updates", () -> {
                video.click(doc.<Node>find("#menu-help"));
                video.pause(1200);
                video.spoken(0.6);
                new VideoTyping(doc, video).hidePopups();
                video.spoken(0.95);
            });

            video.scene("install", () -> {
                if (Files.exists(guide)) {
                    doc.open(guide, "# Administration");
                    zoomTo(video, host, "## Silent installation", 9);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("offline", () -> video.spoken(0.95));

            video.scene("security", () -> {
                doc.open(policy, "policy.properties");
                zoomTo(video, host, "# Security switches", 5);
                video.spoken(0.7);
                zoomTo(video, host, "# No telemetry", 4);
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    private void show(String group) {
        SettingsPanel settings = doc.findType(SettingsPanel.class);
        if (settings != null) {
            doc.onFx(() -> settings.showSection(group));
            doc.settle(800);
        }
    }

    private void zoomTo(DocVideoRecorder video, EditorHost host, String snippet, int lines) {
        int at = host.getActiveText().orElse("").indexOf(snippet);
        CodeArea area = host.getActiveCodeArea();
        if (at < 0 || area == null) {
            System.out.println("[video] not in text: " + snippet);
            return;
        }
        doc.onFx(() -> area.showParagraphAtTop(Math.max(0, area.offsetToPosition(at, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor() - 2)));
        doc.settle(400);
        Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElse(null));
        if (line != null) {
            video.zoom(new BoundingBox(line.getMinX() - 60, line.getMinY() - 30, 1300, line.getHeight() * (lines + 2)));
        }
    }

    private static Properties properties(String text) throws java.io.IOException {
        Properties p = new Properties();
        p.load(new StringReader(text));
        return p;
    }

    private String resource(String name) throws java.io.IOException {
        try (var in = getClass().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
