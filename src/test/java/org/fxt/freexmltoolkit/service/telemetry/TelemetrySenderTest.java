package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.fxt.freexmltoolkit.service.ConnectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

/** Exercises {@link TelemetrySender} against a local fake ingest server. */
class TelemetrySenderTest {

    private static final String INSTALL_ID = "9b2d7c7e-1111-4222-8333-444455556666";
    private static final TelemetryEnvironment ENV = new TelemetryEnvironment("2.2.0", "Linux", "x64", 25, "de");

    private HttpServer server;
    private final List<JsonObject> received = new CopyOnWriteArrayList<>();
    private final List<String> userAgents = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(202);
    private final AtomicBoolean allowed = new AtomicBoolean(true);
    private MutableClock clock;
    private TelemetryQueue queue;
    private TelemetrySender sender;

    /** A clock that tests can move forward. */
    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Simple direct (proxy-less) poster for the fake server. */
    static ConnectionService.HttpPostResult post(URI uri, String json, Duration timeout) throws IOException {
        HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).connectTimeout(timeout).build();
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "FreeXmlToolkit/test")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return new ConnectionService.HttpPostResult(resp.statusCode(), resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/events", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                received.add(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject());
            }
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] body = "{\"accepted\":1,\"rejected\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        clock = new MutableClock(Instant.parse("2026-09-24T10:00:00Z"));
        queue = new TelemetryQueue(null, 5000, Duration.ofDays(14), clock);
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        sender = new TelemetrySender(queue, TelemetrySenderTest::post, () -> base, () -> INSTALL_ID, ENV,
                allowed::get, clock);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void addEvents(String session, int n) {
        for (int i = 0; i < n; i++) {
            queue.add(session, "2.2.0", TelemetryEvent.builder("validate", TelemetryEvent.Category.ACTION)
                    .clientEventAt(clock.instant()).docKind(DocKind.XML).build());
        }
    }

    @Test
    void accepted202RemovesSentEventsAndEnvelopeIsComplete() {
        addEvents("11111111-1111-4111-8111-111111111111", 3);

        TelemetrySender.FlushResult r = sender.flush(Duration.ofSeconds(5));

        assertEquals(TelemetrySender.Outcome.SENT, r.outcome());
        assertEquals(3, r.sent());
        assertEquals(0, queue.size());
        assertEquals(1, received.size());
        JsonObject env = received.getFirst();
        assertEquals(INSTALL_ID, env.get("install_id").getAsString());
        assertEquals("11111111-1111-4111-8111-111111111111", env.get("session_id").getAsString());
        assertEquals("2.2.0", env.get("app_version").getAsString());
        assertEquals("Linux", env.get("os_name").getAsString());
        assertEquals("x64", env.get("os_arch").getAsString());
        assertEquals(25, env.get("java_major").getAsInt());
        assertEquals("de", env.get("locale").getAsString());
        assertEquals(3, env.getAsJsonArray("events").size());
        JsonObject ev = env.getAsJsonArray("events").get(0).getAsJsonObject();
        assertEquals("validate", ev.get("event_type").getAsString());
        assertEquals("action", ev.get("category").getAsString());
        assertEquals("ok", ev.get("status").getAsString());
        assertEquals("xml", ev.get("doc_kind").getAsString());
        assertEquals("2026-09-24T10:00:00Z", ev.get("client_event_at").getAsString());
    }

    @Test
    void batchesAreLimitedTo200EventsAndSplitBySession() {
        addEvents("aaaaaaaa-1111-4111-8111-111111111111", 450);
        addEvents("bbbbbbbb-1111-4111-8111-111111111111", 5);

        TelemetrySender.FlushResult r = sender.flush(Duration.ofSeconds(5));

        assertEquals(455, r.sent());
        assertEquals(4, received.size());
        for (JsonObject env : received) {
            assertTrue(env.getAsJsonArray("events").size() <= TelemetrySender.MAX_EVENTS_PER_REQUEST);
        }
        assertEquals("bbbbbbbb-1111-4111-8111-111111111111", received.getLast().get("session_id").getAsString());
        assertEquals(5, received.getLast().getAsJsonArray("events").size());
    }

    @Test
    void badRequestDropsTheBatch() {
        status.set(400);
        addEvents("s", 2);
        TelemetrySender.FlushResult r = sender.flush(Duration.ofSeconds(5));
        assertEquals(2, r.dropped());
        assertEquals(0, queue.size());

        status.set(413);
        addEvents("s", 2);
        sender.flush(Duration.ofSeconds(5));
        assertEquals(0, queue.size());
    }

    @Test
    void tooManyRequestsKeepsEventsAndBacksOffExponentially() {
        status.set(429);
        addEvents("s", 2);

        TelemetrySender.FlushResult r = sender.flush(Duration.ofSeconds(5));
        assertEquals(TelemetrySender.Outcome.RETRY_LATER, r.outcome());
        assertEquals(2, queue.size());
        assertEquals(clock.instant().plus(Duration.ofMinutes(5)), sender.getNextAttemptAt());

        // within the backoff window nothing is attempted
        assertEquals(TelemetrySender.Outcome.BACKING_OFF, sender.flush(Duration.ofSeconds(5)).outcome());
        assertEquals(1, received.size());

        clock.advance(Duration.ofMinutes(5));
        status.set(500);
        sender.flush(Duration.ofSeconds(5));
        assertEquals(2, queue.size());
        assertEquals(clock.instant().plus(Duration.ofMinutes(10)), sender.getNextAttemptAt());

        // backoff is capped at 6 hours
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofHours(7));
            sender.flush(Duration.ofSeconds(5));
        }
        assertEquals(TelemetrySender.MAX_BACKOFF, sender.getCurrentBackoff());

        // success resets the backoff and removes the events
        clock.advance(Duration.ofHours(7));
        status.set(202);
        assertEquals(TelemetrySender.Outcome.SENT, sender.flush(Duration.ofSeconds(5)).outcome());
        assertEquals(0, queue.size());
        assertEquals(TelemetrySender.INITIAL_BACKOFF, sender.getCurrentBackoff());
    }

    @Test
    void networkErrorKeepsEvents() {
        addEvents("s", 2);
        server.stop(0);
        TelemetrySender.FlushResult r = sender.flush(Duration.ofSeconds(2));
        assertEquals(TelemetrySender.Outcome.RETRY_LATER, r.outcome());
        assertEquals(2, queue.size());
    }

    @Test
    void nothingIsSentWhileTheGateIsClosed() {
        allowed.set(false); // e.g. first-start notice not yet shown
        addEvents("s", 2);
        assertEquals(TelemetrySender.Outcome.NOT_ALLOWED, sender.flush(Duration.ofSeconds(5)).outcome());
        assertEquals(0, received.size());
        assertEquals(2, queue.size());

        allowed.set(true);
        assertEquals(TelemetrySender.Outcome.SENT, sender.flush(Duration.ofSeconds(5)).outcome());
        assertEquals(1, received.size());
    }

    @Test
    void emptyQueueMakesNoRequest() {
        assertEquals(TelemetrySender.Outcome.NOTHING_TO_SEND, sender.flush(Duration.ofSeconds(5)).outcome());
        assertEquals(0, received.size());
    }
}
