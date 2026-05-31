# FreeXmlToolkit — UI Rebuild: Analysis (Phase 0)

> **Status:** Analysis & planning phase. No production code changed.
> **Branch:** `feature/ui-rebuild-unified-editor`
> **Date:** 2026-05-29
> **Companion:** [`2026-05-29-ui-rebuild-plan.md`](2026-05-29-ui-rebuild-plan.md) (phase plan / deliverable G)
> **Target design:** Figma "FreeXmlToolkit — UI Modernization", key `oqJVcInD6RgKaQ4dYmMWYh`.

## Goal

Replace the current per-tool, tab-switching UI with **one Unified Editor driven by a
VS-Code-style Activity Bar**, exactly per the Figma mockups, with **zero feature loss**,
**high performance on large files**, and **light/dark theming from a single token source**.

## Approved direction (decisions taken 2026-05-29)

| # | Decision | Choice | Consequence |
|---|----------|--------|-------------|
| D1 | Relationship to existing partial Unified Editor | **Full greenfield rebuild** of shell + editor component tree | Services + V2 model reused only; `UnifiedEditorController` and `controls/unified/*` are *not* carried forward as the base |
| D2 | Tree / Graphic+Grid renderer | **New virtualized renderer** | Only-visible-node rendering, incremental updates; replaces Canvas `XsdGraphView` |
| D3 | Migration sequencing | **Side-by-side until parity** | New shell lives behind existing tabs; old per-tool tabs stay reachable until each activity is verified, then removed activity-by-activity |
| D4 | Theming / tokens | **Single token source (CSS variables)** | One `design-tokens.css` with JavaFX looked-up colors; light/dark = one root style class swap |

These four decisions are the backbone of the phase plan. D1 (greenfield) + D3 (side-by-side)
combine well: new components are built fresh but never block the running app, because the old
tabs remain reachable until each new activity reaches verified parity.

---

# A. Feature Inventory (code-derived)

Organized by the **target activity** that will own the feature. Every row is traceable to a
controller/service/FXML in today's code (full per-feature mapping in §G, the Traceability Matrix).

## A.1 Editor core (file-type-aware center + Text/Tree/Graphic)

| Feature | Today: entry point | Today: controller/method | Service(s) | FXML |
|---|---|---|---|---|
| New / Open / Save / Save As / Save All | toolbar buttons | `XmlUltimateController.newFilePressed/openFile/saveFile/saveAsFile/saveAllFiles` | XmlService, ExportMetadataService, PropertiesService | `tab_xml_ultimate.fxml` |
| Recent files | toolbar MenuButton | `XmlUltimateController.refreshRecentFilesMenu` (async exists-checks) | PropertiesService | — |
| Undo / Redo | toolbar | `XmlUltimateController.undo/redo` (RichTextFX) | — | — |
| Format / Pretty-print (warns >20 MB) | toolbar | `XmlUltimateController.prettifyingXmlText` | XmlService | — |
| Minify | toolbar | JSON: `JsonController.minifyJson`; XML via format service | XmlService/JsonService | — |
| Find / Replace | Ctrl+F / Ctrl+H | `EventHandlerManager`, `FindReplaceDialog` | — | — |
| Syntax highlighting | automatic | `SyntaxHighlightManagerV2` → `XmlSyntaxHighlighter` (regex) | — | — |
| Code folding (disabled for single-line / >20 MB) | gutter | `FoldingManagerV2` | — | — |
| Line numbers / status line | gutter / bottom | `LineNumberFactory`, `StatusLineManagerV2` | — | — |
| Font size (Ctrl+wheel, 6–72) | editor | `XmlCodeEditorV2.increase/decreaseFontSize` | — | — |
| Drag & drop files | editor / pane | `XmlEditor`, `XmlUltimateController.handleDragDropped` | DragDropService | — |
| File comparison / diff | toolbar | `XmlUltimateController.handleCompareWithFile` → `DiffView` | XmlService | — |
| **XML IntelliSense** (elements/attrs/auto-close/smart insert) | `<`, space, Ctrl+Space | `IntelliSenseEngine`, `ProviderRegistry`, `CompletionCache` | XsdCompletionProvider, Pattern/Schematron/XsltCompletionProvider | — |
| **JSON editor** (new/open/save, format, minify, validate, tree, JSONPath, JSON-Schema) | `tab_json.fxml` toolbar | `JsonController.*`, `JsonCodeEditor`, `JsonTreeView`, `JsonPathExecutor` | JsonService | `tab_json.fxml` |
| Templates (parameterized snippets + preview) | Templates tab / popup | `TemplatesController`, `TemplateManagerPopupController` | TemplateEngine, TemplateRepository | `tab_templates.fxml`, `popup_templates.fxml` |
| XML ↔ Excel/CSV conversion | Ctrl+E / dialog | `XmlSpreadsheetConverterDialogController` (Task-async) | XmlSpreadsheetConverterService, CsvHandler | `dialogs/XmlSpreadsheetConverterDialog.fxml` |

## A.2 Schema activity (XSD — the largest subsystem)

