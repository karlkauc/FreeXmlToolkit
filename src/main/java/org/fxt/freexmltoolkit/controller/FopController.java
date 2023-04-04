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
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.FOPService;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class FopController {

    XmlService xmlService = XmlServiceImpl.getInstance();
    FOPService fopService = new FOPService();

    @FXML
    GridPane settings;

    @FXML
    Button startConversion, test;

    @FXML
    AnchorPane xml, xslt, pdf;

    @FXML
    StackPane stackPaneXml, stackPaneXslt;

    File xmlFile, xslFile, pdfFile;

    @FXML
    TextField xmlFileName, xslFileName, pdfFileName;

    @FXML
    ProgressIndicator progressIndicator;

    String lastOpenDir = ".";

    FileChooser fileChooser = new FileChooser();

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    private final static Logger logger = LogManager.getLogger(FopController.class);

    @FXML
    private void initialize() {
        logger.debug("BIN IM FOP CONTROLLER");
        progressIndicator.setVisible(false);

        showTestData();
    }

    private void showTestData() {
        if (System.getenv("debug") != null) {
            logger.debug("set visible false");
            test.setVisible(true);

            test();
        }
    }

    @FXML
    private void openXmlFile() {
        logger.debug("Last open Dir: {}", lastOpenDir);
        fileChooser.setInitialDirectory(new File(lastOpenDir));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());
            this.lastOpenDir = selectedFile.getParent();
            xmlFile = selectedFile;
            xmlFileName.setText(xmlFile.getName());
        } else {
            logger.debug("No file selected");
        }
    }

    @FXML
    private void openXslFile() {
        logger.debug("Last open Dir: {}", lastOpenDir);
        fileChooser.setInitialDirectory(new File(lastOpenDir));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSL files (*.xsl)", "*.xsl"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());
            this.lastOpenDir = selectedFile.getParent();
            xslFile = selectedFile;
            xslFileName.setText(xslFile.getName());
        } else {
            logger.debug("No file selected");
        }
    }

    @FXML
    private void openPdfFile() {
        logger.debug("Last open Dir: {}", lastOpenDir);
        fileChooser.setInitialDirectory(new File(lastOpenDir));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());
            this.lastOpenDir = selectedFile.getParent();
            pdfFile = selectedFile;
            pdfFileName.setText(pdfFile.getName());
        } else {
            logger.debug("No file selected");
        }
    }

    @FXML
    private void buttonConversion() throws IOException {
        logger.debug("Start Conversion!");

        progressIndicator.setVisible(true);
        progressIndicator.setProgress(0);

        HashMap<String, String> parameter = new HashMap<>();
        parameter.put("versionParam", "3");

        fopService.createPdfFile(xmlFile, xslFile, pdfFile, parameter);

        if (pdfFile != null && pdfFile.exists()) {
            logger.debug("Written {} bytes", pdfFile.length());

            progressIndicator.setProgress(0.9);
            Desktop.getDesktop().open(pdfFile);
            progressIndicator.setProgress(1.0);
        } else {
            logger.warn("PDF File do not exits");
        }
    }

    @FXML
    private void test() {
        xmlFile = new File("src/test/resources/projectteam.xml");
        xslFile = new File("src/test/resources/projectteam2fo.xsl");
        pdfFile = new File("output/ResultXML2PDF.pdf");

        xmlFileName.setText(xmlFile.getName());
        xslFileName.setText(xslFile.getName());
        pdfFileName.setText(pdfFile.getName());
    }

}

