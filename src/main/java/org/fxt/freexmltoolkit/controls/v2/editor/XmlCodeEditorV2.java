package org.fxt.freexmltoolkit.controls.v2.editor;

import javafx.application.Platform;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorEventBus;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.core.ModeDetector;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.IntelliSenseEngine;
import org.fxt.freexmltoolkit.controls.v2.editor.managers.*;
import org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProvider;

import java.util.Objects;

/**
 * XML Code Editor V2 - Completely redesigned architecture.
 *
 * <p>This is a clean-room implementation with:</p>
 * <ul>
 *   <li>Dependency Injection for all components</li>
 *   <li>Manager-based architecture for separation of concerns</li>
 *   <li>Event-driven communication via EditorEventBus</li>
 *   <li>Context-sensitive IntelliSense with provider registry</li>
 *   <li>Testable design with no tight coupling</li>
 * </ul>
 *
 * <p>Target: &lt;400 lines - only coordination logic here.</p>
 */
public class XmlCodeEditorV2 extends VBox {

    private static final Logger logger = LogManager.getLogger(XmlCodeEditorV2.class);

    // Core components
    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final EditorContext editorContext;
    private final EditorEventBus eventBus;

    // Managers
    private final SyntaxHighlightManagerV2 syntaxManager;
    private final ValidationManagerV2 validationManager;
    private final FoldingManagerV2 foldingManager;
    private final StatusLineManagerV2 statusLineManager;
    private final EventHandlerManager eventHandlerManager;
    private final ContextMenuManagerV2 contextMenuManager;

    // IntelliSense
    private final IntelliSenseEngine intelliSenseEngine;

    // Folding control - can be disabled for single-line XML files
    private boolean foldingEnabled = true;

    // Font size control
    private static final int DEFAULT_FONT_SIZE = 11;
    private static final int MIN_FONT_SIZE = 6;
    private static final int MAX_FONT_SIZE = 72;
    private int fontSize = DEFAULT_FONT_SIZE;

    /**
     * Creates a new XmlCodeEditorV2.
     *
     * @param schemaProvider the XSD schema provider
     */
    public XmlCodeEditorV2(XmlSchemaProvider schemaProvider) {
        super();
        Objects.requireNonNull(schemaProvider, "SchemaProvider cannot be null");

        logger.info("Creating XmlCodeEditorV2...");

        // Create core components
        this.codeArea = new CodeArea();
        this.scrollPane = new VirtualizedScrollPane<>(codeArea);
        this.eventBus = new EditorEventBus();
        this.editorContext = new EditorContext(codeArea, eventBus, schemaProvider);

        // Create managers
        this.syntaxManager = new SyntaxHighlightManagerV2(codeArea);
        this.validationManager = new ValidationManagerV2(editorContext);
        this.foldingManager = new FoldingManagerV2(codeArea);
        this.statusLineManager = new StatusLineManagerV2(editorContext);
        this.eventHandlerManager = new EventHandlerManager(editorContext);
        this.contextMenuManager = new ContextMenuManagerV2(editorContext, foldingManager);
        this.contextMenuManager.initialize();

        // Create IntelliSense engine
        this.intelliSenseEngine = new IntelliSenseEngine(editorContext);
        this.intelliSenseEngine.initialize();
        this.editorContext.setIntelliSenseEngine(intelliSenseEngine);
        this.eventHandlerManager.setIntelliSenseEngine(intelliSenseEngine);

        // Set font size callbacks
        this.eventHandlerManager.setFontSizeCallbacks(
                this::increaseFontSize,
                this::decreaseFontSize,
                this::resetFontSize
        );

        // Initialize UI
        initializeUI();

        // Setup event handlers
        eventHandlerManager.setupHandlers();

        // Setup text change listeners
        setupTextChangeListeners();

        logger.info("XmlCodeEditorV2 created successfully");
    }

    /**
     * Initializes the UI layout.
     */
    private void initializeUI() {
        // Load CSS stylesheets
        loadStylesheets();

        // Set up code area styling
        codeArea.setStyle("-fx-font-size: 11pt; -fx-font-family: 'JetBrains Mono', 'Consolas', 'Monaco', monospace;");

        // Setup combined paragraph graphics (fold indicators + line numbers)
        codeArea.setParagraphGraphicFactory(createCombinedParagraphGraphicFactory());

        // Layout: scrollpane + status line
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(scrollPane, statusLineManager.getStatusLine());

        logger.debug("UI initialized");
    }

