package org.fxt.freexmltoolkit.service;

import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SignatureService - XML digital signature operations.
 */
class SignatureServiceTest {

    private SignatureService signatureService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        signatureService = new SignatureService();
    }

    @Test
    @DisplayName("Should create new keystore file with certificate")
    void testCreateNewKeystore() {
        // Arrange
        X500NameBuilder nameBuilder = new X500NameBuilder();
        nameBuilder.addRDN(BCStyle.CN, "Test User");
        nameBuilder.addRDN(BCStyle.O, "Test Organization");
        nameBuilder.addRDN(BCStyle.C, "AT");

        String alias = "testkey";
        String keystorePassword = "password123";
        String aliasPassword = "keypass123";

        // Act
        File keystoreFile = signatureService.createNewKeystoreFile(
            nameBuilder,
            alias,
            keystorePassword,
            aliasPassword
        );

        // Assert
        assertNotNull(keystoreFile);
        assertTrue(keystoreFile.exists());
        assertTrue(keystoreFile.getName().endsWith(".jks"));
        assertTrue(keystoreFile.length() > 0);

        // Verify PEM files were created
        File certDir = keystoreFile.getParentFile();
        assertTrue(new File(certDir, alias + "_publicKey.pem").exists());
        assertTrue(new File(certDir, alias + "_privateKey.pem").exists());
        assertTrue(new File(certDir, "summary.txt").exists());
    }

    @Test
    @DisplayName("Should sign XML document")
    void testSignDocument() throws Exception {
        // Arrange - Create keystore first
        X500NameBuilder nameBuilder = new X500NameBuilder();
        nameBuilder.addRDN(BCStyle.CN, "Signer");
        nameBuilder.addRDN(BCStyle.O, "FreeXmlToolkit");
        nameBuilder.addRDN(BCStyle.C, "AT");

        String alias = "signerkey";
        String keystorePassword = "storepass";
        String aliasPassword = "keypass";

        File keystoreFile = signatureService.createNewKeystoreFile(
            nameBuilder,
            alias,
            keystorePassword,
            aliasPassword
        );

        // Create test XML document
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <title>Test Document</title>
                <content>This document will be signed.</content>
            </document>
            """;

        Path xmlFile = tempDir.resolve("test-document.xml");
        Files.writeString(xmlFile, xmlContent);

        Path signedFile = tempDir.resolve("signed-document.xml");

        // Act
        File result = signatureService.signDocument(
            xmlFile.toFile(),
            keystoreFile,
            keystorePassword,
            alias,
            aliasPassword,
            signedFile.toString()
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > xmlFile.toFile().length(), "Signed document should be larger");

        String signedContent = Files.readString(result.toPath());
        assertTrue(signedContent.contains("<Signature"), "Should contain Signature element");
        assertTrue(signedContent.contains("SignatureValue"), "Should contain SignatureValue");
        assertTrue(signedContent.contains("KeyInfo"), "Should contain KeyInfo");
    }

    @Test
    @DisplayName("Should validate valid signed document")
    void testValidateValidSignature() throws Exception {
        // Arrange - Create keystore, sign document
        X500NameBuilder nameBuilder = new X500NameBuilder();
        nameBuilder.addRDN(BCStyle.CN, "Validator Test");
        nameBuilder.addRDN(BCStyle.O, "Test Org");

        String alias = "validatorkey";
        String keystorePassword = "pass123";
        String aliasPassword = "keypass123";

        File keystoreFile = signatureService.createNewKeystoreFile(
            nameBuilder,
            alias,
            keystorePassword,
            aliasPassword
        );

        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <data>
                <item id="1">Value 1</item>
                <item id="2">Value 2</item>
            </data>
            """;

        Path xmlFile = tempDir.resolve("data.xml");
        Path signedFile = tempDir.resolve("data-signed.xml");
        Files.writeString(xmlFile, xmlContent);

        signatureService.signDocument(
            xmlFile.toFile(),
            keystoreFile,
            keystorePassword,
            alias,
            aliasPassword,
            signedFile.toString()
        );

        // Act
        boolean isValid = signatureService.isSignatureValid(signedFile.toFile());

        // Assert
        assertTrue(isValid, "Signature should be valid");
    }

    @Test
    @DisplayName("Should detect invalid signature when document is modified")
    void testDetectModifiedDocument() throws Exception {
        // Arrange - Sign document
        X500NameBuilder nameBuilder = new X500NameBuilder();
        nameBuilder.addRDN(BCStyle.CN, "Tampering Test");

        String alias = "tamperkey";
        String keystorePassword = "pass";
        String aliasPassword = "keypass";

        File keystoreFile = signatureService.createNewKeystoreFile(
            nameBuilder,
            alias,
            keystorePassword,
            aliasPassword
        );

        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <message>Original message</message>
            """;

        Path xmlFile = tempDir.resolve("message.xml");
        Path signedFile = tempDir.resolve("message-signed.xml");
        Files.writeString(xmlFile, xmlContent);

        signatureService.signDocument(
            xmlFile.toFile(),
            keystoreFile,
            keystorePassword,
            alias,
            aliasPassword,
            signedFile.toString()
        );

        // Modify signed document
        String signedContent = Files.readString(signedFile);
        String tamperedContent = signedContent.replace("Original message", "TAMPERED message");
        Files.writeString(signedFile, tamperedContent);

        // Act
        boolean isValid = signatureService.isSignatureValid(signedFile.toFile());

        // Assert
        assertFalse(isValid, "Signature should be invalid after modification");
    }

    @Test
    @DisplayName("Should return false for document without signature")
    void testDocumentWithoutSignature() throws Exception {
        // Arrange
        String unsignedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>No signature here</document>
            """;

        Path unsignedFile = tempDir.resolve("unsigned.xml");
        Files.writeString(unsignedFile, unsignedXml);

        // Act
        boolean isValid = signatureService.isSignatureValid(unsignedFile.toFile());

        // Assert
        assertFalse(isValid, "Should return false for unsigned document");
    }

    @Test
    @DisplayName("Should reject weak SHA1 signature algorithm")
    void testRejectWeakAlgorithm() throws Exception {
        // Arrange - Create a signed document with weak SHA1 algorithm
        // This test documents the security policy - SHA1 is rejected
        String weakSignedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <data>Content</data>
                <Signature xmlns="http://www.w3.org/2000/09/xmldsig#">
                    <SignedInfo>
                        <CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                        <SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/>
                        <Reference URI="">
                            <Transforms>
                                <Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
                            </Transforms>
                            <DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/>
                            <DigestValue>dummyValue==</DigestValue>
                        </Reference>
                    </SignedInfo>
                    <SignatureValue>dummySignature==</SignatureValue>
                    <KeyInfo>
                        <KeyValue>
                            <RSAKeyValue>
                                <Modulus>dummy</Modulus>
                                <Exponent>AQAB</Exponent>
                            </RSAKeyValue>
                        </KeyValue>
                    </KeyInfo>
                </Signature>
            </document>
            """;

        Path weakFile = tempDir.resolve("weak-signed.xml");
        Files.writeString(weakFile, weakSignedXml);

        // Act & Assert
        SignatureService.SignatureServiceException exception = assertThrows(
            SignatureService.SignatureServiceException.class,
            () -> signatureService.isSignatureValid(weakFile.toFile()),
            "Should throw exception for weak algorithm"
        );

        assertTrue(exception.getMessage().contains("security") ||
                   exception.getMessage().contains("SHA1") ||
                   exception.getMessage().contains("Weak"),
                   "Exception should mention security or weak algorithm");
    }

    @Test
    @DisplayName("Should throw exception when signing with invalid keystore password")
    void testInvalidKeystorePassword() throws Exception {
        // Arrange
        X500NameBuilder nameBuilder = new X500NameBuilder();
        nameBuilder.addRDN(BCStyle.CN, "Invalid Password Test");

        String alias = "invalidkey";
        String correctPassword = "correctpass";
        String wrongPassword = "wrongpass";
        String aliasPassword = "keypass";

        File keystoreFile = signatureService.createNewKeystoreFile(
            nameBuilder,
            alias,
            correctPassword,
            aliasPassword
        );

        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <test>data</test>
            """;

        Path xmlFile = tempDir.resolve("test.xml");
        Path signedFile = tempDir.resolve("signed.xml");
        Files.writeString(xmlFile, xmlContent);

        // Act & Assert
        assertThrows(
            SignatureService.SignatureServiceException.class,
            () -> signatureService.signDocument(
                xmlFile.toFile(),
                keystoreFile,
                wrongPassword,  // Wrong password
                alias,
                aliasPassword,
                signedFile.toString()
            ),
            "Should throw exception with wrong keystore password"
        );
    }

    @Test
    @DisplayName("Should throw exception when alias not found in keystore")
    void testAliasNotFound() throws Exception {
        // Arrange
        X500NameBuilder nameBuilder = new X500NameBuilder();
        nameBuilder.addRDN(BCStyle.CN, "Alias Test");

        String correctAlias = "correctalias";
        String wrongAlias = "wrongalias";
        String keystorePassword = "pass";
        String aliasPassword = "keypass";

        File keystoreFile = signatureService.createNewKeystoreFile(
            nameBuilder,
            correctAlias,
            keystorePassword,
            aliasPassword
        );

        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <test>data</test>
            """;

        Path xmlFile = tempDir.resolve("test.xml");
        Path signedFile = tempDir.resolve("signed.xml");
        Files.writeString(xmlFile, xmlContent);

        // Act & Assert
        assertThrows(
            SignatureService.SignatureServiceException.class,
            () -> signatureService.signDocument(
                xmlFile.toFile(),
                keystoreFile,
                keystorePassword,
                wrongAlias,  // Wrong alias
                aliasPassword,
                signedFile.toString()
            ),
            "Should throw exception when alias not found"
        );
    }

    @Test
    @DisplayName("Should handle complex XML structure signing")
    void testSignComplexXml() throws Exception {
        // Arrange
        X500NameBuilder nameBuilder = new X500NameBuilder();
        nameBuilder.addRDN(BCStyle.CN, "Complex XML Test");
        nameBuilder.addRDN(BCStyle.O, "Test Organization");
        nameBuilder.addRDN(BCStyle.OU, "Development");
        nameBuilder.addRDN(BCStyle.L, "Vienna");
        nameBuilder.addRDN(BCStyle.ST, "Vienna");
        nameBuilder.addRDN(BCStyle.C, "AT");

        String alias = "complexkey";
        String keystorePassword = "complex123";
        String aliasPassword = "keycomplex123";

        File keystoreFile = signatureService.createNewKeystoreFile(
            nameBuilder,
            alias,
            keystorePassword,
            aliasPassword
        );

        String complexXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <library xmlns="http://example.com/library">
                <books>
                    <book id="1" category="programming">
                        <title>XML Processing</title>
                        <author>John Doe</author>
                        <year>2023</year>
                        <price currency="EUR">49.99</price>
                    </book>
                    <book id="2" category="security">
                        <title>Digital Signatures</title>
                        <author>Jane Smith</author>
                        <year>2024</year>
                        <price currency="USD">59.99</price>
                    </book>
                </books>
                <metadata>
                    <created>2024-01-01</created>
                    <modified>2024-12-01</modified>
                </metadata>
            </library>
            """;

        Path xmlFile = tempDir.resolve("complex.xml");
        Path signedFile = tempDir.resolve("complex-signed.xml");
        Files.writeString(xmlFile, complexXml);

        // Act
        File result = signatureService.signDocument(
            xmlFile.toFile(),
            keystoreFile,
            keystorePassword,
            alias,
            aliasPassword,
            signedFile.toString()
        );

        // Assert
        assertTrue(result.exists());

        // Validate the signature
        boolean isValid = signatureService.isSignatureValid(result);
        assertTrue(isValid, "Complex XML signature should be valid");

        // Verify structure is preserved
        String signedContent = Files.readString(result.toPath());
        assertTrue(signedContent.contains("library"));
        assertTrue(signedContent.contains("books"));
        assertTrue(signedContent.contains("XML Processing"));
        assertTrue(signedContent.contains("Digital Signatures"));
    }
}
