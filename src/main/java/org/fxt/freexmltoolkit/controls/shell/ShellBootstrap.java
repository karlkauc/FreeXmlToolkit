package org.fxt.freexmltoolkit.controls.shell;

import java.util.concurrent.Executors;
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
 * Owns the application's startup background tasks (app-update + FundsXML-update
 * checks) and their scheduler, decoupled from the (retiring) MainController.
 * Invoked once at boot by FxtGui; shut down by FxtGui.stop().
 */
public final class ShellBootstrap {

    private static final Logger logger = LogManager.getLogger(ShellBootstrap.class);
    private static final ShellBootstrap INSTANCE = new ShellBootstrap();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "ShellBootstrap-Scheduler");
        t.setDaemon(true);
        return t;
    });
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
        scheduler.schedule(this::checkForAppUpdate, 2, TimeUnit.SECONDS);
        scheduler.schedule(this::fundsXmlStartupSync, 5, TimeUnit.SECONDS);
    }

    private void checkForAppUpdate() {
        try {
            UpdateCheckService svc = ServiceRegistry.get(UpdateCheckService.class);
            if (svc == null || !svc.isUpdateCheckEnabled()) {
                return;
            }
            svc.checkForUpdates().thenAccept(info -> {
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

    /**
     * Startup sync for the FundsXML extension: downloads missing content, installs
     * updates in the background, or just re-registers cached content — all silently
     * (log + toast, never a dialog). Runs on the daemon scheduler thread, where the
     * blocking GitHub call inside {@code determineAction()} is acceptable.
     */
    private void fundsXmlStartupSync() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            var service = ServiceRegistry.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService.class);
            var checker = new org.fxt.freexmltoolkit.service.fundsxml.FundsXmlUpdateChecker(
                    props, service, org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache.getInstance());
            var sync = new org.fxt.freexmltoolkit.service.fundsxml.FundsXmlStartupSync(
                    props::loadProperties, service, checker);
            dispatchFundsXmlAction(sync.determineAction(),
                    org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlDownloadCoordinator.getInstance());
        } catch (Throwable t) {
            logger.warn("FundsXML startup sync failed: {}", t.getMessage());
        }
    }

    /** Maps a startup-sync decision onto coordinator work. Package-private for tests. */
    static void dispatchFundsXmlAction(
            org.fxt.freexmltoolkit.service.fundsxml.FundsXmlStartupSync.Action action,
            org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlDownloadCoordinator coordinator) {
        switch (action) {
            case DOWNLOAD_INITIAL, DOWNLOAD_UPDATE -> coordinator.startBackgroundDownload("startup");
            case REGISTER_ONLY -> coordinator.runRegisterOnly();
            case NONE -> {
            }
        }
    }

    /** Stops the scheduler. Idempotent. */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
