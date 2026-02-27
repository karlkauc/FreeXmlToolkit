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

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

/**
 * ProxySelector wrapper that filters out SOCKS proxies from the delegate's results.
 *
 * <p>On Windows with PAC/WPAD auto-configuration, Java's default ProxySelector
 * (when {@code java.net.useSystemProxies=true}) can return SOCKS proxy entries
 * for URLs that should use an HTTP proxy. When Java then tries the SOCKS protocol
 * against the HTTP proxy server, the connection fails with
 * "SocketException: Malformed reply from SOCKS server".
 *
 * <p>This wrapper delegates to the original ProxySelector but only returns
 * HTTP-type and DIRECT proxies, filtering out any SOCKS entries.
 *
 * @since 2.0
 */
public final class HttpOnlyProxySelector extends ProxySelector {

    private static final Logger logger = LogManager.getLogger(HttpOnlyProxySelector.class);
    private static final List<Proxy> DIRECT = List.of(Proxy.NO_PROXY);

    private final ProxySelector delegate;

    /**
     * Creates a new HttpOnlyProxySelector wrapping the given delegate.
     *
     * @param delegate the original ProxySelector to filter
     */
    public HttpOnlyProxySelector(ProxySelector delegate) {
        this.delegate = delegate;
    }

    /**
     * Installs this filter as the default ProxySelector, wrapping the current default.
     * Safe to call multiple times — will not double-wrap.
     */
    public static void install() {
        ProxySelector current = ProxySelector.getDefault();
        if (current instanceof HttpOnlyProxySelector) {
            logger.debug("HttpOnlyProxySelector already installed, skipping");
            return;
        }
        if (current != null) {
            ProxySelector.setDefault(new HttpOnlyProxySelector(current));
            logger.info("HttpOnlyProxySelector installed (filtering SOCKS proxies)");
        } else {
            logger.debug("No default ProxySelector to wrap");
        }
    }

    @Override
    public List<Proxy> select(URI uri) {
        List<Proxy> candidates = delegate.select(uri);
        if (candidates == null || candidates.isEmpty()) {
            return DIRECT;
        }

        List<Proxy> filtered = candidates.stream()
                .filter(p -> p.type() != Proxy.Type.SOCKS)
                .toList();

        if (filtered.size() < candidates.size()) {
            logger.debug("Filtered {} SOCKS proxy entries for URI: {} (kept {} of {})",
                    candidates.size() - filtered.size(), uri, filtered.size(), candidates.size());
        }

        return filtered.isEmpty() ? DIRECT : filtered;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        delegate.connectFailed(uri, sa, ioe);
    }
}
