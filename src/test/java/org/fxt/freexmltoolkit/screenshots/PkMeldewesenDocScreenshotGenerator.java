package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Path;
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
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.fxt.freexmltoolkit.plugins.pkmeldewesen.PkMeldewesenPanel;
import org.fxt.freexmltoolkit.plugins.pkmeldewesen.PkMeldewesenPlugin;
import org.fxt.freexmltoolkit.plugins.pkmeldewesen.PkSettings;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Generates the screenshots of the PK-Meldewesen page ({@code docs/img/plugin-pk-meldewesen*.png}) from
 * the bundled example report (a fictitious pension fund): the side panel with the FMA rule findings
 * and the rule report. Run with {@code xvfb-run ./gradlew docScreenshots}.
 */
@ExtendWith(ApplicationExtension.class)
class PkMeldewesenDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");

    @TempDir
    static Path store;
    private String previousEnabled;
    private Path previousTemplates;
    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        ServiceRegistry.initialize();
        System.setProperty("fxt.pk.dir", store.toString());
        // the extension's templates go to a temporary folder, never into release/examples/templates
        var templateFiles = org.fxt.freexmltoolkit.service.TemplateFileService.getInstance();
        previousTemplates = templateFiles.getTemplatesDirectoryPath();
        templateFiles.setTemplatesDirectory(store.resolve("templates"));
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        props.set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        previousEnabled = props.get(PkSettings.ENABLED);
        props.set(PkSettings.ENABLED, "true");
        PluginManager.getInstance().resetForTesting();
        PluginManager.getInstance().loadAll();
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @AfterEach
    void restore() {
        ServiceRegistry.get(PropertiesService.class).set(PkSettings.ENABLED, previousEnabled == null ? "" : previousEnabled);
        System.clearProperty("fxt.pk.dir");
        org.fxt.freexmltoolkit.service.TemplateFileService.getInstance().setTemplatesDirectory(previousTemplates);
        PluginManager.getInstance().resetForTesting();
    }

    @Test
    void generatePkMeldewesenScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle(600);

        // the bundled example report (fictitious data), opened from the panel
        PkMeldewesenPanel panel = showActivity();
        onFx(panel::onOpenExample);
        settle(1500);
        onFx(host::runValidation);
        settle(6000);
        panel = showActivity(); // validation shows its own panel; the PK panel is the subject
        settle(800);
        shot("plugin-pk-meldewesen");

        onFx(panel::onRuleReport);
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> ScreenshotFx.call("pk-screenshot",
                () -> host.getActiveDocument().map(d -> d.getDisplayName().endsWith("-pruefbericht.html")).orElse(false)));
        settle(2500);
        shot("plugin-pk-meldewesen-report");

    }

    private PkMeldewesenPanel showActivity() throws Exception {
        onFx(() -> shell.getSelectionModel().select(Activity.fromId(PkMeldewesenPlugin.ID).orElseThrow()));
        settle(800);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> visiblePanel() != null);
        return visiblePanel();
    }

    private PkMeldewesenPanel visiblePanel() {
        return ScreenshotFx.call("pk-screenshot", () -> {
            for (Node node : root.lookupAll("*")) {
                if (node instanceof PkMeldewesenPanel panel && shown(panel)) {
                    return panel;
                }
            }
            return null;
        });
    }

    private static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    private void onFx(Runnable action) {
        ScreenshotFx.run("pk-screenshot", action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = ScreenshotFx.call("pk-screenshot", () -> shell.snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[pk-screenshot] wrote " + out.getAbsolutePath()
                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
    }
}
