package org.fxt.freexmltoolkit.controls.theme;

import java.util.Locale;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken;

/**
 * The workflow colour families of the Unified Shell (design spec
 * {@code docs/superpowers/specs/2026-09-26-workflow-colours-branding-design.md}).
 * <p>
 * Each activity has its own family ({@code Activity.workflow()}) — only Help and Settings share
 * {@link #NEUTRAL} (user decision 2026-09-26: Explorer/Search/Favorites and Schema/Schema Library
 * must be told apart at a glance). The family
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
    SEARCH("search", ColorToken.WF_SEARCH_ACCENT, ColorToken.WF_SEARCH_FG, ColorToken.WF_SEARCH_FILL,
            ColorToken.WF_SEARCH_BG, ColorToken.WF_SEARCH_BORDER, ColorToken.WF_SEARCH_RAIL),
    FAVORITES("favorites", ColorToken.WF_FAVORITES_ACCENT, ColorToken.WF_FAVORITES_FG, ColorToken.WF_FAVORITES_FILL,
            ColorToken.WF_FAVORITES_BG, ColorToken.WF_FAVORITES_BORDER, ColorToken.WF_FAVORITES_RAIL),
    VALIDATION("validation", ColorToken.WF_VALIDATION_ACCENT, ColorToken.WF_VALIDATION_FG, ColorToken.WF_VALIDATION_FILL,
            ColorToken.WF_VALIDATION_BG, ColorToken.WF_VALIDATION_BORDER, ColorToken.WF_VALIDATION_RAIL),
    TRANSFORM("transform", ColorToken.WF_TRANSFORM_ACCENT, ColorToken.WF_TRANSFORM_FG, ColorToken.WF_TRANSFORM_FILL,
            ColorToken.WF_TRANSFORM_BG, ColorToken.WF_TRANSFORM_BORDER, ColorToken.WF_TRANSFORM_RAIL),
    SCHEMA("schema", ColorToken.WF_SCHEMA_ACCENT, ColorToken.WF_SCHEMA_FG, ColorToken.WF_SCHEMA_FILL,
            ColorToken.WF_SCHEMA_BG, ColorToken.WF_SCHEMA_BORDER, ColorToken.WF_SCHEMA_RAIL),
    SCHEMA_LIBRARY("schema-library", ColorToken.WF_SCHEMA_LIBRARY_ACCENT, ColorToken.WF_SCHEMA_LIBRARY_FG, ColorToken.WF_SCHEMA_LIBRARY_FILL,
            ColorToken.WF_SCHEMA_LIBRARY_BG, ColorToken.WF_SCHEMA_LIBRARY_BORDER, ColorToken.WF_SCHEMA_LIBRARY_RAIL),
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

    /** @return icon tint (toolbar group, welcome icon chip). */
    public ColorToken accent() {
        return accent;
    }

    /** @return text on a surface (panel title, badge text). */
    public ColorToken fg() {
        return fg;
    }

    /** @return filled primary action with a white label. */
    public ColorToken fill() {
        return fill;
    }

    /** @return tint (badge background, action-row hover, icon chip). */
    public ColorToken bg() {
        return bg;
    }

    /** @return badge / chip outline. */
    public ColorToken border() {
        return border;
    }

    /** @return indicator + icon colour on the always-navy Activity Bar. */
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
