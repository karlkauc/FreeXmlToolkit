# Workflow Colours & Branding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every activity of the Unified Shell one workflow accent colour (rail indicator, panel header, primary action, toolbar group, status badge) and one consistent action-colour rule for all menus and panel action rows, driven by theme-aware tokens.

**Architecture:** Two new enums in `controls/theme` (`Workflow`, `ActionColor`) map onto `DesignTokens.ColorToken` entries and matching `-fxt-*` looked-up colours in `design-tokens.css`. The shell attaches a `fxt-wf-<id>` style class to the rail button and the side-panel host, and CSS selectors scoped to that class recolour header, primary button, row hover and badge. Icons that CSS would override are **bound** (not set) via a new `SemanticIcon.bind`, so per-item colours survive CSS passes and re-tint on theme switch.

**Tech Stack:** Java 25 / JavaFX 25, JUnit 5 + TestFX, Gradle 9 (`./gradlew test --tests "<exact.ClassName>"` — never a leading wildcard, see memory `gradle-tests-leading-wildcard-trap`).

**Spec:** `docs/superpowers/specs/2026-09-26-workflow-colours-branding-design.md` (decisions D1–D7; §1 palette, §2 surfaces, §3 action table, §5 token mapping, §6 code sites).

## Global Constraints

- Tokens are the single source of truth: every new colour exists **both** as `ColorToken` (Java) and `-fxt-*` (CSS), light + dark, identical hex (spec §5.1/§5.2).
- Workflow ids and CSS classes: `workspace, validation, transform, schema, pdf, signature, fundsxml, neutral` → `fxt-wf-<id>`; CSS tokens `-fxt-wf-<id>-{accent,fg,fill,bg,border,rail}` (spec §5.4).
- Rail selected style = **B indicator** (3 px left bar in `-fxt-wf-<id>-rail` on `#3b3866`), Help/Settings keep a white icon with the workspace-blue indicator (D3).
- Toolbar Validate = filled `-fxt-wf-validation-fill` (D4). Transform family = brand orange (D6). Dark `fill` values apply only to workflow buttons; `.fxt-primary-button` outside a workflow scope and the brand primary stay `-fxt-primary` (D7).
- No inline `#rrggbb` in migrated Java files (`SemanticColorGuardTest` ratchet); state CSS rules must not change geometry (`ShellCssStabilityTest`).
- All user-facing text English; JavaDoc English; `@FXML` handlers public.
- Icons: `bi-*` names only; `IconifyIconCoverageTest` must stay green.
- Commit after every task; push at the end (user rule: auto commit + push).

## Review Focus

1. **Theme switch after a panel is built** — bound icon colours (`SemanticIcon.bind`) must re-tint to the dark token; pinned by `SemanticIconTest.boundIconRecolorsOnThemeSwitch` (Task 3).
2. **Unknown ExecutionStats operation type / category name** — `Workflow.forOperationType("SOMETHING_NEW")` and `Workflow.forCategory("Other")` must fall back (NEUTRAL / WORKSPACE) instead of throwing; pinned in `WorkflowTest` (Task 1).
3. **FundsXML extension disabled** — the rail has no FundsXML button; `Activity.workflow()` still resolves for every activity; pinned in `ActivityTest.everyActivityHasAWorkflow` (Task 1) and `ActivityBarWorkflowClassTest` (Task 4).
4. **Hover / pressed on the new rail and row rules** — no geometry change in any state rule; pinned by the existing `ShellCssStabilityTest` (run in Tasks 4, 5, 6, 7).
5. **CSS-vs-Java drift** — a later edit to only one side must fail the build; pinned by `WorkflowTokensCssSyncTest` (Task 2).

---

### Task 1: `Workflow` + `ActionColor` enums, `ColorToken` entries, `Activity.workflow()`

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/theme/Workflow.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/theme/ActionColor.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/theme/DesignTokens.java:64-87` (add tokens)
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/Activity.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/theme/WorkflowTest.java` (new), `src/test/java/org/fxt/freexmltoolkit/controls/shell/ActivityTest.java`

**Interfaces:**
- Produces: `enum Workflow { WORKSPACE, VALIDATION, TRANSFORM, SCHEMA, PDF, SIGNATURE, FUNDSXML, NEUTRAL }` with `String id()`, `String cssClass()` (= `"fxt-wf-" + id`), `ColorToken accent()/fg()/fill()/bg()/border()/rail()`, `static Workflow forCategory(String)`, `static Workflow forOperationType(String)`.
- Produces: `enum ActionColor { CREATE, DELETE, MODIFY, NAVIGATE, STRUCTURE, TOOL, NEUTRAL }` with `ColorToken token()`.
- Produces: `Activity.workflow()`.
- Produces: 48 `ColorToken` constants `WF_<ID>_<SLOT>` with CSS names `-fxt-wf-<id>-<slot>`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/org/fxt/freexmltoolkit/controls/theme/WorkflowTest.java`:
```java
package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

/** Workflow colour families (spec 2026-09-26-workflow-colours-branding-design.md §1, §5.2). */
class WorkflowTest {

    @Test
    void everyWorkflowHasSixSlotsWithMatchingCssNames() {
        for (Workflow wf : Workflow.values()) {
            assertEquals("-fxt-wf-" + wf.id() + "-accent", wf.accent().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-fg", wf.fg().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-fill", wf.fill().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-bg", wf.bg().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-border", wf.border().cssVariable());
            assertEquals("-fxt-wf-" + wf.id() + "-rail", wf.rail().cssVariable());
            assertEquals("fxt-wf-" + wf.id(), wf.cssClass());
        }
    }

    @Test
    void railIsTheDarkAccentInBothThemes() {
        for (Workflow wf : Workflow.values()) {
            Color darkAccent = wf.accent().color(DesignTokens.Theme.DARK);
            assertEquals(darkAccent, wf.rail().color(DesignTokens.Theme.LIGHT), wf + " rail light");
            assertEquals(darkAccent, wf.rail().color(DesignTokens.Theme.DARK), wf + " rail dark");
        }
    }

