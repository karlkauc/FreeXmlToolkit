package org.fxt.freexmltoolkit.service.telemetry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** A {@link TelemetryService} that records and sends nothing (tests, before startup). */
final class NoOpTelemetryService implements TelemetryService {

    static final NoOpTelemetryService INSTANCE = new NoOpTelemetryService();

    private NoOpTelemetryService() {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void track(String eventType, TelemetryEvent.Category category, Consumer<TelemetryEvent.Builder> customizer) {
        // no-op
    }

    @Override
    public void trackAppStart() {
        // no-op
    }

    @Override
    public void trackAppExit(Duration sessionDuration) {
        // no-op
    }

    @Override
    public void trackError(Throwable t, String where) {
        // no-op
    }

    @Override
    public void trackError(String errorCode, String where) {
        // no-op
    }

    @Override
    public CompletableFuture<ErrorReportResult> sendErrorReport(String description, String contact,
                                                                Throwable throwableOrNull, boolean includeTechnicalDetails) {
        return CompletableFuture.completedFuture(ErrorReportResult.failure("Error reporting is not available."));
    }

    @Override
    public String previewErrorReport(String description, String contact, Throwable throwableOrNull,
                                     boolean includeTechnicalDetails) {
        return "{}";
    }

    @Override
    public boolean isUsageEnabled() {
        return false;
    }

    @Override
    public void setUsageEnabled(boolean enabled) {
        // no-op
    }

    @Override
    public boolean isErrorReportingEnabled() {
        return false;
    }

    @Override
    public void setErrorReportingEnabled(boolean enabled) {
        // no-op
    }

    @Override
    public boolean isNoticeShown() {
        return true;
    }

    @Override
    public void markNoticeShown() {
        // no-op
    }

    @Override
    public void flushAsync() {
        // no-op
    }

    @Override
    public void shutdown(Duration timeout) {
        // no-op
    }

    @Override
    public String getInstallId() {
        return "";
    }

    @Override
    public String resetInstallId() {
        return "";
    }

    @Override
    public String previewPayload() {
        return "{}";
    }
}
