# Legacy Chrome Retirement — Sub-project B: Make the Shell the Root + Relocate Globals — Design

**Date:** 2026-06-07
**Branch:** `feature/ui-rebuild-unified-editor`
**Status:** Approved design — ready for implementation plan

## Context

Sub-project A made the Unified Shell feature-complete versus the legacy chrome.
This is sub-project **B** of the three-step chrome retirement (A → B → C):

- **A (done):** close the shell feature gaps.
- **B (this spec):** make the shell the top-level root scene AND relocate the
  global concerns that today live only on `MainController`. After B, the legacy
  chrome (`main.fxml`, `MainController`, `WelcomeController`, `SettingsController`,
  `welcome.fxml`, `settings.fxml`) is **orphaned but still present** — never
  loaded, dead at runtime.
- **C (later):** delete the orphaned chrome files.

**Decision (2026-06-07):** B does the boot flip together with the relocation
(the user chose "flip + globals together; C = just deletion"). The moment FxtGui
loads the shell directly, the chrome is functionally dead, so the risky cutover
is concentrated here; C becomes a mechanical deletion.

## Goal

FxtGui loads the shell as the root scene, and every global concern currently
owned only by `MainController` has a new home in the shell or a thin boot layer —
so nothing is lost when `MainController`/`main.fxml` stop being loaded.

## What the shell already owns (no work in B)

From the codebase map:
- **Status-bar memory monitor** — `UnifiedShellView.buildStatusBar()` runs its own
  ~2s Timeline; the legacy footer monitor simply stops existing after the flip.
- **File-op keyboard shortcuts** — `UnifiedShellView.handleShortcut()` already
  covers Ctrl+N/O/S/Shift+S/F/H/F8, scoped to the shell.
- **File-open API** — `UnifiedShellView.openFile(Path/File)`, `openXsdAndReveal`,
  `getEditorHost().getActiveText()`; `EditorHost.openFile` is the central open path.
- **No shell → MainController coupling** exists (only MainController → shell
  bridges), so nothing live calls back into MainController after the flip.

## The boot flip

`FxtGui.start(Stage)` currently loads `/pages/main.fxml` → `MainController`. After B
it loads `/pages/tab_unified_shell.fxml` → `UnifiedShellController` → `UnifiedShellView`
(a `BorderPane`, self-contained) as the scene root. Splash, taskbar icons,
maximize, CSSFX, fade-in, and usage-tracking start stay unchanged. Two additions
to `start()`: apply the initial theme (via `ThemeManager`) and schedule startup
tasks (via `ShellBootstrap`).

`FxtGui.stop()` is rewired to do shutdown cleanup itself (it must not reference a
`MainController` instance that no longer exists) — see relocation 5.

## The five relocations

### 1. Theme application → new `ThemeManager`

Today `MainController.applyTheme()` swaps `light-theme.css`/`dark-theme.css` on the
Scene AND sets the root style class `fxt-theme-{dark,light}`; the shell's
`SettingsPanel.applyTheme(boolean)` only sets the style class + persists
`ui.theme` (it does NOT load the CSS — a real gap). Introduce a `ThemeManager`
(util/service) with `apply(Scene scene, boolean dark)` (swap the theme stylesheet
+ toggle the root style class + `PropertiesService.set("ui.theme", …)`) and
`currentIsDark()` (read the property). Called by `FxtGui.start` (initial theme on
the shell scene) AND by the shell `SettingsPanel` toggle (replacing its
incomplete `applyTheme`). `MainController.applyTheme` becomes dead (deleted in C).

### 2. Drag & drop → into the shell

Today on `MainController.contentPane` (global). Move into `UnifiedShellView`:
register `setOnDragOver`/`setOnDragDropped` on the shell, filter via the existing
`DragDropService` (same extension set), and route each supported file to
`editorHost.openFile(...)`. Since the shell opens all file types itself
(XML/XSD/XSLT/Schematron/JSON), the legacy `routeFileByType` switch is dropped.

### 3. Recent files on open → into the shell

Ensure a document opened in the shell is recorded via
`PropertiesService.addLastOpenFile(file)` (today done by
`MainController.addFileToRecentFiles`). Put it in `EditorHost.openFile(Path)` (the
central open path). The `ExplorerPanel` RECENT list reads the same property, so it
stays consistent. (Verify whether `EditorHost.openFile` already records recents;
if so, this is a no-op confirmation + test.)