| Feature | Today: entry point | Today: controller/method | Service(s) | FXML |
|---|---|---|---|---|
| XSD text load/save/format/find | toolbar | `XsdController.handleToolbar*` | XmlService, XsdSerializer, MultiFileXsdSerializer, BackupUtility | `tab_xsd.fxml` |
| **Graphical schema view** (XMLSpy-style, expand/collapse, context menu) | xsdTab | `XsdGraphView` (+`XsdGraphViewEventHandler`, `XsdContextMenuFactory`) | XsdNodeFactory, XsdEditorContext, CommandManager | embedded |
| Properties panel (per node) | node select | `XsdPropertiesPanel` (+Type/Facets/Constraint helpers) | XsdEditorContext, CommandManager | embedded |
| Graphical edit (Add/Delete/Rename/Move/Duplicate/Paste/cardinality/type/use/form/…) | context menu / panel | 35 commands via `CommandManager` | — | — |
| Undo/Redo (100-history) | toolbar | `CommandManager` | — | — |
| **Type Library + Type Editor** (Simple/Complex tabs, list, usage finder) | typeLibraryTab | `TypeEditorTabManager`, `Simple/ComplexTypeEditorTab`, `SimpleTypesListTab`, `TypeLibraryView`, `TypeUsageFinder` | XsdEditorContext | embedded |
| **Facets** (datatype-aware, fixed/inherited styling) | type editor | `FacetsPanel`, `XsdDatatypeFacets`, `XsdFacetType` | AddFacet/DeleteFacet/EditFacetCommand | embedded |
| **Schema Analysis** (statistics, quality, identity constraints) | schemaAnalysisTab (lazy) | `SchemaAnalysisTabController`, `XsdStatisticsCollector`, `XsdQualityChecker` | — | `schema_analysis_tab.fxml` |
| **Flatten** (merge includes/imports, sort, strip comments) | flattenTab | `FlattenTabController.flattenXsdAction` (Task-async) | XsdParsingService, XsdSerializer, XsdModelAdapter | `flatten_tab.fxml` |
| **Documentation export** (HTML / PDF / Word + SVG diagrams) | documentation_tab | `DocumentationTabController` (Task-async) | XsdDocumentation{Html,Pdf,Word,Svg,Image}Service | `documentation_tab.fxml` |
| **Schema Generator** (XSD from XML, type inference, batch) | schemaGeneratorTab / popup / Ctrl+G | `SchemaGeneratorController`, `SchemaGeneratorPopupController` | SchemaGenerationEngine, SchemaGenerationOptions | `tab_schema_generator.fxml`, `popup_schema_generator.fxml` |
| **Sample data generation** (profiled XML from XSD) | button | `XsdController.generateSampleData` (Task-async) | XsdSampleDataGenerator, XmlValidationService | `tab_xsd.fxml` |
| XSD IntelliSense + XPath query panel | text editor / toolbar | `IntelliSenseEngine`, `XsdXPathQueryPanel`, `XsdSchemaElementExtractor` | — | embedded |

## A.3 Validation activity

| Feature | Today: entry point | Today: controller/method | Service(s) | FXML |
|---|---|---|---|---|
| XSD validation (single, 1.0/1.1; XSD selection via favorites/file) | sidebar / button | `XmlUltimateController.validateXml`, `XmlEditorSidebarController` | XmlService, XsdValidationService, SaxonXmlValidationService | `tab_xml_ultimate.fxml`, `controls/XmlEditorSidebar.fxml` |
| XSD validation (batch, drag-drop, error table, Excel report) | validationTab | `XsdValidationController.validate` | XsdValidationService, XmlValidationService, POI | `tab_validation.fxml` |
| Continuous/live validation (debounced) | sidebar checkbox | `XmlEditorSidebarController.toggleContinuousValidation` | SaxonXmlValidationService | embedded |
| **Schematron**: create/open/save, add rule/pattern, format, validate syntax, batch test, results table | schematron tab | `SchematronController.*` | SchematronService | `tab_schematron.fxml` |
| Schematron visual builder / docs generator / template library / tester | embedded tools | `SchematronVisualBuilder`, `SchematronDocumentationGenerator`, `SchematronTemplateLibrary`, `SchematronTester`, `SchematronErrorDetector` | SchematronService | embedded |
| Continuous schematron validation (debounced) | sidebar checkbox | `XmlEditorSidebarController.toggleSchematronValidation` | SchematronService | embedded |
| Problems / errors list with jump-to-line | sidebar / panels | `validationErrorsListView` → `XmlEditor.navigateToLine` | — | embedded |

## A.4 Transform activity

| Feature | Today: entry point | Today: controller/method | Service(s) | FXML |
|---|---|---|---|---|
| XSLT viewer (load XML+XSLT, transform Ctrl+R, HTML/XML/text output) | xslt tab | `XsltController.checkFiles/renderHTML/renderXML` | XmlService (Saxon), XsltTransformationEngine | `tab_xslt.fxml` |
| XSLT auto-pickup from `xml-stylesheet` PI; open result in browser/editor | toolbar | `XsltController` | — | — |
| Performance statistics (timing, throughput) | output area | `XsltController.updatePerformanceStatistics` | — | — |
| File-watch auto re-transform | scheduler | `XsltController.checkForFileChanges` (3 s poll) | — | — |
| **XSLT Developer** (params, live preview, output format/encoding/indent, recent) | xslt-developer tab / Ctrl+Shift+T | `XsltDeveloperController.executeTransformation` (async) | XsltTransformationEngine (compiled-stylesheet cache) | `tab_xslt_developer.fxml` |
| **XPath / XQuery console** (execute, result table, FLWOR, saved queries, examples) | console / Ctrl+Q | `XsltDeveloperController`, `XPathIntelliSenseEngine`, `XPathFunctionLibrary` | XmlService.getXQueryResult, XsltTransformationEngine.executeXQuery | embedded |

