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

package org.fxt.freexmltoolkit.controls.unified;

import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonCodeEditor;
import org.fxt.freexmltoolkit.domain.LinkedFileInfo;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.service.JsonService;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Full-featured Unified Editor tab for JSON files.
 * <p>
 * Features:
 * <ul>
 *   <li>Text view with syntax highlighting (JsonCodeEditor)</li>
 *   <li>JSON, JSONC, and JSON5 format support</li>
 *   <li>Pretty printing and validation</li>
 *   <li>JSONPath queries</li>
 * </ul>
 *
 * @since 2.0
 */
public class JsonUnifiedTab extends AbstractUnifiedEditorTab {

    private static final Logger logger = LogManager.getLogger(JsonUnifiedTab.class);

    // UI Components
    private final TabPane viewTabPane;
    private final Tab jsonTab;
    private final Tab treeTab;
    private final JsonCodeEditor textEditor;

    // Services
    private final JsonService jsonService;

    // State
    private String lastSavedContent;
    private File schemaFile;

    /**
     * Creates a new JSON Unified Editor tab.
     *
     * @param sourceFile the file to edit (can be null for new files)
     */
    public JsonUnifiedTab(File sourceFile) {
        super(sourceFile, UnifiedEditorFileType.JSON);

        // Initialize services
        this.jsonService = new JsonService();

        // Create text editor
        this.textEditor = new JsonCodeEditor();
        if (sourceFile != null) {
            textEditor.setDocumentPath(sourceFile.getAbsolutePath());
        }

        // Create view tabs
        this.viewTabPane = new TabPane();
        this.jsonTab = new Tab("JSON");
        this.treeTab = new Tab("Tree");

        initializeContent();

        // Load file content if provided
        if (sourceFile != null && sourceFile.exists()) {
            loadFile();
        } else {
            // New file with template
            String template = "{\n  \n}";
            textEditor.setText(template);
            lastSavedContent = template;
        }
    }

