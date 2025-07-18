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

package org.fxt.freexmltoolkit;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.ConnectionServiceImpl;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class NetTest {

    private final static Logger logger = LogManager.getLogger(NetTest.class);

    ConnectionService connectionService = ConnectionServiceImpl.getInstance();

    @Test
    public void testConnection() throws URISyntaxException {
        var result = connectionService.executeHttpRequest(new URI("https://www.google.com"));
        System.out.println("result = " + result.httpStatus());
    }

    @Test
    // @Disabled("Currently not testable")
    public void testService() {

        try {
            URI uri = new URI("https://www.google.com");
            var r = connectionService.getTextContentFromURL(uri);

            // logger.debug(r);
            Assertions.assertTrue(r.startsWith("<!doctype html><html itemscope=\"\""));
        } catch (URISyntaxException e) {
            logger.error(e.getMessage());
        }
    }

    @Test
    public void t() {
        try {
            System.setProperty("java.net.useSystemProxies", "true");
            List<Proxy> l = ProxySelector.getDefault().select(
                    new URI("http://www.google.com/"));

            for (Proxy proxy : l) {
                System.out.println("proxy type: " + proxy.type());
                InetSocketAddress addr = (InetSocketAddress) proxy.address();

                if (addr == null) {
                    System.out.println("No Proxy");
                } else {
                    System.out.println("proxy hostname: " + addr.getHostName());
                    System.out.println("proxy port: " + addr.getPort());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testNewWithJdkHttpClient() {
        var properties = PropertiesServiceImpl.getInstance().loadProperties();
        // Stellt sicher, dass die Authentifizierungsschemata für den Proxy aktiviert sind
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");

        // Lädt die Proxy-Konfiguration aus den Properties
        final String httpProxyHost = properties.get("http.proxy.host").toString();
        final int httpProxyPort = Integer.parseInt(properties.get("http.proxy.port").toString());
        final String httpProxyUser = properties.get("http.proxy.user").toString();
        final String httpProxyPassword = properties.get("http.proxy.password").toString();

        // 1. Setzt einen standardmäßigen Authenticator für die Proxy-Anmeldeinformationen.
        //    Dieser wird für die Dauer des Tests für die gesamte JVM gesetzt.
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                // Prüft, ob die Authentifizierungsanfrage für einen Proxy-Server ist
                if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication(httpProxyUser, httpProxyPassword.toCharArray());
                }
                return null;
            }
        });

        try {
            // 2. Erstellt den HttpClient und konfiguriert ihn für die Verwendung des angegebenen Proxys
            HttpClient httpClient = HttpClient.newBuilder()
                    .proxy(ProxySelector.of(new InetSocketAddress(httpProxyHost, httpProxyPort)))
                    .build();

            // 3. Erstellt die HTTP-Anfrage
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://www.github.com"))
                    .GET()
                    .build();

            // 4. Sendet die Anfrage und empfängt die Antwort als Byte-Array
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            System.out.println("response.statusCode() = " + response.statusCode());

            // 5. Verarbeitet die Antwort
            if (response.statusCode() == HttpStatus.SC_OK) { // 200 OK
                // Loggt den Antwort-Body als String zur besseren Lesbarkeit
                String responseBody = new String(response.body(), StandardCharsets.UTF_8);
                logger.debug("Content received, length: {}", responseBody.length());
            } else {
                logger.warn("Request failed with status code: {}", response.statusCode());
            }

        } catch (IOException | InterruptedException | URISyntaxException e) {
            logger.error("Error during HTTP request: {}", e.getMessage(), e);
            // Stellt den Interrupted-Status wieder her, falls eine InterruptedException gefangen wurde
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            // In einer Testumgebung ist es eine gute Praxis, den globalen Authenticator zurückzusetzen,
            // um Seiteneffekte in anderen Tests zu vermeiden.
            Authenticator.setDefault(null);
        }
    }

    /**
     * This is the most reliable test for corporate environments.
     * It uses the application's own ConnectionService, which is based on the
     * robust Apache HttpClient 5, to test the proxy connection and authentication.
     */
    @Test
    public void testConnectionWithApacheHttpClientThroughConnectionService() {
        logger.info("--- Starting Connection Test via ConnectionService (Apache HttpClient 5) ---");

        // 1. Ensure the JVM is set to find the system proxy
        System.setProperty("java.net.useSystemProxies", "true");

        // NEU: Explizites Deaktivieren von SOCKS-Proxy-Einstellungen.
        // Dies verhindert, dass externe Konfigurationen (z.B. von der IDE) den Test stören.
        logger.debug("Clearing any lingering SOCKS proxy system properties to prevent protocol conflicts.");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");

        // 2. Create a temporary properties object to simulate the "Use System Proxy" setting
        var testProperties = new java.util.Properties();
        testProperties.setProperty("useSystemProxy", "true");

        // Load real credentials from the saved properties file
        var savedProps = PropertiesServiceImpl.getInstance().loadProperties();
        testProperties.setProperty("http.proxy.user", savedProps.getProperty("http.proxy.user", ""));
        testProperties.setProperty("http.proxy.password", savedProps.getProperty("http.proxy.password", ""));

        logger.info("Testing with user: {}", testProperties.getProperty("http.proxy.user"));

        // 3. Use the application's ConnectionService to perform the test
        // This service is already configured to handle Basic and NTLM authentication.
        ConnectionService service = ConnectionServiceImpl.getInstance();
        org.fxt.freexmltoolkit.domain.ConnectionResult result = null;
        try {
            result = service.testHttpRequest(new URI("https://www.github.com"), testProperties);
        } catch (URISyntaxException e) {
            Assertions.fail("Failed to create URI", e);
        }

        // 4. Assert the final result
        Assertions.assertNotNull(result, "Connection result should not be null.");
        logger.info("Final status code received from ConnectionService: {}", result.httpStatus());

        Assertions.assertEquals(HttpStatus.SC_OK, result.httpStatus(),
                "Expected HTTP Status 200 OK, but received " + result.httpStatus() +
                        ". Check credentials and proxy logs. Body: " + result.resultBody());

        logger.info("SUCCESS: Connection and authentication through proxy was successful!");
        logger.info("--- Test Finished ---");
    }
}
