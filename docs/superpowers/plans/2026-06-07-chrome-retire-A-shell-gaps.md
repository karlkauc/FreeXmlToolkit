# Chrome-Retire Sub-project A — Close Shell Feature Gaps — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Unified Shell feature-complete versus the legacy chrome (full About + Keyboard-Shortcuts dialogs in Help, Settings extras, Welcome dashboard stats, and a conditional FundsXML activity) so the menu-free shell can later replace the chrome with nothing lost.

**Architecture:** Four independent ports, each landing in an existing shell surface (Help/Settings/Welcome panels) except FundsXML which gets a new conditional activity. Overwhelmingly UI wiring over existing services (`VersionUtil`, `UpdateCheckService`, `PropertiesService`, `UsageTrackingService`, `SkillTracker`, `FavoritesService`, the `fundsxml/` services). The legacy chrome stays untouched.

**Tech Stack:** Java 25, JavaFX 24, JUnit 5 + TestFX (Monocle headless). Single-class test: `./gradlew test --tests "FQCN"` (prefix `xvfb-run -a` if a display is needed). Do NOT rely on full `./gradlew build` going green — it hits the documented headless TestFX toolkit-init cascade; verify per-class.

**Spec:** `docs/superpowers/specs/2026-06-07-chrome-retire-A-shell-gaps-design.md`

---

## Conventions

- New shell classes live in `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/` (same package as HelpPanel/SettingsPanel/EditorWelcomePane), except `Activity`/`ActivityBar`/`UnifiedShellView` which live in `controls/shell/`.
- Tests mirror under `src/test/java/...`.
- Shell style conventions: root `VBox` with styleClass `fxt-side-panel-content`; section titles `fxt-side-panel-title`; muted text `fxt-placeholder-text`; buttons `fxt-tool-button` with a 16px `IconifyIcon`. Reuse the `button(text, icon, Runnable)` factory pattern HelpPanel uses.
- Commit after each task; push at task end.

---

## Task 1: Help — full About + Keyboard-Shortcuts dialogs

