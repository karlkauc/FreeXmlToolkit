package org.fxt.freexmltoolkit.service.telemetry;

import java.util.function.Consumer;

/**
 * Startup phase marks for the {@code app_start} event ({@code meta.phase_*_ms}).
 *
 * <p>{@code phase_main_ms} is measured from the JVM start (wall clock, JVM boot and class
 * loading up to {@code FxtGui.main}); {@code phase_clinit_ms} is the part of it before the first
 * static initializer of {@code FxtGui} (JVM boot, launcher, runtime image), so
 * {@code phase_main_ms - phase_clinit_ms} is that class's static setup (Log4j, which opens every JAR
 * on the class path for its resource lookups). {@code phase_cp_open_ms} is how long opening every
 * class-path JAR once took, measured in that static setup just before Log4j (which would open them a
 * moment later anyway). Every later phase is measured <b>from the entry of
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

    private static volatile long clinitNanos;
    private static volatile long cpOpenMs = -1;
    private static volatile long mainNanos;
    private static volatile long mainSinceJvmMs = -1;
    private static volatile long splashNanos;
    private static volatile long fxmlNanos;
    private static volatile long readyNanos;

    private StartupPhases() {
    }

    /** First statement of {@code FxtGui}'s first static initializer (before Log4j). */
    public static void markClinit() {
        clinitNanos = System.nanoTime();
    }

    /** A resource name no JAR contains: looking it up has to open every JAR on the class path. */
    static final String PROBE_RESOURCE = "META-INF/fxt-startup-probe.absent";

    /**
     * Opens every JAR on the class path once (a lookup of a resource none of them has) and records
     * how long that took. The class loader keeps the JARs open, so the lookups Log4j makes right
     * after do not repeat the work. Never throws.
     *
     * @param loader the application class loader
     */
    public static void probeClasspath(ClassLoader loader) {
        if (loader == null) {
            return;
        }
        long t0 = System.nanoTime();
        try {
            var resources = loader.getResources(PROBE_RESOURCE);
            while (resources.hasMoreElements()) {
                resources.nextElement();
            }
            cpOpenMs = (System.nanoTime() - t0) / 1_000_000L;
        } catch (Throwable t) {
            cpOpenMs = -1;
        }
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
        long clinit = clinitNanos;
        long cpOpen = cpOpenMs;
        return b -> {
            if (main == 0) {
                return;
            }
            if (mainMs >= 0) {
                b.meta("phase_main_ms", mainMs);
                if (clinit != 0 && main - clinit >= 0) {
                    b.meta("phase_clinit_ms", Math.max(0, mainMs - (main - clinit) / 1_000_000L));
                }
            }
            if (cpOpen >= 0) {
                b.meta("phase_cp_open_ms", cpOpen);
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

    /** Sets the static-setup marks directly (tests). */
    static void setClinit(long clinit, long cpOpen) {
        clinitNanos = clinit;
        cpOpenMs = cpOpen;
    }

    /** Clears all marks (tests). */
    static void reset() {
        set(0, -1, 0, 0, 0);
        setClinit(0, -1);
    }
}
