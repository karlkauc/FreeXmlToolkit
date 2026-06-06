package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
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
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Generates the <strong>Unified Shell</strong> documentation screenshots into
 * {@code docs/img/} (file names {@code unified-shell-*.png}).
 *
 * <p>Boots the real application ({@code main.fxml} + {@code MainController}, which lands on the
 * Unified Shell), then drives the shell's activities and editor view modes and writes a
 * JavaFX {@code snapshot} of the shell node for each. Unlike {@link DocScreenshotGenerator}
 * (legacy tabs, AWT-Robot capture), this uses node snapshots so it works with software
 * rendering and captures the shell content cleanly.
 *
 * <p>Run via the dedicated {@code docScreenshots} Gradle task on a real display:
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ShellDocScreenshotGenerator {

    private static final File EXAMPLES = new File("release/examples");
    private static final File IMG_DIR = new File("docs/img");

    private Parent root;
    private UnifiedShellView shell;
    private final org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/main.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateShellScreenshots() throws Exception {
        IMG_DIR.mkdirs();

        // The app boots into the shell.
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle();

        // --- Overview: an XML document open (Explorer + Text editor + inspector) ---
        File xml = new File(EXAMPLES, "xml/context-sensitive-demo.xml");
        if (xml.exists()) {
            onFx(() -> host.openFile(xml.toPath()));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> !t.isBlank()).orElse(false));
            settle();
            shot("unified-shell-overview");
        }

        // --- XSD in the Graphic view (Schema activity) ---
        File xsd = new File(EXAMPLES, "xsd/context-sensitive-demo.xsd");
        if (xsd.exists()) {
            onFx(() -> host.openFile(xsd.toPath()));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("schema")).orElse(false));
            onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
            settle();
            shot("unified-shell-type-library");
            onFx(() -> host.setActiveViewMode(ViewMode.GRAPHIC));
            settle(900);
            shot("unified-shell-schema-graphic");
            onFx(() -> host.setActiveViewMode(ViewMode.TREE));
            settle();
            shot("unified-shell-schema-tree");
            onFx(() -> host.setActiveViewMode(ViewMode.TEXT));
            settle();
            shot("unified-shell-schema-text");

            // Schema statistics open as an in-shell text tab (best-effort: click the button).
            clickButton("Statistics");
            settle(900);
            shot("unified-shell-schema-statistics");
        }

        // --- Activity panels (selecting the activity shows its side panel) ---
        onFx(() -> shell.getSelectionModel().select(Activity.VALIDATION));
        settle();
        shot("unified-shell-validation");

        onFx(() -> shell.getSelectionModel().select(Activity.TRANSFORM));
        settle();
        shot("unified-shell-transform");

        onFx(() -> shell.getSelectionModel().select(Activity.SIGNATURE));
        settle();
        shot("unified-shell-signature");

        // --- JSON document in the Tree view ---
        Path json = Path.of(System.getProperty("java.io.tmpdir"), "fxt-shell-doc-sample.json");
        Files.writeString(json, "{\n  \"fund\": {\n    \"id\": \"EAM\",\n    \"items\": [1, 2, 3],\n    \"active\": true\n  }\n}\n");
        json.toFile().deleteOnExit();
        onFx(() -> host.openFile(json));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("fund")).orElse(false));
        onFx(() -> host.setActiveViewMode(ViewMode.TREE));
        settle();
        shot("unified-shell-json-tree");

        // --- XML document in the Grid (XMLSpy-style) view ---
        if (xml.exists()) {
            onFx(() -> shell.getSelectionModel().select(Activity.EXPLORER));
            onFx(() -> host.openFile(xml.toPath())); // re-selects the already-open XML tab
            settle();
            onFx(() -> host.setActiveViewMode(ViewMode.GRID));
            settle();
            shot("unified-shell-xml-grid");
        }
    }

    /** Clicks a button by its visible text (best-effort; never aborts the run). */
    private void clickButton(String text) {
        try {
            robot.clickOn(text);
            WaitForAsyncUtils.waitForFxEvents();
        } catch (Exception e) {
            System.out.println("[shell-screenshot] could not click '" + text + "': " + e);
        }
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle() {
        settle(400);
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(8000, () -> {
            Node target = shell != null ? shell : root;
            return target.snapshot(new SnapshotParameters(), null);
        });
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[shell-screenshot] wrote " + out.getAbsolutePath()
                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
    }
}
