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

package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.w3c.dom.Document;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SignatureService {

    private final static Logger logger = LogManager.getLogger(SignatureService.class);

    public File signDocument(File documentToSign,
                             File keystore, String keystorePassword,
                             String alias, String aliasPassword,
                             String outputFileName) {
        try {
            // Lade das unsignierte XML-Dokument
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(documentToSign);

            // Lade den privaten Schlüssel und das Zertifikat aus dem KeyStore
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(keystore), keystorePassword.toCharArray());
            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, aliasPassword.toCharArray());
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

            // Erstelle die XMLSignatureFactory
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

            // Erstelle die Referenz
            Reference ref = fac.newReference("", fac.newDigestMethod(DigestMethod.SHA3_512, null),
                    java.util.Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null, null);

            // Erstelle SignedInfo
            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                    java.util.Collections.singletonList(ref));

            // Erstelle KeyInfo
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            KeyValue kv = kif.newKeyValue(cert.getPublicKey());
            X509Data x509Data = kif.newX509Data(java.util.Collections.singletonList(cert));
            KeyInfo ki = kif.newKeyInfo(java.util.Arrays.asList(kv, x509Data));

            // Erstelle die XML-Signatur
            XMLSignature signature = fac.newXMLSignature(si, ki);

            // Signiere das Dokument
            DOMSignContext dsc = new DOMSignContext(privateKey, doc.getDocumentElement());
            signature.sign(dsc);

            // Speichere das signierte Dokument
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer trans = tf.newTransformer();

            File outputFile = new File(outputFileName);
            trans.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(outputFile));

            return outputFile;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    public boolean isSignatureValid(File signedFile, File keyStore, String alias, String password) {
        try {
            // Lade das signierte XML-Dokument
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(signedFile);

            // Lade das Zertifikat aus dem KeyStore
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(keyStore), password.toCharArray());
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

            // Erstelle die XMLSignatureFactory
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

            // Finde das Signature-Element
            org.w3c.dom.NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (nl.getLength() == 0) {
                throw new Exception("Kein Signature-Element gefunden");
            }

            // Erstelle den DOMValidateContext
            DOMValidateContext valContext = new DOMValidateContext(cert.getPublicKey(), nl.item(0));

            // Untersuche die Signatur
            XMLSignature signature = fac.unmarshalXMLSignature(valContext);

            // Überprüfe die Signatur
            boolean isValid = signature.validate(valContext);

            // Ausgabe des Ergebnisses
            if (isValid) {
                System.out.println("Die Signatur ist gültig.");
            } else {
                System.out.println("Die Signatur ist ungültig.");
            }
            return isValid;

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return false;
    }


    public File createNewKeystoreFile(X500NameBuilder x500NameBuilder, String alias, String keystorePassword, String aliasPassword) {
        try {
            Security.addProvider(new BouncyCastleProvider());

            final Path outputDir = Paths.get("certs", alias);
            Files.createDirectories(outputDir);

            // Schlüsselpaar generieren
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
            keyPairGenerator.initialize(2048, new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Zertifikatsinformationen festlegen
            long now = System.currentTimeMillis();
            var startDate = new Date(now);
            Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 Jahr gültig
            BigInteger serialNumber = BigInteger.valueOf(now); // Seriennummer

            // X.500 Name für das Zertifikat
            X500Name issuer = new X500Name("CN=FreeXmlToolkit");
            if (x500NameBuilder != null) {
                issuer = x500NameBuilder.build();
            }
            X500Name subject = issuer;

            // Zertifikat erstellen
            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuer, serialNumber, startDate, endDate, subject,
                    keyPair.getPublic());
            // Zertifikat signieren
            // Original: SHA256WithRSA
            // System.out.println("BCFKSLoadStoreParameter.SignatureAlgorithm.SHA512withRSA.toString() = " + BCFKSLoadStoreParameter.SignatureAlgorithm.SHA512withRSA.toString());
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA512withRSA").build(keyPair.getPrivate());
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certBuilder.build(contentSigner));

            // Zertifikat anzeigen
            logger.debug("Zertifikat: {}", certificate);

            // Zertifikat und Schlüssel speichern
            // kann auch die Endung .cer haben
            try (JcaPEMWriter certWriter = new JcaPEMWriter(new FileWriter("meinZertifikat.pem"))) {
                certWriter.writeObject(certificate);
            } catch (IOException e) {
                logger.error(e.getMessage());
            }

            /*
            ohne passwort:
            // Zertifikat und Schlüssel speichern
            try (JcaPEMWriter certWriter = new JcaPEMWriter(new FileWriter("meinZertifikat.pem"));
                 JcaPEMWriter keyWriter = new JcaPEMWriter(new FileWriter("meinSchluessel.pem"))) {
                certWriter.writeObject(certificate);
                keyWriter.writeObject(keyPair.getPrivate());
            } catch (IOException e) {
                e.printStackTrace();
            }
             */

            // Privaten Schlüssel mit AES verschlüsseln und speichern
            var encryptor = new JcePEMEncryptorBuilder("AES-256-CFB")
                    .setProvider("BC")
                    .build(keystorePassword.toCharArray());

            try (JcaPEMWriter keyWriter = new JcaPEMWriter(new FileWriter("meinVerschluesselterSchluessel.pem"))) {
                keyWriter.writeObject(keyPair.getPrivate(), encryptor);
            } catch (IOException e) {
                logger.error(e.getMessage());
            }

            // KeyStore erstellen und initialisieren
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);

            // Privaten Schlüssel und Zertifikat in den KeyStore laden
            keyStore.setKeyEntry(alias, keyPair.getPrivate(), aliasPassword.toCharArray(), new java.security.cert.Certificate[]{certificate});

            // KeyStore in eine Datei speichern
            File outputFile = new File(outputDir + File.separator + alias + "_KeyStore.jks");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                keyStore.store(fos, keystorePassword.toCharArray());
            }

            logger.debug("Zertifikat und Schlüssel wurden gespeichert.");
            return outputFile;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }

}
