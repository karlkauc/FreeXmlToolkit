package org.fxt.freexmltoolkit.service.telemetry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.UsageTrackingService;

/**
 * One-call facade for usage events at UI hook points: every method records the anonymous
 * telemetry event ({@link Telemetry#get()}) <b>and</b> updates the local usage statistics
 * ({@link UsageTrackingService}, shown on the Welcome page).
 *
 * <p>All methods are cheap, never block, never throw and may be called from any thread.
 * Arguments are enums, counts, sizes and durations only — never file names, paths, query
 * text or document content (see {@link TelemetryService}'s privacy contract).
 *
 * <p>Durations are passed as a {@link System#nanoTime()} start value; {@code 0} means "not
 * measured".
 */
public final class UsageEvents {

    /** {@code meta.source} values for {@link #fileOpened}. */
    public static final String SOURCE_FILE = "file";
    public static final String SOURCE_URL = "url";
    public static final String SOURCE_NEW = "new";
    public static final String SOURCE_GENERATED = "generated";

    /** {@code meta.result} values for {@link #updateCheck}. */
    public static final String UPDATE_UP_TO_DATE = "up_to_date";
    public static final String UPDATE_AVAILABLE = "update_available";
    public static final String UPDATE_ERROR = "error";

    /** Live (typing/watch-triggered) runs are recorded at most once per event type and interval. */
    static final long LIVE_INTERVAL_NANOS = 5L * 60 * 1_000_000_000L;

    private static final Supplier<UsageTrackingService> REGISTRY_SINK =
            () -> ServiceRegistry.get(UsageTrackingService.class);

    private static volatile Supplier<UsageTrackingService> localSink = REGISTRY_SINK;
    private static final Set<String> seenActivities = ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicLong> lastLiveRun = new ConcurrentHashMap<>();

    private UsageEvents() {
    }

    // ------------------------------------------------------------------ files / navigation

    /**
     * A document was opened. Generated documents (reports, generated XSD/samples) only
     * reach telemetry, not the local "files opened" counter.
     *
     * @param kind       document kind (may be null)
     * @param inputBytes size in bytes, negative when unknown
     * @param source     one of the {@code SOURCE_*} constants
     */
    public static void fileOpened(DocKind kind, long inputBytes, String source) {
        action("file_open", b -> {
            b.docKind(kind).meta("source", source);
            if (inputBytes >= 0) {
                b.inputBytes(inputBytes);
            }
        });
        if (!SOURCE_GENERATED.equals(source)) {
            local(UsageTrackingService::trackFileOpened);
        }
    }

    /** Documents were saved ({@code fileCount} > 1 for "Save All"; 0 is ignored). */
    public static void fileSaved(DocKind kind, int fileCount) {
        if (fileCount <= 0) {
            return;
        }
        action("file_save", b -> b.docKind(kind).fileCount(fileCount));
    }

    /** The active document switched view mode ({@code text|tree|graphic|preview}). */
    public static void viewModeChanged(String mode, DocKind kind) {
        send("view_mode", TelemetryEvent.Category.NAVIGATION, b -> b.docKind(kind).meta("mode", mode));
        if (kind == DocKind.XSD && ("tree".equals(mode) || "graphic".equals(mode))) {
            local(s -> s.trackFeatureUsed("xsd_visualization"));
        }
    }

    /** An activity (side panel) was opened; recorded once per activity per session. */
    public static void activityOpened(String activityId) {
        if (activityId == null || !seenActivities.add(activityId)) {
            return;
        }
        send("activity_open", TelemetryEvent.Category.NAVIGATION, b -> b.meta("activity", activityId));
        if ("favorites".equals(activityId)) {
            local(s -> s.trackFeatureUsed("favorites_system"));
        }
    }

    // ------------------------------------------------------------------ validation

