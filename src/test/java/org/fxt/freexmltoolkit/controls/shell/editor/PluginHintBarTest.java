package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.layout.VBox;
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

/** The offer of a switched-off extension: recorded as shown, Switch On stores the setting and asks for a restart. */
@ExtendWith(ApplicationExtension.class)
class PluginHintBarTest {

    private static final String ID = "test.hint";
    private PluginHintBar bar;
    private VBox box;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        bar = new PluginHintBar(ID, "Test Extension");
        box = new VBox(bar);
        stage.setScene(new Scene(box, 800, 120));
        stage.show();
    }

    @AfterEach
    void clean() {
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        props.set(PluginManager.enabledKey(ID), "");
        props.set(PluginManager.hintShownKey(ID), "");
    }

    @Test
    void switchOnStoresTheSettingAndAsksForARestart() throws Exception {
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        assertEquals("true", props.get(PluginManager.hintShownKey(ID)));
        assertTrue(bar.messageForTesting().contains("Test Extension"), bar.messageForTesting());
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            bar.onEnable();
            return null;
        });
        assertEquals("true", props.get(PluginManager.enabledKey(ID)));
        assertTrue(bar.messageForTesting().contains("restart"), bar.messageForTesting());
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            bar.onDismiss();
            return null;
        });
        assertEquals(0, WaitForAsyncUtils.waitForAsyncFx(5000, () -> box.getChildren().size()));
    }
}
