package org.fxt.freexmltoolkit.controls.intellisense;

import javafx.scene.control.Alert;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.fxmisc.richtext.CodeArea;

/**
 * Integration class for Quick Actions Menu with XML editors.
 * Handles context detection and menu triggering.
 */
public class QuickActionsIntegration {

    private final QuickActionsMenu quickActionsMenu;
    private final CodeArea codeArea;
    private final Object xsdIntegration; // Using Object to avoid dependency issues

    public QuickActionsIntegration(CodeArea codeArea, Object xsdIntegration) {
        this.codeArea = codeArea;
        this.xsdIntegration = xsdIntegration;
        this.quickActionsMenu = new QuickActionsMenu();

        setupIntegration();
    }

    /**
     * Setup integration with code area
     */
    private void setupIntegration() {
        // Keyboard shortcut (Ctrl+Space or Ctrl+.)
        codeArea.setOnKeyPressed(event -> {
            KeyCombination quickActionsShortcut = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);
            KeyCombination alternativeShortcut = new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.CONTROL_DOWN);

            if (quickActionsShortcut.match(event) || alternativeShortcut.match(event)) {
                event.consume();
                showQuickActionsMenu();
            }
        });

        // Right-click context menu integration
        codeArea.setOnMousePressed(event -> {
            if (event.isSecondaryButtonDown()) {
                // Could integrate with existing context menu or show standalone
                showQuickActionsMenu(event.getScreenX(), event.getScreenY());
            }
        });

        // Setup action execution listener
        quickActionsMenu.setExecutionListener((action, result) -> {
            handleActionResult(action, result);
        });
    }

    /**
     * Show quick actions menu at cursor position
     */
    public void showQuickActionsMenu() {
        var bounds = codeArea.getCaretBounds();
        if (bounds.isPresent()) {
            double x = bounds.get().getMaxX();
            double y = bounds.get().getMaxY();

            // Convert to screen coordinates
            var screenBounds = codeArea.localToScreen(x, y);
            showQuickActionsMenu(screenBounds.getX(), screenBounds.getY());
        }
    }

    /**
     * Show quick actions menu at specific coordinates
     */
    public void showQuickActionsMenu(double screenX, double screenY) {
        QuickAction.ActionContext context = createActionContext();
        quickActionsMenu.show(screenX, screenY, context);
    }

    /**
     * Create action context from current editor state
     */
    private QuickAction.ActionContext createActionContext() {
        String fullText = codeArea.getText();
        String selectedText = codeArea.getSelectedText();
        int caretPosition = codeArea.getCaretPosition();
        int selectionStart = codeArea.getAnchor();
        int selectionEnd = codeArea.getCaretPosition();

        if (selectionStart > selectionEnd) {
            int temp = selectionStart;
            selectionStart = selectionEnd;
            selectionEnd = temp;
        }

        QuickAction.ActionContext context = new QuickAction.ActionContext(fullText, selectedText, caretPosition);
        context.selectionStart = selectionStart;
        context.selectionEnd = selectionEnd;

        // Analyze content for context flags
        analyzeContentContext(context, fullText, caretPosition);

        return context;
    }

    /**
     * Analyze content to set context flags
     */
    private void analyzeContentContext(QuickAction.ActionContext context, String fullText, int caretPosition) {
        // Check if content is XML
        context.hasXmlContent = isXmlContent(fullText);

        // Check for XSD schema availability - simplified check
        context.hasXsdSchema = xsdIntegration != null;

        if (context.hasXmlContent) {
            // Check for namespaces
            context.hasNamespaces = fullText.contains("xmlns");

            // Determine cursor position context
            String textUpToCursor = fullText.substring(0, Math.min(caretPosition, fullText.length()));
            analyzeCursorContext(context, textUpToCursor);
        }
    }

    /**
     * Analyze cursor position context
     */
    private void analyzeCursorContext(QuickAction.ActionContext context, String textUpToCursor) {
        // Find the last opening tag before cursor
        int lastOpenTag = textUpToCursor.lastIndexOf('<');
        int lastCloseTag = textUpToCursor.lastIndexOf('>');

        if (lastOpenTag > lastCloseTag) {
            // Cursor is inside a tag
            String tagContent = textUpToCursor.substring(lastOpenTag + 1);

            if (tagContent.contains("=")) {
                context.cursorInAttribute = true;
            } else {
                context.cursorInElement = true;

                // Extract element name
                String[] parts = tagContent.split("\\s+");
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    context.currentElement = parts[0];
                }
            }
        }

        // Extract current namespace (simplified)
        if (textUpToCursor.contains("xmlns")) {
            // This is a simplified namespace detection
            // In practice, would need proper XML parsing
            context.currentNamespace = "detected"; // Placeholder
        }
    }

    /**
     * Check if content appears to be XML
     */
    private boolean isXmlContent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String trimmed = text.trim();
        return trimmed.startsWith("<?xml") ||
                (trimmed.startsWith("<") && trimmed.contains(">"));
    }

    /**
     * Handle action execution result
     */
    private void handleActionResult(QuickAction action, QuickAction.ActionResult result) {
        if (result.isSuccess()) {
            if (result.hasTextModification()) {
                int oldCaret = codeArea.getCaretPosition();
                int oldAnchor = codeArea.getAnchor();
                // Replace editor content
                codeArea.replaceText(result.getModifiedText());

                // Set caret position if specified, otherwise restore previous
                int newCaret = result.getNewCaretPosition();
                if (newCaret >= 0) {
                    codeArea.moveTo(Math.min(newCaret, codeArea.getLength()));
                } else {
                    int len = codeArea.getLength();
                    int restoreCaret = Math.max(0, Math.min(oldCaret, len));
                    int restoreAnchor = Math.max(0, Math.min(oldAnchor, len));
                    if (restoreCaret != restoreAnchor) {
                        codeArea.selectRange(restoreAnchor, restoreCaret);
                    } else {
                        codeArea.moveTo(restoreCaret);
                    }
                }
            }

            // Show success message if no text modification
            if (!result.hasTextModification() && !result.getMessage().equals("Success")) {
                showStatusMessage(result.getMessage(), false);
            }

        } else {
            // Show error message
            showStatusMessage("Action failed: " + result.getMessage(), true);
        }
    }

    /**
     * Show status message to user
     */
    private void showStatusMessage(String message, boolean isError) {
        Alert alert = new Alert(isError ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION);
        alert.setTitle(isError ? "Action Error" : "Action Result");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Add custom action to the menu
     */
    public void addCustomAction(QuickAction action) {
        quickActionsMenu.addAction(action);
    }

    /**
     * Get the quick actions menu instance
     */
    public QuickActionsMenu getQuickActionsMenu() {
        return quickActionsMenu;
    }
}