    /**
     * Classifies what a validation checked against, for {@code meta.schema}.
     *
     * @return {@code json | wellformed | xsd | schematron | xsd_schematron}
     */
    public static String schemaKind(boolean json, boolean hasSchema, boolean hasSchematron) {
        if (json) {
            return hasSchema ? "json" : "wellformed";
        }
        if (hasSchema && hasSchematron) {
            return "xsd_schematron";
        }
        return hasSchema ? "xsd" : hasSchematron ? "schematron" : "wellformed";
    }

    /**
     * A single document was validated. Finding problems is a successful run
     * ({@code failed=false}, {@code errorCount>0}); {@code failed} means the validation
     * itself crashed.
     *
     * @param schema     a {@link #schemaKind} value
     * @param live       triggered by typing (throttled) rather than an explicit user action
     */
    public static void validated(String schema, DocKind kind, int errorCount, long startNanos,
                                 boolean failed, boolean live) {
        if (live && !liveRunDue("validate")) {
            return;
        }
        action("validate", b -> {
            b.docKind(kind).errorCount(errorCount).status(status(!failed)).meta("schema", schema);
            duration(b, startNanos);
            if (live) {
                b.meta("trigger", "live");
            }
        });
        if (failed) {
            return;
        }
        local(s -> {
            s.trackFileValidation(errorCount);
            if (schema != null && schema.startsWith("xsd")) {
                s.trackFeatureUsed("xsd_validation");
            }
            if (schema != null && schema.endsWith("schematron")) {
                s.trackSchematronValidation();
            }
        });
    }

    /** A batch of files was validated. */
    public static void batchValidated(String schema, int fileCount, int errorCount, long startNanos,
                                      boolean cancelled) {
        action("validate_batch", b -> {
            b.fileCount(fileCount).errorCount(errorCount).meta("schema", schema)
                    .status(cancelled ? TelemetryEvent.Status.CANCELLED : TelemetryEvent.Status.OK);
            duration(b, startNanos);
        });
        local(s -> {
            s.trackFeatureUsed("batch_validation");
            s.trackFileValidation(errorCount);
            if (schema != null && schema.endsWith("schematron")) {
                s.trackSchematronValidation();
            }
        });
    }

    /**
     * The Schematron checker (lint of a Schematron document itself) ran. Locally this only
     * marks the Schematron feature as used — it is not an instance validation.
     *
     * @param issueCount detected issues (errors, warnings and infos)
     */
    public static void schematronChecked(int issueCount, long startNanos) {
        action("schematron_check", b -> {
            b.docKind(DocKind.SCHEMATRON).errorCount(issueCount);
            duration(b, startNanos);
        });
        local(s -> s.trackFeatureUsed("schematron_validation"));
    }

    // ------------------------------------------------------------------ transform / query

    /** An XSLT transformation ran ({@code fileCount} &gt; 1 for batch runs, else 1). */
    public static void xsltTransformed(int fileCount, long startNanos, boolean ok) {
        xsltTransformed(fileCount, startNanos, ok, false);
    }

    /**
     * An XSLT transformation ran.
     *
     * @param live triggered by live preview / stylesheet watch (throttled) rather than the user
     */
    public static void xsltTransformed(int fileCount, long startNanos, boolean ok, boolean live) {
        if (live && !liveRunDue("xslt_transform")) {
            return;
        }
        action("xslt_transform", b -> {
            b.docKind(DocKind.XML).status(status(ok)).meta("engine", "saxon");
            if (fileCount > 1) {
                b.fileCount(fileCount);
            }
            if (live) {
                b.meta("trigger", "live");
            }
            duration(b, startNanos);
        });
        if (ok) {
            local(UsageTrackingService::trackTransformation);
        }
    }

