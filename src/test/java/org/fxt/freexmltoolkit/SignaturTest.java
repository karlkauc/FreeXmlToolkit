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
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class SignaturTest {

    @Test
    public void testSignatur() {
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

    @Test
    public void testSignatur2() {
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
}
