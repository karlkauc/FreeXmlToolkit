package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.debugger.DebugLaunchConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The debug configuration dialog shows and returns a configuration unchanged. */
@ExtendWith(ApplicationExtension.class)
class DebugConfigurationDialogTest {

    @Start
    void start(Stage stage) {
    }

    @Test
    void showsAndReturnsTheConfiguration() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("lang", "de");
        DebugLaunchConfig file = new DebugLaunchConfig("/tmp/in.xml", params, "TEXT", true);
        DebugLaunchConfig[] r = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            DebugConfigurationDialog dialog = new DebugConfigurationDialog("a.xsl", file);
            DebugLaunchConfig same = dialog.currentConfig();
            dialog.setConfig(DebugLaunchConfig.defaults());
            return new DebugLaunchConfig[]{same, dialog.currentConfig()};
        });
        assertEquals(file, r[0]);
        assertEquals(DebugLaunchConfig.defaults(), r[1], "Run target, automatic format");
    }
}
