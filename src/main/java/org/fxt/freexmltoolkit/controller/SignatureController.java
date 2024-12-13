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
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xml.security.Init;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;

public class SignatureController {
    private MainController parentController;

    @FXML
    TextField commonName, organizationUnit, organizationName, localityName, streetName, country, email, alias;

    @FXML
    Button loadCertificate, loadXmlFile;

    File certificateFile, xmlFile;
    FileChooser fileChooserCertificate = new FileChooser();
    FileChooser fileChooserXMl = new FileChooser();

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

        FileChooser.ExtensionFilter certFilter = new FileChooser.ExtensionFilter("Certificate Files", "*.pem", "*.jks");
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
                Security.addProvider(new BouncyCastleProvider());
                final Path outputDir = Paths.get("certs", aliasText);
                Files.createDirectories(outputDir);

                final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
                keyPairGenerator.initialize(2048, new SecureRandom());
                final KeyPair keyPair = keyPairGenerator.generateKeyPair();

                final long now = System.currentTimeMillis();
                final Date startDate = new Date(now);
                final Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 Jahr gültig
                final BigInteger serialNumber = BigInteger.valueOf(now); // Seriennummer

                // X.500 Name für das Zertifikat
                final X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
                nameBuilder.addRDN(BCStyle.CN, commonName.getText());
                nameBuilder.addRDN(BCStyle.OU, organizationUnit.getText());
                nameBuilder.addRDN(BCStyle.O, organizationName.getText());
                nameBuilder.addRDN(BCStyle.L, localityName.getText());
                nameBuilder.addRDN(BCStyle.ST, streetName.getText());
                nameBuilder.addRDN(BCStyle.C, country.getText());
                nameBuilder.addRDN(BCStyle.EmailAddress, email.getText());

                final X500Name issuer = nameBuilder.build();
                final X500Name subject = issuer;

                final X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuer, serialNumber, startDate, endDate, subject, keyPair.getPublic());

                final ContentSigner contentSigner = new JcaContentSignerBuilder("SHA512withRSA").build(keyPair.getPrivate());
                final X509Certificate certificate = new JcaX509CertificateConverter()
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

    @FXML
    public void signDocument() {
        logger.debug("Signing Document");
        try {
            Init.init();

            // Lade das bestehende XML-Dokument
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new FileInputStream("src/test/resources/test01.xml"));

            // Erstelle eine XMLSignature-Instanz
            XMLSignature sig = new XMLSignature(doc, "", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1);

            // Füge die Signatur dem Dokument hinzu
            Element root = doc.getDocumentElement();
            root.appendChild(sig.getElement());

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            // initialize StreamResult with File object to save to file
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(sig.getElement());
            transformer.transform(source, result);
            String xmlString = result.getWriter().toString();
            //  System.out.println(xmlString);

            // Erstelle Transforms für die Signatur
            Transforms transforms = new Transforms(doc);
            transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
            transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
            sig.addDocument("", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);

            // Pfad zur PEM-Datei und Passwort
            String pemFilePath = "release/examples/certs/karl/karl_SignedKey.pem";
            String password = "123";

            // PEMParser initialisieren
            PEMParser pemParser = new PEMParser(new FileReader(pemFilePath));
            Object object = pemParser.readObject();
            pemParser.close();

            // Entschlüsseln des privaten Schlüssels
            Security.addProvider(new BouncyCastleProvider());
            JcePEMDecryptorProviderBuilder decryptorProviderBuilder = new JcePEMDecryptorProviderBuilder();
            var decryptorProvider = decryptorProviderBuilder.build(password.toCharArray());
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            PrivateKey privateKey = converter.getPrivateKey(((org.bouncycastle.openssl.PEMEncryptedKeyPair) object).decryptKeyPair(decryptorProvider).getPrivateKeyInfo());
            PublicKey publicKey = converter.getPublicKey(((org.bouncycastle.openssl.PEMEncryptedKeyPair) object).decryptKeyPair(decryptorProvider).getPublicKeyInfo());

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            Reference ref = fac.newReference("", fac.newDigestMethod(DigestMethod.SHA256, null), Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null, null);
            SignedInfo si = fac.newSignedInfo(fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null), fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null), Collections.singletonList(ref));
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            KeyValue kv = kif.newKeyValue(publicKey);
            KeyInfo ki = kif.newKeyInfo(Collections.singletonList(kv));
            DOMSignContext dsc = new DOMSignContext(privateKey, doc.getDocumentElement());
            javax.xml.crypto.dsig.XMLSignature signature = fac.newXMLSignature(si, ki);
            signature.sign(dsc);

            // Speichere das signierte Dokument
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer trans = tf.newTransformer();
            File outputfile = new File("signed_document.xml");
            trans.transform(new DOMSource(doc), new StreamResult(new FileOutputStream(outputfile)));
            System.out.println("Das Dokument wurde erfolgreich signiert.");
            System.out.println("outputfile Size = " + outputfile.length());

        } catch (Exception e) {
            logger.error(e.getMessage());
        }

    }

    private void setCertificateFile(File certificateFile) {
        this.certificateFile = certificateFile;
        // hier noch label ändern
    }

}
