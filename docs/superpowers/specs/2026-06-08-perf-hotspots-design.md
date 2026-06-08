# Performance Hotspots — Design

**Date:** 2026-06-08
**Branch:** `feature/ui-rebuild-unified-editor`
**Status:** Approved design — ready for implementation plan

## Context

The three originally-documented perf hotspots (XsdGraphView full redraw, FOP on
the FX thread, PDF-preview-rasterizes-all-pages) were found to be **already
fixed** during the rebuild (incremental region repaint; FOP on
`FxtGui.executorService`; lazy per-page PDF rendering). An empirical profiling
harness on real worst-case inputs (`FundsXML_428.xsd` = 2.8 MB / 43k lines and
`FundsXML_428.xml` = 1.8 MB / 35k lines, a validating pair already in
`src/test/resources/`) found two **genuine remaining** hotspots:

| # | Operation | Median | Cause |
|---|-----------|-------:|-------|
| 1 | `XsdDocumentationService.processXsd` | **~5050 ms** | Re-expands every shared type at every reference → **46,601** entries; per expanded node it recomputes type resolution, doc/annotation extraction, restriction parsing, and identity constraints; plus tens of thousands of param-boxing `logger.debug(...)` calls. Runs when the Documentation sub-tab opens and feeds the inspector schema-adapter. |
| 2 | `ContextAnalyzer.buildXPathContext` | **~20 ms / keystroke** | Allocates `fullText.substring(0, caret)` (up to 1.8 MB) and forward-scans from position 0 on every keystroke → O(caret). On the per-keystroke latency path; noticeable when editing deep in a large file. |

(Validation grammar rebuild ~350 ms is a distant third — out of scope unless
validate-on-type is enabled.)

## Goal

Make `processXsd` and `buildXPathContext` measurably faster on large inputs,
**with byte-identical output** (no behavioral change), and demonstrate the
speed-up with a before/after benchmark.

## Correctness safety net (the linchpin for the risky #1)

Before touching `processXsd`, add an **equivalence test** that is the hard gate
for every #1 optimization:

- Run `processXsd` on `FundsXML_428.xsd`, then serialize the resulting
  `XsdDocumentationData.getExtendedXsdElementMap()` to a canonical string: for
  every XPath (sorted), emit the entry's key fields — `elementName`,
  `elementType`, documentation, restriction info, sample data, identity
  constraints, parent XPath, children XPaths, level.
- Compare this canonical string against a committed **golden file**
  (`src/test/resources/perf/processxsd-golden.txt`). The implementer generates
  the golden from the CURRENT (pre-optimization) code in build-sequence step 1
  and commits it; every #1 optimization must then reproduce it byte-for-byte, or
  the test fails. (If the golden file is absent, the test writes it and fails,
  prompting a review of the first capture — so the baseline is never silently
  overwritten after step 1.)

For #2, an equivalence test asserts the resulting `XPathContext` element stack is
identical before/after for a battery of caret positions over a representative XML
(top, mid, end-of-doc; inside an incomplete tag; right after a comment / CDATA /
self-closing element / closing tag).

Both equivalence tests are written and made to pass on the CURRENT code first
(capturing the golden), so any optimization that changes output fails them.

## Benchmark method (before/after)

Formalize the throwaway profiling harness into a kept, non-gating benchmark test
`PerfBenchmark` (under `src/test/.../perf/`) that, for each hotspot, times the
operation on the FundsXML inputs: 1 warm-up run + N timed runs (N≥5), reports the
**median** in ms, and prints a labeled line (e.g.
`PERF processXsd median=NNNNms over N runs`). It contains no assertions (won't
gate the build). The before/after delta is produced by running it on the current
code (before), applying the fix, and running it again (after); the controller
records both medians and reports the ms + speed-up factor per hotspot.

## Hotspot 1 — `XsdDocumentationService.processXsd` (safe → bigger)

Each step is gated by the #1 equivalence test (output must stay byte-identical).

