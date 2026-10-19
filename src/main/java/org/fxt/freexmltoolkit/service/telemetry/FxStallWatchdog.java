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
 * signature only once.
 *
 * <p>A freeze that never ends (the user kills the app) would never reach {@code ui_stall}.
 * Therefore a block that lasts {@value #FREEZE_REPORT_MS} ms is reported <em>while it is still
 * ongoing</em> as one {@code ui_freeze} event (fresh stack, UI context, no document shape — that
 * one can only be measured on the blocked FX thread) and flushed right away; the queue is
 * persistent, so it is also sent at the next start. When the thread recovers, the regular
 * {@code ui_stall} follows, so a {@code ui_freeze} without a {@code ui_stall} of the same session
 * marks a freeze that never ended (usually followed by {@code prev_session_crashed}, see
 * {@link SessionMarker}). At most {@value #MAX_FREEZE_REPORTS_PER_SESSION} per session.
 */
public final class FxStallWatchdog {

    static final long PING_INTERVAL_MS = 500;
    static final long STALL_THRESHOLD_MS = 2000;
    static final int MAX_REPORTS_PER_SESSION = 10;
    /** Block duration after which an ongoing freeze is reported before it ends. */
    static final long FREEZE_REPORT_MS = 10_000;
    static final int MAX_FREEZE_REPORTS_PER_SESSION = 3;

    /** Receives a finished stall: duration and the FX stack captured while it was blocked. */
    interface StallSink {
        void stalled(long durationMs, StackTraceElement[] stack);

        /** The FX thread has been blocked for {@code blockedMs} so far and still is. */
        default void freezing(long blockedMs, StackTraceElement[] stack) {
        }
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
    /** Whether the outstanding ping was already checked for an ongoing freeze. */
    private boolean freezeChecked;
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
                fxThread::getStackTrace, System::nanoTime, new TelemetrySink(TelemetrySink.backgroundReporter()));
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

    /**
     * One watchdog step: post a ping, capture the stack of an overdue one, or report an
     * ongoing freeze once it lasts {@value #FREEZE_REPORT_MS} ms.
     */
    void tick() {
        long freezeMs = -1;
        StackTraceElement[] freezeStack = null;
        synchronized (lock) {
            long now = nanoClock.getAsLong();
            if (!pingOutstanding) {
                pingOutstanding = true;
                pingPostedAt = now;
                freezeChecked = false;
                long postedAt = now;
                fxExecutor.accept(() -> pong(postedAt));
                return;
            }
            long elapsed = elapsedMs(pingPostedAt, now);
            if (capturedStack == null && elapsed >= STALL_THRESHOLD_MS) {
                capturedStack = fxStack.get();
            } else if (!freezeChecked && elapsed >= FREEZE_REPORT_MS) {
                freezeChecked = true;
                // fresh stack: after 10 s it shows where the thread is stuck, not where it was at 2 s
                freezeStack = fxStack.get();
                freezeMs = elapsed;
            }
        }
        if (freezeStack != null && !isNativeWait(freezeStack)) {
            sink.freezing(freezeMs, freezeStack); // outside the lock: the sink enqueues and flushes
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
        if (stack != null && durationMs >= STALL_THRESHOLD_MS && !isNativeWait(stack)) {
            sink.stalled(durationMs, stack);
        }
    }

    /**
     * Whether the FX thread sat in a native toolkit loop when the stack was captured: the
     * platform event loop itself (an OS modal loop such as dragging or resizing the window
     * keeps it from running posted work) or a native file/folder dialog. No application
     * code is stuck there, so these are not stalls worth reporting.
     */
    static boolean isNativeWait(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) {
            return false;
        }
        StackTraceElement top = stack[0];
        String cls = top.getClassName();
        String method = top.getMethodName();
        if (!cls.startsWith("com.sun.glass.ui.")) {
            return false;
        }
        return method.startsWith("_runLoop")
                || method.startsWith("_enterNestedEventLoop")
                || (cls.endsWith("CommonDialogs") && method.startsWith("_show"));
    }

    private static long elapsedMs(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000L;
    }

    /** Records stalls as {@code ui_stall} telemetry events (deduplicated, capped). */
    static final class TelemetrySink implements StallSink {
        private final Set<String> seenHashes = ConcurrentHashMap.newKeySet();
        private final AtomicInteger reports = new AtomicInteger();

        private final Set<String> seenFreezeHashes = ConcurrentHashMap.newKeySet();
        private final AtomicInteger freezeReports = new AtomicInteger();
        /** Runs the signature and the report of a finished stall; the stall itself ends on the FX thread. */
        private final java.util.concurrent.Executor reporter;

        /** Reports synchronously, on the calling thread. */
        TelemetrySink() {
            this(Runnable::run);
        }

        TelemetrySink(java.util.concurrent.Executor reporter) {
            this.reporter = reporter;
        }

        /**
         * A daemon thread for {@link #stalled}: it is called on the FX thread right after a stall, and
         * normalizing and hashing the stack there (regular expressions over every frame) showed up as a
         * stall of its own in the reports.
         */
        static java.util.concurrent.Executor backgroundReporter() {
            return Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "FX-Stall-Reporter");
                t.setDaemon(true);
                return t;
            });
        }

        @Override
        public void stalled(long durationMs, StackTraceElement[] stack) {
            if (reports.get() >= MAX_REPORTS_PER_SESSION) {
                return;
            }
            // UI context and document shape are read here, on the FX thread; the rest runs on the reporter
            Map<String, String> context = UsageEvents.uiContext();
            UsageEvents.DocShape shape = UsageEvents.activeDocShape();
            DocKind docKind = UsageEvents.activeDocKind();
            try {
                reporter.execute(() -> report(durationMs, stack, context, shape, docKind));
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // shutting down: the stall goes unreported
            }
        }

        private void report(long durationMs, StackTraceElement[] stack, Map<String, String> context,
                            UsageEvents.DocShape shape, DocKind docKind) {
            ErrorSignature sig = signature(stack);
            if (!seenHashes.add(sig.hash()) || reports.incrementAndGet() > MAX_REPORTS_PER_SESSION) {
                return;
            }
            Telemetry.get().track("ui_stall", TelemetryEvent.Category.ACTION, b -> {
                b.status(TelemetryEvent.Status.TIMEOUT).durationMs(durationMs)
                        .errorDetail(sig.detail()).errorHash(sig.hash());
                context.forEach(b::meta);
                b.docKind(docKind);
                if (shape != null) {
                    b.inputBytes(shape.chars()).meta("lines", shape.lines())
                            .meta("max_line_len", UsageEvents.lineLengthBucket(shape.maxLineLen()));
                }
            });
        }

        /**
         * Runs on the watchdog thread while the FX thread is blocked: only reads volatile UI
         * context (the document shape would have to be measured on the FX thread).
         */
        @Override
        public void freezing(long blockedMs, StackTraceElement[] stack) {
            ErrorSignature sig = signature(stack);
            if (!seenFreezeHashes.add(sig.hash())
                    || freezeReports.incrementAndGet() > MAX_FREEZE_REPORTS_PER_SESSION) {
                return;
            }
            Map<String, String> context = UsageEvents.uiContext();
            TelemetryService telemetry = Telemetry.get();
            telemetry.track("ui_freeze", TelemetryEvent.Category.ACTION, b -> {
                b.status(TelemetryEvent.Status.TIMEOUT).durationMs(blockedMs)
                        .errorDetail(sig.detail()).errorHash(sig.hash());
                context.forEach(b::meta);
                b.docKind(UsageEvents.activeDocKind());
            });
            // the app may be killed any moment now — send it instead of waiting for the next flush
            telemetry.flushAsync();
        }

        private static ErrorSignature signature(StackTraceElement[] stack) {
            Throwable carrier = new Throwable();
            carrier.setStackTrace(stack == null ? new StackTraceElement[0] : stack);
            return ErrorSignature.of(carrier);
        }
    }
}