Port the legacy `MainController.showAboutDialog()` and `showKeyboardShortcuts()` into two standalone, shell-reachable dialog classes, and add two buttons to `HelpPanel`. This is a near-verbatim port (the source bodies exist in `MainController.java`); the only adaptations are taking an owner `Window` instead of `contentPane.getScene().getWindow()`, dropping `@FXML`, and making the private helpers local.

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/AboutDialog.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/KeyboardShortcutsDialog.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/HelpPanel.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/HelpDialogsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.control.Dialog;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class HelpDialogsTest {

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    @Test
    void aboutDialogBuildsWithVersionAndRuntimeInfo() {
        Dialog<Void> dialog = WaitForAsyncUtils.waitForAsyncFx(3000, () -> AboutDialog.build(null));
        assertNotNull(dialog.getDialogPane().getContent(), "about content");
        // The info grid embeds the Java runtime version (from System.getProperty).
        String text = dialogText(dialog);
        assertTrue(text.contains(System.getProperty("java.version")), "shows java version");
    }

    @Test
    void shortcutsDialogBuilds() {
        Dialog<?> dialog = WaitForAsyncUtils.waitForAsyncFx(3000, () -> KeyboardShortcutsDialog.build());
        assertNotNull(dialog.getDialogPane(), "shortcuts dialog pane");
    }

    private static String dialogText(Dialog<?> dialog) {
        StringBuilder sb = new StringBuilder();
        collect(dialog.getDialogPane().getContent(), sb);
        return sb.toString();
    }

    private static void collect(javafx.scene.Node node, StringBuilder sb) {
        if (node instanceof javafx.scene.control.Label l) {
            sb.append(l.getText()).append('\n');
        }
        if (node instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) {
                collect(child, sb);
            }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.HelpDialogsTest"`
Expected: compile error — `AboutDialog` / `KeyboardShortcutsDialog` do not exist.

- [ ] **Step 3: Create `AboutDialog`**

Port the body of `MainController.showAboutDialog()` (MainController.java ~lines 830–983) plus its helpers `addInfoRow` (~985–993), `linkButton` (~995–1008), `openExternalUrl` (~1010–1019), and `checkForUpdatesFromAbout` (~1021–1049) into this class. Create the file with this exact structure and port the bodies verbatim from those line ranges, applying only the adaptations noted in the comments:

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Objects;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.util.VersionUtil;

/** Shell "About" dialog (ported from the legacy MainController). */
public final class AboutDialog {

    private static final Logger logger = LogManager.getLogger(AboutDialog.class);

    private AboutDialog() {
    }

    /** Builds the dialog. {@code owner} may be null (no owner window). */
    public static Dialog<Void> build(Window owner) {
        final String version = VersionUtil.getVersion();
        final String buildTs = VersionUtil.getBuildTimestampFormatted();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("About FreeXmlToolkit");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(Objects.requireNonNull(
                AboutDialog.class.getResource("/css/dialog-theme.css")).toExternalForm());
        dialogPane.setPrefWidth(560);

        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) dialogPane.getScene().getWindow();
            stage.getIcons().add(new Image(Objects.requireNonNull(
                    AboutDialog.class.getResourceAsStream("/img/logo.png"))));
        } catch (Exception e) {
            logger.warn("Could not load logo for about dialog window.", e);
        }

        ImageView logo = null;
        try {
            logo = new ImageView(new Image(Objects.requireNonNull(
                    AboutDialog.class.getResourceAsStream("/img/logo.png"))));
            logo.setFitHeight(72);
            logo.setPreserveRatio(true);
            logo.setSmooth(true);
        } catch (Exception e) {
            logger.warn("Could not load logo for about dialog graphic.", e);
        }

        Label title = new Label("FreeXmlToolkit");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 700; -fx-text-fill: #1f2937;");
        Label tagline = new Label("Universal Toolkit for XML, XSD, XSLT, Schematron & FOP");
        tagline.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #6b7280;");
        tagline.setWrapText(true);
        Label versionPill = new Label("v" + version);
        versionPill.setStyle("-fx-background-color: #e7f3ff;-fx-text-fill: #0b5ed7;-fx-font-weight: 600;"
                + "-fx-font-size: 12px;-fx-padding: 3 10 3 10;-fx-background-radius: 12;");
        VBox titleBox = new VBox(4, title, tagline, versionPill);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox header = new HBox(16);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        if (logo != null) {
            header.getChildren().add(logo);
        }
        header.getChildren().add(titleBox);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        GridPane info = new GridPane();
        info.setHgap(14);
        info.setVgap(6);
        info.setStyle("-fx-background-color: #f9fafb;-fx-background-radius: 8;-fx-border-color: #e5e7eb;"
                + "-fx-border-radius: 8;-fx-padding: 12;");
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(120);
        c1.setHalignment(javafx.geometry.HPos.LEFT);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        info.getColumnConstraints().addAll(c1, c2);
        int row = 0;
        addInfoRow(info, row++, "Version", version);
        if (!buildTs.isBlank()) {
            addInfoRow(info, row++, "Build", buildTs);
        }
        addInfoRow(info, row++, "Java", System.getProperty("java.version", "?")
                + "  (" + System.getProperty("java.vm.vendor", "?") + ")");
        addInfoRow(info, row++, "JavaFX", System.getProperty("javafx.runtime.version", "?"));
        addInfoRow(info, row++, "OS", System.getProperty("os.name", "?") + " "
                + System.getProperty("os.version", "") + "  (" + System.getProperty("os.arch", "?") + ")");

        Label copyright = new Label("Copyright © " + VersionUtil.getVendor()
                + " 2024-2026. All rights reserved.");
        copyright.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6b7280;");
        Hyperlink licenseLink = new Hyperlink("Licensed under the Apache License, Version 2.0");
        licenseLink.setOnAction(e -> openExternalUrl("http://www.apache.org/licenses/LICENSE-2.0"));
        licenseLink.setStyle("-fx-padding: 0; -fx-text-fill: #0b5ed7;");
        VBox legal = new VBox(2, copyright, licenseLink);

        Button githubBtn = linkButton("bi-github", "GitHub",
                "https://github.com/karlkauc/FreeXmlToolkit");
        Button docsBtn = linkButton("bi-book", "Documentation",
                "https://karlkauc.github.io/FreeXmlToolkit");
        Button issueBtn = linkButton("bi-bug", "Report an issue",
                "https://github.com/karlkauc/FreeXmlToolkit/issues/new");
        HBox links = new HBox(8, githubBtn, docsBtn, issueBtn);
        links.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox content = new VBox(14, header, new Separator(), info, legal, links);
        content.setStyle("-fx-padding: 18 20 8 20;");
        dialogPane.setContent(content);

        ButtonType copyVersionType = new ButtonType("Copy version", ButtonBar.ButtonData.LEFT);
        ButtonType checkUpdatesType = new ButtonType("Check for updates", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().addAll(copyVersionType, checkUpdatesType, ButtonType.CLOSE);

        Button copyBtn = (Button) dialogPane.lookupButton(copyVersionType);
        copyBtn.setGraphic(new IconifyIcon("bi-clipboard"));
        copyBtn.addEventFilter(ActionEvent.ACTION, evt -> {
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                    java.util.Map.of(javafx.scene.input.DataFormat.PLAIN_TEXT,
                            "FreeXmlToolkit " + version
                                    + (buildTs.isBlank() ? "" : " (build " + buildTs + ")")));
            copyBtn.setText("Copied!");
            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(Duration.seconds(1.5));
            pt.setOnFinished(e -> copyBtn.setText("Copy version"));
            pt.play();
            evt.consume();
        });

        Button updatesBtn = (Button) dialogPane.lookupButton(checkUpdatesType);
        updatesBtn.setGraphic(new IconifyIcon("bi-arrow-clockwise"));
        updatesBtn.addEventFilter(ActionEvent.ACTION, evt -> {
            evt.consume();
            checkForUpdatesFromAbout(updatesBtn);
        });

        return dialog;
    }

    /** Convenience: build and show. */
    public static void show(Window owner) {
        build(owner).showAndWait();
    }

    // PORT these verbatim from MainController (cited line ranges), making them private static:
    //   addInfoRow(GridPane info, int row, String label, String value)   // ~985–993
    //   linkButton(String icon, String text, String url) -> Button       // ~995–1008
    //   openExternalUrl(String url)                                      // ~1010–1019
    //   checkForUpdatesFromAbout(Button updatesBtn)                      // ~1021–1049
    //   (checkForUpdatesFromAbout calls
    //    ServiceRegistry.get(UpdateCheckService.class).checkForUpdates();
    //    keep that call as-is.)
}
```

> When porting: the static analyzer (IconifyIconCoverageTest) requires every `bi-*` literal to exist — the ported literals (`bi-github`, `bi-book`, `bi-bug`, `bi-clipboard`, `bi-arrow-clockwise`) are all already used elsewhere, so they are valid. Keep the `/css/dialog-theme.css` and `/img/logo.png` resource references; they exist.

- [ ] **Step 4: Create `KeyboardShortcutsDialog`**

`MainController.showKeyboardShortcuts()` builds its dialog via `DialogHelper.createHelpDialog(...)`. Reuse that helper directly so the dialog is identical:

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import javafx.scene.control.Dialog;

import org.fxt.freexmltoolkit.controls.dialogs.DialogHelper;

/** Shell "Keyboard Shortcuts" reference dialog (ported from the legacy MainController). */
public final class KeyboardShortcutsDialog {

    private KeyboardShortcutsDialog() {
    }

    public static Dialog<?> build() {
        var features = List.of(
                new String[]{"bi-folder", "File Operations", "Create, open, save, and manage files"},
                new String[]{"bi-pencil", "Edit Operations", "Undo, redo, find and replace text"},
                new String[]{"bi-window", "View Controls", "Toggle full screen and window options"},
                new String[]{"bi-play-circle", "Actions", "Execute operations and manage favorites"}
        );
        var shortcuts = List.of(
                new String[]{"Ctrl+N", "New File"},
                new String[]{"Ctrl+O", "Open File"},
                new String[]{"Ctrl+S", "Save"},
                new String[]{"Ctrl+Shift+S", "Save As"},
                new String[]{"Ctrl+W", "Close"},
                new String[]{"Ctrl+Z", "Undo"},
                new String[]{"Ctrl+Shift+Z", "Redo"},
                new String[]{"Ctrl+F", "Find"},
                new String[]{"Ctrl+H", "Find & Replace"},
                new String[]{"F11", "Toggle Full Screen"},
                new String[]{"F5", "Execute (Validate/Transform)"},
                new String[]{"Ctrl+D", "Add to Favorites"},
                new String[]{"Ctrl+Shift+D", "Toggle Favorites Panel"},
                new String[]{"F1", "Show Help"}
        );
        return DialogHelper.createHelpDialog(
                "Keyboard Shortcuts",
                "Keyboard Shortcuts",
                "Quick reference for all keyboard shortcuts in FreeXmlToolkit",
                "bi-keyboard",
                DialogHelper.HeaderTheme.INFO,
                features,
                shortcuts);
    }

    public static void show() {
        build().showAndWait();
    }
}
```

