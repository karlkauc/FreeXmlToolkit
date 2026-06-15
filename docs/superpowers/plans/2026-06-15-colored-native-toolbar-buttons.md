# Colored Native Toolbar Buttons — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the editor toolbar buttons (New, Open, Save, …) a native, raised, semantically-colored look, with a Settings toggle for showing labels and choosing icon size (small/large).

**Architecture:** Two new persisted settings (`toolbar.show.labels`, `toolbar.icon.size`) in `PropertiesService`. A pure helper `ToolbarDisplay` maps the size setting to pixel size + CSS size-class (unit-testable, no JavaFX). `UnifiedShellView` gets a `ToolColor` enum and a refactored `toolButton(...)` core that tags each button with a color class + stored label, registers it, and applies the current display settings; a `setOnSaved` hook re-applies settings live. All visuals come from new `-fxt-tool-*` design tokens (light+dark) and reworked `.fxt-tool-button` CSS variants.

**Tech Stack:** Java 25 + JavaFX 24, JavaFX CSS (`-fxt-*` looked-up colors), JUnit 5. Build: `./gradlew`.

**Spec:** `docs/superpowers/specs/2026-06-15-colored-native-toolbar-buttons-design.md`

**Color assignment (reference for Task 4):**

| Button(s) | ToolColor |
|-----------|-----------|
| New | SUCCESS |
| Open, Save, Save As, Save All, Type Editor, Validate | PRIMARY |
| Format, Minify, Insert Template, Compare, Spreadsheet, Query Console, Transform, Generate Docs | INFO |
| Set Schema | WARNING |
| Undo, Redo, panel toggles | NEUTRAL |
| (reserved, no current button) | DANGER |

---

## Task 1: Persisted settings in PropertiesService

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/PropertiesService.java` (interface, after the `setUseSmallIcons` declaration around line 300)
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/PropertiesServiceImpl.java` (defaults block ~line 118; method block ~line 405 next to `isUseSmallIcons`)
- Test: `src/test/java/org/fxt/freexmltoolkit/service/PropertiesServiceToolbarDisplayTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/fxt/freexmltoolkit/service/PropertiesServiceToolbarDisplayTest.java`:

```java
package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the toolbar display settings. The getters normalize the
 * stored value, so an unknown icon-size value falls back to "small".
 */
class PropertiesServiceToolbarDisplayTest {

    @Test
    @DisplayName("toolbar.icon.size round-trips and falls back to 'small' for unknown values")
    void iconSizeRoundTripAndFallback() {
        PropertiesService p = PropertiesServiceImpl.getInstance();
        String original = p.getToolbarIconSize();
        try {
            p.setToolbarIconSize("large");
            assertEquals("large", p.getToolbarIconSize());
            p.setToolbarIconSize("nonsense");
            assertEquals("small", p.getToolbarIconSize());
        } finally {
            p.setToolbarIconSize(original);
        }
    }

    @Test
    @DisplayName("toolbar.show.labels round-trips as a boolean")
    void showLabelsRoundTrip() {
        PropertiesService p = PropertiesServiceImpl.getInstance();
        boolean original = p.isToolbarShowLabels();
        try {
            p.setToolbarShowLabels(true);
            assertTrue(p.isToolbarShowLabels());
            p.setToolbarShowLabels(false);
            assertFalse(p.isToolbarShowLabels());
        } finally {
            p.setToolbarShowLabels(original);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.PropertiesServiceToolbarDisplayTest"`
Expected: COMPILE FAILURE — `getToolbarIconSize()` / `isToolbarShowLabels()` are undefined.

- [ ] **Step 3: Add the interface methods**

In `PropertiesService.java`, immediately after the existing `void setUseSmallIcons(boolean useSmallIcons);` (around line 300), add:

```java
    /**
     * Whether the editor-toolbar buttons show a text label next to their icon.
     *
     * @return true to show labels, false for icon-only (default: false)
     */
    boolean isToolbarShowLabels();

    /**
     * Sets whether the editor-toolbar buttons show a text label next to their icon.
     *
     * @param showLabels true to show labels, false for icon-only
     */
    void setToolbarShowLabels(boolean showLabels);

    /**
     * The editor-toolbar icon size.
     *
     * @return {@code "small"} or {@code "large"} (default and fallback: {@code "small"})
     */
    String getToolbarIconSize();

    /**
     * Sets the editor-toolbar icon size. Values other than {@code "large"}
     * (case-insensitive) are stored as given but read back as {@code "small"}.
     *
     * @param size {@code "small"} or {@code "large"}
     */
    void setToolbarIconSize(String size);
```

- [ ] **Step 4: Add defaults and implementations**

In `PropertiesServiceImpl.java`, in the defaults block right after the `ui.use.small.icons` line (~line 118), add:

```java
        properties.setProperty("toolbar.show.labels", "false"); // editor toolbar: icon-only by default
        properties.setProperty("toolbar.icon.size", "small");   // editor toolbar: small icons by default
```

In `PropertiesServiceImpl.java`, right after `setUseSmallIcons(...)` (~line 414), add:

```java
    @Override
    public boolean isToolbarShowLabels() {
        return Boolean.parseBoolean(properties.getProperty("toolbar.show.labels", "false"));
    }

    @Override
    public void setToolbarShowLabels(boolean showLabels) {
        properties.setProperty("toolbar.show.labels", String.valueOf(showLabels));
        saveProperties(properties);
        logger.debug("Set toolbar show labels to: {}", showLabels);
    }

    @Override
    public String getToolbarIconSize() {
        String raw = properties.getProperty("toolbar.icon.size", "small");
        return "large".equalsIgnoreCase(raw) ? "large" : "small";
    }

    @Override
    public void setToolbarIconSize(String size) {
        properties.setProperty("toolbar.icon.size", size == null ? "small" : size);
        saveProperties(properties);
        logger.debug("Set toolbar icon size to: {}", size);
    }
```

> Note: confirm the persist helper is named `saveProperties(properties)` by checking how `setUseSmallIcons` persists (line ~411). Use the identical call.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.PropertiesServiceToolbarDisplayTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/PropertiesService.java \
        src/main/java/org/fxt/freexmltoolkit/service/PropertiesServiceImpl.java \
        src/test/java/org/fxt/freexmltoolkit/service/PropertiesServiceToolbarDisplayTest.java
