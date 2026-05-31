package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the EditorHost shows the welcome empty-state when no document is
 * open (e.g. on boot into the shell) and hides it once a document opens.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostEmptyStateTest {

    private EditorHost host;

    private Scene scene;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        scene = new Scene(host, 900, 600);
        scene.getStylesheets().addAll(
                getClass().getResource("/css/design-tokens.css").toExternalForm(),
                getClass().getResource("/css/unified-shell.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void showsWelcomeWhenNoDocumentOpen() {
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(host.isEmpty());
        assertNotNull(host.lookup(".fxt-editor-empty-state"),
                "the welcome empty-state must show when no document is open");
    }

    @Test
    void hidesWelcomeWhenADocumentOpens() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.newDocument(EditorFileType.XML));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(host.isEmpty());
        assertNull(host.lookup(".fxt-editor-empty-state"),
                "the welcome empty-state must be hidden when a document is open");
    }

    @Test
    void writesSnapshotWhenRequested() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        WaitForAsyncUtils.waitForFxEvents();
        javafx.scene.image.WritableImage img = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> host.snapshot(new javafx.scene.SnapshotParameters(), null));
        java.io.File out = new java.io.File(System.getProperty("java.io.tmpdir"), "fxt_empty_state.png");
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png", out);
    }
}
