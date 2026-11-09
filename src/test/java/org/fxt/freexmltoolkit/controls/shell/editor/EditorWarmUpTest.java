package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The off-screen warm-up of the editor ({@link EditorWarmUp}) runs once and never fails loudly. */
@ExtendWith(ApplicationExtension.class)
class EditorWarmUpTest {

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    @Test
    void loadsTheClassesAndBuildsTheEditorOffScreen() throws Exception {
        for (String name : EditorWarmUp.CLASSES) {
            assertDoesNotThrow(() -> Class.forName(name, false, EditorWarmUp.class.getClassLoader()),
                    name + " must exist: a renamed class silently drops out of the warm-up");
        }
        Thread worker = new Thread(EditorWarmUp::run, "warm-up-test");
        worker.start();
        worker.join(10_000);
        assertTrue(EditorWarmUp.hasRun());
        WaitForAsyncUtils.waitForFxEvents();
        // building it again directly: no exception reaches the FX thread
        assertDoesNotThrow(() -> WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            EditorWarmUp.buildOffScreen();
            return null;
        }));
    }
}
