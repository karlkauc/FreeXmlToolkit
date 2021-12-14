package org.fxt.freexmltoolkit;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    FileChooser fileChooser = new FileChooser();

    @FXML
    VBox mainBox;

    @FXML
    private void openFile(ActionEvent e) {
        Stage stage = (Stage) mainBox.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        System.out.println(selectedFile.getAbsolutePath());

        xmlController.codeArea.clear();
        try {
            xmlController.codeArea.replaceText(0,0, Files.readString(Path.of(selectedFile.getAbsolutePath())));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    @FXML
    private void initialize() {
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml")
                ,new FileChooser.ExtensionFilter("XSLT Files", "*.xslt")
        );

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
                    xmlController.setPrettyText();
                }
                else
                    System.out.println("XMLController is null");
            }
        });
    }


}
