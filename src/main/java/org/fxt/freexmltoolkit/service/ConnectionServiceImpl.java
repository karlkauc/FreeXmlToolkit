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
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
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
            SSLConnectionSocketFactory sslSocketFactory;
            if (trustAllCerts) {
                logger.warn("!!! SECURITY WARNING !!! SSL certificate validation is disabled. All certificates will be trusted.");
                SSLContext sslContext = createTrustAllSslContext();
                sslSocketFactory = new SSLConnectionSocketFactory(
                        sslContext,
                        SUPPORTED_PROTOCOLS,
                        null,
                        NoopHostnameVerifier.INSTANCE);
            } else {
                // Use the default SSL context but explicitly set the allowed TLS versions.
                sslSocketFactory = new SSLConnectionSocketFactory(
                        SSLContext.getDefault(),
                        SUPPORTED_PROTOCOLS,
                        null, // Default ciphers
                        null);
            }

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslSocketFactory)
                    .build();
            BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry);
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
            // Use the default system proxy selector. This requires the JVM to be started with -Djava.net.useSystemProxies=true
            ProxySelector proxySelector = ProxySelector.getDefault();
            clientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(proxySelector));
        }

        long start = System.currentTimeMillis();

        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            HttpGet httpGet = new HttpGet(url);

            return httpClient.execute(httpGet, response -> {
                String[] headers = Arrays.stream(response.getHeaders())
                        .map(header -> header.getName() + ":" + header.getValue())
                        .toArray(String[]::new);

                String text = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));

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
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLSv1.3");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }
}
