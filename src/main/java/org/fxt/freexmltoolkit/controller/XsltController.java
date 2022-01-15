package org.fxt.freexmltoolkit.controller;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import net.sf.saxon.TransformerFactoryImpl;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FileTreeCell;
import org.fxt.freexmltoolkit.SimpleFileTreeItem;
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
import java.util.Locale;

public class XsltController {

    @Inject
    XmlService xmlService;

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
    TreeView<File> treeViewXml, treeViewXslt;

    @FXML
    ProgressBar progressBar;

    @FXML
    AnchorPane anchorPane;

    @FXML
    WebView webView;

    @FXML
    TextField xmlTextField;

    File currentFile;

    @FXML
    private void initialize() {
        if (currentFile != null && currentFile.exists()) {
            try {
                xmlService.setCurrentXml(Files.readString(currentFile.toPath()));
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage());
                e.printStackTrace();
            }
        }

        String userHome = System.getProperty("user.home");
        if (SystemUtils.OS_NAME.toUpperCase(Locale.ROOT).startsWith("WINDOWS")) {
            if (new File("C:\\Data\\src\\FreeXmlToolkit\\output").exists()) {
                userHome = "C:\\Data\\src\\FreeXmlToolkit\\output";
            }
        }
        else {
            userHome = "/Users/karlkauc/IdeaProjects/XMLTEST";
        }

        // userHome = ".";
        treeViewXml.setRoot(new SimpleFileTreeItem(new File(userHome), XML_PATTERN));
        treeViewXml.setOnMouseClicked(ae -> {
            if (treeViewXml.getSelectionModel().getSelectedItem() != null) {
                System.out.println("treeViewXml = " + treeViewXml.getSelectionModel().getSelectedItem().toString());
                xmlTextField.textProperty().setValue(treeViewXml.getSelectionModel().getSelectedItem().valueProperty().getValue().getAbsolutePath());
                var file = treeViewXml.getSelectionModel().getSelectedItem().getValue();
                if (file.isFile() && treeViewXslt.getSelectionModel().getSelectedItem() != null) {
                    currentFile = file;
                    progressBar.setProgress(0.1);
                    renderFile(file.getAbsolutePath(), treeViewXslt.getSelectionModel().getSelectedItem().getValue().getAbsolutePath());
                }
                else {
                    logger.debug("WAR NULL");
                    logger.debug("FILE: " + file.isFile());
                }
                if (file.isFile()) {
                    try {
                        var fileContent = Files.readString(file.toPath());
                        parentController.getXmlController().setNewText(fileContent);

                        logger.debug("File content size: " + fileContent.length());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        treeViewXml.setCellFactory(param -> new FileTreeCell());

        treeViewXslt.setRoot(new SimpleFileTreeItem(new File(userHome), XSLT_PATTERN));
        treeViewXslt.setOnMouseClicked(ae -> {
            if (treeViewXslt.getSelectionModel().getSelectedItem() != null) {
                System.out.println("treeViewXml = " + treeViewXslt.getSelectionModel().getSelectedItem().toString());
                var file = treeViewXslt.getSelectionModel().getSelectedItem().getValue();
                if (file.isFile() && treeViewXml.getSelectionModel().getSelectedItem() != null) {
                    progressBar.setProgress(0.1);
                    renderFile(treeViewXml.getSelectionModel().getSelectedItem().getValue().getAbsolutePath(), file.getAbsolutePath());
                }
            }
        });
        treeViewXslt.setCellFactory(param -> new FileTreeCell());
        progressBar.setProgress(0);
    }


    private void renderFile(String xmlFileName, String xsdFileName) {
        final String outputFile = "output/output.html";

        String output;
        try {
            progressBar.setProgress(0.1);
            output = saxonTransform(xmlFileName, xsdFileName);
            progressBar.setProgress(0.5);

            output = output.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "");
            output = output.replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "");
            output = output.replace("  >", "");

            WebEngine engine = webView.getEngine();
            Files.writeString(Paths.get(outputFile), output);
            progressBar.setProgress(0.6);
            System.out.println("write successful");

            engine.getLoadWorker().stateProperty().addListener(
                    (ov, oldState, newState) -> {
                        if (newState == State.SUCCEEDED) {
                            logger.debug("FERTIG: " + engine.getLocation());
                            progressBar.setProgress(1);
                        }
                    });

            engine.load(new File(outputFile).toURI().toURL().toString());
            logger.debug("Loaded Content");

            xmlService.setCurrentXml(Files.readString(currentFile.toPath()));
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
