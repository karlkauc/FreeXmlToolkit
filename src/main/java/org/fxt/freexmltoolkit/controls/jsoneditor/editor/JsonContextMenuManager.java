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

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Manages the context menu for JSON code editing.
 * Provides JSON-specific operations like Copy JSONPath, Format, Minify, Validate.
 */
public class JsonContextMenuManager {

    private static final Logger logger = LogManager.getLogger(JsonContextMenuManager.class);

    private final CodeArea codeArea;
    private ContextMenu contextMenu;
    private JsonContextActions contextActions;

    // Menu items that need state updates
    private MenuItem undoMenuItem;
    private MenuItem redoMenuItem;
    private MenuItem cutMenuItem;
    private MenuItem copyMenuItem;
    private MenuItem pasteMenuItem;
    private MenuItem copyJsonPathMenuItem;

    /**
     * Interface for handling JSON context menu actions.
     */
    public interface JsonContextActions {
        /**
         * Formats the JSON content with proper indentation.
         */
        void formatJson();

        /**
         * Minifies the JSON content by removing whitespace.
         */
        void minifyJson();

        /**
         * Validates the JSON content and reports any errors.
         */
        void validateJson();
    }

    /**
     * Creates a new JsonContextMenuManager.
     *
     * @param codeArea The CodeArea to attach the context menu to
     */
    public JsonContextMenuManager(CodeArea codeArea) {
        this.codeArea = codeArea;
    }

    /**
     * Sets the context actions handler.
     *
     * @param contextActions The action handler
     */
    public void setContextActions(JsonContextActions contextActions) {
        this.contextActions = contextActions;
    }

    /**
     * Initializes and sets up the context menu.
     */
    public void initializeContextMenu() {
        contextMenu = new ContextMenu();

        // === Undo/Redo Section ===
        undoMenuItem = new MenuItem("Undo (Ctrl+Z)");
        undoMenuItem.setGraphic(createColoredIcon("bi-arrow-counterclockwise", "#6c757d"));
        undoMenuItem.setOnAction(e -> performUndo());

        redoMenuItem = new MenuItem("Redo (Ctrl+Y)");
        redoMenuItem.setGraphic(createColoredIcon("bi-arrow-clockwise", "#6c757d"));
        redoMenuItem.setOnAction(e -> performRedo());

        // === Edit Section ===
        SeparatorMenuItem separator1 = new SeparatorMenuItem();

        cutMenuItem = new MenuItem("Cut (Ctrl+X)");
        cutMenuItem.setGraphic(createColoredIcon("bi-scissors", "#dc3545"));
        cutMenuItem.setOnAction(e -> cutToClipboard());

        copyMenuItem = new MenuItem("Copy (Ctrl+C)");
        copyMenuItem.setGraphic(createColoredIcon("bi-files", "#007bff"));
        copyMenuItem.setOnAction(e -> copyToClipboard());

        pasteMenuItem = new MenuItem("Paste (Ctrl+V)");
        pasteMenuItem.setGraphic(createColoredIcon("bi-clipboard", "#28a745"));
        pasteMenuItem.setOnAction(e -> pasteFromClipboard());

        // === JSON-Specific Section ===
        SeparatorMenuItem separator2 = new SeparatorMenuItem();

        copyJsonPathMenuItem = new MenuItem("Copy JSONPath");
        copyJsonPathMenuItem.setGraphic(createColoredIcon("bi-signpost-2", "#ffc107"));
        copyJsonPathMenuItem.setOnAction(e -> copyJsonPathToClipboard());

        // === Selection Section ===
        SeparatorMenuItem separator3 = new SeparatorMenuItem();

        MenuItem selectAllMenuItem = new MenuItem("Select All (Ctrl+A)");
        selectAllMenuItem.setGraphic(createColoredIcon("bi-border-all", "#6f42c1"));
        selectAllMenuItem.setOnAction(e -> codeArea.selectAll());

        // === Format Section ===
        SeparatorMenuItem separator4 = new SeparatorMenuItem();

        MenuItem formatMenuItem = new MenuItem("Format JSON (Ctrl+Alt+F)");
        formatMenuItem.setGraphic(createColoredIcon("bi-text-indent-left", "#17a2b8"));
        formatMenuItem.setOnAction(e -> {
            if (contextActions != null) {
                contextActions.formatJson();
            }
        });

        MenuItem minifyMenuItem = new MenuItem("Minify JSON");
        minifyMenuItem.setGraphic(createColoredIcon("bi-text-left", "#6c757d"));
        minifyMenuItem.setOnAction(e -> {
            if (contextActions != null) {
                contextActions.minifyJson();
            }
        });

        // === Validation Section ===
        SeparatorMenuItem separator5 = new SeparatorMenuItem();

        MenuItem validateMenuItem = new MenuItem("Validate JSON (F5)");
        validateMenuItem.setGraphic(createColoredIcon("bi-check-circle", "#28a745"));
        validateMenuItem.setOnAction(e -> {
            if (contextActions != null) {
                contextActions.validateJson();
            }
        });

        // Add all items to context menu
        contextMenu.getItems().addAll(
                undoMenuItem,
                redoMenuItem,
                separator1,
                cutMenuItem,
                copyMenuItem,
                pasteMenuItem,
                separator2,
                copyJsonPathMenuItem,
                separator3,
                selectAllMenuItem,
                separator4,
                formatMenuItem,
                minifyMenuItem,
                separator5,
                validateMenuItem
        );

        // Set up state updates when menu is shown
        contextMenu.setOnShowing(e -> updateMenuState());

        // Attach to code area
        codeArea.setContextMenu(contextMenu);

        logger.debug("JSON context menu initialized with {} items", contextMenu.getItems().size());
    }

