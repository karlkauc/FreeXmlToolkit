package org.fxt.freexmltoolkit.service.crypto;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The algorithms of W3C XML Encryption 1.1 (and the xmldsig-more additions Santuario implements)
 * that FreeXmlToolkit offers, with their URIs. Every one can encrypt and decrypt; {@code legacy}
 * ones (RSA-1_5, TripleDES, CBC block ciphers without integrity protection, finite-field DH) are
 * marked so the UI and reports warn, and an administrator can switch them off
 * ({@code xmlenc.legacyAlgorithms.allowed}).
 */
public final class XmlEncryptionAlgorithms {

    private XmlEncryptionAlgorithms() {
    }

    /** Kinds of algorithms. */
    public enum Kind { DATA, KEY_TRANSPORT, KEY_WRAP, KEY_AGREEMENT, KEY_DERIVATION, DIGEST, MGF }

    /** One algorithm: URI, short CLI name, label, key length in bits (0: none), legacy flag. */
    public enum Algorithm {
        // block ciphers (EncryptedData / EncryptedKey with a symmetric KEK is KEY_WRAP)
        AES_128_GCM(Kind.DATA, "aes128-gcm", "AES-128-GCM", "http://www.w3.org/2009/xmlenc11#aes128-gcm", 128, false),
        AES_192_GCM(Kind.DATA, "aes192-gcm", "AES-192-GCM", "http://www.w3.org/2009/xmlenc11#aes192-gcm", 192, false),
        AES_256_GCM(Kind.DATA, "aes256-gcm", "AES-256-GCM", "http://www.w3.org/2009/xmlenc11#aes256-gcm", 256, false),
        AES_128_CBC(Kind.DATA, "aes128-cbc", "AES-128-CBC", "http://www.w3.org/2001/04/xmlenc#aes128-cbc", 128, true),
        AES_192_CBC(Kind.DATA, "aes192-cbc", "AES-192-CBC", "http://www.w3.org/2001/04/xmlenc#aes192-cbc", 192, true),
        AES_256_CBC(Kind.DATA, "aes256-cbc", "AES-256-CBC", "http://www.w3.org/2001/04/xmlenc#aes256-cbc", 256, true),
        TRIPLEDES_CBC(Kind.DATA, "tripledes-cbc", "TripleDES-CBC", "http://www.w3.org/2001/04/xmlenc#tripledes-cbc", 192, true),
        CAMELLIA_128_CBC(Kind.DATA, "camellia128-cbc", "Camellia-128-CBC", "http://www.w3.org/2001/04/xmldsig-more#camellia128-cbc", 128, true),
        CAMELLIA_192_CBC(Kind.DATA, "camellia192-cbc", "Camellia-192-CBC", "http://www.w3.org/2001/04/xmldsig-more#camellia192-cbc", 192, true),
        CAMELLIA_256_CBC(Kind.DATA, "camellia256-cbc", "Camellia-256-CBC", "http://www.w3.org/2001/04/xmldsig-more#camellia256-cbc", 256, true),
        SEED_128_CBC(Kind.DATA, "seed128-cbc", "SEED-128-CBC", "http://www.w3.org/2007/05/xmldsig-more#seed128-cbc", 128, true),

        // key transport
        RSA_OAEP_11(Kind.KEY_TRANSPORT, "rsa-oaep", "RSA-OAEP 1.1", "http://www.w3.org/2009/xmlenc11#rsa-oaep", 0, false),
        RSA_OAEP_MGF1P(Kind.KEY_TRANSPORT, "rsa-oaep-mgf1p", "RSA-OAEP MGF1P", "http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p", 0, false),
        RSA_1_5(Kind.KEY_TRANSPORT, "rsa-1_5", "RSA PKCS#1 v1.5", "http://www.w3.org/2001/04/xmlenc#rsa-1_5", 0, true),

        // symmetric key wrap
        KW_AES_128(Kind.KEY_WRAP, "kw-aes128", "AES-128 Key Wrap", "http://www.w3.org/2001/04/xmlenc#kw-aes128", 128, false),
        KW_AES_192(Kind.KEY_WRAP, "kw-aes192", "AES-192 Key Wrap", "http://www.w3.org/2001/04/xmlenc#kw-aes192", 192, false),
        KW_AES_256(Kind.KEY_WRAP, "kw-aes256", "AES-256 Key Wrap", "http://www.w3.org/2001/04/xmlenc#kw-aes256", 256, false),
        KW_TRIPLEDES(Kind.KEY_WRAP, "kw-tripledes", "TripleDES Key Wrap", "http://www.w3.org/2001/04/xmlenc#kw-tripledes", 192, true),
        KW_CAMELLIA_128(Kind.KEY_WRAP, "kw-camellia128", "Camellia-128 Key Wrap", "http://www.w3.org/2001/04/xmldsig-more#kw-camellia128", 128, true),
        KW_CAMELLIA_192(Kind.KEY_WRAP, "kw-camellia192", "Camellia-192 Key Wrap", "http://www.w3.org/2001/04/xmldsig-more#kw-camellia192", 192, true),
        KW_CAMELLIA_256(Kind.KEY_WRAP, "kw-camellia256", "Camellia-256 Key Wrap", "http://www.w3.org/2001/04/xmldsig-more#kw-camellia256", 256, true),
        KW_SEED_128(Kind.KEY_WRAP, "kw-seed128", "SEED-128 Key Wrap", "http://www.w3.org/2007/05/xmldsig-more#kw-seed128", 128, true),

