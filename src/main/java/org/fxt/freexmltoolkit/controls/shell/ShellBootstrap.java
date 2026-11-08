package org.fxt.freexmltoolkit.controls.shell;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shell.editor.AboutDialog;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.UpdateCheckService;

/**
 * Owns the application's startup background tasks (install-source detection, app-update +
 * plugin startup tasks such as the FundsXML content sync, periodic telemetry flush) and their scheduler.
 * Invoked once at boot by FxtGui; shut down by FxtGui.stop().
 */
public final class ShellBootstrap {

    private static final Logger logger = LogManager.getLogger(ShellBootstrap.class);
    private static final ShellBootstrap INSTANCE = new ShellBootstrap();

    private final ScheduledExecutorService scheduler =
            org.fxt.freexmltoolkit.service.telemetry.ErrorReportingThreadPoolExecutor.scheduled(1, r -> {
                Thread t = new Thread(r, "ShellBootstrap-Scheduler");
                t.setDaemon(true);
                return t;
            }, "bootstrap");
    private volatile boolean scheduled;

    private ShellBootstrap() {
    }

    public static ShellBootstrap getInstance() {
        return INSTANCE;
    }

    /** Schedules the startup update checks (runs once; subsequent calls are no-ops). */
    public synchronized void scheduleStartupTasks() {
        if (scheduled) {
            return;
        }
        if (scheduler.isShutdown()) {
            logger.warn("ShellBootstrap scheduler already shut down; ignoring scheduleStartupTasks()");
            return;
        }
        scheduled = true;
        // Full install-source detection (winget needs a process) for the next app_start event
        scheduler.schedule(() -> org.fxt.freexmltoolkit.service.telemetry.LaunchContext.recordInstallSource(
                ServiceRegistry.get(PropertiesService.class)), 1, TimeUnit.SECONDS);
        // The project of the last session (its listeners switch to the FX thread themselves)
        scheduler.execute(() -> org.fxt.freexmltoolkit.service.project.ProjectService.getInstance().reopenLast());
        scheduler.schedule(this::checkForAppUpdate, 2, TimeUnit.SECONDS);
        // The plugins' startup tasks (e.g. the FundsXML content sync); a failing one does not stop the others
        scheduler.schedule(ShellBootstrap::runPluginStartupTasks, 5, TimeUnit.SECONDS);
        scheduler.schedule(ShellBootstrap::cleanOldLogs, 10, TimeUnit.SECONDS);
        // Anonymous telemetry: first flush after ~60 s, then every 5 minutes. The flush itself
        // runs on the telemetry worker thread, so this scheduler thread is never blocked.
        scheduler.scheduleWithFixedDelay(ShellBootstrap::flushTelemetry,
                TELEMETRY_FIRST_FLUSH_SECONDS, TELEMETRY_FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** Delay of the first periodic telemetry flush. */
    static final long TELEMETRY_FIRST_FLUSH_SECONDS = 60;
    /** Interval of the periodic telemetry flush. */
    static final long TELEMETRY_FLUSH_INTERVAL_SECONDS = 300;

    /** Deletes old log files with the user's limits (Settings → Log Files), when enabled. */
    private static void cleanOldLogs() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            if (props != null && props.isLogCleanupEnabled()) {
                org.fxt.freexmltoolkit.service.LogCleanupService.clean(
                        props.getLogCleanupMaxAgeDays(), props.getLogCleanupMaxSizeMb());
            }
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnexpected(t, "log_cleanup");
        }
    }

    private static void flushTelemetry() {
        try {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.get().flushAsync();
        } catch (Throwable t) {
            // a periodic task that throws is silently cancelled — never let that happen
            logger.debug("Telemetry flush scheduling failed: {}", t.toString());
        }
    }

    private void checkForAppUpdate() {
        try {
            UpdateCheckService svc = ServiceRegistry.get(UpdateCheckService.class);
            if (svc == null || !svc.isUpdateCheckEnabled()) {
                return;
            }
            svc.checkForUpdates().whenComplete((info, err) ->
                    org.fxt.freexmltoolkit.service.telemetry.UsageEvents.updateCheck("startup",
                            org.fxt.freexmltoolkit.controls.shell.editor.UpdateActionRunner.usageResult(info, err),
                            org.fxt.freexmltoolkit.controls.shell.editor.UpdateActionRunner.failureReason(info, err))
            ).thenAccept(info -> {
                if (info != null && info.updateAvailable()) {
                    // Reuses the shared update-notification flow (boot → dialog dependency,
                    // intentional for now; extract an UpdateNotificationService if it grows).
                    Platform.runLater(() -> AboutDialog.showUpdateDialog(info));
                }
            }).exceptionally(ex -> {
                logger.warn("Startup update check failed: {}", ex.getMessage());
                return null;
            });
        } catch (Throwable t) {
            logger.warn("Startup update check error: {}", t.getMessage());
        }
    }

    /** Runs every plugin's startup tasks; a failing one is logged and the others still run. */
    static void runPluginStartupTasks() {
        for (Runnable task : org.fxt.freexmltoolkit.plugin.PluginManager.getInstance().startupTasks()) {
            try {
                task.run();
            } catch (Throwable t) {
                logger.warn("Plugin startup task failed: {}", t.getMessage());
            }
        }
    }

    /** Stops the scheduler. Idempotent. */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
