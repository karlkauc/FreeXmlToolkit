package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.editor.XmlContextMenuManager;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.XmlService;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Context menu manager for XmlCodeEditorV2.
 * Integrates with the existing manager-based architecture.
 *
 * <p>This manager wraps {@link XmlContextMenuManager} and implements
 * all action handlers via the {@link XmlContextMenuManager.XmlContextActions} interface.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>14 menu items including Undo/Redo</li>
 *   <li>Context-sensitive enable/disable</li>
 *   <li>XML-specific operations (format, validate, XPath)</li>
 *   <li>Code folding integration</li>
 * </ul>
 */
public class ContextMenuManagerV2 implements XmlContextMenuManager.XmlContextActions {

    private static final Logger logger = LogManager.getLogger(ContextMenuManagerV2.class);

    private final CodeArea codeArea;
    private final EditorContext editorContext;
    private final FoldingManagerV2 foldingManager;
    private final XmlContextMenuManager contextMenuManager;

    // Undo/Redo menu items (added to existing menu)
    private MenuItem undoMenuItem;
    private MenuItem redoMenuItem;

    /**
     * Creates a new ContextMenuManagerV2.
     *
     * @param editorContext  the editor context
     * @param foldingManager the folding manager for expand/collapse operations
     */
    public ContextMenuManagerV2(EditorContext editorContext, FoldingManagerV2 foldingManager) {
        this.editorContext = editorContext;
        this.codeArea = editorContext.getCodeArea();
        this.foldingManager = foldingManager;

        // Create the context menu manager
        this.contextMenuManager = new XmlContextMenuManager(codeArea);
        this.contextMenuManager.setContextActions(this);

        logger.info("ContextMenuManagerV2 created");
    }

    /**
     * Initializes the context menu with all items including Undo/Redo.
     */
    public void initialize() {
        // Initialize base context menu
        contextMenuManager.initializeContextMenu();

        // Add Undo/Redo items
        addUndoRedoItems();

        // Setup context-sensitive state updates
        setupContextMenuStateListener();

        logger.debug("Context menu initialized with {} items",
                contextMenuManager.getContextMenu().getItems().size());
    }

    /**
     * Adds Undo and Redo menu items to the context menu.
     */
    private void addUndoRedoItems() {
        ContextMenu menu = contextMenuManager.getContextMenu();
        if (menu == null) return;

        // Create Undo item
        undoMenuItem = new MenuItem("Undo (Ctrl+Z)");
        undoMenuItem.setGraphic(createColoredIcon("bi-arrow-counterclockwise", "#6c757d"));
        undoMenuItem.setOnAction(e -> performUndo());

        // Create Redo item
        redoMenuItem = new MenuItem("Redo (Ctrl+Y)");
        redoMenuItem.setGraphic(createColoredIcon("bi-arrow-clockwise", "#6c757d"));
        redoMenuItem.setOnAction(e -> performRedo());

        // Insert after Comment Lines (position 1, before first separator)
        menu.getItems().add(1, undoMenuItem);
        menu.getItems().add(2, redoMenuItem);
        menu.getItems().add(3, new SeparatorMenuItem());

        logger.debug("Undo/Redo menu items added");
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
     * Creates a colored FontIcon for menu items.
     */
    private FontIcon createColoredIcon(String iconLiteral, String color) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(javafx.scene.paint.Color.web(color));
        icon.setIconSize(12);
        return icon;
    }

    /**
     * Sets up context-sensitive menu state updates.
     */
    private void setupContextMenuStateListener() {
        ContextMenu menu = contextMenuManager.getContextMenu();

        // Update menu state when context menu is about to show
        menu.setOnShowing(event -> updateMenuState());
    }

    /**
     * Updates the state of all menu items based on current context.
     */
    private void updateMenuState() {
        boolean hasSelection = !codeArea.getSelectedText().isEmpty();
        boolean hasText = !codeArea.getText().isEmpty();
        boolean isXmlContent = isContentXml();
        boolean canUndo = codeArea.isUndoAvailable();
        boolean canRedo = codeArea.isRedoAvailable();
        boolean hasSchema = editorContext.hasSchema();
        boolean clipboardHasText = Clipboard.getSystemClipboard().hasString();
        boolean foldingEnabled = foldingManager != null && !foldingManager.getFoldableRegions().isEmpty();

        // Update Undo/Redo state
        if (undoMenuItem != null) {
            undoMenuItem.setDisable(!canUndo);
        }
        if (redoMenuItem != null) {
            redoMenuItem.setDisable(!canRedo);
        }

        // Update other menu items
        updateAdditionalMenuItems(hasSelection, hasText, isXmlContent,
                hasSchema, clipboardHasText, foldingEnabled);

        logger.debug("Menu state updated: selection={}, xml={}, undo={}, redo={}",
                hasSelection, isXmlContent, canUndo, canRedo);
    }

