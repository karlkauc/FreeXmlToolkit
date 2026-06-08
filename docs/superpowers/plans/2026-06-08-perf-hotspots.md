# Performance Hotspots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `XsdDocumentationService.processXsd` (~5 s) and `ContextAnalyzer.buildXPathContext` (~20 ms/keystroke) measurably faster on large inputs, with byte-identical output, demonstrated by a before/after benchmark.

**Architecture:** A non-gating `PerfBenchmark` test prints median timings on the real FundsXML inputs (`src/test/resources/FundsXML_428.{xsd,xml}`). Two equivalence tests (a SHA-256 golden over the expanded-element map; a caret-battery over `ContextAnalyzer`) are the hard correctness gate — written/captured on the CURRENT code first, then every optimization must keep them green. Optimizations: guard hot per-node debug logs; memoize node-only metadata (identity constraints + annotations) keyed by the source DOM node; eliminate the per-keystroke `substring(0, caret)` allocation.

**Tech Stack:** Java 25, JUnit 5, Xerces DOM, Log4j2. Pure-Java (no JavaFX) — run with plain `./gradlew test --tests "FQCN"`.

**Spec:** `docs/superpowers/specs/2026-06-08-perf-hotspots-design.md`

---

## Notes for the implementer

- `XsdDocumentationService` exposes its result as the PUBLIC field `xsdDocumentationData` (`XsdDocumentationData`); after `svc.setXsdFilePath(path); svc.processXsd(Boolean.FALSE);`, read `svc.xsdDocumentationData.getExtendedXsdElementMap()` (a `Map<String, XsdExtendedElement>`).
- `XsdExtendedElement` accessors used by the canonical serialization: `getElementName()`, `getElementType()`, `getCurrentXpath()`, `getParentXpath()`, `getLevel()`, `getDisplaySampleData()`, `getChildren()` (`List<String>`), `getDocumentations()` (`List<DocumentationInfo>`), `getRestrictionInfo()` (`RestrictionInfo`, may be null).
- `ContextAnalyzer.analyze(String text, int caretPosition)` returns `XmlContext`; `XmlContext.getXPath()` returns the slash path (e.g. `/a/b/c`) and `getCurrentElement()` the innermost element.
- A throwaway `src/test/java/org/fxt/freexmltoolkit/perf/PerfHotspotHarness.java` already exists (it has an unused `java.io.File` import). Task 1 replaces it with `PerfBenchmark`.

---

## Task 1: Benchmark harness + equivalence goldens (capture BEFORE)

**Files:**
- Delete: `src/test/java/org/fxt/freexmltoolkit/perf/PerfHotspotHarness.java`
- Create: `src/test/java/org/fxt/freexmltoolkit/perf/PerfBenchmark.java`
- Create: `src/test/java/org/fxt/freexmltoolkit/perf/ProcessXsdEquivalenceTest.java`
- Create: `src/test/java/org/fxt/freexmltoolkit/perf/ContextAnalyzerEquivalenceTest.java`

- [ ] **Step 1: Create `PerfBenchmark` (non-gating; prints medians)**

```java
package org.fxt.freexmltoolkit.perf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;

/** Non-gating perf benchmark: prints median timings for the two hotspots on the FundsXML inputs. */
class PerfBenchmark {

    private static final Path BIG_XSD = Path.of("src/test/resources/FundsXML_428.xsd");
    private static final Path BIG_XML = Path.of("src/test/resources/FundsXML_428.xml");
    private static final int RUNS = 5;

    @Test
    void benchmarkHotspots() throws Exception {
        // Hotspot 1: processXsd
        time("processXsd", () -> {
            XsdDocumentationService svc = new XsdDocumentationService();
            svc.setXsdFilePath(BIG_XSD.toAbsolutePath().toString());
            try {
                svc.processXsd(Boolean.FALSE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Hotspot 2: ContextAnalyzer.analyze at end-of-document (worst case)
        String xml = Files.readString(BIG_XML);
        int caret = xml.length();
        time("analyze@eod", () -> ContextAnalyzer.analyze(xml, caret));
    }

    private static void time(String label, Runnable op) {
        op.run(); // warm-up
        List<Long> ms = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            op.run();
            ms.add((System.nanoTime() - t0) / 1_000_000);
        }
        ms.sort(Long::compareTo);
        long median = ms.get(ms.size() / 2);
        System.out.println("PERF " + label + " median=" + median + "ms runs=" + ms);
    }
}
```

- [ ] **Step 2: Delete the old harness and run the benchmark to capture BEFORE numbers**

