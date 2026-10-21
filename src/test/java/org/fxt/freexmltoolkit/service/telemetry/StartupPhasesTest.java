package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StartupPhasesTest {

    private static final long MS = 1_000_000L;

    @AfterEach
    void tearDown() {
        StartupPhases.reset();
    }

    private static TelemetryEvent.Builder apply(java.util.function.Consumer<TelemetryEvent.Builder> c) {
        TelemetryEvent.Builder b = TelemetryEvent.builder("app_start", TelemetryEvent.Category.LIFECYCLE);
        c.accept(b);
        return b;
    }

    @Test
    void phasesAreMillisecondsSinceMainEntry() {
        long main = 1_000 * MS;
        StartupPhases.set(main, 850, main + 120 * MS, main + 900 * MS, main + 1_400 * MS);
        TelemetryEvent.Builder b = apply(StartupPhases.meta());
        assertEquals(850L, b.metaValue("phase_main_ms"));
        assertEquals(120L, b.metaValue("phase_splash_ms"));
        assertEquals(900L, b.metaValue("phase_fxml_ms"));
        assertEquals(1_400L, b.metaValue("phase_ready_ms"));
    }

    @Test
    void unmarkedPhasesAreOmitted() {
        long main = 5 * MS;
        StartupPhases.set(main, -1, main + 10 * MS, 0, 0);
        TelemetryEvent.Builder b = apply(StartupPhases.meta());
        assertEquals(10L, b.metaValue("phase_splash_ms"));
        assertEquals(null, b.metaValue("phase_main_ms"));
        assertEquals(null, b.metaValue("phase_fxml_ms"));
        assertEquals(null, b.metaValue("phase_ready_ms"));
    }

    @Test
    void withoutMainMarkNothingIsReported() {
        StartupPhases.set(0, 100, 7 * MS, 8 * MS, 9 * MS);
        TelemetryEvent e = apply(StartupPhases.meta()).build();
        assertTrue(e.meta() == null || e.meta().keySet().stream().noneMatch(k -> k.startsWith("phase_")));
    }

    @Test
    void markingRecordsMonotonicPhases() {
        StartupPhases.markMain();
        StartupPhases.markSplash();
        StartupPhases.markFxml();
        StartupPhases.markReady();
        TelemetryEvent.Builder b = apply(StartupPhases.meta());
        long splash = (Long) b.metaValue("phase_splash_ms");
        long ready = (Long) b.metaValue("phase_ready_ms");
        assertTrue(splash >= 0 && ready >= splash);
    }

    @Test
    void appStartKeepsChannelFirstAndStaysUnderTheMetaLimit() {
        long main = System.nanoTime() - 3_000 * MS;
        StartupPhases.set(main, 400, main + 300 * MS, main + 1_500 * MS, main + 2_500 * MS);
        TelemetryQueue queue = new TelemetryQueue(null, 5000, Duration.ofDays(14), Clock.systemUTC());
        TelemetryServiceImpl service = new TelemetryServiceImpl(new TelemetrySettings.InMemory(), queue,
                (uri, body, timeout) -> new ConnectionService.HttpPostResult(202, "{}"),
                new TelemetryEnvironment("2.6.0", "Linux", "x64", 25, "en"), Clock.systemUTC(), false);
        try {
            service.trackAppStart(LaunchContext.appStart(mock(PropertiesService.class)));
            service.awaitIdle();
            List<TelemetryEvent> events = queue.snapshot().stream().map(TelemetryQueue.QueuedEvent::event).toList();
            TelemetryEvent e = events.getFirst();
            assertEquals("app_start", e.eventType());
            assertEquals("channel", e.meta().keySet().iterator().next(), "channel must stay the first meta key");
            assertEquals(400L, ((Number) e.meta().get("phase_main_ms")).longValue());
            assertEquals(300L, ((Number) e.meta().get("phase_splash_ms")).longValue());
            assertEquals(1_500L, ((Number) e.meta().get("phase_fxml_ms")).longValue());
            assertEquals(2_500L, ((Number) e.meta().get("phase_ready_ms")).longValue());
            assertTrue(e.meta().containsKey("package"));
            int metaBytes = e.toJson().get("meta").toString().getBytes(StandardCharsets.UTF_8).length;
            assertTrue(metaBytes < 1024, "app_start meta is " + metaBytes + " bytes (ingest limit 4096)");
            assertFalse(e.meta().keySet().stream().anyMatch(k -> k.length() > 40));
        } finally {
            service.shutdown(Duration.ofMillis(100));
        }
    }
}
