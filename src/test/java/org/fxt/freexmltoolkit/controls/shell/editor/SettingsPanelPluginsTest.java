package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Settings → Extensions → INSTALLED EXTENSIONS lists the extensions and saves their switches:
 * FundsXML's row is its own switch (applies at once), the others load at the next start.
 */
@ExtendWith(ApplicationExtension.class)
class SettingsPanelPluginsTest {

    private SettingsPanel settings;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        PluginManager.getInstance().loadAll();
        settings = new SettingsPanel();
        stage.setScene(new Scene(settings, 900, 600));
        stage.show();
    }

    @AfterEach
    void restore() {
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        props.set(PluginManager.enabledKey("fundsxml"), "true");
        props.set(PluginManager.enabledKey("iso20022"), "false");
        props.set(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED, "false");
    }

    @Test
    void fundsXmlsRowIsItsSwitch() throws Exception {
        CheckBox fundsXml = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> (CheckBox) settings.lookup("#settings-plugin-fundsxml"));
        assertNotNull(fundsXml, "the built-in FundsXML extension is listed");
        assertTrue(fundsXml.getText().startsWith("FundsXML") && fundsXml.getText().endsWith("(built-in)"), fundsXml.getText());
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            fundsXml.setSelected(true);
            settings.onSave();
            return null;
        });
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        assertEquals("true", props.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED));
        assertEquals("true", props.get(PluginManager.enabledKey("fundsxml")), "a switched-on FundsXML stays loaded");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            fundsXml.setSelected(false);
            settings.onSave();
            return null;
        });
        assertEquals("false", props.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED));
        assertNull(settings.lookup("#settings-fundsxml-card"), "no separate FundsXML card any more");
    }

    @Test
    void otherExtensionsSaveTheirLoadSwitch() throws Exception {
        CheckBox iso = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> (CheckBox) settings.lookup("#settings-plugin-iso20022"));
        assertNotNull(iso, "an industry extension is listed");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            iso.setSelected(true);
            settings.onSave();
            return null;
        });
        assertEquals("true", ServiceRegistry.get(PropertiesService.class).get(PluginManager.enabledKey("iso20022")));
    }
}
