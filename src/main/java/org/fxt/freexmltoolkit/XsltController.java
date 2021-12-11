package org.fxt.freexmltoolkit;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
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
    ListView xmlFiles;

    @FXML
    ListView xsltFiles;

    @FXML
    WebView webView;

    public final ObservableList<Object> xmlFileList =
            FXCollections.observableArrayList();
    public final ObservableList<Object> xsdFileList =
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
        // directoryChooser.setInitialDirectory(new File("output"));
        loadFiles();

        xmlDirChooserButton.setOnAction(e -> {
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            File selectedDirectory = directoryChooser.showDialog(stage);
            xmlFileDir.textProperty().setValue(selectedDirectory.getAbsolutePath());
            loadFiles();
        });

        xsltChooserButton.setOnAction(e -> {
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            File selectedDirectory = directoryChooser.showDialog(stage);
            xsltFileDir.textProperty().setValue(selectedDirectory.getAbsolutePath());
            loadFiles();
        });


        xmlFiles.setItems(xmlFileList);
        xsltFiles.setItems(xsdFileList);

        xmlFiles.setOnMouseClicked(event -> {
            System.out.println("clicked on " + xmlFiles.getSelectionModel().getSelectedItem());
            if (xsltFiles.getSelectionModel().getSelectedItem() != null) {
                renderFile(xmlFiles.getSelectionModel().getSelectedItem().toString(), xsltFiles.getSelectionModel().getSelectedItem().toString());
            }
        });

        xsltFiles.setOnMouseClicked(event -> {
            System.out.println("clicked on " + xsltFiles.getSelectionModel().getSelectedItem());
            if (xmlFiles.getSelectionModel().getSelectedItem() != null) {
                renderFile(xmlFiles.getSelectionModel().getSelectedItem().toString(), xsltFiles.getSelectionModel().getSelectedItem().toString());
            }
        });
    }

    private void loadFiles() {
        try {
            xsdFileList.clear();
            xmlFileList.clear();

            xmlFileList.addAll(getFileNames(xmlFileDir.getText(), ".xml"));
            xsdFileList.addAll(getFileNames(xsltFileDir.getText(), ".xslt"));
        } catch (FileNotFoundException fileNotFoundException) {
            System.out.println("FILE NOT FOUND");
        }
    }

    private void renderFile(String xmlFileName, String xsdFileName) {
        final String outputFile = "output/output.html";

        String output;
        try {
            output = saxonTransform(xmlFileName, xsdFileName);

            output = output.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "");
            output = output.replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "");

            WebEngine engine = webView.getEngine();
            Files.writeString(Paths.get(outputFile), output);
            System.out.println("write successful");

            engine.getLoadWorker().stateProperty().addListener(
                    (ov, oldState, newState) -> {
                        if (newState == State.SUCCEEDED) {
                            System.out.println("FERTIG: " + engine.getLocation());
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

    public static String prettyFormat(String input, int indent) {
        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch (Exception e) {
            throw new RuntimeException(e); // simple exception handling, please review it
        }
    }


}
