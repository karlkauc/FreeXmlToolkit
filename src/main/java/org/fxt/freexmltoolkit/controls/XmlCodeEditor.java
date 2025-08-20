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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
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
 * A self-contained XML code editor component that extends StackPane.
 * It includes a CodeArea with line numbers, syntax highlighting logic,
 * and built-in controls for font size and caret movement.
 */
public class XmlCodeEditor extends StackPane {

    private static final int DEFAULT_FONT_SIZE = 11;
    private int fontSize = DEFAULT_FONT_SIZE;

    private final CodeArea codeArea = new CodeArea();
    private final VirtualizedScrollPane<CodeArea> virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);

    // Speichert Start- und Endzeilen der faltbaren Bereiche
    private final Map<Integer, Integer> foldingRegions = new HashMap<>();

    // Speichert den Zustand der eingeklappten Zeilen manuell,
    // um Probleme mit der Bibliotheks-API zu umgehen.
    private final Set<Integer> foldedLines = new HashSet<>();

    // LSP Server functionality removed

    // Document URI for LSP requests
    private String documentUri;

    // Document version for LSP synchronization
    private final int documentVersion = 1;

    // Reference to parent XmlEditor for accessing schema information
    private Object parentXmlEditor;

    // IntelliSense Popup Components
    private Stage intelliSensePopup;
    private ListView<String> completionListView;
    private List<String> availableElementNames = new ArrayList<>();
    private Map<String, List<String>> contextElementNames = new HashMap<>();
    private int popupStartPosition = -1;
    private boolean isElementCompletionContext = false; // Track if we're completing elements or attributes

    // --- Syntax Highlighting Patterns (aus XmlEditor verschoben) ---
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

    // LSP functionality removed

    /**
     * Sets the document URI for LSP requests.
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
    public void setParentXmlEditor(Object parentEditor) {
        this.parentXmlEditor = parentEditor;
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

        this.getChildren().add(virtualizedScrollPane);

        // Set up RichTextFX styling
        setupRichTextFXStyling();
        resetFontSize();

        // Load CSS styles after the component is fully initialized
        Platform.runLater(() -> {
            loadCssStyles();
            injectCssIntoScene();

            // Apply initial syntax highlighting if there's text
            if (codeArea.getText() != null && !codeArea.getText().isEmpty()) {
                applyInlineSyntaxHighlighting(codeArea.getText());
            }
        });

        // Text change listener for syntax highlighting
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            // Apply syntax highlighting with inline styles as fallback
            applyInlineSyntaxHighlighting(newText);
        });
    }

    /**
     * Debug method to check CSS loading status.
     */
    public void debugCssStatus() {
        System.out.println("=== CSS Debug Information ===");
        System.out.println("CodeArea stylesheets count: " + codeArea.getStylesheets().size());
        for (int i = 0; i < codeArea.getStylesheets().size(); i++) {
            System.out.println("CodeArea stylesheet " + i + ": " + codeArea.getStylesheets().get(i));
        }

        System.out.println("Parent container stylesheets count: " + this.getStylesheets().size());
        for (int i = 0; i < this.getStylesheets().size(); i++) {
            System.out.println("Parent stylesheet " + i + ": " + this.getStylesheets().get(i));
        }

        if (this.getScene() != null) {
            System.out.println("Scene stylesheets count: " + this.getScene().getStylesheets().size());
            for (int i = 0; i < this.getScene().getStylesheets().size(); i++) {
                System.out.println("Scene stylesheet " + i + ": " + this.getScene().getStylesheets().get(i));
            }
        }

        System.out.println("Current text: '" + codeArea.getText() + "'");
        System.out.println("Text length: " + (codeArea.getText() != null ? codeArea.getText().length() : 0));
        System.out.println("=============================");
    }

    /**
     * Injects CSS styles directly into the Scene when it becomes available.
     */
    private void injectCssIntoScene() {
        // Wait for the scene to be available
        if (this.getScene() != null) {
            try {
                String cssUrl = getClass().getResource("/css/fxt-theme.css").toExternalForm();
                this.getScene().getStylesheets().add(cssUrl);
                System.out.println("CSS injected into scene successfully");
            } catch (Exception e) {
                System.out.println("Failed to inject CSS into scene: " + e.getMessage());
                injectInlineCss();
            }
        } else {
            // If scene is not available yet, schedule it for later
            Platform.runLater(this::injectCssIntoScene);
        }
    }

    /**
     * Forces syntax highlighting by directly setting styles on the CodeArea.
     */
    private void forceSyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Apply the syntax highlighting using StyleSpans
        StyleSpans<Collection<String>> highlighting = computeHighlighting(text);
        codeArea.setStyleSpans(0, highlighting);

        // Force a repaint of the CodeArea
        codeArea.requestLayout();

        System.out.println("Forced syntax highlighting applied for text length: " + text.length());
    }

    /**
     * Applies syntax highlighting with inline styles as a fallback.
     */
    private void applyInlineSyntaxHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Create a simple inline style approach
        String inlineStyle = "-fx-font-family: monospace; -fx-font-size: " + fontSize + "px; " +
                "-fx-background-color: white; ";

        // Add specific colors for different XML elements
        // This is a simplified approach - in a real implementation, you'd use StyleSpans
        codeArea.setStyle(inlineStyle);

        // Apply the actual syntax highlighting using StyleSpans
        forceSyntaxHighlighting(text);
    }

    /**
     * Sets up RichTextFX styling directly without relying on external CSS.
     */
    private void setupRichTextFXStyling() {
        // Set up the CodeArea with RichTextFX-specific styling
        codeArea.setStyle("-fx-font-family: monospace; -fx-font-size: " + fontSize + "px; -fx-background-color: white;");

        // Add CSS class for code area
        codeArea.getStyleClass().add("code-area");

        // Set up the VirtualizedScrollPane
        virtualizedScrollPane.setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-border-width: 1px;");

        // Ensure the CodeArea is properly configured for syntax highlighting
        codeArea.setEditable(true);
        codeArea.setWrapText(false);

        System.out.println("RichTextFX styling applied");
    }

    /**
     * Sets up the CodeArea with proper styling for syntax highlighting.
     */
    private void setupCodeAreaStyling() {
        // Set up the CodeArea with proper styling
        codeArea.setStyle("-fx-font-family: monospace; -fx-font-size: " + fontSize + "px;");

        // Ensure the CodeArea has the proper CSS class
        codeArea.getStyleClass().add("code-area");

        // Set up the VirtualizedScrollPane styling
        virtualizedScrollPane.setStyle("-fx-background-color: white;");
    }

    /**
     * Injects CSS styles directly into the CodeArea if external CSS loading fails.
     */
    private void injectInlineCss() {
        String inlineCss = """
                .tagmark {
                    -fx-fill: rgb(0, 0, 255);
                }
                
                .anytag {
                    -fx-fill: rgb(128, 0, 0);
                }
                
                .paren {
                    -fx-fill: firebrick;
                    -fx-font-weight: bold;
                }
                
                .attribute {
                    -fx-fill: darkviolet;
                }
                
                .avalue {
                    -fx-fill: black;
                }
                
                .comment {
                    -fx-fill: teal;
                }
                
                .lineno {
                    -fx-background-color: #e3e3e3;
                }
                
                .code-area {
                    -fx-font-family: monospace;
                }
                """;

        codeArea.setStyle(codeArea.getStyle() + inlineCss);
        System.out.println("Inline CSS injected");
    }

    /**
     * Ensures that the CSS styles for syntax highlighting are loaded.
     */
    private void loadCssStyles() {
        try {
            // Inject CSS styles directly into the CodeArea
            String cssStyles = """
                    .tagmark {
                        -fx-fill: rgb(0, 0, 255);
                    }
                    
                    .anytag {
                        -fx-fill: rgb(128, 0, 0);
                    }
                    
                    .paren {
                        -fx-fill: firebrick;
                        -fx-font-weight: bold;
                    }
                    
                    .attribute {
                        -fx-fill: darkviolet;
                    }
                    
                    .avalue {
                        -fx-fill: black;
                    }
                    
                    .comment {
                        -fx-fill: teal;
                    }
                    
                    .lineno {
                        -fx-background-color: #e3e3e3;
                    }
                    
                    .code-area {
                        -fx-font-family: monospace;
                    }
                    """;

            // Create a temporary CSS file and load it
            try {
                // Try to load from external CSS first
                String cssUrl = getClass().getResource("/css/fxt-theme.css").toExternalForm();
                codeArea.getStylesheets().add(cssUrl);
                this.getStylesheets().add(cssUrl);
                System.out.println("External CSS loaded successfully");
            } catch (Exception e) {
                System.out.println("External CSS not available, using inline styles");
                // If external CSS fails, use inline styles
                injectInlineCss();
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to load CSS styles: " + e.getMessage());
            e.printStackTrace();
            // Use inline CSS as fallback
            injectInlineCss();
        }
    }

    // Der Key-Pressed-Handler wurde um die Strg+F Logik erweitert
    private void setupEventHandlers() {
        // Schriftgröße mit Strg + Mausrad ändern
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

        // Handler für Tastenkombinationen
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown()) {
                // Schriftgröße mit Strg +/-, Reset mit Strg + 0
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
                    System.out.println("DEBUG: ENTER key pressed in CodeArea");
                    if (intelliSensePopup != null && intelliSensePopup.isShowing()) {
                        System.out.println("DEBUG: IntelliSense popup is showing - calling selectCompletionItem()");
                        selectCompletionItem();
                        event.consume();
                    } else {
                        System.out.println("DEBUG: IntelliSense popup not showing - normal ENTER behavior");
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
                System.out.println("DEBUG: KEY_TYPED event - character: '" + character + "' (code: " + (int) character.charAt(0) + ")");
                if (handleIntelliSenseTrigger(event)) {
                    System.out.println("DEBUG: IntelliSense trigger handled for: " + character);
                }
            } else {
                System.out.println("DEBUG: KEY_TYPED event - character is null or empty");
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
            System.out.println("DEBUG: ListView KeyPressed: " + event.getCode());
            switch (event.getCode()) {
                case ENTER -> {
                    System.out.println("DEBUG: ENTER pressed in ListView - calling selectCompletionItem()");
                    selectCompletionItem();
                    event.consume();
                }
                case ESCAPE -> {
                    System.out.println("DEBUG: ESCAPE pressed in ListView - hiding popup");
                    hideIntelliSensePopup();
                    event.consume();
                }
                case UP, DOWN -> {
                    // Let ListView handle navigation naturally
                    System.out.println("DEBUG: Navigation key in ListView: " + event.getCode());
                }
                default -> {
                    // For all other keys, try to pass them back to the CodeArea
                    System.out.println("DEBUG: Other key in ListView: " + event.getCode() + " - passing to CodeArea");
                    codeArea.fireEvent(event);
                    event.consume();
                }
            }
        });

        // Ensure the popup scene doesn't steal focus from the main window
        intelliSensePopup.getScene().setOnKeyPressed(event -> {
            System.out.println("DEBUG: Scene KeyPressed: " + event.getCode());
            completionListView.fireEvent(event);
        });
    }

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

    public void replace(String findText, String replaceText) {
        if (findText == null || findText.isEmpty()) return;

        String selectedText = codeArea.getSelectedText();
        if (selectedText.equalsIgnoreCase(findText)) {
            codeArea.replaceSelection(replaceText);
        }
        find(findText, true);
    }

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

        System.out.println("=== Testing Syntax Highlighting ===");
        System.out.println("Test XML:");
        System.out.println(testXml);

        // Debug CSS status before test
        debugCssStatus();

        // Set the test content
        codeArea.replaceText(testXml);

        // Manually trigger syntax highlighting
        refreshSyntaxHighlighting();

        // Debug CSS status after test
        debugCssStatus();

        System.out.println("=== Test completed ===");
    }

    /**
     * Manually triggers syntax highlighting for the current text content.
     * This can be used for testing or to force a refresh of the highlighting.
     */
    public void refreshSyntaxHighlighting() {
        String currentText = codeArea.getText();
        applyInlineSyntaxHighlighting(currentText);
    }

    /**
     * Erstellt eine kompakte Zeilennummer ohne Abstände.
     */
    private Node createCompactLineNumber(int lineIndex) {
        Label lineNumber = new Label(String.valueOf(lineIndex + 1));
        lineNumber.getStyleClass().add("lineno");

        // Alle Abstände entfernen
        lineNumber.setPadding(Insets.EMPTY); // Kein Padding
        lineNumber.setMinWidth(30); // Kompakte Breite
        lineNumber.setMaxHeight(Double.MAX_VALUE); // Nimmt die volle Zeilenhöhe ein
        lineNumber.setAlignment(Pos.CENTER_RIGHT);

        // Styling für nahtlose Darstellung ohne Abstände
        // Grauer Hintergrund
        // Kein Border
        // Links und rechts 3px Padding
        lineNumber.setStyle(
                "-fx-text-fill: #666666; -fx-font-family: monospace; -fx-font-size: " + fontSize + "px; -fx-background-color: #f0f0f0; -fx-border-width: 0; -fx-padding: 0 3 0 3; -fx-spacing: 0;"                   // Kein Spacing
        );

        return lineNumber;
    }

    /**
     * Erstellt eine Factory, die für jede Zeile eine Grafik (Zeilennummer + Falt-Symbol) erzeugt.
     */
    private IntFunction<Node> createParagraphGraphicFactory() {
        return lineIndex -> {
            // Sicherheitsprüfung, da die Factory während Textänderungen aufgerufen werden kann
            if (lineIndex >= codeArea.getParagraphs().size()) {
                HBox fallbackHBox = new HBox(createCompactLineNumber(lineIndex));
                fallbackHBox.setSpacing(0); // Entferne Abstände auch im Fallback
                fallbackHBox.setPadding(Insets.EMPTY); // Kein Padding
                fallbackHBox.setAlignment(Pos.TOP_LEFT); // TOP_LEFT für nahtlose Ausrichtung
                fallbackHBox.setFillHeight(true); // Volle Höhe ausfüllen
                return fallbackHBox;
            }

            boolean isFoldable = foldingRegions.containsKey(lineIndex);
            boolean isFolded = foldedLines.contains(lineIndex);

            // Icon erstellen
            Region foldingIndicator = new Region();
            foldingIndicator.getStyleClass().add("icon");

            if (isFolded) {
                foldingIndicator.getStyleClass().add("toggle-expand");
            } else {
                foldingIndicator.getStyleClass().add("toggle-collapse");
            }

            // Erstelle einen Wrapper für das Icon, um die CSS-Struktur aus XmlGraphicEditor nachzubilden.
            StackPane iconWrapper = new StackPane(foldingIndicator);
            iconWrapper.getStyleClass().add("tree-toggle-button");

            // Klick-Logik auf den Wrapper anwenden
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
            hbox.setAlignment(Pos.TOP_LEFT); // TOP_LEFT für nahtlose Ausrichtung
            hbox.setSpacing(0); // Entferne Abstände zwischen Zeilennummer und Folding-Icons
            hbox.setPadding(Insets.EMPTY); // Kein Padding in der HBox
            hbox.setFillHeight(true); // Volle Höhe ausfüllen

            // Der Wrapper (und damit das Symbol) ist nur sichtbar, wenn die Zeile faltbar ist.
            iconWrapper.setVisible(isFoldable);

            return hbox;
        };
    }


    // LSP folding functionality removed

    // --- Öffentliche API für den Editor ---
    public void moveUp() {
        codeArea.moveTo(0);
        codeArea.showParagraphAtTop(0);
        codeArea.requestFocus();
    }

    public void moveDown() {
        if (codeArea.getText() != null && !codeArea.getParagraphs().isEmpty()) {
            codeArea.moveTo(codeArea.getLength());
            codeArea.showParagraphAtBottom(codeArea.getParagraphs().size() - 1);
            codeArea.requestFocus();
        }
    }

    public void increaseFontSize() {
        setFontSize(++fontSize);
    }

    public void decreaseFontSize() {
        if (fontSize > 1) {
            setFontSize(--fontSize);
        }
    }

    public void resetFontSize() {
        fontSize = DEFAULT_FONT_SIZE;
        setFontSize(fontSize);
    }

    private void setFontSize(int size) {
        codeArea.setStyle("-fx-font-size: " + size + "pt;");
    }

    /**
     * Sucht nach dem gegebenen Text in der CodeArea, hebt alle Vorkommen hervor
     * und scrollt zum ersten Treffer.
     *
     * @param text Der zu suchende Text. Wenn null oder leer, wird die Hervorhebung entfernt.
     */
    public void searchAndHighlight(String text) {
        // Zuerst das normale Syntax-Highlighting anwenden
        StyleSpans<Collection<String>> syntaxHighlighting = computeHighlighting(codeArea.getText());

        if (text == null || text.isBlank()) {
            codeArea.setStyleSpans(0, syntaxHighlighting); // Nur Syntax-Highlighting
            return;
        }

        // Style für die Such-Hervorhebung erstellen
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

        // Such-Highlighting über das Syntax-Highlighting legen
        codeArea.setStyleSpans(0, syntaxHighlighting.overlay(searchSpansBuilder.create(), (style1, style2) -> {
            return style2.isEmpty() ? style1 : style2;
        }));
    }

    /**
     * Gibt die interne CodeArea-Instanz zurück.
     * Dies ermöglicht kontrollierten Zugriff von außen, z.B. für Fokus-Management.
     *
     * @return Die CodeArea-Komponente.
     */
    public CodeArea getCodeArea() {
        return codeArea;
    }

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
            System.out.println("DEBUG: handleIntelliSenseTrigger called with character: '" + character + "'");

            // Handle "<" trigger for element completion
            if ("<".equals(character)) {
                System.out.println("DEBUG: Detected < character, triggering element completion");

                // Store the position OF the '<' character (before it was typed)
                popupStartPosition = codeArea.getCaretPosition() - 1;
                isElementCompletionContext = true; // Mark as element completion
                System.out.println("DEBUG: Set popupStartPosition to: " + popupStartPosition + " (position of <), isElementCompletionContext = true");

                // Show the IntelliSense popup with slight delay to ensure the character is processed
                javafx.application.Platform.runLater(() -> {
                    System.out.println("DEBUG: Calling requestCompletionsFromLSP for element completion");
                    requestCompletionsFromLSP();
                });

                return true; // Event was handled
            }

            // Handle space trigger for attribute completion (inside XML tags)
            if (" ".equals(character)) {
                System.out.println("DEBUG: Detected space character, checking if inside XML tag");
                if (isInsideXmlTag()) {
                    System.out.println("DEBUG: Inside XML tag, triggering attribute completion");

                    // Store the current position (after the space)
                    popupStartPosition = codeArea.getCaretPosition();
                    isElementCompletionContext = false; // Mark as attribute completion

                    // Show attribute completions with slight delay
                    javafx.application.Platform.runLater(() -> {
                        System.out.println("DEBUG: Calling requestCompletionsFromLSP for attribute completion");
                        requestCompletionsFromLSP();
                    });

                    return true; // Event was handled
                } else {
                    System.out.println("DEBUG: Not inside XML tag, no completion triggered");
                }
            }

        } catch (Exception e) {
            System.err.println("Error during IntelliSense trigger: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("Error checking if inside XML tag: " + e.getMessage());
            return false;
        }
    }

    /**
     * Shows the IntelliSense popup with schema-aware element names.
     */
    private void showIntelliSensePopup() {
        // First try to get schema-aware completions from the context
        List<String> schemaAwareCompletions = getSchemaAwareCompletions();

        if (schemaAwareCompletions != null && !schemaAwareCompletions.isEmpty()) {
            System.out.println("DEBUG: Using schema-aware completions (" + schemaAwareCompletions.size() + " items)");
            completionListView.getItems().clear();
            completionListView.getItems().addAll(schemaAwareCompletions);

            if (!schemaAwareCompletions.isEmpty()) {
                completionListView.getSelectionModel().select(0);
            }
            showPopupAtCursor();
        } else {
            System.out.println("DEBUG: No schema-aware completions available, using manual completion");
            showManualIntelliSensePopup();
        }
    }

    /**
     * Gets schema-aware completions based on the current cursor position and loaded XSD schema.
     *
     * @return List of allowed element names at the current position, or null if no schema available
     */
    private List<String> getSchemaAwareCompletions() {
        try {
            // Get the current parent element context
            String currentContext = getCurrentElementContext();
            System.out.println("DEBUG: Current element context: " + currentContext);

            // Try to get completions from the parent XmlEditor's schema information
            if (parentXmlEditor instanceof org.fxt.freexmltoolkit.controls.XmlEditor xmlEditor) {
                // Get the XmlService which has the XSD schema information
                var xmlService = xmlEditor.getXmlService();
                if (xmlService != null && xmlService.getCurrentXsdFile() != null) {
                    System.out.println("DEBUG: XSD schema available: " + xmlService.getCurrentXsdFile().getName());

                    // Try to get allowed child elements for the current context
                    List<String> allowedElements = getChildElementsFromSchema(xmlService, currentContext);
                    if (allowedElements != null && !allowedElements.isEmpty()) {
                        System.out.println("DEBUG: Found " + allowedElements.size() + " allowed child elements for context '" + currentContext + "'");
                        return allowedElements;
                    } else {
                        System.out.println("DEBUG: No allowed child elements found for context '" + currentContext + "'");
                    }
                }
            }

            return null;
        } catch (Exception e) {
            System.err.println("DEBUG: Error getting schema-aware completions: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets allowed child elements for a given parent element from the XSD schema.
     *
     * @param xmlService    The XmlService containing schema information
     * @param parentElement The parent element name, or null for root elements
     * @return List of allowed child element names
     */
    private List<String> getChildElementsFromSchema(org.fxt.freexmltoolkit.service.XmlService xmlService, String parentElement) {
        try {
            // This is a simplified implementation - in a complete implementation,
            // we would parse the XSD schema and determine allowed child elements

            if (parentElement == null) {
                // For root level, try to get root elements from the schema
                // For now, we'll return some common root elements for the FundsXML schema
                return List.of("FundsXML4");
            }

            // For specific parent elements, we could analyze the XSD schema
            // For demonstration purposes, let's implement some common FundsXML patterns
            return switch (parentElement.toLowerCase()) {
                case "fundsxml4" ->
                        List.of("ControlData", "Funds", "AssetMgmtCompanyDynData", "AssetMasterData", "Documents", "RegulatoryReportings");
                case "controldata" ->
                        List.of("UniqueDocumentID", "DocumentGenerated", "Version", "ContentDate", "DataSupplier", "DataOperation", "RelatedDocumentIDs", "Language");
                case "funds" -> List.of("Fund");
                case "fund" ->
                        List.of("Identifiers", "Names", "Currency", "SingleFundFlag", "DataSupplier", "FundStaticData", "FundDynamicData");
                case "identifiers" -> List.of("LEI", "ISIN", "CUSIP", "SEDOL", "WKN");
                case "names" -> List.of("OfficialName", "ShortName");
                case "datasupplier" -> List.of("SystemCountry", "Short", "Name", "Type", "Contact");
                case "contact" -> List.of("Name", "Phone", "Email");
                case "fundstaticdata" -> List.of("ListedLegalStructure", "InceptionDate", "StartOfFiscalYear");
                case "funddynamicdata" -> List.of("TotalAssetValues", "Portfolios");
                case "totalassetvalues" -> List.of("TotalAssetValue");
                case "totalassetvalue" -> List.of("NavDate", "TotalNetAssets", "GrossAssets");
                case "portfolios" -> List.of("Portfolio");
                case "portfolio" -> List.of("Positions");
                case "positions" -> List.of("Position");
                default -> {
                    System.out.println("DEBUG: No specific child elements defined for parent '" + parentElement + "'");
                    yield null;
                }
            };

        } catch (Exception e) {
            System.err.println("DEBUG: Error getting child elements from schema: " + e.getMessage());
            return null;
        }
    }


    /**
     * Requests completions - now uses manual completion since LSP is removed.
     */
    private void requestCompletionsFromLSP() {
        System.out.println("DEBUG: Using manual completion (LSP removed)");
        showManualIntelliSensePopup();
    }

    /**
     * Shows the completion popup with current items at the cursor position.
     */
    private void showEmptyPopup() {
        showPopupAtCursor();
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
                    System.out.println("DEBUG: Focus returned to CodeArea after popup show");
                });

                System.out.println("DEBUG: IntelliSense popup shown at cursor position");
            }
        }
    }

    /**
     * Fallback method that shows manual IntelliSense popup (original behavior).
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

            // Simple regex to find opening and closing tags
            Pattern openTagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)\\b[^>]*>");
            Pattern closeTagPattern = Pattern.compile("</([a-zA-Z][a-zA-Z0-9_:]*)\\s*>");

            Matcher openMatcher = openTagPattern.matcher(textBeforeCursor);
            Matcher closeMatcher = closeTagPattern.matcher(textBeforeCursor);

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
            System.err.println("Error determining current context: " + e.getMessage());
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
        System.out.println("DEBUG: selectCompletionItem called with selectedItem: '" + selectedItem + "'");
        System.out.println("DEBUG: popupStartPosition: " + popupStartPosition);
        
        if (selectedItem != null && popupStartPosition >= 0) {
            // Remove any existing partial input between popupStartPosition and current position
            int currentPosition = codeArea.getCaretPosition();
            System.out.println("DEBUG: currentPosition: " + currentPosition);

            // Use the context flag set during trigger detection
            System.out.println("DEBUG: isElementCompletionContext: " + isElementCompletionContext);

            if (isElementCompletionContext) {
                // SAFER APPROACH: Find the most recent "<" and replace from there
                String tagName = selectedItem.trim();
                String completeElement = "<" + tagName + "></" + tagName + ">";

                // Find the position of the most recent "<" character before the current cursor
                String textToCursor = codeArea.getText(0, currentPosition);
                int lastBracketPos = textToCursor.lastIndexOf('<');

                if (lastBracketPos >= 0) {
                    // Debug information
                    String textBeingReplaced = codeArea.getText(lastBracketPos, currentPosition);
                    String contextBefore = codeArea.getText(Math.max(0, lastBracketPos - 10), lastBracketPos);
                    String contextAfter = codeArea.getText(currentPosition, Math.min(codeArea.getLength(), currentPosition + 10));

                    System.out.println("DEBUG: Found '<' at position: " + lastBracketPos);
                    System.out.println("DEBUG: Full context: '" + contextBefore + "[" + textBeingReplaced + "]" + contextAfter + "'");
                    System.out.println("DEBUG: Replacing from pos " + lastBracketPos + " to " + currentPosition + ": '" + textBeingReplaced + "'");
                    System.out.println("DEBUG: Will replace with: '" + completeElement + "'");

                    // Replace only from the "<" character to current cursor position
                    codeArea.replaceText(lastBracketPos, currentPosition, completeElement);

                    // Position cursor between the opening and closing tags
                    int cursorPosition = lastBracketPos + tagName.length() + 2; // After "<tagname>"
                    codeArea.moveTo(cursorPosition);

                    System.out.println("DEBUG: Created complete XML element: " + completeElement);
                    System.out.println("DEBUG: Cursor positioned at: " + cursorPosition);
                } else {
                    System.out.println("DEBUG: No '<' found before current position - fallback to simple insertion");
                    // Fallback: just insert the tag name
                    codeArea.replaceText(popupStartPosition, currentPosition, selectedItem);
                    codeArea.moveTo(popupStartPosition + selectedItem.length());
                }
            } else {
                // For attribute completions or other contexts, just insert the selected item
                System.out.println("DEBUG: Not element completion - inserting selectedItem only");
                codeArea.replaceText(popupStartPosition, currentPosition, selectedItem);
                codeArea.moveTo(popupStartPosition + selectedItem.length());
            }

            // Hide the popup
            hideIntelliSensePopup();
        }
    }

    /**
     * Handles tab completion for XML elements.
     * Currently a placeholder for future LSP integration.
     *
     * @param event The key event
     * @return true if the event was handled, false otherwise
     */
    private boolean handleTabCompletion(KeyEvent event) {
        // For now, just allow normal tab behavior
        // In a full implementation, this would:
        // 1. Request completions from LSP server
        // 2. Show a completion popup
        // 3. Insert the selected completion
        System.out.println("Tab completion requested - LSP integration pending");
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
            System.err.println("Error during auto-closing tag: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a tag is a self-closing tag.
     *
     * @param tagName The tag name to check
     * @return true if it's a self-closing tag, false otherwise
     */
    private boolean isSelfClosingTag(String tagName) {
        String[] selfClosingTags = {
                "br", "hr", "img", "input", "meta", "link", "area", "base", "col", "embed",
                "source", "track", "wbr", "param", "keygen", "command"
        };

        for (String tag : selfClosingTags) {
            if (tagName.equalsIgnoreCase(tag)) {
                return true;
            }
        }

        return false;
    }
}
