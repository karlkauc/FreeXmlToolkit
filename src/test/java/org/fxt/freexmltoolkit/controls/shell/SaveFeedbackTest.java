package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A save is confirmed in the status bar, also when the file had no changes (telemetry: users pressed
 * Save several times because nothing visible happened).
 */
@ExtendWith(ApplicationExtension.class)
class SaveFeedbackTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1000, 700));
        stage.show();
    }

    @Test
    void messagesNameTheFileAndTheTime() throws Exception {
        String[] messages = WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            Label label = new Label();
            SaveFeedback feedback = new SaveFeedback(label, () -> LocalTime.of(12, 55, 18));
            assertNull(feedback.message(), "hidden until something was saved");
            assertFalse(label.isManaged(), "takes no room while hidden");
            feedback.saved("a.xml");
            String one = feedback.message();
            feedback.savedAll(3);
            String three = feedback.message();
            feedback.savedAll(1);
            String single = feedback.message();
            feedback.savedAll(0);
            return new String[] {one, three, single, feedback.message()};
        });
        assertEquals("Saved a.xml (12:55:18)", messages[0]);
        assertEquals("Saved 3 files (12:55:18)", messages[1]);
        assertEquals("Saved 1 file (12:55:18)", messages[2]);
        assertEquals("No unsaved changes", messages[3]);
    }

    @Test
    void savingAnUnchangedFileIsConfirmedInTheStatusBar(@TempDir Path tmp) throws Exception {
        Path xml = Files.writeString(tmp.resolve("saved.xml"), "<root/>");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.openFile(xml));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("<root/>")).orElse(false)));
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.saveFeedback().message()));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            shell.onSave();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.saveFeedback().message() != null));
        String message = WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.saveFeedback().message());
        assertTrue(message.startsWith("Saved saved.xml ("), message);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.lookup("#status-saved").isVisible()), "the status-bar label shows it");
    }
}
