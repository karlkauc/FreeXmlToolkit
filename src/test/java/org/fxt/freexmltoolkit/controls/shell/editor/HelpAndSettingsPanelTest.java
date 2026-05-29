package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestFX verification of the small Help and Settings activity panels: Help shows
 * a version line; Settings switches the design-token theme on the scene root.
 */
@ExtendWith(ApplicationExtension.class)
class HelpAndSettingsPanelTest {

    private HelpPanel help;
    private SettingsPanel settings;
    private HBox rootBox;

    @Start
    void start(Stage stage) {
        help = new HelpPanel();
        settings = new SettingsPanel();
        rootBox = new HBox(help, settings);
        stage.setScene(new Scene(rootBox, 800, 500));
        stage.show();
    }

    @Test
    void helpShowsVersion() {
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(help.getVersionText().startsWith("Version "), help.getVersionText());
    }

    @Test
    void settingsTogglesDesignTokenThemeOnRoot() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.applyTheme(true);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(settings.isDarkSelected());
        assertTrue(rootBox.getScene().getRoot().getStyleClass().contains("fxt-theme-dark"),
                "dark theme must add the fxt-theme-dark root class");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.applyTheme(false);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(rootBox.getScene().getRoot().getStyleClass().contains("fxt-theme-dark"));
        assertTrue(rootBox.getScene().getRoot().getStyleClass().contains("fxt-theme-light"));
    }
}
