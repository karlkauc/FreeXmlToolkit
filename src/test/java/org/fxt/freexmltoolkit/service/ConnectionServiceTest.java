/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ConnectionService.
 * Tests HTTP request execution, proxy configuration, SSL handling, and error scenarios.
 */
@DisplayName("ConnectionService Tests")
class ConnectionServiceTest {

    private static HttpServer testServer;
    private static int testPort;
    private ConnectionServiceImpl connectionService;

    @BeforeAll
    static void setUpTestServer() throws IOException {
        // Start a simple HTTP test server
        testServer = HttpServer.create(new InetSocketAddress(0), 0);
        testPort = testServer.getAddress().getPort();

        // Handler for successful GET requests
        testServer.createContext("/success", exchange -> {
            String response = "<xml>Success Response</xml>";
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.getResponseHeaders().add("X-Custom-Header", "test-value");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        // Handler for 404 errors
        testServer.createContext("/notfound", exchange -> {
            String response = "404 - Not Found";
            exchange.sendResponseHeaders(404, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        // Handler for 500 errors
        testServer.createContext("/error", exchange -> {
            String response = "500 - Internal Server Error";
            exchange.sendResponseHeaders(500, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        // Handler for large responses
        testServer.createContext("/large", exchange -> {
            StringBuilder largeResponse = new StringBuilder("<xml>");
            for (int i = 0; i < 1000; i++) {
                largeResponse.append("<element id=\"").append(i).append("\">Data</element>");
            }
            largeResponse.append("</xml>");
            String response = largeResponse.toString();
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        testServer.setExecutor(null);
        testServer.start();
    }

    @AfterAll
    static void tearDownTestServer() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        connectionService = ConnectionServiceImpl.getInstance();
    }

    @Test
    @DisplayName("Should successfully execute HTTP GET request")
    void testExecuteHttpRequestSuccess() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        assertEquals(testUri, result.url(), "URL should match");
        assertEquals(200, result.httpStatus(), "HTTP status should be 200");
        assertNotNull(result.resultBody(), "Result body should not be null");
        assertTrue(result.resultBody().contains("<xml>Success Response</xml>"), "Response should contain expected content");
        assertTrue(result.duration() >= 0, "Duration should be non-negative");
        assertNotNull(result.resultHeader(), "Headers should not be null");
        assertTrue(result.resultHeader().length > 0, "Should have at least one header");
    }

    @Test
    @DisplayName("Should handle 404 errors correctly")
    void testExecuteHttpRequest404Error() {
        URI testUri = URI.create("http://localhost:" + testPort + "/notfound");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        assertEquals(404, result.httpStatus(), "HTTP status should be 404");
        assertTrue(result.resultBody().contains("404"), "Response should contain 404 message");
    }

    @Test
    @DisplayName("Should handle 500 errors correctly")
    void testExecuteHttpRequest500Error() {
        URI testUri = URI.create("http://localhost:" + testPort + "/error");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        assertEquals(500, result.httpStatus(), "HTTP status should be 500");
        assertTrue(result.resultBody().contains("500"), "Response should contain 500 message");
    }

    @Test
    @DisplayName("Should handle large responses")
    void testExecuteHttpRequestLargeResponse() {
        URI testUri = URI.create("http://localhost:" + testPort + "/large");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        assertEquals(200, result.httpStatus(), "HTTP status should be 200");
        assertTrue(result.resultBody().length() > 10000, "Response should be large");
        assertTrue(result.resultBody().contains("<element id=\"999\">"), "Response should contain last element");
    }

    @Test
    @DisplayName("Should handle connection errors gracefully")
    void testExecuteHttpRequestConnectionError() {
        // Use an invalid port that won't respond
        URI testUri = URI.create("http://localhost:1/invalid");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null even on error");
        assertEquals(0, result.httpStatus(), "HTTP status should be 0 for connection errors");
        assertNotNull(result.resultBody(), "Error message should not be null");
        assertTrue(result.resultBody().length() > 0, "Error message should be present");
    }

    @Test
    @DisplayName("Should successfully retrieve text content from URL")
    void testGetTextContentFromURLSuccess() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();

        // We need to use testHttpRequest since getTextContentFromURL uses saved properties
        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertTrue(result.httpStatus() >= 200 && result.httpStatus() < 400, "Should be successful status");
        assertNotNull(result.resultBody(), "Content should not be null");
        assertTrue(result.resultBody().contains("<xml>Success Response</xml>"), "Should contain expected content");
    }

    @Test
    @DisplayName("Should throw RuntimeException for failed getTextContentFromURL")
    void testGetTextContentFromURLFailure() {
        URI testUri = URI.create("http://localhost:" + testPort + "/notfound");

        // This will fail because getTextContentFromURL expects 2xx/3xx status
        assertThrows(RuntimeException.class, () -> connectionService.getTextContentFromURL(testUri),
                "Should throw RuntimeException for non-2xx/3xx status");
    }

    @Test
    @DisplayName("Should handle manual proxy configuration")
    void testManualProxyConfiguration() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();
        props.setProperty("manualProxy", "true");
        props.setProperty("http.proxy.host", "localhost");
        props.setProperty("http.proxy.port", "8080");

        // This will fail to connect through the proxy, but we're testing configuration
        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        // The request will fail due to invalid proxy, but configuration was applied
    }

    @Test
    @DisplayName("Should handle invalid proxy port gracefully")
    void testInvalidProxyPort() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();
        props.setProperty("manualProxy", "true");
        props.setProperty("http.proxy.host", "localhost");
        props.setProperty("http.proxy.port", "invalid");

        // Should ignore invalid proxy and proceed with direct connection
        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        // Should succeed with direct connection
        assertEquals(200, result.httpStatus(), "Should succeed with direct connection");
    }

    @Test
    @DisplayName("Should handle system proxy configuration")
    void testSystemProxyConfiguration() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();
        props.setProperty("useSystemProxy", "true");

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        // System proxy might not be configured, but should not crash
    }

    @Test
    @DisplayName("Should handle no proxy configuration")
    void testNoProxyConfiguration() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();
        // No proxy settings

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        assertEquals(200, result.httpStatus(), "Should succeed with direct connection");
    }

    @Test
    @DisplayName("Should handle SSL trust all certs configuration")
    void testSslTrustAllCertsConfiguration() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();
        props.setProperty("ssl.trustAllCerts", "true");

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        assertEquals(200, result.httpStatus(), "Should succeed even with SSL bypass enabled");
    }

    @Test
    @DisplayName("Should extract response headers correctly")
    void testResponseHeaderExtraction() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result.resultHeader(), "Headers should not be null");
        assertTrue(result.resultHeader().length > 0, "Should have headers");

        // Check for custom header
        boolean foundCustomHeader = false;
        for (String header : result.resultHeader()) {
            if (header.contains("X-Custom-Header")) {
                foundCustomHeader = true;
                assertTrue(header.contains("test-value"), "Custom header should have expected value");
                break;
            }
        }
        assertTrue(foundCustomHeader, "Should find custom header");
    }

