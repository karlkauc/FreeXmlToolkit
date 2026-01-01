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

package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.controls.shared.JsonSyntaxHighlighter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * JSON Code Editor component using RichTextFX.
 * Provides syntax highlighting for JSON, JSONC, and JSON5.
 */
public class JsonCodeEditor extends VBox {

    private static final Logger logger = LogManager.getLogger(JsonCodeEditor.class);

    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final Label statusLabel;

    // State
    private boolean dirty = false;
    private String documentPath;
    private String detectedFormat = "json";

    // Font size control
    private static final int DEFAULT_FONT_SIZE = 11;
    private static final int MIN_FONT_SIZE = 6;
    private static final int MAX_FONT_SIZE = 72;
    private int fontSize = DEFAULT_FONT_SIZE;

    // Hover tooltip
    private Popup hoverPopup;
    private Label hoverLabel;
    private long lastHoverUpdate = 0;
    private static final long HOVER_DELAY_MS = 300;

    // Drag & Drop callback
    private Consumer<File> onFileDropped;

    /**
     * Creates a new JSON code editor.
     */
    public JsonCodeEditor() {
        super();

        logger.info("Creating JsonCodeEditor...");

        // Create code area
        this.codeArea = new CodeArea();
        this.scrollPane = new VirtualizedScrollPane<>(codeArea);

        // Create status bar
        this.statusLabel = new Label("Ready");

        // Initialize UI
        initializeUI();

        // Setup event handlers
        setupEventHandlers();

        // Setup hover tooltip
        setupHoverTooltip();

        // Setup drag & drop
        setupDragAndDrop();

        logger.info("JsonCodeEditor created successfully");
    }

    /**
     * Initializes the UI layout.
     */
    private void initializeUI() {
        // Load stylesheets
        loadStylesheets();

        // Set up code area styling
        codeArea.setStyle("-fx-font-size: " + fontSize + "pt; -fx-font-family: 'JetBrains Mono', 'Consolas', 'Monaco', monospace;");

        // Setup line numbers
        codeArea.setParagraphGraphicFactory(createLineNumberFactory());

        // Status bar styling
        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-padding: 3 10; -fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");
        statusBar.getChildren().add(statusLabel);

        // Layout
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(scrollPane, statusBar);

        logger.debug("UI initialized");
    }

    /**
     * Creates line number factory.
     */
    private IntFunction<javafx.scene.Node> createLineNumberFactory() {
        return lineIndex -> {
            HBox hbox = new HBox(3);
            hbox.setAlignment(Pos.CENTER_LEFT);

            // Add spacer for alignment
            Region spacer = new Region();
            spacer.setPrefSize(12, 12);
            hbox.getChildren().add(spacer);

            // Add line number
            Label lineNumber = new Label(String.format("%4d", lineIndex + 1));
            lineNumber.setStyle("-fx-font-family: monospace; -fx-text-fill: gray;");
            hbox.getChildren().add(lineNumber);

            return hbox;
        };
    }

    /**
     * Loads CSS stylesheets.
     */
    private void loadStylesheets() {
        try {
            // Load main theme
            var cssResource = getClass().getResource("/css/fxt-theme.css");
            if (cssResource != null) {
                codeArea.getStylesheets().add(cssResource.toExternalForm());
            }

            // Load JSON highlighting CSS
            var jsonCssResource = getClass().getResource("/css/json-editor.css");
            if (jsonCssResource != null) {
                codeArea.getStylesheets().add(jsonCssResource.toExternalForm());
            }

            logger.debug("Stylesheets loaded");
        } catch (Exception e) {
            logger.error("Error loading stylesheets: {}", e.getMessage(), e);
        }
    }

    /**
     * Sets up event handlers.
     */
    private void setupEventHandlers() {
        // Text change listener with debounce for syntax highlighting
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(100))
                .subscribe(ignore -> {
                    String text = codeArea.getText();
                    Platform.runLater(() -> applySyntaxHighlighting(text));
                    dirty = true;
                });