    @Override
    protected void initializeContent() {
        // Setup view tabs
        viewTabPane.setSide(Side.LEFT);
        viewTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // JSON (Text) tab
        FontIcon jsonIcon = new FontIcon("bi-code-slash");
        jsonIcon.setIconSize(16);
        jsonTab.setGraphic(jsonIcon);
        jsonTab.setContent(textEditor);

        // Tree tab (placeholder for now)
        FontIcon treeIcon = new FontIcon("bi-diagram-3");
        treeIcon.setIconSize(16);
        treeTab.setGraphic(treeIcon);
        treeTab.setContent(new Label("JSON Tree View (coming soon)"));

        viewTabPane.getTabs().addAll(jsonTab, treeTab);

        // Tab switch listener to sync content
        treeTab.setOnSelectionChanged(e -> {
            if (treeTab.isSelected()) {
                refreshTreeView();
            }
        });

        // Set content to viewTabPane
        setContent(viewTabPane);

        // Setup change listener for dirty tracking
        CodeArea codeArea = textEditor.getCodeArea();
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (lastSavedContent != null && !lastSavedContent.equals(newText)) {
                setDirty(true);
            }
        });

        // Cursor position listener
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCursorInformation());
    }

    /**
     * Refreshes the tree view based on current JSON content.
     */
    private void refreshTreeView() {
        String content = textEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            treeTab.setContent(new Label("No JSON content to display"));
            return;
        }

        try {
            // Parse JSON to validate it first
            jsonService.parseAuto(content);

            // TODO: Implement tree view in Phase 4
            VBox placeholder = new VBox(10);
            placeholder.setStyle("-fx-padding: 20; -fx-alignment: center;");
            Label infoLabel = new Label("JSON Tree View coming in Phase 4");
            infoLabel.setStyle("-fx-text-fill: #666;");
            placeholder.getChildren().add(infoLabel);

            treeTab.setContent(placeholder);
        } catch (Exception e) {
            treeTab.setContent(new Label("Invalid JSON: " + e.getMessage()));
        }
    }

    /**
     * Validates the JSON content.
     * @return list of validation error strings, empty if valid
     */
    public List<String> validateJson() {
        String content = getEditorContent();
        String error = jsonService.validateJson(content);
        if (error == null) {
            return Collections.emptyList();
        }
        return List.of(error);
    }

    /**
     * Updates cursor information.
     */
    private void updateCursorInformation() {
        // Cursor info is handled by getCaretPosition()
    }

    /**
     * Loads the content from the source file.
     */
    private void loadFile() {
        if (sourceFile == null || !sourceFile.exists()) {
            return;
        }

        try {
            String content = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
            textEditor.setText(content);
            lastSavedContent = content;
            setDirty(false);

            logger.info("Loaded file: {}", sourceFile.getName());
        } catch (IOException e) {
            logger.error("Failed to load file: {}", sourceFile, e);
        }
    }

    // ==================== File Operations ====================

    @Override
    public String getEditorContent() {
        return textEditor.getText();
    }

    @Override
    public void setEditorContent(String content) {
        textEditor.setText(content);
    }

    @Override
    public boolean save() {
        if (sourceFile == null) {
            logger.warn("Cannot save: no source file specified");
            return false;
        }

        try {
            String content = getEditorContent();
            Files.writeString(sourceFile.toPath(), content, StandardCharsets.UTF_8);
            lastSavedContent = content;
            setDirty(false);
            logger.info("Saved file: {}", sourceFile.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save file: {}", sourceFile, e);
            return false;
        }
    }

    @Override
    public boolean saveAs(File file) {
        if (file == null) {
            return false;
        }

        try {
            String content = getEditorContent();
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            lastSavedContent = content;
            setDirty(false);
            updateTitle(file.getName());
            logger.info("Saved file as: {}", file.getName());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save file as: {}", file, e);
            return false;
        }
    }

    @Override
    public void reload() {
        loadFile();
    }

    @Override
    public void discardChanges() {
        if (lastSavedContent != null) {
            textEditor.setText(lastSavedContent);
            setDirty(false);
        }
    }

    @Override
    public String validate() {
        String content = getEditorContent();
        if (content == null || content.trim().isEmpty()) {
            return "Empty document";
        }

        String error = jsonService.validateJson(content);
        return error; // null if valid
    }

    @Override
    public void format() {
        String content = getEditorContent();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        try {
            String formatted = jsonService.formatJson(content, 2);
            if (formatted != null && !formatted.equals(content)) {
                textEditor.setText(formatted);
            }
        } catch (Exception e) {
            logger.warn("Failed to format JSON: {}", e.getMessage());
        }
    }

    @Override
    public List<LinkedFileInfo> detectLinkedFiles() {
        // JSON files typically don't have linked files like XML does
        // Could detect $ref references in JSON Schema in the future
        return Collections.emptyList();
    }

    @Override
    public String getCaretPosition() {
        CodeArea codeArea = textEditor.getCodeArea();
        int line = codeArea.getCurrentParagraph() + 1;
        int col = codeArea.getCaretColumn() + 1;
        return String.format("Ln %d, Col %d", line, col);
    }

    @Override
    public void requestEditorFocus() {
        Platform.runLater(() -> textEditor.getCodeArea().requestFocus());
    }

    // ==================== JSON-Specific Methods ====================

    /**
     * Executes a JSONPath query on the current content.
     *
     * @param jsonPath the JSONPath expression
     * @return the result as a formatted string
     */
    public String executeJsonPath(String jsonPath) {
        String content = getEditorContent();
        if (content == null || content.trim().isEmpty()) {
            return "No JSON content";
        }
        return jsonService.executeJsonPathAsString(content, jsonPath);
    }

    /**
     * Validates the JSON against a JSON Schema.
     *
     * @param schemaJson the JSON Schema as a string
     * @return list of validation errors, empty if valid
     */
    public List<String> validateAgainstSchema(String schemaJson) {
        String content = getEditorContent();
        return jsonService.validateAgainstSchema(content, schemaJson);
    }

    /**
     * Sets the JSON Schema file for validation.
     *
     * @param file the schema file
     */
    public void setSchemaFile(File file) {
        this.schemaFile = file;
    }

    /**
     * Gets the JSON Schema file.
     *
     * @return the schema file, or null
     */
    public File getSchemaFile() {
        return schemaFile;
    }

    /**
     * Gets the detected JSON format (json, jsonc, json5).
     *
     * @return the detected format
     */
    public String getDetectedFormat() {
        return textEditor.getDetectedFormat();
    }

    // ==================== Accessors ====================

    /**
     * Gets the underlying text editor.
     */
    public JsonCodeEditor getTextEditor() {
        return textEditor;
    }

    /**
     * Gets the code area for direct access.
     */
    public CodeArea getCodeArea() {
        return textEditor.getCodeArea();
    }

    @Override
    public CodeArea getPrimaryCodeArea() {
        return textEditor.getCodeArea();
    }

    /**
     * JSON files don't support XPath.
     * They support JSONPath instead.
     */
    @Override
    public boolean supportsXPath() {
        return false;
    }

    /**
     * JSON files don't have XML version.
     */
    @Override
    public String getXmlVersion() {
        return null;
    }

    @Override
    public String getDocumentStats() {
        String content = getEditorContent();
        if (content == null || content.isEmpty()) {
            return "Empty document";
        }
        int lines = (int) content.lines().count();
        int chars = content.length();
        String format = getDetectedFormat().toUpperCase();
        return String.format("Lines: %d, Characters: %d, Format: %s", lines, chars, format);
    }
}
