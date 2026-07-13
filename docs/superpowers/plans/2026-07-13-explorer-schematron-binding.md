# Explorer Schematron Binding & One-Click Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `[Schematron ▾][Validate]` bar to the Explorer side panel — pick a Schematron once (recents + favorites), bind it to the active document, and validate the selected XML file(s) with one click in the Validation activity.

**Architecture:** Mirrors the existing Explorer transform bar exactly: a sticky picker backed by a new shared recent-Schematron store in `PropertiesService`, a Validate button that delegates via a shell-wired callback (`setSchematronValidateAction`, analogous to `setNewFileAction`) to the cached `ValidationPanel` — single active document → `revalidate()`, anything else → a new `runBatch(files, schematron)` overload. No new result UI, no new threading.

**Tech Stack:** Java 25 (preview), JavaFX 25, TestFX (`ApplicationExtension`), JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-07-13-explorer-schematron-binding-design.md`

## Global Constraints

- All user-facing text in English; comments and JavaDoc in English.
- Icons via `org.fxt.freexmltoolkit.controls.icons.IconifyIcon` with `bi-*` literals only. Both literals used here (`bi-ui-checks-grid`, `bi-play-fill`) already exist in the codebase; `IconifyIconCoverageTest` guards them.
- All `@FXML` handlers public (none added here, but applies).
- Never modify XsdNode directly (not touched here).
- UI updates on the FX thread; background work stays on `FxtGui.executorService` (already the case in `ValidationPanel`).
- Tests must not write the user's real `FreeXmlToolkit.properties` — the Gradle test task sets `fxt.properties.file` to a path under `build/`, so using `PropertiesServiceImpl.getInstance()` in tests is safe.
- Run tests via `./gradlew test --tests "..."`; format with `./gradlew spotlessApply` before the final commit.
- Do NOT edit sources while a Gradle run is still executing (phantom test failures).
- Ignore any search hits under `.claude/worktrees/*` (stale copies).

---

### Task 1: Recent-Schematron store in PropertiesService

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/PropertiesService.java` (after `clearRecentXsltFiles()`, ~line 494)
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/PropertiesServiceImpl.java` (after `clearRecentXsltFiles()`, ~line 650)
- Test: `src/test/java/org/fxt/freexmltoolkit/service/PropertiesServiceRecentSchematronTest.java` (new)

**Interfaces:**
- Consumes: existing `PropertiesServiceImpl` singleton (`getInstance()`), its `properties` field and `saveProperties(properties)` method.
- Produces (used by Task 4):
  - `List<File> getRecentSchematronFiles()` — most recent first, missing files pruned
  - `void addRecentSchematronFile(File file)` — de-duplicates, caps at 10, ignores null/missing
  - `void clearRecentSchematronFiles()`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/fxt/freexmltoolkit/service/PropertiesServiceRecentSchematronTest.java`:

```java
package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the shared recent-Schematron store (the Explorer's one-click
 * validation feeds from it): ordering, de-duplication, the 10-entry cap,
 * pruning of deleted files, and clearing.
 */
class PropertiesServiceRecentSchematronTest {

    private final PropertiesService service = PropertiesServiceImpl.getInstance();

    @BeforeEach
    void clearStore() {
        service.clearRecentSchematronFiles();
    }

    @Test
    void addedFilesComeBackMostRecentFirstWithoutDuplicates(@TempDir Path tmp) throws Exception {
        File a = Files.writeString(tmp.resolve("a.sch"), "<x/>").toFile();
        File b = Files.writeString(tmp.resolve("b.sch"), "<x/>").toFile();

        service.addRecentSchematronFile(a);
        service.addRecentSchematronFile(b);
        service.addRecentSchematronFile(a); // re-adding moves to front, no duplicate

        List<String> recent = service.getRecentSchematronFiles().stream()
                .map(File::getAbsolutePath).toList();
        assertEquals(List.of(a.getAbsolutePath(), b.getAbsolutePath()), recent);
    }

    @Test
    void storeIsCappedAtTenEntries(@TempDir Path tmp) throws Exception {
        for (int i = 0; i < 12; i++) {
            service.addRecentSchematronFile(
                    Files.writeString(tmp.resolve("s" + i + ".sch"), "<x/>").toFile());
        }
        List<File> recent = service.getRecentSchematronFiles();
        assertEquals(10, recent.size());
        assertEquals("s11.sch", recent.get(0).getName());
        assertEquals("s2.sch", recent.get(9).getName());
    }

    @Test
    void deletedFilesArePrunedAndMissingAddsIgnored(@TempDir Path tmp) throws Exception {
        File gone = Files.writeString(tmp.resolve("gone.sch"), "<x/>").toFile();
        service.addRecentSchematronFile(gone);
        assertTrue(gone.delete());
        assertTrue(service.getRecentSchematronFiles().isEmpty(),
                "deleted files must be pruned on read");

        service.addRecentSchematronFile(new File(tmp.toFile(), "never-existed.sch"));
        service.addRecentSchematronFile(null);
        assertTrue(service.getRecentSchematronFiles().isEmpty(),
                "null / non-existing files must not be recorded");
    }

