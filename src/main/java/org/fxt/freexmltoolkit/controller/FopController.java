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

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.PDFSettings;
import org.fxt.freexmltoolkit.service.FOPService;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller class for handling FOP (Formatting Objects Processor) related actions.
 */
public class FopController {
    private static final Logger logger = LogManager.getLogger(FopController.class);
    private final FOPService fopService = new FOPService();
    private final FileChooser fileChooser = new FileChooser();
    private String lastOpenDir = ".";
    private File xmlFile, xslFile, pdfFile;
    private MainController parentController;

    @FXML
    private GridPane settings;
    @FXML
    private Button startConversion;
    @FXML
    private TextField xmlFileName, xslFileName, pdfFileName, producer, author, creationDate, title, keywords, subject;
    @FXML
    private CheckBox openPdfAfterCreation;
    @FXML
    private ProgressIndicator progressIndicator;

    /**
     * Sets the parent controller.
     *
     * @param parentController the parent controller
     */
    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    /**
     * Initializes the controller. Sets default values and configurations.
     */
    @FXML
    private void initialize() {
        progressIndicator.setVisible(false);
        if ("true".equals(System.getenv("debug"))) {
            xmlFile = new File("src/test/resources/projectteam.xml");
            xslFile = new File("src/test/resources/projectteam2fo.xsl");
            pdfFile = new File("output/ResultXML2PDF.pdf");
            xmlFileName.setText(xmlFile.getName());
            xslFileName.setText(xslFile.getName());
            pdfFileName.setText(pdfFile.getName());
        }
        creationDate.setText(new Date().toString());
        author.setText(System.getProperty("user.name"));

        xmlFileName.setOnDragOver(this::handleDragOver);
        xmlFileName.setOnDragDropped(event -> handleDragDropped(event, file -> {
            xmlFile = file;
            xmlFileName.setText(file.getName());
        }));

        xslFileName.setOnDragOver(this::handleDragOver);
        xslFileName.setOnDragDropped(event -> handleDragDropped(event, file -> {
            xslFile = file;
            xslFileName.setText(file.getName());
        }));
    }

    /**
     * Handles the drag over event for file loading.
     *
     * @param event the drag event
     */
    private void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY);
        else event.consume();
    }

    /**
     * Handles the drag dropped event for file loading.
     *
     * @param event        the drag event
     * @param fileConsumer the consumer to handle the dropped file
     */
    private void handleDragDropped(DragEvent event, java.util.function.Consumer<File> fileConsumer) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            logger.debug("Dropped Files: {}", db.getFiles());
            fileConsumer.accept(db.getFiles().getFirst());
            event.setDropCompleted(true);
        } else event.setDropCompleted(false);
        event.consume();
    }


    /**
     * Opens a file chooser dialog to select an XML file.
     */
    @FXML
    private void openXmlFile() {
        openFile("XML files (*.xml)", "*.xml", file -> {
            xmlFile = file;
            xmlFileName.setText(file.getName());
        });
    }

    /**
     * Opens a file chooser dialog to select an XSL file.
     */
    @FXML
    private void openXslFile() {
        openFile("XSL files (*.xsl)", "*.xsl", file -> {
            xslFile = file;
            xslFileName.setText(file.getName());
        });
    }

    /**
     * Opens a file chooser dialog to select a PDF file.
     */
    @FXML
    private void openPdfFile() {
        pdfFile = saveFile("PDF files (*.pdf)", "*.pdf");
        if (pdfFile != null) {
            pdfFileName.setText(pdfFile.getName());
        }
    }

    /**
     * Opens a file chooser dialog with the specified description and extension filter.
     *
     * @param description  the description of the file type
     * @param extension    the file extension filter
     * @param fileConsumer the consumer to handle the selected file
     */
    private void openFile(String description, String extension, java.util.function.Consumer<File> fileConsumer) {
        fileChooser.setInitialDirectory(new File(lastOpenDir));
        fileChooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(description, extension));
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null && selectedFile.exists()) {
            lastOpenDir = selectedFile.getParent();
            fileConsumer.accept(selectedFile);
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());
        } else {
            logger.debug("No file selected");
        }
    }

    /**
     * Opens a file chooser dialog to save a file with the specified description and extension filter.
     *
     * @param description the description of the file type
     * @param extension   the file extension filter
     * @return the selected file
     */
    private File saveFile(String description, String extension) {
        fileChooser.setInitialDirectory(new File(lastOpenDir));
        fileChooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(description, extension));
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            lastOpenDir = selectedFile.getParent();
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());
            return selectedFile;
        } else {
            logger.debug("No file selected");
        }
        return null;
    }

    /**
     * Starts the conversion process from XML and XSL to PDF.
     *
     * @throws IOException if an I/O error occurs
     */
    @FXML
    private void buttonConversion() throws IOException {
        logger.debug("Start Conversion!");
        progressIndicator.setVisible(true);
        progressIndicator.setProgress(0);

        PDFSettings pdfSettings = new PDFSettings(
                new HashMap<>(Map.of("versionParam", "3")),
                producer.getText(), author.getText(), "created with FreeXMLToolkit",
                creationDate.getText(), title.getText(), keywords.getText()
        );

        fopService.createPdfFile(xmlFile, xslFile, pdfFile, pdfSettings);

        if (pdfFile != null && pdfFile.exists()) {
            logger.debug("Written {} bytes in File {}", pdfFile.length(), pdfFile.getAbsoluteFile());
            progressIndicator.setProgress(1.0);
            if (openPdfAfterCreation.isSelected() && pdfFile.length() > 0) {
                Desktop.getDesktop().open(pdfFile);
            }
        } else {
            logger.warn("PDF File does not exist");
        }
    }
}
