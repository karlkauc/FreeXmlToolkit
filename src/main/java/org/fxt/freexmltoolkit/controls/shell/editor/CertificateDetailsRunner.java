package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/**
 * UI-free extraction of an X.509 certificate's display details from a keystore
 * (the Figma mockup's CERTIFICATE inspector: subject, validity, serial,
 * algorithm, key usage, SHA-256 fingerprint). Pure JDK; no UI dependencies.
 */
public final class CertificateDetailsRunner {

    /** The names of the X.509 keyUsage bits, in bit order. */
    private static final String[] KEY_USAGE_NAMES = {
            "digitalSignature", "nonRepudiation", "keyEncipherment", "dataEncipherment",
            "keyAgreement", "keyCertSign", "cRLSign", "encipherOnly", "decipherOnly"};

    private CertificateDetailsRunner() {
    }

    /**
     * The certificate's display details.
     *
     * @param subjectCn          the subject common name (or the full subject DN as fallback)
     * @param subjectO           the subject organization, or {@code null}
     * @param subjectC           the subject country, or {@code null}
     * @param selfSigned         whether issuer equals subject
     * @param issued             validity start
     * @param expires            validity end
     * @param daysRemaining      days until {@code expires} (negative when expired)
     * @param currentlyValid     whether now is within the validity window
     * @param serialHex          the serial number in hex (0x-prefixed, uppercase)
     * @param signatureAlgorithm e.g. SHA256withRSA
     * @param keyUsage           comma-separated keyUsage names, or {@code null} when absent
     * @param sha256Fingerprint  colon-separated uppercase SHA-256 fingerprint
     */
    public record CertificateDetails(String subjectCn, String subjectO, String subjectC,
                                     boolean selfSigned, LocalDateTime issued, LocalDateTime expires,
                                     long daysRemaining, boolean currentlyValid, String serialHex,
                                     String signatureAlgorithm, String keyUsage, String sha256Fingerprint) {
    }

    /**
     * Loads the certificate of {@code alias} from the keystore and extracts its details.
     *
     * @param keystore the keystore file (JKS, or PKCS12 by .p12/.pfx extension)
     * @param password the keystore password
     * @param alias    the certificate's alias
     * @return the extracted details
     * @throws Exception when the keystore cannot be opened or the alias has no certificate
     */
    public static CertificateDetails fromKeystore(File keystore, String password, String alias) throws Exception {
        String type = keystore.getName().toLowerCase(Locale.ROOT).matches(".*\\.(p12|pfx)$") ? "PKCS12" : "JKS";
        KeyStore store = KeyStore.getInstance(type);
        try (FileInputStream in = new FileInputStream(keystore)) {
            store.load(in, password != null ? password.toCharArray() : null);
        }
        if (!(store.getCertificate(alias) instanceof X509Certificate certificate)) {
            throw new IllegalArgumentException("No certificate for alias '" + alias + "' in " + keystore.getName());
        }
        return fromCertificate(certificate);
    }

    /** Extracts the display details from an X.509 certificate. */
    public static CertificateDetails fromCertificate(X509Certificate certificate) throws Exception {
        String subjectDn = certificate.getSubjectX500Principal().getName();
        String cn = rdn(subjectDn, "CN");
        LocalDateTime issued = toLocal(certificate.getNotBefore());
        LocalDateTime expires = toLocal(certificate.getNotAfter());
        LocalDateTime now = LocalDateTime.now();
        boolean[] usageBits = certificate.getKeyUsage();
        List<String> usages = new ArrayList<>();
        if (usageBits != null) {
            for (int i = 0; i < usageBits.length && i < KEY_USAGE_NAMES.length; i++) {
                if (usageBits[i]) {
                    usages.add(KEY_USAGE_NAMES[i]);
                }
            }
        }
        return new CertificateDetails(
                cn != null ? cn : subjectDn,
                rdn(subjectDn, "O"),
                rdn(subjectDn, "C"),
                certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal()),
                issued, expires,
                ChronoUnit.DAYS.between(now, expires),
                !now.isBefore(issued) && !now.isAfter(expires),
                "0x" + certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT),
                certificate.getSigAlgName(),
                usages.isEmpty() ? null : String.join(", ", usages),
                fingerprint(certificate));
    }

    private static String rdn(String dn, String key) {
        try {
            for (Rdn rdn : new LdapName(dn).getRdns()) {
                if (key.equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (Exception ignored) {
            // unparsable DN - fall through
        }
        return null;
    }

    private static LocalDateTime toLocal(java.util.Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static String fingerprint(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            if (hex.length() > 0) {
                hex.append(':');
            }
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }
}