    @Test
    void clearEmptiesTheStore(@TempDir Path tmp) throws Exception {
        service.addRecentSchematronFile(
                Files.writeString(tmp.resolve("c.sch"), "<x/>").toFile());
        assertFalse(service.getRecentSchematronFiles().isEmpty());
        service.clearRecentSchematronFiles();
        assertTrue(service.getRecentSchematronFiles().isEmpty());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.PropertiesServiceRecentSchematronTest"`
Expected: **compilation FAILURE** — `cannot find symbol: method clearRecentSchematronFiles()` (the API does not exist yet).

- [ ] **Step 3: Add the interface methods**

In `src/main/java/org/fxt/freexmltoolkit/service/PropertiesService.java`, directly after `void clearRecentXsltFiles();`:

```java
    // Schematron recent files

    /**
     * Gets the list of recently used Schematron files (most recent first;
     * files that no longer exist are omitted).
     *
     * @return list of recently used Schematron files
     */
    List<File> getRecentSchematronFiles();

    /**
     * Adds a Schematron file to the recent files list (moves it to the front
     * if already present). Null or non-existing files are ignored.
     *
     * @param file the file to add
     */
    void addRecentSchematronFile(File file);

    /**
     * Clears all recent Schematron files.
     */
    void clearRecentSchematronFiles();
```

- [ ] **Step 4: Implement in PropertiesServiceImpl**

In `src/main/java/org/fxt/freexmltoolkit/service/PropertiesServiceImpl.java`, directly after `clearRecentXsltFiles()` (mirror of the XSLT block, key prefix `schematron.recent.file.`):

```java
    // Schematron recent files implementation

    private static final int MAX_RECENT_SCHEMATRON_FILES = 10;

    @Override
    public List<File> getRecentSchematronFiles() {
        List<File> recentFiles = new java.util.ArrayList<>();
        for (int i = 0; i < MAX_RECENT_SCHEMATRON_FILES; i++) {
            String path = properties.getProperty("schematron.recent.file." + i);
            if (path != null && !path.isEmpty()) {
                File file = new File(path);
                if (file.exists()) {
                    recentFiles.add(file);
                }
            }
        }
        return recentFiles;
    }

    @Override
    public void addRecentSchematronFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        // Get current list and remove if already present
        List<File> recentFiles = new java.util.ArrayList<>(getRecentSchematronFiles());
        recentFiles.removeIf(f -> f.getAbsolutePath().equals(file.getAbsolutePath()));

        // Add to front
        recentFiles.addFirst(file);

        // Limit to max
        while (recentFiles.size() > MAX_RECENT_SCHEMATRON_FILES) {
            recentFiles.removeLast();
        }

        // Save to properties
        for (int i = 0; i < MAX_RECENT_SCHEMATRON_FILES; i++) {
            if (i < recentFiles.size()) {
                properties.setProperty("schematron.recent.file." + i,
                        recentFiles.get(i).getAbsolutePath());
            } else {
                properties.remove("schematron.recent.file." + i);
            }
        }
        saveProperties(properties);
        logger.debug("Added recent Schematron file: {}", file.getAbsolutePath());
    }

    @Override
    public void clearRecentSchematronFiles() {
        for (int i = 0; i < MAX_RECENT_SCHEMATRON_FILES; i++) {
            properties.remove("schematron.recent.file." + i);
        }
        saveProperties(properties);
        logger.debug("Cleared all recent Schematron files");
    }
```

Note: `addRecentSchematronFile` stores `getAbsolutePath()`, so `getRecentSchematronFiles()` returns absolute files — the test compares absolute paths.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.PropertiesServiceRecentSchematronTest"`
Expected: BUILD SUCCESSFUL, 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/PropertiesService.java \
        src/main/java/org/fxt/freexmltoolkit/service/PropertiesServiceImpl.java \
        src/test/java/org/fxt/freexmltoolkit/service/PropertiesServiceRecentSchematronTest.java
git commit -m "feat(service): shared recent-Schematron store in PropertiesService

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: FavoritesMenu submenu helper

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/FavoritesMenu.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/FavoritesMenuSubmenuTest.java` (new)

**Interfaces:**
- Consumes: `FavoritesService.getInstance().getFavoritesByType(type)` (existing), `FileFavorite.FileType.SCHEMATRON`.
- Produces (used by Task 4):
  - `public static Menu submenu(String text, FileFavorite.FileType type, Consumer<File> onPick)` — a `javafx.scene.control.Menu` listing the favorites of `type` (flat, or one nested submenu per folder), disabled when empty.
  - `public static List<String> leafNames(Menu menu)` — observer overload for tests.

Background: `FavoritesMenu.populate(MenuButton, …)` builds the item list today; this task extracts that into a private `buildItems(...)` so the new `submenu(...)` and the existing `populate(...)` share it (DRY). `Menu`/`MenuItem` are not `Node`s, so plain JUnit without a JavaFX stage suffices. `FavoritesService.addFavorite(File)` auto-detects `.sch` as `FileType.SCHEMATRON` (see `FileFavorite.FileType` detection) — the test must remove seeded favorites afterwards because the store persists.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/FavoritesMenuSubmenuTest.java`:

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Menu;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link FavoritesMenu#submenu}: it lists the favorites of the requested
 * type as pickable items (for embedding in the Explorer's Schematron picker) and
 * is disabled while there is nothing to pick.
 */
class FavoritesMenuSubmenuTest {

    private final List<String> seededPaths = new ArrayList<>();

    @AfterEach
    void removeSeededFavorites() {
        for (String path : seededPaths) {
            try {
                FavoritesService.getInstance().removeFavoriteByPath(path);
            } catch (Throwable ignored) {
                // best-effort cleanup
            }
        }
    }

    @Test
    void listsSchematronFavoritesAndPicksTheFile(@TempDir Path tmp) throws Exception {
        Path sch = Files.writeString(tmp.resolve("rules.sch"), "<x/>");
        FavoritesService.getInstance().addFavorite(sch.toFile());
        seededPaths.add(sch.toString());

        List<File> picked = new ArrayList<>();
        Menu menu = FavoritesMenu.submenu("Favorites",
                FileFavorite.FileType.SCHEMATRON, picked::add);

        assertEquals("Favorites", menu.getText());
        assertFalse(menu.isDisable(), "with a favorite present the submenu is pickable");
        // addFavorite(File) strips the extension for the display name.
        assertTrue(FavoritesMenu.leafNames(menu).contains("rules"),
                "leaf names were: " + FavoritesMenu.leafNames(menu));

        // Firing the leaf hands the favorite's file to the picker.
        menu.getItems().stream()
                .filter(i -> "rules".equals(i.getText()))
                .findFirst().orElseThrow()
                .fire();
        assertEquals(List.of(sch.toFile().getAbsoluteFile()),
                picked.stream().map(File::getAbsoluteFile).toList());
    }

    @Test
    void submenuIsDisabledWhileThereAreNoFavoritesOfTheType() {
        Menu menu = FavoritesMenu.submenu("Favorites",
                FileFavorite.FileType.XPATH, f -> fail("nothing to pick"));
        assertTrue(menu.isDisable());
        assertTrue(menu.getItems().isEmpty());
    }
}
```

Note: the second test uses `FileType.XPATH` (an unlikely-to-be-seeded type) instead of SCHEMATRON so a developer's real favorites store cannot make it flaky.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.FavoritesMenuSubmenuTest"`
Expected: **compilation FAILURE** — `cannot find symbol: method submenu(...)` / `leafNames(javafx.scene.control.Menu)`.

- [ ] **Step 3: Implement submenu() and refactor populate() onto a shared buildItems()**

In `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/FavoritesMenu.java`:

Replace the body of `populate(MenuButton menu, FileFavorite.FileType type, Consumer<File> onPick)` with:

```java
    public static void populate(MenuButton menu, FileFavorite.FileType type, Consumer<File> onPick) {
        List<MenuItem> items = buildItems(type, onPick);
        menu.getItems().setAll(items);
        menu.setDisable(items.isEmpty());
    }
```

Add after it:

```java
    /**
     * Builds a {@link Menu} listing the favorites of {@code type} — for embedding
     * inside another menu (e.g. the Explorer's Schematron picker). Same structure
     * as {@link #populate}: a flat item list while everything is uncategorized,
     * one nested submenu per folder otherwise. Disabled while there is nothing
     * to pick.
     *
     * @param text   the submenu title (e.g. "Favorites")
     * @param type   the favorite type to list
     * @param onPick receives the picked favorite's file
     * @return the ready-to-embed submenu
     */
    public static Menu submenu(String text, FileFavorite.FileType type, Consumer<File> onPick) {
        Menu menu = new Menu(text);
        List<MenuItem> items = buildItems(type, onPick);
        menu.getItems().setAll(items);
        menu.setDisable(items.isEmpty());
        return menu;
    }

    /**
     * Builds the pickable favorite items of {@code type}: a flat list while
     * everything is uncategorized, one {@link Menu} per folder otherwise.
     * Empty when there are no (existing) favorites of the type.
     */
    private static List<MenuItem> buildItems(FileFavorite.FileType type, Consumer<File> onPick) {
        List<FileFavorite> favorites;
        try {
            favorites = FavoritesService.getInstance().getFavoritesByType(type).stream()
                    .filter(FileFavorite::fileExists)
                    .toList();
        } catch (Throwable t) {
            favorites = List.of();
        }
        if (favorites.isEmpty()) {
            return List.of();
        }

        Map<String, List<FileFavorite>> byFolder = new LinkedHashMap<>();
        for (FileFavorite favorite : favorites) {
            String folder = favorite.getFolderName() == null || favorite.getFolderName().isBlank()
                    ? "" : favorite.getFolderName();
            byFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(favorite);
        }
        List<MenuItem> items = new ArrayList<>();
        if (byFolder.size() == 1) {
            // Single (or no) folder: flat list, no submenu indirection.
            for (FileFavorite favorite : favorites) {
                items.add(item(favorite, onPick));
            }
            return items;
        }
        for (var entry : byFolder.entrySet()) {
            Menu submenu = new Menu(entry.getKey().isEmpty() ? "Uncategorized" : entry.getKey());
            for (FileFavorite favorite : entry.getValue()) {
                submenu.getItems().add(item(favorite, onPick));
            }
            items.add(submenu);
        }
        return items;
    }
```

Replace the body of `leafNames(MenuButton menu)` and add the `Menu` overload plus a shared private helper:

```java
    /** @return the pickable leaf labels (flat items + submenu items) — for tests/observers. */
    public static List<String> leafNames(MenuButton menu) {
        return leafNames(menu.getItems());
    }

    /** @return the pickable leaf labels of a favorites submenu — for tests/observers. */
    public static List<String> leafNames(Menu menu) {
        return leafNames(menu.getItems());
    }

    private static List<String> leafNames(List<MenuItem> items) {
        List<String> names = new ArrayList<>();
        for (MenuItem item : items) {
            if (item instanceof Menu sub) {
                for (MenuItem leaf : sub.getItems()) {
                    names.add(leaf.getText());
                }
            } else {
                names.add(item.getText());
            }
        }
        return names;
    }
```

(The original folder-grouping code moves out of `populate` into `buildItems` — delete the now-duplicated logic from `populate`.)

- [ ] **Step 4: Run the new test and the existing favorites-menu consumers**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.FavoritesMenuSubmenuTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanelTest"`
Expected: BUILD SUCCESSFUL — the refactored `populate` keeps `ValidationPanelTest.schematronFavoriteNames` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/FavoritesMenu.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/FavoritesMenuSubmenuTest.java
git commit -m "feat(shell): FavoritesMenu.submenu for embedding favorites in other menus

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: ValidationPanel.runBatch(files, schematron) overload

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/ValidationPanel.java:513` (`runBatch`)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/ValidationPanelBatchResultsTest.java` (add one test)

**Interfaces:**
- Consumes: existing `ValidationRunner.batch(files, xsd, schematron, progress, cancelled)`, `editorHost.getActiveSchematron()`, `editorHost.activeSchemaProperty()`.
- Produces (used by Task 5):
  - `public void runBatch(java.util.List<File> files, File schematron)` — validates against the bound XSD and the **given** Schematron; works with no open document.
  - Existing `public void runBatch(java.util.List<File> files)` keeps its behavior (delegates with the active document's Schematron).

- [ ] **Step 1: Write the failing test**

In `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/ValidationPanelBatchResultsTest.java`, add (uses the class's existing `SCHEMATRON` constant, `host`, `panel`):

```java
    @Test
    void runBatchWithExplicitSchematronNeedsNoOpenDocument(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path bad = tmp.resolve("bad.xml");
        Files.writeString(bad, "<root/>");
        Path good = tmp.resolve("good.xml");
        Files.writeString(good, "<root><name>x</name></root>");

        // No document is opened: the Schematron comes in as an argument
        // (the Explorer's one-click validation path).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.runBatch(java.util.List.of(bad.toFile(), good.toFile()), sch.toFile());
            return null;
        });
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS,
                () -> panel.batchResultCount() == 2);
        assertEquals(1, panel.batchFailedCount(),
                "bad.xml must fail against the explicitly passed Schematron");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanelBatchResultsTest"`
Expected: **compilation FAILURE** — no `runBatch(List<File>, File)` overload yet.

- [ ] **Step 3: Add the overload**

In `ValidationPanel.java`, replace the existing `runBatch(java.util.List<File> files)` (line ~513) — its body currently reads the Schematron from the active document — with:

```java
    /** Validates the given files against the bound XSD/Schematron and fills the RESULTS list (async). */
    public void runBatch(java.util.List<File> files) {
        runBatch(files, editorHost.getActiveSchematron());
    }

    /**
     * Validates the given files against the bound XSD and the given Schematron and
     * fills the RESULTS list (async). Used by the Explorer's one-click Schematron
     * validation, where files may be validated without any open document to bind
     * the Schematron to.
     *
     * @param files      the XML files to validate
     * @param schematron the Schematron to validate against (may be {@code null})
     */
    public void runBatch(java.util.List<File> files, File schematron) {
        if (files == null || files.isEmpty()) {
            return;
        }
        File xsd = editorHost.activeSchemaProperty().get();
        int total = files.size();
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        PanelStatus.info(status, "Validating " + total + " file(s)…");
        progress.beginDeterminate(total, () -> {
            cancelled.set(true);
            PanelStatus.info(status, "Cancelling…");
        });
        FxtGui.executorService.submit(() -> {
            List<ValidationRunner.FileValidationResult> results = ValidationRunner.batch(files, xsd, schematron,
                    done -> javafx.application.Platform.runLater(() -> progress.setProgress(done)),
                    cancelled::get);
            String report = ValidationRunner.report(results, xsd, schematron);
            javafx.application.Platform.runLater(() -> {
                progress.finish();
                showBatchResults(results, report);
                if (cancelled.get()) {
                    // showBatchResults set a "N valid/failed" status; correct it for a partial run.
                    PanelStatus.info(status,
                            "Cancelled — validated " + results.size() + " of " + total + " file(s)");
                }
            });
        });
    }
```

(The only change to the moved body: the line `File schematron = editorHost.getActiveSchematron();` is gone — the value now arrives as the parameter.)

- [ ] **Step 4: Run the batch tests to verify all pass**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanelBatchResultsTest"`
Expected: BUILD SUCCESSFUL — the new test and the pre-existing batch tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/ValidationPanel.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/ValidationPanelBatchResultsTest.java
git commit -m "feat(validation): runBatch overload with explicit Schematron

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Explorer Schematron bar (picker + Validate button)

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/ExplorerPanel.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/ExplorerPanelSchematronTest.java` (new)

**Interfaces:**
- Consumes: Task 1's `getRecentSchematronFiles()/addRecentSchematronFile()/clearRecentSchematronFiles()`; Task 2's `FavoritesMenu.submenu("Favorites", FileFavorite.FileType.SCHEMATRON, onPick)`; existing `editorHost.setActiveSchematron(File)`, `editorHost.getActiveDocument()`, `workspace.getSelectedFiles()`, `editorHost.transformOutputPanel().showError(String)`.
- Produces (used by Task 5):
  - `public void setSchematronValidateAction(BiConsumer<List<File>, File> action)` — shell wiring hook
  - `public void useSchematron(File file)` — sets the sticky Schematron, records it as recent, binds it to the active document (also the programmatic entry point for tests)
  - `public List<String> schematronMenuItemTexts()` — observer for tests
  - Node ids `#explorer-schematron` (MenuButton), `#explorer-validate` (Button), `#explorer-schematron-bar` (HBox)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/ExplorerPanelSchematronTest.java`:

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Explorer's Schematron bar: the sticky picker (label, menu
 * entries, active-document binding, recents) and the Validate button's
 * delegation to the shell-wired callback.
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelSchematronTest {

    private EditorHost host;
    private ExplorerPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        // A clean recent store so label/menu assertions are deterministic.
        org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                .clearRecentSchematronFiles();
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void pickerStartsUnsetAndShowsTheChosenSchematron(@TempDir Path tmp) throws Exception {
        MenuButton picker = (MenuButton) panel.lookup("#explorer-schematron");
        assertNotNull(picker, "the Explorer must offer a Schematron picker");
        assertEquals("Schematron…", picker.getText(), "unset picker shows the placeholder");

        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, "<sch:schema xmlns:sch=\"http://purl.oclc.org/dsdl/schematron\"/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            return null;
        });

        assertEquals("rules.sch", picker.getText(), "picker label follows the chosen file");
        assertEquals(sch.toFile(), host.getActiveSchematron(),
                "picking must bind the Schematron to the active document");
        assertTrue(org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                        .getRecentSchematronFiles().contains(sch.toFile().getAbsoluteFile()),
                "picking must record the file in the shared recent store");
    }

    @Test
    void menuOffersRecentsFavoritesChooseAndClear(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("recent-rules.sch");
        Files.writeString(sch, "<x/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            return null;
        });

        List<String> texts = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> panel.schematronMenuItemTexts());
        assertTrue(texts.contains("recent-rules.sch"), "menu lists recents, was: " + texts);
        assertTrue(texts.contains("Favorites"), "menu offers the Favorites submenu, was: " + texts);
        assertTrue(texts.contains("Choose Schematron…"), texts.toString());
        assertTrue(texts.contains("Clear recent"), texts.toString());
    }