## A.5 PDF/FOP activity

| Feature | Today: entry point | Today: controller/method | Service(s) | FXML |
|---|---|---|---|---|
| XSL-FO → PDF generation + metadata (title/author/subject/keywords/producer/date) | fop tab | `FopController.buttonConversion` (**sync, blocking**) | FOPService (Apache FOP) | `tab_fop.fxml` |
| PDF page preview gallery | image gallery | `FopController.displayPdfPreview` (rasterizes all pages) | PDFBox PDFRenderer | embedded |

## A.6 Signature activity

| Feature | Today: entry point | Today: controller/method | Service(s) | FXML |
|---|---|---|---|---|
| Create self-signed certificate (DN form, keystore/alias pw, validity) | signature tab | `SignatureController.createCertificate` | SignatureService (BouncyCastle) | `tab_signature.fxml` |
| Sign document (enveloped, RSA-SHA256, exclusive C14N) | sign tab | `SignatureController.handleSignButton` | SignatureService (JSR-105) | embedded |
| Validate signature (+ chain/trust/revocation/timestamp in expert mode) | validate tab | `SignatureController.validateSignature` | SignatureService | embedded |
| Expert mode (algorithm/key size/sig alg/C14N/transform/digest/SAN) | expert tab | `SignatureController` | SignatureService | embedded |

## A.7 Favorites activity

| Feature | Today: entry point | Today: controller/method | Service(s) | FXML |
|---|---|---|---|---|
| Add/list/categorize/open favorites; cross-editor | star buttons, panel | `FavoritesParentController`, `FavoritesPanelController` | FavoritesService | `controls/FavoritesPanel.fxml` |
| XSD favorites quick-select | sidebar combo | `XmlEditorSidebarController.loadXsdFromFavorites` | FavoritesService | embedded |

## A.8 Help / Settings / shell-level

| Feature | Today: entry point | Today: controller/method | Service(s) | FXML |
|---|---|---|---|---|
| Welcome / dashboard | startup | `WelcomeController`, `MainController.loadWelcomePage` | — | `welcome.fxml` |
| Help / About (version, docs WebViews, migration guide) | menu | `HelpController`, `MainController.handleAboutAction` | VersionUtil, UpdateCheckService | `tab_help.fxml` |
| Settings (interface/theme, proxy, temp folder, FundsXML, update, cache) | menu / sidebar | `SettingsController` | PropertiesService, ConnectionService, FavoritesService | `settings.fxml` |
| Theme switch (light/dark) | settings | `SettingsController.onThemeChange` → `MainController.applyTheme` (CSS swap) | PropertiesService (`ui.theme`) | — |
| Auto-update (check on startup +2 s, download/install) | menu / timer | `MainController.checkForUpdatesOnStartup` | UpdateCheckService, AutoUpdateServiceImpl, update-helper | — |
| Memory monitor (status bar, 3 s) | status bar | `MainController.updateMemoryUsage` | — | — |
| FundsXML extension (conditional menu/tab, schema download) | menu (if enabled) | `MainController.openFundsXmlDownload` | FundsXmlExtensionService, FundsXmlCache | — |
| Global keyboard shortcuts, drag-drop routing, recent-files routing | scene | `MainController` | DragDropService, PropertiesService | `main.fxml` |

---

# B. Architecture As-Is

## B.1 Layers

```
View (FXML + custom JavaFX controls)
   → Controller (25 controllers; MainController orchestrates tab nav)
      → Service layer (XmlService, XsltTransformationEngine, Schematron, FOP, Signature, …)
         → Model (XSD Editor V2: XsdNode tree + Commands; DOM/Saxon for XML)
```

The **XSD Editor V2** sub-system (`controls/v2/`) is MVVM + Command pattern and is the
architectural high point: model is UI-free, all edits go through `CommandManager`, observable
via `PropertyChangeSupport`.

## B.2 Navigation / shell (what gets replaced)

- `MainController.loadPage(ActionEvent)` maps a sidebar **button id → `/pages/tab_*.fxml`**,
  loads it with `FXMLLoader`, and swaps it into the content pane; `activeTabId` tracks state.
- Theme = **CSS swap only** (`MainController.applyTheme` adds `light-theme.css` / `dark-theme.css`);
  persisted as `ui.theme`. No shared token source; colors duplicated across CSS files.
- This whole button→FXML tab model is replaced by the **Activity Bar → side panel → unified
  editor** model.

## B.3 Threading model

- `FxtGui.executorService` (fixed pool, daemon) — general background work.
- `MainController.scheduler` (5) + `MainController.service` (cached) — periodic + per-controller work.
- `ThreadPoolManager` — centralized 5-pool design (UI/CPU/I-O/scheduled/background) **but not used
  consistently**; many controllers spin up their own `ExecutorService`. Consolidation opportunity.
- UI updates via `Platform.runLater`. Good async exists for: recent-files exists-checks, XSD doc
  loading, spreadsheet conversion, XSLT developer transform, schematron batch.

## B.4 Reusable vs. UI-specific

**Reusable as-is (keep, ~100 %)** — the entire foundation the rebuild stands on:
- All `service/*` (XmlService, XsltTransformationEngine, Schematron, FOP, Signature, Connection,
  Properties, Favorites, DragDrop, XsdValidation, SchemaGeneration, XsdDocumentation*, Csv,
  Template*, ExportMetadata, UpdateCheck, AutoUpdate).
