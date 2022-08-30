package org.fxt.freexmltoolkit.controller;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.FileLoader;
import org.fxt.freexmltoolkit.service.ModuleBindings;
import org.fxt.freexmltoolkit.service.XmlService;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;

public class XsltController {

    @Inject
    XmlService xmlService;

    @FXML
    FileLoader xmlFileLoader, xsltFileLoader;

    @FXML
    Button reload;

    final Injector injector = Guice.createInjector(new ModuleBindings());
    BuilderFactory builderFactory = new JavaFXBuilderFactory();
    Callback<Class<?>, Object> guiceControllerFactory = injector::getInstance;

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @FXML
    ProgressBar progressBar;

    @FXML
    AnchorPane anchorPane;

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
            logger.debug("Loaded XML File: {}", xmlFile.getAbsolutePath());
            xmlService.setCurrentXmlFile(xmlFile);
            parentController.getXmlController().reloadXmlText();
            checkFiles();
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
                switch (outputMethod) {
                    case "html", "xhtml" -> {
                        logger.debug("BIN IM HTML");
                        outputMethodSwitch.getSelectionModel().select(tabWeb);
                    }
                    case "xml" -> outputMethodSwitch.getSelectionModel().select(tabText);
                    case "text" -> outputMethodSwitch.getSelectionModel().select(tabText);
                    default -> outputMethodSwitch.getSelectionModel().select(tabText);
                }
            }
            catch (IOException | TransformerException exception) {
                logger.error("Exception: {}", exception.getMessage());
            }
        }
    }

    private void renderXML(String output) {
        renderText(output);

        // if (output.length() < 1024 * 1024) {
            Platform.runLater(() -> codeArea.setStyleSpans(0, XmlController.computeHighlighting(output)));
        // }
    }

    private void renderText(String output) {
        codeArea.clear();
        codeArea.replaceText(0, 0, output);
    }


    private void renderHTML(String output) {
        final String outputFileName = "output/output.html";

        try {
            output = output.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "")
                    .replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "")
                    .replace("  >", "");

            WebEngine engine = webView.getEngine();
            Files.writeString(Paths.get(outputFileName), output);
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

}
