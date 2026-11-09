package org.fxt.freexmltoolkit.controls.shell.editor;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Algorithm;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.PasswordRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.PublicKeyRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.Recipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.SymmetricRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.X509Reference;

/**
 * The recipients of the Encrypt form and how they become {@link Recipient}s with the chosen
 * advanced options; the conversion of the editor's caret XPath into a namespace-free XPath. No UI.
 */
final class EncryptionRecipients {

    private EncryptionRecipients() {
    }

    /** One row of the RECIPIENTS list. */
    sealed interface Entry permits CertificateEntry, KeyFileEntry, PasswordEntry {
        /** @return the row's title */
        String title();

        /** @return the row's second line */
        String detail();

        /** @return the icon of the row */
        String icon();
    }

    /** A certificate (or a bare public key: {@code certificate == null}). */
    record CertificateEntry(PublicKey key, X509Certificate certificate, String title) implements Entry {
        @Override
        public String detail() {
            Algorithm method = XmlEncryptionService.defaultMethodFor(key);
            String keyText = key.getAlgorithm() + (method.kind() == XmlEncryptionAlgorithms.Kind.KEY_AGREEMENT
                    ? " · " + method.label() : " · RSA key transport");
            return certificate == null ? "Public key · " + keyText
                    : "Certificate · " + keyText + " · valid until " + certificate.getNotAfter().toInstant().toString().substring(0, 10);
        }

        @Override
        public String icon() {
            return "bi-person-badge";
        }
    }

    /** A shared symmetric key from a file. */
    record KeyFileEntry(SecretKey key, String keyName, String title) implements Entry {
        @Override
        public String detail() {
            return "Shared key · " + key.getEncoded().length * 8 + " bit"
                    + (keyName != null && !keyName.isBlank() ? " · KeyName " + keyName : "");
        }

        @Override
        public String icon() {
            return "bi-key";
        }
    }

    /** A password. */
    record PasswordEntry(char[] password) implements Entry {
        @Override
        public String title() {
            return "Password";
        }

        @Override
        public String detail() {
            return "PBKDF2 with HMAC-SHA256, " + XmlEncryptionService.DEFAULT_ITERATIONS + " iterations";
        }

        @Override
        public String icon() {
            return "bi-asterisk";
        }
    }

    /**
     * The advanced options of the form; {@code null} values take the defaults.
     *
     * @param keyWrap {@code null}: AES matching the data key; for a key file the data key itself
     *                when {@code directKeyFile}
     */
    record Options(Algorithm keyTransport, Algorithm oaepDigest, Algorithm mgf, Algorithm kdf, Algorithm kdfDigest,
                   Algorithm keyWrap, boolean directKeyFile, X509Reference x509, String recipientName) {
    }

    /** Builds the service recipients from the rows. */
    static List<Recipient> toRecipients(List<Entry> entries, Options options) {
        List<Recipient> recipients = new ArrayList<>();
        String name = options.recipientName() == null || options.recipientName().isBlank() ? null : options.recipientName();
        for (Entry entry : entries) {
            switch (entry) {
                case CertificateEntry c -> {
                    Algorithm method = XmlEncryptionService.defaultMethodFor(c.key());
                    if (method.kind() == XmlEncryptionAlgorithms.Kind.KEY_TRANSPORT && options.keyTransport() != null) {
                        method = options.keyTransport();
                    }
                    recipients.add(new PublicKeyRecipient(c.key(), c.certificate(), method, options.oaepDigest(),
                            options.mgf(), options.keyWrap(), options.kdf(), options.kdfDigest(),
                            c.certificate() == null ? X509Reference.NONE
                                    : options.x509() != null ? options.x509() : X509Reference.ISSUER_SERIAL, name));
                }
                case KeyFileEntry k -> {
                    Algorithm wrap = options.directKeyFile() ? null
                            : options.keyWrap() != null ? options.keyWrap()
                            : XmlEncryptionAlgorithms.keyWrapFor(k.key().getEncoded().length * 8);
                    recipients.add(new SymmetricRecipient(k.key(), k.keyName(), wrap, name));
                }
                case PasswordEntry p -> recipients.add(new PasswordRecipient(p.password(), options.keyWrap(),
                        XmlEncryptionService.DEFAULT_ITERATIONS, XmlEncryptionService.HMAC_SHA256, name));
            }
        }
        return recipients;
    }

    /** Parses the "name=value per line" properties text. */
    static Map<String, String> properties(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null) {
            return map;
        }
        for (String line : text.split("\\R")) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.substring(0, eq).isBlank()) {
                map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return map;
    }

    /**
     * The editor's positional XPath ({@code /p:order/item[2]}) as a namespace-free one
     * ({@code /*[local-name()='order']/*[local-name()='item'][2]}): the element's prefixes need no
     * declarations then.
     */
    static String localNameXPath(String xpath) {
        if (xpath == null || xpath.isBlank() || "/".equals(xpath)) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String step : xpath.substring(1).split("/")) {
            String name = step;
            String predicate = "";
            int bracket = step.indexOf('[');
            if (bracket > 0) {
                name = step.substring(0, bracket);
                predicate = step.substring(bracket);
            }
            int colon = name.indexOf(':');
            String local = colon >= 0 ? name.substring(colon + 1) : name;
            out.append("/*[local-name()='").append(local).append("']").append(predicate);
        }
        return out.toString();
    }

    /** @return the telemetry name of a recipient's key method */
    static String keyMethodName(Recipient recipient) {
        return switch (recipient) {
            case PublicKeyRecipient p -> p.method() != null ? p.method().id() : "public-key";
            case SymmetricRecipient s -> s.keyWrap() == null ? "direct" : s.keyWrap().id();
            case PasswordRecipient ignored -> "password";
        };
    }
}
