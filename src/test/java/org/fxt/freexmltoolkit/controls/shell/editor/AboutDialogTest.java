package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AboutDialogTest {

    @Test
    void otherInstancesMessageNamesTheCountAndTheWayOut() {
        String one = AboutDialog.otherInstancesMessage(1);
        assertTrue(one.startsWith("FreeXmlToolkit is running in 1 other window."), one);
        assertTrue(one.contains("Retry"), one);

        String many = AboutDialog.otherInstancesMessage(5);
        assertTrue(many.startsWith("FreeXmlToolkit is running in 5 other windows."), many);
    }
}