### 4. Update checks → new `ShellBootstrap`

The app-update and FundsXML-update startup scheduling (today on
`MainController.scheduler`, +2s/+5s, throttled) moves into `ShellBootstrap`: it
owns its own `ScheduledExecutorService`, exposes `scheduleStartupTasks()` (runs
`UpdateCheckService.checkForUpdates()` → on an available update shows the existing
`UpdateNotificationDialog` on the FX thread; plus the
`FundsXmlUpdateChecker.runIfDue()` block) and `shutdown()`. Called from
`FxtGui.start` after the scene is built.

### 5. Shutdown / cleanup → FxtGui takes over directly

`MainController.shutdown()` shut down `UpdateCheckService`,
`XsltTransformationEngine`, `XPathExecutionEngine`, `ThreadPoolManager`, and its
own `scheduler`/`service` executors. Since `MainController` is no longer loaded,
`FxtGui.stop()` performs this cleanup itself: delegate the scheduler shutdown to
`ShellBootstrap.shutdown()` and shut down the engines + `ThreadPoolManager`
directly. No `MainController` reference remains in `FxtGui.stop()`.

## Out of scope (sub-project C)

- Deleting the orphaned chrome: `main.fxml`, `MainController`, `WelcomeController`,
  `SettingsController`, `welcome.fxml`, `settings.fxml`, the MenuBar/sidebar/footer,
  and their tests. B leaves these present-but-unloaded.
- After B, `MainController.applyTheme`/`updateMemoryUsage`/`setupKeyboardShortcuts`/
  `initializeDragAndDrop`/the update-scheduling/the bridge methods are all dead code
  — removed in C.

## Testing strategy

TDD; pull boot-near logic into testable units:
- **`ThemeManager`:** apply to a test Scene → assert the theme stylesheet swap +
  root style class + `PropertiesService` persistence (TestFX, headless-safe).
- **Recent-on-open:** open a temp file via `EditorHost.openFile`, assert it appears
  in `PropertiesService.getLastOpenFiles()`.
- **Drag & drop:** extract the routing decision into a testable helper
  (`acceptsDrop(List<File>)` / the open loop) and unit-test it; plus a smoke that
  `UnifiedShellView` installs the handlers.
- **`ShellBootstrap`:** construction + `shutdown()` idempotent / no throw;
  `scheduleStartupTasks()` does not throw (the live update check is network — not
  exercised).
- **Boot flip:** repurpose `AppBootsIntoShellTest` to assert FxtGui loads the shell
  as the root (or that `tab_unified_shell.fxml`'s root is `UnifiedShellView`);
  adapt `UnifiedShellIntegrationTest` (the legacy-footer-restore assertions become
  obsolete once the chrome isn't loaded — adjust or drop those cases).
- Mind the documented headless TestFX toolkit-init cascade — verify per class.

## Build sequence — 5 increments (1–4 are safe refactors with boot UNCHANGED; 5 is the cutover)

1. **`ThemeManager`** — extract theme logic; both the shell `SettingsPanel` and
   `MainController.applyTheme` delegate to it (closes the SettingsPanel CSS gap).
   Boot unchanged.
2. **Recent-on-open** in `EditorHost.openFile`. Boot unchanged.
3. **Drag & drop** in `UnifiedShellView` (shell-scoped; MainController's global
   handler stays redundantly alongside until the flip — harmless). Boot unchanged.
4. **`ShellBootstrap`** — extract update-check scheduling;
   `MainController.initialize` calls `ShellBootstrap.scheduleStartupTasks()`
   instead of its own code. Boot unchanged.
5. **The flip** — FxtGui loads `tab_unified_shell.fxml` as root; initial theme via
   `ThemeManager`; `ShellBootstrap` call; `FxtGui.stop()` does cleanup directly (no
   MainController reference). Repurpose `AppBootsIntoShellTest`. After this the
   chrome is orphaned-but-present.

Risk is concentrated in step 5; steps 1–4 are independently testable/reviewable.

After B completes and is verified, **C** (delete the orphaned chrome) is the next
sub-project's spec.
