package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The HTTP service against a local proxy that demands Basic authentication: the settings decide the
 * proxy, the credentials (with NTLM domain) reach it, and exceptions connect directly.
 */
class ConnectionServiceProxyTest {

    private static HttpServer proxy;
    private static HttpServer direct;
    private static final List<String> proxiedTargets = new CopyOnWriteArrayList<>();
    private static final List<String> proxyAuthorizations = new CopyOnWriteArrayList<>();
    private static final String EXPECTED_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("CORP\\jdoe:s3cret".getBytes(StandardCharsets.UTF_8));
    private static ConnectionServiceImpl service;
    private static final Properties saved = new Properties();

    @BeforeAll
    static void start() throws IOException {
        proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Proxy-Authorization");
            proxyAuthorizations.add(String.valueOf(auth));
            if (!EXPECTED_AUTH.equals(auth)) {
                exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"corp\"");
                respond(exchange, 407, "authentication required");
                return;
            }
            proxiedTargets.add(exchange.getRequestURI().toString());
            respond(exchange, 200, "via proxy");
        });
        proxy.start();
        direct = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        direct.createContext("/", exchange -> respond(exchange, 200, "direct"));
        direct.start();

        PropertiesService properties = mock(PropertiesService.class);
        when(properties.loadProperties()).thenReturn(saved);
        ServiceRegistry.reset();
        ServiceRegistry.register(PropertiesService.class, properties);
        service = ConnectionServiceImpl.getInstance();
    }

    @AfterAll
    static void stop() {
        proxy.stop(0);
        direct.stop(0);
        ServiceRegistry.reset();
    }

    @BeforeEach
    void clear() {
        proxiedTargets.clear();
        proxyAuthorizations.clear();
        saved.clear();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Properties manualProxy(String user, String password, String domain, String bypass) {
        Properties props = new Properties();
        new ProxySettings(ProxySettings.Mode.MANUAL, "127.0.0.1", proxy.getAddress().getPort(),
                ProxySettings.parseHostList(bypass), user, password, domain, false, 5_000, 5_000).writeTo(props);
        return props;
    }

    @Test
    void credentialsWithDomainReachTheProxy() {
        ConnectionResult result = service.testHttpRequest(URI.create("http://example.invalid/hello"),
                manualProxy("jdoe", "s3cret", "CORP", ""));

        assertEquals(200, result.httpStatus(), result.resultBody());
        assertEquals("via proxy", result.resultBody().strip());
        assertEquals(List.of("http://example.invalid/hello"), proxiedTargets, "the proxy got the full URL");
    }

    @Test
    void withoutCredentialsTheProxyRefuses() {
        ConnectionResult result = service.testHttpRequest(URI.create("http://example.invalid/hello"),
                manualProxy("", "", "", ""));

        assertEquals(407, result.httpStatus());
        assertTrue(proxiedTargets.isEmpty());
    }

    @Test
    void exceptionsConnectDirectly() {
        URI local = URI.create("http://127.0.0.1:" + direct.getAddress().getPort() + "/x");
        ConnectionResult result = service.testHttpRequest(local, manualProxy("jdoe", "s3cret", "CORP", ""));

        assertEquals(200, result.httpStatus());
        assertEquals("direct", result.resultBody().strip());
        assertTrue(proxyAuthorizations.isEmpty(), "the proxy was not asked");
    }

    @Test
    void openConnectionUsesTheSavedSettings() throws Exception {
        saved.putAll(manualProxy("jdoe", "s3cret", "CORP", ""));
        HttpURLConnection connection = service.openConnection(URI.create("http://example.invalid/stream"));
        try {
            assertEquals(5_000, connection.getConnectTimeout());
            assertEquals(200, connection.getResponseCode());
            assertEquals(List.of("http://example.invalid/stream"), proxiedTargets);
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void xprocGetsTheManualProxy() {
        saved.putAll(manualProxy("", "", "", ""));
        String expected = "http://127.0.0.1:" + proxy.getAddress().getPort();
        assertEquals(expected, service.xprocProxies().get("http"));
        assertEquals(expected, service.xprocProxies().get("https"));

        saved.clear();
        saved.setProperty("manualProxy", "false");
        saved.setProperty("useSystemProxy", "false");
        assertTrue(service.xprocProxies().isEmpty());
    }
}
