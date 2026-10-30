# Architecture Quick-Reference

## The Unified Shell (the entire UI)

```
FxtGui → /pages/tab_unified_shell.fxml → UnifiedShellController → UnifiedShellView
┌──────────────────────────────────────────────────────────────────────┐
│ menu bar (File · Edit · View · Tools · Help; macOS: system menu bar) │
├──┬──────────────┬──────────────────────────────────────┬─────────────┤
│A │ Side panel   │ header (breadcrumb · search · theme) │ Inspector   │
│c │ (per         ├──────────────────────────────────────┤ (Properties │
│t │  activity)   │ file tabs · editor toolbar           │  panel)     │
│i │              ├──────────────────────────────────────┤             │
│v │              │ EditorHost (Text/Tree/Graphic views) │             │
│B │              │                                      │             │
│a ├──────────────┴──────────────────────────────────────┴─────────────┤
│r │ status bar (Ln/Col · type · XSD indicator · memory · file)        │
└──┴───────────────────────────────────────────────────────────────────┘
```

The shell logic lives in `controls/shell/`. The **chrome** (root BorderPane, header bar,
editor toolbar, status bar, work-area skeleton) is defined in `pages/shell.fxml` and loaded
by `UnifiedShellView` via the `fx:root` pattern (so it is Scene-Builder-editable); the dynamic,
`EditorHost`-dependent custom controls are built in `UnifiedShellView.initialize()` and injected
into the FXML `fx:id` placeholders (activityBarHost, sidePanelHost, viewSwitchHost, the wrappers).
Legacy per-tool tabs/controllers are gone; `controller/` only keeps `UnifiedShellController`.

## Key Shell Classes

