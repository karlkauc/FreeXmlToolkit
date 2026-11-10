package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the appearance settings for the documentation ({@code docs/img/}): the Appearance
 * cards of the Settings page, and the same page in an accent colour, light and dark.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*AppearanceDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class AppearanceDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");

    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
                "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateAppearanceScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        try {
            onFx(() -> ThemeManager.apply(root.getScene(), false));

            // The Settings page scrolled to its Appearance cards
            onFx(() -> shell.getSelectionModel().select(Activity.SETTINGS));
            settle(800);
            shot("unified-shell-settings-appearance");

            // The same page in the violet accent, light and dark, set through the page itself
            SettingsPanel panel = findSettings(root);
            onFx(() -> panel.applyAccent("violet"));
            settle(800);
            shot("unified-shell-accent-violet");
            onFx(() -> panel.applyTheme(true));
            settle(800);
            shot("unified-shell-accent-violet-dark");
            onFx(() -> {
                panel.applyAccent("blue");
                panel.applyTheme(false);
            });

            // Settings → Editor → Editor font, then an XML document in that font
            var font = new org.fxt.freexmltoolkit.controls.shared.EditorFont.Settings(
                    org.fxt.freexmltoolkit.controls.shared.EditorFont.DEFAULT_FAMILY, 13, 1.4, true);
            onFx(() -> {
                panel.applyEditorFont(font);
                panel.showSection("editor");
            });
            settle(800);
            shot("unified-shell-settings-editor-font");
            var host = shell.getEditorHost();
            java.nio.file.Path xml = java.nio.file.Path.of("src/main/resources/examples/getting-started/order.xml");
            onFx(() -> host.openFile(xml.toAbsolutePath()));
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("<order")).orElse(false));
            settle(1200);
            shot("unified-shell-editor-font-spacing");
            onFx(() -> panel.applyEditorFont(org.fxt.freexmltoolkit.controls.shared.EditorFont.Settings.defaults()));

            // UI text size: the Settings card, then the shell in Extra Large
            onFx(() -> {
                panel.applyUiFont(org.fxt.freexmltoolkit.controls.theme.Typography.Size.LARGE, null);
                shell.getSelectionModel().select(Activity.EXPLORER);
                shell.getSelectionModel().select(Activity.SETTINGS);
                panel.showSection("appearance");
            });
            settle(1000);
            shot("unified-shell-settings-ui-font");
            onFx(() -> {
                panel.applyUiFont(org.fxt.freexmltoolkit.controls.theme.Typography.Size.XLARGE, null);
                shell.getSelectionModel().select(Activity.EXPLORER);
                host.openFile(xml.toAbsolutePath());
            });
            settle(1200);
            shot("unified-shell-ui-font-xlarge");
            onFx(() -> panel.applyUiFont(org.fxt.freexmltoolkit.controls.theme.Typography.Size.NORMAL, null));

            // Syntax colours: the card, then the XML document in Monokai, dark
            onFx(() -> {
                panel.applySyntaxScheme("monokai");
                shell.getSelectionModel().select(Activity.EXPLORER);
                shell.getSelectionModel().select(Activity.SETTINGS);
                panel.showSection("editor");
            });
            settle(1000);
            shot("unified-shell-settings-syntax-colours");
            onFx(() -> {
                panel.applyTheme(true);
                shell.getSelectionModel().select(Activity.EXPLORER);
                host.openFile(xml.toAbsolutePath());
            });
            settle(1200);
            shot("unified-shell-syntax-monokai-dark");
            onFx(() -> panel.applyTheme(false));
        } finally {
            onFx(() -> {
                org.fxt.freexmltoolkit.controls.shared.EditorFont.update(
                        org.fxt.freexmltoolkit.controls.shared.EditorFont.Settings.defaults());
                ThemeManager.setTypography(null, null);
                ThemeManager.resetSyntaxColors();
            });
            onFx(() -> {
                SettingsPanel panel = findSettings(root);
                if (panel != null) {
                    panel.applyAccent("blue");
                    panel.applyTheme(false);
                } else {
                    ThemeManager.setAccent(null);
                    ThemeManager.apply(root.getScene(), false);
                }
            });
        }
    }

    /** @return the Settings page in the node tree, or null */
    private static SettingsPanel findSettings(Node node) {
        if (node instanceof SettingsPanel panel) {
            return panel;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                SettingsPanel found = findSettings(child);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof javafx.scene.control.TabPane tabs) {
            for (var tab : tabs.getTabs()) {
                SettingsPanel found = tab.getContent() == null ? null : findSettings(tab.getContent());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void onFx(Runnable action) {
        ScreenshotFx.run("appearance-screenshot", action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = ScreenshotFx.call("appearance-screenshot", () -> {
            Node target = shell != null ? shell : root;
            return target.snapshot(new SnapshotParameters(), null);
        });
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[appearance-screenshot] wrote " + out.getAbsolutePath());
    }
}
