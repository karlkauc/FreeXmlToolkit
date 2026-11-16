package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.GleifSettings;
import org.fxt.freexmltoolkit.controls.shell.editor.SettingsCard;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.plugins.IdentifierKind;
import org.fxt.freexmltoolkit.plugins.IndustryPanel;
import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.GleifClient;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the GLEIF images for {@code docs/gleif.md}: the identifier checker of an extension panel after a lookup
 * (GLEIF's own, public LEI from a saved answer - no network) and the Settings card.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*GleifDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class GleifDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        stage.setScene(new Scene(new StackPane(), 400, 300));
        stage.show();
    }

    @AfterEach
    void realGleif() {
        Gleif.setCache(null);
    }

    @Test
    void generateGleifScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        Gleif.setCache(new LeiCache(new GleifClient(uri -> {
            try (var in = getClass().getResourceAsStream("/gleif/lei-records.json")) {
                return new GleifClient.Response(200, in.readAllBytes());
            }
        }, java.time.Clock.systemUTC()), null, java.time.Clock.systemUTC(), java.time.Duration.ofHours(1)));

        IndustryPanel panel = ScreenshotFx.call("gleif-screenshot", () -> {
            EditorHost host = new EditorHost();
            PluginUi ui = new PluginUi() {
                @Override public EditorHost editorHost() { return host; }
                @Override public Window window() { return stage; }
                @Override public void openFile(Path file) { }
                @Override public void showActivity(String id) { }
                @Override public void refreshContributions() { }
                @Override public void showEditorBar(Node bar) { }
                @Override public void hideEditorBar() { }
            };
            IndustryPanel p = new IndustryPanel(ui, "KAG-MELDEWESEN", "AIFMD, MMF and 4. DeRiMV reports to the FMA.",
                    List.of(), c -> null, (content, path, name) -> List.of(), List.of(IdentifierKind.LEI, IdentifierKind.ISIN));
            p.setPrefWidth(320);
            Scene scene = new Scene(new StackPane(p), 320, 720);
            for (String sheet : new String[] {"/css/design-tokens.css", "/css/unified-shell.css"}) {
                scene.getStylesheets().add(getClass().getResource(sheet).toExternalForm());
            }
            ThemeManager.apply(scene, false);
            stage.setScene(scene);
            stage.sizeToScene();
            @SuppressWarnings("unchecked")
            ComboBox<IdentifierKind> kind = (ComboBox<IdentifierKind>) p.lookup("#industry-identifier-kind");
            kind.setValue(IdentifierKind.LEI);
            ((TextField) p.lookup("#industry-identifier")).setText("506700GE1G29325QX363");
            ((Button) p.lookup("#industry-identifier-gleif")).fire();
            return p;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> ScreenshotFx.call("gleif-screenshot",
                () -> ((VBox) panel.lookup("#industry-identifier-gleif-result")).getChildren().size() > 1));
        settle(600);
        write(ScreenshotFx.call("gleif-screenshot", () -> panel.snapshot(new SnapshotParameters(), null)), "gleif-identifier-checker");

        VBox card = ScreenshotFx.call("gleif-screenshot", () -> {
            SettingsCard settingsCard = new SettingsCard();
            settingsCard.setTitle("GLEIF (LEI lookup)");
            settingsCard.setIconLiteral("bi-globe2");
            settingsCard.getChildren().add(new GleifSettings());
            VBox box = new VBox(settingsCard);
            box.setStyle("-fx-padding: 16; -fx-pref-width: 560;");
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
        write(ScreenshotFx.call("gleif-screenshot", () -> card.snapshot(new SnapshotParameters(), null)), "gleif-settings");
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void write(javafx.scene.image.WritableImage image, String name) throws Exception {
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
        System.out.println("[gleif-screenshot] wrote " + out.getAbsolutePath());
    }
}
