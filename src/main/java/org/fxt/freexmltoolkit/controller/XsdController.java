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

package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
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

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class XsdController {
    XmlService xmlService = XmlServiceImpl.getInstance();

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    Pane xsdPane, xmlPane;

    @FXML
    Button newFile, openFile, saveFile, prettyPrint, validateSchema, openDocFolder;

    DirectoryChooser documentationOutputDirectory;
    File selectedDocumentationOutputDirectory;

    @FXML
    TextField documentationOutputDirPath, xsdFilePath;

    @FXML
    Label schemaValidText, statusText;

    @FXML
    StackPane stackPane, xsdStackPane;

    @FXML
    TextArea sampleData;

    @FXML
    CheckBox openFileAfterCreation, useMarkdownRenderer, createExampleData;

    @FXML
    ProgressIndicator progressDocumentation = new ProgressIndicator(0);

    @FXML
    ChoiceBox<String> grafikFormat;

    @FXML
    TabPane tabPane;
    @FXML
    Tab documentation;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(XsdController.class);

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }


    @FXML
    private void initialize() {
        setupCodeArea();
        setupDragAndDrop();
        reloadXmlText();
        setupXsdDiagram();
    }

    private void setupXsdDiagram() {
        if (this.xmlService.getCurrentXsdFile() != null) {
            logger.debug("Loading XSD File: {}", this.xmlService.getCurrentXsdFile().getAbsolutePath());
            xsdStackPane.getChildren().clear();

            //xsdStackPane.getChildren().add()
        }

    }

    private void setupCodeArea() {
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.length() < 2 * 1024 * 1024) { // Max 2 MB
                Platform.runLater(() -> codeArea.setStyleSpans(0, XmlEditor.computeHighlighting(newText)));
            }
        });
        virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
        stackPane.getChildren().add(virtualizedScrollPane);
    }

    private void setupDragAndDrop() {
        xsdPane.setOnDragOver(this::handleFileOverEvent);
        xsdPane.setOnDragExited(event -> resetDragStyle());
        xsdPane.setOnDragDropped(this::handleFileDroppedEvent);
    }

    private void resetDragStyle() {
        xsdPane.getStyleClass().clear();
        xsdPane.getStyleClass().add("tab-pane");
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
        if (isValidDocumentationSetup()) {
            progressDocumentation.setVisible(true);
            Task<Void> task = createDocumentationTask();
            progressDocumentation.progressProperty().bind(task.progressProperty());
            statusText.textProperty().bind(task.messageProperty());
            executeTask(task);
        } else {
            logger.debug("Invalid setup for documentation generation");
        }
    }

    private boolean isValidDocumentationSetup() {
        return selectedDocumentationOutputDirectory != null &&
                xmlService.getCurrentXsdFile() != null &&
                xmlService.getCurrentXsdFile().exists();
    }

    private Task<Void> createDocumentationTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                try {
                    XsdDocumentationService xsdDocService = new XsdDocumentationService();
                    xsdDocService.setXsdFilePath(xmlService.getCurrentXsdFile().getPath());
                    xsdDocService.setMethod(grafikFormat.getValue().equals("SVG") ?
                            XsdDocumentationService.ImageOutputMethod.SVG :
                            XsdDocumentationService.ImageOutputMethod.PNG);
                    xsdDocService.generateXsdDocumentation(selectedDocumentationOutputDirectory);

                    if (openFileAfterCreation.isSelected()) {
                        Desktop.getDesktop().open(new File(selectedDocumentationOutputDirectory, "index.html"));
                    }
                } catch (IOException e) {
                    logger.error("Error generating documentation", e);
                }
                return null;
            }
        };
    }

    private void executeTask(Task<Void> task) {
        if (parentController != null) {
            parentController.service.execute(task);
        } else {
            logger.warn("Parent controller is null");
        }
    }

    @FXML
    void handleFileOverEvent(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!xsdPane.getStyleClass().contains("xmlPaneFileDragDrop-active")) {
                xsdPane.getStyleClass().add("xmlPaneFileDragDrop-active");
            }
        } else {
            event.consume();
        }
    }

    @FXML
    void handleDragExitedEvent(DragEvent event) {
        xsdPane.getStyleClass().clear();
        xsdPane.getStyleClass().add("tab-pane");
    }

    @FXML
    void handleFileDroppedEvent(DragEvent event) {
        Dragboard db = event.getDragboard();

        for (File f : db.getFiles()) {
            logger.debug("FILE: {}", f.getAbsoluteFile());
            if (f.isFile() && f.exists() && f.getAbsolutePath().toLowerCase().endsWith(".xsd")) {
                this.xmlService.setCurrentXsdFile(f);
                this.xsdFilePath.setText(f.getName());
            }
        }
    }

    @FXML
    private void test() {
        final var testFilePath = Paths.get("release/examples/xsd/purchageOrder.xsd");
        final var outputFilePath = Paths.get("output/test");

        if (Files.exists(testFilePath)) {
            this.xmlService.setCurrentXsdFile(testFilePath.toFile());
            this.xsdFilePath.setText(testFilePath.toFile().getName());

            this.selectedDocumentationOutputDirectory = outputFilePath.toFile();
            this.documentationOutputDirPath.setText(outputFilePath.toFile().getAbsolutePath());

            generateDocumentation();

            try {
                this.codeArea.replaceText(0, 0, this.xmlService.getCurrentXsdString());
            } catch (IOException ioException) {
                logger.error(ioException.getMessage());
            }
        } else {
            logger.debug("test file not found: {}", testFilePath.toFile().getAbsolutePath());
        }
    }
}
