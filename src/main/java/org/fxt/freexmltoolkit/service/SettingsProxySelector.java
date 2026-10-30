package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The JVM-wide {@link ProxySelector} that applies {@link ProxySettings}: connections the application does
 * not open itself — Saxon {@code doc()}, schema and Schematron includes, JSON Schema {@code $ref}s,
 * XProc document loading, WebView previews — use the same proxy, exceptions and mode as
 * {@link ConnectionServiceImpl}. In system mode it asks the selector it replaced (the system's, with
 * PAC/WPAD); exceptions and loopback addresses always go direct.
 *
 * <p>Installed by {@link ConnectionServiceImpl#applyGlobalSettings}; later calls only swap the settings.
 */
public final class SettingsProxySelector extends ProxySelector {

    private static final Logger logger = LogManager.getLogger(SettingsProxySelector.class);
    private static final List<Proxy> DIRECT = List.of(Proxy.NO_PROXY);

    private final ProxySelector system;
    private volatile ProxySettings settings;

    SettingsProxySelector(ProxySelector system, ProxySettings settings) {
        this.system = system;
        this.settings = settings;
    }

    /** Installs a selector for {@code settings}, or updates the one already installed. */
    static synchronized SettingsProxySelector install(ProxySettings settings) {
        ProxySelector current = ProxySelector.getDefault();
        if (current instanceof SettingsProxySelector installed) {
            installed.settings = settings;
            return installed;
        }
        SettingsProxySelector selector = new SettingsProxySelector(current, settings);
        ProxySelector.setDefault(selector);
        logger.info("Proxy settings installed JVM-wide (mode {})", settings.mode());
        return selector;
    }

    ProxySettings settings() {
        return settings;
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null");
        }
        ProxySettings s = settings;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean web = scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp");
        if (!web || s.bypasses(uri.getHost())) {
            return web ? DIRECT : delegate(uri);
        }
        return switch (s.mode()) {
            case NONE -> DIRECT;
            case MANUAL -> {
                Proxy proxy = s.manualProxy();
                yield proxy != null ? List.of(proxy) : DIRECT;
            }
            case SYSTEM -> delegate(uri);
        };
    }

    private List<Proxy> delegate(URI uri) {
        if (system == null) {
            return DIRECT;
        }
        try {
            List<Proxy> proxies = system.select(uri);
            return proxies == null || proxies.isEmpty() ? DIRECT : proxies;
        } catch (RuntimeException e) {
            logger.debug("System proxy selection failed for {}: {}", uri, e.toString());
            return DIRECT;
        }
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException failure) {
        if (system != null && settings.mode() == ProxySettings.Mode.SYSTEM) {
            system.connectFailed(uri, address, failure);
        }
        logger.debug("Proxy connection to {} via {} failed: {}", uri, address, failure.toString());
    }
}
