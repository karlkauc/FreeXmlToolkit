package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies a JSON document gains a Tree view mode (but not Graphic) in the
 * shell, reusing the existing JSON tree component.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostJsonTreeTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void jsonDocumentSupportsTreeButNotGraphic(@TempDir Path tmp) throws Exception {
        openJson(tmp);
        assertTrue(host.activeSupportsView(ViewMode.TREE), "JSON must support the Tree view");
        assertFalse(host.activeSupportsView(ViewMode.GRAPHIC), "JSON must not offer the Graphic view");
    }

    @Test
    void switchingJsonToTreeRendersATree(@TempDir Path tmp) throws Exception {
        openJson(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(ViewMode.TREE, host.activeViewModeProperty().get(),
                "JSON Tree mode must stick (not fall back to Text)");
        assertNotNull(host.lookup(".tree-view"), "a tree view must be rendered for the JSON document");
    }

    @Test
    void writesSnapshotWhenRequested(@TempDir Path tmp) throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        openJson(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        javafx.scene.image.WritableImage img = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> host.snapshot(new javafx.scene.SnapshotParameters(), null));
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png",
                new java.io.File(System.getProperty("java.io.tmpdir"), "fxt_json_tree.png"));
    }

    private void openJson(Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{\"fund\":{\"id\":\"EAM\",\"items\":[1,2,3]}}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("fund")).orElse(false));
    }
}