    @Test
    void clearRecentResetsThePickerLabel(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("gone.sch");
        Files.writeString(sch, "<x/>");
        MenuButton picker = (MenuButton) panel.lookup("#explorer-schematron");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            panel.schematronMenuItemTexts(); // rebuild the menu items
            panel.clearRecentSchematron();
            return null;
        });
        assertEquals("Schematron…", picker.getText(), "clearing resets the sticky choice");
        assertTrue(org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                .getRecentSchematronFiles().isEmpty());
    }

    @Test
    void validateButtonDelegatesActiveDocumentAndSchematron(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("active.xml");
        Files.writeString(xml, "<root/>");
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, "<x/>");

        AtomicReference<List<File>> gotFiles = new AtomicReference<>();
        AtomicReference<File> gotSchematron = new AtomicReference<>();
        panel.setSchematronValidateAction((files, schematron) -> {
            gotFiles.set(files);
            gotSchematron.set(schematron);
        });

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            ((Button) panel.lookup("#explorer-validate")).fire();
            return null;
        });

        assertNotNull(gotFiles.get(), "Validate must invoke the shell-wired action");
        assertEquals(List.of(xml.toFile()), gotFiles.get(),
                "with no tree selection the active document is the fallback input");
        assertEquals(sch.toFile(), gotSchematron.get());
    }
}
```

Note: the test calls `panel.clearRecentSchematron()` — expose the clear action as a small public method (see Step 3) instead of firing the MenuItem, mirroring how `clearRecent()` is public today.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanelSchematronTest"`
Expected: **compilation FAILURE** — `useSchematron`, `schematronMenuItemTexts`, `clearRecentSchematron`, `setSchematronValidateAction` do not exist.

