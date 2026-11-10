package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shared.EditorFont;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.theme.AccentPalette;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Settings → Appearance: the System theme and the accent colour card. */
@ExtendWith(ApplicationExtension.class)
class SettingsPanelAppearanceTest {

    private SettingsPanel settings;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        settings = new SettingsPanel();
        stage.setScene(new Scene(new HBox(settings), 900, 600));
        stage.show();
    }

    @AfterEach
    void reset() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.setAccent(null);
            ThemeManager.apply(settings.getScene(), false);
            EditorFont.update(EditorFont.Settings.defaults());
            ThemeManager.setTypography(null, null, null);
            ThemeManager.resetSyntaxColors();
        });
    }

    @Test
    void everyPresetHasANamedSwatch() {
        var swatches = WaitForAsyncUtils.waitForAsyncFx(2000, settings::accentSwatchButtons);
        assertEquals(AccentPalette.PRESETS.size(), swatches.size());
        for (ToggleButton swatch : swatches) {
            assertNotNull(swatch.getAccessibleText(), "screen readers name the colour");
            assertNotNull(swatch.getTooltip());
        }
        assertNotNull(settings.lookup("#settings-accent-custom"));
    }

    @Test
    void choosingAnAccentRecoloursTheTokensAndMarksTheSwatch() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> settings.applyAccent("teal"));
        assertEquals("teal", ThemeManager.currentAccent());
        ToggleButton teal = (ToggleButton) settings.lookup("#settings-accent-teal");
        assertTrue(teal.isSelected());
        assertEquals(1, settings.accentSwatchButtons().stream().filter(ToggleButton::isSelected).count());
        assertEquals(AccentPalette.light(AccentPalette.parse("teal")).get("-fxt-primary"),
                DesignTokens.ColorToken.PRIMARY.color(DesignTokens.Theme.LIGHT));

        javafx.scene.control.ColorPicker picker = (javafx.scene.control.ColorPicker) settings.lookup("#settings-accent-custom");
        assertEquals(AccentPalette.parse("teal"), picker.getValue(), "the picker shows the chosen preset");
        assertEquals("teal", ThemeManager.currentAccent(), "showing it chose nothing else");
        assertTrue(!settings.isDirty(), "theme and accent apply at once: nothing left to save");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> settings.applyAccent("#123456"));
        assertEquals("#123456", ThemeManager.currentAccent());
        assertEquals(0, settings.accentSwatchButtons().stream().filter(ToggleButton::isSelected).count(),
                "an own colour selects no preset");
    }

    @Test
    void theSystemThemeIsStoredAsSuch() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> settings.applyThemeMode(ThemeManager.Mode.SYSTEM));
        assertEquals(ThemeManager.Mode.SYSTEM, ThemeManager.currentMode());
        assertTrue(((ToggleButton) settings.lookup("#settings-theme-system")).isSelected());
    }

    @Test
    void themeAndAccentCanBeLockedByTheAdministrator() {
        var keys = WaitForAsyncUtils.waitForAsyncFx(2000, settings::policyKeys);
        assertTrue(keys.values().stream().anyMatch(k -> java.util.List.of(k).contains("ui.theme")));
        assertTrue(keys.values().stream().anyMatch(k -> java.util.List.of(k).contains(ThemeManager.ACCENT_KEY)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theEditorFontCardAppliesAtOnceAndFollowsAZoom() {
        var size = (javafx.scene.control.Spinner<Integer>) settings.lookup("#settings-editor-font-size");
        var spacing = (javafx.scene.control.Spinner<Double>) settings.lookup("#settings-editor-line-spacing");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            size.getValueFactory().setValue(16);
            spacing.getValueFactory().setValue(1.4);
        });
        assertEquals(16, EditorFont.current().size());
        assertEquals(1.4, EditorFont.current().lineSpacing(), 1e-9);
        assertTrue(!settings.isDirty(), "the editor font applies at once: nothing left to save");
        var preview = (javafx.scene.control.Label) settings.lookup("#settings-editor-font-preview");
        assertTrue(preview.getStyle().contains("-fx-font-size: 16pt"), preview.getStyle());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> EditorFont.zoomTo(18));
        assertEquals(18, size.getValue(), "a zoom in an editor shows in the card");

        WaitForAsyncUtils.waitForAsyncFx(2000, settings::onEditorFontReset);
        assertEquals(EditorFont.Settings.defaults(), EditorFont.current());
        assertEquals(EditorFont.DEFAULT_SIZE, size.getValue());
    }

    @Test
    void aLargerTextSizeAddsTheTypographySheetAfterTheShellSheet() {
        javafx.scene.Parent root = new HBox();
        root.getStylesheets().setAll("x/design-tokens.css", "x/unified-shell.css");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((ToggleButton) settings.lookup("#settings-ui-font-xlarge")).fire();
            ThemeManager.syncAppearanceSheet(root);
        });
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Typography.Size.XLARGE, ThemeManager.currentFontSize());
        assertNotNull(ThemeManager.typographySheet());
        assertEquals(ThemeManager.typographySheet(), root.getStylesheets().get(2), "right after unified-shell.css");
        assertTrue(!settings.isDirty(), "the text size applies at once");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            settings.onUiFontReset();
            ThemeManager.syncAppearanceSheet(root);
        });
        assertEquals(null, ThemeManager.typographySheet(), "Normal + Inter needs no sheet");
        assertEquals(2, root.getStylesheets().size(), "the stale sheet is removed");
        assertTrue(((ToggleButton) settings.lookup("#settings-ui-font-normal")).isSelected());
    }

    @Test
    void aSyntaxSchemeAndAnOwnColourReachTheTokens() {
        var light = DesignTokens.Theme.LIGHT;
        var role = org.fxt.freexmltoolkit.controls.theme.SyntaxSchemes.Role.ELEMENT;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> settings.applySyntaxScheme("github"));
        assertEquals("github", ThemeManager.currentSyntaxScheme());
        var github = org.fxt.freexmltoolkit.controls.theme.SyntaxSchemes.effective("github", k -> null, light).get(role);
        assertEquals(github, DesignTokens.ColorToken.CODE_ELEM.color(light), "the grid and Java views follow");
        assertEquals(github, settings.syntaxPickers().get(role).getValue());

        WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                settings.syntaxPickers().get(role).setValue(javafx.scene.paint.Color.web("#123456")));
        assertEquals(javafx.scene.paint.Color.web("#123456"), DesignTokens.ColorToken.CODE_ELEM.color(light));
        assertEquals("#123456", ThemeManager.setting(role.key(light)));
        assertTrue(!settings.isDirty(), "syntax colours apply at once");

        WaitForAsyncUtils.waitForAsyncFx(2000, settings::onSyntaxReset);
        assertEquals(DesignTokens.ColorToken.CODE_ELEM.defaultColor(light), DesignTokens.ColorToken.CODE_ELEM.color(light));
        assertEquals("default", ThemeManager.currentSyntaxScheme());
    }
}
