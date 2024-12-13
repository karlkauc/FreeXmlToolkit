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

package org.fxt.freexmltoolkit;

import org.apache.xml.security.Init;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.stax.ext.XMLSecurityProperties;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jcajce.BCFKSLoadStoreParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SignaturTest {
    @Test
    public void t() {
        for (Provider provider : Security.getProviders()) {
            System.out.println("Provider: " + provider.getName());
            for (Provider.Service service : provider.getServices()) {
                if (service.getType().equals("KeyPairGenerator")) {
                    System.out.println("  Algorithm: " + service.getAlgorithm());
                }
            }
        }

        for (var x : BCFKSLoadStoreParameter.SignatureAlgorithm.values()) {
            System.out.println("x.name() = " + x.name());

        }
    }

    @Test
    public void createNewSignatureFile() throws NoSuchAlgorithmException, NoSuchProviderException, CertificateException, OperatorCreationException, IOException, KeyStoreException {
        Security.addProvider(new BouncyCastleProvider());

        // Schlüsselpaar generieren
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Zertifikatsinformationen festlegen
        long now = System.currentTimeMillis();
        X509v3CertificateBuilder certBuilder = getX509v3CertificateBuilder(now, keyPair);

        // Zertifikat signieren
        // Original: SHA256WithRSA
        // System.out.println("BCFKSLoadStoreParameter.SignatureAlgorithm.SHA512withRSA.toString() = " + BCFKSLoadStoreParameter.SignatureAlgorithm.SHA512withRSA.toString());
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA512withRSA").build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(contentSigner));

        // Zertifikat anzeigen
        System.out.println("Zertifikat: " + certificate);

        // Zertifikat und Schlüssel speichern
        // kann auch die Endung .cer haben
        try (JcaPEMWriter certWriter = new JcaPEMWriter(new FileWriter("meinZertifikat.pem"))) {
            certWriter.writeObject(certificate);
        } catch (IOException e) {
            e.printStackTrace();
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
        char[] password = "deinPasswort".toCharArray();
        var encryptor = new JcePEMEncryptorBuilder("AES-256-CFB")
                .setProvider("BC")
                .build(password);

        try (JcaPEMWriter keyWriter = new JcaPEMWriter(new FileWriter("meinVerschluesselterSchluessel.pem"))) {
            keyWriter.writeObject(keyPair.getPrivate(), encryptor);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // KeyStore erstellen und initialisieren
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        // Privaten Schlüssel und Zertifikat in den KeyStore laden
        char[] password2 = "deinPasswort".toCharArray();
        keyStore.setKeyEntry("meinAlias", keyPair.getPrivate(), password2, new java.security.cert.Certificate[]{certificate});

        // KeyStore in eine Datei speichern
        try (FileOutputStream fos = new FileOutputStream("meinKeystore.jks")) {
            keyStore.store(fos, password);
        }

        System.out.println("Zertifikat und Schlüssel wurden gespeichert.");
    }

    private static @NotNull X509v3CertificateBuilder getX509v3CertificateBuilder(long now, KeyPair keyPair) {
        Date startDate = new Date(now);
        Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 Jahr gültig
        BigInteger serialNumber = BigInteger.valueOf(now); // Seriennummer

        // X.500 Name für das Zertifikat
        X500Name issuer = new X500Name("CN=MeinZertifikat");
        X500Name subject = issuer;
        // "CN=cn, O=o, L=L, ST=il, C= c"

        // Zertifikat erstellen
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuer, serialNumber, startDate, endDate, subject, keyPair.getPublic());
        return certBuilder;
    }

    @Test
    public void createSignature() {
        try {
            // Initialisiere die Bibliothek
            Init.init();

            // Lade das bestehende XML-Dokument
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new FileInputStream("src/test/resources/EAM_7290691_20240829_FUND_101.xml"));

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
            System.out.println(xmlString);

            // Erstelle Transforms für die Signatur
            Transforms transforms = new Transforms(doc);
            transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
            transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
            sig.addDocument("", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);

            // Lade den privaten Schlüssel und das Zertifikat
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream("src/test/resources/mykeystore.jks"), "keystorepassword".toCharArray());
            PrivateKey privateKey = (PrivateKey) ks.getKey("mykey", "keystorepassword".toCharArray()); // keypassword
            X509Certificate cert = (X509Certificate) ks.getCertificate("mykey");

            // Füge KeyInfo hinzu
            sig.addKeyInfo(cert);
            sig.addKeyInfo(cert.getPublicKey());

            // Signiere das Dokument
            sig.sign(privateKey);

            // Speichere das signierte Dokument
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer trans = tf.newTransformer();
            trans.transform(new DOMSource(doc), new StreamResult(new FileOutputStream("signed_document.xml")));

            System.out.println("Das Dokument wurde erfolgreich signiert.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Test
    public void verifySignature() {
        try {
            // Initialisiere die Bibliothek
            Init.init();

            // Lade die XML-Datei
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new FileInputStream("signed_document.xml"));

            // Finde das Signature-Element
            Element sigElement = (Element) doc.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature").item(0);

            // Erstelle ein XMLSignature-Objekt
            XMLSignature signature = new XMLSignature(sigElement, "");

            // Lade den öffentlichen Schlüssel (z.B. aus einer Zertifikatsdatei)
            FileInputStream fis = new FileInputStream("src/test/resources/public_key.cer");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
            PublicKey publicKey = cert.getPublicKey();

            XMLSecurityProperties properties = new XMLSecurityProperties();

            // Überprüfe die Signatur
            boolean isValid = signature.checkSignatureValue(publicKey);

            if (isValid) {
                System.out.println("Die Signatur ist gültig.");
            } else {
                System.out.println("Die Signatur ist ungültig.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