    /**
     * Updates additional menu items based on context.
     */
    private void updateAdditionalMenuItems(boolean hasSelection, boolean hasText,
                                           boolean isXmlContent, boolean hasSchema,
                                           boolean clipboardHasText, boolean foldingEnabled) {
        ContextMenu menu = contextMenuManager.getContextMenu();
        if (menu == null) return;

        for (var item : menu.getItems()) {
            if (item instanceof MenuItem menuItem && !(item instanceof SeparatorMenuItem)) {
                String text = menuItem.getText();
                if (text == null) continue;

                // Cut/Copy require selection
                if (text.startsWith("Cut") || (text.startsWith("Copy") && !text.contains("XPath"))) {
                    menuItem.setDisable(!hasSelection);
                }

                // Paste requires clipboard content
                if (text.startsWith("Paste")) {
                    menuItem.setDisable(!clipboardHasText);
                }

                // Copy XPath requires XML content
                if (text.contains("XPath")) {
                    menuItem.setDisable(!isXmlContent || !hasText);
                }

                // Go to Definition requires schema
                if (text.startsWith("Go to Definition")) {
                    menuItem.setDisable(!hasSchema || !isXmlContent);
                }

                // Format/Validate require content
                if (text.startsWith("Format XML") || text.startsWith("Validate XML")) {
                    menuItem.setDisable(!hasText || !isXmlContent);
                }

                // Expand/Collapse require foldable regions
                if (text.startsWith("Expand All") || text.startsWith("Collapse All")) {
                    menuItem.setDisable(!foldingEnabled);
                }
            }
        }
    }

    /**
     * Checks if the current content is XML.
     */
    private boolean isContentXml() {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return false;

        String trimmed = text.trim();
        return trimmed.startsWith("<?xml") ||
                trimmed.startsWith("<") ||
                trimmed.contains("</");
    }

    /**
     * Gets the underlying context menu.
     *
     * @return the context menu
     */
    public ContextMenu getContextMenu() {
        return contextMenuManager.getContextMenu();
    }

    // ==================== XmlContextActions Implementation ====================

    @Override
    public void toggleLineComment() {
        if (codeArea.getSelectedText().isEmpty()) {
            // Comment/uncomment current line
            int paragraphIndex = codeArea.getCurrentParagraph();
            String line = codeArea.getParagraph(paragraphIndex).getText();

            int lineStart = codeArea.getAbsolutePosition(paragraphIndex, 0);
            int lineEnd = lineStart + line.length();

            if (line.trim().startsWith("<!--") && line.trim().endsWith("-->")) {
                // Uncomment
                String uncommented = line.replaceFirst("<!--\\s*", "")
                        .replaceFirst("\\s*-->", "");
                codeArea.replaceText(lineStart, lineEnd, uncommented);
            } else {
                // Comment
                String commented = "<!-- " + line + " -->";
                codeArea.replaceText(lineStart, lineEnd, commented);
            }
        } else {
            // Comment/uncomment selection
            String selectedText = codeArea.getSelectedText();
            if (selectedText.trim().startsWith("<!--") &&
                    selectedText.trim().endsWith("-->")) {
                String uncommented = selectedText.replaceFirst("<!--\\s*", "")
                        .replaceFirst("\\s*-->", "");
                codeArea.replaceSelection(uncommented);
            } else {
                String commented = "<!-- " + selectedText + " -->";
                codeArea.replaceSelection(commented);
            }
        }
        logger.debug("Toggle line comment executed");
    }

    @Override
    public void cutToClipboard() {
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

    @Override
    public void copyToClipboard() {
        String selectedText = codeArea.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(selectedText);
            clipboard.setContent(content);
            logger.debug("Copied to clipboard: {} chars", selectedText.length());
        }
    }

