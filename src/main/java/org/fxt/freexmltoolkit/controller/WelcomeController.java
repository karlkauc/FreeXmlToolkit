/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

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
import java.net.URI;
import java.time.LocalTime;
import java.util.Properties;

public class WelcomeController {

    private final static Logger logger = LogManager.getLogger(WelcomeController.class);

    private MainController parentController;

    @FXML
    HBox versionUpdate;

    @FXML
    CheckBox sendUsageStatistics;

    @FXML
    Label duration;

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    Properties properties;


    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void initialize() {
        properties = propertiesService.loadProperties();
        logger.debug("Properties: {}", properties);

        if (properties.get("version") != null && properties.getProperty("version").equals("20221008")) {
            versionUpdate.setVisible(true);
        } else {
            logger.debug("not visible");
            versionUpdate.setVisible(false);
        }

        int oldSeconds = Integer.parseInt(properties.getProperty("usageDuration"));
        if (oldSeconds > 0) {
            var oldSecondsText = formatSecondsHumanReadable(oldSeconds);
            duration.setText(duration.getText().replace("{duration}", oldSecondsText));
        } else {
            duration.setText("You are here the first time!");
        }
    }

    @FXML
    private void openUpdatePage() {
        try {
            Desktop.getDesktop().browse(URI.create("https://github.com/karlkauc/FreeXmlToolkit"));
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private String formatSecondsHumanReadable(Integer seconds) {
        logger.debug("Format: {}", seconds);

        return LocalTime.MIN.plusSeconds(seconds).toString();
    }

}
