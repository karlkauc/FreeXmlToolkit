package org.fxt.freexmltoolkit.service.telemetry;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.util.VersionUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Default {@link TelemetryService}: queues events persistently ({@link TelemetryQueue}) and
 * sends them in the background ({@link TelemetrySender}) on a single daemon thread.
 *
 * <p><b>Kill switch:</b> nothing is recorded or sent when {@code -Dfxt.telemetry.disabled=true}
 * (set by every Gradle test task and {@code gradlew run}) or when the app version is the
 * development fallback {@code 0.0.0-dev} (unless {@code -Dfxt.telemetry.force=true}).
 *
 * <p><b>Gates:</b> events are queued but not sent until the first-start notice was shown;
 * usage events obey the usage toggle, error events the error toggle.
 */
public class TelemetryServiceImpl implements TelemetryService {

    private static final Logger logger = LogManager.getLogger(TelemetryServiceImpl.class);

    /** Default ingest base URL. */
    public static final String DEFAULT_ENDPOINT = "https://telemetry.status20.net";
    /** System property: disable telemetry entirely. */
    public static final String PROP_DISABLED = "fxt.telemetry.disabled";
    /** System property: allow telemetry in development builds. */
    public static final String PROP_FORCE = "fxt.telemetry.force";
    /** System property: endpoint override. */
    public static final String PROP_ENDPOINT = "fxt.telemetry.endpoint";
    /** Version reported by development builds (see {@link VersionUtil}). */
    static final String DEV_VERSION = "0.0.0-dev";

    /** Max automatic error events per session. */
    static final int MAX_ERRORS_PER_SESSION = 30;
    /** Per-request timeout of background flushes. */
    static final Duration FLUSH_TIMEOUT = Duration.ofSeconds(10);
    /** Timeout of the error-report request. */
    static final Duration REPORT_TIMEOUT = Duration.ofSeconds(15);

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Recursion guard: tracking an error must never re-enter itself (e.g. from the uncaught handler). */
    private static final ThreadLocal<Boolean> IN_ERROR = new ThreadLocal<>();

    private final TelemetrySettings settings;
    private final TelemetryQueue queue;
    private final TelemetrySender.HttpPoster poster;
    private final TelemetryEnvironment environment;
    private final boolean killed;
    private final TelemetrySender sender;
    private final ExecutorService executor;

    private final String sessionId = UUID.randomUUID().toString();
    private volatile boolean firstRun;
    private final AtomicBoolean appStartTracked = new AtomicBoolean();
    private final Map<String, AtomicInteger> errorRepeats = new ConcurrentHashMap<>();
    private final AtomicInteger errorEvents = new AtomicInteger();
    private final AtomicBoolean flushPending = new AtomicBoolean();

    /**
     * @param settings    persistent settings
     * @param queue       event queue
     * @param poster      HTTP POST function (normally {@link ConnectionService#postJson})
     * @param environment runtime environment for the envelope
     * @param clock       clock (backoff, queue age)
     * @param killSwitch  true to disable recording and sending entirely
     */
    public TelemetryServiceImpl(TelemetrySettings settings, TelemetryQueue queue,
                                TelemetrySender.HttpPoster poster, TelemetryEnvironment environment,
                                Clock clock, boolean killSwitch) {
        this.settings = Objects.requireNonNull(settings);
        this.queue = Objects.requireNonNull(queue);
        this.poster = Objects.requireNonNull(poster);
        this.environment = Objects.requireNonNull(environment);
        this.killed = killSwitch;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Telemetry-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        this.sender = new TelemetrySender(queue, poster, this::endpoint, this::getInstallId, environment,
                this::isSendAllowed, clock);
        if (!killed) {
            ensureInstallId();
        }
    }

    /** Production instance: real properties, proxy-aware HTTP, queue under {@code ~/.freeXmlToolkit}. */
    public static TelemetryServiceImpl createDefault() {
        boolean killed = isKillSwitchActive(System.getProperties(), VersionUtil.getVersion());
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        ConnectionService connection = ServiceRegistry.get(ConnectionService.class);
        TelemetryQueue queue = killed
                ? new TelemetryQueue(null, TelemetryQueue.DEFAULT_MAX_EVENTS, TelemetryQueue.DEFAULT_MAX_AGE, Clock.systemUTC())
                : TelemetryQueue.createDefault();
        if (killed) {
            logger.info("Telemetry disabled for this process (test/dev build or -D{}=true)", PROP_DISABLED);
        }
        return new TelemetryServiceImpl(TelemetrySettings.of(props), queue, connection::postJson,
                TelemetryEnvironment.current(), Clock.systemUTC(), killed);
    }