    @Override
    public void pasteFromClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            String clipboardText = clipboard.getString();
            codeArea.replaceSelection(clipboardText);
            logger.debug("Pasted from clipboard: {} chars", clipboardText.length());
        }
    }

    @Override
    public void copyXPathToClipboard() {
        try {
            int caretPos = codeArea.getCaretPosition();
            String text = codeArea.getText();

            XmlContext context = ContextAnalyzer.analyze(text, caretPos);
            String xpath = context.getXPath();

            if (xpath != null && !xpath.isEmpty() && !xpath.equals("/")) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(xpath);
                clipboard.setContent(content);
                logger.debug("XPath copied: {}", xpath);
            } else {
                logger.debug("No XPath context at current position");
            }
        } catch (Exception e) {
            logger.error("Error copying XPath: {}", e.getMessage(), e);
        }
    }

    @Override
    public void goToDefinition() {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();

        if (text == null || text.isEmpty()) return;

        XmlContext context = ContextAnalyzer.analyze(text, caretPos);

        if (editorContext.hasSchema()) {
            String xpath = context.getXPath();
            var xsdData = editorContext.getSchemaProvider().getXsdDocumentationData();

            if (xsdData != null) {
                XsdExtendedElement elementInfo = xsdData.getExtendedXsdElementMap().get(xpath);
                if (elementInfo != null) {
                    showElementInfoDialog(elementInfo);
                } else {
                    logger.debug("No definition found for: {}", xpath);
                }
            }
        } else {
            logger.debug("No schema loaded - go to definition unavailable");
        }
    }

    /**
     * Shows element information dialog.
     */
    private void showElementInfoDialog(XsdExtendedElement elementInfo) {
        StringBuilder info = new StringBuilder();

        if (elementInfo.getElementType() != null) {
            info.append("Type: ").append(elementInfo.getElementType()).append("\n");
        }

        info.append("Mandatory: ").append(elementInfo.isMandatory() ? "Yes" : "No").append("\n");

        if (elementInfo.getChildren() != null && !elementInfo.getChildren().isEmpty()) {
            info.append("\nChild Elements (").append(elementInfo.getChildren().size()).append("):\n");
            for (String child : elementInfo.getChildren()) {
                info.append("  - ").append(child).append("\n");
            }
        }

        logger.debug("Element Info displayed for: {}", elementInfo.getElementName());

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Element Information");
            alert.setHeaderText("Element: " + elementInfo.getElementName());
            alert.setContentText(info.toString());
            alert.showAndWait();
        });
    }

    @Override
    public void selectAllText() {
        codeArea.selectAll();
        logger.debug("Select all executed");
    }

    @Override
    public void openFindReplace() {
        // Publish event to open Find & Replace dialog
        editorContext.getEventBus().publish(
                new org.fxt.freexmltoolkit.controls.v2.editor.core.EditorEvent.FindReplaceRequestedEvent()
        );
        logger.debug("Find & Replace requested");
    }

    @Override
    public void formatXmlContent() {
        String currentText = codeArea.getText();
        if (currentText == null || currentText.isEmpty()) return;

        try {
            String formattedXml = XmlService.prettyFormat(currentText, 4);
            if (formattedXml != null && !formattedXml.equals(currentText)) {
                int caretPos = codeArea.getCaretPosition();
                codeArea.replaceText(formattedXml);
                // Try to restore caret position
                int newCaretPos = Math.min(caretPos, formattedXml.length());
                codeArea.moveTo(newCaretPos);
                logger.debug("XML formatted successfully");
            }
        } catch (Exception e) {
            logger.error("Error formatting XML: {}", e.getMessage(), e);
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Format Error");
                alert.setHeaderText("Unable to format XML");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            });
        }
    }

    @Override
    public void validateXmlContent() {
        // Publish event to trigger validation
        editorContext.getEventBus().publish(
                new org.fxt.freexmltoolkit.controls.v2.editor.core.EditorEvent.ValidationRequestedEvent()
        );
        logger.debug("Validation triggered via context menu");
    }

    @Override
    public void expandAllFolds() {
        if (foldingManager != null) {
            foldingManager.unfoldAll();
            logger.debug("Expand all folds executed");
        }
    }

    @Override
    public void collapseAllFolds() {
        if (foldingManager != null) {
            foldingManager.foldAll();
            logger.debug("Collapse all folds executed");
        }
    }
}
