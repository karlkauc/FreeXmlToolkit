package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.crypto.EncryptionReport;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Algorithm;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.DecryptionKeys;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.EncryptionRequest;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.PasswordRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.TargetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The Sign &amp; Encrypt page's encryption texts: in English they equal the service's (CLI) texts,
 * in German they are translated.
 */
class EncryptionTextsTest {

    private static final String XML = "<order><card>4111</card><n>x</n></order>";

    @AfterEach
    void english() {
        I18n.setLocale(Locale.ENGLISH);
    }

    private static EncryptionRequest request() {
        return new EncryptionRequest(List.of("/order/card"), Map.of(), TargetType.ELEMENT, null,
                List.of(new PasswordRecipient("pw".toCharArray(), null, 1000, null, "ops")),
                Map.of("purpose", "test"), false);
    }

    private static EncryptionReport sampleReport() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("purpose", "test");
        return new EncryptionReport(List.of(
                new EncryptionReport.Data("ED-1", "/order/card[1]", "Element", "", "AES-256-GCM",
                        "CipherValue (64 Base64 characters)",
                        List.of(new EncryptionReport.KeyRef("EncryptedKey", "EK-1", "RSA-OAEP 1.1", "partner",
                                "shared", "certificate CN=x")), properties),
                new EncryptionReport.Data("", "/", "data", "application/pdf", "AES-128-CBC",
                        "CipherReference payload.bin",
                        List.of(new EncryptionReport.KeyRef("KeyName", "", "symmetric key", "", "k1", "")), Map.of()),
                new EncryptionReport.Data("ED-3", "/x", "Content", "", "TripleDES-CBC", "", List.of(), Map.of())),
                List.of(XmlEncryptionService.legacyWarning(Algorithm.TRIPLEDES_CBC),
                        XmlEncryptionService.legacyWarning(Algorithm.AES_128_CBC)));
    }

    @Test
    void englishReportEqualsTheServiceReport() throws Exception {
        I18n.setLocale(Locale.ENGLISH);
        EncryptionReport sample = sampleReport();
        assertEquals(sample.toText(), EncryptionTexts.report(sample));
        EncryptionReport empty = new EncryptionReport(List.of(), List.of());
        assertEquals(empty.toText(), EncryptionTexts.report(empty));

        var encrypted = EncryptionActionRunner.encryptText(XML, null, request());
        EncryptionReport real = EncryptionActionRunner.inspectText(encrypted.xml());
        assertEquals(real.toText(), EncryptionTexts.report(real));
    }

    @Test
    void englishResultLinesEqualTheRunnerMessages() {
        I18n.setLocale(Locale.ENGLISH);
        var encrypted = EncryptionActionRunner.encryptText(XML, null, request());
        assertTrue(encrypted.ok(), encrypted.message());
        assertEquals(encrypted.message(), EncryptionTexts.encrypted(1, 1));

        var decrypted = EncryptionActionRunner.decryptText(encrypted.xml(), null,
                DecryptionKeys.password("pw".toCharArray()), Set.of());
        assertTrue(decrypted.ok(), decrypted.message());
        assertEquals(1, decrypted.decrypted());
        assertEquals(decrypted.message(), EncryptionTexts.decrypted(decrypted.decrypted(), decrypted.remaining()));
        assertEquals("Decrypted 2, 3 EncryptedData element(s) left", EncryptionTexts.decrypted(2, 3));
        assertEquals("Decrypted 12 bytes of binary data", EncryptionTexts.decryptedBinary(12));

        var wrong = EncryptionActionRunner.decryptText(encrypted.xml(), null,
                DecryptionKeys.password("nope".toCharArray()), Set.of());
        assertFalse(wrong.ok());
        assertEquals(wrong.message(), EncryptionTexts.message(wrong.message()));
    }

    @Test
    void englishMessagesAndWarningsAreUnchanged() {
        I18n.setLocale(Locale.ENGLISH);
        for (String message : List.of("The document holds no EncryptedData", "None of the given keys fits",
                "No symmetric key named 'k1' given", "Invalid XPath '/a[': unexpected end",
                "CipherReference 'x.bin' needs the document's file location",
                "AES-256-GCM needs a 256-bit key, this one has 128 bits",
                "Decryption failed (wrong key or damaged data): pad block corrupted",
                "Something nobody translated")) {
            assertEquals(message, EncryptionTexts.message(message));
        }
        for (Algorithm algorithm : Algorithm.values()) {
            if (algorithm.legacy()) {
                assertEquals(XmlEncryptionService.legacyWarning(algorithm), EncryptionTexts.legacyWarning(algorithm));
                assertEquals(List.of(XmlEncryptionService.legacyWarning(algorithm)),
                        EncryptionTexts.warnings(List.of(XmlEncryptionService.legacyWarning(algorithm))));
            }
        }
    }

    @Test
    void germanTextsAreTranslated() {
        I18n.setLocale(Locale.GERMAN);
        assertEquals("1 Element(e) für 2 Empfänger verschlüsselt", EncryptionTexts.encrypted(1, 2));
        assertEquals("Das Dokument enthält keine EncryptedData",
                EncryptionTexts.message("The document holds no EncryptedData"));
        assertEquals("Kein symmetrischer Schlüssel namens „k1“ angegeben",
                EncryptionTexts.message("No symmetric key named 'k1' given"));
        assertEquals("Entschlüsselung fehlgeschlagen (falscher Schlüssel oder beschädigte Daten): pad",
                EncryptionTexts.message("Decryption failed (wrong key or damaged data): pad"));
        assertTrue(EncryptionTexts.legacyWarning(Algorithm.RSA_1_5).contains("veraltet"));

        String report = EncryptionTexts.report(sampleReport());
        assertTrue(report.startsWith("3 EncryptedData-Element(e)\n"), report);
        assertTrue(report.contains("   Algorithmus:  AES-256-GCM\n"), report);
        assertTrue(report.contains("   Typ:          Daten\n"), report);
        assertTrue(report.contains("CipherValue (64 Base64-Zeichen)"), report);
        assertTrue(report.contains("(keine Id) bei /"), report);
        assertTrue(report.contains("nicht benannt (außerhalb vereinbart)"), report);
        assertTrue(report.contains("Warnungen\n"), report);
    }
}
