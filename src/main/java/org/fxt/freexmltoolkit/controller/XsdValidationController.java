package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.FileLoader;
import org.fxt.freexmltoolkit.service.XmlService;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Arrays;

public class XsdValidationController {

    @Inject
    XmlService xmlService;
    private MainController parentController;

    @FXML
    FileLoader xmlFileLoader, xsdFileLoader;

    @FXML
    ProgressBar progressBar;

    @FXML
    GridPane auswertung;

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
        xmlFileLoader.setButtonText("XML File");
        xmlFileLoader.getLoadButton().setOnAction(ae -> {
            var xmlFile = xmlFileLoader.getFileAction();
            logger.debug("Loaded XML File: {}", xmlFile.getAbsolutePath());
            xmlService.setCurrentXmlFile(xmlFile);
            reload();
        });

        xsdFileLoader.setLoadPattern("*.xsd", "XSD File");
        xsdFileLoader.setButtonText("XSD File");
        xsdFileLoader.getLoadButton().setOnAction(ae -> {
            var xsdFile = xsdFileLoader.getFileAction();
            logger.debug("Loaded XSLT File: {}", xsdFile.getAbsolutePath());
            xmlService.setCurrentXsdFile(xsdFile);
            reload();
        });

        reload();
    }

    @FXML
    private void reload() {
        auswertung.add(new Text("XML File:"), 0, 0);
        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists()) {
            auswertung.add(new Text(xmlService.getCurrentXmlFile().getName()), 1, 0);
        }

        auswertung.add(new Text("XSLT File:"), 0, 1);
        if (xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
            auswertung.add(new Text(xmlService.getCurrentXsdFile().getName()), 1, 1);
        }

        auswertung.add(new Text("XSD validity:"), 0, 2);
        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists() &&
                xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
            var exceptionList = xmlService.validate();
            if (exceptionList != null) {
                logger.warn(Arrays.toString(exceptionList.toArray()));
                auswertung.add(new TextArea(Arrays.toString(exceptionList.toArray())), 1, 2);
            }
            else {
                logger.warn("KEINE ERRORS");
                auswertung.add(new TextArea("No Errors found"), 1, 2);
            }
        }
        else {
            logger.debug("war nicht alles ausgewählt!!");
            // logger.debug("Current XML File: {}", xmlService.getCurrentXsdFile().getName());
            // logger.debug("Current XSD File: {}", xmlService.getCurrentXsdFile().getName());
        }
    }

    @FXML
    private void test() {
        xmlService.setCurrentXmlFile(Paths.get("C:/Data/src/FreeXmlToolkit/output/!FundsXML AMUNDI FLOATING RATE EURO CORP ESG as of 2021-12-30 v2.xml").toFile());
        xmlService.setCurrentXsdFile(Paths.get("C:/Data/src/schema/FundsXML4.xsd").toFile());
        reload();
    }


}
