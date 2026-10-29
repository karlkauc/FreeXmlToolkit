package org.fxt.freexmltoolkit.service.telemetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Static access point to the application's {@link TelemetryService}.
 *
 * <p>Returns a no-op service until {@code FxtGui} {@link #install installs} the real one, so
 * library code, dialogs and unit tests can call it unconditionally without ever sending
 * anything (tests never start {@code FxtGui}). Errors reported before the real service is
 * installed are kept (at most {@value #MAX_PENDING}) and replayed on installation, so failures
 * during the first milliseconds of startup are not lost.
 *
 * <pre>{@code
 * Telemetry.get().trackAction("validate", b -> b.docKind(path).durationMs(ms).errorCount(n));
 * Telemetry.reportError(ex, "xslt.transform");
 * }</pre>
 */
public final class Telemetry {

    private static final Logger logger = LogManager.getLogger(Telemetry.class);
    /** Errors reported before {@link #install} that are replayed once the real service is there. */
    static final int MAX_PENDING = 20;

    private static volatile TelemetryService service = TelemetryService.noop();
    private static volatile boolean installed;
    private static final List<Object[]> pending = new ArrayList<>();
    /**
     * Throwables that were reported explicitly, so the log bridge ({@link TelemetryLogAppender})
     * does not report them a second time when they are also logged. Weak: entries vanish with
     * the exception.
     */
    private static final Set<Throwable> reported = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile boolean shuttingDown;

    private Telemetry() {
    }

    /** @return the installed service, or a no-op service */
    public static TelemetryService get() {
        return service;
    }

    /** Installs the application's service (called once during startup) and replays buffered errors. */
    public static void install(TelemetryService telemetryService) {
        List<Object[]> replay;
        synchronized (pending) {
            service = Objects.requireNonNullElse(telemetryService, TelemetryService.noop());
            installed = telemetryService != null;
            replay = new ArrayList<>(pending);
            pending.clear();
        }
        if (installed) {
            // Directly to the service: the throwables are already in the reported set.
            for (Object[] entry : replay) {
                try {
                    if (entry[0] instanceof Throwable t) {
                        service.trackError(t, (String) entry[1]);
                    } else {
                        service.trackError((String) entry[0], (String) entry[1]);
                    }
                } catch (Throwable ignored) {
                    // telemetry must never disturb startup
                }
            }
        }
    }

    /** Resets to the no-op service and drops buffered errors (tests). */
    public static void reset() {
        synchronized (pending) {
            service = TelemetryService.noop();
            installed = false;
            pending.clear();
        }
        synchronized (reported) {
            reported.clear();
        }
        shuttingDown = false;
    }

    /** Set when the application is quitting: stack-less NPEs reported from then on are window teardown noise. */
    public static void markShuttingDown() {
        shuttingDown = true;
    }

    /**
     * Whether an exception is shutdown noise: on Linux, Glass/GTK reports a
     * {@link NullPointerException} without any stack trace while the windows are torn down at
     * exit. Such an exception carries nothing to act on, and the application ends anyway.
     */
    public static boolean isShutdownNoise(Throwable throwable) {
        return shuttingDown && throwable instanceof NullPointerException
                && throwable.getStackTrace().length == 0;
    }

    /**
     * Null-safe, never-throwing shortcut for {@link TelemetryService#trackError(Throwable, String)}.
     * The same throwable instance is reported once: logging it afterwards (the log bridge) or
     * showing it in a dialog does not count it again; the first {@code where} wins.
     */
    public static void reportError(Throwable t, String where) {
        if (t == null) {
            return;
        }
        try {
            synchronized (reported) {
                if (!reported.add(t)) {
                    return;
                }
            }
            if (!installed && buffer(t, where)) {
                return;
            }
            service.trackError(t, where);
        } catch (Throwable ignored) {
            // telemetry must never disturb the caller
        }
    }

    /** Never-throwing shortcut for {@link TelemetryService#trackError(String, String)}. */
    public static void reportError(String errorCode, String where) {
        try {
            if (!installed && buffer(errorCode, where)) {
                return;
            }
            service.trackError(errorCode, where);
        } catch (Throwable ignored) {
            // telemetry must never disturb the caller
        }
    }

    /**
     * Reports a failure unless it is the user's input (a document or schema that does not parse,
     * a missing file): parsers that fall back to a placeholder call this, so parser bugs are
     * counted but broken documents are not. Never throws.
     */
    public static void reportUnlessUserInput(Throwable t, String where) {
        if (t == null) {
            return;
        }
        try {
            if (QueryFailure.classify(t).status() != TelemetryEvent.Status.INVALID_INPUT) {
                reportError(t, where);
            }
        } catch (Throwable ignored) {
            // never disturb the caller
        }
    }

    /**
     * Marks a throwable as reported without sending it: its classification travels with an
     * action event ({@link QueryFailure#of}), so logging it afterwards must not count it again.
     */
    public static void markReported(Throwable t) {
        if (t == null) {
            return;
        }
        synchronized (reported) {
            reported.add(t);
        }
    }

    /**
     * Reports an exception that was logged ({@link TelemetryLogAppender}) unless it was already
     * reported explicitly or is shutdown noise. Never throws.
     */
    public static void reportLogged(Throwable t, String where) {
        if (t == null || isShutdownNoise(t)) {
            return;
        }
        try {
            synchronized (reported) {
                if (reported.contains(t)) {
                    return;
                }
            }
            reportError(t, where);
        } catch (Throwable ignored) {
            // never disturb logging
        }
    }

    /**
     * Reports a failure of asynchronous work: {@link CompletionException} /
     * {@link ExecutionException} wrappers are unwrapped, cancellation and interruption are
     * expected and not reported. Logs the failure too. Never throws.
     *
     * @return the unwrapped failure, or {@code null} when nothing was reported
     */
    public static Throwable reportUnexpected(Throwable throwable, String where) {
        Throwable t = throwable;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t == null || t instanceof CancellationException || t instanceof InterruptedException) {
            return null;
        }
        reportError(t, where);
        try {
            logger.error("Background work failed ({})", where, t);
        } catch (Throwable ignored) {
            // logging must not break the caller
        }
        return t;
    }

    /**
     * A {@code whenComplete} handler for {@link java.util.concurrent.CompletableFuture} chains
     * (which otherwise keep their exception silently): reports the failure, if any.
     *
     * <pre>{@code future.whenComplete(Telemetry.whenComplete("editor.save"));}</pre>
     */
    public static <T> BiConsumer<T, Throwable> whenComplete(String where) {
        return (value, error) -> {
            if (error != null) {
                reportUnexpected(error, where);
            }
        };
    }

    /**
     * Wraps a periodic or fire-and-forget task so that a failure is reported and logged but does
     * not end the task's schedule (a {@code scheduleAtFixedRate} task that throws is never run
     * again) and never reaches the thread.
     */
    public static Runnable guard(String where, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                reportUnexpected(t, where);
            }
        };
    }

    private static boolean buffer(Object codeOrThrowable, String where) {
        synchronized (pending) {
            if (installed) {
                return false;
            }
            if (pending.size() < MAX_PENDING) {
                pending.add(new Object[] {codeOrThrowable, where});
            }
            return true;
        }
    }

    /** @return how many errors wait for the real service (tests) */
    static int pendingCount() {
        synchronized (pending) {
            return pending.size();
        }
    }
}