- XSD V2 **model** (79 classes), **35 commands**, `CommandManager`, `XsdEditorContext`,
  `SelectionModel`, `XsdClipboard`, `XsdSerializer`/`MultiFileXsdSerializer`, `XsdNodeFactory`,
  `XsdNodeSorter`.
- `controls/icons/IconifyIcon`, `VersionUtil`, `ThreadPoolManager`.
- IntelliSense **engines/providers** (`IntelliSenseEngine`, `XPathIntelliSenseEngine`, providers,
  `CompletionCache`) — engine reusable, UI popup re-skinned.

**UI-specific (replaced by the rebuild):**
- `main.fxml` + the button→FXML navigation in `MainController`.
- All `tab_*.fxml` and their controllers as *shells* (logic migrates into new activities).
- `XsdGraphView` (Canvas renderer) → replaced by the new virtualized renderer (D2).
- `XsdPropertiesPanel`, `FacetsPanel`, `TypeLibraryView`, type-editor tabs → rebuilt as the
  **unified inspector** + new type UI (facet *logic* and datatype mappings reused).
- `XmlEditorSidebarController` (already `@Deprecated`).
- The existing partial unified editor (`UnifiedEditorController`, `tab_unified_editor.fxml`,
  `controls/unified/*`) → **not** the base (D1); mined for ideas only, then removed.

---

# C. Gap / Mapping Analysis (Target ↔ Code)

## C.1 Activity Bar → owned functions

| Activity (Figma) | Owns | New-build vs mapped |
|---|---|---|
| **Explorer** | File tree (workspace + open editors), new/open/save/recent, drag-drop | **New** tree explorer; maps file-ops logic |
| **Favorites** (star) | Favorites list/categories, add-current, quick-open | New panel UI; maps FavoritesService |
| **Validation** | XSD single+batch (1.0/1.1), Schematron, Problems panel, continuous validation | New panels; maps validation/schematron services |
| **Transform** | XSLT viewer + developer, XPath/XQuery console, output preview, params, saved queries | New panels; maps XsltTransformationEngine/XmlService |
| **Schema** | XSD Text/Tree/Graphic(+Grid), Type Library/Editor, Facets, Analysis, Flatten, Doc export, Generator, sample data | **Largest new build** (renderer); maps V2 model/commands/services |
| **PDF/FOP** | XSL-FO→PDF + metadata + preview | New panel; maps FOPService (+async fix) |
| **Signature** | Create cert / sign / validate / expert | New panels; maps SignatureService |
| **Help** | Help/About, docs, update check | New panel; maps HelpController logic |
| **Settings** | Interface/theme, proxy, temp, FundsXML, update, cache | New panel; maps SettingsController logic |

## C.2 Cross-cutting target components

| Target component | New build? | Notes |
|---|---|---|
| **Unified shell** (window scene: activity bar + side panel + main + inspector + status bar) | **New** | Replaces `main.fxml` + button nav |
| **Activity Bar** control | **New** | Slim icon bar; switches side panel; IconifyIcon |
| **Unified Inspector** (Node&XPath, Type&Facets, Cardinality&Use, Documentation&Refs) — identical in Text/Tree/Graphic | **New** | Reuses facet logic + XsdEditorContext/SelectionModel |
| **Virtualized renderer** (Tree + Graphic with embedded grid) | **New** (D2) | Only-visible rendering, incremental updates |
| **Embedded grid in Graphic** (XMLSpy-style, vertical nesting; repeating same-type nodes → grid) | **New** | No standalone Grid mode; see `2026-03-26-xmlspy-grid-view-design.md` |
| **Design-token system** (`design-tokens.css`, light/dark root class) | **New** (D4) | Single source of truth |
| **Command palette / keyboard layer** | **New** | Usability requirement |
| File-type-aware **editor host** (XML/XSD/XSLT/Schematron/JSON) | **New** | Hosts reused code editor + IntelliSense |

## C.3 Placement of "where does it go?" features

- **JSON editor + JSONPath** → editor center as a file type; JSONPath in the Transform/console area.
- **Templates** → command + side panel (Explorer or a dedicated palette action), not a top-level tab.
- **Spreadsheet conversion** → command/dialog invoked from Explorer/editor (keep dialog).
- **Sample data / Schema Generator** → Schema activity actions.
- **FundsXML** (conditional) → stays conditional; surfaced under Explorer/Help when enabled.
- **Welcome/Dashboard** → shown when no file open (Figma Welcome 52-2).

## C.4 What is dropped from the UI concept (not features)

- The **standalone Grid view mode** (decision: Grid is part of Graphic). Feature preserved as an
  embedded rendering, not a separate mode.
- The **button→FXML tab navigation** itself.
- The **partial unified editor** shell and deprecated `controls/unified/*` tabs.

No *user feature* is dropped — only UI scaffolding. Enforced by the Traceability Matrix (§G).

---

# D. Performance Analysis

## D.1 Current bottlenecks (evidence)

