package org.fxt.freexmltoolkit.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
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

    /**
     * Shows {@code file} selected in the system file manager (Explorer, Finder, or a freedesktop
     * file manager over D-Bus). Where that fails, the containing folder is opened instead.
     */
    public static void reveal(File file, Consumer<Exception> onFailure) {
        run(() -> {
            List<String> command = revealCommand(System.getProperty("os.name", ""), file);
            if (command != null && runsSuccessfully(command)) {
                return;
            }
            File folder = file.getAbsoluteFile().getParentFile();
            if (folder == null || !Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new UnsupportedOperationException("No file manager to show " + file);
            }
            Desktop.getDesktop().open(folder);
        }, onFailure);
    }

    /**
     * @return the OS command that shows {@code file} selected in the file manager, or {@code null}
     *         if the OS has none
     */
    static List<String> revealCommand(String osName, File file) {
        String os = osName.toLowerCase(Locale.ROOT);
        String path = file.getAbsolutePath();
        if (os.contains("win")) {
            return List.of("explorer.exe", "/select," + path);
        }
        if (os.contains("mac")) {
            return List.of("open", "-R", path);
        }
        if (os.contains("linux") || os.contains("bsd")) {
            return List.of("dbus-send", "--session", "--print-reply", "--dest=org.freedesktop.FileManager1",
                    "--type=method_call", "/org/freedesktop/FileManager1",
                    "org.freedesktop.FileManager1.ShowItems",
                    "array:string:" + file.getAbsoluteFile().toURI(), "string:");
        }
        return null;
    }

    private static boolean runsSuccessfully(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroy();
                return false;
            }
            // explorer.exe /select reports exit code 1 even when it worked.
            return process.exitValue() == 0 || command.getFirst().equals("explorer.exe");
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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
