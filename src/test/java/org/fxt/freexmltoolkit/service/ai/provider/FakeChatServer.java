package org.fxt.freexmltoolkit.service.ai.provider;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A scripted chat server on a loopback port: answers each request with the next scripted reply and records what it
 * received. Test code only — main code opens connections through ConnectionService alone.
 */
public final class FakeChatServer implements AutoCloseable {

    /** One recorded request. */
    public record Request(String method, String path, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            for (var e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue().getFirst();
                }
            }
            return null;
        }
    }

    /** One scripted reply: status, headers, body parts written with a delay before each. */
    public record Reply(int status, Map<String, String> headers, List<String> parts, long delayMillis) {
        public static Reply json(String body) {
            return new Reply(200, Map.of("Content-Type", "application/json"), List.of(body), 0);
        }

        public static Reply status(int status, String body, Map<String, String> headers) {
            java.util.HashMap<String, String> h = new java.util.HashMap<>(headers);
            h.putIfAbsent("Content-Type", "application/json");
            return new Reply(status, h, List.of(body), 0);
        }

        /** Server-sent events, one {@code data:} event per entry (an entry starting with "event:" is sent as is). */
        public static Reply sse(long delayMillis, String... events) {
            List<String> parts = new ArrayList<>();
            for (String e : events) {
                parts.add(e.startsWith("event:") ? e + "\n\n" : "data: " + e + "\n\n");
            }
            return new Reply(200, Map.of("Content-Type", "text/event-stream"), parts, delayMillis);
        }
    }

    private final HttpServer server;
    private final ConcurrentLinkedQueue<Reply> script = new ConcurrentLinkedQueue<>();
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

    public FakeChatServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    /** {@code http://127.0.0.1:<port>} + path. */
    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    public URI uri(String path) {
        return URI.create(url(path));
    }

    public FakeChatServer reply(Reply reply) {
        script.add(reply);
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public Request last() {
        return requests.getLast();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                Map.copyOf(exchange.getRequestHeaders()), body));
        Reply reply = script.poll();
        if (reply == null) {
            reply = Reply.status(500, "{\"error\":{\"message\":\"no scripted reply\"}}", Map.of());
        }
        reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        boolean streaming = reply.parts().size() > 1 || reply.delayMillis() > 0;
        try {
            exchange.sendResponseHeaders(reply.status(), streaming ? 0 : reply.parts().getFirst().getBytes(
                    StandardCharsets.UTF_8).length);
            try (OutputStream out = exchange.getResponseBody()) {
                for (String part : reply.parts()) {
                    if (reply.delayMillis() > 0) {
                        Thread.sleep(reply.delayMillis());
                    }
                    out.write(part.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // the client went away (cancel tests)
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
