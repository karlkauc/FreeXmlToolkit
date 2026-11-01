package org.fxt.freexmltoolkit.screenshots;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import org.testfx.util.WaitForAsyncUtils;

/**
 * Runs screenshot-generator work on the FX thread. The FX thread can be busy for several seconds
 * (FOP's first initialisation, Saxon compiling, a GC), so a scene waits up to
 * {@link #TIMEOUT_MS} for its action instead of failing after 3-8 s. Actions are submitted once
 * and never retried, because a timed-out action is still queued and runs later anyway. When the
 * deadline passes, the FX, worker and AWT stacks are printed to show what held the FX thread.
 */
final class ScreenshotFx {

    /** How long one action or snapshot may wait for the FX thread. */
    static final long TIMEOUT_MS = 60_000;

    private ScreenshotFx() {
    }

    /** Calls {@code action} on the FX thread and returns its result. */
    static <T> T call(String tag, Callable<T> action) {
        try {
            return WaitForAsyncUtils.waitForAsyncFx(TIMEOUT_MS, action);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof TimeoutException) {
                System.out.println("[" + tag + "] FX thread did not answer within " + TIMEOUT_MS / 1000 + " s");
                printThreadStacks(tag);
            }
            throw e;
        }
    }

    /** Runs {@code action} on the FX thread and waits until it has finished. */
    static void run(String tag, Runnable action) {
        call(tag, () -> {
            action.run();
            return null;
        });
    }

    /** Prints the stacks of the FX, worker and AWT threads (diagnostics for a failed wait). */
    static void printThreadStacks(String tag) {
        Thread.getAllStackTraces().forEach((t, stack) -> {
            if (t.getName().startsWith("JavaFX Application") || t.getName().startsWith("FxtGui-Worker")
                    || t.getName().startsWith("AWT")) {
                System.out.println("[" + tag + "] STACK " + t.getName() + " " + t.getState());
                for (StackTraceElement el : stack) {
                    System.out.println("[" + tag + "]     at " + el);
                }
            }
        });
    }
}
