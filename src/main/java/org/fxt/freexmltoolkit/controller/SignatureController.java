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

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.fxt.freexmltoolkit.service.SignatureService;

import java.io.File;

public class SignatureController {
    private final static Logger logger = LogManager.getLogger(SignatureController.class);
    private final SignatureService signatureService = new SignatureService();

    @FXML
    TextField commonName, organizationUnit, organizationName, localityName, streetName, country, email,
            createCertificateAlias, validateKeystoreAlias, signKeystoreAlias;

    @FXML
    TextField newFileName;

    @FXML
    Button signLoadKeystoreButton, signLoadXmlFileButton, validateLoadKeystoreButton, validateLoadXmlFileButton;

    File certificateFile, xmlFile;
    FileChooser fileChooserCertificate = new FileChooser();
    FileChooser fileChooserXMl = new FileChooser();

    @FXML
    Label certFileInfo, xmlFileInfo, validateKeystoreInfo, validateXmlFileInfo;

    @FXML
    PasswordField createCertificateKeystorePassword, createCertificateAliasPassword, signKeystorePassword, signAliasPassword, validatePasswordField;
    private MainController parentController;

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
        signLoadKeystoreButton.setOnAction(this::handleKeystoreOnAction);
        signLoadKeystoreButton.setOnDragOver(SignatureController::handleOnDragOver);
        signLoadKeystoreButton.setOnDragDropped(this::handleLoadKeystore);

        FileChooser.ExtensionFilter xmlFilter = new FileChooser.ExtensionFilter("XML Files", "*.xml");
        fileChooserXMl.getExtensionFilters().add(xmlFilter);
        signLoadXmlFileButton.setOnAction(this::handleXmlOnAction);
        signLoadXmlFileButton.setOnDragOver(SignatureController::handleOnDragOver);
        signLoadXmlFileButton.setOnDragDropped(this::handleXmlLoad);

        // validate xml file
        validateLoadKeystoreButton.setOnAction(this::handleKeystoreOnAction);
        validateLoadKeystoreButton.setOnDragOver(SignatureController::handleOnDragOver);
        validateLoadKeystoreButton.setOnDragDropped(this::handleLoadKeystore);

        validateLoadXmlFileButton.setOnAction(this::handleXmlOnAction);
        validateLoadXmlFileButton.setOnDragOver(SignatureController::handleOnDragOver);
        validateLoadXmlFileButton.setOnDragDropped(this::handleXmlLoad);

