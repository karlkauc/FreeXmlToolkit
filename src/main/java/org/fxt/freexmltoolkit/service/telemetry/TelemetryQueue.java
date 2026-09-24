package org.fxt.freexmltoolkit.service.telemetry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Persistent, bounded FIFO of telemetry events waiting to be sent.
 *
 * <p>Stored as JSON Lines (one {@code {"session_id","app_version","event"}} object per line)
 * at {@code ~/.freeXmlToolkit/telemetry-queue.jsonl} by default. The queue survives restarts
 * and offline periods but is capped at {@link #DEFAULT_MAX_EVENTS} events and
 * {@link #DEFAULT_MAX_AGE} — older/excess events are dropped oldest-first.
 *
 * <p>Thread-safe (all public methods synchronize on the queue). I/O failures are logged
 * and never propagated: telemetry must never disturb the application.
 */
public class TelemetryQueue {

    private static final Logger logger = LogManager.getLogger(TelemetryQueue.class);

    /** Default event cap. */
    public static final int DEFAULT_MAX_EVENTS = 5000;
    /** Default maximum event age. */
    public static final Duration DEFAULT_MAX_AGE = Duration.ofDays(14);

    /**
     * A queued event plus the session it belongs to.
     *
     * @param seq        in-memory sequence number (identity for removal; not persisted)
     * @param sessionId  the app session that produced the event
     * @param appVersion the app version that produced the event
     * @param event      the event
     */
    public record QueuedEvent(long seq, String sessionId, String appVersion, TelemetryEvent event) {
    }

    private final Path file;
    private final int maxEvents;
    private final Duration maxAge;
    private final Clock clock;
    private final List<QueuedEvent> events = new ArrayList<>();
    private long nextSeq = 1;

    /** Queue at the default location with default caps. */
    public static TelemetryQueue createDefault() {
        Path file = Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "telemetry-queue.jsonl");
        return new TelemetryQueue(file, DEFAULT_MAX_EVENTS, DEFAULT_MAX_AGE, Clock.systemUTC());
    }

    /**
     * @param file      JSONL file (parent directories are created on first write); may be
     *                  {@code null} for a purely in-memory queue
     * @param maxEvents event cap
     * @param maxAge    maximum event age (by {@code client_event_at})
     * @param clock     clock used for the age cut-off
     */
    public TelemetryQueue(Path file, int maxEvents, Duration maxAge, Clock clock) {
        this.file = file;
        this.maxEvents = Math.max(1, maxEvents);
        this.maxAge = maxAge;
        this.clock = clock;
        load();
    }

    /** Appends an event (and persists it). Drops the oldest events beyond the caps. */
    public synchronized void add(String sessionId, String appVersion, TelemetryEvent event) {
        if (event == null) {
            return;
        }
        QueuedEvent q = new QueuedEvent(nextSeq++, sessionId, appVersion, event);
        events.add(q);
        if (pruneInMemory()) {
            rewrite();
        } else {
            append(q);
        }
    }

    /** @return up to {@code max} oldest events (a snapshot; the queue is unchanged). */
    public synchronized List<QueuedEvent> peek(int max) {
        pruneAgeAndPersistIfNeeded();
        return new ArrayList<>(events.subList(0, Math.min(max, events.size())));
    }

    /** @return a snapshot of all queued events */
    public synchronized List<QueuedEvent> snapshot() {
        return new ArrayList<>(events);
    }

    /** Removes the events with the given sequence numbers. */
    public synchronized void remove(Collection<Long> seqs) {
        if (seqs == null || seqs.isEmpty()) {
            return;
        }
        Set<Long> set = new HashSet<>(seqs);
        if (events.removeIf(q -> set.contains(q.seq()))) {
            rewrite();
        }
    }

    /** Removes every event matching {@code filter}. @return the number removed */
    public synchronized int removeIf(Predicate<QueuedEvent> filter) {
        int before = events.size();
        if (events.removeIf(filter)) {
            rewrite();
        }
        return before - events.size();
    }

    /**
     * Replaces every event matching {@code filter} by {@code update.apply(event)}.
     *
     * @return the number of updated events
     */
    public synchronized int update(Predicate<QueuedEvent> filter, UnaryOperator<TelemetryEvent> update) {
        int n = 0;
        for (int i = 0; i < events.size(); i++) {
            QueuedEvent q = events.get(i);
            if (filter.test(q)) {
                events.set(i, new QueuedEvent(q.seq(), q.sessionId(), q.appVersion(), update.apply(q.event())));
                n++;
            }
        }
        if (n > 0) {
            rewrite();
        }
        return n;
    }

    /** @return the number of queued events */
    public synchronized int size() {
        return events.size();
    }

    /** Removes everything. */
    public synchronized void clear() {
        events.clear();
        rewrite();
    }

    /** @return the backing file (may be null) */
    public Path getFile() {
        return file;
    }

    // ---------------------------------------------------------------- persistence

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        int bad = 0;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                    TelemetryEvent e = TelemetryEvent.fromJson(o.getAsJsonObject("event"));
                    events.add(new QueuedEvent(nextSeq++,
                            o.has("session_id") ? o.get("session_id").getAsString() : null,
                            o.has("app_version") ? o.get("app_version").getAsString() : null,
                            e));
                } catch (RuntimeException ex) {
                    bad++;
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.debug("Could not read telemetry queue {}: {}", file, e.toString());
        }
        if (pruneInMemory() || bad > 0) {
            rewrite();
        }
    }

    private void pruneAgeAndPersistIfNeeded() {
        if (pruneInMemory()) {
            rewrite();
        }
    }

    /** Drops too-old events and trims to the cap (oldest first). @return true if anything changed */
    private boolean pruneInMemory() {
        boolean changed = false;
        if (maxAge != null) {
            Instant cutoff = clock.instant().minus(maxAge);
            changed = events.removeIf(q -> q.event().clientEventAt().isBefore(cutoff));
        }
        if (events.size() > maxEvents) {
            // Trim a little below the cap for large queues so a long offline period does not
            // rewrite the whole file on every single add.
            int target = maxEvents >= 1000 ? maxEvents - maxEvents / 20 : maxEvents;
            events.subList(0, events.size() - target).clear();
            changed = true;
        }
        return changed;
    }

    private static String toLine(QueuedEvent q) {
        JsonObject o = new JsonObject();
        if (q.sessionId() != null) {
            o.addProperty("session_id", q.sessionId());
        }
        if (q.appVersion() != null) {
            o.addProperty("app_version", q.appVersion());
        }
        o.add("event", q.event().toJson());
        return o.toString();
    }

    private void append(QueuedEvent q) {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, toLine(q) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException e) {
            logger.debug("Could not append to telemetry queue {}: {}", file, e.toString());
        }
    }

    private void rewrite() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            if (events.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (QueuedEvent q : events) {
                    w.write(toLine(q));
                    w.write('\n');
                }
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            logger.debug("Could not rewrite telemetry queue {}: {}", file, e.toString());
        }
    }
}
