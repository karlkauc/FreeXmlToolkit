package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

/**
 * Real PKIX trust validation of a signing certificate chain against a trust store — the net-new
 * security work behind "expert signature validation": a self-signed cert is trusted only when it is
 * present in the trust store.
 */
class SignatureTrustValidatorTest {

    static {
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static X509Certificate selfSigned() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        X500Name dn = new X500Name("CN=Test Signer, O=FXT, C=AT");
        long now = System.currentTimeMillis();
        var builder = new JcaX509v3CertificateBuilder(dn, BigInteger.valueOf(now),
                new Date(now - 86400000L), new Date(now + 86400000L), dn, kp.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(kp.getPrivate());
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
    }

    private static KeyStore trustStoreWith(X509Certificate... certs) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        for (int i = 0; i < certs.length; i++) {
            ks.setCertificateEntry("anchor" + i, certs[i]);
        }
        return ks;
    }

    @Test
    void selfSignedTrustedOnlyWhenInTrustStore() throws Exception {
        X509Certificate cert = selfSigned();

        var trusted = SignatureTrustValidator.validateChain(List.of(cert), trustStoreWith(cert), false);
        assertTrue(trusted.trusted(), trusted.message());
        assertTrue(trusted.selfSigned());

        var untrusted = SignatureTrustValidator.validateChain(List.of(cert), trustStoreWith(), false);
        assertFalse(untrusted.trusted(), "an empty trust store must not trust the self-signed cert");
        assertTrue(untrusted.selfSigned());
    }

    @Test
    void emptyChainIsAnError() throws Exception {
        var result = SignatureTrustValidator.validateChain(List.of(), trustStoreWith(), false);
        assertFalse(result.trusted());
        assertTrue(result.message().toLowerCase().contains("chain") || result.message().toLowerCase().contains("certificate"));
    }
}
