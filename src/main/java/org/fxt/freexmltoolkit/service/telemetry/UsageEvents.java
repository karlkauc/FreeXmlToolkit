package org.fxt.freexmltoolkit.service.telemetry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
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
    /** Highest sampled heap usage of the session in bytes ({@link #sampleHeap}). */
    private static final java.util.concurrent.atomic.AtomicLong sessionHeapPeak = new java.util.concurrent.atomic.AtomicLong();
    /** Last UI context (for {@code ui_stall} events): current activity, view mode, doc kind. */
    private static volatile String currentActivity;
    private static volatile String currentViewMode;
    private static volatile DocKind currentDocKind;
    /** Measures the active text document (for {@code ui_stall}); null when none is open. */
    private static volatile Supplier<DocShape> currentDocShape;
    /** Length of the active text document in characters, cheap and safe off the FX thread; null when none. */
    private static volatile LongSupplier currentDocChars;
    /** The active document (identity only, held weakly); null when none / unknown. */
    private static volatile java.lang.ref.WeakReference<Object> currentDocument;
    /** Documents whose quick fixes were already reported as offered this session (weak keys). */
    private static final Map<Object, Boolean> quickFixOfferedDocs =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** Throttle key when the document is unknown. */
    private static final Object NO_DOCUMENT = new Object();

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
     * Number of elements of an XML-family document for {@code element_count}: a linear scan
     * of the text ({@link ElementCounter}), so call it off the FX thread. Skipped (-1) for
     * JSON and other non-XML kinds, unknown kinds and while telemetry is inactive.
     *
     * @param kind    the document kind (nullable)
     * @param content the document text (nullable)
     * @return the element count, or {@code -1} when not counted
     */
    public static int elementCount(DocKind kind, CharSequence content) {
        if (content == null || !countsElements(kind)) {
            return -1;
        }
        try {
            return Telemetry.get().isActive() ? ElementCounter.countStartTags(content) : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** @return whether {@link #elementCount} counts documents of this kind (the XML family) */
    public static boolean countsElements(DocKind kind) {
        return kind == DocKind.XML || kind == DocKind.XSD || kind == DocKind.XSLT || kind == DocKind.SCHEMATRON;
    }

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
        fileOpened(kind, inputBytes, source, startNanos, -1);
    }

    /**
     * A document was opened.
     *
     * @param elementCount number of elements (see {@link #elementCount}), negative = unknown
     */
    public static void fileOpened(DocKind kind, long inputBytes, String source, long startNanos,
                                  int elementCount) {
        sessionFilesOpened.incrementAndGet();
        action("file_open", b -> {
            b.docKind(kind).meta("source", source);
            if (inputBytes >= 0) {
                b.inputBytes(inputBytes);
            }
            if (elementCount >= 0) {
                b.elementCount(elementCount);
            }
            duration(b, startNanos);
        });
        if (!SOURCE_GENERATED.equals(source)) {
            local(UsageTrackingService::trackFileOpened);
        }
    }

    /**
     * A file was not opened because of its size: the user declined the large-file warning
     * ({@code cancelled}) or it does not fit into the maximum heap ({@code unsupported}).
     *
     * @param refused true when the app refused it, false when the user cancelled
     */
    public static void fileOpenDeclined(DocKind kind, long inputBytes, boolean refused) {
        action("file_open", b -> {
            b.docKind(kind).meta("source", SOURCE_FILE).inputBytes(inputBytes)
                    .status(refused ? TelemetryEvent.Status.UNSUPPORTED : TelemetryEvent.Status.CANCELLED)
                    .meta("reason", "too_large")
                    .meta("heap_max_mb", roundMb(Runtime.getRuntime().maxMemory()));
        });
    }

    /** Documents were saved ({@code fileCount} > 1 for "Save All"; 0 is ignored). */
    public static void fileSaved(DocKind kind, int fileCount) {
        if (fileCount <= 0) {
            return;
        }
        action("file_save", b -> b.docKind(kind).fileCount(fileCount));
    }

    /**
     * A document could not be written.
     *
     * @param failure why (a file the OS refuses — missing folder, locked, read-only — is
     *                {@code invalid_input}; anything else carries the exception class and stack)
     */
    public static void fileSaveFailed(DocKind kind, QueryFailure failure) {
        action("file_save", b -> {
            b.docKind(kind).fileCount(1);
            outcome(b, failure != null ? failure : QueryFailure.UNKNOWN);
        });
    }

    /**
     * A document could not be read.
     *
     * @param inputBytes file size, negative = unknown
     * @param failure    why (a missing or unreadable file is {@code invalid_input}; anything else
     *                   carries the exception class and stack)
     */
    public static void fileOpenFailed(DocKind kind, long inputBytes, String source, QueryFailure failure) {
        action("file_open", b -> {
            b.docKind(kind).meta("source", source);
            if (inputBytes >= 0) {
                b.inputBytes(inputBytes);
            }
            outcome(b, failure != null ? failure : QueryFailure.UNKNOWN);
        });
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
    /** {@code meta.via} of {@link #command}: menu bar item. */
    public static final String VIA_MENU = "menu";
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
                                 QueryFailure failure, boolean live) {
        validated(schema, kind, -1, errorCount, startNanos, failure, live);
    }

    /**
     * A single document was validated.
     *
     * @param inputChars document size in characters (negative = unknown)
     */
    public static void validated(String schema, DocKind kind, long inputChars, int errorCount, long startNanos,
                                 QueryFailure failure, boolean live) {
        validated(schema, kind, inputChars, null, errorCount, startNanos, failure, live);
    }

    /**
     * A single document was validated; size and {@code element_count} are taken from its
     * text. Call it off the FX thread: the element count scans the text (only when the event
     * is actually recorded, and only for the XML family).
     *
     * @param content the validated text (nullable: size and element count unknown)
     * @param failure why the validation itself could not run (a schema that does not load is
     *                {@code invalid_input}; anything else carries the exception class and stack
     *                signature), or {@code null} when it ran — validation problems in the document
     *                are {@code errorCount}, not a failure
     */
    public static void validated(String schema, DocKind kind, CharSequence content, int errorCount,
                                 long startNanos, QueryFailure failure, boolean live) {
        validated(schema, kind, content != null ? content.length() : -1,
                content != null ? () -> elementCount(kind, content) : null,
                errorCount, startNanos, failure, live);
    }

    private static void validated(String schema, DocKind kind, long inputChars, IntSupplier elementCount,
                                  int errorCount, long startNanos, QueryFailure failure, boolean live) {
        boolean failed = failure != null;
        if (live && !failed && !liveRunDue("validate")) {
            return; // a failed live run is never throttled away
        }
        action("validate", b -> {
            b.docKind(kind).errorCount(errorCount).meta("schema", schema);
            outcome(b, failure);
            if (inputChars >= 0) {
                b.inputBytes(inputChars);
            }
            int elements = elementCount != null ? elementCount.getAsInt() : -1;
            if (elements >= 0) {
                b.elementCount(elements);
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
        schematronChecked(issueCount, startNanos, null);
    }

    /**
     * The Schematron rule checker ran over a document.
     *
     * @param failure why the checker itself failed, or {@code null} when it ran ({@code issueCount}
     *                are findings in the document, not failures)
     */
    public static void schematronChecked(int issueCount, long startNanos, QueryFailure failure) {
        action("schematron_check", b -> {
            b.docKind(DocKind.SCHEMATRON).errorCount(issueCount);
            outcome(b, failure);
            duration(b, startNanos);
        });
        if (failure == null) {
            local(s -> s.trackFeatureUsed("schematron_validation"));
        }
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
            b.docKind(DocKind.XML).status(xsltStatus(ok, phase, errorCode)).meta("engine", "saxon");
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
     * An XSLT transformation ran; a failed run is classified by {@code failure} (processor error
     * code and, for an app failure, the stack signature). A failure in the {@code compile} or
     * {@code input} phase is the user's stylesheet or source being broken, not the app.
     *
     * @param phase   failure phase {@code compile | input | runtime} (nullable)
     * @param failure why it failed, or {@code null} when it succeeded
     */
    public static void xsltTransformed(int fileCount, long startNanos, boolean live, String phase,
                                       QueryFailure failure, long inputBytes) {
        if (live && !liveRunDue("xslt_transform")) {
            return;
        }
        boolean ok = failure == null;
        action("xslt_transform", b -> {
            b.docKind(DocKind.XML).meta("engine", "saxon");
            if (ok) {
                b.status(TelemetryEvent.Status.OK);
            } else {
                TelemetryEvent.Status byPhase = xsltStatus(false, phase, failure.errorCode());
                b.status(byPhase == TelemetryEvent.Status.INVALID_INPUT ? byPhase : failure.status())
                        .errorCode(failure.errorCode()).meta("phase", phase);
                if (failure.signature() != null) {
                    b.error(failure.signature());
                }
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

    /**
     * A PDF was rendered with FOP (XSLT to XSL-FO, then FOP to PDF).
     *
     * @param stage   the stage that ran last: {@code transform} or {@code render} (nullable)
     * @param failure why it failed (processor error code or exception class; a broken stylesheet
     *                or FO document counts as {@code invalid_input}), or {@code null} when it succeeded
     */
    public static void pdfGenerated(long startNanos, String stage, QueryFailure failure) {
        action("fop_pdf", b -> {
            outcome(b, failure);
            if (stage != null) {
                b.meta("stage", stage);
            }
            duration(b, startNanos);
        });
        if (failure == null) {
            local(UsageTrackingService::trackPdfGeneration);
        }
    }

    /** A document was signed. */
    public static void signed(long startNanos, boolean ok) {
        signed(startNanos, ok ? null : QueryFailure.UNKNOWN);
    }

    /**
     * A document was signed.
     *
     * @param failure why signing failed (keystore, password, I/O: exception class and stack), or
     *                {@code null} when it succeeded
     */
    public static void signed(long startNanos, QueryFailure failure) {
        action("sign", b -> {
            b.docKind(DocKind.XML);
            outcome(b, failure);
            duration(b, startNanos);
        });
        if (failure == null) {
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
        signatureVerified(mode, outcome, startNanos, "error".equals(outcome) ? QueryFailure.UNKNOWN : null);
    }

    /**
     * A signature was verified; when the check itself failed ({@code outcome=error}),
     * {@code failure} says why (exception class and stack; a document that is not XML is
     * {@code invalid_input}).
     */
    public static void signatureVerified(String mode, String outcome, long startNanos, QueryFailure failure) {
        boolean ok = failure == null && !"error".equals(outcome);
        action("verify_signature", b -> {
            b.docKind(DocKind.XML).errorCount("valid".equals(outcome) || "report".equals(outcome) ? 0 : 1)
                    .meta("mode", mode).meta("outcome", outcome);
            outcome(b, ok ? null : failure != null ? failure : QueryFailure.UNKNOWN);
            duration(b, startNanos);
        });
        if (ok) {
            local(s -> s.trackSignatureOperation(false));
        }
    }

    /** A signing certificate was created. */
    public static void certificateCreated(boolean ok) {
        certificateCreated(ok ? null : QueryFailure.UNKNOWN);
    }

    /** A signing certificate was created; {@code failure} says why it was not (nullable). */
    public static void certificateCreated(QueryFailure failure) {
        action("create_certificate", b -> outcome(b, failure));
        if (failure == null) {
            local(s -> s.trackFeatureUsed("digital_signature"));
        }
    }

    /**
     * Schema documentation was generated.
     *
     * @param format  {@code html|pdf|word} (normalized to lower case)
     * @param failure why it failed (a schema that does not parse counts as {@code invalid_input};
     *                other failures carry the exception class and stack signature), or
     *                {@code null} when it succeeded
     */
    public static void schemaDocGenerated(String format, long startNanos, QueryFailure failure) {
        action("schema_doc", b -> {
            b.docKind(DocKind.XSD).meta("format", normalizedFormat(format));
            outcome(b, failure);
            duration(b, startNanos);
        });
        if (failure == null) {
            local(s -> s.trackFeatureUsed("xsd_documentation"));
        }
    }

    /** The user cancelled a schema documentation run. */
    public static void schemaDocCancelled(String format, long startNanos) {
        action("schema_doc", b -> {
            b.docKind(DocKind.XSD).status(TelemetryEvent.Status.CANCELLED).meta("format", normalizedFormat(format));
            duration(b, startNanos);
        });
    }

    private static String normalizedFormat(String format) {
        return format != null ? format.toLowerCase(java.util.Locale.ROOT) : null;
    }

    /**
     * The Schema Analysis tool ran.
     *
     * @param inputChars schema size in characters (negative = unknown)
     * @param status     {@code OK}, {@code ERROR} or {@code CANCELLED}
     */
    public static void schemaAnalyzed(long inputChars, long startNanos, TelemetryEvent.Status status) {
        if (status == TelemetryEvent.Status.CANCELLED) {
            schemaAnalysisCancelled(inputChars, startNanos);
        } else {
            schemaAnalyzed(inputChars, startNanos, status == TelemetryEvent.Status.OK ? null : QueryFailure.UNKNOWN);
        }
    }

    /**
     * The Schema Analysis tool ran.
     *
     * @param failure why it failed (a schema that does not parse is {@code invalid_input}; anything
     *                else carries the exception class and stack), or {@code null} when it succeeded
     */
    public static void schemaAnalyzed(long inputChars, long startNanos, QueryFailure failure) {
        action("schema_analysis", b -> {
            b.docKind(DocKind.XSD);
            outcome(b, failure);
            if (inputChars >= 0) {
                b.inputBytes(inputChars);
            }
            duration(b, startNanos);
        });
    }

    /** The user cancelled a Schema Analysis run. */
    public static void schemaAnalysisCancelled(long inputChars, long startNanos) {
        action("schema_analysis", b -> {
            b.docKind(DocKind.XSD).status(TelemetryEvent.Status.CANCELLED);
            if (inputChars >= 0) {
                b.inputBytes(inputChars);
            }
            duration(b, startNanos);
        });
    }

    /** XSD(s) were generated from XML instance(s). */
    public static void xsdGenerated(int fileCount, long startNanos, boolean ok) {
        xsdGenerated(fileCount, startNanos, ok ? null : "", false);
    }

    /**
     * XSD(s) were generated from XML instance(s).
     *
     * @param errorCode    fixed identifier of a failure (exception class or {@code not_well_formed});
     *                     null on success, empty for a failure without a code
     * @param invalidInput whether the failure is the user's input ({@code invalid_input})
     */
    public static void xsdGenerated(int fileCount, long startNanos, String errorCode, boolean invalidInput) {
        xsdGenerated(fileCount, startNanos, errorCode == null ? null
                : invalidInput ? QueryFailure.invalidInput(errorCode.isEmpty() ? null : errorCode)
                : new QueryFailure(errorCode.isEmpty() ? null : errorCode, TelemetryEvent.Status.ERROR, null));
    }

    /**
     * XSD(s) were generated from XML instance(s).
     *
     * @param failure why it failed (an instance that is not well-formed is {@code invalid_input};
     *                anything else carries the exception class and stack), or {@code null} when it succeeded
     */
    public static void xsdGenerated(int fileCount, long startNanos, QueryFailure failure) {
        action("xsd_generate", b -> {
            b.docKind(DocKind.XML).fileCount(fileCount);
            outcome(b, failure);
            duration(b, startNanos);
        });
        if (failure == null) {
            local(UsageTrackingService::trackSchemaGeneration);
        }
    }

    /**
     * The spreadsheet converter ran (XML to Excel/CSV or back).
     *
     * @param direction  {@code to_spreadsheet} or {@code to_xml}
     * @param format     {@code xlsx}, {@code xls} or {@code csv}
     * @param status     {@code ok}, {@code invalid_input} (not well-formed), {@code unsupported}
     *                   (too many rows for the format, or too large for the heap) or {@code error}
     * @param inputBytes length of the converted XML in characters; negative when unknown (import)
     * @param errorCode  exception class of a failure; null on success
     */
    public static void spreadsheetConverted(String direction, String format, TelemetryEvent.Status status,
                                            long startNanos, long inputBytes, String errorCode) {
        spreadsheetConverted(direction, format, status, startNanos, inputBytes, errorCode, null);
    }

    /**
     * The spreadsheet converter ran; a failed run carries the exception class and, for an app
     * failure (status {@code error}), the stack signature of {@code error}.
     *
     * @param error the failure (nullable)
     */
    public static void spreadsheetConverted(String direction, String format, TelemetryEvent.Status status,
                                            long startNanos, long inputBytes, Throwable error) {
        spreadsheetConverted(direction, format, status, startNanos, inputBytes,
                error != null ? error.getClass().getSimpleName() : null, error);
    }

    private static void spreadsheetConverted(String direction, String format, TelemetryEvent.Status status,
                                             long startNanos, long inputBytes, String errorCode, Throwable error) {
        action("spreadsheet_convert", b -> {
            b.status(status).meta("direction", direction).meta("format", format);
            if (inputBytes >= 0) {
                b.inputBytes(inputBytes);
            }
            if (errorCode != null) {
                b.errorCode(errorCode);
            }
            if (error != null && status == TelemetryEvent.Status.ERROR) {
                b.error(ErrorSignature.of(error));
                Telemetry.markReported(error);
            }
            duration(b, startNanos);
        });
    }

    /** The active document was pretty-printed. */
    public static void formatted(DocKind kind, boolean ok) {
        formatted(kind, "format", ok ? null : QueryFailure.UNKNOWN);
    }

    /**
     * The active document was rewritten as a whole.
     *
     * @param mode    {@code format} (pretty-print) or {@code minify}
     * @param failure why it failed (a document that does not parse is {@code invalid_input}), or
     *                {@code null} when it succeeded
     */
    public static void formatted(DocKind kind, String mode, QueryFailure failure) {
        action("format", b -> {
            b.docKind(kind).meta("mode", mode);
            outcome(b, failure);
        });
        if (failure == null) {
            local(UsageTrackingService::trackFormatting);
        }
    }

    /** A format / minify run produced nothing to apply (the text changed meanwhile, or the result was empty). */
    public static void formatSkipped(DocKind kind, String mode) {
        action("format", b -> b.docKind(kind).meta("mode", mode).status(TelemetryEvent.Status.CANCELLED));
    }

    /**
     * Sample XML instance(s) were generated from a schema.
     *
     * @param mode {@code basic | profiled}
     */
    public static void sampleGenerated(String mode, int fileCount, long startNanos, boolean ok) {
        sampleGenerated(mode, fileCount, startNanos, ok ? null : QueryFailure.UNKNOWN);
    }

    /**
     * Sample XML instance(s) were generated from a schema.
     *
     * @param failure why it failed (a schema that does not parse is {@code invalid_input}, a
     *                sample beyond the size limits {@code unsupported}), or {@code null} when it succeeded
     */
    public static void sampleGenerated(String mode, int fileCount, long startNanos, QueryFailure failure) {
        action("sample_generate", b -> {
            b.docKind(DocKind.XSD).fileCount(fileCount).meta("mode", mode);
            outcome(b, failure);
            duration(b, startNanos);
        });
    }

    /** {@code meta.via} of {@link #quickFixOffered}: lightbulbs appeared in the editor gutter. */
    public static final String QUICK_FIX_VIA_GUTTER = "gutter";
    /** {@code meta.via} of {@link #quickFixOffered}: the "Quick Fix" menu of a problem row was opened. */
    public static final String QUICK_FIX_VIA_MENU = "menu";

    /**
     * Quick fixes were offered for the active document. Recorded at most once per document per
     * session (the first offer wins), so {@code quick_fix} / {@code quick_fix_offered} reads as
     * an acceptance rate. Not counted as a user action in the session summary.
     *
     * @param count number of offered fixes ({@code meta.count}); 0 or less is ignored
     * @param via   one of the {@code QUICK_FIX_VIA_*} constants
     */
    public static void quickFixOffered(int count, String via) {
        java.lang.ref.WeakReference<Object> ref = currentDocument;
        quickFixOffered(ref != null ? ref.get() : null, currentDocKind, count, via);
    }

    /**
     * Quick fixes were offered for {@code document} (see {@link #quickFixOffered(int, String)}).
     *
     * @param document the document (identity only; nullable: all unknown documents share one slot)
     */
    public static void quickFixOffered(Object document, DocKind kind, int count, String via) {
        if (count <= 0) {
            return;
        }
        Object key = document != null ? document : NO_DOCUMENT;
        if (quickFixOfferedDocs.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        send("quick_fix_offered", TelemetryEvent.Category.ACTION,
                b -> b.docKind(kind != null ? kind : DocKind.XML).meta("count", count).meta("via", via));
    }

    /** A Schematron Quick Fix (SQF) was applied (counts as one corrected error locally). */
    /**
     * A quick fix was chosen but could not be applied.
     *
     * @param failure why: a fix the Schematron's SQF cannot compute for this document is
     *                {@code invalid_input} ({@code SqfExecutionException}); anything else an app failure
     */
    public static void quickFixFailed(QueryFailure failure) {
        action("quick_fix", b -> outcome(b, failure != null ? failure : QueryFailure.UNKNOWN));
    }

    /**
     * A quick fix was chosen but not applied for a reason that is neither success nor failure.
     *
     * @param reason fixed code: {@code no_document}, {@code document_changed}, {@code cancelled}
     */
    public static void quickFixSkipped(String reason) {
        action("quick_fix", b -> b.status(TelemetryEvent.Status.CANCELLED).meta("reason", reason));
    }

    /**
     * A replace over search matches ran.
     *
     * @param kind    {@code xpath} or {@code text}
     * @param scope   {@code document} (the active editor) or {@code files}
     * @param count   replaced matches (or files)
     * @param failure why it failed (an XPath the planner rejects is {@code invalid_input}), or {@code null}
     */
    public static void replaced(String kind, String scope, int count, QueryFailure failure) {
        action("replace", b -> {
            b.meta("kind", kind).meta("scope", scope).errorCount(0).fileCount(Math.max(count, 0));
            outcome(b, failure);
        });
    }

    /**
     * A debugger run (XSLT, XQuery or Schematron) ended.
     *
     * @param language {@code xslt | xquery | schematron}
     * @param stopped  the user stopped the run (status {@code cancelled})
     * @param failure  why the run failed (stylesheet errors are {@code invalid_input}; an engine or
     *                 debugger failure carries class + stack), or {@code null} when it completed
     */
    public static void debugRun(String language, long startNanos, boolean stopped, QueryFailure failure) {
        action("debug_run", b -> {
            b.meta("language", language).meta("stage", "run");
            if (stopped) {
                b.status(TelemetryEvent.Status.CANCELLED);
            } else {
                outcome(b, failure);
            }
            duration(b, startNanos);
        });
        if (!stopped && failure == null) {
            local(s -> s.trackFeatureUsed("xslt_debugger"));
        }
    }

    /** The debugger could not be started (stylesheet or input unreadable, no input document). */
    public static void debugStartFailed(String language, QueryFailure failure) {
        action("debug_run", b -> {
            b.meta("language", language).meta("stage", "start");
            outcome(b, failure != null ? failure : QueryFailure.UNKNOWN);
        });
    }

    /** An XSD was flattened (includes resolved, optional reductions). */
    public static void schemaFlattened(long startNanos, QueryFailure failure) {
        action("xsd_flatten", b -> {
            b.docKind(DocKind.XSD);
            outcome(b, failure);
            duration(b, startNanos);
        });
    }

    /** "Find usages" of a type ran over the active schema. */
    public static void typeUsagesSearched(int usages, long startNanos, QueryFailure failure) {
        action("type_usages", b -> {
            b.docKind(DocKind.XSD).errorCount(0).fileCount(Math.max(usages, 0));
            outcome(b, failure);
            duration(b, startNanos);
        });
    }

    /**
     * A Schema Library action ran.
     *
     * @param action  {@code materialize | open | refresh | import_catalog | reveal}
     * @param failure why it failed (an unavailable remote schema is {@code invalid_input}), or {@code null}
     */
    public static void schemaLibraryAction(String action, QueryFailure failure) {
        action("schema_library", b -> {
            b.meta("action", action);
            outcome(b, failure);
        });
    }

    /**
     * A search over the workspace files ran.
     *
     * @param kind       {@code text | xpath}
     * @param files      files searched
     * @param errorFiles files that could not be read or evaluated
     */
    public static void filesSearched(String kind, int files, int errorFiles, long startNanos, boolean cancelled) {
        action("file_search", b -> {
            b.meta("kind", kind).fileCount(Math.max(files, 0)).errorCount(Math.max(errorFiles, 0))
                    .status(cancelled ? TelemetryEvent.Status.CANCELLED : TelemetryEvent.Status.OK);
            duration(b, startNanos);
        });
    }

    /** A template was rendered into the editor; {@code failure} says why it was not (nullable). */
    public static void templateInserted(QueryFailure failure) {
        action("template_insert", b -> outcome(b, failure));
    }

    public static void quickFixApplied() {
        action("quick_fix", b -> b.docKind(DocKind.XML));
        local(s -> s.trackErrorCorrected(1));
    }

    /**
     * Schema auto-detection finished for an opened document (XML family or JSON).
     *
     * @param source how the schema was found: {@code none | declared | library | catalog | manual};
     *               on failure the source that was tried
     * @param ok     false when a schema was referenced but could not be loaded
     */
    public static void schemaBound(DocKind kind, String source, boolean ok, long startNanos) {
        schemaBound(kind, source, ok, null, startNanos);
    }

    /**
     * Same as {@link #schemaBound(DocKind, String, boolean, long)} with the failure reason.
     *
     * @param failure why the binding failed: {@code unresolved} (reference not resolvable /
     *                reachable) or {@code unloadable} (schema missing or not parsable); null on success
     */
    public static void schemaBound(DocKind kind, String source, boolean ok, String failure, long startNanos) {
        schemaBound(kind, source, ok, failure, null, startNanos);
    }

    /**
     * Same as {@link #schemaBound(DocKind, String, boolean, String, long)} with the kind of the
     * reference that failed.
     *
     * @param refKind {@link #referenceKind} of the declared location (nullable; only sent on failure).
     *                An unresolved {@code relative} reference names a file that does not exist next to the
     *                document - the document's mistake, reported as {@code invalid_input}, not as an error.
     */
    public static void schemaBound(DocKind kind, String source, boolean ok, String failure, String refKind,
                                   long startNanos) {
        boolean missingLocalFile = !ok && "unresolved".equals(failure) && "relative".equals(refKind);
        action("schema_bind", b -> {
            b.docKind(kind).status(missingLocalFile ? TelemetryEvent.Status.INVALID_INPUT : status(ok))
                    .meta("source", source);
            if (!ok && failure != null) {
                b.meta("failure", failure);
            }
            if (!ok && refKind != null) {
                b.meta("ref_kind", refKind);
            }
            duration(b, startNanos);
        });
    }

    /**
     * Classifies a schema reference by its form only - never by its value:
     * {@code http} (http/https/ftp URL), {@code file} (file: URL or absolute path),
     * {@code urn}, {@code relative} (a path relative to the document) or {@code other}.
     *
     * @param location the declared location (nullable)
     * @return the kind, or null for a missing location
     */
    public static String referenceKind(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String l = location.strip().toLowerCase(java.util.Locale.ROOT);
        if (l.startsWith("http://") || l.startsWith("https://") || l.startsWith("ftp://")) {
            return "http";
        }
        if (l.startsWith("file:") || l.startsWith("/") || l.startsWith("\\\\") || l.matches("^[a-z]:[\\\\/].*")) {
            return "file";
        }
        if (l.startsWith("urn:")) {
            return "urn";
        }
        return l.matches("^[a-z][a-z0-9+.-]*:.*") ? "other" : "relative";
    }

    /**
     * An update check finished.
     *
     * @param trigger {@code startup | manual}
     * @param result  one of the {@code UPDATE_*} constants
     */
    public static void updateCheck(String trigger, String result) {
        updateCheck(trigger, result, null);
    }

    /**
     * An update check finished.
     *
     * @param trigger {@code startup | manual}
     * @param result  one of the {@code UPDATE_*} constants
     * @param reason  fixed identifier why a failed check failed (e.g. {@code network},
     *                {@code http_403}); ignored unless {@code result} is {@link #UPDATE_ERROR}
     */
    public static void updateCheck(String trigger, String result, String reason) {
        boolean failed = UPDATE_ERROR.equals(result);
        action("update_check", b -> {
            b.meta("result", result).meta("trigger", trigger)
                    .status(failed ? TelemetryEvent.Status.ERROR : TelemetryEvent.Status.OK);
            if (failed && reason != null) {
                b.meta("reason", reason);
            }
        });
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
        sampleHeap();
        return roundMb(Math.min(sessionHeapPeak.get(), Runtime.getRuntime().maxMemory()));
    }

    /**
     * Records the current heap usage for {@code heap_peak_mb}. Called periodically (stall
     * watchdog tick) and at exit. Summing the pools' individual peaks overstated the peak
     * (they peak at different times; reports showed a peak above the maximum heap).
     */
    static void sampleHeap() {
        try {
            long used = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            sessionHeapPeak.accumulateAndGet(used, Math::max);
        } catch (Throwable ignored) {
            // best effort
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
        activeDocumentChanged(kind, viewMode, shape, null);
    }

    /**
     * The active editor tab changed (UI context for {@code ui_stall} and out-of-memory errors;
     * no event is sent).
     *
     * @param chars length of the active document in characters — called from any thread when an
     *              {@link OutOfMemoryError} is reported, so it must be a cheap read (nullable)
     */
    public static void activeDocumentChanged(DocKind kind, String viewMode, Supplier<DocShape> shape,
                                             LongSupplier chars) {
        activeDocumentChanged(null, kind, viewMode, shape, chars);
    }

    /**
     * The active editor tab changed (UI context; no event is sent).
     *
     * @param document the active document — only its identity is used (throttling
     *                 {@code quick_fix_offered}), held weakly (nullable)
     */
    public static void activeDocumentChanged(Object document, DocKind kind, String viewMode,
                                             Supplier<DocShape> shape, LongSupplier chars) {
        currentDocument = document == null ? null : new java.lang.ref.WeakReference<>(document);
        currentDocKind = kind;
        currentViewMode = viewMode;
        currentDocShape = shape;
        currentDocChars = chars;
    }

    /** @return the length of the active document in characters, or -1 when unknown */
    static long activeDocChars() {
        LongSupplier chars = currentDocChars;
        if (chars == null) {
            return -1;
        }
        try {
            return chars.getAsLong();
        } catch (Throwable t) {
            return -1;
        }
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
        if (length < 0) {
            return "unknown";
        }
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
        return xsltStatus(ok, phase, null);
    }

    /**
     * Like {@link #xsltStatus(boolean, String)}, but a runtime failure whose processor code marks
     * the user's stylesheet or input (e.g. {@code SESU0013}, an unsupported serialization
     * parameter) is {@code invalid_input} as well.
     */
    static TelemetryEvent.Status xsltStatus(boolean ok, String phase, String errorCode) {
        if (ok) {
            return TelemetryEvent.Status.OK;
        }
        if (XsltTransformationResult.PHASE_COMPILE.equals(phase) || XsltTransformationResult.PHASE_INPUT.equals(phase)) {
            return TelemetryEvent.Status.INVALID_INPUT;
        }
        return errorCode != null ? QueryFailure.ofCode(errorCode).status() : TelemetryEvent.Status.ERROR;
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
        sessionHeapPeak.set(0);
        sessionActions.set(0);
        sessionMaxTabs.set(0);
        currentActivity = null;
        currentViewMode = null;
        currentDocKind = null;
        currentDocShape = null;
        currentDocChars = null;
        currentDocument = null;
        quickFixOfferedDocs.clear();
    }
}
