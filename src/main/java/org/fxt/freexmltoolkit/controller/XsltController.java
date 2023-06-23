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
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.FileLoader;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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
    TabPane outputMethodSwitch;

    @FXML
    Tab tabWeb, tabText;

    File xmlFile, xsltFile;

    CodeArea codeArea = new CodeArea();
    VirtualizedScrollPane<CodeArea> virtualizedScrollPane;

    @FXML
    Button debugButton;

    @FXML
    private void initialize() {
        var test = System.getenv("debug");
        if (test != null) {
            logger.debug("set visible false");
            debugButton.setVisible(true);
        }

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
        /*
        if (xmlFile != null && xmlFile.exists()) {
            try {
                xmlService.setCurrentXml(Files.readString(xmlFile.toPath()));
                xmlService.setCurrentXmlFile(xmlFile);
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage());
                e.printStackTrace();
            }
        }
*/
        if (xsltFile != null && xsltFile.exists()) {
            xmlService.setCurrentXsltFile(xsltFile);
        }

        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()
                && xmlService.getCurrentXsltFile() != null && xmlService.getCurrentXsltFile().exists()) {
            logger.debug("RENDER FILE");

            try {
                String output = xmlService.performXsltTransformation();
                output = cleanHtmlContent(output);

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
            } catch (Exception exception) {
                logger.error("Exception: {}", exception.getMessage());
            }
        }
    }

    private void renderXML(String output) {
        renderText(output);
        Platform.runLater(() -> codeArea.setStyleSpans(0, XmlEditor.computeHighlighting(output)));
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
            Files.writeString(Paths.get(outputFileName), output);

            WebEngine engine = webView.getEngine();
            progressBar.setProgress(0.6);

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
                //xmlService.setCurrentXml(Files.readString(xmlFile.toPath()));
            }
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    private static String cleanHtmlContent(String output) {
        output = output.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "")
                .replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "")
                .replace("  >", "");

        Document doc = Jsoup.parse(output, Parser.xmlParser());
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
        //Document.OutputSettings outputSettings = new Document.OutputSettings();
        //outputSettings.prettyPrint(false);
        //outputSettings.escapeMode(Entities.EscapeMode.xhtml);
        //doc.outputSettings(outputSettings);

        var div = doc.select(".language-xml");
        logger.debug("Lang XML Elements: {}", div.size());
        for (int i = 0; i < div.size(); i++) {
            var oneDiv = div.get(i);

            String content = StringEscapeUtils.escapeHtml4(oneDiv.data());
            oneDiv.html(content);

            logger.debug("PARSED DOCUMENT");
            logger.debug("NEW: {}", content);
            logger.debug("HTML CONTENT: {}", div.get(i).data());
        }
        doc.outputSettings(new Document.OutputSettings().prettyPrint(true));
        output = doc.html();
        return output;
    }

    @FXML
    private void test() {
        xmlFile = Paths.get("examples/xml/ALPDINAMIK_fundsXml_20230228_20230404-062438.xml").toFile();
        xsltFile = Paths.get("examples/xslt/Check_FundsXML_File.xslt").toFile();

        xmlFileLoader.setFile(xmlFile);
        xsltFileLoader.setFile(xsltFile);

        if (this.xmlService != null) {
            this.xmlService.setCurrentXmlFile(xmlFile);
            this.xmlService.setCurrentXsltFile(xsltFile);

            checkFiles();
        }
    }
}
