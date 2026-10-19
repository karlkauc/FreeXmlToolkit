package org.fxt.freexmltoolkit.util;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.function.Consumer;

import javafx.application.Platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FxtGui;

/**
 * Opens files and URLs with the operating system, off the calling thread. {@link Desktop} initializes
 * AWT on first use and can block for a long time (or hang on some Linux desktops); called from a UI
 * handler that froze the whole window. Failures are reported back on the FX thread.
 */
public final class DesktopActions {

    private static final Logger logger = LogManager.getLogger(DesktopActions.class);

    private DesktopActions() {
    }

    /** Opens {@code file} (a document or a folder) with its system application. */
    public static void open(File file, Consumer<Exception> onFailure) {
        run(() -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new UnsupportedOperationException("No desktop application to open " + file);
            }
            Desktop.getDesktop().open(file);
        }, onFailure);
    }

    /** Opens {@code uri} in the system browser. */
    public static void browse(URI uri, Consumer<Exception> onFailure) {
        run(() -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("No desktop browser to open " + uri);
            }
            Desktop.getDesktop().browse(uri);
        }, onFailure);
    }

    @FunctionalInterface
    private interface DesktopCall {
        void run() throws Exception;
    }

    private static void run(DesktopCall call, Consumer<Exception> onFailure) {
        FxtGui.executorService.submit(() -> {
            try {
                call.run();
            } catch (Exception e) {
                logger.warn("Desktop action failed: {}", e.getMessage());
                if (onFailure != null) {
                    Platform.runLater(() -> onFailure.accept(e));
                }
            }
        });
    }
}
