package org.fxt.freexmltoolkit.controls.shell;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controller.MainController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration smoke test: loads the real application shell ({@code main.fxml} +
 * {@link MainController}) and navigates to the Unified Shell preview, asserting
 * it renders within the full app (CSS, service registry, controller wiring) —
 * not just in isolation. Guards the upcoming cutover.
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedShellIntegrationTest {

    private Parent root;
    private MainController mainController;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/main.fxml"));
        root = loader.load();
        mainController = loader.getController();
        // Keep below the Monocle headless screen size (~1280x800) to avoid a
        // blit BufferOverflow; this test verifies wiring, not pixel rendering.
        stage.setScene(new Scene(root, 1024, 720));
        stage.show();
    }

    @Test
    void unifiedShellLoadsInsideTheRealApp() {
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            mainController.navigateToPage("unifiedShell");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(root.lookup(".fxt-shell"), "Unified shell root must render in the full app");
        assertNotNull(root.lookup(".fxt-activity-bar"), "Activity bar must render");
        assertFalse(root.lookupAll(".fxt-activity-button").isEmpty(), "activity buttons must render");
    }
}
