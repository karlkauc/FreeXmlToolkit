package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The GRID TABLE COLOURS card stores per-theme colours and applies them to the grids at once. */
@ExtendWith(ApplicationExtension.class)
class SettingsPanelGridColorsTest {

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
    }

    @Test
    void customColoursAreSavedForTheCurrentThemeAndClearedWhenSwitchedOff() {
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        String theme = GridColors.key(DesignTokens.currentTheme());
        SettingsPanel panel = WaitForAsyncUtils.waitForAsyncFx(3000, SettingsPanel::new);
        CheckBox custom = panel.gridCustomColorsForTest();
        ColorPicker header = panel.gridHeaderColorForTest();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            custom.setSelected(true);
            header.setValue(Color.web("#fff6bf"));
            panel.saveSettings();
            return null;
        });
        assertEquals("#fff6bf", props.getGridColor("tableHeader", theme));
        assertNull(props.getGridColor("missingCell", theme), "unchanged slots keep the default");
        assertEquals(Color.web("#fff6bf"), GridColors.override(DesignTokens.currentTheme(), GridColors.Slot.TABLE_HEADER));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            custom.setSelected(false);
            panel.saveSettings();
            return null;
        });
        assertNull(props.getGridColor("tableHeader", theme));
        assertNull(GridColors.override(DesignTokens.currentTheme(), GridColors.Slot.TABLE_HEADER));
    }
}
