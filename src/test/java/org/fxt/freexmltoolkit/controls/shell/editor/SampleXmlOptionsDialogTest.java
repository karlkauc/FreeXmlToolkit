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
 * Verifies the sample-XML options dialog maps its controls to the generation
 * options (mandatory-only + max occurrences) — without showing it modally.
 */
@ExtendWith(ApplicationExtension.class)
class SampleXmlOptionsDialogTest {

    private SampleXmlOptionsDialog dialog;

    @Start
    void start(Stage stage) {
        dialog = new SampleXmlOptionsDialog();
    }

    @Test
    void defaultsToAllElementsWithSmallRepetition() {
        var opts = WaitForAsyncUtils.waitForAsyncFx(2000, () -> dialog.currentOptions());
        assertFalse(opts.mandatoryOnly(), "default includes optional elements");
        assertTrue(opts.maxOccurrences() >= 1);
    }

    @Test
    void reflectsToggledOptions() {
        var opts = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            dialog.setOptions(true, 5);
            return dialog.currentOptions();
        });
        assertTrue(opts.mandatoryOnly());
        assertEquals(5, opts.maxOccurrences());
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
