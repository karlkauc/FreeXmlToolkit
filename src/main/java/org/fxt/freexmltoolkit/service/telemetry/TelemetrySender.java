package org.fxt.freexmltoolkit.service.telemetry;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ConnectionService;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Sends queued events to {@code POST <endpoint>/v1/events} in batches of at most
 * {@value #MAX_EVENTS_PER_REQUEST} events (one batch per app session, since the envelope
 * carries one {@code session_id}).
 *
 * <p>Response handling (see the ingest API contract):
 * <ul>
 *   <li>{@code 2xx} (202) — the batch is removed from the queue;</li>
 *   <li>{@code 400} / {@code 413} — the batch is dropped (never retried);</li>
 *   <li>{@code 429}, {@code 5xx}, other codes and network errors — the batch is kept and the
 *       sender backs off exponentially ({@link #INITIAL_BACKOFF} → max {@link #MAX_BACKOFF}).</li>
 * </ul>
 *
 * <p>Nothing is sent while {@code sendAllowed} is false (kill switch, notice not yet shown,
 * both toggles off).
 */
public class TelemetrySender {

    private static final Logger logger = LogManager.getLogger(TelemetrySender.class);

    /** Max events per request (API limit 200). */
    public static final int MAX_EVENTS_PER_REQUEST = 200;
    /** Max request body (API limit 256 KB, keep a margin). */
    static final int MAX_BODY_BYTES = 240 * 1024;
    /** Max requests per flush (the server allows 60 requests/hour). */
    static final int MAX_REQUESTS_PER_FLUSH = 10;
    /** First backoff after a failed attempt. */
    public static final Duration INITIAL_BACKOFF = Duration.ofMinutes(5);
    /** Backoff ceiling. */
    public static final Duration MAX_BACKOFF = Duration.ofHours(6);

    /** Posts a JSON body; {@link ConnectionService#postJson} fits this shape. */
    @FunctionalInterface
    public interface HttpPoster {
        ConnectionService.HttpPostResult post(URI uri, String json, Duration timeout) throws IOException;
    }

    /** Outcome of a {@link #flush(Duration)}. */
    public enum Outcome {
        /** Queue empty. */
        NOTHING_TO_SEND,
        /** Sending not allowed right now (gate closed). */
        NOT_ALLOWED,
        /** Skipped because of an active backoff. */
        BACKING_OFF,
        /** Everything attempted was accepted or dropped as invalid. */
        SENT,
        /** A request failed transiently; remaining events are kept for later. */
        RETRY_LATER
    }

    /**
     * @param outcome  overall outcome
     * @param sent     events accepted by the server
     * @param dropped  events dropped (400/413)
     * @param requests HTTP requests made
     */
    public record FlushResult(Outcome outcome, int sent, int dropped, int requests) {
    }

    private final TelemetryQueue queue;
    private final HttpPoster poster;
    private final Supplier<String> endpoint;
    private final Supplier<String> installId;
    private final TelemetryEnvironment environment;
    private final BooleanSupplier sendAllowed;
    private final Clock clock;

    private Instant nextAttemptAt = Instant.EPOCH;
    private Duration currentBackoff = INITIAL_BACKOFF;

    public TelemetrySender(TelemetryQueue queue, HttpPoster poster, Supplier<String> endpoint,
                           Supplier<String> installId, TelemetryEnvironment environment,
                           BooleanSupplier sendAllowed, Clock clock) {
        this.queue = Objects.requireNonNull(queue);
        this.poster = Objects.requireNonNull(poster);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.installId = Objects.requireNonNull(installId);
        this.environment = Objects.requireNonNull(environment);
        this.sendAllowed = Objects.requireNonNull(sendAllowed);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Sends queued events (blocking, bounded by {@code timeout} per request).
     *
     * @param timeout connect/read timeout per request
     * @return what happened
     */
    public synchronized FlushResult flush(Duration timeout) {
        if (!sendAllowed.getAsBoolean()) {
            return new FlushResult(Outcome.NOT_ALLOWED, 0, 0, 0);
        }
        if (queue.size() == 0) {
            return new FlushResult(Outcome.NOTHING_TO_SEND, 0, 0, 0);
        }
        if (clock.instant().isBefore(nextAttemptAt)) {
            return new FlushResult(Outcome.BACKING_OFF, 0, 0, 0);
        }
        URI uri = eventsUri(endpoint.get());
        int sent = 0;
        int dropped = 0;
        int requests = 0;
        while (requests < MAX_REQUESTS_PER_FLUSH) {
            List<TelemetryQueue.QueuedEvent> batch = nextBatch();
            if (batch.isEmpty()) {
                break;
            }
            String body = envelope(batch).toString();
            while (body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES && batch.size() > 1) {
                batch = new ArrayList<>(batch.subList(0, batch.size() / 2));
                body = envelope(batch).toString();
            }
            List<Long> seqs = batch.stream().map(TelemetryQueue.QueuedEvent::seq).toList();
            if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
                queue.remove(seqs); // a single oversized event can never be accepted
                dropped += seqs.size();
                continue;
            }
            requests++;
            int status;
            try {
                status = poster.post(uri, body, timeout).status();
            } catch (IOException | RuntimeException e) {
                logger.debug("Telemetry send failed: {}", e.toString());
                backOff();
                return new FlushResult(Outcome.RETRY_LATER, sent, dropped, requests);
            }
            if (status >= 200 && status < 300) {
                queue.remove(seqs);
                sent += seqs.size();
                resetBackoff();
            } else if (status == 400 || status == 413) {
                logger.debug("Telemetry batch rejected with HTTP {} — dropping {} events", status, seqs.size());
                queue.remove(seqs);
                dropped += seqs.size();
            } else {
                logger.debug("Telemetry send got HTTP {} — keeping {} events for later", status, seqs.size());
                backOff();
                return new FlushResult(Outcome.RETRY_LATER, sent, dropped, requests);
            }
        }
        return new FlushResult(sent + dropped == 0 ? Outcome.NOTHING_TO_SEND : Outcome.SENT, sent, dropped, requests);
    }

    /** @return the instant before which no send is attempted */
    public synchronized Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    /** @return the backoff that will be applied after the next failure */
    public synchronized Duration getCurrentBackoff() {
        return currentBackoff;
    }

    /** Clears any active backoff (e.g. after the endpoint changed). */
    public synchronized void resetBackoff() {
        nextAttemptAt = Instant.EPOCH;
        currentBackoff = INITIAL_BACKOFF;
    }

    private void backOff() {
        nextAttemptAt = clock.instant().plus(currentBackoff);
        Duration doubled = currentBackoff.multipliedBy(2);
        currentBackoff = doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled;
    }

    /** Oldest events that share the first event's session and app version (≤ 200). */
    private List<TelemetryQueue.QueuedEvent> nextBatch() {
        List<TelemetryQueue.QueuedEvent> candidates = queue.peek(MAX_EVENTS_PER_REQUEST * 5);
        List<TelemetryQueue.QueuedEvent> batch = new ArrayList<>();
        if (candidates.isEmpty()) {
            return batch;
        }
        TelemetryQueue.QueuedEvent first = candidates.getFirst();
        for (TelemetryQueue.QueuedEvent q : candidates) {
            if (Objects.equals(q.sessionId(), first.sessionId())
                    && Objects.equals(q.appVersion(), first.appVersion())) {
                batch.add(q);
                if (batch.size() >= MAX_EVENTS_PER_REQUEST) {
                    break;
                }
            }
        }
        return batch;
    }

    private JsonObject envelope(List<TelemetryQueue.QueuedEvent> batch) {
        TelemetryQueue.QueuedEvent first = batch.getFirst();
        List<TelemetryEvent> events = batch.stream().map(TelemetryQueue.QueuedEvent::event).toList();
        return buildEnvelope(installId.get(),
                first.sessionId() != null ? first.sessionId() : UUID.randomUUID().toString(),
                first.appVersion() != null ? first.appVersion() : environment.appVersion(),
                environment, events);
    }

    /** Builds the {@code POST /v1/events} request body. */
    public static JsonObject buildEnvelope(String installId, String sessionId, String appVersion,
                                           TelemetryEnvironment env, List<TelemetryEvent> events) {
        JsonObject o = new JsonObject();
        o.addProperty("install_id", installId);
        o.addProperty("session_id", sessionId);
        o.addProperty("app_version", TelemetrySanitizer.truncate(appVersion, 32));
        o.addProperty("os_name", env.osName());
        o.addProperty("os_arch", env.osArch());
        o.addProperty("java_major", env.javaMajor());
        o.addProperty("locale", env.locale());
        JsonArray arr = new JsonArray();
        for (TelemetryEvent e : events) {
            arr.add(e.toJson());
        }
        o.add("events", arr);
        return o;
    }

    /** {@code <base>/v1/events}. */
    static URI eventsUri(String base) {
        return URI.create(stripSlash(base) + "/v1/events");
    }

    /** {@code <base>/v1/error-report}. */
    static URI errorReportUri(String base) {
        return URI.create(stripSlash(base) + "/v1/error-report");
    }

    private static String stripSlash(String base) {
        String b = base == null ? "" : base.strip();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }
}
