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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.crypto.*;
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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Service class for handling XML document signatures.
 * This class provides methods to sign, validate, and create keystores for XML signatures.
 */
public class SignatureService {

    private static final Logger logger = LogManager.getLogger(SignatureService.class);

    // --- KRYPTOGRAFISCHE KONSTANTEN ---
    public static final int KEY_SIZE = 2048;
    public static final String KEY_ALGORITHM = "RSA";
    public static final String KEYSTORE_TYPE = "JKS";
    public static final String SECURITY_PROVIDER = "BC";

    // Algorithmen für die Zertifikat- und XML-Signatur
    public static final String SIGNATURE_ALGORITHM_BC = "SHA256withRSA"; // BouncyCastle-Name
    public static final String SIGNATURE_METHOD_XML = SignatureMethod.RSA_SHA256; // JSR 105 Name
    public static final String DIGEST_METHOD_XML = DigestMethod.SHA256; // JSR 105 Name

    // Algorithmus für die Verschlüsselung des privaten Schlüssels im PEM-Format
    public static final String PEM_ENCRYPT_ALGORITHM = "AES-256-CFB";

    private final DocumentBuilderFactory dbf;

    public SignatureService() {
        // Bouncy Castle Provider hinzufügen, falls noch nicht geschehen
        if (Security.getProvider(SECURITY_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
    }

    /**
     * Signs an XML document using a private key from a keystore.
     *
     * @param documentToSign   the XML document to sign
     * @param keystore         the keystore file containing the private key
     * @param keystorePassword the password for the keystore
     * @param alias            the alias of the key in the keystore
     * @param aliasPassword    the password for the alias
     * @param outputFileName   the name of the output file to save the signed document
     * @return the signed XML document as a File
     * @throws SignatureServiceException if signing fails due to configuration or crypto errors.
     */
    public File signDocument(File documentToSign, File keystore, String keystorePassword, String alias, String aliasPassword, String outputFileName) {
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(documentToSign);

            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            try (FileInputStream fis = new FileInputStream(keystore)) {
                ks.load(fis, keystorePassword.toCharArray());
            }

            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, aliasPassword.toCharArray());
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (privateKey == null || cert == null) {
                throw new KeyStoreException("Key or certificate for alias '" + alias + "' not found in keystore.");
            }

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

            Reference ref = fac.newReference("", fac.newDigestMethod(DIGEST_METHOD_XML, null),
                    Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null, null);

            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(SIGNATURE_METHOD_XML, null),
                    Collections.singletonList(ref));

            KeyInfoFactory kif = fac.getKeyInfoFactory();
            KeyValue kv = kif.newKeyValue(cert.getPublicKey());
            X509Data x509Data = kif.newX509Data(Collections.singletonList(cert));
            KeyInfo ki = kif.newKeyInfo(List.of(kv, x509Data));

            DOMSignContext dsc = new DOMSignContext(privateKey, doc.getDocumentElement());
            XMLSignature signature = fac.newXMLSignature(si, ki);
            signature.sign(dsc);

            File outputFile = new File(outputFileName);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer trans = tf.newTransformer();
                trans.transform(new DOMSource(doc), new StreamResult(fos));
            }

