package org.fxt.freexmltoolkit.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.testfx.util.WaitForAsyncUtils;

/**
 * Measures how long an action occupies the JavaFX application thread. Regression tests for UI freezes
 * use it: the action itself runs on the FX thread and must hand heavy work to a background thread, so
 * only its FX-thread share is timed (not the background work it starts).
 */
public final class FxThreadBudget {

    private FxThreadBudget() {
    }

    /**
     * Runs {@code onFx} on the FX thread and fails if it took longer than {@code maxMillis}. The best of
     * {@code runs} attempts counts, which keeps JIT warm-up and GC pauses on a loaded CI machine from
     * failing the test.
     *
     * @return the fastest measured duration in milliseconds
     */
    public static long assertFast(long maxMillis, int runs, String what, Runnable onFx) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < runs; i++) {
            long nanos = WaitForAsyncUtils.waitForAsyncFx(60_000, (Callable<Long>) () -> {
                long start = System.nanoTime();
                onFx.run();
                return System.nanoTime() - start;
            });
            best = Math.min(best, TimeUnit.NANOSECONDS.toMillis(nanos));
            WaitForAsyncUtils.waitForFxEvents();
        }
        long measured = best;
        assertTrue(measured <= maxMillis,
                () -> what + " occupied the FX thread for " + measured + " ms (budget " + maxMillis + " ms)");
        return measured;
    }

    /** {@link #assertFast(long, int, String, Runnable)} with three attempts. */
    public static long assertFast(long maxMillis, String what, Runnable onFx) {
        return assertFast(maxMillis, 3, what, onFx);
    }
}
