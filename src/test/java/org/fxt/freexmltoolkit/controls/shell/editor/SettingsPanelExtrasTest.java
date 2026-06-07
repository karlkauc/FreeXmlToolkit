package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class SettingsPanelExtrasTest {

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
    }

    @Test
    void userInfoRoundTripsThroughProperties() {
        SettingsPanel panel = WaitForAsyncUtils.waitForAsyncFx(3000, SettingsPanel::new);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setUserName("Ada Lovelace");
            panel.setUserEmail("ada@example.com");
            panel.setUserCompany("Analytical Engines");
            panel.saveSettings();
            return null;
        });
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        assertEquals("Ada Lovelace", props.get("user.name"));
        assertEquals("ada@example.com", props.get("user.email"));
        assertEquals("Analytical Engines", props.get("user.company"));
    }
}
