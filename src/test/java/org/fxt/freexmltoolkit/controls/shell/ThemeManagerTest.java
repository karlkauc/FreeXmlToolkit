package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class ThemeManagerTest {

    private Scene scene;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        scene = new Scene(new StackPane(), 200, 200);
        stage.setScene(scene);
    }

    @Test
    void applyDarkThenLightSwapsStylesheetAndRootClass() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, true);
            return null;
        });
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")), "dark css added");
        assertTrue(scene.getRoot().getStyleClass().contains("fxt-theme-dark"), "dark root class");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, false);
            return null;
        });
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("light-theme.css")), "light css added");
        assertFalse(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")), "dark css removed");
        assertTrue(scene.getRoot().getStyleClass().contains("fxt-theme-light"), "light root class");
        assertFalse(scene.getRoot().getStyleClass().contains("fxt-theme-dark"), "dark root class removed");
    }
}
