package org.fxt.freexmltoolkit;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import net.sf.saxon.TransformerFactoryImpl;

import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EventObject;

public class XsltController {

    public XsltController() {

    }

    DirectoryChooser directoryChooser = new DirectoryChooser();

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

    private ObservableList<File> getFileNames(String path, String pattern) throws FileNotFoundException {

        if (!Files.exists(Path.of(path))) {
            throw new FileNotFoundException();
        } else {
            File dir = new File(path);
            File[] files = dir.listFiles((dir1, name) -> name.endsWith(pattern));
            return FXCollections.observableArrayList(files);
        }
    }

    @FXML
    private void initialize() {
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

        loadXmlFiles();
        loadXsdFiles();

        xmlDirChooserButton.setOnAction(e -> {
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            File selectedDirectory = directoryChooser.showDialog(stage);
            xmlFileDir.textProperty().setValue(selectedDirectory.getAbsolutePath());
            loadXmlFiles();
        });

        xsltChooserButton.setOnAction(e -> {
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            File selectedDirectory = directoryChooser.showDialog(stage);
            xsltFileDir.textProperty().setValue(selectedDirectory.getAbsolutePath());
            loadXsdFiles();
        });

        xmlFiles.setItems(xmlFileList);
        xsltFiles.setItems(xsdFileList);

        xmlFiles.setOnMouseClicked(event -> {
            System.out.println("clicked on " + xmlFiles.getSelectionModel().getSelectedItem());
            status.setText("Loading File: " + xmlFiles.getSelectionModel().getSelectedItem());
            if (xsltFiles.getSelectionModel().getSelectedItem() != null) {
                renderFile(xmlFiles.getSelectionModel().getSelectedItem().toString(), xsltFiles.getSelectionModel().getSelectedItem().toString());
            }
        });

        xsltFiles.setOnMouseClicked(event -> {
            status.setText("Loading File: " + xsltFiles.getSelectionModel().getSelectedItem());
            System.out.println("clicked on " + xsltFiles.getSelectionModel().getSelectedItem());
            if (xmlFiles.getSelectionModel().getSelectedItem() != null) {
                renderFile(xmlFiles.getSelectionModel().getSelectedItem().toString(), xsltFiles.getSelectionModel().getSelectedItem().toString());
            }
        });
    }

    // ToDo: zellen rendern: nur Filename (ohne Pfad) und Größe anzeigen
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
            output = saxonTransform(xmlFileName, xsdFileName);

            output = output.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "");
            output = output.replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "");
            output = output.replace("  >", "");

            status.setText("finished transforming");

            WebEngine engine = webView.getEngine();
            Files.writeString(Paths.get(outputFile), output);
            System.out.println("write successful");
            status.setText("write successful");

            // ToDo: auf Loader Balken umändern

            engine.getLoadWorker().stateProperty().addListener(
                    (ov, oldState, newState) -> {
                        if (newState == State.SUCCEEDED) {
                            System.out.println("FERTIG: " + engine.getLocation());
                            status.setText("rendering finished");
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