- [ ] **Step 3: Implement the Schematron bar in ExplorerPanel**

All changes in `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/ExplorerPanel.java`.

**(a) Fields** — after the existing `stylesheetMenu` field (line ~55):

```java
    /** Sticky Schematron for the Explorer's one-click validation; shared via the recent-Schematron store. */
    private File currentSchematron;
    private final MenuButton schematronMenu = new MenuButton();
    /** Shell-wired: (files, schematron) → run validation in the Validation activity. */
    private java.util.function.BiConsumer<java.util.List<File>, File> schematronValidateAction;
```

**(b) Layout** — in the constructor, change

```java
        getChildren().addAll(header,
                buildTransformBar(),
                openHeader, openEditorsBox,
```

to

```java
        getChildren().addAll(header,
                buildTransformBar(),
                buildSchematronBar(),
                openHeader, openEditorsBox,
```

**(c) Extract the shared file-collection helper** — in `runExplorerTransform()`, replace

```java
        java.util.List<File> xmls = new java.util.ArrayList<>();
        for (Path p : workspace.getSelectedFiles()) {
            if (isXmlFile(p)) {
                xmls.add(p.toFile());
            }
        }
        if (xmls.isEmpty()) {
            // Fall back to the active editor document if it is an XML file.
            editorHost.getActiveDocument()
                    .map(OpenDocument::getPath)
                    .filter(p -> p != null && isXmlFile(p))
                    .ifPresent(p -> xmls.add(p.toFile()));
        }
```

