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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.ConnectionResult;

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
import java.util.Optional;
import java.util.Properties;

/**
 * Implementation of the ConnectionService interface.
 * Provides methods for executing HTTP requests and retrieving text content from URLs.
 * Uses native Java HttpsURLConnection for better corporate environment compatibility.
 */
public class ConnectionServiceImpl implements ConnectionService {

    private final static Logger logger = LogManager.getLogger(ConnectionServiceImpl.class);
    private static final ConnectionServiceImpl instance = new ConnectionServiceImpl();
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);

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
        HttpURLConnection connection = null;

        try {
            // Log proxy configuration for debugging
            logger.debug("Testing HTTP request to: {}", uri);
            logger.debug("Proxy properties: manualProxy={}, useSystemProxy={}, http.proxyHost={}, socksProxyHost={}",
                    testProperties.getProperty("manualProxy", "false"),
                    testProperties.getProperty("useSystemProxy", "false"),
                    testProperties.getProperty("http.proxy.host", "not set"),
                    System.getProperty("socksProxyHost", "not set"));

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

            // Configure proxy authentication
            configureProxyAuthentication(testProperties);

            // Configure proxy
            Proxy proxy = configureProxy(testProperties);

            // Create connection based on proxy configuration:
            // - non-null, non-NO_PROXY: use explicit proxy
            // - Proxy.NO_PROXY: user chose no proxy, bypass all
            // - null: delegate to Java's ProxySelector (handles PAC/WPAD)
            URL url = uri.toURL();

            if (proxy == null) {
                // Delegate to Java's ProxySelector (PAC/WPAD auto-configuration)
                connection = (HttpURLConnection) url.openConnection();
                logger.debug("Using Java ProxySelector (PAC/WPAD delegation)");
            } else if (proxy == Proxy.NO_PROXY) {
                connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
                logger.debug("Using direct connection (user chose no proxy)");
            } else {
                connection = (HttpURLConnection) url.openConnection(proxy);
                logger.debug("Using explicit proxy: {}", proxy);
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

            // Execute request and follow redirects manually (including HTTP→HTTPS)
            int responseCode = connection.getResponseCode();
            String responseBody = "";
            URI finalUri = uri;
            int maxRedirects = 5;
            int redirectCount = 0;

            while (responseCode >= 300 && responseCode < 400 && redirectCount < maxRedirects) {
                String location = connection.getHeaderField("Location");
                if (location == null || location.isEmpty()) {
                    logger.warn("Redirect response {} without Location header for: {}", responseCode, finalUri);
                    break;
                }

                // Close current connection
                connection.disconnect();

                // Parse redirect location (can be relative or absolute)
                URI redirectUri;
                if (location.startsWith("http://") || location.startsWith("https://")) {
                    redirectUri = new URI(location);
                } else {
                    // Relative URL - resolve against current URL
                    redirectUri = finalUri.resolve(location);
                }

                logger.debug("Following redirect {} → {} ({})", responseCode, redirectUri, redirectCount + 1);

                // Create new connection for redirect (same proxy logic)
                url = redirectUri.toURL();
                if (proxy == null) {
                    connection = (HttpURLConnection) url.openConnection();
                } else {
                    connection = (HttpURLConnection) url.openConnection(proxy);
                }

                // Configure connection
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "FreeXmlToolkit/2.0");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

                // Apply SSL settings if redirected to HTTPS
                if (trustAllCerts && connection instanceof HttpsURLConnection httpsConnection) {
                    try {
                        applySslBypassToConnection(httpsConnection);
                    } catch (Exception sslEx) {
                        logger.warn("Failed to apply SSL bypass to redirect connection: {}", sslEx.getMessage());
                    }
                }

                // Get response
                responseCode = connection.getResponseCode();
                finalUri = redirectUri;
                redirectCount++;
            }

            // Read final response
            responseBody = readResponse(connection);

            logger.debug("HTTP request completed: {} - Status: {}, Response length: {} (after {} redirects)",
                    finalUri, responseCode, responseBody.length(), redirectCount);

            return new ConnectionResult(
                    finalUri,
                    responseCode,
                    System.currentTimeMillis() - start,
                    getHeaders(connection),
                    responseBody
            );

        } catch (Exception e) {
            // Log as WARN instead of ERROR since connection failures are expected in some network environments
            // (e.g., blocked URLs, offline mode, proxy issues)
            logger.warn("HTTP request failed for {}: {} ({})", uri, e.getClass().getSimpleName(), e.getMessage());
            logger.debug("Full stack trace:", e);  // Full trace only in DEBUG mode
            return new ConnectionResult(
                    uri,
                    0,
                    System.currentTimeMillis() - start,
                    new String[0],
                    e.getMessage()
            );
        } finally {
            // Always disconnect to release resources immediately
            if (connection != null) {
                connection.disconnect();
                logger.debug("HTTP connection closed for: {}", uri);
            }
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
     * Configures proxy authentication if manual proxy with credentials is enabled.
     *
     * <p>This method installs a custom Authenticator that provides proxy credentials
     * to HttpURLConnection when connecting through an authenticated proxy.
     *
     * <p>Security: The authenticator only responds to PROXY authentication requests.
     * Server authentication requests are ignored to avoid credential leakage.
     *
     * @param props properties containing proxy settings
     */
    private void configureProxyAuthentication(Properties props) {
        boolean useManualProxy = Boolean.parseBoolean(props.getProperty("manualProxy", "false"));

        if (!useManualProxy) {
            // Not using manual proxy - no authentication needed
            logger.debug("Manual proxy not enabled, skipping proxy authentication");
            return;
        }

        String proxyUser = props.getProperty("http.proxy.user", "").trim();
        String proxyPassword = props.getProperty("http.proxy.password", "").trim();

        // Only configure authentication if credentials are provided
        if (!proxyUser.isEmpty()) {
            logger.debug("Configuring proxy authentication for user: {}", proxyUser);
            // DO NOT log password!

            ProxyAuthenticator authenticator = new ProxyAuthenticator(proxyUser, proxyPassword);
            Authenticator.setDefault(authenticator);

            logger.debug("Proxy authenticator installed successfully");
        } else {
            logger.debug("No proxy credentials provided, proxy authentication not configured");
        }
    }

    /**
     * Configures proxy settings based on properties.
     *
     * <p>Supports three modes:
     * <ul>
     *   <li>Manual proxy: Uses explicitly configured proxy host/port</li>
     *   <li>System proxy: Detects proxy using NTLM-aware system proxy detection.
     *       If custom detection fails, returns {@code null} to delegate to Java's
     *       built-in ProxySelector (which handles PAC/WPAD auto-configuration).</li>
     *   <li>No proxy: Returns {@link Proxy#NO_PROXY} to explicitly bypass all proxies</li>
     * </ul>
     *
     * <p><b>Return value semantics:</b>
     * <ul>
     *   <li>{@code non-null Proxy} (not NO_PROXY): Use this explicit proxy</li>
     *   <li>{@code Proxy.NO_PROXY}: User explicitly chose no proxy — bypass all</li>
     *   <li>{@code null}: Delegate to Java's ProxySelector (PAC/WPAD support)</li>
     * </ul>
     *
     * @param props properties containing proxy configuration
     * @return a Proxy instance, {@link Proxy#NO_PROXY}, or null to delegate to ProxySelector
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
                    logger.debug("Using manual proxy configuration: {}:{}", proxyHost, proxyPort);
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
                } catch (NumberFormatException e) {
                    logger.error("Invalid proxy port: {}", proxyPortStr);
                }
            }
        }

        if (useSystemProxy) {
            // Use NTLM-aware system proxy detection
            logger.debug("Using system proxy detection with NTLM support");

            Optional<SystemProxyDetector.ProxyConfig> proxyConfig = SystemProxyDetector.detectSystemProxy();

            if (proxyConfig.isPresent()) {
                SystemProxyDetector.ProxyConfig config = proxyConfig.get();
                // Configure JVM proxy properties for NTLM authentication
                SystemProxyDetector.configureProxy(config.host(), config.port());
                logger.info("System proxy detected and configured: {}:{}", config.host(), config.port());
                return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.host(), config.port()));
            } else {
                // Custom detection found nothing, but system proxy is enabled.
                // Return null to delegate to Java's ProxySelector which handles
                // PAC/WPAD auto-configuration transparently.
                logger.debug("No static proxy detected; delegating to Java ProxySelector (PAC/WPAD)");
                return null;
            }
        }

        // User explicitly chose "No Proxy" (both manual and system are false)
        logger.debug("No proxy mode selected, using Proxy.NO_PROXY");
        return Proxy.NO_PROXY;
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