    /**
     * A multi-file XSLT / XQuery batch ran (one event for the whole batch).
     *
     * @param failedCount files whose transformation failed ({@code error_count})
     */
    public static void batchTransformed(boolean xquery, int fileCount, int failedCount, long startNanos) {
        action(xquery ? "xquery" : "xslt_transform", b -> {
            b.fileCount(fileCount).errorCount(failedCount).meta("engine", "saxon").meta("batch", true)
                    .status(fileCount > 0 && failedCount >= fileCount
                            ? TelemetryEvent.Status.ERROR : TelemetryEvent.Status.OK);
            duration(b, startNanos);
        });
        if (failedCount < fileCount) {
            local(xquery ? UsageTrackingService::trackXQueryExecution : UsageTrackingService::trackTransformation);
        }
    }

    /** An XQuery ran. */
    public static void xqueryExecuted(long startNanos, boolean ok) {
        action("xquery", b -> {
            b.docKind(DocKind.XML).status(status(ok)).meta("engine", "saxon");
            duration(b, startNanos);
        });
        if (ok) {
            local(UsageTrackingService::trackXQueryExecution);
        }
    }

    /** An XPath expression was evaluated. */
    public static void xpathExecuted(long startNanos, boolean ok) {
        action("xpath", b -> {
            b.docKind(DocKind.XML).status(status(ok)).meta("engine", "saxon");
            duration(b, startNanos);
        });
        if (ok) {
            local(UsageTrackingService::trackXPathQuery);
        }
    }

    /** A JSONPath expression was evaluated (telemetry only; no local counter exists). */
    public static void jsonPathExecuted(long startNanos, boolean ok) {
        action("jsonpath", b -> {
            b.docKind(DocKind.JSON).status(status(ok));
            duration(b, startNanos);
        });
    }

    /** An XProc pipeline ran. */
    public static void xprocExecuted(long startNanos, boolean ok) {
        action("xproc", b -> {
            b.status(status(ok)).meta("engine", "calabash");
            duration(b, startNanos);
        });
    }

    // ------------------------------------------------------------------ tools

    /** A PDF was rendered with FOP. */
    public static void pdfGenerated(long startNanos, boolean ok) {
        action("fop_pdf", b -> {
            b.status(status(ok));
            duration(b, startNanos);
        });
        if (ok) {
            local(UsageTrackingService::trackPdfGeneration);
        }
    }

    /** A document was signed. */
    public static void signed(long startNanos, boolean ok) {
        action("sign", b -> {
            b.docKind(DocKind.XML).status(status(ok));
            duration(b, startNanos);
        });
        if (ok) {
            local(s -> s.trackSignatureOperation(true));
        }
    }

    /**
     * A signature was verified.
     *
     * @param mode    {@code basic | details | trust}
     * @param outcome fixed outcome code ({@code valid}, {@code invalid}, {@code untrusted},
     *                {@code no_signature}, {@code report} (details only), …); {@code error} means
     *                the check itself failed. Everything but {@code valid}/{@code report} counts
     *                as {@code error_count=1}
     */
    public static void signatureVerified(String mode, String outcome, long startNanos) {
        boolean ok = !"error".equals(outcome);
        action("verify_signature", b -> {
            b.docKind(DocKind.XML).status(status(ok)).errorCount("valid".equals(outcome) || "report".equals(outcome) ? 0 : 1)
                    .meta("mode", mode).meta("outcome", outcome);
            duration(b, startNanos);
        });
        if (ok) {
            local(s -> s.trackSignatureOperation(false));
        }
    }

    /** A signing certificate was created. */
    public static void certificateCreated(boolean ok) {
        action("create_certificate", b -> b.status(status(ok)));
        if (ok) {
            local(s -> s.trackFeatureUsed("digital_signature"));
        }
    }

    /**
     * Schema documentation was generated.
     *
     * @param format {@code html|pdf|word} (normalized to lower case)
     * @param status {@code OK}, {@code ERROR} or {@code CANCELLED}
     */
    public static void schemaDocGenerated(String format, long startNanos, TelemetryEvent.Status status) {
        action("schema_doc", b -> {
            b.docKind(DocKind.XSD).status(status)
                    .meta("format", format != null ? format.toLowerCase(java.util.Locale.ROOT) : null);
            duration(b, startNanos);
        });
        if (status == TelemetryEvent.Status.OK) {
            local(s -> s.trackFeatureUsed("xsd_documentation"));
        }
    }

