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
 * <p>Target: <400 lines - only coordination logic here.</p>
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

    // IntelliSense
    private final IntelliSenseEngine intelliSenseEngine;

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

        // Create IntelliSense engine
        this.intelliSenseEngine = new IntelliSenseEngine(editorContext);
        this.intelliSenseEngine.initialize();
        this.editorContext.setIntelliSenseEngine(intelliSenseEngine);
        this.eventHandlerManager.setIntelliSenseEngine(intelliSenseEngine);

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

        // Layout: scrollpane + status line
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(scrollPane, statusLineManager.getStatusLine());

        logger.debug("UI initialized");
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

        // Auto-detect editor mode
        // TODO: Implement mode detection
        if (text != null && !text.isEmpty()) {
            Platform.runLater(() -> {
                syntaxManager.applySyntaxHighlighting(text);
                foldingManager.updateFoldingRegions(text);
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
     * Invalidates IntelliSense cache.
     * Call this when XSD schema changes.
     */
    public void invalidateIntelliSenseCache() {
        intelliSenseEngine.invalidateCacheForSchema();
        logger.debug("IntelliSense cache invalidated");
    }

    @Override
    public String toString() {
        return "XmlCodeEditorV2{" +
                "mode=" + editorContext.getCurrentMode() +
                ", dirty=" + editorContext.isDirty() +
                ", hasSchema=" + editorContext.hasSchema() +
                '}';
    }
}
