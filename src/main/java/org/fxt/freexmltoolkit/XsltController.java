package org.fxt.freexmltoolkit;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import net.sf.saxon.TransformerFactoryImpl;

import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class XsltController {

    public XsltController() {

    }

    @FXML
    ListView xmlFiles;

    @FXML
    ListView xsltFiles;

    @FXML
    WebView webView;

    public final ObservableList xmlFileList =
            FXCollections.observableArrayList();
    public final ObservableList xsdFileList =
            FXCollections.observableArrayList();

    private ObservableList getFileNames(String path, String pattern) {
        File dir = new File(path);
        File[] files = dir.listFiles((dir1, name) -> name.endsWith(pattern));
        return FXCollections.observableArrayList(files);
    }

    @FXML
    private void initialize() {
        xmlFileList.addAll(getFileNames("output/testfiles", ".xml"));
        xsdFileList.addAll(getFileNames("output", ".xslt"));


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

    private void renderFile(String xmlFileName, String xsdFileName) {
        final String outputFile = "output/output.html";

        String output;
        try {
            output = saxonTransform(xmlFileName, xsdFileName);
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

    public static void main(String[] args) {
        try {
            var result = saxonTransform("C:\\Data\\src\\XMLTEST\\LU0622306495_EUR_20210930_v4_1_5.xml", "C:\\Data\\src\\XMLTEST\\Check_FundsXML_File.xslt");
            result = result.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "");
            result = result.replace("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"", "");

            System.out.println("result = " + result);
        } catch (Exception e) {
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
