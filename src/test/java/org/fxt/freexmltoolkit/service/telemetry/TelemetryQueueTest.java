package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelemetryQueueTest {

    @TempDir
    Path tmp;

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static TelemetryEvent event(String type, Instant at) {
        return TelemetryEvent.builder(type, TelemetryEvent.Category.ACTION).clientEventAt(at).build();
    }

    @Test
    void persistenceRoundTrip() {
        Path file = tmp.resolve("q.jsonl");
        TelemetryQueue q = new TelemetryQueue(file, 100, Duration.ofDays(14), CLOCK);
        q.add("s1", "2.2.0", TelemetryEvent.builder("validate", TelemetryEvent.Category.ACTION)
                .clientEventAt(NOW).docKind(DocKind.XSD).durationMs(12).meta("mode", "tree").build());
        q.add("s1", "2.2.0", event("open", NOW));
        assertTrue(Files.exists(file));

        TelemetryQueue reloaded = new TelemetryQueue(file, 100, Duration.ofDays(14), CLOCK);
        List<TelemetryQueue.QueuedEvent> all = reloaded.snapshot();
        assertEquals(2, all.size());
        assertEquals("validate", all.getFirst().event().eventType());
        assertEquals(DocKind.XSD, all.getFirst().event().docKind());
        assertEquals("tree", all.getFirst().event().meta().get("mode"));
        assertEquals("s1", all.getFirst().sessionId());
        assertEquals("2.2.0", all.getFirst().appVersion());

        reloaded.remove(List.of(all.getFirst().seq()));
        assertEquals(1, new TelemetryQueue(file, 100, Duration.ofDays(14), CLOCK).size());
    }

    @Test
    void capDropsOldestFirst() {
        TelemetryQueue q = new TelemetryQueue(tmp.resolve("cap.jsonl"), 10, Duration.ofDays(14), CLOCK);
        for (int i = 0; i < 25; i++) {
            q.add("s", "v", event("e" + i, NOW));
        }
        List<TelemetryQueue.QueuedEvent> all = q.snapshot();
        assertEquals(10, all.size());
        assertEquals("e15", all.getFirst().event().eventType());
        assertEquals("e24", all.getLast().event().eventType());
        assertEquals(10, new TelemetryQueue(tmp.resolve("cap.jsonl"), 10, Duration.ofDays(14), CLOCK).size());
    }

    @Test
    void defaultCapIsHonoredForLargeQueues() {
        TelemetryQueue q = new TelemetryQueue(null, TelemetryQueue.DEFAULT_MAX_EVENTS,
                TelemetryQueue.DEFAULT_MAX_AGE, CLOCK);
        for (int i = 0; i < TelemetryQueue.DEFAULT_MAX_EVENTS + 50; i++) {
            q.add("s", "v", event("e", NOW));
        }
        assertTrue(q.size() <= TelemetryQueue.DEFAULT_MAX_EVENTS);
    }

    @Test
    void eventsOlderThanMaxAgeAreDropped() {
        Path file = tmp.resolve("age.jsonl");
        TelemetryQueue q = new TelemetryQueue(file, 100, Duration.ofDays(14), CLOCK);
        q.add("s", "v", event("old", NOW.minus(Duration.ofDays(15))));
        q.add("s", "v", event("recent", NOW.minus(Duration.ofDays(13))));
        assertEquals(1, q.size());
        assertEquals("recent", q.peek(10).getFirst().event().eventType());

        // aging while persisted: reload with a later clock
        Clock later = Clock.fixed(NOW.plus(Duration.ofDays(2)), ZoneOffset.UTC);
        assertEquals(0, new TelemetryQueue(file, 100, Duration.ofDays(14), later).size());
    }

    @Test
    void corruptLinesAreSkipped() throws Exception {
        Path file = tmp.resolve("corrupt.jsonl");
        TelemetryQueue q = new TelemetryQueue(file, 100, Duration.ofDays(14), CLOCK);
        q.add("s", "v", event("good", NOW));
        Files.writeString(file, "not json\n{\"event\":{}}\n", java.nio.file.StandardOpenOption.APPEND);
        TelemetryQueue reloaded = new TelemetryQueue(file, 100, Duration.ofDays(14), CLOCK);
        assertEquals(1, reloaded.size());
        assertFalse(Files.readString(file).contains("not json"));
    }

    @Test
    void concurrentAddsAreAllKept() throws Exception {
        Path file = tmp.resolve("concurrent.jsonl");
        TelemetryQueue q = new TelemetryQueue(file, 5000, Duration.ofDays(14), CLOCK);
        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    q.add("s", "v", event("e", NOW));
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(threads * perThread, q.size());
        assertEquals(threads * perThread, new TelemetryQueue(file, 5000, Duration.ofDays(14), CLOCK).size());
    }

    @Test
    void removeIfAndUpdate() {
        TelemetryQueue q = new TelemetryQueue(null, 100, Duration.ofDays(14), CLOCK);
        q.add("s", "v", event("a", NOW));
        q.add("s", "v", TelemetryEvent.builder("error", TelemetryEvent.Category.ERROR).clientEventAt(NOW).build());
        assertEquals(1, q.update(e -> e.event().category() == TelemetryEvent.Category.ERROR,
                e -> e.toBuilder().meta("repeat", 3).build()));
        assertEquals(3, q.snapshot().getLast().event().meta().get("repeat"));
        assertEquals(1, q.removeIf(e -> e.event().category() != TelemetryEvent.Category.ERROR));
        assertEquals(1, q.size());
    }
}
