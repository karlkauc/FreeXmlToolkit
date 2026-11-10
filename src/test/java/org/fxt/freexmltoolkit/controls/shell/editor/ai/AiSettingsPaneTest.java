package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Properties;

import org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel;
import org.fxt.freexmltoolkit.service.AdminPolicyTestAccess;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
import org.fxt.freexmltoolkit.service.ai.context.DataLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Settings → AI: saved at once, details disabled while off; the whole group is gone under ai.allowed=false. */
@ExtendWith(ApplicationExtension.class)
class AiSettingsPaneTest {

    @TempDir
    Path temp;

    private final PropertiesService properties = PropertiesServiceImpl.getInstance();
    private Stage stage;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        this.stage = stage;
        stage.setScene(new Scene(new VBox(), 900, 700));
        stage.show();
    }

    @AfterEach
    void cleanUp() {
        AdminPolicyTestAccess.reset();
        Properties all = properties.loadProperties();
        for (String key : new String[] {AiSettings.KEY_ENABLED, AiSettings.KEY_DATA_LEVEL}) {
            all.remove(key);
        }
        properties.saveProperties(all);
    }

    @Test
    @SuppressWarnings("unchecked")
    void changesAreSavedAtOnce() throws Exception {
        AiSettingsPane pane = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            AiSettingsPane p = new AiSettingsPane();
            stage.getScene().setRoot(new VBox(p));
            return p;
        });
        CheckBox enabled = (CheckBox) pane.lookup("#settings-ai-enabled");
        ComboBox<DataLevel> level = (ComboBox<DataLevel>) pane.lookup("#settings-ai-data-level");
        assertTrue(pane.lookup("#settings-ai-providers").isDisabled() || !enabled.isSelected());
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            enabled.setSelected(true);
            level.setValue(DataLevel.STRUCTURE);
            return null;
        });
        assertEquals("true", properties.get(AiSettings.KEY_ENABLED));
        assertEquals("STRUCTURE", properties.get(AiSettings.KEY_DATA_LEVEL));
    }

    @Test
    void theGroupIsAbsentWhenThePolicyRemovesAi() throws Exception {
        SettingsPanel allowed = WaitForAsyncUtils.waitForAsyncFx(10_000, this::shown);
        assertNotNull(allowed.lookup("#settings-group-ai"));
        assertTrue(allowed.sections().stream().anyMatch(s -> s.key().equals("ai")));

        Properties locked = new Properties();
        locked.setProperty("ai.allowed", "false");
        AdminPolicyTestAccess.setCurrent(temp.resolve("policy.properties"), locked, new Properties());
        SettingsPanel removed = WaitForAsyncUtils.waitForAsyncFx(10_000, this::shown);
        assertNull(removed.lookup("#settings-group-ai"));
        assertTrue(removed.sections().stream().noneMatch(s -> s.key().equals("ai")));
    }

    /** A settings panel in the test scene, CSS and layout applied, so the scroll pane's content is in the tree. */
    private SettingsPanel shown() {
        SettingsPanel panel = new SettingsPanel();
        stage.getScene().setRoot(new VBox(panel));
        panel.applyCss();
        panel.layout();
        return panel;
    }
}
