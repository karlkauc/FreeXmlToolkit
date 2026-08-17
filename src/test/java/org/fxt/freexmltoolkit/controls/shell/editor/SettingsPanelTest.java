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

    @Test
    void executionStatisticsCheckboxDefaultsOffAndRoundTrips() {
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        String key = org.fxt.freexmltoolkit.service.DeveloperPropertyKeys.EXECUTION_STATS_ENABLED;
        String previous = props.get(key);
        try {
            // Default: without a stored value the developer feature is off.
            assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000,
                    () -> panel.getExecStatsCheckBox().isSelected()));

            // Toggle + save persists the key; a fresh panel reloads it.
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.getExecStatsCheckBox().setSelected(true);
                panel.saveSettings();
                return null;
            });
            assertEquals("true", props.get(key));
            SettingsPanel reloaded = WaitForAsyncUtils.waitForAsyncFx(2000, SettingsPanel::new);
            assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000,
                    () -> reloaded.getExecStatsCheckBox().isSelected()));
        } finally {
            props.set(key, previous == null ? "false" : previous);
        }
    }

    @Test
    void fundsXmlEnabledHookFiresOnlyOnTheOffToOnTransition() {
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        props.set(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED, "false");
        java.util.concurrent.atomic.AtomicInteger fired = new java.util.concurrent.atomic.AtomicInteger();
        panel.setOnFundsXmlEnabled(fired::incrementAndGet);

        // Save while the checkbox stays off → no hook.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setFundsXmlEnabledSelected(false);
            panel.saveSettings();
            return null;
        });
        assertEquals(0, fired.get());

        // Turning it on fires the hook exactly once.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setFundsXmlEnabledSelected(true);
            panel.saveSettings();
            return null;
        });
        assertEquals(1, fired.get());

        // Saving again while it stays on must NOT fire again.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.saveSettings();
            return null;
        });
        assertEquals(1, fired.get());
    }
}
