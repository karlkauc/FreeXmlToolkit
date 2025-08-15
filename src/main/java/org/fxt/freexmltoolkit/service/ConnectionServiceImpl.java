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

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ConnectionResult;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Implementation of the ConnectionService interface.
 * Provides methods for executing HTTP requests and retrieving text content from URLs.
 */
public class ConnectionServiceImpl implements ConnectionService {

    private final static Logger logger = LogManager.getLogger(ConnectionService.class);
    private static final ConnectionServiceImpl instance = new ConnectionServiceImpl();
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    private static final String[] SUPPORTED_PROTOCOLS = {"TLSv1.3", "TLSv1.2"};

    private ConnectionServiceImpl() {
    }

    /**
     * Returns the singleton instance of ConnectionServiceImpl.
     *
     * @return the singleton instance
     */
    public static ConnectionServiceImpl getInstance() {
        return instance;
    }

    /**
     * Retrieves the text content from the specified URL.
     *
     * @param uri the URI of the target URL
     * @return a String containing the text content of the URL
     */
    @Override
    public String getTextContentFromURL(URI uri) {
        return executeHttpRequest(uri).resultBody();
    }

    /**
     * Executes an HTTP request to the specified URL using saved properties.
     *
     * @param url the URI of the target URL
     * @return a ConnectionResult object containing the details of the HTTP response
     */
    @Override
    public ConnectionResult executeHttpRequest(URI url) {
        var props = propertiesService.loadProperties();
        return buildConnectionWithProperties(url, props);
    }

    /**
     * Executes an HTTP request using a transient set of properties, for testing purposes.
     *
     * @param url            the URI of the target URL
     * @param testProperties a Properties object containing the connection settings to test
     * @return a ConnectionResult object containing the details of the HTTP response
     */
    @Override
    public ConnectionResult testHttpRequest(URI url, Properties testProperties) {
        return buildConnectionWithProperties(url, testProperties);
    }

    /**
     * Builds and executes an HTTP request based on a given set of properties.
     *
     * @param url   The target URL.
     * @param props The properties to use for configuration (e.g., proxy settings).
     * @return The result of the connection attempt.
     */
    private ConnectionResult buildConnectionWithProperties(URI url, Properties props) {
        boolean useManualProxy = Boolean.parseBoolean(props.getProperty("manualProxy", "false"));
        String proxyHost = props.getProperty("http.proxy.host", "");
        String proxyPortStr = props.getProperty("http.proxy.port", "");
        String proxyUser = props.getProperty("http.proxy.user", "");
        String proxyPass = props.getProperty("http.proxy.password", "");
        String noProxyHosts = props.getProperty("noProxyHost", "");
        boolean trustAllCerts = Boolean.parseBoolean(props.getProperty("ssl.trustAllCerts", "false"));

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        HttpClientBuilder clientBuilder = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider);

        try {
            // Konfiguriere SSL/TLS mit den unterstützten Protokollen
            SSLContext sslContext;
            if (trustAllCerts) {
                logger.warn("!!! SECURITY WARNING !!! SSL certificate validation is disabled. All certificates will be trusted.");
                sslContext = createTrustAllSslContext();
            } else {
                sslContext = createSecureSslContext();
            }

            // Konfiguriere SSL mit den unterstützten Protokollen
            configureSslProtocols();

            BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager();
            connectionManager.setSocketConfig(SocketConfig.custom()
                    .setSoTimeout(Timeout.ofSeconds(30))
                    .build());
            clientBuilder.setConnectionManager(connectionManager);

        } catch (Exception e) {
            logger.error("Could not configure SSL with specific TLS versions. Falling back to default connection manager.", e);
            clientBuilder.setConnectionManager(new BasicHttpClientConnectionManager());
        }

