package org.fxt.freexmltoolkit.controls.editor;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;

import java.io.File;

/**
 * Manages file operations including monitoring, saving, and external change detection.
 * This class handles file I/O operations and notifies about external file changes.
 */
public class FileOperationsManager {

    private static final Logger logger = LogManager.getLogger(FileOperationsManager.class);

    private static final int FILE_MONITOR_INTERVAL_SECONDS = 2;

    private final CodeArea codeArea;

    // File monitoring
    private File currentFile;
    private long lastModifiedTime = -1;
    private Timeline fileMonitorTimer;
    private boolean isFileMonitoringEnabled = true;
    private boolean ignoreNextChange = false; // Flag to ignore changes we made ourselves

    // Callbacks
    private FileOperationHandler fileOperationHandler;

    /**
     * Interface for handling file operations that require external services.
     */
    public interface FileOperationHandler {
        /**
         * Save the file using the application's save mechanism.
         *
         * @return true if save was successful, false otherwise
         */
        boolean saveFile();

        /**
         * Show save as dialog using the application's mechanism.
         */
        void saveAsFile();
    }

    /**
     * Constructor for FileOperationsManager.
     *
     * @param codeArea The CodeArea to manage file operations for
     */
    public FileOperationsManager(CodeArea codeArea) {
        this.codeArea = codeArea;
        initializeFileMonitoring();
    }

    /**
     * Sets the file operation handler for save operations.
     *
     * @param handler The file operation handler
     */
    public void setFileOperationHandler(FileOperationHandler handler) {
        this.fileOperationHandler = handler;
    }

    /**
     * Initializes the file monitoring system to detect external changes.
     */
    private void initializeFileMonitoring() {
        // Create timer for checking file modifications
        fileMonitorTimer = new Timeline(new KeyFrame(
                Duration.seconds(FILE_MONITOR_INTERVAL_SECONDS),
                event -> checkForExternalChanges()
        ));
        fileMonitorTimer.setCycleCount(Timeline.INDEFINITE);

        logger.debug("File monitoring system initialized");
    }

    /**
     * Sets the current file being monitored and starts monitoring.
     *
     * @param file The file to monitor, or null to stop monitoring
     */
    public void setCurrentFile(File file) {
        stopFileMonitoring();

        this.currentFile = file;
        if (file != null && file.exists()) {
            this.lastModifiedTime = file.lastModified();
            startFileMonitoring();
            logger.debug("Started monitoring file: {}", file.getAbsolutePath());
        } else {
            this.lastModifiedTime = -1;
            logger.debug("File monitoring stopped");
        }
    }

    /**
     * Gets the current file being monitored.
     *
     * @return The current file, or null if no file is being monitored
     */
    public File getCurrentFile() {
        return currentFile;
    }

    /**
     * Starts the file monitoring timer.
     */
    private void startFileMonitoring() {
        if (isFileMonitoringEnabled && fileMonitorTimer != null && currentFile != null) {
            fileMonitorTimer.play();
        }
    }

    /**
     * Stops the file monitoring timer.
     */
    private void stopFileMonitoring() {
        if (fileMonitorTimer != null) {
            fileMonitorTimer.stop();
        }
    }

    /**
     * Checks if the current file has been modified externally.
     */
    private void checkForExternalChanges() {
        if (currentFile == null || !currentFile.exists() || ignoreNextChange) {
            if (ignoreNextChange) {
                ignoreNextChange = false; // Reset the flag
            }
            return;
        }

        long currentModifiedTime = currentFile.lastModified();
        if (currentModifiedTime > lastModifiedTime) {
            // File has been modified externally
            Platform.runLater(() -> showExternalChangeDialog(currentModifiedTime));
        }
    }

