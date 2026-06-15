package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ToolbarDisplayTest {

    @Test
    void iconSizePxMapsSmallAndLarge() {
        assertEquals(16, ToolbarDisplay.iconSizePx(false));
        assertEquals(22, ToolbarDisplay.iconSizePx(true));
    }

    @Test
    void sizeStyleClassMapsSmallAndLarge() {
        assertEquals("fxt-tool-small", ToolbarDisplay.sizeStyleClass(false));
        assertEquals("fxt-tool-large", ToolbarDisplay.sizeStyleClass(true));
    }
}
