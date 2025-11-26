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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.UpdateCheckService;
import org.fxt.freexmltoolkit.service.UpdateCheckServiceImpl;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.time.LocalTime;
import java.util.Properties;

public class WelcomeController {

    private static final Logger logger = LogManager.getLogger(WelcomeController.class);
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    private final UpdateCheckService updateCheckService = UpdateCheckServiceImpl.getInstance();
    private Properties properties;
    private MainController parentController;
    private String latestVersionUrl = "https://github.com/karlkauc/FreeXmlToolkit/releases/latest";

    @FXML
    private HBox versionUpdate;
    @FXML
    private CheckBox sendUsageStatistics;
    @FXML
    private Label durationLabel, versionLabel;
    @FXML
    private Hyperlink updateLink;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void initialize() {
        properties = propertiesService.loadProperties();
        logger.debug("Properties: {}", properties);

        // Set current version
        String currentVersion = updateCheckService.getCurrentVersion();
        versionLabel.setText("Version: " + currentVersion);

        // Hide update notification initially
        versionUpdate.setVisible(false);
        versionUpdate.setManaged(false);

        // Check for updates asynchronously
        checkForUpdates();

        // Set usage duration
        int oldSeconds = Integer.parseInt(properties.getProperty("usageDuration", "0"));
        durationLabel.setText(oldSeconds > 0 ? formatSecondsHumanReadable(oldSeconds) : "You are here the first time!");
    }

    /**
     * Checks for updates asynchronously and updates the UI if a new version is available.
     */
    private void checkForUpdates() {
        if (!updateCheckService.isUpdateCheckEnabled()) {
            logger.debug("Update check is disabled");
            return;
        }

        updateCheckService.checkForUpdates()
                .thenAccept(this::handleUpdateInfo)
                .exceptionally(ex -> {
                    logger.warn("Failed to check for updates: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Handles the update information and updates the UI accordingly.
     */
    private void handleUpdateInfo(UpdateInfo updateInfo) {
        Platform.runLater(() -> {
            if (updateInfo.updateAvailable()) {
                logger.info("Update available: {} -> {}", updateInfo.currentVersion(), updateInfo.latestVersion());

                // Update version label to show both versions
                versionLabel.setText(String.format("Version: %s (Update available: %s)",
                        updateInfo.currentVersion(), updateInfo.latestVersion()));

                // Store the download URL for the hyperlink
                if (updateInfo.downloadUrl() != null) {
                    latestVersionUrl = updateInfo.downloadUrl();
                }

                // Show update notification
                versionUpdate.setVisible(true);
                versionUpdate.setManaged(true);
            } else {
                logger.debug("No update available. Current version: {}", updateInfo.currentVersion());
                versionLabel.setText("Version: " + updateInfo.currentVersion() + " (up to date)");
            }
        });
    }

    @FXML
    private void openUpdatePage() {
        try {
            Desktop.getDesktop().browse(URI.create(latestVersionUrl));
        } catch (IOException e) {
            logger.error("Failed to open update page: {}", e.getMessage());
        }
    }

    private String formatSecondsHumanReadable(int seconds) {
        logger.debug("Format: {}", seconds);
        return LocalTime.MIN.plusSeconds(seconds).toString();
    }
}