package org.fxt.freexmltoolkit.service.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Algorithm;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.DecryptionKeys;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.DecryptionStatus;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.EncryptionRequest;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.PasswordRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.PublicKeyRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.Recipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.SymmetricRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.TargetType;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.X509Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;

/** Round trips of W3C XML Encryption 1.1 through {@link XmlEncryptionService}, for every algorithm. */
class XmlEncryptionServiceTest {

    private static final String XML = """
            <order xmlns="urn:shop" xmlns:p="urn:pay"><customer>Ada</customer>\
            <p:card number="4111"><p:holder>Ada Lovelace</p:holder></p:card><p:card number="5500"/></order>""";

    private static KeyPair rsa;
    private static KeyPair ec;
    private static KeyPair x25519;
    private static KeyPair x448;
    private static final XmlEncryptionService service = new XmlEncryptionService();

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator r = KeyPairGenerator.getInstance("RSA");
        r.initialize(2048);
        rsa = r.generateKeyPair();
        KeyPairGenerator e = KeyPairGenerator.getInstance("EC");
        e.initialize(new ECGenParameterSpec("secp384r1"));
        ec = e.generateKeyPair();
        x25519 = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        x448 = KeyPairGenerator.getInstance("X448").generateKeyPair();
    }

    static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    static String serialize(Document d) throws Exception {
        var t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty("omit-xml-declaration", "yes");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(d), new StreamResult(w));
        return w.toString();
    }

    /** Encrypts, serializes, parses again and decrypts: the document is back. */
    private static String roundTrip(EncryptionRequest request, DecryptionKeys keys) throws Exception {
        Document document = parse(XML);
        var outcome = service.encrypt(document, request);
        assertTrue(outcome.encrypted() > 0);
        String encrypted = serialize(document);
        assertFalse(encrypted.contains("Ada Lovelace"), "the card is encrypted: " + encrypted);
        Document again = parse(encrypted);
        var result = service.decrypt(again, keys, Set.of());
        assertEquals(DecryptionStatus.DECRYPTED, result.status(), result.message());
        assertEquals(0, XmlEncryptionService.count(again));
        assertEquals(serialize(parse(XML)), serialize(again));
        return encrypted;
    }

    private static EncryptionRequest cards(Algorithm data, TargetType type, Recipient... recipients) {
        return new EncryptionRequest(List.of("//p:card"), Map.of(), type, data, List.of(recipients), Map.of(), false);
    }

    static Stream<Algorithm> dataAlgorithms() {
        return XmlEncryptionAlgorithms.of(XmlEncryptionAlgorithms.Kind.DATA).stream();
    }

    @ParameterizedTest
    @MethodSource("dataAlgorithms")
    void everyDataAlgorithmWithRsaOaep(Algorithm data) throws Exception {
        String encrypted = roundTrip(cards(data, TargetType.ELEMENT, PublicKeyRecipient.of(rsa.getPublic(), null)),
                DecryptionKeys.privateKey(rsa.getPrivate()));
        assertTrue(encrypted.contains(data.uri()), encrypted);
    }

    static Stream<Arguments> rsaVariants() {
        return Stream.of(
                Arguments.of(Algorithm.RSA_OAEP_11, Algorithm.SHA256, Algorithm.MGF1_SHA256),
                Arguments.of(Algorithm.RSA_OAEP_11, Algorithm.SHA512, Algorithm.MGF1_SHA512),
                Arguments.of(Algorithm.RSA_OAEP_11, Algorithm.SHA384, Algorithm.MGF1_SHA1),
                Arguments.of(Algorithm.RSA_OAEP_11, Algorithm.SHA1, Algorithm.MGF1_SHA224),
                Arguments.of(Algorithm.RSA_OAEP_MGF1P, null, null),
                Arguments.of(Algorithm.RSA_OAEP_MGF1P, Algorithm.SHA256, null),
                Arguments.of(Algorithm.RSA_1_5, null, null));
    }

    @ParameterizedTest
    @MethodSource("rsaVariants")
    void rsaKeyTransportVariants(Algorithm method, Algorithm digest, Algorithm mgf) throws Exception {
        var recipient = new PublicKeyRecipient(rsa.getPublic(), null, method, digest, mgf, null, null, null,
                X509Reference.NONE, "bob");
        String encrypted = roundTrip(cards(Algorithm.AES_128_GCM, TargetType.CONTENT, recipient),
                DecryptionKeys.privateKey(rsa.getPrivate()));
        assertTrue(encrypted.contains(method.uri()) && encrypted.contains("Recipient=\"bob\""), encrypted);
        if (mgf != null) {
            assertTrue(encrypted.contains(mgf.uri()), encrypted);
        }
    }

    static Stream<Arguments> agreementVariants() {
        return Stream.of(
                Arguments.of("EC", Algorithm.ECDH_ES, Algorithm.CONCAT_KDF, Algorithm.SHA256),
                Arguments.of("EC", Algorithm.ECDH_ES, Algorithm.CONCAT_KDF, Algorithm.SHA512),
                Arguments.of("EC", Algorithm.ECDH_ES, Algorithm.HKDF, Algorithm.SHA256),
                Arguments.of("X25519", Algorithm.X25519, Algorithm.HKDF, Algorithm.SHA256),
                Arguments.of("X448", Algorithm.X448, Algorithm.CONCAT_KDF, Algorithm.SHA384));
    }

    @ParameterizedTest
    @MethodSource("agreementVariants")
    void keyAgreementVariants(String keyType, Algorithm method, Algorithm kdf, Algorithm digest) throws Exception {
        KeyPair pair = switch (keyType) {
            case "X25519" -> x25519;
            case "X448" -> x448;
            default -> ec;
        };
        var recipient = new PublicKeyRecipient(pair.getPublic(), null, method, null, null, null, kdf, digest,
                X509Reference.NONE, null);
        String encrypted = roundTrip(cards(Algorithm.AES_256_GCM, TargetType.ELEMENT, recipient),
                DecryptionKeys.privateKey(pair.getPrivate()));
        assertTrue(encrypted.contains(method.uri()) && encrypted.contains(kdf.uri()), encrypted);
    }

    static Stream<Algorithm> keyWraps() {
        return XmlEncryptionAlgorithms.of(XmlEncryptionAlgorithms.Kind.KEY_WRAP).stream();
    }

    @ParameterizedTest
    @MethodSource("keyWraps")
    void symmetricKeyWrap(Algorithm wrap) throws Exception {
        SecretKey kek = new SecretKeySpec(new byte[wrap.keyBits() / 8], wrap.jceKeyAlgorithm());
        byte[] bytes = kek.getEncoded();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 7 + 3);
        }
        SecretKey key = new SecretKeySpec(bytes, wrap.jceKeyAlgorithm());
        String encrypted = roundTrip(cards(Algorithm.AES_192_CBC, TargetType.ELEMENT,
                        new SymmetricRecipient(key, "partner-key", wrap, null)),
                DecryptionKeys.secretKey("partner-key", key));
        assertTrue(encrypted.contains(wrap.uri()) && encrypted.contains("partner-key"), encrypted);
    }

    @Test
    void symmetricKeyAsDataKeyWithKeyName() throws Exception {
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        String encrypted = roundTrip(cards(Algorithm.AES_256_GCM, TargetType.ELEMENT,
                new SymmetricRecipient(key, "shared", null, null)), DecryptionKeys.secretKey("shared", key));
        assertFalse(encrypted.contains("EncryptedKey"), encrypted);
        assertTrue(encrypted.contains("<ds:KeyName>shared</ds:KeyName>"), encrypted);
    }

    @Test
    void passwordWithPbkdf2() throws Exception {
        var recipient = new PasswordRecipient("correct horse".toCharArray(), null, 1000,
                XmlEncryptionService.HMAC_SHA512, null);
        String encrypted = roundTrip(cards(Algorithm.AES_128_GCM, TargetType.ELEMENT, recipient),
                DecryptionKeys.password("correct horse".toCharArray()));
        assertTrue(encrypted.contains("xenc11:DerivedKey") && encrypted.contains(Algorithm.PBKDF2.uri())
                && encrypted.contains("<xenc11:IterationCount>1000</xenc11:IterationCount>"), encrypted);

        Document document = parse(encrypted);
        var wrong = service.decrypt(document, DecryptionKeys.password("wrong".toCharArray()), Set.of());
        assertEquals(DecryptionStatus.WRONG_KEY, wrong.status());
    }

    @Test
    void severalRecipientsEachDecrypt() throws Exception {
        var request = new EncryptionRequest(List.of(), Map.of(), TargetType.ELEMENT, Algorithm.AES_256_GCM,
                List.of(PublicKeyRecipient.of(rsa.getPublic(), null), PublicKeyRecipient.of(ec.getPublic(), null),
                        new PasswordRecipient("pw".toCharArray(), null, 1000, null, "ops")),
                Map.of(), false);
        Document document = parse(XML);
        service.encrypt(document, request);
        String encrypted = serialize(document);
        assertEquals(3, encrypted.split("<xenc:EncryptedKey", -1).length - 1, encrypted);
        for (DecryptionKeys keys : List.of(DecryptionKeys.privateKey(rsa.getPrivate()),
                DecryptionKeys.privateKey(ec.getPrivate()), DecryptionKeys.password("pw".toCharArray()))) {
            Document copy = parse(encrypted);
            assertEquals(DecryptionStatus.DECRYPTED, service.decrypt(copy, keys, Set.of()).status());
            assertEquals(serialize(parse(XML)), serialize(copy));
        }
    }

    @Test
    void keysOutsideWithReferenceListAndProperties() throws Exception {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("created", "2026-11-09");
        properties.put("purpose", "test");
        var request = new EncryptionRequest(List.of("//p:card"), Map.of(), TargetType.ELEMENT, Algorithm.AES_128_GCM,
                List.of(PublicKeyRecipient.of(rsa.getPublic(), null)), properties, true);
        Document document = parse(XML);
        assertEquals(2, service.encrypt(document, request).encrypted());
        String encrypted = serialize(document);
        assertTrue(encrypted.contains("RetrievalMethod") && encrypted.contains("DataReference URI=\"#ED-1\"")
                && encrypted.contains("Name=\"purpose\">test<"), encrypted);
        Document again = parse(encrypted);
        assertEquals(DecryptionStatus.DECRYPTED, service.decrypt(again, DecryptionKeys.privateKey(rsa.getPrivate()),
                Set.of()).status());
        String decrypted = serialize(again);
        assertFalse(decrypted.contains("EncryptedKey"), "used EncryptedKeys removed: " + decrypted);
        assertEquals(serialize(parse(XML)), decrypted);
    }

    @Test
    void superEncryptionAndSelectiveDecryption() throws Exception {
        Document document = parse(XML);
        service.encrypt(document, cards(Algorithm.AES_128_GCM, TargetType.ELEMENT, PublicKeyRecipient.of(rsa.getPublic(), null)));
        // encrypt the whole document again, EncryptedData included
        service.encrypt(document, EncryptionRequest.document(List.of(
                new PasswordRecipient("outer".toCharArray(), null, 1000, null, null))));
        Document again = parse(serialize(document));
        assertEquals(1, XmlEncryptionService.count(again));
        // only the password: the outer layer opens, the inner cards stay encrypted
        var outer = service.decrypt(again, DecryptionKeys.password("outer".toCharArray()), Set.of());
        assertEquals(DecryptionStatus.PARTIAL, outer.status(), outer.message());
        assertEquals(2, XmlEncryptionService.count(again));
        // only the first card by its Id
        var first = service.decrypt(again, DecryptionKeys.privateKey(rsa.getPrivate()), Set.of("ED-1"));
        assertEquals(1, first.decrypted());
        assertEquals(1, XmlEncryptionService.count(again));
    }

    @Test
    void wrongKeyAndTamperedData() throws Exception {
        Document document = parse(XML);
        service.encrypt(document, cards(Algorithm.AES_256_GCM, TargetType.ELEMENT, PublicKeyRecipient.of(rsa.getPublic(), null)));
        String encrypted = serialize(document);
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        var wrong = service.decrypt(parse(encrypted), DecryptionKeys.privateKey(g.generateKeyPair().getPrivate()), Set.of());
        assertEquals(DecryptionStatus.WRONG_KEY, wrong.status());

        // flip one Base64 character of the first data CipherValue: GCM detects it
        int at = encrypted.lastIndexOf("<xenc:CipherValue>") + "<xenc:CipherValue>".length() + 4;
        char c = encrypted.charAt(at);
        String tampered = encrypted.substring(0, at) + (c == 'A' ? 'B' : 'A') + encrypted.substring(at + 1);
        var broken = service.decrypt(parse(tampered), DecryptionKeys.privateKey(rsa.getPrivate()), Set.of());
        assertNotEquals(DecryptionStatus.DECRYPTED, broken.status());
    }

    @Test
    void legacyAlgorithmsWarn() throws Exception {
        Document document = parse(XML);
        var outcome = service.encrypt(document, cards(Algorithm.TRIPLEDES_CBC, TargetType.ELEMENT,
                new PublicKeyRecipient(rsa.getPublic(), null, Algorithm.RSA_1_5, null, null, null, null, null,
                        X509Reference.NONE, null)));
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.contains("RSA-1_5")), outcome.warnings().toString());
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.contains("TripleDES")), outcome.warnings().toString());
        var result = service.decrypt(parse(serialize(document)), DecryptionKeys.privateKey(rsa.getPrivate()), Set.of());
        assertEquals(DecryptionStatus.DECRYPTED, result.status());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void binaryData() throws Exception {
        byte[] data = "%PDF-1.7 not really".getBytes(StandardCharsets.UTF_8);
        Document encrypted = service.encryptBinary(data, "application/pdf",
                EncryptionRequest.document(List.of(PublicKeyRecipient.of(ec.getPublic(), null))));
        String text = serialize(encrypted);
        assertTrue(text.startsWith("<xenc:EncryptedData") && text.contains("MimeType=\"application/pdf\""), text);
        assertArrayEquals(data, service.decryptBinary(parse(text), DecryptionKeys.privateKey(ec.getPrivate())));
    }

    @ParameterizedTest
    @EnumSource(TargetType.class)
    void wholeDocument(TargetType type) throws Exception {
        roundTrip(new EncryptionRequest(List.of(), Map.of(), type, null,
                List.of(PublicKeyRecipient.of(rsa.getPublic(), null)), Map.of(), false),
                DecryptionKeys.privateKey(rsa.getPrivate()));
    }

    @Test
    void requestErrors() throws Exception {
        assertThrows(XmlEncryptionService.XmlEncryptionException.class, () -> service.encrypt(parse(XML),
                cards(null, TargetType.ELEMENT)));
        assertThrows(XmlEncryptionService.XmlEncryptionException.class, () -> service.encrypt(parse(XML),
                new EncryptionRequest(List.of("//nothing"), Map.of(), null, null,
                        List.of(PublicKeyRecipient.of(rsa.getPublic(), null)), Map.of(), false)));
        assertThrows(XmlEncryptionService.XmlEncryptionException.class, () -> service.encrypt(parse(XML),
                cards(null, TargetType.ELEMENT, new PublicKeyRecipient(ec.getPublic(), null, Algorithm.RSA_OAEP_11,
                        null, null, null, null, null, X509Reference.NONE, null))));
        List<Recipient> both = new ArrayList<>();
        both.add(new SymmetricRecipient(new SecretKeySpec(new byte[32], "AES"), null, null, null));
        both.add(PublicKeyRecipient.of(rsa.getPublic(), null));
        assertThrows(XmlEncryptionService.XmlEncryptionException.class, () -> service.encrypt(parse(XML),
                new EncryptionRequest(List.of(), Map.of(), null, null, both, Map.of(), false)));
        assertEquals(DecryptionStatus.NO_ENCRYPTED_DATA,
                service.decrypt(parse(XML), DecryptionKeys.privateKey(rsa.getPrivate()), Set.of()).status());
    }

    @Test
    void cipherReferenceOutsideTheFolderIsRefused() throws Exception {
        String xml = """
                <xenc:EncryptedData xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" Id="x">\
                <xenc:EncryptionMethod Algorithm="http://www.w3.org/2009/xmlenc11#aes128-gcm"/>\
                <xenc:CipherData><xenc:CipherReference URI="http://example.org/data.bin"/></xenc:CipherData>\
                </xenc:EncryptedData>""";
        var result = service.decrypt(parse(xml), DecryptionKeys.secretKey("", new SecretKeySpec(new byte[16], "AES")), Set.of());
        assertEquals(DecryptionStatus.POLICY_BLOCKED, result.status(), result.message());
    }
}