git commit -m "feat(settings): persist toolbar label/icon-size display settings"
```

---

## Task 2: ToolbarDisplay pure helper (size mapping)

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/ToolbarDisplay.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/ToolbarDisplayTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/fxt/freexmltoolkit/controls/shell/ToolbarDisplayTest.java`:

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ToolbarDisplayTest {

    @Test
    void iconSizePxMapsSmallAndLarge() {
        assertEquals(16, ToolbarDisplay.iconSizePx(false));
        assertEquals(22, ToolbarDisplay.iconSizePx(true));
    }

    @Test
    void sizeStyleClassMapsSmallAndLarge() {
        assertEquals("fxt-tool-small", ToolbarDisplay.sizeStyleClass(false));
        assertEquals("fxt-tool-large", ToolbarDisplay.sizeStyleClass(true));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ToolbarDisplayTest"`
Expected: COMPILE FAILURE — `ToolbarDisplay` does not exist.

- [ ] **Step 3: Create the helper**

Create `src/main/java/org/fxt/freexmltoolkit/controls/shell/ToolbarDisplay.java`:

```java
package org.fxt.freexmltoolkit.controls.shell;

/**
 * Pure mapping helpers for the editor toolbar's display settings (icon size).
 * Kept free of JavaFX so the size logic is unit-testable.
 */
public final class ToolbarDisplay {

    /** Icon edge length in pixels for the small / large toolbar modes. */
    public static final int SMALL_PX = 16;
    public static final int LARGE_PX = 22;

    /** CSS marker classes toggled on each toolbar button per size mode. */
    public static final String SMALL_CLASS = "fxt-tool-small";
    public static final String LARGE_CLASS = "fxt-tool-large";

    private ToolbarDisplay() {
    }

    /** @return the icon pixel size for the given mode. */
    public static int iconSizePx(boolean large) {
        return large ? LARGE_PX : SMALL_PX;
    }

    /** @return the CSS size class for the given mode. */
    public static String sizeStyleClass(boolean large) {
        return large ? LARGE_CLASS : SMALL_CLASS;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ToolbarDisplayTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/ToolbarDisplay.java \
        src/test/java/org/fxt/freexmltoolkit/controls/shell/ToolbarDisplayTest.java
git commit -m "feat(toolbar): add ToolbarDisplay size-mapping helper"
```

---

## Task 3: Design tokens for tool-button colors (light + dark)

**Files:**
- Modify: `src/main/resources/css/design-tokens.css` (light `.root` block ends ~line 56; dark `.root.fxt-theme-dark` block ends ~line 95)

- [ ] **Step 1: Add light-theme tokens**

In `design-tokens.css`, inside the light `.root { … }` block, just before its closing `}` (after the `-fxt-code-*` lines, ~line 55), add:

```css

    /* Semantic — editor toolbar buttons (soft-tinted native look) */
    -fxt-tool-primary-bg: #eef2ff;  -fxt-tool-primary-border: #c3cef6;  -fxt-tool-primary-fg: #3b5bdb;
    -fxt-tool-success-bg: #eaf6ee;  -fxt-tool-success-border: #bfe3cb;  -fxt-tool-success-fg: #2f9e44;
    -fxt-tool-info-bg:    #e7f6f8;  -fxt-tool-info-border:    #b9e3e9;  -fxt-tool-info-fg:    #1098ad;
    -fxt-tool-warning-bg: #fff6e6;  -fxt-tool-warning-border: #ffe0a3;  -fxt-tool-warning-fg: #b8860b;
    -fxt-tool-danger-bg:  #fdeaea;  -fxt-tool-danger-border:  #f5c2c2;  -fxt-tool-danger-fg:  #e03131;
    -fxt-tool-neutral-bg: #f2f4f8;  -fxt-tool-neutral-border: #dde1e7;  -fxt-tool-neutral-fg: #5a6472;
```

- [ ] **Step 2: Add dark-theme tokens**

In `design-tokens.css`, inside the `.root.fxt-theme-dark { … }` block, just before its closing `}` (after the dark `-fxt-code-*` lines, ~line 94), add:

```css

    /* Semantic — editor toolbar buttons (soft-tinted native look) */
    -fxt-tool-primary-bg: #1d2748;  -fxt-tool-primary-border: #34406e;  -fxt-tool-primary-fg: #93a6fb;
    -fxt-tool-success-bg: #14301e;  -fxt-tool-success-border: #2b5a3a;  -fxt-tool-success-fg: #69db7c;
    -fxt-tool-info-bg:    #0e2a30;  -fxt-tool-info-border:    #1f4a53;  -fxt-tool-info-fg:    #4dd4e8;
    -fxt-tool-warning-bg: #2e2410;  -fxt-tool-warning-border: #5a4a1f;  -fxt-tool-warning-fg: #fab005;
    -fxt-tool-danger-bg:  #2e1414;  -fxt-tool-danger-border:  #5a2b2b;  -fxt-tool-danger-fg:  #ff8787;
    -fxt-tool-neutral-bg: #1c232c;  -fxt-tool-neutral-border: #2a323d;  -fxt-tool-neutral-fg: #9ba6b3;
```

- [ ] **Step 3: Verify build still compiles resources**

Run: `./gradlew classes`
Expected: BUILD SUCCESSFUL (CSS is a resource; this just confirms nothing else broke).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/css/design-tokens.css
git commit -m "feat(theme): add -fxt-tool-* color tokens for toolbar buttons (light+dark)"
```

---

## Task 4: Rework toolbar button CSS (native look + color/size variants)

**Files:**
- Modify: `src/main/resources/css/unified-shell.css` (`.fxt-tool-button` block lines 314–331; `.fxt-validate-button` block lines 989–1006)

- [ ] **Step 1: Replace the `.fxt-tool-button` block**

In `unified-shell.css`, replace the existing block (lines 314–331, from `.fxt-tool-button {` through the `.fxt-tool-button:hover .iconify-icon { … }` rule) with:

```css
.fxt-tool-button {
    -fx-background-color: -fxt-tool-neutral-bg;
    -fx-border-color: -fxt-tool-neutral-border;
    -fx-border-width: 1;
    -fx-border-radius: 6;
    -fx-background-radius: 6;
    -fx-padding: 4 8 4 8;
    -fx-graphic-text-gap: 6;
    -fx-font-family: "Inter", sans-serif;
    -fx-text-fill: -fxt-tool-neutral-fg;
    -fx-cursor: hand;
}

.fxt-tool-button .iconify-icon {
    -fx-icon-color: -fxt-tool-neutral-fg;
}

.fxt-tool-button:hover {
    -fx-background-color: derive(-fxt-tool-neutral-bg, -6%);
}

.fxt-tool-button:disabled {
    -fx-opacity: 0.45;
}

/* ----- Color variants (semantic, soft-tinted) ----- */
.fxt-tool-button.fxt-tool-primary {
    -fx-background-color: -fxt-tool-primary-bg;
    -fx-border-color: -fxt-tool-primary-border;
    -fx-text-fill: -fxt-tool-primary-fg;
}
.fxt-tool-button.fxt-tool-primary .iconify-icon { -fx-icon-color: -fxt-tool-primary-fg; }
.fxt-tool-button.fxt-tool-primary:hover { -fx-background-color: derive(-fxt-tool-primary-bg, -8%); }

.fxt-tool-button.fxt-tool-success {
    -fx-background-color: -fxt-tool-success-bg;
    -fx-border-color: -fxt-tool-success-border;
    -fx-text-fill: -fxt-tool-success-fg;
}
.fxt-tool-button.fxt-tool-success .iconify-icon { -fx-icon-color: -fxt-tool-success-fg; }
.fxt-tool-button.fxt-tool-success:hover { -fx-background-color: derive(-fxt-tool-success-bg, -8%); }

.fxt-tool-button.fxt-tool-info {
    -fx-background-color: -fxt-tool-info-bg;
    -fx-border-color: -fxt-tool-info-border;
    -fx-text-fill: -fxt-tool-info-fg;
}
.fxt-tool-button.fxt-tool-info .iconify-icon { -fx-icon-color: -fxt-tool-info-fg; }
.fxt-tool-button.fxt-tool-info:hover { -fx-background-color: derive(-fxt-tool-info-bg, -8%); }

.fxt-tool-button.fxt-tool-warning {
    -fx-background-color: -fxt-tool-warning-bg;
    -fx-border-color: -fxt-tool-warning-border;
    -fx-text-fill: -fxt-tool-warning-fg;
}
.fxt-tool-button.fxt-tool-warning .iconify-icon { -fx-icon-color: -fxt-tool-warning-fg; }
.fxt-tool-button.fxt-tool-warning:hover { -fx-background-color: derive(-fxt-tool-warning-bg, -8%); }

.fxt-tool-button.fxt-tool-danger {
    -fx-background-color: -fxt-tool-danger-bg;
    -fx-border-color: -fxt-tool-danger-border;
    -fx-text-fill: -fxt-tool-danger-fg;
}
.fxt-tool-button.fxt-tool-danger .iconify-icon { -fx-icon-color: -fxt-tool-danger-fg; }
.fxt-tool-button.fxt-tool-danger:hover { -fx-background-color: derive(-fxt-tool-danger-bg, -8%); }

.fxt-tool-button.fxt-tool-neutral {
    -fx-background-color: -fxt-tool-neutral-bg;
    -fx-border-color: -fxt-tool-neutral-border;
    -fx-text-fill: -fxt-tool-neutral-fg;
}
.fxt-tool-button.fxt-tool-neutral .iconify-icon { -fx-icon-color: -fxt-tool-neutral-fg; }
.fxt-tool-button.fxt-tool-neutral:hover { -fx-background-color: derive(-fxt-tool-neutral-bg, -6%); }

/* ----- Size variants ----- */
.fxt-tool-button.fxt-tool-small { -fx-padding: 4 8 4 8; -fx-font-size: 11px; }
.fxt-tool-button.fxt-tool-large { -fx-padding: 6 12 6 12; -fx-font-size: 13px; }
```

- [ ] **Step 2: Retire the special validate styling**

In `unified-shell.css`, replace the `.fxt-validate-button` block (lines 989–1006, the comment `/* ----- Editor toolbar: primary Validate button (Figma) ----- */` through the `.fxt-validate-button:hover { … }` rule) with just the comment (the button now uses the shared `fxt-tool-primary` variant):

```css
/* ----- Editor toolbar: Validate now uses the shared .fxt-tool-primary variant ----- */
```

- [ ] **Step 3: Verify build**

Run: `./gradlew classes`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/css/unified-shell.css
git commit -m "feat(toolbar): native colored button CSS with color + size variants"
```

---

## Task 5: Wire colors, labels, size + live refresh in UnifiedShellView

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java`
  - `buildEditorToolbar()` lines 589–673
  - `openSettingsTab()` `setOnSaved` block lines 531–534
  - `toolButton(...)` overloads lines 858–874
  - `panelToggle(...)` lines 886–905
  - `documentActionButton(...)` lines 908–913
  - add fields + enum + helpers near the toolbar code

This task is JavaFX UI wiring (needs an initialized FX toolkit to instantiate, so it is verified by a compile + a manual run rather than a unit test; the size mapping it depends on is already covered by `ToolbarDisplayTest`).

- [ ] **Step 1: Add the `ToolColor` enum, the button registry field, and the label-key constant**

In `UnifiedShellView.java`, add near the other private fields (e.g. just below the field declared around line 78 `/** Type-gated document-action toolbar buttons … */`):

```java
    /** Editor-toolbar buttons (incl. panel toggles) registered for live display updates. */
    private final java.util.List<javafx.scene.control.ButtonBase> toolbarButtons = new java.util.ArrayList<>();

    /** Node-properties key under which each toolbar button stores its short text label. */
    private static final String TOOL_LABEL_KEY = "fxt.toolLabel";

    /** Semantic color categories for editor-toolbar buttons (maps to CSS variant classes). */
    private enum ToolColor {
        PRIMARY("fxt-tool-primary"),
        SUCCESS("fxt-tool-success"),
        INFO("fxt-tool-info"),
        WARNING("fxt-tool-warning"),
        DANGER("fxt-tool-danger"),
        NEUTRAL("fxt-tool-neutral");

        final String styleClass;

        ToolColor(String styleClass) {
            this.styleClass = styleClass;
        }
    }
```

- [ ] **Step 2: Replace the two `toolButton(...)` overloads with the new color-aware core**

In `UnifiedShellView.java`, replace lines 858–874 (both old `toolButton` methods) with:

```java
    /**
     * Builds an editor-toolbar button: stores its short {@code label}, applies its
     * semantic {@code color} variant, registers it for live display updates, and
     * applies the current label/size settings.
     */
    private javafx.scene.control.Button toolButton(String id, String icon, String label,
            String tooltip, ToolColor color, Runnable action) {
        javafx.scene.control.Button button = new javafx.scene.control.Button();
        if (id != null) {
            button.setId(id);
        }
        button.getStyleClass().addAll("fxt-tool-button", color.styleClass);
        button.setGraphic(new IconifyIcon(icon));
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        registerToolButton(button, label);
        return button;
    }

    /** Registers a toolbar button (any {@link javafx.scene.control.ButtonBase}) for display updates. */
    private void registerToolButton(javafx.scene.control.ButtonBase button, String label) {
        button.getProperties().put(TOOL_LABEL_KEY, label == null ? "" : label);
        toolbarButtons.add(button);
        applyDisplayTo(button);
    }

    /** Applies the current label-visibility + icon-size settings to one toolbar button. */
    private void applyDisplayTo(javafx.scene.control.ButtonBase button) {
        boolean showLabels = false;
        boolean large = false;
        try {
            var props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(
                    org.fxt.freexmltoolkit.service.PropertiesService.class);
            showLabels = props.isToolbarShowLabels();
            large = "large".equalsIgnoreCase(props.getToolbarIconSize());
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests) — fall back to defaults
        }
        Object stored = button.getProperties().get(TOOL_LABEL_KEY);
        String label = stored == null ? "" : stored.toString();
        button.setText(showLabels && !label.isEmpty() ? label : null);
        if (button.getGraphic() instanceof IconifyIcon icon) {
            icon.setIconSize(ToolbarDisplay.iconSizePx(large));
        }
        button.getStyleClass().removeAll(ToolbarDisplay.SMALL_CLASS, ToolbarDisplay.LARGE_CLASS);
        button.getStyleClass().add(ToolbarDisplay.sizeStyleClass(large));
    }

    /** Re-applies the current toolbar display settings to every registered button (live refresh). */
    private void applyToolbarDisplaySettings() {
        for (javafx.scene.control.ButtonBase button : toolbarButtons) {
            applyDisplayTo(button);
        }
    }
```

> `ToolbarDisplay` lives in the same package (`org.fxt.freexmltoolkit.controls.shell`), so no import is needed. `IconifyIcon` is already imported in this file (used by the existing toolbar code).

- [ ] **Step 3: Update `documentActionButton(...)` to take a color + label**

In `UnifiedShellView.java`, replace the `documentActionButton(...)` method (lines 908–913) with:

```java
    /** Builds a type-gated document-action toolbar button (delegates to {@link #toolButton}). */
    private javafx.scene.control.Button documentActionButton(String id, String icon, String label,
            String tooltip, ToolColor color, Runnable action) {
        return toolButton(id, icon, label, tooltip, color, action);
    }
```

- [ ] **Step 4: Register the panel toggles and give them neutral color**

In `UnifiedShellView.java`, in `panelToggle(...)` (lines 886–905), make two changes:

Change the style-class line (currently `button.getStyleClass().addAll("fxt-tool-button", "fxt-panel-toggle");`) to:

```java
        button.getStyleClass().addAll("fxt-tool-button", "fxt-tool-neutral", "fxt-panel-toggle");
```

Remove the now-redundant fixed icon size + graphic wiring lines:

```java
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        button.setGraphic(graphic);
```

replace them with:

```java
        button.setGraphic(new IconifyIcon(icon));
```

And just before `return button;`, register the toggle (empty label → stays icon-only, but size still scales):

```java
        registerToolButton(button, "");
```

- [ ] **Step 5: Rebuild the Validate button via the core and clear the registry at the top of `buildEditorToolbar()`**

In `UnifiedShellView.java`, replace the Validate construction (lines 590–598, from `IconifyIcon validateIcon = …` through `actionValidate = validate;`) with:

```java
        toolbarButtons.clear();
        javafx.scene.control.Button validate = toolButton("doc-action-validate", "bi-check2-circle",
                "Validate",
                "Validate the document (well-formedness, or against the bound XSD) — F8",
                ToolColor.PRIMARY, this::validateActive);
        actionValidate = validate;
```

> Behavior note: Validate now follows the global label setting (icon-only when labels are off, which is the default) instead of always showing its text. It keeps a prominent PRIMARY color.

- [ ] **Step 6: Update the three document-action calls (lines 602–610)**

Replace lines 602–610 with:

```java
        actionTransform = documentActionButton("doc-action-transform", "bi-arrow-left-right", "Transform",
                "Transform with XSLT… (choose a stylesheet)",
                ToolColor.INFO, () -> editorActions.transformActiveWithXslt(window()));
        actionGenerateDocs = documentActionButton("doc-action-generate-docs", "bi-file-earmark-text", "Docs",
                "Generate Documentation… (HTML / PDF / Word) for the active XSD",
                ToolColor.INFO, () -> editorActions.generateDocsActive(window()));
        actionTypeEditor = documentActionButton("doc-action-type-editor", "bi-braces-asterisk", "Type Editor",
                "Open Type Editor… (pick a named type from the active XSD)",
                ToolColor.PRIMARY, editorActions::openTypeEditorActive);
```

- [ ] **Step 7: Update the FlowPane `toolButton(...)` calls (lines 621–641)**

Replace the toolButton calls inside the `FlowPane actions = …` constructor (lines 621–641) with these (note: each now passes a short label + a `ToolColor`):

```java
                toolButton("action-new", "bi-file-earmark-plus", "New", "New (Ctrl+N)",
                        ToolColor.SUCCESS, this::newDocument),
                toolButton("action-open", "bi-folder2-open", "Open", "Open (Ctrl+O)",
                        ToolColor.PRIMARY, this::openFile),
                toolButton("action-save", "bi-save", "Save", "Save (Ctrl+S)",
                        ToolColor.PRIMARY, this::saveActive),
                toolButton("action-save-as", "bi-save2", "Save As", "Save As (Ctrl+Shift+S)",
                        ToolColor.PRIMARY, this::saveActiveAs),
                toolButton("action-save-all", "bi-files", "Save All", "Save All",
                        ToolColor.PRIMARY, editorHost::saveAll),
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                toolButton("action-undo", "bi-arrow-counterclockwise", "Undo", "Undo (Ctrl+Z)",
                        ToolColor.NEUTRAL, editorHost::undoActive),
                toolButton("action-redo", "bi-arrow-clockwise", "Redo", "Redo (Ctrl+Y)",
                        ToolColor.NEUTRAL, editorHost::redoActive),
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                toolButton("action-format", "bi-text-indent-left", "Format", "Format",
                        ToolColor.INFO, editorHost::formatActive),
                toolButton("action-minify", "bi-arrows-collapse", "Minify", "Minify",
                        ToolColor.INFO, editorHost::minifyActive),
                toolButton("action-insert-template", "bi-puzzle", "Template", "Insert Template…",
                        ToolColor.INFO, this::insertTemplate),
                toolButton("action-compare", "bi-layout-split", "Compare", "Compare with File…",
                        ToolColor.INFO, this::compareWithFile),
                toolButton("action-spreadsheet", "bi-table", "Spreadsheet",
                        "Spreadsheet Converter… (Excel / CSV ↔ XML)", ToolColor.INFO, this::convertSpreadsheet),
                toolButton("action-query-console", "bi-terminal", "Query",
                        "Query Console (XPath/XQuery)  Ctrl+Shift+X", ToolColor.INFO, this::toggleQueryConsole),
                actionTransform, actionGenerateDocs, actionTypeEditor,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                toolButton("action-set-schema", "bi-diagram-3", "Set Schema",
                        "Set XSD Schema… (IntelliSense & validation)", ToolColor.WARNING, this::setSchema));
```

> Keep the surrounding `validate, new Separator(...)` first two arguments of the FlowPane exactly as they are (line 619–620).

- [ ] **Step 8: Wire the live refresh into `setOnSaved`**

In `UnifiedShellView.java`, in `openSettingsTab()` (lines 531–534), change the callback to:

```java
        settings.setOnSaved(() -> {
            activityBar.refresh();
            reloadPanelPrefs();
            applyToolbarDisplaySettings();
        });
```

- [ ] **Step 9: Compile**

Run: `./gradlew classes`
Expected: BUILD SUCCESSFUL. If the compiler reports an unused `validateIcon` or a missing method, re-check Steps 2/5 (the old overloads and the inline validate icon must be fully removed).

- [ ] **Step 10: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java
git commit -m "feat(toolbar): apply semantic colors, label/size settings and live refresh"
```

---

## Task 6: Settings UI for label toggle + icon size

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanel.java`
  - fields ~lines 63–67
  - constructor toggle wiring ~lines 98–104
  - GENERAL card ~lines 180–181
  - `loadSettings()` ~line 339
  - `saveSettings()` ~line 384

- [ ] **Step 1: Add the controls as fields**

In `SettingsPanel.java`, in the `// General` field group (after line 65 `private final CheckBox smallIcons = …`), add:

```java
    private final CheckBox toolbarLabels = new CheckBox("Show toolbar button labels");
    private final ToggleButton toolbarIconSmall = new ToggleButton("Small");
    private final ToggleButton toolbarIconLarge = new ToggleButton("Large");
```

> `ToggleButton` is already imported (line 13).

- [ ] **Step 2: Group the size toggles in the constructor**

In `SettingsPanel.java`, in the constructor right after the theme-group wiring (after line 104 `dark.setOnAction(e -> applyTheme(true));`), add:

```java
        ToggleGroup toolbarIconSizeGroup = new ToggleGroup();
        toolbarIconSmall.setToggleGroup(toolbarIconSizeGroup);
        toolbarIconLarge.setToggleGroup(toolbarIconSizeGroup);
```

- [ ] **Step 3: Add the controls to the GENERAL card**

In `SettingsPanel.java`, replace the GENERAL card (lines 180–181):

```java
                card("GENERAL", "bi-sliders", "#007bff",
                        updateCheck, smallIcons, showLeftPanel, showInspector),
```

with:

```java
                card("GENERAL", "bi-sliders", "#007bff",
                        updateCheck, smallIcons, toolbarLabels,
                        labeled("Toolbar icons:", new HBox(6, toolbarIconSmall, toolbarIconLarge)),
                        showLeftPanel, showInspector),
```

> `HBox` and the `labeled(...)` helper are already used in this file.

- [ ] **Step 4: Load the values**

In `SettingsPanel.java`, in `loadSettings()` right after line 339 (`smallIcons.setSelected(props.isUseSmallIcons());`), add:

```java
            toolbarLabels.setSelected(props.isToolbarShowLabels());
            boolean toolbarLarge = "large".equalsIgnoreCase(props.getToolbarIconSize());
            (toolbarLarge ? toolbarIconLarge : toolbarIconSmall).setSelected(true);
```

- [ ] **Step 5: Save the values**

In `SettingsPanel.java`, in `saveSettings()` right after line 384 (`props.setUseSmallIcons(smallIcons.isSelected());`), add:

```java
            props.setToolbarShowLabels(toolbarLabels.isSelected());
            props.setToolbarIconSize(toolbarIconLarge.isSelected() ? "large" : "small");
```

- [ ] **Step 6: Compile**

Run: `./gradlew classes`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanel.java
git commit -m "feat(settings): UI for toolbar labels and icon size"
```

---

## Task 7: Full verification

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — includes the new `PropertiesServiceToolbarDisplayTest`, `ToolbarDisplayTest`, and the existing `IconifyIconCoverageTest` (no new `bi-*` literals were introduced, so it stays green).

- [ ] **Step 2: Manual visual check**

Run: `./gradlew run` (or `xvfb-run ./gradlew run` headless). Verify:
- Toolbar buttons now have a soft-tinted, bordered, native look with semantic colors (New=green, Open/Save=blue, Format/Transform=cyan, Set Schema=yellow, Undo/Redo=grey).
- Open Settings → GENERAL: toggle "Show toolbar button labels" and switch "Toolbar icons" Small/Large, Save → toolbar updates **immediately** (labels appear/disappear, icons resize) without restart.
- Switch theme Light/Dark → button colors adapt.

- [ ] **Step 3: Final commit (if any cleanup was needed)**

```bash
git add -A
git commit -m "chore(toolbar): colored native toolbar buttons — verification pass" || echo "nothing to commit"
```

---

## Self-Review notes

- **Spec coverage:** soft-tinted native look (Task 4 CSS) ✓; semantic per-action colors (Task 3 tokens + Task 5 mapping) ✓; two separate settings labels/size (Tasks 1 & 6) ✓; all toolbar buttons incl. panel toggles (Task 5 Steps 4–7) ✓; defaults labels=false/size=small (Task 1 Step 4) ✓; live refresh (Task 5 Step 8) ✓; light+dark tokens (Task 3) ✓; tests (Tasks 1, 2, 7) ✓.
- **`useSmallIcons` left untouched** (pre-existing unwired flag) per spec note.
- **Type consistency:** `toolButton(id, icon, label, tooltip, ToolColor, Runnable)` is the single core used by `documentActionButton` and all call sites; `registerToolButton` / `applyDisplayTo` / `applyToolbarDisplaySettings` take `ButtonBase` so both `Button` and `ToggleButton` (panel toggles) work; `ToolbarDisplay.SMALL_CLASS`/`LARGE_CLASS`/`iconSizePx`/`sizeStyleClass` names match between Task 2 and Task 5.