    @Test
    void paletteMatchesSpec() {
        assertEquals(Color.web("#2f9e44"), Workflow.VALIDATION.accent().color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#238636"), Workflow.VALIDATION.fill().color(DesignTokens.Theme.DARK));
        assertEquals(Color.web("#126ccc"), Workflow.WORKSPACE.fg().color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#ac560d"), Workflow.TRANSFORM.fg().color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#d6336c"), Workflow.PDF.accent().color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#1f6feb"), Workflow.WORKSPACE.fill().color(DesignTokens.Theme.DARK));
    }

    @Test
    void categoryLookupCoversWelcomeCategoriesAndFallsBack() {
        assertEquals(Workflow.VALIDATION, Workflow.forCategory("Validation"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory("Editing"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory("Query"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory("Organization"));
        assertEquals(Workflow.TRANSFORM, Workflow.forCategory("Transformation"));
        assertEquals(Workflow.SCHEMA, Workflow.forCategory("Tools"));
        assertEquals(Workflow.SIGNATURE, Workflow.forCategory("Security"));
        assertEquals(Workflow.PDF, Workflow.forCategory("Export"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory("Something new"));
        assertEquals(Workflow.WORKSPACE, Workflow.forCategory(null));
    }

    @Test
    void operationTypeLookupMapsRunsToWorkflowsAndFallsBack() {
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("XSLT"));
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("XQUERY"));
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("XPATH"));
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("JSONPATH"));
        assertEquals(Workflow.TRANSFORM, Workflow.forOperationType("XPROC"));
        assertEquals(Workflow.VALIDATION, Workflow.forOperationType("VALIDATION"));
        assertEquals(Workflow.PDF, Workflow.forOperationType("FOP_PDF"));
        assertEquals(Workflow.NEUTRAL, Workflow.forOperationType("SOMETHING_NEW"));
        assertEquals(Workflow.NEUTRAL, Workflow.forOperationType(null));
    }

    @Test
    void actionColorsMapOntoExistingSemanticTokens() {
        assertEquals(DesignTokens.ColorToken.SUCCESS, ActionColor.CREATE.token());
        assertEquals(DesignTokens.ColorToken.DANGER, ActionColor.DELETE.token());
        assertEquals(DesignTokens.ColorToken.ACCENT, ActionColor.MODIFY.token());
        assertEquals(DesignTokens.ColorToken.INFO, ActionColor.NAVIGATE.token());
        assertEquals(DesignTokens.ColorToken.PURPLE, ActionColor.STRUCTURE.token());
        assertEquals(DesignTokens.ColorToken.PRIMARY, ActionColor.TOOL.token());
        assertEquals(DesignTokens.ColorToken.NEUTRAL, ActionColor.NEUTRAL.token());
    }
}
```

Add to `ActivityTest.java`:
```java
    @Test
    void everyActivityHasAWorkflow() {
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.WORKSPACE, Activity.EXPLORER.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.WORKSPACE, Activity.SEARCH.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.WORKSPACE, Activity.FAVORITES.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.VALIDATION, Activity.VALIDATION.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.TRANSFORM, Activity.TRANSFORM.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.SCHEMA, Activity.SCHEMA.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.SCHEMA, Activity.SCHEMA_LIBRARY.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.PDF, Activity.PDF_FOP.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.SIGNATURE, Activity.SIGNATURE.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.FUNDSXML, Activity.FUNDSXML.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.NEUTRAL, Activity.HELP.workflow());
        assertEquals(org.fxt.freexmltoolkit.controls.theme.Workflow.NEUTRAL, Activity.SETTINGS.workflow());
        for (Activity a : Activity.values()) {
            assertNotNull(a.workflow(), () -> a + " has no workflow");
        }
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.WorkflowTest" --tests "org.fxt.freexmltoolkit.controls.shell.ActivityTest"`
Expected: compilation FAILS (`Workflow`, `ActionColor`, `workflow()` do not exist).

- [ ] **Step 3: Add the 48 tokens to `DesignTokens.ColorToken`**

Insert after `INDIGO(...)` (line 79) and before the `CODE_*` block; every value verbatim from the spec §1.3 (light, dark). Rail = dark accent in both modes.

```java
        // Workflow colour families (spec 2026-09-26 §1.3). Slots: accent (icon tint), fg (text on
        // surface), fill (filled primary action, white label), bg (tint), border, rail (= the dark
        // accent in BOTH themes because the Activity Bar is always navy). See Workflow.
        WF_WORKSPACE_ACCENT("-fxt-wf-workspace-accent", "#1373d9", "#4c9bf5"),
        WF_WORKSPACE_FG("-fxt-wf-workspace-fg", "#126ccc", "#4c9bf5"),
        WF_WORKSPACE_FILL("-fxt-wf-workspace-fill", "#1373d9", "#1f6feb"),
        WF_WORKSPACE_BG("-fxt-wf-workspace-bg", "#eaf2fd", "#14283f"),
        WF_WORKSPACE_BORDER("-fxt-wf-workspace-border", "#c4dcf8", "#275077"),
        WF_WORKSPACE_RAIL("-fxt-wf-workspace-rail", "#4c9bf5", "#4c9bf5"),

        WF_VALIDATION_ACCENT("-fxt-wf-validation-accent", "#2f9e44", "#51cf66"),
        WF_VALIDATION_FG("-fxt-wf-validation-fg", "#1f7a35", "#51cf66"),
        WF_VALIDATION_FILL("-fxt-wf-validation-fill", "#1f7a35", "#238636"),
        WF_VALIDATION_BG("-fxt-wf-validation-bg", "#eaf6ee", "#14301e"),
        WF_VALIDATION_BORDER("-fxt-wf-validation-border", "#bfe3cb", "#2b5a3a"),
        WF_VALIDATION_RAIL("-fxt-wf-validation-rail", "#51cf66", "#51cf66"),

        WF_TRANSFORM_ACCENT("-fxt-wf-transform-accent", "#f08c2e", "#f59f46"),
        WF_TRANSFORM_FG("-fxt-wf-transform-fg", "#ac560d", "#f59f46"),
        WF_TRANSFORM_FILL("-fxt-wf-transform-fill", "#b35a0e", "#b35a0e"),
        WF_TRANSFORM_BG("-fxt-wf-transform-bg", "#fdf0e6", "#2e2010"),
        WF_TRANSFORM_BORDER("-fxt-wf-transform-border", "#f9cfae", "#5c3f1c"),
        WF_TRANSFORM_RAIL("-fxt-wf-transform-rail", "#f59f46", "#f59f46"),

        WF_SCHEMA_ACCENT("-fxt-wf-schema-accent", "#6f42c1", "#b197fc"),
        WF_SCHEMA_FG("-fxt-wf-schema-fg", "#6f42c1", "#b197fc"),
        WF_SCHEMA_FILL("-fxt-wf-schema-fill", "#6f42c1", "#7048e8"),
        WF_SCHEMA_BG("-fxt-wf-schema-bg", "#f3f0ff", "#241b3d"),
        WF_SCHEMA_BORDER("-fxt-wf-schema-border", "#d0bfff", "#463374"),
        WF_SCHEMA_RAIL("-fxt-wf-schema-rail", "#b197fc", "#b197fc"),

        WF_PDF_ACCENT("-fxt-wf-pdf-accent", "#d6336c", "#f783ac"),
        WF_PDF_FG("-fxt-wf-pdf-fg", "#c2255c", "#f783ac"),
        WF_PDF_FILL("-fxt-wf-pdf-fill", "#c2255c", "#d6336c"),
        WF_PDF_BG("-fxt-wf-pdf-bg", "#fff0f6", "#33161f"),
        WF_PDF_BORDER("-fxt-wf-pdf-border", "#fcc2d7", "#66293d"),
        WF_PDF_RAIL("-fxt-wf-pdf-rail", "#f783ac", "#f783ac"),

        WF_SIGNATURE_ACCENT("-fxt-wf-signature-accent", "#1098ad", "#4dd4e8"),
        WF_SIGNATURE_FG("-fxt-wf-signature-fg", "#0b7285", "#4dd4e8"),
        WF_SIGNATURE_FILL("-fxt-wf-signature-fill", "#0b7285", "#0b7285"),
        WF_SIGNATURE_BG("-fxt-wf-signature-bg", "#e7f6f8", "#0e2a30"),
        WF_SIGNATURE_BORDER("-fxt-wf-signature-border", "#b9e3e9", "#1f4a53"),
        WF_SIGNATURE_RAIL("-fxt-wf-signature-rail", "#4dd4e8", "#4dd4e8"),

        WF_FUNDSXML_ACCENT("-fxt-wf-fundsxml-accent", "#12a594", "#38d9a9"),
        WF_FUNDSXML_FG("-fxt-wf-fundsxml-fg", "#0b7a6e", "#38d9a9"),
        WF_FUNDSXML_FILL("-fxt-wf-fundsxml-fill", "#0b7a6e", "#0b7a6e"),
        WF_FUNDSXML_BG("-fxt-wf-fundsxml-bg", "#e6fcf5", "#0f2b26"),
        WF_FUNDSXML_BORDER("-fxt-wf-fundsxml-border", "#a8ecd6", "#1f5348"),
        WF_FUNDSXML_RAIL("-fxt-wf-fundsxml-rail", "#38d9a9", "#38d9a9"),

        WF_NEUTRAL_ACCENT("-fxt-wf-neutral-accent", "#5a6472", "#9ba6b3"),
        WF_NEUTRAL_FG("-fxt-wf-neutral-fg", "#5a6472", "#9ba6b3"),
        WF_NEUTRAL_FILL("-fxt-wf-neutral-fill", "#5a6472", "#5a6472"),
        WF_NEUTRAL_BG("-fxt-wf-neutral-bg", "#f2f4f8", "#1c232c"),
        WF_NEUTRAL_BORDER("-fxt-wf-neutral-border", "#dde1e7", "#2a323d"),
        WF_NEUTRAL_RAIL("-fxt-wf-neutral-rail", "#9ba6b3", "#9ba6b3"),
```

- [ ] **Step 4: Create `Workflow.java`**

```java
package org.fxt.freexmltoolkit.controls.theme;

import java.util.Locale;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken;

/**
 * The workflow colour families of the Unified Shell (design spec
 * {@code docs/superpowers/specs/2026-09-26-workflow-colours-branding-design.md}).
 * <p>
 * Each activity belongs to exactly one family ({@code Activity.workflow()}); the family
 * colours only the chrome that names the workflow: the Activity-Bar indicator, the side-panel
 * title, the one primary action of the panel, the editor-toolbar group and the status badge.
 * Body text, lists, editors, menus and dialogs never take a workflow colour — menus use
 * {@link ActionColor} instead.
 * <p>
 * Slots per family (all theme-aware {@link ColorToken}s):
 * <ul>
 *   <li>{@link #accent()} icon tint (toolbar group, welcome icon chip)</li>
 *   <li>{@link #fg()} text on a surface (panel title, badge text), ≥ 4.5:1</li>
 *   <li>{@link #fill()} filled primary action with a white label, ≥ 4.5:1 against white</li>
 *   <li>{@link #bg()} tint (badge background, action-row hover, icon chip)</li>
 *   <li>{@link #border()} badge / chip outline</li>
 *   <li>{@link #rail()} indicator + icon on the always-navy Activity Bar (= dark accent in both themes)</li>
 * </ul>
 * The CSS side declares the same values as {@code -fxt-wf-<id>-<slot>} in
 * {@code design-tokens.css}; the style-class hook is {@link #cssClass()} ({@code fxt-wf-<id>}).
 */
public enum Workflow {
    WORKSPACE("workspace", ColorToken.WF_WORKSPACE_ACCENT, ColorToken.WF_WORKSPACE_FG, ColorToken.WF_WORKSPACE_FILL,
            ColorToken.WF_WORKSPACE_BG, ColorToken.WF_WORKSPACE_BORDER, ColorToken.WF_WORKSPACE_RAIL),
    VALIDATION("validation", ColorToken.WF_VALIDATION_ACCENT, ColorToken.WF_VALIDATION_FG, ColorToken.WF_VALIDATION_FILL,
            ColorToken.WF_VALIDATION_BG, ColorToken.WF_VALIDATION_BORDER, ColorToken.WF_VALIDATION_RAIL),
    TRANSFORM("transform", ColorToken.WF_TRANSFORM_ACCENT, ColorToken.WF_TRANSFORM_FG, ColorToken.WF_TRANSFORM_FILL,
            ColorToken.WF_TRANSFORM_BG, ColorToken.WF_TRANSFORM_BORDER, ColorToken.WF_TRANSFORM_RAIL),
    SCHEMA("schema", ColorToken.WF_SCHEMA_ACCENT, ColorToken.WF_SCHEMA_FG, ColorToken.WF_SCHEMA_FILL,
            ColorToken.WF_SCHEMA_BG, ColorToken.WF_SCHEMA_BORDER, ColorToken.WF_SCHEMA_RAIL),
    PDF("pdf", ColorToken.WF_PDF_ACCENT, ColorToken.WF_PDF_FG, ColorToken.WF_PDF_FILL,
            ColorToken.WF_PDF_BG, ColorToken.WF_PDF_BORDER, ColorToken.WF_PDF_RAIL),
    SIGNATURE("signature", ColorToken.WF_SIGNATURE_ACCENT, ColorToken.WF_SIGNATURE_FG, ColorToken.WF_SIGNATURE_FILL,
            ColorToken.WF_SIGNATURE_BG, ColorToken.WF_SIGNATURE_BORDER, ColorToken.WF_SIGNATURE_RAIL),
    FUNDSXML("fundsxml", ColorToken.WF_FUNDSXML_ACCENT, ColorToken.WF_FUNDSXML_FG, ColorToken.WF_FUNDSXML_FILL,
            ColorToken.WF_FUNDSXML_BG, ColorToken.WF_FUNDSXML_BORDER, ColorToken.WF_FUNDSXML_RAIL),
    NEUTRAL("neutral", ColorToken.WF_NEUTRAL_ACCENT, ColorToken.WF_NEUTRAL_FG, ColorToken.WF_NEUTRAL_FILL,
            ColorToken.WF_NEUTRAL_BG, ColorToken.WF_NEUTRAL_BORDER, ColorToken.WF_NEUTRAL_RAIL);

    /** Prefix of the style class that scopes the workflow CSS rules. */
    public static final String CSS_CLASS_PREFIX = "fxt-wf-";

    private final String id;
    private final ColorToken accent;
    private final ColorToken fg;
    private final ColorToken fill;
    private final ColorToken bg;
    private final ColorToken border;
    private final ColorToken rail;

    Workflow(String id, ColorToken accent, ColorToken fg, ColorToken fill, ColorToken bg, ColorToken border,
             ColorToken rail) {
        this.id = id;
        this.accent = accent;
        this.fg = fg;
        this.fill = fill;
        this.bg = bg;
        this.border = border;
        this.rail = rail;
    }

    /** @return the stable id used in token and class names, e.g. {@code validation}. */
    public String id() {
        return id;
    }

    /** @return the style class that scopes this family's CSS rules, e.g. {@code fxt-wf-validation}. */
    public String cssClass() {
        return CSS_CLASS_PREFIX + id;
    }

    public ColorToken accent() {
        return accent;
    }

    public ColorToken fg() {
        return fg;
    }

    public ColorToken fill() {
        return fill;
    }

    public ColorToken bg() {
        return bg;
    }

    public ColorToken border() {
        return border;
    }

    public ColorToken rail() {
        return rail;
    }

    /**
     * Maps a Welcome-page / SkillTracker feature category to its workflow (spec §2, Welcome
     * tool cards). Unknown or {@code null} categories fall back to {@link #WORKSPACE}.
     */
    public static Workflow forCategory(String category) {
        if (category == null) {
            return WORKSPACE;
        }
        return switch (category) {
            case "Validation" -> VALIDATION;
            case "Transformation" -> TRANSFORM;
            case "Tools" -> SCHEMA;
            case "Security" -> SIGNATURE;
            case "Export" -> PDF;
            default -> WORKSPACE; // Editing, Query, Organization, anything new
        };
    }

    /**
     * Maps an {@code ExecutionStats.OperationType} name to the workflow that ran it (status-bar
     * badge). Takes the enum <em>name</em> so this package stays free of service dependencies.
     * Unknown or {@code null} names fall back to {@link #NEUTRAL}.
     */
    public static Workflow forOperationType(String operationType) {
        if (operationType == null) {
            return NEUTRAL;
        }
        return switch (operationType.toUpperCase(Locale.ROOT)) {
            case "XSLT", "XQUERY", "XPATH", "JSONPATH", "XPROC" -> TRANSFORM;
            case "VALIDATION" -> VALIDATION;
            case "FOP_PDF" -> PDF;
            default -> NEUTRAL;
        };
    }
}
```

- [ ] **Step 5: Create `ActionColor.java`**

```java
package org.fxt.freexmltoolkit.controls.theme;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken;

/**
 * The action-colour roles for menu items and panel action rows (design spec
 * {@code docs/superpowers/specs/2026-09-26-workflow-colours-branding-design.md} §3).
 * One rule for every context menu and side-panel action list:
 * <ul>
 *   <li>{@link #CREATE} — Add, Insert, New, Paste, Duplicate, Validate, Generate</li>
 *   <li>{@link #DELETE} — Delete, Remove, Clear, Cut</li>
 *   <li>{@link #MODIFY} — Rename, Edit value, Comment toggle</li>
 *   <li>{@link #NAVIGATE} — Go to Definition, Reveal, Open referenced/report/tool, Find</li>
 *   <li>{@link #STRUCTURE} — Change Type, Cardinality, Move Up/Down, Reorder</li>
 *   <li>{@link #TOOL} — Format, Minify, Sort, Run/Execute in menus, Open/Save/Export, Download</li>
 *   <li>{@link #NEUTRAL} — Copy (all variants), Expand/Collapse, Select all, Undo/Redo, Settings</li>
 * </ul>
 * Roles map onto the existing semantic tokens; TEAL and INDIGO are not used for actions.
 * Paint an icon with {@code SemanticIcon.paint(icon, ActionColor.DELETE)} or bind it with
 * {@code SemanticIcon.bind(icon, ActionColor.DELETE)} where CSS would otherwise override it.
 */
public enum ActionColor {
    CREATE(ColorToken.SUCCESS),
    DELETE(ColorToken.DANGER),
    MODIFY(ColorToken.ACCENT),
    NAVIGATE(ColorToken.INFO),
    STRUCTURE(ColorToken.PURPLE),
    TOOL(ColorToken.PRIMARY),
    NEUTRAL(ColorToken.NEUTRAL);

    private final ColorToken token;

    ActionColor(ColorToken token) {
        this.token = token;
    }

    /** @return the theme-aware token this role paints with. */
    public ColorToken token() {
        return token;
    }
}
```

- [ ] **Step 6: Add `workflow()` to `Activity`**

Replace the enum constants and constructor in `Activity.java`:
```java
import org.fxt.freexmltoolkit.controls.theme.Workflow;
...
public enum Activity {
    EXPLORER("explorer", "Explorer", "bi-folder2-open", Workflow.WORKSPACE),
    SEARCH("search", "Search", "bi-search", Workflow.WORKSPACE),
    FAVORITES("favorites", "Favorites", "bi-star", Workflow.WORKSPACE),
    VALIDATION("validation", "Validation", "bi-check2-circle", Workflow.VALIDATION),
    TRANSFORM("transform", "Transform", "bi-arrow-repeat", Workflow.TRANSFORM),
    SCHEMA("schema", "Schema", "bi-diagram-3", Workflow.SCHEMA),
    SCHEMA_LIBRARY("schema-library", "Schema Library", "bi-collection", Workflow.SCHEMA),
    PDF_FOP("pdf", "PDF / FOP", "bi-file-earmark-pdf", Workflow.PDF),
    SIGNATURE("signature", "Signature", "bi-shield-lock", Workflow.SIGNATURE),
    FUNDSXML("fundsxml", "FundsXML", "bi-file-earmark-code", Workflow.FUNDSXML),
    HELP("help", "Help", "bi-question-circle", Workflow.NEUTRAL),
    SETTINGS("settings", "Settings", "bi-gear", Workflow.NEUTRAL);

    private final String id;
    private final String label;
    private final String icon;
    private final Workflow workflow;

    Activity(String id, String label, String icon, Workflow workflow) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.workflow = workflow;
    }

    /** @return the workflow colour family this activity belongs to (spec §1.1). */
    public Workflow workflow() {
        return workflow;
    }
```
Keep the existing `id()/label()/icon()/defaultActivity()/fromId()` untouched.

- [ ] **Step 7: Run the tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.WorkflowTest" --tests "org.fxt.freexmltoolkit.controls.shell.ActivityTest" --tests "org.fxt.freexmltoolkit.controls.theme.DesignTokensTest"`
Expected: all PASS (DesignTokensTest also checks the new CSS names start with `-fxt-` and are unique).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/theme/Workflow.java src/main/java/org/fxt/freexmltoolkit/controls/theme/ActionColor.java src/main/java/org/fxt/freexmltoolkit/controls/theme/DesignTokens.java src/main/java/org/fxt/freexmltoolkit/controls/shell/Activity.java src/test/java/org/fxt/freexmltoolkit/controls/theme/WorkflowTest.java src/test/java/org/fxt/freexmltoolkit/controls/shell/ActivityTest.java
git commit -m "feat(theme): Workflow and ActionColor enums with workflow colour tokens"
```

---

### Task 2: CSS tokens in `design-tokens.css` + Java/CSS sync test

**Files:**
- Modify: `src/main/resources/css/design-tokens.css:65-71` (light block end) and `:119-125` (dark block end)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/theme/WorkflowTokensCssSyncTest.java` (new)

**Interfaces:**
- Consumes: `Workflow`, `ColorToken.cssVariable()/color(Theme)` (Task 1).
- Produces: looked-up colours `-fxt-wf-<id>-{accent,fg,fill,bg,border,rail}` for light (`.root`) and dark (`.root.fxt-theme-dark`), plus action aliases `-fxt-action-{create,delete,modify,navigate,structure,tool,neutral}`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

/**
 * Lock-step guard: every workflow token exists in design-tokens.css for light AND dark with the
 * same hex as {@link DesignTokens.ColorToken}, and the action aliases exist. Editing only one side
 * fails the build.
 */
class WorkflowTokensCssSyncTest {

    private static final Path CSS = Path.of("src/main/resources/css/design-tokens.css");

    private static String block(String css, String selector) {
        int start = css.indexOf(selector + " {");
        assertTrue(start >= 0, "selector not found: " + selector);
        int end = css.indexOf("}", start);
        return css.substring(start, end);
    }

    private static String value(String block, String variable) {
        Matcher m = Pattern.compile(Pattern.quote(variable) + "\\s*:\\s*([^;]+);").matcher(block);
        assertTrue(m.find(), () -> variable + " missing in block");
        return m.group(1).trim();
    }

    @Test
    void workflowTokensMatchJavaInBothThemes() throws IOException {
        String css = Files.readString(CSS);
        String light = block(css, ".root");
        String dark = block(css, ".root.fxt-theme-dark");
        for (Workflow wf : Workflow.values()) {
            for (DesignTokens.ColorToken t : new DesignTokens.ColorToken[] {
                    wf.accent(), wf.fg(), wf.fill(), wf.bg(), wf.border(), wf.rail() }) {
                assertEquals(t.color(DesignTokens.Theme.LIGHT), Color.web(value(light, t.cssVariable())),
                        () -> t.cssVariable() + " light differs between CSS and Java");
                assertEquals(t.color(DesignTokens.Theme.DARK), Color.web(value(dark, t.cssVariable())),
                        () -> t.cssVariable() + " dark differs between CSS and Java");
            }
        }
    }

    @Test
    void actionAliasesPointAtSemanticTokens() throws IOException {
        String css = Files.readString(CSS);
        String light = block(css, ".root");
        String dark = block(css, ".root.fxt-theme-dark");
        for (ActionColor a : ActionColor.values()) {
            String alias = "-fxt-action-" + a.name().toLowerCase();
            assertEquals(a.token().cssVariable(), value(light, alias), alias + " (light)");
            assertEquals(a.token().cssVariable(), value(dark, alias), alias + " (dark)");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.WorkflowTokensCssSyncTest"`
Expected: FAIL with `-fxt-wf-workspace-accent missing in block`.

- [ ] **Step 3: Add the tokens to the light block** (insert after line 71, still inside `.root { … }`)

```css
    /* Workflow colour families (spec 2026-09-26, see controls/theme/Workflow):
       accent = icon tint · fg = text on surface · fill = filled primary action (white label)
       bg = tint · border = chip outline · rail = indicator/icon on the always-navy Activity Bar
       (= the DARK accent in both themes). Kept in lock-step with DesignTokens.ColorToken.WF_*. */
    -fxt-wf-workspace-accent: #1373d9;  -fxt-wf-workspace-fg: #126ccc;  -fxt-wf-workspace-fill: #1373d9;  -fxt-wf-workspace-bg: #eaf2fd;  -fxt-wf-workspace-border: #c4dcf8;  -fxt-wf-workspace-rail: #4c9bf5;
    -fxt-wf-validation-accent: #2f9e44; -fxt-wf-validation-fg: #1f7a35; -fxt-wf-validation-fill: #1f7a35; -fxt-wf-validation-bg: #eaf6ee; -fxt-wf-validation-border: #bfe3cb; -fxt-wf-validation-rail: #51cf66;
    -fxt-wf-transform-accent: #f08c2e;  -fxt-wf-transform-fg: #ac560d;  -fxt-wf-transform-fill: #b35a0e;  -fxt-wf-transform-bg: #fdf0e6;  -fxt-wf-transform-border: #f9cfae;  -fxt-wf-transform-rail: #f59f46;
    -fxt-wf-schema-accent: #6f42c1;     -fxt-wf-schema-fg: #6f42c1;     -fxt-wf-schema-fill: #6f42c1;     -fxt-wf-schema-bg: #f3f0ff;     -fxt-wf-schema-border: #d0bfff;     -fxt-wf-schema-rail: #b197fc;
    -fxt-wf-pdf-accent: #d6336c;        -fxt-wf-pdf-fg: #c2255c;        -fxt-wf-pdf-fill: #c2255c;        -fxt-wf-pdf-bg: #fff0f6;        -fxt-wf-pdf-border: #fcc2d7;        -fxt-wf-pdf-rail: #f783ac;
    -fxt-wf-signature-accent: #1098ad;  -fxt-wf-signature-fg: #0b7285;  -fxt-wf-signature-fill: #0b7285;  -fxt-wf-signature-bg: #e7f6f8;  -fxt-wf-signature-border: #b9e3e9;  -fxt-wf-signature-rail: #4dd4e8;
    -fxt-wf-fundsxml-accent: #12a594;   -fxt-wf-fundsxml-fg: #0b7a6e;   -fxt-wf-fundsxml-fill: #0b7a6e;   -fxt-wf-fundsxml-bg: #e6fcf5;   -fxt-wf-fundsxml-border: #a8ecd6;   -fxt-wf-fundsxml-rail: #38d9a9;
    -fxt-wf-neutral-accent: #5a6472;    -fxt-wf-neutral-fg: #5a6472;    -fxt-wf-neutral-fill: #5a6472;    -fxt-wf-neutral-bg: #f2f4f8;    -fxt-wf-neutral-border: #dde1e7;    -fxt-wf-neutral-rail: #9ba6b3;

    /* Action-colour roles (spec §3, see controls/theme/ActionColor) — aliases of the semantic tokens. */
    -fxt-action-create: -fxt-success;   -fxt-action-delete: -fxt-danger;   -fxt-action-modify: -fxt-accent;
    -fxt-action-navigate: -fxt-info;    -fxt-action-structure: -fxt-purple; -fxt-action-tool: -fxt-primary;
    -fxt-action-neutral: -fxt-neutral;
```

- [ ] **Step 4: Add the tokens to the dark block** (insert after line 125, inside `.root.fxt-theme-dark { … }`)

```css
    /* Workflow colour families — dark */
    -fxt-wf-workspace-accent: #4c9bf5;  -fxt-wf-workspace-fg: #4c9bf5;  -fxt-wf-workspace-fill: #1f6feb;  -fxt-wf-workspace-bg: #14283f;  -fxt-wf-workspace-border: #275077;  -fxt-wf-workspace-rail: #4c9bf5;
    -fxt-wf-validation-accent: #51cf66; -fxt-wf-validation-fg: #51cf66; -fxt-wf-validation-fill: #238636; -fxt-wf-validation-bg: #14301e; -fxt-wf-validation-border: #2b5a3a; -fxt-wf-validation-rail: #51cf66;
    -fxt-wf-transform-accent: #f59f46;  -fxt-wf-transform-fg: #f59f46;  -fxt-wf-transform-fill: #b35a0e;  -fxt-wf-transform-bg: #2e2010;  -fxt-wf-transform-border: #5c3f1c;  -fxt-wf-transform-rail: #f59f46;
    -fxt-wf-schema-accent: #b197fc;     -fxt-wf-schema-fg: #b197fc;     -fxt-wf-schema-fill: #7048e8;     -fxt-wf-schema-bg: #241b3d;     -fxt-wf-schema-border: #463374;     -fxt-wf-schema-rail: #b197fc;
    -fxt-wf-pdf-accent: #f783ac;        -fxt-wf-pdf-fg: #f783ac;        -fxt-wf-pdf-fill: #d6336c;        -fxt-wf-pdf-bg: #33161f;        -fxt-wf-pdf-border: #66293d;        -fxt-wf-pdf-rail: #f783ac;
    -fxt-wf-signature-accent: #4dd4e8;  -fxt-wf-signature-fg: #4dd4e8;  -fxt-wf-signature-fill: #0b7285;  -fxt-wf-signature-bg: #0e2a30;  -fxt-wf-signature-border: #1f4a53;  -fxt-wf-signature-rail: #4dd4e8;
    -fxt-wf-fundsxml-accent: #38d9a9;   -fxt-wf-fundsxml-fg: #38d9a9;   -fxt-wf-fundsxml-fill: #0b7a6e;   -fxt-wf-fundsxml-bg: #0f2b26;   -fxt-wf-fundsxml-border: #1f5348;   -fxt-wf-fundsxml-rail: #38d9a9;
    -fxt-wf-neutral-accent: #9ba6b3;    -fxt-wf-neutral-fg: #9ba6b3;    -fxt-wf-neutral-fill: #5a6472;    -fxt-wf-neutral-bg: #1c232c;    -fxt-wf-neutral-border: #2a323d;    -fxt-wf-neutral-rail: #9ba6b3;

    /* Action-colour roles — same aliases, dark values come from the semantic tokens above. */
    -fxt-action-create: -fxt-success;   -fxt-action-delete: -fxt-danger;   -fxt-action-modify: -fxt-accent;
    -fxt-action-navigate: -fxt-info;    -fxt-action-structure: -fxt-purple; -fxt-action-tool: -fxt-primary;
    -fxt-action-neutral: -fxt-neutral;
```

- [ ] **Step 5: Run the sync test and the CSS stability test**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.WorkflowTokensCssSyncTest" --tests "org.fxt.freexmltoolkit.controls.theme.ShellCssStabilityTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/css/design-tokens.css src/test/java/org/fxt/freexmltoolkit/controls/theme/WorkflowTokensCssSyncTest.java
git commit -m "feat(theme): workflow and action colour tokens in design-tokens.css with sync test"
```

---

### Task 3: `SemanticIcon.paint/bind` for `ActionColor` and bound (CSS-proof) colours

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/theme/SemanticIcon.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/theme/SemanticIconTest.java`

**Interfaces:**
- Consumes: `ActionColor.token()` (Task 1), `ThemeManager.addThemeChangeListener/currentTheme()` (exists).
- Produces: `SemanticIcon.paint(IconifyIcon, ActionColor)`, `SemanticIcon.bind(IconifyIcon, ColorToken)`, `SemanticIcon.bind(IconifyIcon, ActionColor)`. `bind` binds `iconColorProperty()` to a theme-tracking property, so a CSS `-fx-icon-color` rule cannot override it (JavaFX CSS skips bound properties — see memory `javafx-css-overrides-programmatic-color`).

- [ ] **Step 1: Write the failing tests** (append to `SemanticIconTest`, same `runAndWait` helper)

```java
    @Test
    @DisplayName("Action-colour overload paints with the role's token")
    void paintsActionColor() {
        runAndWait(() -> {
            Scene scene = new Scene(new StackPane(), 10, 10);
            ThemeManager.apply(scene, false);
            IconifyIcon icon = SemanticIcon.paint(new IconifyIcon("bi-trash"), ActionColor.DELETE);
            assertEquals(DesignTokens.ColorToken.DANGER.color(DesignTokens.Theme.LIGHT), icon.getIconColor());
        });
    }

    @Test
    @DisplayName("Bound icon colour re-tints on a theme switch and stays bound (CSS cannot override it)")
    void boundIconRecolorsOnThemeSwitch() {
        runAndWait(() -> {
            Scene scene = new Scene(new StackPane(), 10, 10);
            ThemeManager.apply(scene, false);
            IconifyIcon icon = SemanticIcon.bind(new IconifyIcon("bi-plus-circle"), ActionColor.CREATE);
            assertTrue(icon.iconColorProperty().isBound(), "icon colour must be bound");
            assertEquals(DesignTokens.ColorToken.SUCCESS.color(DesignTokens.Theme.LIGHT), icon.getIconColor());
            ThemeManager.apply(scene, true);
            assertEquals(DesignTokens.ColorToken.SUCCESS.color(DesignTokens.Theme.DARK), icon.getIconColor());
            ThemeManager.apply(scene, false);
        });
    }
```
Add `import org.fxt.freexmltoolkit.controls.theme.ActionColor;` is not needed (same package).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.SemanticIconTest"`
Expected: compilation FAILS (`paint(IconifyIcon, ActionColor)` / `bind` missing).

- [ ] **Step 3: Implement in `SemanticIcon`**

Add imports `javafx.beans.property.ObjectProperty`, `javafx.beans.property.SimpleObjectProperty`, `javafx.scene.paint.Paint`. Add a second registry and the three methods:

```java
    private record Bound(WeakReference<IconifyIcon> icon, ObjectProperty<Paint> color,
                         DesignTokens.ColorToken token) {
    }

    private static final CopyOnWriteArrayList<Bound> BOUND = new CopyOnWriteArrayList<>();

    /** Colours {@code icon} with the token of the action role; see {@link #paint(IconifyIcon, DesignTokens.ColorToken)}. */
    public static IconifyIcon paint(IconifyIcon icon, ActionColor role) {
        return role == null ? icon : paint(icon, role.token());
    }

    /**
     * Like {@link #paint(IconifyIcon, DesignTokens.ColorToken)} but <em>binds</em> the icon colour to a
     * theme-tracking property. Use this wherever a CSS rule ({@code -fx-icon-color}) would otherwise
     * repaint the icon on every CSS pass — e.g. panel action rows, status-bar items — because JavaFX
     * CSS never writes to a bound property. The icon is held weakly; the binding is released with it.
     */
    public static IconifyIcon bind(IconifyIcon icon, DesignTokens.ColorToken token) {
        if (icon == null || token == null) {
            return icon;
        }
        ObjectProperty<Paint> color = new SimpleObjectProperty<>(token.color(ThemeManager.currentTheme()));
        icon.iconColorProperty().unbind();
        icon.iconColorProperty().bind(color);
        BOUND.add(new Bound(new WeakReference<>(icon), color, token));
        return icon;
    }

    /** Binds the icon colour to the token of the action role; see {@link #bind(IconifyIcon, DesignTokens.ColorToken)}. */
    public static IconifyIcon bind(IconifyIcon icon, ActionColor role) {
        return role == null ? icon : bind(icon, role.token());
    }
```
Extend `recolorAll` so it also updates the bound properties:
```java
    private static void recolorAll(DesignTokens.Theme theme) {
        REGISTRY.removeIf(reg -> {
            IconifyIcon icon = reg.icon().get();
            if (icon == null) {
                return true;
            }
            icon.setIconColor(reg.token().color(theme));
            return false;
        });
        BOUND.removeIf(b -> {
            if (b.icon().get() == null) {
                return true;
            }
            b.color().set(b.token().color(theme));
            return false;
        });
    }
```
Update the class JavaDoc usage line to mention both `ActionColor` overloads and `bind`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.SemanticIconTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/theme/SemanticIcon.java src/test/java/org/fxt/freexmltoolkit/controls/theme/SemanticIconTest.java
git commit -m "feat(theme): SemanticIcon ActionColor overloads and CSS-proof bound colours"
```

---

### Task 4: Activity Bar — workflow class + selected style B (indicator)

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/ActivityBar.java:130-165`
- Modify: `src/main/resources/css/unified-shell.css:17-84` (rail block)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/ActivityBarWorkflowClassTest.java` (new)

**Interfaces:**
- Consumes: `Activity.workflow().cssClass()` (Task 1); CSS tokens `-fxt-wf-<id>-rail` (Task 2).
- Produces: every rail `ToggleButton` carries `fxt-wf-<id>`; CSS rules `.fxt-activity-button.fxt-wf-<id>:selected`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.ToggleButton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/** Each Activity-Bar button carries its workflow style class so CSS can colour the selected indicator. */
@ExtendWith(ApplicationExtension.class)
class ActivityBarWorkflowClassTest {

    @Test
    void everyButtonCarriesItsWorkflowClass() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ActivityBar bar = new ActivityBar(new ActivitySelectionModel());
                for (var node : bar.lookupAll(".fxt-activity-button")) {
                    ToggleButton button = (ToggleButton) node;
                    Activity activity = (Activity) button.getUserData();
                    assertTrue(button.getStyleClass().contains(activity.workflow().cssClass()),
                            () -> activity + " is missing " + activity.workflow().cssClass());
                }
                assertTrue(bar.lookupAll(".fxt-activity-button").size() >= 11, "expected the rail buttons");
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
```
(`lookupAll` works without a Scene for style-class lookups on the built children; if it returns empty, wrap the bar in `new Scene(new javafx.scene.layout.StackPane(bar))` first.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ActivityBarWorkflowClassTest"`
Expected: FAIL (`EXPLORER is missing fxt-wf-workspace`).

- [ ] **Step 3: Add the class in `ActivityBar.createButton`** (after `button.getStyleClass().add("fxt-activity-button");`)

```java
        // Workflow colour hook (spec 2026-09-26 §2): CSS paints the selected indicator and icon in
        // the activity's workflow/rail colour — .fxt-activity-button.fxt-wf-<id>:selected.
        button.getStyleClass().add(activity.workflow().cssClass());
```

- [ ] **Step 4: Replace the rail CSS (lines 17–84 of `unified-shell.css`)**

The button gets two background layers: layer 1 is the full rectangle (the 3 px indicator shows on the left), layer 2 covers everything except the left 3 px. Insets/radii are set ONLY in the base rule, so state rules change colours only (ShellCssStabilityTest).

```css
/* ----- Activity Bar (navy rail in both themes; chrome/rail/* in Figma) ------------------ */
.fxt-activity-bar {
    -fx-background-color: #2a284c;
    -fx-border-color: #3b3866;
    -fx-border-width: 0 1 0 0;
    -fx-min-width: 56;
    -fx-pref-width: 56;
    -fx-padding: 8 0 8 0;
    -fx-spacing: 4;
    -fx-alignment: TOP_CENTER;
}

/* Two background layers: [0] = the full button (only its left 3 px stay visible = the
   selected-indicator, style B of the workflow-colour spec), [1] = the item surface inset by
   3 px on the left. Geometry lives here only; state rules below change colours. */
.fxt-activity-button {
    -fx-background-color: transparent, transparent;
    -fx-background-insets: 0, 0 0 0 3;
    -fx-background-radius: 2 8 8 2, 8;
    -fx-min-width: 40;
    -fx-min-height: 40;
    -fx-max-width: 40;
    -fx-max-height: 40;
    -fx-alignment: CENTER;
    -fx-cursor: hand;
}

.fxt-activity-bar-labeled {
    -fx-min-width: 74;
    -fx-pref-width: 74;
}

.fxt-activity-button-labeled {
    -fx-min-width: 66;
    -fx-max-width: 66;
    -fx-min-height: 52;
    -fx-max-height: 52;
    -fx-content-display: top;
    -fx-graphic-text-gap: 3;
    -fx-font-size: 9.5px;
    -fx-text-fill: #a5a3c4;
    -fx-alignment: CENTER;
}

.fxt-activity-button .iconify-icon {
    -fx-icon-color: #a5a3c4;
}

.fxt-activity-button:hover {
    -fx-background-color: transparent, #3b3866;
}

.fxt-activity-button:hover .iconify-icon,
.fxt-activity-button-labeled:hover {
    -fx-icon-color: #e9ecf1;
    -fx-text-fill: #e9ecf1;
}

/* Selected = item surface + 3 px indicator and icon/label in the workflow's rail colour. */
.fxt-activity-button:selected {
    -fx-background-color: -fxt-wf-workspace-rail, #3b3866;
}
.fxt-activity-button:selected .iconify-icon,
.fxt-activity-button-labeled:selected {
    -fx-icon-color: -fxt-wf-workspace-rail;
    -fx-text-fill: -fxt-wf-workspace-rail;
}
.fxt-activity-button.fxt-wf-validation:selected { -fx-background-color: -fxt-wf-validation-rail, #3b3866; }
.fxt-activity-button.fxt-wf-validation:selected .iconify-icon, .fxt-activity-button-labeled.fxt-wf-validation:selected { -fx-icon-color: -fxt-wf-validation-rail; -fx-text-fill: -fxt-wf-validation-rail; }
.fxt-activity-button.fxt-wf-transform:selected { -fx-background-color: -fxt-wf-transform-rail, #3b3866; }
.fxt-activity-button.fxt-wf-transform:selected .iconify-icon, .fxt-activity-button-labeled.fxt-wf-transform:selected { -fx-icon-color: -fxt-wf-transform-rail; -fx-text-fill: -fxt-wf-transform-rail; }
.fxt-activity-button.fxt-wf-schema:selected { -fx-background-color: -fxt-wf-schema-rail, #3b3866; }
.fxt-activity-button.fxt-wf-schema:selected .iconify-icon, .fxt-activity-button-labeled.fxt-wf-schema:selected { -fx-icon-color: -fxt-wf-schema-rail; -fx-text-fill: -fxt-wf-schema-rail; }
.fxt-activity-button.fxt-wf-pdf:selected { -fx-background-color: -fxt-wf-pdf-rail, #3b3866; }
.fxt-activity-button.fxt-wf-pdf:selected .iconify-icon, .fxt-activity-button-labeled.fxt-wf-pdf:selected { -fx-icon-color: -fxt-wf-pdf-rail; -fx-text-fill: -fxt-wf-pdf-rail; }
.fxt-activity-button.fxt-wf-signature:selected { -fx-background-color: -fxt-wf-signature-rail, #3b3866; }
.fxt-activity-button.fxt-wf-signature:selected .iconify-icon, .fxt-activity-button-labeled.fxt-wf-signature:selected { -fx-icon-color: -fxt-wf-signature-rail; -fx-text-fill: -fxt-wf-signature-rail; }
.fxt-activity-button.fxt-wf-fundsxml:selected { -fx-background-color: -fxt-wf-fundsxml-rail, #3b3866; }
.fxt-activity-button.fxt-wf-fundsxml:selected .iconify-icon, .fxt-activity-button-labeled.fxt-wf-fundsxml:selected { -fx-icon-color: -fxt-wf-fundsxml-rail; -fx-text-fill: -fxt-wf-fundsxml-rail; }
/* Help / Settings (neutral): brand-blue indicator, white icon and label (spec D3). */
.fxt-activity-button.fxt-wf-neutral:selected { -fx-background-color: -fxt-wf-workspace-rail, #3b3866; }
.fxt-activity-button.fxt-wf-neutral:selected .iconify-icon, .fxt-activity-button-labeled.fxt-wf-neutral:selected { -fx-icon-color: #ffffff; -fx-text-fill: #ffffff; }
```
Note: `-fx-icon-color` on a `.fxt-activity-button-labeled` selector is harmless (ignored on the button) and lets the label + icon rules share one block.

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ActivityBarWorkflowClassTest" --tests "org.fxt.freexmltoolkit.controls.theme.ShellCssStabilityTest" --tests "org.fxt.freexmltoolkit.controls.shell.ActivitySelectionModelTest"`
Expected: PASS.

- [ ] **Step 6: Visual check** (memory `prod-log-root-warn-verify-via-screenshot`, `xvfb-shared-display-collision`)

Run: `xvfb-run -a ./gradlew docScreenshots` (own display) and open `docs/img/unified-shell-validation.png`: the Validation rail item shows a green left bar + green icon on the navy rail; Explorer shows blue. If the indicator is invisible, check that layer [0] is not covered (insets `0, 0 0 0 3`).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/ActivityBar.java src/main/resources/css/unified-shell.css src/test/java/org/fxt/freexmltoolkit/controls/shell/ActivityBarWorkflowClassTest.java
git commit -m "feat(shell): workflow-coloured Activity Bar selection (indicator style B)"
```

---

### Task 5: Side panels — workflow scope class, coloured title, primary button, row hover

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/theme/WorkflowStyle.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java:807-815` (`showSidePanelFor`) and `:852-864` (`hintPanel`)
- Modify (one line each — add `"fxt-panel-title"` to the panel title label): `controls/shell/editor/ValidationPanel.java:93`, `TransformPanel.java:112`, `FopPanel.java:63`, `SchemaLibraryPanel.java:70`, `SignaturePanel.java:75`, `TypeLibraryPanel.java:62`, `ExplorerPanel.java:77`, `FavoritesActivityPanel.java:76`, `HelpPanel.java:32`, `FundsXmlPanel.java:55`, `SettingsPanel.java:154`, and `controls/shell/editor/search/SearchPanel.java` (its title label; find `fxt-side-panel-title` there)
- Modify: `src/main/resources/css/unified-shell.css` — append a new section after the `.fxt-action-row-menu` rules (~line 2965)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/theme/WorkflowStyleTest.java` (new)

**Interfaces:**
- Consumes: `Workflow.cssClass()`, `Workflow.CSS_CLASS_PREFIX` (Task 1); tokens (Task 2).
- Produces: `WorkflowStyle.apply(Node node, Workflow workflow)` — removes every `fxt-wf-*` class, adds the given one; `WorkflowStyle.clear(Node)`. Style class `fxt-panel-title` on the panel title label. CSS rules scoped by `.fxt-wf-<id>`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.layout.StackPane;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ApplicationExtension;

/** {@link WorkflowStyle} keeps exactly one workflow scope class on a node. */
@org.junit.jupiter.api.extension.ExtendWith(ApplicationExtension.class)
class WorkflowStyleTest {

    @Test
    void applyReplacesPreviousWorkflowClass() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                StackPane node = new StackPane();
                node.getStyleClass().add("fxt-side-panel-host");
                WorkflowStyle.apply(node, Workflow.VALIDATION);
                assertTrue(node.getStyleClass().contains("fxt-wf-validation"));
                WorkflowStyle.apply(node, Workflow.TRANSFORM);
                assertTrue(node.getStyleClass().contains("fxt-wf-transform"));
                assertFalse(node.getStyleClass().contains("fxt-wf-validation"));
                assertTrue(node.getStyleClass().contains("fxt-side-panel-host"), "other classes untouched");
                WorkflowStyle.clear(node);
                assertTrue(node.getStyleClass().stream().noneMatch(c -> c.startsWith("fxt-wf-")));
                WorkflowStyle.apply(node, null);
                assertTrue(node.getStyleClass().stream().noneMatch(c -> c.startsWith("fxt-wf-")));
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
```
(Use `import org.testfx.framework.junit5.ApplicationExtension;` — fix the import line above accordingly.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.WorkflowStyleTest"`
Expected: compilation FAILS (`WorkflowStyle` missing).

- [ ] **Step 3: Create `WorkflowStyle.java`**

```java
package org.fxt.freexmltoolkit.controls.theme;

import javafx.scene.Node;

/**
 * Attaches the workflow scope class ({@code fxt-wf-<id>}) to a node so the scoped rules in
 * {@code unified-shell.css} colour its title, primary action, row hover and badges. A node
 * carries at most one workflow class.
 */
public final class WorkflowStyle {

    private WorkflowStyle() {
    }

    /** Removes any previous {@code fxt-wf-*} class and adds {@code workflow}'s; {@code null} only clears. */
    public static void apply(Node node, Workflow workflow) {
        if (node == null) {
            return;
        }
        clear(node);
        if (workflow != null) {
            node.getStyleClass().add(workflow.cssClass());
        }
    }

    /** Removes every {@code fxt-wf-*} class from {@code node}. */
    public static void clear(Node node) {
        if (node != null) {
            node.getStyleClass().removeIf(c -> c.startsWith(Workflow.CSS_CLASS_PREFIX));
        }
    }
}
```

- [ ] **Step 4: Scope the side-panel host in `UnifiedShellView.showSidePanelFor`**

```java
    private void showSidePanelFor(Activity activity) {
        if (activity == Activity.SETTINGS) {
            openSettingsTab();
        }
        // Workflow colour scope for the panel's title, primary action and row hover (spec §2).
        org.fxt.freexmltoolkit.controls.theme.WorkflowStyle.apply(sidePanelHost,
                activity == null ? null : activity.workflow());
        sidePanelHost.getChildren().setAll(sidePanel(activity));
    }
```
In `hintPanel` add `"fxt-panel-title"`: `titleLabel.getStyleClass().addAll("fxt-side-panel-title", "fxt-panel-title");`.

- [ ] **Step 5: Tag the title label of every side panel**

In each file listed above, extend the title's style classes with `"fxt-panel-title"`, e.g. `ValidationPanel.java:93`:
```java
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title", "fxt-panel-title");
```
Only the **panel title** (the one word in caps at the top: VALIDATION, TRANSFORM, EXPLORER, …) gets the class — not section headers, not result views.

- [ ] **Step 6: Add the scoped CSS** (append after the `.fxt-action-row-menu` block in `unified-shell.css`)

Order matters: the plain-row hover rule must come **before** the primary-row rules of the same family (equal specificity, later wins).

```css
/* ===== Workflow colour scope (spec 2026-09-26 §2) ==========================================
   UnifiedShellView puts fxt-wf-<id> on the side-panel host; only the panel title, the ONE
   primary action and the row-hover tint take the workflow colour. Everything else in the panel
   stays neutral; icons on plain rows use their ACTION colour (bound in PanelActionList). */
.fxt-panel-title { -fx-text-fill: -fxt-text-secondary; }

.fxt-wf-workspace .fxt-panel-title { -fx-text-fill: -fxt-wf-workspace-fg; }
.fxt-wf-workspace .fxt-action-row:hover { -fx-background-color: -fxt-wf-workspace-bg; }
.fxt-wf-workspace .fxt-primary-button, .fxt-wf-workspace .fxt-action-row-primary { -fx-background-color: -fxt-wf-workspace-fill; }
.fxt-wf-workspace .fxt-primary-button:hover, .fxt-wf-workspace .fxt-action-row-primary:hover { -fx-background-color: derive(-fxt-wf-workspace-fill, -10%); }
.fxt-wf-workspace .fxt-primary-button:pressed, .fxt-wf-workspace .fxt-action-row-primary:pressed, .fxt-wf-workspace .fxt-action-row-primary:armed { -fx-background-color: derive(-fxt-wf-workspace-fill, -18%); }

.fxt-wf-validation .fxt-panel-title { -fx-text-fill: -fxt-wf-validation-fg; }
.fxt-wf-validation .fxt-action-row:hover { -fx-background-color: -fxt-wf-validation-bg; }
.fxt-wf-validation .fxt-primary-button, .fxt-wf-validation .fxt-action-row-primary { -fx-background-color: -fxt-wf-validation-fill; }
.fxt-wf-validation .fxt-primary-button:hover, .fxt-wf-validation .fxt-action-row-primary:hover { -fx-background-color: derive(-fxt-wf-validation-fill, -10%); }
.fxt-wf-validation .fxt-primary-button:pressed, .fxt-wf-validation .fxt-action-row-primary:pressed, .fxt-wf-validation .fxt-action-row-primary:armed { -fx-background-color: derive(-fxt-wf-validation-fill, -18%); }

.fxt-wf-transform .fxt-panel-title { -fx-text-fill: -fxt-wf-transform-fg; }
.fxt-wf-transform .fxt-action-row:hover { -fx-background-color: -fxt-wf-transform-bg; }
.fxt-wf-transform .fxt-primary-button, .fxt-wf-transform .fxt-action-row-primary { -fx-background-color: -fxt-wf-transform-fill; }
.fxt-wf-transform .fxt-primary-button:hover, .fxt-wf-transform .fxt-action-row-primary:hover { -fx-background-color: derive(-fxt-wf-transform-fill, -10%); }
.fxt-wf-transform .fxt-primary-button:pressed, .fxt-wf-transform .fxt-action-row-primary:pressed, .fxt-wf-transform .fxt-action-row-primary:armed { -fx-background-color: derive(-fxt-wf-transform-fill, -18%); }

.fxt-wf-schema .fxt-panel-title { -fx-text-fill: -fxt-wf-schema-fg; }
.fxt-wf-schema .fxt-action-row:hover { -fx-background-color: -fxt-wf-schema-bg; }
.fxt-wf-schema .fxt-primary-button, .fxt-wf-schema .fxt-action-row-primary { -fx-background-color: -fxt-wf-schema-fill; }
.fxt-wf-schema .fxt-primary-button:hover, .fxt-wf-schema .fxt-action-row-primary:hover { -fx-background-color: derive(-fxt-wf-schema-fill, -10%); }
.fxt-wf-schema .fxt-primary-button:pressed, .fxt-wf-schema .fxt-action-row-primary:pressed, .fxt-wf-schema .fxt-action-row-primary:armed { -fx-background-color: derive(-fxt-wf-schema-fill, -18%); }

.fxt-wf-pdf .fxt-panel-title { -fx-text-fill: -fxt-wf-pdf-fg; }
.fxt-wf-pdf .fxt-action-row:hover { -fx-background-color: -fxt-wf-pdf-bg; }
.fxt-wf-pdf .fxt-primary-button, .fxt-wf-pdf .fxt-action-row-primary { -fx-background-color: -fxt-wf-pdf-fill; }
.fxt-wf-pdf .fxt-primary-button:hover, .fxt-wf-pdf .fxt-action-row-primary:hover { -fx-background-color: derive(-fxt-wf-pdf-fill, -10%); }
.fxt-wf-pdf .fxt-primary-button:pressed, .fxt-wf-pdf .fxt-action-row-primary:pressed, .fxt-wf-pdf .fxt-action-row-primary:armed { -fx-background-color: derive(-fxt-wf-pdf-fill, -18%); }

.fxt-wf-signature .fxt-panel-title { -fx-text-fill: -fxt-wf-signature-fg; }
.fxt-wf-signature .fxt-action-row:hover { -fx-background-color: -fxt-wf-signature-bg; }
.fxt-wf-signature .fxt-primary-button, .fxt-wf-signature .fxt-action-row-primary { -fx-background-color: -fxt-wf-signature-fill; }
.fxt-wf-signature .fxt-primary-button:hover, .fxt-wf-signature .fxt-action-row-primary:hover { -fx-background-color: derive(-fxt-wf-signature-fill, -10%); }
.fxt-wf-signature .fxt-primary-button:pressed, .fxt-wf-signature .fxt-action-row-primary:pressed, .fxt-wf-signature .fxt-action-row-primary:armed { -fx-background-color: derive(-fxt-wf-signature-fill, -18%); }

.fxt-wf-fundsxml .fxt-panel-title { -fx-text-fill: -fxt-wf-fundsxml-fg; }
.fxt-wf-fundsxml .fxt-action-row:hover { -fx-background-color: -fxt-wf-fundsxml-bg; }
.fxt-wf-fundsxml .fxt-primary-button, .fxt-wf-fundsxml .fxt-action-row-primary { -fx-background-color: -fxt-wf-fundsxml-fill; }
.fxt-wf-fundsxml .fxt-primary-button:hover, .fxt-wf-fundsxml .fxt-action-row-primary:hover { -fx-background-color: derive(-fxt-wf-fundsxml-fill, -10%); }
.fxt-wf-fundsxml .fxt-primary-button:pressed, .fxt-wf-fundsxml .fxt-action-row-primary:pressed, .fxt-wf-fundsxml .fxt-action-row-primary:armed { -fx-background-color: derive(-fxt-wf-fundsxml-fill, -18%); }

/* Help / Settings: neutral title, brand-primary buttons (unchanged look). */
.fxt-wf-neutral .fxt-panel-title { -fx-text-fill: -fxt-text-secondary; }
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.WorkflowStyleTest" --tests "org.fxt.freexmltoolkit.controls.theme.ShellCssStabilityTest" --tests "org.fxt.freexmltoolkit.controls.shell.UnifiedShellViewTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/theme/WorkflowStyle.java src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/ src/main/resources/css/unified-shell.css src/test/java/org/fxt/freexmltoolkit/controls/theme/WorkflowStyleTest.java
git commit -m "feat(shell): workflow-coloured side-panel title, primary action and row hover"
```

---

### Task 6: `PanelAction.color` — action-coloured row icons

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/PanelAction.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/PanelActionList.java:44-62, 69-81, 125-140`
- Modify: `src/main/resources/css/unified-shell.css` — the `.fxt-action-row .iconify-icon` and `.fxt-action-row:hover .iconify-icon` rules (~lines 2912–2922)
- Modify (assign colours): `ValidationPanel`, `TransformPanel`, `TypeLibraryPanel`, `ExplorerPanel`, `FavoritesActivityPanel`, `FopPanel`, `HelpPanel`, `FundsXmlPanel`, `SignaturePanel` (all in `controls/shell/editor/`)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/PanelActionListTest.java`

**Interfaces:**
- Consumes: `ActionColor`, `SemanticIcon.bind(IconifyIcon, ActionColor)` (Tasks 1, 3).
- Produces: `PanelAction.color()` (default `ActionColor.NEUTRAL`), wither `PanelAction color(ActionColor)`; `PanelActionList` binds every non-primary row icon to its action colour; primary rows keep the CSS white icon.

- [ ] **Step 1: Write the failing test** (add to `PanelActionListTest`; add `import org.fxt.freexmltoolkit.controls.theme.ActionColor;` and `import org.fxt.freexmltoolkit.controls.theme.DesignTokens;`)

In `start(...)` change the first action to `.color(ActionColor.CREATE)` and the second to `.color(ActionColor.DELETE)`. Then:
```java
    @Test
    void rowIconsAreBoundToTheirActionColourAndPrimaryStaysWhite() {
        WaitForAsyncUtils.waitForFxEvents();
        IconifyIcon create = (IconifyIcon) list.button("act-one").getGraphic();
        IconifyIcon delete = (IconifyIcon) list.button("act-two").getGraphic();
        IconifyIcon primary = (IconifyIcon) list.button("act-primary").getGraphic();
        assertTrue(create.iconColorProperty().isBound(), "plain row icon is bound (CSS-proof)");
        assertEquals(ActionColor.CREATE.token().color(DesignTokens.Theme.LIGHT), create.getIconColor());
        assertEquals(ActionColor.DELETE.token().color(DesignTokens.Theme.LIGHT), delete.getIconColor());
        assertFalse(primary.iconColorProperty().isBound(), "primary row icon is left to CSS (on-primary)");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.PanelActionListTest"`
Expected: compilation FAILS (`color(...)` missing).

- [ ] **Step 3: Extend `PanelAction`**

Add the component `ActionColor color` (last), default `NEUTRAL`, and the wither. Full record header and helpers:
```java
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
...
 * @param color        the action-colour role that tints the row icon (default {@link ActionColor#NEUTRAL})
 */
record PanelAction(String id, String iconLiteral, String label, String tooltip, Runnable onAction,
                   ObservableValue<Boolean> disabledWhen, ObservableValue<Boolean> visibleWhen,
                   boolean primary, ActionColor color) {

    PanelAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(iconLiteral, "iconLiteral");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(onAction, "onAction");
        color = color == null ? ActionColor.NEUTRAL : color;
    }

    static PanelAction of(String id, String iconLiteral, String label, Runnable onAction) {
        return new PanelAction(id, iconLiteral, label, null, onAction, null, null, false, ActionColor.NEUTRAL);
    }

    PanelAction tooltip(String text) {
        return new PanelAction(id, iconLiteral, label, text, onAction, disabledWhen, visibleWhen, primary, color);
    }

    PanelAction disabledWhen(ObservableValue<Boolean> binding) {
        return new PanelAction(id, iconLiteral, label, tooltip, onAction, binding, visibleWhen, primary, color);
    }

    PanelAction visibleWhen(ObservableValue<Boolean> binding) {
        return new PanelAction(id, iconLiteral, label, tooltip, onAction, disabledWhen, binding, primary, color);
    }

    PanelAction asPrimary() {
        return new PanelAction(id, iconLiteral, label, tooltip, onAction, disabledWhen, visibleWhen, true, color);
    }

    /** Sets the action-colour role of the row icon (spec §3); ignored on the primary row (white icon). */
    PanelAction color(ActionColor role) {
        return new PanelAction(id, iconLiteral, label, tooltip, onAction, disabledWhen, visibleWhen, primary, role);
    }
```

- [ ] **Step 4: Bind the icon in `PanelActionList`**

Change `row(...)` to take the colour and bind for non-primary rows; `addMenu` binds NEUTRAL:
```java
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
...
    Button add(PanelAction action) {
        Button button = row(action.label(), action.iconLiteral(), action.onAction(),
                action.primary() ? null : action.color());
        ...
    }

    MenuButton addMenu(MenuButton menu, String id, String iconLiteral, String label) {
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(ICON_SIZE);
        SemanticIcon.bind(icon, ActionColor.NEUTRAL);
        menu.setGraphic(icon);
        ...
    }

    static Button inlineRow(String label, String iconLiteral, Runnable onAction) {
        Button button = row(label, iconLiteral, onAction, ActionColor.NEUTRAL);
        ...
    }

    /**
     * @param color the action-colour role bound to the icon, or {@code null} to leave the colour to CSS
     *              (primary rows: white icon on the workflow fill)
     */
    private static Button row(String label, String iconLiteral, Runnable onAction, ActionColor color) {
        Button button = new Button(label);
        if (iconLiteral != null) {
            IconifyIcon icon = new IconifyIcon(iconLiteral);
            icon.setIconSize(ICON_SIZE);
            if (color != null) {
                // Bound, not set: the .fxt-action-row .iconify-icon CSS rule would otherwise repaint it.
                SemanticIcon.bind(icon, color);
            }
            button.setGraphic(icon);
        }
        ...
    }
```

- [ ] **Step 5: CSS — rows no longer flip the icon to primary on hover**

In `unified-shell.css` replace
```css
.fxt-action-row .iconify-icon {
    -fx-icon-color: -fxt-text-secondary;
}
...
.fxt-action-row:hover .iconify-icon {
    -fx-icon-color: -fxt-primary;
}
```
with
```css
/* Row icons carry their ACTION colour (bound in PanelActionList; spec §3). The fallback below
   only applies to icons nobody bound; hover does not recolour the icon any more. */
.fxt-action-row .iconify-icon {
    -fx-icon-color: -fxt-text-secondary;
}
```
(delete the `:hover .iconify-icon` rule). Keep the `.fxt-action-row-primary .iconify-icon` on-primary rules.

- [ ] **Step 6: Assign roles at every call site** (append `.color(ActionColor.X)` to the `PanelAction.of(...)` chain; add `import org.fxt.freexmltoolkit.controls.theme.ActionColor;` per file)

| File | id | role |
|---|---|---|
| ValidationPanel | validation-tool-templates, -tester, -builder, validation-schematron-report, validation-tool-documentation, validation-tool-batch-report | NAVIGATE (opens a tool/report) |
| ValidationPanel | validation-tool-check, validation-tool-fundsxml | CREATE (runs a check) |
| ValidationPanel | validation-export-problems | TOOL |
| TransformPanel | transform-tool-debug, transform-tool-batch, transform-xpath-run, transform-xpath-save, transform-xquery-run | TOOL |
| TransformPanel | transform-tool-stats | NAVIGATE |
| TypeLibraryPanel | schema-tool-generate, -generate-batch, -sample, -sample-advanced, -documentation | CREATE |
| TypeLibraryPanel | schema-tool-flatten | TOOL |
| TypeLibraryPanel | schema-tool-analysis | NAVIGATE |
| ExplorerPanel | explorer-open-file, explorer-transform | TOOL |
| ExplorerPanel | explorer-validate | CREATE |
| ExplorerPanel | explorer-clear-recent | DELETE |
| FavoritesActivityPanel | favorites-add-current | CREATE |
| FavoritesActivityPanel | favorites-manage | NEUTRAL (default, no change) |
| FopPanel | fop-preview, fop-open | NAVIGATE |
| HelpPanel | help-github, help-report-problem, help-docs, help-fundsxml-site, help-schema-docs | NAVIGATE |
| HelpPanel | help-check-updates, help-fundsxml-updates | TOOL |
| HelpPanel | help-sponsor, help-about, help-shortcuts | NEUTRAL (default) |
| FundsXmlPanel | fundsxml-download | TOOL |
| FundsXmlPanel | fundsxml-validate, fundsxml-generate-docs | CREATE |
| FundsXmlPanel | fundsxml-open-schema, -examples-folder, -schema-folder, -schematron-folder, -online-docs | NAVIGATE |
| SignaturePanel | sig-validate-details | CREATE |

Rule of thumb for future rows: opens something = NAVIGATE, runs a check/validation or generates = CREATE, runs a transform/export/download = TOOL, clears = DELETE.

- [ ] **Step 7: Run the tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.PanelActionListTest" --tests "org.fxt.freexmltoolkit.controls.theme.ShellCssStabilityTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/ src/main/resources/css/unified-shell.css src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/PanelActionListTest.java
git commit -m "feat(shell): action-coloured panel action rows (PanelAction.color)"
```

---

### Task 7: Editor toolbar — group tints per owning workflow, green Validate

**Files:**
- Modify: `src/main/resources/pages/shell.fxml:97-101, 128-158, 160-215`
- Modify: `src/main/resources/css/unified-shell.css:553-558` (tint rules) and `:596-608` (accent block)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/ToolbarWorkflowClassesTest.java` (new, file-based)

**Interfaces:**
- Consumes: tokens `-fxt-wf-transform-accent`, `-fxt-wf-schema-accent`, `-fxt-wf-validation-fill`, `-fxt-action-create` (Task 2).
- Produces: style classes `fxt-tool-transform`, `fxt-tool-schema`, `fxt-tool-create`; Validate filled green.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** shell.fxml toolbar buttons carry the workflow / action colour classes of the spec (§2, toolbar row). */
class ToolbarWorkflowClassesTest {

    private static String classesOf(String fxml, String id) {
        Matcher m = Pattern.compile("id=\"" + Pattern.quote(id) + "\"[^>]*?styleClass=\"([^\"]+)\"", Pattern.DOTALL)
                .matcher(fxml);
        assertTrue(m.find(), () -> "button " + id + " not found");
        return m.group(1);
    }

    @Test
    void toolbarButtonsCarryWorkflowClasses() throws IOException {
        String fxml = Files.readString(Path.of("src/main/resources/pages/shell.fxml"));
        assertTrue(classesOf(fxml, "action-new").contains("fxt-tool-create"), "New = CREATE green");
        assertTrue(classesOf(fxml, "action-open").contains("fxt-tool-primary"), "Open = workspace blue (was warning)");
        assertTrue(classesOf(fxml, "action-save").contains("fxt-tool-primary"));
        assertTrue(classesOf(fxml, "action-format").contains("fxt-tool-neutral"), "Format is neutral (was info)");
        assertTrue(classesOf(fxml, "action-insert-template").contains("fxt-tool-neutral"));
        assertTrue(classesOf(fxml, "action-compare").contains("fxt-tool-neutral"));
        assertTrue(classesOf(fxml, "action-spreadsheet").contains("fxt-tool-neutral"));
        assertTrue(classesOf(fxml, "doc-action-transform").contains("fxt-tool-transform"));
        assertTrue(classesOf(fxml, "doc-action-run").contains("fxt-tool-transform"));
        assertTrue(classesOf(fxml, "action-set-schema").contains("fxt-tool-schema"));
        assertTrue(classesOf(fxml, "doc-action-validate").contains("fxt-tool-accent"));
    }

    @Test
    void cssDefinesTheNewToolbarClasses() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/css/unified-shell.css"));
        assertTrue(css.contains(".fxt-tool-button.fxt-tool-create .iconify-icon"));
        assertTrue(css.contains(".fxt-tool-button.fxt-tool-transform .iconify-icon"));
        assertTrue(css.contains(".fxt-tool-button.fxt-tool-schema .iconify-icon"));
        assertTrue(css.contains("-fxt-wf-validation-fill"), "Validate is filled in the validation colour");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ToolbarWorkflowClassesTest"`
Expected: FAIL (`New = CREATE green`).

- [ ] **Step 3: Update `shell.fxml` style classes**

| id | old | new |
|---|---|---|
| action-new | `fxt-tool-success` | `fxt-tool-create` |
| action-open | `fxt-tool-warning` | `fxt-tool-primary` |
| action-format | `fxt-tool-info` | `fxt-tool-neutral` |
| action-insert-template | `fxt-tool-purple` | `fxt-tool-neutral` |
| action-compare | `fxt-tool-indigo` | `fxt-tool-neutral` |
| action-spreadsheet | `fxt-tool-success` | `fxt-tool-neutral` |
| doc-action-transform | `fxt-tool-info` | `fxt-tool-transform` |
| doc-action-run | `fxt-tool-success` | `fxt-tool-transform` |
| action-set-schema | `fxt-tool-purple` | `fxt-tool-schema` |
| doc-action-validate | `fxt-tool-primary, fxt-tool-accent` | unchanged (accent rule changes colour in CSS) |

Update the FXML comment above the buttons: "Icon tints follow the owning workflow (spec 2026-09-26 §2): file group = workspace blue, New = CREATE, editing tools neutral, Transform/Run = transform, Schema = schema, Validate filled in validation green."

- [ ] **Step 4: Update the CSS tint and accent rules**

Replace lines 553–558 with:
```css
/* Icon tints per owning workflow (spec 2026-09-26 §2): file group = workspace, New = CREATE,
   Transform/Run = transform, Schema = schema; editing tools stay neutral. Labels stay near-black. */
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-primary .iconify-icon   { -fx-icon-color: -fxt-wf-workspace-accent; }
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-create .iconify-icon    { -fx-icon-color: -fxt-action-create; }
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-transform .iconify-icon { -fx-icon-color: -fxt-wf-transform-accent; }
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-schema .iconify-icon    { -fx-icon-color: -fxt-wf-schema-accent; }
/* Legacy semantic classes still used outside the shell toolbar row. */
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-success .iconify-icon { -fx-icon-color: -fxt-success; }
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-info .iconify-icon    { -fx-icon-color: -fxt-info; }
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-warning .iconify-icon { -fx-icon-color: -fxt-warning; }
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-purple .iconify-icon  { -fx-icon-color: -fxt-purple; }
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-indigo .iconify-icon  { -fx-icon-color: -fxt-indigo; }
```
Replace the accent block (596–608) with:
```css
/* ----- Filled Validate button: the one filled toolbar action points at the Validation workflow (D4) ----- */
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-accent {
    -fx-background-color: -fxt-wf-validation-fill;
    -fx-border-color: -fxt-wf-validation-fill;
    -fx-text-fill: -fxt-on-primary;
}
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-accent .iconify-icon {
    -fx-icon-color: -fxt-on-primary;
}
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-accent:hover {
    -fx-background-color: derive(-fxt-wf-validation-fill, -8%);
}
.fxt-editor-toolbar .fxt-tool-button.fxt-tool-accent:hover .iconify-icon {
    -fx-icon-color: -fxt-on-primary;
}
```
Also fix the stale comment at lines 530–531 ("intentionally NOT colored") to "tinted per owning workflow, see below".

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.ToolbarWorkflowClassesTest" --tests "org.fxt.freexmltoolkit.controls.shell.ToolbarDisplayTest" --tests "org.fxt.freexmltoolkit.controls.theme.ShellCssStabilityTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/pages/shell.fxml src/main/resources/css/unified-shell.css src/test/java/org/fxt/freexmltoolkit/controls/shell/ToolbarWorkflowClassesTest.java
git commit -m "feat(shell): editor toolbar tints per workflow, Validate filled green"
```

---

### Task 8: Context menus on the unified action-colour rule; remove dead colour code

**Files:**
- Modify: `controls/v2/editor/menu/XsdContextMenuFactory.java` (icons at lines ~132, 150–197, 202–233, 251–266, 280–301, 316–320, 334–372, 382, 395–403, 416–421, 437, 450–464, 982–999, 1138)
- Modify: `controls/shared/utilities/XmlContextMenuManager.java:99-217`, `controls/v2/editor/managers/ContextMenuManagerV2.java:99-104`, `controls/jsoneditor/editor/JsonContextMenuManager.java:100-159`, `controls/v2/xmleditor/view/XmlGridContextMenu.java:108-224`, `controls/jsoneditor/grid/JsonGridContextMenu.java:120-237`
- Modify: `controls/shell/schema/NodeContextMenu.java:28-72`, `controls/shell/editor/FavoritesActivityPanel.java:116-140, 221-230`, `controls/shell/editor/FavoritesManagerView.java:146-152`, `controls/shell/editor/SchemaLibraryPanel.java:502-518`, `controls/shell/editor/TransformPanel.java:555-561, 1074-1086`, `controls/shell/editor/TypeLibraryPanel.java:181, 517`
- Delete: `src/main/java/org/fxt/freexmltoolkit/util/ContextMenuFactory.java`, `src/main/resources/css/context-menu-theme.css` (unused: grep shows no loader)
- Modify: `src/test/java/org/fxt/freexmltoolkit/controls/theme/SemanticColorGuardTest.java:31` (drop the deleted file)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/theme/ActionColorMenuGuardTest.java` (new)

**Interfaces:**
- Consumes: `ActionColor`, `SemanticIcon.paint(IconifyIcon, ActionColor)` (Tasks 1, 3).
- Produces: every menu icon in the listed files is painted through `ActionColor`; the guard test enforces that these files contain no `SemanticIcon.paint(..., DesignTokens.ColorToken.X)` call.

- [ ] **Step 1: Write the failing guard test**

```java
package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Ratchet for the unified action-colour rule (spec §3): menu classes paint icons through
 * {@link ActionColor} only, never through a raw semantic ColorToken, so Cut/Copy/Paste/… cannot
 * drift apart between editors again.
 */
class ActionColorMenuGuardTest {

    private static final List<String> MENU_FILES = List.of(
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/menu/XsdContextMenuFactory.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shared/utilities/XmlContextMenuManager.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/ContextMenuManagerV2.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/jsoneditor/editor/JsonContextMenuManager.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlGridContextMenu.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/jsoneditor/grid/JsonGridContextMenu.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/schema/NodeContextMenu.java");

    private static final Pattern RAW_TOKEN = Pattern.compile("ColorToken\\.(SUCCESS|DANGER|WARNING|INFO|PRIMARY|NEUTRAL|PURPLE|TEAL|INDIGO|ACCENT)\\b");

    @Test
    void menuIconsUseActionColorOnly() throws IOException {
        for (String file : MENU_FILES) {
            String src = Files.readString(Path.of(file));
            assertFalse(RAW_TOKEN.matcher(src).find(), () -> file + " paints a menu icon with a raw ColorToken; use ActionColor");
            assertTrue(src.contains("ActionColor."), () -> file + " does not use ActionColor at all");
        }
        assertFalse(Files.exists(Path.of("src/main/java/org/fxt/freexmltoolkit/util/ContextMenuFactory.java")),
                "dead util/ContextMenuFactory must be gone");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.ActionColorMenuGuardTest"`
Expected: FAIL (raw tokens present).

- [ ] **Step 3: Migrate each file** — change the icon helper signature to take `ActionColor` and map every item:

Helper pattern (each file already has a helper like `createColoredIcon(String, DesignTokens.ColorToken)` / `icon(String, ColorToken)`): change the parameter type to `ActionColor` and call `SemanticIcon.paint(icon, role)`. In `NodeContextMenu` add a parameter: `item(String text, String icon, ActionColor role, Supplier<XsdNode>, Consumer<XsdNode>)` and paint with `SemanticIcon.paint(graphic, role)`.

Mapping (verb → role; identical in every file):

| Items | Role |
|---|---|
| Add ▸, Add Element/Attribute/Sequence/Choice/All/Comment/Root Element, Add Child/Text/Sibling/Property/Array Item, Paste (all variants), Duplicate, Validate | `CREATE` |
| Delete, Remove, Clear, Cut | `DELETE` |
| Rename, Edit Value, Edit Comment, Comment lines, Overwrite | `MODIFY` |
| Go to Definition, Edit Referenced Type, Reveal in Tree, Open Type Editor, Find Usage, Find/Replace, Open, Open in editor, Load | `NAVIGATE` |
| Change Type (+ submenu items), Change compositor ▸, Edit Cardinality, Toggle Required/Optional, Move ▸/Up/Down, Move to folder | `STRUCTURE` |
| Format, Minify, Sort ▸/Ascending/Descending | `TOOL` |
| Copy, Copy XPath, Copy Node, Copy Cell Content, Copy JSONPath, Copy namespace, Select All, Expand All, Collapse All, Undo, Redo | `NEUTRAL` |

Items that had **no icon** in `XsdContextMenuFactory` (Add Root Element ~132/395, ComplexType/SimpleType Rename/Delete ~297–318, Group items ~416–421) get one: `bi-plus-circle`/CREATE, `bi-pencil`/MODIFY, `bi-trash`/DELETE.

The shell menus (`FavoritesActivityPanel`, `FavoritesManagerView`, `SchemaLibraryPanel`, `TransformPanel` saved queries, `TypeLibraryPanel`) build `new IconifyIcon(...)` graphics: wrap them as `SemanticIcon.paint(new IconifyIcon("bi-trash"), ActionColor.DELETE)` with the same table.

- [ ] **Step 4: Delete the dead code**

```bash
git rm src/main/java/org/fxt/freexmltoolkit/util/ContextMenuFactory.java src/main/resources/css/context-menu-theme.css
```
Remove the `ContextMenuFactory.java` line from `SemanticColorGuardTest.MIGRATED_FILES`. Remove the `contextMenu.iconClasses` entry (`.menu-icon-*`, lines ~998–1017) from `STYLE_GUIDE.jsonc` (Task 10 rewrites that section anyway; delete the block now so the build has no dangling reference).

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.ActionColorMenuGuardTest" --tests "org.fxt.freexmltoolkit.controls.theme.SemanticColorGuardTest" --tests "org.fxt.freexmltoolkit.controls.icons.IconifyIconCoverageTest"` then `./gradlew compileJava compileTestJava`
Expected: PASS, compiles (no remaining reference to `ContextMenuFactory`).

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/org/fxt/freexmltoolkit/controls src/main/java/org/fxt/freexmltoolkit/util src/main/resources/css src/test/java/org/fxt/freexmltoolkit/controls/theme STYLE_GUIDE.jsonc
git commit -m "refactor(menus): unified action colours via ActionColor; drop dead ContextMenuFactory"
```

---

### Task 9: Welcome page tool cards + category colours; status-bar last-run badge

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorWelcomePane.java:195-206` (`categoryColor`) and `:468-491` (`toolCard`)
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java:1692-1700` (last-run badge)
- Modify: `src/main/resources/css/unified-shell.css` — `.fxt-card-icon` (~1306), `.fxt-tool-card .iconify-icon` (~1430), status bar (~360)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/WelcomeAndBadgeCssTest.java` (new)

**Interfaces:**
- Consumes: `Workflow.forCategory/forOperationType/cssClass/accent()`, `SemanticStyle.hex`, `WorkflowStyle.apply`, `SemanticIcon.bind`.
- Produces: `.fxt-card-icon.fxt-wf-<id>` chips; `.fxt-status-badge.fxt-wf-<id>` chip on `statusLastRun` with a semantic result icon.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.junit.jupiter.api.Test;

/** Welcome tool-card chips and the status-bar badge have one CSS rule per workflow family. */
class WelcomeAndBadgeCssTest {

    @Test
    void everyWorkflowHasChipAndBadgeRules() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/css/unified-shell.css"));
        for (Workflow wf : Workflow.values()) {
            assertTrue(css.contains(".fxt-card-icon." + wf.cssClass()), wf + " card chip rule");
            assertTrue(css.contains(".fxt-status-badge." + wf.cssClass()), wf + " badge rule");
        }
        assertFalse(Files.readString(Path.of("src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorWelcomePane.java"))
                .contains("#e64980"), "hard-coded category colours are gone");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.WelcomeAndBadgeCssTest"`
Expected: FAIL.

- [ ] **Step 3: `EditorWelcomePane` — categories and tool cards**

Replace `categoryColor`:
```java
    /** Workflow accent per feature category (spec §2, Welcome); theme-aware hex for inline styles. */
    private static String categoryColor(String category) {
        return org.fxt.freexmltoolkit.controls.theme.SemanticStyle.hex(
                org.fxt.freexmltoolkit.controls.theme.Workflow.forCategory(category).accent());
    }
```
In `toolCard(...)` after `tile.getStyleClass().add("fxt-card-icon");` add:
```java
        // Workflow colour chip: bg tint + accent icon (spec §2, Welcome tool cards).
        Activity.fromId(activityKey).ifPresent(a ->
                org.fxt.freexmltoolkit.controls.theme.WorkflowStyle.apply(tile, a.workflow()));
```
(`EditorWelcomePane` is in `controls.shell.editor`; import `org.fxt.freexmltoolkit.controls.shell.Activity`.)

- [ ] **Step 4: CSS for chips** (replace the `.fxt-card-icon` block and add per-family rules right after it)

```css
.fxt-card-icon {
    -fx-background-color: rgba(59, 91, 219, 0.10);
    -fx-background-radius: 8;
    -fx-min-width: 36; -fx-min-height: 36;
    -fx-max-width: 36; -fx-max-height: 36;
    -fx-icon-color: -fxt-primary;
}
/* Welcome tool cards: chip = workflow bg, icon = workflow accent (spec §2). Outranks the
   .fxt-tool-card .iconify-icon muted rule by specificity. */
.fxt-card-icon.fxt-wf-workspace  { -fx-background-color: -fxt-wf-workspace-bg; }
.fxt-card-icon.fxt-wf-validation { -fx-background-color: -fxt-wf-validation-bg; }
.fxt-card-icon.fxt-wf-transform  { -fx-background-color: -fxt-wf-transform-bg; }
.fxt-card-icon.fxt-wf-schema     { -fx-background-color: -fxt-wf-schema-bg; }
.fxt-card-icon.fxt-wf-pdf        { -fx-background-color: -fxt-wf-pdf-bg; }
.fxt-card-icon.fxt-wf-signature  { -fx-background-color: -fxt-wf-signature-bg; }
.fxt-card-icon.fxt-wf-fundsxml   { -fx-background-color: -fxt-wf-fundsxml-bg; }
.fxt-card-icon.fxt-wf-neutral    { -fx-background-color: -fxt-wf-neutral-bg; }
.fxt-tool-card .fxt-card-icon.fxt-wf-workspace .iconify-icon  { -fx-icon-color: -fxt-wf-workspace-accent; }
.fxt-tool-card .fxt-card-icon.fxt-wf-validation .iconify-icon { -fx-icon-color: -fxt-wf-validation-accent; }
.fxt-tool-card .fxt-card-icon.fxt-wf-transform .iconify-icon  { -fx-icon-color: -fxt-wf-transform-accent; }
.fxt-tool-card .fxt-card-icon.fxt-wf-schema .iconify-icon     { -fx-icon-color: -fxt-wf-schema-accent; }
.fxt-tool-card .fxt-card-icon.fxt-wf-pdf .iconify-icon        { -fx-icon-color: -fxt-wf-pdf-accent; }
.fxt-tool-card .fxt-card-icon.fxt-wf-signature .iconify-icon  { -fx-icon-color: -fxt-wf-signature-accent; }
.fxt-tool-card .fxt-card-icon.fxt-wf-fundsxml .iconify-icon   { -fx-icon-color: -fxt-wf-fundsxml-accent; }
.fxt-tool-card .fxt-card-icon.fxt-wf-neutral .iconify-icon    { -fx-icon-color: -fxt-wf-neutral-accent; }
```

- [ ] **Step 5: Status-bar badge in `UnifiedShellView`**

Replace the listener at lines 1698–1700:
```java
        statusLastRun.getStyleClass().add("fxt-status-badge");
        org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().addListener(
                stats -> javafx.application.Platform.runLater(() -> showLastRunBadge(stats)));
```
Add the method:
```java
    /**
     * Status-bar "last run" badge (spec §2): chip in the colours of the workflow that ran, result
     * icon in the semantic colour (success/danger wins over the workflow colour).
     */
    private void showLastRunBadge(org.fxt.freexmltoolkit.service.ExecutionStats stats) {
        var workflow = org.fxt.freexmltoolkit.controls.theme.Workflow.forOperationType(
                stats.type() == null ? null : stats.type().name());
        org.fxt.freexmltoolkit.controls.theme.WorkflowStyle.apply(statusLastRun, workflow);
        IconifyIcon result = new IconifyIcon(stats.success() ? "bi-check-circle" : "bi-x-circle");
        result.setIconSize(11);
        org.fxt.freexmltoolkit.controls.theme.SemanticIcon.bind(result,
                stats.success() ? DesignTokens.ColorToken.SUCCESS : DesignTokens.ColorToken.DANGER);
        statusLastRun.setGraphic(result);
        statusLastRun.setText(stats.shortLabel());
    }
```
(Imports: `org.fxt.freexmltoolkit.controls.icons.IconifyIcon`, `org.fxt.freexmltoolkit.controls.theme.DesignTokens` — check which are already imported.) The line that clears the label (`statusLastRun.setText("")`, ~891) stays; add `statusLastRun.setGraphic(null);` next to it.

- [ ] **Step 6: Badge CSS** (append after the `.fxt-status-schema-error` rules, ~line 375)

```css
/* Last-run badge: chip in the workflow's bg/border/fg; the result icon is bound to a semantic
   colour in UnifiedShellView.showLastRunBadge (spec §2). Padding lives only on the base rule. */
.fxt-status-badge {
    -fx-padding: 1 8 1 8;
    -fx-background-radius: 10;
    -fx-border-radius: 10;
    -fx-border-width: 1;
    -fx-border-color: transparent;
    -fx-font-size: 10.5px;
    -fx-font-weight: 600;
}
.fxt-status-badge.fxt-wf-workspace  { -fx-background-color: -fxt-wf-workspace-bg;  -fx-border-color: -fxt-wf-workspace-border;  -fx-text-fill: -fxt-wf-workspace-fg; }
.fxt-status-badge.fxt-wf-validation { -fx-background-color: -fxt-wf-validation-bg; -fx-border-color: -fxt-wf-validation-border; -fx-text-fill: -fxt-wf-validation-fg; }
.fxt-status-badge.fxt-wf-transform  { -fx-background-color: -fxt-wf-transform-bg;  -fx-border-color: -fxt-wf-transform-border;  -fx-text-fill: -fxt-wf-transform-fg; }
.fxt-status-badge.fxt-wf-schema     { -fx-background-color: -fxt-wf-schema-bg;     -fx-border-color: -fxt-wf-schema-border;     -fx-text-fill: -fxt-wf-schema-fg; }
.fxt-status-badge.fxt-wf-pdf        { -fx-background-color: -fxt-wf-pdf-bg;        -fx-border-color: -fxt-wf-pdf-border;        -fx-text-fill: -fxt-wf-pdf-fg; }
.fxt-status-badge.fxt-wf-signature  { -fx-background-color: -fxt-wf-signature-bg;  -fx-border-color: -fxt-wf-signature-border;  -fx-text-fill: -fxt-wf-signature-fg; }
.fxt-status-badge.fxt-wf-fundsxml   { -fx-background-color: -fxt-wf-fundsxml-bg;   -fx-border-color: -fxt-wf-fundsxml-border;   -fx-text-fill: -fxt-wf-fundsxml-fg; }
.fxt-status-badge.fxt-wf-neutral    { -fx-background-color: -fxt-wf-neutral-bg;    -fx-border-color: -fxt-wf-neutral-border;    -fx-text-fill: -fxt-wf-neutral-fg; }
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.WelcomeAndBadgeCssTest" --tests "org.fxt.freexmltoolkit.controls.theme.ShellCssStabilityTest" --tests "org.fxt.freexmltoolkit.controls.theme.SemanticColorGuardTest" --tests "org.fxt.freexmltoolkit.controls.shell.MemoryStatusTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorWelcomePane.java src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java src/main/resources/css/unified-shell.css src/test/java/org/fxt/freexmltoolkit/controls/shell/WelcomeAndBadgeCssTest.java
git commit -m "feat(shell): workflow-coloured welcome chips and last-run status badge"
```

---

### Task 10: Docs, style guide, screenshots, full test run, push

**Files:**
- Modify: `STYLE_GUIDE.jsonc` (colour section 34–68, `iconColors` 834–858, `panelActions` 920–945, rules ~1228–1257)
- Modify: `docs/unified-shell.md` (Activity Bar + side panels + toolbar paragraphs), `CLAUDE.md` ("Semantic colors" line under Icon Usage), `.claude/rules/architecture.md` (CSS section)
- Regenerate: `docs/img/unified-shell-*.png`
- Memory: `~/.claude/projects/-home-karl-webdav-FreeXmlToolkit/memory/project-workflow-colours-branding.md` (status), `semantic-colors-consolidation.md` (point to ActionColor)

- [ ] **Step 1: Style guide** — in `STYLE_GUIDE.jsonc` add a `"workflowColors"` object (families → `-fxt-wf-<id>-*` tokens, the six restraint rules from the spec §2) and replace the `iconColors.semantic` guidance with the `ActionColor` table (spec §3). Delete the legacy `tabSpecific` colours (xslt #e27429 etc.) and the `colors.tabCategories` block. Update the rules: "Use `ActionColor` for menu/action icons; use `Workflow` only on chrome that names the workflow; never both on one surface."

- [ ] **Step 2: User docs** — in `docs/unified-shell.md` add a short "Colours" subsection: the rail indicator colour follows the activity, the panel title and its primary button share that colour, menu icons follow the action (green = adds, red = removes, orange = edits, blue = tools, cyan = navigates, violet = structure). Update `CLAUDE.md` Icon-Usage bullet 5 to reference `ActionColor`/`Workflow` and `.claude/rules/architecture.md` CSS bullet to mention `-fxt-wf-*`.

- [ ] **Step 3: Regenerate screenshots**

Run: `xvfb-run -a ./gradlew docScreenshots` then inspect `docs/img/unified-shell-overview.png`, `unified-shell-validation.png`, `unified-shell-transform.png`, `unified-shell-schema-graphic.png` (rail indicator, coloured title, coloured Run button, green Validate, tinted toolbar icons). Fix any CSS specificity issue found before continuing.

- [ ] **Step 4: Full test suite**

Run: `./gradlew test 2>&1 | tail -30; echo EXIT=${PIPESTATUS[0]}` (memory `gradle-bg-exit-code-unreliable`: trust the printed EXIT and the result XML, not a notification code). No source edits while it runs (memory `no-source-edits-during-gradle-run`).
Expected: EXIT=0. Known pre-existing flakes are listed in memory `pre-existing-ui-test-failures`; anything else must be fixed.

- [ ] **Step 5: Update memory** — mark implementation done in `project-workflow-colours-branding.md`; in `semantic-colors-consolidation.md` note that menu colouring is now `ActionColor` and `util/ContextMenuFactory` is deleted.

- [ ] **Step 6: Commit and push**

```bash
git add STYLE_GUIDE.jsonc docs/unified-shell.md docs/img CLAUDE.md .claude/rules/architecture.md
git commit -m "docs: workflow colours and action-colour rule in style guide and user docs"
git push origin main
```
Then trigger the `docs-updater` agent per CLAUDE.md (feature complete).

---

## Self-review notes

- Spec coverage: §1 palette → T1/T2; §2 rail → T4, title/primary/hover → T5, toolbar → T7, badge + welcome → T9, restraint (menus never workflow-coloured) → T8 guard; §3 → T6 (rows) + T8 (menus); §5 mapping → T1/T2/T5; §6 dead code → T8; §7 order matches task order; §8 verification → T10.
- Type consistency: `Workflow.cssClass()` returns `"fxt-wf-" + id` everywhere; `ActionColor.token()`; `SemanticIcon.bind(IconifyIcon, ActionColor|ColorToken)`; `WorkflowStyle.apply(Node, Workflow)`; `PanelAction.color(ActionColor)`.
- Deliberate deviation from the Figma variable set: the code has no `-fxt-action-*` Java constants (aliases are CSS-only); Help/Settings use the workspace rail colour via CSS, matching the Figma sheet's special case.
