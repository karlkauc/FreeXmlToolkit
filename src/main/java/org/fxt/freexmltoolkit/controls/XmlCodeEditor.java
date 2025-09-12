package org.fxt.freexmltoolkit.controls;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.editor.*;
import org.fxt.freexmltoolkit.controls.intellisense.*;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.ThreadPoolManager;

import java.io.File;
import java.util.*;
import java.util.function.IntFunction;

/**
 * A comprehensive XML code editor component with separated concerns.
 * This class coordinates multiple managers to provide XML editing functionality.
 *
 * This refactored version uses the Manager pattern to separate responsibilities:
 * - SyntaxHighlightManager: Handles all syntax highlighting
 * - XmlValidationManager: Manages validation and error highlighting
 * - FileOperationsManager: Handles file operations and monitoring
 * - XmlContextMenuManager: Manages context menu and actions
 * - StatusLineController: Controls status line display
 */
public class XmlCodeEditor extends VBox {

    private static final Logger logger = LogManager.getLogger(XmlCodeEditor.class);
    private static final int DEFAULT_FONT_SIZE = 11;

    // Core services
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    private final ThreadPoolManager threadPoolManager = ThreadPoolManager.getInstance();

    // Core UI components
    private final CodeArea codeArea = new CodeArea();
    private final VirtualizedScrollPane<CodeArea> virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);

    // Manager components
    private SyntaxHighlightManager syntaxHighlightManager;
    private XmlValidationManager validationManager;
    private FileOperationsManager fileOperationsManager;
    private XmlContextMenuManager contextMenuManager;
    private StatusLineController statusLineController;

    // Editor state
    private int fontSize = DEFAULT_FONT_SIZE;
    private String documentUri;
    private XmlEditor parentXmlEditor;
    private EditorMode currentMode = EditorMode.XML_WITHOUT_XSD;

    // Layout components
    private HBox editorContainer;
    private MinimapView minimapView;
    private boolean minimapVisible = false;

    // IntelliSense components (will be moved to separate manager in future phases)
    private Popup intelliSensePopup;
    private ListView<String> completionListView; // Legacy - will be replaced
    private List<String> availableElementNames = new ArrayList<>();
    private Map<String, List<String>> contextElementNames = new HashMap<>();
    private int popupStartPosition = -1;
    private boolean isElementCompletionContext = false;

    // Enhanced IntelliSense with 3-column layout
    private EnhancedCompletionPopup enhancedIntelliSensePopup;

    // Specialized Auto-Completion (will be managed by IntelliSenseManager in future)
    private SchematronAutoComplete schematronAutoComplete;
    private XsdAutoComplete xsdAutoComplete;
    private XsltAutoComplete xsltAutoComplete;
    private XslFoAutoComplete xslFoAutoComplete;

    // Enhanced IntelliSense Components (will be managed in future phases)
    private EnhancedCompletionPopup enhancedCompletionPopup;
    private FuzzySearch fuzzySearch;
    private XsdDocumentationExtractor xsdDocExtractor;
    private AttributeValueHelper attributeValueHelper;
    private CompletionCache completionCache;
    private PerformanceProfiler performanceProfiler;
    private MultiSchemaManager multiSchemaManager;
    private TemplateEngine templateEngine;
    private QuickActionsIntegration quickActionsIntegration;

    private final boolean enhancedIntelliSenseEnabled = true;
    private boolean isProcessingEnterKey = false;
    private org.fxt.freexmltoolkit.controls.intellisense.XsdIntegrationAdapter xsdIntegration;
    private XmlIntelliSenseEngine intelliSenseEngine;
    private XmlCodeFoldingManager codeFoldingManager;

    // Debouncing for syntax highlighting and validation
    private PauseTransition syntaxHighlightingDebouncer;
    private PauseTransition errorHighlightingDebouncer;

    // Code folding (will be moved to separate manager in Phase 4)
    private final Map<Integer, Integer> foldingRegions = new HashMap<>();
    private final Set<Integer> foldedLines = new HashSet<>();

    // Current element text info for enumeration completion
    private ElementTextInfo currentElementTextInfo;

    /**
     * Editor modes enumeration.
     */
    public enum EditorMode {
        XML,        // Standard XML with IntelliSense
        XML_WITHOUT_XSD, // XML without linked XSD (basic auto-close only)
        XML_WITH_XSD,    // XML with linked XSD (enhanced IntelliSense)
        SCHEMATRON, // Schematron-specific auto-completion
        XSD,        // XSD-specific auto-completion
        XSLT,       // XSLT Stylesheet auto-completion
        XSL_FO      // XSL-FO Stylesheet auto-completion
    }

    /**
     * Constructor for the XML code editor.
     */
    public XmlCodeEditor() {
        super();
        initialize();
    }

    /**
     * Initializes the editor and all its managers.
     */
    private void initialize() {
        // Load CSS stylesheets for syntax highlighting
        loadCssStylesheets();

        // Initialize managers
        initializeManagers();

        // Set up paragraph graphics factory
        codeArea.setParagraphGraphicFactory(createParagraphGraphicFactory());

        // Set up event handlers
        setupEventHandlers();

        // Initialize IntelliSense (temporary, will be moved to manager in Phase 2)
        initializeIntelliSensePopup();
        initializeEnhancedIntelliSense();
        initializeXmlIntelliSenseEngine();
        initializeCodeFoldingManager();
        initializeSpecializedAutoComplete();

        // Set up the main layout with minimap
        setupLayoutWithMinimap();

        // Set up VBox growth
        VBox.setVgrow(editorContainer, Priority.ALWAYS);

        // Set up basic styling and reset font size
        resetFontSize();

        // Set up line numbers
        codeArea.setParagraphGraphicFactory(createParagraphGraphicFactory());

        // Apply initial syntax highlighting if there's text
        Platform.runLater(() -> {
            if (codeArea.getText() != null && !codeArea.getText().isEmpty()) {
                syntaxHighlightManager.applySyntaxHighlighting(codeArea.getText());
                updateFoldingRegions(codeArea.getText());
            }
        });

        // Initialize debouncers
        initializeDebouncers();

        // Set up text change listeners
        setupTextChangeListeners();

        // Set up scene and parent change listeners
        setupSceneAndParentListeners();
    }

    /**
     * Initializes all manager components.
     */
    private void initializeManagers() {
        // Initialize syntax highlight manager
        syntaxHighlightManager = new SyntaxHighlightManager(codeArea, threadPoolManager);

        // Initialize validation manager
        validationManager = new XmlValidationManager(codeArea, threadPoolManager);
        validationManager.setValidationService(this::validateText);
        validationManager.setErrorCallback(this::handleValidationErrors);
        validationManager.setValidationCompleteCallback(this::updateMinimapErrors);

        // Initialize file operations manager
        fileOperationsManager = new FileOperationsManager(codeArea);
        fileOperationsManager.setFileOperationHandler(new FileOperationHandlerImpl());

        // Initialize context menu manager
        contextMenuManager = new XmlContextMenuManager(codeArea);
        contextMenuManager.setContextActions(new ContextActionsImpl());
        contextMenuManager.initializeContextMenu();

        // Initialize status line controller
        statusLineController = new StatusLineController(codeArea, propertiesService);

        logger.debug("All managers initialized successfully");
    }

    /**
     * Implementation of FileOperationHandler interface.
     */
    private class FileOperationHandlerImpl implements FileOperationsManager.FileOperationHandler {
        @Override
        public boolean saveFile() {
            if (parentXmlEditor != null) {
                return parentXmlEditor.saveFile();
            }
            return false;
        }

        @Override
        public void saveAsFile() {
            if (parentXmlEditor != null) {
                parentXmlEditor.saveAsFile();
            }
        }
    }

    /**
     * Implementation of XmlContextActions interface.
     */
    private class ContextActionsImpl implements XmlContextMenuManager.XmlContextActions {
        @Override
        public void toggleLineComment() {
            XmlCodeEditor.this.toggleLineComment();
        }

        @Override
        public void cutToClipboard() {
            cutTextToClipboard();
        }

        @Override
        public void copyToClipboard() {
            copyTextToClipboard();
        }

        @Override
        public void pasteFromClipboard() {
            pasteTextFromClipboard();
        }

        @Override
        public void copyXPathToClipboard() {
            XmlCodeEditor.this.copyXPathToClipboard();
        }

        @Override
        public void goToDefinition() {
            // Create a synthetic mouse event for go-to-definition
            handleGoToDefinition(null);
        }

        @Override
        public void selectAllText() {
            selectAll();
        }

        @Override
        public void openFindReplace() {
            XmlCodeEditor.this.openFindReplace();
        }

        @Override
        public void formatXmlContent() {
            XmlCodeEditor.this.formatXmlContent();
        }

        @Override
        public void validateXmlContent() {
            XmlCodeEditor.this.validateXmlContent();
        }

        @Override
        public void expandAllFolds() {
            expandAll();
        }

        @Override
        public void collapseAllFolds() {
            collapseAll();
        }
    }

    // =====================================================
    // PUBLIC API METHODS
    // =====================================================

    /**
     * Sets the text content and immediately applies syntax highlighting.
     *
     * @param text The text to set
     */
    public void setText(String text) {
        codeArea.replaceText(text);

        // Auto-detect editor mode based on content
        autoDetectEditorMode(text);

        Platform.runLater(() -> {
            if (text != null && !text.isEmpty()) {
                syntaxHighlightManager.applySyntaxHighlighting(text);
                updateFoldingRegions(text);
            }
        });
    }

    /**
     * Gets the current text content.
     *
     * @return The current text
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Returns the internal CodeArea instance.
     *
     * @return The CodeArea component
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * Sets the document URI for the current document.
     *
     * @param documentUri The URI of the current document
     */
    public void setDocumentUri(String documentUri) {
        this.documentUri = documentUri;
    }

    /**
     * Gets the current document URI.
     *
     * @return The URI of the current document
     */
    public String getDocumentUri() {
        return this.documentUri;
    }

    /**
     * Sets the parent XmlEditor for accessing schema information.
     *
     * @param parentEditor The parent XmlEditor instance
     */
    public void setParentXmlEditor(XmlEditor parentEditor) {
        logger.debug("setParentXmlEditor called with: {}", parentEditor);
        this.parentXmlEditor = parentEditor;

        // Update XSD integration with new parent
        updateXsdIntegration();

        // Trigger immediate cache update when parent is set
        if (parentEditor != null) {
            Platform.runLater(this::updateEnumerationElementsCache);
        }
    }

    /**
     * Sets the current file being monitored.
     *
     * @param file The file to monitor
     */
    public void setCurrentFile(File file) {
        fileOperationsManager.setCurrentFile(file);
    }

    /**
     * Notifies that the file was saved.
     */
    public void notifyFileSaved() {
        fileOperationsManager.notifyFileSaved();
    }

    /**
     * Gets the current editor mode.
     *
     * @return The current editor mode
     */
    public EditorMode getEditorMode() {
        return currentMode;
    }

    /**
     * Sets the editor mode.
     *
     * @param mode The editor mode to set
     */
    public void setEditorMode(EditorMode mode) {
        if (this.currentMode == mode) {
            return;
        }

        // Disable all auto-completion systems first
        disableAllAutoCompletion();

        // Set new mode and enable appropriate auto-completion
        this.currentMode = mode;

        switch (mode) {
            case XML, XML_WITHOUT_XSD, XML_WITH_XSD -> {
                logger.debug("Switched to {} mode - standard IntelliSense active", mode);
            }
            case SCHEMATRON -> {
                if (schematronAutoComplete != null) {
                    schematronAutoComplete.setEnabled(true);
                    logger.debug("Switched to Schematron mode - Schematron auto-completion active");
                } else {
                    logger.warn("Cannot enable Schematron mode: SchematronAutoComplete not initialized");
                }
            }
            case XSD -> {
                if (xsdAutoComplete != null) {
                    xsdAutoComplete.setEnabled(true);
                    logger.debug("Switched to XSD mode - XSD auto-completion active");
                } else {
                    logger.warn("Cannot enable XSD mode: XsdAutoComplete not initialized");
                }
            }
            case XSLT -> {
                if (xsltAutoComplete != null) {
                    xsltAutoComplete.setEnabled(true);
                    logger.debug("Switched to XSLT mode - XSLT auto-completion active");
                } else {
                    logger.warn("Cannot enable XSLT mode: XsltAutoComplete not initialized");
                }
            }
            case XSL_FO -> {
                if (xslFoAutoComplete != null) {
                    xslFoAutoComplete.setEnabled(true);
                    logger.debug("Switched to XSL-FO mode - XSL-FO auto-completion active");
                } else {
                    logger.warn("Cannot enable XSL-FO mode: XslFoAutoComplete not initialized");
                }
            }
        }
    }

    /**
     * Increases the font size by 1 point.
     */
    public void increaseFontSize() {
        setFontSize(++fontSize);
    }

    /**
     * Decreases the font size by 1 point (minimum 1).
     */
    public void decreaseFontSize() {
        if (fontSize > 1) {
            setFontSize(--fontSize);
        }
    }

    /**
     * Resets the font size to the default value.
     */
    public void resetFontSize() {
        fontSize = DEFAULT_FONT_SIZE;
        setFontSize(fontSize);
    }

    /**
     * Moves the cursor to the beginning of the document and scrolls to the top.
     */
    public void moveUp() {
        codeArea.moveTo(0);
        codeArea.showParagraphAtTop(0);
        codeArea.requestFocus();
    }

    /**
     * Moves the cursor to the end of the document and scrolls to the bottom.
     */
    public void moveDown() {
        if (codeArea.getText() != null && !codeArea.getParagraphs().isEmpty()) {
            codeArea.moveTo(codeArea.getLength());
            codeArea.showParagraphAtBottom(codeArea.getParagraphs().size() - 1);
            codeArea.requestFocus();
        }
    }

    /**
     * Refreshes the indentation display in the status line.
     */
    public void refreshIndentationDisplay() {
        statusLineController.refreshIndentationDisplay();
    }

    /**
     * Forces syntax highlighting refresh.
     */
    public void refreshHighlighting() {
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            Platform.runLater(() -> syntaxHighlightManager.applySyntaxHighlighting(currentText));
        }
    }

    /**
     * Manually triggers syntax highlighting refresh.
     */
    public void refreshSyntaxHighlighting() {
        String currentText = codeArea.getText();
        syntaxHighlightManager.refreshSyntaxHighlighting(currentText);
    }

    // =====================================================
    // VALIDATION METHODS
    // =====================================================

    /**
     * Validates the given text using the parent XML editor's validation service.
     *
     * @param text The text to validate
     * @return List of validation errors
     * @throws Exception if validation fails
     */
    private List<org.xml.sax.SAXParseException> validateText(String text) throws Exception {
        if (parentXmlEditor instanceof org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor) {
            var xmlService = xmlEditor.getXmlService();
            if (xmlService != null) {
                return xmlService.validateText(text);
            }
        }
        return new ArrayList<>();
    }

    /**
     * Handles validation errors from the validation manager.
     *
     * @param errors Map of line numbers to error messages
     */
    private void handleValidationErrors(Map<Integer, String> errors) {
        // Update syntax highlighting with errors
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            syntaxHighlightManager.applySyntaxHighlightingWithErrors(currentText, errors);
        }
    }

    /**
     * Updates minimap with error information.
     */
    private void updateMinimapErrors() {
        if (minimapView != null && minimapVisible) {
            minimapView.updateErrors();
        }
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private void setFontSize(int size) {
        this.fontSize = size;

        // Update code area font size
        codeArea.setStyle("-fx-font-size: " + size + "pt; -fx-font-family: 'JetBrains Mono', 'Consolas', 'Monaco', monospace;");

        // Update line numbers by recreating the paragraph graphic factory
        codeArea.setParagraphGraphicFactory(createParagraphGraphicFactory());

        // Refresh syntax highlighting to ensure consistent styling
        Platform.runLater(() -> {
            if (codeArea.getText() != null && !codeArea.getText().isEmpty()) {
                syntaxHighlightManager.applySyntaxHighlighting(codeArea.getText());
            }
        });

        logger.debug("Font size updated to: {} pt", size);
    }

    /**
     * Loads CSS stylesheets for syntax highlighting.
     */
    private void loadCssStylesheets() {
        try {
            // Load the main CSS file for syntax highlighting
            String cssPath = "/css/fxt-theme.css";
            var cssResource = getClass().getResource(cssPath);
            if (cssResource != null) {
                String cssUrl = cssResource.toExternalForm();
                codeArea.getStylesheets().add(cssUrl);
                logger.debug("Loaded CSS stylesheet: {}", cssUrl);
            }

            // Also load the XML highlighting specific CSS
            String xmlCssPath = "/scss/xml-highlighting.css";
            var xmlCssResource = getClass().getResource(xmlCssPath);
            if (xmlCssResource != null) {
                String xmlCssUrl = xmlCssResource.toExternalForm();
                codeArea.getStylesheets().add(xmlCssUrl);
                logger.debug("Loaded XML highlighting CSS: {}", xmlCssUrl);
            }

            // Load IntelliSense CSS
            String intelliSenseCssPath = "/css/xml-intellisense.css";
            var intelliSenseCssResource = getClass().getResource(intelliSenseCssPath);
            if (intelliSenseCssResource != null) {
                String intelliSenseCssUrl = intelliSenseCssResource.toExternalForm();
                codeArea.getStylesheets().add(intelliSenseCssUrl);
                logger.debug("Loaded IntelliSense CSS: {}", intelliSenseCssUrl);
            }

            // Load XMLSpy-style Context Menu CSS
            String contextMenuCssPath = "/css/xml-context-menu-xmlspy.css";
            var contextMenuCssResource = getClass().getResource(contextMenuCssPath);
            if (contextMenuCssResource != null) {
                String contextMenuCssUrl = contextMenuCssResource.toExternalForm();
                codeArea.getStylesheets().add(contextMenuCssUrl);
                logger.debug("Loaded XMLSpy-style Context Menu CSS: {}", contextMenuCssUrl);
            }

        } catch (Exception e) {
            logger.error("Error loading CSS stylesheets: {}", e.getMessage(), e);
        }
    }

    /**
     * Initializes debouncers for syntax highlighting and error highlighting.
     */
    private void initializeDebouncers() {
        // Initialize debouncer for syntax highlighting
        syntaxHighlightingDebouncer = new PauseTransition(Duration.millis(300));
        syntaxHighlightingDebouncer.setOnFinished(event -> {
            String currentText = codeArea.getText();
            if (currentText != null && !currentText.isEmpty()) {
                syntaxHighlightManager.applySyntaxHighlighting(currentText);
            }
        });

        // Initialize debouncer for error highlighting
        errorHighlightingDebouncer = new PauseTransition(Duration.millis(500));
        errorHighlightingDebouncer.setOnFinished(event -> {
            String currentText = codeArea.getText();
            if (currentText != null && !currentText.isEmpty()) {
                validationManager.performLiveValidation(currentText);
            }
        });
    }

    /**
     * Sets up text change listeners.
     */
    private void setupTextChangeListeners() {
        // Text change listener for syntax highlighting with debouncing
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.equals(oldText)) {
                // Apply syntax highlighting immediately for small changes
                if (newText.length() < 10000) {
                    Platform.runLater(() -> syntaxHighlightManager.applySyntaxHighlighting(newText));
                } else {
                    // Use debouncer for larger documents
                    syntaxHighlightingDebouncer.stop();
                    syntaxHighlightingDebouncer.playFromStart();
                }

                // Reset the error highlighting debouncer timer
                errorHighlightingDebouncer.stop();
                errorHighlightingDebouncer.playFromStart();

                // Handle automatic tag completion
                handleAutomaticTagCompletion(oldText, newText);

                // Handle intelligent enter
                handleIntelligentEnter(oldText, newText);
            }
        });

        // Also add listener for caret position changes to update status line
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (statusLineController != null) {
                statusLineController.refreshCursorPosition();
            }
        });
    }

    /**
     * Sets up scene and parent change listeners to restore syntax highlighting.
     */
    private void setupSceneAndParentListeners() {
        // Add scene change listener to restore syntax highlighting when tab becomes visible
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> {
                    String currentText = codeArea.getText();
                    if (currentText != null && !currentText.isEmpty()) {
                        syntaxHighlightManager.applySyntaxHighlighting(currentText);
                    }
                });
            }
        });

        // Add parent change listener to restore syntax highlighting when moved between containers
        parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent != null) {
                Platform.runLater(() -> {
                    String currentText = codeArea.getText();
                    if (currentText != null && !currentText.isEmpty()) {
                        syntaxHighlightManager.applySyntaxHighlighting(currentText);
                    }
                });
            }
        });

        // Add focus listener to restore highlighting when CodeArea gains focus
        codeArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                Platform.runLater(() -> {
                    String currentText = codeArea.getText();
                    if (currentText != null && !currentText.isEmpty()) {
                        syntaxHighlightManager.applySyntaxHighlighting(currentText);
                    }
                });
            }
        });
    }
    
    // Event handlers setup
    private void setupEventHandlers() {
        setupFontSizeHandlers();
        setupMouseEventHandlers();
        setupKeyboardHandlers();
    }

    private void setupFontSizeHandlers() {
        // Change font size with Ctrl + mouse wheel
        codeArea.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                if (event.getDeltaY() > 0) {
                    increaseFontSize();
                } else {
                    decreaseFontSize();
                }
                event.consume();
            }
        });
    }

    private void setupMouseEventHandlers() {
        // Go-to-Definition support
        codeArea.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.isControlDown() && event.getClickCount() == 1) {
                handleGoToDefinition(event);
                event.consume();
            }
        });

        // Cursor change for Go-to-Definition
        codeArea.setOnMouseMoved(event -> {
            if (event.isControlDown()) {
                String elementAtCursor = getElementNameAtPosition(event.getX(), event.getY());
                if (elementAtCursor != null && !elementAtCursor.isEmpty()) {
                    codeArea.setCursor(Cursor.HAND);
                } else {
                    codeArea.setCursor(Cursor.DEFAULT);
                }
            } else {
                codeArea.setCursor(Cursor.DEFAULT);
            }
        });

        codeArea.setOnMouseExited(event -> codeArea.setCursor(Cursor.DEFAULT));
    }

    private void setupKeyboardHandlers() {
        // Font size shortcuts
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Handle Enhanced IntelliSense popup navigation first
            if (enhancedIntelliSensePopup != null && enhancedIntelliSensePopup.isShowing()) {
                // The EnhancedCompletionPopup handles its own key events internally
                // We just consume the event to prevent it from propagating
                switch (event.getCode()) {
                    case UP, DOWN, ENTER, TAB, ESCAPE -> {
                        // Let the popup handle these keys internally
                        event.consume();
                        return;
                    }
                }
            }
            // Fallback to legacy IntelliSense popup
            else if (intelliSensePopup != null && intelliSensePopup.isShowing()) {
                switch (event.getCode()) {
                    case UP -> {
                        moveIntelliSenseSelection(-1);
                        event.consume();
                        return;
                    }
                    case DOWN -> {
                        moveIntelliSenseSelection(1);
                        event.consume();
                        return;
                    }
                    case ENTER, TAB -> {
                        selectCurrentIntelliSenseCompletion();
                        event.consume();
                        return;
                    }
                    case ESCAPE -> {
                        hideAllIntelliSensePopups();
                        event.consume();
                        return;
                    }
                }
            }

            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case PLUS, ADD -> {
                        increaseFontSize();
                        event.consume();
                    }
                    case MINUS, SUBTRACT -> {
                        decreaseFontSize();
                        event.consume();
                    }
                    case NUMPAD0, DIGIT0 -> {
                        resetFontSize();
                        event.consume();
                    }
                    case S -> {
                        if (event.isShiftDown()) {
                            fileOperationsManager.requestSaveAs();
                        } else {
                            fileOperationsManager.handleSaveFile();
                        }
                        event.consume();
                    }
                    case D -> {
                        toggleLineComment();
                        event.consume();
                    }
                    case SPACE -> {
                        // Trigger IntelliSense manually
                        logger.info("🎯 EVENT: Ctrl+Space pressed - triggering IntelliSense");
                        showIntelliSenseCompletion();
                        event.consume();
                    }
                }
            } else {
                // Handle auto-completion triggers
                switch (event.getCode()) {
                    case LESS -> {
                        // '<' typed - show element completion after short delay
                        Platform.runLater(() -> {
                            if (shouldShowElementCompletion()) {
                                showIntelliSenseCompletion();
                            }
                        });
                    }
                }
            }
        });

        // Handle character typed events for IntelliSense
        codeArea.addEventHandler(KeyEvent.KEY_TYPED, event -> {
            String character = event.getCharacter();
            if (character != null && character.length() == 1) {
                char c = character.charAt(0);

                // Check if we should trigger IntelliSense
                if (c == '<' || (c == ' ' && isInAttributeContext())) {
                    logger.info("⚡ EVENT: Character '{}' typed - checking if should trigger IntelliSense", c);
                    Platform.runLater(() -> {
                        if (shouldShowCompletion(c)) {
                            logger.info("💡 EVENT: Should show completion for '{}' - triggering IntelliSense", c);
                            showIntelliSenseCompletion();
                        } else {
                            logger.info("❌ EVENT: Should NOT show completion for '{}' - skipping", c);
                        }
                    });
                }

                // Update existing popup if showing
                if (intelliSensePopup != null && intelliSensePopup.isShowing()) {
                    Platform.runLater(this::updateIntelliSenseCompletion);
                }
            }
        });
    }
    
    // Line number factory
    private IntFunction<Node> createParagraphGraphicFactory() {
        return lineIndex -> {
            // Create line number label
            Label lineNumber = new Label(String.format("%4d", lineIndex + 1));
            lineNumber.getStyleClass().addAll("lineno", "paragraph-graphic");

            // Set consistent styling
            lineNumber.setPadding(new Insets(0, 8, 0, 4));
            lineNumber.setMinWidth(40);
            lineNumber.setPrefWidth(40);
            lineNumber.setMaxWidth(40);
            lineNumber.setAlignment(Pos.CENTER_RIGHT);

            // Apply dynamic font size and consistent colors
            lineNumber.setStyle(String.format(
                    "-fx-text-fill: #888888; " +
                            "-fx-font-family: 'JetBrains Mono', 'Consolas', 'Monaco', monospace; " +
                            "-fx-font-size: %dpx; " +
                            "-fx-background-color: #fafafa; " +
                            "-fx-border-color: #e0e0e0; " +
                            "-fx-border-width: 0 1 0 0;",
                    fontSize
            ));

            // Add hover effect for better UX
            lineNumber.setOnMouseEntered(e ->
                    lineNumber.setStyle(lineNumber.getStyle() + "-fx-background-color: #f0f0f0;")
            );
            lineNumber.setOnMouseExited(e ->
                    lineNumber.setStyle(lineNumber.getStyle().replace("-fx-background-color: #f0f0f0;", "-fx-background-color: #fafafa;"))
            );

            return lineNumber;
        };
    }

    // IntelliSense initialization
    private void initializeIntelliSensePopup() {
        // Create the enhanced 3-column IntelliSense popup
        enhancedIntelliSensePopup = new EnhancedCompletionPopup();

        // Set up completion handler
        enhancedIntelliSensePopup.setOnItemSelected(this::insertEnhancedCompletion);

        // Initialize legacy popup for backward compatibility (will be removed)
        intelliSensePopup = new Popup();
        intelliSensePopup.setAutoHide(true);
        intelliSensePopup.setHideOnEscape(true);

        completionListView = new ListView<>();
        completionListView.setPrefWidth(300);
        completionListView.setPrefHeight(200);
        completionListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                selectCurrentIntelliSenseCompletion();
            }
        });

        VBox popupContent = new VBox(completionListView);
        intelliSensePopup.getContent().add(popupContent);

        // Initialize with some default element names
        availableElementNames.addAll(java.util.List.of(
                "root", "element", "item", "data", "value", "name", "id", "text",
                "description", "title", "content", "node", "entry", "record"
        ));

        logger.debug("Enhanced IntelliSense popup initialized");
    }

    // Temporary placeholder methods (to be moved to appropriate managers)
    private void initializeEnhancedIntelliSense() {
        // Placeholder
    }

    private void initializeXmlIntelliSenseEngine() {
        // Placeholder
    }

    private void initializeCodeFoldingManager() {
        // Placeholder
    }

    private void initializeSpecializedAutoComplete() {
        // Placeholder
    }

    private void setupLayoutWithMinimap() {
        editorContainer = new HBox();
        editorContainer.getChildren().add(virtualizedScrollPane);
        HBox.setHgrow(virtualizedScrollPane, Priority.ALWAYS);

        this.getChildren().addAll(editorContainer, statusLineController.getStatusLine());
    }

    private void updateFoldingRegions(String text) {
        // Placeholder for code folding
    }

    private void handleAutomaticTagCompletion(String oldText, String newText) {
        // Check if user typed '>' to complete a tag
        if (newText != null && oldText != null && newText.length() > oldText.length()) {
            int caretPos = codeArea.getCaretPosition();
            if (caretPos > 0 && newText.charAt(caretPos - 1) == '>') {
                // Find the opening tag
                String textBeforeCaret = newText.substring(0, caretPos - 1);
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<([a-zA-Z][a-zA-Z0-9_-]*)(?:[^>]*)$");
                java.util.regex.Matcher matcher = pattern.matcher(textBeforeCaret);

                if (matcher.find()) {
                    String tagName = matcher.group(1);
                    // Check if it's not a self-closing tag and doesn't already have a closing tag
                    if (!textBeforeCaret.endsWith("/>") && !hasClosingTag(newText, tagName, caretPos)) {
                        // Insert closing tag
                        String closingTag = "</" + tagName + ">";
                        Platform.runLater(() -> {
                            codeArea.insertText(caretPos, closingTag);
                            codeArea.moveTo(caretPos); // Move cursor back between tags
                        });
                    }
                }
            }
        }
    }

    private boolean hasClosingTag(String text, String tagName, int fromPosition) {
        String remainingText = text.substring(fromPosition);
        String closingTag = "</" + tagName + ">";
        return remainingText.contains(closingTag);
    }

    private void handleIntelligentEnter(String oldText, String newText) {
        // Prevent recursion
        if (isProcessingEnterKey) {
            return;
        }

        // Check if user pressed Enter
        if (newText != null && oldText != null && newText.length() > oldText.length()) {
            String added = newText.substring(oldText.length());
            if (added.contains("\n")) {
                isProcessingEnterKey = true;
                try {
                    int caretPos = codeArea.getCaretPosition();
                    String textBeforeCaret = newText.substring(0, caretPos);

                    // Extract current indentation from previous line
                    String[] lines = textBeforeCaret.split("\n");
                    if (lines.length >= 2) {
                        String prevLine = lines[lines.length - 2];
                        String indentation = extractIndentation(prevLine);

                        // If previous line contains an opening tag, add extra indentation
                        if (prevLine.trim().matches(".*<[^/][^>]*[^/]>.*")) {
                            int indentSize = propertiesService.getXmlIndentSpaces();
                            indentation += " ".repeat(indentSize);
                        }

                        if (!indentation.isEmpty()) {
                            codeArea.insertText(caretPos, indentation);
                        }
                    }
                } finally {
                    isProcessingEnterKey = false;
                }
            }
        }
    }

    private String extractIndentation(String line) {
        StringBuilder indentation = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') {
                indentation.append(c);
            } else {
                break;
            }
        }
        return indentation.toString();
    }

    // IntelliSense implementation methods
    private void moveIntelliSenseSelection(int delta) {
        if (completionListView != null && completionListView.getItems().size() > 0) {
            int currentIndex = completionListView.getSelectionModel().getSelectedIndex();
            int newIndex = Math.max(0, Math.min(completionListView.getItems().size() - 1, currentIndex + delta));
            completionListView.getSelectionModel().select(newIndex);
            completionListView.scrollTo(newIndex);
        }
    }

    private void selectCurrentIntelliSenseCompletion() {
        if (completionListView != null && completionListView.getSelectionModel().getSelectedItem() != null) {
            String selectedItem = completionListView.getSelectionModel().getSelectedItem();
            insertCompletion(selectedItem);
            hideIntelliSensePopup();
        }
    }

    private void insertCompletion(String completion) {
        if (popupStartPosition >= 0 && popupStartPosition <= codeArea.getLength()) {
            int currentPos = codeArea.getCaretPosition();
            // Remove any partially typed text
            if (currentPos > popupStartPosition) {
                codeArea.deleteText(popupStartPosition, currentPos);
            }

            // Insert the completion
            if (isElementCompletionContext) {
                // For element completion, add closing bracket and potentially closing tag
                String elementName = completion;
                codeArea.insertText(popupStartPosition, elementName + ">");

                // Add closing tag if not self-closing
                if (!isSelfClosingElement(elementName)) {
                    int insertPos = popupStartPosition + elementName.length() + 1;
                    String closingTag = "</" + elementName + ">";

                    // Check for mandatory child elements and insert them
                    List<String> mandatoryChildren = getMandatoryChildElements(elementName);
                    if (!mandatoryChildren.isEmpty()) {
                        StringBuilder childElements = new StringBuilder();
                        String indentation = getCurrentIndentation();
                        String childIndentation = indentation + " ".repeat(propertiesService.getXmlIndentSpaces());

                        for (String childElement : mandatoryChildren) {
                            childElements.append("\n").append(childIndentation)
                                    .append("<").append(childElement).append(">")
                                    .append(getSampleValue(childElement))
                                    .append("</").append(childElement).append(">");
                        }
                        childElements.append("\n").append(indentation);

                        codeArea.insertText(insertPos, childElements + closingTag);
                        codeArea.moveTo(insertPos + childElements.toString().indexOf(getSampleValue(mandatoryChildren.get(0))));
                    } else {
                        codeArea.insertText(insertPos, closingTag);
                        codeArea.moveTo(insertPos); // Position cursor between tags
                    }
                }
            } else {
                codeArea.insertText(popupStartPosition, completion);
            }
        }
    }

    private boolean isSelfClosingElement(String elementName) {
        // Common self-closing XML elements
        return java.util.Set.of("br", "hr", "img", "input", "meta", "link", "area", "base",
                "col", "embed", "source", "track", "wbr").contains(elementName.toLowerCase());
    }

    private void hideIntelliSensePopup() {
        if (intelliSensePopup != null && intelliSensePopup.isShowing()) {
            intelliSensePopup.hide();
        }
        popupStartPosition = -1;
        isElementCompletionContext = false;
    }

    private void hideAllIntelliSensePopups() {
        // Hide legacy popup
        hideIntelliSensePopup();

        // Hide enhanced popup
        if (enhancedIntelliSensePopup != null && enhancedIntelliSensePopup.isShowing()) {
            enhancedIntelliSensePopup.hide();
        }

        popupStartPosition = -1;
        isElementCompletionContext = false;
    }

    private boolean shouldShowElementCompletion() {
        int caretPos = codeArea.getCaretPosition();
        if (caretPos > 0) {
            String textBeforeCaret = codeArea.getText().substring(0, caretPos);
            // Check if we just typed '<' and are in a position where element completion makes sense
            return textBeforeCaret.endsWith("<") && !isInComment(textBeforeCaret) && !isInCData(textBeforeCaret);
        }
        return false;
    }

    private boolean isInComment(String text) {
        int lastCommentStart = text.lastIndexOf("<!--");
        int lastCommentEnd = text.lastIndexOf("-->");
        return lastCommentStart > lastCommentEnd;
    }

    private boolean isInCData(String text) {
        int lastCDataStart = text.lastIndexOf("<![CDATA[");
        int lastCDataEnd = text.lastIndexOf("]]>");
        return lastCDataStart > lastCDataEnd;
    }

    private boolean isInAttributeContext() {
        int caretPos = codeArea.getCaretPosition();
        if (caretPos > 0) {
            String textBeforeCaret = codeArea.getText().substring(0, caretPos);
            // Simple check: are we inside an opening tag?
            int lastOpenBracket = textBeforeCaret.lastIndexOf('<');
            int lastCloseBracket = textBeforeCaret.lastIndexOf('>');
            return lastOpenBracket > lastCloseBracket;
        }
        return false;
    }

    private boolean shouldShowCompletion(char c) {
        return c == '<' || (c == ' ' && isInAttributeContext());
    }

    private void showIntelliSenseCompletion() {
        if (enhancedIntelliSensePopup == null) {
            logger.warn("Enhanced IntelliSense popup not initialized");
            return;
        }

        int caretPos = codeArea.getCaretPosition();
        String textBeforeCaret = codeArea.getText().substring(0, caretPos);

        logger.info("🚀 INTELLISENSE: Showing completion at position {}, text before: '{}'", caretPos,
                textBeforeCaret.length() > 20 ? textBeforeCaret.substring(Math.max(0, textBeforeCaret.length() - 20)) : textBeforeCaret);

        // Determine what completions to show based on context
        List<CompletionItem> completions = new ArrayList<>();

        if (textBeforeCaret.endsWith("<")) {
            // Element completion - get context-sensitive suggestions
            isElementCompletionContext = true;
            popupStartPosition = caretPos;
            completions.addAll(getContextSensitiveElementCompletions(textBeforeCaret));
            logger.info("🔍 INTELLISENSE: Element context - found {} completions", completions.size());
        } else if (isInAttributeContext()) {
            // Attribute completion based on current element
            isElementCompletionContext = false;
            popupStartPosition = getWordStartPosition(textBeforeCaret, caretPos);
            String currentElement = getCurrentElementName(textBeforeCaret);
            completions.addAll(getAttributeCompletions(currentElement));
            logger.debug("Attribute context for element '{}': found {} completions", currentElement, completions.size());
        }

        if (!completions.isEmpty()) {
            // Show enhanced popup with rich completion items
            logger.info("✨ INTELLISENSE: Showing enhanced popup with {} completions", completions.size());
            showEnhancedIntelliSensePopup(completions);
        } else {
            // Strict mode: only show XSD-valid options; if none, show nothing
            logger.info("ℹ️ INTELLISENSE: No XSD-valid completions at this position.");
        }
    }

    private void showLegacyIntelliSenseCompletion() {
        // Legacy implementation for backward compatibility
        int caretPos = codeArea.getCaretPosition();
        String textBeforeCaret = codeArea.getText().substring(0, caretPos);

        List<String> completions = new ArrayList<>();

        if (textBeforeCaret.endsWith("<")) {
            isElementCompletionContext = true;
            popupStartPosition = caretPos;
            completions.addAll(availableElementNames);
        } else if (isInAttributeContext()) {
            isElementCompletionContext = false;
            popupStartPosition = getWordStartPosition(textBeforeCaret, caretPos);
            completions.addAll(java.util.List.of("id", "class", "name", "value", "type"));
        }

        if (!completions.isEmpty()) {
            updateCompletionList(completions);
            showIntelliSensePopupAtCaret();
        }
    }

    private int getWordStartPosition(String textBeforeCaret, int caretPos) {
        int pos = caretPos - 1;
        while (pos >= 0 && Character.isLetterOrDigit(textBeforeCaret.charAt(pos))) {
            pos--;
        }
        return pos + 1;
    }

    private void updateIntelliSenseCompletion() {
        if (intelliSensePopup == null || !intelliSensePopup.isShowing() || popupStartPosition < 0) {
            return;
        }

        int caretPos = codeArea.getCaretPosition();
        if (caretPos < popupStartPosition) {
            hideIntelliSensePopup();
            return;
        }

        String typedText = codeArea.getText().substring(popupStartPosition, caretPos);

        // Filter completions based on typed text
        List<String> allCompletions = isElementCompletionContext ?
                new ArrayList<>(availableElementNames) :
                java.util.List.of("id", "class", "name", "value", "type");

        List<String> filteredCompletions = allCompletions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(typedText.toLowerCase()))
                .collect(java.util.stream.Collectors.toList());

        if (filteredCompletions.isEmpty()) {
            hideIntelliSensePopup();
        } else {
            updateCompletionList(filteredCompletions);
        }
    }

    private void updateCompletionList(List<String> completions) {
        if (completionListView != null) {
            completionListView.getItems().clear();
            completionListView.getItems().addAll(completions);
            if (!completions.isEmpty()) {
                completionListView.getSelectionModel().select(0);
            }
        }
    }

    private void showIntelliSensePopupAtCaret() {
        if (intelliSensePopup != null && codeArea.getScene() != null && codeArea.getScene().getWindow() != null) {
            var bounds = codeArea.getCaretBounds();
            if (bounds.isPresent()) {
                var caretBounds = bounds.get();
                var screenBounds = codeArea.localToScreen(caretBounds);
                intelliSensePopup.show(codeArea.getScene().getWindow(),
                        screenBounds.getMinX(),
                        screenBounds.getMaxY() + 2);
            }
        }
    }

    /**
     * Updates the XSD integration with current documentation data.
     * This method can be called when XSD data changes.
     */
    public void refreshXsdIntegration() {
        updateXsdIntegration();
    }

    private void updateXsdIntegration() {
        // Initialize XSD integration if available
        try {
            if (xsdIntegration == null) {
                xsdIntegration = new org.fxt.freexmltoolkit.controls.intellisense.XsdIntegrationAdapter();
                logger.debug("XSD integration adapter initialized");
            }

            // Connect with XSD documentation data from parent XmlEditor
            if (parentXmlEditor != null) {
                var xsdDocumentationData = parentXmlEditor.getXsdDocumentationData();
                if (xsdDocumentationData != null) {
                    xsdIntegration.setXsdDocumentationData(xsdDocumentationData);
                    logger.debug("XSD integration connected with documentation data: {} elements",
                            xsdDocumentationData.getExtendedXsdElementMap().size());
                } else {
                    logger.debug("No XSD documentation data available from parent editor");
                }
            } else {
                logger.debug("No parent XML editor available for XSD integration");
            }
            // This would be called when an XSD is associated with the XML

        } catch (Exception e) {
            logger.debug("XSD integration setup failed: {}", e.getMessage());
        }
    }
    
    /**
     * Gets mandatory child elements for a given element name based on XSD schema.
     */
    private List<String> getMandatoryChildElements(String elementName) {
        // Check if we have context element names mapping (from XSD)
        if (contextElementNames.containsKey(elementName)) {
            return contextElementNames.get(elementName);
        }

        // Fallback to some common patterns for demonstration
        return switch (elementName.toLowerCase()) {
            case "person" -> java.util.List.of("name", "age");
            case "book" -> java.util.List.of("title", "author");
            case "product" -> java.util.List.of("name", "price");
            case "order" -> java.util.List.of("id", "date", "items");
            case "address" -> java.util.List.of("street", "city", "zipcode");
            case "contact" -> java.util.List.of("name", "email");
            default -> java.util.List.of(); // No mandatory children by default
        };
    }

    /**
     * Gets a sample value for an element based on its name.
     */
    private String getSampleValue(String elementName) {
        return switch (elementName.toLowerCase()) {
            case "name" -> "Sample Name";
            case "title" -> "Sample Title";
            case "age" -> "25";
            case "price" -> "19.99";
            case "id" -> "001";
            case "date" -> "2024-01-01";
            case "author" -> "Sample Author";
            case "email" -> "example@domain.com";
            case "street" -> "123 Main St";
            case "city" -> "Sample City";
            case "zipcode" -> "12345";
            case "items" -> ""; // Container elements typically empty
            default -> ""; // Default empty for unknown elements
        };
    }

    /**
     * Gets the current indentation level at the cursor position.
     */
    private String getCurrentIndentation() {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();

        // Find the start of the current line
        int lineStart = text.lastIndexOf('\n', caretPos - 1) + 1;
        int lineEnd = text.indexOf('\n', caretPos);
        if (lineEnd == -1) lineEnd = text.length();

        String currentLine = text.substring(lineStart, lineEnd);
        return extractIndentation(currentLine);
    }

    // ================================================================================
    // XSD-based Context-Sensitive IntelliSense Methods
    // ================================================================================

    /**
     * Gets context-sensitive element completions based on XSD schema and current XML position.
     */
    private List<CompletionItem> getContextSensitiveElementCompletions(String textBeforeCaret) {
        List<CompletionItem> completions = new ArrayList<>();

        // Determine current XML context
        String currentXPath = getCurrentXPath(textBeforeCaret);
        String parentElement = getParentElementName(textBeforeCaret);

        logger.debug("Getting completions for XPath: {}, Parent: {}", currentXPath, parentElement);

        // Get allowed child elements from XSD context or fallback patterns
        List<String> allowedChildren = getAllowedChildElements(parentElement, currentXPath);

        // Convert to CompletionItems with rich information
        int index = 0; // Preserve XSD order with index-based relevance
        for (String elementName : allowedChildren) {
            CompletionItem.Builder builder = new CompletionItem.Builder(
                    elementName,
                    elementName,
                    CompletionItemType.ELEMENT
            );

            // Add XSD documentation if available
            String description = getElementDescription(elementName, parentElement);
            if (description != null && !description.isEmpty()) {
                builder.description(description);
            }

            // Add data type information
            String dataType = getElementDataType(elementName, parentElement);
            if (dataType != null && !dataType.isEmpty()) {
                builder.dataType(dataType);
            }

            // Check if element is required
            boolean isRequired = isElementRequired(elementName, parentElement);
            builder.required(isRequired);

            // Set relevance score (required elements get higher score, preserve XSD order with index)
            int baseScore = isRequired ? 150 : 100;
            builder.relevanceScore(baseScore + (1000 - index)); // Higher score for earlier XSD elements

            // Add mandatory children information
            List<String> mandatoryChildren = getMandatoryChildElements(elementName);
            if (!mandatoryChildren.isEmpty()) {
                builder.requiredAttributes(mandatoryChildren);
            }

            completions.add(builder.build());
            index++; // Increment for next element
        }

        // Sort by relevance (required first, then preserve XSD order)
        // DO NOT sort alphabetically - XSD-based completions are already in correct schema order
        completions.sort((a, b) -> {
            if (a.isRequired() != b.isRequired()) {
                return a.isRequired() ? -1 : 1;
            }
            // Preserve original XSD order by using relevance score (higher = better)
            return Integer.compare(b.getRelevanceScore(), a.getRelevanceScore());
        });

        return completions;
    }

    /**
     * Gets attribute completions for the current element.
     */
    private List<CompletionItem> getAttributeCompletions(String elementName) {
        List<CompletionItem> completions = new ArrayList<>();

        if (elementName == null || elementName.isEmpty()) {
            return completions;
        }

        // Get allowed attributes from XSD or fallback patterns
        List<String> allowedAttributes = getAllowedAttributes(elementName);

        for (String attrName : allowedAttributes) {
            CompletionItem.Builder builder = new CompletionItem.Builder(
                    attrName + "=\"\"",
                    attrName + "=\"\"",
                    CompletionItemType.ATTRIBUTE
            );

            builder.description("Attribute: " + attrName);

            // Add data type if known
            String attrType = getAttributeDataType(elementName, attrName);
            if (attrType != null) {
                builder.dataType(attrType);
            }

            // Check if attribute is required
            boolean isRequired = isAttributeRequired(elementName, attrName);
            builder.required(isRequired);
            builder.relevanceScore(isRequired ? 150 : 100);

            completions.add(builder.build());
        }

        // Sort by relevance
        completions.sort((a, b) -> {
            if (a.isRequired() != b.isRequired()) {
                return a.isRequired() ? -1 : 1;
            }
            // Preserve original XSD order by using relevance score (higher = better)
            return Integer.compare(b.getRelevanceScore(), a.getRelevanceScore());
        });

        return completions;
    }

    /**
     * Gets the current XPath based on the XML structure at the caret position.
     */
    private String getCurrentXPath(String textBeforeCaret) {
        // Simple XPath calculation - can be enhanced with proper XML parsing
        StringBuilder xpath = new StringBuilder("/");

        // Find all opening tags that haven't been closed
        java.util.regex.Pattern openTagPattern = java.util.regex.Pattern.compile("<([a-zA-Z][a-zA-Z0-9_-]*)[^/>]*(?<!/)>");
        java.util.regex.Pattern closeTagPattern = java.util.regex.Pattern.compile("</([a-zA-Z][a-zA-Z0-9_-]*)>");

        java.util.regex.Matcher openMatcher = openTagPattern.matcher(textBeforeCaret);
        java.util.regex.Matcher closeMatcher = closeTagPattern.matcher(textBeforeCaret);

        java.util.Stack<String> elementStack = new java.util.Stack<>();

        // Simple approach: find all tags and build stack
        int pos = 0;
        while (pos < textBeforeCaret.length()) {
            int nextOpen = textBeforeCaret.indexOf('<', pos);
            if (nextOpen == -1) break;

            int nextClose = textBeforeCaret.indexOf('>', nextOpen);
            if (nextClose == -1) break;

            String tag = textBeforeCaret.substring(nextOpen + 1, nextClose);
            if (tag.startsWith("/")) {
                // Closing tag
                String elementName = tag.substring(1);
                if (!elementStack.isEmpty() && elementStack.peek().equals(elementName)) {
                    elementStack.pop();
                }
            } else if (!tag.endsWith("/") && !tag.startsWith("?") && !tag.startsWith("!")) {
                // Opening tag
                String elementName = tag.split("\\s+")[0];
                elementStack.push(elementName);
            }

            pos = nextClose + 1;
        }

        // Build XPath from stack
        for (String element : elementStack) {
            xpath.append(element).append("/");
        }

        return xpath.toString();
    }

    /**
     * Gets the parent element name from the current context.
     */
    private String getParentElementName(String textBeforeCaret) {
        // Find the last unclosed opening tag
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<([a-zA-Z][a-zA-Z0-9_-]*)[^/>]*(?<!/)>");
        java.util.regex.Matcher matcher = pattern.matcher(textBeforeCaret);

        java.util.Stack<String> elementStack = new java.util.Stack<>();

        while (matcher.find()) {
            elementStack.push(matcher.group(1));
        }

        // Remove closed elements
        java.util.regex.Pattern closePattern = java.util.regex.Pattern.compile("</([a-zA-Z][a-zA-Z0-9_-]*)>");
        java.util.regex.Matcher closeMatcher = closePattern.matcher(textBeforeCaret);

        while (closeMatcher.find()) {
            String closedElement = closeMatcher.group(1);
            if (!elementStack.isEmpty() && elementStack.peek().equals(closedElement)) {
                elementStack.pop();
            }
        }

        return elementStack.isEmpty() ? null : elementStack.peek();
    }

    /**
     * Gets the current element name from attribute context.
     */
    private String getCurrentElementName(String textBeforeCaret) {
        // Find the opening bracket of the current tag
        int lastOpenBracket = textBeforeCaret.lastIndexOf('<');
        if (lastOpenBracket == -1) return null;

        // Extract tag name
        String tagContent = textBeforeCaret.substring(lastOpenBracket + 1);
        String[] parts = tagContent.split("\\s+");
        return parts.length > 0 ? parts[0] : null;
    }

    private void updateEnumerationElementsCache() {
        // Placeholder for enumeration cache update
    }

    // ================================================================================
    // XSD Integration Helper Methods
    // ================================================================================

    /**
     * Gets allowed child elements for a parent element based on XSD or context mapping.
     */
    private List<String> getAllowedChildElements(String parentElement, String currentXPath) {
        // Strict mode: use XSD documentation data (same source as sidebar) first
        try {
            if (parentXmlEditor != null && parentXmlEditor.getXsdDocumentationData() != null) {
                var xsdDocData = parentXmlEditor.getXsdDocumentationData();
                // Normalize current XPath to the parent element path (remove trailing slash)
                String parentPath = currentXPath;
                if (parentPath != null && parentPath.endsWith("/")) {
                    parentPath = parentPath.substring(0, parentPath.length() - 1);
                }

                // Lookup exact element first, then fallback to best matching
                org.fxt.freexmltoolkit.domain.XsdExtendedElement elementInfo =
                        xsdDocData.getExtendedXsdElementMap().get(parentPath);
                if (elementInfo == null) {
                    elementInfo = parentXmlEditor.findBestMatchingElement(parentPath);
                }

                if (elementInfo != null && elementInfo.getChildren() != null) {
                    java.util.List<String> childNames = new java.util.ArrayList<>();
                    for (String childXpath : elementInfo.getChildren()) {
                        var child = xsdDocData.getExtendedXsdElementMap().get(childXpath);
                        if (child != null && child.getElementName() != null) {
                            if (!childNames.contains(child.getElementName())) {
                                childNames.add(child.getElementName());
                            }
                        }
                    }
                    logger.debug("🎯 Sidebar-aligned XSD children for '{}': {}", parentPath, childNames);
                    return childNames;
                }
            }
        } catch (Exception e) {
            logger.warn("❌ Error resolving XSD children from documentation data: {}", e.getMessage(), e);
        }

        // Fallback: use adapter by parent element name only (still strict, no pattern-based fallbacks)
        logger.debug("🔍 XSD Integration status: integration={}, hasSchema={}",
                xsdIntegration != null ? "available" : "null",
                xsdIntegration != null ? xsdIntegration.hasSchema() : "n/a");
        if (xsdIntegration != null && xsdIntegration.hasSchema()) {
            try {
                List<String> xsdChildren = xsdIntegration.getAvailableElements(parentElement);
                logger.debug("🎯 Adapter XSD children for parent '{}': {}", parentElement, xsdChildren);
                return xsdChildren;
            } catch (Exception e) {
                logger.warn("❌ XSD integration error: {}", e.getMessage(), e);
            }
        }

        logger.debug("❌ No XSD-derived children available for context '{}'/parent '{}'", currentXPath, parentElement);
        return java.util.List.of();
    }

    /**
     * Gets pattern-based child elements for common XML structures.
     */
    private List<String> getPatternBasedChildren(String parentElement) {
        return switch (parentElement.toLowerCase()) {
            case "root", "document" -> java.util.List.of("header", "body", "metadata", "content", "items");
            case "header" -> java.util.List.of("title", "author", "date", "version", "description");
            case "body", "content" -> java.util.List.of("section", "paragraph", "item", "element", "entry");
            case "metadata" -> java.util.List.of("property", "attribute", "key", "value", "meta");
            case "items", "list" -> java.util.List.of("item", "entry", "element", "object");
            case "person", "contact" -> java.util.List.of("name", "email", "phone", "address", "id");
            case "address" -> java.util.List.of("street", "city", "zipcode", "country", "state");
            case "product" -> java.util.List.of("name", "price", "description", "category", "sku", "stock");
            case "order" -> java.util.List.of("id", "date", "customer", "items", "total", "status");
            case "customer" -> java.util.List.of("id", "name", "email", "address", "phone");
            case "book" -> java.util.List.of("title", "author", "isbn", "publisher", "year", "pages");
            case "config", "settings" -> java.util.List.of("property", "option", "parameter", "value");
            default -> java.util.List.of("value", "text", "data", "property", "item");
        };
    }

    /**
     * Gets allowed attributes for an element.
     */
    private List<String> getAllowedAttributes(String elementName) {
        if (elementName == null) return java.util.List.of();

        // Pattern-based attribute suggestions
        List<String> commonAttrs = java.util.List.of("id", "class", "name");
        List<String> specificAttrs = getElementSpecificAttributes(elementName);

        List<String> allAttributes = new ArrayList<>(commonAttrs);
        allAttributes.addAll(specificAttrs);

        return allAttributes;
    }

    /**
     * Gets element-specific attributes.
     */
    private List<String> getElementSpecificAttributes(String elementName) {
        return switch (elementName.toLowerCase()) {
            case "item", "entry" -> java.util.List.of("type", "value", "index");
            case "product" -> java.util.List.of("sku", "category", "price", "currency");
            case "person", "contact" -> java.util.List.of("type", "role", "active");
            case "book" -> java.util.List.of("isbn", "format", "language");
            case "order" -> java.util.List.of("status", "priority", "date");
            case "image", "img" -> java.util.List.of("src", "alt", "width", "height");
            case "link", "a" -> java.util.List.of("href", "target", "rel");
            default -> java.util.List.of("type", "value");
        };
    }

    /**
     * Gets element description from XSD documentation.
     */
    private String getElementDescription(String elementName, String parentElement) {
        // Pattern-based descriptions for common elements
        return switch (elementName.toLowerCase()) {
            case "name" -> "The name or title of the " + (parentElement != null ? parentElement : "element");
            case "id" -> "Unique identifier for this " + (parentElement != null ? parentElement : "element");
            case "title" -> "The title or heading text";
            case "description" -> "Detailed description or summary";
            case "date" -> "Date value in ISO format (YYYY-MM-DD)";
            case "email" -> "Email address in valid format";
            case "phone" -> "Phone number";
            case "address" -> "Physical address information";
            case "price" -> "Monetary value or price";
            case "quantity", "stock" -> "Numeric quantity or count";
            case "status" -> "Current status or state";
            default -> "XML element: " + elementName;
        };
    }

    /**
     * Gets data type information for an element.
     */
    private String getElementDataType(String elementName, String parentElement) {
        return switch (elementName.toLowerCase()) {
            case "id", "count", "quantity", "stock", "pages" -> "xs:int";
            case "price", "total", "amount" -> "xs:decimal";
            case "date", "created", "updated" -> "xs:date";
            case "email" -> "xs:string (email format)";
            case "phone" -> "xs:string (phone format)";
            case "active", "enabled", "visible" -> "xs:boolean";
            case "url", "link", "href" -> "xs:anyURI";
            default -> "xs:string";
        };
    }

    /**
     * Checks if an element is required in the current context.
     */
    private boolean isElementRequired(String elementName, String parentElement) {
        if (parentElement == null) return false;

        // Pattern-based required elements
        return switch (parentElement.toLowerCase()) {
            case "person", "contact" -> elementName.equals("name");
            case "book" -> java.util.List.of("title", "author").contains(elementName);
            case "product" -> java.util.List.of("name", "price").contains(elementName);
            case "order" -> java.util.List.of("id", "date").contains(elementName);
            case "address" -> java.util.List.of("street", "city").contains(elementName);
            default -> false;
        };
    }

    /**
     * Gets attribute data type.
     */
    private String getAttributeDataType(String elementName, String attrName) {
        return switch (attrName.toLowerCase()) {
            case "id", "index", "count" -> "xs:int";
            case "price", "amount" -> "xs:decimal";
            case "date" -> "xs:date";
            case "active", "enabled" -> "xs:boolean";
            case "href", "src", "url" -> "xs:anyURI";
            default -> "xs:string";
        };
    }

    /**
     * Checks if an attribute is required.
     */
    private boolean isAttributeRequired(String elementName, String attrName) {
        return switch (elementName.toLowerCase()) {
            case "img", "image" -> attrName.equals("src");
            case "link", "a" -> attrName.equals("href");
            case "product" -> attrName.equals("sku");
            default -> false;
        };
    }

    /**
     * Shows the enhanced IntelliSense popup with rich completion items.
     */
    private void showEnhancedIntelliSensePopup(List<CompletionItem> completions) {
        if (enhancedIntelliSensePopup != null && !completions.isEmpty()) {
            // Position and show popup with completion items
            if (codeArea.getScene() != null && codeArea.getScene().getWindow() != null) {
                var bounds = codeArea.getCaretBounds();
                if (bounds.isPresent()) {
                    var caretBounds = bounds.get();
                    var screenBounds = codeArea.localToScreen(caretBounds);

                    // Use the show method with screen coordinates and completion items
                    enhancedIntelliSensePopup.show(screenBounds.getMinX(),
                            screenBounds.getMaxY() + 2,
                            completions,
                            getCurrentXmlContext());

                    logger.debug("Enhanced IntelliSense popup shown with {} items", completions.size());
                }
            }
        }
    }

    /**
     * Gets the current XML context for the enhanced popup.
     */
    private Object getCurrentXmlContext() {
        // Return a simple context object containing current state
        return new Object() {
            @Override
            public String toString() {
                return "XmlEditor Context - Position: " + codeArea.getCaretPosition();
            }
        };
    }

    /**
     * Handles completion item selection from enhanced popup.
     */
    private void insertEnhancedCompletion(CompletionItem item) {
        if (item == null || popupStartPosition < 0) return;

        // Remove any partially typed text
        int currentPos = codeArea.getCaretPosition();
        if (currentPos > popupStartPosition) {
            codeArea.deleteText(popupStartPosition, currentPos);
        }

        // Insert the completion text
        String insertText = item.getInsertText();

        if (isElementCompletionContext && item.getType() == CompletionItemType.ELEMENT) {
            // Handle element completion with auto-closing and mandatory children
            insertElementCompletion(item);
        } else {
            // Simple text insertion
            codeArea.insertText(popupStartPosition, insertText);

            // Position cursor appropriately
            if (insertText.contains("\"\"")) {
                int quotePos = popupStartPosition + insertText.indexOf("\"\"") + 1;
                codeArea.moveTo(quotePos);
            } else {
                codeArea.moveTo(popupStartPosition + insertText.length());
            }
        }

        // Hide popup
        if (enhancedIntelliSensePopup != null) {
            enhancedIntelliSensePopup.hide();
        }

        popupStartPosition = -1;
        isElementCompletionContext = false;

        logger.debug("Inserted enhanced completion: {}", item.getLabel());
    }

    /**
     * Inserts element completion with auto-closing tags and mandatory children.
     */
    private void insertElementCompletion(CompletionItem item) {
        String elementName = item.getLabel();
        int insertPos = popupStartPosition;

        // Insert opening tag
        codeArea.insertText(insertPos, elementName + ">");
        insertPos += elementName.length() + 1;

        // Add closing tag and mandatory children if not self-closing
        if (!isSelfClosingElement(elementName)) {
            String closingTag = "</" + elementName + ">";

            // Check for mandatory children from the CompletionItem
            List<String> mandatoryChildren = item.getRequiredAttributes(); // Reusing this field for child elements
            if (mandatoryChildren != null && !mandatoryChildren.isEmpty()) {
                StringBuilder childElements = new StringBuilder();
                String indentation = getCurrentIndentation();
                String childIndentation = indentation + " ".repeat(propertiesService.getXmlIndentSpaces());

                for (String childElement : mandatoryChildren) {
                    childElements.append("\n").append(childIndentation)
                            .append("<").append(childElement).append(">")
                            .append(getSampleValue(childElement))
                            .append("</").append(childElement).append(">");
                }
                childElements.append("\n").append(indentation);

                codeArea.insertText(insertPos, childElements + closingTag);

                // Position cursor on first sample value
                if (!mandatoryChildren.isEmpty()) {
                    String firstChild = mandatoryChildren.get(0);
                    String sampleValue = getSampleValue(firstChild);
                    if (!sampleValue.isEmpty()) {
                        int samplePos = insertPos + childElements.toString().indexOf(sampleValue);
                        codeArea.selectRange(samplePos, samplePos + sampleValue.length());
                    }
                }
            } else {
                codeArea.insertText(insertPos, closingTag);
                codeArea.moveTo(insertPos); // Position cursor between tags
            }
        }
    }

    private void disableAllAutoCompletion() {
        if (schematronAutoComplete != null) schematronAutoComplete.setEnabled(false);
        if (xsdAutoComplete != null) xsdAutoComplete.setEnabled(false);
        if (xsltAutoComplete != null) xsltAutoComplete.setEnabled(false);
        if (xslFoAutoComplete != null) xslFoAutoComplete.setEnabled(false);
    }

    private void autoDetectEditorMode(String content) {
        // Placeholder for mode detection
    }

    // Context menu action implementations
    private void toggleLineComment() {
        // Get current selection or current line
        if (codeArea.getSelectedText().isEmpty()) {
            // Comment/uncomment current line
            int paragraphIndex = codeArea.getCurrentParagraph();
            String line = codeArea.getParagraph(paragraphIndex).getText();

            // Get line start and end positions
            int lineStart = codeArea.getAbsolutePosition(paragraphIndex, 0);
            int lineEnd = lineStart + line.length();

            if (line.trim().startsWith("<!--") && line.trim().endsWith("-->")) {
                // Uncomment
                String uncommented = line.replaceFirst("<!--\\s*", "").replaceFirst("\\s*-->", "");
                codeArea.replaceText(lineStart, lineEnd, uncommented);
            } else {
                // Comment
                String commented = "<!-- " + line + " -->";
                codeArea.replaceText(lineStart, lineEnd, commented);
            }
        } else {
            // Comment/uncomment selection
            String selectedText = codeArea.getSelectedText();
            if (selectedText.trim().startsWith("<!--") && selectedText.trim().endsWith("-->")) {
                // Uncomment
                String uncommented = selectedText.replaceFirst("<!--\\s*", "").replaceFirst("\\s*-->", "");
                codeArea.replaceSelection(uncommented);
            } else {
                // Comment
                String commented = "<!-- " + selectedText + " -->";
                codeArea.replaceSelection(commented);
            }
        }
    }
    
    private void cutTextToClipboard() {
        String selectedText = codeArea.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(selectedText);
            clipboard.setContent(content);
            codeArea.replaceSelection("");
        }
    }
    
    private void copyTextToClipboard() {
        String selectedText = codeArea.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(selectedText);
            clipboard.setContent(content);
        }
    }
    
    private void pasteTextFromClipboard() {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            String clipboardText = clipboard.getString();
            codeArea.replaceSelection(clipboardText);
        }
    }

    private void copyXPathToClipboard() {
        // Placeholder
    }

    private void handleGoToDefinition(MouseEvent event) {
        // Placeholder
    }

    private void selectAll() {
        codeArea.selectAll();
    }
    
    private void openFindReplace() {
        // Placeholder
    }

    private void formatXmlContent() {
        // Placeholder
    }

    private void validateXmlContent() {
        // Placeholder
    }

    private void expandAll() {
        // Placeholder
    }

    private void collapseAll() {
        // Placeholder
    }

    private String getElementNameAtPosition(double x, double y) {
        // Placeholder
        return null;
    }

    /**
     * Information about element text content.
     */
    private record ElementTextInfo(String elementName, String textContent, int startPosition, int endPosition) {
    }

    // ================================================================================
    // Missing methods for backward compatibility
    // ================================================================================

    /**
     * Static method for computing syntax highlighting.
     * Delegate to the manager for backward compatibility.
     */
    public static org.fxmisc.richtext.model.StyleSpans<Collection<String>> computeHighlighting(String text) {
        // Use the static method from SyntaxHighlightManager
        return SyntaxHighlightManager.computeHighlighting(text);
    }

    /**
     * Toggles minimap visibility.
     */
    public void toggleMinimap() {
        minimapVisible = !minimapVisible;
        updateMinimapVisibility();
    }

    private void updateMinimapVisibility() {
        if (minimapVisible && minimapView == null) {
            minimapView = new MinimapView(codeArea, virtualizedScrollPane, new HashMap<>());
            if (editorContainer.getChildren().size() == 1) {
                editorContainer.getChildren().add(minimapView);
            }
        } else if (!minimapVisible && minimapView != null) {
            editorContainer.getChildren().remove(minimapView);
        }

        if (minimapView != null) {
            minimapView.setVisible(minimapVisible);
        }
    }

    /**
     * Checks if minimap is currently visible.
     */
    public boolean isMinimapVisible() {
        return minimapVisible;
    }

    /**
     * Sets Schematron mode for the editor.
     */
    public void setSchematronMode(boolean enabled) {
        if (enabled) {
            setEditorMode(EditorMode.SCHEMATRON);
        } else {
            setEditorMode(EditorMode.XML_WITHOUT_XSD);
        }
    }

    /**
     * Checks if Schematron mode is active.
     */
    public boolean isSchematronMode() {
        return currentMode == EditorMode.SCHEMATRON;
    }

    /**
     * Gets Schematron auto-complete instance.
     */
    public SchematronAutoComplete getSchematronAutoComplete() {
        if (schematronAutoComplete == null) {
            schematronAutoComplete = new SchematronAutoComplete(codeArea);
        }
        return schematronAutoComplete;
    }

    /**
     * Sets available element names for IntelliSense.
     */
    public void setAvailableElementNames(List<String> elementNames) {
        this.availableElementNames = new ArrayList<>(elementNames);
    }

    /**
     * Sets context element names for IntelliSense.
     */
    public void setContextElementNames(Map<String, List<String>> contextElementNames) {
        this.contextElementNames = new HashMap<>(contextElementNames);
    }

    /**
     * Tests syntax highlighting functionality.
     */
    public void testSyntaxHighlighting() {
        // Test syntax highlighting functionality
        logger.debug("Testing syntax highlighting functionality");
        String sampleXml = "<?xml version=\"1.0\"?><root><element>test</element></root>";
        syntaxHighlightManager.applySyntaxHighlighting(sampleXml);
    }

    /**
     * Debugs CSS status for troubleshooting.
     */
    public void debugCssStatus() {
        // Implementation would go here - for now just log
        logger.debug("CSS status debug requested");
    }

    /**
     * Finds text in the editor.
     */
    public void find(String searchText, boolean forward) {
        // Basic find implementation
        String content = codeArea.getText();
        int currentPos = codeArea.getCaretPosition();
        int foundPos = forward ? content.indexOf(searchText, currentPos) : content.lastIndexOf(searchText, currentPos - 1);

        if (foundPos >= 0) {
            codeArea.selectRange(foundPos, foundPos + searchText.length());
        }
    }

    /**
     * Replaces current selection or next occurrence.
     */
    public void replace(String findText, String replaceText) {
        if (codeArea.getSelection().getLength() > 0) {
            codeArea.replaceSelection(replaceText);
        } else {
            find(findText, true);
            if (codeArea.getSelection().getLength() > 0) {
                codeArea.replaceSelection(replaceText);
            }
        }
    }
    
    /**
     * Replaces all occurrences of text.
     */
    public void replaceAll(String findText, String replaceText) {
        String content = codeArea.getText();
        String newContent = content.replace(findText, replaceText);
        codeArea.replaceText(newContent);
    }

    /**
     * Checks if XSD schema is available for validation and IntelliSense.
     * Used by tests for reflection access.
     */
    private boolean isXsdSchemaAvailable() {
        return parentXmlEditor != null &&
                parentXmlEditor.getXsdFile() != null;
    }

    /**
     * Requests completions for IntelliSense.
     * Used by tests for reflection access.
     */
    private void requestCompletions() {
        showIntelliSenseCompletion();
    }
}