    /**
     * Creates a colored FontIcon for menu items.
     */
    private FontIcon createColoredIcon(String iconLiteral, String color) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(javafx.scene.paint.Color.web(color));
        icon.setIconSize(14);
        return icon;
    }

    /**
     * Updates menu item states based on current context.
     */
    private void updateMenuState() {
        boolean hasSelection = !codeArea.getSelectedText().isEmpty();
        boolean hasText = !codeArea.getText().isEmpty();
        boolean canUndo = codeArea.isUndoAvailable();
        boolean canRedo = codeArea.isRedoAvailable();
        boolean clipboardHasText = Clipboard.getSystemClipboard().hasString();

        undoMenuItem.setDisable(!canUndo);
        redoMenuItem.setDisable(!canRedo);
        cutMenuItem.setDisable(!hasSelection);
        copyMenuItem.setDisable(!hasSelection);
        pasteMenuItem.setDisable(!clipboardHasText);
        copyJsonPathMenuItem.setDisable(!hasText);

        logger.trace("Menu state updated: selection={}, text={}, undo={}, redo={}",
                hasSelection, hasText, canUndo, canRedo);
    }

    /**
     * Performs undo operation.
     */
    private void performUndo() {
        if (codeArea.isUndoAvailable()) {
            codeArea.undo();
            logger.debug("Undo performed");
        }
    }

    /**
     * Performs redo operation.
     */
    private void performRedo() {
        if (codeArea.isRedoAvailable()) {
            codeArea.redo();
            logger.debug("Redo performed");
        }
    }

    /**
     * Cuts selected text to clipboard.
     */
    private void cutToClipboard() {
        String selectedText = codeArea.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(selectedText);
            clipboard.setContent(content);
            codeArea.replaceSelection("");
            logger.debug("Cut to clipboard: {} chars", selectedText.length());
        }
    }

    /**
     * Copies selected text to clipboard.
     */
    private void copyToClipboard() {
        String selectedText = codeArea.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(selectedText);
            clipboard.setContent(content);
            logger.debug("Copied to clipboard: {} chars", selectedText.length());
        }
    }

    /**
     * Pastes text from clipboard.
     */
    private void pasteFromClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            String clipboardText = clipboard.getString();
            codeArea.replaceSelection(clipboardText);
            logger.debug("Pasted from clipboard: {} chars", clipboardText.length());
        }
    }

    /**
     * Copies the JSONPath at current cursor position to clipboard.
     */
    private void copyJsonPathToClipboard() {
        try {
            int caretPos = codeArea.getCaretPosition();
            String text = codeArea.getText();

            JsonPathCalculator.JsonHoverInfo hoverInfo = JsonPathCalculator.calculatePath(text, caretPos);

            if (hoverInfo != null && hoverInfo.isValid()) {
                String jsonPath = hoverInfo.jsonPath();
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(jsonPath);
                clipboard.setContent(content);
                logger.debug("JSONPath copied: {}", jsonPath);
            } else {
                logger.debug("No JSONPath context at current position");
            }
        } catch (Exception e) {
            logger.error("Error copying JSONPath: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the context menu.
     *
     * @return The context menu
     */
    public ContextMenu getContextMenu() {
        return contextMenu;
    }
}