| Area | Problem | Evidence |
|---|---|---|
| XSD graphical view | Full Canvas redraw + O(n) layout + O(n) bounds calc on **every** edit; no virtualization | `XsdGraphView` (rebuild→layoutNode→redraw); `calculateCanvasBounds` |
| FOP PDF | Generation runs **synchronously on the FX thread** | `FopController.buttonConversion` → `FOPService.createPdfFile` |
| PDF preview | Rasterizes **all** pages to `BufferedImage` upfront | `FopController.displayPdfPreview` (PDFBox) |
| XQuery | New Saxon `Processor` created **per query** | `XmlServiceImpl.getXQueryResult` (~1074) |
| XSLT file-watch | 3 s **polling** instead of `WatchService` | `XsltController.checkForFileChanges` |
| Syntax highlighting | Regex on FX thread (debounced); large single-line files pathological | `SyntaxHighlightManagerV2`; guarded by `XmlService.hasProblematicLineLength` |
| Tree/property panels | No virtualization for very large facet/type lists; property fields built eagerly | `XsdPropertiesPanel`, `FacetsPanel` |

Good existing patterns to keep: large-file thresholds (1 / 5 / 20 MB) with adaptive debounce,
lazy IntelliSense init on focus, compiled-stylesheet cache, schema cache.

## D.2 Strategies for the new design

1. **Virtualized Tree/Graphic** (D2): render only visible rows/nodes; lazy expansion; incremental
   re-render of changed subtrees only (never full redraw). Embedded grid uses a virtualized table.
2. **All heavy ops async** via `ThreadPoolManager` (consolidate pools); `Platform.runLater` only for
   UI updates. Make **FOP generation and PDF preview async** with progress + lazy per-page render.
3. **Streaming/SAX** for read-only scans (statistics, batch validation) where a full DOM/model isn't
   needed; lazy parsing of large files.
4. **Reuse Saxon `Processor`/compiled artifacts**; fix per-query Processor creation; cache by content
   hash.
5. **No full copies** of large structures in the UI; bind to model via observers.
6. **Replace XSLT polling** with `java.nio.file.WatchService`.

## D.3 Profiling plan + target budgets (proposed)

- **Instrument** with JFR + simple in-app timers (already partly present in XSLT stats). Add a debug
  HUD for render/parse/validate timings.
- **Test corpus:** small (10 KB), medium (1 MB), large (10 MB), pathological (single-line 5 MB),
  and a deep XSD (1 000+ nodes / 5 000 types).
- **Budgets (initial, to refine in plan):**
  - Open + first paint: ≤ 1 s (≤10 MB), ≤ 3 s (large).
  - Keystroke → highlight: ≤ 50 ms (≤1 MB), ≤ 150 ms (large, debounced).
  - Tree/Graphic expand node: ≤ 100 ms regardless of schema size (virtualized).
  - Validation (single, 1 MB): ≤ 500 ms async, UI never blocked.
  - Type/Schema tab open: < 1 s (existing V2 target).
  - UI thread: **never** blocked > 16 ms during scroll/typing.

---

# E. Dead-Code Candidates (post-mapping; remove only after parity verified)

| Candidate | Reason | Confidence | Gate before removal |
|---|---|---|---|
| `controls/unified/{Xml,Xsd,Xslt,Schematron}UnifiedTab.java` | `@Deprecated`; superseded; not the rebuild base (D1) | High | New activity at parity |
| `controller/controls/XmlEditorSidebarController` + `controls/XmlEditorSidebar.fxml` | `@Deprecated` sidebar approach | High | Explorer + Inspector live |
| `UnifiedEditorController` + `tab_unified_editor.fxml` | Replaced by greenfield shell (D1) | High | New shell is default |
| `XsdGraphView` + `view/*` Canvas renderer + `XsdPropertiesPanel`/`FacetsPanel`/type-editor tabs | Replaced by virtualized renderer + unified inspector (D2) | Medium | New Schema activity at parity (facet *logic* extracted first) |
| `main.fxml` + `MainController.loadPage` button-nav | Replaced by activity-bar shell | Medium | Shell + all activities migrated |
| Per-tool `tab_*.fxml` + controller *shells* | Logic migrated into activities | Medium | Per-feature parity |
| `tab_templates.fxml` | Already removed from menu | Medium | Templates re-surfaced in new UI |
| Per-tool CSS (`xml-editor.css`, `unified-editor.css`, `xsd-tab-groups.css`, …) | Superseded by token system (D4) | Medium | Token CSS covers all |

**Rule:** every removal requires (a) static reference search proving no live use, and (b) the
replacing feature verified at parity per the Traceability Matrix. Never delete to "clean up" before
the replacement runs.

---

# F. Risks, Assumptions & Open Decisions

## F.1 Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Greenfield (D1) is large scope | Long timeline, regression risk | Side-by-side (D3); strict phasing; matrix as acceptance gate |
| New virtualized renderer is hard (esp. embedded grid + editing parity) | Schema activity is the critical path | Spike early (Phase 2); keep `XsdGraphView` reachable until parity |
| Feature loss during cutover | Violates hard constraint #1 | Matrix-driven verification before any old-code deletion |
| Two editor stacks coexisting | Temporary duplication/maintenance | Time-box each activity's parity; remove old promptly after verify |
| Large-file performance regressions | Violates hard constraint #4 | Budgets + profiling each phase; virtualization from day one |
| Theme refactor touches all CSS | Visual regressions | Token CSS introduced first; screenshot diffs (`docScreenshots`) |
| IntelliSense/command coupling to old editor | Migration friction | Engines are reusable; re-host behind new editor API |

## F.2 Assumptions

- Java 25 + JavaFX 24 + AtlantaFX retained (no platform change).
- RichTextFX remains the code editor; the new renderer is for Tree/Graphic, not the text view.
- Saxon HE / Xerces (XSD 1.1) / FOP / Santuario libraries unchanged.
- Figma is the source of truth for layout, tokens, icon set (bi-*).
- Persisted theme continues via `ui.theme`; view-state (expand/collapse) preserved across edits.

