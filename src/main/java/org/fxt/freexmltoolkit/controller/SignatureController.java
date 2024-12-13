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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SignatureController {
    private MainController parentController;

    @FXML
    TextField commonName, organizationUnit, organizationName, localityName, streetName, country, email, alias;

    @FXML
    Button loadCertificate, loadXmlFile;

    File certificateFile, xmlFile;

    @FXML
    PasswordField passwordField;

    private final static Logger logger = LogManager.getLogger(SignatureController.class);

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
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
                Security.addProvider(new BouncyCastleProvider());
                final Path outputDir = Paths.get("certs", aliasText);
                Files.createDirectories(outputDir);

                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
                keyPairGenerator.initialize(2048, new SecureRandom());
                KeyPair keyPair = keyPairGenerator.generateKeyPair();

                long now = System.currentTimeMillis();
                Date startDate = new Date(now);
                Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 Jahr gültig
                BigInteger serialNumber = BigInteger.valueOf(now); // Seriennummer

                // X.500 Name für das Zertifikat
                X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
                nameBuilder.addRDN(BCStyle.CN, commonName.getText());
                nameBuilder.addRDN(BCStyle.OU, organizationUnit.getText());
                nameBuilder.addRDN(BCStyle.O, organizationName.getText());
                nameBuilder.addRDN(BCStyle.L, localityName.getText());
                nameBuilder.addRDN(BCStyle.ST, streetName.getText());
                nameBuilder.addRDN(BCStyle.C, country.getText());
                nameBuilder.addRDN(BCStyle.EmailAddress, email.getText());

                X500Name issuer = nameBuilder.build();
                X500Name subject = issuer;

                X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuer, serialNumber, startDate, endDate, subject, keyPair.getPublic());

                ContentSigner contentSigner = new JcaContentSignerBuilder("SHA512withRSA").build(keyPair.getPrivate());
                X509Certificate certificate = new JcaX509CertificateConverter()
                        .setProvider("BC")
                        .getCertificate(certBuilder.build(contentSigner));

                JcaPEMWriter certWriter = new JcaPEMWriter(new FileWriter(outputDir + File.separator + aliasText + ".pem"));
                certWriter.writeObject(certificate);
                certWriter.close();

                var encryptor = new JcePEMEncryptorBuilder("AES-256-CFB")
                        .setProvider("BC")
                        .build(passwordText.toCharArray());
                JcaPEMWriter keyWriter = new JcaPEMWriter(new FileWriter(outputDir + File.separator + aliasText + "_SignedKey.pem"));
                keyWriter.writeObject(keyPair.getPrivate(), encryptor);
                keyWriter.close();

                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);
                keyStore.setKeyEntry(aliasText, keyPair.getPrivate(), passwordText.toCharArray(), new java.security.cert.Certificate[]{certificate});

                final var keyStoreFileName = outputDir + File.separator + aliasText + "_Keystore.jks";
                final File keyStoreFile = new File(keyStoreFileName);
                FileOutputStream fos = new FileOutputStream(keyStoreFile);
                keyStore.store(fos, passwordText.toCharArray());
                fos.close();
                setCertificateFile(keyStoreFile);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setHeaderText("Certificate created successfully.");
                alert.setContentText("Your certificate was created successfully. \n It can be found under: " + outputDir.toFile().getAbsolutePath());
                alert.showAndWait();
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private void setCertificateFile(File certificateFile) {
        this.certificateFile = certificateFile;
        // hier noch label ändern
    }

}