with

```java
        java.util.List<File> xmls = selectedXmlFiles();
```

and add below `isXmlFile(Path)`:

```java
    /** @return the tree-selected XML files, falling back to the active XML document. */
    private java.util.List<File> selectedXmlFiles() {
        java.util.List<File> xmls = new java.util.ArrayList<>();
        for (Path p : workspace.getSelectedFiles()) {
            if (isXmlFile(p)) {
                xmls.add(p.toFile());
            }
        }
        if (xmls.isEmpty()) {
            // Fall back to the active editor document if it is an XML file.
            editorHost.getActiveDocument()
                    .map(OpenDocument::getPath)
                    .filter(p -> p != null && isXmlFile(p))
                    .ifPresent(p -> xmls.add(p.toFile()));
        }
        return xmls;
    }
```

**(d) The Schematron bar** — add a new section after `runExplorerTransform()` / before `isXmlFile`:

```java
    // ----- one-click Schematron validation --------------------------------------

    /**
     * Builds the Explorer validation bar: a Schematron picker ({@link #schematronMenu})
     * and a Validate button — the Schematron sibling of {@link #buildTransformBar()}.
     * The chosen Schematron stays fixed across files, so switching the XML selection
     * and clicking Validate is a single click.
     */
    private HBox buildSchematronBar() {
        schematronMenu.setId("explorer-schematron");
        schematronMenu.setGraphic(icon("bi-ui-checks-grid", 14));
        schematronMenu.getStyleClass().add("fxt-tool-button");
        schematronMenu.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(schematronMenu, Priority.ALWAYS);
        schematronMenu.setOnShowing(e -> refreshSchematronMenu());
        refreshSchematronLabel();

        Button validateButton = new Button("Validate", icon("bi-play-fill", 14));
        validateButton.setId("explorer-validate");
        validateButton.getStyleClass().add("fxt-tool-button");
        validateButton.setTooltip(new javafx.scene.control.Tooltip(
                "Validate selected XML file(s) with the current Schematron"));
        validateButton.setOnAction(e -> runExplorerValidation());

        HBox bar = new HBox(6, schematronMenu, validateButton);
        bar.setId("explorer-schematron-bar");
        bar.getStyleClass().add("fxt-sp-header");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /** Rebuilds the Schematron dropdown: recents · Favorites · Choose… · Clear recent. */
    private void refreshSchematronMenu() {
        schematronMenu.getItems().clear();
        for (File file : recentSchematronFiles()) {
            MenuItem item = new MenuItem(file.getName());
            item.setOnAction(e -> useSchematron(file));
            schematronMenu.getItems().add(item);
        }
        if (!schematronMenu.getItems().isEmpty()) {
            schematronMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        }
        schematronMenu.getItems().add(FavoritesMenu.submenu("Favorites",
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.SCHEMATRON,
                this::useSchematron));
        schematronMenu.getItems().add(menuItem("Choose Schematron…", this::chooseSchematronFile));
        schematronMenu.getItems().add(menuItem("Clear recent", this::clearRecentSchematron));
    }

    /** Updates the dropdown text to the current (or most recent) Schematron's name. */
    private void refreshSchematronLabel() {
        File schematron = currentSchematronFile();
        schematronMenu.setText(schematron != null ? schematron.getName() : "Schematron…");
    }

    /**
     * Makes {@code file} the sticky Schematron: records it as most recent and binds it
     * to the active document (so the Validation activity, the PROBLEMS view and live
     * validation pick it up). Also the programmatic entry point for the shell and tests.
     */
    public void useSchematron(File file) {
        currentSchematron = file;
        if (file != null) {
            if (propertiesService != null) {
                propertiesService.addRecentSchematronFile(file);
            }
            editorHost.setActiveSchematron(file);
        }
        refreshSchematronLabel();
    }

    /** Clears the recent-Schematron store and resets the sticky choice. */
    public void clearRecentSchematron() {
        if (propertiesService != null) {
            propertiesService.clearRecentSchematronFiles();
        }
        currentSchematron = null;
        refreshSchematronLabel();
    }

    /** Opens a file chooser to pick a Schematron, then makes it the current one. */
    private void chooseSchematronFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Schematron");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Schematron", "*.sch", "*.schematron"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser,
                getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            useSchematron(file);
        }
    }

    /**
     * Resolves the Schematron to use: the explicitly chosen one, otherwise the head
     * of the shared recent-Schematron list (mirrors {@link #currentStylesheet()}).
     */
    private File currentSchematronFile() {
        if (currentSchematron != null && currentSchematron.isFile()) {
            return currentSchematron;
        }
        java.util.List<File> recent = recentSchematronFiles();
        return recent.isEmpty() ? null : recent.get(0);
    }

    private java.util.List<File> recentSchematronFiles() {
        if (propertiesService == null) {
            return java.util.List.of();
        }
        try {
            return propertiesService.getRecentSchematronFiles();
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }

    /**
     * Validates the selected XML file(s) with the current Schematron via the
     * shell-wired {@link #schematronValidateAction}. Falls back to the active editor
     * document when nothing is selected in the tree; opens the chooser first when no
     * Schematron is set yet.
     */
    private void runExplorerValidation() {
        File schematron = currentSchematronFile();
        if (schematron == null) {
            chooseSchematronFile();
            schematron = currentSchematronFile();
            if (schematron == null) {
                return;
            }
        }
        java.util.List<File> xmls = selectedXmlFiles();
        if (xmls.isEmpty()) {
            editorHost.transformOutputPanel().showError("Select an XML file to validate.");
            return;
        }
        editorHost.setActiveSchematron(schematron);
        if (schematronValidateAction != null) {
            schematronValidateAction.accept(xmls, schematron);
        }
    }

    /**
     * Wires the Explorer's one-click Schematron validation to the shell: the callback
     * receives the XML files to validate and the Schematron to use, and is expected
     * to show the result in the Validation activity.
     *
     * @param action the action to run (ignored if {@code null})
     */
    public void setSchematronValidateAction(
            java.util.function.BiConsumer<java.util.List<File>, File> action) {
        if (action != null) {
            this.schematronValidateAction = action;
        }
    }

    /** @return the Schematron dropdown item texts (rebuilds the menu) — for tests/observers. */
    public java.util.List<String> schematronMenuItemTexts() {
        refreshSchematronMenu();
        return schematronMenu.getItems().stream().map(MenuItem::getText).toList();
    }
```

