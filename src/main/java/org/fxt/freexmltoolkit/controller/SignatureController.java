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
import org.fxt.freexmltoolkit.service.SignatureService.SignatureServiceException;

import java.io.File;

public class SignatureController {
    private final static Logger logger = LogManager.getLogger(SignatureController.class);
    private final SignatureService signatureService = new SignatureService();

    @FXML
    private TextField commonName, organizationUnit, organizationName, localityName, streetName, country, email,
            createCertificateAlias, signKeystoreAlias;

    @FXML
    private TextField newFileName;

    @FXML
    private Button signLoadKeystoreButton, signLoadXmlFileButton, validateLoadXmlFileButton;

    private File certificateFile, xmlFile;
    private final FileChooser fileChooserCertificate = new FileChooser();
    private final FileChooser fileChooserXMl = new FileChooser();

    @FXML
    private Label certFileInfo, xmlFileInfo, validateXmlFileInfo;

    @FXML
    private PasswordField createCertificateKeystorePassword, createCertificateAliasPassword, signKeystorePassword, signAliasPassword;
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

        // KORREKTUR: Event-Handler für die Validierungs-Buttons werden hier ebenfalls gesetzt
        validateLoadXmlFileButton.setOnAction(this::handleXmlOnAction);
        validateLoadXmlFileButton.setOnDragOver(SignatureController::handleOnDragOver);
        validateLoadXmlFileButton.setOnDragDropped(this::handleXmlLoad);
    }

    @FXML
    public void createNewSignatureFile() {
        final var aliasText = createCertificateAlias.getText();
        final var keystorePasswordText = createCertificateKeystorePassword.getText();
        final var aliasPasswordText = createCertificateAliasPassword.getText();

        if (aliasText.isBlank() || keystorePasswordText.isBlank() || aliasPasswordText.isBlank()) {
            showErrorAlert("Input Required", "All fields are mandatory.", "Please provide Alias, Keystore Password, and Alias Password.");
            return;
        }

        try {
            final X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
            if (!commonName.getText().isBlank()) nameBuilder.addRDN(BCStyle.CN, commonName.getText());
            if (!organizationUnit.getText().isBlank()) nameBuilder.addRDN(BCStyle.OU, organizationUnit.getText());
            if (!organizationName.getText().isBlank()) nameBuilder.addRDN(BCStyle.O, organizationName.getText());
            if (!localityName.getText().isBlank()) nameBuilder.addRDN(BCStyle.L, localityName.getText());
            if (!streetName.getText().isBlank()) nameBuilder.addRDN(BCStyle.ST, streetName.getText());
            if (!country.getText().isBlank()) nameBuilder.addRDN(BCStyle.C, country.getText());
            if (!email.getText().isBlank()) nameBuilder.addRDN(BCStyle.EmailAddress, email.getText());

            var outFile = signatureService.createNewKeystoreFile(nameBuilder, aliasText, keystorePasswordText, aliasPasswordText);

            showInfoAlert("Success", "Certificate created successfully.", "Your certificate was created successfully. \nIt can be found under: " + outFile.getParent());

        } catch (SignatureServiceException e) {
            logger.error("Certificate creation failed.", e);
            showErrorAlert("Certificate Creation Failed", "Could not create the certificate.", e.getMessage());
        }
    }

    @FXML
    public void signDocument() {
        final var keystoreAlias = signKeystoreAlias.getText();
        final var keystorePassword = signKeystorePassword.getText();
        final var aliasPassword = signAliasPassword.getText();

        if (certificateFile == null || xmlFile == null || keystoreAlias.isBlank() || keystorePassword.isBlank() || aliasPassword.isBlank()) {
            showErrorAlert("Input Required", "All fields and files are mandatory.", "Please provide an XML File, a Keystore File, and all password/alias fields.");
            return;
        }

        final var outputFileName = this.xmlFile.getName().toLowerCase().replace(".xml", newFileName.getText() + ".xml");

        try {
            var signedDocument = signatureService.signDocument(xmlFile, certificateFile, keystorePassword, keystoreAlias, aliasPassword, outputFileName);
            showInfoAlert("Success", "File signed successfully.", "File '" + signedDocument.getName() + "' was created.");

            // Das neu signierte Dokument für die Validierung bereitstellen
            this.xmlFile = signedDocument;
            this.validateXmlFileInfo.setText(this.xmlFile.getName());

        } catch (SignatureServiceException e) {
            logger.error("Document signing failed.", e);
            showErrorAlert("Signing Failed", "Could not sign the document.", e.getMessage());
        }
    }

    @FXML
    public void validateSignedDocument() {
        if (this.xmlFile == null) {
            showErrorAlert("Validation Failed", "No XML file loaded.", "Please load a signed XML file to validate.");
            return;
        }

        try {
            boolean isValid = signatureService.isSignatureValid(this.xmlFile);

            if (isValid) {
                showInfoAlert("Validation Successful", "The signature is valid.", "The signature in '" + this.xmlFile.getName() + "' has been successfully validated.");
            } else {
                showErrorAlert("Validation Failed", "The signature is invalid.", "The signature in '" + this.xmlFile.getName() + "' could not be validated.");
            }
        } catch (SignatureServiceException e) {
            logger.error("An error occurred during validation.", e);
            showErrorAlert("Validation Error", "A technical error occurred during validation.", e.getMessage());
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
        if (db.hasFiles() && !db.getFiles().isEmpty()) {
            File file = db.getFiles().get(0);
            updateXmlFile(file);
            event.setDropCompleted(true);
        }
        event.consume();
    }

    private void handleLoadKeystore(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() && !db.getFiles().isEmpty()) {
            File file = db.getFiles().get(0);
            updateKeystoreFile(file);
            event.setDropCompleted(true);
        }
        event.consume();
    }

    private void handleXmlOnAction(ActionEvent e) {
        File file = fileChooserXMl.showOpenDialog(null);
        if (file != null) {
            updateXmlFile(file);
        }
    }

    private void handleKeystoreOnAction(ActionEvent e) {
        File file = fileChooserCertificate.showOpenDialog(null);
        if (file != null) {
            updateKeystoreFile(file);
        }
    }

    private void updateXmlFile(File file) {
        this.xmlFile = file;
        String fileName = file.getName();
        this.xmlFileInfo.setText(fileName);
        this.validateXmlFileInfo.setText(fileName);
        logger.debug("Set XML File to: {}", file.getAbsolutePath());
    }

    private void updateKeystoreFile(File file) {
        this.certificateFile = file;
        String fileName = file.getName();
        this.certFileInfo.setText(fileName);
        logger.debug("Set Keystore File to: {}", file.getAbsolutePath());
    }

    private void showInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}