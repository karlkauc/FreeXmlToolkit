package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.FileLoader;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Arrays;

public class XsdValidationController {

    @Inject
    XmlService xmlService;
    private MainController parentController;

    @FXML
    AnchorPane anchorPane;

    @FXML
    FileLoader xmlFileLoader, xsdFileLoader;

    @FXML
    BorderPane borderPane;

    @FXML
    ProgressBar progressBar;

    @FXML
    GridPane auswertung;

    @FXML
    Button toggleButton;

    @FXML
    VBox fileChooserContainer;

    @FXML
    TextField xmlFileName, xsltFileName, schemaValid;

    @FXML
    TextArea errorList;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void initialize() {
        logger.debug("BIN IM XSD VALIDATION CONTROLLER");

        progressBar.setDisable(true);
        progressBar.setVisible(false);

        xmlFileLoader.setLoadPattern("*.xml", "XML File");
        xmlFileLoader.getLoadButton().setText("XML File");
        xmlFileLoader.getLoadButton().setOnAction(ae -> {
            var xmlFile = xmlFileLoader.getFileAction();
            logger.debug("Loaded XML File: {}", xmlFile.getAbsolutePath());
            xmlService.setCurrentXmlFile(xmlFile);
            reload();
        });
        xmlFileLoader.setImageView(new ImageView(new Image(getClass().getResource("/img/icons8-xml-64.png").toString(), 20, 20, true, true)));

        xsdFileLoader.setLoadPattern("*.xsd", "XSD File");
        xsdFileLoader.getLoadButton().setText("XSD File");
        xsdFileLoader.getLoadButton().setOnAction(ae -> {
            var xsdFile = xsdFileLoader.getFileAction();
            logger.debug("Loaded XSLT File: {}", xsdFile.getAbsolutePath());
            xmlService.setCurrentXsdFile(xsdFile);
            reload();
        });
        xsdFileLoader.setImageView(new ImageView(new Image(getClass().getResource("/img/icons8-daten-angekommen-32.png").toString(), 20, 20, true, true)));

        reload();
    }

    @FXML
    private void reload() {
        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()) {
            xmlFileName.setText(xmlService.getCurrentXmlFile().getName());
        }

        if (xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
            xsltFileName.setText(xmlService.getCurrentXsdFile().getName());
        }

        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists() &&
                xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
            var exceptionList = xmlService.validate();
            if (exceptionList != null) {
                StringBuilder errorListString = new StringBuilder();

                logger.warn(Arrays.toString(exceptionList.toArray()));
                int i = 0;
                for (SAXParseException saxParseException : exceptionList) {
                    errorListString.append("#").append(++i).append(": ").append(saxParseException.getLocalizedMessage()).append(System.lineSeparator());
                }
                schemaValid.setText("NO");
                errorList.setText(errorListString.toString());

            } else {
                logger.warn("KEINE ERRORS");
                schemaValid.setText("YES");
            }
        } else {
            logger.debug("war nicht alles ausgewählt!!");
        }
    }

    @FXML
    private void toggleBorderPane() {
        if (borderPane.isVisible()) {
            borderPane.setPrefWidth(0);
            toggleButton.setText(">>");
        } else {
            borderPane.setPrefWidth(300);
            toggleButton.setText("<<");
        }
        borderPane.setVisible(!borderPane.isVisible());
    }

    @FXML
    private void test() {
        xmlService.setCurrentXmlFile(Paths.get("C:/Data/src/FreeXmlToolkit/output/!FundsXML AMUNDI FLOATING RATE EURO CORP ESG as of 2021-12-30 v2.xml").toFile());
        xmlService.setCurrentXsdFile(Paths.get("C:/Data/src/schema/FundsXML4.xsd").toFile());
        reload();
    }


}
