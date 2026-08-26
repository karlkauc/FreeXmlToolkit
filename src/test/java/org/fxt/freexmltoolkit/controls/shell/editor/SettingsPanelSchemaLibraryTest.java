package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SettingsPanelSchemaLibraryTest {

    private SettingsPanel panel;
    private final AtomicBoolean manageClicked = new AtomicBoolean();

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        panel = new SettingsPanel();
        panel.setManageSchemaCacheAction(() -> manageClicked.set(true));
        stage.setScene(new Scene(panel, 900, 700));
        stage.show();
    }

    @AfterEach void tearDown() {
        ServiceRegistry.get(PropertiesService.class).setSchemaLibraryAutoBindEnabled(true);
        ServiceRegistry.reset();
    }

    @Test
    void autoBindCheckboxRoundTrips(FxRobot robot) {
        CheckBox cb = robot.lookup("#settings-schema-library-autobind").queryAs(CheckBox.class);
        assertTrue(cb.isSelected());
        robot.interact(() -> cb.setSelected(false));
        robot.interact(panel::saveSettings);
        assertFalse(ServiceRegistry.get(PropertiesService.class).isSchemaLibraryAutoBindEnabled());
    }

    @Test
    void manageLinkInvokesAction(FxRobot robot) {
        robot.interact(() -> robot.lookup("#settings-manage-schema-cache").queryAs(javafx.scene.control.Hyperlink.class).fire());
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(manageClicked.get());
    }
}
