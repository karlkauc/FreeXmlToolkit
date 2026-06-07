# Chrome-Retire Sub-project B — Shell as Root + Relocate Globals — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `FxtGui` load the Unified Shell as the root scene and relocate the five global concerns still owned only by `MainController`, so nothing is lost once `main.fxml`/`MainController` stop being loaded (the orphaned chrome is deleted in sub-project C).

**Architecture:** Four safe refactors with the boot UNCHANGED (Tasks 1–4: `ThemeManager`, recent-on-open, shell drag-drop, `ShellBootstrap`), then one cutover (Task 5: flip `FxtGui` to load `tab_unified_shell.fxml` as root + rewire `FxtGui.stop()`). The shell already owns the status-bar memory monitor, file-op keyboard shortcuts, and the file-open API.

**Tech Stack:** Java 25, JavaFX 24, JUnit 5 + TestFX (Monocle headless). Single-class test: `./gradlew test --tests "FQCN"` (prefix `xvfb-run -a` if a display is needed). Do NOT rely on full `./gradlew build` going green — it hits the documented headless TestFX toolkit-init cascade; verify per class.

**Spec:** `docs/superpowers/specs/2026-06-07-chrome-retire-B-shell-root-design.md`

---

## Conventions

- New classes: `controls/shell/ThemeManager.java`, `controls/shell/ShellBootstrap.java`.
- Tests mirror under `src/test/java/...`.
- Theme resources (verified): `/css/dark-theme.css`, `/css/light-theme.css`; root style classes `fxt-theme-dark` / `fxt-theme-light`; property key `ui.theme` (`"dark"`/`"light"`).
- Commit after each task; push at task end.

---

## Task 1: `ThemeManager` (unify theme application)

