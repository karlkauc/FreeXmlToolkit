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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Utility class for system proxy detection with NTLM authentication support.
 *
 * <p>This class provides methods to:
 * <ul>
 *   <li>Enable NTLM authentication for corporate proxy environments</li>
 *   <li>Detect system proxy settings using multiple methods (netsh, registry, environment variables)</li>
 *   <li>Configure JVM proxy properties</li>
 * </ul>
 *
 * <p><b>Important:</b> {@link #enableNtlmAuthentication()} must be called before the first HTTP request
 * for NTLM authentication to work properly. Since Java 8u111, NTLM is disabled by default.
 *
 * <p>The proxy detection uses three methods in priority order:
 * <ol>
 *   <li>netsh winhttp show proxy (Windows system configuration)</li>
 *   <li>Windows Registry (Internet Settings)</li>
 *   <li>Environment variables (HTTP_PROXY, HTTPS_PROXY)</li>
 * </ol>
 *
 * @author Karl Kauc
 * @since 2.0
 */
public final class SystemProxyDetector {

    private static final Logger logger = LogManager.getLogger(SystemProxyDetector.class);

    private SystemProxyDetector() {
        // Utility class - prevent instantiation
    }

    /**
     * Enables NTLM authentication for HTTP proxies.
     *
     * <p><b>MUST be called before the first HTTP request!</b>
     *
     * <p>This method:
     * <ul>
     *   <li>Enables NTLM scheme for HTTPS tunneling (disabled by default since Java 8u111)</li>
     *   <li>Enables NTLM scheme for HTTP proxy authentication</li>
     *   <li>Sets IPv4 preference for compatibility with some proxies</li>
     *   <li>Installs an Authenticator that uses Windows session credentials</li>
     * </ul>
     *
     * <p>On Windows, when the Authenticator returns {@code null}, the JVM automatically
     * uses the Windows session credentials (SSPI/Kerberos) for NTLM authentication.
     */
    public static void enableNtlmAuthentication() {
        // Enable NTLM for HTTPS tunneling (disabled by default since Java 8u111)
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        // Enable NTLM for HTTP proxy authentication
        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
        // Prefer IPv4 (helps with some corporate proxies)
        System.setProperty("java.net.preferIPv4Stack", "true");

        // Install Authenticator that uses Windows session credentials
        // Returning null tells JVM to use integrated Windows authentication
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    logger.debug("NTLM Authenticator invoked for proxy: {} (scheme: {})",
                            getRequestingHost(), getRequestingScheme());
                    // Return null = use Windows session credentials automatically
                    return null;
                }
                return null;
            }
        });

        logger.info("NTLM proxy authentication enabled");
    }

    /**
     * Detects system proxy settings using multiple methods.
     *
     * <p>Detection priority:
     * <ol>
     *   <li>netsh winhttp show proxy (Windows system configuration)</li>
     *   <li>Windows Registry (Internet Settings)</li>
     *   <li>Environment variables (HTTP_PROXY, HTTPS_PROXY)</li>
     * </ol>
     *
     * @return an Optional containing the detected proxy configuration, or empty if no proxy found
     */
    public static Optional<ProxyConfig> detectSystemProxy() {
        logger.debug("Starting system proxy detection...");

        // Method 1: netsh winhttp (Windows system proxy)
        String[] proxyFromNetsh = readProxyFromNetsh();
        if (proxyFromNetsh != null) {
            return createProxyConfig(proxyFromNetsh, "netsh winhttp");
        }

        // Method 2: Windows Registry
        String[] proxyFromRegistry = readProxyFromRegistry();
        if (proxyFromRegistry != null) {
            return createProxyConfig(proxyFromRegistry, "Windows Registry");
        }

        // Method 3: Environment variables
        String[] proxyFromEnv = readProxyFromEnvironment();
        if (proxyFromEnv != null) {
            return createProxyConfig(proxyFromEnv, "environment variable");
        }

        logger.debug("No system proxy detected");
        return Optional.empty();
    }

    /**
     * Configures JVM proxy properties for HTTP and HTTPS.
     *
     * @param host the proxy host
     * @param port the proxy port
     */
    public static void configureProxy(String host, int port) {
        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", String.valueOf(port));
        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", String.valueOf(port));
        logger.debug("Proxy configured: {}:{}", host, port);
    }

    /**
     * Configures JVM proxy properties with a bypass list.
     *
     * @param host the proxy host
     * @param port the proxy port
     * @param nonProxyHosts pipe-separated list of hosts that bypass the proxy
     *                      (e.g., "localhost|127.0.0.1|*.firma.local")
     */
    public static void configureProxy(String host, int port, String nonProxyHosts) {
        configureProxy(host, port);
        if (nonProxyHosts != null && !nonProxyHosts.isEmpty()) {
            System.setProperty("http.nonProxyHosts", nonProxyHosts);
            System.setProperty("https.nonProxyHosts", nonProxyHosts);
            logger.debug("Non-proxy hosts configured: {}", nonProxyHosts);
        }
    }

    /**
     * Clears all proxy configuration from JVM properties.
     */
    public static void clearProxyConfiguration() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("http.nonProxyHosts");
        System.clearProperty("https.nonProxyHosts");
        logger.debug("Proxy configuration cleared");
    }

    /**
     * Returns the current proxy configuration from JVM properties.
     *
     * @return an Optional containing the current proxy configuration, or empty if not configured
     */
    public static Optional<ProxyConfig> getCurrentConfig() {
        String host = System.getProperty("http.proxyHost");
        String port = System.getProperty("http.proxyPort");

        if (host != null && port != null) {
            try {
                return Optional.of(new ProxyConfig(host, Integer.parseInt(port)));
            } catch (NumberFormatException e) {
                logger.warn("Invalid proxy port in system properties: {}", port);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    // ========== Private Detection Methods ==========

    /**
     * Reads proxy settings from netsh winhttp.
     * This is the Windows system-wide WinHTTP proxy configuration.
     */
    private static String[] readProxyFromNetsh() {
        if (!isWindows()) {
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("netsh", "winhttp", "show", "proxy");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Format: "Proxyserver: proxy.example.com:8080" (German)
                    // Format: "Proxy Server: proxy.example.com:8080" (English)
                    if (line.contains("Proxyserver") || line.contains("Proxy Server")) {
                        String[] parts = line.split(":");
                        if (parts.length >= 3) {
                            // "Proxyserver: host:port" -> parts[1] = host, parts[2] = port
                            String host = parts[1].trim();
                            String port = parts[2].trim();
                            logger.debug("Proxy detected via netsh: {}:{}", host, port);
                            return new String[]{host, port};
                        }
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            logger.debug("Error reading proxy from netsh: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Reads proxy settings from the Windows Registry.
     * Location: HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings
     */
    private static String[] readProxyFromRegistry() {
        if (!isWindows()) {
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", "ProxyServer");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Format: "    ProxyServer    REG_SZ    proxy.example.com:8080"
                    if (line.contains("ProxyServer") && line.contains("REG_SZ")) {
                        String[] parts = line.split("REG_SZ");
                        if (parts.length > 1) {
                            String proxyString = parts[1].trim();
                            // Format can be: "proxy:8080" or "http=proxy:8080;https=proxy:8080"
                            if (proxyString.contains("=")) {
                                // Multiple protocols, extract http= or https=
                                for (String part : proxyString.split(";")) {
                                    if (part.startsWith("http=") || part.startsWith("https=")) {
                                        proxyString = part.substring(part.indexOf("=") + 1);
                                        break;
                                    }
                                }
                            }

                            // Parse proxyString (Format: "host:port")
                            if (proxyString.contains(":")) {
                                String[] hostPort = proxyString.split(":");
                                logger.debug("Proxy detected via Registry: {}:{}", hostPort[0], hostPort[1]);
                                return new String[]{hostPort[0], hostPort[1]};
                            }
                        }
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            logger.debug("Error reading proxy from Registry: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Reads proxy settings from environment variables.
     * Checks: HTTP_PROXY, http_proxy, HTTPS_PROXY, https_proxy
     */
    private static String[] readProxyFromEnvironment() {
        String[] envVars = {"HTTP_PROXY", "http_proxy", "HTTPS_PROXY", "https_proxy"};

        for (String envVar : envVars) {
            String proxyUrl = System.getenv(envVar);
            if (proxyUrl != null && !proxyUrl.isEmpty()) {
                // Format: "http://proxy:8080" or "proxy:8080"
                String cleaned = proxyUrl.replaceFirst("^https?://", "");
                if (cleaned.contains(":")) {
                    String[] parts = cleaned.split(":");
                    logger.debug("Proxy detected via environment variable {}: {}:{}", envVar, parts[0], parts[1]);
                    return new String[]{parts[0], parts[1]};
                }
            }
        }
        return null;
    }

    // ========== Helper Methods ==========

    /**
     * Creates a ProxyConfig from host/port array, handling port parsing.
     */
    private static Optional<ProxyConfig> createProxyConfig(String[] hostPort, String source) {
        try {
            int port = Integer.parseInt(hostPort[1]);
            ProxyConfig config = new ProxyConfig(hostPort[0], port);
            logger.info("System proxy detected via {}: {}:{}", source, hostPort[0], port);
            return Optional.of(config);
        } catch (NumberFormatException e) {
            logger.warn("Invalid proxy port from {}: {}", source, hostPort[1]);
            return Optional.empty();
        }
    }

    /**
     * Checks if the current OS is Windows.
     */
    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    /**
     * Proxy configuration record containing host and port.
     *
     * @param host the proxy hostname
     * @param port the proxy port number
     */
    public record ProxyConfig(String host, int port) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
