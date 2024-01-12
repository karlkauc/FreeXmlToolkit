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

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Side;
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
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public XmlEditor(File f) {
        init();
        this.setXmlFile(f);
        this.refresh();
    }

    public XmlEditor() {
        init();
    }

    private void init() {
        TabPane tabPane = new TabPane();

        xml.setGraphic(new FontIcon("bi-code-slash:20"));
        graphic.setGraphic(new FontIcon("bi-columns-gap:20"));

        tabPane.setSide(Side.LEFT);
        tabPane.getTabs().addAll(xml, graphic);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() < MAX_SIZE_FOR_FORMATTING) {
                Platform.runLater(() -> codeArea.setStyleSpans(0, computeHighlighting(newText)));
            }
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
            if (event.isControlDown() && event.getCode() == KeyCode.NUMPAD0
                    || event.isControlDown() && event.getCode() == KeyCode.DIGIT0) {
                resetFontSize();
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
        if (this.xmlFile.exists()) {
            refreshTextView();
            refreshGraphicView();
        }
    }

    private void refreshTextView() {
        try {
            codeArea.clear();
            codeArea.replaceText(0, 0, Files.readString(Path.of(this.xmlFile.toURI())));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private void refreshGraphicView() {
        try {
            BackgroundFill backgroundFill =
                    new BackgroundFill(
                            Color.rgb(200, 200, 50, 0.5),
                            new CornerRadii(10),
                            new Insets(10)
                    );

            Pane pane = new Pane();
            pane.setBackground(new Background(backgroundFill));
            VBox vBox = new VBox();
            vBox.setPadding(new Insets(20));
            pane.getChildren().add(vBox);

            if (this.xmlFile != null) {
                XmlService xmlService = new XmlServiceImpl();
                xmlService.setCurrentXmlFile(this.xmlFile);
                Document document = xmlService.getXmlDocument();

                SimpleNodeElement simpleNodeElement = new SimpleNodeElement();
                simpleNodeElement.createByNode(document);

                vBox.getChildren().add(simpleNodeElement);
            }
            this.graphic.setContent(pane);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
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
