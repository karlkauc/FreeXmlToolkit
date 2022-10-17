package org.fxt.freexmltoolkit.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

public class SettingsController {

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    private final static Logger logger = LogManager.getLogger(SettingsController.class);

    @FXML
    CheckBox useSystemHttpProxy;

    @FXML
    public void initialize() {
        logger.debug("INIT!");
    }


}