(`schematronMenuItemTexts()` contains a `null` for the separator — the tests only use `contains(...)`, so that is fine.)

- [ ] **Step 4: Run the new test and the existing Explorer tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanelSchematronTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanelTest"`
Expected: BUILD SUCCESSFUL — new bar does not break the existing panel tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/ExplorerPanel.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/ExplorerPanelSchematronTest.java
git commit -m "feat(explorer): Schematron picker + one-click Validate bar

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Shell wiring — Validate runs in the Validation activity

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java:527-532` (EXPLORER case) and a new private method next to `validateActive()` (~line 630)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellExplorerSchematronTest.java` (new)

**Interfaces:**
- Consumes: Task 4's `setSchematronValidateAction` / `useSchematron`, Task 3's `runBatch(files, schematron)`, existing `selectionModel.select(Activity.VALIDATION)`, `validationPanel()`, `editorHost.getActiveDocument()`.
- Produces: user-visible behavior only (no new public API).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellExplorerSchematronTest.java` (pattern copied from `UnifiedShellValidateTest`):

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Explorer's one-click Schematron validation end to end: picking a
 * Schematron and hitting Validate surfaces the rule violations of the active
 * document in the Validation activity (single-file flow).
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedShellExplorerSchematronTest {

    private static final String SCHEMATRON = """
            <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
              <sch:pattern><sch:rule context="root">
                <sch:assert test="name">root must have a name child</sch:assert>
              </sch:rule></sch:pattern>
            </sch:schema>
            """;

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                .clearRecentSchematronFiles();
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1100, 720));
        stage.show();
    }

    @Test
    void explorerValidateShowsSchematronProblemsOfTheActiveDocument(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, SCHEMATRON);
        Path xml = tmp.resolve("bad.xml");
        Files.writeString(xml, "<root/>"); // violates the rule: no <name> child

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("root")).orElse(false));

        ExplorerPanel explorer = explorerPanel();
        assertNotNull(explorer, "the Explorer activity is the default side panel");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            explorer.useSchematron(sch.toFile());
            ((Button) explorer.lookup("#explorer-validate")).fire();
            return null;
        });

        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> validationPanel() != null
                && validationPanel().getProblemCount() > 0);
        assertTrue(validationPanel().getProblemCount() > 0,
                "the Schematron violation must show up in the Validation panel");
    }

    private ExplorerPanel explorerPanel() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.lookupAll("*").stream()
                .filter(n -> n instanceof ExplorerPanel)
                .map(n -> (ExplorerPanel) n)
                .findFirst().orElse(null));
    }

    private ValidationPanel validationPanel() {
        // lookupAll must run on the FX thread (scene graph may still be mutating)
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.lookupAll("*").stream()
                .filter(n -> n instanceof ValidationPanel)
                .map(n -> (ValidationPanel) n)
                .findFirst().orElse(null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.UnifiedShellExplorerSchematronTest"`
