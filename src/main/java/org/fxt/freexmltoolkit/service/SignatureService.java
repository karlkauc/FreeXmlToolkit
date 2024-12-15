package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xml.security.Init;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.bouncycastle.asn1.x500.X500Name;
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
import org.bouncycastle.operator.OperatorCreationException;
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
import java.util.Collections;
import java.util.Date;

public class SignatureService {

    private final static Logger logger = LogManager.getLogger(SignatureService.class);

    public File signDocument(File documentToSign,
                             File pem, String password,
                             String outputFileName) {
        try {
            Init.init();

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new FileInputStream(documentToSign));

            XMLSignature signature = new XMLSignature(doc, "", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1);

            Element root = doc.getDocumentElement();
            root.appendChild(signature.getElement());

            Transforms transforms = new Transforms(doc);
            transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
            transforms.addTransform(Transforms.TRANSFORM_C14N11_OMIT_COMMENTS);
            signature.addDocument("", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);

            PEMParser pemParser = new PEMParser(new FileReader(pem));
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
            var signature2 = fac.newXMLSignature(si, ki);
            signature2.sign(dsc);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer trans = tf.newTransformer();
            File outputfile = new File(outputFileName);

            trans.transform(new DOMSource(doc), new StreamResult(new FileOutputStream(outputfile)));
            System.out.println("Das Dokument wurde erfolgreich signiert.");
            System.out.println("outputfile Size = " + outputfile.length());

            return outputfile;
        } catch (Exception e) {
            System.out.println("e.getMessage() = " + e.getMessage());
        }
        return null;
    }

    public boolean isSignatureValid(File signedFile, File cerFile) {
        try {
            // Initialisiere die Bibliothek
            Init.init();

            // Lade die XML-Datei
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new FileInputStream(signedFile));

            // Finde das Signature-Element
            Element sigElement = (Element) doc.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature").item(0);

            // Erstelle ein XMLSignature-Objekt
            XMLSignature signature = new XMLSignature(sigElement, "");

            // Lade den öffentlichen Schlüssel (z.B. aus einer Zertifikatsdatei)
            FileInputStream fis = new FileInputStream(cerFile);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
            PublicKey publicKey = cert.getPublicKey();

            // XMLSecurityProperties properties = new XMLSecurityProperties();

            // Überprüfe die Signatur
            boolean isValid = signature.checkSignatureValue(publicKey);

            if (isValid) {
                System.out.println("Die Signatur ist gültig.");
                return true;
            } else {
                System.out.println("Die Signatur ist ungültig.");
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return false;
    }


    public void createNewSignatureFile() throws NoSuchAlgorithmException, NoSuchProviderException, CertificateException, OperatorCreationException, IOException, KeyStoreException {
        Security.addProvider(new BouncyCastleProvider());

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
        X500Name issuer = new X500Name("CN=MeinZertifikat");
        X500Name subject = issuer;
        // "CN=cn, O=o, L=L, ST=il, C= c"

        // Zertifikat erstellen
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuer, serialNumber, startDate, endDate, subject, keyPair.getPublic());
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
            logger.error(e.getMessage());
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

}
