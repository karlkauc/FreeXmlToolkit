package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.XmlParserType;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The Settings panel surfaces the full PropertiesService configuration (editor, XSD, parser, temp,
 * general) and round-trips it: loads saved values into the controls and persists control changes.
 */
@ExtendWith(ApplicationExtension.class)
class SettingsPanelTest {

    private SettingsPanel panel;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        // seed known values so load() has something deterministic to reflect
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        props.setXmlParserType(XmlParserType.SAXON);
        props.setXsdBackupEnabled(true);
        props.setUseSystemTempFolder(false);
        props.setCustomTempFolder("/tmp/fxt-settings-test");
        panel = new SettingsPanel();
        stage.setScene(new Scene(panel, 360, 800));
        stage.show();
    }

    @Test
    void loadsAndPersistsTheExpandedSettings() {
        // Loaded from the seeded values.
        assertEquals(XmlParserType.SAXON, WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.getParserType()));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.isXsdBackupSelected()));
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.isUseSystemTempSelected()));
        assertEquals("/tmp/fxt-settings-test",
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.getCustomTempText()));

        // Change controls and persist.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setParserType(XmlParserType.XERCES);
            panel.setXsdBackupSelected(false);
            panel.saveSettings();
            return null;
        });
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        assertEquals(XmlParserType.XERCES, props.getXmlParserType());
        assertFalse(props.isXsdBackupEnabled());
    }
}
