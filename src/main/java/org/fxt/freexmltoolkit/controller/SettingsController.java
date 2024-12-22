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
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.ConnectionServiceImpl;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import java.net.URI;
import java.util.Properties;

public class SettingsController {

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    ConnectionService connectionService = ConnectionServiceImpl.getInstance();

    private final static Logger logger = LogManager.getLogger(SettingsController.class);

    @FXML
    RadioButton noProxy, systemProxy, manualProxy;

    @FXML
    TextField username, password, customTempFolder, httpProxyHost, httpProxyPort, httpProxyUser, httpProxyPass;

    @FXML
    Spinner<Integer> portSpinner;

    @FXML
    Button checkConnection;

    @FXML
    ToggleGroup proxy, tempFolder;

    Properties props;

    SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 8080);

    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    public void initialize() {
        portSpinner.setValueFactory(valueFactory);
        props = propertiesService.loadProperties();

        if (props.get("httpProxyHost") != null) this.httpProxyHost.setText(props.get("httpProxyHost").toString());

    }

    @FXML
    private void performCheck() {
        logger.debug("Perform Connection Check");
        try {
            var r = connectionService.executeHttpRequest(new URI("https://www.github.com"));
            logger.debug(r.httpStatus());

            Alert alert;
            if (r.httpStatus() == 200) {
                alert = new Alert(Alert.AlertType.INFORMATION);
            } else {
                alert = new Alert(Alert.AlertType.ERROR);
            }

            alert.setTitle("Connection Check");
            alert.setHeaderText("Connection Check");
            alert.setContentText("Connection Check successful. " + r);
            alert.showAndWait();

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @FXML
    private void performSave() {
        props.setProperty("username", username.getText());
        props.setProperty("password", password.getText());
        props.setProperty("customTempFolder", customTempFolder.getText());
        props.setProperty("httpProxyHost", httpProxyHost.getText());
        props.setProperty("httpProxyPort", httpProxyPort.getText());
        props.setProperty("httpProxyUser", httpProxyUser.getText());
        props.setProperty("httpProxyPass", httpProxyPass.getText());

        propertiesService.saveProperties(props);
    }

    @FXML
    private void loadCurrentSettings() {
        // connectionService.testConnection();
    }
}
