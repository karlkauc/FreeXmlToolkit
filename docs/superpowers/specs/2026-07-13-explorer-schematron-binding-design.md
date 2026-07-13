# Explorer Panel: Schematron Binding & One-Click Validation

**Date:** 2026-07-13
**Status:** Approved

## Goal

The Explorer side panel already offers a one-click XSLT transform: a sticky
stylesheet picker (fed by the shared recent-XSLT store) plus a Transform
button. Schematron files can currently only be bound per document through the
Validation panel's SOURCES section. This feature adds the same convenience for
Schematron to the Explorer panel: pick a Schematron once, then validate the
selected file(s) with one click.

## UI

A second toolbar row directly below the existing transform bar in
`ExplorerPanel`:

```
[bi-file-earmark-code  stylesheet ▾] [▶ Transform]     (existing)
[bi-ui-checks-grid     schematron ▾] [▶ Validate ]     (new)
```

- Same look and feel as the transform bar: `MenuButton` + `Button`, both with
  style class `fxt-tool-button`, wrapped in an `HBox` with `fxt-sp-header`.
- The picker label shows the current Schematron's file name, or
  `"Schematron…"` when none is chosen (mirrors the stylesheet picker).
- Icon for the picker: `bi-ui-checks-grid` (the icon the Validation panel
  already uses for its Schematron source row).
- Node ids for tests: `explorer-schematron`, `explorer-validate`,
  `explorer-schematron-bar`.

## Picker dropdown

Menu contents, rebuilt on every `onShowing` (like the stylesheet menu):

1. Recent Schematron files (most recent first) — selecting one makes it the
   current Schematron.
2. Separator.
3. `Favorites` submenu — populated from the existing `FavoritesService`
   entries of type `FileFavorite.FileType.SCHEMATRON` (reuse `FavoritesMenu`
   population logic).
4. `Choose Schematron…` — `FileChooser` with filter `*.sch`, `*.schematron`.
5. `Clear recent` — clears the recent store and resets the current selection.

## Persistence: recent-Schematron store

New `PropertiesService` API, exactly parallel to the recent-XSLT store (same
size limit, same pruning of non-existing files):

- `List<File> getRecentSchematronFiles()`
- `void addRecentSchematronFile(File file)`
- `void clearRecentSchematronFiles()`

Implemented in `PropertiesServiceImpl` with a new properties key analogous to
the recent-XSLT key. The store is shared, so other panels can later reuse it.

## Behavior

**Picking a Schematron** (from recents, favorites, or the chooser):

- Sets the panel's sticky `currentSchematron` (kept across file switches,
  like the stylesheet picker).
- Records it in the recent-Schematron store.
- Immediately binds it to the active document via
  `editorHost.setActiveSchematron(file)`, so the Validation panel, the
  PROBLEMS view, and live validation pick it up. No-op when no document is
  open (existing `withActive` semantics).
- Updates the picker label.

**Resolution order** when the Validate button needs a Schematron: the
explicitly picked one, else the head of the recent store, else open the
chooser (mirrors `currentStylesheet()`).

**Validate button** (mirrors `runExplorerTransform`):

1. Resolve the Schematron; open the chooser if none — abort if still none.
2. Collect selected `.xml` files from the workspace tree; fall back to the
   active editor document when the selection contains none.
3. If no XML file at all: show an error via the transform output panel's
   status (same pattern as the transform flow).
4. Bind the Schematron to the active document
   (`editorHost.setActiveSchematron`).
5. Delegate to the shell via a callback (see Wiring):
   - **Single file that is the active document** (or the active-document
     fallback was used): switch to the Validation activity and call
     `ValidationPanel.revalidate()` — identical to the editor-toolbar
     Validate flow.
   - **Multiple files, or a single tree-selected file that is not the active
     document:** switch to the Validation activity and call the new
     `ValidationPanel.runBatch(files, schematron)` overload.

**Result display:** entirely reused from the Validation panel — problems
list, Schematron report button, batch RESULTS list, Excel export. No new
result UI in the Explorer.

## Wiring

`ExplorerPanel` must not know the shell. Following the existing
`setNewFileAction` pattern, it gets a callback:

```java
/** (files, schematron) -> run validation in the Validation activity. */
public void setSchematronValidateAction(
        BiConsumer<List<File>, File> action)
```

`UnifiedShellView.createSidePanel(EXPLORER)` wires it: select
`Activity.VALIDATION`, then dispatch to `revalidate()` or
`runBatch(files, schematron)` on the cached `validationPanel()` as described
above.

## ValidationPanel change

New overload so a batch run works even when no document is open (today
`runBatch(List<File>)` reads the Schematron from the active document):

```java
public void runBatch(List<File> files, File schematron)
```

The existing single-argument `runBatch` delegates to it with
`editorHost.getActiveSchematron()`. The XSD keeps coming from the active
document's binding (unchanged).

## Error handling

- Picked Schematron file deleted on disk: resolution falls through to the
  recents head / chooser, same as `currentStylesheet()`.
- Invalid/unloadable Schematron: already handled inside `ValidationRunner`
  (the Schematron stage is skipped); no new handling needed.
- No `PropertiesService` (isolated tests): all store access is best-effort
  and returns empty lists, mirroring the XSLT code paths.

## Threading

No new threading: `revalidate()` and `runBatch(...)` already run
asynchronously on `FxtGui.executorService` and publish results via
`Platform.runLater`.

## Testing

- **PropertiesServiceImplTest:** recent-Schematron store — add/get order,
  de-duplication, size limit, pruning of missing files, clear.
- **ExplorerPanel (TestFX):** picker label default and after selection; menu
  contains recents, Favorites submenu, Choose…, Clear recent; picking binds
  to the active document (`editorHost.getActiveSchematron()`); Validate with
  a single active document triggers the callback with that file; Clear recent
  resets label and store.
- **ValidationPanel:** `runBatch(files, schematron)` overload uses the passed
  Schematron when no document is open.
- Icon literals are covered by the existing `IconifyIconCoverageTest`.

## Out of scope

- No changes to the Transform panel or the Validation panel's SOURCES UI.
- No recent-Schematron dropdown anywhere else (the store is shared, so other
  panels can adopt it later).
- No workspace-tree context-menu entry ("Validate with Schematron…") — can be
  added later on top of the same callback.
