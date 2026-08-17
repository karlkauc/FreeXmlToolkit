package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the flatten options dialog maps its controls to {@code FlattenOptions}
 * (defaults fully reduced, resolved includes always dropped) — without showing it
 * modally.
 */
@ExtendWith(ApplicationExtension.class)
class FlattenOptionsDialogTest {

    private FlattenOptionsDialog dialog;

    @Start
    void start(Stage stage) {
        dialog = new FlattenOptionsDialog();
    }

    @Test
    void defaultsToFullyReducedOutput() {
        var opts = WaitForAsyncUtils.waitForAsyncFx(2000, () -> dialog.currentOptions());
        assertTrue(opts.removeAnnotations());
        assertTrue(opts.removeComments());
        assertTrue(opts.minify());
        assertTrue(opts.removeUnusedTypes());
        assertTrue(opts.removeResolvedIncludes());
    }

    @Test
    void reflectsToggledOptionsButAlwaysDropsResolvedIncludes() {
        var opts = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            dialog.setOptions(false, false, false, false);
            return dialog.currentOptions();
        });
        assertFalse(opts.removeAnnotations());
        assertFalse(opts.removeComments());
        assertFalse(opts.minify());
        assertFalse(opts.removeUnusedTypes());
        assertTrue(opts.removeResolvedIncludes(), "flatten output is standalone by design");
    }

    @Test
    void okReturnsOptionsCancelReturnsNull() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            assertNotNull(dialog.getResultConverter().call(ButtonType.OK));
            assertNull(dialog.getResultConverter().call(ButtonType.CANCEL));
            return null;
        });
    }
}
