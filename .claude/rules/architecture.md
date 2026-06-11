# Architecture Quick-Reference

## The Unified Shell (the entire UI)

```
FxtGui → /pages/tab_unified_shell.fxml → UnifiedShellController → UnifiedShellView
┌──┬──────────────┬──────────────────────────────────────┬─────────────┐
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

Everything lives in `controls/shell/` and is **built in code** (no FXML except the
bootstrap page). Legacy per-tool tabs/controllers are gone; `controller/` only keeps
`UnifiedShellController`, `TemplatesController`, and dialog controllers.

## Key Shell Classes

| Class | Purpose | Location |
|-------|---------|----------|
| `UnifiedShellView` | Shell layout, editor toolbar, status bar, activity→panel wiring | `controls/shell/` |
| `Activity` / `ActivityBar` / `ActivitySelectionModel` | Activity Bar (Explorer…Settings), stable ids | `controls/shell/` |
| `ShellBootstrap` | Startup/shutdown tasks, scheduler | `controls/shell/` |
| `EditorHost` | Center: file tabs (`EditorTab`), view-mode switching, schema/schematron binding, tool tabs, welcome page | `controls/shell/editor/` |
| `ViewMode` | `TEXT` / `TREE` / `GRAPHIC` (no Grid mode — Graphic shows the XSD diagram for schemas, the XMLSpy-style instance grid for XML-family files) | `controls/shell/editor/` |
| `EditorView` + `XmlEditorView`/`JsonEditorView` (`EditorViews` factory) | Per-file-type text editor adapter (wraps `XmlCodeEditorV2`) | `controls/shell/editor/` |
| `ExplorerPanel`, `ValidationPanel`, `TransformPanel`, `FavoritesActivityPanel`, `FopPanel`, `SignaturePanel`, `FundsXmlPanel`, `HelpPanel`, `SettingsPanel` | Side-panel content per activity (Settings opens as a main-area tab instead) | `controls/shell/editor/` |
| `EditorWelcomePane` | Welcome/Dashboard (quick actions, stats, tools grid) | `controls/shell/editor/` |
| `XmlGridView` → `XmlCanvasView` | Instance grid shown in Graphic mode for XML | `controls/shell/editor/`, `controls/v2/xmleditor/view/` |
| `InspectorPanel` | Right-hand Properties panel | `controls/shell/inspector/` |
| `XsdTreeView`, `TypeLibrary`, `NodeContextMenu`, … | Schema activity views | `controls/shell/schema/` |
| `*Runner` (ValidationRunner, TransformRunner, FopRunner, …) | Off-thread workhorses for panel actions | `controls/shell/editor/` |

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

**31 Concrete Commands:**
- Structure (11): `AddElementCommand`, `AddContainerElementCommand`, `AddAttributeCommand`, `AddSequenceCommand`, `AddChoiceCommand`, `AddAllCommand`, `AddCompositorCommand`, `DeleteNodeCommand`, `MoveNodeCommand`, `DuplicateNodeCommand`, `PasteNodeCommand`
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

The `AutoUpdateServiceImpl` orchestrates downloads and platform-specific
updater dispatch:

- **Windows:** Native Rust helper (`update-helper/` crate, ~500 KB binary)
  is bundled into the app-image at `<install>/fxt-update-helper.exe`.
  At update time, the helper is copied to `%TEMP%`, launched from there,
  and the install directory becomes free for overwrite. See
  `docs/superpowers/specs/2026-05-07-windows-auto-update-redesign.md`.
- **Mac/Linux:** No separate helper — `AutoUpdateServiceImpl` performs
  in-process recursive copy then exec's the new launcher (POSIX inode
  semantics allow overwriting files of running processes).

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

## FXML (only a handful of pages remain)

| File | Purpose |
|------|---------|
| `tab_unified_shell.fxml` | Bootstrap page hosting `UnifiedShellView` |
| `tab_templates.fxml` | Templates management |
| `pages/controls/*.fxml`, `pages/dialogs/*.fxml` | Reusable controls/dialogs |

## CSS

- `css/design-tokens.css` — `-fxt-*` variables (Light + Dark), Indigo `#3B5BDB` primary
- `css/unified-shell.css` — all shell styling (`fxt-*` classes)
- Figma source of truth: file `oqJVcInD6RgKaQ4dYmMWYh` ("FreeXmlToolkit — UI Modernization")
