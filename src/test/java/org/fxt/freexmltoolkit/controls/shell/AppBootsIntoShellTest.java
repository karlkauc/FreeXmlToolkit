package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.TimeUnit;

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
 * Cutover guard: the application must boot directly into the Unified Shell
 * (no manual navigation). The legacy sidebar tools remain reachable as a
 * bridge, but the shell is the default landing surface.
 */
@ExtendWith(ApplicationExtension.class)
class AppBootsIntoShellTest {

    private Parent root;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/main.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1024, 720));
        stage.show();
    }

    @Test
    void bootsIntoTheUnifiedShell() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        assertNotNull(root.lookup(".fxt-shell"), "the app must boot into the Unified Shell by default");
    }
}