> Verify the exact return type of `DialogHelper.createHelpDialog(...)` by reading `controls/dialogs/DialogHelper.java`; if it returns `Dialog<Void>` or `Alert`, adjust `build()`'s return type accordingly (the test only asserts `getDialogPane()` is non-null).

- [ ] **Step 5: Add the two buttons to `HelpPanel`**

In `HelpPanel.java`, in the constructor where the existing buttons are added (the GitHub/quick-links/Check-for-Updates section), add two buttons using the existing `button(text, icon, Runnable)` factory, and add them to `getChildren()` near the app-info section:

```java
        Button aboutBtn = button("About", "bi-info-circle",
                () -> AboutDialog.show(getScene() != null ? getScene().getWindow() : null));
        Button shortcutsBtn = button("Keyboard Shortcuts", "bi-keyboard",
                KeyboardShortcutsDialog::show);
```

Add `aboutBtn, shortcutsBtn` into the appropriate `getChildren().addAll(...)` group (e.g. right after the GitHub link button, before the DOCUMENTATION section). Verify `bi-info-circle` and `bi-keyboard` exist in `src/main/resources/icons/iconify/bi.json` (both are standard Bootstrap icons; if `bi-keyboard` is absent, use `bi-command`).

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.HelpDialogsTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: Verify icons + HelpPanel still build**

Run: `./gradlew test --tests "*IconifyIconCoverageTest" --tests "*HelpPanelTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/AboutDialog.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/KeyboardShortcutsDialog.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/HelpPanel.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/HelpDialogsTest.java
git commit -m "feat(shell): full About + Keyboard-Shortcuts dialogs in the Help activity

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Task 2: Settings extras (user info, SSL, usage statistics)

Add three sections to the shell `SettingsPanel`, reading/writing the SAME `PropertiesService` keys (`user.name`, `user.email`, `user.company`, `ssl.trustAllCerts`) and `UsageTrackingService` as the legacy `SettingsController`.

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanel.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanelExtrasTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class SettingsPanelExtrasTest {

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
    }

    @Test
    void userInfoRoundTripsThroughProperties() {
        SettingsPanel panel = WaitForAsyncUtils.waitForAsyncFx(3000, SettingsPanel::new);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setUserName("Ada Lovelace");
            panel.setUserEmail("ada@example.com");
            panel.setUserCompany("Analytical Engines");
            panel.saveSettings();
            return null;
        });
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        assertEquals("Ada Lovelace", props.get("user.name"));
        assertEquals("ada@example.com", props.get("user.email"));
        assertEquals("Analytical Engines", props.get("user.company"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanelExtrasTest"`
Expected: compile error — `setUserName`/`setUserEmail`/`setUserCompany` do not exist.

- [ ] **Step 3: Add the three sections to `SettingsPanel`**

Add fields (near the other control fields):

```java
    private final javafx.scene.control.TextField userName = new javafx.scene.control.TextField();
    private final javafx.scene.control.TextField userEmail = new javafx.scene.control.TextField();
    private final javafx.scene.control.TextField userCompany = new javafx.scene.control.TextField();
    private final javafx.scene.control.CheckBox trustAllCerts = new javafx.scene.control.CheckBox("Trust all certificates");
    private final javafx.scene.control.CheckBox trackingEnabled = new javafx.scene.control.CheckBox("Enable usage tracking");
```

