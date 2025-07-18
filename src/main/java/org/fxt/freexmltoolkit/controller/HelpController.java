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
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        System.setProperty("jdk.https.auth.tunneling.disabledSchemes", "");

        // System.setProperty("java.net.useSystemProxies", "true");
        final String proxyUser = "j13hkpb";
        final String proxyPass = "MarleneLeopold47";
        final String host = "proxy.p1at.s-group.cc";
        final Integer port = 8080;

        // http
        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", String.valueOf(port));
        System.setProperty("http.proxyUser", proxyUser);
        System.setProperty("http.proxyPassword", proxyPass);

        // https
        /*
        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", String.valueOf(port));
        System.setProperty("https.proxyUser", proxyUser);
        System.setProperty("https.proxyPassword", proxyPass);
         */

        /*
        Authenticator.setDefault(new Authenticator() {
                                     @Override
                                     public PasswordAuthentication getPasswordAuthentication() {
                                         return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
                                     }
                                 }
        );
         */


        WebEngine viewFXTDocEngine = viewFXTDoc.getEngine();
        viewFXTDocEngine.load("https://karlkauc.github.io/FreeXmlToolkit");

        WebEngine engineFundsXMLSite = viewFundsXMLSite.getEngine();
        engineFundsXMLSite.load("http://www.fundsxml.org");

        WebEngine engineMigrationGuide = viewMigrationGuide.getEngine();
        engineMigrationGuide.load("https://fundsxml.github.io/");
    }
}
