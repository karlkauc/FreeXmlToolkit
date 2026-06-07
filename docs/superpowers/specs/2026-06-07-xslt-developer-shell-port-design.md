# XSLT Developer → Unified Shell Port — Design

**Date:** 2026-06-07
**Branch:** `feature/ui-rebuild-unified-editor`
**Status:** Approved design — ready for implementation plan

## Goal

Port the four advanced features of the legacy **XSLT Developer** subsystem
into the Unified Shell so that `xsltDeveloper` becomes fully redundant and can
be retired (the retirement itself is a separate follow-up, not part of this
spec). The four features:

1. **Interactive debugger** — breakpoints, step into/over/out, variables,
   call stack, watch expressions, gutter markers.
2. **Multi-file batch processing** — run one stylesheet/XQuery over many files.
3. **Performance profiler** — read-only report of timings, sizes, template
   execution metrics.
4. **Debug trace** — read-only capture of template matches and `xsl:message`
   output.

This is the last legacy subsystem; the only one intentionally kept after
Phase 10c retired 11 others, because the shell's Transform panel did not yet
cover these advanced capabilities.

## Background — what already exists

The shell's **Transform panel** (`controls/shell/editor/TransformPanel.java`)
already covers: XSLT 3.0 transform, XQuery, XPath/JSONPath, parameters, output
formats, result Text/Table toggle, recent stylesheets, file-watch, live
preview, transform stats, browser preview, saved queries.

The **service layer is complete** — `XsltTransformationEngine` already provides
`transformWithDebugSession(...)`, profiling (`TransformationProfile`),
`transformXQueryBatch(...)` / `transformXQueryPerFile(...)`, and the debug
listeners (`XsltDebugTraceListener`, `XsltDebugMessageListener`).

The **shell editor already anticipates the debugger**: `XmlCodeEditorV2`
exposes `setExtraGutterFactory(IntFunction<Node>)` + `refreshGutter()` —
JavaDoc: *"Used by the XSLT debugger to draw breakpoint markers and execution
arrows."* XSLT is a first-class `EditorFileType`. `EditorHost` already has
`goToLine(int)`, `getActiveCodeArea()`, and current-line highlight
(`updateCurrentLineStyle`).

**Therefore the gap is almost entirely UI wiring in the shell, not logic.**

## Architecture — reuse map

### Reused unchanged

- **`debugger/` domain:** `DebugSession`, `Breakpoint`, `PausedSnapshot`,
  `DebugStackFrame`, `VariableBinding`, `VariableScope`, `WatchExpression`
  (pure Java, Saxon-coupled; the ReentrantLock/Condition pause logic stays
  untouched).
- **`debugger/ui/BreakpointGutterFactory`** — it is just an `IntFunction<Node>`
  (editor-agnostic RichTextFX paragraph factory) needing a `DebugSession`,
  a file path, and the current line. Reused as-is; it has no legacy-controller
  coupling in its core.
- **`XsltTransformationEngine`** — debug-session transform, profiling, batch
  methods, debug listeners. Already complete.

### New, shell-native (replaces the legacy controller's UI)

- **Four panels** — Breakpoints / Variables / Call Stack / Watch — as slim
  shell components under `controls/shell/editor/debug/`.
- **UI-free runners** in the established shell pattern (cf. `TransformRunner`,
  `SchematronCheckRunner`): `XsltDebugController` (orchestrates DebugSession +
  engine + threading), `BatchTransformRunner`, and a profile-returning overload
  on `TransformRunner`.
- **Result/tool views** opened via `editorHost.openToolTab(...)`:
  `XsltDebugView`, `BatchTransformView`, `ProfileView`, `TraceView`.

### The one new seam (minimal-invasive)

- `EditorView` interface gains
  `default void setExtraGutterFactory(IntFunction<Node>)` (no-op default).
