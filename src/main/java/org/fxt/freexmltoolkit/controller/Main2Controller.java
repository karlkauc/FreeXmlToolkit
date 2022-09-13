package org.fxt.freexmltoolkit.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main2Controller {

    private final static Logger logger = LogManager.getLogger(MainController.class);

    @FXML
    Label version;

    @FXML
    void initialize() {
        version.setText("Version: 0.0.1");

        System.out.println(Runtime.getRuntime().freeMemory() +
                " \t \t " + Runtime.getRuntime().totalMemory() +
                " \t \t " + Runtime.getRuntime().maxMemory());

        // oder MemoryMXBean

    }


}