    /**
     * Shows a dialog asking the user if they want to reload the externally modified file.
     *
     * @param newModifiedTime The new last modified time of the file
     */
    private void showExternalChangeDialog(long newModifiedTime) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("File Modified Externally");
        alert.setHeaderText("The file has been modified by another program");
        alert.setContentText("The file '" + currentFile.getName() + "' has been changed outside the editor.\n\n" +
                "Do you want to reload the changes from the file system?");

        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.YES) {
                reloadFileFromDisk(newModifiedTime);
            } else {
                // User chose not to reload, update the timestamp to avoid repeated dialogs
                lastModifiedTime = newModifiedTime;
                logger.debug("User chose not to reload external changes, updating timestamp");
            }
        });
    }

    /**
     * Reloads the file content from disk.
     *
     * @param newModifiedTime The new last modified time to set
     */
    private void reloadFileFromDisk(long newModifiedTime) {
        try {
            // Read the new content from the file
            String newContent = java.nio.file.Files.readString(currentFile.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);

            // Set the flag to ignore the next change notification (from our reload)
            ignoreNextChange = true;

            // Update the editor content
            Platform.runLater(() -> {
                codeArea.replaceText(newContent);
                lastModifiedTime = newModifiedTime;
                logger.info("Successfully reloaded file from disk: {}", currentFile.getAbsolutePath());
            });

        } catch (Exception e) {
            logger.error("Error reloading file from disk: {}", e.getMessage(), e);

            Platform.runLater(() -> {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Error Reloading File");
                errorAlert.setHeaderText("Failed to reload file from disk");
                errorAlert.setContentText("An error occurred while reloading the file:\n" + e.getMessage());
                errorAlert.showAndWait();
            });
        }
    }

    /**
     * Handles Ctrl+S keyboard shortcut to save the current file.
     * Saves the content to the current file or triggers Save As dialog if no file is associated.
     */
    public void handleSaveFile() {
        if (fileOperationHandler != null) {
            // Use handler's save functionality
            if (!fileOperationHandler.saveFile()) {
                // If save failed (probably no file associated), try Save As
                fileOperationHandler.saveAsFile();
            }
        } else {
            logger.warn("Cannot save: no file operation handler available");
        }
    }

    /**
     * Requests the handler to show Save As dialog.
     * This is called when Ctrl+Shift+S is pressed.
     */
    public void requestSaveAs() {
        if (fileOperationHandler != null) {
            fileOperationHandler.saveAsFile();
        } else {
            logger.warn("Cannot save as: no file operation handler available");
        }
    }

    /**
     * Enables or disables file monitoring.
     *
     * @param enabled True to enable monitoring, false to disable
     */
    public void setFileMonitoringEnabled(boolean enabled) {
        this.isFileMonitoringEnabled = enabled;
        if (enabled && currentFile != null) {
            startFileMonitoring();
        } else {
            stopFileMonitoring();
        }
        logger.debug("File monitoring enabled: {}", enabled);
    }

    /**
     * Returns whether file monitoring is currently enabled.
     *
     * @return True if monitoring is enabled
     */
    public boolean isFileMonitoringEnabled() {
        return isFileMonitoringEnabled;
    }

    /**
     * Should be called when the user saves the file to update the timestamp
     * and avoid triggering the external change dialog for our own save.
     */
    public void notifyFileSaved() {
        if (currentFile != null && currentFile.exists()) {
            lastModifiedTime = currentFile.lastModified();
            logger.debug("File save notification received, updated timestamp");
        }
    }

    /**
     * Gets the last modified time of the currently monitored file.
     *
     * @return The last modified time, or -1 if no file is being monitored
     */
    public long getLastModifiedTime() {
        return lastModifiedTime;
    }

    /**
     * Checks if the current file exists and is readable.
     *
     * @return true if the file exists and is readable
     */
    public boolean isFileAccessible() {
        return currentFile != null && currentFile.exists() && currentFile.canRead();
    }

    /**
     * Gets the name of the current file.
     *
     * @return The file name, or null if no file is set
     */
    public String getCurrentFileName() {
        return currentFile != null ? currentFile.getName() : null;
    }

    /**
     * Gets the absolute path of the current file.
     *
     * @return The absolute path, or null if no file is set
     */
    public String getCurrentFilePath() {
        return currentFile != null ? currentFile.getAbsolutePath() : null;
    }

    /**
     * Stops all file monitoring and cleanup resources.
     * Should be called when the editor is being disposed.
     */
    public void dispose() {
        stopFileMonitoring();
        if (fileMonitorTimer != null) {
            fileMonitorTimer = null;
        }
        currentFile = null;
        fileOperationHandler = null;
        logger.debug("FileOperationsManager disposed");
    }
}