        // NEW LOGIC: Use manual proxy if enabled and valid, otherwise default to system proxy.
        if (useManualProxy && !proxyHost.isBlank() && !proxyPortStr.isBlank()) {
            logger.debug("Using manual proxy settings: {}:{}", proxyHost, proxyPortStr);

            if (!noProxyHosts.isBlank()) {
                logger.debug("Setting non-proxy hosts: {}", noProxyHosts);
                System.setProperty("http.nonProxyHosts", noProxyHosts.replace(',', '|').replace(";", "|"));
            } else {
                System.clearProperty("http.nonProxyHosts");
            }

            try {
                int proxyPort = Integer.parseInt(proxyPortStr);
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                clientBuilder.setProxy(proxy);

                if (!proxyUser.isBlank()) {
                    logger.debug("... providing user credentials for proxy.");
                    Credentials basicCredentials = new UsernamePasswordCredentials(proxyUser, proxyPass.toCharArray());
                    credentialsProvider.setCredentials(new AuthScope(proxy), basicCredentials);
                }
            } catch (NumberFormatException e) {
                logger.error("Invalid proxy port number provided: '{}'", proxyPortStr, e);
                return new ConnectionResult(url, 0, 0L, new String[0], "Invalid proxy port: " + proxyPortStr);
            }
        } else {
            logger.debug("Manual proxy not configured or incomplete. Defaulting to system proxy settings.");
            // Use the default system proxy selector with modern approach
            ProxySelector systemProxySelector = getSystemProxySelector();
            clientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(systemProxySelector));
        }

        long start = System.currentTimeMillis();

        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            HttpGet httpGet = new HttpGet(url);

            return httpClient.execute(httpGet, response -> {
                String[] headers = Arrays.stream(response.getHeaders())
                        .map(header -> header.getName() + ":" + header.getValue())
                        .toArray(String[]::new);

                String text;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
                    text = reader.lines().collect(Collectors.joining("\n"));
                }

                return new ConnectionResult(
                        url,
                        response.getCode(),
                        System.currentTimeMillis() - start,
                        headers,
                        text);
            });
        } catch (IOException e) {
            logger.error("HTTP request failed: {}", e.getMessage(), e);
            return new ConnectionResult(url, 0, 0L, new String[0], e.getMessage());
        } finally {
            // Clean up the system property to avoid side effects
            System.clearProperty("http.nonProxyHosts");
        }
    }

    /**
     * Konfiguriert die SSL-Protokolle für HTTPS-Verbindungen
     */
    private void configureSslProtocols() {
        // Setze die unterstützten TLS-Protokolle als System-Property
        System.setProperty("https.protocols", String.join(",", SUPPORTED_PROTOCOLS));

        // Zusätzliche Sicherheitseinstellungen
        System.setProperty("jdk.tls.client.protocols", String.join(",", SUPPORTED_PROTOCOLS));

        logger.debug("SSL protocols configured: {}", String.join(",", SUPPORTED_PROTOCOLS));
    }

    /**
     * Creates a secure SSLContext with modern TLS configuration.
     *
     * @return An SSLContext configured with secure defaults.
     */
    private SSLContext createSecureSslContext() throws NoSuchAlgorithmException, KeyManagementException {
        // Use TLS 1.3 as the default, falling back to TLS 1.2 if needed
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, new java.security.SecureRandom());
        return sslContext;
    }

    /**
     * Creates an SSLContext that trusts all certificates. This is insecure and should
     * only be used for development or testing purposes.
     *
     * @return An SSLContext configured to bypass certificate validation.
     */
    private SSLContext createTrustAllSslContext() throws NoSuchAlgorithmException, KeyManagementException {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0]; // Return empty array instead of null
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Trust all client certificates
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Trust all server certificates
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }

    /**
     * Gets the system proxy selector with proper error handling.
     *
     * @return The system proxy selector or a default one if not available.
     */
    private ProxySelector getSystemProxySelector() {
        try {
            ProxySelector systemProxySelector = ProxySelector.getDefault();
            if (systemProxySelector != null) {
                return systemProxySelector;
            } else {
                logger.warn("System proxy selector is null, using default proxy selector");
                return ProxySelector.getDefault();
            }
        } catch (SecurityException e) {
            logger.warn("Security manager prevents access to system proxy selector: {}", e.getMessage());
            return ProxySelector.getDefault();
        }
    }
}
