package org.fxt.freexmltoolkit.service.telemetry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Detects UI freezes: a daemon thread posts a ping to the JavaFX application thread every
 * {@value #PING_INTERVAL_MS} ms. When a ping is not answered within {@value #STALL_THRESHOLD_MS}
 * ms, the FX thread's stack is captured once; when the thread recovers, one {@code ui_stall}
 * event is recorded with the total blocked time, the stack signature (code locations only,
 * like error events) and the UI context ({@code meta.activity}, {@code meta.view_mode},
 * {@code doc_kind}) plus the active document's shape ({@code input_bytes} = characters,
 * {@code meta.lines}, {@code meta.max_line_len} as a size class) — long lines are the
 * classic cause of text-layout freezes.
 *
 * <p>At most {@value #MAX_REPORTS_PER_SESSION} stalls per session, and the same stack
 * signature only once. A freeze that never ends shows up as {@code prev_session_crashed}
 * on the next start instead (see {@link SessionMarker}).
 */
public final class FxStallWatchdog {

    static final long PING_INTERVAL_MS = 500;
    static final long STALL_THRESHOLD_MS = 2000;
    static final int MAX_REPORTS_PER_SESSION = 10;

    /** Receives a finished stall: duration and the FX stack captured while it was blocked. */
    interface StallSink {
        void stalled(long durationMs, StackTraceElement[] stack);
    }

    private static volatile FxStallWatchdog running;

    private final Consumer<Runnable> fxExecutor;
    private final Supplier<StackTraceElement[]> fxStack;
    private final LongSupplier nanoClock;
    private final StallSink sink;

    private final Object lock = new Object();
    private boolean pingOutstanding;
    private long pingPostedAt;
    private StackTraceElement[] capturedStack;
    private ScheduledExecutorService scheduler;

    FxStallWatchdog(Consumer<Runnable> fxExecutor, Supplier<StackTraceElement[]> fxStack,
                    LongSupplier nanoClock, StallSink sink) {
        this.fxExecutor = fxExecutor;
        this.fxStack = fxStack;
        this.nanoClock = nanoClock;
        this.sink = sink;
    }

    /**
     * Starts the process-wide watchdog; must be called on the JavaFX application thread.
     * No-op when telemetry is inactive or it already runs.
     */
    public static synchronized void startForCurrentFxThread() {
        if (running != null || !Telemetry.get().isActive()) {
            return;
        }
        Thread fxThread = Thread.currentThread();
        FxStallWatchdog watchdog = new FxStallWatchdog(javafx.application.Platform::runLater,
                fxThread::getStackTrace, System::nanoTime, new TelemetrySink());
        watchdog.start();
        running = watchdog;
    }

    /** Stops the process-wide watchdog (shutdown). */
    public static synchronized void stopRunning() {
        if (running != null) {
            running.stop();
            running = null;
        }
    }

    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FX-Stall-Watchdog");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::tickSafely, PING_INTERVAL_MS, PING_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Throwable ignored) {
            // the watchdog must never die or disturb the app
        }
    }

    /** One watchdog step: post a ping, or capture the stack of an overdue one. */
    void tick() {
        synchronized (lock) {
            long now = nanoClock.getAsLong();
            if (!pingOutstanding) {
                pingOutstanding = true;
                pingPostedAt = now;
                long postedAt = now;
                fxExecutor.accept(() -> pong(postedAt));
                return;
            }
            if (capturedStack == null && elapsedMs(pingPostedAt, now) >= STALL_THRESHOLD_MS) {
                capturedStack = fxStack.get();
            }
        }
    }

    private void pong(long postedAt) {
        StackTraceElement[] stack;
        long durationMs;
        synchronized (lock) {
            if (!pingOutstanding || postedAt != pingPostedAt) {
                return;
            }
            pingOutstanding = false;
            stack = capturedStack;
            capturedStack = null;
            durationMs = elapsedMs(postedAt, nanoClock.getAsLong());
        }
        if (stack != null && durationMs >= STALL_THRESHOLD_MS) {
            sink.stalled(durationMs, stack);
        }
    }

    private static long elapsedMs(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000L;
    }

    /** Records stalls as {@code ui_stall} telemetry events (deduplicated, capped). */
    static final class TelemetrySink implements StallSink {
        private final Set<String> seenHashes = ConcurrentHashMap.newKeySet();
        private final AtomicInteger reports = new AtomicInteger();

        @Override
        public void stalled(long durationMs, StackTraceElement[] stack) {
            Throwable carrier = new Throwable();
            carrier.setStackTrace(stack == null ? new StackTraceElement[0] : stack);
            ErrorSignature sig = ErrorSignature.of(carrier);
            if (!seenHashes.add(sig.hash()) || reports.incrementAndGet() > MAX_REPORTS_PER_SESSION) {
                return;
            }
            Map<String, String> context = UsageEvents.uiContext();
            UsageEvents.DocShape shape = UsageEvents.activeDocShape();
            Telemetry.get().track("ui_stall", TelemetryEvent.Category.ACTION, b -> {
                b.status(TelemetryEvent.Status.TIMEOUT).durationMs(durationMs)
                        .errorDetail(sig.detail()).errorHash(sig.hash());
                context.forEach(b::meta);
                b.docKind(UsageEvents.activeDocKind());
                if (shape != null) {
                    b.inputBytes(shape.chars()).meta("lines", shape.lines())
                            .meta("max_line_len", UsageEvents.lineLengthBucket(shape.maxLineLen()));
                }
            });
        }
    }
}
