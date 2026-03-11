package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for SystemProxyDetector utility class.
 * Focuses on proxy configuration and retrieval methods that can be tested
 * without depending on actual system proxy settings.
 */
class SystemProxyDetectorTest {

    @AfterEach
    void tearDown() {
        // Clean up any proxy settings we may have configured
        SystemProxyDetector.clearProxyConfiguration();
    }

    // =========================================================================
    // ProxyConfig Record Tests
    // =========================================================================

    @Nested
    @DisplayName("ProxyConfig Record")
    class ProxyConfigTests {

        @Test
        @DisplayName("Create ProxyConfig with host and port")
        void createProxyConfig() {
            SystemProxyDetector.ProxyConfig config = new SystemProxyDetector.ProxyConfig("proxy.example.com", 8080);
            assertEquals("proxy.example.com", config.host());
            assertEquals(8080, config.port());
        }

        @Test
        @DisplayName("toString returns host:port format")
        void toStringFormat() {
            SystemProxyDetector.ProxyConfig config = new SystemProxyDetector.ProxyConfig("proxy.example.com", 8080);
            assertEquals("proxy.example.com:8080", config.toString());
        }

        @Test
        @DisplayName("Record equality")
        void recordEquality() {
            SystemProxyDetector.ProxyConfig config1 = new SystemProxyDetector.ProxyConfig("proxy.example.com", 8080);
            SystemProxyDetector.ProxyConfig config2 = new SystemProxyDetector.ProxyConfig("proxy.example.com", 8080);
            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("Record inequality - different host")
        void recordInequalityHost() {
            SystemProxyDetector.ProxyConfig config1 = new SystemProxyDetector.ProxyConfig("proxy1.example.com", 8080);
            SystemProxyDetector.ProxyConfig config2 = new SystemProxyDetector.ProxyConfig("proxy2.example.com", 8080);
            assertNotEquals(config1, config2);
        }

        @Test
        @DisplayName("Record inequality - different port")
        void recordInequalityPort() {
            SystemProxyDetector.ProxyConfig config1 = new SystemProxyDetector.ProxyConfig("proxy.example.com", 8080);
            SystemProxyDetector.ProxyConfig config2 = new SystemProxyDetector.ProxyConfig("proxy.example.com", 3128);
            assertNotEquals(config1, config2);
        }
    }

    // =========================================================================
    // Proxy Configuration Tests
    // =========================================================================

    @Nested
    @DisplayName("Proxy Configuration")
    class ProxyConfigurationTests {

        @Test
        @DisplayName("Configure proxy sets system properties")
        void configureProxy() {
            SystemProxyDetector.configureProxy("proxy.example.com", 8080);

            assertEquals("proxy.example.com", System.getProperty("http.proxyHost"));
            assertEquals("8080", System.getProperty("http.proxyPort"));
            assertEquals("proxy.example.com", System.getProperty("https.proxyHost"));
            assertEquals("8080", System.getProperty("https.proxyPort"));
        }

        @Test
        @DisplayName("Configure proxy with non-proxy hosts")
        void configureProxyWithBypass() {
            SystemProxyDetector.configureProxy("proxy.example.com", 8080, "localhost|127.0.0.1|*.local");

            assertEquals("proxy.example.com", System.getProperty("http.proxyHost"));
            assertEquals("8080", System.getProperty("http.proxyPort"));
            assertEquals("localhost|127.0.0.1|*.local", System.getProperty("http.nonProxyHosts"));
        }

        @Test
        @DisplayName("Configure proxy with null non-proxy hosts skips bypass")
        void configureProxyNullBypass() {
            SystemProxyDetector.configureProxy("proxy.example.com", 8080, null);

            assertEquals("proxy.example.com", System.getProperty("http.proxyHost"));
            assertNull(System.getProperty("http.nonProxyHosts"));
        }

        @Test
        @DisplayName("Configure proxy with empty non-proxy hosts skips bypass")
        void configureProxyEmptyBypass() {
            SystemProxyDetector.configureProxy("proxy.example.com", 8080, "");

            assertNull(System.getProperty("http.nonProxyHosts"));
        }

