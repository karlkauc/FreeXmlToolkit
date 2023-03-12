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
import javafx.scene.control.Tab;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class HelpController {

    @FXML
    AnchorPane anchorPane;

    @FXML
    WebView viewFXTDoc, viewFundsXMLSite, viewMigrationGuide;

    @FXML
    Tab tabFXTDoc, tabFundsXMlSite, tabMigrationGuide;

    @FXML
    public void initialize() {
        WebEngine viewFXTDocEngine = viewFXTDoc.getEngine();
        viewFXTDocEngine.load("https://karlkauc.github.io/FreeXmlToolkit");

        WebEngine engineFundsXMLSite = viewFundsXMLSite.getEngine();
        engineFundsXMLSite.load("http://www.fundsxml.org");

        WebEngine engineMigrationGuide = viewMigrationGuide.getEngine();
        engineMigrationGuide.load("https://fundsxml.github.io/");
    }
}
