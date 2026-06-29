package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.control.Label;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the severity-aware status helper: each call applies exactly one severity
 * style class (and the matching icon), and {@link PanelStatus#strip} cleans the
 * {@code "ERROR:"} prefix the runners emit. The dialog raised by {@code failure(...)}
 * is suppressed in the test JVM via the {@code fxt.suppressErrorDialogs} system
 * property (set by the Gradle test task), so it never blocks.
 *
 * <p>Labels are created on the FX thread (the JavaFX toolkit is only initialised once
 * {@link #start} has run), never in a field initialiser.
 */
@ExtendWith(ApplicationExtension.class)
class PanelStatusTest {

    @Start
    void start(Stage stage) {
        // No scene required — the helper only mutates the Label.
    }

    @Test
    void severityClassesAreMutuallyExclusive() {
        Label status = newLabel();

        run(() -> PanelStatus.info(status, "Working…"));
        assertTrue(status.getStyleClass().contains("fxt-status-info"));
        assertNull(status.getGraphic(), "info has no icon");

        run(() -> PanelStatus.success(status, "Done"));
        assertTrue(status.getStyleClass().contains("fxt-status-success"));
        assertFalse(status.getStyleClass().contains("fxt-status-info"),
                "the previous severity class must be removed");
        assertNotNull(status.getGraphic(), "success shows a check icon");

        run(() -> PanelStatus.precondition(status, "Select a file first."));
        assertTrue(status.getStyleClass().contains("fxt-status-error"));
        assertFalse(status.getStyleClass().contains("fxt-status-success"));

        run(() -> PanelStatus.failure(status, "PDF generation failed", "ERROR: boom"));
        assertTrue(status.getStyleClass().contains("fxt-status-error"));
        assertEquals(1, status.getStyleClass().stream()
                .filter(c -> c.startsWith("fxt-status-")).count(),
                "exactly one severity class at a time");
    }

    @Test
    void failureStripsTheErrorPrefixForDisplay() {
        Label status = newLabel();
        run(() -> PanelStatus.failure(status, "Title", "ERROR: something broke"));
        assertEquals("something broke", status.getText());
    }

    @Test
    void stripHandlesPrefixVariantsAndPlainMessages() {
        assertEquals("boom", PanelStatus.strip("ERROR: boom"));
        assertEquals("boom", PanelStatus.strip("ERROR boom"));
        assertEquals("plain message", PanelStatus.strip("plain message"));
        assertEquals("", PanelStatus.strip(null));
    }

    /** Creates a Label on the FX thread (its static init needs the toolkit). */
    private static Label newLabel() {
        AtomicReference<Label> ref = new AtomicReference<>();
        run(() -> ref.set(new Label()));
        return ref.get();
    }

    private static void run(Runnable action) {
        javafx.application.Platform.runLater(action);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
