package org.fxt.freexmltoolkit.controls.shell.editor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link SignatureActionRunner} certificate/keystore creation (no UI),
 * reusing SignatureService. The created JKS must be loadable and contain the
 * requested alias + certificate.
 */
class SignatureActionRunnerTest {

    private static final String ALIAS = "fxtShellSelfTest";

    @AfterEach
    void cleanup() throws Exception {
        Path dir = Path.of("certs", ALIAS);
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    @Test
    void createsALoadableKeystoreContainingTheAlias() throws Exception {
        File keystore = SignatureActionRunner.createKeystore(
                new SignatureActionRunner.CertificateInfo("Test CN", "Dev", "ACME", "City", "ST", "AT", "a@b.c"),
                ALIAS, "storepass", "keypass");

        assertNotNull(keystore, "keystore file must be created");
        assertTrue(keystore.exists() && keystore.length() > 0, "keystore must be non-empty");

        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream in = Files.newInputStream(keystore.toPath())) {
            ks.load(in, "storepass".toCharArray());
        }
        assertTrue(ks.containsAlias(ALIAS), "keystore must contain the alias");
        assertNotNull(ks.getCertificate(ALIAS), "alias must have a certificate");
    }
}
