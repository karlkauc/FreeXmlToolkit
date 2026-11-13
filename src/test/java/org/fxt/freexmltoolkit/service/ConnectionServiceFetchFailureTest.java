package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

/**
 * A URL that answers with an error status (or not at all) fails with a {@link ConnectionService.FetchFailedException}
 * carrying the status, so "Open from URL" can tell it apart from an application error (telemetry: a 404 was
 * reported as a {@code RuntimeException}).
 */
class ConnectionServiceFetchFailureTest {

    @Test
    void anErrorStatusIsAFetchFailureWithItsStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/missing", exchange -> {
            byte[] body = "<html>not here</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/missing");
            var e = assertThrows(ConnectionService.FetchFailedException.class,
                    () -> ConnectionServiceImpl.getInstance().getTextContentFromURL(uri));
            assertEquals(404, e.status());
            assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void noAnswerIsAFetchFailureWithStatusZero() throws Exception {
        // A port nobody listens on (taken and released again)
        int port;
        try (var socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        URI uri = URI.create("http://127.0.0.1:" + port + "/x");
        var e = assertThrows(ConnectionService.FetchFailedException.class,
                () -> ConnectionServiceImpl.getInstance().getTextContentFromURL(uri));
        assertEquals(0, e.status());
    }
}
