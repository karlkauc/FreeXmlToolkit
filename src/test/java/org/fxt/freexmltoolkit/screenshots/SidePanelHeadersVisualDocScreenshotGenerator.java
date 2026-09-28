package org.fxt.freexmltoolkit.screenshots;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Visual verification of the side-panel headers (title colour, spacing, indent, background) across
 * all activities. Output goes to {@code build/visual-verify/side-panels/}.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*SidePanelHeadersVisualDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class SidePanelHeadersVisualDocScreenshotGenerator {

    private static final File OUT_DIR = new File("build/visual-verify/side-panels");

    private Parent root;

    @Start
    void start(Stage stage) throws Exception {
        ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1400, 900));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void captureSidePanels() throws Exception {
        OUT_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        UnifiedShellView shell = (UnifiedShellView) root.lookup(".fxt-shell");
        settle(800);
        // Explorer is selected at startup (re-selecting it would collapse the panel): shoot it last.
        java.util.List<Activity> order = new java.util.ArrayList<>(java.util.List.of(Activity.values()));
        order.remove(Activity.SETTINGS);
        order.remove(Activity.EXPLORER);
        order.add(Activity.EXPLORER);
        for (Activity activity : order) {
            WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
                shell.getSelectionModel().select(activity);
                return null;
            });
            settle(700);
            var img = WaitForAsyncUtils.waitForAsyncFx(8000, () -> {
                var title = shell.lookup(".fxt-side-panel-title");
                Bounds b = title.localToScene(title.getBoundsInLocal());
                Bounds h = title.getParent().localToScene(title.getParent().getBoundsInLocal());
                System.out.printf("[visual] %s title x=%.1f y=%.1f header h=%.1f%n",
                        activity.id(), b.getMinX(), b.getMinY(), h.getHeight());
                SnapshotParameters params = new SnapshotParameters();
                // activity rail + side panel
                params.setViewport(new Rectangle2D(0, 0, 420, 900));
                return root.snapshot(params, null);
            });
            File out = new File(OUT_DIR, activity.id() + ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
            System.out.println("[visual] wrote " + out.getAbsolutePath());
        }
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
