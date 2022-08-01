package org.fxt.freexmltoolkit.controller;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.FileLoader;
import org.fxt.freexmltoolkit.service.ModuleBindings;
import org.fxt.freexmltoolkit.service.XmlService;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
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

    File xmlFile, xsltFile;

    @FXML
    private void initialize() {
        progressBar.setDisable(true);
        progressBar.setVisible(false);

        xmlFileLoader.setLoadPattern("*.xml", "XML File");
        xmlFileLoader.setButtonText("XML File");
        xmlFileLoader.getLoadButton().setOnAction(ae -> {
            xmlFile = xmlFileLoader.getFileAction();
            logger.debug("Loaded XML File: {}", xmlFile.getAbsolutePath());
            xmlService.setCurrentXmlFile(xmlFile);
            checkFiles();
        });

        xsltFileLoader.setLoadPattern("*.xslt", "XSLT File");
        xsltFileLoader.setButtonText("XSLT File");
        xsltFileLoader.getLoadButton().setOnAction(ae -> {
            xsltFile = xsltFileLoader.getFileAction();
            logger.debug("Loaded XSLT File: {}", xsltFile.getAbsolutePath());
            xmlService.setCurrentXsltFile(xsltFile);
            checkFiles();
        });
    }

    @FXML
    private void test() {
        xmlFile = Path.of("C:/Data/src/FreeXmlToolkit/output/!FundsXML AMUNDI FLOATING RATE EURO CORP ESG as of 2021-12-30 v2.xml").toFile();
        xsltFile = Path.of("C:/Data/src/FreeXmlToolkit/output/Check_FundsXML_File.xslt").toFile();

        xmlService.setCurrentXmlFile(xmlFile);
        xmlService.setCurrentXsltFile(xsltFile);

        checkFiles();
        parentController.getXmlController().reloadXmlText();
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
            renderFile();
        }
    }


    private void renderFile() {
        final String outputFileName = "output/output.html";

        String output;
        try {
            progressBar.setVisible(true);

            progressBar.setProgress(0.1);
            output = xmlService.saxonTransform();
            progressBar.setProgress(0.5);

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
        } catch (TransformerException | IOException e) {
            e.printStackTrace();
            logger.error(e.getLocalizedMessage());
        }
    }

}
