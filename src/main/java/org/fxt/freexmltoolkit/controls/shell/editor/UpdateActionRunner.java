package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.concurrent.CompletableFuture;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.UpdateCheckService;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * UI-free application-update helper for the shell, reusing
 * {@link UpdateCheckService}. The check itself is asynchronous and network-bound;
 * {@link #describe(UpdateInfo)} turns its result into a user-facing message.
 */
public final class UpdateActionRunner {

    private UpdateActionRunner() {
    }

    /** Starts an asynchronous check for application updates. */
    public static CompletableFuture<UpdateInfo> check() {
        return ServiceRegistry.get(UpdateCheckService.class).checkForUpdates();
    }

    /**
     * Classifies a finished check for usage statistics.
     *
     * @return {@code UsageEvents.UPDATE_ERROR} for a failed check (exception, no result, or no
     *         latest version), else {@code UPDATE_AVAILABLE} / {@code UPDATE_UP_TO_DATE}
     */
    public static String usageResult(UpdateInfo info, Throwable error) {
        if (error != null || info == null || info.latestVersion() == null) {
            return UsageEvents.UPDATE_ERROR;
        }
        return info.updateAvailable() ? UsageEvents.UPDATE_AVAILABLE : UsageEvents.UPDATE_UP_TO_DATE;
    }

    /** @return a human-readable message describing the update status. */
    public static String describe(UpdateInfo info) {
        if (info == null) {
            return "Update check failed.";
        }
        if (info.updateAvailable()) {
            return "Update available: " + info.latestVersion() + " (you have " + info.currentVersion() + ")";
        }
        return "You are up to date (" + info.currentVersion() + ").";
    }
}
