/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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
import javafx.geometry.Side;
import javafx.scene.control.*;
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
import java.time.LocalDateTime;
import java.util.*;
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

    CodeArea codeAreaXpath = new CodeArea();
    CodeArea codeAreaXQuery = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXpath;
    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXQuery;

    @FXML
    Button openFile, saveFile, prettyPrint, newFile, validateSchema, runXpathQuery;

    @FXML
    StackPane stackPane, stackPaneXPath, stackPaneXQuery;

    @FXML
    ComboBox<File> schemaList = new ComboBox<>();

    String lastOpenDir;
    FileChooser fileChooser = new FileChooser();

    @FXML
    HBox test;

    @FXML
    Label schemaValidText;

    @FXML
    Tab text, graphic, xPathTab, xQueryTab, openFileTab;

    @FXML
    TabPane xPathQueryPane, xmlFilesPane;

    List<Tab> openFileTabs = new LinkedList<>();

    @FXML
    private void initialize() {
        logger.debug("Bin im xmlController init");
        xmlService = XmlServiceImpl.getInstance();

        schemaValidText.setText("");

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

        codeAreaXpath.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXpath));
        virtualizedScrollPaneXpath = new VirtualizedScrollPane<>(codeAreaXpath);
        stackPaneXPath.getChildren().add(virtualizedScrollPaneXpath);
        codeAreaXpath.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXpath.setStyleSpans(0, computeHighlighting(newText))));

        codeAreaXQuery.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXQuery));
        virtualizedScrollPaneXQuery = new VirtualizedScrollPane<>(codeAreaXQuery);
        stackPaneXQuery.getChildren().add(virtualizedScrollPaneXQuery);
        codeAreaXQuery.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXQuery.setStyleSpans(0, computeHighlighting(newText))));

        var t = System.getenv("debug");
        if (t != null) {
            logger.debug("set visible false");
            test.setVisible(true);

            codeAreaXpath.replaceText(0, 0, "/FundsXML4/ControlData");
            codeAreaXQuery.replaceText(0, 0, "for $i in /FundsXML4/Funds/Fund\n" +
                    "    return\n" +
                    "        string-join(\n" +
                    "            (\n" +
                    "                $i/Names/OfficialName,\n" +
                    "                $i/Currency,\n" +
                    "                $i/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$i/Currency]/text(),\n" +
                    "                string(sum($i/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$i/Currency])),\n" +
                    "                string($i/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$i/Currency] - sum($i/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$i/Currency]))\n" +
                    "            ), ' | '\n" +
                    "        )");
        }

        reloadXmlText();
        openFileTabs.add(openFileTab);
    }

    @FXML
    private void newFilePressed() {
        logger.debug("New File Pressed");
        logger.debug("Current Open Files: {}", openFileTabs.size());

        Tab t = new Tab("Untitled.xml *");

        TabPane tb = new TabPane();
        tb.setSide(Side.LEFT);

        Tab tabText = new Tab("XML");

        StackPane sp = new StackPane();
        CodeArea ca = new CodeArea();
        ca.setParagraphGraphicFactory(LineNumberFactory.get(ca));
        VirtualizedScrollPane<CodeArea> vsc = new VirtualizedScrollPane<>(ca);
        sp.getChildren().add(vsc);
        ca.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> ca.setStyleSpans(0, computeHighlighting(newText))));
        tabText.setContent(sp);

        Tab tabGraphic = new Tab("Tree");
        tb.getTabs().addAll(tabText, tabGraphic);

        t.setContent(tb);
        xmlFilesPane.getTabs().add(t);
        xmlFilesPane.getSelectionModel().select(t);
    }

    private CodeArea getCurrentCodeArea() {
        Tab active = xmlFilesPane.getSelectionModel().getSelectedItem();
        TabPane tp = (TabPane) active.getContent();
        Tab tabText = tp.getTabs().get(0);
        StackPane sp = (StackPane) tabText.getContent();
        VirtualizedScrollPane<?> vsp = (VirtualizedScrollPane<?>) sp.getChildren().get(0);

        return (CodeArea) vsp.getContent();
    }

    @FXML
    public void runXpathQueryPressed() {
        logger.debug("BUTTON PRESSED");

        String xml = getCurrentCodeArea().getText();

        if (xml != null) {
            Tab selectedItem = xPathQueryPane.getSelectionModel().getSelectedItem();
            String query = ((CodeArea) ((VirtualizedScrollPane<?>) ((StackPane) selectedItem.getContent()).getChildren().get(0)).getContent()).getText();
            String result = "";

            logger.debug("QUERY: {}", query);

            switch (selectedItem.getId()) {
                case "xQueryTab":
                    result = xmlService.getXQueryResult(query).toString();
                    break;

                case "xPathTab":
                    result = xmlService.getXmlFromXpath(query);
                    break;
            }

            if (!Objects.equals(result, "")) {
                logger.debug(result);
                codeArea.clear();
                codeArea.replaceText(0, 0, result);
            }
        }
    }

    public void setParentController(MainController parentController) {
        logger.debug("XML Controller - set parent controller");
        this.parentController = parentController;
    }

    @FXML
    public void reloadXmlText() {
        logger.debug("Reload XML Text");
        codeArea.clear();

        try {
            if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()) {
                codeArea.replaceText(0, 0, Files.readString(xmlService.getCurrentXmlFile().toPath()));
                codeArea.scrollToPixel(1, 1);
                openFileTab.setText(xmlService.getCurrentXmlFile().getName());
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

                if (xmlTreeView != null) {
                    xmlTreeView.setXmlDocument(xmlDocument);
                }
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            logger.error("ERROR: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public boolean saveCurrentChanges() {
        if (text.isSelected()) {
            logger.debug("Code Area selected");
            var errors = xmlService.validateText(codeArea.getText());

            if (errors == null || errors.size() == 0) {
                return saveTextToFile();
            } else {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION);
                a.setTitle("Text not schema Valid");
                a.setHeaderText(errors.size() + " Errors found.");
                a.setContentText("Save anyway?");

                var result = a.showAndWait();
                if (result.isPresent()) {
                    var buttonType = result.get();
                    if (buttonType == ButtonType.OK) {
                        return saveTextToFile();
                    }
                }
            }
        }
        if (graphic.isSelected()) {
            logger.debug("Graphic selected");
        }
        return false;
    }

    private boolean saveTextToFile() {
        try {
            Path path = Paths.get(this.xmlService.getCurrentXmlFile().getPath());
            byte[] strToBytes = codeArea.getText().getBytes();
            Files.write(path, strToBytes);

            logger.debug("File saved!");
            this.xmlService.setCurrentXmlFile(path.toFile());
            schemaValidText.setText("File '" + path + "' saved (" + path.toFile().length() + " bytes)");

            return true;
        } catch (Exception e) {
            logger.error("Exception in writing File: {}", e.getMessage());
            logger.error("File: {}", this.xmlService.getCurrentXmlFile().getAbsolutePath());
        }
        return false;
    }

    public void formatXmlText() {
        CodeArea ca = getCurrentCodeArea();
        String text = ca.getText();

        logger.debug("Text before formatting: {}", text);
        ca.clear();

        final String tempFormat = XmlService.prettyFormat(text, 20);
        logger.debug("Format String length: {}", tempFormat.length());
        ca.replaceText(0, 0, tempFormat);

        logger.debug("Text after formatting: {}", tempFormat);
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

            if (codeArea.getText().length() > 1) {
                var errors = xmlService.validateText(codeArea.getText());
                if (errors.size() > 0) {
                    Alert t = new Alert(Alert.AlertType.ERROR);
                    t.setTitle(errors.size() + " validation Errors");
                    StringBuilder temp = new StringBuilder();
                    for (SAXParseException error : errors) {
                        temp.append(error.getMessage()).append(System.lineSeparator());
                    }

                    schemaValidText.setText(LocalDateTime.now() + "... Schema not valid!");

                    t.setContentText(temp.toString());
                    t.showAndWait();
                } else {
                    schemaValidText.setText(LocalDateTime.now() + "... Schema Valid!");
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
            validateSchema();
        } else {
            logger.debug("No file selected");
        }
    }

    @FXML
    private void test() {
        xmlService.setCurrentXmlFile(Paths.get("examples/xml/FundsXML_422_Bond_Fund.xml").toFile());
        xmlService.setCurrentXsdFile(Paths.get("examples/xsd/FundsXML4.xsd").toFile());
        reloadXmlText();
        validateSchema();
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
