package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // Cache for enumeration elements from XsdDocumentationData
    // Key: XPath-like context, Value: Set of element names with enumeration
    private final Map<String, Set<String>> enumerationElementsByContext = new HashMap<>();
    private int popupStartPosition = -1;
    private boolean isElementCompletionContext = false; // Track if we're completing elements or attributes

    // Debouncing for syntax highlighting
    private javafx.animation.PauseTransition syntaxHighlightingDebouncer;

    // Background task for syntax highlighting
    private javafx.concurrent.Task<StyleSpans<Collection<String>>> syntaxHighlightingTask;

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

        // Set up the main layout
        VBox.setVgrow(virtualizedScrollPane, Priority.ALWAYS);

        this.getChildren().addAll(virtualizedScrollPane, statusLine);

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

        // Text change listener for syntax highlighting with debouncing
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            // Reset the debouncer timer
            syntaxHighlightingDebouncer.stop();
            syntaxHighlightingDebouncer.playFromStart();
        });
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
            String xmlCssUrl = getClass().getResource(xmlCssPath).toExternalForm();
            codeArea.getStylesheets().add(xmlCssUrl);
            logger.debug("Loaded XML highlighting CSS: {}", xmlCssUrl);

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
                    default -> {
                    }
                }
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
                logger.debug("Ctrl+Space pressed for manual completion");
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
            HBox hbox = new HBox(lineNumberNode, iconWrapper);
            hbox.setAlignment(Pos.TOP_LEFT); // TOP_LEFT for seamless alignment
            hbox.setSpacing(0); // Remove spacing between line number and folding icons
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
     * Requests completions using XSD-based implementation.
     * Only shows IntelliSense if XSD schema is available.
     */
    private void requestCompletions() {
        logger.debug("IntelliSense requested - checking XSD schema availability");

        // Show IntelliSense popup - prioritize XSD-based suggestions but allow fallback
        if (isXsdSchemaAvailable()) {
            logger.debug("XSD schema available - showing context-aware IntelliSense popup");
            showManualIntelliSensePopup();
        } else {
            logger.debug("No XSD schema - showing basic IntelliSense with available elements");
            showBasicIntelliSensePopup();
        }
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
        // Get the current context (parent element)
        String currentContext = getCurrentElementContext();
        logger.debug("Current element context determined as: '{}'", currentContext);
        List<String> contextSpecificElements = getContextSpecificElements(currentContext);

        // Update the list view with context-specific elements
        completionListView.getItems().clear();
        completionListView.getItems().addAll(contextSpecificElements);

        // Select the first item
        if (!contextSpecificElements.isEmpty()) {
            completionListView.getSelectionModel().select(0);
        }

        // Show the popup at the current cursor position
        showIntelliSensePopupAtCursor();
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

            // Use cached compiled patterns for better performance
            Matcher openMatcher = OPEN_TAG_PATTERN.matcher(textBeforeCursor);
            Matcher closeMatcher = CLOSE_TAG_PATTERN.matcher(textBeforeCursor);

            // Process all tags before cursor
            while (openMatcher.find()) {
                String elementName = openMatcher.group(1);
                elementStack.push(elementName);
            }

            while (closeMatcher.find()) {
                if (!elementStack.isEmpty()) {
                    elementStack.pop();
                }
            }

            // Return the current parent element (top of stack)
            return elementStack.isEmpty() ? null : elementStack.peek();

        } catch (Exception e) {
            logger.error("Error determining current context: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets context-specific element names based on the current parent element.
     *
     * @param parentElement The parent element name
     * @return List of child element names for the given parent
     */
    private List<String> getContextSpecificElements(String parentElement) {
        logger.debug("Getting context-specific elements for parent: {}", parentElement);
        logger.debug("Available contextElementNames keys: {}", contextElementNames.keySet());
        
        if (parentElement == null) {
            // If no parent context, return root-level elements
            List<String> rootElements = contextElementNames.getOrDefault("root", availableElementNames);
            logger.debug("No parent context - returning root elements: {}", rootElements.size());
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

        // Final fallback to general element names if no specific children found
        logger.debug("No specific children found for parent '{}' - falling back to availableElementNames: {}", parentElement, availableElementNames.size());
        return availableElementNames;
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
}
