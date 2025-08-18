package org.fxt.freexmltoolkit.controls;

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
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.services.LanguageServer;
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

    // LSP Server for IntelliSense
    private LanguageServer languageServer;

    // Document URI for LSP requests
    private String documentUri;

    // Reference to parent XmlEditor for accessing schema information
    private Object parentXmlEditor;

    // IntelliSense Popup Components
    private Stage intelliSensePopup;
    private ListView<String> completionListView;
    private List<String> availableElementNames = new ArrayList<>();
    private Map<String, List<String>> contextElementNames = new HashMap<>();
    private int popupStartPosition = -1;

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

    public void setLanguageServer(LanguageServer languageServer) {
        this.languageServer = languageServer;
    }

    /**
     * Sets the document URI for LSP requests.
     *
     * @param documentUri The URI of the current document
     */
    public void setDocumentUri(String documentUri) {
        this.documentUri = documentUri;
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

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            codeArea.setStyleSpans(0, computeHighlighting(newText));
        });

        setupEventHandlers();
        initializeIntelliSensePopup();

        this.getChildren().add(virtualizedScrollPane);
        resetFontSize();
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
                case LESS -> {
                    if (handleIntelliSenseTrigger(event)) {
                        event.consume();
                    }
                }
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
                    if (intelliSensePopup.isShowing()) {
                        selectCompletionItem();
                        event.consume();
                    }
                }
                default -> {
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
    }

    // --- NEUE Such- und Ersetzen-Methoden (von XmlEditor hierher verschoben) ---

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
        lineNumber.setStyle(
                "-fx-text-fill: #666666; " +
                        "-fx-font-family: monospace; " +
                        "-fx-font-size: " + fontSize + "px; " +
                        "-fx-background-color: #f0f0f0; " + // Grauer Hintergrund
                        "-fx-border-width: 0; " +           // Kein Border
                        "-fx-padding: 0 3 0 3; " +          // Links und rechts 3px Padding
                        "-fx-spacing: 0;"                   // Kein Spacing
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


    /**
     * Aktualisiert die Falt-Informationen basierend auf den Daten vom LSP-Server.
     *
     * @param ranges Eine Liste von FoldingRange-Objekten.
     */
    public void updateFoldingRanges(List<FoldingRange> ranges) {
        foldingRegions.clear();
        foldedLines.clear(); // Setzt unseren manuellen Falt-Zustand zurück.

        if (ranges != null) {
            for (FoldingRange range : ranges) {
                foldingRegions.put(range.getStartLine(), range.getEndLine());
            }
        }
        // Erzwinge eine Neuzeichnung der Gutter-Grafiken, nachdem die Daten aktualisiert wurden.
        codeArea.setParagraphGraphicFactory(createParagraphGraphicFactory());
    }

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
     * Handles IntelliSense trigger when "<" is typed.
     *
     * @param event The key event
     * @return true if the event was handled, false otherwise
     */
    private boolean handleIntelliSenseTrigger(KeyEvent event) {
        try {
            // Insert the "<" character first
            codeArea.insertText(codeArea.getCaretPosition(), "<");

            // Store the position where we started (after the '<')
            popupStartPosition = codeArea.getCaretPosition();

            // Show the IntelliSense popup (with slight delay to ensure text is processed)
            javafx.application.Platform.runLater(() -> {
                showIntelliSensePopup();
            });

            return true; // Consume the event since we handled it
        } catch (Exception e) {
            System.err.println("Error during IntelliSense trigger: " + e.getMessage());
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
     * Ensures the current document content is synchronized with the LSP server.
     * This is crucial for getting accurate completion suggestions.
     */
    private void ensureDocumentSynchronized() {
        try {
            String content = codeArea.getText();

            // For untitled documents, we need to send a didOpen notification first
            if (documentUri.startsWith("untitled:")) {
                System.out.println("DEBUG: Sending didOpen for untitled document");
                org.eclipse.lsp4j.TextDocumentItem textDocument = new org.eclipse.lsp4j.TextDocumentItem(
                        documentUri, "xml", 1, content);

                org.eclipse.lsp4j.DidOpenTextDocumentParams openParams =
                        new org.eclipse.lsp4j.DidOpenTextDocumentParams(textDocument);

                languageServer.getTextDocumentService().didOpen(openParams);
            } else {
                // For file-based documents, send a didChange notification
                System.out.println("DEBUG: Sending didChange for file document");
                org.eclipse.lsp4j.VersionedTextDocumentIdentifier identifier =
                        new org.eclipse.lsp4j.VersionedTextDocumentIdentifier(documentUri, 1);

                org.eclipse.lsp4j.TextDocumentContentChangeEvent changeEvent =
                        new org.eclipse.lsp4j.TextDocumentContentChangeEvent(content);

                org.eclipse.lsp4j.DidChangeTextDocumentParams params =
                        new org.eclipse.lsp4j.DidChangeTextDocumentParams(identifier,
                                java.util.Collections.singletonList(changeEvent));

                languageServer.getTextDocumentService().didChange(params);
            }

            System.out.println("DEBUG: Document synchronized with LSP server");

        } catch (Exception e) {
            System.err.println("ERROR: Failed to synchronize document with LSP server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Requests completions from the Language Server based on the current cursor position.
     */
    private void requestCompletionsFromLSP() {
        try {
            int caretPosition = codeArea.getCaretPosition();
            String text = codeArea.getText();

            // Calculate line and character position
            String textBeforeCursor = text.substring(0, caretPosition);
            String[] lines = textBeforeCursor.split("\n", -1);
            int lineNumber = lines.length - 1;
            int character = lines[lineNumber].length();

            // Debug logging
            System.out.println("DEBUG: Requesting LSP completion at line=" + lineNumber + ", character=" + character);
            System.out.println("DEBUG: Document URI: " + documentUri);
            System.out.println("DEBUG: Text around cursor: '" +
                    textBeforeCursor.substring(Math.max(0, textBeforeCursor.length() - 20)) + "' | '" +
                    text.substring(caretPosition, Math.min(text.length(), caretPosition + 20)) + "'");

            // Create completion request parameters
            org.eclipse.lsp4j.TextDocumentIdentifier textDocument = new org.eclipse.lsp4j.TextDocumentIdentifier();
            // Use the document URI if available, otherwise fallback to a placeholder
            String uriToUse = (documentUri != null && !documentUri.isEmpty()) ? documentUri : "file:///temp.xml";
            textDocument.setUri(uriToUse);

            org.eclipse.lsp4j.Position position = new org.eclipse.lsp4j.Position(lineNumber, character);
            org.eclipse.lsp4j.CompletionParams completionParams = new org.eclipse.lsp4j.CompletionParams(textDocument, position);

            // Set completion context to indicate this is a triggered completion (after '<')
            org.eclipse.lsp4j.CompletionContext context = new org.eclipse.lsp4j.CompletionContext();
            context.setTriggerKind(org.eclipse.lsp4j.CompletionTriggerKind.TriggerCharacter);
            context.setTriggerCharacter("<");
            completionParams.setContext(context);

            System.out.println("DEBUG: Sending LSP completion request...");

            // Request completions from the Language Server
            languageServer.getTextDocumentService().completion(completionParams)
                    .thenAccept(completionList -> {
                        System.out.println("DEBUG: LSP completion response received");
                        javafx.application.Platform.runLater(() -> {
                            if (completionList != null && completionList.isRight()) {
                                // Handle CompletionList
                                var items = completionList.getRight().getItems();
                                System.out.println("DEBUG: CompletionList received with " + (items != null ? items.size() : 0) + " items");
                                if (items != null && !items.isEmpty()) {
                                    showCompletionItems(items);
                                } else {
                                    System.out.println("DEBUG: No completion items from LSP CompletionList");
                                    completionListView.getItems().clear();
                                    completionListView.getItems().add("(No LSP completions - CompletionList empty)");
                                    showEmptyPopup();
                                }
                            } else if (completionList != null && completionList.isLeft()) {
                                // Handle List<CompletionItem>
                                var items = completionList.getLeft();
                                System.out.println("DEBUG: CompletionItem list received with " + (items != null ? items.size() : 0) + " items");
                                if (items != null && !items.isEmpty()) {
                                    showCompletionItems(items);
                                } else {
                                    System.out.println("DEBUG: No completion items from LSP List");
                                    completionListView.getItems().clear();
                                    completionListView.getItems().add("(No LSP completions - List empty)");
                                    showEmptyPopup();
                                }
                            } else {
                                System.out.println("DEBUG: No completion results from LSP at all");
                                completionListView.getItems().clear();
                                completionListView.getItems().add("(No LSP response)");
                                showEmptyPopup();
                            }
                        });
                    })
                    .exceptionally(throwable -> {
                        System.err.println("ERROR: LSP completion request failed: " + throwable.getMessage());
                        throwable.printStackTrace();
                        // Fallback to manual completion on error
                        javafx.application.Platform.runLater(this::showManualIntelliSensePopup);
                        return null;
                    });

        } catch (Exception e) {
            System.err.println("ERROR: Exception preparing completion request: " + e.getMessage());
            e.printStackTrace();
            // Fallback to manual completion on error
            showManualIntelliSensePopup();
        }
    }

    /**
     * Shows completion items from the Language Server.
     */
    private void showCompletionItems(java.util.List<org.eclipse.lsp4j.CompletionItem> items) {
        // Extract the labels from completion items
        java.util.List<String> completionLabels = items.stream()
                .map(org.eclipse.lsp4j.CompletionItem::getLabel)
                .collect(java.util.stream.Collectors.toList());

        // Update the list view with LSP completion items
        completionListView.getItems().clear();
        completionListView.getItems().addAll(completionLabels);

        // Select the first item
        if (!completionLabels.isEmpty()) {
            completionListView.getSelectionModel().select(0);
        }

        showPopupAtCursor();
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
                intelliSensePopup.show();
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
     * Selects the currently highlighted completion item.
     */
    private void selectCompletionItem() {
        String selectedItem = completionListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null && popupStartPosition >= 0) {
            // Replace the content from popupStartPosition to current position
            int currentPosition = codeArea.getCaretPosition();
            codeArea.replaceText(popupStartPosition, currentPosition, selectedItem);

            // Move cursor to the end of the inserted text
            codeArea.moveTo(popupStartPosition + selectedItem.length());

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