- **A. Guard hot debug logs.** The per-node `logger.debug(...)` calls with
  parameters (e.g. "Resolved named type '{}' to base type '{}'", "Processing {}
  container with {} children", and the identity-constraint logs) fire tens of
  thousands of times, boxing args each call. Wrap the hot ones in
  `if (logger.isDebugEnabled())` (or remove). Output-neutral.
- **B. Cache type-definition resolution.** `findTypeDefinition(node, typeName)`
  does a DOM scan to resolve a named type to its definition node; the same
  `typeName` always resolves to the same node. Add a `Map<String, Node>`
  memoization (per `processXsd` run, cleared at the start). Output-neutral (type
  definitions are stable within a schema). Avoids the repeated DOM scans across
  the 46k expansions.
- **C. (Only if A+B are insufficient) Memoize per-source-node metadata.** For a
  given DOM `node`, the XPath-independent results — resolved `elementType`,
  `restrictionInfo`, list/union info, assertions, doc/annotations, identity-
  constraint structure — are identical across all of that node's XPath
  expansions. Compute them once per source node (keyed by the DOM `node`) and
  copy into each `XsdExtendedElement`, leaving the XPath/ref/namespace-specific
  fields (`currentXpath`, `parentXpath`, `counter`, `level`, `sourceNamespace`,
  cardinality node, unique-value-substituted sample data) per-visit. Gated
  strictly behind the equivalence test; applied only if A+B do not reach a
  satisfying win.

The expanded-entry COUNT (46k) is unchanged — it IS the output consumed by the
docs export, the inspector schema-adapter, and sample-data generation. The fix
only stops RECOMPUTING node-only work per expansion.

## Hotspot 2 — `ContextAnalyzer.buildXPathContext`

- **Eliminate the per-keystroke `substring(0, caret)` allocation.** Scan
  `fullText` directly with `indexOf('<', pos)` bounded by `caretPosition` (stop
  once the next `<` is at or past the caret), keeping the existing
  incomplete-tag look-ahead into the full text. Same algorithm, same element
  stack, minus the up-to-1.8 MB copy + GC pressure per keystroke.
- **Optional small memo.** Cache the last `(text, caret) → XPathContext`; the
  completion engine may call `analyze` several times for one trigger, so a single
  cached entry helps the popup path. Keyed on the exact text + caret (cleared/
  replaced when either changes). Low-risk; include only if it does not complicate
  the equivalence test.

Gated by the #2 equivalence test (identical `XPathContext` across the caret
battery).

## Testing strategy

- **Equivalence tests** (the correctness gate): `ProcessXsdEquivalenceTest`
  (canonical serialization of the expanded map on FundsXML, golden-compared) and
  `ContextAnalyzerEquivalenceTest` (XPathContext stack identical across a caret
  battery). Both pass on current code first, then must keep passing after each
  optimization.
- **Benchmark**: `PerfBenchmark` (non-gating, prints medians) — run before and
  after.
- **Existing regressions**: the XSD documentation/inspector tests
  (`XsdDocumentationService*`, inspector schema-info tests) and IntelliSense /
  context-analysis tests must continue to pass.
- Mind the documented headless TestFX toolkit-init cascade — these are mostly
  pure-Java (no FX), so verify per class.

## Build sequence

1. **Harness + safety net** — formalize `PerfBenchmark`; write
   `ProcessXsdEquivalenceTest` + `ContextAnalyzerEquivalenceTest`, capture their
   goldens on the current code; run `PerfBenchmark` and record the BEFORE medians.
2. **Hotspot 1** — apply A (guard logs) + B (cache `findTypeDefinition`); confirm
   the equivalence test is green; re-run `PerfBenchmark`. If the win is
   insufficient, apply C (per-node memo) and re-verify.
3. **Hotspot 2** — eliminate the substring allocation (+ optional memo); confirm
   the equivalence test is green; re-run `PerfBenchmark`.
4. **Report** — present the before/after medians (ms + factor) for both hotspots.

## Out of scope

- The three already-fixed hotspots (XsdGraphView / FOP / PDF preview) — no work.
- Validation grammar caching (only relevant with validate-on-type).
- Any change that alters `processXsd` output or `XPathContext` results — the
  equivalence tests forbid it.
- Reducing the 46k expanded-entry count (it is the contract consumed downstream).