- `XmlEditorView` overrides it to delegate to `XmlCodeEditorV2`.
- `EditorHost` gains `setActiveEditorGutterFactory(IntFunction<Node>)` that
  forwards to the active editor tab; JSON tabs are a no-op.
- Current-line highlight + scroll reuse the existing `goToLine` /
  `getActiveCodeArea`.

## Debugger design

Triggered by a **"Debug"** button in the Transform side panel:

1. **Open XSLT as a document:** the stylesheet currently chosen in the
   Transform panel is opened via `editorHost.openFile(xsltPath)` as an XSLT
   editor document (Text view, gutter available). If already open, that tab is
   focused.
2. **Attach gutter:** a `BreakpointGutterFactory` (bound to the `DebugSession`)
   is attached to the active XSLT tab via
   `editorHost.setActiveEditorGutterFactory(...)`. A gutter click →
   `session.toggleBreakpoint(file, line)` → `refreshGutter()`.
3. **Open the Debug tool tab:** `XsltDebugView` via
   `openToolTab("Debug", "bi-bug", …)` bundles:
   - **Step controls bar** (in the Debug tab, not the side panel):
     Run/Continue · Pause · Step Into · Step Over · Step Out · Stop. Optional
     accelerators mirroring the legacy ones (F7/F10/F9 etc.).
   - **Four panels** (Breakpoints, Variables, Call Stack, Watch) laid out
     compactly in a SplitPane/Accordion.

### Orchestration — `XsltDebugController` (new, UI-free, testable)

- Holds the `DebugSession`; starts the transform via
  `XsltTransformationEngine.transformWithDebugSession(xml, xslt, params, format,
  session)` on `FxtGui.executorService` (never the FX thread).
- Listens to the `DebugSession` `PropertyChangeSupport` for state transitions
  (RUNNING/PAUSED/STOPPED) → `Platform.runLater(...)` updates: current line in
  the editor (`goToLine` + gutter arrow), and Variables/Call Stack/Watch from
  the `PausedSnapshot`.
- Step buttons call `session.stepInto/Over/Out()` / `continue()` / `pause()` /
  `stop()`.

### Inputs

Debug uses the same active XML source + parameters as a normal transform run —
the Transform panel is the single source of truth.

### Watch expressions

XPath evaluated against the current context in the `PausedSnapshot` (Saxon
`XPathCompiler`). Breakpoints and watches persist via `PropertiesService`
(mirroring the legacy `persistBreakpoints` / `persistWatchExpressions`), so they
survive across sessions.

### Cleanup

Closing the Debug tab or pressing "Stop" → `session.stop()`, detach the gutter
factory (`setActiveEditorGutterFactory(null)`), and clear the current-line
highlight.

## Batch processing design

Entry: a **"Batch…"** button in the Transform side panel opens a
`BatchTransformView` via `openToolTab("Batch", "bi-files", …)`.

`BatchTransformView` (new, shell-native):

- **File table** (top): columns Checkbox · Name · Size · Status. Buttons:
  "Add Files…", "Add Directory…", "Remove Selected", "Clear", "Select All".
- **Stylesheet source:** the XSLT/XQuery currently chosen in the Transform
  panel (shown read-only) — one source of truth.
- **Run bar:** "Run Batch", output-format display, "Save All…" (target dir).
- **Result area** (bottom): per-file selection shows that file's result; a
  summary line shows success/error count and total time.

`BatchTransformRunner` (new, UI-free, testable):

- `runXsltBatch(files, xslt, params, format)` and
  `runXQueryBatch(files, xquery, vars, format)` delegate to
  `XsltTransformationEngine.transformXQueryPerFile(...)` / the XSLT batch path;
  return `List<BatchFileResult>` where
  `BatchFileResult(file, output, ok, errorMsg, timeMs)`.
- `writeAll(results, targetDir, namePattern)` writes every result (naming
  scheme like legacy: `<basename>.<ext>`).
- Runs entirely on `FxtGui.executorService`; progress/status pushed to the
  table via `Platform.runLater`.

