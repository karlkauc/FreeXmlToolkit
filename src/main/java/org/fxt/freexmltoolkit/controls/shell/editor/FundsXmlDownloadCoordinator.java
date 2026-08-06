/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;
import javafx.stage.Window;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.ToastNotification;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;

/**
 * The single place that runs FundsXML background downloads. All three triggers
 * (startup sync, enabling the feature in Settings, the panel's Download button)
 * funnel through here so that:
 * <ul>
 *   <li>only one download runs at a time (UI-level guard on top of the service's own),</li>
 *   <li>every interested UI (panel progress bar, welcome page) observes the same
 *       run via {@link Listener}s on the FX thread,</li>
 *   <li>completion feedback is a non-blocking toast — never a modal dialog.</li>
 * </ul>
 *
 * <p>Progress callbacks fire many times per second on the I/O thread; the
 * coordinator throttles the FX-thread hops to stage changes and ≥1% advances.
 */
public final class FundsXmlDownloadCoordinator {

    private static final Logger logger = LogManager.getLogger(FundsXmlDownloadCoordinator.class);

    private static FundsXmlDownloadCoordinator instance;

    /** Observer of one background download run. All callbacks arrive on the FX thread. */
    public interface Listener {
        default void onStarted() {
        }

        /**
         * @param stage    high-level phase (e.g. "Downloading schema 4.2.11")
         * @param message  human-readable status text
         * @param fraction progress in [0,1], or {@code -1} for indeterminate
         */
        default void onProgress(String stage, String message, double fraction) {
        }

        default void onFinished(FundsXmlExtensionService.DownloadResult result) {
        }
    }

    private final FundsXmlExtensionService service;
    private final Executor executor;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Direct construction is for tests; production code uses {@link #getInstance()}. */
    public FundsXmlDownloadCoordinator(FundsXmlExtensionService service, Executor executor) {
        this.service = service;
        this.executor = executor;
    }

    public static synchronized FundsXmlDownloadCoordinator getInstance() {
        if (instance == null) {
            instance = new FundsXmlDownloadCoordinator(
                    FundsXmlExtensionService.getInstance(),
                    org.fxt.freexmltoolkit.FxtGui.executorService);
        }
        return instance;
    }

    /** Replaces the singleton — for tests only. Pass {@code null} to reset. */
    public static synchronized void setInstanceForTesting(FundsXmlDownloadCoordinator replacement) {
        instance = replacement;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /**
     * Starts a background download-or-update run.
     *
     * @param reason short cause tag for logging ("startup", "settings-enable", "panel")
     * @return {@code false} if a run is already in flight (no new run started)
     */
    public boolean startBackgroundDownload(String reason) {
        if (!running.compareAndSet(false, true)) {
            logger.info("FundsXML download requested ({}) but one is already running", reason);
            return false;
        }
        logger.info("Starting FundsXML background download ({})", reason);
        executor.execute(() -> {
            FundsXmlExtensionService.DownloadResult result;
            try {
                fxRun(() -> listeners.forEach(Listener::onStarted));
                result = service.downloadOrUpdate(progressCallback());
            } catch (Throwable t) {
                logger.warn("FundsXML background download failed: {}", t.getMessage());
                result = FundsXmlExtensionService.DownloadResult.builder()
                        .error("Download failed: " + t.getMessage())
                        .build();
            } finally {
                running.set(false);
            }
            FundsXmlExtensionService.DownloadResult finalResult = result;
            fxRun(() -> {
                listeners.forEach(l -> l.onFinished(finalResult));
                showCompletionToast(finalResult);
            });
        });
        return true;
    }

    /**
     * Re-registers cached content (favorites, snippets, templates) in the background —
     * no network. Used at startup when the cache is already current, and when the
     * feature is re-enabled with a populated cache.
     */
    public void runRegisterOnly() {
        executor.execute(() -> {
            try {
                service.reRegisterFromCache();
            } catch (Throwable t) {
                logger.warn("FundsXML re-registration failed: {}", t.getMessage());
            }
        });
    }

    /**
     * Removes stale seeded entries (e.g. pom.xml sample templates from earlier
     * versions) in the background. Used at startup when the feature is disabled,
     * where full re-registration must not run.
     */
    public void runCleanupOnly() {
        executor.execute(() -> {
            try {
                service.cleanupStaleEntries();
            } catch (Throwable t) {
                logger.warn("FundsXML stale-entry cleanup failed: {}", t.getMessage());
            }
        });
    }

    /** Builds the I/O-thread progress callback with FX-hop throttling. */
    private org.fxt.freexmltoolkit.service.fundsxml.DownloadProgressCallback progressCallback() {
        return new org.fxt.freexmltoolkit.service.fundsxml.DownloadProgressCallback() {
            private String lastStage;
            private int lastPercent = -2;

            @Override
            public void onProgress(String stage, long bytesDownloaded, long totalBytes, String message) {
                double fraction = (totalBytes > 0 && bytesDownloaded >= 0)
                        ? Math.min(1.0, (double) bytesDownloaded / totalBytes)
                        : -1;
                int percent = fraction < 0 ? -1 : (int) (fraction * 100);
                boolean stageChanged = !stage.equals(lastStage);
                if (!stageChanged && percent == lastPercent) {
                    return;
                }
                lastStage = stage;
                lastPercent = percent;
                fxRun(() -> listeners.forEach(l -> l.onProgress(stage, message, fraction)));
            }
        };
    }

    private void showCompletionToast(FundsXmlExtensionService.DownloadResult result) {
        Window window = Window.getWindows().stream()
                .filter(Window::isShowing)
                .findFirst()
                .orElse(null);
        if (window == null) {
            return; // headless or shutting down — the log already has the outcome
        }
        try {
            if (result.isSuccess()) {
                String version = result.schemaVersion() == null ? "" : " (schema " + result.schemaVersion() + ")";
                ToastNotification.success(window, "FundsXML content updated" + version);
            } else {
                ToastNotification.info(window, "FundsXML update failed — see log for details");
            }
        } catch (Exception e) {
            logger.debug("Could not show FundsXML toast: {}", e.getMessage());
        }
    }

    /** Runs on the FX thread; falls back to direct execution when the toolkit is absent (unit tests). */
    private static void fxRun(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
            return;
        }
        try {
            Platform.runLater(r);
        } catch (IllegalStateException toolkitNotRunning) {
            r.run();
        }
    }
}
