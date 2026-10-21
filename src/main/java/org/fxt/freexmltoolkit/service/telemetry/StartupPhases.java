package org.fxt.freexmltoolkit.service.telemetry;

import java.util.function.Consumer;

/**
 * Startup phase marks for the {@code app_start} event ({@code meta.phase_*_ms}).
 *
 * <p>{@code phase_main_ms} is measured from the JVM start (wall clock, JVM boot and class
 * loading up to {@code FxtGui.main}); every later phase is measured <b>from the entry of
 * {@code main}</b> with the monotonic {@link System#nanoTime()} clock, so they are cumulative:
 * <ul>
 *   <li>{@code phase_splash_ms} — main entry → splash screen shown</li>
 *   <li>{@code phase_fxml_ms} — main entry → main FXML loaded</li>
 *   <li>{@code phase_ready_ms} — main entry → main window shown, just before {@code app_start}</li>
 * </ul>
 * A phase that was not marked is omitted, and without the {@code main} mark (another entry
 * point, tests) no phase is reported. The event's {@code duration_ms} stays JVM start → app_start.
 */
public final class StartupPhases {

    private static volatile long mainNanos;
    private static volatile long mainSinceJvmMs = -1;
    private static volatile long splashNanos;
    private static volatile long fxmlNanos;
    private static volatile long readyNanos;

    private StartupPhases() {
    }

    /** First statement of {@code main}. */
    public static void markMain() {
        mainNanos = System.nanoTime();
        mainSinceJvmMs = LaunchContext.startupMillis();
    }

    /** The splash screen is visible. */
    public static void markSplash() {
        splashNanos = System.nanoTime();
    }

    /** The main FXML was loaded. */
    public static void markFxml() {
        fxmlNanos = System.nanoTime();
    }

    /** The main window is shown; called just before {@code app_start} is recorded. */
    public static void markReady() {
        readyNanos = System.nanoTime();
    }

    /** Adds the {@code phase_*_ms} meta of the marked phases. Never throws. */
    public static Consumer<TelemetryEvent.Builder> meta() {
        long main = mainNanos;
        long mainMs = mainSinceJvmMs;
        long splash = splashNanos;
        long fxml = fxmlNanos;
        long ready = readyNanos;
        return b -> {
            if (main == 0) {
                return;
            }
            if (mainMs >= 0) {
                b.meta("phase_main_ms", mainMs);
            }
            phase(b, "phase_splash_ms", main, splash);
            phase(b, "phase_fxml_ms", main, fxml);
            phase(b, "phase_ready_ms", main, ready);
        };
    }

    private static void phase(TelemetryEvent.Builder b, String key, long main, long mark) {
        if (mark != 0 && mark - main >= 0) {
            b.meta(key, (mark - main) / 1_000_000L);
        }
    }

    /** Sets the marks directly (tests). */
    static void set(long main, long mainSinceJvm, long splash, long fxml, long ready) {
        mainNanos = main;
        mainSinceJvmMs = mainSinceJvm;
        splashNanos = splash;
        fxmlNanos = fxml;
        readyNanos = ready;
    }

    /** Clears all marks (tests). */
    static void reset() {
        set(0, -1, 0, 0, 0);
    }
}
