package org.fxt.freexmltoolkit;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.stage.Window;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class MainController {

    @FXML
    private Parent xml, tabXslt;

    @FXML
    private XmlController xmlController;

    @FXML
    Tab tabPaneXml, tabPaneXslt;

    @FXML
    Button prettyPrint;

    @FXML
    Button exit;

    @FXML
    private void initialize() {
        FXMLLoader fxmlLoader = new FXMLLoader();
        try {
            fxmlLoader.load(getClass().getResource("tab_xml.fxml").openStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        exit.setOnAction(e -> System.exit(0));

        prettyPrint.setOnAction(event -> {
            System.out.println("Hello World!");
            System.out.println("event.getSource() = " + event.getSource());
            System.out.println("tabPaneXml.isSelected() = " + tabPaneXml.isSelected());
            System.out.println("tabPaneXslt.isSelected() = " + tabPaneXslt.isSelected());

            if (tabPaneXml.isSelected()) {
                if (xmlController != null) {
                    String f = xmlController.getXMLContent();
                    System.out.println(f);
                    xmlController.setPrettyText();
                }
                else
                    System.out.println("XMLController is null");
            }
        });
    }


}
