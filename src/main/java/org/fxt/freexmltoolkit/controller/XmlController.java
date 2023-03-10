/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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

package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.controls.XmlTreeView;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlController {

    // MAX File Size for formating Syntax
    public static final int MAX_SIZE_FOR_FORMATING = 1024 * 1024 * 20;
    XmlService xmlService = XmlServiceImpl.getInstance();

    @FXML
    XmlTreeView xmlTreeView;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(XmlController.class);

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

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    Button openFile, saveFile, prettyPrint, newFile, validateSchema;

    @FXML
    StackPane stackPane;

    @FXML
    Button xpath;

    @FXML
    TextArea xpathText;

    @FXML
    ComboBox<File> schemaList = new ComboBox<>();

    String lastOpenDir;
    FileChooser fileChooser = new FileChooser();

    @FXML
    HBox test;

    @FXML
    private void initialize() {
        logger.debug("Bin im xmlController init");
        xmlService = XmlServiceImpl.getInstance();

        var t = System.getenv("debug");
        if (t != null) {
            logger.debug("set visible false");
            test.setVisible(true);
        }

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() < MAX_SIZE_FOR_FORMATING) {
                logger.debug("Format Text begin!");
                Platform.runLater(() -> {
                    codeArea.setStyleSpans(0, computeHighlighting(newText));
                    logger.debug("FINISH REFORMAT TEXT in XmlController");
                });
            } else {
                logger.debug("FILE TOO BIG: {}", newText.length());
            }
        });

        virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
        stackPane.getChildren().add(virtualizedScrollPane);

        reloadXmlText();
    }

    public void setParentController(MainController parentController) {
        logger.debug("XML Controller - set parent controller");
        this.parentController = parentController;
    }

    @FXML
    private void evaluateXpath() {
        var newString = this.xmlService.getXmlFromXpath(xpathText.getText());

        if (!newString.isEmpty()) {
            logger.debug("New String: {}", newString);
            codeArea.clear();
            codeArea.replaceText(0, 0, newString);
        } else {
            logger.debug("XPath is empty!");
        }
    }

    @FXML
    public void reloadXmlText() {
        logger.debug("Reload XML Text");
        codeArea.clear();
        // codeArea.setBackground(null);

        try {
            if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()) {
                codeArea.replaceText(0, 0, Files.readString(xmlService.getCurrentXmlFile().toPath()));
                codeArea.scrollToPixel(1, 1);
            } else {
                logger.warn("FILE IS NULL");
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

        try {
            if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()) {
                FileInputStream fileIS = new FileInputStream(xmlService.getCurrentXmlFile());
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                var builder = builderFactory.newDocumentBuilder();
                var xmlDocument = builder.parse(fileIS);

                xmlTreeView.setXmlDocument(xmlDocument);
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            logger.error("ERROR: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public boolean saveCurrentChanges() {
        if (this.codeArea.getText() != null) {
            if (this.xmlService.getCurrentXmlFile() != null) {
                try {
                    Path path = Paths.get(this.xmlService.getCurrentXmlFile().getPath());
                    byte[] strToBytes = codeArea.getText().getBytes();
                    Files.write(path, strToBytes);

                    this.xmlService.setCurrentXmlFile(path.toFile());
                    return true;
                } catch (Exception e) {
                    logger.error("Exception in writing File: {}", e.getMessage());
                    logger.error("File: {}", this.xmlService.getCurrentXmlFile().getAbsolutePath());
                }
            }
        } else {
            logger.debug("No Text");
        }
        return false;
    }

    public void formatXmlText() {
        var temp = codeArea.getText();
        codeArea.clear();

        final String tempFormat = XmlService.prettyFormat(temp, 20);
        logger.debug("Format String length: {}", tempFormat.length());
        codeArea.replaceText(0, 0, tempFormat);
    }

    @FXML
    private void validateSchema() {
        logger.debug("Validate Schema");
        xmlService.loadSchemaFromXMLFile(); // schaut, ob er schema selber finden kann
        String xsdLocation = xmlService.getRemoteXsdLocation();
        logger.debug("XSD Location: {}", xsdLocation);

        if (xsdLocation != null) {
            xmlService.loadSchemaFromXMLFile();
            logger.debug("Schema loaded: {}", xsdLocation);

            if (xmlService.getCurrentXsdFile().length() > 1) {
                var errors = xmlService.validate();
                if (errors.size() > 0) {
                    Alert t = new Alert(Alert.AlertType.ERROR);
                    t.setTitle(errors.size() + " validation Errors");
                    StringBuilder temp = new StringBuilder();
                    for (SAXParseException error : errors) {
                        temp.append(error.getMessage()).append(System.lineSeparator());
                    }

                    t.setContentText(temp.toString());
                    t.showAndWait();
                }
            }
        }
    }

    @FXML
    private void moveUp() {
        logger.debug("Caret Pos: {}", codeArea.caretPositionProperty().getValue());
        codeArea.scrollToPixel(0, 0);
    }

    @FXML
    private void moveDown() {
        codeArea.moveTo(codeArea.getLength());
        codeArea.getCaretBounds().ifPresent(bounds -> {
            System.out.println("MAX X: " + bounds.getMaxX());
            System.out.println("MAX Y: " + bounds.getMaxY());
        });
        codeArea.scrollToPixel(codeArea.getLayoutBounds().getMaxX(), codeArea.getLayoutBounds().getMaxY());
    }

    @FXML
    private void openFile() {
        logger.debug("Last open Dir: {}", lastOpenDir);
        if (lastOpenDir == null) {
            lastOpenDir = Path.of(".").toString();
            logger.debug("New last open Dir: {}", lastOpenDir);
        }

        fileChooser.setInitialDirectory(new File(lastOpenDir));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());
            this.lastOpenDir = selectedFile.getParent();

            xmlService.setCurrentXmlFile(selectedFile);
            if (xmlService.loadSchemaFromXMLFile()) {
                schemaList.getItems().add(xmlService.getCurrentXsdFile());
            }

            reloadXmlText();
        } else {
            logger.debug("No file selected");
        }
    }

    @FXML
    private void test() {
        xmlService.setCurrentXmlFile(Paths.get("C:\\Data\\src\\FreeXmlToolkit\\examples\\xml\\FundsXML_422_Bond_Fund.xml").toFile());
        xmlService.setCurrentXsdFile(Paths.get("C:\\Data\\src\\FreeXmlToolkit\\examples\\xsd\\FundsXML4.xsd").toFile());
        reloadXmlText();
    }

    static StyleSpans<Collection<String>> computeHighlighting(String text) {
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
