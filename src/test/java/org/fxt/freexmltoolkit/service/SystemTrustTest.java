package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedTrustManager;

import org.junit.jupiter.api.Test;

/** The operating system's roots are trusted next to the JDK's list, and only what one of them accepts passes. */
class SystemTrustTest {

    @Test
    void onByDefault() {
        assertTrue(SystemTrust.enabled(new Properties()));
        Properties off = new Properties();
        off.setProperty(SystemTrust.KEY, "false");
        assertFalse(SystemTrust.enabled(off));
    }

    @Test
    void noOperatingSystemStoreOnLinux() {
        assertTrue(SystemTrust.systemStores("linux").isEmpty());
    }

    @Test
    void combinedAcceptsTheIssuersOfEveryStore() throws Exception {
        KeyStore empty = KeyStore.getInstance(KeyStore.getDefaultType());
        empty.load(null, null);
        X509ExtendedTrustManager combined = SystemTrust.combined(List.of(empty));
        // the JDK's own roots stay in
        assertTrue(combined.getAcceptedIssuers().length > 10);
    }

    @Test
    void aCompanyRootInTheSystemStoreIsTrustedAndOnlyThere() throws Exception {
        X509Certificate companyRoot = selfSigned("CN=Company Proxy Root");
        KeyStore system = KeyStore.getInstance(KeyStore.getDefaultType());
        system.load(null, null);
        system.setCertificateEntry("company", companyRoot);

        X509Certificate[] chain = {companyRoot};
        // the JDK's list alone refuses it - the "PKIX path building failed" users see
        assertThrows(CertificateException.class, () -> SystemTrust.combined(List.of()).checkServerTrusted(chain, "RSA"));
        // with the operating system's store it passes
        SystemTrust.combined(List.of(system)).checkServerTrusted(chain, "RSA");
        // a certificate in neither stays refused
        assertThrows(CertificateException.class, () -> SystemTrust.combined(List.of(system))
                .checkServerTrusted(new X509Certificate[]{selfSigned("CN=Someone Else")}, "RSA"));
    }

    private static X509Certificate selfSigned(String dn) throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var name = new org.bouncycastle.asn1.x500.X500Name(dn);
        var now = new java.util.Date();
        var builder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(name, java.math.BigInteger.ONE,
                new java.util.Date(now.getTime() - 60_000), new java.util.Date(now.getTime() + 86_400_000L), name,
                keys.getPublic());
        builder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true,
                new org.bouncycastle.asn1.x509.BasicConstraints(true));
        var signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate());
        return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    @Test
    void installAndRemoveSwapTheDefaults() throws Exception {
        SSLContext before = SSLContext.getDefault();
        var factoryBefore = HttpsURLConnection.getDefaultSSLSocketFactory();
        SystemTrust.install(true); // on Linux: nothing to add, the defaults stay
        SystemTrust.install(false);
        assertEquals(before, SSLContext.getDefault());
        assertEquals(factoryBefore, HttpsURLConnection.getDefaultSSLSocketFactory());
    }

    @Test
    void aChainOneStoreAcceptsPasses() throws Exception {
        var refusing = new Fake(false);
        var accepting = new Fake(true);
        new SystemTrust.Combined(List.of(refusing, accepting)).checkServerTrusted(new X509Certificate[0], "RSA");
        assertTrue(accepting.asked, "the operating system's store is asked when the JDK's list refuses");
        CertificateException e = assertThrows(CertificateException.class,
                () -> new SystemTrust.Combined(List.of(new Fake(false), new Fake(false)))
                        .checkServerTrusted(new X509Certificate[0], "RSA"));
        assertEquals("refused", e.getMessage());
    }

    /** A trust manager that accepts or refuses everything. */
    private static final class Fake extends X509ExtendedTrustManager {
        private final boolean accept;
        boolean asked;

        Fake(boolean accept) {
            this.accept = accept;
        }

        private void check() throws CertificateException {
            asked = true;
            if (!accept) {
                throw new CertificateException("refused");
            }
        }

        @Override public void checkClientTrusted(X509Certificate[] c, String a) throws CertificateException { check(); }
        @Override public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException { check(); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, java.net.Socket s) throws CertificateException { check(); }
        @Override public void checkServerTrusted(X509Certificate[] c, String a, java.net.Socket s) throws CertificateException { check(); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, javax.net.ssl.SSLEngine e) throws CertificateException { check(); }
        @Override public void checkServerTrusted(X509Certificate[] c, String a, javax.net.ssl.SSLEngine e) throws CertificateException { check(); }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}