Expected: FAIL — the wait for `getProblemCount() > 0` times out (`TimeoutException`), because nothing is wired to the Validate button yet (compilation succeeds: Task 4 provided the API).

- [ ] **Step 3: Wire the callback in UnifiedShellView**

In `src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java`, extend the EXPLORER case of `createSidePanel`:

```java
            case EXPLORER -> {
                var explorer = new org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel(editorHost);
                explorer.setNewFileAction(this::newDocument);
                explorer.setSchematronValidateAction(this::validateWithSchematron);
                yield explorer;
            }
```

Add next to `validateActive()`:

```java
    /**
     * Runs the Explorer's one-click Schematron validation in the Validation activity:
     * the active document goes through the panel's single-file flow (problems list,
     * report button), everything else through the batch flow with the given Schematron.
     * The Explorer already bound the Schematron to the active document before calling.
     */
    private void validateWithSchematron(java.util.List<java.io.File> files, java.io.File schematron) {
        selectionModel.select(Activity.VALIDATION);
        var panel = validationPanel();
        boolean activeDocumentOnly = files.size() == 1 && editorHost.getActiveDocument()
                .map(org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument::getPath)
                .map(p -> p.toFile().getAbsoluteFile().equals(files.get(0).getAbsoluteFile()))
                .orElse(false);
        if (activeDocumentOnly) {
            panel.revalidate();
        } else {
            panel.runBatch(files, schematron);
        }
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.UnifiedShellExplorerSchematronTest" --tests "org.fxt.freexmltoolkit.controls.shell.UnifiedShellValidateTest"`
Expected: BUILD SUCCESSFUL — the new end-to-end flow works and the existing toolbar-validate flow stays green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellExplorerSchematronTest.java
git commit -m "feat(shell): wire Explorer Schematron Validate into the Validation activity

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Docs, quality gate, push

