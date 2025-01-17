
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
        import java.nio.charset.Charset;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.Paths;
        import java.security.*;
        import java.security.cert.X509Certificate;
        import java.util.Date;

        /**
         * Service class for handling XML document signatures.
         */
        public class SignatureService {

            private final static Logger logger = LogManager.getLogger(SignatureService.class);
            public static final int KEY_SIZE = 2048;
            public static final String SIGNATURE_ALGORITHM = "SHA512withRSA";
            public static final String PEM_ENCRYPT_ALGORITHM = "AES-256-CFB";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

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
             */
            public File signDocument(File documentToSign,
                                     File keystore, String keystorePassword,
                                     String alias, String aliasPassword,
                                     String outputFileName) {
                try {
                    // Load the unsigned XML document
                    dbf.setNamespaceAware(true);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(documentToSign);

                    // Load the private key and certificate from the KeyStore
                    KeyStore ks = KeyStore.getInstance("JKS");
                    ks.load(new FileInputStream(keystore), keystorePassword.toCharArray());

                    PrivateKey privateKey = (PrivateKey) ks.getKey(alias, aliasPassword.toCharArray());
                    X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

                    // Create the XMLSignatureFactory
                    XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

                    // Create the Reference
                    Reference ref = fac.newReference("", fac.newDigestMethod(DigestMethod.SHA3_512, null),
                            java.util.Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null, null);

                    // Create SignedInfo
                    SignedInfo si = fac.newSignedInfo(
                            fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                            fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                            java.util.Collections.singletonList(ref));

                    // Create KeyInfo
                    KeyInfoFactory kif = fac.getKeyInfoFactory();
                    KeyValue kv = kif.newKeyValue(cert.getPublicKey());
                    X509Data x509Data = kif.newX509Data(java.util.Collections.singletonList(cert));
                    KeyInfo ki = kif.newKeyInfo(java.util.Arrays.asList(kv, x509Data));

                    // Create the XML signature
                    XMLSignature signature = fac.newXMLSignature(si, ki);

                    // Sign the document
                    DOMSignContext dsc = new DOMSignContext(privateKey, doc.getDocumentElement());
                    signature.sign(dsc);

                    // Save the signed document
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

            /**
             * Validates the signature of a signed XML document.
             *
             * @param signedFile the signed XML document
             * @param keyStore   the keystore file containing the certificate
             * @param alias      the alias of the certificate in the keystore
             * @param password   the password for the keystore
             * @return true if the signature is valid, false otherwise
             */
            public boolean isSignatureValid(File signedFile, File keyStore, String alias, String password) {
                try {
                    // Load the signed XML document
                    dbf.setNamespaceAware(true);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(signedFile);

                    // Load the certificate from the KeyStore
                    KeyStore ks = KeyStore.getInstance("JKS");
                    ks.load(new FileInputStream(keyStore), password.toCharArray());
                    X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

                    // Create the XMLSignatureFactory
                    XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

                    // Find the Signature element
                    org.w3c.dom.NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
                    if (nl.getLength() == 0) {
                        throw new Exception("No Signature element found");
                    }

                    // Create the DOMValidateContext
                    DOMValidateContext valContext = new DOMValidateContext(cert.getPublicKey(), nl.item(0));

                    // Unmarshal the signature
                    XMLSignature signature = fac.unmarshalXMLSignature(valContext);

                    // Validate the signature
                    boolean isValid = signature.validate(valContext);

                    // Output the result
                    if (isValid) {
                        System.out.println("The signature is valid.");
                    } else {
                        System.out.println("The signature is invalid.");
                    }
                    return isValid;

                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
                return false;
            }

            /**
             * Creates a new keystore file with a self-signed certificate.
             *
             * @param x500NameBuilder  the X500NameBuilder for the certificate
             * @param alias            the alias for the key in the keystore
             * @param keystorePassword the password for the keystore
             * @param aliasPassword    the password for the alias
             * @return the created keystore file
             */
            public File createNewKeystoreFile(X500NameBuilder x500NameBuilder,
                                              String alias,
                                              String keystorePassword,
                                              String aliasPassword) {
                try {
                    Security.addProvider(new BouncyCastleProvider());

                    final Path outputDir = Paths.get("certs", alias);
                    Files.createDirectories(outputDir);

                    // Generate key pair
                    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
                    keyPairGenerator.initialize(KEY_SIZE, new SecureRandom());
                    KeyPair keyPair = keyPairGenerator.generateKeyPair();

                    // Set certificate information
                    long now = System.currentTimeMillis();
                    var startDate = new Date(now);
                    Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000); // Valid for 1 year
                    BigInteger serialNumber = BigInteger.valueOf(now); // Serial number

                    // X.500 Name for the certificate
                    X500Name issuer = new X500Name("CN=FreeXmlToolkit");
                    if (x500NameBuilder != null) {
                        issuer = x500NameBuilder.build();
                    }
                    X500Name subject = issuer;

                    // Create certificate
                    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuer, serialNumber, startDate, endDate, subject,
                            keyPair.getPublic());

                    // Sign the certificate
                    ContentSigner contentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.getPrivate());
                    X509Certificate certificate = new JcaX509CertificateConverter()
                            .setProvider("BC")
                            .getCertificate(certBuilder.build(contentSigner));

                    // Save certificate and key
                    File summary = new File(outputDir + File.separator + "summary.txt");
                    String summaryText = "Keystore Alias: " + alias + System.lineSeparator() +
                            "Keystore Password: " + keystorePassword + System.lineSeparator() +
                            "Alias Password: " + aliasPassword + System.lineSeparator() +
                            System.lineSeparator() +
                            "Certificate: " + System.lineSeparator() +
                            certificate;
                    Files.writeString(summary.toPath(), summaryText, Charset.defaultCharset());

                    // Save certificate and key
                    try (JcaPEMWriter certWriter = new JcaPEMWriter(new FileWriter(outputDir + File.separator + alias + "_publicKey.pem"))) {
                        certWriter.writeObject(certificate);
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }

                    final var encryptor = new JcePEMEncryptorBuilder(PEM_ENCRYPT_ALGORITHM)
                            .setProvider("BC")
                            .build(keystorePassword.toCharArray());

                    try (JcaPEMWriter keyWriter = new JcaPEMWriter(new FileWriter(outputDir + File.separator + alias + "_privateKey.pem"))) {
                        keyWriter.writeObject(keyPair.getPrivate(), encryptor);
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }

                    // Create and initialize KeyStore
                    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    keyStore.load(null, null);

                    // Load private key and certificate into KeyStore
                    keyStore.setKeyEntry(alias, keyPair.getPrivate(), aliasPassword.toCharArray(), new java.security.cert.Certificate[]{certificate});

                    // Save KeyStore to a file
                    File outputFile = new File(outputDir + File.separator + alias + "_KeyStore.jks");
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        keyStore.store(fos, keystorePassword.toCharArray());
                    }

                    logger.debug("Certificate and key have been saved.");
                    return outputFile;
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
                return null;
            }

        }