```bash
git rm src/test/java/org/fxt/freexmltoolkit/perf/PerfHotspotHarness.java
./gradlew perfBenchmark
```
Find the `PERF processXsd median=...` and `PERF analyze@eod median=...` lines in the test output (`build/test-results/test/TEST-...PerfBenchmark.xml`, the `system-out` section, or the console). **Record both numbers — these are the BEFORE baseline** (report them at the end). Expected ballpark: processXsd ~5000 ms, analyze@eod ~20 ms.

- [ ] **Step 3: Create `ProcessXsdEquivalenceTest` (canonical SHA-256 golden)**

```java
package org.fxt.freexmltoolkit.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;

/**
 * Correctness gate for processXsd optimizations: the canonical serialization of the
 * expanded-element map (on FundsXML_428.xsd) must hash to a fixed value. Any change to
 * the produced output changes the hash and fails the test.
 */
class ProcessXsdEquivalenceTest {

    private static final Path BIG_XSD = Path.of("src/test/resources/FundsXML_428.xsd");

    // Captured from the CURRENT (pre-optimization) code in Task 1 step 4. DO NOT edit after capture.
    private static final String EXPECTED_SHA256 = "REPLACE_WITH_CAPTURED_HASH";

    @Test
    void expandedElementMapIsByteIdentical() throws Exception {
        XsdDocumentationService svc = new XsdDocumentationService();
        svc.setXsdFilePath(BIG_XSD.toAbsolutePath().toString());
        svc.processXsd(Boolean.FALSE);

        String canonical = canonicalize(svc.xsdDocumentationData.getExtendedXsdElementMap());
        String actual = sha256(canonical);
        System.out.println("PROCESSXSD_CANONICAL_SHA256=" + actual + " entries="
                + svc.xsdDocumentationData.getExtendedXsdElementMap().size());
        assertEquals(EXPECTED_SHA256, actual,
                "processXsd output changed — an optimization altered the expanded-element map");
    }

    /** Deterministic, order-independent serialization of the entry key fields. */
    static String canonicalize(Map<String, XsdExtendedElement> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, XsdExtendedElement> e : new TreeMap<>(map).entrySet()) {
            XsdExtendedElement x = e.getValue();
            List<String> children = x.getChildren() == null ? List.of() : x.getChildren();
            List<String> sortedChildren = new java.util.ArrayList<>(children);
            java.util.Collections.sort(sortedChildren);
            sb.append(e.getKey()).append('\u0001')
              .append(nz(x.getElementName())).append('\u0001')
              .append(nz(x.getElementType())).append('\u0001')
              .append(nz(x.getParentXpath())).append('\u0001')
              .append(x.getLevel()).append('\u0001')
              .append(nz(x.getDisplaySampleData())).append('\u0001')
              .append(restr(x.getRestrictionInfo())).append('\u0001')
              .append(String.valueOf(x.getDocumentations())).append('\u0001')
              .append(sortedChildren)
              .append('');
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Deterministic serialization of a RestrictionInfo (its facets map order is not guaranteed). */
    private static String restr(XsdExtendedElement.RestrictionInfo r) {
        if (r == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("base=").append(nz(r.base())).append("{");
        java.util.Map<String, java.util.List<String>> facets =
                r.facets() == null ? java.util.Map.of() : r.facets();
        for (String key : new java.util.TreeSet<>(facets.keySet())) {
            java.util.List<String> vals = new java.util.ArrayList<>(facets.get(key));
            java.util.Collections.sort(vals);
            sb.append(key).append('=').append(vals).append(';');
        }
        return sb.append('}').toString();
    }

    private static String sha256(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : d) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
```

- [ ] **Step 4: Capture the golden hash from the current code**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.perf.ProcessXsdEquivalenceTest"`
It FAILS (the placeholder hash mismatches). Read the printed `PROCESSXSD_CANONICAL_SHA256=<hash> entries=<n>` line (expect `entries=` around 46000). Replace `EXPECTED_SHA256`'s value with the captured `<hash>`. Re-run → PASS. This golden now pins the current output; every later optimization must reproduce it.

- [ ] **Step 5: Create `ContextAnalyzerEquivalenceTest` (caret battery, explicit expected stacks)**