            logger.info("Document successfully signed and saved to '{}'", outputFile.getAbsolutePath());
            return outputFile;

        } catch (ParserConfigurationException | SAXException | IOException | KeyStoreException |
                 NoSuchAlgorithmException |
                 UnrecoverableKeyException | InvalidAlgorithmParameterException | MarshalException |
                 XMLSignatureException | TransformerException | CertificateException | KeyException e) {
            logger.error("Failed to sign the document.", e);
            throw new SignatureServiceException("Failed to sign the document: " + e.getMessage(), e);
        }
    }

    /**
     * Validates the signature of a signed XML document.
     *
     * @param signedFile the signed XML document
     * @return true if the signature is valid, false otherwise
     * @throws SignatureServiceException if validation fails due to configuration or crypto errors.
     */
    public boolean isSignatureValid(File signedFile) {
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(signedFile);

            NodeList signatureNodeList = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (signatureNodeList.getLength() == 0) {
                logger.warn("Cannot find Signature element in the document '{}'", signedFile.getName());
                return false;
            }
            Node signatureNode = signatureNodeList.item(0);

            // KORREKTUR: Strikte Prüfung auf schwache Algorithmen.
            // Wenn ein schwacher Algorithmus gefunden wird, wird die Validierung sofort abgebrochen.
            if (isWeakAlgorithmUsed(signatureNode)) {
                throw new SignatureServiceException("Weak algorithm detected. Validation aborted for security reasons.", new SecurityException("Usage of SHA1 is forbidden."));
            }

            DOMValidateContext valContext = new DOMValidateContext(new X509KeySelector(), signatureNode);

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            XMLSignature signature = fac.unmarshalXMLSignature(valContext);

            boolean coreValidity = signature.validate(valContext);
            if (coreValidity) {
                logger.info("Signature in '{}' is valid.", signedFile.getName());
            } else {
                logger.warn("Signature in '{}' is INVALID.", signedFile.getName());
            }
            return coreValidity;

        } catch (MarshalException | XMLSignatureException | ParserConfigurationException | SAXException |
                 IOException e) {
            // Fange die Exception, die durch die Sicherheitsrichtlinie ausgelöst wird, und gib eine klare Meldung aus.
            if (e.getCause() instanceof SecurityException) {
                logger.error("Validation failed due to security policy: {}", e.getCause().getMessage(), e);
                throw new SignatureServiceException("Validation forbidden by security policy: " + e.getCause().getMessage(), e);
            }
            logger.error("Failed to validate the signature.", e);
            throw new SignatureServiceException("Failed to validate the signature: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to inspect the DOM and check for weak signature or digest algorithms.
     */
    private boolean isWeakAlgorithmUsed(Node signatureNode) {
        if (signatureNode instanceof Element) {
            // Check SignatureMethod
            NodeList signatureMethodNodes = ((Element) signatureNode).getElementsByTagNameNS(XMLSignature.XMLNS, "SignatureMethod");
            if (signatureMethodNodes.getLength() > 0) {
                String sigMethodAlg = ((Element) signatureMethodNodes.item(0)).getAttribute("Algorithm");
                if (SignatureMethod.RSA_SHA1.equals(sigMethodAlg)) {
                    logger.error("Weak signature algorithm found: {}", sigMethodAlg);
                    return true;
                }
            }
            // Check DigestMethod within Reference
            NodeList digestMethodNodes = ((Element) signatureNode).getElementsByTagNameNS(XMLSignature.XMLNS, "DigestMethod");
            for (int i = 0; i < digestMethodNodes.getLength(); i++) {
                String digestMethodAlg = ((Element) digestMethodNodes.item(i)).getAttribute("Algorithm");
                if (DigestMethod.SHA1.equals(digestMethodAlg)) {
                    logger.error("Weak digest algorithm found: {}", digestMethodAlg);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates a new keystore file with a self-signed certificate and associated PEM files.
     *
     * @param x500NameBuilder  the X500NameBuilder for the certificate's subject.
     * @param alias            the alias for the key in the keystore.
     * @param keystorePassword the password for the keystore and the encrypted private key PEM.
     * @param aliasPassword    the password for the key entry within the keystore.
     * @return the created keystore file.
     * @throws SignatureServiceException if keystore creation fails.
     */
    public File createNewKeystoreFile(X500NameBuilder x500NameBuilder, String alias, String keystorePassword, String aliasPassword) {
        try {
            Path outputDir = Paths.get("certs", alias);
            Files.createDirectories(outputDir);

            KeyPair keyPair = generateKeyPair();
            X509Certificate certificate = createSelfSignedCertificate(keyPair, x500NameBuilder);

            writePemAndSummaryFiles(outputDir, alias, certificate, keyPair, keystorePassword, aliasPassword);

            return createAndSaveKeyStore(outputDir, alias, keyPair, certificate, aliasPassword, keystorePassword);

        } catch (Exception e) {
            logger.error("Failed to create new keystore.", e);
            throw new SignatureServiceException("Failed to create new keystore: " + e.getMessage(), e);
        }
    }

    private KeyPair generateKeyPair() throws NoSuchProviderException, NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM, SECURITY_PROVIDER);
        keyPairGenerator.initialize(KEY_SIZE, new SecureRandom());
        logger.debug("Generating {} key pair with size {}", KEY_ALGORITHM, KEY_SIZE);
        return keyPairGenerator.generateKeyPair();
    }

    private X509Certificate createSelfSignedCertificate(KeyPair keyPair, X500NameBuilder subjectNameBuilder) throws Exception {
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 year validity
        BigInteger serialNumber = BigInteger.valueOf(now);

        X500Name subject = (subjectNameBuilder != null) ? subjectNameBuilder.build() : new X500Name("CN=FreeXmlToolkitDefault");
        // For self-signed, issuer is the same as subject
        X500Name issuer = subject;

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuer, serialNumber, startDate, endDate, subject, keyPair.getPublic());
        ContentSigner contentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM_BC).build(keyPair.getPrivate());

        logger.debug("Creating self-signed certificate for subject: {}", subject);
        return new JcaX509CertificateConverter().setProvider(SECURITY_PROVIDER).getCertificate(certBuilder.build(contentSigner));
    }

    private void writePemAndSummaryFiles(Path outputDir, String alias, X509Certificate certificate, KeyPair keyPair, String keystorePassword, String aliasPassword) throws IOException {
        // Write summary file
        Path summaryFile = outputDir.resolve("summary.txt");
        String summaryText = String.format(
                "Keystore Alias: %s%nKeystore Password: %s%nAlias Password: %s%n%nCertificate Details:%n%s",
                alias, keystorePassword, aliasPassword, certificate.toString()
        );
        Files.writeString(summaryFile, summaryText, StandardCharsets.UTF_8);

        // Write public key (certificate) to PEM
        Path certFile = outputDir.resolve(alias + "_publicKey.pem");
        try (JcaPEMWriter certWriter = new JcaPEMWriter(new FileWriter(certFile.toFile()))) {
            certWriter.writeObject(certificate);
        }

        // Write encrypted private key to PEM
        Path keyFile = outputDir.resolve(alias + "_privateKey.pem");
        JcePEMEncryptorBuilder encryptorBuilder = new JcePEMEncryptorBuilder(PEM_ENCRYPT_ALGORITHM).setProvider(SECURITY_PROVIDER);
        try (JcaPEMWriter keyWriter = new JcaPEMWriter(new FileWriter(keyFile.toFile()))) {
            keyWriter.writeObject(keyPair.getPrivate(), encryptorBuilder.build(keystorePassword.toCharArray()));
        }
        logger.info("PEM files and summary written to '{}'", outputDir.toAbsolutePath());
    }

    private File createAndSaveKeyStore(Path outputDir, String alias, KeyPair keyPair, X509Certificate certificate, String aliasPassword, String keystorePassword) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        keyStore.load(null, null); // Initialize new, empty keystore

        keyStore.setKeyEntry(alias, keyPair.getPrivate(), aliasPassword.toCharArray(), new java.security.cert.Certificate[]{certificate});

        File outputFile = outputDir.resolve(alias + "_KeyStore.jks").toFile();
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            keyStore.store(fos, keystorePassword.toCharArray());
        }
        logger.info("Keystore successfully created at '{}'", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * Custom exception for service-specific errors.
     */
    public static class SignatureServiceException extends RuntimeException {
        public SignatureServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * A KeySelector that selects the public key from the X509Data within the KeyInfo structure.
     * This implementation trusts the key embedded in the signature. For higher security,
     * one might want to validate the certificate against a truststore.
     */
    private static class X509KeySelector extends KeySelector {
        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context) throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("Null KeyInfo object!");
            }

            for (Object keyInfoEntry : keyInfo.getContent()) {
                if (keyInfoEntry instanceof X509Data) {
                    for (Object x509DataEntry : ((X509Data) keyInfoEntry).getContent()) {
                        if (x509DataEntry instanceof X509Certificate) {
                            final PublicKey publicKey = ((X509Certificate) x509DataEntry).getPublicKey();
                            // Make sure the algorithm is compatible
                            if (algEquals(method.getAlgorithm(), publicKey.getAlgorithm())) {
                                return new SimpleKeySelectorResult(publicKey);
                            }
                        }
                    }
                }
            }
            throw new KeySelectorException("No key found!");
        }

        private boolean algEquals(String sigAlg, String keyAlg) {
            // Allow RSA keys for SHA256 and SHA512 signatures
            return (sigAlg.equalsIgnoreCase(SignatureMethod.RSA_SHA256) ||
                    sigAlg.equalsIgnoreCase(SignatureMethod.RSA_SHA512)) &&
                    keyAlg.equalsIgnoreCase("RSA");
            // Die Unterstützung für den unsicheren RSA-SHA1-Algorithmus wurde entfernt.
            // Add other algorithm compatibility checks if needed
        }
    }

    /**
     * A simple implementation of KeySelectorResult.
     */
    private static class SimpleKeySelectorResult implements KeySelectorResult {
        private final Key key;

        SimpleKeySelectorResult(Key key) {
            this.key = key;
        }

        @Override
        public Key getKey() {
            return key;
        }
    }
}