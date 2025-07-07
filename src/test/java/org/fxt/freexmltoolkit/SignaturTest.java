package org.fxt.freexmltoolkit;

import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Security;

import static org.junit.jupiter.api.Assertions.*;

class SignatureServiceTest {

    private SignatureService signatureService;

    // @TempDir erstellt für jeden Test ein neues, leeres temporäres Verzeichnis
    // und räumt es danach automatisch auf.
    @TempDir
    Path tempDir;

    private static final String ALIAS = "test-alias";
    private static final String KEYSTORE_PASSWORD = "keystore-password";
    private static final String ALIAS_PASSWORD = "alias-password";

    @BeforeAll
    static void beforeAll() {
        // Der Bouncy Castle Provider muss einmalig für alle Tests hinzugefügt werden.
        Security.addProvider(new BouncyCastleProvider());
    }

    @BeforeEach
    void setUp() {
        // Vor jedem Test wird eine neue Instanz des Services erstellt.
        signatureService = new SignatureService();
    }

    @Test
    void createNewKeystoreFile_shouldCreateAllRequiredFiles() throws Exception {
        // Arrange
        X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, "Test Certificate");

        // Act
        File keystoreFile = signatureService.createNewKeystoreFile(nameBuilder, ALIAS, KEYSTORE_PASSWORD, ALIAS_PASSWORD);

        // Assert
        assertTrue(keystoreFile.exists(), "Keystore file should exist");
        assertTrue(keystoreFile.getName().endsWith("_KeyStore.jks"), "Keystore file should have correct extension");

        // Überprüfe, ob die zusätzlichen Dateien erstellt wurden
        Path certsDir = keystoreFile.getParentFile().toPath();
        assertTrue(Files.exists(certsDir.resolve(ALIAS + "_publicKey.pem")), "Public key PEM should exist");
        assertTrue(Files.exists(certsDir.resolve(ALIAS + "_privateKey.pem")), "Private key PEM should exist");
        assertTrue(Files.exists(certsDir.resolve("summary.txt")), "Summary file should exist");