    /**
     * Creates combined paragraph graphics factory for line numbers and fold indicators.
     *
     * @return the paragraph graphics factory
     */
    private java.util.function.IntFunction<javafx.scene.Node> createCombinedParagraphGraphicFactory() {
        return lineIndex -> {
            javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(3);
            hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // Get fold indicator from folding manager
            javafx.scene.Node foldIndicator = foldingManager.createFoldIndicator(lineIndex);
            if (foldIndicator != null) {
                logger.debug("Adding fold indicator for line {}", lineIndex);
                hbox.getChildren().add(foldIndicator);
            } else {
                // Add spacer to maintain alignment
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                spacer.setPrefSize(12, 12);
                hbox.getChildren().add(spacer);
            }

            // Add line number label
            javafx.scene.control.Label lineNumber = new javafx.scene.control.Label(String.format("%4d", lineIndex + 1));
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
            String cssPath = "/css/fxt-theme.css";
            var cssResource = getClass().getResource(cssPath);
            if (cssResource != null) {
                codeArea.getStylesheets().add(cssResource.toExternalForm());
            }

            // Load XML highlighting CSS
            String xmlCssPath = "/scss/xml-highlighting.css";
            var xmlCssResource = getClass().getResource(xmlCssPath);
            if (xmlCssResource != null) {
                codeArea.getStylesheets().add(xmlCssResource.toExternalForm());
            }

            logger.debug("Stylesheets loaded");
        } catch (Exception e) {
            logger.error("Error loading stylesheets: {}", e.getMessage(), e);
        }
    }

