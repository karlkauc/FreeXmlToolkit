package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** Settings → Extensions → PLUGINS lists the plugins and saves their switches. */
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
        ServiceRegistry.get(PropertiesService.class).set(PluginManager.enabledKey("fundsxml"), "true");
    }

    @Test
    void listsPluginsAndSavesTheirSwitch() throws Exception {
        CheckBox fundsXml = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> (CheckBox) settings.lookup("#settings-plugin-fundsxml"));
        assertNotNull(fundsXml, "the built-in FundsXML plugin is listed");
        assertTrue(fundsXml.getText().startsWith("FundsXML") && fundsXml.getText().endsWith("(built-in)"), fundsXml.getText());
        assertTrue(fundsXml.isSelected());
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            fundsXml.setSelected(false);
            settings.onSave();
            return null;
        });
        assertEquals("false", ServiceRegistry.get(PropertiesService.class).get(PluginManager.enabledKey("fundsxml")));
    }
}
