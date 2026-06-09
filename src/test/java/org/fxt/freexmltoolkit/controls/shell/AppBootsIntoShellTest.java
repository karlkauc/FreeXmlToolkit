package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * Cutover guard: FxtGui boots by loading the shell FXML directly as the root
 * scene (no MainController, no navigation). This test loads the SAME FXML FxtGui
 * loads and asserts the Unified Shell is present at the root.
 */
@ExtendWith(ApplicationExtension.class)
class AppBootsIntoShellTest {

    private Parent root;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1024, 720));
        stage.show();
    }

    @Test
    void shellFxmlIsTheRoot() {
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(root.lookup(".fxt-shell"), "the shell FXML must render the Unified Shell as root");
    }
}
