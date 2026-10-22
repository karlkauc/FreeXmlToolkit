package org.fxt.freexmltoolkit.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.testfx.util.WaitForAsyncUtils;

/**
 * Measures how long an action occupies the JavaFX application thread. Regression tests for UI freezes
 * use it: the action itself runs on the FX thread and must hand heavy work to a background thread, so
 * only its FX-thread share is timed (not the background work it starts).
 *
 * <p>The budget applies to the FX thread's <em>CPU time</em> (user + system, class loading included):
 * that is the work the action does on the FX thread, independent of how busy the machine is. Wall-clock
 * time on a loaded machine (other builds, parallel test JVMs) was several times higher for the same
 * work and made these tests flaky. Blocking on the FX thread (I/O, locks) costs no CPU, so the wall-clock
 * time has a separate, generous ceiling ({@link #wallCeilingMillis}).
 */
public final class FxThreadBudget {

    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private FxThreadBudget() {
    }

    /**
     * Runs {@code onFx} on the FX thread and fails if it took longer than {@code maxMillis} of FX-thread
     * CPU time, or longer than {@link #wallCeilingMillis} wall-clock time. The best of {@code runs}
     * attempts counts, which keeps JIT warm-up and GC pauses from failing the test.
     *
     * @return the fastest measured CPU time in milliseconds
     */
    public static long assertFast(long maxMillis, int runs, String what, Runnable onFx) {
        boolean cpuTimeSupported = THREADS.isCurrentThreadCpuTimeSupported();
        long bestCpu = Long.MAX_VALUE;
        long bestWall = Long.MAX_VALUE;
        for (int i = 0; i < runs; i++) {
            long[] measured = WaitForAsyncUtils.waitForAsyncFx(60_000, (Callable<long[]>) () -> {
                long cpuStart = cpuTimeSupported ? THREADS.getCurrentThreadCpuTime() : 0;
                long start = System.nanoTime();
                onFx.run();
                long wall = System.nanoTime() - start;
                long cpu = cpuTimeSupported ? THREADS.getCurrentThreadCpuTime() - cpuStart : wall;
                return new long[]{cpu, wall};
            });
            bestCpu = Math.min(bestCpu, TimeUnit.NANOSECONDS.toMillis(measured[0]));
            bestWall = Math.min(bestWall, TimeUnit.NANOSECONDS.toMillis(measured[1]));
            WaitForAsyncUtils.waitForFxEvents();
        }
        long cpu = bestCpu;
        long wall = bestWall;
        long ceiling = wallCeilingMillis(maxMillis);
        assertTrue(cpu <= maxMillis, () -> what + " occupied the FX thread for " + cpu
                + " ms CPU time (budget " + maxMillis + " ms, wall-clock " + wall + " ms)");
        assertTrue(wall <= ceiling, () -> what + " blocked the FX thread for " + wall
                + " ms wall-clock time (ceiling " + ceiling + " ms, CPU time " + cpu + " ms)");
        return cpu;
    }

    /**
     * Wall-clock ceiling for a CPU budget: high enough for a heavily loaded machine, low enough to catch
     * an FX thread that waits for I/O or a lock for seconds.
     */
    static long wallCeilingMillis(long maxMillis) {
        return Math.max(10 * maxMillis, 2_000);
    }

    /** {@link #assertFast(long, int, String, Runnable)} with three attempts. */
    public static long assertFast(long maxMillis, String what, Runnable onFx) {
        return assertFast(maxMillis, 3, what, onFx);
    }
}
