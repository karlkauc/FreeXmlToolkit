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
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.FileLoader;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class XsltController {

    XmlService xmlService = XmlServiceImpl.getInstance();

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    @FXML
    FileLoader xmlFileLoader, xsltFileLoader;

    @FXML
    Button reload;

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    private final static Logger logger = LogManager.getLogger(XsltController.class);

    @FXML
    ProgressBar progressBar;

    @FXML
    WebView webView;

    @FXML
    StackPane textView;

    @FXML
    TableView csvView;

    @FXML
    TabPane outputMethodSwitch;

    @FXML
    Tab tabWeb, tabText, tabCsv;

    File xmlFile, xsltFile;

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    private void initialize() {
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        virtualizedScrollPane = new VirtualizedScrollPane<>(codeArea);
        textView.getChildren().add(virtualizedScrollPane);

        progressBar.setDisable(true);
        progressBar.setVisible(false);

        xmlFileLoader.setLoadPattern("*.xml", "XML File");
        xmlFileLoader.getLoadButton().setText("XML File");
        xmlFileLoader.getLoadButton().setOnAction(ae -> {
            xmlFile = xmlFileLoader.getFileAction();
            if (xmlFile != null) {
                logger.debug("Loaded XML File: {}", xmlFile.getAbsolutePath());
                xmlService.setCurrentXmlFile(xmlFile);
                checkFiles();
            }
        });

        xsltFileLoader.setLoadPattern("*.xslt", "XSLT File");
        xsltFileLoader.getLoadButton().setText("XSLT File");
        xsltFileLoader.getLoadButton().setOnAction(ae -> {
            xsltFile = xsltFileLoader.getFileAction();
            logger.debug("Loaded XSLT File: {}", xsltFile.getAbsolutePath());
            xmlService.setCurrentXsltFile(xsltFile);
            checkFiles();
        });
    }

    @FXML
    private void checkFiles() {
        if (xmlFile != null && xmlFile.exists()) {
            try {
                xmlService.setCurrentXml(Files.readString(xmlFile.toPath()));
                xmlService.setCurrentXmlFile(xmlFile);
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage());
                e.printStackTrace();
            }
        }

        if (xsltFile != null && xsltFile.exists()) {
            xmlService.setCurrentXsltFile(xsltFile);
        }

        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()
                && xmlService.getCurrentXsltFile() != null && xmlService.getCurrentXsltFile().exists()) {
            logger.debug("RENDER FILE");

            try {
                String output = xmlService.saxonTransform();

                progressBar.setVisible(true);
                progressBar.setProgress(0.1);
                renderHTML(output);
                progressBar.setProgress(0.6);
                renderXML(output);
                progressBar.setProgress(0.8);
                renderText(output);
                progressBar.setProgress(1);

                var outputMethod = xmlService.getXsltOutputMethod();
                logger.debug("Output Method: {}", outputMethod);
                switch (outputMethod.toLowerCase().trim()) {
                    case "html", "xhtml" -> {
                        logger.debug("BIN IM HTML");
                        outputMethodSwitch.getSelectionModel().select(tabWeb);
                    }
                    case "xml" -> outputMethodSwitch.getSelectionModel().select(tabText);
                    case "text" -> outputMethodSwitch.getSelectionModel().select(tabText);
                    default -> outputMethodSwitch.getSelectionModel().select(tabText);
                }
            } catch (TransformerException | SaxonApiException | FileNotFoundException exception) {
                logger.error("Exception: {}", exception.getMessage());
            }
        }
    }

    private void renderXML(String output) {
        renderText(output);
        Platform.runLater(() -> codeArea.setStyleSpans(0, XmlController.computeHighlighting(output)));
    }

    private void renderText(String output) {
        codeArea.clear();
        codeArea.replaceText(0, 0, output);
    }


    private void renderHTML(String output) {
        new File("output").mkdirs();
        final String outputFileName = "output" + File.separator + "output.html";
        logger.debug("Output File: {}", outputFileName);

        try {
            output = output.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "")
                    .replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "")
                    .replace("  >", "");

            Files.writeString(Paths.get(outputFileName), output);

            if (propertiesService != null) {
                Properties properties = propertiesService.loadProperties();
                var httpHost = properties.getProperty("http.proxy.host");
                var httpPort = properties.getProperty("http.proxy.port");

                if (httpHost != null) {
                    logger.debug("HTTP PROXY: {}:{}", httpHost, httpPort);
                    System.setProperty("java.net.useSystemProxies", "true");
                }
            }

            WebEngine engine = webView.getEngine();
            progressBar.setProgress(0.6);
            System.out.println("write successful");

            engine.getLoadWorker().stateProperty().addListener(
                    (ov, oldState, newState) -> {
                        if (newState == State.SUCCEEDED) {
                            logger.debug("FERTIG: " + engine.getLocation());
                            progressBar.setProgress(1);
                        }
                    });

            engine.load(new File(outputFileName).toURI().toURL().toString());
            logger.debug("Loaded Content");

            if (xmlFile != null && xmlFile.exists()) {
                logger.debug("CURRENT FILE: {}", xmlFile.getAbsolutePath());
                xmlService.setCurrentXml(Files.readString(xmlFile.toPath()));
            }
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    @FXML
    private void test() {
        xmlFile = new File("C:\\Data\\src\\FreeXmlToolkit\\examples\\xml\\L.xml");
        xsltFile = new File("C:\\Data\\src\\FreeXmlToolkit\\examples\\xslt\\Check_FundsXML_File.xslt");

        // xmlFile = new File("C:\\Data\\src\\FreeXmlToolkit\\output\\DE000A1JXLX8_EUR_20211130_v4_1_8.xml");
        // xsltFile = new File("C:\\Data\\src\\FreeXmlToolkit\\examples\\xslt\\Check_FundsXML_File.xslt");

        if (this.xmlService != null) {
            this.xmlService.setCurrentXmlFile(xmlFile);
            this.xmlService.setCurrentXsltFile(xsltFile);

            checkFiles();
        }

    }

}
