package org.fxt.freexmltoolkit.service.crypto;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xml.security.encryption.AgreementMethod;
import org.apache.xml.security.encryption.EncryptedData;
import org.apache.xml.security.encryption.EncryptedKey;
import org.apache.xml.security.encryption.EncryptionProperties;
import org.apache.xml.security.encryption.EncryptionProperty;
import org.apache.xml.security.encryption.ReferenceList;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.encryption.XMLCipherUtil;
import org.apache.xml.security.encryption.params.ConcatKDFParams;
import org.apache.xml.security.encryption.params.HKDFParams;
import org.apache.xml.security.encryption.params.KeyAgreementParameters;
import org.apache.xml.security.encryption.params.KeyDerivationParameters;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.keys.content.X509Data;
import org.apache.xml.security.utils.EncryptionConstants;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Algorithm;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Kind;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * W3C XML Encryption 1.1 on Apache Santuario: encrypts elements, element content, the whole
 * document or binary data for any number of recipients, decrypts (also super-encrypted data and
 * EncryptedKeys referenced from elsewhere) and describes encrypted documents without a key.
 *
 * <p>Recipients: public keys (RSA key transport, ECDH-ES / X25519 / X448 key agreement with
 * ConcatKDF or HKDF), symmetric keys (key wrap, or the data key itself) and passwords (PBKDF2
 * {@code xenc11:DerivedKey}, implemented here as Santuario has no PBKDF2). Decryption runs with
 * Santuario's secure validation; legacy algorithms follow the administrator policy.</p>
 */
public final class XmlEncryptionService {

    private static final Logger logger = LogManager.getLogger(XmlEncryptionService.class);

    /** Namespaces. */
    public static final String XENC_NS = EncryptionConstants.EncryptionSpecNS;
    public static final String XENC11_NS = EncryptionConstants.EncryptionSpec11NS;
    public static final String DSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
    /** Namespace of the free key/value encryption properties written by FreeXmlToolkit. */
    public static final String PROPERTY_NS = "urn:freexmltoolkit:encryption-property";
    /** PRF URIs of PBKDF2. */
    public static final String HMAC_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#hmac-sha256";
    public static final String HMAC_SHA384 = "http://www.w3.org/2001/04/xmldsig-more#hmac-sha384";
    public static final String HMAC_SHA512 = "http://www.w3.org/2001/04/xmldsig-more#hmac-sha512";
    /** Default PBKDF2 iteration count (OWASP 2023 recommendation for HMAC-SHA256). */
    public static final int DEFAULT_ITERATIONS = 600_000;
    /** Upper bounds against crafted documents. */
    static final int MAX_ENCRYPTED_DATA = 10_000;
    static final int MAX_KEYS_PER_DATA = 64;
    static final int MAX_ITERATIONS = 10_000_000;
    static final int MAX_ROUNDS = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        // no &#xD; line breaks inside Base64 values
        System.setProperty("org.apache.xml.security.ignoreLineBreaks", "true");
        org.apache.xml.security.Init.init();
        // Camellia and SEED (data and key wrap) come from BouncyCastle
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    /** What is encrypted of a target element. */
    public enum TargetType {
        /** The element with its tags. */
        ELEMENT,
        /** Only its content (children), the element stays. */
        CONTENT
    }

    /** Which X509 information the EncryptedKey names for a certificate recipient. */
    public enum X509Reference {
        CERTIFICATE("Whole certificate"), ISSUER_SERIAL("Issuer and serial number"), SKI("Subject key identifier"),
        SUBJECT_NAME("Subject name"), NONE("None");

        private final String label;

