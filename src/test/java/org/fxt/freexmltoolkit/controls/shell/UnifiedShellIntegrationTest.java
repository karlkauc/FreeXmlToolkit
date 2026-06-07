package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Integration smoke test: loads the real shell FXML ({@code tab_unified_shell.fxml})
 * exactly as {@code FxtGui} now boots it — as the root scene, with no MainController
 * and no navigation. Asserts the Unified Shell renders within the full app context
 * (CSS, service registry, controller wiring), not just in isolation.
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedShellIntegrationTest {

    private Parent root;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        // Keep below the Monocle headless screen size (~1280x800) to avoid a
        // blit BufferOverflow; this test verifies wiring, not pixel rendering.
        stage.setScene(new Scene(root, 1024, 720));
        stage.show();
    }

    @Test
    void unifiedShellLoadsInsideTheRealApp() {
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(root.lookup(".fxt-shell"), "Unified shell root must render in the full app");
        assertNotNull(root.lookup(".fxt-activity-bar"), "Activity bar must render");
        assertFalse(root.lookupAll(".fxt-activity-button").isEmpty(), "activity buttons must render");
    }

    @Test
    void shellFillsTheSceneVertically() {
        WaitForAsyncUtils.waitForFxEvents();

        javafx.scene.layout.Region shell = (javafx.scene.layout.Region) root.lookup(".fxt-shell");
        assertNotNull(shell);
        // The shell is the scene root, so it must stretch to the scene height with
        // the status bar pinned to the bottom rather than floating at preferred size.
        double sceneHeight = shell.getScene().getHeight();
        assertTrue(sceneHeight > 300, "scene must have a real height: " + sceneHeight);
        assertEquals(sceneHeight, shell.getHeight(), 1.0,
                "the shell must fill the scene height (status bar pinned to the bottom)");
    }
}