```java
package org.fxt.freexmltoolkit.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.junit.jupiter.api.Test;

/** Correctness gate for the buildXPathContext optimization: the XPath must be unchanged. */
class ContextAnalyzerEquivalenceTest {

    private static String xpathAt(String xml, int caret) {
        return ContextAnalyzer.analyze(xml, caret).getXPath();
    }

    @Test
    void xpathStackIsUnchangedAcrossCaretBattery() {
        String xml = "<root>\n  <a>\n    <b>text</b>\n    <c x=\"1\"/>\n  </a>\n  <!-- note -->\n"
                + "  <d><![CDATA[stuff]]></d>\n  <e>\n";

        // caret right after "<b>" opening (inside element b under a under root)
        int inB = xml.indexOf("text");
        assertEquals("/root/a/b", xpathAt(xml, inB));

        // caret after the self-closing <c x="1"/> — back to /root/a
        int afterC = xml.indexOf("/>", xml.indexOf("<c")) + 2;
        assertEquals("/root/a", xpathAt(xml, afterC));

        // caret after </a> — back to /root
        int afterCloseA = xml.indexOf("</a>") + 4;
        assertEquals("/root", xpathAt(xml, afterCloseA));

        // caret after the comment — still /root
        int afterComment = xml.indexOf("-->") + 3;
        assertEquals("/root", xpathAt(xml, afterComment));

        // caret inside <d> after the CDATA — /root/d
        int inD = xml.indexOf("]]>") + 3;
        assertEquals("/root/d", xpathAt(xml, inD));

        // caret at the very end, inside the still-open <e> — /root/e
        assertEquals("/root/e", xpathAt(xml, xml.length()));

        // caret inside an incomplete tag: "<e" with no '>' yet
        String incomplete = "<root>\n  <parent>\n    <chi";
        assertEquals("/root/parent/chi", xpathAt(incomplete, incomplete.length()));
    }
}
```