        // key agreement
        ECDH_ES(Kind.KEY_AGREEMENT, "ecdh-es", "ECDH-ES", "http://www.w3.org/2009/xmlenc11#ECDH-ES", 0, false),
        X25519(Kind.KEY_AGREEMENT, "x25519", "X25519", "http://www.w3.org/2021/04/xmldsig-more#x25519", 0, false),
        X448(Kind.KEY_AGREEMENT, "x448", "X448", "http://www.w3.org/2021/04/xmldsig-more#x448", 0, false),
        DH(Kind.KEY_AGREEMENT, "dh", "Diffie-Hellman", "http://www.w3.org/2001/04/xmlenc#dh", 0, true),

        // key derivation
        CONCAT_KDF(Kind.KEY_DERIVATION, "concatkdf", "ConcatKDF", "http://www.w3.org/2009/xmlenc11#ConcatKDF", 0, false),
        HKDF(Kind.KEY_DERIVATION, "hkdf", "HKDF", "http://www.w3.org/2021/04/xmldsig-more#hkdf", 0, false),
        PBKDF2(Kind.KEY_DERIVATION, "pbkdf2", "PBKDF2", "http://www.w3.org/2009/xmlenc11#pbkdf2", 0, false),

        // digests (OAEP, ConcatKDF) and MGFs (OAEP 1.1)
        SHA1(Kind.DIGEST, "sha1", "SHA-1", "http://www.w3.org/2000/09/xmldsig#sha1", 0, true),
        SHA224(Kind.DIGEST, "sha224", "SHA-224", "http://www.w3.org/2001/04/xmldsig-more#sha224", 0, false),
        SHA256(Kind.DIGEST, "sha256", "SHA-256", "http://www.w3.org/2001/04/xmlenc#sha256", 0, false),
        SHA384(Kind.DIGEST, "sha384", "SHA-384", "http://www.w3.org/2001/04/xmldsig-more#sha384", 0, false),
        SHA512(Kind.DIGEST, "sha512", "SHA-512", "http://www.w3.org/2001/04/xmlenc#sha512", 0, false),
        MGF1_SHA1(Kind.MGF, "mgf1sha1", "MGF1-SHA1", "http://www.w3.org/2009/xmlenc11#mgf1sha1", 0, false),
        MGF1_SHA224(Kind.MGF, "mgf1sha224", "MGF1-SHA224", "http://www.w3.org/2009/xmlenc11#mgf1sha224", 0, false),
        MGF1_SHA256(Kind.MGF, "mgf1sha256", "MGF1-SHA256", "http://www.w3.org/2009/xmlenc11#mgf1sha256", 0, false),
        MGF1_SHA384(Kind.MGF, "mgf1sha384", "MGF1-SHA384", "http://www.w3.org/2009/xmlenc11#mgf1sha384", 0, false),
        MGF1_SHA512(Kind.MGF, "mgf1sha512", "MGF1-SHA512", "http://www.w3.org/2009/xmlenc11#mgf1sha512", 0, false);

        private final Kind kind;
        private final String id;
        private final String label;
        private final String uri;
        private final int keyBits;
        private final boolean legacy;

        Algorithm(Kind kind, String id, String label, String uri, int keyBits, boolean legacy) {
            this.kind = kind;
            this.id = id;
            this.label = label;
            this.uri = uri;
            this.keyBits = keyBits;
            this.legacy = legacy;
        }

        public Kind kind() {
            return kind;
        }

        /** @return the short name used on the command line */
        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public String uri() {
            return uri;
        }

        /** @return the key length in bits, 0 for algorithms without a fixed one */
        public int keyBits() {
            return keyBits;
        }

        /** @return whether the algorithm is kept for old partners only (warned about, can be switched off) */
        public boolean legacy() {
            return legacy;
        }

        /** @return the JCE key algorithm of a block cipher or key wrap ("AES", "DESede", "Camellia", "SEED") */
        public String jceKeyAlgorithm() {
            String u = uri.toLowerCase(Locale.ROOT);
            if (u.contains("tripledes")) {
                return "DESede";
            }
            if (u.contains("camellia")) {
                return "Camellia";
            }
            if (u.contains("seed")) {
                return "SEED";
            }
            return "AES";
        }

        @Override
        public String toString() {
            return legacy ? label + " (legacy)" : label;
        }
    }

    /** The recommended data algorithm. */
    public static final Algorithm DEFAULT_DATA = Algorithm.AES_256_GCM;
    /** The recommended key transport for RSA recipients. */
    public static final Algorithm DEFAULT_KEY_TRANSPORT = Algorithm.RSA_OAEP_11;
    /** The recommended key wrap (symmetric keys, passwords, key agreement). */
    public static final Algorithm DEFAULT_KEY_WRAP = Algorithm.KW_AES_256;

    /** @return the algorithms of one kind, in declaration order */
    public static List<Algorithm> of(Kind kind) {
        return Arrays.stream(Algorithm.values()).filter(a -> a.kind == kind).toList();
    }

    /** @return the algorithm with this URI */
    public static Optional<Algorithm> byUri(String uri) {
        return Arrays.stream(Algorithm.values()).filter(a -> a.uri.equals(uri)).findFirst();
    }

    /** @return the algorithm of {@code kind} with this short name or URI (case-insensitive name) */
    public static Optional<Algorithm> parse(Kind kind, String nameOrUri) {
        if (nameOrUri == null) {
            return Optional.empty();
        }
        String v = nameOrUri.trim();
        return Arrays.stream(Algorithm.values())
                .filter(a -> a.kind == kind && (a.id.equalsIgnoreCase(v) || a.uri.equals(v)))
                .findFirst();
    }

    /** @return the key wrap matching a data key length (AES), for key agreement and passwords */
    public static Algorithm keyWrapFor(int keyBits) {
        return switch (keyBits) {
            case 128 -> Algorithm.KW_AES_128;
            case 192 -> Algorithm.KW_AES_192;
            default -> Algorithm.KW_AES_256;
        };
    }
}
