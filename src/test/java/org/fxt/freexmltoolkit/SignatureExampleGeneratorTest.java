package org.fxt.freexmltoolkit;

import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual generator for the bundled signature example in {@code release/examples/signature/}.
 *
 * <p>Run it whenever the example needs to be regenerated (e.g. after the demo
 * certificate expired or {@code FundsXML4_Equity_Fund.xml} changed):
 *
 * <pre>./gradlew test --tests "SignatureExampleGeneratorTest"</pre>
 *
 * <p>Remove the {@code @Disabled} annotation for the run, then restore it.
 *
 * <p>It produces, via the app's own {@link SignatureService} (JKS, RSA-2048, SHA-256,
 * enveloped signature):
 * <ul>
 *   <li>{@code fundsxml-demo_KeyStore.jks} (keystore and alias password: {@code changeit})</li>
 *   <li>{@code fundsxml-demo_publicKey.pem} / {@code fundsxml-demo_privateKey.pem}</li>
 *   <li>{@code FundsXML4_Equity_Fund_signed.xml}</li>
 * </ul>
 */
@Disabled("manual example (re)generation - see class Javadoc")
class SignatureExampleGeneratorTest {

    private static final String ALIAS = "fundsxml-demo";
    private static final String PASSWORD = "changeit";

    @BeforeAll
    static void beforeAll() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void generateSignatureExample() throws IOException {
        Path examplesDir = Path.of("release", "examples");
        assertTrue(Files.isDirectory(examplesDir), "Must run from the project root");

        Path signatureDir = examplesDir.resolve("signature");
        Files.createDirectories(signatureDir);

        SignatureService signatureService = new SignatureService();

        // 1. Create keystore + PEMs (service writes to certs/<alias>/ under the working dir)
        X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, "FreeXmlToolkit Demo")
                .addRDN(BCStyle.O, "FundsXML Examples");
        File keystoreFile = signatureService.createNewKeystoreFile(nameBuilder, ALIAS, PASSWORD, PASSWORD);
        assertTrue(keystoreFile.exists(), "Keystore should have been created");

        // 2. Sign the equity fund example (enveloped signature)
        File xmlToSign = examplesDir.resolve("xml").resolve("FundsXML4_Equity_Fund.xml").toFile();
        assertTrue(xmlToSign.exists(), "Equity fund example must exist");
        File signedFile = signatureService.signDocument(
                xmlToSign, keystoreFile, PASSWORD, ALIAS, PASSWORD,
                signatureDir.resolve("FundsXML4_Equity_Fund_signed.xml").toString());

        // 3. Verify before shipping
        assertTrue(signatureService.isSignatureValid(signedFile), "Generated signature must validate");

        // 4. Copy keystore + PEMs next to the signed file, then clean up the working dir
        Path certsDir = keystoreFile.getParentFile().toPath();
        for (String name : new String[]{keystoreFile.getName(), ALIAS + "_publicKey.pem", ALIAS + "_privateKey.pem"}) {
            Files.copy(certsDir.resolve(name), signatureDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        try (var walk = Files.walk(certsDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
