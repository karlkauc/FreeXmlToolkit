/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.FileExplorer;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.DragDropService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.XmlService;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class XsltController {

    private static final Logger logger = LogManager.getLogger(XsltController.class);
    private static final int PANE_SIZE = 500;
    private static final int FILE_WATCH_INTERVAL_SECONDS = 3;
    private static final int FILE_EXPLORER_REFRESH_INTERVAL_SECONDS = 5;

    private final XmlService xmlService = ServiceRegistry.get(XmlService.class);
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    private MainController parentController;
    private File xmlFile, xsltFile;
    private WebEngine webEngine;
    private final CodeArea codeArea = new CodeArea();
    private VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    // File change monitoring
    private ScheduledExecutorService fileWatchExecutor;
    private FileTime lastXmlModified;
    private FileTime lastXsltModified;

    @FXML
    private FileExplorer xmlFileExplorer, xsltFileExplorer;
    @FXML
    private Button openInDefaultWebBrowser, openInDefaultTextEditor;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private WebView webView;
    @FXML
    private StackPane textView;
    @FXML
    private TabPane outputMethodSwitch;
    @FXML
    private Tab tabWeb, tabText;
    @FXML
    private TextArea performanceArea;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void initialize() {
        xmlFileExplorer.setAllowedFileExtensions(List.of("xml"));
        xsltFileExplorer.setAllowedFileExtensions(List.of("xslt", "xsl"));

        if (System.getenv("debug") != null) {
            logger.debug("Debug mode enabled for XsltController");
            // Debug mode - test functionality can be added here if needed
        }

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
        textView.getChildren().add(virtualizedScrollPane);

        progressBar.setDisable(true);
        progressBar.setVisible(false);

        if (performanceArea != null) {
            performanceArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        }

        webEngine = webView.getEngine();
        webEngine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                logger.debug("Loading Web Content successfully: {}", webEngine.getLocation());
            }
        });

        setupKeyboardShortcuts();
        setupDragAndDrop();
        setupAutoRefresh();
        setupFileWatching();
        applySmallIconsSetting();
    }

    /**
     * Set up drag and drop functionality for the XSLT Viewer controller.
     * Accepts XML files for input and XSLT files for stylesheet.
     */
    private void setupDragAndDrop() {
        if (outputMethodSwitch == null) {
            logger.warn("Cannot setup drag and drop: outputMethodSwitch is null");
            return;
        }

        DragDropService.setupDragDrop(outputMethodSwitch, DragDropService.XML_AND_XSLT, files -> {
            logger.info("Files dropped on XSLT Viewer: {} file(s)", files.size());

            for (File file : files) {
                DragDropService.FileType fileType = DragDropService.getFileType(file);
                if (fileType == DragDropService.FileType.XSLT) {
                    // Set as XSLT file
                    xsltFile = file;
                    if (xsltFileExplorer != null) {
                        xsltFileExplorer.setSelectedFile(file.toPath());
                    }
                    xmlService.setCurrentXsltFile(file);
                    logger.debug("Dropped XSLT file set: {}", file.getName());
                } else if (fileType == DragDropService.FileType.XML) {
                    // Set as XML file
                    xmlFile = file;
                    if (xmlFileExplorer != null) {
                        xmlFileExplorer.setSelectedFile(file.toPath());
                    }
                    xmlService.setCurrentXmlFile(file);
                    logger.debug("Dropped XML file set: {}", file.getName());
                }
            }

            // Auto-transform if both files are now set
            if (xmlFile != null && xsltFile != null) {
                checkFiles();
            }
        });
        logger.debug("Drag and drop initialized for XSLT Viewer controller");
    }

    /**
     * Sets up keyboard shortcuts for XSLT Controller actions.
     */
    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (webView == null || webView.getScene() == null) {
                // Scene not ready yet, try again later
                Platform.runLater(this::setupKeyboardShortcuts);
                return;
            }

            Scene scene = webView.getScene();

            // Ctrl+R - Reload/Transform
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),
                    this::checkFiles
            );

            // Ctrl+B - Open in Browser
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN),
                    () -> {
                        if (openInDefaultWebBrowser != null && !openInDefaultWebBrowser.isDisable()) {
                            openInDefaultWebBrowser.fire();
                        }
                    }
            );

            // Ctrl+Shift+E - Open in Text Editor
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                    () -> {
                        if (openInDefaultTextEditor != null && !openInDefaultTextEditor.isDisable()) {
                            openInDefaultTextEditor.fire();
                        }
                    }
            );

            logger.debug("XSLT Controller keyboard shortcuts registered");
        });
    }

    @FXML
    private void checkFiles() {
        if (xsltFileExplorer.getSelectedFile() != null) {
            xsltFile = xsltFileExplorer.getSelectedFile().toFile();
            xmlService.setCurrentXsltFile(xsltFile);
        }

        if (xmlFileExplorer.getSelectedFile() != null) {
            xmlFile = xmlFileExplorer.getSelectedFile().toFile();
            xmlService.setCurrentXmlFile(xmlFile);

            // Auto-load linked XSLT stylesheet if user hasn't manually loaded one
            if (xsltFile == null) {
                tryLoadLinkedStylesheet();
            }
        }

        // Update modification times to track changes
        updateFileModificationTimes();

        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()
                && xmlService.getCurrentXsltFile() != null && xmlService.getCurrentXsltFile().exists()) {
            try {
                // Capture performance metrics
                long startTime = System.currentTimeMillis();
                long xmlFileSize = xmlFile.length();
                long xsltFileSize = xsltFile.length();

                String output = xmlService.performXsltTransformation();

                long transformationTime = System.currentTimeMillis() - startTime;
                int outputSize = output != null ? output.length() : 0;

                progressBar.setProgress(0.1);
                renderHTML(output);
                progressBar.setProgress(0.6);
                renderXML(output);
                progressBar.setProgress(0.8);
                renderText(output);
                progressBar.setProgress(1);

                // Update performance statistics
                updatePerformanceStatistics(xmlFileSize, xsltFileSize, outputSize, transformationTime);

                String outputMethodRaw = xmlService.getXsltOutputMethod();
                String outputMethod = (outputMethodRaw != null) ? outputMethodRaw.toLowerCase().trim() : "text";
                switch (outputMethod) {
                    case "html", "xhtml" -> outputMethodSwitch.getSelectionModel().select(tabWeb);
                    default -> outputMethodSwitch.getSelectionModel().select(tabText);
                }
            } catch (Exception exception) {
                // Log the error for developer analysis (with full stacktrace)
                logger.error("XSLT Transformation failed: {}", exception.getMessage(), exception);

                // Update performance area with error information
                updatePerformanceWithError(exception);

                // NEW: Show an alert dialog for the user
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Transformation Error");
                alert.setHeaderText("An error occurred during the XSLT transformation.");
                // Show a comprehensible error message
                alert.setContentText(exception.getMessage());

                // Optional: Add the complete stacktrace in an expandable area
                // This is very useful for technically savvy users.
                TextArea textArea = new TextArea(exception.toString());
                textArea.setEditable(false);
                textArea.setWrapText(true);
                alert.getDialogPane().setExpandableContent(textArea);

                alert.showAndWait();
            }
            progressBar.setVisible(false);
        }
    }

    /**
     * Updates the performance statistics display with transformation metrics.
     */
    private void updatePerformanceStatistics(long xmlFileSize, long xsltFileSize, int outputSize, long transformationTime) {
        if (performanceArea == null) {
            return;
        }

        StringBuilder stats = new StringBuilder();
        stats.append("═══════════════════════════════════════════════════════════\n");
        stats.append("              XSLT TRANSFORMATION STATISTICS\n");
        stats.append("═══════════════════════════════════════════════════════════\n\n");

        // Timestamp
        stats.append("Executed at: ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        // Status
        stats.append("┌─────────────────────────────────────────────────────────┐\n");
        stats.append("│ STATUS: SUCCESS                                          │\n");
        stats.append("└─────────────────────────────────────────────────────────┘\n\n");

        // Timing
        stats.append("─── Timing ───────────────────────────────────────────────\n");
        stats.append(String.format("  Total Transformation Time:  %,d ms\n", transformationTime));
        if (transformationTime < 100) {
            stats.append("  Performance Rating:         ★★★★★ Excellent\n");
        } else if (transformationTime < 500) {
            stats.append("  Performance Rating:         ★★★★☆ Good\n");
        } else if (transformationTime < 1000) {
            stats.append("  Performance Rating:         ★★★☆☆ Average\n");
        } else if (transformationTime < 3000) {
            stats.append("  Performance Rating:         ★★☆☆☆ Slow\n");
        } else {
            stats.append("  Performance Rating:         ★☆☆☆☆ Very Slow\n");
        }
        stats.append("\n");

        // File sizes
        stats.append("─── Input Files ──────────────────────────────────────────\n");
        stats.append(String.format("  XML Source:    %s (%s)\n",
                xmlFile != null ? xmlFile.getName() : "N/A",
                formatBytes(xmlFileSize)));
        stats.append(String.format("  XSLT File:     %s (%s)\n",
                xsltFile != null ? xsltFile.getName() : "N/A",
                formatBytes(xsltFileSize)));
        stats.append("\n");

        // Output
        stats.append("─── Output ───────────────────────────────────────────────\n");
        stats.append(String.format("  Output Size:   %s\n", formatBytes(outputSize)));
        stats.append(String.format("  Output Method: %s\n",
                xmlService.getXsltOutputMethod() != null ? xmlService.getXsltOutputMethod() : "text"));

        // Size ratio
        if (xmlFileSize > 0) {
            double ratio = (double) outputSize / xmlFileSize;
            stats.append(String.format("  Size Ratio:    %.2fx %s\n", ratio,
                    ratio > 1 ? "(expansion)" : "(compression)"));
        }
        stats.append("\n");

        // Throughput
        stats.append("─── Throughput ───────────────────────────────────────────\n");
        if (transformationTime > 0) {
            double throughputKBps = (xmlFileSize / 1024.0) / (transformationTime / 1000.0);
            stats.append(String.format("  Processing Speed: %.2f KB/s\n", throughputKBps));
        }
        stats.append("\n");

        // Summary
        stats.append("═══════════════════════════════════════════════════════════\n");
        stats.append(String.format("  Processed %s → %s in %d ms\n",
                formatBytes(xmlFileSize), formatBytes(outputSize), transformationTime));
        stats.append("═══════════════════════════════════════════════════════════\n");

        Platform.runLater(() -> performanceArea.setText(stats.toString()));
    }

    /**
     * Updates the performance area with error information when transformation fails.
     */
    private void updatePerformanceWithError(Exception exception) {
        if (performanceArea == null) {
            return;
        }

        StringBuilder stats = new StringBuilder();
        stats.append("═══════════════════════════════════════════════════════════\n");
        stats.append("              XSLT TRANSFORMATION STATISTICS\n");
        stats.append("═══════════════════════════════════════════════════════════\n\n");

        // Timestamp
        stats.append("Executed at: ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        // Status
        stats.append("┌─────────────────────────────────────────────────────────┐\n");
        stats.append("│ STATUS: FAILED                                           │\n");
        stats.append("└─────────────────────────────────────────────────────────┘\n\n");

        // Error details
        stats.append("─── Error Details ────────────────────────────────────────\n");
        stats.append("  Error Type:    ").append(exception.getClass().getSimpleName()).append("\n");
        stats.append("  Message:       ").append(exception.getMessage()).append("\n\n");

        // Input files info
        stats.append("─── Input Files ──────────────────────────────────────────\n");
        if (xmlFile != null) {
            stats.append(String.format("  XML Source:    %s (%s)\n",
                    xmlFile.getName(), formatBytes(xmlFile.length())));
        }
        if (xsltFile != null) {
            stats.append(String.format("  XSLT File:     %s (%s)\n",
                    xsltFile.getName(), formatBytes(xsltFile.length())));
        }

        stats.append("\n═══════════════════════════════════════════════════════════\n");

        Platform.runLater(() -> performanceArea.setText(stats.toString()));
    }

    /**
     * Formats byte size to human-readable format.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Attempts to load a linked XSLT stylesheet from the current XML file's xml-stylesheet processing instruction.
     * Only loads if user hasn't already manually selected a stylesheet.
     * Shows a warning if stylesheet is referenced but cannot be loaded.
     */
    private void tryLoadLinkedStylesheet() {
        try {
            var linkedStylesheet = xmlService.getLinkedStylesheetFromCurrentXMLFile();

            if (linkedStylesheet.isPresent()) {
                String stylesheetPath = linkedStylesheet.get();
                logger.debug("Found linked stylesheet: {}", stylesheetPath);

                File stylesheetFile = new File(stylesheetPath);

                // Check if file exists and is valid
                if (stylesheetFile.exists()) {
                    // Basic validation - check if it's an XSL file
                    String fileName = stylesheetFile.getName().toLowerCase();
                    if (fileName.endsWith(".xsl") || fileName.endsWith(".xslt")) {
                        xsltFile = stylesheetFile;
                        xmlService.setCurrentXsltFile(xsltFile);

                        logger.info("Auto-loaded linked XSLT stylesheet: {}", stylesheetPath);
                    } else {
                        logger.warn("Linked file is not an XSLT stylesheet (doesn't end with .xsl or .xslt): {}", stylesheetPath);
                        showStylesheetWarning("Invalid Stylesheet", "The linked file is not an XSLT stylesheet: " + stylesheetPath);
                    }
                } else {
                    logger.warn("Linked stylesheet file not found: {}", stylesheetPath);
                    showStylesheetWarning("Stylesheet Not Found", "The linked XSLT stylesheet could not be found:\n" + stylesheetPath);
                }
            } else {
                logger.debug("No linked stylesheet found in XML file");
            }
        } catch (Exception e) {
            logger.error("Error while trying to load linked stylesheet: {}", e.getMessage(), e);
            showStylesheetWarning("Error Loading Stylesheet", "An error occurred while trying to load the linked stylesheet:\n" + e.getMessage());
        }
    }

    /**
     * Shows a warning dialog about stylesheet loading issues.
     */
    private void showStylesheetWarning(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText("Linked XSLT Stylesheet Issue");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void renderXML(String output) {
        renderText(output);
        Platform.runLater(() -> codeArea.setStyleSpans(0, XmlCodeEditor.computeHighlighting(output)));
    }

    private void renderText(String output) {
        codeArea.clear();
        codeArea.replaceText(0, 0, output);
    }

    private void renderHTML(String output) {
        File outputDir = new File("output");
        String outputFileName = outputDir.getAbsolutePath() + File.separator + "output.html";

        try {
            Files.createDirectories(outputDir.toPath());

            Files.copy(
                    Objects.requireNonNull(getClass().getResourceAsStream("/scss/prism.css")),
                    Paths.get(outputDir.getAbsolutePath(), "prism.css"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(
                    Objects.requireNonNull(getClass().getResourceAsStream("/xsdDocumentation/assets/freexmltoolkit-docs.css")),
                    Paths.get(outputDir.getAbsolutePath(), "freeXmlToolkit.css"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(
                    Objects.requireNonNull(getClass().getResourceAsStream("/css/fonts/Roboto-Regular.ttf")),
                    Paths.get(outputDir.getAbsolutePath(), "Roboto-Regular.ttf"),
                    StandardCopyOption.REPLACE_EXISTING);

            File newFile = Paths.get(outputFileName).toFile();
            Files.writeString(newFile.toPath(), output);
            logger.debug("Rendering HTML file: {}", newFile.getAbsolutePath());

            openInDefaultWebBrowser.setOnAction(event -> {
                try {
                    Desktop.getDesktop().open(newFile);
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            });
            openInDefaultWebBrowser.setDisable(false);

            webEngine.load(newFile.toURI().toString());
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }


    // ==================== AUTO-REFRESH AND FILE WATCHING ====================

    /**
     * Sets up automatic refresh for both file explorers.
     * This ensures that new or modified files appear automatically.
     */
    private void setupAutoRefresh() {
        if (xmlFileExplorer != null) {
            xmlFileExplorer.enableAutoRefresh(FILE_EXPLORER_REFRESH_INTERVAL_SECONDS);
            logger.debug("Auto-refresh enabled for XML file explorer");
        }
        if (xsltFileExplorer != null) {
            xsltFileExplorer.enableAutoRefresh(FILE_EXPLORER_REFRESH_INTERVAL_SECONDS);
            logger.debug("Auto-refresh enabled for XSLT file explorer");
        }
    }

    /**
     * Sets up file watching to detect changes in the currently loaded XML and XSLT files.
     * When a file changes, the transformation is automatically re-executed.
     */
    private void setupFileWatching() {
        fileWatchExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable);
            t.setDaemon(true);
            t.setName("XsltController-FileWatch");
            return t;
        });

        fileWatchExecutor.scheduleAtFixedRate(
                this::checkForFileChanges,
                FILE_WATCH_INTERVAL_SECONDS,
                FILE_WATCH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        logger.info("File watching enabled with interval: {} seconds", FILE_WATCH_INTERVAL_SECONDS);
    }

    /**
     * Checks if the currently loaded XML or XSLT files have been modified.
     * If changes are detected, the transformation is automatically re-executed.
     */
    private void checkForFileChanges() {
        try {
            boolean xmlChanged = checkFileChanged(xmlFile, lastXmlModified);
            boolean xsltChanged = checkFileChanged(xsltFile, lastXsltModified);

            if (xmlChanged || xsltChanged) {
                // Update last modified times
                if (xmlFile != null && xmlFile.exists()) {
                    lastXmlModified = Files.getLastModifiedTime(xmlFile.toPath());
                }
                if (xsltFile != null && xsltFile.exists()) {
                    lastXsltModified = Files.getLastModifiedTime(xsltFile.toPath());
                }

                // Log what changed
                if (xmlChanged && xsltChanged) {
                    logger.info("Both XML and XSLT files changed, re-executing transformation");
                } else if (xmlChanged) {
                    logger.info("XML file changed, re-executing transformation");
                } else {
                    logger.info("XSLT stylesheet changed, re-compiling and re-executing transformation");
                }

                // Re-execute transformation on JavaFX thread
                Platform.runLater(this::checkFiles);
            }
        } catch (Exception e) {
            logger.warn("Error checking for file changes: {}", e.getMessage());
        }
    }

    /**
     * Checks if a file has been modified since the last known modification time.
     *
     * @param file the file to check
     * @param lastModified the last known modification time
     * @return true if the file has been modified, false otherwise
     */
    private boolean checkFileChanged(File file, FileTime lastModified) {
        if (file == null || !file.exists()) {
            return false;
        }

        try {
            FileTime currentModified = Files.getLastModifiedTime(file.toPath());
            if (lastModified == null) {
                return false; // First time checking, don't consider it a change
            }
            return currentModified.compareTo(lastModified) > 0;
        } catch (IOException e) {
            logger.warn("Could not check file modification time: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Updates the stored file modification times.
     * Should be called after files are loaded or transformation is executed.
     */
    private void updateFileModificationTimes() {
        try {
            if (xmlFile != null && xmlFile.exists()) {
                lastXmlModified = Files.getLastModifiedTime(xmlFile.toPath());
            }
            if (xsltFile != null && xsltFile.exists()) {
                lastXsltModified = Files.getLastModifiedTime(xsltFile.toPath());
            }
        } catch (IOException e) {
            logger.warn("Could not update file modification times: {}", e.getMessage());
        }
    }

    /**
     * Cleans up resources when the controller is no longer needed.
     * This should be called when the view is being destroyed.
     */
    public void shutdown() {
        // Shutdown file watch executor
        if (fileWatchExecutor != null && !fileWatchExecutor.isShutdown()) {
            fileWatchExecutor.shutdown();
            try {
                if (!fileWatchExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    fileWatchExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                fileWatchExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            fileWatchExecutor = null;
        }

        // Dispose file explorers
        if (xmlFileExplorer != null) {
            xmlFileExplorer.dispose();
        }
        if (xsltFileExplorer != null) {
            xsltFileExplorer.dispose();
        }

        logger.info("XSLT Controller shutdown completed");
    }

    /**
     * Shows help dialog.
     */
    @FXML
    private void showHelp() {
        Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
        helpDialog.setTitle("XSLT - Help");
        helpDialog.setHeaderText("How to use the XSLT Transformation Tool");
        helpDialog.setContentText("""
                Use this tool to work with your documents.

                FEATURES:
                - Auto-refresh: File lists update automatically every 5 seconds
                - Auto-recompile: Stylesheets are recompiled when changed
                - Drag & Drop: Drop XML/XSLT files to load them

                Press F1 to show this help.
                """);
        helpDialog.showAndWait();
    }

    /**
     * Applies the small icons setting to XSLT toolbar buttons.
     * Small icons = 14px with GRAPHIC_ONLY display mode
     * Normal icons = 20px with TOP display mode
     */
    private void applySmallIconsSetting() {
        boolean useSmallIcons = propertiesService.isUseSmallIcons();
        logger.debug("Applying small icons setting to XSLT toolbar: {}", useSmallIcons);

        // Determine display mode and icon size
        javafx.scene.control.ContentDisplay displayMode = useSmallIcons
                ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
                : javafx.scene.control.ContentDisplay.TOP;

        // Icon sizes: small = 14px, normal = 20px
        int iconSize = useSmallIcons ? 14 : 20;

        // Button style: compact padding for small icons
        String buttonStyle = useSmallIcons
                ? "-fx-padding: 4px;"
                : "";

        // Apply to all toolbar buttons
        applyButtonSettings(openInDefaultWebBrowser, displayMode, iconSize, buttonStyle);
        applyButtonSettings(openInDefaultTextEditor, displayMode, iconSize, buttonStyle);

        logger.info("Small icons setting applied to XSLT toolbar (size: {}px)", iconSize);
    }

    /**
     * Helper method to apply display mode, icon size, and style to a button.
     */
    private void applyButtonSettings(javafx.scene.control.ButtonBase button,
                                     javafx.scene.control.ContentDisplay displayMode,
                                     int iconSize,
                                     String style) {
        if (button == null) return;

        // Set content display mode
        button.setContentDisplay(displayMode);

        // Apply compact style
        button.setStyle(style);

        // Update icon size if the button has a FontIcon graphic
        if (button.getGraphic() instanceof org.kordamp.ikonli.javafx.FontIcon fontIcon) {
            fontIcon.setIconSize(iconSize);
        }
    }

    /**
     * Public method to refresh toolbar icons.
     * Can be called from Settings or MainController when icon size preference changes.
     */
    public void refreshToolbarIcons() {
        applySmallIconsSetting();
    }

}
