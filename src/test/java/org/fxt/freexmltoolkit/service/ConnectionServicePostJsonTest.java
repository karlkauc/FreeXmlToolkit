package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.util.VersionUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** {@link ConnectionServiceImpl#postJson} against a local server (direct connection, no proxy). */
class ConnectionServicePostJsonTest {

    private static HttpServer server;
    private static int port;
    private static final List<String> bodies = new CopyOnWriteArrayList<>();
    private static final List<String> contentTypes = new CopyOnWriteArrayList<>();
    private static final List<String> userAgents = new CopyOnWriteArrayList<>();
    private static final List<String> methods = new CopyOnWriteArrayList<>();
    private static ConnectionServiceImpl service;

    @BeforeAll
    static void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/v1/events", exchange -> {
            methods.add(exchange.getRequestMethod());
            contentTypes.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            try (InputStream in = exchange.getRequestBody()) {
                bodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] resp = "{\"accepted\":1,\"rejected\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.createContext("/bad", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"error\":\"malformed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/v1/events");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.start();

        // Direct connection: no system/manual proxy, independent of the developer's environment.
        Properties props = new Properties();
        props.setProperty("manualProxy", "false");
        props.setProperty("useSystemProxy", "false");
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(propertiesService.loadProperties()).thenReturn(props);
        ServiceRegistry.reset();
        ServiceRegistry.register(PropertiesService.class, propertiesService);
        service = ConnectionServiceImpl.getInstance();
    }

    @AfterAll
    static void tearDown() {
        server.stop(0);
        ServiceRegistry.reset();
    }

    @Test
    void postsJsonAndReturnsStatusAndBody() throws IOException {
        String json = "{\"install_id\":\"x\",\"text\":\"ümlaut\"}";
        ConnectionService.HttpPostResult r = service.postJson(
                URI.create("http://127.0.0.1:" + port + "/v1/events"), json, Duration.ofSeconds(5));

        assertEquals(202, r.status());
        assertTrue(r.body().contains("accepted"));
        assertEquals("POST", methods.getLast());
        assertEquals(json, bodies.getLast());
        assertEquals("application/json; charset=utf-8", contentTypes.getLast());
        assertEquals("FreeXmlToolkit/" + VersionUtil.getVersion(), userAgents.getLast());
    }

    @Test
    void errorStatusesAreReturnedNotThrown() throws IOException {
        ConnectionService.HttpPostResult r = service.postJson(
                URI.create("http://127.0.0.1:" + port + "/bad"), "{}", Duration.ofSeconds(5));
        assertEquals(400, r.status());
        assertTrue(r.body().contains("malformed"));
    }

    @Test
    void redirectsAreNotFollowed() throws IOException {
        int before = bodies.size();
        ConnectionService.HttpPostResult r = service.postJson(
                URI.create("http://127.0.0.1:" + port + "/redirect"), "{}", Duration.ofSeconds(5));
        assertEquals(307, r.status());
        assertEquals(before, bodies.size());
    }

    @Test
    void transportFailureThrowsIOException() throws IOException {
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        }
        assertThrows(IOException.class, () -> service.postJson(
                URI.create("http://127.0.0.1:" + freePort + "/v1/events"), "{}", Duration.ofSeconds(2)));
    }
}
