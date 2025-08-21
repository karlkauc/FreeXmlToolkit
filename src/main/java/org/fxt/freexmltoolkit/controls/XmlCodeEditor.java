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

    private final CodeArea codeArea = new CodeArea();
    private final VirtualizedScrollPane<CodeArea> virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);

    // Status line components
    private final HBox statusLine = new HBox();
    private final Label cursorPositionLabel = new Label("Line: 1, Column: 1");
    private final Label encodingLabel = new Label("UTF-8");
    private final Label lineSeparatorLabel = new Label("LF");
    private final Label indentationLabel = new Label("4 spaces");

    // File properties for status line
    private String currentEncoding = "UTF-8";
    private String currentLineSeparator = "LF";
    private int currentIndentationSize = 4;
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

    // Cache for enumeration elements to avoid repeated XSD parsing
    private final Set<String> enumerationElements = new HashSet<>();
    private long lastXsdModified = -1;
    private int popupStartPosition = -1;
    private boolean isElementCompletionContext = false; // Track if we're completing elements or attributes

    // Performance optimization: Cache compiled patterns
    private static final Pattern OPEN_TAG_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)\b[^>]*>");
    private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("</([a-zA-Z][a-zA-Z0-9_:]*)\s*>");
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
        initialize();
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
            updateEnumerationElementsCache();
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

    private void initialize() {
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

        // Text change listener for syntax highlighting
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            // Update enumeration elements cache before highlighting
            updateEnumerationElementsCache();
            // Apply syntax highlighting using external CSS
            applySyntaxHighlighting(newText);
        });
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
     * Forces syntax highlighting by directly setting styles on the CodeArea.
     */
    private void forceSyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Apply the syntax highlighting using StyleSpans
        StyleSpans<Collection<String>> highlighting = computeHighlightingWithEnumeration(text);
        codeArea.setStyleSpans(0, highlighting);

        // Force a repaint of the CodeArea
        codeArea.requestLayout();


        logger.debug("Forced syntax highlighting applied for text length: {}", text.length());
    }


    /**
     * Applies syntax highlighting using external CSS only.
     */
    private void applySyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Apply the syntax highlighting using StyleSpans (colors come from external CSS)
        forceSyntaxHighlighting(text);
    }

    /**
     * Updates the cache of elements that have enumeration constraints in the XSD.
     */
    private void updateEnumerationElementsCache() {
        try {
            logger.debug("updateEnumerationElementsCache called. parentXmlEditor: {}", parentXmlEditor);
            if (parentXmlEditor instanceof org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor) {
                var xmlService = xmlEditor.getXmlService();
                logger.debug("xmlService: {}", xmlService);
                if (xmlService != null) {
                    logger.debug("currentXsdFile: {}", xmlService.getCurrentXsdFile());
                }
                if (xmlService != null && xmlService.getCurrentXsdFile() != null) {
                    java.io.File xsdFile = xmlService.getCurrentXsdFile();
                    logger.debug("Updating enumeration elements cache from XSD: {}", xsdFile.getAbsolutePath());

                    long currentModified = xsdFile.lastModified();
                    if (currentModified == lastXsdModified) {
                        logger.debug("XSD file has not been modified. Cache is up to date.");
                        return;
                    }

                    enumerationElements.clear();
                    lastXsdModified = currentModified;

                    javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                    org.w3c.dom.Document xsdDoc = builder.parse(xsdFile);

                    org.w3c.dom.NodeList elements = xsdDoc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
                    logger.debug("Found {} elements in the XSD file.", elements.getLength());

                    for (int i = 0; i < elements.getLength(); i++) {
                        org.w3c.dom.Element element = (org.w3c.dom.Element) elements.item(i);
                        String name = element.getAttribute("name");

                        if (name != null && !name.isEmpty()) {
                            boolean hasEnum = hasEnumerationConstraint(element);
                            logger.debug("Checking element: {}, Has enumeration: {}", name, hasEnum);
                            if (hasEnum) {
                                enumerationElements.add(name);
                            }
                        }
                    }

                    logger.debug("Updated enumeration elements cache with {} elements: {}", enumerationElements.size(), enumerationElements);
                } else {
                    logger.debug("XmlService or XSD file is null. Cannot update enumeration cache.");
                }
            } else {
                logger.debug("Parent editor is null or XSD file not available.");
            }
        } catch (Exception e) {
            logger.error("Error updating enumeration elements cache: {}", e.getMessage(), e);
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
     * Manually triggers syntax highlighting for the current text content.
     * This can be used for testing or to force a refresh of the highlighting.
     */
    public void refreshSyntaxHighlighting() {
        String currentText = codeArea.getText();
        if (currentText != null && !currentText.isEmpty()) {
            logger.debug("Manually refreshing syntax highlighting for text length: {}", currentText.length());

            // Force syntax highlighting with debugging
            forceSyntaxHighlighting(currentText);

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
                        ex.printStackTrace();
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
                    foldingTask.getException().printStackTrace();
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
            enumSpansBuilder.add(Collections.emptyList(), elementInfo.startPosition() - lastMatchEnd);
            enumSpansBuilder.add(Collections.singleton("enumeration-content"), elementInfo.endPosition() - elementInfo.startPosition());
            lastMatchEnd = elementInfo.endPosition();
        }
        enumSpansBuilder.add(Collections.emptyList(), text.length() - lastMatchEnd);

        // Overlay the enumeration highlighting on top of the base syntax highlighting
        return baseHighlighting.overlay(enumSpansBuilder.create(), (baseStyle, enumStyle) -> {
            return enumStyle.isEmpty() ? baseStyle : enumStyle;
        });
    }

    private List<ElementTextInfo> findAllEnumerationElements(String text) {
        List<ElementTextInfo> elements = new ArrayList<>();
        logger.debug("Searching for enumeration elements. Cache contains: {}", enumerationElements);
        for (String elementName : enumerationElements) {
            Pattern tagPattern = Pattern.compile("<" + elementName + "[^>]*>([^<]*)</" + elementName + ">");
            Matcher matcher = tagPattern.matcher(text);
            while (matcher.find()) {
                String content = matcher.group(1);
                if (!content.isBlank()) {
                    int contentStart = matcher.start(1);
                    int contentEnd = matcher.end(1);
                    logger.debug("Found enumeration element: {} with content: '{}'", elementName, content);
                    elements.add(new ElementTextInfo(elementName, content, contentStart, contentEnd));
                }
            }
        }
        logger.debug("Total enumeration elements found: {}", elements.size());
        return elements;
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

        // Only show IntelliSense popup if XSD schema is available
        if (isXsdSchemaAvailable()) {
            logger.debug("XSD schema available - showing IntelliSense popup");
            showManualIntelliSensePopup();
        } else {
            logger.debug("No XSD schema - IntelliSense popup disabled");
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
     * Shows IntelliSense popup with XSD-based completion.
     */
    private void showManualIntelliSensePopup() {
        // Get the current context (parent element)
        String currentContext = getCurrentElementContext();
        List<String> contextSpecificElements = getContextSpecificElements(currentContext);

        // Update the list view with context-specific elements
        completionListView.getItems().clear();
        completionListView.getItems().addAll(contextSpecificElements);

        // Select the first item
        if (!contextSpecificElements.isEmpty()) {
            completionListView.getSelectionModel().select(0);
        }

        // Show the popup at the current cursor position
        if (codeArea.getScene() != null && codeArea.getScene().getWindow() != null) {
            var caretBounds = codeArea.getCaretBounds().orElse(null);
            if (caretBounds != null) {
                var screenPos = codeArea.localToScreen(caretBounds.getMinX(), caretBounds.getMaxY());
                intelliSensePopup.setX(screenPos.getX());
                intelliSensePopup.setY(screenPos.getY());
                intelliSensePopup.show();
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
        if (parentElement == null) {
            // If no parent context, return root-level elements
            return contextElementNames.getOrDefault("root", availableElementNames);
        }

        // Get child elements for the current parent
        List<String> childElements = contextElementNames.get(parentElement);
        if (childElements != null && !childElements.isEmpty()) {
            return childElements;
        }

        // Fallback to general element names if no specific children found
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
     * Implements two main rules:
     * 1. After a closing XML tag: maintain indentation of previous element
     * 2. Between opening and closing tag: indent by 4 spaces more than parent
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

            // Add 4 spaces of additional indentation
            String newIndentation = baseIndentation + "    ";

            // Insert newline with increased indentation and another newline with original indentation
            String insertText = "\n" + newIndentation + "\n" + baseIndentation;
            codeArea.insertText(caretPosition, insertText);

            // Position cursor at the end of the first inserted line (between the tags)
            int newPosition = caretPosition + newIndentation.length() + 1; // +1 for first newline
            codeArea.moveTo(newPosition);

            logger.debug("Applied Enter between tags with indentation: '{}'", newIndentation);
            return true;

        } catch (Exception e) {
            logger.error("Error handling Enter between tags: {}", e.getMessage(), e);
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
