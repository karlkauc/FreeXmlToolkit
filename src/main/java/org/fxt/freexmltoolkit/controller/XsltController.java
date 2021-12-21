package org.fxt.freexmltoolkit.controller;

import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import net.sf.saxon.TransformerFactoryImpl;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FileTreeCell;
import org.fxt.freexmltoolkit.SimpleFileTreeItem;

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

    public XsltController() {

    }

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    private final static String XML_PATTERN = ".*\\.xml$";
    private final static String XSLT_PATTERN = ".*\\.xslt";

    @FXML
    TreeView<File> treeViewXml, treeViewXslt;

    @FXML
    ProgressBar progressBar;

    DirectoryChooser directoryChooserXSLT = new DirectoryChooser();

    @FXML
    AnchorPane anchorPane;

    @FXML
    TextField xsltFileDir;

    @FXML
    WebView webView;

    @FXML
    private void initialize() {
        if (SystemUtils.OS_NAME.toUpperCase(Locale.ROOT).startsWith("WINDOWS")) {
            if (new File("C:\\Data\\src\\FreeXmlToolkit\\output").exists()) {
                directoryChooserXSLT.setInitialDirectory(new File("C:\\Data\\src\\FreeXmlToolkit\\output"));
                xsltFileDir.textProperty().setValue(directoryChooserXSLT.getInitialDirectory().getAbsolutePath());
            }
        }

        String userHome = System.getProperty("user.home");
        userHome = ".";
        treeViewXml.setRoot(new SimpleFileTreeItem(new File(userHome), XML_PATTERN));
        treeViewXml.setOnMouseClicked(ae -> {
            if (treeViewXml.getSelectionModel().getSelectedItem() != null) {
                System.out.println("treeViewXml = " + treeViewXml.getSelectionModel().getSelectedItem().toString());
                var file = treeViewXml.getSelectionModel().getSelectedItem().getValue();
                if (file.isFile() && treeViewXslt.getSelectionModel().getSelectedItem() != null) {
                    progressBar.setProgress(0.1);
                    renderFile(file.getAbsolutePath(), treeViewXslt.getSelectionModel().getSelectedItem().getValue().getAbsolutePath());
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
            // status.setText("Starting...");
            progressBar.setProgress(0.1);
            output = saxonTransform(xmlFileName, xsdFileName);
            progressBar.setProgress(0.5);

            output = output.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "");
            output = output.replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "");
            output = output.replace("  >", "");

            // status.setText("finished transforming");

            WebEngine engine = webView.getEngine();
            Files.writeString(Paths.get(outputFile), output);
            progressBar.setProgress(0.6);
            System.out.println("write successful");
            // status.setText("write successful");

            engine.getLoadWorker().stateProperty().addListener(
                    (ov, oldState, newState) -> {
                        if (newState == State.SUCCEEDED) {
                            // System.out.println("FERTIG: " + engine.getLocation());
                            logger.debug("FERTIG: " + engine.getLocation());
                            // status.setText("rendering finished");
                            progressBar.setProgress(1);
                        }
                    });

            engine.load(new File(outputFile).toURI().toURL().toString());

            System.out.println("Loaded Content");

        } catch (TransformerException | IOException e) {
            e.printStackTrace();
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