    /** XSD(s) were generated from XML instance(s). */
    public static void xsdGenerated(int fileCount, long startNanos, boolean ok) {
        action("xsd_generate", b -> {
            b.docKind(DocKind.XML).fileCount(fileCount).status(status(ok));
            duration(b, startNanos);
        });
        if (ok) {
            local(UsageTrackingService::trackSchemaGeneration);
        }
    }

    /** The active document was pretty-printed. */
    public static void formatted(DocKind kind, boolean ok) {
        action("format", b -> b.docKind(kind).status(status(ok)));
        if (ok) {
            local(UsageTrackingService::trackFormatting);
        }
    }

    /**
     * Sample XML instance(s) were generated from a schema.
     *
     * @param mode {@code basic | profiled}
     */
    public static void sampleGenerated(String mode, int fileCount, long startNanos, boolean ok) {
        action("sample_generate", b -> {
            b.docKind(DocKind.XSD).fileCount(fileCount).status(status(ok)).meta("mode", mode);
            duration(b, startNanos);
        });
    }

    /** A Schematron Quick Fix (SQF) was applied (counts as one corrected error locally). */
    public static void quickFixApplied() {
        action("quick_fix", b -> b.docKind(DocKind.XML));
        local(s -> s.trackErrorCorrected(1));
    }

    /**
     * An update check finished.
     *
     * @param trigger {@code startup | manual}
     * @param result  one of the {@code UPDATE_*} constants
     */
    public static void updateCheck(String trigger, String result) {
        action("update_check", b -> b.meta("result", result).meta("trigger", trigger)
                .status(UPDATE_ERROR.equals(result) ? TelemetryEvent.Status.ERROR : TelemetryEvent.Status.OK));
    }

    // ------------------------------------------------------------------ internals

    private static TelemetryEvent.Status status(boolean ok) {
        return ok ? TelemetryEvent.Status.OK : TelemetryEvent.Status.ERROR;
    }

    private static void duration(TelemetryEvent.Builder b, long startNanos) {
        if (startNanos != 0) {
            b.durationSinceNanos(startNanos);
        }
    }

    private static boolean liveRunDue(String key) {
        AtomicLong lastRun = lastLiveRun.computeIfAbsent(key, k -> new AtomicLong(Long.MIN_VALUE));
        long now = System.nanoTime();
        long last = lastRun.get();
        return (last == Long.MIN_VALUE || now - last >= LIVE_INTERVAL_NANOS)
                && lastRun.compareAndSet(last, now);
    }

    private static void action(String eventType, Consumer<TelemetryEvent.Builder> customizer) {
        send(eventType, TelemetryEvent.Category.ACTION, customizer);
    }

    private static void send(String eventType, TelemetryEvent.Category category,
                             Consumer<TelemetryEvent.Builder> customizer) {
        try {
            Telemetry.get().track(eventType, category, customizer);
        } catch (Throwable ignored) {
            // telemetry must never disturb the caller
        }
    }

    private static void local(Consumer<UsageTrackingService> call) {
        try {
            UsageTrackingService service = localSink.get();
            if (service != null) {
                call.accept(service);
            }
        } catch (Throwable ignored) {
            // local statistics unavailable (e.g. tests without a registry)
        }
    }

    /** Replaces the local statistics sink; {@code null} restores the registry lookup (tests). */
    static void setLocalSink(Supplier<UsageTrackingService> sink) {
        localSink = sink != null ? sink : REGISTRY_SINK;
    }

    /** Clears per-session state (activity de-duplication, live-run throttle) (tests). */
    static void resetSession() {
        seenActivities.clear();
        lastLiveRun.clear();
    }
}