## F.3 Open decisions (defaults proposed; confirm during plan execution)

1. **Test depth** — proposed: unit tests for all new model/adapters; TestFX smoke tests per activity
   shell + critical flows; keep all existing tests green. (Default unless told otherwise.)
2. **Command palette scope** — full fuzzy palette vs. keyboard shortcuts only for v1. Proposed:
   shortcuts in early phases, palette as a later usability phase.
3. **Workspace concept** — Explorer shows a folder tree; do we add a saved "project" file? Proposed:
   folder-based explorer now, no project file in v1.
4. **FundsXML placement** — keep conditional; exact activity placement TBD when Explorer is built.

(Resolved already: D1–D4 above.)

---

# G. Traceability Matrix (Feature → current code → target)

Acceptance checklist for "no feature loss." Each feature must be reachable & working in the new UI
**before** its old code is removed.

| # | Feature | Current location (controller / control / service) | Target activity / location | Status |
|---|---|---|---|---|
| 1 | New/Open/Save/SaveAs/SaveAll | XmlUltimateController; XmlService/ExportMetadataService | Explorer + editor toolbar | ☐ |
| 2 | Recent files | XmlUltimateController; PropertiesService | Explorer / Welcome | ☐ |
| 3 | Undo/Redo (text) | XmlUltimateController (RichTextFX) | Editor toolbar | ☐ |
| 4 | Format / Minify | XmlUltimateController/JsonController; XmlService/JsonService | Editor toolbar (Code group) | ☐ |
| 5 | Find/Replace | EventHandlerManager; FindReplaceDialog | Editor toolbar / Ctrl+F | ☐ |
| 6 | Syntax highlight / folding / line numbers / status line | SyntaxHighlightManagerV2, FoldingManagerV2, StatusLineManagerV2 | Editor host | ☐ |
| 7 | Font size, drag-drop, diff/compare | XmlCodeEditorV2, XmlEditor; DiffView | Editor host | ☐ |
| 8 | XML IntelliSense | IntelliSenseEngine + providers | Editor host (Text mode) | ☐ |
| 9 | JSON editor (edit/format/minify/validate/tree/JSONPath/schema) | JsonController, JsonCodeEditor, JsonTreeView, JsonPathExecutor; JsonService | Editor center (JSON type) + Transform | ☐ |
| 10 | Templates | TemplatesController, TemplateManagerPopupController; TemplateEngine/Repository | Command + side panel | ☐ |
| 11 | XML↔Excel/CSV conversion | XmlSpreadsheetConverterDialogController; XmlSpreadsheetConverterService, CsvHandler | Command/dialog | ☐ |
| 12 | XSD text load/save/format/find | XsdController; XsdSerializer, MultiFileXsdSerializer | Schema activity + editor | ☐ |
| 13 | XSD graphical view + expand/collapse + context menu | XsdGraphView, XsdContextMenuFactory | Schema → Graphic (new renderer) | ☐ |
| 14 | XSD graphical editing (35 commands) | CommandManager + commands | Schema (Graphic/Tree + inspector) | ☐ |
| 15 | Properties panel | XsdPropertiesPanel (+helpers) | Unified Inspector | ☐ |
| 16 | Type Library + Type Editor (Simple/Complex/list/usage) | TypeEditorTabManager, *EditorTab, TypeLibraryView, TypeUsageFinder | Schema activity (types panel) | ☐ |
| 17 | Facets (datatype-aware, fixed/inherited) | FacetsPanel, XsdDatatypeFacets, XsdFacetType | Inspector → Type&Facets | ☐ |
| 18 | Schema Analysis (stats/quality/identity) | SchemaAnalysisTabController, XsdStatisticsCollector, XsdQualityChecker | Schema activity panel | ☐ |
| 19 | Flatten | FlattenTabController; XsdParsingService, XsdSerializer | Schema activity action | ☐ |
| 20 | Documentation export (HTML/PDF/Word/SVG) | DocumentationTabController; XsdDocumentation*Service | Schema activity action | ☐ |
| 21 | Schema Generator (XSD from XML, batch) | SchemaGeneratorController/Popup; SchemaGenerationEngine | Schema activity action / command | ☐ |
| 22 | Sample/profiled data generation | XsdController; XsdSampleDataGenerator | Schema activity action | ☐ |
| 23 | XSD IntelliSense + XPath query panel | IntelliSenseEngine, XsdXPathQueryPanel | Editor host + Transform | ☐ |
| 24 | XSD validation single (1.0/1.1) | XmlUltimateController, XmlEditorSidebarController; XsdValidationService, SaxonXmlValidationService | Validation activity | ☐ |
| 25 | XSD validation batch (drag-drop, table, Excel) | XsdValidationController; XsdValidationService, POI | Validation activity | ☐ |
| 26 | Continuous validation (debounced) | XmlEditorSidebarController | Validation + Problems panel | ☐ |
| 27 | Schematron edit (rules/patterns/format/syntax/batch/results) | SchematronController; SchematronService | Validation activity | ☐ |
| 28 | Schematron tools (builder/docs/templates/tester/detector) | SchematronVisualBuilder, *Generator, *TemplateLibrary, *Tester, *ErrorDetector | Validation activity | ☐ |
| 29 | Problems / jump-to-line | validationErrorsListView → navigateToLine | Problems panel | ☐ |
| 30 | XSLT viewer (transform, HTML/XML/text, browser, stats, file-watch) | XsltController; XmlService, XsltTransformationEngine | Transform activity | ☐ |
| 31 | XSLT Developer (params, live preview, formats, recent) | XsltDeveloperController; XsltTransformationEngine | Transform activity | ☐ |
| 32 | XPath/XQuery console (execute, table, FLWOR, saved, examples) | XsltDeveloperController, XPathIntelliSenseEngine, XPathFunctionLibrary | Transform activity | ☐ |
| 33 | FOP PDF generation + metadata | FopController; FOPService | PDF/FOP activity (async) | ☐ |
| 34 | PDF preview | FopController; PDFBox | PDF/FOP activity (lazy pages) | ☐ |
| 35 | Create certificate | SignatureController; SignatureService | Signature activity | ☐ |
| 36 | Sign document | SignatureController; SignatureService | Signature activity | ☐ |
| 37 | Validate signature (+expert chain/trust/revocation/timestamp) | SignatureController; SignatureService | Signature activity | ☐ |
| 38 | Signature expert options | SignatureController; SignatureService | Signature activity (expert) | ☐ |
| 39 | Favorites (add/list/categories/open, cross-editor) | FavoritesParentController, FavoritesPanelController; FavoritesService | Favorites activity + inspector star | ☐ |
| 40 | XSD favorites quick-select | XmlEditorSidebarController; FavoritesService | Validation/Schema panel | ☐ |
| 41 | Welcome / dashboard | WelcomeController | Shell (no file open) | ☐ |
| 42 | Help / About (version, docs, migration) | HelpController; VersionUtil | Help activity | ☐ |
| 43 | Settings (interface/theme/proxy/temp/FundsXML/update/cache) | SettingsController; PropertiesService, ConnectionService | Settings activity | ☐ |
| 44 | Theme switch light/dark | SettingsController → MainController.applyTheme | Shell header + token system | ☐ |
| 45 | Auto-update (startup + manual) | MainController; UpdateCheckService, AutoUpdateServiceImpl | Help / shell | ☐ |
| 46 | Memory monitor | MainController.updateMemoryUsage | Status bar | ☐ |
| 47 | FundsXML extension (conditional) | MainController; FundsXmlExtensionService, FundsXmlCache | Explorer/Help (conditional) | ☐ |
| 48 | Global shortcuts / drag-drop routing | MainController; DragDropService | Shell + command layer | ☐ |
| 49 | File comparison / diff | XmlUltimateController; DiffView | Editor / command | ☐ |
| 50 | View-state preservation (expand/collapse across edits) | XsdGraphView expansion cache | New renderer (must preserve) | ☐ |

