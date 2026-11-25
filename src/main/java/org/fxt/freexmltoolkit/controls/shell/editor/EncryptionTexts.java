package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.crypto.EncryptionReport;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Algorithm;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService;

/**
 * The texts of the Sign &amp; Encrypt page's encryption results in the interface language.
 *
 * <p>{@link XmlEncryptionService}, {@link EncryptionReport} and {@link EncryptionActionRunner} are
 * shared with {@code fxt-cli encrypt/decrypt} and keep their English texts. The page words its lines
 * here: result lines from the structured counts, service messages by matching their English wording
 * (an unknown message is shown as it is), the Inspect report with translated labels. With the
 * English bundle every text equals the service's own.</p>
 */
final class EncryptionTexts {

    /** Service and key-file messages: English wording (whole message) → bundle key, groups = arguments. */
    private static final Map<Pattern, String> MESSAGES = new java.util.LinkedHashMap<>();

    static {
        message("Add at least one recipient \\(certificate, key or password\\)", "add-recipient");
        message("A symmetric key used directly as data key cannot be combined with other recipients", "direct-key-alone");
        message("The password is empty", "password-empty");
        message("The document holds no EncryptedData", "no-encrypted-data");
        message("The document is no EncryptedData", "not-encrypted-data");
        message("Give a private key, a symmetric key or a password", "give-key");
        message("None of the given keys fits", "no-key-fits");
        message("None of the given keys opens an EncryptedKey of this EncryptedData", "no-key-opens");
        message("The EncryptedData names no key: give the symmetric key", "names-no-key");
        message("No symmetric key named '(.*)' given", "no-named-key");
        message("Too many EncryptedData elements \\((\\d+)\\)", "too-many-data");
        message("Too many EncryptedKeys for one EncryptedData", "too-many-keys");
        message("Incomplete PBKDF2 parameters", "pbkdf2-incomplete");
        message("Invalid PBKDF2 parameters", "pbkdf2-invalid");
        message("PBKDF2 parameters out of range", "pbkdf2-range");
        message("PBKDF2 failed: (.*)", "pbkdf2-failed");
        message("The document is empty", "document-empty");
        message("The XPath selects no element", "xpath-no-element");
        message("Invalid XPath '(.*?)': (.*)", "xpath-invalid");
        message("XML encryption is disabled by the administrator", "disabled");
        message("(.+) is a legacy algorithm blocked by the administrator", "legacy-blocked");
        message("(.+) needs an RSA key, this one is (.+)", "needs-rsa");
        message("(.+) is no key transport or key agreement", "no-key-transport");
        message("(.+) needs a (\\d+)-bit key, this one has (\\d+) bits", "key-size");
        message("Unsupported data algorithm (.*)", "unsupported-data");
        message("Unsupported key algorithm (.*)", "unsupported-key");
        message("Unsupported key agreement (.*)", "unsupported-agreement");
        message("Unsupported key derivation (.*)", "unsupported-derivation");
        message("Unreadable EncryptedKey: (.*)", "unreadable-key");
        message("CipherReference to '(.*)' refused: only files next to the document", "ref-refused");
        message("CipherReference within the document is not supported", "ref-internal");
        message("CipherReference '(.*)' needs the document's file location", "ref-location");
        message("CipherReference '(.*)' is no file next to the document", "ref-not-sibling");
        message("CipherReference '(.*)' is too large", "ref-too-large");
        message("Cannot read CipherReference '(.*?)': (.*)", "ref-unreadable");
        message("CipherReference transform (.*) is not supported", "ref-transform");
        message("Decryption failed \\(wrong key or damaged data\\): (.*)", "decryption-wrong-key");
        message("Decryption failed: (.*)", "decryption-failed");
        message("Encryption failed: (.*)", "encryption-failed");
        // key files (KeyMaterial)
        message("No private key under alias '(.*)'", "no-private-key-alias");
        message("No certificate under alias '(.*)'", "no-certificate-alias");
        message("No certificate in (.*)", "no-certificate-in");
        message("No private key in (.*)", "no-private-key-in");
        message("Cannot read the private key: (.*)", "private-key-unreadable");
        message("A symmetric key has 16, 24 or 32 bytes, this one (\\d+)", "symmetric-key-size");
        message("The private key is encrypted: give its password", "private-key-encrypted");
    }

    private EncryptionTexts() {
    }

    private static void message(String regex, String key) {
        MESSAGES.put(Pattern.compile(regex, Pattern.DOTALL), "EncryptionTexts.msg." + key);
    }

    /** @return {@code key} formatted with {@code args} (always through MessageFormat, so quotes are doubled in the bundle) */
    private static String t(String key, Object... args) {
        return I18n.t(key, args);
    }

    /** @return the line after an encryption: "Encrypted 2 element(s) for 1 recipient(s)" */
    static String encrypted(int elements, int recipients) {
        return t("EncryptionTexts.encrypted", elements, recipients);
    }

    /** @return the line after a decryption of XML */
    static String decrypted(int decrypted, int remaining) {
        return remaining == 0 ? t("EncryptionTexts.decrypted", decrypted)
                : t("EncryptionTexts.decrypted-partly", decrypted, remaining);
    }