        if (System.getenv("debug") != null) {
            logger.debug("set Debug to True");
            this.xmlFile = new File("/release/examples/xml/FundsXML_sign.xml");
            this.certificateFile = new File("/release/examples/certs/karl/karl.pem");
        }
    }

    @FXML
    public void createNewSignatureFile() {
        try {
            final var aliasText = createCertificateAlias.getText();
            final var keystorePasswordText = createCertificateKeystorePassword.getText();
            final var aliasPasswordText = createCertificateAliasPassword.getText();

            if (aliasText.isEmpty() || keystorePasswordText.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setContentText("Alias and Keystore Password need to be provided!");
                alert.showAndWait();
            } else {
                // X.500 Name für das Zertifikat
                final X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
                if (!commonName.getText().isEmpty()) nameBuilder.addRDN(BCStyle.CN, commonName.getText());
                if (!organizationUnit.getText().isEmpty()) nameBuilder.addRDN(BCStyle.OU, organizationUnit.getText());
                if (!organizationName.getText().isEmpty()) nameBuilder.addRDN(BCStyle.O, organizationName.getText());
                if (!localityName.getText().isEmpty()) nameBuilder.addRDN(BCStyle.L, localityName.getText());
                if (!streetName.getText().isEmpty()) nameBuilder.addRDN(BCStyle.ST, streetName.getText());
                if (!country.getText().isEmpty()) nameBuilder.addRDN(BCStyle.C, country.getText());
                if (!email.getText().isEmpty()) nameBuilder.addRDN(BCStyle.EmailAddress, email.getText());

                var outFile = signatureService.createNewKeystoreFile(nameBuilder, aliasText, keystorePasswordText, aliasPasswordText);

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
        final var keystoreAlias = signKeystoreAlias.getText();
        final var keystorePassword = signKeystorePassword.getText();
        final var aliasPassword = signAliasPassword.getText();
        final var outputFileName = this.xmlFile.getName().toLowerCase().replace(".xml", newFileName.getText() + ".xml");

        if (certificateFile != null && xmlFile != null && !keystoreAlias.isEmpty() && !keystorePassword.isEmpty()) {
            try {
                var signedDocument = signatureService.signDocument(xmlFile, certificateFile, keystorePassword, keystoreAlias, aliasPassword, outputFileName);
                if (signedDocument != null) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setHeaderText("File signed successfully.");
                    alert.setContentText("File '" + signedDocument.getName() + "' signed successfully!");
                    alert.showAndWait();

                    this.xmlFile = signedDocument;
                    this.validateXmlFileInfo.setText(this.xmlFile.getName());
                }
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Certificate signing failed!");
            alert.setContentText("Certificate File, XML File, Keystore Password and Keystore Alias must be provided!");
            alert.showAndWait();
        }
    }

    @FXML
    public void validateSignedDocument() {
        final var validateAlias = validateKeystoreAlias.getText();
        final var validateKeystorePassword = validatePasswordField.getText();

        if (this.certificateFile != null && this.xmlFile != null && !validateAlias.isEmpty() && !validateKeystorePassword.isEmpty()) {
            var isValid = signatureService.isSignatureValid(this.xmlFile, this.certificateFile, validateAlias, validateKeystorePassword);

            Alert alert;
            if (isValid) {
                alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setHeaderText("Certificate validated successfully.");
                alert.setContentText("File '" + this.xmlFile.getName() + "' is valid!");
            } else {
                alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("Certificate not validated successfully.");
                alert.setContentText("File '" + this.xmlFile.getName() + "' is not valid!");
            }
            alert.showAndWait();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Certificate file or XML file are empty!");
            alert.setContentText("Certificate File, XML File and Keystore Alias must be provided!");
            alert.showAndWait();
        }
    }

    private static void handleOnDragOver(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        } else {
            event.consume();
        }
    }

    private void handleXmlLoad(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;
            for (File file : db.getFiles()) {
                this.xmlFile = file;
                this.xmlFileInfo.setText(this.xmlFile.getName());
                this.validateXmlFileInfo.setText(this.xmlFile.getName());
                logger.debug("Setting XML File from DragEvent: {}", this.xmlFile.getAbsoluteFile().getName());
            }
        }
        event.setDropCompleted(success);
        event.consume();
    }

    private void handleLoadKeystore(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;
            for (File file : db.getFiles()) {
                this.certificateFile = file;
                this.certFileInfo.setText(this.certificateFile.getName());
                this.validateKeystoreInfo.setText(this.certificateFile.getName());
                logger.debug("Setting Keystore File from dragEvent: {}", this.certificateFile.getAbsoluteFile().getName());
            }
        }
        event.setDropCompleted(success);
        event.consume();
    }

    private void handleXmlOnAction(ActionEvent e) {
        this.xmlFile = fileChooserXMl.showOpenDialog(null);
        this.validateXmlFileInfo.setText(this.xmlFile.getName());
        this.xmlFileInfo.setText(this.xmlFile.getName());
        this.validateXmlFileInfo.setText(this.xmlFile.getName());
        logger.debug("Setting XML File from onAction: {}", this.xmlFile.getAbsoluteFile().getName());
    }

    private void handleKeystoreOnAction(ActionEvent e) {
        this.certificateFile = fileChooserCertificate.showOpenDialog(null);
        this.validateKeystoreInfo.setText(this.certificateFile.getName());
        this.certFileInfo.setText(this.certificateFile.getName());
        logger.debug("Setting Keystore File from OnAction: {}", this.certificateFile.getAbsoluteFile().getName());
    }
}