- [ ] **Step 6: Run it on the current code to confirm the expectations match (golden for #2)**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.perf.ContextAnalyzerEquivalenceTest"`
Expected: PASS. (If any expectation is wrong for the CURRENT code, the current behavior is the golden — adjust the expected string to whatever the current code returns, since the goal is no CHANGE, not a particular value. Note any adjustment.)

- [ ] **Step 7: Commit**

```bash
git add src/test/java/org/fxt/freexmltoolkit/perf/
git commit -m "test(perf): benchmark + equivalence goldens for processXsd and ContextAnalyzer

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Hotspot 1-A — guard hot per-node debug logs

The traversal calls parameterized `logger.debug(...)` per expanded node; even when debug is disabled, each call evaluates its arguments and allocates a varargs array. Guarding the hot ones is output-neutral.

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/XsdDocumentationService.java`

- [ ] **Step 1: Guard the per-node debug calls in the traversal path**

In `XsdDocumentationService.java`, find the `logger.debug("... {}...", ...)` calls inside the per-node traversal methods — at minimum these two confirmed-hot sites, plus any other parameterized `logger.debug` within `traverseNode`, `processElementOrAttribute`, `processComplexContent`, `processAttributes`, and `processIdentityConstraints`:

- `logger.debug("Resolved named type '{}' to base type '{}' via simpleContent extension", typeName, baseType);` (~line 1674)
- `logger.debug("Processing {} container with {} children", elementName, containerChildren.size());` (~line 2461)

Wrap each such call site in a guard so the arguments are not evaluated when debug is off. For the two above:

```java
if (logger.isDebugEnabled()) {
    logger.debug("Resolved named type '{}' to base type '{}' via simpleContent extension",
            typeName, baseType);
}
```

```java
if (logger.isDebugEnabled()) {
    logger.debug("Processing {} container with {} children", elementName, containerChildren.size());
}
```

Apply the same `if (logger.isDebugEnabled()) { ... }` guard to every other parameterized `logger.debug(...)` that sits on the per-node traversal path (grep `logger.debug` within the method bodies of `traverseNode`/`processElementOrAttribute`/`processComplexContent`/`processAttributes`/`processIdentityConstraints` and guard the ones with `{}`/arguments). Do NOT touch non-parameterized one-shot logs outside the traversal (e.g. the schema-file load logs) — only the per-node hot path.

- [ ] **Step 2: Verify output is unchanged**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.perf.ProcessXsdEquivalenceTest"`
Expected: PASS (logging changes can't affect the map → hash unchanged).

- [ ] **Step 3: Measure**

Run: `./gradlew perfBenchmark`
Read the new `PERF processXsd median=...` line; note it (intermediate "after A" number).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/XsdDocumentationService.java
git commit -m "perf(xsd): guard hot per-node debug logs in processXsd traversal

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Hotspot 1-C — memoize node-only metadata (identity constraints + annotations)

For a source DOM node visited at many XPaths, `processIdentityConstraints(node, elem)` and the type's annotation extraction produce identical results every time (they read only the node / its type definition, not the XPath). Memoize them per source node and replay onto each new entry. The equivalence test guarantees output stays identical.

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/XsdDocumentationService.java`

- [ ] **Step 1: Add a per-run node-metadata cache**

Add a field near the other per-run state, and clear it at the start of `processXsd` (find where `processXsd` initializes per-run state — clear the cache there):

```java
// Per-run memo of XPath-independent identity-constraint results, keyed by the source DOM node.
// Repeated expansions of a shared type reuse the cached result instead of re-parsing constraints.
private final java.util.Map<org.w3c.dom.Node, org.fxt.freexmltoolkit.domain.XsdExtendedElement>
        identityConstraintMemo = new java.util.IdentityHashMap<>();
```

In `processXsd`, where per-run state is reset, add:

```java
identityConstraintMemo.clear();
```

- [ ] **Step 2: Memoize the identity-constraint result onto each entry**

`processIdentityConstraints(Node node, XsdExtendedElement elem)` sets identity-constraint-related fields on `elem`. Read its body to identify the field(s) it sets (e.g. an identity-constraint list / key/keyref/unique info). Change the call site in `processElementOrAttribute` (currently `if (!isAttribute && !isContainer) { processIdentityConstraints(node, extendedElem); }`) to reuse a cached "donor" entry when the same source node was already processed:

```java
        if (!isAttribute && !isContainer) {
            XsdExtendedElement donor = identityConstraintMemo.get(node);
            if (donor != null) {
                copyIdentityConstraints(donor, extendedElem);
            } else {
                processIdentityConstraints(node, extendedElem);
                identityConstraintMemo.put(node, extendedElem);
            }
        }
```

Add a private `copyIdentityConstraints(XsdExtendedElement from, XsdExtendedElement to)` that copies exactly the field(s) `processIdentityConstraints` sets (read the method + `XsdExtendedElement` to find the getter/setter pair, e.g. `to.setIdentityConstraints(from.getIdentityConstraints())`). Copy a defensive shallow copy of any mutable collection if needed.

> The identity-constraint fields are XPath-independent (they describe the element's key/keyref/unique definitions, which live on the element/type, not the instance path). The equivalence test is the proof — if any copied field is actually path-dependent, the SHA changes and the test fails; in that case, remove that field from the copy and recompute it per-visit.

- [ ] **Step 3: Verify output is unchanged**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.perf.ProcessXsdEquivalenceTest"`
Expected: PASS. If it FAILS, the memo copied a path-dependent field — narrow `copyIdentityConstraints` to only the genuinely node-only fields (or revert this task and report that identity constraints are not safely memoizable). Do NOT edit the golden hash.

- [ ] **Step 4: Measure**

Run: `./gradlew perfBenchmark`
Read the new `PERF processXsd median=...`; note it ("after A+C" number).

- [ ] **Step 5: (Optional, only if more win is needed) Extend the memo to annotations**

If the processXsd median is still high and the annotation/doc extraction is a measured contributor, apply the SAME pattern to the type-annotation extraction: cache, keyed by the type-definition node, the `getDocumentations()` result that `processAnnotations(getDirectChildElement(typeDefinitionNode, "annotation"), extendedElem)` produces, and copy it onto subsequent entries instead of re-parsing. Gate it behind the equivalence test exactly as in Step 3 (the SHA must stay identical). Only keep this if it passes equivalence AND improves the benchmark; otherwise revert it.

- [ ] **Step 6: Run the existing XSD documentation regressions**

Run: `./gradlew test --tests "*XsdDocumentationServiceTest" --tests "*InspectorXmlSchemaInfoTest" --tests "*SchemaConstraintsTest"`
Expected: PASS (these exercise the documentation data + inspector schema info + identity constraints).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/XsdDocumentationService.java
git commit -m "perf(xsd): memoize node-only identity-constraint metadata across type expansions

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Hotspot 2 — eliminate the per-keystroke substring allocation

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/intellisense/context/ContextAnalyzer.java`

- [ ] **Step 1: Replace the prefix substring + scan with a bounded scan over the full text**

In `ContextAnalyzer.buildXPathContext(String fullText, int caretPosition)`, replace the body (which currently does `String textBeforeCaret = fullText.substring(0, caretPosition);` then loops over `textBeforeCaret`) with a loop that scans `fullText` directly, bounded by `caretPosition`, preserving the exact same element-stack logic and the incomplete-tag look-ahead:

```java
    private static XPathContext buildXPathContext(String fullText, int caretPosition) {
        Stack<String> elementStack = new Stack<>();

        int pos = 0;
        while (pos < caretPosition) {
            int nextOpen = fullText.indexOf('<', pos);
            if (nextOpen == -1 || nextOpen >= caretPosition) {
                break;
            }

            int nextClose = fullText.indexOf('>', nextOpen);
            if (nextClose == -1 || nextClose >= caretPosition) {
                // Incomplete tag — cursor is inside a tag. Look ahead in the full text
                // to get the complete tag name.
                int fullClose = fullText.indexOf('>', nextOpen);
                if (fullClose != -1) {
                    String tag = fullText.substring(nextOpen + 1, fullClose);
                    if (!tag.startsWith("!--") && !tag.startsWith("![CDATA[") && !tag.startsWith("?")) {
                        if (!tag.startsWith("/") && !tag.endsWith("/")) {
                            String elementName = extractElementName(tag);
                            if (elementName != null && !elementName.isEmpty()) {
                                elementStack.push(elementName);
                            }
                        }
                    }
                }
                break;
            }

            String tag = fullText.substring(nextOpen + 1, nextClose);

            // Skip comments, CDATA, processing instructions
            if (tag.startsWith("!--") || tag.startsWith("![CDATA[") || tag.startsWith("?")) {
                pos = nextClose + 1;
                continue;
            }

            if (tag.startsWith("/")) {
                String elementName = tag.substring(1).trim();
                if (!elementStack.isEmpty() && elementStack.peek().equals(elementName)) {
                    elementStack.pop();
                }
            } else if (!tag.endsWith("/")) {
                String elementName = extractElementName(tag);
                if (elementName != null) {
                    elementStack.push(elementName);
                }
            }

            pos = nextClose + 1;
        }

        return new XPathContext(new ArrayList<>(elementStack));
    }
```

The only change vs the original: scan `fullText` with a `pos < caretPosition` bound and treat a `<`/`>` at/after the caret like the previous end-of-prefix / incomplete-tag cases — no `fullText.substring(0, caretPosition)` allocation. The per-tag `substring(nextOpen+1, nextClose)` (small) is unchanged; comment/CDATA/self-closing/closing handling is unchanged.

- [ ] **Step 2: Verify output is unchanged**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.perf.ContextAnalyzerEquivalenceTest"`
Expected: PASS (identical XPath for every caret-battery case). If any case differs, the bound handling is off — align it with the original's incomplete-tag semantics until all cases match.

- [ ] **Step 3: Run the broader IntelliSense/context regressions**

Run: `./gradlew test --tests "*ContextAnalyzer*" --tests "*IntelliSense*" --tests "*XmlContext*"`
Expected: PASS.

- [ ] **Step 4: Measure**

Run: `./gradlew perfBenchmark`
Read the new `PERF analyze@eod median=...`; note it ("#2 after" number).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/intellisense/context/ContextAnalyzer.java
git commit -m "perf(intellisense): scan the full text bounded by the caret (no per-keystroke 1.8MB substring)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Report before/after

- [ ] **Step 1: Re-run the benchmark for the final numbers**

Run: `./gradlew perfBenchmark`
Collect the final `PERF processXsd median=...` and `PERF analyze@eod median=...`.

- [ ] **Step 2: Produce the before/after summary**

Present a table: hotspot · BEFORE median (ms, from Task 1 step 2) · AFTER median (ms) · speed-up factor, for both `processXsd` and `analyze@eod`. Note honestly if a hotspot's win is modest. Confirm both equivalence tests are green (output unchanged).

- [ ] **Step 3: Push**

```bash
git push
```

---

## Self-review checklist

- Spec coverage: benchmark (T1) · equivalence goldens (T1) · #1-A logs (T2) · #1-C memo (T3) · #2 substring (T4) · before/after report (T5). Note: spec "fix B (findTypeDefinition cache)" was dropped because `findTypeDefinition` already resolves named types via `complexTypeMap`/`simpleTypeMap` (O(1)) — no cache to add. The real #1 levers are A + C.
- The equivalence tests are the hard correctness gate; every optimization step re-runs `ProcessXsdEquivalenceTest` / `ContextAnalyzerEquivalenceTest` and must keep them green, and the golden hash is captured once (T1 step 4) and never edited after.
- Honest-outcome clause: if the #1-C memo can't be made output-neutral, T3 is reverted and the win comes from A + #2 only — still a measured, safe improvement.

## Out of scope

- The 3 already-fixed hotspots (XsdGraphView/FOP/PDF preview).
- Validation grammar caching.
- Reducing the 46k expanded-entry count (it is the downstream contract).