> The phase plan in the companion document sequences these into runnable milestones, each with its
> own subset of this matrix as its acceptance checklist.

---

# G.1 Matrix reconciliation (2026-05-31, after smoke test)

Status legend: ✅ at parity in the shell · ◐ core migrated, advanced sub-features still legacy-only ·
☐ not migrated (legacy-only).

**✅ At parity (24):** 1 New/Open/Save/SaveAs/SaveAll · 2 Recent · 3 Undo/Redo · 6 Highlight/fold/line-nums/status ·
7 Font/drag-drop/diff · 8 XML IntelliSense · 11 XML↔Excel/CSV · 12 XSD text load/save/format · 15 Properties (Inspector) ·
17 Facets · 19 Flatten · 20 Doc export HTML/PDF/Word · 23 XSD IntelliSense + XPath panel · 24 XSD validation single ·
25 XSD validation batch · 29 Problems/jump-to-line · 33 FOP PDF · 34 PDF preview · 35 Create certificate · 36 Sign ·
39 Favorites · 42 Help/About · 44 Theme switch · 49 Diff/compare. (48 shortcuts/drag-drop ≈ ✅.)

**◐ Partial — core in shell, advanced pending (≈19):** 4 Minify (format ✅, minify ✗) · 5 Find/Replace (widget supports it;
no explicit Find UI/Ctrl+F in shell) · 9 JSON (edit/tree/JSONPath ✅; schema-validate/minify ✗) · 13 Graphic view
(render ✅; context menu + expand/collapse-state ✗) · 14 Graphical editing (8 of 35 commands) · 16 Type Editor
(library list ✅; Simple/Complex editor tabs + usage finder ✗) · 18 Schema analysis (statistics ✅; quality/identity ✗) ·
21 Schema generator (single ✅; batch ✗) · 22 Sample data (basic ✅; profiled profiles ✗) · 27 Schematron (validate ✅;
edit/builder ✗) · 30 XSLT viewer (transform+formats ✅; browser/stats/file-watch ✗) · 31 XSLT Developer (params+formats ✅;
live preview/recent ✗) · 32 XPath/XQuery console (XPath+saved ✅; XQuery FLWOR/table/examples ✗) · 37 Validate signature
(basic ✅; expert chain/trust/revocation/timestamp ✗) · 38 Signature expert (cert creation ✅; expert validation opts ✗) ·
40 XSD favorites quick-select · 41 Welcome (empty-state ✅; dashboard stats/tips ✗) · 43 Settings (theme ✅; proxy/temp/
FundsXML/update/cache ✗) · 46 Memory monitor (legacy bottom bar; not in shell status bar).

**☐ Not migrated (2):** 45 Auto-update UI · 47 FundsXML extension menu.

