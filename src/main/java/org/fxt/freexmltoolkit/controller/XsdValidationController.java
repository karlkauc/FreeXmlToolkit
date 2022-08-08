package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class XsdValidationController {

    @Inject
    XmlService xmlService;
    private MainController parentController;

    @FXML
    AnchorPane anchorPane;

    FileChooser xmlFileChooser = new FileChooser(), xsdFileChooser = new FileChooser();

    @FXML
    GridPane auswertung;

    @FXML
    Button xmlLoadButton, xsdLoadButton;

    @FXML
    TextField xmlFileName, xsdFileName, schemaValid;

    @FXML
    TextArea errorList;

    @FXML
    CheckBox autodetect;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void toggleAutoDetection() {
        logger.debug("AUTO DETECT!");

        xsdLoadButton.setDisable(!xsdLoadButton.isDisable());
        xsdFileName.setDisable(!xsdFileName.isDisable());
    }

    @FXML
    private void initialize() {
        final Path path = FileSystems.getDefault().getPath(".");

        xmlFileChooser.setInitialDirectory(path.toFile());
        xmlFileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XML File", "*.xml"));
        xmlLoadButton.setOnAction(ae -> {
            var tempFile = xmlFileChooser.showOpenDialog(null);
            if (tempFile != null) {
                logger.debug("Loaded XML File: {}", tempFile.getAbsolutePath());
                xmlService.setCurrentXmlFile(tempFile);
                xmlFileName.setText(xmlService.getCurrentXmlFile().getName());
                reload();
            }
        });

        xsdFileChooser.setInitialDirectory(path.toFile());
        xsdFileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XSD File", "*.xsd"));
        xsdLoadButton.setOnAction(ae -> {
            var tempFile = xsdFileChooser.showOpenDialog(null);
            if (tempFile != null) {
                logger.debug("Loaded XSLT File: {}", tempFile.getAbsolutePath());
                xmlService.setCurrentXsdFile(tempFile);
                xsdFileName.setText(xmlService.getCurrentXsdFile().getName());
                reload();
            }
        });
    }

    @FXML
    private void reload() {
        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists() &&
                xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
            var exceptionList = xmlService.validate();
            if (exceptionList != null && exceptionList.size() > 0) {
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
                errorList.clear();
            }
        } else {
            logger.debug("war nicht alles ausgewählt!!");
        }
    }

    @FXML
    private void test() {
        xmlService.setCurrentXmlFile(Paths.get("C:/Data/src/FreeXmlToolkit/output/!FundsXML AMUNDI FLOATING RATE EURO CORP ESG as of 2021-12-30 v2.xml").toFile());
        xmlService.setCurrentXsdFile(Paths.get("C:/Data/src/schema/FundsXML4.xsd").toFile());
        reload();
    }

}
