# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Detailed technical references are in `.claude/rules/`:**
> - `architecture.md` - Application layers, V2 data flow, controllers, threading, observable pattern
> - `domain.md` - the 38 XsdNode types, circular-reference warning
> - `quick-reference.md` - Build commands, command template, icons, FXML rules, testing, gotchas
> - `xsd-editor-v2-details.md` - Type Editor, CommandManager, XsdEditorContext, facets, serialization, package structure

## Build Commands

```bash
./gradlew run                    # Run the application
./gradlew run -Pheap=12g         # Run with a larger max heap (-Xmx)
./gradlew test                   # Run all tests
./gradlew clean build            # Clean and build
./gradlew test --tests "ClassName.methodName"  # Single test
./gradlew createAllExecutables   # All native packages
./gradlew dependencyUpdates      # Check for dependency updates
./gradlew runCli --args="validate src/test/resources"   # Command line (fxt-cli)
```

**Build Details:** Java 25 with preview features, JavaFX 25 (bundled in Liberica Full JDK), Gradle 9.x with Kotlin DSL.

## Release Checklist

For version bumping, `VersionUtil` resolution, and the release/tag workflow (including
the tag-pinning gotcha), invoke the **`release` skill** (`.claude/skills/release/SKILL.md`).

## Application Entry Point

**Main Class:** `org.fxt.freexmltoolkit.FxtGui` — extends `javafx.application.Application`, loads `/pages/tab_unified_shell.fxml` (→ `UnifiedShellController` → `UnifiedShellView`), initializes logging, fonts, CSS hot-reloading, platform icons, and `ShellBootstrap` startup tasks.

## Command Line (fxt-cli)

`org.fxt.freexmltoolkit.cli.FxtCli` (picocli): `validate`, `transform`, `xsd-doc`, `xspec`, `scenario`, `compare`, `merge`, `convert`, `fop`. The
subcommands only parse options and call the UI-free runners in `controls/shell/editor/`
(`ValidationRunner`, `TransformRunner`, `XProcRunner`, `DocumentationRunner`, `FopRunner`) — put
new logic there so the shell and the CLI share it. Logs go to stderr (`log4j2-cli.xml`), stdout is
for results; no telemetry. Exit codes in `cli/ExitCodes`. Packaged as a second jpackage launcher
(`--add-launcher fxt-cli=…`, properties written in `createJPackageTask`). User docs:
`docs/command-line.md`; tests: `FxtCliTest`.

## Compare, Merge, Conversion

UI-free services in `service/`: `XmlStructuralDiff` (XMLUnit 2; DOM built from SAX with line numbers
as user data), `FolderComparison`, `ThreeWayMerge` (diff3 on java-diff-utils, Git markers),
`DataFormatConverter` (XML ↔ JSON ↔ YAML). XSLT profiler: `XsltProfilingTraceListener` → `XsltProfile`
(components with total/self, call tree, lines) via `XsltTransformationEngine.profileTransformation`
(compiled with tracing on the debug processor, warm-up runs); UI `controls/shell/editor/debug/ProfileView`
+ `FlameGraph` (Transform panel → Profile run), CLI `transform --profile`. UI: `controls/diff/DiffView` (Text | XML mode,
`XmlDiffPanel`), `MergeView` + `MergeFilesDialog`, `controls/shell/editor/FolderCompareView`,
`DataConversionAction`. CLI: `compare`, `merge`, `convert`. Diff colours: `-fxt-diff-*` tokens.

## Large File Viewer

`service/largefile/`: `LargeFileIndex` (sparse row index, every 1024th row's offset; rows ≤ 4 KB, read
from disk on demand; UTF-8/single-byte only), `LargeXmlStructure` (byte-level scanner: children of one
element a page at a time, byte ranges, XPaths, fields), `LargeFileValidation` (streaming XSD/well-formedness
via `XercesXmlValidationService.validateStream`). UI `controls/shell/editor/LargeFileView` (Text | Problems |
Structure; `ListView` over `RowNumbers`, a list that stores nothing — a standalone `VirtualFlow` rendered no
cells), opened by `EditorHost.openLargeFileViewer`, offered by `EditorFileSync` for files ≥ 50 MB / too large.
`fxt-cli validate` streams XML files ≥ 64 MB. Docs: `docs/large-files.md`.

