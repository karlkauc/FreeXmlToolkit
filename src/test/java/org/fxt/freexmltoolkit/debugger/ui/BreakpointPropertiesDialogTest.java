package org.fxt.freexmltoolkit.debugger.ui;

import static org.junit.jupiter.api.Assertions.*;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.debugger.HitCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The properties dialog shows a breakpoint's condition, hit rule and hits, and returns the edits. */
@ExtendWith(ApplicationExtension.class)
class BreakpointPropertiesDialogTest {

    @Start
    void start(Stage stage) {
    }

    @Test
    void showsCurrentSettingsAndReturnsEdits() {
        HitCondition atLeast4 = new HitCondition(HitCondition.Mode.AT_LEAST, 4);
        BreakpointPropertiesDialog.Settings result = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            BreakpointPropertiesDialog dialog = new BreakpointPropertiesDialog("a.xsl, line 3",
                    new BreakpointPropertiesDialog.Settings("@id = 1", atLeast4), 7);
            BreakpointPropertiesDialog.Settings initial = dialog.currentSettings();
            assertEquals("@id = 1", initial.condition());
            assertEquals(atLeast4, initial.hit());
            assertEquals("Hits in the current run: 7", dialog.hitsText());
            dialog.setControls("  $n > 2 ", HitCondition.Mode.MULTIPLE_OF, 5);
            return dialog.currentSettings();
        });
        assertEquals("$n > 2", result.condition(), "trimmed");
        assertEquals(new HitCondition(HitCondition.Mode.MULTIPLE_OF, 5), result.hit());
    }

    @Test
    void everyHitIgnoresTheTargetAndNoRunSaysSo() {
        BreakpointPropertiesDialog.Settings result = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            BreakpointPropertiesDialog dialog = new BreakpointPropertiesDialog("a.xsl, line 3",
                    new BreakpointPropertiesDialog.Settings("", null), -1);
            assertEquals("Hits are counted while a debug run is active.", dialog.hitsText());
            dialog.setControls("", HitCondition.Mode.ALWAYS, 9);
            return dialog.currentSettings();
        });
        assertEquals(HitCondition.ALWAYS, result.hit());
        assertEquals("", result.condition());
    }
}
