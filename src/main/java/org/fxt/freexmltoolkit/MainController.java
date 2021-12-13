package org.fxt.freexmltoolkit;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;

public class MainController {

    @FXML
    private Parent tabXml, tabXslt;

    @FXML
    private XmlController xmlController;

    @FXML
    Tab tabPaneXml, tabPaneXslt;

    @FXML
    Button prettyPrint;

    @FXML
    private void initialize() {
        prettyPrint.setOnAction(event -> {
            System.out.println("Hello World!");
            System.out.println("event.getSource() = " + event.getSource());
            System.out.println("tabPaneXml.isSelected() = " + tabPaneXml.isSelected());
            System.out.println("tabPaneXslt.isSelected() = " + tabPaneXslt.isSelected());
        });
    }

}
