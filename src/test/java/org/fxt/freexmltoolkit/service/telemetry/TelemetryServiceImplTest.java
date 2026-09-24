package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.fxt.freexmltoolkit.service.ConnectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class TelemetryServiceImplTest {

    private static final TelemetryEnvironment ENV = new TelemetryEnvironment("2.2.0", "Linux", "x64", 25, "en");

    private record Request(URI uri, String body) {
    }

    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(202);
    private TelemetrySettings.InMemory settings;
    private TelemetryServiceImpl service;

    @BeforeEach
    void setUp() {
        settings = new TelemetrySettings.InMemory();
        service = newService(false);
    }

    @AfterEach
    void tearDown() {
        service.shutdown(Duration.ofMillis(100));
    }

    private TelemetryServiceImpl newService(boolean killed) {
        TelemetryQueue queue = new TelemetryQueue(null, 5000, Duration.ofDays(14), Clock.systemUTC());
        return new TelemetryServiceImpl(settings, queue, (uri, body, timeout) -> {
            requests.add(new Request(uri, body));
            String resp = uri.getPath().endsWith("/error-report") ? "{\"feedback_id\":\"abc\"}" : "{}";
            return new ConnectionService.HttpPostResult(status.get(), resp);
        }, ENV, Clock.systemUTC(), killed);
    }

    private List<TelemetryEvent> queued() {
        service.awaitIdle();
        return service.queue().snapshot().stream().map(TelemetryQueue.QueuedEvent::event).toList();
    }

    private static JsonArray sentEvents(Request r) {
        return JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("events");
    }

    @Test
    void killSwitchDecision() {
        Properties p = new Properties();
        assertFalse(TelemetryServiceImpl.isKillSwitchActive(p, "2.2.0"));
        assertTrue(TelemetryServiceImpl.isKillSwitchActive(p, "0.0.0-dev"));
        p.setProperty("fxt.telemetry.force", "true");
        assertFalse(TelemetryServiceImpl.isKillSwitchActive(p, "0.0.0-dev"));
        p.setProperty("fxt.telemetry.disabled", "true");
        assertTrue(TelemetryServiceImpl.isKillSwitchActive(p, "2.2.0"));
        // The Gradle test tasks set the kill switch, so a default instance can never send.
        assertTrue(TelemetryServiceImpl.isKillSwitchActive(System.getProperties(), "2.2.0"));
    }

    @Test
    void killedServiceRecordsAndSendsNothing() {
        service.shutdown(Duration.ofMillis(100));
        service = newService(true);
        settings.setNoticeShown(true);
        service.trackAction("validate");
        service.trackError(new IllegalStateException(), "test");
        service.flushAsync();
        assertTrue(queued().isEmpty());
        assertTrue(requests.isEmpty());
        assertFalse(service.isActive());
        assertFalse(service.sendErrorReport("x", null, null, false).join().success());
    }

    @Test
    void usageToggleGatesUsageEventsButNotErrors() {
        settings.setUsageEnabled(false);
        service.trackAction("validate");
        service.trackError(new IllegalStateException(), "test");
        List<TelemetryEvent> events = queued();
        assertEquals(1, events.size());
        assertEquals(TelemetryEvent.Category.ERROR, events.getFirst().category());
    }

    @Test
    void errorToggleGatesErrorEvents() {
        settings.setErrorsEnabled(false);
        service.trackError(new IllegalStateException(), "test");
        service.trackError("ui.action_failed", "dialog.x");
        service.track("error", TelemetryEvent.Category.ERROR);
        service.trackAction("validate");
        List<TelemetryEvent> events = queued();
        assertEquals(1, events.size());
        assertEquals("validate", events.getFirst().eventType());
    }

    @Test
    void disablingAToggleClearsQueuedEventsOfThatKind() {
        service.trackAction("validate");
        service.trackError(new IllegalStateException(), "test");
        assertEquals(2, queued().size());

        service.setUsageEnabled(false);
        assertEquals(1, queued().size());
        assertEquals(TelemetryEvent.Category.ERROR, queued().getFirst().category());

        service.setErrorReportingEnabled(false);
        assertTrue(queued().isEmpty());
        assertFalse(settings.isUsageEnabled());
        assertFalse(settings.isErrorsEnabled());
    }

    @Test
    void errorEventHasSignatureAndWhere() {
        service.trackError(new IllegalStateException("/home/alice/secret.xml"), "Quick Fix");
        TelemetryEvent e = queued().getFirst();
        assertEquals("error", e.eventType());
        assertEquals(TelemetryEvent.Status.ERROR, e.status());
        assertEquals("java.lang.IllegalStateException", e.errorCode());
        assertTrue(e.errorHash().matches("[0-9a-f]{16}"));
        assertEquals("quick_fix", e.meta().get("where"));
        assertFalse(e.toJson().toString().contains("alice"));
    }

    @Test
    void errorsAreDeduplicatedPerSessionWithRepeatCounter() {
        settings.setNoticeShown(true);
        for (int i = 0; i < 4; i++) {
            service.trackError(sameException(), "loop");
        }
        assertEquals(1, queued().size());

        TelemetrySender.FlushResult r = service.flushNow(Duration.ofSeconds(1));
        assertEquals(TelemetrySender.Outcome.SENT, r.outcome());
        JsonObject sent = sentEvents(requests.getFirst()).get(0).getAsJsonObject();
        assertEquals(3, sent.getAsJsonObject("meta").get("repeat").getAsInt());

        // after sending, further repeats are dropped (no new event)
        service.trackError(sameException(), "loop");
        assertTrue(queued().isEmpty());
    }

    private static RuntimeException sameException() {
        RuntimeException e = new RuntimeException("varying message " + System.nanoTime());
        e.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("org.fxt.freexmltoolkit.Foo", "bar", "Foo.java", 1)});
        return e;
    }

    @Test
    void atMostThirtyErrorEventsPerSession() {
        for (int i = 0; i < 45; i++) {
            RuntimeException e = new RuntimeException();
            e.setStackTrace(new StackTraceElement[]{
                    new StackTraceElement("org.fxt.freexmltoolkit.Foo", "m" + i, "Foo.java", 1)});
            service.trackError(e, "cap");
        }
        assertEquals(TelemetryServiceImpl.MAX_ERRORS_PER_SESSION, queued().size());
    }

    @Test
    void recursionAndFailingThrowablesAreSafe() {
        AtomicInteger innerCalls = new AtomicInteger();
        RuntimeException reentrant = new RuntimeException() {
            @Override
            public StackTraceElement[] getStackTrace() {
                innerCalls.incrementAndGet();
                service.trackError(new IllegalArgumentException(), "inner"); // must be ignored
                return super.getStackTrace();
            }
        };
        assertDoesNotThrow(() -> service.trackError(reentrant, "outer"));
        assertTrue(innerCalls.get() > 0);
        List<TelemetryEvent> events = queued();
        assertEquals(1, events.size());
        assertEquals("outer", events.getFirst().meta().get("where"));

        RuntimeException broken = new RuntimeException() {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new IllegalStateException("boom");
            }
        };
        assertDoesNotThrow(() -> service.trackError(broken, "broken"));
        assertDoesNotThrow(() -> service.trackError((Throwable) null, "null"));
        assertDoesNotThrow(() -> service.trackError((String) null, null));
    }

    @Test
    void trackErrorFromManyThreadsIsSafe() throws Exception {
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 50; i++) {
                    service.trackError(sameException(), "threads");
                }
            });
            threads[t].start();
        }
        for (Thread t : threads) {
            t.join(TimeUnit.SECONDS.toMillis(10));
        }
        assertEquals(1, queued().size());
    }

    @Test
    void nothingIsSentBeforeTheNoticeWasShown() {
        service.trackAction("validate");
        assertEquals(TelemetrySender.Outcome.NOT_ALLOWED, service.flushNow(Duration.ofSeconds(1)).outcome());
        assertTrue(requests.isEmpty());

        service.markNoticeShown(); // triggers an async flush
        service.awaitIdle();
        assertEquals(1, requests.size());
        assertTrue(requests.getFirst().uri().toString().endsWith("/v1/events"));
        assertTrue(queued().isEmpty());
    }

    @Test
    void firstRunCreatesInstallIdAndFlagsAppStart() {
        assertTrue(service.isFirstRun());
        String id = settings.getInstallId();
        assertNotNull(id);
        assertEquals(36, id.length());

        service.trackAppStart();
        service.trackAppStart(); // only once per session
        List<TelemetryEvent> events = queued();
        assertEquals(1, events.size());
        assertEquals(Boolean.TRUE, events.getFirst().isFirstRun());

        TelemetryServiceImpl second = newService(false);
        try {
            assertFalse(second.isFirstRun());
            assertEquals(id, second.getInstallId());
        } finally {
            second.shutdown(Duration.ofMillis(100));
        }
    }

    @Test
    void appExitCarriesSessionDuration() {
        service.trackAppExit(Duration.ofSeconds(90));
        TelemetryEvent e = queued().getFirst();
        assertEquals("app_exit", e.eventType());
        assertEquals(TelemetryEvent.Category.LIFECYCLE, e.category());
        assertEquals(90_000L, e.durationMs());
    }

    @Test
    void resetInstallIdCreatesNewId() {
        String before = service.getInstallId();
        String after = service.resetInstallId();
        assertFalse(before.equals(after));
        assertEquals(after, settings.getInstallId());
    }

    @Test
    void errorReportIsPostedWithoutMessages() {
        status.set(201);
        IllegalStateException ex = new IllegalStateException("contains /home/alice/secret.xml");
        ErrorReportResult r = service.sendErrorReport("It crashed when saving", " me@example.com ", ex, true).join();
        assertTrue(r.success(), r.message());
        assertEquals("abc", r.feedbackId());

        Request req = requests.getFirst();
        assertTrue(req.uri().toString().endsWith("/v1/error-report"));
        JsonObject body = JsonParser.parseString(req.body()).getAsJsonObject();
        assertEquals("It crashed when saving", body.get("description").getAsString());
        assertEquals("me@example.com", body.get("contact").getAsString());
        assertEquals("java.lang.IllegalStateException", body.get("error_code").getAsString());
        assertTrue(body.has("stack_signature"));
        assertFalse(req.body().contains("alice"));

        String preview = service.previewErrorReport("d", null, ex, false);
        assertFalse(preview.contains("stack_signature"));
        assertFalse(service.sendErrorReport("  ", null, null, false).join().success());
    }

    @Test
    void errorReportFailureStatusesAreMapped() {
        status.set(429);
        ErrorReportResult r = service.sendErrorReport("x", null, null, false).join();
        assertFalse(r.success());
        assertEquals(429, r.httpStatus());
    }

    @Test
    void previewPayloadIsAnEnvelope() {
        JsonObject o = JsonParser.parseString(service.previewPayload()).getAsJsonObject();
        assertEquals(service.getInstallId(), o.get("install_id").getAsString());
        assertTrue(o.getAsJsonArray("events").size() > 0);
    }
}
