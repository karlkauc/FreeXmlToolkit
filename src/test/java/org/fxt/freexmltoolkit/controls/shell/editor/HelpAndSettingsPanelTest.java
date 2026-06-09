package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

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
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
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
    void helpExposesCheckForUpdatesAndHidesFundsXmlByDefault() {
        WaitForAsyncUtils.waitForFxEvents();
        boolean hasUpdate = help.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Button b && "Check for Updates".equals(b.getText()));
        assertTrue(hasUpdate, "Help must offer 'Check for Updates'");
        boolean fundsShown = help.lookupAll(".label").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Label l && "FUNDSXML".equals(l.getText()));
        assertFalse(fundsShown, "FundsXML section must be hidden unless enabled in settings");
    }

    @Test
    void settingsLoadsEditorAndProxyControls() {
        WaitForAsyncUtils.waitForFxEvents();
        int expectedIndent = org.fxt.freexmltoolkit.di.ServiceRegistry
                .get(org.fxt.freexmltoolkit.service.PropertiesService.class).getXmlIndentSpaces();
        assertEquals(expectedIndent, settings.getIndentValue(),
                "indent spinner must load the persisted XML indent setting");
        boolean hasProxyHost = settings.lookupAll(".text-field").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.TextField t && "host".equals(t.getPromptText()));
        boolean hasSave = settings.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Button b && "Save Settings".equals(b.getText()));
        assertTrue(hasProxyHost, "proxy host field must be present");
        assertTrue(hasSave, "'Save Settings' button must be present");
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