**Deliberately omitted (YAGNI):** no separate batch parameter editor — batch
uses the parameters from the Transform panel.

## Profiler & trace design

Both are read-only reports from a transform run; the engine already produces
the data. Three independent toggles/buttons in the side panel — **Debug**,
**Profile**, **Trace** — not forced mutually exclusive, but each meaningful on
its own.

### Profiler

- A **"Profile"** toggle in the side panel. When on, a normal transform runs
  with `enableProfiling=true`; on completion a `ProfileView` tool tab opens
  (`openToolTab("Profile", "bi-speedometer2", …)`).
- Content from `TransformationProfile`: compile/transform/serialize times,
  output size, memory (before/after), template execution times + counts (table,
  sortable by time), detected features (XSLT/XQuery, output method).
- No new runner needed — `TransformRunner` gains an overload that additionally
  returns the `XsltTransformationResult` / `TransformationProfile`.

### Debug trace

- A **"Trace"** toggle in the side panel → transform with
  `enableDebugging=true`; afterwards a `TraceView` tool tab opens
  (`openToolTab("Trace", "bi-list-columns", …)`).
- Content from the debug listeners: template matches (pattern/name/line/time),
  `xsl:message` output + warnings, call-stack capture. Table + message list.

### Side-panel growth (the only visible change to the existing panel)

The Transform panel gains a small **"Advanced"** section: button `Debug`,
button `Batch…`, checkbox `Profile`, checkbox `Trace`.

## Testing strategy

TDD in the established shell pattern (UI-free runners, TestFX views):

- **Runner unit tests (UI-free):** `XsltDebugControllerTest` (state machine:
  run → pause@breakpoint → step → variables/call-stack from snapshot → stop),
  `BatchTransformRunnerTest` (multiple files, ok/error mix, `writeAll` naming),
  `TransformRunner` profile-overload (profile fields populated).
- **Editor seam:** `EditorHostGutterTest` — `setActiveEditorGutterFactory`
  attaches a factory to the active XSLT tab; JSON tab = no-op.
- **View tests (TestFX, gated where needed):** `XsltDebugViewTest`,
  `BatchTransformViewTest`, `ProfileViewTest` / `TraceViewTest` — panels render
  snapshot data; step buttons call the right session methods.
- **Domain already covered:** `XsltDebuggerIntegrationTest` /
  `XsltDebugTraceListenerTest` (engine + DebugSession) remain valid and protect
  the reused logic.
- Mind the known toolkit-init / combined-run flakiness patterns from prior
  phases (inspector debounce, FxToolkit init order).

## Build sequence — 5 self-contained increments (each committed + pushed)

1. **Editor seam** — `EditorView.setExtraGutterFactory` +
   `XmlEditorView` delegation + `EditorHost.setActiveEditorGutterFactory`
   (+test). Smallest, foundational.
2. **Debugger** — `XsltDebugController` + 4 shell panels + `XsltDebugView` tool
   tab + gutter wiring + side-panel "Debug" button (+tests). The big piece.
3. **Batch** — `BatchTransformRunner` + `BatchTransformView` + side-panel
   "Batch…" button (+tests).
4. **Profiler + Trace** — `TransformRunner` profile overload +
   `ProfileView` / `TraceView` + the two side-panel checkboxes (+tests).
5. **Parity check + docs** — pull in the XQuery example templates and the XSLT
   version selector as minor follow-ons if still missing; update `docs/`
   (Transform / shell page); verify against the legacy feature list that
   nothing is missing.

The actual retirement of `xsltDeveloper` is the separate follow-up after this
plan completes.

## Out of scope

- Retiring the `xsltDeveloper` subsystem (controller, FXML, debugger package
  deletion, MainController/menu/wiring removal) — separate follow-up.
- Per-file batch parameters.
- JSON debugger gutter (JSON editor lacks `setExtraGutterFactory`; not needed).
- Performance refactors of the engine (e.g. profiling overhead) — the engine is
  reused as-is.
