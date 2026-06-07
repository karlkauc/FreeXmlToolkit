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
        scheduled = true;
        scheduler.schedule(this::checkForAppUpdate, 2, TimeUnit.SECONDS);
        scheduler.schedule(this::checkForFundsXmlUpdate, 5, TimeUnit.SECONDS);
    }

    private void checkForAppUpdate() {
        try {
            UpdateCheckService svc = ServiceRegistry.get(UpdateCheckService.class);
            if (svc == null || !svc.isUpdateCheckEnabled()) {
                return;
            }
            svc.checkForUpdates().thenAccept(info -> {
                if (info != null && info.updateAvailable()) {
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

    private void checkForFundsXmlUpdate() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            var checker = new org.fxt.freexmltoolkit.service.fundsxml.FundsXmlUpdateChecker(
                    props,
                    ServiceRegistry.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService.class),
                    org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache.getInstance());
            checker.runIfDue().ifPresent(release -> Platform.runLater(() -> {
                String body = "A newer FundsXML schema release is available on GitHub:\n\n"
                        + "  • Tag: " + release.tagName() + "\n"
                        + (release.publishedAt() == null ? "" : "  • Published: " + release.publishedAt() + "\n")
                        + "\nOpen the FundsXML activity and click 'Download / Update Content' to install it.";
                org.fxt.freexmltoolkit.util.DialogHelper.showInformation("FundsXML Update Available",
                        "New FundsXML release: " + release.tagName(), body);
            }));
        } catch (Throwable t) {
            logger.warn("FundsXML startup check failed: {}", t.getMessage());
        }
    }

    /** Stops the scheduler. Idempotent. */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
