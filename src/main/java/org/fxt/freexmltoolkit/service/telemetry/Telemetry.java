package org.fxt.freexmltoolkit.service.telemetry;

import java.util.Objects;

/**
 * Static access point to the application's {@link TelemetryService}.
 *
 * <p>Returns a no-op service until {@code FxtGui} {@link #install installs} the real one, so
 * library code, dialogs and unit tests can call it unconditionally without ever sending
 * anything (tests never start {@code FxtGui}).
 *
 * <pre>{@code
 * Telemetry.get().trackAction("validate", b -> b.docKind(path).durationMs(ms).errorCount(n));
 * Telemetry.reportError(ex, "xslt.transform");
 * }</pre>
 */
public final class Telemetry {

    private static volatile TelemetryService service = TelemetryService.noop();

    private Telemetry() {
    }

    /** @return the installed service, or a no-op service */
    public static TelemetryService get() {
        return service;
    }

    /** Installs the application's service (called once during startup). */
    public static void install(TelemetryService telemetryService) {
        service = Objects.requireNonNullElse(telemetryService, TelemetryService.noop());
    }

    /** Resets to the no-op service (tests). */
    public static void reset() {
        service = TelemetryService.noop();
    }

    /** Null-safe, never-throwing shortcut for {@link TelemetryService#trackError(Throwable, String)}. */
    public static void reportError(Throwable t, String where) {
        try {
            service.trackError(t, where);
        } catch (Throwable ignored) {
            // telemetry must never disturb the caller
        }
    }

    /** Never-throwing shortcut for {@link TelemetryService#trackError(String, String)}. */
    public static void reportError(String errorCode, String where) {
        try {
            service.trackError(errorCode, where);
        } catch (Throwable ignored) {
            // telemetry must never disturb the caller
        }
    }
}
