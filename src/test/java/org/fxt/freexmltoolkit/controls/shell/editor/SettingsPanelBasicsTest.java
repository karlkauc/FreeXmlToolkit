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
 * TestFX verification of the Settings page basics: persisted values load, the proxy and
 * sponsor controls exist, and the theme switch changes the design-token class on the root.
 */
@ExtendWith(ApplicationExtension.class)
class SettingsPanelBasicsTest {

    private SettingsPanel settings;
    private HBox rootBox;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        settings = new SettingsPanel();
        rootBox = new HBox(settings);
        stage.setScene(new Scene(rootBox, 800, 500));
        stage.show();
    }

    @Test
    void settingsLoadsEditorAndProxyControls() {
        WaitForAsyncUtils.waitForFxEvents();
        int expectedIndent = org.fxt.freexmltoolkit.di.ServiceRegistry
                .get(org.fxt.freexmltoolkit.service.PropertiesService.class).getXmlIndentSpaces();
        assertEquals(expectedIndent, settings.getIndentValue(),
                "indent spinner must load the persisted XML indent setting");
        boolean hasProxyHost = WaitForAsyncUtils.waitForAsyncFx(2000, () -> settings.lookupAll(".text-field").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.TextField t && "host".equals(t.getPromptText())));
        boolean hasSave = WaitForAsyncUtils.waitForAsyncFx(2000, () -> settings.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Button b && "Save Settings".equals(b.getText())));
        assertNotNull(settings.lookup("#settings-save-bar"), "Save sits in a fixed bar below the cards");
        assertTrue(hasProxyHost, "proxy host field must be present");
        assertTrue(hasSave, "'Save Settings' button must be present");
    }

    @Test
    void cardsComeInSevenGroupsWhichTheSidePanelLists() {
        WaitForAsyncUtils.waitForFxEvents();
        var sections = settings.sections();
        assertEquals(java.util.List.of("appearance", "editor", "schemas", "files", "network", "privacy", "extensions"),
                sections.stream().map(PageNavigator.Entry::key).toList());
        assertEquals("Schemas & Validation", sections.get(2).label());
        assertTrue(sections.get(4).keywords().contains("Trust all certificates"),
                "a group is found by the settings inside it");
    }

    @Test
    void theFilterHidesCardsAndGroupsWithoutAMatch() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.filter("proxy");
            return null;
        });
        assertTrue(settings.lookup("#settings-group-network").isVisible());
        assertFalse(settings.lookup("#settings-group-appearance").isVisible());
        assertFalse(settings.lookup("#settings-no-match").isVisible());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.filter("no-such-setting-xyz");
            return null;
        });
        assertTrue(settings.lookup("#settings-no-match").isVisible(), "an empty result says so");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.filter("");
            return null;
        });
        assertTrue(settings.lookup("#settings-group-appearance").isVisible());
    }

    @Test
    void editsShowTheUnsavedHintUntilSaved() {
        WaitForAsyncUtils.waitForFxEvents();
        javafx.scene.Node hint = settings.lookup("#settings-dirty");
        assertFalse(settings.isDirty());
        assertFalse(hint.isVisible(), "freshly loaded values are not unsaved changes");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.applyTheme(!settings.isDarkSelected());
            settings.applyTheme(!settings.isDarkSelected());
            return null;
        });
        assertFalse(settings.isDirty(), "the theme applies at once and is no unsaved change");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.setUserName(settings.getUserName() + "x");
            return null;
        });
        assertTrue(settings.isDirty());
        assertTrue(hint.isVisible());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.setUserName(settings.getUserName().substring(0, settings.getUserName().length() - 1));
            ((javafx.scene.control.Button) settings.lookup("#settings-save")).fire();
            return null;
        });
        assertFalse(settings.isDirty(), "saving clears the hint");
    }

    @Test
    void resetPutsDefaultsIntoTheControlsWithoutSaving() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.setParserType(org.fxt.freexmltoolkit.domain.XmlParserType.values()[
                    org.fxt.freexmltoolkit.domain.XmlParserType.values().length - 1]);
            settings.resetToDefaults();
            return null;
        });
        assertEquals(org.fxt.freexmltoolkit.domain.XmlParserType.XERCES, settings.getParserType());
        assertFalse(settings.isTrustAllCertsSelected());
        assertTrue(settings.isDirty(), "the defaults still need Save Settings");
    }

    @Test
    void settingsOffersSponsorCard() {
        WaitForAsyncUtils.waitForFxEvents();
        Object sponsor = WaitForAsyncUtils.waitForAsyncFx(2000, () -> settings.lookup("#settings-sponsor"));
        assertNotNull(sponsor, "Settings must offer the 'Sponsor on GitHub' button");
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