        X509Reference(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** One recipient of the data key. */
    public sealed interface Recipient permits PublicKeyRecipient, SymmetricRecipient, PasswordRecipient {
        /** @return the name written as {@code Recipient} attribute of its EncryptedKey, or {@code null} */
        String name();
    }

    /**
     * A recipient's public key (from a certificate or a key file).
     *
     * @param method     key transport (RSA) or key agreement (ECDH-ES, X25519, X448, DH)
     * @param oaepDigest digest of RSA-OAEP 1.1 ({@code null}: SHA-256), MGF of RSA-OAEP 1.1 in {@code mgf}
     * @param keyWrap    key wrap after a key agreement ({@code null}: AES matching the data key)
     * @param kdf        CONCAT_KDF or HKDF after a key agreement ({@code null}: ConcatKDF)
     * @param kdfDigest  digest of the KDF ({@code null}: SHA-256)
     */
    public record PublicKeyRecipient(PublicKey key, X509Certificate certificate, Algorithm method, Algorithm oaepDigest,
                                     Algorithm mgf, Algorithm keyWrap, Algorithm kdf, Algorithm kdfDigest,
                                     X509Reference x509, String name) implements Recipient {

        /** A certificate with the default method for its key (RSA-OAEP 1.1 or ECDH-ES). */
        public static PublicKeyRecipient of(X509Certificate certificate) {
            return of(certificate.getPublicKey(), certificate);
        }

        /** A public key with the default method for it. */
        public static PublicKeyRecipient of(PublicKey key, X509Certificate certificate) {
            Algorithm method = defaultMethodFor(key);
            return new PublicKeyRecipient(key, certificate, method, null, null, null, null, null,
                    certificate != null ? X509Reference.ISSUER_SERIAL : X509Reference.NONE, null);
        }
    }

    /**
     * A shared symmetric key.
     *
     * @param keyWrap {@code null}: the key is the data key itself (only one recipient then)
     * @param keyName written as {@code ds:KeyName}, so the receiver knows which key to take
     */
    public record SymmetricRecipient(SecretKey key, String keyName, Algorithm keyWrap, String name) implements Recipient {
    }

    /**
     * A password: PBKDF2 derives the key-encryption key ({@code xenc11:DerivedKey}).
     *
     * @param prf HMAC URI ({@code null}: HMAC-SHA256)
     */
    public record PasswordRecipient(char[] password, Algorithm keyWrap, int iterations, String prf, String name)
            implements Recipient {
    }

    /**
     * What to encrypt and how.
     *
     * @param xpaths      XPath 1.0 expressions selecting the elements (empty: the document element)
     * @param namespaces  prefixes for the XPaths (the document element's declarations are added)
     * @param type        element or content
     * @param data        block cipher of the data
     * @param recipients  at least one
     * @param properties  free key/value pairs written as EncryptionProperties
     * @param keysOutside EncryptedKeys next to the EncryptedData (with ReferenceList) instead of inside its KeyInfo
     */
    public record EncryptionRequest(List<String> xpaths, Map<String, String> namespaces, TargetType type,
                                    Algorithm data, List<Recipient> recipients, Map<String, String> properties,
                                    boolean keysOutside) {

        public EncryptionRequest {
            xpaths = xpaths == null ? List.of() : List.copyOf(xpaths);
            namespaces = namespaces == null ? Map.of() : Map.copyOf(namespaces);
            type = type == null ? TargetType.ELEMENT : type;
            data = data == null ? XmlEncryptionAlgorithms.DEFAULT_DATA : data;
            recipients = recipients == null ? List.of() : List.copyOf(recipients);
            properties = properties == null ? Map.of() : new LinkedHashMap<>(properties);
        }

        /** The whole document element for these recipients with the recommended algorithms. */
        public static EncryptionRequest document(List<Recipient> recipients) {
            return new EncryptionRequest(List.of(), Map.of(), TargetType.ELEMENT, null, recipients, Map.of(), false);
        }
    }

    /**
     * The keys available for decryption: private keys (RSA, EC, XDH, DH), symmetric keys by
     * name (a {@code null} or blank name matches any EncryptedData) and a password.
     */
    public record DecryptionKeys(List<PrivateKey> privateKeys, List<NamedKey> secretKeys, char[] password) {

        public DecryptionKeys {
            privateKeys = privateKeys == null ? List.of() : List.copyOf(privateKeys);
            secretKeys = secretKeys == null ? List.of() : List.copyOf(secretKeys);
        }

        public static DecryptionKeys privateKey(PrivateKey key) {
            return new DecryptionKeys(List.of(key), List.of(), null);
        }

        public static DecryptionKeys secretKey(String name, SecretKey key) {
            return new DecryptionKeys(List.of(), List.of(new NamedKey(name, key)), null);
        }

        public static DecryptionKeys password(char[] password) {
            return new DecryptionKeys(List.of(), List.of(), password);
        }

        boolean isEmpty() {
            return privateKeys.isEmpty() && secretKeys.isEmpty() && (password == null || password.length == 0);
        }
    }

    /**
     * A symmetric key with the KeyName it stands for ({@code null} or blank: any).
     */
    public record NamedKey(String name, SecretKey key) {
        public NamedKey {
            name = name == null ? "" : name;
        }
    }

    /** Result kinds of a decryption. */
    public enum DecryptionStatus { DECRYPTED, PARTIAL, NO_ENCRYPTED_DATA, WRONG_KEY, UNSUPPORTED, POLICY_BLOCKED, ERROR }

    /**
     * The outcome of a decryption.
     *
     * @param decrypted EncryptedData elements decrypted (super-encrypted layers count each)
     * @param remaining EncryptedData elements left (no matching key, or not selected)
     * @param warnings  legacy algorithms met
     */
    public record DecryptionOutcome(DecryptionStatus status, String message, int decrypted, int remaining,
                                    List<String> warnings, Throwable cause) {
        public boolean isSuccess() {
            return status == DecryptionStatus.DECRYPTED || status == DecryptionStatus.PARTIAL;
        }
    }

    /** The outcome of an encryption: how many EncryptedData elements were written, and warnings. */
    public record EncryptionOutcome(int encrypted, List<String> warnings) {
    }

    /** Raised for a request that cannot be carried out (no target, policy, unsuitable key, …). */
    public static final class XmlEncryptionException extends Exception {
        private final DecryptionStatus status;

        public XmlEncryptionException(String message) {
            this(message, DecryptionStatus.ERROR, null);
        }

        public XmlEncryptionException(String message, DecryptionStatus status, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public DecryptionStatus status() {
            return status;
        }
    }

    // ------------------------------------------------------------------ encryption

    /**
     * Encrypts the request's targets in place.
     *
     * @return how many EncryptedData elements were written, with warnings about legacy algorithms
     */
    public EncryptionOutcome encrypt(Document document, EncryptionRequest request) throws XmlEncryptionException {
        requireAllowed();
        if (request.recipients().isEmpty()) {
            throw new XmlEncryptionException("Add at least one recipient (certificate, key or password)");
        }
        List<String> warnings = new ArrayList<>();
        checkPolicy(request.data(), warnings);
        long direct = request.recipients().stream()
                .filter(r -> r instanceof SymmetricRecipient s && s.keyWrap() == null).count();
        if (direct > 0 && request.recipients().size() > 1) {
            throw new XmlEncryptionException("A symmetric key used directly as data key cannot be combined with other recipients");
        }
        List<Element> targets = targets(document, request);
        int n = 0;
        int sequence = nextId(document);
        for (Element target : targets) {
            String dataId = "ED-" + (sequence++);
            try {
                SecretKey dataKey = direct > 0
                        ? asDataKey(((SymmetricRecipient) request.recipients().get(0)).key(), request.data())
                        : generateKey(request.data());
                XMLCipher cipher = XMLCipher.getInstance(request.data().uri());
                cipher.init(XMLCipher.ENCRYPT_MODE, dataKey);
                EncryptedData encryptedData = cipher.getEncryptedData();
                encryptedData.setId(dataId);
                KeyInfo keyInfo = new KeyInfo(document);
                List<EncryptedKey> outside = new ArrayList<>();
                if (direct > 0) {
                    String keyName = ((SymmetricRecipient) request.recipients().get(0)).keyName();
                    if (keyName != null && !keyName.isBlank()) {
                        keyInfo.addKeyName(keyName);
                    }
                } else {
                    int k = 0;
                    for (Recipient recipient : request.recipients()) {
                        EncryptedKey encryptedKey = encryptKey(document, dataKey, request.data(), recipient, warnings);
                        String keyId = dataId + "-K" + (++k);
                        encryptedKey.setId(keyId);
                        if (request.keysOutside()) {
                            ReferenceList references = cipher.createReferenceList(ReferenceList.DATA_REFERENCE);
                            references.add(references.newDataReference("#" + dataId));
                            encryptedKey.setReferenceList(references);
                            outside.add(encryptedKey);
                            keyInfo.addRetrievalMethod("#" + keyId, null, "http://www.w3.org/2001/04/xmlenc#EncryptedKey");
                        } else {
                            keyInfo.add(encryptedKey);
                        }
                    }
                }
                if (!keyInfo.isEmpty()) {
                    encryptedData.setKeyInfo(keyInfo);
                }
                if (!request.properties().isEmpty()) {
                    encryptedData.setEncryptionProperties(properties(document, cipher, dataId, request.properties()));
                }
                boolean content = request.type() == TargetType.CONTENT;
                Node parent = target.getParentNode();
                Node next = content ? null : target.getNextSibling();
                cipher.doFinal(document, target, content);
                if (!outside.isEmpty()) {
                    Element written = content ? lastEncryptedDataIn(target)
                            : previousElement(parent, next);
                    insertKeys(document, cipher, outside, written);
                }
                n++;
            } catch (XmlEncryptionException e) {
                throw e;
            } catch (Exception e) {
                throw new XmlEncryptionException("Encryption failed: " + message(e), DecryptionStatus.ERROR, e);
            }
        }
        return new EncryptionOutcome(n, List.copyOf(new java.util.LinkedHashSet<>(warnings)));
    }

    /**
     * Encrypts arbitrary bytes: a new document whose root is the EncryptedData (Type omitted,
     * MimeType and Encoding as given).
     */
    public Document encryptBinary(byte[] data, String mimeType, EncryptionRequest request) throws XmlEncryptionException {
        requireAllowed();
        List<String> warnings = new ArrayList<>();
        checkPolicy(request.data(), warnings);
        if (request.recipients().isEmpty()) {
            throw new XmlEncryptionException("Add at least one recipient (certificate, key or password)");
        }
        try {
            Document document = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory(true)
                    .newDocumentBuilder().newDocument();
            boolean direct = request.recipients().get(0) instanceof SymmetricRecipient s && s.keyWrap() == null;
            SecretKey dataKey = direct ? asDataKey(((SymmetricRecipient) request.recipients().get(0)).key(), request.data())
                    : generateKey(request.data());
            XMLCipher cipher = XMLCipher.getInstance(request.data().uri());
            cipher.init(XMLCipher.ENCRYPT_MODE, dataKey);
            EncryptedData encryptedData = cipher.encryptData(document, null, new java.io.ByteArrayInputStream(data));
            encryptedData.setId("ED-1");
            if (mimeType != null && !mimeType.isBlank()) {
                encryptedData.setMimeType(mimeType);
            }
            KeyInfo keyInfo = new KeyInfo(document);
            if (direct) {
                String keyName = ((SymmetricRecipient) request.recipients().get(0)).keyName();
                if (keyName != null && !keyName.isBlank()) {
                    keyInfo.addKeyName(keyName);
                }
            } else {
                int k = 0;
                for (Recipient recipient : request.recipients()) {
                    EncryptedKey encryptedKey = encryptKey(document, dataKey, request.data(), recipient, warnings);
                    encryptedKey.setId("ED-1-K" + (++k));
                    keyInfo.add(encryptedKey);
                }
            }
            if (!keyInfo.isEmpty()) {
                encryptedData.setKeyInfo(keyInfo);
            }
            if (!request.properties().isEmpty()) {
                encryptedData.setEncryptionProperties(properties(document, cipher, "ED-1", request.properties()));
            }
            document.appendChild(cipher.martial(document, encryptedData));
            return document;
        } catch (XmlEncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new XmlEncryptionException("Encryption failed: " + message(e), DecryptionStatus.ERROR, e);
        }
    }

    private EncryptedKey encryptKey(Document document, SecretKey dataKey, Algorithm data, Recipient recipient,
                                    List<String> warnings) throws Exception {
        EncryptedKey encryptedKey = switch (recipient) {
            case PublicKeyRecipient p -> encryptForPublicKey(document, dataKey, data, p, warnings);
            case SymmetricRecipient s -> {
                Algorithm wrap = s.keyWrap();
                checkPolicy(wrap, warnings);
                XMLCipher wrapper = XMLCipher.getInstance(wrap.uri());
                wrapper.init(XMLCipher.WRAP_MODE, new SecretKeySpec(s.key().getEncoded(), wrap.jceKeyAlgorithm()));
                EncryptedKey ek = wrapper.encryptKey(document, dataKey);
                if (s.keyName() != null && !s.keyName().isBlank()) {
                    KeyInfo info = new KeyInfo(document);
                    info.addKeyName(s.keyName());
                    ek.setKeyInfo(info);
                }
                yield ek;
            }
            case PasswordRecipient p -> encryptForPassword(document, dataKey, data, p, warnings);
        };
        if (recipient.name() != null && !recipient.name().isBlank()) {
            encryptedKey.setRecipient(recipient.name());
        }
        return encryptedKey;
    }

    private EncryptedKey encryptForPublicKey(Document document, SecretKey dataKey, Algorithm data,
                                             PublicKeyRecipient p, List<String> warnings) throws Exception {
        Algorithm method = p.method() != null ? p.method() : defaultMethodFor(p.key());
        checkPolicy(method, warnings);
        EncryptedKey encryptedKey;
        if (method.kind() == Kind.KEY_TRANSPORT) {
            if (!(p.key() instanceof RSAKey)) {
                throw new XmlEncryptionException(method.label() + " needs an RSA key, this one is " + p.key().getAlgorithm());
            }
            XMLCipher transport;
            if (method == Algorithm.RSA_OAEP_11 || method == Algorithm.RSA_OAEP_MGF1P) {
                Algorithm digest = p.oaepDigest() != null ? p.oaepDigest()
                        : method == Algorithm.RSA_OAEP_MGF1P ? Algorithm.SHA1 : Algorithm.SHA256;
                Algorithm mgf = method == Algorithm.RSA_OAEP_MGF1P ? null
                        : p.mgf() != null ? p.mgf() : Algorithm.MGF1_SHA256;
                transport = XMLCipher.getInstance(method.uri(), null, digest.uri());
                transport.init(XMLCipher.WRAP_MODE, p.key());
                AlgorithmParameterSpec oaep = XMLCipherUtil.constructOAEPParameters(method.uri(), digest.uri(),
                        mgf == null ? null : mgf.uri(), null);
                encryptedKey = transport.encryptKey(document, dataKey, oaep, null);
                if (mgf != null) {
                    encryptedKey.getEncryptionMethod().setMGFAlgorithm(mgf.uri());
                }
            } else {
                transport = XMLCipher.getInstance(method.uri());
                transport.init(XMLCipher.WRAP_MODE, p.key());
                encryptedKey = transport.encryptKey(document, dataKey);
            }
            KeyInfo info = x509KeyInfo(document, p);
            if (info != null) {
                encryptedKey.setKeyInfo(info);
            }
            return encryptedKey;
        }
        if (method.kind() != Kind.KEY_AGREEMENT) {
            throw new XmlEncryptionException(method.label() + " is no key transport or key agreement");
        }
        Algorithm wrap = p.keyWrap() != null ? p.keyWrap() : XmlEncryptionAlgorithms.keyWrapFor(data.keyBits());
        checkPolicy(wrap, warnings);
        Algorithm kdf = p.kdf() != null ? p.kdf() : Algorithm.CONCAT_KDF;
        Algorithm digest = p.kdfDigest() != null ? p.kdfDigest() : Algorithm.SHA256;
        KeyDerivationParameters derivation = kdf == Algorithm.HKDF
                ? HKDFParams.createBuilder(wrap.keyBits(), hmacFor(digest)).salt(random(32)).build()
                : ConcatKDFParams.createBuilder(wrap.keyBits(), digest.uri()).algorithmID("0000")
                        .partyUInfo("").partyVInfo("").build();
        KeyAgreementParameters agreement = XMLCipherUtil.constructAgreementParameters(method.uri(),
                KeyAgreementParameters.ActorType.ORIGINATOR, derivation, null, p.key());
        XMLCipher wrapper = XMLCipher.getInstance(wrap.uri());
        wrapper.init(XMLCipher.WRAP_MODE, null);
        return wrapper.encryptKey(document, dataKey, agreement, null);
    }

    private EncryptedKey encryptForPassword(Document document, SecretKey dataKey, Algorithm data,
                                            PasswordRecipient p, List<String> warnings) throws Exception {
        if (p.password() == null || p.password().length == 0) {
            throw new XmlEncryptionException("The password is empty");
        }
        Algorithm wrap = p.keyWrap() != null ? p.keyWrap() : XmlEncryptionAlgorithms.keyWrapFor(data.keyBits());
        checkPolicy(wrap, warnings);
        int iterations = p.iterations() > 0 ? p.iterations() : DEFAULT_ITERATIONS;
        String prf = p.prf() != null ? p.prf() : HMAC_SHA256;
        byte[] salt = random(16);
        int keyBytes = wrap.keyBits() / 8;
        SecretKey kek = pbkdf2(p.password(), salt, iterations, keyBytes, prf, wrap.jceKeyAlgorithm());
        XMLCipher wrapper = XMLCipher.getInstance(wrap.uri());
        wrapper.init(XMLCipher.WRAP_MODE, kek);
        EncryptedKey encryptedKey = wrapper.encryptKey(document, dataKey);
        KeyInfo info = new KeyInfo(document);
        info.addUnknownElement(derivedKeyElement(document, salt, iterations, keyBytes, prf));
        encryptedKey.setKeyInfo(info);
        return encryptedKey;
    }

    // ------------------------------------------------------------------ decryption

    /**
     * Decrypts the EncryptedData elements of {@code document} in place, layer by layer (super
     * encryption), with the keys given. EncryptedKeys used from outside an EncryptedData are
     * removed afterwards.
     *
     * @param onlyIds only EncryptedData with these Ids (empty: all)
     */
    public DecryptionOutcome decrypt(Document document, DecryptionKeys keys, Set<String> onlyIds) {
        try {
            requireAllowed();
        } catch (XmlEncryptionException e) {
            return new DecryptionOutcome(DecryptionStatus.POLICY_BLOCKED, e.getMessage(), 0, count(document), List.of(), null);
        }
        if (count(document) == 0) {
            return new DecryptionOutcome(DecryptionStatus.NO_ENCRYPTED_DATA, "The document holds no EncryptedData",
                    0, 0, List.of(), null);
        }
        if (keys == null || keys.isEmpty()) {
            return new DecryptionOutcome(DecryptionStatus.WRONG_KEY, "Give a private key, a symmetric key or a password",
                    0, count(document), List.of(), null);
        }
        List<String> warnings = new ArrayList<>();
        Set<String> selected = onlyIds == null ? Set.of() : onlyIds;
        int decrypted = 0;
        XmlEncryptionException last = null;
        Set<Element> usedKeys = new HashSet<>();
        Set<Element> failed = new HashSet<>();
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<Element> pending = encryptedData(document).stream()
                    .filter(e -> !failed.contains(e))
                    .filter(e -> selected.isEmpty() || selected.contains(e.getAttribute("Id")))
                    .toList();
            if (pending.isEmpty()) {
                break;
            }
            if (pending.size() > MAX_ENCRYPTED_DATA) {
                return new DecryptionOutcome(DecryptionStatus.ERROR, "Too many EncryptedData elements ("
                        + pending.size() + ")", decrypted, count(document), warnings, null);
            }
            for (Element element : pending) {
                try {
                    decryptOne(document, element, keys, warnings, usedKeys);
                    decrypted++;
                } catch (XmlEncryptionException e) {
                    failed.add(element);
                    last = e;
                    if (e.status() == DecryptionStatus.POLICY_BLOCKED || e.status() == DecryptionStatus.UNSUPPORTED) {
                        logger.debug("EncryptedData not decrypted: {}", e.getMessage());
                    }
                }
            }
        }
        for (Element used : usedKeys) {
            if (used.getParentNode() != null && !isInsideKeyInfo(used)) {
                used.getParentNode().removeChild(used);
            }
        }
        int remaining = count(document);
        List<String> distinct = List.copyOf(new java.util.LinkedHashSet<>(warnings));
        if (decrypted == 0) {
            DecryptionStatus status = last != null ? last.status() : DecryptionStatus.WRONG_KEY;
            String message = last != null ? last.getMessage() : "None of the given keys fits";
            return new DecryptionOutcome(status, message, 0, remaining, distinct, last);
        }
        return new DecryptionOutcome(remaining == 0 ? DecryptionStatus.DECRYPTED : DecryptionStatus.PARTIAL,
                remaining == 0 ? "Decrypted " + decrypted + " EncryptedData element(s)"
                        : "Decrypted " + decrypted + ", " + remaining + " EncryptedData element(s) left",
                decrypted, remaining, distinct, null);
    }

    /**
     * Decrypts a document whose root is an EncryptedData of arbitrary (non-XML) data.
     *
     * @return the plain bytes
     */
    public byte[] decryptBinary(Document document, DecryptionKeys keys) throws XmlEncryptionException {
        requireAllowed();
        Element root = document.getDocumentElement();
        if (root == null || !XENC_NS.equals(root.getNamespaceURI()) || !"EncryptedData".equals(root.getLocalName())) {
            throw new XmlEncryptionException("The document is no EncryptedData", DecryptionStatus.NO_ENCRYPTED_DATA, null);
        }
        List<String> warnings = new ArrayList<>();
        Key dataKey = dataKeyFor(document, root, keys, warnings, new HashSet<>());
        try {
            XMLCipher cipher = XMLCipher.getInstance();
            cipher.setSecureValidation(true);
            cipher.init(XMLCipher.DECRYPT_MODE, dataKey);
            return cipher.decryptToByteArray(root);
        } catch (Exception e) {
            throw new XmlEncryptionException("Decryption failed: " + message(e), DecryptionStatus.WRONG_KEY, e);
        }
    }

    private void decryptOne(Document document, Element element, DecryptionKeys keys, List<String> warnings,
                            Set<Element> usedKeys) throws XmlEncryptionException {
        checkCipherReference(element);
        Key dataKey = dataKeyFor(document, element, keys, warnings, usedKeys);
        try {
            XMLCipher cipher = XMLCipher.getInstance();
            cipher.setSecureValidation(true);
            cipher.init(XMLCipher.DECRYPT_MODE, dataKey);
            cipher.doFinal(document, element);
        } catch (Exception e) {
            throw new XmlEncryptionException("Decryption failed (wrong key or damaged data): " + message(e),
                    DecryptionStatus.WRONG_KEY, e);
        }
    }

    /** The data key of one EncryptedData: from one of its EncryptedKeys, or a symmetric key given directly. */
    private Key dataKeyFor(Document document, Element element, DecryptionKeys keys, List<String> warnings,
                           Set<Element> usedKeys) throws XmlEncryptionException {
        String dataAlgorithm = encryptionMethod(element);
        Algorithm data = XmlEncryptionAlgorithms.byUri(dataAlgorithm).orElseThrow(() -> new XmlEncryptionException(
                "Unsupported data algorithm " + dataAlgorithm, DecryptionStatus.UNSUPPORTED, null));
        checkPolicyForDecryption(data, warnings);
        List<Element> encryptedKeys = encryptedKeysFor(document, element);
        if (encryptedKeys.size() > MAX_KEYS_PER_DATA) {
            throw new XmlEncryptionException("Too many EncryptedKeys for one EncryptedData", DecryptionStatus.ERROR, null);
        }
        XmlEncryptionException last = null;
        for (Element ekElement : encryptedKeys) {
            try {
                Key key = unwrap(document, ekElement, data, keys, warnings);
                if (key != null) {
                    usedKeys.add(ekElement);
                    return key;
                }
            } catch (XmlEncryptionException e) {
                last = e;
                if (e.status() == DecryptionStatus.POLICY_BLOCKED) {
                    throw e;
                }
            }
        }
        if (encryptedKeys.isEmpty()) {
            // the data key itself: by KeyName, else every symmetric key of a fitting length
            String keyName = keyName(element);
            for (NamedKey entry : keys.secretKeys()) {
                boolean nameFits = keyName == null || entry.name().isBlank() || entry.name().equals(keyName);
                if (nameFits && entry.key().getEncoded().length * 8 == data.keyBits()) {
                    return asDataKey(entry.key(), data);
                }
            }
            throw new XmlEncryptionException(keyName != null ? "No symmetric key named '" + keyName + "' given"
                    : "The EncryptedData names no key: give the symmetric key", DecryptionStatus.WRONG_KEY, null);
        }
        throw last != null ? last
                : new XmlEncryptionException("None of the given keys opens an EncryptedKey of this EncryptedData",
                        DecryptionStatus.WRONG_KEY, null);
    }

    /** Unwraps one EncryptedKey with the fitting given key; {@code null} when none fits. */
    private Key unwrap(Document document, Element ekElement, Algorithm data, DecryptionKeys keys, List<String> warnings)
            throws XmlEncryptionException {
        String method = encryptionMethod(ekElement);
        Algorithm algorithm = XmlEncryptionAlgorithms.byUri(method).orElseThrow(() -> new XmlEncryptionException(
                "Unsupported key algorithm " + method, DecryptionStatus.UNSUPPORTED, null));
        checkPolicyForDecryption(algorithm, warnings);
        EncryptedKey encryptedKey;
        XMLCipher loader;
        try {
            loader = XMLCipher.getInstance();
            loader.setSecureValidation(true);
            loader.init(XMLCipher.UNWRAP_MODE, null);
            encryptedKey = loader.loadEncryptedKey(document, ekElement);
        } catch (Exception e) {
            throw new XmlEncryptionException("Unreadable EncryptedKey: " + message(e), DecryptionStatus.ERROR, e);
        }
        Element agreement = firstChild(keyInfoOf(ekElement), XENC_NS, "AgreementMethod");
        Element derived = firstChild(keyInfoOf(ekElement), XENC11_NS, "DerivedKey");
        List<Key> candidates = new ArrayList<>();
        if (algorithm.kind() == Kind.KEY_TRANSPORT) {
            keys.privateKeys().stream().filter(k -> k instanceof RSAKey).forEach(candidates::add);
        } else if (agreement != null) {
            checkPolicyForDecryption(XmlEncryptionAlgorithms.byUri(agreement.getAttribute("Algorithm"))
                    .orElseThrow(() -> new XmlEncryptionException("Unsupported key agreement "
                            + agreement.getAttribute("Algorithm"), DecryptionStatus.UNSUPPORTED, null)), warnings);
            keys.privateKeys().stream().filter(k -> !(k instanceof RSAKey)).forEach(candidates::add);
        } else if (derived != null) {
            if (keys.password() != null && keys.password().length > 0) {
                candidates.add(passwordKek(derived, keys.password(), algorithm));
            }
        } else {
            String keyName = keyName(ekElement);
            for (NamedKey entry : keys.secretKeys()) {
                if (keyName == null || entry.name().isBlank() || entry.name().equals(keyName)) {
                    candidates.add(new SecretKeySpec(entry.key().getEncoded(), algorithm.jceKeyAlgorithm()));
                }
            }
        }
        for (Key candidate : candidates) {
            try {
                XMLCipher cipher = XMLCipher.getInstance();
                cipher.setSecureValidation(true);
                cipher.init(XMLCipher.UNWRAP_MODE, candidate);
                Key key = cipher.decryptKey(encryptedKey, data.uri());
                if (key != null && key.getEncoded() != null && key.getEncoded().length * 8 == data.keyBits()) {
                    return key;
                }
            } catch (Exception e) {
                logger.trace("Key does not open EncryptedKey: {}", e.toString());
            }
        }
        return null;
    }

    private static SecretKey passwordKek(Element derivedKey, char[] password, Algorithm wrap) throws XmlEncryptionException {
        Element method = firstChild(derivedKey, XENC11_NS, "KeyDerivationMethod");
        if (method == null || !Algorithm.PBKDF2.uri().equals(method.getAttribute("Algorithm"))) {
            throw new XmlEncryptionException("Unsupported key derivation "
                    + (method == null ? "(none)" : method.getAttribute("Algorithm")), DecryptionStatus.UNSUPPORTED, null);
        }
        Element params = firstChild(method, XENC11_NS, "PBKDF2-params");
        Element salt = params == null ? null : firstChild(firstChild(params, XENC11_NS, "Salt"), XENC11_NS, "Specified");
        Element count = params == null ? null : firstChild(params, XENC11_NS, "IterationCount");
        Element length = params == null ? null : firstChild(params, XENC11_NS, "KeyLength");
        Element prf = params == null ? null : firstChild(params, XENC11_NS, "PRF");
        if (salt == null || count == null) {
            throw new XmlEncryptionException("Incomplete PBKDF2 parameters", DecryptionStatus.ERROR, null);
        }
        int iterations;
        int keyBytes;
        try {
            iterations = Integer.parseInt(count.getTextContent().trim());
            keyBytes = length != null ? Integer.parseInt(length.getTextContent().trim()) : wrap.keyBits() / 8;
        } catch (NumberFormatException e) {
            throw new XmlEncryptionException("Invalid PBKDF2 parameters", DecryptionStatus.ERROR, e);
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS || keyBytes < 16 || keyBytes > 64) {
            throw new XmlEncryptionException("PBKDF2 parameters out of range", DecryptionStatus.ERROR, null);
        }
        try {
            return pbkdf2(password, Base64.getMimeDecoder().decode(salt.getTextContent().trim()), iterations, keyBytes,
                    prf != null ? prf.getAttribute("Algorithm") : HMAC_SHA256, wrap.jceKeyAlgorithm());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new XmlEncryptionException("PBKDF2 failed: " + message(e), DecryptionStatus.UNSUPPORTED, e);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** @return the default key method for a public key: RSA-OAEP 1.1, ECDH-ES, X25519 or X448 */
    public static Algorithm defaultMethodFor(PublicKey key) {
        if (key instanceof RSAKey) {
            return XmlEncryptionAlgorithms.DEFAULT_KEY_TRANSPORT;
        }
        if (key instanceof ECKey) {
            return Algorithm.ECDH_ES;
        }
        String algorithm = key.getAlgorithm();
        if ("X448".equalsIgnoreCase(algorithm)) {
            return Algorithm.X448;
        }
        if ("XDH".equalsIgnoreCase(algorithm) || "X25519".equalsIgnoreCase(algorithm)) {
            return key.getEncoded() != null && key.getEncoded().length > 50 ? Algorithm.X448 : Algorithm.X25519;
        }
        if ("DH".equalsIgnoreCase(algorithm) || "DiffieHellman".equalsIgnoreCase(algorithm)) {
            return Algorithm.DH;
        }
        return XmlEncryptionAlgorithms.DEFAULT_KEY_TRANSPORT;
    }

    /** @return the EncryptedData elements of a document, in document order */
    public static List<Element> encryptedData(Document document) {
        return elements(document, XENC_NS, "EncryptedData");
    }

    /** @return the number of EncryptedData elements */
    public static int count(Document document) {
        return document == null ? 0 : encryptedData(document).size();
    }

    static SecretKey pbkdf2(char[] password, byte[] salt, int iterations, int keyBytes, String prf, String jceAlgorithm)
            throws GeneralSecurityException {
        String mac = switch (prf) {
            case HMAC_SHA512 -> "PBKDF2WithHmacSHA512";
            case HMAC_SHA384 -> "PBKDF2WithHmacSHA384";
            case HMAC_SHA256 -> "PBKDF2WithHmacSHA256";
            default -> throw new GeneralSecurityException("Unsupported PBKDF2 PRF " + prf);
        };
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBytes * 8);
        try {
            byte[] key = SecretKeyFactory.getInstance(mac).generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, jceAlgorithm);
        } finally {
            spec.clearPassword();
        }
    }

    private static Element derivedKeyElement(Document document, byte[] salt, int iterations, int keyBytes, String prf) {
        Element derived = document.createElementNS(XENC11_NS, "xenc11:DerivedKey");
        derived.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xenc11", XENC11_NS);
        Element method = child(document, derived, "KeyDerivationMethod");
        method.setAttribute("Algorithm", Algorithm.PBKDF2.uri());
        Element params = child(document, method, "PBKDF2-params");
        Element saltElement = child(document, params, "Salt");
        child(document, saltElement, "Specified").setTextContent(Base64.getEncoder().encodeToString(salt));
        child(document, params, "IterationCount").setTextContent(String.valueOf(iterations));
        child(document, params, "KeyLength").setTextContent(String.valueOf(keyBytes));
        child(document, params, "PRF").setAttribute("Algorithm", prf);
        return derived;
    }

    private static Element child(Document document, Element parent, String localName) {
        Element element = document.createElementNS(XENC11_NS, "xenc11:" + localName);
        parent.appendChild(element);
        return element;
    }

    private static String hmacFor(Algorithm digest) {
        return switch (digest) {
            case SHA512 -> HMAC_SHA512;
            case SHA384 -> HMAC_SHA384;
            default -> HMAC_SHA256;
        };
    }

    private static KeyInfo x509KeyInfo(Document document, PublicKeyRecipient p) throws Exception {
        if (p.certificate() == null || p.x509() == null || p.x509() == X509Reference.NONE) {
            return null;
        }
        KeyInfo info = new KeyInfo(document);
        X509Data x509 = new X509Data(document);
        X509Certificate certificate = p.certificate();
        switch (p.x509()) {
            case CERTIFICATE -> x509.addCertificate(certificate);
            case ISSUER_SERIAL -> x509.addIssuerSerial(certificate.getIssuerX500Principal().getName(),
                    certificate.getSerialNumber());
            case SKI -> x509.addSKI(certificate);
            case SUBJECT_NAME -> x509.addSubjectName(certificate.getSubjectX500Principal().getName());
            default -> {
                return null;
            }
        }
        info.add(x509);
        return info;
    }

    private static EncryptionProperties properties(Document document, XMLCipher cipher, String dataId,
                                                   Map<String, String> values) {
        EncryptionProperties properties = cipher.createEncryptionProperties();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            EncryptionProperty property = cipher.createEncryptionProperty();
            property.setTarget("#" + dataId);
            Element value = document.createElementNS(PROPERTY_NS, "p:Property");
            value.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:p", PROPERTY_NS);
            value.setAttribute("Name", entry.getKey());
            value.setTextContent(entry.getValue());
            property.addEncryptionInformation(value);
            properties.addEncryptionProperty(property);
        }
        return properties;
    }