        // Überprüfe den Inhalt des Keystores
        KeyStore ks = KeyStore.getInstance(SignatureService.KEYSTORE_TYPE);
        try (var fis = Files.newInputStream(keystoreFile.toPath())) {
            ks.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }
        assertTrue(ks.containsAlias(ALIAS), "Keystore should contain the alias");
        assertTrue(ks.isKeyEntry(ALIAS), "Alias should be a key entry");
    }

    @Test
    void signAndValidate_withValidDocument_shouldSucceed() throws IOException {
        // Arrange: Erstelle einen Keystore und eine Test-XML-Datei
        File keystoreFile = createTestKeystore();
        File xmlToSign = createTestXmlFile("test-data.xml");
        File signedXmlFile = tempDir.resolve("signed.xml").toFile();

        // Act: Signiere das Dokument
        File resultFile = signatureService.signDocument(
                xmlToSign,
                keystoreFile,
                KEYSTORE_PASSWORD,
                ALIAS,
                ALIAS_PASSWORD,
                signedXmlFile.getAbsolutePath()
        );

        // Assert: Überprüfe das signierte Dokument und validiere es
        assertTrue(resultFile.exists(), "Signed file should exist");
        boolean isValid = signatureService.isSignatureValid(resultFile);
        assertTrue(isValid, "Signature should be valid");
    }

    @Test
    void isSignatureValid_withTamperedDocument_shouldFail() throws IOException {
        // Arrange: Erstelle ein gültig signiertes Dokument
        File keystoreFile = createTestKeystore();
        File xmlToSign = createTestXmlFile("test-data.xml");
        File signedXmlFile = tempDir.resolve("signed.xml").toFile();
        File validSignedFile = signatureService.signDocument(xmlToSign, keystoreFile, KEYSTORE_PASSWORD, ALIAS, ALIAS_PASSWORD, signedXmlFile.getAbsolutePath());

        // Act: Manipuliere das signierte Dokument
        String content = Files.readString(validSignedFile.toPath());
        String tamperedContent = content.replace("<data>some content</data>", "<data>tampered content</data>");
        File tamperedFile = tempDir.resolve("tampered.xml").toFile();
        Files.writeString(tamperedFile.toPath(), tamperedContent);

        // Assert: Die Validierung muss fehlschlagen
        boolean isValid = signatureService.isSignatureValid(tamperedFile);
        assertFalse(isValid, "Signature of tampered file should be invalid");
    }

    @Test
    void isSignatureValid_withWeakAlgorithm_shouldThrowException() throws Exception {
        // Arrange: Erstelle ein Dokument, das mit dem unsicheren SHA1-Algorithmus signiert wurde
        File keystoreFile = createTestKeystore();
        File xmlToSign = createTestXmlFile("weak-test.xml");
        File weakSignedFile = tempDir.resolve("weak-signed.xml").toFile();

        // Signiere manuell mit SHA1
        signWithWeakAlgorithm(xmlToSign, keystoreFile, weakSignedFile);

        // Act & Assert: Die Validierung muss eine Exception auslösen.
        // assertThrowsExactly fängt die Exception und gibt sie zurück,
        // sodass wir die Nachricht überprüfen können.
        SignatureService.SignatureServiceException exception = assertThrowsExactly(
                SignatureService.SignatureServiceException.class,
                () -> signatureService.isSignatureValid(weakSignedFile)
        );

        // Überprüfe die Nachricht der gefangenen Exception
        assertTrue(exception.getMessage().contains("Weak algorithm detected"), "Exception message should indicate a weak algorithm");
    }

    @Test
    void isSignatureValid_withUnsignedDocument_shouldReturnFalse() throws IOException {
        // Arrange
        File unsignedXml = createTestXmlFile("unsigned.xml");

        // Act
        boolean isValid = signatureService.isSignatureValid(unsignedXml);

        // Assert
        assertFalse(isValid, "Unsigned document should be invalid");
    }

    @Test
    void signDocument_withInvalidKeystorePassword_shouldThrowException() {
        // Arrange
        File keystoreFile = createTestKeystore();
        File xmlToSign = tempDir.resolve("test-data.xml").toFile();
        File signedXmlFile = tempDir.resolve("signed.xml").toFile();

        // Act & Assert
        SignatureService.SignatureServiceException exception = assertThrowsExactly(
                SignatureService.SignatureServiceException.class,
                () -> signatureService.signDocument(
                        xmlToSign,
                        keystoreFile,
                        "wrong-password", // Falsches Passwort
                        ALIAS,
                        ALIAS_PASSWORD,
                        signedXmlFile.getAbsolutePath()
                )
        );

        assertTrue(exception.getMessage().contains("Failed to sign the document"), "Exception message should indicate a signing failure");
    }


    // --- HILFSMETHODEN ---

    /**
     * Erstellt eine einfache XML-Datei für Testzwecke.
     */
    private File createTestXmlFile(String fileName) throws IOException {
        File xmlFile = tempDir.resolve(fileName).toFile();
        String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><data>some content</data></root>";
        Files.writeString(xmlFile.toPath(), xmlContent);
        return xmlFile;
    }

    /**
     * Erstellt einen Test-Keystore.
     */
    private File createTestKeystore() {
        X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, "Test");
        return signatureService.createNewKeystoreFile(nameBuilder, ALIAS, KEYSTORE_PASSWORD, ALIAS_PASSWORD);
    }

    /**
     * Eine spezielle Signaturmethode, die absichtlich den veralteten SHA1-Algorithmus verwendet,
     * um die Sicherheitsprüfungen zu testen.
     */
    private void signWithWeakAlgorithm(File documentToSign, File keystore, File outputFile) throws Exception {
        // Diese Methode ist eine vereinfachte Kopie von signDocument,
        // die explizit unsichere Algorithmen verwendet.
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        var db = dbf.newDocumentBuilder();
        Document doc = db.parse(documentToSign);

        var ks = KeyStore.getInstance(SignatureService.KEYSTORE_TYPE);
        try (var fis = Files.newInputStream(keystore.toPath())) {
            ks.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }

        var privateKey = ks.getKey(ALIAS, ALIAS_PASSWORD.toCharArray());
        var fac = javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM");

        var ref = fac.newReference("", fac.newDigestMethod(DigestMethod.SHA1, null),
                java.util.Collections.singletonList(fac.newTransform(javax.xml.crypto.dsig.Transform.ENVELOPED, (TransformParameterSpec) null)), null, null);

        var si = fac.newSignedInfo(
                fac.newCanonicalizationMethod(javax.xml.crypto.dsig.CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null), // SCHWACHER ALGORITHMUS
                java.util.Collections.singletonList(ref));

        var dsc = new javax.xml.crypto.dsig.dom.DOMSignContext(privateKey, doc.getDocumentElement());
        var signature = fac.newXMLSignature(si, null);
        signature.sign(dsc);

        try (var fos = Files.newOutputStream(outputFile.toPath())) {
            var tf = javax.xml.transform.TransformerFactory.newInstance();
            var trans = tf.newTransformer();
            trans.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(fos));
        }
    }
}