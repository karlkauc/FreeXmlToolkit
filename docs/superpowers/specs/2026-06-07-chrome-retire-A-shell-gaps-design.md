# Legacy Chrome Retirement — Sub-project A: Close Shell Feature Gaps — Design

**Date:** 2026-06-07
**Branch:** `feature/ui-rebuild-unified-editor`
**Status:** Approved design — ready for implementation plan

## Context

The UI rebuild has retired 12 legacy editor subsystems; the app now boots into
the Unified Shell. The only thing left is the **legacy chrome** that still wraps
the shell as a page inside `main.fxml`: the **MenuBar**, the **sidebar**, the
**footer/bottom_line**, plus the legacy **WelcomeController** and
**SettingsController** pages. Retiring the chrome makes the shell top-level.

**Decision (2026-06-07): the top-level shell is menu-free** — no MenuBar.
Every menu function must find a home in an Activity, a dialog, or be dropped.

Retiring the chrome is **too large for one spec** and decomposes into three
sub-projects, each with its own spec → plan → implementation cycle:

- **A (this spec): close the shell feature gaps** so nothing is lost when the
  chrome goes. The chrome stays untouched; the shell is made feature-complete in
  parallel.
- **B (later): make the shell top-level** — relocate global concerns (drag-drop,
  keyboard shortcuts, update-check scheduling, theme, recent-files persistence)
  into the shell; strip `main.fxml`/`MainController` to the shell only.
- **C (later): delete the legacy chrome** — MenuBar, sidebar, footer,
  `welcome.fxml`, `settings.fxml`, `WelcomeController`, `SettingsController`,
  and reduce/merge `MainController`.

This spec covers **only A**.

## Goal

Make the Unified Shell feature-complete versus the legacy chrome, so that after
A nothing is lost when B/C remove the chrome. Four largely-independent ports,
each landing in an existing shell surface (except FundsXML, which gets a new
conditional activity). The work is overwhelmingly UI wiring over existing
services — almost no new business logic. Pattern follows the XSLT-Developer port:
UI-free runners/helpers + slim shell views/dialogs, TDD.

The user chose to preserve **all four** gap areas (nothing dropped).

## What the shell already covers (no work needed)

- File New/Open → shell Welcome + Explorer; Recent → Explorer RECENT section;
  Save/SaveAs → per-tab (Ctrl+S); Settings → SETTINGS activity.
- Edit Undo/Redo/Find/Replace → in-editor (Ctrl+Z/Y/F/H); the legacy menu items
  are no-ops/info-dialogs.
- XML Tools (Template Builder, XML→CSV, Generate Schema) → existing shell
  activities.
- Theme, indent, auto-format, proxy, temp/cache → already in shell `SettingsPanel`.
- Update check → HelpPanel + Help activity.
- Favorites (quick list) → Favorites activity.

These are confirmed covered and are NOT part of A.

## Package 1 — FundsXML activity (largest)

A **dedicated, conditional** FundsXML home, chosen over distributing the 11
items across Settings/Validation/Help because (a) it matches the one-cohesive-
domain-per-activity pattern, (b) the feature flag maps cleanly to show/hide one
activity instead of conditionally showing fragments in three panels, and (c)
discoverability — FundsXML users get one obvious home.

**Visibility:** `Activity.FUNDSXML` is registered in the activity bar **only when
`PropertiesService` reports `fundsxml.enabled=true`** (same condition as today's
menu visibility). Toggling the flag in Settings rebuilds the activity list (the
shell already rebuilds on settings-save for theme, etc.).

**`FundsXmlPanel`** (new side-panel, activity pattern) groups the 11 legacy items
into sections:
- **Management:** "Download / Update Content"; active schema version (ComboBox,
  replacing the legacy radio-menu); "Check for Updates" + last-update timestamp.
- **Action (on the active document):** "Validate against FundsXML" — uses
  `editorHost.getActiveText()`, shows the result as a tool-tab/alert like the
  other shell validations. A link also remains in the Validation panel.
- **Docs & resources:** "Generate Schema Documentation" (background thread →
  result); "Open Examples / Schema / Schematron Folder" (`Desktop.open`); "Open
  Online Docs" (browser).

**`FundsXmlRunner`** (new, UI-free, testable) — a thin wrapper over the existing
FundsXML logic currently in `MainController` (`openFundsXmlDownload`,
active-version handling, `validateAgainstFundsXml`,
`generateFundsXmlSchemaDocumentation`, folder/online openers). The plan extracts
those method bodies from `MainController` into the runner/service so the panel
AND (later) C share the logic and `MainController` becomes FundsXML-free.
Long-running work (download, doc generation) runs on `FxtGui.executorService`
with `Platform.runLater`.

