package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
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

    // Property, um eine Such-Aktion von außen zu registrieren
    private final ObjectProperty<Runnable> onSearchRequested = new SimpleObjectProperty<>();

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

    private void initialize() {
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            codeArea.setStyleSpans(0, computeHighlighting(newText));
        });

        setupEventHandlers();

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
            // Schriftgröße mit Strg +/-, Reset mit Strg + 0
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.PLUS || event.getCode() == KeyCode.ADD) {
                    increaseFontSize();
                    event.consume();
                } else if (event.getCode() == KeyCode.MINUS || event.getCode() == KeyCode.SUBTRACT) {
                    decreaseFontSize();
                    event.consume();
                } else if (event.getCode() == KeyCode.NUMPAD0 || event.getCode() == KeyCode.DIGIT0) {
                    resetFontSize();
                    event.consume();
                }
            }

            // NEU: Suche mit Strg + F
            if (event.isControlDown() && event.getCode() == KeyCode.F) {
                if (getOnSearchRequested() != null) {
                    getOnSearchRequested().run();
                    event.consume();
                }
            }
        });
    }

    // --- Öffentliche API für den Editor ---

    /**
     * Setzt die Aktion, die bei Strg+F ausgeführt werden soll.
     * @param value Die auszuführende Aktion (Runnable).
     */
    public final void setOnSearchRequested(Runnable value) {
        onSearchRequested.set(value);
    }

    public final Runnable getOnSearchRequested() {
        return onSearchRequested.get();
    }

    public final ObjectProperty<Runnable> onSearchRequestedProperty() {
        return onSearchRequested;
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

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
}