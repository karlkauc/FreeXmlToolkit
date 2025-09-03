package org.fxt.freexmltoolkit.controls;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.controls.intellisense.*;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import java.io.File;
import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * A self-contained XML code editor component that extends VBox.
 * It includes a CodeArea with line numbers, syntax highlighting logic,
 * built-in controls for font size and caret movement, and a status line.
 */
public class XmlCodeEditor extends VBox {

    private static final Logger logger = LogManager.getLogger(XmlCodeEditor.class);
    private static final int DEFAULT_FONT_SIZE = 11;
    private int fontSize = DEFAULT_FONT_SIZE;
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    private final CodeArea codeArea = new CodeArea();
    private final VirtualizedScrollPane<CodeArea> virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);

    // Status line components
    private final HBox statusLine = new HBox();
    private final Label cursorPositionLabel = new Label("Line: 1, Column: 1");
    private final Label encodingLabel = new Label("UTF-8");
    private final Label lineSeparatorLabel = new Label("LF");
    private final Label indentationLabel = new Label();

    // File properties for status line
    private String currentEncoding = "UTF-8";
    private String currentLineSeparator = "LF";
    private int currentIndentationSize;
    private boolean useSpaces = true;

    // Stores start and end lines of foldable regions
    private final Map<Integer, Integer> foldingRegions = new HashMap<>();

    // Stores the state of folded lines manually
    // to avoid issues with the library API.
    private final Set<Integer> foldedLines = new HashSet<>();

    private String documentUri;

    // Reference to parent XmlEditor for accessing schema information
    private XmlEditor parentXmlEditor;

    // IntelliSense Popup Components
    private Stage intelliSensePopup;
    private ListView<String> completionListView;
    private List<String> availableElementNames = new ArrayList<>();
    private Map<String, List<String>> contextElementNames = new HashMap<>();

    // Enumeration completion support
    private ElementTextInfo currentElementTextInfo;

    // Specialized Auto-Completion
    private SchematronAutoComplete schematronAutoComplete;
    private XsdAutoComplete xsdAutoComplete;

    // Editor modes (only one can be active at a time)
    public enum EditorMode {
        XML,        // Standard XML with IntelliSense
        SCHEMATRON, // Schematron-specific auto-completion
        XSD         // XSD-specific auto-completion
    }

    private EditorMode currentMode = EditorMode.XML;

    // Cache for enumeration elements from XsdDocumentationData
    // Key: XPath-like context, Value: Set of element names with enumeration
    private final Map<String, Set<String>> enumerationElementsByContext = new HashMap<>();
    private int popupStartPosition = -1;
    private boolean isElementCompletionContext = false; // Track if we're completing elements or attributes

    // Enhanced IntelliSense Components
    private EnhancedCompletionPopup enhancedCompletionPopup;
    private FuzzySearch fuzzySearch;
    private XsdDocumentationExtractor xsdDocExtractor;
    private AttributeValueHelper attributeValueHelper;
    private CompletionCache completionCache;
    private PerformanceProfiler performanceProfiler;
    private MultiSchemaManager multiSchemaManager;
    private TemplateEngine templateEngine;
    private QuickActionsIntegration quickActionsIntegration;

    // Integration state
    private boolean enhancedIntelliSenseEnabled = true;
    private org.fxt.freexmltoolkit.controls.intellisense.XsdIntegrationAdapter xsdIntegration;
    private XmlIntelliSenseEngine intelliSenseEngine;
    private XmlCodeFoldingManager codeFoldingManager;

    // Debouncing for syntax highlighting
    private javafx.animation.PauseTransition syntaxHighlightingDebouncer;

    // Background task for syntax highlighting
    private javafx.concurrent.Task<StyleSpans<Collection<String>>> syntaxHighlightingTask;

    // Live error highlighting
    private javafx.animation.PauseTransition errorHighlightingDebouncer;
    private javafx.concurrent.Task<List<org.xml.sax.SAXParseException>> validationTask;
    private final Map<Integer, String> currentErrors = new HashMap<>();
    private Tooltip errorTooltip;
    private int lastTooltipLine = -1;

    // Minimap component
    private MinimapView minimapView;
    private HBox editorContainer;
    private boolean minimapVisible = false;
    private boolean minimapInitialized = false;

    // File monitoring for external changes
    private File currentFile;
    private long lastModifiedTime = -1;
    private javafx.animation.Timeline fileMonitorTimer;
    private static final int FILE_MONITOR_INTERVAL_SECONDS = 2;
    private boolean isFileMonitoringEnabled = true;
    private boolean ignoreNextChange = false; // Flag to ignore changes we made ourselves

    // Performance optimization: Cache compiled patterns
    private static final Pattern OPEN_TAG_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)\b[^>]*>");
    private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("</([a-zA-Z][a-zA-Z0-9_:]*) *>");
    private static final Pattern ELEMENT_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)");

    // --- Syntax Highlighting Patterns (moved from XmlEditor) ---
    private static final Pattern XML_TAG = Pattern.compile("(?<ELEMENT>(</?\\h*)(\\w+)([^<>]*)(\\h*/?>))"
            + "|(?<COMMENT><!--[^<>]+-->)");
    private static final Pattern ATTRIBUTES = Pattern.compile("(\\w+\\h*)(=)(\\h*\"[^\"]+\")");

    private static final int GROUP_OPEN_BRACKET = 2;
    private static final int GROUP_ELEMENT_NAME = 3;
    private static final int GROUP_ATTRIBUTES_SECTION = 4;
    private static final int GROUP_CLOSE_BRACKET = 5;
    private static final int GROUP_ATTRIBUTE_NAME = 1;
    private static final int GROUP_EQUAL_SYMBOL = 2;
    private static final int GROUP_ATTRIBUTE_VALUE = 3;

    public XmlCodeEditor() {
        super();
        currentIndentationSize = propertiesService.getXmlIndentSpaces();
        initialize();
    }

    /**
     * Updates the indentation label to show the current configured indent spaces.
     */
    private void updateIndentationLabel() {
        int indentSpaces = propertiesService.getXmlIndentSpaces();
        currentIndentationSize = indentSpaces;
        String indentType = useSpaces ? "spaces" : "tabs";
        indentationLabel.setText(indentSpaces + " " + indentType);
    }

    /**
     * Refreshes the indentation display in the status line.
     * Call this method when the indent settings have been changed.
     */
    public void refreshIndentationDisplay() {
        updateIndentationLabel();
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
            // Use Platform.runLater to avoid blocking the UI thread
            Platform.runLater(() -> updateEnumerationElementsCache());
        }
    }

    /**
     * Sets the available element names for IntelliSense completion.
     *
     * @param elementNames List of available element names
     */
    public void setAvailableElementNames(List<String> elementNames) {
        this.availableElementNames = new ArrayList<>(elementNames);
    }

    /**
     * Sets the context-sensitive element names for IntelliSense completion.
     * This should be a map where the key is the parent element name and the value is a list of child element names.
     *
     * @param contextElementNames Map of parent element names to their child element names
     */
    public void setContextElementNames(Map<String, List<String>> contextElementNames) {
        this.contextElementNames = new HashMap<>(contextElementNames);
    }

    /**
     * Manually triggers enumeration cache update.
     * Call this method when the XSD schema changes.
     */
    public void refreshEnumerationCache() {
        logger.debug("Manual enumeration cache refresh requested");
        Platform.runLater(() -> updateEnumerationElementsCache());
    }

    private void initialize() {
        // Load CSS stylesheets for syntax highlighting
        loadCssStylesheets();
        
        codeArea.setParagraphGraphicFactory(createParagraphGraphicFactory());

        setupEventHandlers();
        initializeIntelliSensePopup();
        initializeEnhancedIntelliSense();
        initializeXmlIntelliSenseEngine();
        initializeCodeFoldingManager();
        initializeSpecializedAutoComplete();

        // Set up the main layout with minimap
        setupLayoutWithMinimap();

        // Set up VBox growth
        VBox.setVgrow(editorContainer, Priority.ALWAYS);

        // Initialize status line
        initializeStatusLine();

        // Set up basic styling and reset font size
        resetFontSize();

        // Apply initial syntax highlighting and folding regions if there's text
        Platform.runLater(() -> {
            if (codeArea.getText() != null && !codeArea.getText().isEmpty()) {
                applySyntaxHighlighting(codeArea.getText());
                updateFoldingRegions(codeArea.getText());
            }
        });

        // Initialize debouncer for syntax highlighting
        syntaxHighlightingDebouncer = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
        syntaxHighlightingDebouncer.setOnFinished(event -> {
            String currentText = codeArea.getText();
            if (currentText != null && !currentText.isEmpty()) {
                applySyntaxHighlighting(currentText);
            }
        });

        // Initialize debouncer for error highlighting
        errorHighlightingDebouncer = new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
        errorHighlightingDebouncer.setOnFinished(event -> {
            String currentText = codeArea.getText();
            if (currentText != null && !currentText.isEmpty()) {
                performLiveValidation(currentText);
            }
        });

        // Text change listener for syntax highlighting with debouncing
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            // Reset the debouncer timer
            syntaxHighlightingDebouncer.stop();
            syntaxHighlightingDebouncer.playFromStart();

            // Reset the error highlighting debouncer timer
            errorHighlightingDebouncer.stop();
            errorHighlightingDebouncer.playFromStart();

            // Handle automatic tag completion
            handleAutomaticTagCompletion(oldText, newText);
        });

        // Add scene change listener to restore syntax highlighting when tab becomes visible
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> {
                    String currentText = codeArea.getText();
                    if (currentText != null && !currentText.isEmpty()) {
                        applySyntaxHighlighting(currentText);
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
                        applySyntaxHighlighting(currentText);
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
                        applySyntaxHighlighting(currentText);
                    }
                });
            }
        });

        // Initialize file monitoring
        initializeFileMonitoring();
    }

    /**
     * Initializes the file monitoring system to detect external changes.
     */
    private void initializeFileMonitoring() {
        // Create timer for checking file modifications
        fileMonitorTimer = new Timeline(new KeyFrame(
            Duration.seconds(FILE_MONITOR_INTERVAL_SECONDS),
            event -> checkForExternalChanges()
        ));
        fileMonitorTimer.setCycleCount(Timeline.INDEFINITE);
        
        logger.debug("File monitoring system initialized");
    }

    /**
     * Sets the current file being monitored and starts monitoring.
     * 
     * @param file The file to monitor, or null to stop monitoring
     */
    public void setCurrentFile(File file) {
        stopFileMonitoring();
        
        this.currentFile = file;
        if (file != null && file.exists()) {
            this.lastModifiedTime = file.lastModified();
            startFileMonitoring();
            logger.debug("Started monitoring file: {}", file.getAbsolutePath());
        } else {
            this.lastModifiedTime = -1;
            logger.debug("File monitoring stopped");
        }
    }

    /**
     * Starts the file monitoring timer.
     */
    private void startFileMonitoring() {
        if (isFileMonitoringEnabled && fileMonitorTimer != null && currentFile != null) {
            fileMonitorTimer.play();
        }
    }

    /**
     * Stops the file monitoring timer.
     */
    private void stopFileMonitoring() {
        if (fileMonitorTimer != null) {
            fileMonitorTimer.stop();
        }
    }

    /**
     * Checks if the current file has been modified externally.
     */
    private void checkForExternalChanges() {
        if (currentFile == null || !currentFile.exists() || ignoreNextChange) {
            if (ignoreNextChange) {
                ignoreNextChange = false; // Reset the flag
            }
            return;
        }

        long currentModifiedTime = currentFile.lastModified();
        if (currentModifiedTime > lastModifiedTime) {
            // File has been modified externally
            Platform.runLater(() -> showExternalChangeDialog(currentModifiedTime));
        }
    }

    /**
     * Shows a dialog asking the user if they want to reload the externally modified file.
     * 
     * @param newModifiedTime The new last modified time of the file
     */
    private void showExternalChangeDialog(long newModifiedTime) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("File Modified Externally");
        alert.setHeaderText("The file has been modified by another program");
        alert.setContentText("The file '" + currentFile.getName() + "' has been changed outside the editor.\n\n" +
                            "Do you want to reload the changes from the file system?");

        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        
        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.YES) {
                reloadFileFromDisk(newModifiedTime);
            } else {
                // User chose not to reload, update the timestamp to avoid repeated dialogs
                lastModifiedTime = newModifiedTime;
                logger.debug("User chose not to reload external changes, updating timestamp");
            }
        });
    }

    /**
     * Reloads the file content from disk.
     * 
     * @param newModifiedTime The new last modified time to set
     */
    private void reloadFileFromDisk(long newModifiedTime) {
        try {
            // Read the new content from the file
            String newContent = java.nio.file.Files.readString(currentFile.toPath(), 
                java.nio.charset.StandardCharsets.UTF_8);
            
            // Set the flag to ignore the next change notification (from our reload)
            ignoreNextChange = true;
            
            // Update the editor content
            Platform.runLater(() -> {
                codeArea.replaceText(newContent);
                lastModifiedTime = newModifiedTime;
                logger.info("Successfully reloaded file from disk: {}", currentFile.getAbsolutePath());
            });
            
        } catch (Exception e) {
            logger.error("Error reloading file from disk: {}", e.getMessage(), e);
            
            Platform.runLater(() -> {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Error Reloading File");
                errorAlert.setHeaderText("Failed to reload file from disk");
                errorAlert.setContentText("An error occurred while reloading the file:\n" + e.getMessage());
                errorAlert.showAndWait();
            });
        }
    }

    /**
     * Enables or disables file monitoring.
     * 
     * @param enabled True to enable monitoring, false to disable
     */
    public void setFileMonitoringEnabled(boolean enabled) {
        this.isFileMonitoringEnabled = enabled;
        if (enabled && currentFile != null) {
            startFileMonitoring();
        } else {
            stopFileMonitoring();
        }
        logger.debug("File monitoring enabled: {}", enabled);
    }

    /**
     * Returns whether file monitoring is currently enabled.
     * 
     * @return True if monitoring is enabled
     */
    public boolean isFileMonitoringEnabled() {
        return isFileMonitoringEnabled;
    }

    /**
     * Should be called when the user saves the file to update the timestamp
     * and avoid triggering the external change dialog for our own save.
     */
    public void notifyFileSaved() {
        if (currentFile != null && currentFile.exists()) {
            lastModifiedTime = currentFile.lastModified();
            logger.debug("File save notification received, updated timestamp");
        }
    }

    /**
     * Handles Ctrl+S keyboard shortcut to save the current file.
     * Saves the content to the current file or triggers Save As dialog if no file is associated.
     */
    private void handleSaveFile() {
        if (parentXmlEditor != null) {
            // Use parent editor's save functionality
            if (!parentXmlEditor.saveFile()) {
                // If save failed (probably no file associated), try Save As
                parentXmlEditor.saveAsFile();
            }
        } else {
            logger.warn("Cannot save: no parent editor available");
        }
    }

    /**
     * Requests the parent editor to show Save As dialog.
     * This is called when Ctrl+Shift+S is pressed.
     */
    private void requestSaveAs() {
        if (parentXmlEditor != null) {
            parentXmlEditor.saveAsFile();
        } else {
            logger.warn("Cannot save as: no parent editor available");
        }
    }

    /**
     * Loads CSS stylesheets for syntax highlighting.
     */
    private void loadCssStylesheets() {
        try {
            // Load the main CSS file for syntax highlighting
            String cssPath = "/css/fxt-theme.css";
            String cssUrl = getClass().getResource(cssPath).toExternalForm();
            codeArea.getStylesheets().add(cssUrl);
            logger.debug("Loaded CSS stylesheet: {}", cssUrl);

            // Also load the XML highlighting specific CSS
            String xmlCssPath = "/scss/xml-highlighting.css";
            if (getClass().getResource(xmlCssPath) != null) {
                String xmlCssUrl = getClass().getResource(xmlCssPath).toExternalForm();
                codeArea.getStylesheets().add(xmlCssUrl);
                logger.debug("Loaded XML highlighting CSS: {}", xmlCssUrl);
            }

            // Load IntelliSense CSS
            String intelliSenseCssPath = "/css/xml-intellisense.css";
            if (getClass().getResource(intelliSenseCssPath) != null) {
                String intelliSenseCssUrl = getClass().getResource(intelliSenseCssPath).toExternalForm();
                codeArea.getStylesheets().add(intelliSenseCssUrl);
                logger.debug("Loaded IntelliSense CSS: {}", intelliSenseCssUrl);
            }

        } catch (Exception e) {
            logger.error("Error loading CSS stylesheets: {}", e.getMessage(), e);
        }
    }

    /**
     * Debug method to check CSS loading status.
     */
    public void debugCssStatus() {
        logger.debug("=== CSS Debug Information ===");
        logger.debug("CodeArea stylesheets count: {}", codeArea.getStylesheets().size());
        for (int i = 0; i < codeArea.getStylesheets().size(); i++) {
            logger.debug("CodeArea stylesheet {}: {}", i, codeArea.getStylesheets().get(i));
        }

        logger.debug("Parent container stylesheets count: {}", this.getStylesheets().size());
        for (int i = 0; i < this.getStylesheets().size(); i++) {
            logger.debug("Parent stylesheet {}: {}", i, this.getStylesheets().get(i));
        }

        if (this.getScene() != null) {
            logger.debug("Scene stylesheets count: {}", this.getScene().getStylesheets().size());
            for (int i = 0; i < this.getScene().getStylesheets().size(); i++) {
                logger.debug("Scene stylesheet {}: {}", i, this.getScene().getStylesheets().get(i));
            }
        }

        logger.debug("Current text: '{}'", codeArea.getText());
        logger.debug("Text length: {}", (codeArea.getText() != null ? codeArea.getText().length() : 0));
        logger.debug("=============================");
    }





    /**
     * Applies syntax highlighting using external CSS only.
     */
    private void applySyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Cancel any running syntax highlighting task
        if (syntaxHighlightingTask != null && syntaxHighlightingTask.isRunning()) {
            syntaxHighlightingTask.cancel();
        }

        // Create new background task for syntax highlighting
        syntaxHighlightingTask = new javafx.concurrent.Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                // Check if task was cancelled
                if (isCancelled()) {
                    return null;
                }

                // Compute syntax highlighting with enumeration in background
                return computeHighlightingWithEnumeration(text);
            }
        };

        syntaxHighlightingTask.setOnSucceeded(event -> {
            StyleSpans<Collection<String>> highlighting = syntaxHighlightingTask.getValue();
            if (highlighting != null) {
                codeArea.setStyleSpans(0, highlighting);
            }
        });

        syntaxHighlightingTask.setOnFailed(event -> {
            logger.error("Syntax highlighting failed", syntaxHighlightingTask.getException());
            // Fallback to basic highlighting
            StyleSpans<Collection<String>> basicHighlighting = computeHighlighting(text);
            codeArea.setStyleSpans(0, basicHighlighting);
        });

        // Run the task in background
        new Thread(syntaxHighlightingTask).start();
    }

    /**
     * Performs live XML validation and applies error highlighting.
     */
    private void performLiveValidation(String text) {
        if (text == null || text.isEmpty()) {
            currentErrors.clear();
            return;
        }

        // Cancel any running validation task
        if (validationTask != null && validationTask.isRunning()) {
            validationTask.cancel();
        }

        // Create new background task for validation
        validationTask = new javafx.concurrent.Task<List<org.xml.sax.SAXParseException>>() {
            @Override
            protected List<org.xml.sax.SAXParseException> call() throws Exception {
                if (isCancelled()) {
                    return new ArrayList<>();
                }

                // Get XML service from parent editor
                if (parentXmlEditor instanceof org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor) {
                    var xmlService = xmlEditor.getXmlService();
                    if (xmlService != null) {
                        try {
                            return xmlService.validateText(text);
                        } catch (Exception e) {
                            logger.debug("Validation error during live validation: {}", e.getMessage());
                            return new ArrayList<>();
                        }
                    }
                }
                return new ArrayList<>();
            }
        };

        validationTask.setOnSucceeded(event -> {
            List<org.xml.sax.SAXParseException> errors = validationTask.getValue();
            if (errors != null) {
                Platform.runLater(() -> applyErrorHighlighting(errors));
            }
        });

        validationTask.setOnFailed(event -> {
            logger.debug("Live validation task failed: {}", validationTask.getException().getMessage());
        });

        // Run the validation task in background
        new Thread(validationTask).start();
    }

    /**
     * Applies error highlighting to the CodeArea based on validation errors.
     */
    private void applyErrorHighlighting(List<org.xml.sax.SAXParseException> errors) {
        currentErrors.clear();

        if (errors == null || errors.isEmpty()) {
            return;
        }

        // Process errors and store for tooltip functionality
        for (org.xml.sax.SAXParseException error : errors) {
            int lineNumber = error.getLineNumber();
            String errorMessage = error.getMessage();

            if (lineNumber > 0 && lineNumber <= codeArea.getParagraphs().size()) {
                currentErrors.put(lineNumber, errorMessage);
            }
        }

        // Re-apply syntax highlighting with error information
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            applySyntaxHighlightingWithErrors(currentText);
        }

        // Update minimap with new errors (only if initialized and visible)
        if (minimapView != null && minimapVisible) {
            minimapView.updateErrors();
        }
    }

    /**
     * Applies syntax highlighting combined with error highlighting.
     */
    private void applySyntaxHighlightingWithErrors(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Cancel any running syntax highlighting task
        if (syntaxHighlightingTask != null && syntaxHighlightingTask.isRunning()) {
            syntaxHighlightingTask.cancel();
        }

        // Create new background task for combined highlighting
        syntaxHighlightingTask = new javafx.concurrent.Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                if (isCancelled()) {
                    return null;
                }

                // Compute base syntax highlighting with enumeration
                StyleSpans<Collection<String>> baseHighlighting = computeHighlightingWithEnumeration(text);

                // Add error highlighting as additional styles
                return addErrorStylesToHighlighting(baseHighlighting, text);
            }
        };

        syntaxHighlightingTask.setOnSucceeded(event -> {
            StyleSpans<Collection<String>> highlighting = syntaxHighlightingTask.getValue();
            if (highlighting != null) {
                codeArea.setStyleSpans(0, highlighting);
                // Update paragraph graphics to show error markers
                Platform.runLater(() -> codeArea.setParagraphGraphicFactory(createParagraphGraphicFactory()));
            }
        });

        syntaxHighlightingTask.setOnFailed(event -> {
            logger.error("Syntax highlighting with errors failed", syntaxHighlightingTask.getException());
            // Fallback to basic highlighting
            StyleSpans<Collection<String>> basicHighlighting = computeHighlighting(text);
            codeArea.setStyleSpans(0, basicHighlighting);
        });

        // Run the task in background
        new Thread(syntaxHighlightingTask).start();
    }

    /**
     * Adds error styles to existing syntax highlighting.
     */
    private StyleSpans<Collection<String>> addErrorStylesToHighlighting(
            StyleSpans<Collection<String>> baseHighlighting, String text) {

        if (currentErrors.isEmpty()) {
            return baseHighlighting;
        }

        // Use overlay to add error styles
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        // Split text into lines to identify error lines
        String[] lines = text.split("\n", -1);
        int position = 0;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            int lineNumber = lineIndex + 1;
            int lineLength = line.length();

            if (currentErrors.containsKey(lineNumber)) {
                // This line has errors - add error styling
                Collection<String> errorStyles = new ArrayList<>();
                errorStyles.add("diagnostic-error");
                spansBuilder.add(errorStyles, lineLength);
            } else {
                // No error - use empty styles (syntax highlighting will be preserved)
                spansBuilder.add(Collections.emptyList(), lineLength);
            }

            position += lineLength;

            // Add newline character styling if not the last line
            if (lineIndex < lines.length - 1) {
                spansBuilder.add(Collections.emptyList(), 1);
                position += 1;
            }
        }

        StyleSpans<Collection<String>> errorHighlighting = spansBuilder.create();

        // Use overlay method to combine syntax and error highlighting
        return baseHighlighting.overlay(errorHighlighting, (syntaxStyles, errorStyles) -> {
            if (errorStyles.isEmpty()) {
                return syntaxStyles;
            }
            // Combine syntax highlighting with error styles
            Collection<String> combined = new ArrayList<>(syntaxStyles);
            combined.addAll(errorStyles);
            return combined;
        });
    }

    /**
     * Updates the cache of elements that have enumeration constraints from XsdDocumentationData.
     */
    private void updateEnumerationElementsCache() {
        try {
            logger.debug("updateEnumerationElementsCache called. parentXmlEditor: {}", parentXmlEditor);
            if (parentXmlEditor instanceof org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor) {
                // Get XsdDocumentationData from XmlEditor
                var xsdDocumentationData = xmlEditor.getXsdDocumentationData();
                if (xsdDocumentationData == null) {
                    logger.debug("XsdDocumentationData is null. Cannot update enumeration cache.");
                    return;
                }

                logger.debug("Updating enumeration cache from XsdDocumentationData...");
                enumerationElementsByContext.clear();

                // Extract enumeration elements from XsdDocumentationData
                extractEnumerationElementsFromDocumentationData(xsdDocumentationData);

                logger.debug("Updated enumeration elements cache with {} contexts: {}",
                        enumerationElementsByContext.size(), enumerationElementsByContext.keySet());

                // Force refresh of syntax highlighting after cache update
                String currentText = codeArea.getText();
                if (currentText != null && !currentText.isEmpty()) {
                    applySyntaxHighlighting(currentText);
                }

            } else {
                logger.debug("Parent editor is null or XsdDocumentationData not available.");
            }
        } catch (Exception e) {
            logger.error("Error updating enumeration elements cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Extracts enumeration elements from XsdDocumentationData.
     */
    private void extractEnumerationElementsFromDocumentationData(org.fxt.freexmltoolkit.domain.XsdDocumentationData xsdDocumentationData) {
        try {
            Map<String, org.fxt.freexmltoolkit.domain.XsdExtendedElement> elementMap = xsdDocumentationData.getExtendedXsdElementMap();

            for (Map.Entry<String, org.fxt.freexmltoolkit.domain.XsdExtendedElement> entry : elementMap.entrySet()) {
                String xpath = entry.getKey();
                org.fxt.freexmltoolkit.domain.XsdExtendedElement element = entry.getValue();

                // Check if element has enumeration constraints
                if (element.getRestrictionInfo() != null &&
                        element.getRestrictionInfo().facets() != null &&
                        element.getRestrictionInfo().facets().containsKey("enumeration")) {

                    // Extract context from XPath
                    String context = extractContextFromXPath(xpath);
                    String elementName = element.getElementName();

                    if (elementName != null && !elementName.isEmpty()) {
                        // Remove @ prefix for attributes
                        if (elementName.startsWith("@")) {
                            elementName = elementName.substring(1);
                        }

                        enumerationElementsByContext.computeIfAbsent(context, k -> new HashSet<>()).add(elementName);
                        logger.debug("Added enumeration element: {} in context: {} (XPath: {})", elementName, context, xpath);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error extracting enumeration elements from documentation data: {}", e.getMessage(), e);
        }
    }

    /**
     * Extracts context from XPath for enumeration mapping.
     */
    private String extractContextFromXPath(String xpath) {
        if (xpath == null || xpath.isEmpty()) {
            return "/";
        }

        // Split XPath by '/' and get the parent context
        String[] parts = xpath.split("/");
        if (parts.length <= 2) {
            return "/"; // Root context
        } else {
            // Return parent context (everything except the last element)
            StringBuilder context = new StringBuilder();
            for (int i = 1; i < parts.length - 1; i++) {
                context.append("/").append(parts[i]);
            }
            return context.toString();
        }
    }

    /**
     * Checks if an XSD element has enumeration constraints.
     */
    private boolean hasEnumerationConstraint(org.w3c.dom.Element element) {
        try {
            org.w3c.dom.NodeList simpleTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");

            for (int i = 0; i < simpleTypes.getLength(); i++) {
                org.w3c.dom.Element simpleType = (org.w3c.dom.Element) simpleTypes.item(i);
                org.w3c.dom.NodeList restrictions = simpleType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");

                for (int j = 0; j < restrictions.getLength(); j++) {
                    org.w3c.dom.Element restriction = (org.w3c.dom.Element) restrictions.item(j);
                    org.w3c.dom.NodeList enumerations = restriction.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "enumeration");

                    if (enumerations.getLength() > 0) {
                        return true; // Found enumeration constraints
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error checking enumeration constraint: {}", e.getMessage(), e);
        }
        return false;
    }


    // The key-pressed handler was extended with Ctrl+F logic
    private void setupEventHandlers() {
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

        // Mouse hover for error tooltips and Go-to-Definition cursor
        codeArea.setOnMouseMoved(event -> {
            if (event.isControlDown()) {
                // Show hand cursor when Ctrl is held over XML elements
                String elementAtCursor = getElementNameAtPosition(event.getX(), event.getY());
                if (elementAtCursor != null && !elementAtCursor.isEmpty()) {
                    codeArea.setCursor(Cursor.HAND);
                } else {
                    codeArea.setCursor(Cursor.DEFAULT);
                }
            } else {
                codeArea.setCursor(Cursor.DEFAULT);
                // Handle error tooltips
                var hit = codeArea.hit(event.getX(), event.getY());
                int characterIndex = hit.getCharacterIndex().orElse(-1);
                if (characterIndex >= 0) {
                    int lineNumber = codeArea.offsetToPosition(characterIndex, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor() + 1;
                    showErrorTooltipIfPresent(lineNumber, event.getScreenX(), event.getScreenY());
                }
            }
        });

        codeArea.setOnMouseExited(event -> {
            hideErrorTooltip();
            codeArea.setCursor(Cursor.DEFAULT);
        });

        // Ctrl+Click for Go-to-Definition (use addEventHandler to avoid conflicts)
        codeArea.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, event -> {
            logger.debug("Mouse clicked - Ctrl: {}, Clicks: {}", event.isControlDown(), event.getClickCount());
            if (event.isControlDown() && event.getClickCount() == 1) {
                logger.debug("Ctrl+Click detected - triggering Go-to-Definition");
                handleGoToDefinition(event);
                event.consume();
            }
        });

        // Handler for keyboard shortcuts
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown()) {
                // Font size with Ctrl +/-, Reset with Ctrl + 0
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
                            // Ctrl+Shift+S = Save As
                            requestSaveAs();
                        } else {
                            // Ctrl+S = Save
                            handleSaveFile();
                        }
                        event.consume();
                    }
                    default -> {
                    }
                }
            }
        });

        // Handle Ctrl key events for Go-to-Definition visual feedback
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.CONTROL) {
                logger.debug("Ctrl pressed - Go-to-Definition mode active");
            }
        });

        codeArea.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.CONTROL) {
                codeArea.setCursor(Cursor.DEFAULT);
                logger.debug("Ctrl released - Go-to-Definition mode inactive");
            }
        });

        // IntelliSense: Tab completion and auto-closing tags
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case TAB -> {
                    if (handleTabCompletion(event)) {
                        event.consume();
                    }
                }
                case GREATER -> {
                    if (handleAutoClosingTag(event)) {
                        event.consume();
                    }
                }
                case ESCAPE -> {
                    hideIntelliSensePopup();
                }
                case UP, DOWN -> {
                    if (intelliSensePopup.isShowing()) {
                        handlePopupNavigation(event);
                        event.consume();
                    }
                }
                case ENTER -> {
                    logger.debug("ENTER key pressed in CodeArea");
                    if (intelliSensePopup != null && intelliSensePopup.isShowing()) {
                        logger.debug("IntelliSense popup is showing - calling selectCompletionItem()");
                        selectCompletionItem();
                        event.consume();
                    } else {
                        logger.debug("IntelliSense popup not showing - applying intelligent cursor positioning");
                        if (handleIntelligentEnterKey()) {
                            event.consume();
                        }
                    }
                }
                default -> {
                }
            }
        });

        // IntelliSense: Handle typed characters for completion triggers
        codeArea.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String character = event.getCharacter();
            if (character != null && !character.isEmpty()) {
                logger.debug("KEY_TYPED event - character: '{}' (code: {})", character, (int) character.charAt(0));
                
                // If we're in specialized mode (Schematron or XSD), completely skip XML IntelliSense
                if (currentMode != EditorMode.XML) {
                    logger.debug("{} mode is active, skipping XML IntelliSense completely", currentMode);
                    // Don't call handleIntelliSenseTrigger - let only the specialized auto-completion handle it
                    return;
                }
                
                if (handleIntelliSenseTrigger(event)) {
                    logger.debug("IntelliSense trigger handled for: {}", character);
                }
            } else {
                logger.debug("KEY_TYPED event - character is null or empty");
            }
        });

        // Handle Ctrl+Space for manual completion (including enumeration completion)
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
                logger.debug("Ctrl+Space pressed for manual completion in mode: {}", currentMode);
                if (handleManualCompletion()) {
                    event.consume();
                }
            }
        });
    }

    /**
     * Initializes the IntelliSense popup components.
     */
    private void initializeIntelliSensePopup() {
        // Create completion list view
        completionListView = new ListView<>();
        completionListView.setPrefWidth(300);
        completionListView.setPrefHeight(200);
        completionListView.setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-border-width: 1px;");

        // Create popup stage
        intelliSensePopup = new Stage(StageStyle.UTILITY);
        intelliSensePopup.setAlwaysOnTop(true);
        intelliSensePopup.setResizable(false);

        // Add list view to popup
        VBox popupContent = new VBox();
        popupContent.setPadding(new Insets(5));
        popupContent.setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-border-width: 1px;");

        Label titleLabel = new Label("Element Names");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-padding: 0 0 5 0;");

        popupContent.getChildren().addAll(titleLabel, completionListView);
        intelliSensePopup.setScene(new javafx.scene.Scene(popupContent));

        // Add double-click handler for selection
        completionListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                selectCompletionItem();
            }
        });

        // Add key event handler directly to the completion list view
        completionListView.setOnKeyPressed(event -> {
            logger.debug("ListView KeyPressed: {}", event.getCode());
            switch (event.getCode()) {
                case ENTER -> {
                    logger.debug("ENTER pressed in ListView - calling selectCompletionItem()");
                    selectCompletionItem();
                    event.consume();
                }
                case ESCAPE -> {
                    logger.debug("ESCAPE pressed in ListView - hiding popup");
                    hideIntelliSensePopup();
                    event.consume();
                }
                case UP, DOWN -> {
                    // Let ListView handle navigation naturally
                    logger.debug("Navigation key in ListView: {}", event.getCode());
                }
                default -> {
                    // For all other keys, try to pass them back to the CodeArea
                    logger.debug("Other key in ListView: {} - passing to CodeArea", event.getCode());
                    codeArea.fireEvent(event);
                    event.consume();
                }
            }
        });

        // Ensure the popup scene doesn't steal focus from the main window
        intelliSensePopup.getScene().setOnKeyPressed(event -> {
            logger.debug("Scene KeyPressed: {}", event.getCode());
            completionListView.fireEvent(event);
        });
    }

    /**
     * Initialize the new XML IntelliSense Engine
     */
    private void initializeXmlIntelliSenseEngine() {
        try {
            logger.debug("Initializing XML IntelliSense Engine...");
            intelliSenseEngine = new XmlIntelliSenseEngine(codeArea);
            logger.info("XML IntelliSense Engine initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize XML IntelliSense Engine: {}", e.getMessage(), e);
        }
    }

    /**
     * Initialize the Code Folding Manager
     */
    private void initializeCodeFoldingManager() {
        try {
            logger.debug("Initializing Code Folding Manager...");
            codeFoldingManager = new XmlCodeFoldingManager(codeArea);
            logger.info("Code Folding Manager initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize Code Folding Manager: {}", e.getMessage(), e);
        }
    }

    /**
     * Initialize Enhanced IntelliSense components
     */
    private void initializeEnhancedIntelliSense() {
        try {
            logger.debug("Initializing Enhanced IntelliSense components...");

            // Initialize core components with null checks
            try {
                fuzzySearch = new FuzzySearch();
                logger.debug("FuzzySearch initialized");
            } catch (Exception e) {
                logger.warn("Failed to initialize FuzzySearch: {}", e.getMessage());
            }

            try {
                completionCache = new CompletionCache();
                logger.debug("CompletionCache initialized");
            } catch (Exception e) {
                logger.warn("Failed to initialize CompletionCache: {}", e.getMessage());
            }

            try {
                performanceProfiler = PerformanceProfiler.getInstance();
                logger.debug("PerformanceProfiler initialized");
            } catch (Exception e) {
                logger.warn("Failed to initialize PerformanceProfiler: {}", e.getMessage());
            }

            try {
                templateEngine = new TemplateEngine();
                logger.debug("TemplateEngine initialized");
            } catch (Exception e) {
                logger.warn("Failed to initialize TemplateEngine: {}", e.getMessage());
            }

            try {
                xsdIntegration = new org.fxt.freexmltoolkit.controls.intellisense.XsdIntegrationAdapter();
                logger.debug("XsdIntegrationAdapter initialized");

                // XSD integration will be updated when parent is set via setParentXmlEditor
            } catch (Exception e) {
                logger.warn("Failed to initialize XsdIntegrationAdapter: {}", e.getMessage());
            }

            try {
                quickActionsIntegration = new QuickActionsIntegration(codeArea, xsdIntegration);
                logger.debug("QuickActionsIntegration initialized");
            } catch (Exception e) {
                logger.warn("Failed to initialize QuickActionsIntegration: {}", e.getMessage());
            }

            logger.info("Enhanced IntelliSense components initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize Enhanced IntelliSense components: {}", e.getMessage(), e);
            // Fall back to basic IntelliSense only
            enhancedIntelliSenseEnabled = false;
        }
    }

    /**
     * Initialize specialized Auto-Completion components (Schematron and XSD)
     */
    private void initializeSpecializedAutoComplete() {
        try {
            logger.debug("Initializing specialized Auto-Completion components...");

            // Initialize Schematron auto-completion
            schematronAutoComplete = new SchematronAutoComplete(codeArea);
            logger.debug("Schematron Auto-Complete initialized");

            // Initialize XSD auto-completion  
            xsdAutoComplete = new XsdAutoComplete(codeArea);
            logger.debug("XSD Auto-Complete initialized");

            // Initially disable both - they will be enabled when the appropriate mode is set
            schematronAutoComplete.setEnabled(false);
            xsdAutoComplete.setEnabled(false);

            logger.info("Specialized Auto-Complete components initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize specialized Auto-Complete components: {}", e.getMessage(), e);
        }
    }

    /**
     * Sets the editor mode, which determines which type of auto-completion is active.
     *
     * @param mode The editor mode to activate
     */
    public void setEditorMode(EditorMode mode) {
        if (this.currentMode == mode) {
            return; // Already in the requested mode
        }

        // Disable all auto-completion systems first
        disableAllAutoCompletion();

        // Set new mode and enable appropriate auto-completion
        this.currentMode = mode;

        switch (mode) {
            case XML -> {
                // XML mode uses the standard IntelliSense system
                logger.debug("Switched to XML mode - standard IntelliSense active");
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
        }
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
     * Disables all auto-completion systems.
     */
    private void disableAllAutoCompletion() {
        if (schematronAutoComplete != null) {
            schematronAutoComplete.setEnabled(false);
            logger.debug("Disabled Schematron auto-completion");
        }
        if (xsdAutoComplete != null) {
            xsdAutoComplete.setEnabled(false);
            logger.debug("Disabled XSD auto-completion");
        }
    }

    /**
     * Convenience method to enable Schematron mode.
     *
     * @param enabled True to enable Schematron mode, false to return to XML mode
     */
    public void setSchematronMode(boolean enabled) {
        logger.debug("setSchematronMode called with enabled = {}", enabled);
        setEditorMode(enabled ? EditorMode.SCHEMATRON : EditorMode.XML);
        logger.debug("After setSchematronMode: current mode = {}, schematronAutoComplete enabled = {}", 
                currentMode, schematronAutoComplete != null ? schematronAutoComplete.isEnabled() : "null");
    }

    /**
     * Returns whether Schematron mode is currently active.
     *
     * @return True if Schematron auto-completion is active
     */
    public boolean isSchematronMode() {
        return currentMode == EditorMode.SCHEMATRON;
    }

    /**
     * Convenience method to enable XSD mode.
     *
     * @param enabled True to enable XSD mode, false to return to XML mode
     */
    public void setXsdMode(boolean enabled) {
        setEditorMode(enabled ? EditorMode.XSD : EditorMode.XML);
    }

    /**
     * Returns whether XSD mode is currently active.
     *
     * @return True if XSD auto-completion is active
     */
    public boolean isXsdMode() {
        return currentMode == EditorMode.XSD;
    }

    /**
     * Gets the Schematron auto-completion instance for advanced configuration.
     *
     * @return The SchematronAutoComplete instance, or null if not initialized
     */
    public SchematronAutoComplete getSchematronAutoComplete() {
        return schematronAutoComplete;
    }

    /**
     * Gets the XSD auto-completion instance for advanced configuration.
     *
     * @return The XsdAutoComplete instance, or null if not initialized
     */
    public XsdAutoComplete getXsdAutoComplete() {
        return xsdAutoComplete;
    }

    /**
     * Finds the next or previous occurrence of the specified text in the editor.
     *
     * @param text    The text to search for
     * @param forward If true, search forward; if false, search backward
     */
    public void find(String text, boolean forward) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String content = codeArea.getText();
        int searchFrom = codeArea.getSelection().getEnd();

        int index;
        if (forward) {
            index = content.toLowerCase().indexOf(text.toLowerCase(), searchFrom);
            // Wrap around if not found from caret onwards
            if (index == -1) {
                index = content.toLowerCase().indexOf(text.toLowerCase());
            }
        } else {
            searchFrom = codeArea.getSelection().getStart() - 1;
            index = content.toLowerCase().lastIndexOf(text.toLowerCase(), searchFrom);
            // Wrap around
            if (index == -1) {
                index = content.toLowerCase().lastIndexOf(text.toLowerCase());
            }
        }

        if (index >= 0) {
            codeArea.selectRange(index, index + text.length());
            codeArea.requestFollowCaret();
        }
    }

    /**
     * Replaces the currently selected text if it matches the find text.
     *
     * @param findText    The text to find
     * @param replaceText The text to replace it with
     */
    public void replace(String findText, String replaceText) {
        if (findText == null || findText.isEmpty()) return;

        String selectedText = codeArea.getSelectedText();
        if (selectedText.equalsIgnoreCase(findText)) {
            codeArea.replaceSelection(replaceText);
        }
        find(findText, true);
    }

    /**
     * Replaces all occurrences of the find text with the replace text.
     *
     * @param findText    The text to find
     * @param replaceText The text to replace it with
     */
    public void replaceAll(String findText, String replaceText) {
        if (findText == null || findText.isEmpty()) return;
        Pattern pattern = Pattern.compile(Pattern.quote(findText), Pattern.CASE_INSENSITIVE);
        String newContent = pattern.matcher(codeArea.getText()).replaceAll(replaceText);
        codeArea.replaceText(newContent);
    }

    /**
     * Test method to verify syntax highlighting is working.
     * This method loads a simple XML example and applies syntax highlighting.
     */
    public void testSyntaxHighlighting() {
        String testXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!-- This is a test comment -->\n" +
                "<root>\n" +
                "    <element attribute=\"value\">content</element>\n" +
                "</root>";

        logger.debug("=== Testing Syntax Highlighting ===");
        logger.debug("Test XML:");
        logger.debug("{}", testXml);

        debugCssStatus();

        // Set the test content
        codeArea.replaceText(testXml);

        // Manually trigger syntax highlighting
        refreshSyntaxHighlighting();

        debugCssStatus();

        logger.debug("=== Test completed ===");
    }

    /**
     * Test method to verify enumeration highlighting is working.
     * This method loads XML with enumeration elements and tests highlighting.
     */
    public void testEnumerationHighlighting() {
        // Add some test enumeration elements to the cache with context
        Set<String> rootContext = new HashSet<>();
        rootContext.add("DataOperation");
        rootContext.add("Status");
        enumerationElementsByContext.put("/", rootContext);

        String testXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root>\n" +
                "    <DataOperation>INITIAL</DataOperation>\n" +
                "    <Status>ACTIVE</Status>\n" +
                "    <OtherElement>Some content</OtherElement>\n" +
                "</root>";

        logger.debug("=== Testing Enumeration Highlighting ===");
        logger.debug("Test XML:");
        logger.debug("{}", testXml);
        logger.debug("Enumeration elements in cache: {}", enumerationElementsByContext);

        debugCssStatus();

        // Set the test content
        codeArea.replaceText(testXml);

        // Manually trigger syntax highlighting
        refreshSyntaxHighlighting();

        debugCssStatus();

        logger.debug("=== Enumeration Test completed ===");
    }

    /**
     * Test method to verify editor mode switching and specialized auto-completion.
     * This method tests all three modes: XML, Schematron, and XSD.
     */
    public void testEditorModes() {
        logger.debug("=== Testing Editor Mode Switching ===");

        // Test XML Mode
        logger.debug("Testing XML Mode...");
        setEditorMode(EditorMode.XML);
        logger.debug("Current mode: {}", getEditorMode());
        logger.debug("XML IntelliSense should be active");

        // Test Schematron Mode
        logger.debug("Testing Schematron Mode...");
        setEditorMode(EditorMode.SCHEMATRON);
        String testSchematron = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                    <sch:pattern>
                        <sch:rule context="test">
                            <sch:assert test="@id">Element must have ID</sch:assert>
                        </sch:rule>
                    </sch:pattern>
                </sch:schema>""";
        codeArea.replaceText(testSchematron);
        logger.debug("Current mode: {}, Schematron AutoComplete active: {}",
                getEditorMode(), schematronAutoComplete != null);

        // Test XSD Mode
        logger.debug("Testing XSD Mode...");
        setEditorMode(EditorMode.XSD);
        String testXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/schema"
                           elementFormDefault="qualified">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>""";
        codeArea.replaceText(testXsd);
        logger.debug("Current mode: {}, XSD AutoComplete active: {}",
                getEditorMode(), xsdAutoComplete != null);

        // Reset to XML mode
        setEditorMode(EditorMode.XML);
        logger.debug("Reset to XML mode");
        logger.debug("=== Editor Mode Testing completed ===");
    }

    /**
     * Test method to verify Schematron auto-completion functionality.
     * This method tests if Schematron mode activates correctly and auto-completion works.
     */
    public void testSchematronAutoCompletion() {
        logger.debug("=== Testing Schematron Auto-Completion ===");

        // Enable Schematron mode
        setSchematronMode(true);

        // Test Schematron content
        String testSchematron = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                    <sch:pattern>
                        <sch:rule context="test">
                            <sch:assert test="@id">Element must have ID</sch:assert>
                        </sch:rule>
                    </sch:pattern>
                </sch:schema>""";

        codeArea.replaceText(testSchematron);

        logger.debug("Schematron mode enabled: {}", isSchematronMode());
        logger.debug("SchematronAutoComplete initialized: {}", schematronAutoComplete != null);
        logger.debug("Current editor mode: {}", getEditorMode());
        logger.debug("=== Schematron Auto-Completion Test completed ===");
    }

    /**
     * Test method to verify XsdDocumentationData-based enumeration highlighting.
     * This method tests the new optimized approach.
     */
    public void testXsdDocumentationDataEnumerationHighlighting() {
        logger.debug("=== Testing XsdDocumentationData-based Enumeration Highlighting ===");

        // Update enumeration cache from XsdDocumentationData
        updateEnumerationElementsCache();

        String testXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root>\n" +
                "    <DataOperation>INITIAL</DataOperation>\n" +
                "    <Status>ACTIVE</Status>\n" +
                "    <OtherElement>Some content</OtherElement>\n" +
                "</root>";

        logger.debug("Test XML:");
        logger.debug("{}", testXml);
        logger.debug("Enumeration elements in cache: {}", enumerationElementsByContext);

        // Set the test content
        codeArea.replaceText(testXml);

        // Manually trigger syntax highlighting
        refreshSyntaxHighlighting();

        logger.debug("=== XsdDocumentationData Enumeration Test completed ===");
    }

    /**
     * Performance test for enumeration highlighting.
     * This method tests the performance with a large XML file.
     */
    public void testEnumerationHighlightingPerformance() {
        logger.debug("=== Performance Test for Enumeration Highlighting ===");

        // Add test enumeration elements
        Set<String> rootContext = new HashSet<>();
        rootContext.add("DataOperation");
        rootContext.add("Status");
        rootContext.add("Priority");
        enumerationElementsByContext.put("/", rootContext);

        // Create a large XML file for testing
        StringBuilder largeXml = new StringBuilder();
        largeXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        largeXml.append("<root>\n");

        for (int i = 0; i < 1000; i++) {
            largeXml.append("    <DataOperation>INITIAL</DataOperation>\n");
            largeXml.append("    <Status>ACTIVE</Status>\n");
            largeXml.append("    <Priority>HIGH</Priority>\n");
            largeXml.append("    <OtherElement>Some content ").append(i).append("</OtherElement>\n");
        }
        largeXml.append("</root>");

        String testXml = largeXml.toString();
        logger.debug("Created test XML with {} lines", testXml.split("\n").length);

        // Measure performance
        long startTime = System.currentTimeMillis();

        // Set the test content
        codeArea.replaceText(testXml);

        // Manually trigger syntax highlighting
        refreshSyntaxHighlighting();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        logger.debug("Performance test completed in {} ms", duration);
        logger.debug("=== Performance Test completed ===");
    }

    /**
     * Manually triggers syntax highlighting for the current text content.
     * This can be used for testing or to force a refresh of the highlighting.
     */
    public void refreshSyntaxHighlighting() {
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            logger.debug("Manually refreshing syntax highlighting for text length: {}", currentText.length());

            // Apply syntax highlighting
            applySyntaxHighlighting(currentText);

            logger.debug("Syntax highlighting refresh completed");
        } else {
            logger.debug("No text to highlight");
        }
    }

    /**
     * Manually triggers folding region calculation for the current text content.
     * This can be used to refresh folding capabilities after loading new content.
     */
    public void refreshFoldingRegions() {
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            logger.debug("Manually refreshing folding regions for text length: {}", currentText.length());
            updateFoldingRegions(currentText);
            logger.debug("Found {} foldable regions", foldingRegions.size());
        } else {
            logger.debug("No text to analyze for folding");
        }
    }

    /**
     * Creates a compact line number without spacing.
     */
    private Node createCompactLineNumber(int lineIndex) {
        Label lineNumber = new Label(String.valueOf(lineIndex + 1));
        lineNumber.getStyleClass().add("lineno");

        // Remove all spacing
        lineNumber.setPadding(Insets.EMPTY); // No padding
        lineNumber.setMinWidth(30); // Compact width
        lineNumber.setMaxHeight(Double.MAX_VALUE); // Takes full line height
        lineNumber.setAlignment(Pos.CENTER_RIGHT);

        // Styling for seamless display without spacing
        // Gray background
        // No border
        // 3px padding left and right
        lineNumber.setStyle(
                "-fx-text-fill: #666666; -fx-font-family: monospace; -fx-font-size: " + fontSize + "px; -fx-background-color: #f0f0f0; -fx-border-width: 0; -fx-padding: 0 3 0 3; -fx-spacing: 0;"                   // No spacing
        );

        return lineNumber;
    }

    /**
     * Creates a factory that generates graphics (line number + fold symbol) for each line.
     */
    private IntFunction<Node> createParagraphGraphicFactory() {
        return lineIndex -> {
            // Safety check, as the factory can be called during text changes
            if (lineIndex >= codeArea.getParagraphs().size()) {
                HBox fallbackHBox = new HBox(createCompactLineNumber(lineIndex));
                fallbackHBox.setSpacing(0); // Remove spacing in fallback too
                fallbackHBox.setPadding(Insets.EMPTY); // No padding
                fallbackHBox.setAlignment(Pos.TOP_LEFT); // TOP_LEFT for seamless alignment
                fallbackHBox.setFillHeight(true); // Fill full height
                return fallbackHBox;
            }

            boolean isFoldable = foldingRegions.containsKey(lineIndex);
            boolean isFolded = foldedLines.contains(lineIndex);

            // Create icon
            Region foldingIndicator = new Region();
            foldingIndicator.getStyleClass().add("icon");

            if (isFolded) {
                foldingIndicator.getStyleClass().add("toggle-expand");
            } else {
                foldingIndicator.getStyleClass().add("toggle-collapse");
            }

            // Create a wrapper for the icon to replicate the CSS structure from XmlGraphicEditor.
            StackPane iconWrapper = new StackPane(foldingIndicator);
            iconWrapper.getStyleClass().add("tree-toggle-button");

            // Apply click logic to the wrapper
            iconWrapper.setOnMouseClicked(e -> {
                // Toggling a fold can be slow. We use a Task to manage the process,
                // ensuring the UI remains responsive and the cursor provides feedback.
                // The actual UI modification MUST happen on the JavaFX Application Thread.

                // 1. Define the operation in a Task. The 'call' method runs in the background
                //    and should prepare everything needed for the UI update.
                Task<Boolean> foldingTask = new Task<>() {
                    @Override
                    protected Boolean call() {
                        // This runs in the background.
                        // We are NOT modifying the UI here.
                        // We are just returning whether we are about to fold or unfold.
                        return !foldedLines.contains(lineIndex);
                    }
                };

                // 2. Set up handlers for the task's lifecycle, which run on the JAT.
                foldingTask.setOnRunning(event -> {
                    if (getScene() != null) {
                        getScene().setCursor(Cursor.WAIT);
                    }
                });

                foldingTask.setOnSucceeded(event -> {
                    // This runs on the JAT after 'call' is complete.
                    try {
                        boolean shouldFold = foldingTask.get(); // Get the result from the background task

                        // --- PERFORM UI MODIFICATION ON JAT ---
                        if (shouldFold) {
                            Integer endLine = foldingRegions.get(lineIndex);
                            if (endLine != null) {
                                codeArea.foldParagraphs(lineIndex, endLine);
                                foldedLines.add(lineIndex);
                            }
                        } else {
                            codeArea.unfoldParagraphs(lineIndex);
                            foldedLines.remove(lineIndex);
                        }
                        // --- END OF UI MODIFICATION ---

                    } catch (Exception ex) {
                        // Handle exceptions from the task
                        logger.error("Exception in folding task", ex);
                    } finally {
                        // Always clean up the UI
                        // Redraw the gutter to update all line numbers and folding icons
                        codeArea.setParagraphGraphicFactory(createParagraphGraphicFactory());
                        if (getScene() != null) {
                            getScene().setCursor(Cursor.DEFAULT);
                        }
                    }
                });

                foldingTask.setOnFailed(event -> {
                    // Handle failures and clean up the UI
                    if (getScene() != null) {
                        getScene().setCursor(Cursor.DEFAULT);
                    }
                    logger.error("Folding task failed", foldingTask.getException());
                });

                // 3. Start the task on a new thread.
                new Thread(foldingTask).start();
            });


            Node lineNumberNode = createCompactLineNumber(lineIndex);

            // Create error marker if this line has errors
            Region errorMarker = new Region();
            errorMarker.getStyleClass().add("error-marker");
            errorMarker.setPrefSize(8, 8);
            errorMarker.setMaxSize(8, 8);
            errorMarker.setMinSize(8, 8);
            boolean hasError = currentErrors.containsKey(lineIndex + 1);
            errorMarker.setVisible(hasError);

            HBox hbox = new HBox(errorMarker, lineNumberNode, iconWrapper);
            hbox.setAlignment(Pos.TOP_LEFT); // TOP_LEFT for seamless alignment
            hbox.setSpacing(2); // Small spacing for error marker
            hbox.setPadding(Insets.EMPTY); // No padding in the HBox
            hbox.setFillHeight(true); // Fill full height

            // The wrapper (and thus the symbol) is only visible if the line is foldable.
            iconWrapper.setVisible(isFoldable);

            return hbox;
        };
    }



    // --- Public API for the Editor ---

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
     * Sets the font size of the code area.
     *
     * @param size The font size in points
     */
    private void setFontSize(int size) {
        codeArea.setStyle("-fx-font-size: " + size + "pt;");
    }

    /**
     * Searches for the given text in the CodeArea, highlights all occurrences
     * and scrolls to the first match.
     *
     * @param text The text to search for. If null or empty, highlighting is removed.
     */
    public void searchAndHighlight(String text) {
        // First apply normal syntax highlighting
        StyleSpans<Collection<String>> syntaxHighlighting = computeHighlightingWithEnumeration(codeArea.getText());

        if (text == null || text.isBlank()) {
            codeArea.setStyleSpans(0, syntaxHighlighting); // Only syntax highlighting
            return;
        }

        // Create style for search highlighting
        StyleSpansBuilder<Collection<String>> searchSpansBuilder = new StyleSpansBuilder<>();
        Pattern pattern = Pattern.compile(Pattern.quote(text), CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(codeArea.getText());
        int lastMatchEnd = 0;

        while (matcher.find()) {
            searchSpansBuilder.add(Collections.emptyList(), matcher.start() - lastMatchEnd);
            searchSpansBuilder.add(Collections.singleton("search-highlight"), matcher.end() - matcher.start());
            lastMatchEnd = matcher.end();
        }
        searchSpansBuilder.add(Collections.emptyList(), codeArea.getLength() - lastMatchEnd);

        // Overlay search highlighting over syntax highlighting
        codeArea.setStyleSpans(0, syntaxHighlighting.overlay(searchSpansBuilder.create(), (style1, style2) -> {
            return style2.isEmpty() ? style1 : style2;
        }));
    }

    /**
     * Returns the internal CodeArea instance.
     * This enables controlled access from outside, e.g., for focus management.
     *
     * @return The CodeArea component.
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * Sets the text content and immediately applies syntax highlighting
     */
    public void setText(String text) {
        codeArea.replaceText(text);

        // Auto-detect editor mode based on content
        autoDetectEditorMode(text);

        Platform.runLater(() -> {
            if (text != null && !text.isEmpty()) {
                applySyntaxHighlighting(text);
                updateFoldingRegions(text);
            }
        });
    }

    /**
     * Auto-detects the appropriate editor mode based on the document content.
     * This method analyzes the XML content to determine if it's a Schematron or XSD file.
     *
     * @param content The document content to analyze
     */
    public void autoDetectEditorMode(String content) {
        if (content == null || content.trim().isEmpty()) {
            // For empty content, don't change the mode - let the caller decide
            // This prevents overriding explicitly set modes (e.g., when SchematronController sets Schematron mode)
            logger.debug("Empty content detected - keeping current editor mode: {}", currentMode);
            return;
        }

        String lowerContent = content.toLowerCase();

        // Check for Schematron namespace and elements
        if (lowerContent.contains("http://purl.oclc.org/dsdl/schematron") ||
                lowerContent.contains("sch:schema") ||
                lowerContent.contains("schematron")) {

            logger.debug("Auto-detected Schematron content - switching to Schematron mode");
            setEditorMode(EditorMode.SCHEMATRON);
            return;
        }

        // Check for XSD namespace and elements
        if (lowerContent.contains("http://www.w3.org/2001/xmlschema") ||
                lowerContent.contains("xs:schema") ||
                lowerContent.contains("xsd:schema") ||
                (lowerContent.contains("<schema") && lowerContent.contains("xmlns"))) {

            logger.debug("Auto-detected XSD content - switching to XSD mode");
            setEditorMode(EditorMode.XSD);
            return;
        }

        // Default to XML mode
        if (currentMode != EditorMode.XML) {
            logger.debug("No special content detected - switching to XML mode");
            setEditorMode(EditorMode.XML);
        }
    }

    /**
     * Sets the document file path and automatically detects the editor mode based on file extension.
     *
     * @param filePath The file path of the document
     */
    public void setDocumentFilePath(String filePath) {
        if (filePath != null) {
            String lowerPath = filePath.toLowerCase();

            if (lowerPath.endsWith(".sch") || lowerPath.contains("schematron")) {
                logger.debug("Schematron file detected: {} - switching to Schematron mode", filePath);
                setEditorMode(EditorMode.SCHEMATRON);
            } else if (lowerPath.endsWith(".xsd") || lowerPath.contains("schema")) {
                logger.debug("XSD file detected: {} - switching to XSD mode", filePath);
                setEditorMode(EditorMode.XSD);
            } else {
                logger.debug("XML file detected: {} - switching to XML mode", filePath);
                setEditorMode(EditorMode.XML);
            }
        }
    }

    /**
     * Gets the current text content
     */
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Forces syntax highlighting refresh - useful when tab becomes visible
     */
    public void refreshHighlighting() {
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            Platform.runLater(() -> applySyntaxHighlighting(currentText));
        }
    }

    /**
     * Computes syntax highlighting with enumeration element indicators.
     */
    private StyleSpans<Collection<String>> computeHighlightingWithEnumeration(String text) {
        if (text == null) {
            text = "";
        }

        // First, get the standard syntax highlighting
        StyleSpans<Collection<String>> baseHighlighting = computeHighlighting(text);

        // Create a builder for enumeration highlighting
        StyleSpansBuilder<Collection<String>> enumSpansBuilder = new StyleSpansBuilder<>();
        int lastMatchEnd = 0;

        // Find all enumeration elements with content
        List<ElementTextInfo> enumElements = findAllEnumerationElements(text);

        for (ElementTextInfo elementInfo : enumElements) {
            int gapLength = elementInfo.startPosition() - lastMatchEnd;
            int contentLength = elementInfo.endPosition() - elementInfo.startPosition();

            // Skip invalid spans with negative lengths
            if (gapLength < 0 || contentLength < 0) {
                logger.warn("Skipping invalid span: gap={}, content={}, start={}, end={}",
                        gapLength, contentLength, elementInfo.startPosition(), elementInfo.endPosition());
                continue;
            }

            enumSpansBuilder.add(Collections.emptyList(), gapLength);
            enumSpansBuilder.add(Collections.singleton("enumeration-content"), contentLength);
            lastMatchEnd = elementInfo.endPosition();
        }
        int finalGapLength = text.length() - lastMatchEnd;
        if (finalGapLength >= 0) {
            enumSpansBuilder.add(Collections.emptyList(), finalGapLength);
        }

        // Overlay the enumeration highlighting on top of the base syntax highlighting
        StyleSpans<Collection<String>> enumHighlighting = enumSpansBuilder.create();

        // Debug logging
        logger.debug("Base highlighting spans: {}", baseHighlighting.length());
        logger.debug("Enumeration highlighting spans: {}", enumHighlighting.length());

        return baseHighlighting.overlay(enumHighlighting, (baseStyle, enumStyle) -> {
            // If we have enumeration styling, use it; otherwise use base styling
            if (enumStyle != null && !enumStyle.isEmpty()) {
                logger.debug("Applying enumeration style: {}", enumStyle);
                return enumStyle;
            } else {
                return baseStyle;
            }
        });
    }

    private List<ElementTextInfo> findAllEnumerationElements(String text) {
        List<ElementTextInfo> elements = new ArrayList<>();

        if (enumerationElementsByContext.isEmpty()) {
            return elements;
        }

        // Use a more flexible pattern that matches any element with content
        // and then checks if the element name is in our enumeration cache for the current context
        Pattern tagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)[^>]*>([^<]*)</\\1>");
        Matcher matcher = tagPattern.matcher(text);

        while (matcher.find()) {
            String elementName = matcher.group(1);
            String content = matcher.group(2);

            if (content.isBlank()) {
                continue; // Skip empty elements
            }

            // Find the context for this element by looking at the XML structure
            String context = findElementContext(text, matcher.start());

            // Check if this element is in our enumeration cache for this context
            Set<String> contextElements = enumerationElementsByContext.get(context);
            if (contextElements != null && contextElements.contains(elementName)) {
                int contentStart = matcher.start(2);
                int contentEnd = matcher.end(2);
                elements.add(new ElementTextInfo(elementName, content, contentStart, contentEnd));
            }
        }
        
        return elements;
    }

    /**
     * Finds the context (XPath-like path) for an element at the given position.
     */
    private String findElementContext(String text, int elementPosition) {
        try {
            // Look backwards from the element position to build the context
            String textBeforeElement = text.substring(0, elementPosition);

            // Use a stack to track element nesting
            java.util.Stack<String> elementStack = new java.util.Stack<>();

            // Simple character-based parsing for better performance
            int pos = textBeforeElement.length() - 1;
            while (pos >= 0) {
                char ch = textBeforeElement.charAt(pos);

                if (ch == '>') {
                    // Look for opening tag
                    int tagStart = textBeforeElement.lastIndexOf('<', pos);
                    if (tagStart >= 0) {
                        String tag = textBeforeElement.substring(tagStart + 1, pos).trim();
                        if (!tag.startsWith("/") && !tag.endsWith("/")) {
                            // Extract element name (first word)
                            int spacePos = tag.indexOf(' ');
                            String elementName = spacePos > 0 ? tag.substring(0, spacePos) : tag;
                            if (!elementName.isEmpty()) {
                                elementStack.push(elementName);
                            }
                        }
                    }
                } else if (ch == '<' && pos + 1 < textBeforeElement.length() && textBeforeElement.charAt(pos + 1) == '/') {
                    // Closing tag found, pop from stack
                    if (!elementStack.isEmpty()) {
                        elementStack.pop();
                    }
                }

                pos--;
            }

            // Build context path
            if (elementStack.isEmpty()) {
                return "/"; // Root context
            } else {
                // Use the immediate parent as context
                return "/" + elementStack.peek();
            }

        } catch (Exception e) {
            return "/"; // Default to root context
        }
    }

    /**
     * Static method for basic XML syntax highlighting without enumeration features.
     * Used by other components that don't need enumeration highlighting.
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (text == null) {
            text = "";
        }

        Matcher matcher = XML_TAG.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            if (matcher.group("COMMENT") != null) {
                spansBuilder.add(Collections.singleton("comment"), matcher.end() - matcher.start());
            } else {
                if (matcher.group("ELEMENT") != null) {
                    String attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION);

                    spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));
                    spansBuilder.add(Collections.singleton("anytag"), matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));

                    if (attributesText != null && !attributesText.isEmpty()) {
                        lastKwEnd = 0;

                        Matcher amatcher = ATTRIBUTES.matcher(attributesText);
                        while (amatcher.find()) {
                            spansBuilder.add(Collections.emptyList(), amatcher.start() - lastKwEnd);
                            spansBuilder.add(Collections.singleton("attribute"), amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(Collections.singleton("tagmark"), amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME));
                            spansBuilder.add(Collections.singleton("avalue"), amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL));
                            lastKwEnd = amatcher.end();
                        }
                        if (attributesText.length() > lastKwEnd)
                            spansBuilder.add(Collections.emptyList(), attributesText.length() - lastKwEnd);
                    }

                    lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION);

                    spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd);
                }
            }
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Handles IntelliSense trigger when "<" is typed or space for attributes.
     * Implementation based on requirements: trigger completion after opening a tag "<" or adding space for attributes.
     * Note: This method is called AFTER the character has been typed (KEY_TYPED event).
     *
     * @param event The key event
     * @return true if the event was handled, false otherwise
     */
    private boolean handleIntelliSenseTrigger(KeyEvent event) {
        try {
            String character = event.getCharacter();
            logger.debug("handleIntelliSenseTrigger called with character: '{}'", character);

            // If we're in a specialized mode (Schematron or XSD), don't handle XML IntelliSense
            if (currentMode != EditorMode.XML) {
                logger.debug("{} mode is active, skipping XML IntelliSense", currentMode);
                // The specialized auto-completion handles the event through its own listeners
                return false;
            }

            // Handle "<" trigger for element completion
            if ("<".equals(character)) {
                logger.debug("Detected < character, triggering element completion");

                // Store the position OF the '<' character (before it was typed)
                popupStartPosition = codeArea.getCaretPosition() - 1;
                isElementCompletionContext = true; // Mark as element completion
                logger.debug("Set popupStartPosition to: {} (position of <), isElementCompletionContext = true", popupStartPosition);

                // Show the IntelliSense popup with slight delay to ensure the character is processed
                javafx.application.Platform.runLater(() -> {
                    logger.debug("Calling requestCompletions for element completion");
                    requestCompletions();
                });

                return true; // Event was handled
            }

            // Handle space trigger for attribute completion (inside XML tags)
            if (" ".equals(character)) {
                logger.debug("Detected space character, checking if inside XML tag");
                if (isInsideXmlTag()) {
                    logger.debug("Inside XML tag, triggering attribute completion");

                    // Store the current position (after the space)
                    popupStartPosition = codeArea.getCaretPosition();
                    isElementCompletionContext = false; // Mark as attribute completion

                    // Show attribute completions with slight delay
                    javafx.application.Platform.runLater(() -> {
                        logger.debug("Calling requestCompletions for attribute completion");
                        requestCompletions();
                    });

                    return true; // Event was handled
                } else {
                    logger.debug("Not inside XML tag, no completion triggered");
                }
            }

        } catch (Exception e) {
            logger.error("Error during IntelliSense trigger: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * Checks if the cursor is currently inside an XML tag (for attribute completion).
     * This enables attribute IntelliSense when the user types a space inside a tag.
     *
     * @return true if cursor is inside a tag
     */
    private boolean isInsideXmlTag() {
        try {
            int caretPosition = codeArea.getCaretPosition();
            String text = codeArea.getText();

            if (caretPosition <= 0 || caretPosition > text.length()) {
                return false;
            }

            // Look backwards from cursor to find the last "<" or ">"
            int lastOpenTag = text.lastIndexOf('<', caretPosition - 1);
            int lastCloseTag = text.lastIndexOf('>', caretPosition - 1);

            // We're inside a tag if the last "<" is more recent than the last ">"
            // and we haven't encountered a self-closing tag or end tag
            if (lastOpenTag > lastCloseTag && lastOpenTag < caretPosition) {
                // Check if it's not a closing tag (</...)
                return lastOpenTag + 1 < text.length() && text.charAt(lastOpenTag + 1) != '/';
            }

            return false;

        } catch (Exception e) {
            logger.error("Error checking if inside XML tag: {}", e.getMessage(), e);
            return false;
        }
    }






    /**
     * Checks if an XSD schema is available for IntelliSense.
     * @return true if XSD schema is available, false otherwise
     */
    private boolean isXsdSchemaAvailable() {
        try {
            if (parentXmlEditor instanceof org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor) {
                var xmlService = xmlEditor.getXmlService();
                if (xmlService != null && xmlService.getCurrentXsdFile() != null) {
                    logger.debug("XSD schema is available: {}", xmlService.getCurrentXsdFile().getName());
                    return true;
                }
            }
            logger.debug("No XSD schema available for IntelliSense");
            return false;
        } catch (Exception e) {
            logger.error("Error checking XSD schema availability: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Requests completions using the new intelligent IntelliSense system.
     * Delegates to the XmlIntelliSenseEngine for smart context-aware completions.
     */
    private void requestCompletions() {
        // IMPORTANT: Only show XML IntelliSense if we're in XML mode
        if (currentMode != EditorMode.XML) {
            logger.debug("Skipping XML IntelliSense: current mode is {}", currentMode);
            return;
        }
        
        logger.debug("IntelliSense requested - using intelligent completion system");

        // Use the new intelligent IntelliSense Engine
        if (intelliSenseEngine != null) {
            logger.debug("Delegating to XmlIntelliSenseEngine for intelligent completions");
            // The IntelliSenseEngine will handle context detection and show appropriate completions
            // It automatically detects whether we're completing elements, attributes, or values
            showEnhancedIntelliSenseCompletions();
        } else {
            logger.debug("IntelliSense Engine not available - falling back to basic completions");
            showBasicIntelliSensePopup();
        }
    }

    /**
     * Shows intelligent completions using the enhanced IntelliSense system
     */
    private void showEnhancedIntelliSenseCompletions() {
        // IMPORTANT: Only show XML IntelliSense if we're in XML mode
        if (currentMode != EditorMode.XML) {
            logger.debug("Skipping enhanced XML IntelliSense: current mode is {}", currentMode);
            return;
        }
        
        try {
            // Get current context
            int caretPos = codeArea.getCaretPosition();
            String textBeforeCaret = codeArea.getText(0, Math.min(caretPos, codeArea.getText().length()));

            // Determine completion type based on context
            if (isInElementContext(textBeforeCaret)) {
                logger.debug("Element context detected - showing element completions");
                showEnhancedElementCompletions();
            } else if (isInAttributeContext(textBeforeCaret)) {
                logger.debug("Attribute context detected - showing attribute completions");
                showEnhancedAttributeCompletions();
            } else if (isInAttributeValueContext(textBeforeCaret)) {
                logger.debug("Attribute value context detected - showing value completions");
                showEnhancedAttributeValueCompletions();
            } else {
                logger.debug("General context - showing all available completions");
                showEnhancedElementCompletions(); // Default to elements
            }
        } catch (Exception e) {
            logger.error("Error in enhanced IntelliSense: {}", e.getMessage(), e);
            // Fallback to basic popup
            showBasicIntelliSensePopup();
        }
    }

    /**
     * Show enhanced element completions with XSD integration
     */
    private void showEnhancedElementCompletions() {
        // IMPORTANT: Only show XML IntelliSense if we're in XML mode
        if (currentMode != EditorMode.XML) {
            logger.debug("Skipping enhanced element completions: current mode is {}", currentMode);
            return;
        }
        
        List<String> suggestions = new ArrayList<>();

        // Get current XPath context using the same method as the sidebar
        String currentXPath = null;
        if (parentXmlEditor != null) {
            currentXPath = parentXmlEditor.getCurrentXPath(codeArea.getText(), codeArea.getCaretPosition());
        }
        logger.debug("Current XPath context: {}", currentXPath);

        // Use the same method as the sidebar to get filtered child elements
        if (parentXmlEditor != null && parentXmlEditor.getXsdDocumentationData() != null) {
            List<String> xsdSuggestions = parentXmlEditor.getChildElementsForIntelliSense(currentXPath);
            suggestions.addAll(xsdSuggestions);
            logger.debug("Added {} XSD-based element suggestions from sidebar method for XPath '{}': {}",
                    xsdSuggestions.size(), currentXPath, xsdSuggestions);

            // Only show XSD-based suggestions when we have them
            if (!suggestions.isEmpty()) {
                showEnhancedCompletionPopup(suggestions,
                        "Valid Elements" + (currentXPath != null ? " for " + currentXPath : ""), "📝");
                return;
            }
        }

        // Fallback: Get current element context for backwards compatibility
        String currentContext = getCurrentElementContext();
        logger.debug("Fallback to element context: {}", currentContext);

        // Fallback only when no XSD schema is available or no suggestions found
        if (parentXmlEditor == null || parentXmlEditor.getXsdDocumentationData() == null) {
            // Add context-specific elements from manual configuration
            if (currentContext != null && contextElementNames.containsKey(currentContext)) {
                List<String> contextSuggestions = contextElementNames.get(currentContext);
                suggestions.addAll(contextSuggestions);
                logger.debug("Added {} context-specific element suggestions", contextSuggestions.size());
            }

            // Add available elements as final fallback when no schema is present
            if (suggestions.isEmpty() && !availableElementNames.isEmpty()) {
                suggestions.addAll(availableElementNames);
                logger.debug("Added {} fallback element suggestions", availableElementNames.size());
            }

            // Show completions in enhanced popup
            if (!suggestions.isEmpty()) {
                showEnhancedCompletionPopup(suggestions, "Elements", "📝");
            }
        } else {
            // Schema is available but no valid elements found for this context
            logger.debug("No valid child elements found for XPath '{}' according to XSD schema", currentXPath);
        }
    }

    /**
     * Show enhanced attribute completions
     */
    private void showEnhancedAttributeCompletions() {
        // IMPORTANT: Only show XML IntelliSense if we're in XML mode
        if (currentMode != EditorMode.XML) {
            logger.debug("Skipping enhanced attribute completions: current mode is {}", currentMode);
            return;
        }
        
        List<String> suggestions = new ArrayList<>();
        String currentElement = getCurrentElementFromContext();

        if (currentElement != null && xsdIntegration != null && xsdIntegration.hasSchema()) {
            // Get attributes from XSD
            var attributeInfos = xsdIntegration.getAvailableAttributes(currentElement);
            suggestions = attributeInfos.stream()
                    .map(attr -> attr.name)
                    .collect(Collectors.toList());
            logger.debug("Found {} attribute suggestions for element '{}'", suggestions.size(), currentElement);
        } else {
            // Generic attributes
            suggestions = Arrays.asList("id", "name", "type", "class", "value");
            logger.debug("Using generic attribute suggestions");
        }

        if (!suggestions.isEmpty()) {
            showEnhancedCompletionPopup(suggestions, "Attributes", "🏷️");
        }
    }

    /**
     * Show enhanced attribute value completions
     */
    private void showEnhancedAttributeValueCompletions() {
        // IMPORTANT: Only show XML IntelliSense if we're in XML mode
        if (currentMode != EditorMode.XML) {
            logger.debug("Skipping enhanced attribute value completions: current mode is {}", currentMode);
            return;
        }
        
        List<String> suggestions = new ArrayList<>();
        String currentElement = getCurrentElementFromContext();
        String currentAttribute = getCurrentAttributeFromContext();

        if (currentElement != null && currentAttribute != null &&
                xsdIntegration != null && xsdIntegration.hasSchema()) {
            // Get enumeration values from XSD
            suggestions = xsdIntegration.getAttributeEnumerationValues(currentElement, currentAttribute);
            logger.debug("Found {} enumeration values for {}@{}", suggestions.size(), currentElement, currentAttribute);
        }

        // Add some common values as fallback
        if (suggestions.isEmpty()) {
            if ("type".equals(currentAttribute)) {
                suggestions = Arrays.asList("string", "number", "boolean", "date", "text");
            } else if ("id".equals(currentAttribute)) {
                suggestions = List.of("auto-generated-id");
            }
        }

        if (!suggestions.isEmpty()) {
            showEnhancedCompletionPopup(suggestions, "Values", "💡");
        }
    }

    /**
     * Shows enhanced completion popup with modern styling and features
     */
    private void showEnhancedCompletionPopup(List<String> suggestions, String title, String icon) {
        try {
            // Remove duplicates and sort
            List<String> uniqueSuggestions = suggestions.stream()
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            // Update the existing completion popup with enhanced suggestions
            completionListView.getItems().clear();
            completionListView.getItems().addAll(uniqueSuggestions);

            // Select the first item
            if (!completionListView.getItems().isEmpty()) {
                completionListView.getSelectionModel().select(0);
            }

            // Update title
            if (intelliSensePopup.getScene() != null &&
                    intelliSensePopup.getScene().getRoot() instanceof VBox vbox) {
                if (!vbox.getChildren().isEmpty() &&
                        vbox.getChildren().get(0) instanceof Label titleLabel) {
                    titleLabel.setText(icon + " " + title);
                }
            }

            // Show the enhanced popup
            showIntelliSensePopupAtCursor();

            logger.info("Enhanced IntelliSense popup shown with {} {} suggestions",
                    uniqueSuggestions.size(), title.toLowerCase());

        } catch (Exception e) {
            logger.error("Error showing enhanced completion popup: {}", e.getMessage(), e);
        }
    }

    // Helper methods for context detection

    /**
     * Check if we're in an element context (after '<')
     */
    private boolean isInElementContext(String textBeforeCaret) {
        if (textBeforeCaret == null || textBeforeCaret.isEmpty()) return false;

        // Look for '<' followed by optional element name characters
        return textBeforeCaret.matches(".*<[a-zA-Z_][a-zA-Z0-9_:.-]*$") ||
                textBeforeCaret.endsWith("<");
    }

    /**
     * Check if we're in an attribute context (inside a tag after element name)
     */
    private boolean isInAttributeContext(String textBeforeCaret) {
        if (textBeforeCaret == null || textBeforeCaret.isEmpty()) return false;

        // Look for pattern like: <element attr or <element attr1="value" attr
        return textBeforeCaret.matches(".*<[a-zA-Z_][a-zA-Z0-9_:.-]*\\s+[^>]*[a-zA-Z_][a-zA-Z0-9_:.-]*$") ||
                textBeforeCaret.matches(".*<[a-zA-Z_][a-zA-Z0-9_:.-]*\\s+$");
    }

    /**
     * Check if we're in an attribute value context (inside quotes)
     */
    private boolean isInAttributeValueContext(String textBeforeCaret) {
        if (textBeforeCaret == null || textBeforeCaret.isEmpty()) return false;

        // Look for pattern like: attr="value or attr="
        int lastQuote = textBeforeCaret.lastIndexOf('"');
        int lastEquals = textBeforeCaret.lastIndexOf('=');

        return lastEquals > lastQuote && textBeforeCaret.substring(lastEquals).matches("\\s*\"[^\"]*$");
    }

    /**
     * Get current element from context (the element we're currently inside)
     */
    private String getCurrentElementFromContext() {
        try {
            int caretPos = codeArea.getCaretPosition();
            String text = codeArea.getText(0, Math.min(caretPos, codeArea.getText().length()));

            // Find the last unclosed element
            Pattern openTag = Pattern.compile("<([a-zA-Z_][a-zA-Z0-9_:.-]*)(?:\\s+[^>]*)?(?:>|$)");
            Pattern closeTag = Pattern.compile("</([a-zA-Z_][a-zA-Z0-9_:.-]*)>");

            Stack<String> elementStack = new Stack<>();
            Matcher openMatcher = openTag.matcher(text);
            Matcher closeMatcher = closeTag.matcher(text);

            List<TagMatch> allTags = new ArrayList<>();

            // Find all opening tags
            while (openMatcher.find()) {
                allTags.add(new TagMatch(openMatcher.start(), openMatcher.group(1), true));
            }

            // Find all closing tags
            while (closeMatcher.find()) {
                allTags.add(new TagMatch(closeMatcher.start(), closeMatcher.group(1), false));
            }

            // Sort by position
            allTags.sort((a, b) -> Integer.compare(a.position, b.position));

            // Build element stack
            for (TagMatch tag : allTags) {
                if (tag.isOpening) {
                    elementStack.push(tag.name);
                } else {
                    if (!elementStack.isEmpty() && elementStack.peek().equals(tag.name)) {
                        elementStack.pop();
                    }
                }
            }

            return elementStack.isEmpty() ? null : elementStack.peek();

        } catch (Exception e) {
            logger.debug("Error determining current element: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get current attribute from context (the attribute we're currently editing)
     */
    private String getCurrentAttributeFromContext() {
        try {
            int caretPos = codeArea.getCaretPosition();
            String text = codeArea.getText(0, Math.min(caretPos, codeArea.getText().length()));

            // Find the last attribute name before the caret
            Pattern attrPattern = Pattern.compile("\\s+([a-zA-Z_][a-zA-Z0-9_:.-]*)\\s*=\\s*\"[^\"]*$");
            Matcher matcher = attrPattern.matcher(text);

            String lastMatch = null;
            while (matcher.find()) {
                lastMatch = matcher.group(1);
            }

            return lastMatch;

        } catch (Exception e) {
            logger.debug("Error determining current attribute: {}", e.getMessage());
            return null;
        }
    }

    /**
         * Helper class for tag matching
         */
        private record TagMatch(int position, String name, boolean isOpening) {
    }

    /**
     * Shows the popup at the current cursor position.
     */
    private void showPopupAtCursor() {
        // Show the popup at the current cursor position
        if (codeArea.getScene() != null && codeArea.getScene().getWindow() != null) {
            var caretBounds = codeArea.getCaretBounds().orElse(null);
            if (caretBounds != null) {
                var screenPos = codeArea.localToScreen(caretBounds.getMinX(), caretBounds.getMaxY());
                intelliSensePopup.setX(screenPos.getX());
                intelliSensePopup.setY(screenPos.getY());

                // Show popup but ensure CodeArea keeps focus
                intelliSensePopup.show();

                // Critical: Keep focus on CodeArea so keyboard events work
                javafx.application.Platform.runLater(() -> {
                    codeArea.requestFocus();
                    logger.debug("Focus returned to CodeArea after popup show");
                });

                logger.debug("IntelliSense popup shown at cursor position");
            }
        }
    }

    /**
     * Shows basic IntelliSense popup without XSD schema (fallback mode).
     */
    private void showBasicIntelliSensePopup() {
        // IMPORTANT: Only show XML IntelliSense if we're in XML mode
        if (currentMode != EditorMode.XML) {
            logger.debug("Skipping basic XML IntelliSense: current mode is {}", currentMode);
            return;
        }
        
        logger.debug("Showing basic IntelliSense popup without XSD context");

        // Even without XSD, try to get context-specific elements if any context mapping exists
        String currentContext = getCurrentElementContext();
        List<String> suggestedElements;

        if (currentContext != null && !contextElementNames.isEmpty()) {
            logger.debug("No XSD but context mapping available - trying context-specific elements for '{}'", currentContext);
            suggestedElements = getContextSpecificElements(currentContext);
        } else if (!availableElementNames.isEmpty()) {
            logger.debug("Using available element names from XSD: {}", availableElementNames.size());
            suggestedElements = availableElementNames;
        } else {
            logger.debug("No context available - using generic element names");
            suggestedElements = Arrays.asList("element", "item", "data", "value", "content", "name", "id", "type");
        }

        // Update the list view with suggested elements
        completionListView.getItems().clear();
        completionListView.getItems().addAll(suggestedElements);

        // Select the first item
        if (!completionListView.getItems().isEmpty()) {
            completionListView.getSelectionModel().select(0);
        }

        // Show the popup at the current cursor position
        showIntelliSensePopupAtCursor();
    }

    /**
     * Shows IntelliSense popup with XSD-based completion.
     */
    private void showManualIntelliSensePopup() {
        // IMPORTANT: Only show XML IntelliSense if we're in XML mode
        if (currentMode != EditorMode.XML) {
            logger.debug("Skipping manual XML IntelliSense: current mode is {}", currentMode);
            return;
        }
        
        if (enhancedIntelliSenseEnabled) {
            showEnhancedIntelliSensePopup();
        } else {
            showBasicIntelliSensePopup();
        }
    }

    /**
     * Shows the enhanced IntelliSense popup with all advanced features
     */
    private void showEnhancedIntelliSensePopup() {
        // IMPORTANT: Only show XML IntelliSense if we're in XML mode
        if (currentMode != EditorMode.XML) {
            logger.debug("Skipping enhanced XML IntelliSense popup: current mode is {}", currentMode);
            return;
        }
        
        try {
            logger.debug("Triggering Enhanced IntelliSense popup");

            // For now, enhance the basic popup with improved suggestions
            String currentContext = getCurrentElementContext();
            List<String> contextSpecificElements = getEnhancedContextSpecificElements(currentContext);

            // Update the list view with enhanced context-specific elements
            completionListView.getItems().clear();
            completionListView.getItems().addAll(contextSpecificElements);

            // Select the first item
            if (!contextSpecificElements.isEmpty()) {
                completionListView.getSelectionModel().select(0);
            }

            logger.debug("Enhanced completion list updated with {} enhanced elements", contextSpecificElements.size());

            // Show the popup at the current cursor position
            showIntelliSensePopupAtCursor();

        } catch (Exception e) {
            logger.error("Error showing enhanced IntelliSense popup: {}", e.getMessage(), e);
            // Fallback to basic popup
            showBasicIntelliSensePopup();
        }
    }

    /**
     * Get enhanced context-specific elements using multiple sources
     */
    private List<String> getEnhancedContextSpecificElements(String currentContext) {
        Set<String> allElements = new LinkedHashSet<>();

        try {
            // 1. Get XSD-based elements if available
            if (xsdIntegration != null && xsdIntegration.hasSchema()) {
                List<String> xsdElements = xsdIntegration.getAvailableElements();
                if (!xsdElements.isEmpty()) {
                    allElements.addAll(xsdElements);
                    logger.debug("Added {} XSD elements", xsdElements.size());
                }
            }

            // 2. Get basic context elements
            List<String> basicElements = getContextSpecificElements(currentContext);
            allElements.addAll(basicElements);
            logger.debug("Added {} basic context elements", basicElements.size());

            // 3. Apply fuzzy search if enabled and there's a partial input
            if (fuzzySearch != null) {
                String prefix = extractCurrentPrefix();
                if (prefix != null && !prefix.trim().isEmpty()) {
                    // Filter elements using fuzzy search
                    List<String> elementsList = new ArrayList<>(allElements);
                    // For now, simple contains filter
                    elementsList = elementsList.stream()
                            .filter(elem -> elem.toLowerCase().contains(prefix.toLowerCase()))
                            .collect(java.util.stream.Collectors.toList());
                    allElements = new LinkedHashSet<>(elementsList);
                    logger.debug("Applied fuzzy filter for prefix '{}', {} elements remain", prefix, allElements.size());
                }
            }

        } catch (Exception e) {
            logger.error("Error generating enhanced elements: {}", e.getMessage(), e);
            // Fallback to basic elements
            return getContextSpecificElements(currentContext);
        }

        return new ArrayList<>(allElements);
    }

    /**
     * Extract current prefix being typed for completion
     */
    private String extractCurrentPrefix() {
        try {
            int caretPos = codeArea.getCaretPosition();
            String text = codeArea.getText();

            if (caretPos <= 0) return null;

            // Look backwards for the start of the current word
            int start = caretPos - 1;
            while (start >= 0) {
                char c = text.charAt(start);
                if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != ':') {
                    break;
                }
                start--;
            }

            return text.substring(start + 1, caretPos);

        } catch (Exception e) {
            logger.debug("Error extracting prefix: {}", e.getMessage());
            return null;
        }
    }


    /**
     * Shows the IntelliSense popup at the current cursor position.
     */
    private void showIntelliSensePopupAtCursor() {
        if (codeArea.getScene() != null && codeArea.getScene().getWindow() != null) {
            var caretBounds = codeArea.getCaretBounds().orElse(null);
            if (caretBounds != null) {
                var screenPos = codeArea.localToScreen(caretBounds.getMinX(), caretBounds.getMaxY());
                intelliSensePopup.setX(screenPos.getX());
                intelliSensePopup.setY(screenPos.getY());
                intelliSensePopup.show();
                logger.debug("IntelliSense popup shown at cursor position");
            }
        }
    }

    /**
     * Gets the current element context (parent element name) at the cursor position.
     *
     * @return The name of the current parent element, or null if not found
     */
    private String getCurrentElementContext() {
        try {
            String text = codeArea.getText();
            int position = codeArea.getCaretPosition();

            if (position <= 0 || position > text.length()) {
                return null;
            }

            // Find the current element by looking backwards from the cursor position
            String textBeforeCursor = text.substring(0, position);

            // Use a stack to track element nesting
            java.util.Stack<String> elementStack = new java.util.Stack<>();

            // Create a list to store all tags with their positions
            java.util.List<TagInfo> allTags = new java.util.ArrayList<>();

            // Find all opening tags
            Matcher openMatcher = OPEN_TAG_PATTERN.matcher(textBeforeCursor);
            while (openMatcher.find()) {
                String elementName = openMatcher.group(1);
                // Skip self-closing tags
                String fullMatch = openMatcher.group(0);
                if (!fullMatch.endsWith("/>")) {
                    allTags.add(new TagInfo(elementName, openMatcher.start(), true));
                }
            }

            // Find all closing tags
            Matcher closeMatcher = CLOSE_TAG_PATTERN.matcher(textBeforeCursor);
            while (closeMatcher.find()) {
                String elementName = closeMatcher.group(1);
                allTags.add(new TagInfo(elementName, closeMatcher.start(), false));
            }

            // Sort tags by position to process them chronologically
            allTags.sort((a, b) -> Integer.compare(a.position, b.position));

            // Process tags in chronological order
            for (TagInfo tag : allTags) {
                if (tag.isOpening) {
                    elementStack.push(tag.name);
                } else {
                    // Find and remove matching opening tag
                    if (!elementStack.isEmpty() && elementStack.peek().equals(tag.name)) {
                        elementStack.pop();
                    }
                }
            }

            // Return the current parent element (top of stack)
            String result = elementStack.isEmpty() ? null : elementStack.peek();
            logger.debug("getCurrentElementContext result: '{}' (stack size: {})", result, elementStack.size());
            return result;

        } catch (Exception e) {
            logger.error("Error determining current context: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
         * Helper class to track tag information for context detection
         */
        private record TagInfo(String name, int position, boolean isOpening) {
    }

    /**
     * Gets context-specific element names based on the current parent element.
     *
     * @param parentElement The parent element name
     * @return List of child element names for the given parent
     */
    private List<String> getContextSpecificElements(String parentElement) {
        logger.debug("Getting context-specific elements for parent: {}", parentElement);

        // PRIORITY 1: Always try XSD-based lookup first (regardless of parentElement)
        if (parentXmlEditor instanceof org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor) {
            try {
                List<String> sidebarElements = getSidebarChildElements(xmlEditor, parentElement);
                if (sidebarElements != null && !sidebarElements.isEmpty()) {
                    logger.debug("SUCCESS: Found {} XSD-based elements: {}", sidebarElements.size(), sidebarElements);
                    return sidebarElements;
                } else {
                    logger.debug("XSD lookup returned null or empty for parentElement: {}", parentElement);
                }
            } catch (Exception e) {
                logger.error("Error in XSD-based element lookup: {}", e.getMessage(), e);
            }
        } else {
            logger.debug("parentXmlEditor is null or wrong type");
        }

        // FALLBACK: Use static context mapping
        logger.debug("Using static context mapping, available keys: {}", contextElementNames.keySet());
        
        if (parentElement == null) {
            // If no parent context, return root-level elements (but smaller set)
            List<String> rootElements = contextElementNames.getOrDefault("root", Collections.emptyList());
            if (rootElements.isEmpty()) {
                rootElements = Arrays.asList("FundsXML4", "root", "document", "data");
            }
            logger.debug("No parent context - returning limited root elements: {}", rootElements.size());
            return rootElements;
        }

        // Get child elements for the current parent
        List<String> childElements = contextElementNames.get(parentElement);
        if (childElements != null && !childElements.isEmpty()) {
            logger.debug("Found {} child elements for parent '{}': {}", childElements.size(), parentElement, childElements);
            return childElements;
        }

        // Fallback: try to find if the parent element is actually a child of another element
        // This handles cases where the element context detection might not be perfect
        for (Map.Entry<String, List<String>> entry : contextElementNames.entrySet()) {
            if (entry.getValue().contains(parentElement)) {
                // Found parent element as a child, so maybe we can suggest its siblings or common elements
                logger.debug("Parent '{}' found as child of '{}', returning siblings: {}", parentElement, entry.getKey(), entry.getValue());
                return entry.getValue();
            }
        }

        // Final fallback - return minimal set
        logger.debug("No specific children found for parent '{}' - using minimal fallback", parentElement);
        return Arrays.asList("element", "item", "data", "value", "content", "name", "id", "type");
    }

    /**
     * Gets child elements using the same logic as the XmlEditor sidebar.
     * This ensures IntelliSense shows the same elements as "Possible Child Elements" in sidebar.
     */
    private List<String> getSidebarChildElements(org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor, String parentElement) {
        try {
            logger.debug("getSidebarChildElements called with parentElement: {}", parentElement);

            // Get current cursor position and build XPath
            int caretPosition = codeArea.getCaretPosition();
            String text = codeArea.getText();
            logger.debug("Current cursor position: {}, text length: {}", caretPosition, text.length());

            // Build XPath for current cursor position
            String currentXPath = buildXPathForCurrentPosition(text, caretPosition);
            logger.debug("Built XPath for current position: '{}'", currentXPath);

            if (currentXPath == null || currentXPath.trim().isEmpty()) {
                logger.debug("Could not determine XPath for current position, returning null");
                return null;
            }

            // Use XmlEditor's child element lookup method (same as sidebar uses)
            List<String> childElements = getChildElementsFromXsdByXPath(xmlEditor, currentXPath);
            logger.debug("getChildElementsFromXsdByXPath returned: {} elements",
                    childElements != null ? childElements.size() : "null");

            if (childElements != null && !childElements.isEmpty()) {
                // Clean up the element names - remove formatting and extract just names
                List<String> cleanedElements = extractCleanElementNames(childElements);
                logger.debug("Cleaned element names: {}", cleanedElements);
                return cleanedElements;
            } else {
                logger.debug("No child elements found for XPath: {}", currentXPath);
            }

            return null;
        } catch (Exception e) {
            logger.error("Error in getSidebarChildElements: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Builds XPath for the current cursor position to determine context.
     */
    private String buildXPathForCurrentPosition(String text, int caretPosition) {
        try {
            // Similar to getCurrentElementContext but returns XPath instead of just element name
            java.util.Stack<String> elementStack = new java.util.Stack<>();

            // Find all opening and closing tags before cursor
            String textBeforeCursor = text.substring(0, caretPosition);

            java.util.regex.Matcher openMatcher = OPEN_TAG_PATTERN.matcher(textBeforeCursor);
            java.util.List<java.util.regex.MatchResult> openTags = new java.util.ArrayList<>();
            while (openMatcher.find()) {
                openTags.add(openMatcher.toMatchResult());
            }

            java.util.regex.Matcher closeMatcher = CLOSE_TAG_PATTERN.matcher(textBeforeCursor);
            java.util.List<java.util.regex.MatchResult> closeTags = new java.util.ArrayList<>();
            while (closeMatcher.find()) {
                closeTags.add(closeMatcher.toMatchResult());
            }

            // Build element stack by processing tags in order
            int openIndex = 0, closeIndex = 0;
            while (openIndex < openTags.size() || closeIndex < closeTags.size()) {
                boolean takeOpen = false;

                if (openIndex >= openTags.size()) {
                    takeOpen = false;
                } else if (closeIndex >= closeTags.size()) {
                    takeOpen = true;
                } else {
                    takeOpen = openTags.get(openIndex).start() < closeTags.get(closeIndex).start();
                }

                if (takeOpen) {
                    String tagName = openTags.get(openIndex).group(1);
                    if (tagName != null && !tagName.trim().isEmpty()) {
                        elementStack.push(tagName.trim());
                    }
                    openIndex++;
                } else {
                    if (!elementStack.isEmpty()) {
                        elementStack.pop();
                    }
                    closeIndex++;
                }
            }

            // Build XPath from element stack
            if (elementStack.isEmpty()) {
                return "/";
            }

            StringBuilder xpath = new StringBuilder();
            for (String element : elementStack) {
                xpath.append("/").append(element);
            }

            return xpath.toString();
        } catch (Exception e) {
            logger.error("Error building XPath for current position: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets child elements from XSD using XPath (same method as XmlEditor sidebar uses).
     */
    private List<String> getChildElementsFromXsdByXPath(org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor, String xpath) {
        try {
            logger.debug("Getting child elements from XmlEditor for XPath: {}", xpath);

            // Use the new public method in XmlEditor
            List<String> childElements = xmlEditor.getChildElementsForIntelliSense(xpath);

            if (childElements != null && !childElements.isEmpty()) {
                logger.debug("Found {} child elements from XmlEditor: {}", childElements.size(), childElements);
                return childElements;
            }

            return null;

        } catch (Exception e) {
            logger.error("Error getting child elements from XSD by XPath: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extracts element name from XPath (e.g., "/root/child" -> "child").
     */
    private String getElementNameFromXPath(String xpath) {
        if (xpath == null || xpath.trim().isEmpty() || xpath.equals("/")) {
            return null;
        }

        String[] parts = xpath.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    /**
     * Cleans up element names from display format to simple names for IntelliSense.
     */
    private List<String> extractCleanElementNames(List<String> displayElements) {
        if (displayElements == null) return null;

        return displayElements.stream()
                .map(this::extractElementNameFromDisplay)
                .filter(name -> name != null && !name.trim().isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Extracts element name from display text (e.g., "elementName (type: string)" -> "elementName").
     */
    private String extractElementNameFromDisplay(String displayText) {
        if (displayText == null) return null;

        // Handle different display formats
        String text = displayText.trim();

        // Format: "elementName (type: ...)" -> "elementName"  
        int parenIndex = text.indexOf(" (");
        if (parenIndex > 0) {
            return text.substring(0, parenIndex);
        }

        // Format: "elementName - description" -> "elementName"
        int dashIndex = text.indexOf(" - ");
        if (dashIndex > 0) {
            return text.substring(0, dashIndex);
        }

        // Format: just element name
        return text;
    }

    /**
     * Hides the IntelliSense popup.
     */
    private void hideIntelliSensePopup() {
        if (intelliSensePopup.isShowing()) {
            intelliSensePopup.close();
        }
    }

    /**
     * Handles navigation in the IntelliSense popup.
     *
     * @param event The key event
     */
    private void handlePopupNavigation(KeyEvent event) {
        int currentIndex = completionListView.getSelectionModel().getSelectedIndex();
        int itemCount = completionListView.getItems().size();

        if (event.getCode() == KeyCode.UP) {
            int newIndex = (currentIndex - 1 + itemCount) % itemCount;
            completionListView.getSelectionModel().select(newIndex);
        } else if (event.getCode() == KeyCode.DOWN) {
            int newIndex = (currentIndex + 1) % itemCount;
            completionListView.getSelectionModel().select(newIndex);
        }
    }

    /**
     * Selects the currently highlighted completion item and creates complete XML tags.
     */
    private void selectCompletionItem() {
        String selectedItem = completionListView.getSelectionModel().getSelectedItem();
        logger.debug("selectCompletionItem called with selectedItem: '{}'", selectedItem);
        logger.debug("popupStartPosition: {}", popupStartPosition);

        if (selectedItem != null) {
            // Check if this is enumeration completion
            if (currentElementTextInfo != null) {
                // Replace the element text content with the selected enumeration value
                logger.debug("Enumeration completion - replacing text content from {} to {}",
                        currentElementTextInfo.startPosition, currentElementTextInfo.endPosition);
                codeArea.replaceText(currentElementTextInfo.startPosition, currentElementTextInfo.endPosition, selectedItem);
                codeArea.moveTo(currentElementTextInfo.startPosition + selectedItem.length());

                // Clear enumeration context
                currentElementTextInfo = null;

                // Hide the popup
                hideIntelliSensePopup();
                return;
            }
        }
        
        if (selectedItem != null && popupStartPosition >= 0) {
            // Remove any existing partial input between popupStartPosition and current position
            int currentPosition = codeArea.getCaretPosition();
            logger.debug("currentPosition: {}", currentPosition);

            // Use the context flag set during trigger detection
            logger.debug("isElementCompletionContext: {}", isElementCompletionContext);

            if (isElementCompletionContext) {
                // SAFER APPROACH: Find the most recent "<" and replace from there
                String tagName = selectedItem.trim();
                String completeElement = "<" + tagName + "></" + tagName + ">";

                // Find the position of the most recent "<" character before the current cursor
                String textToCursor = codeArea.getText(0, currentPosition);
                int lastBracketPos = textToCursor.lastIndexOf('<');

                if (lastBracketPos >= 0) {
                    String textBeingReplaced = codeArea.getText(lastBracketPos, currentPosition);
                    String contextBefore = codeArea.getText(Math.max(0, lastBracketPos - 10), lastBracketPos);
                    String contextAfter = codeArea.getText(currentPosition, Math.min(codeArea.getLength(), currentPosition + 10));

                    logger.debug("Found '<' at position: {}", lastBracketPos);
                    logger.debug("Full context: '{}[{}]{}'", contextBefore, textBeingReplaced, contextAfter);
                    logger.debug("Replacing from pos {} to {}: '{}'", lastBracketPos, currentPosition, textBeingReplaced);
                    logger.debug("Will replace with: '{}'", completeElement);

                    // Replace only from the "<" character to current cursor position
                    codeArea.replaceText(lastBracketPos, currentPosition, completeElement);

                    // Position cursor between the opening and closing tags
                    int cursorPosition = lastBracketPos + tagName.length() + 2; // After "<tagname>"
                    codeArea.moveTo(cursorPosition);

                    logger.debug("Created complete XML element: {}", completeElement);
                    logger.debug("Cursor positioned at: {}", cursorPosition);
                } else {
                    logger.debug("No '<' found before current position - fallback to simple insertion");
                    // Fallback: just insert the tag name
                    codeArea.replaceText(popupStartPosition, currentPosition, selectedItem);
                    codeArea.moveTo(popupStartPosition + selectedItem.length());
                }
            } else {
                // For attribute completions or other contexts, just insert the selected item
                logger.debug("Not element completion - inserting selectedItem only");
                codeArea.replaceText(popupStartPosition, currentPosition, selectedItem);
                codeArea.moveTo(popupStartPosition + selectedItem.length());
            }

            // Hide the popup
            hideIntelliSensePopup();
        }
    }

    /**
     * Handles manual completion triggered by Ctrl+Space.
     * Checks if cursor is on element text content with enumeration constraints.
     */
    private boolean handleManualCompletion() {
        try {
            // If we're in a specialized mode, delegate to the appropriate auto-completion
            if (currentMode == EditorMode.SCHEMATRON && schematronAutoComplete != null) {
                logger.debug("Schematron mode is active, delegating manual completion to SchematronAutoComplete");
                schematronAutoComplete.triggerAutoComplete();
                return true;
            } else if (currentMode == EditorMode.XSD && xsdAutoComplete != null) {
                logger.debug("XSD mode is active, delegating manual completion to XsdAutoComplete");
                xsdAutoComplete.triggerAutoComplete();
                return true;
            } else if (currentMode != EditorMode.XML) {
                logger.debug("{} mode is active, but auto-completion not available", currentMode);
                return false;
            }

            int caretPosition = codeArea.getCaretPosition();
            String text = codeArea.getText();

            // Check if cursor is on element text content
            ElementTextInfo elementTextInfo = getElementTextAtCursor(caretPosition, text);
            if (elementTextInfo != null) {
                logger.debug("Found element text: {} = '{}'", elementTextInfo.elementName, elementTextInfo.textContent);

                // Get enumeration values for this element
                List<String> enumerationValues = getEnumerationValues(elementTextInfo.elementName);
                if (enumerationValues != null && !enumerationValues.isEmpty()) {
                    logger.debug("Found enumeration values: {}", enumerationValues);
                    showEnumerationCompletion(enumerationValues, elementTextInfo);
                    return true;
                } else {
                    logger.debug("No enumeration values found for element: {}", elementTextInfo.elementName);
                }
            } else {
                logger.debug("Cursor is not on element text content");
            }

        } catch (Exception e) {
            logger.error("Error during manual completion: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
         * Information about element text content at cursor position.
         */
        private record ElementTextInfo(String elementName, String textContent, int startPosition, int endPosition) {
    }

    /**
     * Analyzes the cursor position to determine if it's on element text content.
     * Example: <DataOperation>INITIAL</DataOperation>
     * ^^^^^ cursor here
     */
    private ElementTextInfo getElementTextAtCursor(int caretPosition, String text) {
        try {
            // Find the element boundaries around the cursor
            int beforeCursor = caretPosition - 1;
            int afterCursor = caretPosition;

            // Look backwards to find opening tag
            int openTagStart = -1;
            int openTagEnd = -1;
            for (int i = beforeCursor; i >= 0; i--) {
                if (text.charAt(i) == '>') {
                    openTagEnd = i;
                    break;
                } else if (text.charAt(i) == '<') {
                    // If we hit another < before >, we're not in element text
                    return null;
                }
            }

            if (openTagEnd == -1) return null;

            // Find the start of the opening tag
            for (int i = openTagEnd; i >= 0; i--) {
                if (text.charAt(i) == '<') {
                    openTagStart = i;
                    break;
                }
            }

            if (openTagStart == -1) return null;

            // Look forwards to find closing tag
            int closeTagStart = -1;
            int closeTagEnd = -1;
            for (int i = afterCursor; i < text.length(); i++) {
                if (text.charAt(i) == '<') {
                    closeTagStart = i;
                    break;
                } else if (text.charAt(i) == '>') {
                    // If we hit > before <, we're not in element text
                    return null;
                }
            }

            if (closeTagStart == -1) return null;

            // Find the end of the closing tag
            for (int i = closeTagStart; i < text.length(); i++) {
                if (text.charAt(i) == '>') {
                    closeTagEnd = i;
                    break;
                }
            }

            if (closeTagEnd == -1) return null;

            // Extract element name from opening tag
            String openingTag = text.substring(openTagStart, openTagEnd + 1);
            Matcher matcher = ELEMENT_PATTERN.matcher(openingTag);
            if (!matcher.find()) return null;

            String elementName = matcher.group(1);

            // Extract closing tag to verify it matches
            String closingTag = text.substring(closeTagStart, closeTagEnd + 1);
            if (!closingTag.equals("</" + elementName + ">")) {
                return null; // Tags don't match
            }

            // Extract text content between tags
            int textStart = openTagEnd + 1;
            int textEnd = closeTagStart;

            if (textStart >= textEnd) return null; // No text content

            // Check if cursor is within the text content
            if (caretPosition < textStart || caretPosition > textEnd) {
                return null;
            }

            String textContent = text.substring(textStart, textEnd);

            return new ElementTextInfo(elementName, textContent, textStart, textEnd);

        } catch (Exception e) {
            logger.error("Error analyzing cursor position: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieves enumeration values for a given element from the XSD schema.
     */
    private List<String> getEnumerationValues(String elementName) {
        try {
            if (parentXmlEditor instanceof org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor) {
                var xmlService = xmlEditor.getXmlService();
                if (xmlService != null && xmlService.getCurrentXsdFile() != null) {
                    return extractEnumerationFromXsd(xmlService, elementName);
                }
            }
        } catch (Exception e) {
            logger.error("Error getting enumeration values: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Extracts enumeration values from XSD schema for a specific element.
     */
    private List<String> extractEnumerationFromXsd(org.fxt.freexmltoolkit.service.XmlService xmlService, String elementName) {
        try {
            java.io.File xsdFile = xmlService.getCurrentXsdFile();
            if (xsdFile == null || !xsdFile.exists()) {
                return null;
            }

            // Parse XSD file
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document xsdDoc = builder.parse(xsdFile);

            // Look for element definition with enumeration
            org.w3c.dom.NodeList elements = xsdDoc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");

            for (int i = 0; i < elements.getLength(); i++) {
                org.w3c.dom.Element element = (org.w3c.dom.Element) elements.item(i);
                String name = element.getAttribute("name");

                if (elementName.equals(name)) {
                    // Found the element, look for enumeration values
                    return extractEnumerationValues(element);
                }
            }

        } catch (Exception e) {
            logger.error("Error parsing XSD for enumeration: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Extracts enumeration values from an XSD element definition.
     */
    private List<String> extractEnumerationValues(org.w3c.dom.Element element) {
        List<String> values = new ArrayList<>();

        try {
            // Look for xs:simpleType > xs:restriction > xs:enumeration
            org.w3c.dom.NodeList simpleTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");

            for (int i = 0; i < simpleTypes.getLength(); i++) {
                org.w3c.dom.Element simpleType = (org.w3c.dom.Element) simpleTypes.item(i);
                org.w3c.dom.NodeList restrictions = simpleType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");

                for (int j = 0; j < restrictions.getLength(); j++) {
                    org.w3c.dom.Element restriction = (org.w3c.dom.Element) restrictions.item(j);
                    org.w3c.dom.NodeList enumerations = restriction.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "enumeration");

                    for (int k = 0; k < enumerations.getLength(); k++) {
                        org.w3c.dom.Element enumeration = (org.w3c.dom.Element) enumerations.item(k);
                        String value = enumeration.getAttribute("value");
                        if (value != null && !value.isEmpty()) {
                            values.add(value);
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error extracting enumeration values: {}", e.getMessage(), e);
        }

        return values;
    }

    /**
     * Shows enumeration completion popup.
     */
    private void showEnumerationCompletion(List<String> enumerationValues, ElementTextInfo elementTextInfo) {
        try {
            // Store element text info for later replacement
            this.currentElementTextInfo = elementTextInfo;

            // Set up completion list
            completionListView.getItems().clear();
            completionListView.getItems().addAll(enumerationValues);

            // Select the current value if it exists in the list
            String currentValue = elementTextInfo.textContent.trim();
            if (enumerationValues.contains(currentValue)) {
                completionListView.getSelectionModel().select(currentValue);
            } else if (!enumerationValues.isEmpty()) {
                completionListView.getSelectionModel().select(0);
            }

            // Show popup at cursor position
            var caretBounds = codeArea.getCaretBounds().orElse(null);
            if (caretBounds != null) {
                var screenPos = codeArea.localToScreen(caretBounds.getMinX(), caretBounds.getMaxY());
                intelliSensePopup.setX(screenPos.getX());
                intelliSensePopup.setY(screenPos.getY());
                intelliSensePopup.show();

                // Keep focus on CodeArea
                javafx.application.Platform.runLater(() -> {
                    codeArea.requestFocus();
                });

                logger.debug("Enumeration completion popup shown with {} values", enumerationValues.size());
            }

        } catch (Exception e) {
            logger.error("Error showing enumeration completion: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles tab completion for XML elements.
     *
     * @param event The key event
     * @return true if the event was handled, false otherwise
     */
    private boolean handleTabCompletion(KeyEvent event) {
        // Allow normal tab behavior for now
        logger.debug("Tab completion requested");
        return false; // Don't consume the event, allow normal tab behavior
    }

    /**
     * Handles auto-closing of XML tags when opening a new tag.
     *
     * @param event The key event
     * @return true if the event was handled, false otherwise
     */
    private boolean handleAutoClosingTag(KeyEvent event) {
        try {
            int caretPosition = codeArea.getCaretPosition();
            String text = codeArea.getText();

            // Check if we're at the end of an opening tag
            if (caretPosition > 0 && caretPosition <= text.length()) {
                String beforeCursor = text.substring(0, caretPosition);

                // Look for the last opening tag
                Pattern pattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)\\b[^>]*$");
                Matcher matcher = pattern.matcher(beforeCursor);

                if (matcher.find()) {
                    String tagName = matcher.group(1);

                    // Don't auto-close self-closing tags or closing tags
                    if (!tagName.startsWith("/") && !isSelfClosingTag(tagName)) {
                        // Insert the closing tag
                        String closingTag = "</" + tagName + ">";
                        codeArea.insertText(caretPosition, closingTag);

                        // Move cursor back to before the closing tag
                        codeArea.moveTo(caretPosition);

                        return true; // Consume the event
                    }
                }
            }

            return false;
        } catch (Exception e) {
            logger.error("Error during auto-closing tag: {}", e.getMessage(), e);
            return false;
        }
    }

    // Performance optimization: Use Set for faster lookups
    private static final Set<String> SELF_CLOSING_TAGS = Set.of(
            "br", "hr", "img", "input", "meta", "link", "area", "base", "col", "embed",
            "source", "track", "wbr", "param", "keygen", "command"
    );

    /**
     * Checks if a tag is a self-closing tag.
     *
     * @param tagName The tag name to check
     * @return true if it's a self-closing tag, false otherwise
     */
    private boolean isSelfClosingTag(String tagName) {
        return SELF_CLOSING_TAGS.contains(tagName.toLowerCase());
    }

    /**
     * Handles intelligent cursor positioning when Enter key is pressed.
     * Implements three main rules:
     * 1. After a closing XML tag: maintain indentation of previous element
     * 2. Between opening and closing tag: indent by 4 spaces more than parent
     * 3. After opening tag with children: insert new line with 4 spaces more indentation
     *
     * @return true if the event was handled and should be consumed, false for normal behavior
     */
    private boolean handleIntelligentEnterKey() {
        try {
            int caretPosition = codeArea.getCaretPosition();
            String text = codeArea.getText();

            if (caretPosition <= 0 || caretPosition > text.length()) {
                return false;
            }

            // Rule 1: Check if we're directly after a closing XML tag
            if (isAfterClosingTag(text, caretPosition)) {
                return handleEnterAfterClosingTag(text, caretPosition);
            }

            // Rule 2: Check if we're between opening and closing tags
            if (isBetweenOpeningAndClosingTag(text, caretPosition)) {
                return handleEnterBetweenTags(text, caretPosition);
            }

            // Rule 3: Check if we're after an opening tag that has child elements
            if (isAfterOpeningTagWithChildren(text, caretPosition)) {
                return handleEnterAfterOpeningTagWithChildren(text, caretPosition);
            }

            // No special handling needed
            return false;

        } catch (Exception e) {
            logger.error("Error in intelligent Enter key handling: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if the cursor is positioned directly after a closing XML tag.
     *
     * @param text          The text content
     * @param caretPosition The current cursor position
     * @return true if cursor is after a closing tag
     */
    private boolean isAfterClosingTag(String text, int caretPosition) {
        // Look backwards from cursor to find the most recent character
        for (int i = caretPosition - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (ch == '>') {
                // Found '>', check if it's a closing tag by looking backwards for '</'
                return isClosingTagEnding(text, i);
            } else if (!Character.isWhitespace(ch)) {
                // Found non-whitespace character that isn't '>'
                return false;
            }
        }
        return false;
    }

    /**
     * Checks if the cursor is between an opening and closing XML tag.
     *
     * @param text          The text content
     * @param caretPosition The current cursor position
     * @return true if cursor is between opening and closing tags
     */
    private boolean isBetweenOpeningAndClosingTag(String text, int caretPosition) {
        // Look backwards to find opening tag
        int openingTagEnd = findPreviousOpeningTagEnd(text, caretPosition);
        if (openingTagEnd == -1) {
            return false;
        }

        // Look forwards to find closing tag
        int closingTagStart = findNextClosingTagStart(text, caretPosition);
        if (closingTagStart == -1) {
            return false;
        }

        // Verify that the tags match and there's no content between them
        String beforeCursor = text.substring(openingTagEnd, caretPosition).trim();
        String afterCursor = text.substring(caretPosition, closingTagStart).trim();

        return beforeCursor.isEmpty() && afterCursor.isEmpty();
    }

    /**
     * Checks if the cursor is positioned after an opening XML tag that has child elements.
     * Example: <Contact> |  (where there are child elements following)
     * <Email>...</Email>
     *
     * @param text          The text content
     * @param caretPosition The current cursor position
     * @return true if cursor is after opening tag with children, false otherwise
     */
    private boolean isAfterOpeningTagWithChildren(String text, int caretPosition) {
        try {
            // Look backwards to find the most recent '>' character
            for (int i = caretPosition - 1; i >= 0; i--) {
                char ch = text.charAt(i);
                if (ch == '>') {
                    // Found '>', check if it's from an opening tag (not closing or self-closing)
                    if (isOpeningTagEnding(text, i)) {
                        // Check if there are child elements after current position
                        return hasChildElementsAfterPosition(text, caretPosition);
                    } else {
                        return false;
                    }
                } else if (!Character.isWhitespace(ch)) {
                    // Found non-whitespace character that isn't '>'
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("Error checking if after opening tag with children: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if a '>' character ends an opening tag (not a closing or self-closing tag).
     */
    private boolean isOpeningTagEnding(String text, int gtPosition) {
        if (gtPosition <= 0) return false;

        // Check if it's a self-closing tag (ends with />)
        if (text.charAt(gtPosition - 1) == '/') {
            return false;
        }

        // Look backwards to find the opening '<'
        for (int i = gtPosition - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (ch == '<') {
                // Make sure it's not a closing tag (doesn't start with </)
                return i + 1 >= text.length() || text.charAt(i + 1) != '/';// It's an opening tag
            }
        }
        return false;
    }

    /**
     * Checks if there are child elements after the given position.
     */
    private boolean hasChildElementsAfterPosition(String text, int position) {
        // Look for the next '<' character that indicates a child element
        for (int i = position; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<') {
                // Found a tag, check if it's an element (not closing tag of current element)
                return true;
            } else if (!Character.isWhitespace(ch)) {
                // Found non-whitespace content, so there are child elements
                return true;
            }
        }
        return false;
    }

    /**
     * Handles Enter key press after a closing XML tag.
     * Creates new line with same indentation as the previous element.
     */
    private boolean handleEnterAfterClosingTag(String text, int caretPosition) {
        try {
            // Find the indentation of the current line
            int lineStart = findLineStart(text, caretPosition);
            String currentLine = text.substring(lineStart, caretPosition);
            String indentation = extractIndentation(currentLine);

            // Insert newline with same indentation
            String insertText = "\n" + indentation;
            codeArea.insertText(caretPosition, insertText);

            // Position cursor at end of inserted text
            codeArea.moveTo(caretPosition + insertText.length());

            logger.debug("Applied Enter after closing tag with indentation: '{}'", indentation);
            return true;

        } catch (Exception e) {
            logger.error("Error handling Enter after closing tag: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Handles Enter key press between opening and closing XML tags.
     * Creates new line with additional indentation (4 spaces more than parent).
     */
    private boolean handleEnterBetweenTags(String text, int caretPosition) {
        try {
            // Find the current line and its indentation
            int lineStart = findLineStart(text, caretPosition);
            String currentLine = getLineContainingPosition(text, caretPosition);
            String baseIndentation = extractIndentation(currentLine);

            // Add 4 spaces of additional indentation for the new content line
            String contentIndentation = baseIndentation + "    ";

            // Split the current position: everything before the cursor and everything after
            String beforeCursor = text.substring(0, caretPosition);
            String afterCursor = text.substring(caretPosition);

            // Insert newline with content indentation, then newline with base indentation for closing tag
            String insertText = "\n" + contentIndentation + "\n" + baseIndentation;

            // Replace the text: before cursor + inserted text + after cursor
            codeArea.replaceText(0, text.length(), beforeCursor + insertText + afterCursor);

            // Position cursor at the end of the content indentation (on the empty content line)
            int newPosition = caretPosition + contentIndentation.length() + 1; // +1 for first newline
            codeArea.moveTo(newPosition);

            logger.debug("Applied Enter between tags with content indentation: '{}'", contentIndentation);
            return true;

        } catch (Exception e) {
            logger.error("Error handling Enter between tags: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Handles Enter key press after an opening XML tag that has child elements.
     * Creates new line with indentation before the existing child content.
     * Example: <Contact> | -> <Contact>
     *          <Email>...       |
     *                           <Email>...
     */
    private boolean handleEnterAfterOpeningTagWithChildren(String text, int caretPosition) {
        try {
            // Find the current line and its indentation
            int lineStart = findLineStart(text, caretPosition);
            String currentLine = getLineContainingPosition(text, caretPosition);
            String baseIndentation = extractIndentation(currentLine);

            // Add 4 spaces of additional indentation for the new content line
            String contentIndentation = baseIndentation + "    ";

            // Insert newline with content indentation
            String insertText = "\n" + contentIndentation;
            codeArea.insertText(caretPosition, insertText);

            // Position cursor at end of inserted text
            codeArea.moveTo(caretPosition + insertText.length());

            logger.debug("Applied Enter after opening tag with children, indentation: '{}'", contentIndentation);
            return true;

        } catch (Exception e) {
            logger.error("Error handling Enter after opening tag with children: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Helper method to check if a '>' character ends a closing tag.
     */
    private boolean isClosingTagEnding(String text, int gtPosition) {
        // Look backwards from '>' to find '</'
        for (int i = gtPosition - 1; i >= 1; i--) {
            char ch = text.charAt(i);
            if (ch == '/' && i > 0 && text.charAt(i - 1) == '<') {
                return true; // Found '</'
            } else if (ch == '<') {
                return false; // Found '<' without preceding '/'
            }
        }
        return false;
    }

    /**
     * Finds the position of the end of the previous opening tag.
     */
    private int findPreviousOpeningTagEnd(String text, int fromPosition) {
        for (int i = fromPosition - 1; i >= 0; i--) {
            if (text.charAt(i) == '>') {
                // Check if this is an opening tag (not a closing tag or self-closing tag)
                if (!isClosingTagEnding(text, i) && !isSelfClosingTagEnding(text, i)) {
                    return i + 1; // Return position after '>'
                }
            }
        }
        return -1;
    }

    /**
     * Finds the position of the start of the next closing tag.
     */
    private int findNextClosingTagStart(String text, int fromPosition) {
        for (int i = fromPosition; i < text.length() - 1; i++) {
            if (text.charAt(i) == '<' && text.charAt(i + 1) == '/') {
                return i; // Return position of '<'
            }
        }
        return -1;
    }

    /**
     * Checks if a '>' character ends a self-closing tag.
     */
    private boolean isSelfClosingTagEnding(String text, int gtPosition) {
        return gtPosition > 0 && text.charAt(gtPosition - 1) == '/';
    }

    /**
     * Finds the start position of the line containing the given position.
     */
    private int findLineStart(String text, int position) {
        for (int i = position - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        return 0; // Beginning of text
    }

    /**
     * Gets the complete line containing the given position.
     */
    private String getLineContainingPosition(String text, int position) {
        int lineStart = findLineStart(text, position);
        int lineEnd = text.indexOf('\n', position);
        if (lineEnd == -1) {
            lineEnd = text.length();
        }
        return text.substring(lineStart, lineEnd);
    }

    /**
     * Extracts the indentation (leading whitespace) from a line.
     */
    private String extractIndentation(String line) {
        StringBuilder indentation = new StringBuilder();
        for (char ch : line.toCharArray()) {
            if (ch == ' ' || ch == '\t') {
                indentation.append(ch);
            } else {
                break;
            }
        }
        return indentation.toString();
    }

    /**
     * Initializes the status line at the bottom of the editor.
     */
    private void initializeStatusLine() {
        // Style the status line
        statusLine.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ccc; -fx-border-width: 1px 0 0 0; -fx-padding: 5px 10px;");
        statusLine.setSpacing(20);
        statusLine.setAlignment(Pos.CENTER_LEFT);

        // Style the labels
        String labelStyle = "-fx-font-size: 11px; -fx-text-fill: #666;";
        cursorPositionLabel.setStyle(labelStyle);
        encodingLabel.setStyle(labelStyle);
        lineSeparatorLabel.setStyle(labelStyle);
        indentationLabel.setStyle(labelStyle);

        // Initialize indent label with current setting
        updateIndentationLabel();

        // Add labels to status line
        statusLine.getChildren().addAll(
                cursorPositionLabel,
                createSeparator(),
                encodingLabel,
                createSeparator(),
                lineSeparatorLabel,
                createSeparator(),
                indentationLabel
        );

        // Set up cursor position tracking
        setupCursorPositionTracking();

        // Initialize status values
        updateStatusLine();
    }

    /**
     * Creates a visual separator for the status line.
     */
    private Label createSeparator() {
        Label separator = new Label("|");
        separator.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");
        return separator;
    }

    /**
     * Sets up cursor position tracking to update the status line.
     */
    private void setupCursorPositionTracking() {
        // Track caret position changes
        codeArea.caretPositionProperty().addListener((observable, oldValue, newValue) -> {
            updateCursorPosition();
        });

        // Track text changes to update indentation info and folding regions
        codeArea.textProperty().addListener((observable, oldText, newText) -> {
            updateIndentationInfo(newText);
            updateFoldingRegions(newText);
        });
    }

    /**
     * Updates the cursor position display in the status line.
     * Performance optimized to avoid unnecessary Platform.runLater calls.
     */
    private void updateCursorPosition() {
        try {
            int caretPosition = codeArea.getCaretPosition();
            String text = codeArea.getText();

            if (text == null) {
                return;
            }

            // Calculate line and column
            int line = 1;
            int column = 1;
            int length = Math.min(caretPosition, text.length());

            for (int i = 0; i < length; i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }

            // Only update if we're on the JavaFX Application Thread
            if (Platform.isFxApplicationThread()) {
                cursorPositionLabel.setText("Line: " + line + ", Column: " + column);
            } else {
                // Capture final variables for lambda
                final int finalLine = line;
                final int finalColumn = column;
                Platform.runLater(() -> {
                    cursorPositionLabel.setText("Line: " + finalLine + ", Column: " + finalColumn);
                });
            }

        } catch (Exception e) {
            logger.error("Error updating cursor position: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates the indentation information based on the current text.
     */
    private void updateIndentationInfo(String text) {
        try {
            if (text == null || text.isEmpty()) {
                return;
            }

            // Analyze indentation patterns in the text
            int[] indentationCounts = analyzeIndentation(text);
            int detectedSize = detectIndentationSize(indentationCounts);
            boolean detectedUseSpaces = detectIndentationType(text);

            if (detectedSize > 0) {
                currentIndentationSize = detectedSize;
            }
            useSpaces = detectedUseSpaces;

            if (Platform.isFxApplicationThread()) {
                String indentType = useSpaces ? "spaces" : "tabs";
                indentationLabel.setText(currentIndentationSize + " " + indentType);
            } else {
                Platform.runLater(() -> {
                    String indentType = useSpaces ? "spaces" : "tabs";
                    indentationLabel.setText(currentIndentationSize + " " + indentType);
                });
            }

        } catch (Exception e) {
            logger.error("Error updating indentation info: {}", e.getMessage(), e);
        }
    }

    /**
     * Analyzes the indentation patterns in the text.
     * Performance optimized to avoid repeated string operations.
     */
    private int[] analyzeIndentation(String text) {
        int[] counts = new int[9]; // Count indentations of size 1-8
        int length = text.length();
        int lineStart = 0;

        for (int i = 0; i < length; i++) {
            if (text.charAt(i) == '\n' || i == length - 1) {
                // Process line from lineStart to i
                int indent = 0;
                boolean hasContent = false;

                for (int j = lineStart; j < i; j++) {
                    char c = text.charAt(j);
                    if (c == ' ') {
                        indent++;
                    } else if (c == '\t') {
                        indent += 4; // Treat tab as 4 spaces for calculation
                        break;
                    } else if (!Character.isWhitespace(c)) {
                        hasContent = true;
                        break;
                    }
                }

                // Only count lines with content
                if (hasContent) {
                    // Count common indentation sizes (2, 4, 8)
                    if (indent % 8 == 0 && indent > 0) counts[8]++;
                    else if (indent % 4 == 0 && indent > 0) counts[4]++;
                    else if (indent % 2 == 0 && indent > 0) counts[2]++;
                }

                lineStart = i + 1;
            }
        }

        return counts;
    }

    /**
     * Detects the most likely indentation size based on analysis.
     */
    private int detectIndentationSize(int[] counts) {
        int maxCount = 0;
        int detectedSize = 4; // Default to 4 spaces

        for (int i = 2; i < counts.length; i++) {
            if (counts[i] > maxCount) {
                maxCount = counts[i];
                detectedSize = i;
            }
        }

        return detectedSize;
    }

    /**
     * Detects whether the text uses spaces or tabs for indentation.
     * Performance optimized to avoid repeated string operations.
     */
    private boolean detectIndentationType(String text) {
        int spaceCount = 0;
        int tabCount = 0;
        int length = text.length();
        int lineStart = 0;

        for (int i = 0; i < length; i++) {
            if (text.charAt(i) == '\n' || i == length - 1) {
                // Process line from lineStart to i
                boolean hasContent = false;

                for (int j = lineStart; j < i; j++) {
                    char c = text.charAt(j);
                    if (c == ' ') {
                        spaceCount++;
                    } else if (c == '\t') {
                        tabCount++;
                        break;
                    } else if (!Character.isWhitespace(c)) {
                        hasContent = true;
                        break;
                    }
                }

                lineStart = i + 1;
            }
        }

        return spaceCount >= tabCount; // Default to spaces if equal
    }

    /**
     * Updates all status line information.
     */
    private void updateStatusLine() {
        updateCursorPosition();
        updateFileEncoding();
        updateLineSeparator();
        updateIndentationInfo(codeArea.getText());
    }

    /**
     * Updates the folding regions by analyzing XML structure.
     */
    private void updateFoldingRegions(String text) {
        try {
            if (text == null || text.isEmpty()) {
                foldingRegions.clear();
                return;
            }

            // Clear existing folding regions
            foldingRegions.clear();

            // Calculate new folding regions based on XML structure
            calculateXmlFoldingRegions(text);

            // Update the paragraph graphic factory to reflect new folding regions
            Platform.runLater(() -> {
                codeArea.setParagraphGraphicFactory(createParagraphGraphicFactory());
            });

        } catch (Exception e) {
            logger.error("Error updating folding regions: {}", e.getMessage(), e);
        }
    }

    /**
     * Calculates folding regions by parsing XML structure.
     */
    private void calculateXmlFoldingRegions(String text) {
        String[] lines = text.split("\n");
        Stack<XmlElement> elementStack = new Stack<>();

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("<!--")) {
                continue;
            }

            // Find XML tags in the line
            Pattern tagPattern = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9_:]*)[^>]*(/?)>");
            Matcher matcher = tagPattern.matcher(line);

            while (matcher.find()) {
                boolean isClosingTag = !matcher.group(1).isEmpty();
                String tagName = matcher.group(2);
                boolean isSelfClosing = !matcher.group(3).isEmpty() || line.contains("/>");

                if (isClosingTag) {
                    // Handle closing tag
                    if (!elementStack.isEmpty() && elementStack.peek().name.equals(tagName)) {
                        XmlElement element = elementStack.pop();
                        // Only create folding region if element spans multiple lines
                        if (lineIndex > element.startLine) {
                            foldingRegions.put(element.startLine, lineIndex);
                        }
                    }
                } else if (!isSelfClosing) {
                    // Handle opening tag (not self-closing)
                    elementStack.push(new XmlElement(tagName, lineIndex));
                }
            }
        }
    }

    /**
         * Helper class to represent XML elements during folding analysis.
         */
        private record XmlElement(String name, int startLine) {
    }

    /**
     * Updates the file encoding display.
     * Performance optimized to avoid unnecessary Platform.runLater calls.
     */
    private void updateFileEncoding() {
        if (Platform.isFxApplicationThread()) {
            encodingLabel.setText(currentEncoding);
        } else {
            Platform.runLater(() -> {
                encodingLabel.setText(currentEncoding);
            });
        }
    }

    /**
     * Updates the line separator display.
     * Performance optimized to avoid unnecessary Platform.runLater calls.
     */
    private void updateLineSeparator() {
        if (Platform.isFxApplicationThread()) {
            lineSeparatorLabel.setText(currentLineSeparator);
        } else {
            Platform.runLater(() -> {
                lineSeparatorLabel.setText(currentLineSeparator);
            });
        }
    }

    /**
     * Sets the file encoding for display in the status line.
     */
    public void setFileEncoding(String encoding) {
        if (encoding != null && !encoding.isEmpty()) {
            this.currentEncoding = encoding;
            updateFileEncoding();
        }
    }

    /**
     * Sets the line separator type for display in the status line.
     */
    public void setLineSeparator(String lineSeparator) {
        if (lineSeparator != null) {
            switch (lineSeparator) {
                case "\n" -> this.currentLineSeparator = "LF";
                case "\r\n" -> this.currentLineSeparator = "CRLF";
                case "\r" -> this.currentLineSeparator = "CR";
                default -> this.currentLineSeparator = "LF";
            }
            updateLineSeparator();
        }
    }

    /**
     * Detects and sets the line separator based on the text content.
     */
    public void detectAndSetLineSeparator(String text) {
        if (text == null) return;

        if (text.contains("\r\n")) {
            setLineSeparator("\r\n");
        } else if (text.contains("\n")) {
            setLineSeparator("\n");
        } else if (text.contains("\r")) {
            setLineSeparator("\r");
        }
    }

    // ========== Enhanced IntelliSense Methods ==========

    /**
     * Update XSD integration when parent XML editor is set
     */
    public void updateXsdIntegration() {
        if (xsdIntegration != null && parentXmlEditor != null) {
            // Note: XSD integration will be updated when XmlEditor provides schema data
            logger.debug("XSD integration ready for updates from parent editor");

            // Update IntelliSense engine with XSD integration
            if (intelliSenseEngine != null) {
                logger.debug("IntelliSense Engine ready for XSD integration");
            }
        }
    }

    // ========== Automatic Tag Completion ==========

    /**
     * Handles automatic tag completion when user types ">"
     * Automatically generates closing tags and positions cursor between them
     */
    private void handleAutomaticTagCompletion(String oldText, String newText) {
        if (oldText == null || newText == null) {
            return;
        }

        // Check if a ">" character was just added
        if (newText.length() != oldText.length() + 1) {
            return; // Not a single character addition
        }

        int caretPosition = codeArea.getCaretPosition();
        if (caretPosition == 0 || caretPosition > newText.length()) {
            return;
        }

        // Check if the last typed character was ">"
        char lastChar = newText.charAt(caretPosition - 1);
        if (lastChar != '>') {
            return;
        }

        // Find the opening tag before the cursor
        String tagName = findOpeningTagBeforeCursor(newText, caretPosition);
        if (tagName != null && !tagName.isEmpty()) {
            // Check if this is not a self-closing tag or XML declaration
            if (!isSelfClosingTag(tagName) && !isSpecialTag(tagName)) {
                // Generate content to insert
                StringBuilder contentToInsert = new StringBuilder();
                int finalCursorPosition = caretPosition;

                // Check if XSD is available and get mandatory child elements
                List<String> mandatoryChildren = getMandatoryChildElementsFromXsd(tagName);

                if (mandatoryChildren != null && !mandatoryChildren.isEmpty()) {
                    // Get current indentation
                    String currentIndentation = getCurrentLineIndentation(newText, caretPosition);
                    String childIndentation = currentIndentation + "  "; // 2 spaces for child indentation

                    // Add line break and mandatory child elements
                    contentToInsert.append("\n");

                    for (String childElement : mandatoryChildren) {
                        // Get mandatory children of this child element recursively
                        List<String> grandchildren = getMandatoryChildElementsFromXsd(childElement);

                        contentToInsert.append(childIndentation)
                                .append("<").append(childElement).append(">");

                        if (grandchildren != null && !grandchildren.isEmpty()) {
                            // This child has mandatory children, add them recursively
                            String grandchildIndentation = childIndentation + "  ";
                            contentToInsert.append("\n");

                            for (String grandchild : grandchildren) {
                                contentToInsert.append(grandchildIndentation)
                                        .append("<").append(grandchild).append("></").append(grandchild).append(">\n");
                            }

                            contentToInsert.append(childIndentation);
                        }

                        contentToInsert.append("</").append(childElement).append(">\n");
                    }

                    contentToInsert.append(currentIndentation);
                    finalCursorPosition = caretPosition + 1; // Position after the first newline
                } else {
                    // No mandatory children, position cursor between tags as before
                    finalCursorPosition = caretPosition;
                }

                // Add the closing tag
                String closingTag = "</" + tagName + ">";
                contentToInsert.append(closingTag);

                // Insert all content at once
                String finalContent = contentToInsert.toString();
                int finalPos = finalCursorPosition;
                
                Platform.runLater(() -> {
                    codeArea.insertText(caretPosition, finalContent);
                    // Position cursor appropriately
                    codeArea.moveTo(finalPos);
                });

                if (mandatoryChildren != null && !mandatoryChildren.isEmpty()) {
                    logger.debug("Auto-completed tag with mandatory children: {} -> {} mandatory children", tagName, mandatoryChildren.size());
                } else {
                    logger.debug("Auto-completed tag: {} -> {}", tagName, closingTag);
                }
            }
        }
    }

    /**
     * Finds the opening tag name before the cursor position
     * Returns null if no valid opening tag is found
     */
    private String findOpeningTagBeforeCursor(String text, int caretPosition) {
        if (text == null || caretPosition <= 0) {
            return null;
        }

        // Look backwards from cursor position to find the opening "<"
        int openingBracketPos = -1;
        for (int i = caretPosition - 2; i >= 0; i--) { // -2 because we just typed ">"
            char ch = text.charAt(i);
            if (ch == '<') {
                openingBracketPos = i;
                break;
            } else if (ch == '>' || ch == '"' || ch == '\n') {
                // Found another closing bracket, quote, or newline before opening bracket
                return null;
            }
        }

        if (openingBracketPos == -1) {
            return null;
        }

        // Extract the tag content between < and >
        String tagContent = text.substring(openingBracketPos + 1, caretPosition - 1);

        // Remove any attributes - just get the tag name
        String tagName = tagContent.trim();
        if (tagName.isEmpty()) {
            return null;
        }

        // Split by whitespace to get only the tag name (ignore attributes)
        String[] parts = tagName.split("\\s+");
        tagName = parts[0];

        // Validate tag name (simple validation)
        if (isValidTagName(tagName)) {
            return tagName;
        }

        return null;
    }

    /**
     * Checks if the tag name is valid according to XML naming rules
     */
    private boolean isValidTagName(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return false;
        }

        // Basic XML tag name validation
        // Must start with letter or underscore, can contain letters, digits, hyphens, periods, underscores
        if (!Character.isLetter(tagName.charAt(0)) && tagName.charAt(0) != '_') {
            return false;
        }

        for (int i = 1; i < tagName.length(); i++) {
            char ch = tagName.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '.' && ch != '_' && ch != ':') {
                return false;
            }
        }

        return true;
    }


    /**
     * Checks if the tag is a special XML tag (like XML declaration, processing instruction, etc.)
     */
    private boolean isSpecialTag(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return false;
        }

        // XML declarations, processing instructions, etc.
        return tagName.startsWith("?") || tagName.startsWith("!") || tagName.contains("?");
    }

    /**
     * Gets mandatory child elements from XSD for a given element name.
     *
     * @param elementName The parent element name
     * @return List of mandatory child element names, or null if no XSD or no mandatory children
     */
    private List<String> getMandatoryChildElementsFromXsd(String elementName) {
        try {
            if (parentXmlEditor == null) {
                logger.debug("No parent XML editor available for XSD lookup");
                return null;
            }

            // parentXmlEditor is already XmlEditor type, no cast needed
            var xsdDocumentationData = parentXmlEditor.getXsdDocumentationData();
            if (xsdDocumentationData == null) {
                logger.debug("No XSD documentation data available");
                return null;
            }

            // Find the element in XSD documentation data
            Map<String, org.fxt.freexmltoolkit.domain.XsdExtendedElement> elementMap = xsdDocumentationData.getExtendedXsdElementMap();

            // Look for exact element name match or xpath ending with element name
            org.fxt.freexmltoolkit.domain.XsdExtendedElement targetElement = null;

            for (Map.Entry<String, org.fxt.freexmltoolkit.domain.XsdExtendedElement> entry : elementMap.entrySet()) {
                org.fxt.freexmltoolkit.domain.XsdExtendedElement element = entry.getValue();
                if (elementName.equals(element.getElementName())) {
                    targetElement = element;
                    break;
                }
            }

            if (targetElement == null) {
                logger.debug("Element '{}' not found in XSD documentation data", elementName);
                return null;
            }

            List<String> children = targetElement.getChildren();
            if (children == null || children.isEmpty()) {
                logger.debug("Element '{}' has no children in XSD", elementName);
                return null;
            }

            // Filter only mandatory children
            List<String> mandatoryChildren = new ArrayList<>();

            for (String childPath : children) {
                // Find the child element in the map
                org.fxt.freexmltoolkit.domain.XsdExtendedElement childElement = elementMap.get(childPath);
                if (childElement != null && childElement.isMandatory()) {
                    String childElementName = childElement.getElementName();
                    if (childElementName != null && !childElementName.startsWith("@")) { // Exclude attributes
                        mandatoryChildren.add(childElementName);
                    }
                }
            }

            if (mandatoryChildren.isEmpty()) {
                logger.debug("Element '{}' has no mandatory children", elementName);
                return null;
            }

            logger.debug("Found {} mandatory children for '{}': {}", mandatoryChildren.size(), elementName, mandatoryChildren);
            return mandatoryChildren;

        } catch (Exception e) {
            logger.error("Error getting mandatory child elements from XSD for '{}': {}", elementName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets the current line indentation by looking backwards from the cursor position.
     *
     * @param text          The full text content
     * @param caretPosition The current cursor position
     * @return The indentation string (spaces/tabs) of the current line
     */
    private String getCurrentLineIndentation(String text, int caretPosition) {
        if (text == null || caretPosition <= 0) {
            return "";
        }

        // Find the start of the current line
        int lineStart = caretPosition - 1;
        while (lineStart > 0 && text.charAt(lineStart) != '\n') {
            lineStart--;
        }
        if (text.charAt(lineStart) == '\n') {
            lineStart++; // Move past the newline character
        }

        // Count leading spaces/tabs
        StringBuilder indentation = new StringBuilder();
        for (int i = lineStart; i < text.length() && i < caretPosition; i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\t') {
                indentation.append(ch);
            } else {
                break;
            }
        }

        return indentation.toString();
    }

    /**
     * Sets up the layout with minimap support.
     */
    private void setupLayoutWithMinimap() {
        // Create horizontal container for editor and optional minimap
        editorContainer = new HBox();
        editorContainer.setSpacing(2);

        // Add editor (takes most space)
        HBox.setHgrow(virtualizedScrollPane, Priority.ALWAYS);
        editorContainer.getChildren().add(virtualizedScrollPane);

        // Add container and status line to main VBox
        this.getChildren().addAll(editorContainer, statusLine);
    }

    /**
     * Initializes the minimap component when first needed.
     */
    private void initializeMinimap() {
        if (!minimapInitialized) {
            minimapView = new MinimapView(codeArea, virtualizedScrollPane, currentErrors);
            minimapView.setMinimapVisible(false); // Start hidden
            minimapInitialized = true;
            logger.debug("Minimap initialized");
        }
    }

    /**
     * Toggles minimap visibility and initializes it if needed.
     */
    public void toggleMinimap() {
        minimapVisible = !minimapVisible;

        if (minimapVisible) {
            // Initialize minimap if not already done
            initializeMinimap();

            // Add to layout if not already added
            if (!editorContainer.getChildren().contains(minimapView)) {
                editorContainer.getChildren().add(minimapView);
            }

            minimapView.setMinimapVisible(true);
        } else {
            // Hide minimap
            if (minimapView != null) {
                minimapView.setMinimapVisible(false);
                editorContainer.getChildren().remove(minimapView);
            }
        }

        logger.debug("Minimap visibility toggled: {}", minimapVisible);
    }

    /**
     * Gets current minimap visibility state.
     */
    public boolean isMinimapVisible() {
        return minimapVisible && minimapView != null && minimapView.isMinimapVisible();
    }

    /**
     * Shows error tooltip if there's an error on the specified line.
     */
    private void showErrorTooltipIfPresent(int lineNumber, double screenX, double screenY) {
        if (currentErrors.containsKey(lineNumber) && lastTooltipLine != lineNumber) {
            hideErrorTooltip();

            String errorMessage = currentErrors.get(lineNumber);
            errorTooltip = new Tooltip(errorMessage);
            errorTooltip.setShowDelay(Duration.millis(100));
            errorTooltip.setHideDelay(Duration.millis(3000));
            errorTooltip.setAutoHide(true);
            errorTooltip.setStyle("-fx-background-color: #ffe6e6; -fx-text-fill: #d32f2f; -fx-border-color: #d32f2f; -fx-border-width: 1px;");

            lastTooltipLine = lineNumber;
            errorTooltip.show(codeArea, screenX + 10, screenY - 30);
        } else if (!currentErrors.containsKey(lineNumber)) {
            hideErrorTooltip();
        }
    }

    /**
     * Hides the error tooltip if it's currently shown.
     */
    private void hideErrorTooltip() {
        if (errorTooltip != null && errorTooltip.isShowing()) {
            errorTooltip.hide();
            errorTooltip = null;
        }
        lastTooltipLine = -1;
    }

    /**
     * Gets the XML element name at the specified screen position.
     */
    private String getElementNameAtPosition(double x, double y) {
        try {
            var hit = codeArea.hit(x, y);
            int characterIndex = hit.getCharacterIndex().orElse(-1);
            logger.debug("Hit test result: characterIndex = {}", characterIndex);

            if (characterIndex < 0) {
                logger.debug("No character at position ({}, {})", x, y);
                return null;
            }

            String text = codeArea.getText();
            if (text == null || characterIndex >= text.length()) {
                logger.debug("Invalid text or character index: text={}, index={}", (text != null ? text.length() : "null"), characterIndex);
                return null;
            }

            logger.debug("Character at position {}: '{}'", characterIndex, text.charAt(characterIndex));

            // Find the XML element at this position
            String elementName = extractElementNameAtPosition(text, characterIndex);
            logger.debug("Extracted element name: '{}'", elementName);
            return elementName;
        } catch (Exception e) {
            logger.error("Error getting element name at position: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extracts the XML element name at the given character position.
     */
    private String extractElementNameAtPosition(String text, int position) {
        logger.debug("Extracting element name at position {}", position);

        // Look backwards to find opening '<'
        int tagStart = -1;
        for (int i = position; i >= 0; i--) {
            if (text.charAt(i) == '<') {
                tagStart = i;
                break;
            } else if (text.charAt(i) == '>') {
                // We're outside a tag
                logger.debug("Found '>' before '<' - cursor is outside a tag");
                return null;
            }
        }

        if (tagStart == -1) {
            logger.debug("No opening '<' found before position");
            return null;
        }

        // Look forwards to find closing '>'
        int tagEnd = -1;
        for (int i = position; i < text.length(); i++) {
            if (text.charAt(i) == '>') {
                tagEnd = i;
                break;
            }
        }

        if (tagEnd == -1) {
            logger.debug("No closing '>' found after position");
            return null;
        }

        // Extract tag content
        String tagContent = text.substring(tagStart + 1, tagEnd);
        logger.debug("Tag content: '{}'", tagContent);

        // Skip closing tags and comments
        if (tagContent.startsWith("/") || tagContent.startsWith("!")) {
            logger.debug("Skipping closing tag or comment: '{}'", tagContent);
            return null;
        }

        // Extract element name (first word)
        String[] parts = tagContent.split("\\s+");
        logger.debug("Tag content split into {} parts: {}", parts.length, Arrays.toString(parts));
        if (parts.length > 0) {
            String elementName = parts[0];
            logger.debug("Raw element name: '{}'", elementName);

            // Remove namespace prefix for lookup
            int colonIndex = elementName.indexOf(':');
            if (colonIndex > 0) {
                elementName = elementName.substring(colonIndex + 1);
                logger.debug("Element name after removing namespace: '{}'", elementName);
            }
            return elementName;
        }

        logger.debug("No element name found in tag content");
        return null;
    }

    /**
     * Handles Ctrl+Click to navigate to XSD element definition.
     */
    private void handleGoToDefinition(javafx.scene.input.MouseEvent event) {
        try {
            logger.debug("handleGoToDefinition called at position ({}, {})", event.getX(), event.getY());

            String elementName = getElementNameAtPosition(event.getX(), event.getY());
            logger.debug("Extracted element name: '{}'", elementName);

            if (elementName == null || elementName.isEmpty()) {
                logger.debug("No element name found at position");
                return;
            }

            logger.info("Go-to-Definition triggered for element: {}", elementName);

            // Find element definition in XSD
            if (parentXmlEditor instanceof org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor) {
                logger.debug("Parent XmlEditor found, navigating to XSD definition");
                navigateToXsdDefinition(xmlEditor, elementName);
            } else {
                logger.debug("Parent XmlEditor not available: {}", parentXmlEditor);
            }
        } catch (Exception e) {
            logger.error("Error in Go-to-Definition: {}", e.getMessage(), e);
        }
    }

    /**
     * Navigates to the XSD definition of the specified element.
     */
    private void navigateToXsdDefinition(org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor, String elementName) {
        try {
            var xsdDocumentationData = xmlEditor.getXsdDocumentationData();
            if (xsdDocumentationData == null) {
                logger.debug("XSD documentation data not yet loaded - showing user notification");
                Platform.runLater(() -> {
                    var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Go-to-Definition");
                    alert.setHeaderText("XSD Schema Loading");
                    alert.setContentText("XSD schema is still being processed. Please wait a moment and try again.");
                    alert.showAndWait();
                });
                return;
            }

            // Look for element in the extended element map
            var extendedElementMap = xsdDocumentationData.getExtendedXsdElementMap();
            org.fxt.freexmltoolkit.domain.XsdExtendedElement targetElement = null;

            // Search for element by name
            for (var entry : extendedElementMap.entrySet()) {
                var element = entry.getValue();
                if (elementName.equals(element.getElementName())) {
                    targetElement = element;
                    break;
                }
            }

            if (targetElement == null) {
                // Also check global elements
                for (org.w3c.dom.Node globalElement : xsdDocumentationData.getGlobalElements()) {
                    String name = getElementNameFromNode(globalElement);
                    if (elementName.equals(name)) {
                        // Found in global elements - navigate to XSD
                        navigateToXsdFile(xmlEditor, globalElement, elementName);
                        return;
                    }
                }

                logger.debug("Element '{}' not found in XSD documentation", elementName);
                showElementNotFoundMessage(elementName);
                return;
            }

            // Navigate to the XSD definition
            navigateToXsdFile(xmlEditor, targetElement.getCurrentNode(), elementName);

        } catch (Exception e) {
            logger.error("Error navigating to XSD definition: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the element name from a DOM Node.
     */
    private String getElementNameFromNode(org.w3c.dom.Node node) {
        if (node == null) {
            return null;
        }

        // For XSD elements, check the 'name' attribute
        if (node.hasAttributes()) {
            org.w3c.dom.Node nameAttr = node.getAttributes().getNamedItem("name");
            if (nameAttr != null) {
                return nameAttr.getNodeValue();
            }
        }

        return null;
    }

    /**
     * Navigates to the XSD file and highlights the element definition.
     */
    private void navigateToXsdFile(org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor, org.w3c.dom.Node elementNode, String elementName) {
        try {
            // Get the main controller to switch to XSD tab
            if (xmlEditor.getMainController() != null) {
                var mainController = xmlEditor.getMainController();

                // Trigger XSD tab by firing the xsd button
                Platform.runLater(() -> {
                    try {
                        // Access the XSD button and fire it to switch tabs
                        var xsdButton = mainController.getClass().getDeclaredField("xsd");
                        xsdButton.setAccessible(true);
                        javafx.scene.control.Button xsdBtn = (javafx.scene.control.Button) xsdButton.get(mainController);
                        if (xsdBtn != null) {
                            xsdBtn.fire();
                            logger.info("Go-to-Definition: Navigated to XSD tab for element '{}'", elementName);
                        }
                    } catch (Exception e) {
                        logger.debug("Could not access XSD button via reflection: {}", e.getMessage());
                        // Fallback: just log the navigation
                        logger.info("Go-to-Definition: Element '{}' found in XSD", elementName);
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Error in XSD navigation: {}", e.getMessage(), e);
        }
    }

    /**
     * Shows a message when an element definition is not found.
     */
    private void showElementNotFoundMessage(String elementName) {
        Platform.runLater(() -> {
            logger.debug("Element '{}' definition not found in associated XSD", elementName);
            var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Go-to-Definition");
            alert.setHeaderText("Element Not Found");
            alert.setContentText("Definition for element '" + elementName + "' was not found in the associated XSD schema.");
            alert.showAndWait();
        });
    }

    /**
     * Cleanup method to stop file monitoring and release resources.
     * Should be called when the editor is no longer needed.
     */
    public void cleanup() {
        stopFileMonitoring();
        currentFile = null;
        lastModifiedTime = -1;
        logger.debug("XmlCodeEditor cleanup completed");
    }
}