        // Caret position listener for status line
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            updateStatusBar();
        });

        // Keyboard shortcuts
        codeArea.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case PLUS, EQUALS -> increaseFontSize();
                    case MINUS -> decreaseFontSize();
                    case DIGIT0 -> resetFontSize();
                    default -> {}
                }
            }
        });

        logger.debug("Event handlers setup");
    }

    /**
     * Sets up the hover tooltip for showing JSONPath information.
     */
    private void setupHoverTooltip() {
        // Create popup and label
        hoverPopup = new Popup();
        hoverLabel = new Label();
        hoverLabel.setStyle(
                "-fx-background-color: #2d2d2d; " +
                "-fx-text-fill: #e0e0e0; " +
                "-fx-padding: 8 12; " +
                "-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; " +
                "-fx-font-size: 11px; " +
                "-fx-background-radius: 4; " +
                "-fx-border-radius: 4; " +
                "-fx-border-color: #555; " +
                "-fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 2, 2);"
        );
        hoverPopup.getContent().add(hoverLabel);
        hoverPopup.setAutoHide(true);

        // Mouse move handler
        codeArea.setOnMouseMoved(event -> {
            long now = System.currentTimeMillis();
            if (now - lastHoverUpdate < HOVER_DELAY_MS) {
                return;
            }
            lastHoverUpdate = now;

            try {
                // Get character position under mouse
                var hitInfo = codeArea.hit(event.getX(), event.getY());
                int charIndex = hitInfo.getCharacterIndex().orElse(-1);

                if (charIndex >= 0 && charIndex < codeArea.getText().length()) {
                    String text = codeArea.getText();
                    JsonPathCalculator.JsonHoverInfo hoverInfo = JsonPathCalculator.calculatePath(text, charIndex);

                    if (hoverInfo != null && hoverInfo.isValid()) {
                        // Build tooltip text
                        StringBuilder tooltipText = new StringBuilder();
                        tooltipText.append("Path: ").append(hoverInfo.jsonPath());
                        tooltipText.append("\nType: ").append(hoverInfo.valueType());
                        if (hoverInfo.key() != null) {
                            tooltipText.append("\nKey: \"").append(hoverInfo.key()).append("\"");
                        }
                        if (hoverInfo.value() != null && !hoverInfo.value().isEmpty()) {
                            String displayValue = hoverInfo.value();
                            if (displayValue.length() > 50) {
                                displayValue = displayValue.substring(0, 47) + "...";
                            }
                            tooltipText.append("\nValue: ").append(displayValue);
                        }

                        hoverLabel.setText(tooltipText.toString());

                        // Position and show popup
                        Bounds screenBounds = codeArea.localToScreen(codeArea.getBoundsInLocal());
                        if (screenBounds != null) {
                            double x = event.getScreenX() + 15;
                            double y = event.getScreenY() + 15;
                            hoverPopup.show(codeArea, x, y);
                        }
                    } else {
                        hoverPopup.hide();
                    }
                } else {
                    hoverPopup.hide();
                }
            } catch (Exception e) {
                logger.trace("Hover tooltip error: {}", e.getMessage());
                hoverPopup.hide();
            }
        });

        // Hide on mouse exit
        codeArea.setOnMouseExited(event -> hoverPopup.hide());

        logger.debug("Hover tooltip setup");
    }

    /**
     * Sets up drag and drop support for JSON files.
     */
    private void setupDragAndDrop() {
        // Accept drag over
        codeArea.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty() && isJsonFile(files.get(0))) {
                    event.acceptTransferModes(TransferMode.COPY);
                }
            }
            event.consume();
        });

        // Handle drop
        codeArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty()) {
                    File file = files.get(0);
                    if (isJsonFile(file)) {
                        if (onFileDropped != null) {
                            // Use callback if set
                            onFileDropped.accept(file);
                            success = true;
                        } else {
                            // Load file directly
                            try {
                                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                                setText(content);
                                documentPath = file.getAbsolutePath();
                                success = true;
                                logger.info("File dropped and loaded: {}", file.getName());
                            } catch (Exception e) {
                                logger.error("Error loading dropped file: {}", e.getMessage(), e);
                            }
                        }
                    }
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });

        // Visual feedback on drag enter/exit
        codeArea.setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles()) {
                codeArea.setStyle(codeArea.getStyle() + "-fx-border-color: #28a745; -fx-border-width: 2;");
            }
        });

        codeArea.setOnDragExited(event -> {
            updateFontSize(); // Reset style
        });

        logger.debug("Drag and drop setup");
    }

    /**
     * Checks if a file is a JSON file.
     */
    private boolean isJsonFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".json") || name.endsWith(".jsonc") || name.endsWith(".json5");
    }

    /**
     * Sets a callback for when a file is dropped onto the editor.
     */
    public void setOnFileDropped(Consumer<File> callback) {
        this.onFileDropped = callback;
    }

    /**
     * Applies syntax highlighting to the code area.
     */
    private void applySyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        try {
            StyleSpans<Collection<String>> highlighting = JsonSyntaxHighlighter.computeHighlighting(text);
            codeArea.setStyleSpans(0, highlighting);

            // Detect format
            detectedFormat = JsonSyntaxHighlighter.detectFormat(text);

            logger.trace("Syntax highlighting applied, detected format: {}", detectedFormat);
        } catch (Exception e) {
            logger.error("Error applying syntax highlighting: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates the status bar.
     */
    private void updateStatusBar() {
        int line = codeArea.getCurrentParagraph() + 1;
        int column = codeArea.getCaretColumn() + 1;
        int totalLines = codeArea.getParagraphs().size();
        int totalChars = codeArea.getText().length();

        String status = String.format("Line %d, Col %d | Lines: %d | Chars: %d | Format: %s",
                line, column, totalLines, totalChars, detectedFormat.toUpperCase());

        statusLabel.setText(status);
    }

    // ==================== Public API ====================

    /**
     * Sets the text content.
     */
    public void setText(String text) {
        codeArea.replaceText(text != null ? text : "");
        if (text != null && !text.isEmpty()) {
            Platform.runLater(() -> {
                applySyntaxHighlighting(text);
                updateStatusBar();
            });
        }
        dirty = false;
    }

    /**
     * Gets the current text content.
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Gets the code area component.
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * Checks if the editor has unsaved changes.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Sets the dirty flag.
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * Sets the document path.
     */
    public void setDocumentPath(String path) {
        this.documentPath = path;
    }

    /**
     * Gets the document path.
     */
    public String getDocumentPath() {
        return documentPath;
    }

    /**
     * Gets the detected JSON format (json, jsonc, json5).
     */
    public String getDetectedFormat() {
        return detectedFormat;
    }

    /**
     * Refreshes syntax highlighting.
     */
    public void refreshHighlighting() {
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            Platform.runLater(() -> applySyntaxHighlighting(currentText));
        }
    }

    /**
     * Performs undo operation.
     */
    public void undo() {
        codeArea.undo();
    }

    /**
     * Performs redo operation.
     */
    public void redo() {
        codeArea.redo();
    }

    /**
     * Checks if undo is available.
     */
    public boolean canUndo() {
        return codeArea.isUndoAvailable();
    }

    /**
     * Checks if redo is available.
     */
    public boolean canRedo() {
        return codeArea.isRedoAvailable();
    }

    /**
     * Copies selected text to clipboard.
     */
    public void copy() {
        codeArea.copy();
    }

    /**
     * Cuts selected text to clipboard.
     */
    public void cut() {
        codeArea.cut();
    }

    /**
     * Pastes text from clipboard.
     */
    public void paste() {
        codeArea.paste();
    }

    /**
     * Selects all text.
     */
    public void selectAll() {
        codeArea.selectAll();
    }

    /**
     * Gets selected text.
     */
    public String getSelectedText() {
        return codeArea.getSelectedText();
    }

    /**
     * Replaces selected text.
     */
    public void replaceSelection(String text) {
        codeArea.replaceSelection(text);
    }

    /**
     * Increases font size.
     */
    public void increaseFontSize() {
        if (fontSize < MAX_FONT_SIZE) {
            fontSize += 2;
            updateFontSize();
        }
    }

    /**
     * Decreases font size.
     */
    public void decreaseFontSize() {
        if (fontSize > MIN_FONT_SIZE) {
            fontSize -= 2;
            updateFontSize();
        }
    }

    /**
     * Resets font size to default.
     */
    public void resetFontSize() {
        fontSize = DEFAULT_FONT_SIZE;
        updateFontSize();
    }

    private void updateFontSize() {
        codeArea.setStyle("-fx-font-size: " + fontSize + "pt; -fx-font-family: 'JetBrains Mono', 'Consolas', 'Monaco', monospace;");
        logger.debug("Font size changed to: {}", fontSize);
    }

    /**
     * Requests focus for the code area.
     */
    public void focusEditor() {
        Platform.runLater(codeArea::requestFocus);
    }
}
