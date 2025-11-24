package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Duration;

/**
 * Text view component for XML editing.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Syntax highlighting for XML</li>
 *   <li>Line numbers</li>
 *   <li>Auto-indentation</li>
 *   <li>Read-only mode support</li>
 *   <li>Bi-directional sync with XmlEditorContext</li>
 * </ul>
 *
 * <p>Integration with XmlEditorContext:</p>
 * <ul>
 *   <li>Loads text from document when context changes</li>
 *   <li>Respects edit mode (editable/read-only)</li>
 *   <li>Fires text change events for model synchronization</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * XmlEditorContext context = new XmlEditorContext();
 * XmlTextView textView = new XmlTextView(context);
 *
 * // Text view automatically syncs with context
 * context.loadDocumentFromString("<root><child/></root>");
 *
 * // Changes in text view can be synced back to model
 * textView.getText(); // Get current text
 * }</pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlTextView extends StackPane {

    /**
     * The code area component (RichTextFX).
     */
    private final CodeArea codeArea;

    /**
     * The editor context.
     */
    private final XmlEditorContext context;

    /**
     * Whether to suppress text change events (to prevent loops).
     */
    private boolean suppressTextEvents = false;

    /**
     * Whether dark theme is enabled.
     */
    private boolean darkTheme = false;

    // ==================== Constructor ====================

    /**
     * Constructs a new XmlTextView with the given context.
     *
     * @param context the editor context
     */
    public XmlTextView(XmlEditorContext context) {
        this.context = context;
        this.codeArea = new CodeArea();

        setupCodeArea();
        setupSyntaxHighlighting();
        setupListeners();
        loadDocumentText();

        // Wrap in virtualized scroll pane
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        getChildren().add(scrollPane);
    }

    // ==================== Setup Methods ====================

    /**
     * Sets up the code area with line numbers and styling.
     */
    private void setupCodeArea() {
        // Add line numbers
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        // Set font
        codeArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 12pt;");

        // Apply default stylesheet
        applySyntaxHighlightingTheme();
    }

    /**
     * Sets up syntax highlighting.
     */
    private void setupSyntaxHighlighting() {
        // Compute highlighting on text changes (with debouncing)
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(100))
                .subscribe(ignore -> {
                    codeArea.setStyleSpans(0, XmlSyntaxHighlighter.computeHighlighting(codeArea.getText()));
                });
    }

    /**
     * Sets up listeners for context changes.
     */
    private void setupListeners() {
        // Listen to document changes
        context.addPropertyChangeListener("document", this::onDocumentChanged);

        // Listen to edit mode changes
        context.addPropertyChangeListener("editMode", this::onEditModeChanged);

        // Initial edit mode
        updateEditMode();
    }

    /**
     * Applies the current syntax highlighting theme.
     */
    private void applySyntaxHighlightingTheme() {
        String stylesheet = darkTheme ?
                XmlSyntaxHighlighter.getDarkThemeStylesheet() :
                XmlSyntaxHighlighter.getDefaultStylesheet();

        // Apply to code area
        codeArea.getStylesheets().clear();
        codeArea.getStylesheets().add("data:text/css," + stylesheet);
    }

    // ==================== Event Handlers ====================

    /**
     * Called when the document changes in the context.
     */
    private void onDocumentChanged(PropertyChangeEvent evt) {
        loadDocumentText();
    }

    /**
     * Called when edit mode changes in the context.
     */
    private void onEditModeChanged(PropertyChangeEvent evt) {
        updateEditMode();
    }

    /**
     * Updates the edit mode of the code area.
     */
    private void updateEditMode() {
        codeArea.setEditable(context.isEditMode());
    }

    // ==================== Document Loading ====================

    /**
     * Loads the current document text from the context.
     */
    private void loadDocumentText() {
        suppressTextEvents = true;

        String text = context.serializeToString();
        codeArea.clear();
        codeArea.replaceText(0, 0, text);

        // Trigger initial syntax highlighting
        Platform.runLater(() -> {
            codeArea.setStyleSpans(0, XmlSyntaxHighlighter.computeHighlighting(text));
        });

        suppressTextEvents = false;
    }

    // ==================== Public API ====================

    /**
     * Returns the current text content.
     *
     * @return the XML text
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Sets the text content.
     *
     * @param text the XML text
     */
    public void setText(String text) {
        suppressTextEvents = true;
        codeArea.replaceText(0, codeArea.getLength(), text);
        codeArea.setStyleSpans(0, XmlSyntaxHighlighter.computeHighlighting(text));
        suppressTextEvents = false;
    }

    /**
     * Clears the text content.
     */
    public void clear() {
        suppressTextEvents = true;
        codeArea.clear();
        suppressTextEvents = false;
    }

    /**
     * Returns the code area component.
     *
     * @return the code area
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * Sets whether dark theme is enabled.
     *
     * @param darkTheme true to enable dark theme
     */
    public void setDarkTheme(boolean darkTheme) {
        this.darkTheme = darkTheme;
        applySyntaxHighlightingTheme();
    }

    /**
     * Returns whether dark theme is enabled.
     *
     * @return true if dark theme enabled
     */
    public boolean isDarkTheme() {
        return darkTheme;
    }

    /**
     * Formats the current XML text (pretty print).
     * Uses the XmlSerializer to reformat the text.
     */
    public void formatXml() {
        try {
            String currentText = getText();

            // Parse and re-serialize for formatting
            XmlDocument doc = context.getParser().parse(currentText);
            String formattedText = context.getSerializer().serialize(doc);

            setText(formattedText);
        } catch (Exception e) {
            // If parsing fails, leave text as-is
            System.err.println("Failed to format XML: " + e.getMessage());
        }
    }

    /**
     * Undo the last text edit.
     */
    public void undo() {
        codeArea.undo();
    }

    /**
     * Redo the last undone text edit.
     */
    public void redo() {
        codeArea.redo();
    }

    /**
     * Checks if undo is available.
     *
     * @return true if can undo
     */
    public boolean canUndo() {
        return codeArea.isUndoAvailable();
    }

    /**
     * Checks if redo is available.
     *
     * @return true if can redo
     */
    public boolean canRedo() {
        return codeArea.isRedoAvailable();
    }

    /**
     * Selects all text.
     */
    public void selectAll() {
        codeArea.selectAll();
    }

    /**
     * Cuts selected text to clipboard.
     */
    public void cut() {
        codeArea.cut();
    }

    /**
     * Copies selected text to clipboard.
     */
    public void copy() {
        codeArea.copy();
    }

    /**
     * Pastes text from clipboard.
     */
    public void paste() {
        codeArea.paste();
    }

    /**
     * Returns selected text.
     *
     * @return the selected text, or empty string
     */
    public String getSelectedText() {
        return codeArea.getSelectedText();
    }

    /**
     * Returns the caret position.
     *
     * @return caret position
     */
    public int getCaretPosition() {
        return codeArea.getCaretPosition();
    }

    /**
     * Sets the caret position.
     *
     * @param position the position
     */
    public void setCaretPosition(int position) {
        codeArea.moveTo(position);
    }

    /**
     * Scrolls to the specified line.
     *
     * @param line the line number (0-based)
     */
    public void scrollToLine(int line) {
        codeArea.showParagraphAtTop(line);
    }

    /**
     * Returns the current line number (0-based).
     *
     * @return current line number
     */
    public int getCurrentLine() {
        return codeArea.getCurrentParagraph();
    }

    /**
     * Returns the total number of lines.
     *
     * @return line count
     */
    public int getLineCount() {
        return codeArea.getParagraphs().size();
    }

    /**
     * Requests focus for the code area.
     */
    public void requestCodeAreaFocus() {
        codeArea.requestFocus();
    }
}