    /**
     * Whether telemetry must be off for this process.
     *
     * @param systemProperties the JVM system properties
     * @param appVersion       the app version
     * @return true for {@code fxt.telemetry.disabled=true}, or a dev version without
     *         {@code fxt.telemetry.force=true}
     */
    public static boolean isKillSwitchActive(Properties systemProperties, String appVersion) {
        if (Boolean.parseBoolean(systemProperties.getProperty(PROP_DISABLED))) {
            return true;
        }
        boolean force = Boolean.parseBoolean(systemProperties.getProperty(PROP_FORCE));
        boolean devBuild = appVersion == null || appVersion.isBlank() || DEV_VERSION.equals(appVersion);
        return devBuild && !force;
    }

    // ------------------------------------------------------------------ state

    @Override
    public boolean isActive() {
        return !killed;
    }

    /** @return the session id of this launch */
    public String getSessionId() {
        return sessionId;
    }

    /** @return true if this launch created the install id */
    public boolean isFirstRun() {
        return firstRun;
    }

    /** @return the queue (tests) */
    TelemetryQueue queue() {
        return queue;
    }

    /** @return the sender (tests) */
    TelemetrySender sender() {
        return sender;
    }

    /** Effective endpoint: system property, then setting, then default. */
    String endpoint() {
        String sys = System.getProperty(PROP_ENDPOINT);
        if (sys != null && !sys.isBlank()) {
            return sys.strip();
        }
        String configured = safe(settings::getEndpoint, null);
        return configured != null && !configured.isBlank() ? configured.strip() : DEFAULT_ENDPOINT;
    }

    private boolean isSendAllowed() {
        return !killed
                && safe(settings::isNoticeShown, false)
                && (safe(settings::isUsageEnabled, false) || safe(settings::isErrorsEnabled, false));
    }

    // ------------------------------------------------------------------ usage events

    @Override
    public void track(String eventType, TelemetryEvent.Category category, Consumer<TelemetryEvent.Builder> customizer) {
        if (killed) {
            return;
        }
        try {
            TelemetryEvent.Builder builder = TelemetryEvent.builder(eventType, category);
            if (customizer != null) {
                customizer.accept(builder);
            }
            enqueue(builder.build());
        } catch (Throwable t) {
            logger.debug("Telemetry track failed: {}", t.toString());
        }
    }

    @Override
    public void trackAppStart() {
        if (!appStartTracked.compareAndSet(false, true)) {
            return;
        }
        boolean first = firstRun;
        track("app_start", TelemetryEvent.Category.LIFECYCLE, b -> b.firstRun(first));
    }

    @Override
    public void trackAppExit(Duration sessionDuration) {
        long ms = sessionDuration == null ? 0 : Math.max(0, sessionDuration.toMillis());
        track("app_exit", TelemetryEvent.Category.LIFECYCLE, b -> b.durationMs(ms));
    }

    // ------------------------------------------------------------------ errors

    @Override
    public void trackError(Throwable t, String where) {
        if (t == null || killed || Boolean.TRUE.equals(IN_ERROR.get())) {
            return;
        }
        IN_ERROR.set(Boolean.TRUE);
        try {
            if (!settings.isErrorsEnabled()) {
                return;
            }
            ErrorSignature sig = ErrorSignature.of(t);
            recordError(sig.errorCode(), sig.detail(), sig.hash(), where);
        } catch (Throwable ignored) {
            // never let telemetry throw, least of all from an error path
        } finally {
            IN_ERROR.remove();
        }
    }

    @Override
    public void trackError(String errorCode, String where) {
        if (killed || Boolean.TRUE.equals(IN_ERROR.get())) {
            return;
        }
        IN_ERROR.set(Boolean.TRUE);
        try {
            if (!settings.isErrorsEnabled()) {
                return;
            }
            String code = TelemetrySanitizer.errorCode(errorCode);
            if (code == null) {
                code = "unknown";
            }
            String w = TelemetrySanitizer.where(where);
            recordError(code, null, TelemetrySanitizer.hash16(code + "|" + w), where);
        } catch (Throwable ignored) {
            // never let telemetry throw
        } finally {
            IN_ERROR.remove();
        }
    }

    private void recordError(String code, String detail, String hash, String where) {
        AtomicInteger previous = errorRepeats.putIfAbsent(hash, new AtomicInteger());
        if (previous != null) {
            previous.incrementAndGet(); // attached as meta.repeat at the next flush
            return;
        }
        if (errorEvents.incrementAndGet() > MAX_ERRORS_PER_SESSION) {
            return;
        }
        TelemetryEvent.Builder b = TelemetryEvent.builder("error", TelemetryEvent.Category.ERROR)
                .status(TelemetryEvent.Status.ERROR)
                .errorCode(code)
                .errorDetail(detail)
                .errorHash(hash);
        String w = TelemetrySanitizer.where(where);
        if (!w.isEmpty()) {
            b.meta("where", w);
        }
        enqueue(b.build());
    }

