package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** A renamed grid class must not silently drop out of the warm-up list. */
class GridWarmupTest {

    @Test
    void everyListedClassExists() {
        for (String name : GridWarmup.CLASSES) {
            assertDoesNotThrow(() -> Class.forName(name, false, GridWarmup.class.getClassLoader()), name);
        }
    }
}
