# Canvas XSD Editor in the Unified Shell — Improvement Plan

**Date:** 2026-05-31
**Branch:** `feature/ui-rebuild-unified-editor`
**Status:** embedding done (v1); improvements proposed

## 1. Background

The unified shell originally rendered the XSD **Graphic** view with a simplified
scene-graph renderer (`controls/shell/schema/XsdGraphicView`) that embedded a
schema-preview *grid*. Per the product decision (2026-05-31):

- The **grid** belongs to **XML instances**, not the XSD schema — now delivered as
  the **Grid** view mode (`ViewMode.GRID`) backed by the Canvas `XmlCanvasView`
  (`controls/shell/editor/XmlGridView`).
- The **XSD** gets its **own dedicated Canvas diagram editor** — the real, fully
  editable `controls/v2/view/XsdGraphView`, now embedded as the **Graphic** view.

This revises decision **D2** (which had replaced the Canvas `XsdGraphView` with a
new virtualized renderer). We deliberately reinstated the Canvas editor for full
feature fidelity, and accept its known performance trade-off for now — to be
addressed by this plan.

## 2. Current state (v1, what shipped)

- `XsdGraphView` now has a context-adopting constructor `XsdGraphView(XsdEditorContext)`
  and `selectModelNode(XsdNode)`. The shell builds it over the tab's existing
  `XsdEditorContext`, so **graphical edits, inspector edits and undo/redo share one
  command stack and selection model**.
- Internal/graphical edits (drag, right-click, keyboard) **round-trip to the editor
  text** via a `XsdSchema` `PropertyChangeListener` → re-serialize → `setText`.
- The view's **own embedded properties panel is suppressed** (`hideEmbeddedPropertiesPanel()`);
  the shell inspector dock is the single source of properties.
- Selection in the diagram mirrors into the shell inspector via `activeSelectedNode`.
- Tests: `EditorHostCanvasGraphicTest`, `EditorHostGraphicEditTest` (round-trip delete),
  `EditorHostXmlGridTest`; visual smoke confirmed (`/tmp/fxt_smoke/04_schema_graphic.png`,
  `xml_grid.png`).

## 3. Known issues / gaps

| # | Issue | Impact | Severity |
|---|-------|--------|----------|
| I1 | **Full Canvas redraw on every model change** (`redraw()` clears + repaints all visible nodes; `layoutNode()`/`calculateCanvasBounds()` are O(n)). | Lag on large schemas (1000+ nodes), and on rapid edits (typing a name). | High |
| I2 | **Round-trip not debounced**: the schema `PropertyChange` listener re-serializes the *whole* schema on *every* property event; a single edit fires several events → several serializations + `setText`s. | Wasted CPU; potential editor flicker on big files. | Medium |
| I3 | **Tree↔Graphic context rebuild**: switching `TEXT→GRAPHIC` re-parses a new context; if edits were made in `TREE` first and the user then enters `GRAPHIC`, the Graphic view is rebuilt over the current model but the **undo history can be lost** at that boundary. | Surprising undo behaviour in mixed-mode editing. | Medium |
| I4 | **`selectModelNode` only finds materialized nodes**: collapsed / lazy subtrees aren't expanded-to-reveal, so a programmatic reveal can silently no-op. | "Find usage"/reveal into Graphic may not scroll to the node. | Low |
| I5 | **Toolbar overlap**: `XsdGraphView` keeps its own toolbar (Expand/Collapse/Fit/Zoom + "XSD Editor V2 — Graphical View" label). It's useful but partly duplicates shell chrome and shows a dev-ish label. | Minor visual redundancy. | Low |
| I6 | **No incremental serialization**: round-trip always serializes the entire schema, even for a one-attribute change. | Couples cost to file size. | Medium |
| I7 | **Scene-graph `XsdGraphicView` + `SchemaGridModel` now unused** by the shell (still covered by their own tests). | Dead-ish code to retire in Phase 10c. | Cleanup |

## 4. Improvement plan (prioritized)

### P1 — Debounce + coalesce the round-trip (addresses I2, partially I1/I6)
- Coalesce schema-change events into a single `Platform.runLater` round-trip per pulse
  (a `pending` flag), so one user edit serializes once.
- Add a short debounce (≈150 ms, matching the view's existing redraw debounce) for
  burst edits (e.g. typing).
- **Acceptance:** editing a name in a 1000-node schema serializes once per commit, not
  per keystroke event; no visible text flicker.

### P2 — Unify the editor context across modes (addresses I3)
- Build the `XsdEditorContext` once per document (lazily on first structured view) and
  **share it across Tree and Graphic**, instead of re-parsing on each `TEXT→structured`
  entry. Re-parse only when the text actually changed since the last parse (track a hash).
- **Acceptance:** edit in Tree, switch to Graphic, `undo()` reverts the Tree edit;
  switch back, redo works.

### P3 — Reveal-and-select for collapsed nodes (addresses I4)
- In `selectModelNode`, walk the model ancestry and expand the corresponding visual
  nodes (load lazy children) before selecting; then `scrollTo` the card.
- **Acceptance:** "Find usage" / type reveal scrolls to and highlights a node inside a
  collapsed subtree.

### P4 — Incremental redraw (addresses I1)
- Replace full-canvas repaint with dirty-region repaint: track changed `VisualNode`s and
  repaint only their bounds (+ connectors). Keep viewport culling.
- Alternatively, evaluate a hybrid: keep Canvas for connectors, promote node bodies to
  cached `WritableImage` tiles.
- **Acceptance:** edit latency on a 2000-node schema is dominated by serialization
  (see P1/P6), not by repaint; measured redraw < 16 ms for a single-node change.

### P5 — Toolbar integration (addresses I5)
- Hide the internal "XSD Editor V2 — Graphical View" label; optionally lift Zoom /
  Fit / Expand-Collapse into the shell's view toolbar for one consistent control strip,
  or keep a slim graph-only toolbar. Decide with the Figma owner.
- **Acceptance:** no dev-ish labels; one obvious place for zoom/fit.

### P6 — Incremental serialization (addresses I6) — optional / stretch
- Teach `XsdSerializer` (or a thin diff layer) to update only the changed subtree's text
  region instead of re-emitting the whole document.
- **Acceptance:** round-trip cost is ~constant w.r.t. document size for a local edit.

### P7 — Retire unused renderer (addresses I7) — Phase 10c
- After parity is confirmed in real use, delete `controls/shell/schema/XsdGraphicView`
  and `SchemaGridModel` (and their tests), since the shell no longer uses them.

## 5. Sequencing

1. **P1** (debounce) and **P2** (shared context) — biggest correctness/feel wins, low risk.
2. **P3** (reveal) — small, user-visible.
3. **P5** (toolbar) — cosmetic, needs design sign-off.
4. **P4** (incremental redraw) — larger; do once P1/P2 land and we can measure.
5. **P6**/**P7** — stretch / cleanup.

## 6. Test strategy

- Keep the TDD pattern: a failing TestFX/unit test per improvement before the change.
- Perf checks via the existing `XsdGraphView` performance test harness
  (`XmlCanvasViewPerformanceTest` is the analogue for the grid).
- Re-run the snapshot smoke (`FXT_SHELL_SNAPSHOT=true … AppSmokeTourTest`) after P4/P5.
