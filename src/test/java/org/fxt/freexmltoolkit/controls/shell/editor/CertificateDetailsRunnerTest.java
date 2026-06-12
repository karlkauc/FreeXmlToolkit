package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * Verifies the certificate-details extraction against a really generated
 * self-signed keystore (reuses the shell's certificate creation).
 */
class CertificateDetailsRunnerTest {

    @Test
    void extractsTheDetailsOfAGeneratedCertificate() throws Exception {
        SignatureActionRunner.CertificateInfo info = new SignatureActionRunner.CertificateInfo(
                "Karl Kauc", "IT", "FreeXmlToolkit", "Vienna", "Vienna", "AT", "karl@example.com");
        File keystore = SignatureActionRunner.createKeystore(info, "fxt-test-signer", "secret", "secret");
        try {
            var details = CertificateDetailsRunner.fromKeystore(keystore, "secret", "fxt-test-signer");

            assertEquals("Karl Kauc", details.subjectCn());
            assertEquals("FreeXmlToolkit", details.subjectO());
            assertEquals("AT", details.subjectC());
            assertTrue(details.selfSigned(), "a generated certificate is self-signed");
            assertTrue(details.currentlyValid(), "a freshly generated certificate is valid");
            assertTrue(details.daysRemaining() > 0);
            assertTrue(details.serialHex().startsWith("0x"));
            assertNotNull(details.signatureAlgorithm());
            assertTrue(details.sha256Fingerprint().matches("([0-9A-F]{2}:){31}[0-9A-F]{2}"),
                    "fingerprint must be a colon-separated SHA-256: " + details.sha256Fingerprint());
        } finally {
            // best-effort cleanup of the generated keystore (and its folder if dedicated)
            if (keystore != null && keystore.exists() && !keystore.delete()) {
                keystore.deleteOnExit();
            }
        }
    }

    @Test
    void failsClearlyForAnUnknownAlias() throws Exception {
        SignatureActionRunner.CertificateInfo info = new SignatureActionRunner.CertificateInfo(
                "X", null, null, null, null, null, null);
        File keystore = SignatureActionRunner.createKeystore(info, "real-alias", "pw", "pw");
        try {
            Exception failure = assertThrows(Exception.class,
                    () -> CertificateDetailsRunner.fromKeystore(keystore, "pw", "no-such-alias"));
            assertTrue(failure.getMessage().contains("no-such-alias"), failure.getMessage());
        } finally {
            if (keystore != null && keystore.exists() && !keystore.delete()) {
                keystore.deleteOnExit();
            }
        }
    }
}
