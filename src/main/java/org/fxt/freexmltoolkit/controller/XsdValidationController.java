package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
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
        TextArea errors = new TextArea();
        errors.setWrapText(true);

        errors.textProperty().addListener((obs, old, niu) -> {
            Text t = new Text(old + niu);
            t.setFont(errors.getFont());
            StackPane pane = new StackPane(t);
            pane.layout();
            double height = t.getLayoutBounds().getHeight();
            double padding = 100;
            logger.debug("NEW HEIGHT: {}", height);

            errors.setPrefHeight(height + padding);
            errors.setMinHeight(height + padding);
        });

        auswertung.add(errors, 1, 2);

        if (xmlService.getCurrentXmlFile() != null && xmlService.getCurrentXmlFile().exists() &&
                xmlService.getCurrentXsdFile() != null && xmlService.getCurrentXsdFile().exists()) {
            var exceptionList = xmlService.validate();
            if (exceptionList != null) {
                StringBuilder temp = new StringBuilder();

                logger.warn(Arrays.toString(exceptionList.toArray()));
                for (SAXParseException saxParseException : exceptionList) {
                    temp.append(saxParseException.getLocalizedMessage()).append(System.lineSeparator());
                }
                errors.textProperty().set(temp.toString());
                logger.debug("HEIGHT ERRORS: {}", errors.getMinHeight());
            } else {
                logger.warn("KEINE ERRORS");
                errors.setText("NO ERRORS FOUND");
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
