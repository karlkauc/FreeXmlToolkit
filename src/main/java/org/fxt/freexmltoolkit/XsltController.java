package org.fxt.freexmltoolkit;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import net.sf.saxon.TransformerFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class XsltController {

    public XsltController() {

    }

    private final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @FXML
    ProgressBar progressBar;

    DirectoryChooser directoryChooserXML = new DirectoryChooser();
    DirectoryChooser directoryChooserXSLT = new DirectoryChooser();


    @FXML
    AnchorPane anchorPane;

    @FXML
    TextField xmlFileDir, xsltFileDir;

    @FXML
    Button xmlDirChooserButton, xsltChooserButton;

    @FXML
    ListView<File> xmlFiles;

    @FXML
    ListView<File> xsltFiles;

    @FXML
    WebView webView;

    @FXML
    Text status;



    public final ObservableList<File> xmlFileList =
            FXCollections.observableArrayList();
    public final ObservableList<File> xsdFileList =
            FXCollections.observableArrayList();

    private ObservableList<File> getFileNames(String path, String patternString) throws FileNotFoundException {
        if (path != null) {
            System.out.println("path = " + path);
            System.out.println("Path.of(path) = " + Path.of(path));
            if (!Files.exists(Path.of(path))) {
                throw new FileNotFoundException();
            } else {
                File dir = new File(path);
                File[] files = dir.listFiles((dir1, name) -> name.matches(patternString));
                return FXCollections.observableArrayList(files);
            }
        } else return FXCollections.emptyObservableList();
    }

    @FXML
    private void initialize() {
        directoryChooserXML.setInitialDirectory(new File("C:\\Data\\TEMP\\2021-12-14_FundsXMLTestFiles"));
        directoryChooserXSLT.setInitialDirectory(new File("C:\\Data\\src\\FreeXmlToolkit\\output"));

        xsltFileDir.textProperty().setValue(directoryChooserXSLT.getInitialDirectory().getAbsolutePath());
        xmlFileDir.textProperty().setValue(directoryChooserXML.getInitialDirectory().getAbsolutePath());

        progressBar.setProgress(0);
        xmlFiles.setCellFactory(new Callback<>() {
            @Override
            public ListCell<File> call(ListView<File> param) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(File item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null) {
                            setText(item.getName() + " (" + String.format("%.2f", item.length() / (1024f * 1024f)) + " MB)");
                        } else {
                            setText("");
                        }
                    }
                };
            }
        });

        loadFilesFromDirectory(xmlFileList, xmlFileDir.getText(), ".*\\.xml$");
        loadFilesFromDirectory(xsdFileList, xsltFileDir.getText(), ".*\\.xslt$");

        xmlDirChooserButton.setOnAction(e -> {
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            File selectedDirectory = directoryChooserXML.showDialog(stage);
            if (selectedDirectory != null && selectedDirectory.isDirectory()) {
                xmlFileDir.textProperty().setValue(selectedDirectory.getAbsolutePath());
                loadXmlFiles();
            }
        });

        xsltChooserButton.setOnAction(e -> {
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            File selectedDirectory = directoryChooserXSLT.showDialog(stage);
            if (selectedDirectory != null && selectedDirectory.isDirectory()) {
                xsltFileDir.textProperty().setValue(selectedDirectory.getAbsolutePath());
                loadXsdFiles();
            }
        });

        xmlFiles.setItems(xmlFileList);
        xsltFiles.setItems(xsdFileList);

        xmlFiles.setOnMouseClicked(event -> {
            System.out.println("clicked on " + xmlFiles.getSelectionModel().getSelectedItem());
            status.setText("Loading File: " + xmlFiles.getSelectionModel().getSelectedItem());
            if (xsltFiles.getSelectionModel().getSelectedItem() != null) {
                progressBar.setProgress(0.1);
                renderFile(xmlFiles.getSelectionModel().getSelectedItem().toString(), xsltFiles.getSelectionModel().getSelectedItem().toString());
            }
        });

        xsltFiles.setOnMouseClicked(event -> {
            // System.out.println("clicked on " + xsltFiles.getSelectionModel().getSelectedItem());
            logger.debug("clicked on " + xsltFiles.getSelectionModel().getSelectedItem());
            if (xmlFiles.getSelectionModel().getSelectedItem() != null) {
                status.setText("Loading File: " + xsltFiles.getSelectionModel().getSelectedItem());
                progressBar.setProgress(0.1);
                renderFile(xmlFiles.getSelectionModel().getSelectedItem().getAbsolutePath(), xsltFiles.getSelectionModel().getSelectedItem().toString());
            }
        });


    }

    private void loadFilesFromDirectory(ObservableList<File> files, String pfad, String pattern) {
        try {
            files.clear();
            var x = getFileNames(pfad, pattern);
            // System.out.println("x = " + x.stream().toArray().length);
            files.addAll(x);
        } catch (FileNotFoundException fileNotFoundException) {
            System.out.println("FILE NOT FOUND");
        }

    }

    private void loadXmlFiles() {
        try {
            xmlFileList.clear();
            xmlFileList.addAll(getFileNames(xmlFileDir.getText(), ".xml"));
        } catch (FileNotFoundException fileNotFoundException) {
            System.out.println("FILE NOT FOUND");
        }
    }

    private void loadXsdFiles() {
        try {
            xsdFileList.clear();
            xsdFileList.addAll(getFileNames(xsltFileDir.getText(), ".xslt"));
        } catch (FileNotFoundException fileNotFoundException) {
            System.out.println("FILE NOT FOUND");
        }
    }

    private void renderFile(String xmlFileName, String xsdFileName) {
        final String outputFile = "output/output.html";

        String output;
        try {
            status.setText("Starting...");
            progressBar.setProgress(0.1);
            output = saxonTransform(xmlFileName, xsdFileName);
            progressBar.setProgress(0.5);

            output = output.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "");
            output = output.replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "");
            output = output.replace("  >", "");

            status.setText("finished transforming");

            WebEngine engine = webView.getEngine();
            Files.writeString(Paths.get(outputFile), output);
            progressBar.setProgress(0.6);
            System.out.println("write successful");
            status.setText("write successful");

            engine.getLoadWorker().stateProperty().addListener(
                    (ov, oldState, newState) -> {
                        if (newState == State.SUCCEEDED) {
                            // System.out.println("FERTIG: " + engine.getLocation());
                            logger.debug("FERTIG: " + engine.getLocation());
                            status.setText("rendering finished");
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
