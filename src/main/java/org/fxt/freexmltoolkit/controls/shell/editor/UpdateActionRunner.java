package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.concurrent.CompletableFuture;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.UpdateCheckService;

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
