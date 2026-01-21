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
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonCodeEditor;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonContextMenuManager;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.view.JsonTreeView;
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

    /**
     * Default constructor for JsonController.
     * Creates a new instance with default service dependencies initialized.
     */
    public JsonController() {
        // Default constructor - services are initialized inline
    }

    private static final Logger logger = LogManager.getLogger(JsonController.class);

    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    private final JsonService jsonService = new JsonService();
    private final FileChooser fileChooser = new FileChooser();

    private MainController parentController;
    private JsonCodeEditor jsonEditor;
    private JsonTreeView jsonTreeView;
    private JsonDocument currentDocument;
    private File currentFile;
    private File schemaFile;
    private String lastOpenDir = ".";

    // FXML Components
    @FXML private ToolBar mainToolbar;
    @FXML private SplitPane mainSplitPane;
    @FXML private SplitPane editorSplitPane;
    @FXML private StackPane editorStackPane;
    @FXML private VBox emptyStatePane;
    @FXML private VBox editorContainer;
    @FXML private VBox treeViewContainer;
    @FXML private VBox jsonPathPane;
    @FXML private TextField jsonPathField;
    @FXML private TextArea jsonPathResultArea;
    @FXML private Label queryResultLabel;
    @FXML private Label formatLabel;
    @FXML private MenuButton toolbarRecentFiles;
    @FXML private ToggleButton treeViewToggle;
    @FXML private Button undoBtn;
    @FXML private Button redoBtn;

    /**
     * Sets the parent controller for this JSON controller.
     * The parent controller provides access to application-level functionality.
     *
     * @param parentController the main controller instance to set as parent
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    /**
     * Gets the parent controller for this JSON controller.
     * The parent controller provides access to application-level functionality.
     *
     * @return the main controller instance, or null if not set
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

        // Wire up context menu actions
        jsonEditor.setContextMenuActions(new JsonContextMenuManager.JsonContextActions() {
            @Override
            public void formatJson() {
                JsonController.this.formatJson();
            }

            @Override
            public void minifyJson() {
                JsonController.this.minifyJson();
            }

            @Override
            public void validateJson() {
                JsonController.this.validateJson();
            }
        });

        // Add editor to container (initially hidden)
        editorContainer.getChildren().add(jsonEditor);
        VBox.setVgrow(jsonEditor, javafx.scene.layout.Priority.ALWAYS);

        // Create and setup tree view
        jsonTreeView = new JsonTreeView();
        jsonTreeView.setOnSelectionChanged(this::onTreeNodeSelected);
        treeViewContainer.getChildren().add(jsonTreeView);
        VBox.setVgrow(jsonTreeView, Priority.ALWAYS);

        // Wire up drag and drop callback
        jsonEditor.setOnFileDropped(this::loadJsonFile);

        // Setup text change listener for tree sync
        jsonEditor.textProperty().addListener((obs, oldText, newText) -> {
            if (treeViewToggle.isSelected()) {
                updateTreeViewFromText();
            }
        });

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        // Load recent files
        loadRecentFiles();

        // Show tree view and JSONPath panel by default
        showTreeViewByDefault();
        showJsonPathPanelByDefault();

        logger.info("JsonController initialized");
    }

    /**
     * Shows the tree view panel by default on page load.
     */
    private void showTreeViewByDefault() {
        treeViewToggle.setSelected(true);
        treeViewContainer.setVisible(true);
        treeViewContainer.setManaged(true);
        logger.debug("Tree view panel shown by default");
    }

    /**
     * Shows the JSONPath panel by default on page load.
     */
    private void showJsonPathPanelByDefault() {
        jsonPathPane.setVisible(true);
        jsonPathPane.setManaged(true);
        logger.debug("JSONPath panel shown by default");
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
     * The file content is read using UTF-8 encoding and displayed in the editor.
     * Also updates recent files list and the tree view if visible.
     *
     * @param file the JSON file to load into the editor
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

        // Adjust SplitPane divider when panel is hidden/shown
        if (!visible) {
            // Showing panel - set divider to show JSONPath pane
            mainSplitPane.setDividerPositions(0.75);
        } else {
            // Hiding panel - let editor take full space
            mainSplitPane.setDividerPositions(1.0);
        }

        logger.debug("JSONPath panel visibility toggled to: {}", !visible);
    }

    // ==================== Tree View Operations ====================

    /**
     * Toggles the tree view visibility.
     */
    @FXML
    public void toggleTreeView() {
        boolean showTree = treeViewToggle.isSelected();

        treeViewContainer.setVisible(showTree);
        treeViewContainer.setManaged(showTree);

        if (showTree) {
            // Show tree - set divider to show tree panel
            editorSplitPane.setDividerPositions(0.25);
            updateTreeViewFromText();
        } else {
            // Hide tree - let editor take full space
            editorSplitPane.setDividerPositions(0.0);
        }

        logger.debug("Tree view toggled to: {}", showTree);
    }

    /**
     * Expands all nodes in the tree view.
     */
    @FXML
    public void expandAllNodes() {
        if (jsonTreeView != null) {
            jsonTreeView.expandAll();
            logger.debug("All tree nodes expanded");
        }
    }

    /**
     * Collapses all nodes in the tree view.
     */
    @FXML
    public void collapseAllNodes() {
        if (jsonTreeView != null) {
            jsonTreeView.collapseAll();
            logger.debug("All tree nodes collapsed");
        }
    }

    /**
     * Syncs the tree view with the current cursor position in the editor.
     */
    @FXML
    public void syncTreeWithEditor() {
        if (jsonTreeView == null || currentDocument == null) {
            return;
        }

        // First update the tree from current text
        updateTreeViewFromText();

        // Get current caret position and try to find the node at that position
        int caretPos = jsonEditor.getCaretPosition();
        String text = jsonEditor.getText();

        if (text == null || text.isEmpty()) {
            return;
        }

        // Use JSONPath calculator to find what node we're in
        var hoverInfo = org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonPathCalculator.calculatePath(text, caretPos);
        if (hoverInfo != null && hoverInfo.isValid()) {
            // Try to find the node in the document by path
            String path = hoverInfo.jsonPath();
            logger.debug("Syncing to path: {}", path);

            // Find node by traversing the path
            JsonNode node = findNodeByPath(currentDocument.getRootValue(), path);
            if (node != null) {
                jsonTreeView.selectNode(node);
            }
        }
    }

    /**
     * Updates the tree view from the current text content.
     */
    private void updateTreeViewFromText() {
        String content = jsonEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            currentDocument = null;
            jsonTreeView.setDocument(null);
            return;
        }

        try {
            currentDocument = JsonNodeFactory.parse(content);
            jsonTreeView.setDocument(currentDocument);
        } catch (Exception e) {
            // Invalid JSON, don't update tree
            logger.trace("Cannot parse JSON for tree view: {}", e.getMessage());
        }
    }

    /**
     * Called when a node is selected in the tree view.
     */
    private void onTreeNodeSelected(JsonNode node) {
        if (node == null) {
            return;
        }

        String text = jsonEditor.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        // Find the position of this node in the text by searching for its key or value
        int position = findNodePosition(text, node);

        if (position >= 0) {
            // Move caret to the node's position
            jsonEditor.moveTo(position);
            jsonEditor.requestFocus();
            logger.debug("Selected tree node: {} at position {}", node.getPath(), position);
        }
    }

    /**
     * Finds the position of a node in the JSON text.
     * Uses the node's path to navigate through the text.
     */
    private int findNodePosition(String text, JsonNode node) {
        // Build the path segments
        java.util.List<String> pathSegments = new java.util.ArrayList<>();
        JsonNode current = node;
        while (current != null && current.getParent() != null) {
            if (current.getKey() != null) {
                pathSegments.add(0, current.getKey());
            } else if (current.getParent() instanceof JsonArray) {
                int index = current.getParent().indexOf(current);
                pathSegments.add(0, "[" + index + "]");
            }
            current = current.getParent();
        }

        // Navigate through the text to find the position
        int searchFrom = 0;
        for (String segment : pathSegments) {
            if (segment.startsWith("[")) {
                // Array index - count array elements
                int index = Integer.parseInt(segment.substring(1, segment.length() - 1));
                int arrayPos = findArrayElement(text, searchFrom, index);
                if (arrayPos < 0) {
                    return -1;
                }
                searchFrom = arrayPos;
            } else {
                // Object key - search for "key":
                String searchKey = "\"" + segment + "\"";
                int keyPos = text.indexOf(searchKey, searchFrom);
                if (keyPos < 0) {
                    return -1;
                }
                searchFrom = keyPos;
            }
        }

        return searchFrom;
    }

    /**
     * Finds the position of an array element by index.
     */
    private int findArrayElement(String text, int startFrom, int targetIndex) {
        int depth = 0;
        int currentIndex = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startFrom; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                switch (c) {
                    case '"' -> inString = true;
                    case '[' -> {
                        if (depth == 0) {
                            currentIndex = 0;
                            if (currentIndex == targetIndex) {
                                return i + 1; // Position after '['
                            }
                        }
                        depth++;
                    }
                    case ']' -> depth--;
                    case ',' -> {
                        if (depth == 1) {
                            currentIndex++;
                            if (currentIndex == targetIndex) {
                                // Find the start of the value after comma
                                int pos = i + 1;
                                while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                                    pos++;
                                }
                                return pos;
                            }
                        }
                    }
                    case '{', '}' -> {
                        // Track nested objects within array
                    }
                }
            }
        }

        return -1;
    }

    /**
     * Finds a node by its JSONPath.
     */
    private JsonNode findNodeByPath(JsonNode root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }

        // Path format: $.key1.key2[0].key3
        if (path.equals("$")) {
            return root;
        }

        // Remove leading "$." or "$"
        String relativePath = path.startsWith("$.") ? path.substring(2) :
                              path.startsWith("$") ? path.substring(1) : path;

        if (relativePath.isEmpty()) {
            return root;
        }

        JsonNode current = root;
        String[] segments = relativePath.split("(?=\\[)|\\.");

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }

            if (current == null) {
                return null;
            }

            if (segment.startsWith("[") && segment.endsWith("]")) {
                // Array index
                try {
                    int index = Integer.parseInt(segment.substring(1, segment.length() - 1));
                    if (current instanceof JsonArray array) {
                        var children = array.getChildren();
                        if (index >= 0 && index < children.size()) {
                            current = children.get(index);
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                // Object key
                if (current instanceof JsonObject obj) {
                    JsonNode child = findChildByKey(obj, segment);
                    current = child;
                } else {
                    return null;
                }
            }
        }

        return current;
    }

    /**
     * Finds a child node by its key.
     */
    private JsonNode findChildByKey(JsonNode parent, String key) {
        for (JsonNode child : parent.getChildren()) {
            if (key.equals(child.getKey())) {
                return child;
            }
        }
        return null;
    }

    // ==================== Help ====================

    /**
     * Shows help dialog.
     */
    @FXML
    public void showHelp() {
        var features = java.util.List.of(
                new String[]{"bi-braces", "Multi-Format Support", "Edit JSON, JSONC (with comments), and JSON5 (extended syntax)"},
                new String[]{"bi-diagram-3", "Tree View", "Navigate JSON structure visually with expand/collapse support"},
                new String[]{"bi-signpost-2", "JSONPath Queries", "Extract data using JSONPath expressions ($.store.book[*].author)"},
                new String[]{"bi-file-earmark-check", "Schema Validation", "Validate JSON against JSON Schema (Draft 4-2020-12)"},
                new String[]{"bi-cursor-text", "Hover Information", "See JSONPath, type, and value info when hovering over elements"},
                new String[]{"bi-cloud-arrow-up", "Drag & Drop", "Drop JSON files directly into the editor to open"}
        );

        var shortcuts = java.util.List.of(
                new String[]{"Ctrl+N", "Create new JSON file"},
                new String[]{"Ctrl+O", "Open JSON file"},
                new String[]{"Ctrl+S", "Save current file"},
                new String[]{"Ctrl+Shift+S", "Save as new file"},
                new String[]{"Ctrl+Alt+F", "Format/beautify JSON"},
                new String[]{"F5", "Validate JSON syntax"},
                new String[]{"Ctrl+Z", "Undo last change"},
                new String[]{"Ctrl+Y", "Redo last change"},
                new String[]{"Ctrl++", "Zoom in"},
                new String[]{"Ctrl+-", "Zoom out"},
                new String[]{"Ctrl+0", "Reset zoom"}
        );

        var helpDialog = DialogHelper.createHelpDialog(
                "JSON Editor - Help",
                "JSON Editor",
                "Edit, validate, and query JSON files",
                "bi-braces",
                DialogHelper.HeaderTheme.WARNING,
                features,
                shortcuts
        );

        helpDialog.showAndWait();
    }

    // ==================== UI Helpers ====================

    /**
     * Shows the editor and hides the empty state.
     */
    private void showEditor() {
        emptyStatePane.setVisible(false);
        emptyStatePane.setManaged(false);
        editorSplitPane.setVisible(true);
        editorSplitPane.setManaged(true);
        editorContainer.setVisible(true);
        editorContainer.setManaged(true);

        // Update tree view if toggle is active
        if (treeViewToggle.isSelected()) {
            updateTreeViewFromText();
        }
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