**Data sources:** existing FundsXML services/properties (version list, cache
folders, enable flag) — no new business logic, only relocation + shell UI.

**YAGNI:** FundsXML management lives in this activity, not duplicated in Settings.
Settings keeps only the plain enable toggle (Package 3).

## Package 2 — Help: full About + Shortcuts dialog

`HelpPanel` (existing HELP activity) gains two buttons:
- **"About"** → `AboutDialog` (new modal, shell-styled): logo/title/version pill
  (via `VersionUtil`), info grid (Version · build timestamp · Java · JavaFX · OS,
  from `System.getProperty`), "Copy version" button, license line (Apache link),
  links (GitHub / Docs / Report Issue). Ported from `MainController.showAboutDialog`.
- **"Keyboard Shortcuts"** → `KeyboardShortcutsDialog` (new): the categorized
  shortcut overview from `MainController.showKeyboardShortcuts`.
- The existing quick-links (User Guide / Online Docs) + "Check for Updates" in
  HelpPanel stay.

## Package 3 — Settings extras

`SettingsPanel` (existing SETTINGS activity) gains the missing sections, all via
`PropertiesService` (same keys as legacy):
- **User info:** Name / Email / Company (text fields).
- **SSL:** "Trust all certificates" toggle.
- **Usage statistics:** tracking toggle + "Clear statistics" button
  (`UsageTrackingService`).
- Load-on-open + "Save Settings" persists everything (the panel already has the
  mechanism).
- Settings retains only the FundsXML **enable** toggle (which shows/hides the
  FundsXML activity); FundsXML management itself lives in Package 1.

**Favorites:** the detailed management (list with add/edit/remove/category) goes
into the **Favorites activity** (`FavoritesActivityPanel`), its natural home, not
into `SettingsPanel`.

## Package 4 — Welcome dashboard statistics

`EditorWelcomePane` (already has stat cards + a tips banner) gains:
- a **trend sparkline** (7-day usage) and a **feature-progress grid**, fed from
  `UsageTrackingService` / `SkillTracker` (the same sources the legacy welcome
  used).
- A small UI-free helper supplies the data series (testable); the pane renders
  them. Missing data degrades gracefully (empty/hidden) — no fabricated data.

## Reused services (all four packages)

`PropertiesService`, `UpdateCheckService` / `AutoUpdateServiceImpl`, the FundsXML
services, `UsageTrackingService`, `SkillTracker`, `FavoritesService`,
`VersionUtil`. A is predominantly UI wiring, not new logic.

## Out of scope (separate sub-projects)

- **B:** boot restructuring (shell as root scene), relocating global concerns,
  stripping `main.fxml`/`MainController`.
- **C:** deleting the chrome (MenuBar/sidebar/footer/welcome/settings + their
  controllers).
- A leaves the legacy chrome fully intact and functional; it only makes the
  shell feature-complete in parallel.

## Testing strategy

TDD in the established shell pattern:
- **UI-free runner/helper tests:** `FundsXmlRunnerTest` (version handling,
  validate-against-FundsXML over a sample, folder/doc actions don't throw),
  the welcome-stats data helper, settings accessors.
- **View/dialog tests (TestFX, gated where needed):** `FundsXmlPanelTest`
  (renders sections; actions wired), `AboutDialogTest` /
  `KeyboardShortcutsDialogTest` (construct + key content like version/runtime
  strings), `SettingsPanel` extras (load/persist round-trip of the new keys),
  `EditorWelcomePane` stats (renders sparkline/grid from a fed data series).
- **Activity registration:** a test that `Activity.FUNDSXML` appears in the
  activity list only when `fundsxml.enabled=true`.
- Mind the documented headless TestFX toolkit-init cascade — verify per-class /
  the package suite, not the full `./gradlew build`.

## Build sequence — 4 self-contained increments (each committed + pushed)

Independent packages; order by value and isolation:

1. **Help: About + Shortcuts dialogs** — smallest, self-contained, no service
   extraction. `AboutDialog` + `KeyboardShortcutsDialog` + HelpPanel buttons.
2. **Settings extras** — `SettingsPanel` user-info/SSL/usage sections +
   persistence; detailed favorites in the Favorites activity.
3. **Welcome dashboard stats** — `EditorWelcomePane` sparkline + feature grid +
   the data helper.
4. **FundsXML activity** — `FundsXmlRunner` (extract logic from MainController) +
   `FundsXmlPanel` + conditional `Activity.FUNDSXML` registration + Validation
   link. The largest; last so the smaller wins land first.

After A completes and is verified, B (make the shell top-level) is the next
sub-project's spec.
