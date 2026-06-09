package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.service.SignatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link SignatureActionRunner#describeSignature} (no UI): signs a document
 * with a freshly created self-signed certificate, then produces a detailed
 * validation report (validity + the embedded signing certificate's details).
 */
class SignatureDescribeTest {

    private static final String ALIAS = "fxtDescribeSelfTest";

    @AfterEach
    void cleanup() throws Exception {
        Path dir = Path.of("certs", ALIAS);
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    @Test
    void reportsValidityAndSigningCertificateDetails(@TempDir Path tmp) throws Exception {
        File keystore = SignatureActionRunner.createKeystore(
                new SignatureActionRunner.CertificateInfo("Reporter CN", "Dev", "ACME", "City", "ST", "AT", "a@b.c"),
                ALIAS, "storepass", "keypass");
        File xml = tmp.resolve("doc.xml").toFile();
        Files.writeString(xml.toPath(), "<root><data>value</data></root>");
        File signed = tmp.resolve("doc.signed.xml").toFile();
        new SignatureService().signDocument(xml, keystore, "storepass", ALIAS, "keypass", signed.getAbsolutePath());

        String report = SignatureActionRunner.describeSignature(signed);

        assertFalse(report.startsWith("ERROR:"), report);
        assertTrue(report.toLowerCase().contains("valid"), report);
        assertTrue(report.contains("Reporter CN"), "report must include the signing certificate subject: " + report);
    }

    @Test
    void missingFileReportsError() {
        assertTrue(SignatureActionRunner.describeSignature(new File("/no/such.signed.xml")).startsWith("ERROR:"));
    }
}
