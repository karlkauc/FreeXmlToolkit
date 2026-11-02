package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A slow check pauses the timer, so a sluggish drive is not polled without a break. */
class ExternalChangeMonitorPauseTest {

    /** The clock; every read advances it, so one check takes one step between its two reads. */
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AtomicLong step = new AtomicLong(0);
    private final AtomicInteger checks = new AtomicInteger();
    private ExternalChangeMonitor monitor;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("a.xsd"), "<a/>");
        OpenDocument document = OpenDocument.forPath(file);
        List<ExternalChangeMonitor.Watched> snapshot =
                List.of(new ExternalChangeMonitor.Watched(document, file, DiskStamp.of(file)));
        monitor = new ExternalChangeMonitor(() -> {
            checks.incrementAndGet();
            return snapshot;
        }, changes -> { }, Runnable::run, () -> now.getAndAdd(step.get()));
    }

    @Test
    void aFastCheckDoesNotPause() {
        step.set(100);
        monitor.pollIfDue();
        monitor.pollIfDue();
        assertEquals(2, checks.get());
    }

    @Test
    void aSlowCheckPausesTheTimerForFiveTimesItsDuration() {
        step.set(2000);
        monitor.pollIfDue();
        step.set(0);

        monitor.pollIfDue();
        assertEquals(1, checks.get(), "paused");

        now.addAndGet(7_999); // the clock's third read of the check lies 2 s in the past by now
        monitor.pollIfDue();
        assertEquals(1, checks.get(), "just before the end of the pause");

        now.addAndGet(1);
        monitor.pollIfDue();
        assertEquals(2, checks.get(), "due again");
    }

    @Test
    void aFastCheckEndsThePause() {
        step.set(2000);
        monitor.pollIfDue();
        step.set(0);

        monitor.checkNow(); // as on regaining focus
        monitor.pollIfDue();

        assertEquals(3, checks.get(), "the drive answers quickly again");
    }

    @Test
    void regainingFocusChecksDuringAPause() {
        step.set(2000);
        monitor.pollIfDue();
        step.set(2000);

        monitor.checkNow();

        assertEquals(2, checks.get());
    }
}
