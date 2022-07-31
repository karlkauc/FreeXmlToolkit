package org.fxt.freexmltoolkit.controller;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import net.sf.saxon.TransformerFactoryImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.FileLoader;
import org.fxt.freexmltoolkit.service.ModuleBindings;
import org.fxt.freexmltoolkit.service.XmlService;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;

public class XsltController {

    @Inject
    XmlService xmlService;

    @FXML
    FileLoader xmlFileLoader, xsltFileLoader;

    final Injector injector = Guice.createInjector(new ModuleBindings());
    BuilderFactory builderFactory = new JavaFXBuilderFactory();
    Callback<Class<?>, Object> guiceControllerFactory = injector::getInstance;

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    private final static String XML_PATTERN = ".*\\.xml$";
    private final static String XSLT_PATTERN = ".*\\.xslt";


    @FXML
    ProgressBar progressBar;

    @FXML
    AnchorPane anchorPane;

    @FXML
    WebView webView;

    @FXML
    TextField xmlTextField;

    File xmlFile, xsltFile;

    @FXML
    private void initialize() {
        xmlFileLoader.setLoadPattern("*.xml");
        xmlFileLoader.getLoadButton().setOnAction(ae -> {
            xmlFile = xmlFileLoader.getFileAction();
            logger.debug("Loaded XML File: {}", xmlFile.getAbsolutePath());
            xmlService.setCurrentXmlFile(xmlFile);
            checkFiles();
        });

        xsltFileLoader.setLoadPattern("*.xslt");
        xsltFileLoader.getLoadButton().setOnAction(ae -> {
            xsltFile = xsltFileLoader.getFileAction();
            logger.debug("Loaded XSLT File: {}", xsltFile.getAbsolutePath());
            xmlService.setCurrentXsltFile(xsltFile);
            checkFiles();
        });


    }

    private void checkFiles() {
        if (xmlFile != null && xmlFile.exists()) {
            try {
                xmlService.setCurrentXml(Files.readString(xmlFile.toPath()));
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage());
                e.printStackTrace();
            }
        }

        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()
                && xmlService.getCurrentXsltFile() != null && xmlService.getCurrentXsltFile().exists()) {
            logger.debug("RENDER FILE");
            renderFile(xmlService.getCurrentXmlFile().getAbsolutePath(), xmlService.getCurrentXsltFile().getAbsolutePath());
        }
    }


    private void renderFile(String xmlFileName, String xsdFileName) {
        final String outputFile = "output/output.html";

        String output;
        try {
            // progressBar.setProgress(0.1);
            output = saxonTransform(xmlFileName, xsdFileName);
            // progressBar.setProgress(0.5);

            output = output.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "");
            output = output.replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "");
            output = output.replace("  >", "");

            WebEngine engine = webView.getEngine();
            Files.writeString(Paths.get(outputFile), output);
            // progressBar.setProgress(0.6);
            System.out.println("write successful");

            engine.getLoadWorker().stateProperty().addListener(
                    (ov, oldState, newState) -> {
                        if (newState == State.SUCCEEDED) {
                            logger.debug("FERTIG: " + engine.getLocation());
                            // progressBar.setProgress(1);
                        }
                    });

            engine.load(new File(outputFile).toURI().toURL().toString());
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

    public static String saxonTransform(String source, String xslt) throws TransformerException, FileNotFoundException {
        TransformerFactoryImpl f = new TransformerFactoryImpl();
        f.setAttribute("http://saxon.sf.net/feature/version-warning", Boolean.FALSE);

        StreamSource xsrc = new StreamSource(new FileInputStream(xslt));
        Transformer t = f.newTransformer(xsrc);
        StreamSource src = new StreamSource(new FileInputStream(source));
        StreamResult res = new StreamResult(new ByteArrayOutputStream());
        t.transform(src, res);
        return res.getOutputStream().toString();
    }
}
