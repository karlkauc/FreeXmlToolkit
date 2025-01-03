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

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

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
    }

    @FXML
    private void openXmlFile() {
        openFile("XML files (*.xml)", "*.xml", file -> {
            xmlFile = file;
            xmlFileName.setText(file.getName());
        });
    }

    @FXML
    private void openXslFile() {
        openFile("XSL files (*.xsl)", "*.xsl", file -> {
            xslFile = file;
            xslFileName.setText(file.getName());
        });
    }

    @FXML
    private void openPdfFile() {
        openFile("PDF files (*.pdf)", "*.pdf", file -> {
            pdfFile = file;
            pdfFileName.setText(file.getName());
        });
    }

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
            logger.debug("Written {} bytes", pdfFile.length());
            progressIndicator.setProgress(1.0);
            if (openPdfAfterCreation.isSelected() && pdfFile.length() > 0) {
                Desktop.getDesktop().open(pdfFile);
            }
        } else {
            logger.warn("PDF File does not exist");
        }
    }
}