/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.ConnectionServiceImpl;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Properties;

public class SettingsController {

    Properties props;
    private final static Logger logger = LogManager.getLogger(SettingsController.class);
    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    ConnectionService connectionService = ConnectionServiceImpl.getInstance();

    @FXML
    RadioButton noProxy, systemProxy, manualProxy, useSystemTempFolder, useCustomTempFolder;

    @FXML
    TextField customTempFolder, httpProxyHost, httpProxyUser, noProxyHost;

    @FXML
    PasswordField httpProxyPass;

    @FXML
    Spinner<Integer> portSpinner, xmlIndentSpaces;

    @FXML
    Button checkConnection;

    @FXML
    ToggleGroup proxy, tempFolder;

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    public void initialize() {
        portSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 8080));
        xmlIndentSpaces.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 4));
        loadCurrentSettings();

        // Listener to enable/disable input fields
        manualProxy.selectedProperty().addListener((observable, oldValue, newValue) -> enableProxyFields(newValue));
        useCustomTempFolder.selectedProperty().addListener((observable, oldValue, newValue) -> enableTempFolderFields(newValue));
    }

    private void enableTempFolderFields(boolean enable) {
        customTempFolder.setDisable(!enable);
    }

    private void enableProxyFields(boolean enable) {
        httpProxyHost.setDisable(!enable);
        portSpinner.setDisable(!enable);
        httpProxyUser.setDisable(!enable);
        httpProxyPass.setDisable(!enable);
        noProxyHost.setDisable(!enable);
    }

    @FXML
    private void performCheck() {
        logger.debug("Perform Connection Check");

        // Create temporary properties with current UI values
        Properties testProps = new Properties();
        testProps.setProperty("useSystemProxy", String.valueOf(systemProxy.isSelected()));
        testProps.setProperty("manualProxy", String.valueOf(manualProxy.isSelected()));
        testProps.setProperty("http.proxy.host", httpProxyHost.getText());
        testProps.setProperty("http.proxy.port", portSpinner.getValue().toString());
        testProps.setProperty("http.proxy.user", httpProxyUser.getText());
        testProps.setProperty("http.proxy.password", httpProxyPass.getText());

        try {
            var connectionResult = connectionService.testHttpRequest(new URI("https://www.github.com"), testProps);
            // Check if a result was returned.
            if (connectionResult == null) {
                logger.error("Connection check failed. The connection service returned a null result.");
                showAlert(Alert.AlertType.ERROR, "Connection Error", "The connection check failed. The connection service did not return a result. Please check proxy settings and application logs.");
                return;
            }

            logger.debug("HTTP Status: {}", connectionResult.httpStatus());

            // HTTP-Statuscodes im 2xx-Bereich als Erfolg werten
            boolean isSuccess = connectionResult.httpStatus() >= 200 && connectionResult.httpStatus() < 300;

            Alert alert = new Alert(isSuccess ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            alert.setHeaderText(isSuccess ? "Connection Check successful." : "Connection Check failed.");
            alert.setTitle("Connection Check");

            StringBuilder headerString = new StringBuilder();
            if (connectionResult.resultHeader() != null) {
                List.of(connectionResult.resultHeader()).forEach(h -> headerString.append(h).append(System.lineSeparator()));
            }
            alert.setContentText(getString(headerString, connectionResult));
            alert.setResizable(true); // Macht das Fenster in der Größe veränderbar
            alert.showAndWait();

        } catch (Exception e) {
            // Log the complete exception for better error analysis
            logger.error("An unexpected error occurred during the connection check.", e);
            showAlert(Alert.AlertType.ERROR, "Connection Error", "An unexpected error occurred: " + e.getClass().getSimpleName() + ". See logs for details.");
        }
    }

    private static @NotNull String getString(StringBuilder headerString, ConnectionResult connectionResult) {
        final String temp = headerString.toString().trim();
        final String body = connectionResult.resultBody() != null ? connectionResult.resultBody().trim() : "";

        return "URL: " + connectionResult.url() + "\n\n" +
                "Http Status: " + connectionResult.httpStatus() + "\n\n" +
                "Duration: " + connectionResult.duration() + " ms\n\n" +
                "Result Header: \n" + temp.substring(0, Math.min(temp.length(), 100)) + "\n\n" +
                "Result Body: \n" + body.substring(0, Math.min(body.length(), 100)) + "...";
    }

    @FXML
    private void performSave() {
        try {
            props.setProperty("customTempFolder", customTempFolder.getText());
            props.setProperty("useCustomTempFolder", String.valueOf(useCustomTempFolder.isSelected()));
            props.setProperty("useSystemTempFolder", String.valueOf(useSystemTempFolder.isSelected()));

            props.setProperty("useSystemProxy", String.valueOf(systemProxy.isSelected()));
            props.setProperty("manualProxy", String.valueOf(manualProxy.isSelected()));
            props.setProperty("noProxyHost", noProxyHost.getText());
            props.setProperty("http.proxy.host", httpProxyHost.getText());
            props.setProperty("http.proxy.port", portSpinner.getValue().toString());
            props.setProperty("http.proxy.user", httpProxyUser.getText());
            props.setProperty("http.proxy.password", httpProxyPass.getText());
            props.setProperty("xml.indent.spaces", xmlIndentSpaces.getValue().toString());

            propertiesService.saveProperties(props);

            // Show success message
            showAlert(Alert.AlertType.INFORMATION, "Settings Saved", "Your settings have been saved successfully.");

        } catch (Exception e) {
            logger.error("Failed to save settings", e);
            // Show error message
            showAlert(Alert.AlertType.ERROR, "Save Error", "Could not save settings. Please check the logs for details.\n" + e.getMessage());
        }
    }

    @FXML
    private void selectCustomTempFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Custom Temporary Folder");
        File selectedDirectory = directoryChooser.showDialog(null);

        if (selectedDirectory != null) {
            customTempFolder.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void loadCurrentSettings() {
        props = propertiesService.loadProperties();

        // Load proxy settings
        boolean useSystem = Boolean.parseBoolean(props.getProperty("useSystemProxy", "true"));
        boolean useManual = Boolean.parseBoolean(props.getProperty("manualProxy", "false"));

        if (useManual) {
            manualProxy.setSelected(true);
            enableProxyFields(true);
            httpProxyHost.setText(props.getProperty("http.proxy.host", ""));
            int port = 8080;
            try {
                port = Integer.parseInt(props.getProperty("http.proxy.port", "8080"));
            } catch (NumberFormatException e) {
                logger.warn("Invalid port in settings, defaulting to 8080.");
            }
            portSpinner.getValueFactory().setValue(port);
            httpProxyUser.setText(props.getProperty("http.proxy.user", ""));
            httpProxyPass.setText(props.getProperty("http.proxy.password", ""));
            noProxyHost.setText(props.getProperty("noProxyHost", ""));
        } else if (useSystem) {
            systemProxy.setSelected(true);
            enableProxyFields(false);
        } else {
            noProxy.setSelected(true);
            enableProxyFields(false);
        }

        // Load temp folder settings
        if (props.get("customTempFolder") != null && !props.getProperty("customTempFolder").isBlank()) {
            useCustomTempFolder.setSelected(true);
            customTempFolder.setText(props.get("customTempFolder").toString());
            enableTempFolderFields(true);
        } else {
            useSystemTempFolder.setSelected(true);
            enableTempFolderFields(false);
        }

        // Load XML indent spaces setting
        int indentSpaces = propertiesService.getXmlIndentSpaces();
        xmlIndentSpaces.getValueFactory().setValue(indentSpaces);
    }

    /**
     * A helper method to display notifications.
     *
     * @param alertType The type of alert (e.g., INFORMATION or ERROR).
     * @param title     The title of the window.
     * @param content   The message to display.
     */
    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}