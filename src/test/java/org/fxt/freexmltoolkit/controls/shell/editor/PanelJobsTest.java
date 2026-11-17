package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.progress.Progress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The progress block of the side panels: what runs now, steps, Cancel, hints, several jobs, refused second starts. */
@ExtendWith(ApplicationExtension.class)
class PanelJobsTest {

    private PanelProgress view;
    private PanelJobs jobs;

    @Start
    void start(Stage stage) {
        view = new PanelProgress();
        jobs = new PanelJobs(view);
        stage.setScene(new Scene(new VBox(view), 400, 200));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> c) {
        return WaitForAsyncUtils.waitForAsyncFx(3000, c);
    }

    @Test
    void showsWhatRunsAndEndsCleanly() throws Exception {
        assertFalse(fx(view::isRunning), "hidden while idle");
        PanelJobs.Job job = fx(() -> jobs.start("identifiers", "Check identifiers online", true));
        assertNotNull(job);
        assertNull(fx(() -> jobs.start("identifiers", "Check identifiers online", true)), "a second start is refused");

        // reported from a worker thread, shown by the next refresh
        Thread worker = new Thread(() -> {
            job.message("GLEIF: request 3 of 16");
            job.progress(3, 16);
        });
        worker.start();
        worker.join();
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "GLEIF: request 3 of 16".equals(fx(view::getMessage)));
        assertTrue(fx(view::isRunning));

        // a second job is shown with the count of the others
        PanelJobs.Job other = fx(() -> jobs.start("report", "Check report", false));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> fx(view::getMessage).equals("Check report  (+1 more)"));
        fx(() -> {
            other.close();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "GLEIF: request 3 of 16".equals(fx(view::getMessage)));

        // Cancel asks the job to stop; the step that cannot be stopped hides the button
        Button cancel = fx(() -> (Button) view.lookup("#panel-progress-cancel"));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> fx(cancel::isVisible));
        job.setCancellable(false);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !fx(cancel::isVisible));
        job.setCancellable(true);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> fx(cancel::isVisible));
        fx(() -> {
            cancel.fire();
            return null;
        });
        assertTrue(job.isCancelled());

        job.close();
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !fx(view::isRunning));
        assertEquals("", fx(view::getMessage));
        assertNotNull(fx(() -> jobs.start("identifiers", "again", true)), "a finished job can start again");
    }

    @Test
    void anOpenFigiHintLinksToTheSettings() throws Exception {
        Progress.Hint[] opened = new Progress.Hint[1];
        PanelJobs.setHintHandler(h -> opened[0] = h);
        try {
            PanelJobs.Job job = fx(() -> jobs.start("identifiers", "Check identifiers online", true));
            job.hint(Progress.Hint.OPENFIGI_API_KEY);
            Hyperlink link = fx(() -> (Hyperlink) view.lookup("#panel-progress-hint-link"));
            WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> fx(link::isVisible));
            fx(() -> {
                link.fire();
                return null;
            });
            assertEquals(Progress.Hint.OPENFIGI_API_KEY, opened[0]);
            job.close();
        } finally {
            PanelJobs.setHintHandler(null);
        }
    }

    @Test
    void elapsedTimeIsMinutesAndSeconds() {
        assertEquals("0:00", PanelJobs.elapsed(0));
        assertEquals("1:05", PanelJobs.elapsed(65_000_000_000L));
        assertEquals("1:00:07", PanelJobs.elapsed(3_607_000_000_000L));
    }
}
