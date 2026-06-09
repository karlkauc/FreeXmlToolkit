# PR: UI rebuild — Unified Editor (Activity-Bar shell)

> Draft PR body for `feature/ui-rebuild-unified-editor` → `main`.
> Companions: [analysis](2026-05-29-ui-rebuild-analysis.md) (A–G + Traceability Matrix + §G.1/G.2 reconciliation),
> [phased plan](2026-05-29-ui-rebuild-plan.md).

## Summary

Rebuilds the FreeXmlToolkit UI from separate per-tool tabs into **one Unified Editor with a VS-Code-style
Activity Bar**, per the Figma mockups. The app now **boots into the new shell**, and **all 50 features of the
Traceability Matrix are reachable** in it — verified by a full-app smoke tour.

The old per-tool tabs remain reachable via the sidebar (the **"legacy bridge"**), so **no feature is lost**.
Legacy code is removed incrementally and only after parity is proven (this PR removes 33 provably-dead classes;
the remaining legacy retirement is staged for Phase 10c).

- **87 commits** (55 `feat:`/`fix:`), 172 files changed.
- New shell: **54 production classes** under `controls/shell/`, **63 test classes / 196 tests** (TestFX + TDD).
- `./gradlew build` is green (spotlessJavaCheck + compile + full suite).

## Approach (decisions D1–D5)

- **D1** Greenfield shell + editor tree; **services and the XSD V2 model are reused, never duplicated**.
- **D2** New virtualized Tree + scene-graph Graphic/Grid renderer (replaces the Canvas `XsdGraphView`).
- **D3** Side-by-side migration; old tabs stay until each activity reaches parity.
- **D4** Single CSS design-token source (`design-tokens.css`); light/dark via a root class swap.
- **D5** **Early cutover + legacy bridge** (added after the smoke test): the shell is the default landing surface
  now, with the legacy tools still reachable, rather than blocking the cutover on 100% parity of every long-tail
  sub-feature.

Every feature was migrated behind a UI-free `*Runner`/helper wrapping an existing service (returning result or
`ERROR: …` strings), kept off the UI thread, and TDD'd (RED→GREEN) before wiring the panel.

## Features migrated (Traceability Matrix — 50/50 reachable)

- **Editor core:** Explorer (workspace tree, Open Editors, Recent), drag-and-drop, New/Open/Save/Save As/Save All,
  Undo/Redo, **Format + Minify**, **Find/Replace** (Ctrl+F/Ctrl+H), syntax highlight/folding/line numbers/status bar,
  schema-aware IntelliSense, file-type-polymorphic editing (XML family + JSON), **welcome empty-state**, **Compare/Diff**.
- **Schema (XSD):** Text/Tree/Graphic+Grid views with **expand/collapse state preserved across edits**; Inspector
  (Node&XPath, Type&Facets, Cardinality, Docs); structured editing via the reused command stack with a shared
  **right-click menu in Tree *and* Graphic**; Type Library + **Find Usage**; Generate XSD (single **+ batch**);
  Flatten; Statistics; **Sample XML (with options)**; **Documentation export (HTML/PDF/Word)**.
- **Validation:** XSD (single + batch), **continuous/debounced validation**, Schematron validation, **Schematron
  tools** (Rule Templates / Tester / Visual Builder), **JSON-Schema validation**, Problems list with jump-to-line.
- **Transform:** XSLT (parameters + output format), **XSLT live preview**, XPath/JSONPath, **XQuery console**,
  saved queries.
- **PDF/FOP:** async PDF generation + **in-app lazy PDF preview**.
- **Signature:** sign, validate, **self-signed certificate creation**, **detailed validation report**.
- **Cross-cutting:** Favorites; Help (About + **auto-update check** + conditional **FundsXML**); Settings
  (**theme + editor + proxy**); spreadsheet **converter (Excel/CSV ↔ XML)**; **Templates**; global shortcuts.

## Code removed / housekeeping

- **Phase 10a:** removed **33 zero-reference dead classes** (orphaned refactor helpers, an unused DI module, and
  legacy panels/services never wired by the bridge or shell) — each proven unused by reference search + a green
  `clean test` (incl. the real-app boot integration tests).
- **`spotlessApply`** run repo-wide so `./gradlew build` (not just `test`) passes the style gate.

## Testing & verification

- TDD throughout; 196 shell tests (TestFX/Monocle headless) plus UI-free runner unit tests.
- **Full-app smoke tour** (`AppSmokeTourTest`, gated by `FXT_SHELL_SNAPSHOT`) boots `main.fxml` + `MainController`
  and screenshots every activity. It caught and fixed a real bug (a Phase-2 placeholder that hid the welcome
  empty-state + toolbar on boot).
- `./gradlew clean build` green: spotlessJavaCheck + compile + full suite.

## Performance

- All loads/transforms/validations run on `FxtGui.executorService`; the UI thread is never blocked.
- Virtualized XSD Tree; lazy scene-graph Graphic; **PDF preview renders one page on demand** (fixes the legacy
  all-pages rasterisation); debounced live validation/preview.

## Honest deviations from the plan

- **Phase 10 reframed** from one big cutover+delete to **staged, parity-gated retirement** (§G.2): the shell is the
  default now (legacy bridge kept); legacy subsystems are deleted per-subsystem once their matrix rows are ✅.
- **Legacy "expert signature validation" was a UI stub** (chain/trust/revocation/timestamp did nothing). This PR
  ships a *real* detailed report (validity + signing-cert details); true chain/trust/revocation/timestamp is
  **net-new security work**, not a migration, and is explicitly out of scope.
- **Graphical Simple/Complex type-editor tabs** (old `XsdGraphView`) are superseded by the shell's native
  Tree/Graphic editing — not ported, not a feature loss.
- Deferred polish: advanced sample-data profiles (per-element rules / named profiles), XQuery result-table view,
  Schematron error-detector/docs, dashboard statistics extras, temp/cache settings, XSLT recent-files, the
  remaining ~27 XSD commands.

## Not in this PR (follow-ups)

- **Phase 10c:** retire each legacy subsystem (controller + `tab_*.fxml` + sidebar button + CSS) once its matrix
  rows are all ✅ and a reference search is clean — each its own revertible commit.
- The deferred polish items above; net-new signature trust validation.

## Known flaky tests (environmental, not regressions)

`ConnectionService 'measure request duration'` (network timing) and a couple of TestFX panel tests
(XPath/Validation) under cold/combined runs — all pass alone and in the warm full suite.

## How to review / verify

```bash
./gradlew clean build          # spotless + compile + full suite (green)
./gradlew run                  # boots into the Unified Shell; legacy tools via the sidebar
FXT_SHELL_SNAPSHOT=true xvfb-run ./gradlew test --tests "*AppSmokeTourTest"   # screenshots → /tmp/fxt_smoke
```

🤖 Generated with [Claude Code](https://claude.com/claude-code)