    /** Folds the per-hash repeat counters into the still-queued error events of this session. */
    private void applyRepeats() {
        for (Map.Entry<String, AtomicInteger> e : errorRepeats.entrySet()) {
            int n = e.getValue().getAndSet(0);
            if (n <= 0) {
                continue;
            }
            String hash = e.getKey();
            queue.update(q -> sessionId.equals(q.sessionId())
                            && q.event().category() == TelemetryEvent.Category.ERROR
                            && hash.equals(q.event().errorHash()),
                    ev -> {
                        Object prev = ev.meta() == null ? null : ev.meta().get("repeat");
                        int base = prev instanceof Number num ? num.intValue() : 0;
                        return ev.toBuilder().meta("repeat", base + n).build();
                    });
            // If the event was already sent, the repeats are simply dropped.
        }
    }

    private void enqueue(TelemetryEvent event) {
        boolean isError = event.category() == TelemetryEvent.Category.ERROR;
        boolean enabled = isError ? safe(settings::isErrorsEnabled, false) : safe(settings::isUsageEnabled, false);
        if (!enabled) {
            return;
        }
        String version = environment.appVersion();
        runInBackground(() -> queue.add(sessionId, version, event));
    }

    // ------------------------------------------------------------------ error report

    @Override
    public CompletableFuture<ErrorReportResult> sendErrorReport(String description, String contact,
                                                                Throwable throwableOrNull,
                                                                boolean includeTechnicalDetails) {
        if (killed) {
            return CompletableFuture.completedFuture(ErrorReportResult.failure(
                    "Error reporting is disabled in this build (development or test run)."));
        }
        if (description == null || description.isBlank()) {
            return CompletableFuture.completedFuture(ErrorReportResult.failure("Please describe the problem."));
        }
        final String body;
        try {
            body = buildErrorReport(description, contact, throwableOrNull, includeTechnicalDetails).toString();
        } catch (Throwable t) {
            return CompletableFuture.completedFuture(ErrorReportResult.failure("The report could not be prepared."));
        }
        CompletableFuture<ErrorReportResult> result = new CompletableFuture<>();
        Runnable task = () -> result.complete(postErrorReport(body));
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            result.complete(ErrorReportResult.failure("The application is shutting down."));
        }
        return result;
    }

    private ErrorReportResult postErrorReport(String body) {
        try {
            ConnectionService.HttpPostResult r = poster.post(
                    TelemetrySender.errorReportUri(endpoint()), body, REPORT_TIMEOUT);
            int status = r.status();
            if (status >= 200 && status < 300) {
                String id = null;
                try {
                    JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                    id = o.has("feedback_id") && !o.get("feedback_id").isJsonNull()
                            ? o.get("feedback_id").getAsString() : null;
                } catch (RuntimeException ignored) {
                    // body is optional for the user
                }
                return new ErrorReportResult(true, status, id, "Thank you! Your report was sent.");
            }
            String msg = switch (status) {
                case 400 -> "The server rejected the report as invalid.";
                case 413 -> "The report is too large.";
                case 429 -> "Too many reports were sent today. Please try again tomorrow.";
                default -> "The server could not accept the report right now (HTTP " + status
                        + "). Please try again later.";
            };
            return new ErrorReportResult(false, status, null, msg);
        } catch (IOException | RuntimeException e) {
            logger.debug("Error report failed: {}", e.toString());
            return ErrorReportResult.failure(
                    "The report server could not be reached. Check your internet connection and proxy "
                            + "settings (Settings ▸ HTTP Proxy), then try again.");
        }
    }

    @Override
    public String previewErrorReport(String description, String contact, Throwable throwableOrNull,
                                     boolean includeTechnicalDetails) {
        try {
            return PRETTY.toJson(buildErrorReport(description, contact, throwableOrNull, includeTechnicalDetails));
        } catch (Throwable t) {
            return "{}";
        }
    }

    /** Builds the {@code POST /v1/error-report} body. */
    JsonObject buildErrorReport(String description, String contact, Throwable t, boolean includeTechnicalDetails) {
        JsonObject o = new JsonObject();
        o.addProperty("install_id", getInstallId());
        o.addProperty("app_version", environment.appVersion());
        o.addProperty("os_name", environment.osName());
        if (includeTechnicalDetails && t != null) {
            ErrorSignature sig = ErrorSignature.of(t, 40, 8000);
            o.addProperty("error_code", sig.errorCode());
            o.addProperty("error_hash", sig.hash());
            o.addProperty("stack_signature", sig.detail());
        }
        o.addProperty("description", TelemetrySanitizer.truncate(description == null ? "" : description.strip(), 4000));
        if (contact != null && !contact.isBlank()) {
            o.addProperty("contact", TelemetrySanitizer.truncate(contact.strip(), 200));
        }
        return o;
    }

    // ------------------------------------------------------------------ toggles

    @Override
    public boolean isUsageEnabled() {
        return safe(settings::isUsageEnabled, false);
    }

    @Override
    public void setUsageEnabled(boolean enabled) {
        settings.setUsageEnabled(enabled);
        if (!enabled) {
            runInBackground(() -> queue.removeIf(q -> q.event().category() != TelemetryEvent.Category.ERROR));
        }
    }

    @Override
    public boolean isErrorReportingEnabled() {
        return safe(settings::isErrorsEnabled, false);
    }

    @Override
    public void setErrorReportingEnabled(boolean enabled) {
        settings.setErrorsEnabled(enabled);
        if (!enabled) {
            runInBackground(() -> queue.removeIf(q -> q.event().category() == TelemetryEvent.Category.ERROR));
        }
    }

    @Override
    public boolean isNoticeShown() {
        return safe(settings::isNoticeShown, false);
    }

    @Override
    public void markNoticeShown() {
        settings.setNoticeShown(true);
        flushAsync();
    }

    // ------------------------------------------------------------------ sending

    @Override
    public void flushAsync() {
        if (killed || !flushPending.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                flushPending.set(false);
                flushNow(FLUSH_TIMEOUT);
            });
        } catch (RejectedExecutionException e) {
            flushPending.set(false);
        }
    }

    /** Blocking flush on the calling thread (used by the worker and tests). */
    TelemetrySender.FlushResult flushNow(Duration timeout) {
        try {
            applyRepeats();
            return sender.flush(timeout);
        } catch (Throwable t) {
            logger.debug("Telemetry flush failed: {}", t.toString());
            return new TelemetrySender.FlushResult(TelemetrySender.Outcome.RETRY_LATER, 0, 0, 0);
        }
    }

    @Override
    public void shutdown(Duration timeout) {
        Duration limit = timeout == null ? Duration.ofSeconds(3) : timeout;
        try {
            if (!killed && !executor.isShutdown()) {
                Future<?> f = executor.submit(() -> flushNow(limit));
                f.get(limit.toMillis() + 250, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            logger.debug("Telemetry shutdown flush incomplete: {}", e.toString());
        } finally {
            executor.shutdown();
        }
    }

    /** Waits until all previously submitted background work is done (tests). */
    void awaitIdle() {
        try {
            executor.submit(() -> { }).get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // best effort
        }
    }

    private void runInBackground(Runnable r) {
        try {
            executor.execute(() -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    logger.debug("Telemetry background task failed: {}", t.toString());
                }
            });
        } catch (RejectedExecutionException e) {
            // executor already shut down (late error during exit) — do it inline
            try {
                r.run();
            } catch (Throwable ignored) {
                // ignore
            }
        }
    }

    // ------------------------------------------------------------------ ids & preview

    @Override
    public String getInstallId() {
        return ensureInstallId();
    }

    private synchronized String ensureInstallId() {
        String id = safe(settings::getInstallId, null);
        if (isUuid(id)) {
            return id;
        }
        String created = UUID.randomUUID().toString();
        try {
            settings.setInstallId(created);
        } catch (Throwable t) {
            logger.debug("Could not persist telemetry install id: {}", t.toString());
        }
        firstRun = true;
        return created;
    }

    @Override
    public synchronized String resetInstallId() {
        String created = UUID.randomUUID().toString();
        settings.setInstallId(created);
        return created;
    }

    @Override
    public String previewPayload() {
        try {
            List<TelemetryEvent> sample = new ArrayList<>();
            for (TelemetryQueue.QueuedEvent q : queue.snapshot()) {
                sample.add(q.event());
                if (sample.size() >= 5) {
                    break;
                }
            }
            if (sample.isEmpty()) {
                sample.add(TelemetryEvent.builder("app_start", TelemetryEvent.Category.LIFECYCLE)
                        .firstRun(false).build());
                sample.add(TelemetryEvent.builder("validate", TelemetryEvent.Category.ACTION)
                        .docKind(DocKind.XML).fileCount(1).inputBytes(12345).errorCount(0).durationMs(87)
                        .meta("mode", "text").build());
            }
            return PRETTY.toJson(TelemetrySender.buildEnvelope(getInstallId(), sessionId,
                    environment.appVersion(), environment, sample));
        } catch (Throwable t) {
            return "{}";
        }
    }

    private static boolean isUuid(String s) {
        if (s == null) {
            return false;
        }
        try {
            UUID.fromString(s);
            return s.length() == 36;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static <T> T safe(java.util.function.Supplier<T> supplier, T fallback) {
        try {
            T v = supplier.get();
            return v != null ? v : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
