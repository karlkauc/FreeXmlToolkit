package org.fxt.freexmltoolkit.service.telemetry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.UsageTrackingService;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

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
    private static final AtomicInteger sessionFilesOpened = new AtomicInteger();
    private static final AtomicInteger sessionActions = new AtomicInteger();
    private static final AtomicInteger sessionMaxTabs = new AtomicInteger();
    /** Last UI context (for {@code ui_stall} events): current activity, view mode, doc kind. */
    private static volatile String currentActivity;
    private static volatile String currentViewMode;
    private static volatile DocKind currentDocKind;
    /** Measures the active text document (for {@code ui_stall}); null when none is open. */
    private static volatile Supplier<DocShape> currentDocShape;

    /**
     * Size and line shape of the active text document — only counts, never content.
     *
     * @param chars      document length in characters
     * @param lines      number of lines (paragraphs)
     * @param maxLineLen length of the longest line
     */
    public record DocShape(long chars, int lines, int maxLineLen) {
    }

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
        fileOpened(kind, inputBytes, source, 0);
    }

    /**
     * A document was opened.
     *
     * @param startNanos {@link System#nanoTime()} when opening started; the duration runs until
     *                   the document is shown and editable ({@code 0} = not measured)
     */
    public static void fileOpened(DocKind kind, long inputBytes, String source, long startNanos) {
        sessionFilesOpened.incrementAndGet();
        action("file_open", b -> {
            b.docKind(kind).meta("source", source);
            if (inputBytes >= 0) {
                b.inputBytes(inputBytes);
            }
            duration(b, startNanos);
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
        viewModeChanged(mode, kind, -1, 0);
    }

    /**
     * The active document switched view mode.
     *
     * @param inputBytes document size (negative = unknown)
     * @param startNanos when the switch started; the duration covers building the view
     *                   ({@code 0} = not measured)
     */
    public static void viewModeChanged(String mode, DocKind kind, long inputBytes, long startNanos) {
        currentViewMode = mode;
        currentDocKind = kind;
        send("view_mode", TelemetryEvent.Category.NAVIGATION, b -> {
            b.docKind(kind).meta("mode", mode);
            if (inputBytes >= 0) {
                b.inputBytes(inputBytes);
            }
            duration(b, startNanos);
        });
        if (kind == DocKind.XSD && ("tree".equals(mode) || "graphic".equals(mode))) {
            local(s -> s.trackFeatureUsed("xsd_visualization"));
        }
    }

    /** An activity (side panel) was opened; recorded once per activity per session. */
    public static void activityOpened(String activityId) {
        if (activityId != null) {
            currentActivity = activityId;
        }
        if (activityId == null || !seenActivities.add(activityId)) {
            return;
        }
        send("activity_open", TelemetryEvent.Category.NAVIGATION, b -> b.meta("activity", activityId));
        if ("favorites".equals(activityId)) {
            local(s -> s.trackFeatureUsed("favorites_system"));
        }
    }

    // ------------------------------------------------------------------ entry points

    /** {@code meta.via} of {@link #command}: editor toolbar button. */
    public static final String VIA_TOOLBAR = "toolbar";
    /** {@code meta.via} of {@link #command}: keyboard shortcut. */
    public static final String VIA_SHORTCUT = "shortcut";
    /** {@code meta.via} of {@link #command}: Welcome page card. */
    public static final String VIA_WELCOME = "welcome";
    /** {@code meta.via} of {@link #command}: files dropped onto the window. */
    public static final String VIA_DROP = "drop";

    /**
     * A shell command was invoked; tells how users reach features (toolbar vs. keyboard).
     *
     * @param command fixed command id, e.g. {@code validate}, {@code format}; for shortcuts the
     *                normalized key combination, e.g. {@code mod+s}, {@code f8}
     * @param via     one of the {@code VIA_*} constants
     */
    public static void command(String command, String via) {
        if (command == null || command.isBlank()) {
            return;
        }
        send("ui_command", TelemetryEvent.Category.ACTION, b -> b.meta("command", command).meta("via", via));
    }

    /**
     * A side-panel action row was clicked.
     *
     * @param actionId the stable row id, e.g. {@code schema-tool-analysis}; its prefix names the panel
     */
    public static void panelAction(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return;
        }
        send("panel_action", TelemetryEvent.Category.ACTION, b -> b.meta("action", actionId));
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
        validated(schema, kind, -1, errorCount, startNanos, failed, live);
    }

    /**
     * A single document was validated.
     *
     * @param inputChars document size in characters (negative = unknown)
     */
    public static void validated(String schema, DocKind kind, long inputChars, int errorCount, long startNanos,
                                 boolean failed, boolean live) {
        if (live && !liveRunDue("validate")) {
            return;
        }
        action("validate", b -> {
            b.docKind(kind).errorCount(errorCount).status(status(!failed)).meta("schema", schema);
            if (inputChars >= 0) {
                b.inputBytes(inputChars);
            }
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
        xsltTransformed(fileCount, startNanos, ok, live, null, null);
    }

    /**
     * An XSLT transformation ran; a failed run carries the processor's error classification.
     *
     * @param errorCode processor error code of a failed run, e.g. {@code XTSE0010} (nullable;
     *                  a fixed code, never the message text)
     * @param phase     failure phase {@code compile | input | runtime} (nullable)
     */
    public static void xsltTransformed(int fileCount, long startNanos, boolean ok, boolean live,
                                       String errorCode, String phase) {
        xsltTransformed(fileCount, startNanos, ok, live, errorCode, phase, -1);
    }

    /**
     * An XSLT transformation ran. A failure in the {@code compile} or {@code input} phase is
     * the user's stylesheet or source being broken, not the app, and is recorded as
     * {@code invalid_input}; only {@code runtime} (or unclassified) failures count as
     * {@code error}.
     *
     * @param inputBytes size of the source document (negative = unknown)
     */
    public static void xsltTransformed(int fileCount, long startNanos, boolean ok, boolean live,
                                       String errorCode, String phase, long inputBytes) {
        if (live && !liveRunDue("xslt_transform")) {
            return;
        }
        action("xslt_transform", b -> {
            b.docKind(DocKind.XML).status(xsltStatus(ok, phase)).meta("engine", "saxon");
            if (!ok) {
                b.errorCode(errorCode).meta("phase", phase);
            }
            if (inputBytes >= 0) {
                b.inputBytes(inputBytes);
            }
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
        xqueryExecuted(startNanos, ok ? null : QueryFailure.UNKNOWN);
    }

    /**
     * An XQuery ran.
     *
     * @param failure why it failed, or {@code null} when it succeeded
     */
    public static void xqueryExecuted(long startNanos, QueryFailure failure) {
        action("xquery", b -> {
            b.docKind(DocKind.XML).meta("engine", "saxon");
            outcome(b, failure);
            duration(b, startNanos);
        });
        if (failure == null) {
            local(UsageTrackingService::trackXQueryExecution);
        }
    }

    /** An XPath expression was evaluated. */
    public static void xpathExecuted(long startNanos, boolean ok) {
        xpathExecuted(startNanos, ok ? null : QueryFailure.UNKNOWN);
    }

    /**
     * An XPath expression was evaluated.
     *
     * @param failure why it failed, or {@code null} when it succeeded
     */
    public static void xpathExecuted(long startNanos, QueryFailure failure) {
        action("xpath", b -> {
            b.docKind(DocKind.XML).meta("engine", "saxon");
            outcome(b, failure);
            duration(b, startNanos);
        });
        if (failure == null) {
            local(UsageTrackingService::trackXPathQuery);
        }
    }

    /** A JSONPath expression was evaluated (telemetry only; no local counter exists). */
    public static void jsonPathExecuted(long startNanos, boolean ok) {
        jsonPathExecuted(startNanos, ok ? null : QueryFailure.UNKNOWN);
    }

    /**
     * A JSONPath expression was evaluated (telemetry only; no local counter exists).
     *
     * @param failure why it failed, or {@code null} when it succeeded
     */
    public static void jsonPathExecuted(long startNanos, QueryFailure failure) {
        action("jsonpath", b -> {
            b.docKind(DocKind.JSON);
            outcome(b, failure);
            duration(b, startNanos);
        });
    }

    /** An XProc pipeline ran. */
    public static void xprocExecuted(long startNanos, boolean ok) {
        xprocExecuted(startNanos, ok ? null : QueryFailure.UNKNOWN);
    }

    /**
     * An XProc pipeline ran.
     *
     * @param failure why it failed, or {@code null} when it succeeded
     */
    public static void xprocExecuted(long startNanos, QueryFailure failure) {
        action("xproc", b -> {
            b.meta("engine", "calabash");
            outcome(b, failure);
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

    /**
     * The Schema Analysis tool ran.
     *
     * @param inputChars schema size in characters (negative = unknown)
     * @param status     {@code OK}, {@code ERROR} or {@code CANCELLED}
     */
    public static void schemaAnalyzed(long inputChars, long startNanos, TelemetryEvent.Status status) {
        action("schema_analysis", b -> {
            b.docKind(DocKind.XSD).status(status);
            if (inputChars >= 0) {
                b.inputBytes(inputChars);
            }
            duration(b, startNanos);
        });
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
     * Schema auto-detection finished for an opened document (XML family or JSON).
     *
     * @param source how the schema was found: {@code none | declared | library | catalog | manual}
     * @param ok     false when a schema was referenced but could not be loaded
     */
    public static void schemaBound(DocKind kind, String source, boolean ok, long startNanos) {
        action("schema_bind", b -> {
            b.docKind(kind).status(status(ok)).meta("source", source);
            duration(b, startNanos);
        });
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

    // ------------------------------------------------------------------ session summary

    /** The number of open editor tabs changed (tracks the session maximum). */
    public static void openTabsChanged(int openTabs) {
        sessionMaxTabs.accumulateAndGet(openTabs, Math::max);
    }

    /**
     * Customizer for the {@code app_exit} event: session counters ({@code files_opened},
     * {@code actions}, {@code activities}, {@code max_tabs}) and heap usage in MB, rounded
     * to 64 MB ({@code heap_peak_mb}, {@code heap_max_mb}).
     */
    public static Consumer<TelemetryEvent.Builder> sessionSummary() {
        int files = sessionFilesOpened.get();
        int actions = sessionActions.get();
        int activities = seenActivities.size();
        int maxTabs = sessionMaxTabs.get();
        long peakMb = heapPeakMb();
        long maxMb = roundMb(Runtime.getRuntime().maxMemory());
        return b -> {
            b.meta("files_opened", files).meta("actions", actions).meta("activities", activities)
                    .meta("max_tabs", maxTabs);
            if (peakMb > 0) {
                b.meta("heap_peak_mb", peakMb);
            }
            if (maxMb > 0) {
                b.meta("heap_max_mb", maxMb);
            }
        };
    }

    private static long heapPeakMb() {
        try {
            long peak = 0;
            for (java.lang.management.MemoryPoolMXBean pool
                    : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
                if (pool.getType() == java.lang.management.MemoryType.HEAP && pool.getPeakUsage() != null) {
                    peak += pool.getPeakUsage().getUsed();
                }
            }
            return roundMb(peak);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Bytes to MB, rounded to the nearest 64 MB (coarse on purpose). */
    static long roundMb(long bytes) {
        if (bytes <= 0 || bytes == Long.MAX_VALUE) {
            return -1;
        }
        long mb = bytes / (1024 * 1024);
        return Math.max(64, Math.round(mb / 64.0) * 64);
    }

    // ------------------------------------------------------------------ UI context

    /**
     * @return the last known UI context as flat meta ({@code activity}, {@code view_mode});
     *         entries are omitted when unknown
     */
    static Map<String, String> uiContext() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        String activity = currentActivity;
        String mode = currentViewMode;
        if (activity != null) {
            m.put("activity", activity);
        }
        if (mode != null) {
            m.put("view_mode", mode);
        }
        return m;
    }

    /** The active editor tab changed (UI context for {@code ui_stall}; no event is sent). */
    public static void activeDocumentChanged(DocKind kind, String viewMode) {
        activeDocumentChanged(kind, viewMode, null);
    }

    /**
     * The active editor tab changed (UI context for {@code ui_stall}; no event is sent).
     *
     * @param shape measures the active document on demand — called on the FX thread, only
     *              when a stall is reported (nullable: unknown / no text document)
     */
    public static void activeDocumentChanged(DocKind kind, String viewMode, Supplier<DocShape> shape) {
        currentDocKind = kind;
        currentViewMode = viewMode;
        currentDocShape = shape;
    }

    /** @return the shape of the active document, or null when unknown or not measurable */
    static DocShape activeDocShape() {
        Supplier<DocShape> shape = currentDocShape;
        if (shape == null) {
            return null;
        }
        try {
            return shape.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Coarse size class of a line length ({@code <1k | <10k | <100k | >=100k}). */
    static String lineLengthBucket(int length) {
        if (length < 1_000) {
            return "<1k";
        }
        if (length < 10_000) {
            return "<10k";
        }
        return length < 100_000 ? "<100k" : ">=100k";
    }

    /** @return the kind of the active document, or null */
    static DocKind activeDocKind() {
        return currentDocKind;
    }

    // ------------------------------------------------------------------ internals

    private static TelemetryEvent.Status status(boolean ok) {
        return ok ? TelemetryEvent.Status.OK : TelemetryEvent.Status.ERROR;
    }

    /** Compile/input-phase XSLT failures are the user's input, not an app failure. */
    static TelemetryEvent.Status xsltStatus(boolean ok, String phase) {
        if (ok) {
            return TelemetryEvent.Status.OK;
        }
        return XsltTransformationResult.PHASE_COMPILE.equals(phase) || XsltTransformationResult.PHASE_INPUT.equals(phase)
                ? TelemetryEvent.Status.INVALID_INPUT : TelemetryEvent.Status.ERROR;
    }

    /** Sets status and, for a failed run, the error fields of a query-style action. */
    private static void outcome(TelemetryEvent.Builder b, QueryFailure failure) {
        if (failure == null) {
            b.status(TelemetryEvent.Status.OK);
            return;
        }
        b.status(failure.status()).errorCode(failure.errorCode());
        if (failure.signature() != null) {
            b.error(failure.signature());
        }
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
        sessionActions.incrementAndGet();
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
        sessionFilesOpened.set(0);
        sessionActions.set(0);
        sessionMaxTabs.set(0);
        currentActivity = null;
        currentViewMode = null;
        currentDocKind = null;
        currentDocShape = null;
    }
}
