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
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.ConnectionServiceImpl;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

public class SettingsController {

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    private final static Logger logger = LogManager.getLogger(SettingsController.class);

    @FXML
    RadioButton noProxy, systemProxy, manualProxy;

    @FXML
    Spinner<Integer> portSpinner;

    @FXML
    Button checkConnection;

    SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 8080);

    @FXML
    public void initialize() {
        portSpinner.setValueFactory(valueFactory);
        loadCurrentSettings();
    }

    @FXML
    private void performCheck() {
        logger.debug("Perform Connection Check");

    }

    @FXML
    private void performSave() {

    }

    @FXML
    private void loadCurrentSettings() {
        var properties = propertiesService.loadProperties();

        ConnectionService connectionService = ConnectionServiceImpl.getInstance();
        connectionService.testConnection();
    }
}