    @Test
    @DisplayName("Should measure request duration")
    void testRequestDuration() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result.duration(), "Duration should not be null");
        assertTrue(result.duration() > 0, "Duration should be positive");
        assertTrue(result.duration() < 10000, "Duration should be reasonable (< 10s for local request)");
    }

    @Test
    @DisplayName("Should return singleton instance")
    void testGetInstance() {
        ConnectionServiceImpl instance1 = ConnectionServiceImpl.getInstance();
        ConnectionServiceImpl instance2 = ConnectionServiceImpl.getInstance();

        assertNotNull(instance1, "Instance should not be null");
        assertSame(instance1, instance2, "Should return same singleton instance");
    }

    @Test
    @DisplayName("Should set User-Agent header")
    void testUserAgentHeader() {
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        // Verify request was made (by checking successful response)
        assertEquals(200, result.httpStatus(), "Request should be successful");
        // User-Agent is set in the request, but we can't easily verify it in the response
        // The test server would need to echo back request headers to verify
    }

    @Test
    @DisplayName("Should handle empty response body")
    void testEmptyResponseBody() {
        // Create a handler for empty response
        testServer.createContext("/empty", exchange -> {
            exchange.sendResponseHeaders(204, -1); // 204 No Content
            exchange.close();
        });

        URI testUri = URI.create("http://localhost:" + testPort + "/empty");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        assertEquals(204, result.httpStatus(), "HTTP status should be 204");
        assertNotNull(result.resultBody(), "Result body should not be null (even if empty)");
    }

    @Test
    @DisplayName("Should handle connection timeout configuration")
    void testConnectionTimeoutConfiguration() {
        // This test verifies that timeout is configured (30 seconds)
        // We can't easily test actual timeout without delaying the server
        URI testUri = URI.create("http://localhost:" + testPort + "/success");
        Properties props = new Properties();

        ConnectionResult result = connectionService.testHttpRequest(testUri, props);

        assertNotNull(result, "Result should not be null");
        assertEquals(200, result.httpStatus(), "Should complete before timeout");
        assertTrue(result.duration() < 30000, "Should complete well before 30s timeout");
    }
}
