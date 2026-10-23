package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private final List<Long> freezes = new ArrayList<>();
    private final StackTraceElement[] stack = {
            new StackTraceElement("org.fxt.freexmltoolkit.controls.shell.editor.EditorHost", "render", "EditorHost.java", 42)};
    private final FxStallWatchdog watchdog = new FxStallWatchdog(fxQueue::add, () -> stack, nanos::get,
            new FxStallWatchdog.StallSink() {
                @Override
                public void stalled(long durationMs, StackTraceElement[] s) {
                    stalls.add(durationMs);
                }

                @Override
                public void freezing(long blockedMs, StackTraceElement[] s) {
                    freezes.add(blockedMs);
                }
            });

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
    void ongoingFreezeIsReportedOnceBeforeItEnds() {
        watchdog.tick();            // ping posted
        for (int i = 0; i < 30; i++) {
            advanceMs(500);
            watchdog.tick();
        }
        assertEquals(List.of(10_000L), freezes, "reported once at 10 s, while still blocked");
        assertTrue(stalls.isEmpty());
        runFx();                    // the thread recovers: the regular ui_stall follows
        assertEquals(List.of(15_000L), stalls);

        // the next freeze is checked again
        watchdog.tick();
        for (int i = 0; i < 20; i++) {
            advanceMs(500);
            watchdog.tick();
        }
        assertEquals(List.of(10_000L, 10_000L), freezes);
    }

    @Test
    void stallShorterThanFreezeThresholdIsNoFreeze() {
        watchdog.tick();
        for (int i = 0; i < 19; i++) {
            advanceMs(500);
            watchdog.tick();
        }
        runFx();
        assertTrue(freezes.isEmpty());
        assertEquals(List.of(9_500L), stalls);
    }

    @Test
    void telemetrySinkReportsFreezeAndFlushesImmediately() {
        RecordingTelemetryService telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        UsageEvents.activityOpened("explorer");
        UsageEvents.activeDocumentChanged(DocKind.OTHER, "text",
                () -> { throw new AssertionError("document shape must not be measured off the FX thread"); });

        FxStallWatchdog.TelemetrySink sink = new FxStallWatchdog.TelemetrySink();
        sink.freezing(10_000, stack);
        sink.freezing(10_500, stack); // same signature

        List<TelemetryEvent> events = telemetry.events("ui_freeze");
        assertEquals(1, events.size());
        TelemetryEvent e = events.getFirst();
        assertEquals(TelemetryEvent.Status.TIMEOUT, e.status());
        assertEquals(10_000L, e.durationMs());
        assertEquals("explorer", e.meta().get("activity"));
        assertEquals(DocKind.OTHER, e.docKind());
        assertEquals(16, e.errorHash().length());
        assertNull(e.inputBytes());
        assertEquals(1, telemetry.flushCount);
        assertTrue(telemetry.events("ui_stall").isEmpty(), "a freeze does not use up the ui_stall dedup");
    }

    @Test
    void freezeReportsAreCappedPerSession() {
        RecordingTelemetryService telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        FxStallWatchdog.TelemetrySink sink = new FxStallWatchdog.TelemetrySink();
        for (int i = 0; i < FxStallWatchdog.MAX_FREEZE_REPORTS_PER_SESSION + 2; i++) {
            sink.freezing(10_000, new StackTraceElement[] {
                    new StackTraceElement("org.fxt.freexmltoolkit.X", "m" + i, "X.java", i)});
        }
        assertEquals(FxStallWatchdog.MAX_FREEZE_REPORTS_PER_SESSION, telemetry.events("ui_freeze").size());
    }

    @Test
    void nativeWaitIsNoFreeze() {
        StackTraceElement[] idle = {frame("com.sun.glass.ui.win.WinCommonDialogs", "_showFileChooser")};
        List<Long> idleFreezes = new ArrayList<>();
        FxStallWatchdog idleWatchdog = new FxStallWatchdog(fxQueue::add, () -> idle, nanos::get,
                new FxStallWatchdog.StallSink() {
                    @Override
                    public void stalled(long durationMs, StackTraceElement[] s) {
                    }

                    @Override
                    public void freezing(long blockedMs, StackTraceElement[] s) {
                        idleFreezes.add(blockedMs);
                    }
                });
        idleWatchdog.tick();
        for (int i = 0; i < 30; i++) {
            advanceMs(500);
            idleWatchdog.tick();
        }
        assertTrue(idleFreezes.isEmpty());
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
        assertNull(e.inputBytes(), "no document shape known");
    }

    @Test
    void telemetrySinkAddsTheActiveDocumentShape() {
        RecordingTelemetryService telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        UsageEvents.activeDocumentChanged(DocKind.XML, "text",
                () -> new UsageEvents.DocShape(250_000, 3, 240_000));

        new FxStallWatchdog.TelemetrySink().stalled(10_000, stack);

        TelemetryEvent e = telemetry.events("ui_stall").getFirst();
        assertEquals(250_000L, e.inputBytes());
        assertEquals(3, ((Number) e.meta().get("lines")).intValue());
        assertEquals(">=100k", e.meta().get("max_line_len"));
    }

    @Test
    void telemetrySinkReadsTheUiOnTheCallerAndReportsOnTheReporter() {
        RecordingTelemetryService telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        UsageEvents.activityOpened("validation");
        UsageEvents.activeDocumentChanged(DocKind.XML, "text");

        List<Runnable> reporter = new ArrayList<>();
        FxStallWatchdog.TelemetrySink sink = new FxStallWatchdog.TelemetrySink(reporter::add);
        sink.stalled(4000, stack);
        assertTrue(telemetry.events("ui_stall").isEmpty(),
                "signature and report must not run on the (FX) thread that ends the stall");

        // the UI changes before the reporter gets to it: the event keeps what was current at the stall
        UsageEvents.activityOpened("explorer");
        reporter.forEach(Runnable::run);

        List<TelemetryEvent> events = telemetry.events("ui_stall");
        assertEquals(1, events.size());
        assertEquals(4000L, events.getFirst().durationMs());
        assertEquals("validation", events.getFirst().meta().get("activity"));
        assertEquals(DocKind.XML, events.getFirst().docKind());
    }

    private static StackTraceElement frame(String cls, String method) {
        return new StackTraceElement(cls, method, null, -1);
    }

    @Test
    void nativeToolkitLoopsAreNotStalls() {
        // Telemetry hash 1fd8d05696bfded4: the bare platform event loop (OS modal loop)
        assertTrue(FxStallWatchdog.isNativeWait(new StackTraceElement[] {
                frame("com.sun.glass.ui.win.WinApplication", "_runLoop"),
                frame("com.sun.glass.ui.win.WinApplication", "lambda$runLoop$0"),
                frame("java.lang.Thread", "run")}));
        // Telemetry hash d404a692e81c46c4: waiting in the native Windows file dialog
        assertTrue(FxStallWatchdog.isNativeWait(new StackTraceElement[] {
                frame("com.sun.glass.ui.win.WinCommonDialogs", "_showFileChooser"),
                frame("com.sun.glass.ui.win.WinCommonDialogs", "showFileChooser_impl"),
                frame("org.fxt.freexmltoolkit.util.FileChooserHelper", "showOpenDialog")}));
        assertTrue(FxStallWatchdog.isNativeWait(new StackTraceElement[] {
                frame("com.sun.glass.ui.gtk.GtkApplication", "_enterNestedEventLoopImpl")}));
    }

    @Test
    void applicationAndRenderStallsStayReportable() {
        assertEquals(false, FxStallWatchdog.isNativeWait(stack));
        assertEquals(false, FxStallWatchdog.isNativeWait(null));
        // Telemetry hash e4d6d1573ad9696a: the FX thread waits for the render thread
        assertEquals(false, FxStallWatchdog.isNativeWait(new StackTraceElement[] {
                frame("jdk.internal.misc.Unsafe", "park"),
                frame("com.sun.javafx.tk.quantum.PaintCollector", "waitForRenderingToComplete")}));
        // native code below application code is still an application stall
        assertEquals(false, FxStallWatchdog.isNativeWait(new StackTraceElement[] {
                frame("org.fxt.freexmltoolkit.controls.diff.DiffGutter", "relayout"),
                frame("com.sun.glass.ui.win.WinApplication", "_runLoop")}));
    }

    @Test
    void nativeWaitIsNotReported() {
        StackTraceElement[] idle = {frame("com.sun.glass.ui.win.WinApplication", "_runLoop")};
        FxStallWatchdog idleWatchdog = new FxStallWatchdog(fxQueue::add, () -> idle, nanos::get,
                (ms, s) -> stalls.add(ms));
        idleWatchdog.tick();
        for (int i = 0; i < 6; i++) {
            advanceMs(500);
            idleWatchdog.tick();
        }
        runFx();
        assertTrue(stalls.isEmpty());
    }

    @Test
    void stackWithoutJavaFramesIsNotReported() {
        // Telemetry (2.5.1, macOS): a ui_freeze whose stack had no frame at all - the main
        // thread sat in the native run loop
        assertTrue(FxStallWatchdog.isNativeWait(new StackTraceElement[0]));
    }

    @Test
    void stackCapturedInThePongIsNotReported() {
        // Telemetry (2.4.0/2.5.0): stacks whose top frame was FxStallWatchdog#pong - captured
        // just as the FX thread answered the ping
        assertTrue(FxStallWatchdog.isNativeWait(new StackTraceElement[] {
                frame(FxStallWatchdog.class.getName(), "pong"),
                frame(FxStallWatchdog.class.getName(), "lambda$tick$0")}));
    }

    @Test
    void suspendedProcessIsNoStallAndNoFreeze() {
        watchdog.tick();            // ping posted
        advanceMs(500);
        watchdog.tick();
        advanceMs(60_000);          // system sleep: neither the watchdog nor the FX thread ran
        watchdog.tick();            // the gap discards the pending measurement
        runFx();
        assertTrue(stalls.isEmpty(), "the pause was the whole process, not the FX thread");
        assertTrue(freezes.isEmpty());

        // detection keeps working after the wake-up
        watchdog.tick();
        for (int i = 0; i < 8; i++) {
            advanceMs(500);
            watchdog.tick();
        }
        runFx();
        assertEquals(List.of(4000L), stalls);
    }

    @Test
    void blockAfterWakeUpIsMeasuredFromTheWakeUp() {
        watchdog.tick();            // ping posted before the sleep
        advanceMs(30_000);          // sleep
        watchdog.tick();            // wake-up: the ping is re-dated
        for (int i = 0; i < 6; i++) {
            advanceMs(500);
            watchdog.tick();
        }
        runFx();                    // the FX thread was busy after the wake-up for 3 s
        assertEquals(List.of(3000L), stalls);
    }
}
