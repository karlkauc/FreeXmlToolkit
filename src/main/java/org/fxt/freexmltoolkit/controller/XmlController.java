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
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.controls.XmlTreeView;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

public class XmlController {

    XmlService xmlService = XmlServiceImpl.getInstance();

    @FXML
    XmlTreeView xmlTreeView;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(XmlController.class);


    CodeArea codeAreaXpath = new CodeArea();
    CodeArea codeAreaXQuery = new CodeArea();

    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXpath;
    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXQuery;

    @FXML
    Button openFile, saveFile, prettyPrint, newFile, validateSchema, runXpathQuery;

    @FXML
    StackPane stackPaneXPath, stackPaneXQuery;

    @FXML
    ComboBox<File> schemaList = new ComboBox<>();

    String lastOpenDir;
    FileChooser fileChooser = new FileChooser();

    @FXML
    HBox test;

    @FXML
    Label schemaValidText;

    @FXML
    Tab xPathTab, xQueryTab;

    @FXML
    TabPane xPathQueryPane, xmlFilesPane;

    @FXML
    TextArea textAreaTemp;

    private int fontSize = 11;

    @FXML
    private void initialize() {
        logger.debug("Bin im xmlController init");
        xmlService = XmlServiceImpl.getInstance();

        schemaValidText.setText("");

        codeAreaXpath.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXpath));
        virtualizedScrollPaneXpath = new VirtualizedScrollPane<>(codeAreaXpath);
        stackPaneXPath.getChildren().add(virtualizedScrollPaneXpath);
        codeAreaXpath.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXpath.setStyleSpans(0, XmlEditor.computeHighlighting(newText))));

        codeAreaXQuery.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXQuery));
        virtualizedScrollPaneXQuery = new VirtualizedScrollPane<>(codeAreaXQuery);
        stackPaneXQuery.getChildren().add(virtualizedScrollPaneXQuery);
        codeAreaXQuery.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeAreaXQuery.setStyleSpans(0, XmlEditor.computeHighlighting(newText))));

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
    }

    @FXML
    private void newFilePressed() {
        logger.debug("New File Pressed");

        XmlEditor x = new XmlEditor();
        xmlFilesPane.getTabs().add(x);
        xmlFilesPane.getSelectionModel().select(x);
    }

    private XmlEditor getCurrentXmlEditor() {
        Tab active = xmlFilesPane.getSelectionModel().getSelectedItem();
        return (XmlEditor) active;
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
    public void increaseFontSize() {
        var c = getCurrentCodeArea();
        c.setStyle("-fx-font-size: " + ++fontSize + "pt;");
    }

    @FXML
    public void decreaseFontSize() {
        var c = getCurrentCodeArea();
        c.setStyle("-fx-font-size: " + --fontSize + "pt;");
    }


    @FXML
    public void runXpathQueryPressed() {
        logger.debug("BUTTON PRESSED");

        var currentCodeArea = getCurrentCodeArea();
        String xml = currentCodeArea.getText();

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
                    result = xmlService.getXmlFromXpath(xml, query);
                    break;
            }

            if (result != null && !result.equals("")) {
                logger.debug(result);
                currentCodeArea.clear();
                currentCodeArea.replaceText(0, 0, result);
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

        try {
            XmlEditor xmlEditor = (XmlEditor) xmlFilesPane.getSelectionModel().getSelectedItem();
            if (xmlEditor != null && xmlEditor.getXmlFile() != null && xmlEditor.getXmlFile().exists()) {
                xmlEditor.refresh();

                if (xmlEditor.getXmlFile() != null) {
                    textAreaTemp.setText(xmlEditor.getXmlFile().getName());
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public boolean saveFile() {
        logger.debug("Code Area selected");
        var errors = xmlService.validateText(getCurrentCodeArea().getText());

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
        return false;
    }

    private boolean saveTextToFile() {
        try {
            var currentXmlEditor = getCurrentXmlEditor();
            File xmlFile = currentXmlEditor.getXmlFile();

            if (xmlFile == null) {
                if (lastOpenDir == null) {
                    lastOpenDir = Path.of(".").toString();
                    logger.debug("New last open Dir: {}", lastOpenDir);
                }

                fileChooser.setInitialDirectory(new File(lastOpenDir));
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));
                File selectedFile = fileChooser.showSaveDialog(null);

                if (selectedFile != null) {
                    this.xmlService.setCurrentXmlFile(selectedFile);
                    currentXmlEditor.setXmlFile(selectedFile);
                    currentXmlEditor.setText(selectedFile.getName());

                    if (!selectedFile.exists()) {
                        Files.writeString(selectedFile.toPath(), getCurrentCodeArea().getText(), Charset.defaultCharset());
                        this.schemaValidText.setText("Saved " + selectedFile.length() + " Bytes");
                    }
                }

            } else {
                byte[] strToBytes = getCurrentCodeArea().getText().getBytes();
                Files.write(xmlFile.toPath(), strToBytes);
            }

            Path path = Paths.get(this.xmlService.getCurrentXmlFile().getPath());
            byte[] strToBytes = getCurrentCodeArea().getText().getBytes();
            Files.write(path, strToBytes);

            logger.debug("File saved!");
            this.xmlService.setCurrentXmlFile(path.toFile());
            schemaValidText.setText("File '" + path + "' saved (" + path.toFile().length() + " bytes)");

            return true;
        } catch (Exception e) {
            logger.error("Exception in writing File: {}", e.getMessage());
            // logger.error("File: {}", this.xmlService.getCurrentXmlFile().getAbsolutePath());
        }
        return false;
    }

    public void formatXmlText() {
        CodeArea ca = getCurrentCodeArea();
        String text = ca.getText();

        // logger.debug("Text before formatting: {}", text);
        ca.clear();

        final String tempFormat = XmlService.prettyFormat(text, 20);
        logger.debug("Format String length: {}", tempFormat.length());
        ca.replaceText(0, 0, tempFormat);

        // logger.debug("Text after formatting: {}", tempFormat);
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

            if (getCurrentCodeArea().getText().length() > 1) {
                var errors = xmlService.validateText(getCurrentCodeArea().getText());
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
        logger.debug("Caret Pos: {}", getCurrentCodeArea().caretPositionProperty().getValue());
        getCurrentCodeArea().scrollToPixel(0, 0);
        getCurrentCodeArea().requestFocus();
    }

    @FXML
    private void moveDown() {
        logger.debug("Caret Pos: {}", getCurrentCodeArea().caretPositionProperty().getValue());

        var area = getCurrentCodeArea();
        if (area != null && area.getText() != null) {
            area.moveTo(0, area.getText().length());
        }
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

            XmlEditor xmlEditor = new XmlEditor(selectedFile);
            xmlEditor.refresh();

            xmlFilesPane.getTabs().add(xmlEditor);
            xmlFilesPane.getSelectionModel().select(xmlEditor);

            xmlService.setCurrentXmlFile(selectedFile);
            if (xmlService.loadSchemaFromXMLFile()) {
                schemaList.getItems().add(xmlService.getCurrentXsdFile());
            }

            validateSchema();
        } else {
            logger.debug("No file selected");
        }
    }

    public void displayFileContent(File file) {
        if (file.exists() && file.isFile()) {
            try {
                getCurrentCodeArea().replaceText(0, 0, Files.readString(Path.of(file.toURI())));
            } catch (IOException exception) {
                logger.error(exception.getMessage());
            }
        }
    }

    @FXML
    private void test() {
        Path xmlExampleFile = Paths.get("examples/xml/FundsXML_422_Bond_Fund.xml");
        xmlService.setCurrentXmlFile(xmlExampleFile.toFile());
        xmlService.setCurrentXsdFile(Paths.get("examples/xsd/FundsXML4.xsd").toFile());

        try {
            XmlEditor xmlEditor = getCurrentXmlEditor();
            xmlEditor.setXmlFile(xmlExampleFile.toFile());
            xmlEditor.refresh();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        xmlService.setCurrentXmlFile(xmlExampleFile.toFile());
        if (xmlService.loadSchemaFromXMLFile()) {
            schemaList.getItems().add(xmlService.getCurrentXsdFile());
        }

        validateSchema();
    }
}