**Files:**
- Modify: `docs/unified-shell.md` (after the "Transform Bar" section, ~line 461-501)
- No production-code changes.

**Interfaces:**
- Consumes: the finished feature (Tasks 1-5).
- Produces: user documentation; a clean, pushed main branch.

- [ ] **Step 1: Document the Schematron bar in docs/unified-shell.md**

Insert a new subsection directly after the existing "### Transform Bar (one-click XSLT from the Explorer)" section (i.e. after its closing paragraph about the sticky, shared stylesheet, before "## Favorites Panel"):

```markdown
### Schematron Bar (one-click validation from the Explorer)

> **New in July 2026** - Validate XML files against a Schematron straight from the
> Explorer, without switching to the Validation activity first.

A **Schematron bar** sits directly below the Transform bar. It keeps one Schematron
fixed and validates whichever XML file(s) you pick in the tree - ideal for repeatedly
checking many files against the same rule set.

The bar has two controls:

- **Schematron picker** - a dropdown (checks-grid icon) labelled **"Schematron…"** until you
  choose one, then showing the chosen file's name. Click it to:
    - reapply one of your **recently used Schematrons** (listed at the top),
    - pick one of your **Favorites** (the same Schematron favorites the
      [Validation panel](#validation-panel) offers),
    - **Choose Schematron…** - pick a `.sch` / `.schematron` file from disk, or
    - **Clear recent** - empty the recent list.
- **Validate** button (play icon) - validates your selected XML file(s) against the chosen
  Schematron. Tooltip: *"Validate selected XML file(s) with the current Schematron"*.

Picking a Schematron also **binds it to the active document**, so the
[Validation panel](#validation-panel) and live validation use it too. Clicking
**Validate** switches to the **Validation** activity and shows the result there:
a single active document runs through the normal single-file flow (problems list,
detailed Schematron report), a multi-file tree selection through the **batch** flow
with one RESULTS row per file. With no tree selection, the active editor document
is validated.
```

Also update the Explorer tip in the Transform-panel section only if it reads oddly next to the new bar (no change expected).

- [ ] **Step 2: Format and run the affected test classes**

```bash
./gradlew spotlessApply
./gradlew test --tests "org.fxt.freexmltoolkit.service.PropertiesServiceRecentSchematronTest" \
               --tests "org.fxt.freexmltoolkit.controls.shell.editor.FavoritesMenuSubmenuTest" \
               --tests "org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanelBatchResultsTest" \
               --tests "org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanelSchematronTest" \
               --tests "org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanelTest" \
               --tests "org.fxt.freexmltoolkit.controls.shell.UnifiedShellExplorerSchematronTest" \
               --tests "org.fxt.freexmltoolkit.controls.shell.UnifiedShellValidateTest" \
               --tests "*IconifyIconCoverageTest"
```

Expected: BUILD SUCCESSFUL. If `spotlessApply` reformatted files, include them in the commit.

- [ ] **Step 3: Full build as the final gate**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (full suite; the project runs UI tests with `forkEvery=1`, so this takes a while). Trust `EXIT=${PIPESTATUS[0]}` / the test-result XML over a background task's notification exit code if run in background.

- [ ] **Step 4: Commit and push**

```bash
git add docs/unified-shell.md
git commit -m "docs(shell): document the Explorer Schematron bar

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
git push
```

(Per the project's working agreement, finishing the feature includes commit + push.)
