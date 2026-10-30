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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.ConnectionResult;

/**
 * Implementation of the ConnectionService interface.
 * Provides methods for executing HTTP requests and retrieving text content from URLs.
 * Uses native Java HttpsURLConnection for better corporate environment compatibility.
 */
public class ConnectionServiceImpl implements ConnectionService {

    private static final Logger logger = LogManager.getLogger(ConnectionServiceImpl.class);
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
            ConnectionAttempt attempt = openConnectionFollowingRedirects(uri, testProperties);
            connection = attempt.connection();

            // Read final response
            String responseBody = readResponse(connection);

            logger.debug("HTTP request completed: {} - Status: {}, Response length: {} (after {} redirects)",
                    attempt.finalUri(), attempt.responseCode(), responseBody.length(), attempt.redirectCount());

            return new ConnectionResult(
                    attempt.finalUri(),
                    attempt.responseCode(),
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
     * Fetches {@code uri} as raw bytes, reusing the exact same proxy/authentication/SSL/redirect
     * handling as {@link #testHttpRequest(URI, Properties)}. Used by {@link SchemaResourceCache}
     * so remote schema downloads go through the configured corporate-proxy settings instead of
     * bypassing them (which yields HTTP 407 behind an authenticating proxy).
     *
     * @param uri the URI of the target resource
     * @return the raw HTTP response
     * @throws IOException on a transport-level failure (DNS, connection refused, TLS, etc.)
     */
    @Override
    public BinaryResponse fetchBinary(URI uri) throws IOException {
        Properties properties = propertiesService.loadProperties();
        HttpURLConnection connection = null;

        try {
            ConnectionAttempt attempt = openConnectionFollowingRedirects(uri, properties);
            connection = attempt.connection();

            byte[] body = readResponseBytes(connection);

            logger.debug("Binary HTTP request completed: {} - Status: {}, Body length: {} (after {} redirects)",
                    attempt.finalUri(), attempt.responseCode(), body.length, attempt.redirectCount());

            return new BinaryResponse(attempt.finalUri(), attempt.responseCode(), getSingleValuedHeaders(connection), body);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            // Transport-level failures must surface as IOException (never swallowed into a status)
            // so SchemaResourceCache's describeCause() can report the root cause (proxy/TLS/DNS).
            throw new IOException(e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
                logger.debug("Binary HTTP connection closed for: {}", uri);
            }
        }
    }

    /** Max response body kept from a POST (the telemetry API answers with tiny JSON documents). */
    private static final int MAX_POST_RESPONSE_BYTES = 64 * 1024;

    /** Redirects followed by the GET paths (including HTTP → HTTPS). */
    private static final int MAX_REDIRECTS = 5;

    /**
     * POSTs {@code jsonBody} through a connection from {@link #newConnection} (proxy, proxy
     * authentication, SSL as configured). Redirects are not followed for POST.
     */
    @Override
    public HttpPostResult postJson(URI uri, String jsonBody, Duration timeout) throws IOException {
        Properties properties = propertiesService.loadProperties();
        HttpURLConnection connection = null;
        int timeoutMs = (int) Math.max(1, Math.min(Integer.MAX_VALUE, timeout == null ? 10_000 : timeout.toMillis()));
        try {
            connection = newConnection(uri, properties);
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] bytes = (jsonBody == null ? "" : jsonBody).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }
            int status = connection.getResponseCode();
            String body = readPostResponse(connection, status);
            logger.debug("HTTP POST completed: {} - Status: {}", uri, status);
            return new HttpPostResult(status, body);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * A connection to {@code uri} with the saved settings, not yet connected: for callers that stream
     * a download or set their own headers. They must not open connections any other way.
     */
    @Override
    public HttpURLConnection openConnection(URI uri) throws IOException {
        try {
            return newConnection(uri, propertiesService.loadProperties());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static String readPostResponse(HttpURLConnection connection, int status) {
        try (InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            if (in == null) {
                return "";
            }
            return new String(in.readNBytes(MAX_POST_RESPONSE_BYTES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Result of {@link #openConnectionFollowingRedirects(URI, Properties)}: a connection
     * positioned after the final (non-redirect) response code has been read. The caller is
     * responsible for reading the body and disconnecting.
     *
     * @param connection    the final connection
     * @param finalUri      the URI actually connected to (after following any redirects)
     * @param responseCode  the final HTTP response code
     * @param redirectCount how many redirects were followed
     */
    private record ConnectionAttempt(HttpURLConnection connection, URI finalUri, int responseCode, int redirectCount) {}

    /**
     * Opens a GET to {@code uri} with the settings in {@code properties} and follows up to
     * {@link #MAX_REDIRECTS} redirects (including HTTP → HTTPS), each through {@link #newConnection}.
     *
     * @return the final connection, positioned after the response code has been read
     */
    private ConnectionAttempt openConnectionFollowingRedirects(URI uri, Properties properties) throws Exception {
        logger.debug("HTTP GET {}", uri);
        HttpURLConnection connection = newGet(uri, properties);
        int responseCode = connection.getResponseCode();
        URI finalUri = uri;
        int redirectCount = 0;
        while (responseCode >= 300 && responseCode < 400 && redirectCount < MAX_REDIRECTS) {
            String location = connection.getHeaderField("Location");
            if (location == null || location.isEmpty()) {
                logger.warn("Redirect response {} without Location header for: {}", responseCode, finalUri);
                break;
            }
            connection.disconnect();
            URI redirectUri = location.startsWith("http://") || location.startsWith("https://")
                    ? new URI(location) : finalUri.resolve(location);
            logger.debug("Following redirect {} → {} ({})", responseCode, redirectUri, redirectCount + 1);
            connection = newGet(redirectUri, properties);
            responseCode = connection.getResponseCode();
            finalUri = redirectUri;
            redirectCount++;
        }
        return new ConnectionAttempt(connection, finalUri, responseCode, redirectCount);
    }

    private HttpURLConnection newGet(URI uri, Properties properties) throws Exception {
        HttpURLConnection connection = newConnection(uri, properties);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return connection;
    }

    /**
     * The one place where the application opens HTTP connections: proxy for this URL (mode,
     * exceptions), proxy authentication (credentials or Windows sign-on, set on this connection
     * only), timeouts, User-Agent and — when switched on — the per-connection certificate bypass.
     */
    private HttpURLConnection newConnection(URI uri, Properties properties) throws Exception {
        ProxySettings settings = ProxySettings.from(properties);
        Proxy proxy = proxyFor(settings, uri);
        URL url = uri.toURL();
        HttpURLConnection connection;
        if (proxy == null) {
            // Delegate to the ProxySelector (the system's with PAC/WPAD, wrapped by SettingsProxySelector)
            connection = (HttpURLConnection) url.openConnection();
        } else {
            connection = (HttpURLConnection) url.openConnection(proxy);
        }
        connection.setAuthenticator(ProxyAuthenticator.forSettings(settings));
        connection.setConnectTimeout(settings.connectTimeoutMs());
        connection.setReadTimeout(settings.readTimeoutMs());
        connection.setRequestProperty("User-Agent",
                "FreeXmlToolkit/" + org.fxt.freexmltoolkit.util.VersionUtil.getVersion());

        // SECURITY: the bypass applies ONLY to this connection, never to the JVM-global
        // HttpsURLConnection defaults (see applySslBypassToConnection).
        boolean trustAllCerts = Boolean.parseBoolean(properties.getProperty("ssl.trustAllCerts", "false"));
        if (trustAllCerts && connection instanceof HttpsURLConnection httpsConnection) {
            logger.warn("!!! SECURITY WARNING !!! SSL certificate validation is disabled for {}", uri);
            try {
                applySslBypassToConnection(httpsConnection);
            } catch (Exception sslEx) {
                logger.warn("Failed to apply SSL bypass, using default SSL settings: {}", sslEx.getMessage());
            }
        }
        return connection;
    }

    /**
     * Applies SSL bypass settings to a specific HTTPS connection.
     *
     * <p><b>Security:</b> the trust-all behaviour is intentionally scoped to the single supplied
     * connection. The JVM-global {@code HttpsURLConnection} defaults are never modified, so
     * disabling certificate validation here cannot leak into unrelated HTTPS connections (e.g.
     * auto-update downloads), which must always validate certificates.
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

    @Override
    public Proxy resolveProxy() {
        return proxyFor(ProxySettings.from(propertiesService.loadProperties()), null);
    }

    /**
     * The proxy for {@code uri} (nullable: no URL known):
     * <ul>
     *   <li>{@link Proxy#NO_PROXY}: no proxy, a configured exception or a local address</li>
     *   <li>a proxy: the manual one, or the system proxy found by {@link SystemProxyDetector}</li>
     *   <li>{@code null}: system mode without a static proxy — the ProxySelector decides (PAC/WPAD)</li>
     * </ul>
     */
    static Proxy proxyFor(ProxySettings settings, URI uri) {
        if (uri != null && settings.bypasses(uri.getHost())) {
            return Proxy.NO_PROXY;
        }
        return switch (settings.mode()) {
            case NONE -> Proxy.NO_PROXY;
            case MANUAL -> {
                Proxy manual = settings.manualProxy();
                if (manual == null) {
                    logger.warn("Manual proxy selected without a valid host and port; connecting directly");
                    yield Proxy.NO_PROXY;
                }
                yield manual;
            }
            case SYSTEM -> {
                Optional<SystemProxyDetector.ProxyConfig> detected = SystemProxyDetector.detectSystemProxy();
                if (detected.isPresent()) {
                    SystemProxyDetector.ProxyConfig config = detected.get();
                    yield new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(config.host(), config.port()));
                }
                yield null;
            }
        };
    }

    /**
     * Applies the saved settings JVM-wide, for every connection the application does not open
     * itself (libraries, WebView): the {@link SettingsProxySelector} and the proxy
     * {@link Authenticator}. Called at startup and whenever the settings are saved.
     */
    public void applyGlobalSettings() {
        applyGlobalSettings(ProxySettings.from(propertiesService.loadProperties()));
    }

    static void applyGlobalSettings(ProxySettings settings) {
        SettingsProxySelector.install(settings);
        Authenticator.setDefault(ProxyAuthenticator.forSettings(settings));
        // Read by the JDK when NTLM is first used (Windows only): a change needs a restart.
        System.setProperty("jdk.http.ntlm.transparentAuth", settings.windowsSignOn() ? "allHosts" : "disabled");
    }

    @Override
    public Map<String, String> xprocProxies() {
        Proxy proxy = proxyFor(ProxySettings.from(propertiesService.loadProperties()), null);
        Map<String, String> proxies = new HashMap<>();
        if (proxy != null && proxy.type() == Proxy.Type.HTTP
                && proxy.address() instanceof InetSocketAddress address) {
            String target = "http://" + address.getHostString() + ":" + address.getPort();
            proxies.put("http", target);
            proxies.put("https", target);
        }
        return proxies;
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
     * Reads the raw response body bytes from an HTTP connection.
     *
     * <p>On an error status (&ge;400) the error stream is read on a best-effort basis: proxies
     * and servers don't always provide one, and the cache callers of {@link #fetchBinary} only
     * need the status code and headers in that case, so a missing/unreadable error body yields an
     * empty array rather than an exception.
     */
    private byte[] readResponseBytes(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() >= 400) {
            try (InputStream errorStream = connection.getErrorStream()) {
                return errorStream != null ? errorStream.readAllBytes() : new byte[0];
            } catch (IOException e) {
                return new byte[0];
            }
        }
        try (InputStream is = connection.getInputStream()) {
            return is.readAllBytes();
        }
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

    /**
     * Extracts the single-valued response headers used by {@link SchemaResourceCache}
     * (Content-Type, Last-Modified, ETag, Content-Length).
     */
    private Map<String, String> getSingleValuedHeaders(HttpURLConnection connection) {
        Map<String, String> headers = new HashMap<>();
        for (String key : new String[]{"Content-Type", "Last-Modified", "ETag", "Content-Length"}) {
            String value = connection.getHeaderField(key);
            if (value != null) {
                headers.put(key, value);
            }
        }
        return headers;
    }
}
