package org.fxt.freexmltoolkit.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;

import java.io.IOException;

public class FopController {

    XmlService xmlService = XmlServiceImpl.getInstance();

    @FXML
    GridPane settings;

    @FXML
    Button startConversion;

    @FXML
    AnchorPane xml, xslt, pdf;

    @FXML
    StackPane stackPaneXml, stackPaneXslt;

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    private final static Logger logger = LogManager.getLogger(FopController.class);

    @FXML
    private void initialize() {
        logger.debug("BIN IM FOP CONTROLLER");
    }

    @FXML
    private void buttonConversion() throws IOException {
        logger.debug("Start Conversion!");

    }

}