    /** @return the line after a decryption of binary data */
    static String decryptedBinary(int bytes) {
        return t("EncryptionTexts.decrypted-binary", bytes);
    }

    /** @return a service message in the interface language, or the message itself when unknown */
    static String message(String english) {
        if (english == null) {
            return null;
        }
        for (Map.Entry<Pattern, String> entry : MESSAGES.entrySet()) {
            Matcher m = entry.getKey().matcher(english);
            if (m.matches()) {
                Object[] args = new Object[m.groupCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = m.group(i + 1);
                }
                return t(entry.getValue(), args);
            }
        }
        return english;
    }

    /** @return the warning about a legacy algorithm in the interface language */
    static String legacyWarning(Algorithm algorithm) {
        return switch (algorithm) {
            case RSA_1_5 -> t("EncryptionTexts.legacy.rsa15");
            case TRIPLEDES_CBC, KW_TRIPLEDES -> t("EncryptionTexts.legacy.tripledes", algorithm.label());
            case DH -> t("EncryptionTexts.legacy.dh");
            case SHA1 -> t("EncryptionTexts.legacy.sha1");
            default -> t("EncryptionTexts.legacy.cbc", algorithm.label());
        };
    }

    /** @return the warnings (English, from the service) in the interface language */
    static List<String> warnings(List<String> english) {
        return english.stream().map(EncryptionTexts::warning).toList();
    }

    private static String warning(String english) {
        for (Algorithm algorithm : Algorithm.values()) {
            if (algorithm.legacy() && XmlEncryptionService.legacyWarning(algorithm).equals(english)) {
                return legacyWarning(algorithm);
            }
        }
        return message(english);
    }

    /**
     * The Inspect report in the interface language: headings, labels and the simple values are
     * translated; algorithm names, ids, locations and key details stay as the document has them.
     * With the English bundle it equals {@link EncryptionReport#toText()}.
     */
    static String report(EncryptionReport report) {
        if (report.data().isEmpty()) {
            return t("EncryptionTexts.report.none") + "\n";
        }
        String type = t("EncryptionTexts.report.type");
        String mime = t("EncryptionTexts.report.mime-type");
        String algorithm = t("EncryptionTexts.report.algorithm");
        String cipher = t("EncryptionTexts.report.cipher");
        String key = t("EncryptionTexts.report.key");
        String property = t("EncryptionTexts.report.property");
        int width = 2 + 1 + java.util.stream.Stream.of(type, mime, algorithm, cipher, key, property)
                .mapToInt(String::length).max().orElse(0);
        StringBuilder out = new StringBuilder();
        out.append(t("EncryptionTexts.report.count", report.data().size())).append('\n');
        int n = 0;
        for (EncryptionReport.Data d : report.data()) {
            out.append('\n').append(t("EncryptionTexts.report.heading", ++n,
                    d.id().isEmpty() ? t("EncryptionTexts.report.no-id") : d.id(), d.location())).append('\n');
            out.append(label(type, width)).append(dataType(d.type())).append('\n');
            if (!d.mimeType().isEmpty()) {
                out.append(label(mime, width)).append(d.mimeType()).append('\n');
            }
            out.append(label(algorithm, width)).append(d.algorithm()).append('\n');
            out.append(label(cipher, width)).append(cipherText(d.cipher())).append('\n');
            if (d.keys().isEmpty()) {
                out.append(label(key, width)).append(t("EncryptionTexts.report.key-not-named")).append('\n');
            }
            for (EncryptionReport.KeyRef k : d.keys()) {
                out.append(label(key, width)).append(k.kind());
                if (!k.id().isEmpty()) {
                    out.append(' ').append(k.id());
                }
                out.append(" - ").append("symmetric key".equals(k.method())
                        ? t("EncryptionTexts.report.symmetric-key") : k.method());
                if (!k.recipient().isEmpty()) {
                    out.append(t("EncryptionTexts.report.recipient", k.recipient()));
                }
                if (!k.carriedName().isEmpty()) {
                    out.append(t("EncryptionTexts.report.name", k.carriedName()));
                }
                if (!k.detail().isEmpty()) {
                    out.append(", ").append(k.detail());
                }
                out.append('\n');
            }
            d.properties().forEach((name, value) ->
                    out.append(label(property, width)).append(name).append(" = ").append(value).append('\n'));
        }
        if (!report.warnings().isEmpty()) {
            out.append('\n').append(t("EncryptionTexts.report.warnings")).append('\n');
            warnings(report.warnings()).forEach(w -> out.append(" - ").append(w).append('\n'));
        }
        return out.toString();
    }

    /** @return "   Label:" padded to {@code width} characters after the indent */
    private static String label(String label, int width) {
        StringBuilder s = new StringBuilder("   ").append(label).append(':');
        while (s.length() < 3 + width) {
            s.append(' ');
        }
        return s.toString();
    }

    private static String dataType(String type) {
        return "data".equals(type) ? t("EncryptionTexts.report.type-data") : type;
    }

    private static final Pattern CIPHER_VALUE = Pattern.compile("CipherValue \\((\\d+) Base64 characters\\)");

    private static String cipherText(String cipher) {
        Matcher m = CIPHER_VALUE.matcher(cipher);
        return m.matches() ? t("EncryptionTexts.report.cipher-value", m.group(1)) : cipher;
    }
}
