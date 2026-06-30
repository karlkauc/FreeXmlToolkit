package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Manual aid (gated by {@code FXT_SHELL_SNAPSHOT}): snapshots the expanded Settings panel. */
@ExtendWith(ApplicationExtension.class)
class SettingsPanelSnapshotTest {

    private SettingsPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        panel = new SettingsPanel();
        Scene scene = new Scene(panel, 900, 1000);
        var css = getClass().getResource("/css/unified-shell.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void snapshotSettings() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        java.nio.file.Files.createDirectories(out);
        // Allow the off-thread GPU detection (RENDERING card) to populate before snapshotting.
        WaitForAsyncUtils.sleep(1500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        var img = WaitForAsyncUtils.waitForAsyncFx(5000, () -> panel.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out.resolve("settings_panel.png").toFile());
    }
}
