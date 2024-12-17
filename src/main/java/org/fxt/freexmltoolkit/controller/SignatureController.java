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
    private MainController parentController;
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
        signLoadKeystoreButton.setOnAction(e -> {
            certificateFile = fileChooserCertificate.showOpenDialog(null);
        });
        signLoadKeystoreButton.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });
        signLoadKeystoreButton.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    this.certificateFile = file;
                    this.certFileInfo.setText(this.certificateFile.getName());
                    logger.debug("Setting Keystore File: {}", this.certificateFile.getAbsoluteFile().getName());
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        FileChooser.ExtensionFilter xmlFilter = new FileChooser.ExtensionFilter("XML Files", "*.xml");
        fileChooserCertificate.getExtensionFilters().add(xmlFilter);
        signLoadXmlFileButton.setOnAction(e ->
                this.xmlFile = fileChooserXMl.showOpenDialog(null)
        );
        signLoadXmlFileButton.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });
        signLoadXmlFileButton.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    this.xmlFile = file;
                    this.xmlFileInfo.setText(this.xmlFile.getName());
                    logger.debug("Setting XML File: {}", this.xmlFile.getAbsoluteFile().getName());
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });


        // validate xml file
        validateLoadKeystoreButton.setOnAction(e -> {
            this.certificateFile = fileChooserCertificate.showOpenDialog(null);
        });
        validateLoadKeystoreButton.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });
        validateLoadKeystoreButton.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    this.certificateFile = file;
                    this.validateKeystoreInfo.setText(this.certificateFile.getName());
                    logger.debug("Setting Keystore File: {}", this.certificateFile.getAbsoluteFile().getName());
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        validateLoadXmlFileButton.setOnAction(e -> {
            this.xmlFile = fileChooserXMl.showOpenDialog(null);
        });
        validateLoadXmlFileButton.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });
        validateLoadXmlFileButton.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    this.xmlFile = file;
                    this.validateXmlFileInfo.setText(this.xmlFile.getName());
                    logger.debug("Setting XML File: {}", this.xmlFile.getAbsoluteFile().getName());
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });


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

            if (aliasText.isEmpty() || keystorePasswordText.isEmpty() || aliasPasswordText.isEmpty()) {
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
        logger.debug("Signing Document");
        final var keystoreAlias = signKeystoreAlias.getText();
        final var keystorePassword = signKeystorePassword.getText();
        final var aliasPassword = signAliasPassword.getText();
        final var outputFileName = this.xmlFile.getName().toLowerCase().replace(".xml", newFileName.getText() + ".xml");

        if (certificateFile != null && xmlFile != null && !keystoreAlias.isEmpty() && !keystorePassword.isEmpty() && !aliasPassword.isEmpty()) {
            try {
                var signedDocument = signatureService.signDocument(xmlFile, certificateFile, keystorePassword, keystoreAlias, aliasPassword, outputFileName);
                if (signedDocument != null) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setContentText("File signed successfully!");
                    alert.showAndWait();
                }
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Certificate File, XML File, Keystore Password and Keystore Alias must be provided!");
            alert.showAndWait();
        }
    }

    @FXML
    public void validateSignedDocument() {
        logger.debug("isDocumentSigned");

        final var validateAlias = validateKeystoreAlias.getText();
        final var validateKeystorePassword = validatePasswordField.getText();

        if (this.certificateFile != null && this.xmlFile != null && !validateAlias.isEmpty() && !validateKeystorePassword.isEmpty()) {
            var isValid = signatureService.isSignatureValid(this.xmlFile, this.certificateFile, validateAlias, validateKeystorePassword);

            Alert alert;
            if (isValid) {
                alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setContentText("File is valid!");
            } else {
                alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("File is not valid!");
            }
            alert.showAndWait();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Certificate File, XML File, Keystore Password and Keystore Alias must be provided!");
            alert.showAndWait();
        }
    }
}
