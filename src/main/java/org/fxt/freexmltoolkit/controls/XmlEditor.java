/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.TwoDimensional;
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
import java.util.Collection;
import java.util.Collections;
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

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);

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

    public XmlEditor(File f) {
        init();
        this.setXmlFile(f);
        this.refresh();
    }

    public XmlEditor() {
        init();
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
                }
        );

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() < MAX_SIZE_FOR_FORMATTING) {
                logger.debug("Starte Formatierung...");
                codeArea.setStyleSpans(0, computeHighlighting(newText));
            }
        });

        codeArea.caretPositionProperty().addListener((observable, oldValue, newValue) -> {
            var lineColumn = codeArea.offsetToPosition(newValue, TwoDimensional.Bias.Forward);
            int lineNumber = lineColumn.getMajor() + 1; // Line numbers are 0-based, so add 1
            int columnNumber = lineColumn.getMinor() + 1; // Column numbers are 0-based, so add 1
            // logger.debug("Line: {}, Column: {}", lineNumber, columnNumber);
        });

        codeArea.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                if (event.getDeltaY() > 0) {
                    increaseFontSize();
                } else {
                    decreaseFontSize();
                }
            }
        });

        codeArea.addEventFilter(KeyEvent.ANY, event -> {
            // Nur auf gedrückte Tasten reagieren, um doppelte Ausführung zu vermeiden
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                // Strg+F für die Suche abfangen
                if (event.isControlDown() && event.getCode() == KeyCode.F) {
                    if (getOnSearchRequested() != null) {
                        getOnSearchRequested().run();
                        event.consume(); // Verhindert, dass das Event weiterverarbeitet wird
                    }
                }
                // Strg+0 für das Zurücksetzen des Zooms
                else if (event.isControlDown() && (event.getCode() == KeyCode.NUMPAD0 || event.getCode() == KeyCode.DIGIT0)) {
                    resetFontSize();
                    event.consume();
                }
            }
        });


        stackPane.getChildren().add(virtualizedScrollPane);
        codeArea.setLineHighlighterOn(true);
        setFontSize(DEFAULT_FONT_SIZE);

        xml.setContent(stackPane);

        this.setText(DEFAULT_FILE_NAME);
        this.setClosable(true);
        this.setOnCloseRequest(eh -> logger.debug("Close Event"));

        this.setContent(tabPane);
    }

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
            xmlService.setCurrentXmlFile(this.xmlFile);
            document = xmlService.getXmlDocument();

            refreshTextView();
            refreshGraphicView();
        }
    }


    void refreshTextView() {
        try {
            final String content = getDocumentAsString();
            if (content != null) {
                codeArea.replaceText(content);
            } else {
                codeArea.clear();
            }
        } catch (Exception e) {
            logger.error("Konnte die Textansicht nicht aktualisieren: {}", e.getMessage(), e);
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