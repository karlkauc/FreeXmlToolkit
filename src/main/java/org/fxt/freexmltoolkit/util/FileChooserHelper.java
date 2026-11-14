package org.fxt.freexmltoolkit.util;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Drop-in wrappers around {@link FileChooser} / {@link DirectoryChooser} that
 * remember the last directory the user navigated to and reuse it as the initial
 * directory for the next dialog.
 *
 * <p>The remembered directory is persisted app-wide via
 * {@link PropertiesService#getLastOpenDirectory()} /
 * {@link PropertiesService#setLastOpenDirectory(String)} (property key
 * {@code last.open.directory} in {@code FreeXmlToolkit.properties}), so it
 * survives application restarts and is shared across open, save and folder
 * dialogs.
 *
 * <p>Usage: replace {@code chooser.showOpenDialog(owner)} with
 * {@code FileChooserHelper.showOpenDialog(chooser, owner)} (and analogously for
 * the other dialog flavours). The helper only ever sets the initial
 * <em>directory</em>; it never touches {@code initialFileName} and never
 * overrides an {@code initialDirectory} that the caller has already set.
 */
public final class FileChooserHelper {

    private static final Logger LOGGER = LogManager.getLogger(FileChooserHelper.class);

    private FileChooserHelper() {
        // utility class
    }

    /**
     * Shows an open-file dialog, pre-selecting the last used directory and
     * remembering the chosen file's directory.
     *
     * @param chooser the configured chooser (title, extension filters, …)
     * @param owner   the owner window (may be {@code null})
     * @return the selected file, or {@code null} if the user cancelled
     */
    public static File showOpenDialog(FileChooser chooser, Window owner) {
        applyInitialDir(chooser);
        File file = chooser.showOpenDialog(owner);
        remember(file);
        return file;
    }

    /**
     * Shows a multi-selection open-file dialog, pre-selecting the last used
     * directory and remembering the directory of the first chosen file.
     *
     * @param chooser the configured chooser
     * @param owner   the owner window (may be {@code null})
     * @return the selected files, or {@code null} if the user cancelled
     */
    public static List<File> showOpenMultipleDialog(FileChooser chooser, Window owner) {
        applyInitialDir(chooser);
        List<File> files = chooser.showOpenMultipleDialog(owner);
        if (files != null && !files.isEmpty()) {
            remember(files.get(0));
        }
        return files;
    }

    /**
     * Shows a save-file dialog, pre-selecting the last used directory and
     * remembering the target file's directory. The caller's
     * {@code initialFileName} (if any) is preserved.
     *
     * @param chooser the configured chooser
     * @param owner   the owner window (may be {@code null})
     * @return the target file, or {@code null} if the user cancelled
     */
    public static File showSaveDialog(FileChooser chooser, Window owner) {
        applyInitialDir(chooser);
        File file = chooser.showSaveDialog(owner);
        remember(file);
        return file;
    }

    /**
     * Shows a directory-selection dialog, pre-selecting the last used directory
     * and remembering the chosen directory.
     *
     * @param chooser the configured chooser
     * @param owner   the owner window (may be {@code null})
     * @return the selected directory, or {@code null} if the user cancelled
     */
    public static File showDialog(DirectoryChooser chooser, Window owner) {
        applyInitialDir(chooser);
        File dir = chooser.showDialog(owner);
        remember(dir);
        return dir;
    }

    // ---------------------------------------------------------------------
    // Package-private core logic (unit-testable without showing a real dialog)
    // ---------------------------------------------------------------------

    /**
     * Sets the chooser's initial directory to the last remembered directory,
     * unless the caller has already set one or no valid directory is stored.
     */
    static void applyInitialDir(FileChooser chooser) {
        if (chooser == null || chooser.getInitialDirectory() != null) {
            return;
        }
        File dir = lastDirectory();
        if (dir != null) {
            chooser.setInitialDirectory(dir);
        }
    }

    /**
     * Sets the directory chooser's initial directory to the last remembered
     * directory, unless the caller has already set one or none is stored.
     */
    static void applyInitialDir(DirectoryChooser chooser) {
        if (chooser == null || chooser.getInitialDirectory() != null) {
            return;
        }
        File dir = lastDirectory();
        if (dir != null) {
            chooser.setInitialDirectory(dir);
        }
    }

    /**
     * Persists the directory of the given file (or the directory itself) as the
     * last used directory. No-op for {@code null} or non-existent locations.
     * The file system is asked at most the probe time limit (300 ms) on the calling (FX)
     * thread; a slower answer is awaited in the background.
     */
    static void remember(File file) {
        if (file == null) {
            return;
        }
        CompletableFuture<File> dir = CompletableFuture.supplyAsync(() -> {
            File d = directoryProbe.test(file) ? file : file.getParentFile();
            return d != null && directoryProbe.test(d) ? d : null;
        }, PROBES);
        File known = await(dir);
        if (known == null && dir.isDone() && !dir.isCompletedExceptionally()) {
            known = dir.getNow(null); // finished just after the wait
        }
        if (known != null) {
            store(known);
        } else if (!dir.isDone()) {
            dir.thenAccept(d -> {
                if (d != null) {
                    store(d);
                }
            });
        }
    }

    private static void store(File dir) {
        verified(dir);
        PropertiesService props = props();
        if (props != null) {
            props.setLastOpenDirectory(dir.getAbsolutePath());
        }
    }

    /**
     * @return the last remembered directory as an existing directory, or
     *         {@code null} if none is stored, the stored path no longer exists,
     *         or the file system does not answer within the probe time limit (300 ms)
     *         (cloud folders, network shares: telemetry showed 5 s on the FX
     *         thread before a dialog). A slow check finishes in the background,
     *         so the next dialog gets the directory.
     */
    static File lastDirectory() {
        PropertiesService props = props();
        if (props == null) {
            return null;
        }
        String last = props.getLastOpenDirectory();
        if (last == null || last.isBlank()) {
            return null;
        }
        File dir = new File(last);
        CompletableFuture<File> check = CompletableFuture.supplyAsync(
                () -> directoryProbe.test(dir) ? dir : null, PROBES);
        check.thenAccept(d -> {
            if (d != null) {
                verified(d);
            }
        });
        File known = await(check);
        if (known != null || check.isDone()) {
            return known != null || check.isCompletedExceptionally() ? known : check.getNow(null);
        }
        if (isRecentlyVerified(dir)) {
            return dir; // slow, but found to exist a moment ago
        }
        LOGGER.info("Last directory {} did not answer within {} ms; dialog opens without it",
                dir, probeTimeout.toMillis());
        return null;
    }

    // ---------------------------------------------------------------------
    // Time-limited file system checks
    // ---------------------------------------------------------------------

    /** Checks run on virtual threads, so a hanging one ties up no pool. */
    private static final Executor PROBES = runnable -> Thread.ofVirtual().name("fxt-dir-probe").start(runnable);

    private static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofMillis(300);

    /** How long a directory found to exist is trusted when the file system is slow. */
    private static final Duration VERIFIED_FOR = Duration.ofMinutes(10);

    /** How long the FX thread waits for the file system before a dialog. */
    private static volatile Duration probeTimeout = DEFAULT_PROBE_TIMEOUT;

    /** The check whether a path is a directory. */
    private static volatile Predicate<File> directoryProbe = File::isDirectory;

    /**
     * Replaces the directory check and its time limit (tests); a {@code null} probe restores the
     * defaults and forgets the trusted directory.
     */
    static void setDirectoryProbe(Predicate<File> probe, Duration timeout) {
        directoryProbe = probe != null ? probe : File::isDirectory;
        probeTimeout = timeout != null ? timeout : DEFAULT_PROBE_TIMEOUT;
        if (probe == null) {
            verifiedDir = null;
        }
    }

    private static volatile File verifiedDir;
    private static volatile long verifiedAt;

    private static void verified(File dir) {
        verifiedDir = dir.getAbsoluteFile();
        verifiedAt = System.nanoTime();
    }

    private static boolean isRecentlyVerified(File dir) {
        File known = verifiedDir;
        return known != null && known.equals(dir.getAbsoluteFile())
                && System.nanoTime() - verifiedAt < VERIFIED_FOR.toNanos();
    }

    private static File await(CompletableFuture<File> future) {
        try {
            return future.get(probeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            LOGGER.debug("Directory check failed", e.getCause());
            return null;
        }
    }

    /**
     * Resolves the {@link PropertiesService}, returning {@code null} when it is
     * unavailable (e.g. in isolated tests) so dialogs still work.
     */
    private static PropertiesService props() {
        try {
            return ServiceRegistry.get(PropertiesService.class);
        } catch (Throwable t) {
            LOGGER.debug("PropertiesService unavailable; not remembering last directory", t);
            return null;
        }
    }
}