    private static void insertKeys(Document document, XMLCipher cipher, List<EncryptedKey> keys, Element after) {
        if (after == null) {
            return;
        }
        Node anchor = after;
        for (EncryptedKey key : keys) {
            Element element = cipher.martial(document, key);
            anchor.getParentNode().insertBefore(element, anchor.getNextSibling());
            anchor = element;
        }
    }

    private static Element lastEncryptedDataIn(Element parent) {
        Element found = null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && XENC_NS.equals(e.getNamespaceURI()) && "EncryptedData".equals(e.getLocalName())) {
                found = e;
            }
        }
        return found;
    }

    private static Element previousElement(Node parent, Node next) {
        Node n = next == null ? parent.getLastChild() : next.getPreviousSibling();
        while (n != null && !(n instanceof Element)) {
            n = n.getPreviousSibling();
        }
        return (Element) n;
    }

    private List<Element> targets(Document document, EncryptionRequest request) throws XmlEncryptionException {
        if (request.xpaths().isEmpty()) {
            Element root = document.getDocumentElement();
            if (root == null) {
                throw new XmlEncryptionException("The document is empty");
            }
            return List.of(root);
        }
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(namespaces(document, request.namespaces()));
        List<Element> found = new ArrayList<>();
        for (String expression : request.xpaths()) {
            try {
                NodeList nodes = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
                for (int i = 0; i < nodes.getLength(); i++) {
                    if (nodes.item(i) instanceof Element e && !found.contains(e)) {
                        found.add(e);
                    }
                }
            } catch (XPathExpressionException e) {
                throw new XmlEncryptionException("Invalid XPath '" + expression + "': " + message(e));
            }
        }
        // an element inside another selected one is encrypted with its ancestor
        found.removeIf(e -> found.stream().anyMatch(other -> other != e && isAncestor(other, e)));
        if (found.isEmpty()) {
            throw new XmlEncryptionException("The XPath selects no element");
        }
        return found;
    }

    private static boolean isAncestor(Node ancestor, Node node) {
        for (Node n = node.getParentNode(); n != null; n = n.getParentNode()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static NamespaceContext namespaces(Document document, Map<String, String> given) {
        Map<String, String> map = new HashMap<>();
        Element root = document.getDocumentElement();
        if (root != null) {
            var attributes = root.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node a = attributes.item(i);
                if ("xmlns".equals(a.getPrefix())) {
                    map.put(a.getLocalName(), a.getNodeValue());
                }
            }
        }
        map.putAll(given);
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return map.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String uri) {
                return map.entrySet().stream().filter(e -> e.getValue().equals(uri)).map(Map.Entry::getKey)
                        .findFirst().orElse(null);
            }

            @Override
            public java.util.Iterator<String> getPrefixes(String uri) {
                return map.entrySet().stream().filter(e -> e.getValue().equals(uri)).map(Map.Entry::getKey).iterator();
            }
        };
    }

    /** EncryptedKeys of an EncryptedData: inside its KeyInfo, via RetrievalMethod, or pointing at it by ReferenceList. */
    private static List<Element> encryptedKeysFor(Document document, Element encryptedData) {
        List<Element> keys = new ArrayList<>();
        Element keyInfo = keyInfoOf(encryptedData);
        if (keyInfo != null) {
            for (Node n = keyInfo.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && XENC_NS.equals(e.getNamespaceURI()) && "EncryptedKey".equals(e.getLocalName())) {
                    keys.add(e);
                } else if (n instanceof Element e && DSIG_NS.equals(e.getNamespaceURI())
                        && "RetrievalMethod".equals(e.getLocalName())) {
                    String uri = e.getAttribute("URI");
                    if (uri.startsWith("#")) {
                        Element target = byId(document, uri.substring(1), XENC_NS, "EncryptedKey");
                        if (target != null && !keys.contains(target)) {
                            keys.add(target);
                        }
                    }
                }
            }
        }
        String id = encryptedData.getAttribute("Id");
        if (!id.isEmpty()) {
            for (Element ek : elements(document, XENC_NS, "EncryptedKey")) {
                Element list = firstChild(ek, XENC_NS, "ReferenceList");
                if (list != null && !keys.contains(ek)) {
                    for (Node n = list.getFirstChild(); n != null; n = n.getNextSibling()) {
                        if (n instanceof Element ref && "DataReference".equals(ref.getLocalName())
                                && ("#" + id).equals(ref.getAttribute("URI"))) {
                            keys.add(ek);
                            break;
                        }
                    }
                }
            }
        }
        return keys;
    }

    private static Element byId(Document document, String id, String ns, String localName) {
        return elements(document, ns, localName).stream().filter(e -> id.equals(e.getAttribute("Id"))).findFirst().orElse(null);
    }

    static List<Element> elements(Document document, String ns, String localName) {
        NodeList list = document.getElementsByTagNameNS(ns, localName);
        List<Element> result = new ArrayList<>(list.getLength());
        for (int i = 0; i < list.getLength(); i++) {
            result.add((Element) list.item(i));
        }
        return result;
    }

    static Element keyInfoOf(Element encryptedType) {
        return firstChild(encryptedType, DSIG_NS, "KeyInfo");
    }

    static Element firstChild(Element parent, String ns, String localName) {
        if (parent == null) {
            return null;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && ns.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    static String encryptionMethod(Element encryptedType) {
        Element method = firstChild(encryptedType, XENC_NS, "EncryptionMethod");
        return method == null ? "" : method.getAttribute("Algorithm");
    }

    static String keyName(Element encryptedType) {
        Element name = firstChild(keyInfoOf(encryptedType), DSIG_NS, "KeyName");
        return name == null ? null : name.getTextContent().trim();
    }

    private static boolean isInsideKeyInfo(Element element) {
        Node parent = element.getParentNode();
        return parent instanceof Element p && DSIG_NS.equals(p.getNamespaceURI()) && "KeyInfo".equals(p.getLocalName());
    }

    /** CipherReference: only a plain file name next to the document (no scheme, no path), like detached signatures. */
    private static void checkCipherReference(Element encryptedData) throws XmlEncryptionException {
        Element cipherData = firstChild(encryptedData, XENC_NS, "CipherData");
        Element reference = firstChild(cipherData, XENC_NS, "CipherReference");
        if (reference == null) {
            return;
        }
        String uri = reference.getAttribute("URI");
        Predicate<String> local = u -> !u.isEmpty() && !u.contains(":") && !u.contains("/") && !u.contains("\\")
                && !u.startsWith(".");
        if (!uri.startsWith("#") && !local.test(uri)) {
            throw new XmlEncryptionException("CipherReference to '" + uri + "' refused: only files next to the document",
                    DecryptionStatus.POLICY_BLOCKED, null);
        }
    }

    private static int nextId(Document document) {
        int max = 0;
        for (Element e : encryptedData(document)) {
            String id = e.getAttribute("Id");
            if (id.startsWith("ED-")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(3).replaceAll("\\D.*", "")));
                } catch (NumberFormatException ignored) {
                    // not one of ours
                }
            }
        }
        return max + 1;
    }

    private static SecretKey generateKey(Algorithm data) throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance(data.jceKeyAlgorithm(), provider(data));
        // TripleDES keys are 192 bits with parity, the generator counts the 168 effective ones
        generator.init("DESede".equals(data.jceKeyAlgorithm()) ? 168 : data.keyBits(), RANDOM);
        return generator.generateKey();
    }

    private static java.security.Provider provider(Algorithm data) {
        String jce = data.jceKeyAlgorithm();
        if ("Camellia".equals(jce) || "SEED".equals(jce)) {
            return java.security.Security.getProvider("BC");
        }
        return java.security.Security.getProviders("KeyGenerator." + jce)[0];
    }

    private static SecretKey asDataKey(SecretKey key, Algorithm data) throws XmlEncryptionException {
        byte[] bytes = key.getEncoded();
        if (bytes == null || bytes.length * 8 != data.keyBits()) {
            throw new XmlEncryptionException(data.label() + " needs a " + data.keyBits() + "-bit key, this one has "
                    + (bytes == null ? 0 : bytes.length * 8) + " bits");
        }
        return new SecretKeySpec(bytes, data.jceKeyAlgorithm());
    }

    private static byte[] random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return value;
    }

    private static void requireAllowed() throws XmlEncryptionException {
        if (!AdminPolicy.current().xmlEncryptionAllowed()) {
            throw new XmlEncryptionException("XML encryption is disabled by the administrator",
                    DecryptionStatus.POLICY_BLOCKED, null);
        }
    }

    private static void checkPolicy(Algorithm algorithm, List<String> warnings) throws XmlEncryptionException {
        if (algorithm == null || !algorithm.legacy()) {
            return;
        }
        if (!AdminPolicy.current().legacyXmlEncryptionAllowed()) {
            throw new XmlEncryptionException(algorithm.label() + " is a legacy algorithm blocked by the administrator",
                    DecryptionStatus.POLICY_BLOCKED, null);
        }
        warnings.add(legacyWarning(algorithm));
    }

    private static void checkPolicyForDecryption(Algorithm algorithm, List<String> warnings) throws XmlEncryptionException {
        checkPolicy(algorithm, warnings);
    }

    /** @return the warning shown for a legacy algorithm */
    public static String legacyWarning(Algorithm algorithm) {
        return switch (algorithm) {
            case RSA_1_5 -> "RSA-1_5 is a legacy key transport (Bleichenbacher padding oracle): use RSA-OAEP";
            case TRIPLEDES_CBC, KW_TRIPLEDES -> algorithm.label() + " is a legacy algorithm (64-bit block size): use AES";
            case DH -> "Finite-field Diffie-Hellman is a legacy key agreement: use ECDH-ES";
            case SHA1 -> "SHA-1 is a legacy digest: use SHA-256";
            default -> algorithm.label() + " has no integrity protection (CBC, padding oracle): use AES-GCM";
        };
    }

    private static String message(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t && (t.getMessage() == null || t.getMessage().isBlank())) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