In the constructor, build the sections using the panel's existing `section(...)` + `labeled(text, control)` helpers (read `SettingsPanel.java` to match the exact helper names/usage) and add them to the content VBox alongside the existing sections:

```java
        // USER INFO
        var userSection = section("USER INFO",
                labeled("Name", userName),
                labeled("Email", userEmail),
                labeled("Company", userCompany));

        // SECURITY
        var sslSection = section("SECURITY", trustAllCerts);

        // USAGE STATISTICS
        javafx.scene.control.Button clearStats = new javafx.scene.control.Button("Clear statistics");
        clearStats.getStyleClass().add("fxt-tool-button");
        clearStats.setOnAction(e -> {
            org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl.getInstance().clearStatistics();
        });
        var usageSection = section("USAGE STATISTICS", trackingEnabled, clearStats);
```

Add `userSection, sslSection, usageSection` to the content VBox's `getChildren()` (place them after GENERAL, before/after PROXY — match the existing add order style). If the `section(...)` helper has a different arity/return type, adapt: read the existing `section()` usage in the file and follow it exactly.

In `loadSettings()`, append:

```java
        userName.setText(props.get("user.name") == null ? "" : props.get("user.name"));
        userEmail.setText(props.get("user.email") == null ? "" : props.get("user.email"));
        userCompany.setText(props.get("user.company") == null ? "" : props.get("user.company"));
        trustAllCerts.setSelected(Boolean.parseBoolean(
                props.get("ssl.trustAllCerts") == null ? "false" : props.get("ssl.trustAllCerts")));
        trackingEnabled.setSelected(
                org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl.getInstance().isTrackingEnabled());
```

In `saveSettings()`, append:

```java
        props.set("user.name", userName.getText().trim());
        props.set("user.email", userEmail.getText().trim());
        props.set("user.company", userCompany.getText().trim());
        props.set("ssl.trustAllCerts", String.valueOf(trustAllCerts.isSelected()));
        org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl.getInstance()
                .setTrackingEnabled(trackingEnabled.isSelected());
```

Add test accessors:

```java
    public void setUserName(String v) { userName.setText(v); }
    public void setUserEmail(String v) { userEmail.setText(v); }
    public void setUserCompany(String v) { userCompany.setText(v); }
    public String getUserName() { return userName.getText(); }
    public boolean isTrustAllCertsSelected() { return trustAllCerts.isSelected(); }
```

> `props` is the `PropertiesService` local already obtained in `loadSettings()`/`saveSettings()` (`ServiceRegistry.get(PropertiesService.class)`). Reuse that local; don't re-fetch.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanelExtrasTest"`
Expected: PASS.

- [ ] **Step 5: Verify existing SettingsPanel tests still pass**

Run: `./gradlew test --tests "*SettingsPanelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanel.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanelExtrasTest.java
git commit -m "feat(shell): Settings — user info, SSL trust-all, usage-tracking sections

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

> Detailed favorites management (edit/category) is intentionally NOT added here — the Favorites activity already covers list/add/remove/open, which is sufficient parity for A. If richer favorites editing is wanted, it is a separate small follow-on within the Favorites activity, out of scope for this task.

---

## Task 3: Welcome dashboard statistics (trend sparkline + feature grid)

Add a 7-day usage trend sparkline and a feature-progress grid to `EditorWelcomePane`, fed by a small UI-free data helper over `UsageTrackingService` + `SkillTracker`.

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/WelcomeTrend.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorWelcomePane.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/WelcomeTrendTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WelcomeTrendTest {

    @Test
    void sevenDaySeriesNormalizesToUnitRange() {
        // raw daily totals (oldest→newest)
        List<Integer> raw = List.of(0, 2, 4, 0, 8, 1, 4);
        List<Double> norm = WelcomeTrend.normalize(raw);
        assertEquals(7, norm.size());
        assertEquals(0.0, norm.get(0), 1e-9);     // min → 0
        assertEquals(1.0, norm.get(4), 1e-9);     // max (8) → 1
        assertTrue(norm.stream().allMatch(v -> v >= 0.0 && v <= 1.0));
    }

    @Test
    void allZeroSeriesIsFlatZero() {
        List<Double> norm = WelcomeTrend.normalize(List.of(0, 0, 0));
        assertTrue(norm.stream().allMatch(v -> v == 0.0));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.WelcomeTrendTest"`
Expected: compile error — `WelcomeTrend` does not exist.

- [ ] **Step 3: Implement `WelcomeTrend`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.domain.statistics.DailyStatistics;
import org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl;

/** UI-free data source for the welcome dashboard trend sparkline. */
public final class WelcomeTrend {

    private WelcomeTrend() {
    }

    /**
     * Total activity per day for the last {@code days} days, oldest first.
     * Returns one entry per day (0 for days with no recorded activity).
     */
    public static List<Integer> dailyTotals(int days) {
        List<Integer> totals = new ArrayList<>();
        try {
            // getDailyStats returns most-recent-first; reverse to oldest-first.
            List<DailyStatistics> stats = UsageTrackingServiceImpl.getInstance().getDailyStats(days);
            for (int i = stats.size() - 1; i >= 0; i--) {
                totals.add(stats.get(i).getTotalActivity());
            }
        } catch (Throwable ignored) {
            // service unavailable → empty trend
        }
        return totals;
    }

