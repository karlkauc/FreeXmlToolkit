package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.shell.editor.XsltProcessorDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.XsltProcessorsSettings;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorConfig;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorPreset;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the external XSLT processor images for {@code docs/guides/transform-query/index.md}: the list in Settings → XSLT
 * Processors and the dialog with the xsltproc preset after its Test run (needs {@code xsltproc} on the PATH for the
 * "Works" line; otherwise the image shows the "not found" outcome).
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*XsltProcessorsDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XsltProcessorsDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
                "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        stage.setScene(new Scene(new StackPane(), 400, 300));
        stage.show();
    }

    @AfterEach
    void forget() {
        XsltProcessorStore store = XsltProcessorStore.settings();
        store.save(List.of());
        store.setDefaultId(XsltProcessorStore.BUILTIN_ID);
    }

    @Test
    void generateXsltProcessorScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        XsltProcessorStore store = XsltProcessorStore.settings();
        XsltProcessorConfig xsltproc = XsltProcessorConfig.fromPreset(XsltProcessorPreset.XSLTPROC);
        XsltProcessorConfig saxon = new XsltProcessorConfig(null, "Saxon EE 12", XsltProcessorPreset.SAXON, "",
                XsltProcessorPreset.SAXON.arguments().replace("saxon-pe-12.jar", "/opt/saxon/saxon-ee-12.9.jar"), null, "", 0);
        store.save(List.of(saxon, xsltproc));
        store.setDefaultId(saxon.id());

        // the Settings card
        VBox card = ScreenshotFx.call("xslt-screenshot", () -> {
            org.fxt.freexmltoolkit.controls.shell.editor.SettingsCard settingsCard =
                    new org.fxt.freexmltoolkit.controls.shell.editor.SettingsCard();
            settingsCard.setTitle("XSLT Processors");
            settingsCard.setIconLiteral("bi-cpu");
            settingsCard.getChildren().add(new XsltProcessorsSettings());
            VBox box = new VBox(settingsCard);
            box.setStyle("-fx-padding: 16; -fx-pref-width: 600;");
            box.getStyleClass().add("fxt-settings-page");
            Scene scene = new Scene(box);
            for (String sheet : new String[] {"/css/design-tokens.css", "/css/unified-shell.css"}) {
                scene.getStylesheets().add(getClass().getResource(sheet).toExternalForm());
            }
            ThemeManager.apply(scene, false);
            stage.setScene(scene);
            stage.sizeToScene();
            return box;
        });
        settle(600);
        write(ScreenshotFx.call("xslt-screenshot", () -> card.snapshot(new SnapshotParameters(), null)), "xslt-processors-settings");

        // the dialog with the xsltproc preset, after Test
        XsltProcessorDialog dialog = ScreenshotFx.call("xslt-screenshot", () -> {
            XsltProcessorDialog d = new XsltProcessorDialog(null, java.util.Set.of());
            @SuppressWarnings("unchecked")
            ComboBox<XsltProcessorPreset> preset = (ComboBox<XsltProcessorPreset>) d.getDialogPane().lookup("#xslt-processor-preset");
            preset.setValue(XsltProcessorPreset.XSLTPROC);
            d.show();
            d.onFindOnPath();
            d.onTest();
            return d;
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> ScreenshotFx.call("xslt-screenshot", () -> {
            String status = ((Label) dialog.getDialogPane().lookup("#xslt-processor-test-status")).getText();
            return status != null && !status.startsWith("Transforming");
        }));
        settle(600);
        write(ScreenshotFx.call("xslt-screenshot", () -> dialog.getDialogPane().snapshot(new SnapshotParameters(), null)),
                "xslt-processor-dialog");
        ScreenshotFx.run("xslt-screenshot", dialog::close);
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void write(javafx.scene.image.WritableImage image, String name) throws Exception {
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
        System.out.println("[xslt-screenshot] wrote " + out.getAbsolutePath());
    }
}
