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
| `MacMenuIntegration` | macOS only (NSMenuFX, the sole class referencing it): system menu bar, application menu (About/Updates/Settings/Hide/Quit), Window menu. The app-menu *title* comes from the bundle's `CFBundleName`, not from code | `app/` |
| `Activity` / `ActivityBar` / `ActivitySelectionModel` | Activity Bar (Explorer…Settings), stable ids | `controls/shell/` |
| `ShellBootstrap` | Startup/shutdown tasks, scheduler | `controls/shell/` |
| `EditorHost` | Center: file tabs (`EditorTab`), view-mode switching, schema/schematron binding, tool tabs, welcome page | `controls/shell/editor/` |
| `ExternalChangeMonitor` + `DiskStamp` / `ExternalChangePrompt` | Polls open tabs' files off-thread (2 s while the window is focused + on focus gain; no `WatchService` — network drives); `EditorHost.handleExternalChanges` asks Reload/Ignore or Keep/Close. Every editor write must go through `EditorHost.write()` so the stamp follows | `controls/shell/editor/` |
| `ViewMode` | `TEXT` / `TREE` / `GRAPHIC` (no Grid mode — Graphic shows the XSD diagram for schemas, the XMLSpy-style instance grid for XML-family files) | `controls/shell/editor/` |
| `EditorView` + `XmlEditorView`/`JsonEditorView` (`EditorViews` factory) | Per-file-type text editor adapter (wraps `XmlCodeEditorV2`) | `controls/shell/editor/` |
| `ExplorerPanel`, `ValidationPanel`, `TransformPanel`, `FavoritesActivityPanel`, `FopPanel`, `FundsXmlPanel` | Side-panel content per activity | `controls/shell/editor/` |
| `SettingsPanel`, `SchemaLibraryView`, `SignatureView` + `PageNavigator` | Activities whose content is a main-area page (tool tab). Their side panel is a `PageNavigator`: the page's sections (Settings groups with search, library tabs, signature actions), current one marked. Tool tabs hide the Inspector (`UnifiedShellView` chrome updater) without changing the user's choice | `controls/shell/editor/` |
| `SchemaLibraryService` / `SchemaCatalogParser` | Namespace→schema mappings, catalogs, bundled list; consulted first by all resolvers | `service/`, `service/catalog/` |
| `EditorWelcomePane` | Welcome/Dashboard (quick actions, task list, stats/trend only once they show data; first start offers "Open an Example" = `ExampleFiles`, bundled in `resources/examples/getting-started/`) | `controls/shell/editor/` |
| `FundsXmlHint` + `FundsXmlHintBar` | One-time bar above the editor offering the FundsXML extension when a FundsXML document is opened while it is off (`EditorHost.setDocumentLoadedHandler`) | `controls/shell/editor/` |
| `GridViewShell` → `XmlGridView` / `JsonGridView` | Grid shown in Graphic mode for XML / JSON (shared header + placeholder) | `controls/shell/editor/` |
| `GridCanvasView<N>` + `GridModelAdapter<N>` | Model-agnostic XMLSpy-style grid canvas (render, scroll, hit-test, inline edit, search); `XmlCanvasView`/`XmlGridAdapter` bind XML, `JsonCanvasView`/`JsonGridAdapter` bind JSON | `controls/v2/xmleditor/view/`, `controls/jsoneditor/grid/` |
| `JsonEditorContext` + `controls/jsoneditor/commands/` | Shared JSON model + undo stack across Text/Tree/Graphic (JSON counterpart of `XmlEditorContext`) | `controls/jsoneditor/editor/` |
| `InspectorPanel` | Right-hand Properties panel | `controls/shell/inspector/` |
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

**32 Concrete Commands:**
- Structure (12): `AddElementCommand`, `AddContainerElementCommand`, `AddAttributeCommand`, `AddSequenceCommand`, `AddChoiceCommand`, `AddAllCommand`, `AddCompositorCommand`, `DeleteNodeCommand`, `DeleteNodesCommand` (several nodes, one undo step), `MoveNodeCommand`, `DuplicateNodeCommand`, `PasteNodeCommand`
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
