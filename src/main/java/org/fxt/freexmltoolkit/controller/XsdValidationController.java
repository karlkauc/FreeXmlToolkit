package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Files;
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
    TextField xmlFileName, xsdFileName, remoteXsdLocation;

    @FXML
    TextArea errorList;

    @FXML
    CheckBox autodetect;

    @FXML
    ImageView statusImage;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void toggleAutoDetection() {
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

                if (autodetect.isSelected()) {
                    var schemaName = xmlService.getSchemaNameFromCurrentXMLFile();
                    if (schemaName != null && !schemaName.isEmpty()) {
                        xsdFileName.setText(schemaName);
                        if (xmlService.loadSchemaFromXMLFile()) {
                            logger.debug("Loading remote schema successfull!");
                        } else {
                            logger.debug("Could not load remote schema");
                        }
                    }
                }
                reload();

                parentController.getXmlController().reloadXmlText();
                // parentController.getXsdValidationController().reload(); TODO: hier file laden
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
            remoteXsdLocation.setText(xmlService.getRemoteXsdLocation());

            var exceptionList = xmlService.validate();
            if (exceptionList != null && exceptionList.size() > 0) {
                StringBuilder errorListString = new StringBuilder();

                logger.warn(Arrays.toString(exceptionList.toArray()));
                int i = 0;
                for (SAXParseException saxParseException : exceptionList) {
                    errorListString.append("#").append(++i).append(": ").append(saxParseException.getLocalizedMessage()).append(System.lineSeparator());
                    errorListString.append("Line#: ").append(saxParseException.getLineNumber()).append(" Col#: ").append(saxParseException.getColumnNumber()).append(System.lineSeparator());

                    try {
                        var lineBevore = Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(saxParseException.getLineNumber()-1).trim();
                        var line = Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(saxParseException.getLineNumber()).trim();
                        var lineAfter = Files.readAllLines(xmlService.getCurrentXmlFile().toPath()).get(saxParseException.getLineNumber()+1).trim();
                        errorListString.append(lineBevore).append(System.lineSeparator()).append(line).append(System.lineSeparator()).append(lineAfter).append(System.lineSeparator());
                    }
                    catch (IOException exception) {
                        logger.error("Exception: {}", exception.getMessage());
                    }
                    errorListString.append(System.lineSeparator());
                }
                Image image = new Image(getClass().getResource("/img/icons8-stornieren-48.png").toString());
                statusImage.setImage(image);
                errorList.setText(errorListString.toString());

            } else {
                logger.warn("KEINE ERRORS");
                Image image = new Image(getClass().getResource("/img/icons8-ok-48.png").toString());
                statusImage.setImage(image);
                errorList.clear();
            }
        } else {
            logger.debug("war nicht alles ausgewählt!!");
            logger.debug("Current XML File: {}", xmlService.getCurrentXmlFile());
            logger.debug("Current XSD File: {}", xmlService.getCurrentXsdFile());
        }
    }

    @FXML
    private void test() {
        xmlService.setCurrentXmlFile(Paths.get("C:/Data/src/FreeXmlToolkit/output/!FundsXML AMUNDI FLOATING RATE EURO CORP ESG as of 2021-12-30 v2.xml").toFile());
        xmlService.setCurrentXsdFile(Paths.get("C:/Data/src/schema/FundsXML4.xsd").toFile());
        reload();
    }

}
