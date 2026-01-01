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
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonCodeEditor;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.JsonService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Controller for the JSON Editor tab.
 * Provides functionality for editing, validating, and formatting JSON files.
 */
public class JsonController {
    private static final Logger logger = LogManager.getLogger(JsonController.class);

    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    private final JsonService jsonService = new JsonService();
    private final FileChooser fileChooser = new FileChooser();

    private MainController parentController;
    private JsonCodeEditor jsonEditor;
    private File currentFile;
    private File schemaFile;
    private String lastOpenDir = ".";

    // FXML Components
    @FXML private ToolBar mainToolbar;
    @FXML private SplitPane mainSplitPane;
    @FXML private StackPane editorStackPane;
    @FXML private VBox emptyStatePane;
    @FXML private VBox editorContainer;
    @FXML private VBox jsonPathPane;
    @FXML private TextField jsonPathField;
    @FXML private TextArea jsonPathResultArea;
    @FXML private Label queryResultLabel;
    @FXML private Label formatLabel;
    @FXML private MenuButton toolbarRecentFiles;
    @FXML private Button undoBtn;
    @FXML private Button redoBtn;

    /**
     * Sets the parent controller.
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    /**
     * Gets the parent controller.
     */
    public MainController getParentController() {
        return parentController;
    }

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        logger.info("Initializing JsonController");

        // Configure file chooser
        configureFileChooser();

        // Create JSON editor
        jsonEditor = new JsonCodeEditor();

        // Add editor to container (initially hidden)
        editorContainer.getChildren().add(jsonEditor);
        VBox.setVgrow(jsonEditor, javafx.scene.layout.Priority.ALWAYS);

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        // Load recent files
        loadRecentFiles();

