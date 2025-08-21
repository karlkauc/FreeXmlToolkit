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
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.FileExplorer;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

public class XsltController {

    private static final Logger logger = LogManager.getLogger(XsltController.class);
    private static final int PANE_SIZE = 500;
    private final XmlService xmlService = XmlServiceImpl.getInstance();
    private MainController parentController;
    private File xmlFile, xsltFile;
    private WebEngine webEngine;
    private final CodeArea codeArea = new CodeArea();
    private VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    private FileExplorer xmlFileExplorer, xsltFileExplorer;
    @FXML
    private Button openInDefaultWebBrowser, openInDefaultTextEditor;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private WebView webView;
    @FXML
    private StackPane textView;
    @FXML
    private TabPane outputMethodSwitch;
    @FXML
    private Tab tabWeb, tabText;
    @FXML
    private BorderPane fileLoaderPane;
    @FXML
    private Label toggleBorderPaneLabel;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void initialize() {
        xmlFileExplorer.setAllowedFileExtensions(List.of("xml"));
        xsltFileExplorer.setAllowedFileExtensions(List.of("xslt", "xsl"));

        if (System.getenv("debug") != null) {
            logger.debug("Debug mode enabled for XsltController");
            // Debug mode - test functionality can be added here if needed
        }

        fileLoaderPane.heightProperty().addListener((observable, oldValue, newValue) -> {
            double fileLoaderHeight = (newValue.doubleValue() - 100) / 2;
            xsltFileExplorer.setPrefHeight(fileLoaderHeight);
            xmlFileExplorer.setPrefHeight(fileLoaderHeight);
        });

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
        textView.getChildren().add(virtualizedScrollPane);

        progressBar.setDisable(true);
        progressBar.setVisible(false);

        webEngine = webView.getEngine();
        webEngine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                logger.debug("Loading Web Content successfully: {}", webEngine.getLocation());
            }
        });
    }

    @FXML
    private void toggleBorderPane() {
        boolean isVisible = fileLoaderPane.isVisible();
        fileLoaderPane.setVisible(!isVisible);
        toggleBorderPaneLabel.setText(isVisible ? ">>" : "<<");
        fileLoaderPane.setMaxWidth(isVisible ? 0 : PANE_SIZE);
        fileLoaderPane.setMinWidth(isVisible ? 0 : PANE_SIZE);
        fileLoaderPane.setPrefWidth(isVisible ? 0 : PANE_SIZE);
        fileLoaderPane.setManaged(!isVisible);
    }

    @FXML
    private void checkFiles() {
        if (xsltFileExplorer.getSelectedFile() != null) {
            xsltFile = xsltFileExplorer.getSelectedFile().toFile();
            xmlService.setCurrentXsltFile(xsltFile);
        }

        if (xmlFileExplorer.getSelectedFile() != null) {
            xmlFile = xmlFileExplorer.getSelectedFile().toFile();
            xmlService.setCurrentXmlFile(xmlFile);
        }

        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()
                && xmlService.getCurrentXsltFile() != null && xmlService.getCurrentXsltFile().exists()) {
            try {
                String output = xmlService.performXsltTransformation();
                progressBar.setProgress(0.1);
                renderHTML(output);
                progressBar.setProgress(0.6);
                renderXML(output);
                progressBar.setProgress(0.8);
                renderText(output);
                progressBar.setProgress(1);

                String outputMethod = xmlService.getXsltOutputMethod().toLowerCase().trim();
                switch (outputMethod) {
                    case "html", "xhtml" -> outputMethodSwitch.getSelectionModel().select(tabWeb);
                    case "xml", "text" -> outputMethodSwitch.getSelectionModel().select(tabText);
                    default -> outputMethodSwitch.getSelectionModel().select(tabText);
                }
            } catch (Exception exception) {
                // Log the error for developer analysis (with full stacktrace)
                logger.error("XSLT Transformation failed: {}", exception.getMessage(), exception);

                // NEW: Show an alert dialog for the user
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Transformation Error");
                alert.setHeaderText("An error occurred during the XSLT transformation.");
                // Show a comprehensible error message
                alert.setContentText(exception.getMessage());

                // Optional: Add the complete stacktrace in an expandable area
                // This is very useful for technically savvy users.
                TextArea textArea = new TextArea(exception.toString());
                textArea.setEditable(false);
                textArea.setWrapText(true);
                alert.getDialogPane().setExpandableContent(textArea);

                alert.showAndWait();
            }
            progressBar.setVisible(false);
        }
    }

    private void renderXML(String output) {
        renderText(output);
        Platform.runLater(() -> codeArea.setStyleSpans(0, XmlCodeEditor.computeHighlighting(output)));
    }

    private void renderText(String output) {
        codeArea.clear();
        codeArea.replaceText(0, 0, output);
    }

    private void renderHTML(String output) {
        File outputDir = new File("output");
        String outputFileName = outputDir.getAbsolutePath() + File.separator + "output.html";

        try {
            Files.createDirectories(outputDir.toPath());

            Files.copy(
                    Objects.requireNonNull(getClass().getResourceAsStream("/scss/prism.css")),
                    Paths.get(outputDir.getAbsolutePath(), "prism.css"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(
                    Objects.requireNonNull(getClass().getResourceAsStream("/xsdDocumentation/assets/freexmltoolkit-docs.css")),
                    Paths.get(outputDir.getAbsolutePath(), "freeXmlToolkit.css"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(
                    Objects.requireNonNull(getClass().getResourceAsStream("/css/fonts/Roboto-Regular.ttf")),
                    Paths.get(outputDir.getAbsolutePath(), "Roboto-Regular.ttf"),
                    StandardCopyOption.REPLACE_EXISTING);

            File newFile = Paths.get(outputFileName).toFile();
            Files.writeString(newFile.toPath(), output);
            logger.debug("Rendering HTML file: {}", newFile.getAbsolutePath());

            openInDefaultWebBrowser.setOnAction(event -> {
                try {
                    Desktop.getDesktop().open(newFile);
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            });
            openInDefaultWebBrowser.setDisable(false);

            webEngine.load(newFile.toURI().toString());
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

}