## Projects

`service/project/`: `FxtProject` (`.fxtproj`, JSON via Gson, paths relative to the file: folders,
`schemaBindings` glob → XSD with `**/` also matching no folder, catalogs, scenarios) and `ProjectService`
(one open project, listeners, `project.last` reopened by `ShellBootstrap`, catalogs via
`SchemaLibraryService.setProjectCatalogs` — before the user's, never saved). Bindings win in
`EditorSchemaBinding.detectSchemaFor` (`SchemaSource.PROJECT`). Scenarios run with
`controls/shell/editor/ScenarioRunner` (XSLT/XQuery/XProc by extension). UI: File → Project
(`ProjectActions`, `BindSchemaDialog`), Transform panel SCENARIO section (`ScenarioDialog`), Explorer
workspace = first project folder. CLI: `scenario`, `validate --project`. Docs: `docs/projects.md`.

## Plugins

`plugin/`: `FxtPlugin` (ServiceLoader, `META-INF/services/org.fxt.freexmltoolkit.plugin.FxtPlugin`) registers
through `PluginContext`: `PluginActivity` (rail button + side panel factory + visibility), `PluginMenuItem`
(Tools/Help, ids `menu-tools-<id>` / `menu-help-<id>`), `DocumentValidator` (appended in
`ValidationRunner.runWithReport`, so panel, F8 and CLI), startup tasks (`ShellBootstrap`), `DocumentListener`
(`EditorHost` document-loaded hook → `PluginUi.showEditorBar`), stylesheets. `PluginManager` loads built-ins and
the jars in `~/.freeXmlToolkit/plugins` (`-Dfxt.plugins.dir`, own `URLClassLoader` each; policy
`plugins.external.allowed`), skips `plugin.<id>.enabled=false`, keeps a failing plugin's contributions out.
`Activity` is a class, not an enum: built-in constants + plugin activities from `values()` (before SETTINGS),
`createSidePanel` switches on `activity.id()`. The UI side is `ShellPluginUi`. FundsXML is the reference plugin
(`controls/shell/editor/FundsXmlPlugin`; its Settings card and Welcome section are still core). Settings →
Extensions → PLUGINS lists them. Docs: `docs/plugins.md`.
Industry plugins (`plugins/`, off by default via `enabledByDefault()`): `iso20022` (schemas via
`PluginContext.addSchema` → Schema Library source PLUGIN, control sums, EPC SEPA rules), `ebinterface`,
`findatex` (EMT/EET/EPT CSV against `plugins/findatex/templates.json` from `scripts/findatex-fields.py`),
`xbrl` (runs the user's Arelle, `ArelleRunner`). Shared: `IndustryPanel` (FXML), `IdentifierKind`,
`service/finance/FinancialIdentifiers` (IBAN, BIC, LEI, ISIN, AT UID), `plugin/XmlScan`. Docs: `docs/industry-plugins.md`.

## Administrator Policy

`service/AdminPolicy` reads machine-wide `policy.properties` (locked) and `defaults.properties`
(`%ProgramData%\FreeXmlToolkit`, `/Library/Application Support/FreeXmlToolkit`, `/etc/freexmltoolkit`,
or `-Dfxt.policy.file` / `FXT_POLICY_FILE`); installed first thing in `FxtGui.main` / `FxtCli.main`
(`fxt.*` keys become system properties). `PropertiesServiceImpl` keeps the user's values in an
overlay (`AdminPolicy.PolicyProperties`), `loadProperties()` returns `AdminPolicy.effective(...)`
(a plain copy that survives `putAll`), `saveProperties` stores only `userValues(...)` — locked values
never reach the user's file. `update.allowed=false` disables update check/menu/install. Settings
page: new controls must be added to `SettingsPanel.policyKeys()`, and disable bindings go through
`bindDisable(...)`. Docs: `docs/administration.md`.

## Architecture Overview

**The UI is the Unified Shell** (`controls/shell/`, VS-Code-style): an Activity Bar switches the left side panel (Explorer, Search, Favorites, Validation, Transform, Schema, Schema Library, PDF/FOP, Signature, FundsXML, Settings; Help is the menu bar's Help menu, also behind the header's ? button and F1). Settings, Schema Library and Signature are pages in the main area — their side panel is a `PageNavigator` listing the page's sections; the center is one `EditorHost` with file tabs and per-document view modes (Text / Tree / Graphic); right is the Properties Inspector. There is **no** per-tool tab/controller architecture anymore — `controller/` only contains `UnifiedShellController`.

**Pattern:** The shell **chrome** (root layout, header bar, editor toolbar, status bar, work-area skeleton) lives in `pages/shell.fxml` and is loaded by `UnifiedShellView` via the `fx:root` pattern (`loader.setRoot(this)/setController(this)`), so it is editable in Scene Builder. `UnifiedShellView.initialize()` wires the dynamic, `EditorHost`-dependent custom controls (ActivityBar, Inspector, side panels, Query Console, view switch) into the FXML `fx:id` placeholders — those can't live in FXML because they need constructor args. The XSD Editor V2 model layer (`controls/v2/`) uses MVVM with Command Pattern and backs the shell's structured views.

**Layout lives in FXML, behaviour in Java.** Side panels, main-area pages, dialogs and the
Inspector follow the same `fx:root` pattern: the class loads `pages/{panels,views,dialogs,inspector}/<ClassName>.fxml`
in its constructor via `FxmlViews.load(this, …)` (dialog content: `FxmlViews.loadContent(this, …)`)
and is its own controller, so constructor signatures stay unchanged. Static structure, texts, icons
and `id`s go into the FXML; cell factories, bindings and data-driven lists stay in code. Recurring
building blocks are FXML tags (`SidePanelHeader`, `SidePanelSection`, `PanelActionList` /
`PanelActionButton`, `SourceRow`, `SettingsCard` in `SettingsCardGrid`, `Welcome*Card`). `FxmlContractTest` checks every
FXML against its class (`@FXML` fields ↔ `fx:id`, handlers public, unique `id`s). See
`.claude/rules/quick-reference.md` → "FXML Views".

> **Scene Builder note:** this project runs on Java 25 with `--enable-preview`; Scene Builder (JDK ~21) cannot load/render the bundled custom controls (`IconifyIcon`, `EditorHost`, …), so they appear as non-rendering placeholder tags. Standard JavaFX nodes (layout, buttons, labels) edit normally. `@FXML` handlers must be `public` (non-modular jpackage build).

See `.claude/rules/architecture.md` for the shell layout, panel/class table, threading model, and observable pattern. See `.claude/rules/xsd-editor-v2-details.md` for V2 internals.

**Key Principle:** All model modifications MUST go through commands (never modify XsdNode directly from UI code). See `.claude/rules/quick-reference.md` for the wrong/right example.

## Critical Architecture Principles

1. **Model-View Separation:** XsdNode has zero UI dependencies
2. **Command Pattern for Editing:** All changes through commands
3. **Observable Properties:** PropertyChangeSupport for reactive updates
4. **Immutable IDs:** UUID-based node identification
5. **Deep Copy Support:** All nodes support duplication
6. **Test-Driven:** Comprehensive test coverage
7. **Incremental Rendering:** Only changed nodes refresh

## UI Guidelines

**IMPORTANT:** When modifying or creating UI components, ALWAYS consult **[STYLE_GUIDE.jsonc](STYLE_GUIDE.jsonc)** first!

The style guide covers: color palette, typography, icons, components, CSS classes, and usage guidelines.

### Core Principles

- Use `xmlspy-style-config.json` for general styling
- Modern and colorful styled UI (custom `-fxt-*` design-token theme)
- Menus and context menus should always have icons and text
- User-friendly error dialogs and alerts
- Graceful degradation on errors

### Icon Usage (Critical)

Icons are rendered by the project's own **`IconifyIcon`** control
(`org.fxt.freexmltoolkit.controls.icons.IconifyIcon`), backed by bundled
[Iconify](https://iconify.design/) SVG data — **not** Ikonli. The control is a drop-in
replacement for the old `FontIcon`: same `iconLiteral` / `iconSize` / `iconColor` attributes.

1. **Use Bootstrap Icon names** (`bi-*` prefix); browse them at https://icons.getbootstrap.com/
   (the bundled set is `src/main/resources/icons/iconify/bi.json`).
2. **Unknown icons never crash.** An unresolved literal renders a placeholder and logs a warning
   instead of throwing (the old Ikonli failure mode is gone).
3. **`IconifyIconCoverageTest` is the safety net:** it fails the build if any `bi-*` literal in
   FXML/Java is not in the bundle. Run `./gradlew test --tests "*IconifyIconCoverageTest"`.
4. **Standard sizes:** Menu items: `iconSize="16"`, Toolbar: `iconSize="20"`, Empty states: `iconSize="48"` or `iconSize="64"`
5. **Colours are roles, not hex:** menu items and panel action rows take an `ActionColor`
   (`SemanticIcon.paint(icon, ActionColor.DELETE)`, or `SemanticIcon.bind(...)` where CSS would
   override it); chrome that names a workflow (rail, panel title, primary action, toolbar group,
   status badge) takes the activity's `Workflow` family (`-fxt-wf-<id>-*` tokens, class `fxt-wf-<id>`).
   See `STYLE_GUIDE.jsonc` → `colors.workflowColors` and `controls/theme/{Workflow,ActionColor}.java`.

### Example FXML Icon Usage

Requires `<?import org.fxt.freexmltoolkit.controls.icons.IconifyIcon?>`.

```xml
<Button onAction="#handleSave">
    <graphic>
        <IconifyIcon iconLiteral="bi-save" iconSize="20"
                  iconColor="#17a2b8" styleClass="toolbar-icon-info"/>
    </graphic>
</Button>

<MenuItem text="Delete" onAction="#handleDelete">
    <graphic>
        <IconifyIcon iconLiteral="bi-trash" iconSize="16" iconColor="#dc3545"/>
    </graphic>
</MenuItem>
```

### Accessibility

`controls/shell/AccessibleNames` names controls for screen readers in every window (icon button →
tooltip's first line or inner label, input → `Label` before it). Give icon-only buttons a tooltip or
`accessibleText`. Text in the primary colour: `-fxt-primary-text`; fills behind `-fxt-on-primary`:
`-fxt-primary-fill`; warning text: `-fxt-warning-text`. `AccessibilityAuditTest` (no unnamed
controls; controls outside the tab order need an entry in `KEYBOARD_ALTERNATIVES`) and `ContrastTest`
(WCAG AA 4.5:1) guard it. Docs: `docs/accessibility.md`.

### CSS Style Classes

- **Buttons:** `.btn-primary`, `.btn-success`, `.btn-danger`, `.toolbar-button`
- **Cards:** `.card`, `.action-card`, `.statistics-card`
- **Tabs:** `.primary-tab`, `.output-tab`, `.utility-tab`
- **Toolbars:** `.xsd-toolbar`, `.toolbar-icon-success`, `.toolbar-icon-danger`
- **Dialogs:** `.dialog-header-primary`, `.dialog-info-box`

## Important Development Guidelines

- **Always create unit tests** for new features
- **Always check** no existing implementation is broken
- **Comments and JavaDoc** in English
- **Refactor automatically:** Split large classes, delete unused code, avoid reflections
- **Error handling:** Comprehensive management, user-friendly alerts, graceful degradation

## Common Mistakes to Avoid

- **Icons:** Use `IconifyIcon` with `bi-*` names. Unknown names no longer crash (placeholder +
  log), but `IconifyIconCoverageTest` fails the build for them — keep literals valid.
- **CSS beats `setIconColor()`:** themed CSS rules (`-fx-icon-color`) override programmatically
  *set* styleable properties on every CSS pass — `bind()` the property to keep a per-item color.
- **XSD Infinite Loops:** Child nodes can reference parent types. Always check circular references.
- **FXML Controller Methods:** All `@FXML` methods MUST be `public` (module system requirement for jpackage).
- **Stale search hits:** ignore `.claude/worktrees/*` — old git worktrees may contain outdated
  copies of the sources.

## Project-Specific Patterns

- **XSLT/XQuery:** Saxon HE 12.10 for XSLT 3.0, XPath 3.1, XQuery 3.1 (pinned by XML Calabash 3)
- **XProc:** XML Calabash 3.0.54 for XProc 3.0 pipelines (Run Pipeline on .xpl documents)
- **XML Validation:** Xerces 2.12.2 (exist-db fork) for XSD 1.1
- **UI Framework:** JavaFX 25 with the project's own `-fxt-*` design-token CSS theme
- **Icons:** `IconifyIcon` control (bundled Iconify Bootstrap Icons SVG); graceful fallback on unknown names
- **Async Operations:** `Platform.runLater()` for UI updates from background threads
- **Background Tasks:** `FxtGui.executorService` or `ThreadPoolManager`

## Known Limitations

**Schema Support:** XSD 1.0/1.1 (full), Schematron (full, via ph-schematron/SchXslt on Saxon; schematrons without `@queryBinding` compile as xslt2). DTD and RELAX NG (.rng/.rnc): validation (Xerces DTD / Jing) and IntelliSense via a Trang-translated XSD (`service/grammar/`, `GrammarStage` in `ValidationRunner`, `EditorSchemaBinding.detectDeclaredGrammar` / `loadForIntelliSense`; policy `security.dtd.allowed`); no editor for them. OASIS XML catalogs are supported (system/public/uri/rewrite*/nextCatalog) via `SchemaLibraryService`.

**Schematron Quick Fixes (SQF):** `service/sqf/` (parser/correlator/execution engine) + `controls/shell/editor/quickfix/` (UI). Fixes are applied as formatting-preserving text-range edits (one native undo step). Not supported: external `sch:extends href`, abstract patterns for fix discovery, `$sqf:match`, `@use-for-each`. See `docs/schematron-quick-fixes.md`.

**XSD Editor V2:** Imported schemas are resolved **transitively** (local file, HTTP via `SchemaResourceCache`, or namespace-URL download — `NamespaceSchemaDownloader`): nested imports/includes inside imported schemas and imports declared in included files resolve too (inside a remote schema relative locations resolve against its URL, and only http(s) targets are allowed), cycle-safe with an import depth cap of 10, flattened into the root schema's imported-schema map. `xs:redefine`/`xs:override` load their file as a separate schema (`XsdSchemaReference.getReferencedSchema`, also listed in the imported-schema map, never inlined); `XsdRedefinitions` holds their semantics (which target definitions are replaced, a self-reference inside `xs:redefine` means the original) for the Graphic view, `SchemaReferenceGraph`, statistics, quality checks, `TypeLibrary`, `XsdInstanceLocator` and `SchemaFacets`; flattening and "Remove unused" still refuse such schemas, also when declared inside an included file (the node stays on the root schema, tagged as from the include, so saving leaves it there).

## Performance Requirements

- **Background Loading:** All loadable data MUST be loaded asynchronously. Never block the UI thread.
- **Lazy Loading:** Load data on-demand where possible.

## Usability Requirements

- **Preserve View State:** Expanded/collapsed nodes must remain after edits.
- **Workflow Continuity:** Multiple sequential actions without interruption.
- **All User-Facing Text in English** as source text, translated into German: texts live in
  `i18n/messages.properties` / `messages_de.properties` (`org.fxt.freexmltoolkit.i18n.I18n`; language
  chosen at start from `-Dfxt.locale`, setting `ui.language`, or the system; tests run with `fxt.locale=en`).
  FXML uses `%Layout.key` (run `scripts/i18n-extract-fxml.py` after adding texts), Java `I18n.t(key, args…)`;
  `I18nBundlesTest` requires every key in both bundles. Many Java-built texts are still plain English.

## Documentation

**Architecture:** `docs/ThreadPoolArchitecture.md`

**Features:** `docs/unified-shell.md` (shell user guide), `docs/context-sensitive-intellisense.md`, `docs/favorites-system.md`, `docs/xml-editor.md`, `docs/grid.md`, `docs/debugger.md`

**User Documentation:** `docs/index.md` (MkDocs site), `README.md`

**UI/Styling:** `STYLE_GUIDE.jsonc`