Today `MainController.applyTheme()` swaps the theme CSS on the Scene + sets the root style class; the shell `SettingsPanel.applyTheme()` only sets the style class + persists `ui.theme` (it does NOT load the CSS — a real gap). Introduce one `ThemeManager` both delegate to.

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/ThemeManager.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanel.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controller/MainController.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/ThemeManagerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class ThemeManagerTest {

    private Scene scene;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        scene = new Scene(new StackPane(), 200, 200);
        stage.setScene(scene);
    }

    @Test
    void applyDarkThenLightSwapsStylesheetAndRootClass() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, true);
            return null;
        });
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")), "dark css added");
        assertTrue(scene.getRoot().getStyleClass().contains("fxt-theme-dark"), "dark root class");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ThemeManager.apply(scene, false);
            return null;
        });
        assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("light-theme.css")), "light css added");
        assertFalse(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")), "dark css removed");
        assertTrue(scene.getRoot().getStyleClass().contains("fxt-theme-light"), "light root class");
        assertFalse(scene.getRoot().getStyleClass().contains("fxt-theme-dark"), "dark root class removed");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ThemeManagerTest"`
Expected: compile error — `ThemeManager` does not exist.

- [ ] **Step 3: Implement `ThemeManager`**

```java
package org.fxt.freexmltoolkit.controls.shell;

import javafx.scene.Scene;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Single source of truth for applying the light/dark theme: swaps the theme
 * stylesheet on the scene, toggles the design-token root style class, and
 * persists the choice. Used by the boot path (FxtGui) and the shell SettingsPanel.
 */
public final class ThemeManager {

    private ThemeManager() {
    }

    /** Applies the light or dark theme to {@code scene} and persists {@code ui.theme}. */
    public static void apply(Scene scene, boolean dark) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().removeIf(s -> s.contains("light-theme.css") || s.contains("dark-theme.css"));
        String css = dark ? "/css/dark-theme.css" : "/css/light-theme.css";
        var url = ThemeManager.class.getResource(css);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
        var root = scene.getRoot();
        if (root != null) {
            root.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
            root.getStyleClass().add(dark ? "fxt-theme-dark" : "fxt-theme-light");
        }
        try {
            ServiceRegistry.get(PropertiesService.class).set("ui.theme", dark ? "dark" : "light");
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests) — the visual switch is still applied
        }
    }

    /** @return {@code true} if the persisted theme is dark. */
    public static boolean currentIsDark() {
        try {
            return "dark".equals(ServiceRegistry.get(PropertiesService.class).get("ui.theme"));
        } catch (Throwable t) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ThemeManagerTest"`
Expected: PASS.

- [ ] **Step 5: Delegate `SettingsPanel.applyTheme` to `ThemeManager`**

In `SettingsPanel.java`, replace the body of `applyTheme(boolean darkTheme)` with:

```java
    public void applyTheme(boolean darkTheme) {
        (darkTheme ? dark : light).setSelected(true);
        org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(getScene(), darkTheme);
    }
```

(This now ALSO loads the theme CSS, closing the gap. The `light.setOnAction(e -> applyTheme(false))` / `dark.setOnAction(e -> applyTheme(true))` wiring is unchanged.)

- [ ] **Step 6: Delegate `MainController.applyTheme` to `ThemeManager`**

In `MainController.java`, replace the body of `applyTheme()` with:

```java
    public void applyTheme() {
        Scene scene = contentPane.getScene();
        boolean dark = "dark".equals(propertiesService.get("ui.theme"));
        org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(scene, dark);
    }
```

(Keep the method `public` and the `Scene` import. `MainController` still boots via main.fxml in Tasks 1–4, so this keeps theming working; the method is deleted in C.)

- [ ] **Step 7: Run regressions**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ThemeManagerTest" --tests "*SettingsPanelTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/ThemeManager.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanel.java \
        src/main/java/org/fxt/freexmltoolkit/controller/MainController.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/ThemeManagerTest.java
git commit -m "refactor(shell): unify theme application in ThemeManager (fixes SettingsPanel CSS gap)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Task 2: Recent-on-open in `EditorHost`

`EditorHost.openFile(Path)` does not currently record the file in the recent list (that was `MainController.addFileToRecentFiles`). Add it so the shell-owned open path keeps recents current.

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHostRecentFilesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class EditorHostRecentFilesTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
    }

    @Test
    void openingAFileRecordsItInRecentFiles(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("recent-probe.xml");
        Files.writeString(xml, "<root/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitForFxEvents();

        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        boolean present = props.getLastOpenFiles().stream()
                .anyMatch(f -> f.getName().equals("recent-probe.xml"));
        assertTrue(present, "opened file should be recorded in recent files");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostRecentFilesTest"`
Expected: FAIL — the opened file is not in the recent list.

- [ ] **Step 3: Record the recent file in `EditorHost.openFile`**

In `EditorHost.openFile(Path path)`, after `addTab(tab);` (and before `loadAsync(tab, path);`), add:

```java
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .addLastOpenFile(path.toFile());
        } catch (Throwable ignored) {
            // properties service unavailable — no recent-files persistence
        }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostRecentFilesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHostRecentFilesTest.java
git commit -m "feat(shell): record opened files in the recent list from EditorHost

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Task 3: Drag & drop in the shell

Move the global file drag-drop (today on `MainController.contentPane`) into `UnifiedShellView`, scoped to the shell, routing supported files to `editorHost.openFile`.

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/ShellDragDropTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class ShellDragDropTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = WaitForAsyncUtils.waitForAsyncFx(3000, UnifiedShellView::new);
    }

    @Test
    void acceptsXmlFamilyFilesOnly() {
        assertTrue(UnifiedShellView.acceptsDrop(List.of(new File("a.xml"))));
        assertTrue(UnifiedShellView.acceptsDrop(List.of(new File("a.xsd"))));
        assertFalse(UnifiedShellView.acceptsDrop(List.of(new File("a.png"))));
        assertFalse(UnifiedShellView.acceptsDrop(List.of()));
    }

    @Test
    void openDroppedFilesOpensSupportedFiles(@TempDir Path tmp) throws Exception {
        File xml = tmp.resolve("dropped.xml").toFile();
        Files.writeString(xml.toPath(), "<root/>");
        File png = tmp.resolve("ignored.png").toFile();
        Files.writeString(png.toPath(), "x");

        int opened = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.openDroppedFiles(List.of(xml, png)));
        assertEquals(1, opened, "only the XML file is opened");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ShellDragDropTest"`
Expected: compile error — `acceptsDrop` / `openDroppedFiles` do not exist.

- [ ] **Step 3: Add drag-drop to `UnifiedShellView`**

Add two methods + wire the handlers in the constructor. Add these methods to `UnifiedShellView`:

```java
    /** @return {@code true} if {@code files} contains at least one XML-family file the shell can open. */
    public static boolean acceptsDrop(java.util.List<java.io.File> files) {
        return files != null && org.fxt.freexmltoolkit.service.DragDropService
                .hasFilesWithExtensions(files, org.fxt.freexmltoolkit.service.DragDropService.ALL_XML_RELATED);
    }

    /** Opens every supported (XML-family) file from {@code files} in the editor host. @return the count opened. */
    public int openDroppedFiles(java.util.List<java.io.File> files) {
        java.util.List<java.io.File> supported = org.fxt.freexmltoolkit.service.DragDropService
                .filterByExtensions(files, org.fxt.freexmltoolkit.service.DragDropService.ALL_XML_RELATED);
        for (java.io.File f : supported) {
            editorHost.openFile(f.toPath());
        }
        return supported.size();
    }
```

In the `UnifiedShellView` constructor (after the existing wiring, e.g. after `editorHost.setWelcomeActionHandler(...)`), register the drag handlers:

```java
        setOnDragOver(e -> {
            if (e.getDragboard().hasFiles() && acceptsDrop(e.getDragboard().getFiles())) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            e.consume();
        });
        setOnDragDropped(e -> {
            boolean ok = false;
            if (e.getDragboard().hasFiles()) {
                ok = openDroppedFiles(e.getDragboard().getFiles()) > 0;
            }
            e.setDropCompleted(ok);
            e.consume();
        });
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ShellDragDropTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the shell regression**

Run: `./gradlew test --tests "*UnifiedShellViewTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/ShellDragDropTest.java
git commit -m "feat(shell): shell-scoped drag-and-drop opens XML-family files in the editor host

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

> The legacy `MainController.contentPane` drag-drop handler still exists and runs while the app boots through main.fxml — it is harmlessly redundant with the shell's until the Task-5 flip (after which MainController isn't loaded). It is deleted in C.

---

## Task 4: `ShellBootstrap` (startup update checks)

Extract the app-update + FundsXML-update startup scheduling out of `MainController.initialize()` into a `ShellBootstrap` singleton that owns its own scheduler. `MainController.initialize()` calls it (boot unchanged); Task 5 calls it from `FxtGui` instead.

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/ShellBootstrap.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controller/MainController.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/AboutDialog.java` (make one helper reusable)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/ShellBootstrapTest.java`

- [ ] **Step 1: Make `AboutDialog.showUpdateDialog` reusable**

In `AboutDialog.java`, change the visibility of the ported `showUpdateDialog(UpdateInfo)` helper from `private static` to `public static` (so `ShellBootstrap` can reuse the exact update-notification + download flow rather than duplicating it). Do not change its body.

- [ ] **Step 2: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ShellBootstrapTest {

    @Test
    void scheduleAndShutdownAreSafe() {
        ShellBootstrap boot = ShellBootstrap.getInstance();
        // Scheduling startup tasks must not throw (the live network check runs on a background
        // thread and is independent of this call returning).
        assertDoesNotThrow(boot::scheduleStartupTasks);
        // Shutdown must be idempotent and not throw.
        assertDoesNotThrow(boot::shutdown);
        assertDoesNotThrow(boot::shutdown);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ShellBootstrapTest"`
Expected: compile error — `ShellBootstrap` does not exist.

- [ ] **Step 4: Implement `ShellBootstrap`**

```java
package org.fxt.freexmltoolkit.controls.shell;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shell.editor.AboutDialog;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.UpdateCheckService;

/**
 * Owns the application's startup background tasks (app-update + FundsXML-update
 * checks) and their scheduler, decoupled from the (retiring) MainController.
 * Invoked once at boot by FxtGui; shut down by FxtGui.stop().
 */
public final class ShellBootstrap {

    private static final Logger logger = LogManager.getLogger(ShellBootstrap.class);
    private static final ShellBootstrap INSTANCE = new ShellBootstrap();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "ShellBootstrap-Scheduler");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean scheduled;

    private ShellBootstrap() {
    }

    public static ShellBootstrap getInstance() {
        return INSTANCE;
    }

    /** Schedules the startup update checks (runs once; subsequent calls are no-ops). */
    public synchronized void scheduleStartupTasks() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        scheduler.schedule(this::checkForAppUpdate, 2, TimeUnit.SECONDS);
        scheduler.schedule(this::checkForFundsXmlUpdate, 5, TimeUnit.SECONDS);
    }

    private void checkForAppUpdate() {
        try {
            UpdateCheckService svc = ServiceRegistry.get(UpdateCheckService.class);
            if (svc == null || !svc.isUpdateCheckEnabled()) {
                return;
            }
            svc.checkForUpdates().thenAccept(info -> {
                if (info != null && info.updateAvailable()) {
                    Platform.runLater(() -> AboutDialog.showUpdateDialog(info));
                }
            }).exceptionally(ex -> {
                logger.warn("Startup update check failed: {}", ex.getMessage());
                return null;
            });
        } catch (Throwable t) {
            logger.warn("Startup update check error: {}", t.getMessage());
        }
    }

    private void checkForFundsXmlUpdate() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            var checker = new org.fxt.freexmltoolkit.service.fundsxml.FundsXmlUpdateChecker(
                    props,
                    ServiceRegistry.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService.class),
                    org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache.getInstance());
            checker.runIfDue().ifPresent(release -> Platform.runLater(() -> {
                String body = "A newer FundsXML schema release is available on GitHub:\n\n"
                        + "  • Tag: " + release.tagName() + "\n"
                        + (release.publishedAt() == null ? "" : "  • Published: " + release.publishedAt() + "\n")
                        + "\nOpen the FundsXML activity and click 'Download / Update Content' to install it.";
                org.fxt.freexmltoolkit.util.DialogHelper.showInformation("FundsXML Update Available",
                        "New FundsXML release: " + release.tagName(), body);
            }));
        } catch (Throwable t) {
            logger.warn("FundsXML startup check failed: {}", t.getMessage());
        }
    }

    /** Stops the scheduler. Idempotent. */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
```

> Verify the exact constructor signature of `FundsXmlUpdateChecker(PropertiesService, FundsXmlExtensionService, FundsXmlCache)` and `runIfDue()` against the source (the report confirmed `FundsXmlUpdateChecker(PropertiesService, ...)` + `Optional<GitHubRelease> runIfDue()` with `release.tagName()`/`release.publishedAt()`). Verify `DialogHelper` is `org.fxt.freexmltoolkit.util.DialogHelper`. Adjust the FQNs if the source differs.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ShellBootstrapTest"`
Expected: PASS.

- [ ] **Step 6: Route `MainController.initialize()` through `ShellBootstrap`**

In `MainController.initialize()`, find the block that schedules the startup update checks (inside the `Platform.runLater(...)`):

```java
        scheduler.schedule(this::checkForUpdatesOnStartup, 2, TimeUnit.SECONDS);
        scheduler.schedule(this::checkFundsXmlUpdatesOnStartup, 5, TimeUnit.SECONDS);
```

Replace those two lines with a single delegation:

```java
        org.fxt.freexmltoolkit.controls.shell.ShellBootstrap.getInstance().scheduleStartupTasks();
```

Leave `checkForUpdatesOnStartup()` / `checkFundsXmlUpdatesOnStartup()` in `MainController` for now (dead after this; deleted in C) — do NOT delete them in B to keep this a minimal, low-risk edit. (They simply become unused.)

- [ ] **Step 7: Run regressions**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ShellBootstrapTest" --tests "*HelpDialogsTest"`
Expected: PASS (HelpDialogsTest confirms `AboutDialog` still compiles after the visibility change).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/ShellBootstrap.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/AboutDialog.java \
        src/main/java/org/fxt/freexmltoolkit/controller/MainController.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/ShellBootstrapTest.java
git commit -m "refactor(shell): ShellBootstrap owns startup update checks (off MainController)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Task 5: The flip — FxtGui loads the shell as root

Change the boot to load `tab_unified_shell.fxml` as the root scene, apply the initial theme via `ThemeManager`, schedule startup tasks via `ShellBootstrap`, and rewire `FxtGui.stop()` to do cleanup itself (no `MainController` reference). After this, `MainController`/the chrome are orphaned-but-present.

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/FxtGui.java`
- Modify: `src/test/java/org/fxt/freexmltoolkit/controls/shell/AppBootsIntoShellTest.java`

- [ ] **Step 1: Update the cutover test FIRST (it should fail against the current boot)**

Replace `AppBootsIntoShellTest.java` with a version that asserts the shell FXML FxtGui now loads is the shell root:

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Cutover guard: FxtGui boots by loading the shell FXML directly as the root
 * scene (no MainController, no navigation). This test loads the SAME FXML FxtGui
 * loads and asserts the Unified Shell is present at the root.
 */
@ExtendWith(ApplicationExtension.class)
class AppBootsIntoShellTest {

    private Parent root;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1024, 720));
        stage.show();
    }

    @Test
    void shellFxmlIsTheRoot() {
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(root.lookup(".fxt-shell"), "the shell FXML must render the Unified Shell as root");
    }
}
```

- [ ] **Step 2: Run it (should pass already — it just loads the shell FXML directly)**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.AppBootsIntoShellTest"`
Expected: PASS. (This test validates the FXML target of the flip; the FxtGui change below makes the real app use it.)

- [ ] **Step 3: Flip `FxtGui.start()` to load the shell FXML**

In `FxtGui.java`, in `start(Stage)`, replace the three lines that load main.fxml + grab the controller:

```java
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/main.fxml"));
                Parent root = loader.load();
                mainController = loader.getController();
```

with:

```java
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
                Parent root = loader.load();
```

(The `mainController` field is no longer assigned — that's fine; it stays declared and null. It is removed in C.)

Then, right after `var scene = new Scene(root, 1024, 768);`, add the initial theme application:

```java
                org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(scene,
                        org.fxt.freexmltoolkit.controls.shell.ThemeManager.currentIsDark());
```

And right after `startUsageTracking();` (inside the try), add the startup-task scheduling:

```java
                org.fxt.freexmltoolkit.controls.shell.ShellBootstrap.getInstance().scheduleStartupTasks();
```

- [ ] **Step 4: Rewire `FxtGui.stop()` (remove the MainController dependency)**

In `FxtGui.stop()`, replace the top block that references `mainController`:

```java
        executorService.shutdown();
        mainController.scheduler.shutdown();

        shutdownExecutor(executorService);
        shutdownExecutor(mainController.scheduler);
        shutdownExecutor(mainController.service);

        // Shutdown centralized thread pool manager
        try {
            ThreadPoolManager.getInstance().shutdown();
            logger.debug("ThreadPoolManager shut down successfully");
        } catch (Exception e) {
            logger.warn("Error shutting down ThreadPoolManager", e);
        }

        mainController.shutdown();
```

with this (shut down the shell bootstrap + the engines/services directly — taking over what `MainController.shutdown()` used to do):

```java
        executorService.shutdown();
        shutdownExecutor(executorService);

        // Shell startup scheduler
        try {
            org.fxt.freexmltoolkit.controls.shell.ShellBootstrap.getInstance().shutdown();
        } catch (Throwable t) {
            logger.warn("Error shutting down ShellBootstrap: {}", t.getMessage());
        }

        // Application services (previously shut down by MainController.shutdown())
        try {
            org.fxt.freexmltoolkit.service.UpdateCheckService svc =
                    ServiceRegistry.get(org.fxt.freexmltoolkit.service.UpdateCheckService.class);
            if (svc != null) {
                svc.shutdown();
            }
        } catch (Throwable t) {
            logger.warn("Error shutting down UpdateCheckService: {}", t.getMessage());
        }
        try {
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.getInstance().shutdown();
        } catch (Throwable t) {
            logger.warn("Error shutting down XsltTransformationEngine: {}", t.getMessage());
        }
        try {
            org.fxt.freexmltoolkit.service.XPathExecutionEngine.getInstance().shutdown();
        } catch (Throwable t) {
            logger.warn("Error shutting down XPathExecutionEngine: {}", t.getMessage());
        }
        try {
            ThreadPoolManager.getInstance().shutdown();
        } catch (Throwable t) {
            logger.warn("Error shutting down ThreadPoolManager: {}", t.getMessage());
        }
```

(Leave the rest of `stop()` — usage tracking end, duration save, `System.exit(0)` — unchanged. Confirm `ServiceRegistry` is imported in FxtGui; if not, add `import org.fxt.freexmltoolkit.di.ServiceRegistry;`. Confirm `XsltTransformationEngine`/`XPathExecutionEngine` shutdown method names against `MainController.shutdown()`'s usage — they were `getInstance().shutdown()`.)

- [ ] **Step 5: Build the app modules + run the cutover test**

Run: `xvfb-run -a ./gradlew compileJava compileTestJava test --tests "org.fxt.freexmltoolkit.controls.shell.AppBootsIntoShellTest" --tests "*UnifiedShellIntegrationTest"`
Expected: compile GREEN; `AppBootsIntoShellTest` PASS. For `UnifiedShellIntegrationTest`: its legacy-footer-restore cases assume booting through main.fxml and navigating between shell and legacy pages — those assertions are now obsolete. Update that test: keep any case that loads `tab_unified_shell.fxml` and asserts the shell renders; DELETE or rewrite the cases that `navigateToPage("...")` on a MainController (there is no MainController in the boot path anymore). If the whole test was MainController-centric, reduce it to a shell-FXML-loads-and-renders smoke test (mirroring `AppBootsIntoShellTest`) — do not leave broken references to MainController navigation.

- [ ] **Step 6: Manually sanity-check the boot (optional but recommended)**

If a display is available, run the app and confirm it boots into the shell with the correct theme, drag-drop opens a file, and the status bar shows memory:
Run: `xvfb-run -a ./gradlew run` (or on a real display, `./gradlew run`). Expected: the shell appears as the whole window (no legacy MenuBar/sidebar). This is a smoke check; do not block the task on it if headless-only.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/FxtGui.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/AppBootsIntoShellTest.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellIntegrationTest.java
git commit -m "feat(shell): boot the Unified Shell as the root scene (chrome now orphaned)

FxtGui loads tab_unified_shell.fxml as root, applies the theme via ThemeManager,
schedules startup checks via ShellBootstrap, and FxtGui.stop() cleans up directly
without MainController. The legacy main.fxml/MainController/Welcome/Settings are no
longer loaded (deleted in sub-project C).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Self-review checklist (run before execution)

- Spec coverage: ThemeManager (T1), recent-on-open (T2), shell drag-drop (T3), ShellBootstrap (T4), the flip + stop rewire (T5). ✓
- Boot stays unchanged through T1–T4 (MainController still loads main.fxml); only T5 flips it. ✓
- Type consistency: `ThemeManager.apply(Scene, boolean)`/`currentIsDark()`; `UnifiedShellView.acceptsDrop(List<File>)`/`openDroppedFiles(List<File>)`; `ShellBootstrap.getInstance()/scheduleStartupTasks()/shutdown()`; `AboutDialog.showUpdateDialog(UpdateInfo)` made public. All used consistently across tasks. ✓
- The two flagged verifications (FundsXmlUpdateChecker ctor/runIfDue; engine shutdown method names) are real signatures to confirm against source, with the report's confirmation noted.

## Out of scope (sub-project C)

Delete the orphaned chrome: `main.fxml`, `MainController` (incl. the now-dead `applyTheme`/`updateMemoryUsage`/`setupKeyboardShortcuts`/`initializeDragAndDrop`/`checkForUpdatesOnStartup`/`checkFundsXmlUpdatesOnStartup`/bridge methods), `WelcomeController`, `SettingsController`, `welcome.fxml`, `settings.fxml`, the MenuBar/sidebar/footer, and their tests. B leaves these present-but-unloaded.
