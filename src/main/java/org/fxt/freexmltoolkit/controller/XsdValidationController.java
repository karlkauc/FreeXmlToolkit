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

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

public class XsdValidationController {

    XmlService xmlService = XmlServiceImpl.getInstance();

    private MainController parentController;

    @FXML
    AnchorPane anchorPane;

    FileChooser xmlFileChooser = new FileChooser(), xsdFileChooser = new FileChooser();

    @FXML
    GridPane auswertung;

    @FXML
    Button xmlLoadButton, xsdLoadButton, test;

    @FXML
    TextField xmlFileName, xsdFileName, remoteXsdLocation;

    @FXML
    VBox errorListBox;

    @FXML
    CheckBox autodetect;

    @FXML
    ImageView statusImage;

    @FXML
    ProgressIndicator progressIndicator;

    private final static Logger logger = LogManager.getLogger(XsdValidationController.class);

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void toggleAutoDetection() {
        xsdLoadButton.setDisable(!xsdLoadButton.isDisable());
        xsdFileName.setDisable(!xsdFileName.isDisable());
    }

    @FXML
    private void initialize() {
        final Path path = FileSystems.getDefault().getPath(".");
        xmlFileChooser.setInitialDirectory(path.toFile());
        xsdFileChooser.setInitialDirectory(path.toFile());

        xmlFileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XML File", "*.xml"));
        xsdFileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XSD File", "*.xsd"));

        xmlLoadButton.setOnAction(ae -> {
            var tempFile = xmlFileChooser.showOpenDialog(null);
            if (tempFile != null) {
                logger.debug("Loaded XML File: {}", tempFile.getAbsolutePath());
                processXmlFile(tempFile);
            }
        });

        xmlLoadButton.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });

        xmlLoadButton.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    xmlService.setCurrentXmlFile(file);
                    processXmlFile(file);
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        xsdLoadButton.setOnAction(ae -> {
            var tempFile = xsdFileChooser.showOpenDialog(null);
            if (tempFile != null) {
                logger.debug("Loaded XSLT File: {}", tempFile.getAbsolutePath());
                xmlService.setCurrentXsdFile(tempFile);
                xsdFileName.setText(xmlService.getCurrentXsdFile().getName());
                if (xmlService.getCurrentXmlFile() != null) {
                    processXmlFile();
                }
            }
        });

        var t = System.getenv("debug");
        if (t != null) {
            logger.debug("set visible false");
            test.setVisible(true);
        }
    }

    @FXML
    private void processXmlFile() {
        processXmlFile(xmlService.getCurrentXmlFile());
    }

    private void processXmlFile(File file) {
        progressIndicator.setVisible(true);
        progressIndicator.setProgress(0.1);

        remoteXsdLocation.setText("");
        statusImage.setImage(null);
        errorListBox.getChildren().clear();

        xmlService.setCurrentXmlFile(file);
        xmlFileName.setText(xmlService.getCurrentXmlFile().getName());

        progressIndicator.setProgress(0.2);
        if (autodetect.isSelected()) {
            var schemaName = xmlService.getSchemaNameFromCurrentXMLFile();
            if (schemaName.isPresent() && xmlService.loadSchemaFromXMLFile()) {
                logger.debug("Loading remote schema successfully!");
                xsdFileName.setText(schemaName.get());
            } else {
                logger.debug("Could not load remote schema");
                xsdFileName.setText("");
                xmlService.setCurrentXsdFile(null);
            }
        } else {
            if (xmlService.getCurrentXsdFile() != null) {
                xsdFileName.setText(xmlService.getCurrentXsdFile().getName());
            }
        }

        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXsdFile() != null) {
            progressIndicator.setProgress(0.4);
            var exceptionList = xmlService.validate();
            if (exceptionList != null && exceptionList.size() > 0) {
                logger.warn(Arrays.toString(exceptionList.toArray()));
                int i = 0;
                for (SAXParseException saxParseException : exceptionList) {
                    TextFlow textFlowPane = new TextFlow();
                    textFlowPane.setLineSpacing(5.0);

                    Text headerText = new Text("#" + i++ + ": " + saxParseException.getLocalizedMessage() + System.lineSeparator());
                    headerText.setFont(Font.font("Verdana", 20));

                    textFlowPane.getChildren().add(headerText);

                    Text lineText = new Text("Line#: " + saxParseException.getLineNumber() + " Col#: " + saxParseException.getColumnNumber() + System.lineSeparator());
                    textFlowPane.getChildren().add(lineText);

                    try {
                        var lineBefore = Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(saxParseException.getLineNumber() - 1).trim();
                        textFlowPane.getChildren().add(new Text(lineBefore + System.lineSeparator()));

                        var line = Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(saxParseException.getLineNumber()).trim();
                        textFlowPane.getChildren().add(new Text(line + System.lineSeparator()));

                        var lineAfter = Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(saxParseException.getLineNumber() + 1).trim();
                        textFlowPane.getChildren().add(new Text(lineAfter + System.lineSeparator()));
                    } catch (IOException exception) {
                        logger.error("Exception: {}", exception.getMessage());
                    }
                    errorListBox.getChildren().add(textFlowPane);

                    Button goToError = new Button("Go to error");
                    goToError.setOnAction(ae -> {
                        try {
                            var p = (TabPane) anchorPane.getParent().getParent();
                            var t = p.getTabs();
                            t.forEach(e -> {
                                if (Objects.equals(e.getId(), "tabPaneXml")) {
                                    p.getSelectionModel().select(e);
                                }
                            });

                        } catch (Exception e) {
                            logger.error(e.getMessage());
                        }
                    });
                    errorListBox.getChildren().add(goToError);
                    errorListBox.getChildren().add(new Separator());
                }

                Image image = new Image(Objects.requireNonNull(getClass().getResource("/img/icons8-stornieren-48.png")).toString());
                statusImage.setImage(image);

            } else {
                logger.debug("No errors in Validation of file {} - schema {}", xmlService.getCurrentXsdFile(), xmlService.getCurrentXsdFile());
                Image image = new Image(Objects.requireNonNull(getClass().getResource("/img/icons8-ok-48.png")).toString());
                statusImage.setImage(image);
            }
        } else {
            logger.debug("war nicht alles ausgewählt!!");
            logger.debug("Current XML File: {}", xmlService.getCurrentXmlFile());
            logger.debug("Current XSD File: {}", xmlService.getCurrentXsdFile());

            errorListBox.getChildren().add(new Label("Schema not found!"));

            Image image = new Image(Objects.requireNonNull(getClass().getResource("/img/icons8-stornieren-48.png")).toString());
            statusImage.setImage(image);
        }

        progressIndicator.setProgress(1.0);
    }

    @FXML
    private void test() {
        processXmlFile(Paths.get("examples/xml/FundsXML_422_Bond_Fund.xml").toFile());
    }
}
