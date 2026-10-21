package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateFunnelTest {

    private static final Instant LAUNCHED = Instant.parse("2026-10-21T10:00:00Z");

    private RecordingTelemetryService telemetry;
    private Map<String, String> stored;
    private PropertiesService props;

    @BeforeEach
    void setUp() {
        telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        stored = new ConcurrentHashMap<>();
        props = fakeProperties(stored);
    }

    @AfterEach
    void tearDown() {
        Telemetry.reset();
    }

    /** A PropertiesService whose get/set work on a map (set ignores null, like the real one). */
    static PropertiesService fakeProperties(Map<String, String> map) {
        PropertiesService p = mock(PropertiesService.class);
        when(p.get(anyString())).thenAnswer(inv -> map.get(inv.<String>getArgument(0)));
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            if (key != null && value != null) {
                map.put(key, value);
            }
            return null;
        }).when(p).set(anyString(), anyString());
        return p;
    }

    private TelemetryEvent single(String type) {
        List<TelemetryEvent> events = telemetry.events(type);
        assertEquals(1, events.size(), "one " + type + " expected, got " + telemetry.events());
        return events.getFirst();
    }

    @Test
    void downloadSuccessCarriesVersionsSizeAndDuration() {
        UpdateFunnel.downloaded("v2.5.0", "2.6.0", System.nanoTime() - 4_000_000L, 123_456, (Throwable) null, false);
        TelemetryEvent e = single("update_download");
        assertEquals(TelemetryEvent.Category.ACTION, e.category());
        assertEquals(TelemetryEvent.Status.OK, e.status());
        assertEquals("2.5.0", e.meta().get("from"));
        assertEquals("2.6.0", e.meta().get("to"));
        assertTrue(e.meta().containsKey("platform"));
        assertEquals(123_456L, e.inputBytes());
        assertTrue(e.durationMs() >= 4);
        assertNull(e.errorCode());
    }

    @Test
    void downloadFailureReportsTheExceptionClassOnly() {
        UpdateFunnel.downloaded("2.5.0", "2.6.0", System.nanoTime(), -1,
                new IOException("secret path /home/user"), false);
        TelemetryEvent e = single("update_download");
        assertEquals(TelemetryEvent.Status.ERROR, e.status());
        assertEquals("IOException", e.errorCode());
        assertNull(e.inputBytes());
        assertFalse(e.toJson().toString().contains("secret"), "never the message");
    }

    @Test
    void cancelledDownloadIsNotAnError() {
        UpdateFunnel.downloaded("2.5.0", "2.6.0", 0, -1, (String) null, true);
        TelemetryEvent e = single("update_download");
        assertEquals(TelemetryEvent.Status.CANCELLED, e.status());
        assertNull(e.durationMs());
    }

    @Test
    void applyReportsLaunchResult() {
        UpdateFunnel.applied("2.5.0", "2.6.0", true);
        UpdateFunnel.applied("2.5.0", "2.6.0", false);
        List<TelemetryEvent> events = telemetry.events("update_apply");
        assertEquals(2, events.size());
        assertEquals(TelemetryEvent.Status.OK, events.get(0).status());
        assertEquals(TelemetryEvent.Status.ERROR, events.get(1).status());
        assertEquals(UpdateFunnel.CODE_LAUNCH_FAILED, events.get(1).errorCode());
    }

    @Test
    void pendingUpdateIsPersistedAndReportedAsInstalledOnTheNextStart() {
        UpdateFunnel.markPending(props, "2.5.0", "2.6.0", Clock.fixed(LAUNCHED, ZoneOffset.UTC));
        assertEquals("2.5.0", stored.get(UpdateFunnel.PROP_FROM));
        assertEquals("2.6.0", stored.get(UpdateFunnel.PROP_TO));
        assertEquals(LAUNCHED.toString(), stored.get(UpdateFunnel.PROP_AT));

        Clock nextStart = Clock.fixed(LAUNCHED.plusSeconds(42), ZoneOffset.UTC);
        assertTrue(UpdateFunnel.reportInstalled(props, "2.6.0", telemetry, nextStart));

        TelemetryEvent e = single("update_installed");
        assertEquals(TelemetryEvent.Category.LIFECYCLE, e.category());
        assertEquals(TelemetryEvent.Status.OK, e.status());
        assertEquals("2.5.0", e.meta().get("from"));
        assertEquals("2.6.0", e.meta().get("to"));
        assertEquals("2.6.0", e.meta().get("actual"));
        assertTrue(e.meta().containsKey("platform"));
        assertEquals(42_000L, e.durationMs());

        // cleared: a further start reports nothing
        assertTrue(stored.get(UpdateFunnel.PROP_FROM).isBlank());
        assertTrue(stored.get(UpdateFunnel.PROP_TO).isBlank());
        assertFalse(UpdateFunnel.reportInstalled(props, "2.6.0", telemetry, nextStart));
        assertEquals(1, telemetry.events("update_installed").size());
    }

    @Test
    void stillRunningTheOldVersionMeansTheSwapFailed() {
        UpdateFunnel.markPending(props, "2.5.0", "v2.6.0", Clock.systemUTC());
        assertTrue(UpdateFunnel.reportInstalled(props, "2.5.0", telemetry, Clock.systemUTC()));
        TelemetryEvent e = single("update_installed");
        assertEquals(TelemetryEvent.Status.ERROR, e.status());
        assertEquals(UpdateFunnel.CODE_VERSION_MISMATCH, e.errorCode());
        assertEquals("2.5.0", e.meta().get("actual"));
        assertEquals("2.6.0", e.meta().get("to"));
    }

    @Test
    void nothingPendingReportsNothing() {
        assertFalse(UpdateFunnel.reportInstalled(props, "2.6.0", telemetry, Clock.systemUTC()));
        assertTrue(telemetry.events().isEmpty());
    }

    @Test
    void clearPendingForgetsALaunchThatFailed() {
        UpdateFunnel.markPending(props, "2.5.0", "2.6.0", Clock.systemUTC());
        UpdateFunnel.clearPending(props);
        assertFalse(UpdateFunnel.reportInstalled(props, "2.5.0", telemetry, Clock.systemUTC()));
    }

    @Test
    void inactiveTelemetryPersistsNothing() {
        Telemetry.install(TelemetryService.noop());
        UpdateFunnel.markPending(props, "2.5.0", "2.6.0", Clock.systemUTC());
        assertTrue(stored.isEmpty());
    }

    @Test
    void brokenSettingsNeverThrow() {
        PropertiesService broken = mock(PropertiesService.class);
        when(broken.get(anyString())).thenThrow(new IllegalStateException("disk"));
        assertDoesNotThrow(() -> UpdateFunnel.markPending(broken, "1", "2", Clock.systemUTC()));
        assertDoesNotThrow(() -> UpdateFunnel.clearPending(broken));
        assertFalse(UpdateFunnel.reportInstalled(broken, "2", telemetry, Clock.systemUTC()));
        assertFalse(UpdateFunnel.reportInstalled(null, "2", telemetry, Clock.systemUTC()));
    }

    @Test
    void platformClassification() {
        assertEquals("windows", UpdateFunnel.platform("Windows 11"));
        assertEquals("macos", UpdateFunnel.platform("Mac OS X"));
        assertEquals("linux", UpdateFunnel.platform("Linux"));
        assertEquals("other", UpdateFunnel.platform(null));
    }

    @Test
    void appStartOfTheServiceReportsThePendingUpdate() {
        TelemetrySettings.InMemory settings = new TelemetrySettings.InMemory();
        TelemetryQueue queue = new TelemetryQueue(null, 5000, Duration.ofDays(14), Clock.systemUTC());
        TelemetryServiceImpl service = new TelemetryServiceImpl(settings, queue,
                (uri, body, timeout) -> new org.fxt.freexmltoolkit.service.ConnectionService.HttpPostResult(202, "{}"),
                new TelemetryEnvironment("2.6.0", "Linux", "x64", 25, "en"), Clock.systemUTC(), false);
        try {
            stored.put(UpdateFunnel.PROP_FROM, "2.5.0");
            stored.put(UpdateFunnel.PROP_TO, "2.6.0");
            service.setUpdateStore(props);
            service.trackAppStart();
            service.awaitIdle();
            service.awaitIdle(); // the report enqueues from the worker itself
            List<TelemetryEvent> events = queue.snapshot().stream().map(TelemetryQueue.QueuedEvent::event).toList();
            assertEquals(List.of("app_start", "update_installed"), events.stream().map(TelemetryEvent::eventType).toList());
            assertEquals(TelemetryEvent.Status.OK, events.get(1).status());
            assertTrue(stored.get(UpdateFunnel.PROP_TO).isBlank());
        } finally {
            service.shutdown(Duration.ofMillis(100));
        }
    }

    @Test
    void optedOutUsersSendNothingButThePendingUpdateIsCleared() {
        TelemetrySettings.InMemory settings = new TelemetrySettings.InMemory();
        settings.setUsageEnabled(false);
        TelemetryQueue queue = new TelemetryQueue(null, 5000, Duration.ofDays(14), Clock.systemUTC());
        TelemetryServiceImpl service = new TelemetryServiceImpl(settings, queue,
                (uri, body, timeout) -> new org.fxt.freexmltoolkit.service.ConnectionService.HttpPostResult(202, "{}"),
                new TelemetryEnvironment("2.6.0", "Linux", "x64", 25, "en"), Clock.systemUTC(), false);
        try {
            stored.put(UpdateFunnel.PROP_FROM, "2.5.0");
            stored.put(UpdateFunnel.PROP_TO, "2.6.0");
            service.setUpdateStore(props);
            service.trackAppStart();
            service.awaitIdle();
            service.awaitIdle();
            assertTrue(queue.snapshot().isEmpty());
            assertTrue(stored.get(UpdateFunnel.PROP_TO).isBlank());
        } finally {
            service.shutdown(Duration.ofMillis(100));
        }
    }
}
