package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SettingsPanelMemoryTest {

    @Test
    void labelsRoundTrip() {
        assertEquals("Automatic", SettingsPanel.memoryLabel(0));
        assertEquals("8 GB", SettingsPanel.memoryLabel(8192));
        assertEquals("10000 MB", SettingsPanel.memoryLabel(10000));
        assertEquals(0, SettingsPanel.memoryValue("Automatic"));
        assertEquals(8192, SettingsPanel.memoryValue("8 GB"));
        assertEquals(10000, SettingsPanel.memoryValue("10000 MB"));
        assertEquals(0, SettingsPanel.memoryValue(null));
    }
}
