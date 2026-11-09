package org.fxt.freexmltoolkit.service.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.shell.editor.EncryptionActionRunner;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.DecryptionKeys;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.DecryptionStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The shipped examples in release/examples/encryption decrypt with the keys their README names. */
class EncryptionExamplesTest {

    private static final Path DIR = Path.of("release/examples/encryption");

    @ParameterizedTest
    @CsvSource({
            "order-payment-rsa.xml, rsa",
            "order-customer-content-ec.xml, ec",
            "order-three-recipients.xml, rsa",
            "order-three-recipients.xml, ec",
            "order-three-recipients.xml, password",
            "order-shared-key.xml, key",
            "order-password.xml, password",
            "order-legacy-3des-rsa15.xml, rsa"
    })
    void decryptsWithTheDocumentedKey(String file, String key) throws Exception {
        DecryptionKeys keys = switch (key) {
            case "rsa" -> DecryptionKeys.privateKey(KeyMaterial.privateKey(DIR.resolve("demo-rsa.p12"),
                    "changeit".toCharArray(), "demo-rsa", null).privateKey());
            case "ec" -> DecryptionKeys.privateKey(KeyMaterial.privateKey(DIR.resolve("demo-ec-private.pem"), null));
            case "key" -> DecryptionKeys.secretKey("demo-shared-2026",
                    KeyMaterial.symmetricKey(DIR.resolve("demo-shared.key"), "AES"));
            default -> DecryptionKeys.password("example".toCharArray());
        };
        var document = EncryptionActionRunner.parse(DIR.resolve(file));
        var outcome = new XmlEncryptionService().decrypt(document, keys, Set.of());
        assertEquals(DecryptionStatus.DECRYPTED, outcome.status(), outcome.message());
        String text = EncryptionActionRunner.serialize(document);
        assertTrue(text.contains("Ada Lovelace") && text.contains("4111111111111111"), text);
        assertTrue(Files.readString(DIR.resolve("order.xml")).contains("XSD-202"));
    }
}
