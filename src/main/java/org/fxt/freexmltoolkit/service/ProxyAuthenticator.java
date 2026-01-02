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

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * Custom Authenticator for HTTP/HTTPS proxy authentication.
 *
 * <p>This authenticator provides username/password credentials when connecting
 * through a corporate proxy that requires authentication. It is designed to work
 * with Java's {@link java.net.HttpURLConnection} when connecting through a proxy
 * that has been configured with {@link java.net.Proxy}.
 *
 * <p><strong>Security Features:</strong>
 * <ul>
 *   <li>Password stored as {@code char[]} instead of {@code String} to allow clearing from memory</li>
 *   <li>Only responds to {@code RequestorType.PROXY} requests to prevent credential leakage to web servers</li>
 *   <li>Passwords are never logged or included in debug output</li>
 *   <li>Supports clearing credentials from memory after use</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * ProxyAuthenticator authenticator = new ProxyAuthenticator("proxyuser", "proxypass");
 * Authenticator.setDefault(authenticator);
 *
 * // Now HttpURLConnection will use these credentials for proxy authentication
 * URL url = new URL("https://api.github.com");
 * HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
 * // ...
 *
 * // Clear credentials from memory when done
 * authenticator.clear();
 * }</pre>
 *
 * @since 1.2.3
 */
public class ProxyAuthenticator extends Authenticator {
    private static final Logger logger = LogManager.getLogger(ProxyAuthenticator.class);

    private final String username;
    private final char[] password;

    /**
     * Creates a new ProxyAuthenticator with the given credentials.
     *
     * @param username the proxy username (not null, but may be empty)
     * @param password the proxy password (may be null)
     */
    public ProxyAuthenticator(String username, String password) {
        this.username = username;
        this.password = password != null ? password.toCharArray() : new char[0];
    }

    /**
     * Called by HttpURLConnection when authentication is required.
     *
     * <p>This method only provides credentials for proxy authentication requests
     * ({@code RequestorType.PROXY}). For all other request types, it returns
     * {@code null} to allow other authenticators to handle the request.
     *
     * @return PasswordAuthentication with proxy credentials for proxy requests,
     *         or null for non-proxy requests
     */
    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        // Only provide credentials for proxy authentication
        if (getRequestorType() == RequestorType.PROXY) {
            logger.debug("Providing proxy authentication for: {}:{}",
                    getRequestingHost(), getRequestingPort());
            // DO NOT log username or password!
            return new PasswordAuthentication(username, password);
        }

        // Not a proxy request - return null to let other authenticators handle it
        // This prevents credential leakage to non-proxy HTTP servers
        logger.debug("Authenticator called for non-proxy request (type: {}), returning null",
                getRequestorType());
        return null;
    }

    /**
     * Clears credentials from memory.
     *
     * <p>Call this after the connection is complete to minimize security exposure.
     * This method overwrites the password array with zeros to prevent the actual
     * password from being recoverable from memory.
     */
    public void clear() {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }
}
