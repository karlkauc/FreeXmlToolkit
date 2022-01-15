package org.fxt.freexmltoolkit.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class MainController {

    @FXML
    private Parent xml, xslt;

    @FXML
    private XmlController xmlController;

    @FXML
    private XsltController xsltController;

    @FXML
    Tab tabPaneXml, tabPaneXslt;

    @FXML
    Button prettyPrint;

    @FXML
    Button exit, about;

    FileChooser fileChooser = new FileChooser();

    @FXML
    VBox mainBox;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @FXML
    private void openHelpPage(ActionEvent e) {
        try {
            Desktop.getDesktop().browse(new URI("https://www.google.at"));
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void pressAboutButton(ActionEvent e) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information Dialog");
        alert.setHeaderText("Look, an Information Dialog");
        alert.setContentText("I have a great message for you!");

        alert.showAndWait();
    }

    public XmlController getXmlController() {
        return xmlController;
    }

    public XsltController getXsltController() {
        return xsltController;
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
                xmlController.codeArea.replaceText(0, 0, fileContent);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }


    @FXML
    private void initialize() {
        xmlController.setParentController(this);
        xsltController.setParentController(this);

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml")
                , new FileChooser.ExtensionFilter("XSLT Files", "*.xslt")
        );

        if (SystemUtils.OS_NAME.toUpperCase(Locale.ROOT).startsWith("WINDOWS") && new File(("C:\\Data\\TEMP\\2021-12-14_FundsXMLTestFiles")).exists()) {
            fileChooser.setInitialDirectory(new File("C:\\Data\\TEMP\\2021-12-14_FundsXMLTestFiles"));
        }

        exit.setOnAction(e -> System.exit(0));

        prettyPrint.setOnAction(event -> {
            if (tabPaneXml.isSelected()) {
                if (xmlController != null) {
                    xmlController.formatXmlText();
                } else {
                    logger.error("XMLController is null");
                }
            }
        });
    }


}
