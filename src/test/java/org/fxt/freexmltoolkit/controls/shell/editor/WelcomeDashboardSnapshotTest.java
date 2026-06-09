package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
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

/** Manual aid (gated by {@code FXT_SHELL_SNAPSHOT}): snapshots the Welcome dashboard with stat cards. */
@ExtendWith(ApplicationExtension.class)
class WelcomeDashboardSnapshotTest {

    private EditorWelcomePane pane;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        pane = new EditorWelcomePane(t -> {
        }, () -> {
        }, f -> {
        }, () -> {
        }, a -> {
        });
        pane.setRecentFiles(List.of(new File("FundsXML_306.xml"), new File("order.xsd"),
                new File("transform.xslt")));
        pane.setStats(new EditorWelcomePane.WelcomeStats(8, 4, 15, 6));
        Scene scene = new Scene(pane, 1180, 780);
        var css = getClass().getResource("/css/unified-shell.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void snapshotDashboard() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        java.nio.file.Files.createDirectories(out);
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        var img = WaitForAsyncUtils.waitForAsyncFx(5000, () -> pane.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out.resolve("welcome_dashboard.png").toFile());
    }
}
