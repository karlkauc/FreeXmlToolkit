package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Drives the watchdog by hand: a fake clock, a queued "FX thread" and a canned stack. */
class FxStallWatchdogTest {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final List<Runnable> fxQueue = new ArrayList<>();
    private final List<Long> stalls = new ArrayList<>();
    private final StackTraceElement[] stack = {
            new StackTraceElement("org.fxt.freexmltoolkit.controls.shell.editor.EditorHost", "render", "EditorHost.java", 42)};
    private final FxStallWatchdog watchdog = new FxStallWatchdog(fxQueue::add, () -> stack, nanos::get,
            (ms, s) -> stalls.add(ms));

    @AfterEach
    void tearDown() {
        Telemetry.reset();
        UsageEvents.resetSession();
    }

    private void advanceMs(long ms) {
        nanos.addAndGet(ms * 1_000_000L);
    }

    private void runFx() {
        List<Runnable> copy = new ArrayList<>(fxQueue);
        fxQueue.clear();
        copy.forEach(Runnable::run);
    }

    @Test
    void responsiveFxThreadReportsNothing() {
        for (int i = 0; i < 10; i++) {
            watchdog.tick();
            advanceMs(100);
            runFx();
            advanceMs(400);
        }
        assertTrue(stalls.isEmpty());
    }

    @Test
    void blockedFxThreadIsReportedOnceWhenItRecovers() {
        watchdog.tick();            // ping posted
        for (int i = 0; i < 8; i++) {
            advanceMs(500);
            watchdog.tick();        // stack captured once past 2 s, no second ping
        }
        assertEquals(1, fxQueue.size(), "no new ping while one is outstanding");
        assertTrue(stalls.isEmpty(), "reported only after recovery");
        runFx();
        assertEquals(List.of(4000L), stalls);
    }

    @Test
    void shortDelayBelowThresholdIsIgnored() {
        watchdog.tick();
        advanceMs(500);
        watchdog.tick();
        advanceMs(1000);
        runFx();
        assertTrue(stalls.isEmpty());
    }

    @Test
    void telemetrySinkDeduplicatesByStackAndAddsUiContext() {
        RecordingTelemetryService telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        UsageEvents.activityOpened("schema");
        UsageEvents.activeDocumentChanged(DocKind.XSD, "graphic");

        FxStallWatchdog.TelemetrySink sink = new FxStallWatchdog.TelemetrySink();
        sink.stalled(3000, stack);
        sink.stalled(5000, stack); // same signature

        List<TelemetryEvent> events = telemetry.events("ui_stall");
        assertEquals(1, events.size());
        TelemetryEvent e = events.getFirst();
        assertEquals(TelemetryEvent.Status.TIMEOUT, e.status());
        assertEquals(3000L, e.durationMs());
        assertEquals("schema", e.meta().get("activity"));
        assertEquals("graphic", e.meta().get("view_mode"));
        assertEquals(DocKind.XSD, e.docKind());
        assertTrue(e.errorDetail().contains("EditorHost"), e.errorDetail());
        assertEquals(16, e.errorHash().length());
    }
}
