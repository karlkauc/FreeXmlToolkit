# FreeXmlToolkit — UI Rebuild: Phased Plan (Deliverable G)

> **Status:** Planning. No production code changed in this phase.
> **Branch:** `feature/ui-rebuild-unified-editor`
> **Date:** 2026-05-29
> **Companion:** [`2026-05-29-ui-rebuild-analysis.md`](2026-05-29-ui-rebuild-analysis.md) (A–F + Traceability Matrix).

## Guiding principles

- **Incremental & always runnable.** After every phase: `./gradlew test` green, `./gradlew run`
  works, app shippable. (Hard constraint.)
- **Side-by-side until parity (D3).** New activity-bar shell is reachable but the old tabs stay
  available until each activity is verified at parity, then old code is removed activity-by-activity.
- **Greenfield shell + editor tree (D1); services + V2 model reused only.**
- **No feature loss (constraint #1).** The Traceability Matrix (analysis §G) is the acceptance
  checklist; old code is deleted only after its replacement is verified and references are gone.
- **Performance & usability first (constraints #4/#5).** Virtualization, async, view-state, keyboard.
- **Conventions:** model edits via Command pattern only; `@FXML` methods `public`; all **app text in
  English**; English JavaDoc; `IconifyIconCoverageTest` must stay green.

## How the side-by-side switch works

- A feature flag / launch entry exposes the **new Unified shell** alongside the existing main window
  during the rebuild (e.g. an "Activity-Bar (preview)" entry), so both run from one build.
- Each phase migrates one (or a few) activities into the new shell. When an activity passes its
  matrix subset + tests + a user checkpoint, the corresponding old tab(s) and dead code are removed.
- Final phase makes the new shell the default and removes the last of the old shell.

---

## Phase 0 — Analysis & branch (this phase) ✅

- Deliverables: analysis doc, this plan, Traceability Matrix, rebuild branch. No app code.
- **Exit:** docs committed on `feature/ui-rebuild-unified-editor`.

---

## Phase 1 — Design tokens & theming foundation (D4)

**Goal:** one source of truth for colors/spacing/radius/typography; light/dark via a root class.

- Create `design-tokens.css` with JavaFX looked-up color vars matching Figma tokens
  (primary `#3B5BDB`, accent `#F08C2E`, neutral/semantic scales, code-syntax colors; dark variants
  per Figma node 35-2). Spacing/radius as constants; typography Inter (UI) + JetBrains Mono (code).
- Add a small `Theme`/`DesignTokens` helper so the future renderer can read the same colors in Java.
- Light/dark = swap one root style class; persist via existing `ui.theme`.
- Do **not** yet delete old per-tool CSS (still used by old tabs).

**Tests/acceptance:** existing UI unchanged visually under "light"; `IconifyIconCoverageTest` green;
`docScreenshots` diff reviewed; theme toggle still works on old shell.
**Rollback:** tokens file is additive; revert the commit.

---

## Phase 2 — Unified shell skeleton + virtualized-renderer spike

**Goal:** the new shell exists and is reachable; de-risk the hardest item early.

- Build the **Unified shell** scene: Activity Bar (Explorer, Favorites, Validation, Transform,
  Schema, PDF/FOP, Signature, Help, Settings) + side-panel host + main host + **Unified Inspector**
  shell + status bar. Wire activity switching (panel swap), theme header, IconifyIcon.
- Reachable via the preview entry; activities are placeholders except Explorer (Phase 3).
- **Spike** the new **virtualized Tree/Graphic renderer** on a representative XSD: prove
  only-visible-node rendering, lazy expansion, incremental subtree update, and an embedded-grid cell
  for repeating same-type nodes. Capture timings vs. budgets (analysis §D.3). Output: a short
  feasibility note appended here; not yet feature-complete.

**Tests/acceptance:** shell opens, activities switch, theme applies; spike meets expand ≤100 ms on
1 000+ node schema; all old features still reachable; tests green.
**Rollback:** preview entry is opt-in; old shell is default.

---

## Phase 3 — Explorer + editor host + Text view (XML/JSON parity)

**Goal:** open/edit/save real files in the new shell, Text mode, with no loss for XML & JSON.

- **Explorer** activity: workspace folder tree + "Open Editors", open/new/save/save-as/save-all,
  recent files, drag-drop, file-type routing (maps DragDropService/PropertiesService).
- **Editor host** (file-type aware) hosting the reused RichTextFX editor managers (syntax/folding/
  status/line numbers/font/find), XML IntelliSense engine re-hosted, JSON editor + JSONPath + tree.
- **Unified Inspector** populated for Text mode (Node&XPath at least) via `XsdEditorContext`/
  `SelectionModel` where applicable.
- Keyboard shortcuts for file ops; large-file thresholds + async carried over.

**Tests/acceptance (matrix #1–11, 49):** open/edit/save XML & JSON; IntelliSense works; large-file
budgets met; TestFX smoke for open→edit→save. **Remove** `XmlEditorSidebar*` only after parity.
**Checkpoint:** user demo/screenshot before Phase 4.

---

## Phase 4 — Schema activity: Tree + Graphic+Grid + Inspector (XSD core)

**Goal:** the critical path — XSD editing in the new virtualized renderer at parity.

- Build the production **virtualized renderer**: Tree mode + Graphic mode with the embedded,
  collapsible XMLSpy-style grid (vertical nesting; repeating same-type nodes → grid). View-state
  (expand/collapse) preserved across edits (matrix #50).
- Wire all **35 commands** + `CommandManager` (undo/redo) through the new renderer; SelectionModel →
  **Unified Inspector** (Node&XPath, Type&Facets [reuse facet logic], Cardinality&Use, Docs&Refs) —
  identical across Text/Tree/Graphic.
- Type Library/Editor + Facets surfaced in the Schema side panel/inspector.

**Tests/acceptance (matrix #12–17, 23, 50):** create/edit/delete/move/rename/duplicate; facets;
type editing; undo/redo; round-trip serialize equals current output (reuse `XsdRoundTripTest`-style
checks); renderer budgets met. **Remove** `XsdGraphView`/`XsdPropertiesPanel`/`FacetsPanel`/type tabs
only after verified. **Checkpoint.**

---

## Phase 5 — Schema activity: analysis / flatten / docs / generator / sample data

**Goal:** the XSD "actions" at parity.

- Schema Analysis, Flatten, Documentation export (HTML/PDF/Word/SVG), Schema Generator (+popup),
  sample/profiled data — surfaced as Schema activity actions; reuse all services; ensure async +
  progress; apply streaming/SAX for read-only stats where beneficial.

**Tests/acceptance (matrix #18–22):** each action produces identical output to today; async, UI
never blocked. **Remove** the four XSD sub-tab controllers/FXML after parity. **Checkpoint.**

---

## Phase 6 — Validation activity (XSD single+batch, Schematron, Problems)

**Goal:** all validation in one activity.

- XSD single (1.0/1.1) + continuous validation; batch (drag-drop, table, Excel report); Schematron
  edit + batch + tools (builder/docs/templates/tester/detector); **Problems panel** with
  jump-to-line; XSD favorites quick-select.

**Tests/acceptance (matrix #24–29, 40):** validation results match today; jump-to-line works; batch
async with progress. **Remove** `XsdValidationController`/`SchematronController` shells +
`tab_validation.fxml`/`tab_schematron.fxml` after parity. **Checkpoint.**

---

## Phase 7 — Transform activity (XSLT viewer + developer + XPath/XQuery)

**Goal:** transformation tooling unified.

- XSLT viewer (transform, HTML/XML/text output, browser/editor open, stats), XSLT Developer (params,
  live preview, output format/encoding/indent, recent), XPath/XQuery console (execute, result table,
  FLWOR, saved queries, examples). **Fix** per-query Processor reuse; **replace** file-watch polling
  with `WatchService`.

**Tests/acceptance (matrix #30–32):** transforms/queries match today; perf improvements verified.
**Remove** `XsltController`/`XsltDeveloperController` shells + FXML after parity. **Checkpoint.**

---

## Phase 8 — PDF/FOP + Signature activities

**Goal:** output + security tooling unified, with the FOP perf fix.

- PDF/FOP: XSL-FO→PDF + metadata; **make generation async** (Task + progress); **lazy per-page**
  preview. Signature: create cert / sign / validate / expert mode.

**Tests/acceptance (matrix #33–38):** PDF + signature flows match today; FOP no longer blocks UI.
**Remove** `FopController`/`SignatureController` shells + FXML after parity. **Checkpoint.**

---

## Phase 9 — Favorites, Help, Settings, Welcome, cross-cutting

**Goal:** finish the periphery and shell-level features.

- Favorites activity (+ inspector star); Help/About (version, docs, update check); Settings
  (interface/theme/proxy/temp/FundsXML/update/cache); Welcome dashboard (no file open); memory
  monitor in status bar; FundsXML conditional placement; global shortcuts + **command palette**
  (usability); drag-drop routing; consolidate executors onto `ThreadPoolManager`.

**Tests/acceptance (matrix #39, 41–48):** all reachable and working; theme/proxy/update verified.
**Checkpoint.**

---

## Phase 10 — Cutover & cleanup

**Goal:** new shell is the only shell; dead code gone.

- Make the Unified shell the **default**; remove the preview entry.
- Remove remaining old shell: `main.fxml` button-nav, `UnifiedEditorController`/`tab_unified_editor`,
  `controls/unified/*`, leftover `tab_*.fxml`/controller shells, superseded per-tool CSS — each
  gated by a static reference search proving no live use (constraint #2/#3).
- Final pass on budgets (analysis §D.3); regenerate docs (`docs-updater`) + screenshots
  (`xvfb-run ./gradlew docScreenshots`).

**Tests/acceptance:** full matrix complete (all ☑); all tests green; `IconifyIconCoverageTest`
green; budgets met; `./gradlew run` verified.
**Deliverable:** PR `feature/ui-rebuild-unified-editor` → `main` with summary (features migrated,
code removed, performance results, deviations from plan).

---

## Per-phase definition of done

1. Reuse existing model/commands/services before building new.
2. Feature implemented/migrated → checked against Figma mockup **and** matrix subset.
3. Tests: unit (model/command/service/adapter) + TestFX smoke for the activity; existing tests stay
   green.
4. Old replaced code removed **only after** parity verified + reference search clean.
5. Build & verify: `./gradlew test`, `./gradlew run` (observe behavior),
   `./gradlew test --tests "*IconifyIconCoverageTest"`.
6. Measure phase performance against budgets.
7. Commit with a clear message; push; **user checkpoint** (screenshot/demo) before the next phase.

## Rollback strategy

- Every phase is its own set of commits on the rebuild branch; the **old shell stays default** until
  Phase 10, so any phase can be reverted without breaking shipping capability.
- Token/CSS changes are additive until Phase 10.
- Old code is deleted only at the end of a phase, after verification — reverting a deletion is a
  single `git revert`.

## Milestone summary

| Phase | Theme | Matrix coverage | Removable after |
|---|---|---|---|
| 1 | Tokens/theme | #44 (foundation) | — |
| 2 | Shell + renderer spike | shell scaffolding | — |
| 3 | Explorer + Text (XML/JSON) | #1–11, 49 | XmlEditorSidebar* |
| 4 | Schema Tree/Graphic+Grid + Inspector | #12–17, 23, 50 | XsdGraphView, props/facets panels, type tabs |
| 5 | Schema actions | #18–22 | 4 XSD sub-tab controllers/FXML |
| 6 | Validation + Schematron | #24–29, 40 | Validation/Schematron shells |
| 7 | Transform | #30–32 | Xslt(/Developer) shells |
| 8 | PDF/FOP + Signature | #33–38 | Fop/Signature shells |
| 9 | Favorites/Help/Settings/Welcome/cross-cut | #39, 41–48 | — |
| 10 | Cutover & cleanup | full matrix ☑ | main.fxml nav, unified-editor, controls/unified/*, leftover tabs/CSS |