        logger.info("JsonController initialized");
    }

    /**
     * Configures the file chooser with JSON file filters.
     */
    private void configureFileChooser() {
        fileChooser.getExtensionFilters().clear();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All JSON Files", "*.json", "*.jsonc", "*.json5"),
                new FileChooser.ExtensionFilter("JSON Files", "*.json"),
                new FileChooser.ExtensionFilter("JSON with Comments", "*.jsonc"),
                new FileChooser.ExtensionFilter("JSON5 Files", "*.json5"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        // Set initial directory
        String lastDir = propertiesService.get("json.lastOpenDir");
        if (lastDir != null && !lastDir.isEmpty()) {
            File dir = new File(lastDir);
            if (dir.exists() && dir.isDirectory()) {
                fileChooser.setInitialDirectory(dir);
                lastOpenDir = lastDir;
            }
        }
    }

    /**
     * Sets up keyboard shortcuts.
     */
    private void setupKeyboardShortcuts() {
        // Will be called after scene is set
        Platform.runLater(() -> {
            if (mainToolbar != null && mainToolbar.getScene() != null) {
                mainToolbar.getScene().getAccelerators().put(
                        new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.N, javafx.scene.input.KeyCombination.CONTROL_DOWN),
                        this::newFile
                );
                mainToolbar.getScene().getAccelerators().put(
                        new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.O, javafx.scene.input.KeyCombination.CONTROL_DOWN),
                        this::openFile
                );
                mainToolbar.getScene().getAccelerators().put(
                        new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.CONTROL_DOWN),
                        this::saveFile
                );
                mainToolbar.getScene().getAccelerators().put(
                        new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F, javafx.scene.input.KeyCombination.CONTROL_DOWN, javafx.scene.input.KeyCombination.ALT_DOWN),
                        this::formatJson
                );
                mainToolbar.getScene().getAccelerators().put(
                        new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F5),
                        this::validateJson
                );
            }
        });
    }

    /**
     * Loads recent files into the menu.
     */
    private void loadRecentFiles() {
        if (toolbarRecentFiles == null) return;

        toolbarRecentFiles.getItems().clear();

        List<File> recentFiles = propertiesService.getRecentJsonFiles();
        if (recentFiles.isEmpty()) {
            MenuItem noRecentItem = new MenuItem("No recent files");
            noRecentItem.setDisable(true);
            toolbarRecentFiles.getItems().add(noRecentItem);
        } else {
            for (File file : recentFiles) {
                MenuItem item = new MenuItem(file.getName());
                item.setOnAction(e -> loadJsonFile(file));
                toolbarRecentFiles.getItems().add(item);
            }
        }
    }

    // ==================== File Operations ====================

    /**
     * Creates a new JSON file.
     */
    @FXML
    public void newFile() {
        logger.debug("Creating new JSON file");

        // Check for unsaved changes
        if (jsonEditor.isDirty()) {
            boolean save = DialogHelper.showConfirmation(
                    "Unsaved Changes",
                    "You have unsaved changes.",
                    "Do you want to save them before creating a new file?",
                    "Save", "Don't Save"
            );
            if (save) {
                saveFile();
            }
        }

        // Set default content
        jsonEditor.setText("{\n  \n}");
        jsonEditor.setDirty(false);
        currentFile = null;
        updateUI();
        showEditor();

        jsonEditor.focusEditor();
    }

    /**
     * Opens a JSON file.
     */
    @FXML
    public void openFile() {
        logger.debug("Opening JSON file");

        fileChooser.setTitle("Open JSON File");
        File file = fileChooser.showOpenDialog(mainToolbar.getScene().getWindow());
        if (file != null) {
            loadJsonFile(file);
        }
    }

    /**
     * Loads a JSON file into the editor.
     */
    public void loadJsonFile(File file) {
        if (file == null || !file.exists()) {
            logger.warn("File does not exist: {}", file);
            return;
        }

        logger.info("Loading JSON file: {}", file.getAbsolutePath());

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            jsonEditor.setText(content);
            jsonEditor.setDocumentPath(file.getAbsolutePath());
            jsonEditor.setDirty(false);
            currentFile = file;

            // Update last open directory
            lastOpenDir = file.getParent();
            propertiesService.set("json.lastOpenDir", lastOpenDir);
            fileChooser.setInitialDirectory(file.getParentFile());

            // Add to recent files
            propertiesService.addRecentJsonFile(file);
            loadRecentFiles();

            updateUI();
            showEditor();

            logger.info("JSON file loaded successfully");
        } catch (IOException e) {
            logger.error("Error loading JSON file: {}", e.getMessage(), e);
            DialogHelper.showError("Error Loading File",
                    "Could not load the file: " + file.getName(),
                    e.getMessage());
        }
    }

    /**
     * Saves the current file.
     */
    @FXML
    public void saveFile() {
        if (currentFile == null) {
            saveAsFile();
            return;
        }

        logger.debug("Saving JSON file: {}", currentFile.getAbsolutePath());
        saveToFile(currentFile);
    }

    /**
     * Saves the current file with a new name.
     */
    @FXML
    public void saveAsFile() {
        logger.debug("Save As JSON file");

        fileChooser.setTitle("Save JSON File");
        if (currentFile != null) {
            fileChooser.setInitialFileName(currentFile.getName());
        } else {
            fileChooser.setInitialFileName("untitled.json");
        }

        File file = fileChooser.showSaveDialog(mainToolbar.getScene().getWindow());
        if (file != null) {
            saveToFile(file);
            currentFile = file;
            updateUI();
        }
    }

    /**
     * Saves content to a file.
     */
    private void saveToFile(File file) {
        try {
            String content = jsonEditor.getText();
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            jsonEditor.setDirty(false);
            propertiesService.addRecentJsonFile(file);
            loadRecentFiles();
            logger.info("JSON file saved successfully: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error saving JSON file: {}", e.getMessage(), e);
            DialogHelper.showError("Error Saving File",
                    "Could not save the file: " + file.getName(),
                    e.getMessage());
        }
    }

    // ==================== Edit Operations ====================

    /**
     * Undoes the last action.
     */
    @FXML
    public void undo() {
        jsonEditor.undo();
    }

    /**
     * Redoes the last undone action.
     */
    @FXML
    public void redo() {
        jsonEditor.redo();
    }

    // ==================== Format Operations ====================

    /**
     * Formats the JSON with indentation.
     */
    @FXML
    public void formatJson() {
        logger.debug("Formatting JSON");

        String content = jsonEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        try {
            String formatted = jsonService.formatJson(content, 2);
            jsonEditor.setText(formatted);
            logger.info("JSON formatted successfully");
        } catch (Exception e) {
            logger.error("Error formatting JSON: {}", e.getMessage(), e);
            DialogHelper.showError("Format Error",
                    "Could not format the JSON content.",
                    e.getMessage());
        }
    }

    /**
     * Minifies the JSON (removes whitespace).
     */
    @FXML
    public void minifyJson() {
        logger.debug("Minifying JSON");

        String content = jsonEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        try {
            String minified = jsonService.formatJson(content, 0);
            jsonEditor.setText(minified);
            logger.info("JSON minified successfully");
        } catch (Exception e) {
            logger.error("Error minifying JSON: {}", e.getMessage(), e);
            DialogHelper.showError("Minify Error",
                    "Could not minify the JSON content.",
                    e.getMessage());
        }
    }

    // ==================== Validation Operations ====================

    /**
     * Validates the current JSON content.
     */
    @FXML
    public void validateJson() {
        logger.debug("Validating JSON");

        String content = jsonEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            DialogHelper.showInformation("Validation", "Empty Content", "No content to validate.");
            return;
        }

        String error = jsonService.validateJson(content);
        if (error == null || error.isEmpty()) {
            DialogHelper.showInformation("Validation Successful", "Valid JSON",
                    "The JSON content is valid.\nDetected format: " + jsonEditor.getDetectedFormat().toUpperCase());
        } else {
            DialogHelper.showWarning("Validation Failed", "Invalid JSON",
                    "The JSON content contains errors:\n" + error);
        }
    }

    /**
     * Loads a JSON Schema file.
     */
    @FXML
    public void loadSchema() {
        logger.debug("Loading JSON Schema");

        FileChooser schemaChooser = new FileChooser();
        schemaChooser.setTitle("Open JSON Schema");
        schemaChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON Schema Files", "*.json", "*.schema.json"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = schemaChooser.showOpenDialog(mainToolbar.getScene().getWindow());
        if (file != null) {
            schemaFile = file;
            logger.info("JSON Schema loaded: {}", file.getAbsolutePath());
            DialogHelper.showInformation("Schema Loaded", "Success",
                    "JSON Schema loaded: " + file.getName());
        }
    }

    /**
     * Clears the current JSON Schema.
     */
    @FXML
    public void clearSchema() {
        schemaFile = null;
        logger.info("JSON Schema cleared");
        DialogHelper.showInformation("Schema Cleared", "Success", "JSON Schema has been cleared.");
    }

    /**
     * Validates the JSON content against the loaded schema.
     */
    @FXML
    public void validateAgainstSchema() {
        logger.debug("Validating against JSON Schema");

        if (schemaFile == null) {
            DialogHelper.showWarning("No Schema", "Schema Required", "Please load a JSON Schema first.");
            return;
        }

        String content = jsonEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            DialogHelper.showInformation("Validation", "Empty Content", "No content to validate.");
            return;
        }

        try {
            String schemaContent = Files.readString(schemaFile.toPath(), StandardCharsets.UTF_8);
            List<String> errors = jsonService.validateAgainstSchema(content, schemaContent);

            if (errors.isEmpty()) {
                DialogHelper.showInformation("Schema Validation Successful", "Valid",
                        "The JSON content is valid against the schema.");
            } else {
                DialogHelper.showWarning("Schema Validation Failed", "Validation Errors",
                        "Validation errors:\n" + String.join("\n", errors));
            }
        } catch (IOException e) {
            logger.error("Error reading schema file: {}", e.getMessage(), e);
            DialogHelper.showError("Schema Error",
                    "Could not read the schema file.",
                    e.getMessage());
        }
    }

    // ==================== JSONPath Operations ====================

    /**
     * Runs a JSONPath query.
     */
    @FXML
    public void runJsonPath() {
        logger.debug("Running JSONPath query");

        // Show JSONPath panel if hidden
        if (!jsonPathPane.isVisible()) {
            jsonPathPane.setVisible(true);
            jsonPathPane.setManaged(true);
        }

        String query = jsonPathField.getText();
        if (query == null || query.trim().isEmpty()) {
            queryResultLabel.setText("Please enter a JSONPath query");
            return;
        }

        String content = jsonEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            queryResultLabel.setText("No JSON content to query");
            return;
        }

        try {
            String result = jsonService.executeJsonPathAsString(content, query);
            jsonPathResultArea.setText(result);
            queryResultLabel.setText("Query executed successfully");
            queryResultLabel.setStyle("-fx-text-fill: #28a745;");
            logger.info("JSONPath query executed successfully");
        } catch (Exception e) {
            logger.error("JSONPath query error: {}", e.getMessage(), e);
            jsonPathResultArea.setText("Error: " + e.getMessage());
            queryResultLabel.setText("Query failed");
            queryResultLabel.setStyle("-fx-text-fill: #dc3545;");
        }
    }

    /**
     * Clears the JSONPath query and result.
     */
    @FXML
    public void clearJsonPath() {
        jsonPathField.clear();
        jsonPathResultArea.clear();
        queryResultLabel.setText("Ready");
        queryResultLabel.setStyle("-fx-text-fill: #6c757d;");
    }

    /**
     * Toggles the visibility of the JSONPath panel.
     */
    @FXML
    public void toggleJsonPath() {
        boolean visible = jsonPathPane.isVisible();
        jsonPathPane.setVisible(!visible);
        jsonPathPane.setManaged(!visible);
        logger.debug("JSONPath panel visibility toggled to: {}", !visible);
    }

    // ==================== Help ====================

    /**
     * Shows help dialog.
     */
    @FXML
    public void showHelp() {
        DialogHelper.showInformation("JSON Editor Help", "Features and Shortcuts",
                "JSON Editor supports:\n\n" +
                "- JSON (standard)\n" +
                "- JSONC (JSON with Comments)\n" +
                "- JSON5 (extended syntax)\n\n" +
                "Keyboard Shortcuts:\n" +
                "- Ctrl+N: New file\n" +
                "- Ctrl+O: Open file\n" +
                "- Ctrl+S: Save file\n" +
                "- Ctrl+Alt+F: Format JSON\n" +
                "- F5: Validate JSON\n" +
                "- Ctrl+Z: Undo\n" +
                "- Ctrl+Y: Redo\n" +
                "- Ctrl++/-: Zoom in/out");
    }

    // ==================== UI Helpers ====================

    /**
     * Shows the editor and hides the empty state.
     */
    private void showEditor() {
        emptyStatePane.setVisible(false);
        emptyStatePane.setManaged(false);
        editorContainer.setVisible(true);
        editorContainer.setManaged(true);
    }

    /**
     * Updates the UI based on current state.
     */
    private void updateUI() {
        // Update format label
        if (formatLabel != null) {
            String format = jsonEditor.getDetectedFormat().toUpperCase();
            formatLabel.setText(format);
        }

        // Update undo/redo buttons
        if (undoBtn != null) {
            undoBtn.setDisable(!jsonEditor.canUndo());
        }
        if (redoBtn != null) {
            redoBtn.setDisable(!jsonEditor.canRedo());
        }
    }

    /**
     * Shuts down the controller and releases resources.
     */
    public void shutdown() {
        logger.info("Shutting down JsonController");
    }
}
