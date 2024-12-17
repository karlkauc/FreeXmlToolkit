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
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.fxt.freexmltoolkit.service.SignatureService;

import java.io.File;

public class SignatureController {
    private MainController parentController;
    private final SignatureService signatureService = new SignatureService();

    @FXML
    TextField commonName, organizationUnit, organizationName, localityName, streetName, country, email, alias;

    @FXML
    TextField newFileName;

    @FXML
    Button loadCertificate, loadXmlFile;

    File certificateFile, xmlFile;
    FileChooser fileChooserCertificate = new FileChooser();
    FileChooser fileChooserXMl = new FileChooser();

    @FXML
    Label certFileInfo, xmlFileInfo;

    @FXML
    PasswordField passwordField;

    private final static Logger logger = LogManager.getLogger(SignatureController.class);

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void initialize() {
        if (new File("certs").exists()) {
            fileChooserCertificate.setInitialDirectory(new File("certs"));
        }

        FileChooser.ExtensionFilter certFilter = new FileChooser.ExtensionFilter("Certificate Files", "*.jks");
        fileChooserCertificate.getExtensionFilters().add(certFilter);
        loadCertificate.setOnAction(e -> {
            certificateFile = fileChooserCertificate.showOpenDialog(null);
        });

        FileChooser.ExtensionFilter xmlFilter = new FileChooser.ExtensionFilter("XML Files", "*.xml");
        fileChooserCertificate.getExtensionFilters().add(xmlFilter);
        loadXmlFile.setOnAction(e ->
                xmlFile = fileChooserXMl.showOpenDialog(null)
        );

        if (System.getenv("debug") != null) {
            logger.debug("set Debug to True");
            this.xmlFile = new File("/release/examples/xml/FundsXML_sign.xml");
            this.certificateFile = new File("/release/examples/certs/karl/karl.pem");
        }
    }

    @FXML
    public void createNewSignatureFile() {
        try {
            final var aliasText = alias.getText();
            final var passwordText = passwordField.getText();
            if (aliasText.isEmpty() || passwordText.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Alias and password need to be provided!!!");
                alert.showAndWait();
            } else {

                // X.500 Name für das Zertifikat
                final X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
                if (!commonName.getText().isEmpty()) {
                    nameBuilder.addRDN(BCStyle.CN, commonName.getText());
                }
                if (!organizationUnit.getText().isEmpty()) {
                    nameBuilder.addRDN(BCStyle.OU, organizationUnit.getText());
                }
                if (!organizationName.getText().isEmpty()) {
                    nameBuilder.addRDN(BCStyle.O, organizationName.getText());
                }
                if (!localityName.getText().isEmpty()) {
                    nameBuilder.addRDN(BCStyle.L, localityName.getText());
                }
                if (!streetName.getText().isEmpty()) {
                    nameBuilder.addRDN(BCStyle.ST, streetName.getText());
                }
                if (!country.getText().isEmpty()) {
                    nameBuilder.addRDN(BCStyle.C, country.getText());
                }
                if (!email.getText().isEmpty()) {
                    nameBuilder.addRDN(BCStyle.EmailAddress, email.getText());
                }

                var outFile = signatureService.createNewKeystoreFile(nameBuilder, aliasText, passwordText);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setHeaderText("Certificate created successfully.");
                alert.setContentText("Your certificate was created successfully. \n It can be found under: " + outFile.getAbsolutePath());
                alert.showAndWait();
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @FXML
    public void signDocument() {
        logger.debug("Signing Document");
        if (certificateFile != null && xmlFile != null) {

            try {
                var f = signatureService.signDocument(xmlFile, certificateFile, "karl", "123", "outputfile.xml");
                if (f != null) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setContentText("File signed successfully!");
                    alert.showAndWait();
                }
                // System.out.println("Das Dokument wurde erfolgreich signiert.");
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

    @FXML
    public void validateSignedDocument() {
        logger.debug("isDocumentSigned");

        if (this.certificateFile != null && this.xmlFile != null) {
            var isValid = signatureService.isSignatureValid(this.xmlFile, this.certificateFile, "karl", "123");

            Alert alert;
            if (isValid) {
                alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setContentText("File is valid!");
            } else {
                alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("File is not valid!");
            }
            alert.showAndWait();
        }
    }

}
