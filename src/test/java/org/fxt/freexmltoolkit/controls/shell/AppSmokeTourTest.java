package org.fxt.freexmltoolkit.controls.shell;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Guided real-app smoke tour: boots the full application ({@code main.fxml} +
 * {@code MainController}, which now lands on the Unified Shell), drives every
 * activity, and writes a series of screenshots to {@code /tmp/fxt_smoke/}.
 *
 * <p>Gated by {@code FXT_SHELL_SNAPSHOT=true} so it does not run (or slow) the
 * normal suite — it is a manual verification aid.
 */
@ExtendWith(ApplicationExtension.class)
class AppSmokeTourTest {

    private static final Path OUT = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/main.fxml"));
        root = loader.load();
        // Stay at/under the Monocle headless screen to avoid a blit overflow.
        stage.setScene(new Scene(root, 1260, 780));
        stage.show();
    }

    @Test
    void tourAllActivities() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Files.createDirectories(OUT);

        // The app boots into the shell.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle();
        shot("01_boot_welcome");

        // Open a real XSD: Explorer + Text.
        File xsd = new File("src/test/resources/purchageOrder.xsd");
        onFx(() -> host.openFile(xsd.toPath()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PurchaseOrder")).orElse(false));
        settle();
        shot("02_xsd_text_explorer");

        // Schema activity: Tree, then Graphic.
        onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
        onFx(() -> host.setActiveViewMode(ViewMode.TREE));
        settle();
        shot("03_schema_tree");
        onFx(() -> host.setActiveViewMode(ViewMode.GRAPHIC));
        settle();
        shot("04_schema_graphic");
        onFx(() -> host.setActiveViewMode(ViewMode.TEXT));

        // Validation, Transform activities.
        onFx(() -> shell.getSelectionModel().select(Activity.VALIDATION));
        settle();
        shot("05_validation");
        onFx(() -> shell.getSelectionModel().select(Activity.TRANSFORM));
        settle();
        shot("06_transform");

        // JSON document + JSON Tree.
        Path json = OUT.resolve("sample.json");
        Files.writeString(json, "{\"fund\":{\"id\":\"EAM\",\"items\":[1,2,3],\"active\":true}}");
        onFx(() -> host.openFile(json));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("fund")).orElse(false));
        onFx(() -> host.setActiveViewMode(ViewMode.TREE));
        settle();
        shot("07_json_tree");

        // PDF/FOP, Signature, Favorites, Help, Settings activities.
        onFx(() -> shell.getSelectionModel().select(Activity.PDF_FOP));
        settle();
        shot("08_pdf_fop");
        onFx(() -> shell.getSelectionModel().select(Activity.SIGNATURE));
        settle();
        shot("09_signature");
        onFx(() -> shell.getSelectionModel().select(Activity.FAVORITES));
        settle();
        shot("10_favorites");
        onFx(() -> shell.getSelectionModel().select(Activity.HELP));
        settle();
        shot("11_help");
        onFx(() -> shell.getSelectionModel().select(Activity.SETTINGS));
        settle();
        shot("12_settings");
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle() {
        WaitForAsyncUtils.sleep(400, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(5000, () -> root.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", OUT.resolve(name + ".png").toFile());
    }
}
