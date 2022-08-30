package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

public class FopController {

    @Inject
    XmlService xmlService;

    @FXML
    GridPane settings;

    @FXML
    Button startConversion;

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @FXML
    private void initialize() {
        logger.debug("BIN IM FOP CONTROLLER");
    }

    @FXML
    private void buttonConversion() throws IOException {
        logger.debug("Start Conversion!");




    }

}

