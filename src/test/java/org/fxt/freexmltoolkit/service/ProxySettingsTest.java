package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ProxySettingsTest {

    private static ProxySettings manual(String bypass) {
        return new ProxySettings(ProxySettings.Mode.MANUAL, "proxy.corp", 3128,
                ProxySettings.parseHostList(bypass), "", "", "", true, 30_000, 60_000);
    }

    @Test
    void defaultsAreTheSystemProxyWithTimeouts() {
        ProxySettings s = ProxySettings.from(new Properties());
        assertEquals(ProxySettings.Mode.SYSTEM, s.mode());
        assertEquals(30_000, s.connectTimeoutMs());
        assertEquals(60_000, s.readTimeoutMs());
        assertTrue(s.windowsSignOn());
        assertFalse(s.hasCredentials());
    }

    @Test
    void writtenSettingsReadBackUnchanged() {
        ProxySettings written = new ProxySettings(ProxySettings.Mode.MANUAL, "proxy.corp", 8080,
                List.of("*.corp", "10.*"), "jdoe", "s3cret", "CORP", false, 15_000, 120_000);
        Properties props = new Properties();
        written.writeTo(props);

        assertEquals(written, ProxySettings.from(props));
        assertEquals("true", props.getProperty("manualProxy"));
        assertEquals("false", props.getProperty("useSystemProxy"));
    }

    @Test
    void theOldTwoFlagsStillSelectTheMode() {
        Properties none = new Properties();
        none.setProperty("manualProxy", "false");
        none.setProperty("useSystemProxy", "false");
        assertEquals(ProxySettings.Mode.NONE, ProxySettings.from(none).mode());
    }

    @Test
    void exceptionsMatchHostsWildcardsAndAlwaysLocalAddresses() {
        ProxySettings s = manual("*.corp.example | 10.* ; intranet");
        assertTrue(s.bypasses("wiki.corp.example"));
        assertTrue(s.bypasses("10.1.2.3"));
        assertTrue(s.bypasses("INTRANET"));
        assertTrue(s.bypasses("localhost"));
        assertTrue(s.bypasses("127.0.0.1"));
        assertTrue(s.bypasses("[::1]"));
        assertFalse(s.bypasses("github.com"));
        assertFalse(s.bypasses("corp.example.com"));
    }

    @Test
    void theDomainIsPrefixedUnlessTheUserNamesOne() {
        assertEquals("CORP\\jdoe", new ProxySettings(ProxySettings.Mode.MANUAL, "h", 1, List.of(),
                "jdoe", "pw", "CORP", true, 1000, 1000).authenticationUser());
        assertEquals("OTHER\\jdoe", new ProxySettings(ProxySettings.Mode.MANUAL, "h", 1, List.of(),
                "OTHER\\jdoe", "pw", "CORP", true, 1000, 1000).authenticationUser());
        assertEquals("jdoe@corp.example", new ProxySettings(ProxySettings.Mode.MANUAL, "h", 1, List.of(),
                "jdoe@corp.example", "pw", "CORP", true, 1000, 1000).authenticationUser());
    }

    @Test
    void aManualProxyNeedsHostAndValidPort() {
        Proxy proxy = manual("").manualProxy();
        assertEquals(Proxy.Type.HTTP, proxy.type());
        assertEquals("proxy.corp", ((InetSocketAddress) proxy.address()).getHostString());
        assertNull(new ProxySettings(ProxySettings.Mode.MANUAL, "proxy.corp", -1, List.of(),
                "", "", "", true, 1000, 1000).manualProxy());
        assertNull(new ProxySettings(ProxySettings.Mode.MANUAL, " ", 8080, List.of(),
                "", "", "", true, 1000, 1000).manualProxy());
    }

    @Test
    void badTimeoutsFallBackToTheDefaults() {
        Properties props = new Properties();
        props.setProperty(ProxySettings.KEY_CONNECT_TIMEOUT, "abc");
        props.setProperty(ProxySettings.KEY_READ_TIMEOUT, "0");
        ProxySettings s = ProxySettings.from(props);
        assertEquals(30_000, s.connectTimeoutMs());
        assertEquals(60_000, s.readTimeoutMs());
    }
}