✅ 28 Schematron tools — DONE 2026-05-31 (commit ec6c34ab): Rule Templates / Tester / Visual Builder reused as
shell tool-tabs via `EditorHost.openToolTab`. (Error-detector + docs generation remain follow-ups.)

✅ 4/5/9 Editor niceties — DONE 2026-05-31 (commit d36518fa): Find/Replace (UnifiedSearchBar via Ctrl+F/Ctrl+H),
Minify (XML + JSON), and JSON-Schema validation (ValidationPanel + `ValidationRunner.validateJson`). JSON edit/tree/
JSONPath were already ✅, so #9 JSON is now complete.

**Resolved since reconciliation (10b):**
- ✅ 50 View-state preservation — DONE 2026-05-31 (commit a4292f89): XsdTreeView + XsdGraphicView preserve
  expand/collapse by node id across re-renders.
- ✅ 26 Continuous (debounced) validation — DONE 2026-05-31 (commit 0631ece4): ValidationPanel auto-validates the
  active XML-family document while typing (600ms debounce, toggle on by default), live Problems list.
- ✅ 13 Graphic context menu — DONE 2026-05-31 (commit e30a37b3): shared `NodeContextMenu` gives the Graphic view
  the same right-click editing as the Tree (select card → Add/Rename/Change/Delete).
- ✅ 10 Templates — DONE 2026-05-31 (commit 2d623b55): `TemplateRunner` + `TemplateInsertDialog` reuse
  `TemplateRepository`; "Insert Template…" toolbar action renders (with defaults) and inserts at the caret. Per-template
  parameter entry is a follow-up.
- ◐→ 14 Graphical editing — the Graphic view now edits via the command stack with the same 8 commands as the Tree
  (Add Element/Attribute/Sequence/Choice, Rename, Change Type/Cardinality, Delete); the remaining ~27 commands are
  still pending for both views.

**Verdict:** the shell is at parity for the **core daily workflows** (open/edit/validate/transform/sign/convert/document
across XML/XSD/JSON), smoke-verified in the full app. It is **not** at full parity for the long tail (☐ + the ✗ parts of
◐). Per hard constraint #1 (no feature loss) and decision D5 (early cutover + legacy bridge), a **wholesale Phase-10
deletion is therefore premature** — the legacy bridge must remain until those items are migrated or explicitly retired.

# G.2 Phase 10 — realistic, staged cutover & cleanup

Phase 10 is reframed from "one big cutover + delete" to **incremental, parity-gated retirement**, matching D5.

**Already done (cutover step 1):** shell is the default landing surface; legacy tools reachable via the sidebar
(commit 968e8f9b). No feature lost.

**10a — STARTED 2026-05-31 (commit d51fd0f0): removed 33 zero-reference dead classes** (orphaned refactor helpers,
an unused DI module, and legacy panels/services never wired by the bridge or shell). Proven via clean compile + full
green suite (incl. real-app boot tests). More 10a passes possible as 10b retires owners.

> **Side finding:** `./gradlew build` fails at `spotlessJavaCheck` — 100+ files (the whole rebuild) were committed
> without `spotlessApply` (the branch has been gated on `./gradlew test`, which skips the style check). Not a
> correctness issue; a one-shot `spotlessApply` formatting commit is advisable before the final PR.

For each remaining deletion, run a static reference search first (constraint #2/#3); delete only if no live use:
- Dead V1 scaffolding already marked deprecated and not on the shell path (verify, then remove).
- The **old Phase-2 shell placeholder** path is already gone (commit 4d29c008).
- *Candidates to verify:* `controls/unified/*` + `UnifiedEditorController`/`tab_unified_editor.fxml` **iff** the
  "Editor (Legacy)" bridge entry is dropped first — but keep until 10c.

**10b — Migrate the remaining gaps (unblocks deletion of their legacy owners).** Priority order:
1. View-state preservation (#50) — usability constraint; affects Tree/Graphic every edit.
2. Continuous/debounced validation (#26) + Problems live update.
3. Remaining graphical-editing commands (#14) + Graphic context menu (#13).
4. Type Editor tabs + usage finder (#16); schema quality/identity (#18); batch schema-gen (#21); profiled data (#22).
5. Templates (#10); Schematron editing/tools (#27/#28).
6. Full Settings (#43): proxy/temp/cache/FundsXML/update; then Auto-update UI (#45), FundsXML menu (#47), memory in status bar (#46).
7. Editor niceties: Find/Replace UI (#5), Minify (#4), JSON schema-validate (#9); XSLT live preview + XQuery console (#30–32); signature expert validation (#37/#38); dashboard extras (#41).

**10c — Retire legacy, per subsystem, once its matrix rows are all ✅.** Remove that tool's controller + `tab_*.fxml`
+ sidebar button + superseded CSS, each gated by a clean reference search. Order mirrors 10b completion. The
`main.fxml` button-nav and remaining old shells are deleted last, when the sidebar bridge is no longer needed.

**10d — Finalize.** Drop the "Editor (Legacy)" entry, run `dependencyUpdates`/dead-code sweep, regenerate docs
(`docs-updater`) + screenshots (`xvfb-run ./gradlew docScreenshots`), measure against the §D.3 budgets, open the PR
`feature/ui-rebuild-unified-editor → main`.

**Gate (unchanged):** no legacy file is deleted until every matrix row it backs is ✅ **and** a reference search proves
no live caller (constraint #2/#3). Each deletion is its own revertible commit.