    /**
     * Sets up text change listeners.
     */
    private void setupTextChangeListeners() {
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.equals(oldText)) {
                // Apply syntax highlighting
                Platform.runLater(() -> syntaxManager.applySyntaxHighlighting(newText));

                // Mark as dirty
                editorContext.setDirty(true);

                // Publish text changed event
                eventBus.publish(new org.fxt.freexmltoolkit.controls.v2.editor.core.EditorEvent.TextChangedEvent(
                        oldText, newText));
            }
        });

        // Caret position listener for status line
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            statusLineManager.refreshCursorPosition();

            // Publish caret moved event
            eventBus.publish(new org.fxt.freexmltoolkit.controls.v2.editor.core.EditorEvent.CaretMovedEvent(
                    oldPos.intValue(), newPos.intValue()));
        });

        logger.debug("Text change listeners setup");
    }

    // ==================== Public API ====================

    /**
     * Sets the text content.
     *
     * @param text the text to set
     */
    public void setText(String text) {
        codeArea.replaceText(text);

        // Auto-detect editor mode based on content
        if (text != null && !text.isEmpty()) {
            EditorMode detectedMode = ModeDetector.detectMode(text, editorContext.hasSchema());
            editorContext.setCurrentMode(detectedMode);
            logger.info("Auto-detected editor mode: {}", detectedMode);

            Platform.runLater(() -> {
                syntaxManager.applySyntaxHighlighting(text);

                // Only update folding if enabled (disabled for single-line XML files to prevent freezing)
                if (foldingEnabled) {
                    foldingManager.updateFoldingRegions(text);
                } else {
                    logger.info("Folding disabled - skipping folding region calculation");
                }

                // Force paragraph graphics refresh after folding regions are updated
                logger.debug("Refreshing paragraph graphics after setText()");
                var factory = codeArea.getParagraphGraphicFactory();
                codeArea.setParagraphGraphicFactory(null);
                codeArea.setParagraphGraphicFactory(factory);
            });
        }
    }

    /**
     * Gets the current text content.
     *
     * @return the text
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Gets the code area component.
     *
     * @return the code area
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * Gets the editor context.
     *
     * @return the editor context
     */
    public EditorContext getEditorContext() {
        return editorContext;
    }

    /**
     * Gets the event bus.
     *
     * @return the event bus
     */
    public EditorEventBus getEventBus() {
        return eventBus;
    }

    /**
     * Gets the IntelliSense engine.
     *
     * @return the IntelliSense engine
     */
    public IntelliSenseEngine getIntelliSenseEngine() {
        return intelliSenseEngine;
    }

    /**
     * Sets the editor mode.
     *
     * @param mode the editor mode
     */
    public void setEditorMode(EditorMode mode) {
        editorContext.setCurrentMode(mode);
    }

    /**
     * Gets the current editor mode.
     *
     * @return the editor mode
     */
    public EditorMode getEditorMode() {
        return editorContext.getCurrentMode();
    }

    /**
     * Checks if the editor has unsaved changes.
     *
     * @return true if dirty
     */
    public boolean isDirty() {
        return editorContext.isDirty();
    }

    /**
     * Sets the dirty flag.
     *
     * @param dirty the dirty state
     */
    public void setDirty(boolean dirty) {
        editorContext.setDirty(dirty);
    }

    /**
     * Sets the document URI.
     *
     * @param uri the document URI
     */
    public void setDocumentUri(String uri) {
        editorContext.setDocumentUri(uri);
    }

    /**
     * Gets the document URI.
     *
     * @return the document URI
     */
    public String getDocumentUri() {
        return editorContext.getDocumentUri();
    }

    /**
     * Refreshes syntax highlighting.
     */
    public void refreshHighlighting() {
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            Platform.runLater(() -> syntaxManager.applySyntaxHighlighting(currentText));
        }
    }

    /**
     * Folds all foldable regions in the editor.
     */
    public void foldAll() {
        foldingManager.foldAll();
    }

    /**
     * Unfolds all folded regions in the editor.
     */
    public void unfoldAll() {
        foldingManager.unfoldAll();
    }

    /**
     * Gets the folding manager.
     *
     * @return the folding manager
     */
    public FoldingManagerV2 getFoldingManager() {
        return foldingManager;
    }

    /**
     * Invalidates IntelliSense cache.
     * Call this when XSD schema changes.
     */
    public void invalidateIntelliSenseCache() {
        intelliSenseEngine.invalidateCacheForSchema();
        logger.debug("IntelliSense cache invalidated");
    }

    /**
     * Refreshes the status line display.
     * Call this when XSD or other status information changes.
     */
    public void refreshStatusLine() {
        statusLineManager.refresh();
    }

    // ==================== XSD Status Control ====================

    /**
     * Sets the XSD status to LOADING.
     * Call this when starting to load an XSD schema.
     */
    public void setXsdLoading() {
        statusLineManager.setXsdLoading();
    }

    /**
     * Sets the XSD status to LOADED.
     * Call this when XSD schema has been successfully loaded.
     * Also invalidates the IntelliSense cache so completions are recalculated.
     */
    public void setXsdLoaded() {
        statusLineManager.setXsdLoaded();
        // Invalidate IntelliSense cache so completions are recalculated with the new schema
        if (intelliSenseEngine != null) {
            intelliSenseEngine.invalidateCacheForSchema();
            logger.debug("IntelliSense cache invalidated after XSD load");
        }
    }

    /**
     * Sets the XSD status to ERROR.
     * Call this when XSD schema loading failed.
     */
    public void setXsdError() {
        statusLineManager.setXsdError();
    }

    /**
     * Sets the XSD status to NONE.
     * Call this when no XSD is associated.
     */
    public void setXsdNone() {
        statusLineManager.setXsdNone();
    }

    /**
     * Gets the context menu manager.
     *
     * @return the context menu manager
     */
    public ContextMenuManagerV2 getContextMenuManager() {
        return contextMenuManager;
    }

    /**
     * Sets whether code folding is enabled.
     * Disabling folding is useful for single-line XML files to prevent performance issues.
     *
     * @param enabled true to enable folding, false to disable
     */
    public void setFoldingEnabled(boolean enabled) {
        this.foldingEnabled = enabled;
        logger.info("Folding enabled: {}", enabled);
    }

    /**
     * Checks if code folding is enabled.
     *
     * @return true if folding is enabled
     */
    public boolean isFoldingEnabled() {
        return foldingEnabled;
    }

    // ==================== Font Size Control ====================

    /**
     * Increases the font size by 1 point.
     */
    public void increaseFontSize() {
        if (fontSize < MAX_FONT_SIZE) {
            setFontSize(fontSize + 1);
        }
    }

    /**
     * Decreases the font size by 1 point.
     */
    public void decreaseFontSize() {
        if (fontSize > MIN_FONT_SIZE) {
            setFontSize(fontSize - 1);
        }
    }

    /**
     * Resets the font size to the default value.
     */
    public void resetFontSize() {
        setFontSize(DEFAULT_FONT_SIZE);
    }

    /**
     * Sets the font size.
     *
     * @param size the font size in points
     */
    public void setFontSize(int size) {
        this.fontSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
        codeArea.setStyle("-fx-font-size: " + fontSize + "pt; -fx-font-family: 'JetBrains Mono', 'Consolas', 'Monaco', monospace;");
        logger.debug("Font size changed to: {}pt", fontSize);
    }

    /**
     * Gets the current font size.
     *
     * @return the font size in points
     */
    public int getFontSize() {
        return fontSize;
    }

    @Override
    public String toString() {
        return "XmlCodeEditorV2{" +
                "mode=" + editorContext.getCurrentMode() +
                ", dirty=" + editorContext.isDirty() +
                ", hasSchema=" + editorContext.hasSchema() +
                '}';
    }

    // ==================== Search & Replace ====================

    private int lastSearchPos = -1;
    private String lastSearchText = "";

    /**
     * Finds text in the editor.
     *
     * @param searchText the text to find
     * @param forward    true to search forward, false to search backward
     * @return true if text was found
     */
    public boolean find(String searchText, boolean forward) {
        if (searchText == null || searchText.isEmpty()) {
            return false;
        }

        String content = codeArea.getText();
        if (content == null || content.isEmpty()) {
            return false;
        }

        // Reset search position if search text changed
        if (!searchText.equals(lastSearchText)) {
            lastSearchPos = -1;
            lastSearchText = searchText;
        }

        int currentPos = codeArea.getCaretPosition();
        int foundPos;

        if (forward) {
            // Search forward from current position
            int startPos = currentPos;
            foundPos = content.toLowerCase().indexOf(searchText.toLowerCase(), startPos);
            // Wrap around if not found
            if (foundPos < 0 && startPos > 0) {
                foundPos = content.toLowerCase().indexOf(searchText.toLowerCase());
            }
        } else {
            // Search backward from current position
            int endPos = currentPos > 0 ? currentPos - 1 : content.length();
            foundPos = content.toLowerCase().lastIndexOf(searchText.toLowerCase(), endPos);
            // Wrap around if not found
            if (foundPos < 0) {
                foundPos = content.toLowerCase().lastIndexOf(searchText.toLowerCase());
            }
        }

        if (foundPos >= 0) {
            lastSearchPos = foundPos;
            codeArea.selectRange(foundPos, foundPos + searchText.length());
            codeArea.requestFollowCaret();
            return true;
        }

        return false;
    }

    /**
     * Finds and highlights all occurrences of the search text.
     *
     * @param searchText the text to find
     * @return the number of occurrences found
     */
    public int findAll(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return 0;
        }

        String content = codeArea.getText();
        if (content == null || content.isEmpty()) {
            return 0;
        }

        String lowerContent = content.toLowerCase();
        String lowerSearch = searchText.toLowerCase();
        int count = 0;
        int pos = 0;

        while ((pos = lowerContent.indexOf(lowerSearch, pos)) >= 0) {
            count++;
            pos += searchText.length();
        }

        // Select first occurrence
        if (count > 0) {
            find(searchText, true);
        }

        return count;
    }

    /**
     * Replaces the current selection with new text.
     *
     * @param findText    the text to find
     * @param replaceText the replacement text
     * @return true if replacement was made
     */
    public boolean replace(String findText, String replaceText) {
        if (findText == null || findText.isEmpty()) {
            return false;
        }

        String selected = codeArea.getSelectedText();
        if (selected != null && selected.equalsIgnoreCase(findText)) {
            codeArea.replaceSelection(replaceText != null ? replaceText : "");
            return true;
        } else {
            // Find next occurrence first
            if (find(findText, true)) {
                codeArea.replaceSelection(replaceText != null ? replaceText : "");
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces all occurrences of text.
     *
     * @param findText    the text to find
     * @param replaceText the replacement text
     * @return the number of replacements made
     */
    public int replaceAll(String findText, String replaceText) {
        if (findText == null || findText.isEmpty()) {
            return 0;
        }

        String content = codeArea.getText();
        if (content == null || content.isEmpty()) {
            return 0;
        }

        // Count occurrences first
        int count = 0;
        int pos = 0;
        String lowerContent = content.toLowerCase();
        String lowerFind = findText.toLowerCase();
        while ((pos = lowerContent.indexOf(lowerFind, pos)) >= 0) {
            count++;
            pos += findText.length();
        }

        if (count > 0) {
            // Perform case-insensitive replace
            String newContent = content;
            StringBuilder result = new StringBuilder();
            int lastEnd = 0;
            pos = 0;
            while ((pos = lowerContent.indexOf(lowerFind, pos)) >= 0) {
                result.append(content, lastEnd, pos);
                result.append(replaceText != null ? replaceText : "");
                lastEnd = pos + findText.length();
                pos = lastEnd;
            }
            result.append(content.substring(lastEnd));

            int oldCaret = codeArea.getCaretPosition();
            codeArea.replaceText(result.toString());
            int newLen = codeArea.getLength();
            codeArea.moveTo(Math.min(oldCaret, newLen));
        }

        return count;
    }

    /**
     * Clears the search state.
     */
    public void clearSearch() {
        lastSearchPos = -1;
        lastSearchText = "";
    }
}
