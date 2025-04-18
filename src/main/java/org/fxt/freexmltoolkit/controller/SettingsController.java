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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.ConnectionServiceImpl;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.jetbrains.annotations.NotNull;

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
    TextField customTempFolder, httpProxyHost, httpProxyUser, httpProxyPass, noProxyHost;

    @FXML
    Spinner<Integer> portSpinner;

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
        portSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 8080));
        loadCurrentSettings();

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
        try {
            var connectionResult = connectionService.executeHttpRequest(new URI("https://www.github.com"));
            logger.debug("HTTP Status: {}", connectionResult.httpStatus());

            Alert alert = new Alert(connectionResult.httpStatus() == 200 ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            alert.setHeaderText(connectionResult.httpStatus() == 200 ? "Connection Check successful." : "Connection Check failed.");
            alert.setTitle("Connection Check");

            StringBuilder headerString = new StringBuilder();
            List.of(connectionResult.resultHeader()).forEach(h -> headerString.append(h).append(System.lineSeparator()));
            alert.setContentText(getString(headerString, connectionResult));
            alert.showAndWait();

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private static @NotNull String getString(StringBuilder headerString, ConnectionResult connectionResult) {
        final String temp = headerString.toString().trim();

        return "URL: " + connectionResult.url() + "\n\n" +
                "Http Status: " + connectionResult.httpStatus() + "\n\n" +
                "Duration: " + connectionResult.duration() + " ms\n\n" +
                "Result Header: \n" + temp.substring(0, Math.min(temp.length(), 100)) + "\n\n" +
                "Result Body: \n" + connectionResult.resultBody().trim().substring(0, Math.min(connectionResult.resultBody().trim().length(), 100)) + "...";
    }

    @FXML
    private void performSave() {
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

        propertiesService.saveProperties(props);
    }

    @FXML
    private void loadCurrentSettings() {
        props = propertiesService.loadProperties();

        if (props.get("http.proxy.host") != null && !props.get("http.proxy.host").toString().isEmpty()) {
            manualProxy.setSelected(true);
            httpProxyHost.setText(props.get("http.proxy.host").toString());
            portSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, Integer.parseInt(props.get("http.proxy.port").toString())));
            httpProxyUser.setText(props.get("http.proxy.user").toString());
            httpProxyPass.setText(props.get("http.proxy.password").toString());
        } else {
            noProxy.setSelected(true);
            enableProxyFields(false);
        }

        if (props.get("customTempFolder") != null) {
            useCustomTempFolder.setSelected(true);
            customTempFolder.setText(props.get("customTempFolder").toString());
        } else {
            useSystemTempFolder.setSelected(true);
            enableTempFolderFields(false);
        }
    }
}
