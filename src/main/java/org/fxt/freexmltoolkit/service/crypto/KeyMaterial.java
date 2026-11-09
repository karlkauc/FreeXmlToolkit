package org.fxt.freexmltoolkit.service.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

/**
 * Loads key material for XML signatures and XML encryption: keystores (JKS, PKCS#12 by file
 * extension), certificates (PEM or DER, one or several), public and private keys in PEM (also
 * encrypted, via BouncyCastle) and symmetric keys from a file (hex, Base64 or raw bytes).
 * Passwords are {@code char[]} and never kept.
 */
public final class KeyMaterial {

    private KeyMaterial() {
    }

    /** A private key with its certificate (if there is one) from a keystore or PEM files. */
    public record PrivateKeyEntry(PrivateKey privateKey, X509Certificate certificate) {
    }

    /** @return the keystore type for a file: PKCS12 for .p12/.pfx, else JKS */
    public static String keystoreType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".p12") || name.endsWith(".pfx") ? "PKCS12" : "JKS";
    }

    /** Loads a JKS or PKCS#12 keystore. */
    public static KeyStore loadKeystore(Path file, char[] password) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance(keystoreType(file));
        try (InputStream in = Files.newInputStream(file)) {
            ks.load(in, password);
        }
        return ks;
    }

    /** @return the aliases of a keystore that hold a private key (decryption / signing) */
    public static List<String> privateKeyAliases(KeyStore ks) throws GeneralSecurityException {
        List<String> aliases = new ArrayList<>();
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                aliases.add(alias);
            }
        }
        return aliases;
    }

    /**
     * The private key and certificate of {@code alias}; without an alias the keystore's only key
     * entry.
     *
     * @param keyPassword the entry's password, or {@code null} for the keystore password
     */
    public static PrivateKeyEntry privateKey(Path keystore, char[] storePassword, String alias, char[] keyPassword)
            throws IOException, GeneralSecurityException {
        KeyStore ks = loadKeystore(keystore, storePassword);
        String chosen = alias;
        if (chosen == null || chosen.isBlank()) {
            List<String> aliases = privateKeyAliases(ks);
            if (aliases.size() != 1) {
                throw new GeneralSecurityException(aliases.isEmpty() ? "The keystore holds no private key"
                        : "The keystore holds several keys, choose an alias: " + String.join(", ", aliases));
            }
            chosen = aliases.get(0);
        }
        Key key = ks.getKey(chosen, keyPassword != null ? keyPassword : storePassword);
        if (!(key instanceof PrivateKey privateKey)) {
            throw new GeneralSecurityException("No private key under alias '" + chosen + "'");
        }
        return new PrivateKeyEntry(privateKey, ks.getCertificate(chosen) instanceof X509Certificate c ? c : null);
    }

    /** The certificate of {@code alias} in a keystore (a recipient's public key). */
    public static X509Certificate certificate(Path keystore, char[] storePassword, String alias)
            throws IOException, GeneralSecurityException {
        KeyStore ks = loadKeystore(keystore, storePassword);
        if (!(ks.getCertificate(alias) instanceof X509Certificate certificate)) {
            throw new GeneralSecurityException("No certificate under alias '" + alias + "'");
        }
        return certificate;
    }

    /** Reads the certificates of a PEM or DER file (.cer, .crt, .pem, .der). */
    public static List<X509Certificate> certificates(Path file) throws IOException, GeneralSecurityException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream in = Files.newInputStream(file)) {
            List<X509Certificate> list = new ArrayList<>();
            factory.generateCertificates(in).forEach(c -> list.add((X509Certificate) c));
            if (list.isEmpty()) {
                throw new GeneralSecurityException("No certificate in " + file.getFileName());
            }
            return list;
        }
    }

    /** Reads a public key from PEM: a certificate or a "PUBLIC KEY" block. */
    public static PublicKey publicKey(Path file) throws IOException, GeneralSecurityException {
        Object object = readPem(file);
        if (object instanceof X509CertificateHolder holder) {
            return new JcaX509CertificateConverter().getCertificate(holder).getPublicKey();
        }
        if (object instanceof SubjectPublicKeyInfo info) {
            return new JcaPEMKeyConverter().getPublicKey(info);
        }
        if (object instanceof PEMKeyPair pair) {
            return new JcaPEMKeyConverter().getPublicKey(pair.getPublicKeyInfo());
        }
        // DER certificate
        return certificates(file).get(0).getPublicKey();
    }

    /** Reads a private key from PEM (PKCS#8, traditional RSA/EC, encrypted with {@code password}). */
    public static PrivateKey privateKey(Path file, char[] password) throws IOException, GeneralSecurityException {
        Object object = readPem(file);
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        try {
            if (object instanceof PrivateKeyInfo info) {
                return converter.getPrivateKey(info);
            }
            if (object instanceof PEMKeyPair pair) {
                return converter.getKeyPair(pair).getPrivate();
            }
            if (object instanceof PEMEncryptedKeyPair encrypted) {
                requirePassword(password);
                return converter.getKeyPair(encrypted.decryptKeyPair(
                        new JcePEMDecryptorProviderBuilder().build(password))).getPrivate();
            }
            if (object instanceof PKCS8EncryptedPrivateKeyInfo encrypted) {
                requirePassword(password);
                return converter.getPrivateKey(encrypted.decryptPrivateKeyInfo(
                        new JceOpenSSLPKCS8DecryptorProviderBuilder().build(password)));
            }
        } catch (GeneralSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityException("Cannot read the private key: " + e.getMessage(), e);
        }
        throw new GeneralSecurityException("No private key in " + file.getFileName());
    }

    /**
     * Reads a symmetric key from a file: hex or Base64 text, else the raw bytes. The length must
     * fit {@code algorithm} (16, 24 or 32 bytes for AES; 24 for TripleDES).
     *
     * @param jceAlgorithm "AES", "DESede", "Camellia" or "SEED"
     */
    public static SecretKey symmetricKey(Path file, String jceAlgorithm) throws IOException, GeneralSecurityException {
        byte[] bytes = Files.readAllBytes(file);
        return new SecretKeySpec(decodeKeyBytes(bytes), jceAlgorithm);
    }

    /** Hex or Base64 text, else the bytes as they are. */
    static byte[] decodeKeyBytes(byte[] bytes) throws GeneralSecurityException {
        String text = new String(bytes, StandardCharsets.US_ASCII).strip();
        byte[] key;
        if (text.matches("(?i)[0-9a-f]+") && text.length() % 2 == 0) {
            key = HexFormat.of().parseHex(text);
        } else if (text.matches("[A-Za-z0-9+/=\\s]+")) {
            try {
                key = Base64.getMimeDecoder().decode(text);
            } catch (IllegalArgumentException e) {
                key = bytes;
            }
        } else {
            key = bytes;
        }
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new GeneralSecurityException("A symmetric key has 16, 24 or 32 bytes, this one " + key.length);
        }
        return key;
    }

    private static Object readPem(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        if (!text.contains("-----BEGIN")) {
            return null;
        }
        try (PEMParser parser = new PEMParser(new StringReader(text))) {
            return parser.readObject();
        }
    }

    private static void requirePassword(char[] password) throws GeneralSecurityException {
        if (password == null || password.length == 0) {
            throw new GeneralSecurityException("The private key is encrypted: give its password");
        }
    }
}
