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
    private Xml2Controller xml2Controller;

    @FXML
    Tab tabPaneXml, tabPaneXslt, tabPaneXml2;

    @FXML
    Button prettyPrint;

    @FXML
    Button exit;

    FileChooser fileChooser = new FileChooser();

    @FXML
    VBox mainBox;


    String textContent;


    @FXML
    private void openFile2(ActionEvent e) {
        Stage stage = (Stage) mainBox.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        System.out.println(selectedFile.getAbsolutePath());
        String fileContent;

        if (selectedFile.exists()) {
            try {
                fileContent = Files.readString(Path.of(selectedFile.getAbsolutePath()));
                System.out.println("fileContent.length() = " + String.format("%.2f", fileContent.length() / (1024f * 1024f)) + " MB");
                xml2Controller.setText(fileContent);
                textContent = fileContent;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }



    @FXML
    private void openFile(ActionEvent e) {
        Stage stage = (Stage) mainBox.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        System.out.println(selectedFile.getAbsolutePath());
        String fileContent;

        if (selectedFile.exists()) {
            try {
                fileContent = Files.readString(Path.of(selectedFile.getAbsolutePath()));
                System.out.println("fileContent.length() = " + String.format("%.2f", fileContent.length() / (1024f * 1024f)) + " MB");
                xmlController.codeArea.clear();
                System.out.println("Clear fertig");
                xmlController.codeArea.replaceText(0,0, fileContent);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }


    @FXML
    private void initialize() {
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml")
                ,new FileChooser.ExtensionFilter("XSLT Files", "*.xslt")
        );
        fileChooser.setInitialDirectory(new File("C:\\Data\\TEMP\\2021-12-14_FundsXMLTestFiles"));

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

            if (tabPaneXml2.isSelected()) {
                var temp = XmlController.prettyFormat(textContent, 2);
                xml2Controller.setText(temp);
            }
        });
    }


}
