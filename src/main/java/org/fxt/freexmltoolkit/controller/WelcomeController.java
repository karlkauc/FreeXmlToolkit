package org.fxt.freexmltoolkit.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class WelcomeController {

    private final static Logger logger = LogManager.getLogger(WelcomeController.class);

    @FXML
    HBox versionUpdate;

    @FXML
    CheckBox sendUsageStatistics;

    @FXML
    Label duration;

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    @FXML
    private void initialize() {
        versionUpdate.setVisible(true);

        var prop = propertiesService.loadProperties();
        var oldSeconds = Integer.valueOf(prop.getProperty("usageDuration"));

        duration.setText(duration.getText().replace("{duration}", oldSeconds + " seconds"));
    }

    @FXML
    private void openUpdatePage() {
        try {
            Desktop.getDesktop().browse(new URL("https://github.com/karlkauc/FreeXmlToolkit").toURI());
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

}
