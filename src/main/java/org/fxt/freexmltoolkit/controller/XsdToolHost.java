package org.fxt.freexmltoolkit.controller;

import java.io.File;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;

/**
 * Interface for hosting XSD sub-tab controllers (Documentation, Flatten, etc.).
 * Implemented by both XsdController (standalone) and adapters for the Unified Editor.
 * Provides common operations that sub-tabs need from their parent.
 */
public interface XsdToolHost {

    /**
     * Executes a background task with progress tracking.
     */
    <T> void executeBackgroundTask(Task<T> task);

    /**
     * Updates the background task timer display.
     */
    void updateBackgroundTaskTimer(String time);

    /**
     * Opens a file chooser to select an XSD file.
     */
    File openXsdFileChooser();

    /**
     * Opens a save dialog.
     */
    File showSaveDialog(String title, String desc, String extension);

    /**
     * Shows an alert dialog.
     */
    void showAlert(Alert.AlertType type, String title, String content);

    /**
     * Opens a folder in the system file explorer.
     */
    void openFolderInExplorer(File folder);
}
