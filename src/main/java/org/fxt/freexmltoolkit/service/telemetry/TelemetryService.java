package org.fxt.freexmltoolkit.service.telemetry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Anonymous, opt-out telemetry: usage events, automatic error events and user-initiated
 * error reports, sent to the self-hosted ingest service (see {@code docs/telemetry.md}).
 *
 * <p>All methods are safe to call from any thread, never block on the network (except
 * {@link #shutdown(Duration)}) and never throw. Obtain the instance via
 * {@link Telemetry#get()} — it is a no-op until the application installed the real service.
 *
 * <p><b>Privacy contract for callers:</b> never put file names, paths, document content,
 * user input or exception messages into an event. Use {@link DocKind} and counts/sizes.
 */
public interface TelemetryService {

    /**
     * @return false when telemetry is switched off for this process (tests, dev builds,
     *         {@code -Dfxt.telemetry.disabled=true}) — independent of the user toggles
     */
    boolean isActive();

    /**
     * Records a usage event. No-op when usage statistics are disabled — except for
     * {@link TelemetryEvent.Category#ERROR} events, which obey the error-report toggle.
     *
     * @param eventType  {@code [a-z0-9_]{1,40}} (normalized), e.g. {@code "validate"}
     * @param category   event category
     * @param customizer fills optional fields (status, doc kind, counts, duration, meta)
     */
    void track(String eventType, TelemetryEvent.Category category, Consumer<TelemetryEvent.Builder> customizer);

    /** {@link #track(String, TelemetryEvent.Category, Consumer)} without extra fields. */
    default void track(String eventType, TelemetryEvent.Category category) {
        track(eventType, category, b -> { });
    }

    /** Records an {@link TelemetryEvent.Category#ACTION} event. */
    default void trackAction(String eventType, Consumer<TelemetryEvent.Builder> customizer) {
        track(eventType, TelemetryEvent.Category.ACTION, customizer);
    }

    /** Records an {@link TelemetryEvent.Category#ACTION} event without extra fields. */
    default void trackAction(String eventType) {
        track(eventType, TelemetryEvent.Category.ACTION);
    }

    /** Records a {@link TelemetryEvent.Category#NAVIGATION} event. */
    default void trackNavigation(String eventType, Consumer<TelemetryEvent.Builder> customizer) {
        track(eventType, TelemetryEvent.Category.NAVIGATION, customizer);
    }

    /** Records the {@code app_start} lifecycle event (with {@code is_first_run}). */
    void trackAppStart();

    /** Records the {@code app_exit} lifecycle event with the session duration. */
    void trackAppExit(Duration sessionDuration);

    /**
     * Records an anonymous error event for {@code t} (class chain + stack signature, never the
     * message). Deduplicated per session by error hash, at most 30 error events per session.
     * Safe inside uncaught-exception handlers (recursion-guarded).
     *
     * @param t     the throwable (null is ignored)
     * @param where short code location / UI context, e.g. {@code "uncaught"}, {@code "executor"}
     */
    void trackError(Throwable t, String where);

    /**
     * Records an anonymous error event without a throwable (e.g. an error dialog that only
     * has a message). {@code errorCode} must be a short, fixed code — never message text.
     */
    void trackError(String errorCode, String where);

    /**
     * Sends a user-written error report asynchronously.
     *
     * @param description             what the user did (required, 1..4000 chars)
     * @param contact                 optional contact (≤ 200 chars), may be null/blank
     * @param throwableOrNull         the error the report refers to, if any
     * @param includeTechnicalDetails whether to attach error code, hash and stack signature
     * @return completes with the result (never exceptionally)
     */
    CompletableFuture<ErrorReportResult> sendErrorReport(String description, String contact,
                                                         Throwable throwableOrNull,
                                                         boolean includeTechnicalDetails);

    /** @return exactly the JSON that {@link #sendErrorReport} would send (pretty-printed) */
    String previewErrorReport(String description, String contact, Throwable throwableOrNull,
                              boolean includeTechnicalDetails);

    boolean isUsageEnabled();

    /** Enables/disables usage statistics; disabling drops queued usage events. */
    void setUsageEnabled(boolean enabled);

    boolean isErrorReportingEnabled();

    /** Enables/disables automatic error reports; disabling drops queued error events. */
    void setErrorReportingEnabled(boolean enabled);

    /** @return whether the one-time first-start notice was shown (nothing is sent before) */
    boolean isNoticeShown();

    /** Marks the first-start notice as shown (sending may start). */
    void markNoticeShown();

    /** Triggers a background send of queued events (non-blocking). */
    void flushAsync();

    /** Final flush (bounded by {@code timeout}) and executor shutdown. */
    void shutdown(Duration timeout);

    /** @return the anonymous installation id (random UUID) */
    String getInstallId();

    /** Replaces the installation id by a new random UUID. @return the new id */
    String resetInstallId();

    /** @return a pretty-printed sample of the {@code /v1/events} payload (for "What is sent?") */
    String previewPayload();

    /** @return a service that does nothing (used until the real one is installed) */
    static TelemetryService noop() {
        return NoOpTelemetryService.INSTANCE;
    }
}
