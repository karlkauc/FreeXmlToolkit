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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.xmlet.xsdparser.xsdelements.XsdComplexType;
import org.xmlet.xsdparser.xsdelements.XsdSimpleType;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class XsdController {
    XmlService xmlService = XmlServiceImpl.getInstance();

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    Button newFile, openFile, saveFile, prettyPrint, validateSchema;

    DirectoryChooser documentationOutputDirectory;
    File selectedDocumentationOutputDirectory;


    @FXML
    TextField documentationOutputDirPath, xsdFilePath;

    @FXML
    Label schemaValidText;

    @FXML
    StackPane stackPane;

    @FXML
    TextArea documentation, sampleData;

    @FXML
    CheckBox openFileAfterCreation;

    String documentationString;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(XsdController.class);

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    final static int MAX_ALLOWED_DEPTH = 99;
    private List<XsdComplexType> xsdComplexTypes;
    private List<XsdSimpleType> xsdSimpleTypes;


    @FXML
    private void initialize() {
        logger.debug("Bin im xsdController init");

        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() < 1024 * 1024 * 2) { // MAX 2 MB große Files
                logger.debug("Format Text begin!");
                Platform.runLater(() -> {
                    codeArea.setStyleSpans(0, XmlEditor.computeHighlighting(newText));
                    logger.debug("FINISH 1");
                });
                logger.debug("Format Text fertig!");
            }
        });

        try {
            virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
            stackPane.getChildren().add(virtualizedScrollPane);
            reloadXmlText();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @FXML
    public void reloadXmlText() {
        logger.debug("Reload XSD Text");
        codeArea.clear();
        codeArea.setBackground(null);

        try {
            if (xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
                codeArea.replaceText(0, 0, Files.readString(xmlService.getCurrentXsdFile().toPath()));
                codeArea.scrollToPixel(1, 1);

                logger.debug("Caret Position: {}", codeArea.getCaretPosition());
                logger.debug("Caret Column: {}", codeArea.getCaretColumn());
            } else {
                logger.warn("FILE IS NULL");
            }
        } catch (IOException e) {
            logger.error("Error in reloadXMLText: ");
            logger.error(e.getMessage());
        }
    }

    @FXML
    private void loadXsdFile() throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Schema Definition", "*.xsd")
        );
        fileChooser.setInitialDirectory(new File("."));
        var xsdFile = fileChooser.showOpenDialog(null);

        if (xsdFile != null && xsdFile.exists()) {
            logger.debug("open File: {}", xsdFile.getAbsolutePath());
            xmlService.setCurrentXsdFile(xsdFile);
            xsdFilePath.setText(xsdFile.getName());

            codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
            virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
            stackPane.getChildren().add(virtualizedScrollPane);
            codeArea.textProperty().addListener((obs, oldText, newText) -> Platform.runLater(() -> codeArea.setStyleSpans(0, XmlEditor.computeHighlighting(newText))));

            codeArea.replaceText(0, 0, xmlService.getCurrentXsdString());
        }
    }

    @FXML
    private void test() {
        final var testFilePath = Paths.get("examples/xsd/FundsXML4.xsd");

        if (Files.exists(testFilePath)) {
            xmlService.setCurrentXsdFile(testFilePath.toFile());
            generateDocumentation();
        } else {
            logger.debug("test file not found: {}", testFilePath.toFile().getAbsolutePath());
        }
    }

    @FXML
    private void openOutputFolderDialog() {
        documentationOutputDirectory = new DirectoryChooser();
        documentationOutputDirectory.setTitle("Output Directory");
        documentationOutputDirectory.setInitialDirectory(new File("."));
        selectedDocumentationOutputDirectory = documentationOutputDirectory.showDialog(null);

        if (selectedDocumentationOutputDirectory != null && selectedDocumentationOutputDirectory.exists()) {
            logger.debug("Directory: {}", selectedDocumentationOutputDirectory.getAbsolutePath());
            documentationOutputDirPath.setText(selectedDocumentationOutputDirectory.getAbsolutePath());
        } else {
            logger.debug("no dir selected");
            documentationOutputDirPath.setText(null);
        }
    }

    @FXML
    private void generateDocumentation() {
        if (selectedDocumentationOutputDirectory != null && selectedDocumentationOutputDirectory.exists()
                && xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {

            XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();
            try {
                xsdDocumentationService.setXsdFilePath(xmlService.getCurrentXsdFile().getPath());
                xsdDocumentationService.generateXsdDocumentation(selectedDocumentationOutputDirectory);

                if (openFileAfterCreation.isSelected()) {
                    Desktop.getDesktop().open(new File(selectedDocumentationOutputDirectory.getAbsolutePath() + "/index.html"));
                }
            } catch (IOException ioException) {
                logger.error(ioException.getMessage());
            }
        }
    }
}
