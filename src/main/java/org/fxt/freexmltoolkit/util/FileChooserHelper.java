package org.fxt.freexmltoolkit.util;

import java.io.File;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

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
     */
    static void remember(File file) {
        if (file == null) {
            return;
        }
        File dir = file.isDirectory() ? file : file.getParentFile();
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        PropertiesService props = props();
        if (props != null) {
            props.setLastOpenDirectory(dir.getAbsolutePath());
        }
    }

    /**
     * @return the last remembered directory as an existing directory, or
     *         {@code null} if none is stored or the stored path no longer exists
     */
    private static File lastDirectory() {
        PropertiesService props = props();
        if (props == null) {
            return null;
        }
        String last = props.getLastOpenDirectory();
        if (last == null || last.isBlank()) {
            return null;
        }
        File dir = new File(last);
        return dir.isDirectory() ? dir : null;
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