| Class | Purpose | Location |
|-------|---------|----------|
| `UnifiedShellView` | Shell layout, editor toolbar, status bar, activity→panel wiring | `controls/shell/` |
| `ShellMenuBar` + `ShellMenuItem` | Menu bar (layout `views/ShellMenuBar.fxml`); items call the shell's existing actions via `UnifiedShellView.runFromMenu`, enabled state follows the active document. Accelerators only *display* the shortcuts — `UnifiedShellView.handleShortcut` consumes the key first and stays the executing path | `controls/shell/` |
| `MacMenuIntegration` | macOS only (NSMenuFX, the sole class referencing it): system menu bar, application menu (About/Updates/Settings/Hide/Quit), Window menu. The app-menu *title* comes from the bundle's `CFBundleName`, not from code. **Gotcha:** NSMenuFX builds native key equivalents from Shift/Alt/Meta/Control only and ignores `Shortcut` — every item it receives gets its accelerator respelled with `Meta` (`nativeAccelerator`). Quit/Exit/Cmd+Q all go through `UnifiedShellView.requestExit()` (a `WINDOW_CLOSE_REQUEST`); window-level chords (Shortcut+Q, Ctrl+Tab, Ctrl+F4) run as an event *filter* on the shell | `app/` |
| `Activity` / `ActivityBar` / `ActivitySelectionModel` | Activity Bar (Explorer…Settings), stable ids | `controls/shell/` |
| `ShellBootstrap` | Startup/shutdown tasks, scheduler | `controls/shell/` |
| `EditorHost` | Center: file tabs (`EditorTab`), view-mode switching, schema/schematron binding, tool tabs, welcome page | `controls/shell/editor/` |
| `EditorTabMenu` | Right-click menu of every editor tab (installed by the `EditorHost` tabs listener): Reload from Disk, Close, Close Others, Save, Save As…, Copy Path, Reveal in File Manager (`DesktopActions.reveal`), Reveal in Explorer Panel (`setRevealInExplorerHandler` → `ExplorerPanel.revealFile`); actions target the clicked tab, tool/diff/PDF tabs only get Close/Close Others. Tabs reorder by drag (`TabDragPolicy.REORDER`, a permutation; `openDocuments` follows the tab order) | `controls/shell/editor/` |
| `ExternalChangeMonitor` + `DiskStamp` / `ExternalChangePrompt` | Polls open tabs' files off-thread (2 s while the window is focused + on focus gain; no `WatchService` — network drives); `EditorHost.handleExternalChanges` asks Reload/Ignore or Keep/Close. Every editor write must go through `EditorHost.write()` so the stamp follows | `controls/shell/editor/` |
| `EditorBottomDock` | `EditorHost.setBottom`: hosts the transform `TransformOutputPanel` ("output") and, during a session, the `XsltDebugPane` ("debug") behind an OUTPUT \| DEBUG strip (shown only for ≥ 2 panels); height persisted (`editor.bottomDock.height`) | `controls/shell/editor/` |
| XSLT debugger: `XsltDebugController` + `XsltDebugPane` (header + persisted two-way split of two `TabPane`s: Variables \| Watch, Call Stack \| Messages \| Breakpoints \| Output; divider `editor.debugPane.divider`, user-chosen tabs `editor.debugPane.leftTab/rightTab` (the pane's own switches via `selectTab` are not saved); the `*View`s have no titles of their own, Messages/Breakpoints get count badges, a failed run selects Messages; a click on a Variables/Watch value opens a non-modal `ValueViewerWindow` — full value via `WatchEvaluator.serialize`, XPath against it via `WatchEvaluator.evaluateValue` (single item = context, `$value`), themed with `ThemeManager.applyTo`, closed by `XsltDebugPane.dispose`; Call Stack frames open there too — `XsltDebugTraceListener` keeps each frame's `XPathContext` and captures context item + locals of the 32 innermost frames into `DebugStackFrame.contextXdm/localVariables`, `XsltDebugPane.resolveFrame` derives a frame-scoped snapshot for XPath; selecting a Call Stack frame switches Variables + watches to that scope via `XsltDebugPane.frameScope`/`showFrame`. Conditional breakpoints: `Breakpoint.condition` (XPath, stored as 4th escaped field by `BreakpointStore`), `DebugSession.checkAndPause(…, conditionSupplier, snapshotSupplier)` evaluates conditions and logpoints against a light snapshot (`XsltDebugTraceListener.buildConditionSnapshot`: context item, focus, local/global values, no rows/frames), captured once per pass; the full `buildPausedSnapshot` runs only on a pause — it asks its `ConditionEvaluator` (`XsltDebugController` → `WatchEvaluator.test`, EBV, compiled expressions cached; `WatchEvaluator.bindFocus` rewrites focus-level `position()`/`last()` to variables holding the snapshot focus; cost measured by the non-gating `perf/DebuggerPerfBenchmark` → `build/debugger-perf.txt`; the per-instruction path is lock-free: `DebugSession.armed` (an `Armed` record: enabled breakpoints + the line each is matched at + sorted lines; rebuilt by `rearm()` after every breakpoint change — new mutation paths must call it; a breakpoint inside a multi-line start tag is matched at the tag's end line via `StartTagLines` over the texts `XsltDebugController.launch` hands to `DebugSession.setSourceTexts`, `runTo` likewise), `hasSymbols`, the listener's `historyCount` and per-rule `matchPatterns` cache; template timing uses the call-stack entry's start time); an evaluation error pauses with `getPauseNote()`. Hit counts: `Breakpoint.hit` = `HitCondition` (always / = N / ≥ N / every N-th, 5th store field), counts per run in `DebugSession.hitCount` (a hit = a pass whose condition held; reset by `startSession`). `BreakpointPropertiesDialog.validate` checks condition + log message live with `ExpressionCheck` (Saxon static compile, undeclared variables allowed, XPST0081/XPST0017 ignored) and blocks OK on an error. Both edited via gutter right-click (`BreakpointGutterFactory.menuFor`) or Breakpoints → Edit… (`BreakpointPropertiesDialog`, FXML); `BreakpointTarget.updateBreakpoint` is the one edit path). Enable/disable: `BreakpointTarget.setEnabled` — `hasBreakpoint` reports *enabled* ones only, the gutter reads `breakpointAt` to draw disabled ones hollow; `DebugSession.setMuted` (header **Mute**) skips all breakpoints, steps still pause. **Pause** = `requestPause` (step-into while running, F6). Smart Step Into (Shift+F7 / right-click Step Into): `StepTargets.find` (calls on the paused line: user functions, `call-template`, `apply-templates` ± mode; multi-line tags read whole) → `XsltDebugPane` menu → `DebugSession.stepIntoTarget` (one-shot `StepTarget`, checked in `symbolBreak`, reported even while muted, dropped at any pause; `EditorHost.debugStepTargets` supplies the editor text). Steps are line-level: `checkAndPause` never step-pauses at `line < 1` or at an instruction nested in the last paused line's execution (`lastPausePath/Line/Depth`, cleared once an instruction at depth ≤ the paused one runs). **Run to Cursor** = one-shot `DebugSession.runTo` (dropped at any pause / new run); `XsltDebugController.setRunToOnStart` + `EditorHost.prepareDebugToCursor` start a run "to cursor"; entry points `UnifiedShellView.runToCursor` (Ctrl+F10, Tools menu) and gutter **Run to Here**. Input node: the run's input file URI goes to the engine (`transformWithDebugSession(xml, inputSystemId, …)`, callers set `EditorHost.setDebugInputFile`; open-document input is matched by text), `InputLocation.of(contextXdm)` gives system id + line, `EditorHost.markDebugInput` tints it (`XmlCodeEditorV2.setDebugInputLine`, style `debug-input-line`) and backs **Show input**; header buttons are icon-only (`fxt-debug-icon-action`). XQuery debugging: `XsltDebugController.Language.XQUERY` (set by `EditorHost.startXsltDebug` for `.xq` files; entry `EditorActions.debugActiveQuery`, Run ▾ → Debug Query, Shift+F5) → `XsltTransformationEngine.transformXQueryWithDebugSession` (compiled with tracing on the debug processor, same `XsltDebugTraceListener`, error reporter → `onError`); `DebugSession.setTagLineMapping(false)` (no XML start-tag mapping), gutters on XQUERY tabs too, `WatchEvaluator.namespacesOf` reads `declare namespace`, `StylesheetSymbols` finds `declare function`. Schematron debugging: `Language.SCHEMATRON` (`.sch` tabs; `EditorActions.debugActiveSchematron`, Run ▾ → Debug Schematron / Run primary / Shift+F5, Target dropdown shown for Schematron) → `XsltDebugController.runSchematron`: `service/SchematronDebugCompiler.compile` (ph-schematron SchXslt XSLT2 from the saved file, serialized indented, runs under the `.sch` URI) + `debugger/SchematronSourceMap` (structural pairing in document order: rule ↔ `xsl:template match=context` in a pattern mode, assert/report ↔ the `xsl:if` whose `xsl:attribute name="test"` text matches, `sch:let` ↔ `xsl:variable`/`xsl:param` by name, `xsl:function` element by element; `sch:include` expanded; unmatched → line -1) installed as `DebugSession.setSourceMapping` — the generic `SourceMapping` hook every location in `XsltDebugTraceListener` goes through (`sourceLocation`, also `onError`, `fn:trace`), so breakpoints, stepping, frames, coverage and Messages all see `.sch` lines; after the run `SchematronDebugCompiler.findings` turns the SVRL into Messages rows at the check's line. `fn:trace()` reaches the TraceListener (props label/value) → `XsltDebugTraceListener.setTraceSink` → Messages (XSLT too). Explain Match (Call Stack menu on template-rule frames): `TemplateRules.collect` (rules from `DebugSession.sourceText`, main = `getMainSourcePath`; import precedence post-order, includes share it) → `candidates(mode)` (default priorities §6.5, unions split) → each `testExpression` evaluated with `WatchEvaluator.evaluateValue` on the frame's context node → `MatchExplanation.explain` (winner + loss reasons) → non-modal `MatchExplanationDialog` (FXML). Coverage (header toggle, pref `debug.coverage`): `XsltDebugTraceListener.countLine` (per systemId `int[]`, only while `DebugSession.isCoverageEnabled`) → `publishCoverage` at `close()`; `DebugSession.coverageCount/coverageDead/deadComponents` (dead = no line of a `ComponentRanges` template/function ran; texts from `setSourceTexts`) → gutter classes `coverage-executed/coverage-dead` (`BreakpointTarget` defaults), Messages "Never ran" rows from `XsltDebugPane.reportCoverage`. Changed values: `ChangeTracker` (per frame scope key `XsltDebugPane.scopeKey` = enclosing template/function frame; keeps previous + current so re-showing the same state keeps the markers) → `VariablesView.setVariables(…, scopeKey)` / `WatchView.evaluateAll(…, scopeKey)`, CSS `fxt-debug-changed`, reset on run end. Variables is a `TreeTableView` (`VariablesView`, lazy `VariableTree.children`: attributes/children, sequence items, map entries, array members; each row's XPath via `Q{uri}local` steps, verified by evaluation in `VariableTreeTest`). XSLT context rows: `XsltDebugTraceListener.captureXsltContext` (mode, `getCurrentGroupIterator`, `getCurrentRegexIterator`, tunnel `ParameterSet`, accumulators via `XsltController.getAccumulatorManager`) → scope XSLT/PARAMETER rows + `PausedSnapshot.contextValues` (per frame too, display only); template rules in a named mode show `mode="…"` in the call stack; loop frames (`ForEach`, `ForEachGroup`, `ApplyTemplates`, `IterateInstr`) get " (n of m)" / " (iteration n)" from the focus the next inner frame was entered with (`XsltDebugTraceListener.loopPosition`). Editor text is debugged: `EditorHost.stylesheetText` (open tab's text, note when dirty) and `openStylesheetTexts` → `XsltDebugController.setModuleTexts` → engine `transformWithDebugSession(…, moduleTexts)` installs a `ResourceResolver` for includes/imports; texts are read on the FX thread at every (re)start (`onFxThread`). Launch configs: `DebugLaunchConfig` per stylesheet (`debug.launch.<hash>`), `DebugConfigurationDialog` (Tools → Debug Configuration…), `EditorHost.debugWithConfig` (used by Shift+F5 / Debug Transform when a config exists) and `rerunLastDebug` (Ctrl+Shift+F5, `LastLaunch` re-reads the input). Breakpoints panel groups by file (header in the first row of each file), row menu per-file actions and Export / Import (`BreakpointExport`, text format). Breakpoints follow edits: `EditorHost.followEditsWithBreakpoints` (RichTextFX `plainTextChanges`, whole-text replacements ignored) → `LineShift` → `BreakpointTarget.shiftLines` on the gutter's target (file store or session). Symbol breakpoints: `SymbolBreakpoint` (template match / name, function, mode) in `DebugSession.symbolBreak`, checked by `XsltDebugTraceListener.symbolBreak` on entering `TemplateRule`/`NamedTemplate`/`UserFunction` (mode from `XPathContext.getCurrentMode`); stored per main stylesheet via `WatchStore.saveSymbols`; suggestions `StylesheetSymbols.scan`; dialog `SymbolBreakpointDialog` (FXML). Input-node breakpoints: `SymbolBreakpoint.Kind.INPUT_NODE` (`inputNode(path, line, column, label)`: the start tag's end line and the column after its `>`, as Saxon's line-numbered `NodeInfo` reports them; `XmlElementAtCaret.Result.startTagEnd`) toggled by the XML editor's **Break When Processed** (`EditorHost.installInputNodeBreakpointMenu` / `toggleInputNodeBreakpoint`, visible while a session runs); `XsltDebugTraceListener.inputNodeBreak` checks only when the context node differs from the enclosing frame's (one pause per visit) → `DebugSession.inputNodeBreak` → `pauseAt`; hidden from `SymbolBreakpointDialog`. Logpoints: `Breakpoint.logMessage/suspend` (store fields 6/7), `DebugSession.setLogHandler` (controller: `WatchEvaluator.formatLog`, AVT-like `{xpath}` → Messages level LOG). Break on: `DebugSession.setBreakOnErrors/Terminate/Messages` + `pauseAt` (forced pause); `XsltDebugTraceListener.messageBreak` (before `MessageInstr`, terminate evaluated) and `onError` (wired into the engine's error reporter — Saxon reports before unwinding, the trace stack still holds the failing instruction's `XPathContext`); menu + prefs `debug.breakOn.*` in `BreakpointsView`; "Only error codes…" → `DebugSession.setBreakOnErrorCodes` / `breaksOnError(code)` (exact codes or `PREFIX*`, pref `debug.breakOn.errorCodes`), checked in `XsltDebugTraceListener.onError`. Secondary results / loaded documents: `XsltTransformationEngine.recordDebugArtifacts` (result-document handler into memory, base output URI, a resolver wrapper delegating to the secured config resolver, a wrapped `CollectionFinder`) → `DebugSession.resultDocuments / loadedDocuments` → Output tab selector and Documents menu. Output → source: the engine also sets `DebugSession.setOutputLength` (writer length); `XsltDebugTraceListener.attributeOutput` on every enter/leave appends (offset, module, line of the instruction active until then) to `DebugSession.outputSources()` (`OutputSourceMap`, binary-search `locate`); `OutputSoFarView` double-click → `XsltDebugPane.jumpToOutputSource`. Start tags are written lazily by the serializer, so they can map to the first inner instruction. Output so far: the engine sets `DebugSession.setOutputProbe(outputWriter::toString)` for the run; `OutputSoFarView` (Output tab) reads it at each pause. **Evaluate** (Alt+F8, `XsltDebugPane.onEvaluate`) opens the value window on the frame's context item, XPath focused; `ValueViewerWindow` keeps an app-wide XPath history (Up/Down). Inline values: `debugger/InlineValues.compute(text, pausedLine, snapshot)` (StAX, line = start-tag end line; locals of the paused top-level component up to the paused line, last declaration per name wins, completed globals) → `EditorHost.setDebugInlineValuesFor` (from `onDebugPaused`; `null` clears) → `EditorView.setDebugInlineValues` → `XmlCodeEditorV2`'s `InlineValueLayer` (mouse-transparent `Pane` stacked over the `VirtualizedScrollPane`, labels placed from `getCharacterBoundsOnScreen` on `viewportDirtyEvents`, cleared by any edit; CSS `fxt-debug-inline-value` in `unified-shell.css`). Hover values: `DebugHover` (RichTextFX `MouseOverTextEvent`, popup with `.root` + theme class so tokens resolve) on every debug module editor, `HoverExpressions.variableAt` / selection, evaluated in `XsltDebugPane.currentScope()`. Watches persist per main stylesheet via `WatchStore` (wired in `EditorHost.startXsltDebug`). After a completed run (`DebugSession.markRunEnded`, from `XsltDebugPane.onFinished`) `notReachedInLastRun` flags enabled breakpoints with zero hits (amber gutter dot, panel row)) (+ `Variables/CallStack/Watch/Messages/BreakpointsView`, `DebugToolbar`), `debugger/{DebugSession,Breakpoint,BreakpointTarget,FileBreakpoints,StylesheetModules,SourceLocations,BreakpointStore,WatchEvaluator,PausedSnapshot}`, `service/XsltDebugTraceListener` | Every saved XSLT tab carries a `BreakpointGutterFactory` (toggles on MOUSE_PRESSED, consumed — a click handler never fired because the press moved the caret and rebuilt the cell) backed by `FileBreakpoints` (persisted per file under the file's own `BreakpointStore` key; `EditorHost.installBreakpointGutter`). `EditorHost.startXsltDebug` seeds the `DebugSession` from the main file's store plus the modules `StylesheetModules` finds via `xsl:include`/`xsl:import`, swaps the gutters of main + open modules to the session, and `endXsltDebug` writes back per file (`BreakpointStore.saveByFile`) and restores the standalone gutters. It opens the stylesheet (execution line `XmlCodeEditorV2.setDebugCurrentLine`) and the DEBUG dock; the run uses the engine's **separate debug `Processor`** (`XsltTransformationEngine.transformWithDebugSession`, the editor text compiled with the file's systemId so includes resolve and breakpoints match per module); entry points: Transform panel **Debug**, Tools → Debug with XSLT… (Shift+F5), toolbar Transform ▾, Run ▾ → Debug Transform, FOP panel **Debug stylesheet…** | `controls/shell/editor/debug/`, `debugger/` |
| `ViewMode` | `TEXT` / `TREE` / `GRAPHIC` (no Grid mode — Graphic shows the XSD diagram for schemas, the XMLSpy-style instance grid for XML-family files) | `controls/shell/editor/` |
| `EditorView` + `XmlEditorView`/`JsonEditorView` (`EditorViews` factory) | Per-file-type text editor adapter (wraps `XmlCodeEditorV2`) | `controls/shell/editor/` |
| `ExplorerPanel`, `ValidationPanel`, `TransformPanel`, `FavoritesActivityPanel`, `FopPanel`, `FundsXmlPanel` | Side-panel content per activity | `controls/shell/editor/` |
| `SettingsPanel`, `SchemaLibraryView`, `SignatureView` + `PageNavigator` | Activities whose content is a main-area page (tool tab). Their side panel is a `PageNavigator`: the page's sections (Settings groups with search, library tabs, signature actions), current one marked. Tool tabs hide the Inspector (`UnifiedShellView` chrome updater) without changing the user's choice | `controls/shell/editor/` |
| `SchemaLibraryService` / `SchemaCatalogParser` | Namespace→schema mappings, catalogs, bundled list; consulted first by all resolvers | `service/`, `service/catalog/` |
| `EditorWelcomePane` | Welcome/Dashboard (quick actions, task list, stats/trend only once they show data; first start offers "Open an Example" = `ExampleFiles`, bundled in `resources/examples/getting-started/`) | `controls/shell/editor/` |
| `FundsXmlHint` + `FundsXmlHintBar` | One-time bar above the editor offering the FundsXML extension when a FundsXML document is opened while it is off (`EditorHost.setDocumentLoadedHandler`) | `controls/shell/editor/` |
| `GridViewShell` → `XmlGridView` / `JsonGridView` | Grid shown in Graphic mode for XML / JSON (shared header + placeholder) | `controls/shell/editor/` |
| `GridCanvasView<N>` + `GridModelAdapter<N>` | Model-agnostic XMLSpy-style grid canvas (render, scroll, hit-test, inline edit, search); `XmlCanvasView`/`XmlGridAdapter` bind XML, `JsonCanvasView`/`JsonGridAdapter` bind JSON. Block layout: each container's children are a nested block docked at its value column (own name-column width, `FlatRow.groupSiblings` for sibling groups); repeating groups are a `RepeatingElementsTable` beside the name cell, drawn and hit-tested only through its drawn-grid API (`drawnRowCount/drawnColumnCount/drawnColumnX/drawnRowTop/cellAt` → `DrawnCell` CORNER/header/row-number/data) so the flipped orientation (`setFlipped`) shares one renderer; expand state, selection, dragged widths, table/flip choices and open table cells (`cellExpansion`, row:column[:sub-row]) live in `GridViewState`, keyed by `GridModelAdapter.stableKey` (XML positional XPath, JSON path) so they survive the row rebuild after every edit; optional adapter hooks: `typeTargets`/`changeType` and `canDrag`/`dropRow`/`dropText` exist for XML and JSON (JSON moves across containers with `ReparentNodeCommand`), `sourceLine` (validation markers), `pasteCells`, filters and formulas are XML-only — multi-node table edits (insert/append row, insert/rename column, TSV paste) are built by `TableEditOps` as one `CompositeXmlCommand`, for JSON by `JsonTableEditOps` as one `CompositeJsonCommand` (both roll back on a failed part). XML entry helpers: `GridViewShell.sidePanel()` (header toggle, pref `grid.entryHelpers`) → `GridEntryHelpersPanel` (FXML) over `EntryHelperModel` (Siblings/Children/Values from `XmlSchemaProvider.getValidChildElements`/`getValidAttributes`/`isChildElementRequired`), following `GridCanvasView.addSelectionListener` | `controls/v2/xmleditor/view/`, `controls/jsoneditor/grid/` |
| `JsonEditorContext` + `controls/jsoneditor/commands/` | Shared JSON model + undo stack across Text/Tree/Graphic (JSON counterpart of `XmlEditorContext`) | `controls/jsoneditor/editor/` |
| `InspectorPanel` + `InspectorKeyValueList` | Right-hand Properties panel after Figma node 33:2 (top bar with favourite star / ⋮ menu, node header, full-width section bars, in-place editors that read as values; facet/attribute rows in `InspectorKeyValueList`). Schema facts come from `SchemaDerivation` (base type, derivation, compositor order) and the `EditorHost.resolveActiveXmlElement*` / `resolveActiveXmlAttributeInfo` / `resolveSchemaUsages` resolvers. For an XML element, `XsdInstanceLocator` maps the instance path to its declaration in the bound XSD's V2 model (`EditorHost.resolveActiveXmlSchemaDeclaration`); edits go through `editActiveXmlSchemaDeclaration` → `SchemaEditTarget` as commands on the XSD's own tab (opened behind the XML tab if needed; included/imported declarations stay read-only), and saving the XSD reloads the schema facts of XML tabs bound to it | `controls/shell/inspector/`, `controls/shell/schema/` |
| `XsdTreeView`, `TypeLibrary`, `NodeContextMenu`, … | Schema activity views | `controls/shell/schema/` |
| `*Runner` (ValidationRunner, TransformRunner, FopRunner, …) | Off-thread workhorses for panel actions | `controls/shell/editor/` |
| `SchemaAnalysisRunner` → `SchemaAnalysisData`; `SchemaAnalysisView` + `*Section` (Statistics, Types, Quality, Identity Constraints, XPath) | Schema Analysis tool tab; `SchemaAnalysisReport` builds the `report/ReportModel` that the CSV/JSON/HTML/PDF/XLSX writers render | `controls/shell/editor/analysis/`, `controls/v2/editor/statistics/{,report/}` |
| `SchemaReferenceGraph` | One reference graph per schema (globals, every `type`/`base`/`ref`/`substitutionGroup` edge, unreferenced vs. unreachable, Tarjan cycles) shared by statistics, quality checks, `TypeUsageFinder` and the flatten transformer | `controls/v2/editor/usage/` |

## XSD Editor V2 (model layer under the shell)

### Data Flow
```
XSD File → XsdNodeFactory → XsdNode Tree → shell views (Tree/Graphic)
                              ↓
                     PropertyChangeEvents
                              ↓
                    Commands (execute/undo)
```

| Class | Purpose | Location |
|-------|---------|----------|
| `XsdEditorContext` | Central coordinator | `controls/v2/editor/` |
| `CommandManager` | Undo/Redo stack (100 history) | `controls/v2/editor/commands/` |
| `SelectionModel` | Tracks selected nodes | `controls/v2/editor/selection/` |
| `XsdSerializer` | Model → XSD XML | `controls/v2/editor/serialization/` |
| `XsdNodeFactory` | XSD XML → Model | `controls/v2/model/` |

The XML-instance counterpart lives in `controls/v2/xmleditor/` (`XmlEditorContext`,
own `commands/`, `StreamingXmlParser`, canvas views). `EditorHost` keeps ONE shared
context per document across Text/Tree/Graphic so undo history survives mode switches.

### Command Pattern (CRITICAL)
```java
// WRONG - direct modification
element.setName("NewName");

// CORRECT - via command
commandManager.executeCommand(new RenameNodeCommand(element, "NewName"));
```

**34 Concrete Commands:**
- Structure (14): `AddGlobalTypeCommand` (top-level complex/simple type; dialog `controls/shell/schema/NewTypeDialog`, name rule `XsdEditRules.addGlobalType`), `AddElementCommand`, `AddContainerElementCommand`, `AddAttributeCommand` (both `.of(parent, NewElementSpec / NewAttributeSpec)` = new node with all properties in one step, checked by `XsdEditRules.newElementProperties` / `newAttributeProperties`; dialog `controls/shell/schema/NewNodeDialog`), `AddSequenceCommand`, `AddChoiceCommand`, `AddAllCommand`, `AddCompositorCommand`, `DeleteNodeCommand`, `DeleteNodesCommand` (several nodes, one undo step), `MoveNodeCommand` (graphic-view drag-and-drop: `controls/v2/view/XsdDropPlanner` computes parent/index before/after/into, rule check and shared-type confirmation), `CopyNodeCommand` (Ctrl-drag: deep copy at an exact index), `DuplicateNodeCommand`, `PasteNodeCommand`
- Properties (9): `RenameNodeCommand`, `ChangeCardinalityCommand`, `ChangeTypeCommand`, `ChangeFormCommand`, `ChangeUseCommand`, `ChangeSubstitutionGroupCommand`, `ChangeDocumentationCommand`, `ChangeDocumentationsCommand`, `ChangeAppinfoCommand`
- Facets (3): `AddFacetCommand`, `DeleteFacetCommand`, `EditFacetCommand`
- Constraints (5): `AddPatternCommand`, `DeletePatternCommand`, `AddEnumerationCommand`, `DeleteEnumerationCommand`, `ChangeConstraintsCommand`
- Assertions (2): `AddAssertionCommand`, `DeleteAssertionCommand`
- Persistence (1): `SaveCommand`

---

## Threading Model

| Context | Pattern |
|---------|---------|
| UI Updates | `Platform.runLater(() -> { ... })` |
| Background Work | `FxtGui.executorService.submit(() -> { ... })` |
| Periodic Tasks | `ShellBootstrap` scheduler |
| Thread Pool | `ThreadPoolManager` (centralized) |

---

## Auto-Update Subsystem

See `.claude/rules/auto-update.md` (loads when working on `*Update*` services or `update-helper/`).

---

## Observable Model Pattern
```java
// All XsdNode subclasses use PropertyChangeSupport
private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

public void setName(String name) {
    String oldName = this.name;
    this.name = name;
    pcs.firePropertyChange("name", oldName, name);  // Triggers UI update
}
```

---

## FXML

| File | Purpose |
|------|---------|
| `tab_unified_shell.fxml` | Bootstrap page; its `UnifiedShellController` instantiates `UnifiedShellView` |
| `shell.fxml` | The shell chrome (`fx:root` BorderPane: header · toolbar · status bar · work-area skeleton + `fx:id` placeholders), loaded by `UnifiedShellView`; **the Scene-Builder-editable layout** |
| `panels/<Class>.fxml` | Activity side panels and their panes (Explorer, Validation, Transform, FOP, FundsXML, Search, Favorites, Type Library, Problems, Transform Output) |
| `views/<Class>.fxml` | Main-area pages and embedded views (Settings, Schema Library, Signature, Welcome, Documentation, Favorites Manager, Query Console, Execution Stats, Schema Analysis + sections, grid header, XSD properties / documentation / appinfo editors, Schematron builder and template library) |
| `dialogs/<Class>.fxml` | Content of the `DialogPane`; buttons, result converter and theme stylesheets stay in code |
| `inspector/<Class>.fxml` | `InspectorPanel` and `AppInfoFieldsEditor` |

Every file is named after the class that loads it (`FxmlViews.load(this, "/pages/…")`, `fx:root`,
the class is root and controller). Panel FXMLs declare no `stylesheets=` — they inherit the shell
root's sheets so the `ThemeManager` Light/Dark switch keeps working. Canvas/editor views, cells and
data-generated content (`ActivityBar`, `FavoritesMenu`, `NodeContextMenu`, `FacetsPanel`, …) stay in code.

## CSS

- `css/design-tokens.css` — `-fxt-*` variables (Light + Dark), saturated blue `#1373D9` primary (Thunderbird-Supernova look); workflow families `-fxt-wf-<id>-{accent,fg,fill,bg,border,rail}` and action aliases `-fxt-action-*` (Java mirror: `controls/theme/Workflow`, `ActionColor`; guarded by `WorkflowTokensCssSyncTest`)
- Workflow colour scope: `ActivityBar` and `UnifiedShellView` put `fxt-wf-<id>` on the rail button / side-panel host (`WorkflowStyle.apply`); `unified-shell.css` scopes title, primary action, row hover and badge rules by that class
- `css/unified-shell.css` — all shell styling (`fxt-*` classes)
- `css/fxt-theme.css` — **generated** from `scss/input.scss` + partials (`cd src/main/resources/scss && npm run build`); edit the `scss/_*.scss` partials, never the CSS directly. `scss/xml-highlighting.css` is not part of that build — `XmlCodeEditorV2` loads it as is
- Figma source of truth: file `oqJVcInD6RgKaQ4dYmMWYh` ("FreeXmlToolkit — UI Modernization")
