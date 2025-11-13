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

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ConnectionResult;

/**
 * Implementation of the ConnectionService interface.
 * Provides methods for executing HTTP requests and retrieving text content from URLs.
 * Uses native Java HttpsURLConnection for better corporate environment compatibility.
 */
public class ConnectionServiceImpl implements ConnectionService {

    private final static Logger logger = LogManager.getLogger(ConnectionServiceImpl.class);
    private static final ConnectionServiceImpl instance = new ConnectionServiceImpl();
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

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
        ConnectionResult result = executeHttpRequest(uri);
        if (result.httpStatus() >= 200 && result.httpStatus() < 400) {
            return result.resultBody();
        }
        throw new RuntimeException("Failed to retrieve content: HTTP " + result.httpStatus() + " - " + result.resultBody());
    }

    /**
     * Executes an HTTP request to the specified URL using saved properties.
     *
     * @param uri the URI of the target URL
     * @return a ConnectionResult object containing the details of the HTTP response
     */
    @Override
    public ConnectionResult executeHttpRequest(URI uri) {
        Properties properties = propertiesService.loadProperties();
        return testHttpRequest(uri, properties);
    }

    /**
     * Executes an HTTP request using a transient set of properties, for testing purposes.
     *
     * @param uri            the URI of the target URL
     * @param testProperties a Properties object containing the connection settings to test
     * @return a ConnectionResult object containing the details of the HTTP response
     */
    @Override
    public ConnectionResult testHttpRequest(URI uri, Properties testProperties) {
        long start = System.currentTimeMillis();
        
        try {
            // Configure SSL bypass if enabled
            boolean trustAllCerts = Boolean.parseBoolean(testProperties.getProperty("ssl.trustAllCerts", "false"));
            if (trustAllCerts) {
                try {
                    configureTrustAllSSL();
                    logger.warn("!!! SECURITY WARNING !!! SSL certificate validation is disabled.");
                } catch (Exception sslEx) {
                    logger.error("Failed to configure SSL bypass, proceeding with default SSL settings: {}", sslEx.getMessage());
                }
            }
            
            // Configure proxy
            Proxy proxy = configureProxy(testProperties);
            
            // Create connection
            URL url = uri.toURL();
            HttpURLConnection connection;
            
            if (proxy != null) {
                connection = (HttpURLConnection) url.openConnection(proxy);
                logger.debug("Using proxy: {}", proxy);
            } else {
                connection = (HttpURLConnection) url.openConnection();
                logger.debug("Using direct connection");
            }
            
            // Configure connection
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "FreeXmlToolkit/2.0");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            
            // Apply SSL settings to this specific connection if it's HTTPS
            if (trustAllCerts && connection instanceof HttpsURLConnection httpsConnection) {
                try {
                    applySslBypassToConnection(httpsConnection);
                } catch (Exception sslEx) {
                    logger.warn("Failed to apply SSL bypass to connection, using default SSL settings: {}", sslEx.getMessage());
                }
            }
            
            // Execute request
            int responseCode = connection.getResponseCode();
            String responseBody = readResponse(connection);
            
            logger.debug("HTTP request completed: {} - Status: {}, Response length: {}", 
                    uri, responseCode, responseBody.length());
            
            return new ConnectionResult(
                    uri,
                    responseCode,
                    System.currentTimeMillis() - start,
                    getHeaders(connection),
                    responseBody
            );
            
        } catch (Exception e) {
            logger.error("HTTP request failed: {}", e.getMessage(), e);
            return new ConnectionResult(
                    uri,
                    0,
                    System.currentTimeMillis() - start,
                    new String[0],
                    e.getMessage()
            );
        }
    }

    /**
     * Configures SSL to trust all certificates if enabled (global settings)
     */
    private void configureTrustAllSSL() throws NoSuchAlgorithmException, KeyManagementException {
        // Create trust-all manager
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Trust all
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Trust all
                    }
                }
        };
        
        // Install trust-all SSL context
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    }

    /**
     * Applies SSL bypass settings to a specific HTTPS connection
     */
    private void applySslBypassToConnection(HttpsURLConnection httpsConnection) throws NoSuchAlgorithmException, KeyManagementException {
        // Create trust-all manager for this specific connection
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Trust all
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Trust all
                    }
                }
        };
        
        // Create SSL context for this connection
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        
        // Apply to this specific connection
        httpsConnection.setSSLSocketFactory(sc.getSocketFactory());
        httpsConnection.setHostnameVerifier((hostname, session) -> true);
        
        logger.debug("SSL bypass applied to connection for: {}", httpsConnection.getURL());
    }

    /**
     * Configures proxy settings based on properties
     */
    private Proxy configureProxy(Properties props) {
        boolean useManualProxy = Boolean.parseBoolean(props.getProperty("manualProxy", "false"));
        boolean useSystemProxy = Boolean.parseBoolean(props.getProperty("useSystemProxy", "false"));
        
        if (useManualProxy) {
            String proxyHost = props.getProperty("http.proxy.host", "");
            String proxyPortStr = props.getProperty("http.proxy.port", "");
            
            if (!proxyHost.isEmpty() && !proxyPortStr.isEmpty()) {
                try {
                    int proxyPort = Integer.parseInt(proxyPortStr);
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
                } catch (NumberFormatException e) {
                    logger.error("Invalid proxy port: {}", proxyPortStr);
                }
            }
        }
        
        if (useSystemProxy) {
            // Use system proxy settings
            System.setProperty("java.net.useSystemProxies", "true");
            return null; // Let Java handle system proxy automatically
        }
        
        return null; // No proxy
    }
    
    /**
     * Reads the response from an HTTP connection
     */
    private String readResponse(HttpURLConnection connection) throws IOException {
        StringBuilder response = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        connection.getResponseCode() >= 400 ? 
                                connection.getErrorStream() : connection.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
        }
        
        return response.toString();
    }
    
    /**
     * Extracts headers from an HTTP connection
     */
    private String[] getHeaders(HttpURLConnection connection) {
        return connection.getHeaderFields().entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .map(entry -> entry.getKey() + ": " + String.join(", ", entry.getValue()))
                .toArray(String[]::new);
    }
}
