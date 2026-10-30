package org.fxt.freexmltoolkit.service;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * The application's HTTP connection settings (Settings → HTTP Proxy), read from the properties. One
 * place decides from them which proxy a URL goes through, who authenticates at it and how long a
 * connection may take; {@link ConnectionServiceImpl} applies them to its own connections and installs
 * them JVM-wide for the libraries that open connections themselves.
 *
 * @param mode           no proxy, the system's proxy (incl. PAC/WPAD) or the manual host/port
 * @param host           manual proxy host (blank = none)
 * @param port           manual proxy port ({@code <= 0} = none)
 * @param nonProxyHosts  host patterns connected directly ({@code *} wildcard; local addresses always)
 * @param user           proxy user name (blank = no credentials: Windows sign-on or none)
 * @param password       proxy password
 * @param domain         NTLM domain, prefixed to the user ({@code DOMAIN\\user}) unless the user has one
 * @param windowsSignOn  on Windows, let NTLM/Kerberos use the signed-in user when no user is set
 * @param connectTimeoutMs connection timeout in milliseconds
 * @param readTimeoutMs    read timeout in milliseconds
 */
public record ProxySettings(Mode mode, String host, int port, List<String> nonProxyHosts,
                            String user, String password, String domain, boolean windowsSignOn,
                            int connectTimeoutMs, int readTimeoutMs) {

    /** How connections find their proxy. */
    public enum Mode {
        /** Always connect directly. */
        NONE,
        /** The operating system's proxy, including automatic configuration (PAC/WPAD). */
        SYSTEM,
        /** The host and port entered in the settings. */
        MANUAL
    }

    // Property keys (the first ones predate this class and stay compatible).
    public static final String KEY_USE_SYSTEM = "useSystemProxy";
    public static final String KEY_MANUAL = "manualProxy";
    public static final String KEY_HOST = "http.proxy.host";
    public static final String KEY_PORT = "http.proxy.port";
    public static final String KEY_USER = "http.proxy.user";
    public static final String KEY_PASSWORD = "http.proxy.password";
    public static final String KEY_NON_PROXY_HOSTS = "http.proxy.nonProxyHosts";
    public static final String KEY_DOMAIN = "http.proxy.domain";
    public static final String KEY_WINDOWS_SIGN_ON = "http.proxy.windowsSignOn";
    public static final String KEY_CONNECT_TIMEOUT = "http.connectTimeoutSeconds";
    public static final String KEY_READ_TIMEOUT = "http.readTimeoutSeconds";

    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 60;
    /** Suggested exceptions for a new manual proxy. */
    public static final String DEFAULT_NON_PROXY_HOSTS = "localhost, 127.*, *.local";

    private static final Pattern SEPARATORS = Pattern.compile("[|,;\\s]+");

    public ProxySettings {
        host = host == null ? "" : host.trim();
        nonProxyHosts = List.copyOf(nonProxyHosts == null ? List.of() : nonProxyHosts);
        user = user == null ? "" : user.trim();
        password = password == null ? "" : password;
        domain = domain == null ? "" : domain.trim();
    }

    /** Reads the settings; missing or malformed values fall back to the defaults (system proxy). */
    public static ProxySettings from(Properties props) {
        Properties p = props != null ? props : new Properties();
        boolean manual = Boolean.parseBoolean(p.getProperty(KEY_MANUAL, "false"));
        boolean system = Boolean.parseBoolean(p.getProperty(KEY_USE_SYSTEM, "true"));
        Mode mode = manual ? Mode.MANUAL : system ? Mode.SYSTEM : Mode.NONE;
        return new ProxySettings(mode,
                p.getProperty(KEY_HOST, ""),
                parseInt(p.getProperty(KEY_PORT), -1),
                parseHostList(p.getProperty(KEY_NON_PROXY_HOSTS, "")),
                p.getProperty(KEY_USER, ""),
                p.getProperty(KEY_PASSWORD, ""),
                p.getProperty(KEY_DOMAIN, ""),
                Boolean.parseBoolean(p.getProperty(KEY_WINDOWS_SIGN_ON, "true")),
                seconds(p.getProperty(KEY_CONNECT_TIMEOUT), DEFAULT_CONNECT_TIMEOUT_SECONDS),
                seconds(p.getProperty(KEY_READ_TIMEOUT), DEFAULT_READ_TIMEOUT_SECONDS));
    }

    /** Writes these settings into {@code props} (the password is encrypted by the properties service). */
    public void writeTo(Properties props) {
        props.setProperty(KEY_MANUAL, String.valueOf(mode == Mode.MANUAL));
        props.setProperty(KEY_USE_SYSTEM, String.valueOf(mode == Mode.SYSTEM));
        props.setProperty(KEY_HOST, host);
        props.setProperty(KEY_PORT, port > 0 ? String.valueOf(port) : "");
        props.setProperty(KEY_NON_PROXY_HOSTS, String.join(", ", nonProxyHosts));
        props.setProperty(KEY_USER, user);
        props.setProperty(KEY_PASSWORD, password);
        props.setProperty(KEY_DOMAIN, domain);
        props.setProperty(KEY_WINDOWS_SIGN_ON, String.valueOf(windowsSignOn));
        props.setProperty(KEY_CONNECT_TIMEOUT, String.valueOf(connectTimeoutMs / 1000));
        props.setProperty(KEY_READ_TIMEOUT, String.valueOf(readTimeoutMs / 1000));
    }

    /** @return the manual proxy, or {@code null} when none is configured completely */
    public Proxy manualProxy() {
        if (host.isEmpty() || port <= 0 || port > 65535) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
    }

    /**
     * @return whether {@code targetHost} is connected directly whatever the mode: loopback addresses
     *         and the hosts matching {@link #nonProxyHosts}
     */
    public boolean bypasses(String targetHost) {
        if (targetHost == null || targetHost.isBlank()) {
            return true;
        }
        String h = targetHost.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (h.equals("localhost") || h.startsWith("127.") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        for (String pattern : nonProxyHosts) {
            if (matches(pattern.toLowerCase(Locale.ROOT), h)) {
                return true;
            }
        }
        return false;
    }

    /** @return the user name the proxy sees: {@code DOMAIN\\user} when a domain is set and the user has none */
    public String authenticationUser() {
        if (domain.isEmpty() || user.contains("\\") || user.contains("@")) {
            return user;
        }
        return domain + "\\" + user;
    }

    /** @return whether credentials are configured */
    public boolean hasCredentials() {
        return !user.isEmpty();
    }

    /** {@code *.corp} matches every sub-domain, {@code 10.*} every address starting there. */
    private static boolean matches(String pattern, String host) {
        if (pattern.isEmpty()) {
            return false;
        }
        if (!pattern.contains("*")) {
            return pattern.equals(host);
        }
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return host.matches(regex.toString());
    }

    /** Splits a host list separated by {@code |}, commas, semicolons or white space. */
    public static List<String> parseHostList(String text) {
        List<String> hosts = new ArrayList<>();
        if (text != null) {
            for (String part : SEPARATORS.split(text.trim())) {
                if (!part.isBlank()) {
                    hosts.add(part.trim());
                }
            }
        }
        return hosts;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int seconds(String value, int fallbackSeconds) {
        int seconds = parseInt(value, fallbackSeconds);
        return (seconds > 0 ? Math.min(seconds, 3600) : fallbackSeconds) * 1000;
    }
}
