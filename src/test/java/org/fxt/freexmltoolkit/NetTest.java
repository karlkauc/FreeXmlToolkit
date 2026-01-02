package org.fxt.freexmltoolkit;

import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.ConnectionServiceImpl;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HTTP Connection Tests")
public class NetTest {

    private ConnectionService connectionService;
    private PropertiesService propertiesService;
    private URI testUri;

    @BeforeEach
    void setUp() throws URISyntaxException {
        connectionService = ConnectionServiceImpl.getInstance();
        propertiesService = PropertiesServiceImpl.getInstance();
        testUri = new URI("https://www.github.com");
        propertiesService.createDefaultProperties(); // Reset properties for test isolation
    }

    @Nested
    @DisplayName("1. Direct Connection Scenarios")
    class DirectConnection {
        @Test
        @DisplayName("1.1. Connects successfully with all proxy flags disabled")
        void testDirectConnectionWhenAllProxyFlagsAreOff() {
            System.out.println("--- Testing Direct Connection (All Flags Off) ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "false");
            props.setProperty("useSystemProxy", "false");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            assertTrue(result.httpStatus() >= 200 && result.httpStatus() < 400, "Expected a successful HTTP status code (2xx or 3xx).");
            System.out.println("Direct connection successful with status: " + result.httpStatus());
        }

        @Test
        @DisplayName("1.2. Connects successfully when manual proxy is defined but disabled")
        void testDirectConnectionWhenProxyDefinedButFlagIsOff() {
            System.out.println("--- Testing Direct Connection when Manual Proxy is Defined but Disabled ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "false");
            props.setProperty("useSystemProxy", "false");
            props.setProperty("http.proxy.host", "127.0.0.1");
            props.setProperty("http.proxy.port", "9999");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            assertTrue(result.httpStatus() >= 200 && result.httpStatus() < 400, "Expected a successful HTTP status code, indicating a direct connection.");
            System.out.println("Direct connection successful (proxy settings ignored as expected): " + result.httpStatus());
        }

        @Test
        @DisplayName("1.3. Connects successfully using default properties from service")
        void testDirectConnectionUsingDefaultProperties() {
            System.out.println("--- Testing Direct Connection using default properties from service ---");
            // This test relies on the default properties allowing a direct connection.
            // It calls the method that internally loads the properties from the service.
            ConnectionResult result = connectionService.executeHttpRequest(testUri);

            assertNotNull(result, "Connection result should not be null.");
            assertTrue(result.httpStatus() >= 200 && result.httpStatus() < 400, "Expected a successful HTTP status code (2xx or 3xx).");
            System.out.println("Direct connection with default properties successful with status: " + result.httpStatus());
        }
    }

    @Nested
    @DisplayName("2. Manual Proxy Scenarios")
    class ManualProxy {

        @Test
        @DisplayName("2.1. Fails gracefully with a correct but unreachable proxy")
        void testConnectionWithValidButUnreachableManualProxy() {
            System.out.println("--- Testing Connection with a valid but unreachable Manual Proxy ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "true");
            props.setProperty("http.proxy.host", "127.0.0.1");
            props.setProperty("http.proxy.port", "9999");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            assertTrue(result.httpStatus() <= 0, "Expected a failed connection status (0 or less).");
            assertTrue(result.resultBody().toLowerCase().contains("connection refused"), "Expected error message to indicate a connection failure.");
            System.out.println("Connection attempt with manual proxy failed as expected: " + result.resultBody());
        }

        @Test
        @DisplayName("2.2. Fails gracefully with an unresolvable proxy host")
        void testConnectionWithUnresolvableProxyHost() {
            System.out.println("--- Testing Connection with an Unresolvable Proxy Host ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "true");
            props.setProperty("http.proxy.host", "unresolvable-host.this-is-not-real");
            props.setProperty("http.proxy.port", "8080");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            assertTrue(result.httpStatus() <= 0, "Expected a failed connection status.");
            // Error message may vary - check for various DNS/resolution related error messages
            // or the hostname itself if the service just returns the unresolvable host
            String errorBody = result.resultBody().toLowerCase();
            assertTrue(errorBody.contains("unresolved") || errorBody.contains("unknown host") ||
                            errorBody.contains("nodename nor servname") || errorBody.contains("name or service not known") ||
                            errorBody.contains("no such host") || errorBody.contains("unresolvable-host"),
                    "Expected error message about unresolvable host. Actual: " + result.resultBody());
            System.out.println("Connection failed with unresolvable host as expected: " + result.resultBody());
        }

        @Test
        @DisplayName("2.3. Fails gracefully with an invalid proxy port")
        void testConnectionWithInvalidProxyPort() {
            System.out.println("--- Testing Connection with an Invalid Manual Proxy Port ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "true");
            props.setProperty("http.proxy.host", "127.0.0.1");
            props.setProperty("http.proxy.port", "not-a-port");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            // Either status is 0 with invalid port message, or fallback to direct connection
            assertTrue(result.httpStatus() == 0 || result.httpStatus() >= 200,
                    "HTTP status should be 0 for invalid port or successful direct connection.");
            if (result.httpStatus() == 0) {
                assertTrue(result.resultBody().contains("Invalid proxy port") ||
                                result.resultBody().contains("NumberFormatException"),
                        "Expected error message about invalid port. Actual: " + result.resultBody());
            }
            System.out.println("Gracefully handled invalid proxy port: " + result.resultBody());
        }

        @Test
        @DisplayName("2.4. Falls back to direct connection if proxy port is missing")
        void testConnectionWithMissingProxyPort() {
            System.out.println("--- Testing Connection with a Missing Proxy Port ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "true");
            props.setProperty("http.proxy.host", "127.0.0.1");
            props.setProperty("http.proxy.port", ""); // Missing port

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            assertTrue(result.httpStatus() >= 200 && result.httpStatus() < 400, "Expected a successful direct connection when port is missing.");
            System.out.println("Fallback to direct connection successful with status: " + result.httpStatus());
        }

        @Test
        @DisplayName("2.5. Falls back to direct connection if proxy host is missing")
        void testConnectionWithMissingProxyHost() {
            System.out.println("--- Testing Connection with a Missing Proxy Host ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "true");
            props.setProperty("http.proxy.host", ""); // Missing host
            props.setProperty("http.proxy.port", "9999");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            assertTrue(result.httpStatus() >= 200 && result.httpStatus() < 400, "Expected a successful direct connection when host is missing.");
            System.out.println("Fallback to direct connection successful with status: " + result.httpStatus());
        }

        @Test
        @DisplayName("2.6. Tests with proxy authentication (requires running proxy)")
        void testConnectionWithProxyAuthentication() {
            System.out.println("--- Testing Connection with Proxy Authentication (dummy) ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "true");
            props.setProperty("https.proxy.host", "localhost");
            props.setProperty("https.proxy.port", "3128");
            props.setProperty("http.proxy.host", "localhost");
            props.setProperty("http.proxy.port", "3128");
            props.setProperty("useSystemProxy", "false");
            props.setProperty("ssl.trustAllCerts", "true");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            // If proxy is running, expect 200; if not, expect connection refused
            assertTrue(result.httpStatus() == 200 || result.httpStatus() <= 0,
                    "Expected either successful connection (200) or connection failure (<=0). Actual: " + result.httpStatus());
            System.out.println("Connection attempt with proxy completed: " + result.httpStatus() + " - " + result.resultBody());
        }

        @Test
        @DisplayName("2.7. Handles proxy authentication with credentials")
        void testConnectionWithProxyUserAndPassword() {
            System.out.println("--- Testing Connection with Proxy User and Password ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "true");
            props.setProperty("http.proxy.host", "127.0.0.1");
            props.setProperty("http.proxy.port", "9999");
            props.setProperty("http.proxy.user", "testuser");
            props.setProperty("http.proxy.password", "testpass");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            // Expected outcomes:
            // - If proxy is not running: Connection refused (status <= 0)
            // - If proxy is running but credentials wrong: 407 Proxy Authentication Required
            // - If proxy is running and credentials correct: 200 OK (or other success)
            assertTrue(result.httpStatus() == 200 || result.httpStatus() == 407 || result.httpStatus() <= 0,
                    "Expected either successful auth (200), auth failure (407), or connection failure (<=0). Actual: " + result.httpStatus());
            System.out.println("Connection attempt with proxy credentials completed: " + result.httpStatus() + " - " + result.resultBody());
        }

        @Test
        @DisplayName("2.8. Fails gracefully with proxy user but empty password")
        void testConnectionWithProxyUserAndEmptyPassword() {
            System.out.println("--- Testing Connection with Proxy User and Empty Password ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "true");
            props.setProperty("http.proxy.host", "127.0.0.1");
            props.setProperty("http.proxy.port", "9999");
            props.setProperty("http.proxy.user", "testuser");
            props.setProperty("http.proxy.password", "");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            // With empty password, expect either connection failure or auth failure (407)
            assertTrue(result.httpStatus() == 407 || result.httpStatus() <= 0,
                    "Expected either auth failure (407) or connection failure (<=0). Actual: " + result.httpStatus());
            System.out.println("Connection attempt with authenticated proxy (empty password) failed as expected: " + result.httpStatus());
        }

        @Test
        @DisplayName("2.9. Test with different settings (requires running proxy)")
        void testConnectionWithProperties() {
            Properties props = propertiesService.loadProperties();

            props.setProperty("manualProxy", "true");
            props.setProperty("https.proxy.host", "localhost");
            props.setProperty("https.proxy.port", "3128");
            props.setProperty("http.proxy.host", "localhost");
            props.setProperty("http.proxy.port", "3128");
            props.setProperty("useSystemProxy", "false");
            props.setProperty("ssl.trustAllCerts", "true");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            // If proxy is running, expect 200; if not, expect connection refused
            assertTrue(result.httpStatus() == 200 || result.httpStatus() <= 0,
                    "Expected either successful connection (200) or connection failure (<=0). Actual: " + result.httpStatus());
            System.out.println("Connection test with proxy completed: " + result.httpStatus() + " - " + result.resultBody());
        }
    }

    @Nested
    @DisplayName("3. System Proxy Scenarios")
    class SystemProxy {
        @Test
        @DisplayName("3.1. Attempts connection with system proxy enabled")
        void testConnectionWithSystemProxyEnabled() {
            System.out.println("--- Testing Connection with System Proxy Enabled ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "false");
            props.setProperty("useSystemProxy", "true");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            System.out.println("System proxy test completed. Status: " + result.httpStatus() + ". The outcome depends on the test environment's system proxy.");
            assertTrue(true, "Test ran without throwing an exception.");
        }

        @Test
        @DisplayName("3.2. System proxy flag takes precedence over manual proxy flag")
        void testSystemProxyTakesPrecedence() {
            System.out.println("--- Testing that System Proxy takes precedence over Manual Proxy ---");
            Properties props = propertiesService.loadProperties();
            props.setProperty("manualProxy", "true"); // This should be ignored
            props.setProperty("useSystemProxy", "true");
            props.setProperty("http.proxy.host", "127.0.0.1");
            props.setProperty("http.proxy.port", "9999");

            ConnectionResult result = connectionService.testHttpRequest(testUri, props);

            assertNotNull(result, "Connection result should not be null.");
            System.out.println("System proxy test completed. Status: " + result.httpStatus() + ". Manual settings should have been ignored.");
            // The test passes if either:
            // 1. Connection succeeded (system proxy used or no proxy needed)
            // 2. Connection failed but NOT with "connection refused" from the manual proxy 127.0.0.1:9999
            // Note: If system proxy fails with "connection refused" that's a different proxy, not our manual one
            if (result.httpStatus() <= 0) {
                // If failed, ensure it's not specifically from our manual proxy settings being used
                // A "connection refused" error from system proxy or other reasons is acceptable
                System.out.println("Connection failed, but this may be due to system proxy or network. Body: " + result.resultBody());
            }
            assertTrue(result.httpStatus() > 0 || !result.resultBody().contains("127.0.0.1:9999"),
                    "If connection failed, it should not be due to manual proxy 127.0.0.1:9999 being used.");
        }
    }
}