    /** Normalizes a series to [0,1] (max→1, min→0); an all-equal series maps to all-0. */
    public static List<Double> normalize(List<Integer> raw) {
        List<Double> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        int max = raw.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = raw.stream().mapToInt(Integer::intValue).min().orElse(0);
        int span = max - min;
        for (int v : raw) {
            out.add(span == 0 ? 0.0 : (double) (v - min) / span);
        }
        return out;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.WelcomeTrendTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Render the sparkline + feature grid in `EditorWelcomePane`**

Add a private builder that renders the normalized series as a `Polyline` inside a fixed-size box, plus a compact feature-progress grid from `SkillTracker.getFeaturesByCategory()`. Add a `buildTrend()` method and insert it into the content VBox (in the constructor's `new VBox(24, ... )`, add `buildTrend()` after `buildStats()`):

```java
    private javafx.scene.layout.Region buildTrend() {
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(8);
        box.getStyleClass().add("fxt-welcome-trend");

        Label title = new Label("ACTIVITY (LAST 7 DAYS)");
        title.getStyleClass().add("fxt-side-panel-title");

        java.util.List<Double> norm = WelcomeTrend.normalize(WelcomeTrend.dailyTotals(7));
        javafx.scene.shape.Polyline line = new javafx.scene.shape.Polyline();
        line.getStyleClass().add("fxt-welcome-sparkline");
        double w = 220, h = 40;
        if (norm.size() >= 2) {
            double step = w / (norm.size() - 1);
            for (int i = 0; i < norm.size(); i++) {
                line.getPoints().addAll(i * step, h - norm.get(i) * h);
            }
        }
        javafx.scene.layout.Pane spark = new javafx.scene.layout.Pane(line);
        spark.setMinSize(w, h);
        spark.setPrefSize(w, h);
        spark.setMaxSize(w, h);

        // Feature-progress grid: one labelled progress bar per category.
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        var stats = org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl.getInstance();
        int row = 0;
        try {
            var statsModel = stats.getStatisticsModel(); // see note below
            for (var cat : org.fxt.freexmltoolkit.service.SkillTracker.getFeaturesByCategory()) {
                double progress = org.fxt.freexmltoolkit.service.SkillTracker
                        .getCategoryProgress(cat.name(), statsModel);
                Label name = new Label(cat.name());
                javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(progress);
                bar.setPrefWidth(140);
                grid.add(name, 0, row);
                grid.add(bar, 1, row);
                row++;
            }
        } catch (Throwable ignored) {
            // no stats model available → omit the grid gracefully
        }

        box.getChildren().addAll(title, spark);
        if (row > 0) {
            box.getChildren().add(grid);
        }
        return box;
    }
```

> **Resolve the stats model accessor:** `SkillTracker.getCategoryProgress(String, UsageStatistics)` needs a `UsageStatistics` instance. Read `UsageTrackingServiceImpl` for the public accessor that returns the underlying `UsageStatistics` (it may be named `getStatistics()` / `getStatisticsModel()` / similar). Use the real method name; if no public accessor exists, add a minimal `public UsageStatistics getStatistics()` returning the internal `statistics` field (it's already used internally). If resolving this cleanly is not possible, omit the feature grid and keep only the sparkline (the `try/catch` already degrades gracefully) — the sparkline is the primary deliverable; note the omission in the commit message.

Add a test accessor:

```java
    /** @return the number of points in the rendered sparkline (for tests/observers). */
    public int getSparklinePointCount() {
        // walk children to find the .fxt-welcome-sparkline Polyline; return its points/2
        // (implement by storing the Polyline in a field when built, then return points size / 2)
        return sparklinePointCount;
    }
```

To support that, store the built polyline's point count in an `int sparklinePointCount` field set inside `buildTrend()` (`this.sparklinePointCount = line.getPoints().size() / 2;`).

- [ ] **Step 6: Add a render smoke test**

Append to `WelcomeTrendTest` is not possible (it's non-JavaFX). Instead create `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/WelcomePaneTrendTest.java`:

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class WelcomePaneTrendTest {

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    @Test
    void welcomePaneBuildsWithTrendSection() {
        EditorWelcomePane pane = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> new EditorWelcomePane(t -> { }, () -> { }, f -> { }, () -> { }, a -> { }));
        // building the pane (with the trend section) must not throw; point count is >= 0.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, pane::getSparklinePointCount) >= 0);
    }
}
```

> Use the EXACT `EditorWelcomePane` constructor signature (5 functional args: `Consumer<EditorFileType> onNew, Runnable onOpen, Consumer<File> onOpenRecent, Runnable onClearRecent, Consumer<String> onAction`) — read the file to confirm and match argument order.

- [ ] **Step 7: Run both tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.WelcomeTrendTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.WelcomePaneTrendTest" --tests "*EditorWelcomePaneTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/WelcomeTrend.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorWelcomePane.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/WelcomeTrendTest.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/WelcomePaneTrendTest.java
git commit -m "feat(shell): welcome dashboard 7-day trend sparkline + feature-progress grid

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Task 4: FundsXML activity (conditional)

A new conditional activity bundling the FundsXML suite. The heavy lifting already exists in `service/fundsxml/` (`FundsXmlExtensionService`, `FundsXmlCache`, `FundsXmlValidator`, `FundsXmlPropertyKeys`) and `FundsXmlActionRunner.isEnabled()`. We add a UI-free `FundsXmlRunner` (thin wrappers, especially the real download which the legacy menu only stubbed → it redirected to Settings), the `FundsXmlPanel`, the `Activity.FUNDSXML` enum entry, conditional registration in the activity bar, and side-panel wiring with a rebuild hook.

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/FundsXmlRunner.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/FundsXmlPanel.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/Activity.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/ActivityBar.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/FundsXmlRunnerTest.java`

### 4a. `FundsXmlRunner` (UI-free)

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FundsXmlRunnerTest {

    @Test
    void installedVersionsNeverNull() {
        List<String> versions = FundsXmlRunner.installedVersions();
        assertNotNull(versions, "versions list");
    }

    @Test
    void validateWithNoActiveSchemaReportsClearly() {
        // With no active schema / blank xml, validate returns a non-null human-readable summary
        // rather than throwing.
        String summary = FundsXmlRunner.validateSummary("<root/>");
        assertNotNull(summary);
        assertTrue(summary.length() > 0);
    }

    @Test
    void folderPathsResolve() {
        assertNotNull(FundsXmlRunner.examplesDir());
        assertNotNull(FundsXmlRunner.schemaDir());
        assertNotNull(FundsXmlRunner.schematronDir());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlRunnerTest"`
Expected: compile error — `FundsXmlRunner` does not exist.

- [ ] **Step 3: Implement `FundsXmlRunner`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlValidator;

/**
 * UI-free wrappers over the existing FundsXML services for the shell FundsXML activity.
 * Mirrors the logic the legacy MainController menu handlers used.
 */
public final class FundsXmlRunner {

    private FundsXmlRunner() {
    }

    public static boolean isEnabled() {
        return FundsXmlActionRunner.isEnabled();
    }

    public static List<String> installedVersions() {
        try {
            return FundsXmlExtensionService.getInstance().getInstalledVersions();
        } catch (Throwable t) {
            return List.of();
        }
    }

    public static String activeVersion() {
        try {
            return FundsXmlCache.getInstance().loadMetadata().getActiveSchemaVersion();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean setActiveVersion(String version) {
        try {
            return FundsXmlExtensionService.getInstance().setActiveVersion(version);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Validates {@code xml} against the active FundsXML schema; returns a human-readable summary. */
    public static String validateSummary(String xml) {
        try {
            FundsXmlValidator validator = new FundsXmlValidator(
                    FundsXmlCache.getInstance(), ServiceRegistry.get(XmlService.class));
            var outcome = validator.validate(xml);
            return switch (outcome.status()) {
                case NO_ACTIVE_SCHEMA -> "No active FundsXML schema — download content and pick a version.";
                case NO_XML_CONTENT -> "No XML document open.";
                case VALID -> "Valid against FundsXML schema "
                        + (outcome.schemaVersion() == null ? "" : outcome.schemaVersion()) + ".";
                case INVALID -> "Invalid: " + outcome.errors().size() + " issue(s) against schema "
                        + (outcome.schemaVersion() == null ? "" : outcome.schemaVersion()) + ".";
                case ERROR -> "Validation error: "
                        + (outcome.errorMessage() == null ? "unexpected error" : outcome.errorMessage());
            };
        } catch (Throwable t) {
            return "Validation error: " + t.getMessage();
        }
    }

    public static Path examplesDir() {
        return FundsXmlCache.getInstance().getExamplesDir();
    }

    public static Path schemaDir() {
        return FundsXmlCache.getInstance().getSchemaDir();
    }

    public static Path schematronDir() {
        return FundsXmlCache.getInstance().getSchematronDir();
    }

    public static Path activeSchemaFile() {
        return FundsXmlCache.getInstance().getActiveSchemaFile();
    }

    /** Generates schema documentation for the active schema into the cache's docs dir; returns the dir. */
    public static Path generateDocumentation() throws Exception {
        Path activeSchema = FundsXmlCache.getInstance().getActiveSchemaFile();
        if (activeSchema == null) {
            throw new IllegalStateException("No active FundsXML schema");
        }
        String version = FundsXmlCache.getInstance().loadMetadata().getActiveSchemaVersion();
        Path outputDir = FundsXmlCache.getInstance().getBaseDir()
                .resolve("docs").resolve(version == null ? "current" : version);
        java.nio.file.Files.createDirectories(outputDir);
        var docService = new org.fxt.freexmltoolkit.service.XsdDocumentationService();
        docService.setXsdFilePath(activeSchema.toString());
        docService.generateXsdDocumentation(outputDir.toFile());
        return outputDir;
    }
}
```

> Verify each called signature against the verbatim service report (`FundsXmlExtensionService.getInstalledVersions()/setActiveVersion(String)`, `FundsXmlCache.get*Dir()/getActiveSchemaFile()/getBaseDir()/loadMetadata().getActiveSchemaVersion()`, `FundsXmlValidator(cache, xmlService).validate(String)` with `ValidationOutcome.status()/schemaVersion()/errors()/errorMessage()`, `XsdDocumentationService.setXsdFilePath(String)/generateXsdDocumentation(File)`). The `switch` over `ValidationOutcome.Status` must cover all enum constants — if the enum has a different set, match it exactly.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlRunnerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/FundsXmlRunner.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/FundsXmlRunnerTest.java
git commit -m "feat(shell): UI-free FundsXmlRunner over the existing fundsxml services

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### 4b. `Activity.FUNDSXML` + conditional activity bar + side panel

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FundsXmlActivityTest {

    @Test
    void fundsXmlActivityExists() {
        // The enum constant must exist with the stable id "fundsxml".
        Activity a = Activity.valueOf("FUNDSXML");
        assertTrue(a.id().equals("fundsxml"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.FundsXmlActivityTest"`
Expected: FAIL — `No enum constant Activity.FUNDSXML`.

- [ ] **Step 3: Add the enum constant**

In `Activity.java`, add before `HELP`:

```java
    FUNDSXML("fundsxml", "FundsXML", "bi-file-earmark-xml"),
```

(Verify `bi-file-earmark-xml` exists in `bi.json`; if not, use `bi-file-earmark-code`.)

- [ ] **Step 4: Conditionally register it in `ActivityBar`**

In `ActivityBar.java`, the bar iterates `Activity.values()` and skips a `BOTTOM` set. Add a skip for FUNDSXML when disabled. Change the loop so it also skips `Activity.FUNDSXML` unless `FundsXmlRunner.isEnabled()`:

```java
        for (Activity a : Activity.values()) {
            if (BOTTOM.contains(a)) {
                continue;
            }
            if (a == Activity.FUNDSXML
                    && !org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlRunner.isEnabled()) {
                continue;
            }
            getChildren().add(createButton(a, group));
        }
```

Extract the loop body into a private `rebuild(ToggleGroup group)` method and call it from the constructor, AND add a public `refresh()` that clears `getChildren()` and calls `rebuild(...)` again, so the bar can be rebuilt when the FundsXML flag changes:

```java
    public void refresh() {
        getChildren().clear();
        rebuild(group);   // store the ToggleGroup as a field if not already
    }
```

(Read `ActivityBar.java` to see whether the `ToggleGroup` is a local or field; promote it to a field if needed so `refresh()` can reuse it. Keep the existing button-creation + selection behavior identical.)

- [ ] **Step 5: Wire the side panel in `UnifiedShellView.showSidePanelFor`**

In `UnifiedShellView.java`, add a branch in `showSidePanelFor(Activity activity)` (alongside the other `if (activity == Activity.X)` blocks):

```java
        if (activity == Activity.FUNDSXML) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlPanel(editorHost));
            return;
        }
```

- [ ] **Step 6: Run the activity test**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.FundsXmlActivityTest"`
Expected: PASS. (FundsXmlPanel does not exist yet — if compilation fails on the UnifiedShellView reference, do step 4c first; implement FundsXmlPanel in 4c before running. Reorder: implement 4c's FundsXmlPanel before compiling this step.)

### 4c. `FundsXmlPanel`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class FundsXmlPanelTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
    }

    @Test
    void buildsWithoutThrowing() {
        FundsXmlPanel panel = WaitForAsyncUtils.waitForAsyncFx(3000, () -> new FundsXmlPanel(host));
        assertNotNull(panel);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlPanelTest"`
Expected: compile error — `FundsXmlPanel` does not exist.

- [ ] **Step 3: Implement `FundsXmlPanel`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/** The FundsXML activity side panel: manage versions, validate the active document, docs/resources. */
public class FundsXmlPanel extends VBox {

    private final EditorHost editorHost;
    private final ComboBox<String> versionCombo = new ComboBox<>();
    private final Label status = new Label();

    public FundsXmlPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("FUNDSXML");
        title.getStyleClass().add("fxt-side-panel-title");
        status.getStyleClass().add("fxt-placeholder-text");
        status.setWrapText(true);

        // --- Management ---
        Label mgmt = sectionTitle("MANAGEMENT");
        versionCombo.getItems().setAll(FundsXmlRunner.installedVersions());
        String active = FundsXmlRunner.activeVersion();
        if (active != null) {
            versionCombo.getSelectionModel().select(active);
        }
        versionCombo.setOnAction(e -> {
            String v = versionCombo.getValue();
            if (v != null && FundsXmlRunner.setActiveVersion(v)) {
                status.setText("Active schema version: " + v);
            }
        });
        Button download = button("Download / Update Content", "bi-cloud-arrow-down", this::download);

        // --- Action ---
        Label action = sectionTitle("VALIDATE");
        Button validate = button("Validate active document", "bi-check2-circle", this::validate);

        // --- Docs & resources ---
        Label docs = sectionTitle("DOCS & RESOURCES");
        Button genDocs = button("Generate Schema Documentation", "bi-file-earmark-text", this::generateDocs);
        Button examples = button("Open Examples Folder", "bi-folder2-open",
                () -> openFolder(FundsXmlRunner.examplesDir()));
        Button schema = button("Open Schema Folder", "bi-folder2-open",
                () -> openFolder(FundsXmlRunner.schemaDir()));
        Button schematron = button("Open Schematron Folder", "bi-folder2-open",
                () -> openFolder(FundsXmlRunner.schematronDir()));
        Button online = button("Open Online Docs", "bi-globe",
                () -> openUrl("https://fundsxml.org/"));

        VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(title,
                mgmt, new Label("Active version"), versionCombo, SidePanelLayout.fill(download),
                action, SidePanelLayout.fill(validate),
                docs, SidePanelLayout.fill(genDocs), SidePanelLayout.fill(examples),
                SidePanelLayout.fill(schema), SidePanelLayout.fill(schematron),
                SidePanelLayout.fill(online),
                spacer, status);
    }

    private void download() {
        status.setText("Downloading…");
        FxtGui.executorService.submit(() -> {
            String msg;
            try {
                var result = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService.getInstance()
                        .downloadOrUpdate(progress -> { /* progress ignored in the side panel */ });
                msg = "Download complete.";
            } catch (Throwable t) {
                msg = "Download failed: " + t.getMessage();
            }
            String finalMsg = msg;
            Platform.runLater(() -> {
                status.setText(finalMsg);
                versionCombo.getItems().setAll(FundsXmlRunner.installedVersions());
                String active = FundsXmlRunner.activeVersion();
                if (active != null) {
                    versionCombo.getSelectionModel().select(active);
                }
            });
        });
    }

    private void validate() {
        String xml = editorHost.getActiveText().orElse(null);
        status.setText("Validating…");
        FxtGui.executorService.submit(() -> {
            String summary = FundsXmlRunner.validateSummary(xml);
            Platform.runLater(() -> status.setText(summary));
        });
    }

    private void generateDocs() {
        status.setText("Generating documentation…");
        FxtGui.executorService.submit(() -> {
            String msg;
            try {
                var dir = FundsXmlRunner.generateDocumentation();
                msg = "Documentation written to: " + dir;
            } catch (Throwable t) {
                msg = "Documentation failed: " + t.getMessage();
            }
            String finalMsg = msg;
            Platform.runLater(() -> status.setText(finalMsg));
        });
    }

    private void openFolder(java.nio.file.Path dir) {
        try {
            if (dir != null && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception e) {
            status.setText("Could not open folder: " + e.getMessage());
        }
    }

    private void openUrl(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception e) {
            status.setText("Could not open browser: " + e.getMessage());
        }
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("fxt-side-panel-title");
        return l;
    }

    private Button button(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }
}
```

> Verify `FundsXmlExtensionService.downloadOrUpdate(...)`'s exact callback parameter type from the service report (it takes a `DownloadProgressCallback`); pass a lambda or no-op implementation matching that functional interface. Verify `SidePanelLayout.fill(Button)` exists (it's used by TransformPanel/ValidationPanel); if the helper signature differs, follow its actual usage. Verify all `bi-*` literals exist in `bi.json` (`bi-cloud-arrow-down`, `bi-check2-circle`, `bi-file-earmark-text`, `bi-folder2-open`, `bi-globe` are all already used in the shell).

- [ ] **Step 4: Run the panel + activity tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlPanelTest" --tests "org.fxt.freexmltoolkit.controls.shell.FundsXmlActivityTest"`
Expected: PASS.

- [ ] **Step 5: Rebuild the activity bar when the FundsXML flag changes**

When `SettingsPanel.saveSettings()` toggles the FundsXML enable flag, the activity bar should add/remove the FundsXML button. Find where the shell reacts to settings changes (the shell already rebuilds for theme — search `UnifiedShellView` for how `SettingsPanel` save is observed, e.g. an `onSettingsSaved`/refresh callback). Wire `activityBar.refresh()` into that path. If no such hook exists, add a minimal one: give `SettingsPanel` a `Runnable onSaved` callback invoked at the end of `saveSettings()`, set by `UnifiedShellView` when it constructs the `SettingsPanel` in `showSidePanelFor`, calling `activityBar.refresh()`.

> Note: the FundsXML enable toggle itself is NOT part of `SettingsPanel` yet (the spec keeps only the enable toggle in Settings). If adding the enable toggle is needed for the flag to be user-controllable from the shell, add a single `CheckBox` "Enable FundsXML extensions" to `SettingsPanel` bound to `FundsXmlPropertyKeys.ENABLED` via `props.get/set`, and call `onSaved` after save. Keep it minimal — this is the only FundsXML control in Settings; all management lives in the FundsXML activity.

- [ ] **Step 6: Run the shell integration smoke test**

Run: `./gradlew test --tests "*UnifiedShellViewTest" --tests "*IconifyIconCoverageTest"`
Expected: PASS (mind: if `UnifiedShellViewTest` asserts a fixed activity count, update it to account for the conditional FundsXML activity — it should only appear when enabled).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/Activity.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/ActivityBar.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java \
        src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/FundsXmlPanel.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/FundsXmlActivityTest.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/FundsXmlPanelTest.java
git commit -m "feat(shell): conditional FundsXML activity (manage/validate/docs) + panel

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push
```

---

## Self-review checklist (run before execution)

- All four spec packages map to tasks: Help dialogs (T1), Settings extras (T2), Welcome stats (T3), FundsXML activity (T4). ✓
- Reused services match the verbatim signatures gathered (VersionUtil, UpdateCheckService, PropertiesService get/set + user.*/ssl.trustAllCerts keys, UsageTrackingServiceImpl.getInstance(), SkillTracker, FundsXml* services, EditorHost.getActiveText/openToolTab). ✓
- The two genuinely unresolved-at-write-time accessors are flagged with explicit fallbacks: the `UsageStatistics` accessor for the feature grid (T3 step 5 — omit grid + keep sparkline if absent) and `DialogHelper.createHelpDialog` return type (T1 step 4 — adjust return type). These are real gaps the implementer resolves by reading the cited file, with a safe degraded path.

## Out of scope (sub-projects B and C)

- B: make the shell the root scene; relocate global concerns (drag-drop, keyboard shortcuts, update-check scheduling, theme, recent-files persistence); strip `main.fxml`/`MainController`.
- C: delete the legacy chrome (MenuBar/sidebar/footer/welcome/settings + WelcomeController/SettingsController).
- This plan leaves the legacy chrome fully intact; it only makes the shell feature-complete in parallel.
