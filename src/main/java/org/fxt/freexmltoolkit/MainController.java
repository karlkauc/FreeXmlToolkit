package org.fxt.freexmltoolkit;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;

public class MainController {

    @FXML private Parent tabXml;

    @FXML private XmlController xmlController;


    @FXML
    Button prettyPrint;

    @FXML
    private void initialize() {
        prettyPrint.setOnAction(event -> {
            System.out.println("Hello World!");
            System.out.println("event.getSource() = " + event.getSource());


        });
    }

}
