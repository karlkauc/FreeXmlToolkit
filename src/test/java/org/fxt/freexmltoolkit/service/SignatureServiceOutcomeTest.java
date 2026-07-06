package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fxt.freexmltoolkit.service.SignatureService.ValidationOutcome;
import org.fxt.freexmltoolkit.service.SignatureService.ValidationStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Tests for {@link SignatureService#validateSignatureDetailed(File)}: each outcome
 * status must carry a plain-language message the Signature panel can show, and
 * {@link SignatureService#isSignatureValid(File)} must keep its historical
 * boolean/exception contract on top of it.
 */
class SignatureServiceOutcomeTest {

    private static final String ALIAS = "outcome-test";
    private static final String KEYSTORE_PASSWORD = "ks-password";
    private static final String ALIAS_PASSWORD = "alias-password";

    @TempDir
    Path tempDir;

    private SignatureService signatureService;

    @BeforeAll
    static void beforeAll() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @BeforeEach
    void setUp() {
        signatureService = new SignatureService();
    }

    @Test
    void validSignatureYieldsValidOutcome() throws Exception {
        File signed = signTestDocument();
        ValidationOutcome outcome = signatureService.validateSignatureDetailed(signed);
        assertEquals(ValidationStatus.VALID, outcome.status());
        assertTrue(outcome.isValid());
    }

    @Test
    void unsignedDocumentYieldsNoSignatureOutcome() throws Exception {
        File unsigned = tempDir.resolve("unsigned.xml").toFile();
        Files.writeString(unsigned.toPath(), "<root><child>x</child></root>");

        ValidationOutcome outcome = signatureService.validateSignatureDetailed(unsigned);
        assertEquals(ValidationStatus.NO_SIGNATURE, outcome.status());
        assertTrue(outcome.message().contains("no <Signature>"), outcome.message());
    }

    @Test
    void tamperedDocumentYieldsInvalidOutcomeNamingTheModifiedContent() throws Exception {
        File signed = signTestDocument();
        String tampered = Files.readString(signed.toPath()).replace("original", "tampered");
        File tamperedFile = tempDir.resolve("tampered.xml").toFile();
        Files.writeString(tamperedFile.toPath(), tampered);

        ValidationOutcome outcome = signatureService.validateSignatureDetailed(tamperedFile);
        assertEquals(ValidationStatus.INVALID, outcome.status());
        assertTrue(outcome.message().contains("modified after signing"),
                "the message must explain the content was altered: " + outcome.message());
    }

    @Test
    void keyInfoWithoutCertificateYieldsUnsupportedKeyInfoOutcome() throws Exception {
        File stripped = withoutX509Data(signTestDocument());

        ValidationOutcome outcome = signatureService.validateSignatureDetailed(stripped);
        assertEquals(ValidationStatus.UNSUPPORTED_KEY_INFO, outcome.status());
        assertTrue(outcome.message().contains("does not embed an X.509 certificate"),
                "the message must explain the missing certificate: " + outcome.message());
        assertTrue(outcome.message().contains("KeyValue"),
                "the message should name what the KeyInfo does contain: " + outcome.message());
    }

    @Test
    void isSignatureValidStillThrowsSpeakingExceptionForUnusableKeyInfo() throws Exception {
        File stripped = withoutX509Data(signTestDocument());

        SignatureService.SignatureServiceException exception = assertThrows(
                SignatureService.SignatureServiceException.class,
                () -> signatureService.isSignatureValid(stripped));
        assertTrue(exception.getMessage().contains("X.509"),
                "the exception must carry the speaking reason: " + exception.getMessage());
    }

    /** Signs a small document with a fresh test keystore (enveloped). */
    private File signTestDocument() throws Exception {
        X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, "Outcome Test");
        File keystore = signatureService.createNewKeystoreFile(nameBuilder, ALIAS, KEYSTORE_PASSWORD, ALIAS_PASSWORD);
        File xml = tempDir.resolve("doc.xml").toFile();
        Files.writeString(xml.toPath(), "<root><child>original</child></root>");
        File output = tempDir.resolve("signed.xml").toFile();
        return signatureService.signDocument(xml, keystore,
                KEYSTORE_PASSWORD, ALIAS, ALIAS_PASSWORD, output.getAbsolutePath());
    }

    /**
     * Removes the X509Data element from a signed file's KeyInfo. The enveloped
     * signature stays cryptographically intact (KeyInfo is not covered by the
     * reference digest), leaving only the KeyValue — the classic "cannot find
     * validation key" shape produced by other signing tools.
     */
    private File withoutX509Data(File signed) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(signed);
        NodeList x509 = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "X509Data");
        assertTrue(x509.getLength() > 0, "fixture must contain an X509Data element");
        for (int i = x509.getLength() - 1; i >= 0; i--) {
            Node node = x509.item(i);
            node.getParentNode().removeChild(node);
        }
        File stripped = tempDir.resolve("keyvalue-only.xml").toFile();
        try (var fos = Files.newOutputStream(stripped.toPath())) {
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(doc), new StreamResult(fos));
        }
        return stripped;
    }
}
