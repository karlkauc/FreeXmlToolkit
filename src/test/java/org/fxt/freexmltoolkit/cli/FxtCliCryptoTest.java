package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.Map;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code fxt-cli sign / verify / encrypt / decrypt} end to end. */
class FxtCliCryptoTest {

    private static final String XML = "<order xmlns=\"urn:shop\" xmlns:p=\"urn:pay\"><customer>Ada</customer>"
            + "<p:card number=\"4111\"><p:holder>Ada Lovelace</p:holder></p:card></order>";

    @TempDir
    Path dir;

    private Path document;
    private String out;
    private String err;

    @BeforeEach
    void setUp() throws Exception {
        document = Files.writeString(dir.resolve("order.xml"), XML, StandardCharsets.UTF_8);
        CliSecrets.environment = Map.of("KS_PW", "secret", "DOC_PW", "correct horse", "BAD_PW", "nope")::get;
    }

    @AfterEach
    void restore() {
        CliSecrets.environment = System::getenv;
    }

    private int run(Object... args) {
        StringWriter outText = new StringWriter();
        StringWriter errText = new StringWriter();
        String[] strings = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            strings[i] = String.valueOf(args[i]);
        }
        int code = FxtCli.newCommandLine(new PrintWriter(outText, true), new PrintWriter(errText, true)).execute(strings);
        out = outText.toString();
        err = errText.toString();
        return code;
    }

    /** A self-signed certificate with its key in a PKCS#12 keystore (password "secret") and as PEM. */
    private record Identity(Path keystore, Path certificatePem, Path privateKeyPem) {
    }

    private Identity identity(String name, String keyType) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(keyType);
        if ("EC".equals(keyType)) {
            generator.initialize(new ECGenParameterSpec("secp256r1"));
        } else {
            generator.initialize(2048);
        }
        KeyPair pair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=" + name);
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()),
                new Date(System.currentTimeMillis() - 60_000), new Date(System.currentTimeMillis() + 86_400_000L),
                subject, pair.getPublic());
        String signer = "EC".equals(keyType) ? "SHA256withECDSA" : "SHA256withRSA";
        X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder(signer).build(pair.getPrivate())));
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(name, pair.getPrivate(), "secret".toCharArray(), new java.security.cert.Certificate[]{certificate});
        Path keystore = dir.resolve(name + ".p12");
        try (OutputStream o = Files.newOutputStream(keystore)) {
            ks.store(o, "secret".toCharArray());
        }
        Path pem = dir.resolve(name + ".pem");
        Path keyPem = dir.resolve(name + "-key.pem");
        try (var w = new JcaPEMWriter(Files.newBufferedWriter(pem))) {
            w.writeObject(certificate);
        }
        try (var w = new JcaPEMWriter(Files.newBufferedWriter(keyPem))) {
            w.writeObject(new org.bouncycastle.openssl.jcajce.JcaPKCS8Generator(pair.getPrivate(), null));
        }
        return new Identity(keystore, pem, keyPem);
    }

    @Test
    @DisplayName("encrypt elements for a certificate, decrypt with the keystore and with the PEM key")
    void certificateRoundTrip() throws Exception {
        Identity bob = identity("bob", "RSA");
        Path encrypted = dir.resolve("order.enc.xml");
        assertEquals(ExitCodes.OK, run("encrypt", document, "-x", "//p:card", "--cert", bob.certificatePem(),
                "--property", "purpose=test", "-o", encrypted), err);
        String text = Files.readString(encrypted);
        assertTrue(text.contains("EncryptedData") && !text.contains("Ada Lovelace") && text.contains("<customer>Ada"), text);

        Path back = dir.resolve("back.xml");
        assertEquals(ExitCodes.OK, run("decrypt", encrypted, "--keystore", bob.keystore(), "--storepass-env", "KS_PW",
                "-o", back), err);
        assertTrue(Files.readString(back).contains("<p:holder>Ada Lovelace</p:holder>"));

        Path back2 = dir.resolve("back2.xml");
        assertEquals(ExitCodes.OK, run("decrypt", encrypted, "--private-key", bob.privateKeyPem(), "-o", back2), err);
        assertEquals(Files.readString(back), Files.readString(back2));

        // inspect without a key
        assertEquals(ExitCodes.OK, run("decrypt", "--inspect", "-f", "json", encrypted), err);
        assertTrue(out.contains("AES-256-GCM") && out.contains("RSA-OAEP") && out.contains("purpose"), out);
    }

    @Test
    @DisplayName("an EC certificate and a password: each decrypts; a wrong password exits 1")
    void ecAndPassword() throws Exception {
        Identity carol = identity("carol", "EC");
        Path encrypted = dir.resolve("order.enc.xml");
        assertEquals(ExitCodes.OK, run("encrypt", document, "--cert", carol.certificatePem(),
                "--password-env", "DOC_PW", "--iterations", "1000", "-o", encrypted), err);
        assertTrue(Files.readString(encrypted).contains("ECDH-ES"));

        assertEquals(ExitCodes.OK, run("decrypt", encrypted, "--password-env", "DOC_PW", "-o", dir.resolve("a.xml")), err);
        assertEquals(ExitCodes.OK, run("decrypt", encrypted, "--keystore", carol.keystore(), "--storepass-env", "KS_PW",
                "-o", dir.resolve("b.xml")), err);
        assertEquals(ExitCodes.FAILED, run("decrypt", encrypted, "--password-env", "BAD_PW", "-o", dir.resolve("c.xml")));
        assertTrue(err.contains("fxt-cli:"), err);
    }

    @Test
    @DisplayName("a shared key file with KeyName, used directly; binary data")
    void keyFileAndBinary() throws Exception {
        Path key = Files.writeString(dir.resolve("partner.key"), "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        Path encrypted = dir.resolve("order.enc.xml");
        assertEquals(ExitCodes.OK, run("encrypt", document, "-x", "//p:card", "--content", "--key-file", key,
                "--key-name", "partner-2026", "--key-wrap", "none", "-o", encrypted), err);
        assertTrue(Files.readString(encrypted).contains("<ds:KeyName>partner-2026</ds:KeyName>"));
        assertEquals(ExitCodes.OK, run("decrypt", encrypted, "--key-file", key, "-o", dir.resolve("plain.xml")), err);
        assertTrue(Files.readString(dir.resolve("plain.xml")).contains("Ada Lovelace"));

        Path pdf = Files.write(dir.resolve("invoice.pdf"), new byte[]{'%', 'P', 'D', 'F', 0, 1, 2, (byte) 0xff});
        assertEquals(ExitCodes.OK, run("encrypt", pdf, "--binary", "--mime-type", "application/pdf",
                "--key-file", key), err);
        Path wrapped = dir.resolve("invoice.encrypted.xml");
        assertTrue(Files.readString(wrapped).contains("MimeType=\"application/pdf\""));
        assertEquals(ExitCodes.OK, run("decrypt", wrapped, "--key-file", key), err);
        assertArrayEquals(Files.readAllBytes(pdf), Files.readAllBytes(dir.resolve("invoice.encrypted.decrypted.bin")));
    }

    @Test
    @DisplayName("usage errors and the algorithm list")
    void usage() {
        assertEquals(ExitCodes.USAGE, run("encrypt", document));
        assertTrue(err.contains("at least one recipient"), err);
        assertEquals(ExitCodes.USAGE, run("encrypt", document, "--password-env", "DOC_PW", "--data-alg", "rot13"));
        assertTrue(err.contains("aes256-gcm"), err);
        assertEquals(ExitCodes.USAGE, run("decrypt", document));
        assertEquals(ExitCodes.OK, run("encrypt", "--list-algorithms"));
        assertTrue(out.contains("http://www.w3.org/2009/xmlenc11#rsa-oaep") && out.contains("legacy"), out);
    }

    @Test
    @DisplayName("sign and verify; a changed document fails")
    void signAndVerify() throws Exception {
        Identity alice = identity("alice", "RSA");
        assertEquals(ExitCodes.OK, run("sign", document, "--keystore", alice.keystore(), "--storepass-env", "KS_PW"), err);
        Path signed = dir.resolve("order.signed.xml");
        assertTrue(Files.readString(signed).contains("SignatureValue"));
        assertEquals(ExitCodes.OK, run("verify", signed), err + out);
        assertTrue(out.contains("VALID"), out);

        Files.writeString(signed, Files.readString(signed).replace("Ada</customer>", "Eve</customer>"));
        assertEquals(ExitCodes.FAILED, run("verify", "-f", "json", signed));
        assertTrue(out.contains("\"status\": \"invalid\""), out);
    }
}
