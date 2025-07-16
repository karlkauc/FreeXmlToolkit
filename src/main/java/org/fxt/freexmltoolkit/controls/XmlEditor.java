package org.fxt.freexmltoolkit.controls;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.PopOver;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.TwoDimensional;
import org.fxt.freexmltoolkit.controller.MainController;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlEditor extends Tab {

    public static final int MAX_SIZE_FOR_FORMATTING = 1024 * 1024 * 20;
    public static final String DEFAULT_FILE_NAME = "Untitled.xml *";
    private static final int DEFAULT_FONT_SIZE = 11;

    private final Tab xml = new Tab("XML");
    private final Tab graphic = new Tab("Graphic");

    private final ObjectProperty<Runnable> onSearchRequested = new SimpleObjectProperty<>();

    StackPane stackPane = new StackPane();

    public CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);

    private List<Diagnostic> currentDiagnostics = new ArrayList<>();

    private final static Logger logger = LogManager.getLogger(XmlEditor.class);

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

    File xmlFile;
    private int fontSize = 11;

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    DocumentBuilder db;
    Document document;
    XmlService xmlService = new XmlServiceImpl();

    // NEU: Referenz zum MainController für die LSP-Kommunikation
    private MainController mainController;
    private LanguageServer serverProxy;

    // NEU: UI-Elemente für die Hover-Informationen
    private PopOver hoverPopOver;
    private final Label popOverLabel = new Label();
    // NEU: Timer, um Anfragen zu verzögern (Debouncing)
    private final PauseTransition hoverDelay = new PauseTransition(Duration.millis(500));

    public XmlEditor() {
        init();
    }

    public XmlEditor(File file) {
        init();
        this.setXmlFile(file);
        // refresh() wird nun vom Controller aufgerufen, nachdem der Tab zur Szene hinzugefügt wurde.
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * NEU: Setter, um den Server-Proxy direkt zu übergeben.
     */
    public void setLanguageServer(LanguageServer serverProxy) {
        this.serverProxy = serverProxy;
    }


    public final ObjectProperty<Runnable> onSearchRequestedProperty() {
        return onSearchRequested;
    }

    public final void setOnSearchRequested(Runnable value) {
        onSearchRequested.set(value);
    }

    public final Runnable getOnSearchRequested() {
        return onSearchRequested.get();
    }

    private void init() {
        try {
            db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            logger.warn("Error parsing XML file: {}", e.getMessage());
        }

        TabPane tabPane = new TabPane();

        xml.setGraphic(new FontIcon("bi-code-slash:20"));
        graphic.setGraphic(new FontIcon("bi-columns-gap:20"));

        tabPane.setSide(Side.LEFT);
        tabPane.getTabs().addAll(xml, graphic);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        xml.setOnSelectionChanged(e -> {
            if (xml.isSelected()) {
                logger.debug("refresh Text view");
                refreshTextView();
            } else {
                logger.debug("refresh Graphic view");
                try {
                    if (!codeArea.getText().isEmpty()) {
                        document = db.parse(new ByteArrayInputStream(codeArea.getText().getBytes(StandardCharsets.UTF_8)));
                        refreshGraphicView();
                    }
                } catch (SAXException | IOException ex) {
                    logger.info("could not create graphic view.");
                }
            }
        });

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            applyStyles();
        });

        // NEU: Initialisierung des PopOver und des Timers
        popOverLabel.setWrapText(true);
        popOverLabel.setStyle("-fx-padding: 8px; -fx-font-family: 'monospaced';");
        hoverPopOver = new PopOver(popOverLabel);
        hoverPopOver.setDetachable(false);
        hoverPopOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
        hoverDelay.setOnFinished(e -> triggerLspHover());


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

        codeArea.addEventFilter(KeyEvent.ANY, event -> {
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                if (event.isControlDown() && event.getCode() == KeyCode.F) {
                    if (getOnSearchRequested() != null) {
                        getOnSearchRequested().run();
                        event.consume();
                    }
                } else if (event.isControlDown() && (event.getCode() == KeyCode.NUMPAD0 || event.getCode() == KeyCode.DIGIT0)) {
                    resetFontSize();
                    event.consume();
                }
            }
        });

        stackPane.getChildren().add(virtualizedScrollPane);
        setFontSize(DEFAULT_FONT_SIZE);

        xml.setContent(stackPane);

        this.setText(DEFAULT_FILE_NAME);
        this.setClosable(true);
        this.setOnCloseRequest(eh -> logger.debug("Close Event"));

        this.setContent(tabPane);
    }

    /**
     * NEU: Wird vom MainController aufgerufen, um diesen Editor über neue Diagnosen zu informieren.
     */
    public void updateDiagnostics(List<Diagnostic> diagnostics) {
        this.currentDiagnostics = new ArrayList<>(diagnostics);
        applyStyles(); // Wende die neuen Stile (Unterstreichungen) an.
    }

    /**
     * NEU: Kombiniert Syntax-Highlighting und Diagnose-Highlighting.
     * Diese Methode wird jetzt immer aufgerufen, wenn sich der Text oder die Diagnosen ändern.
     */
    private void applyStyles() {
        if (codeArea.getText().length() >= MAX_SIZE_FOR_FORMATTING) {
            return;
        }
        // RichTextFX erlaubt das Überlagern von Style-Ebenen.
        // Zuerst das Syntax-Highlighting, dann die Diagnose-Unterstreichungen darüber.
        codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText())
                .overlay(computeDiagnosticStyles(), (syntaxStyles, diagnosticStyles) -> {
                    // Wenn es einen Diagnose-Stil gibt, hat er Vorrang.
                    return diagnosticStyles.isEmpty() ? syntaxStyles : diagnosticStyles;
                }));
    }

    /**
     * NEU: Erstellt die StyleSpans für die Diagnose-Unterstreichungen.
     */
    private StyleSpans<Collection<String>> computeDiagnosticStyles() {
        if (currentDiagnostics.isEmpty()) {
            return StyleSpans.singleton(Collections.emptyList(), codeArea.getLength());
        }

        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastKwEnd = 0;

        for (Diagnostic diagnostic : currentDiagnostics) {
            Range range = diagnostic.getRange();
            // Konvertiere LSP-Position (Zeile/Spalte) in einen flachen Index im Text.
            int start = codeArea.position(range.getStart().getLine(), range.getStart().getCharacter()).toOffset();
            int end = codeArea.position(range.getEnd().getLine(), range.getEnd().getCharacter()).toOffset();

            if (start < end) {
                spansBuilder.add(Collections.emptyList(), start - lastKwEnd);
                String styleClass = getStyleClassFor(diagnostic.getSeverity());
                spansBuilder.add(Collections.singleton(styleClass), end - start);
                lastKwEnd = end;
            }
        }
        spansBuilder.add(Collections.emptyList(), codeArea.getLength() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * NEU: Hilfsmethode, um die richtige CSS-Klasse für den Schweregrad zu finden.
     */
    private String getStyleClassFor(DiagnosticSeverity severity) {
        if (severity == null) return "diagnostic-warning"; // Fallback
        switch (severity) {
            case Error:
                return "diagnostic-error";
            case Warning:
                return "diagnostic-warning";
            // Weitere Fälle wie Information oder Hint können hier hinzugefügt werden.
            default:
                return "diagnostic-warning";
        }
    }

    /**
     * NEU: Findet eine Diagnose an einer bestimmten Textposition.
     */
    private Optional<Diagnostic> findDiagnosticAt(int position) {
        return currentDiagnostics.stream().filter(d -> {
            int start = codeArea.position(d.getRange().getStart().getLine(), d.getRange().getStart().getCharacter()).toOffset();
            int end = codeArea.position(d.getRange().getEnd().getLine(), d.getRange().getEnd().getCharacter()).toOffset();
            return position >= start && position <= end;
        }).findFirst();
    }

    /**
     * NEU: Hilfsmethode, um das PopOver anzuzeigen.
     */
    private void showPopOver() {
        Point2D screenPos = codeArea.localToScreen(codeArea.getCaretBounds().get().getMaxX(), codeArea.getCaretBounds().get().getMaxY());
        hoverPopOver.show(codeArea.getScene().getWindow(), screenPos.getX(), screenPos.getY() + 5);
    }

    /**
     * Löst die LSP-Anfrage für Hover-Informationen aus.
     * Diese Version verarbeitet die Antwort direkt und modern.
     */
    private void triggerLspHover() {
        if (this.serverProxy == null || xmlFile == null) {
            logger.trace("LSP Hover: Server or file not available.");
            return;
        }

        int caretPosition = codeArea.getCaretPosition();
        var lineColumn = codeArea.offsetToPosition(caretPosition, TwoDimensional.Bias.Forward);

        TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(xmlFile.toURI().toString());
        Position position = new Position(lineColumn.getMajor(), lineColumn.getMinor());
        HoverParams hoverParams = new HoverParams(textDocumentIdentifier, position);

        logger.debug("Requesting hover info for {} at [{},{}]", xmlFile.getName(), position.getLine(), position.getCharacter());

        CompletableFuture<Hover> hoverFuture = this.serverProxy.getTextDocumentService().hover(hoverParams);
        hoverFuture.thenAcceptAsync(hover -> {
            if (hover == null || hover.getContents() == null) {
                return;
            }

            // Fall 1 (bevorzugt): Die Antwort ist moderner MarkupContent.
            if (hover.getContents().isRight()) {
                String hoverText = hover.getContents().getRight().getValue();
                if (!hoverText.isBlank()) {
                    Platform.runLater(() -> {
                        popOverLabel.setText(hoverText);
                        showPopOver();
                    });
                }
            }

            // Fall 2 (veraltet): Die Antwort ist eine Liste von MarkedString.
            if (hover.getContents().isLeft()) {
                /*
                List<Either<String, org.eclipse.lsp4j.MarkedString>> legacyItems = hover.getContents().getLeft();
                String hoverText = legacyItems.stream()
                        .map(this::formatLegacyHoverItem)
                        .collect(Collectors.joining("\n\n---\n\n"));

                if (!hoverText.isBlank()) {
                    Platform.runLater(() -> {
                        popOverLabel.setText(hoverText);
                        showPopOver();
                    });
                }
                 */
            }

        }).exceptionally(ex -> {
            logger.error("LSP hover request failed.", ex);
            return null;
        });
    }

    /*
    private String extractHoverContent(Either<List<Either<String, org.eclipse.lsp4j.MarkedString>>, org.eclipse.lsp4j.MarkupContent> contents) {
        if (contents == null) {
            return "";
        }

        // Moderner Ansatz (bevorzugt): Der Server sendet direkt MarkupContent.
        if (contents.isRight()) {
            org.eclipse.lsp4j.MarkupContent markupContent = contents.getRight();
            return (markupContent != null) ? markupContent.getValue() : "";
        }

        // Veralteter Ansatz: Der Server sendet eine Liste. Wir wandeln sie in einen String um.
        if (contents.isLeft()) {
            return contents.getLeft().stream()
                    .map(this::formatLegacyHoverItem) // Jedes Element an eine Hilfsmethode übergeben
                    .collect(Collectors.joining("\n\n---\n\n")); // Ergebnisse mit einem sichtbaren Trenner verbinden
        }

        return "";
    }
     */

    /*
     * Hilfsmethode, um ein einzelnes Element aus der veralteten Hover-Liste zu formatieren.
     *
     * @param item Ein Either, das entweder einen String oder ein MarkedString enthält.
     * @return Der formatierte String-Inhalt.
     */
    /*
    private String formatLegacyHoverItem(Either<String, org.eclipse.lsp4j.MarkedString> item) {
        // Fall 1: Das Element ist ein einfacher String.
        if (item.isLeft()) {
            return item.getLeft();
        }

        // Fall 2: Das Element ist ein (veraltetes) MarkedString.
        if (item.isRight()) {
            org.eclipse.lsp4j.MarkedString markedString = item.getRight();
            String language = markedString.getLanguage();
            String value = markedString.getValue();

            if (language != null && !language.isEmpty()) {
                return "TEST";
            }

            // Wenn eine Sprache angegeben ist, formatieren wir es als Markdown-Code-Block.
            return language + "\n" + value + "\n";
        }

        return ""; // Should not be reached
    }
     */

    public File getXmlFile() {
        return xmlFile;
    }

    public void setXmlFile(File xmlFile) {
        this.xmlFile = xmlFile;
        this.setText(xmlFile.getName());
    }

    public XmlService getXmlService() {
        return xmlService;
    }

    public void increaseFontSize() {
        setFontSize(++fontSize);
    }

    public void decreaseFontSize() {
        setFontSize(--fontSize);
    }

    private void resetFontSize() {
        fontSize = DEFAULT_FONT_SIZE;
        setFontSize(fontSize);
    }

    private void setFontSize(int size) {
        codeArea.setStyle("-fx-font-size: " + size + "pt;");
    }

    public void refresh() {
        if (this.xmlFile != null && this.xmlFile.exists()) {
            // 1. Textansicht direkt aus der Datei laden. Dies ist die primäre und robusteste Aktion.
            refreshTextView();

            // 2. Versuchen, das Dokument für die grafische Ansicht zu parsen.
            //    Dies kann fehlschlagen, beeinträchtigt aber nicht die Textansicht.
            //    Die grafische Ansicht wird bei Auswahl des Tabs ohnehin neu generiert,
            //    aber wir können es hier schon mal versuchen.
            try {
                xmlService.setCurrentXmlFile(this.xmlFile);
                this.document = xmlService.getXmlDocument();
                // Die grafische Ansicht wird bei Bedarf beim Tab-Wechsel aktualisiert,
                // daher ist ein direkter Aufruf von refreshGraphicView() hier nicht zwingend.
            } catch (Exception e) {
                logger.warn("XML-Dokument konnte für die grafische Ansicht nicht initial geparst werden: {}", e.getMessage());
                this.document = null;
            }
        }
    }


    void refreshTextView() {
        if (xmlFile == null || !xmlFile.exists()) {
            codeArea.clear();
            return;
        }

        try {
            // Lese den Dateiinhalt direkt aus der Datei, anstatt vom geparsten Dokument.
            // Das stellt sicher, dass der Inhalt auch bei ungültigem XML angezeigt wird.
            final String content = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
            codeArea.replaceText(content);
            // Das Syntax-Highlighting wird automatisch über den Listener der textProperty ausgelöst.
        } catch (IOException e) {
            logger.error("Konnte Datei für Textansicht nicht lesen: {}", xmlFile.getAbsolutePath(), e);
            codeArea.replaceText("Fehler: Konnte Datei nicht lesen.\n" + e.getMessage());
        }
    }

    public Document getDocument() {
        return this.document;
    }

    private String getDocumentAsString() {
        if (document == null) {
            return null;
        }
        try {
            Transformer transformer = transformerFactory.newTransformer();
            StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
            return stringWriter.toString();
        } catch (Exception e) {
            logger.error("Fehler bei der Konvertierung des Dokuments in einen String: {}", e.getMessage());
            return null;
        }
    }

    private void refreshGraphicView() {
        try {
            BackgroundFill backgroundFill = new BackgroundFill(
                    Color.rgb(200, 200, 50, 0.5),
                    new CornerRadii(5),
                    new Insets(5)
            );

            ScrollPane pane = new ScrollPane();
            pane.setBackground(new Background(backgroundFill));
            VBox vBox = new VBox();
            vBox.setPadding(new Insets(3));
            pane.setContent(vBox);

            if (document != null) {
                var simpleNodeElement = new SimpleNodeElement(document, this);
                VBox.setVgrow(simpleNodeElement, Priority.ALWAYS);
                vBox.getChildren().add(simpleNodeElement);
            }
            this.graphic.setContent(pane);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Searches for the given text in the CodeArea and highlights the first occurrence
     * by selecting it.
     *
     * @param text The text to search for. If null or empty, the highlight is cleared.
     */
    public void searchAndHighlight(String text) {
        // If search text is empty, just clear any existing selection/highlight
        if (text == null || text.isEmpty()) {
            codeArea.deselect();
            return;
        }

        String content = codeArea.getText();
        // Search for the first occurrence from the beginning of the document
        int index = content.indexOf(text);

        if (index != -1) {
            // Found the text, select it to highlight it
            codeArea.selectRange(index, index + text.length());
            // Scroll the view to the found text
            codeArea.requestFollowCaret();
        } else {
            // Text not found, clear any existing selection
            codeArea.deselect();
        }
    }

    /**
     * Clears the current search highlight by deselecting any selected text.
     */
    public void clearHighlight() {
        codeArea.deselect();
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

                    if (!attributesText.isEmpty()) {
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
}