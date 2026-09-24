package org.fxt.freexmltoolkit.service.telemetry;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * One anonymous telemetry event as defined by the ingest API ({@code POST /v1/events}).
 *
 * <p>Instances are immutable and always sanitized: build them through {@link #builder}.
 * Nothing in an event may identify a user, a file name, a path or document content —
 * the richest information is the coarse {@link DocKind} and sizes/counts.
 *
 * @param eventType     {@code [a-z0-9_]{1,40}}, e.g. {@code validate}
 * @param category      event category
 * @param status        outcome
 * @param clientEventAt when the event happened (UTC)
 * @param docKind       coarse document kind (nullable)
 * @param fileCount     number of files involved (nullable)
 * @param inputBytes    input size in bytes (nullable)
 * @param elementCount  number of elements/nodes (nullable)
 * @param errorCount    number of (validation) errors (nullable)
 * @param durationMs    duration in milliseconds (nullable)
 * @param errorCode     exception class or short error code (nullable, ≤ 200)
 * @param errorDetail   sanitized stack signature (nullable, ≤ 4000)
 * @param errorHash     16 hex chars (nullable)
 * @param isFirstRun    first launch of this installation (nullable)
 * @param meta          flat extra attributes (nullable, ≤ 4 KB)
 */
public record TelemetryEvent(
        String eventType,
        Category category,
        Status status,
        Instant clientEventAt,
        DocKind docKind,
        Integer fileCount,
        Long inputBytes,
        Integer elementCount,
        Integer errorCount,
        Long durationMs,
        String errorCode,
        String errorDetail,
        String errorHash,
        Boolean isFirstRun,
        Map<String, Object> meta) {

    /** Event category ({@code lifecycle | action | navigation | error}). */
    public enum Category {
        LIFECYCLE, ACTION, NAVIGATION, ERROR;

        /** @return the lower-case wire value */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Event outcome ({@code ok | error | cancelled | timeout | unsupported | invalid_input}). */
    public enum Status {
        OK, ERROR, CANCELLED, TIMEOUT, UNSUPPORTED, INVALID_INPUT;

        /** @return the lower-case wire value */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Canonical constructor: copies meta defensively. */
    public TelemetryEvent {
        meta = meta == null ? null : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(meta));
    }

    /** Starts a builder for {@code eventType} in {@code category} (status defaults to OK). */
    public static Builder builder(String eventType, Category category) {
        return new Builder(eventType, category);
    }

    /** @return a builder pre-filled with this event's values */
    public Builder toBuilder() {
        Builder b = new Builder(eventType, category);
        b.status = status;
        b.clientEventAt = clientEventAt;
        b.docKind = docKind;
        b.fileCount = fileCount;
        b.inputBytes = inputBytes;
        b.elementCount = elementCount;
        b.errorCount = errorCount;
        b.durationMs = durationMs;
        b.errorCode = errorCode;
        b.errorDetail = errorDetail;
        b.errorHash = errorHash;
        b.isFirstRun = isFirstRun;
        if (meta != null) {
            b.meta.putAll(meta);
        }
        return b;
    }

    /** Serializes to the wire format (snake_case, null fields omitted). */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("event_type", eventType);
        o.addProperty("category", category.wireName());
        o.addProperty("status", status.wireName());
        o.addProperty("client_event_at", clientEventAt.toString());
        if (docKind != null) {
            o.addProperty("doc_kind", docKind.wireName());
        }
        addIfPresent(o, "file_count", fileCount);
        addIfPresent(o, "input_bytes", inputBytes);
        addIfPresent(o, "element_count", elementCount);
        addIfPresent(o, "error_count", errorCount);
        addIfPresent(o, "duration_ms", durationMs);
        if (errorCode != null) {
            o.addProperty("error_code", errorCode);
        }
        if (errorDetail != null) {
            o.addProperty("error_detail", errorDetail);
        }
        if (errorHash != null) {
            o.addProperty("error_hash", errorHash);
        }
        if (isFirstRun != null) {
            o.addProperty("is_first_run", isFirstRun);
        }
        if (meta != null && !meta.isEmpty()) {
            JsonObject m = new JsonObject();
            meta.forEach((k, v) -> {
                if (v instanceof Boolean bool) {
                    m.addProperty(k, bool);
                } else if (v instanceof Number n) {
                    m.addProperty(k, n);
                } else {
                    m.addProperty(k, String.valueOf(v));
                }
            });
            o.add("meta", m);
        }
        return o;
    }

    /**
     * Parses the wire format produced by {@link #toJson()} (used by the persistent queue).
     *
     * @throws IllegalArgumentException if a required field is missing or invalid
     */
    public static TelemetryEvent fromJson(JsonObject o) {
        try {
            Builder b = builder(o.get("event_type").getAsString(),
                    Category.valueOf(o.get("category").getAsString().toUpperCase(Locale.ROOT)));
            b.status(Status.valueOf(o.get("status").getAsString().toUpperCase(Locale.ROOT)));
            b.clientEventAt(Instant.parse(o.get("client_event_at").getAsString()));
            if (has(o, "doc_kind")) {
                b.docKind(DocKind.valueOf(o.get("doc_kind").getAsString().toUpperCase(Locale.ROOT)));
            }
            if (has(o, "file_count")) {
                b.fileCount(o.get("file_count").getAsInt());
            }
            if (has(o, "input_bytes")) {
                b.inputBytes(o.get("input_bytes").getAsLong());
            }
            if (has(o, "element_count")) {
                b.elementCount(o.get("element_count").getAsInt());
            }
            if (has(o, "error_count")) {
                b.errorCount(o.get("error_count").getAsInt());
            }
            if (has(o, "duration_ms")) {
                b.durationMs(o.get("duration_ms").getAsLong());
            }
            if (has(o, "error_code")) {
                b.errorCode(o.get("error_code").getAsString());
            }
            if (has(o, "error_detail")) {
                b.errorDetail(o.get("error_detail").getAsString());
            }
            if (has(o, "error_hash")) {
                b.errorHash(o.get("error_hash").getAsString());
            }
            if (has(o, "is_first_run")) {
                b.firstRun(o.get("is_first_run").getAsBoolean());
            }
            if (has(o, "meta") && o.get("meta").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("meta").entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        JsonPrimitive p = e.getValue().getAsJsonPrimitive();
                        if (p.isBoolean()) {
                            b.meta(e.getKey(), p.getAsBoolean());
                        } else if (p.isNumber()) {
                            b.meta(e.getKey(), p.getAsNumber());
                        } else {
                            b.meta(e.getKey(), p.getAsString());
                        }
                    }
                }
            }
            return b.build();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid telemetry event JSON", e);
        }
    }

    private static boolean has(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    private static void addIfPresent(JsonObject o, String key, Number value) {
        if (value != null) {
            o.addProperty(key, value);
        }
    }

    /** Fluent builder; all setters sanitize their input. */
    public static final class Builder {
        private final String eventType;
        private final Category category;
        private Status status = Status.OK;
        private Instant clientEventAt;
        private DocKind docKind;
        private Integer fileCount;
        private Long inputBytes;
        private Integer elementCount;
        private Integer errorCount;
        private Long durationMs;
        private String errorCode;
        private String errorDetail;
        private String errorHash;
        private Boolean isFirstRun;
        private final Map<String, Object> meta = new LinkedHashMap<>();

        private Builder(String eventType, Category category) {
            this.eventType = TelemetrySanitizer.eventType(eventType);
            this.category = category == null ? Category.ACTION : category;
        }

        public Builder status(Status status) {
            this.status = status == null ? Status.OK : status;
            return this;
        }

        public Builder clientEventAt(Instant at) {
            this.clientEventAt = at;
            return this;
        }

        public Builder docKind(DocKind kind) {
            this.docKind = kind;
            return this;
        }

        /** Derives the doc kind from the path's extension — the path itself is never stored. */
        public Builder docKind(Path path) {
            this.docKind = DocKind.fromPath(path);
            return this;
        }

        public Builder fileCount(int count) {
            this.fileCount = Math.max(0, count);
            return this;
        }

        public Builder inputBytes(long bytes) {
            this.inputBytes = Math.max(0L, bytes);
            return this;
        }

        public Builder elementCount(int count) {
            this.elementCount = Math.max(0, count);
            return this;
        }

        public Builder errorCount(int count) {
            this.errorCount = Math.max(0, count);
            return this;
        }

        public Builder durationMs(long ms) {
            this.durationMs = Math.max(0L, ms);
            return this;
        }

        /** Sets the duration from a {@link System#nanoTime()} start value. */
        public Builder durationSinceNanos(long startNanos) {
            return durationMs((System.nanoTime() - startNanos) / 1_000_000L);
        }

        public Builder errorCode(String code) {
            this.errorCode = TelemetrySanitizer.errorCode(code);
            return this;
        }

        public Builder errorDetail(String detail) {
            this.errorDetail = TelemetrySanitizer.truncate(detail, ErrorSignature.MAX_DETAIL_CHARS);
            return this;
        }

        public Builder errorHash(String hash) {
            this.errorHash = hash != null && hash.matches("[0-9a-f]{16}") ? hash : null;
            return this;
        }

        /** Sets error code, detail and hash from a signature. */
        public Builder error(ErrorSignature signature) {
            if (signature != null) {
                errorCode(signature.errorCode());
                errorDetail(signature.detail());
                errorHash(signature.hash());
            }
            return this;
        }

        public Builder firstRun(boolean firstRun) {
            this.isFirstRun = firstRun;
            return this;
        }

        /** Adds a flat meta attribute (String, Number, Boolean or Enum; others are dropped). */
        public Builder meta(String key, Object value) {
            if (key != null && value != null) {
                meta.put(key, value);
            }
            return this;
        }

        /** @return the event type this builder was created with */
        public String eventType() {
            return eventType;
        }

        /** @return the category this builder was created with */
        public Category category() {
            return category;
        }

        public TelemetryEvent build() {
            return new TelemetryEvent(eventType, category, status,
                    (clientEventAt != null ? clientEventAt : Instant.now()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                    docKind, fileCount, inputBytes, elementCount, errorCount, durationMs,
                    errorCode, errorDetail, errorHash, isFirstRun, TelemetrySanitizer.meta(meta));
        }
    }
}