        @Test
        @DisplayName("Configure proxy for WebEngine")
        void configureProxyForWebEngine() {
            SystemProxyDetector.configureProxyForWebEngine("proxy.example.com", 8080);

            assertEquals("proxy.example.com", System.getProperty("http.proxyHost"));
            assertEquals("8080", System.getProperty("http.proxyPort"));
            assertEquals("false", System.getProperty("com.sun.webkit.useHTTP2Loader"));
        }

        @Test
        @DisplayName("Clear proxy configuration removes all properties")
        void clearProxy() {
            SystemProxyDetector.configureProxy("proxy.example.com", 8080, "localhost");

            SystemProxyDetector.clearProxyConfiguration();

            assertNull(System.getProperty("http.proxyHost"));
            assertNull(System.getProperty("http.proxyPort"));
            assertNull(System.getProperty("https.proxyHost"));
            assertNull(System.getProperty("https.proxyPort"));
            assertNull(System.getProperty("http.nonProxyHosts"));
            assertNull(System.getProperty("https.nonProxyHosts"));
        }
    }

    // =========================================================================
    // Get Current Config Tests
    // =========================================================================

    @Nested
    @DisplayName("Get Current Config")
    class GetCurrentConfigTests {

        @Test
        @DisplayName("Returns empty when no proxy configured")
        void noproxyReturnsEmpty() {
            SystemProxyDetector.clearProxyConfiguration();

            Optional<SystemProxyDetector.ProxyConfig> config = SystemProxyDetector.getCurrentConfig();
            assertTrue(config.isEmpty());
        }

        @Test
        @DisplayName("Returns config when proxy is configured")
        void returnsConfiguredProxy() {
            SystemProxyDetector.configureProxy("proxy.example.com", 8080);

            Optional<SystemProxyDetector.ProxyConfig> config = SystemProxyDetector.getCurrentConfig();
            assertTrue(config.isPresent());
            assertEquals("proxy.example.com", config.get().host());
            assertEquals(8080, config.get().port());
        }

        @Test
        @DisplayName("Returns empty for invalid port")
        void invalidPortReturnsEmpty() {
            System.setProperty("http.proxyHost", "proxy.example.com");
            System.setProperty("http.proxyPort", "not-a-number");

            Optional<SystemProxyDetector.ProxyConfig> config = SystemProxyDetector.getCurrentConfig();
            assertTrue(config.isEmpty());
        }

        @Test
        @DisplayName("Returns empty when only host set")
        void onlyHostSetReturnsEmpty() {
            System.setProperty("http.proxyHost", "proxy.example.com");
            System.clearProperty("http.proxyPort");

            Optional<SystemProxyDetector.ProxyConfig> config = SystemProxyDetector.getCurrentConfig();
            assertTrue(config.isEmpty());
        }
    }

    // =========================================================================
    // System Proxy Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("System Proxy Detection")
    class DetectionTests {

        @Test
        @DisplayName("detectSystemProxy does not throw")
        void detectDoesNotThrow() {
            assertDoesNotThrow(() -> SystemProxyDetector.detectSystemProxy());
        }

        @Test
        @DisplayName("detectSystemProxy returns Optional")
        void detectReturnsOptional() {
            Optional<SystemProxyDetector.ProxyConfig> result = SystemProxyDetector.detectSystemProxy();
            assertNotNull(result);
            // Result may or may not be present depending on system config
        }
    }

    // =========================================================================
    // NTLM Authentication Tests
    // =========================================================================

    @Nested
    @DisplayName("NTLM Authentication")
    class NtlmTests {

        @Test
        @DisplayName("enableNtlmAuthentication does not throw")
        void enableNtlmDoesNotThrow() {
            assertDoesNotThrow(() -> SystemProxyDetector.enableNtlmAuthentication());
        }

        @Test
        @DisplayName("enableNtlmAuthentication sets system properties")
        void enableNtlmSetsProperties() {
            SystemProxyDetector.enableNtlmAuthentication();

            assertEquals("", System.getProperty("jdk.http.auth.tunneling.disabledSchemes"));
            assertEquals("", System.getProperty("jdk.http.auth.proxying.disabledSchemes"));
            assertEquals("true", System.getProperty("java.net.preferIPv4Stack"));
        }
    }
}
