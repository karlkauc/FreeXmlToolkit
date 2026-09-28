package org.fxt.freexmltoolkit.service.telemetry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Test double: records every {@link #track tracked} event (built exactly like the real service
 * builds it) and delegates everything else to the no-op service. Install with
 * {@link Telemetry#install(TelemetryService)}, remove with {@link Telemetry#reset()}.
 */
public class RecordingTelemetryService implements TelemetryService {

    private final List<TelemetryEvent> events = new CopyOnWriteArrayList<>();
    private final List<String> errorReports = new CopyOnWriteArrayList<>();
    private final TelemetryService noop = TelemetryService.noop();

    /** @return the recorded events in call order */
    public List<TelemetryEvent> events() {
        return events;
    }

    /** @return the {@code where} of every reported error ({@link #trackError}), in call order */
    public List<String> errorReports() {
        return errorReports;
    }

    /** @return the recorded events of the given type */
    public List<TelemetryEvent> events(String eventType) {
        return events.stream().filter(e -> e.eventType().equals(eventType)).toList();
    }

    @Override
    public void track(String eventType, TelemetryEvent.Category category, Consumer<TelemetryEvent.Builder> customizer) {
        TelemetryEvent.Builder builder = TelemetryEvent.builder(eventType, category);
        if (customizer != null) {
            customizer.accept(builder);
        }
        events.add(builder.build());
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void trackAppStart(Consumer<TelemetryEvent.Builder> context) {
        track("app_start", TelemetryEvent.Category.LIFECYCLE, context);
    }

    @Override
    public void trackAppExit(Duration sessionDuration, Consumer<TelemetryEvent.Builder> summary) {
        track("app_exit", TelemetryEvent.Category.LIFECYCLE, b -> {
            if (sessionDuration != null) {
                b.durationMs(sessionDuration.toMillis());
            }
            if (summary != null) {
                summary.accept(b);
            }
        });
    }

    @Override
    public void trackError(Throwable t, String where) {
        errorReports.add(where);
        noop.trackError(t, where);
    }

    @Override
    public void trackError(String errorCode, String where) {
        errorReports.add(where);
        noop.trackError(errorCode, where);
    }

    @Override
    public CompletableFuture<ErrorReportResult> sendErrorReport(String description, String contact,
                                                                Throwable throwableOrNull, boolean includeTechnicalDetails) {
        return noop.sendErrorReport(description, contact, throwableOrNull, includeTechnicalDetails);
    }

    @Override
    public String previewErrorReport(String description, String contact, Throwable throwableOrNull,
                                     boolean includeTechnicalDetails) {
        return noop.previewErrorReport(description, contact, throwableOrNull, includeTechnicalDetails);
    }

    @Override
    public boolean isUsageEnabled() {
        return true;
    }

    @Override
    public void setUsageEnabled(boolean enabled) {
        // not needed in tests
    }

    @Override
    public boolean isErrorReportingEnabled() {
        return true;
    }

    @Override
    public void setErrorReportingEnabled(boolean enabled) {
        // not needed in tests
    }

    @Override
    public boolean isNoticeShown() {
        return true;
    }

    @Override
    public void markNoticeShown() {
        // not needed in tests
    }

    @Override
    public void flushAsync() {
        // not needed in tests
    }

    @Override
    public void shutdown(Duration timeout) {
        // not needed in tests
    }

    @Override
    public String getInstallId() {
        return noop.getInstallId();
    }

    @Override
    public String resetInstallId() {
        return noop.resetInstallId();
    }

    @Override
    public String previewPayload() {
        return noop.previewPayload();
    }
}
