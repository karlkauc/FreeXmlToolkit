package org.fxt.freexmltoolkit.controls.theme;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javafx.scene.paint.Color;

/**
 * Java mirror of the FreeXmlToolkit design tokens.
 * <p>
 * These tokens are the single source of truth for the application's color
 * palette and are kept in lock-step with {@code resources/css/design-tokens.css}
 * (the CSS looked-up colors) and with the Figma file
 * "FreeXmlToolkit — UI Modernization" (key {@code oqJVcInD6RgKaQ4dYmMWYh}).
 * <p>
 * The CSS file drives all FXML/CSS-styled controls; this class lets
 * Java-rendered surfaces (e.g. the virtualized Tree/Graphic renderer) use the
 * exact same colors so light/dark theming stays consistent everywhere.
 * <p>
 * Only colors are real, mode-bound Figma variables. Spacing and radius are
 * plain numbers in the design, mirrored here as an application-wide scale.
 */
public final class DesignTokens {

    private DesignTokens() {
    }

    /** The two supported themes. */
    public enum Theme {
        LIGHT,
        DARK;

        /**
         * Resolves a theme from the persisted {@code ui.theme} property value.
         *
         * @param value the property value ({@code "dark"} / {@code "light"} / {@code null})
         * @return {@link #DARK} only for {@code "dark"} (case-insensitive); {@link #LIGHT} otherwise
         */
        public static Theme fromProperty(String value) {
            return "dark".equalsIgnoreCase(value) ? DARK : LIGHT;
        }
    }

    /**
     * The Figma color tokens. Each token carries its CSS looked-up-color name
     * (used in {@code design-tokens.css}) plus the light and dark hex values.
     */
    public enum ColorToken {
        ON_PRIMARY("-fxt-on-primary", "#ffffff", "#ffffff"),
        PRIMARY("-fxt-primary", "#1373d9", "#4c9bf5"),
        ACCENT("-fxt-accent", "#f08c2e", "#f59f46"),

        BG_CANVAS("-fxt-bg-canvas", "#f5f7fa", "#0f1419"),
        BG_SURFACE("-fxt-bg-surface", "#ffffff", "#161b22"),
        BG_SURFACE_2("-fxt-bg-surface-2", "#f2f4f8", "#1c232c"),
        BG_SUBTLE("-fxt-bg-subtle", "#e7ecfd", "#20305c"),
        BORDER_DEFAULT("-fxt-border", "#dde1e7", "#2a323d"),

        TEXT_PRIMARY("-fxt-text-primary", "#1a1d21", "#e6eaf0"),
        TEXT_SECONDARY("-fxt-text-secondary", "#5a6472", "#9ba6b3"),
        TEXT_MUTED("-fxt-text-muted", "#8a93a0", "#6b7480"),

        SUCCESS("-fxt-success", "#2f9e44", "#51cf66"),
        SUCCESS_BG("-fxt-success-bg", "#eaf6ee", "#14301e"),
        SUCCESS_TEXT("-fxt-success-text", "#1f7a35", "#8ce0a0"),
        SUCCESS_BORDER("-fxt-success-border", "#bfe3cb", "#2b5a3a"),

        DANGER("-fxt-danger", "#e03131", "#ff6b6b"),
        WARNING("-fxt-warning", "#f08c00", "#fab005"),

        // Semantic icon/accent foregrounds (match the -fxt-tool-*-fg palette + secondary accents).
        // Used by SemanticIcon to colour IconifyIcons theme-aware; kept in lock-step with
        // design-tokens.css (the *-icon-* tokens below).
        INFO("-fxt-info", "#1098ad", "#4dd4e8"),
        NEUTRAL("-fxt-neutral", "#5a6472", "#9ba6b3"),
        PURPLE("-fxt-purple", "#6f42c1", "#b197fc"),
        TEAL("-fxt-teal", "#12a594", "#38d9a9"),
        INDIGO("-fxt-indigo", "#4c5fd5", "#9775fa"),

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

        CODE_TEXT("-fxt-code-text", "#1a1d21", "#e6eaf0"),
        CODE_ELEM("-fxt-code-elem", "#1f6fb2", "#6cb6ff"),
        CODE_ATTR("-fxt-code-attr", "#c56a12", "#e0a458"),
        CODE_VAL("-fxt-code-val", "#2e8b40", "#7ee787"),
        CODE_DECL("-fxt-code-decl", "#9a6bd6", "#c09bf0"),
        CODE_PUNCT("-fxt-code-punct", "#7a8392", "#6b7480"),
        CODE_CURLINE("-fxt-code-curline", "#f2f6ff", "#1b2438");

        private final String cssVariable;
        private final Color light;
        private final Color dark;

        ColorToken(String cssVariable, String lightHex, String darkHex) {
            this.cssVariable = cssVariable;
            this.light = Color.web(lightHex);
            this.dark = Color.web(darkHex);
        }

        /** @return the CSS looked-up-color variable name, e.g. {@code -fxt-primary}. */
        public String cssVariable() {
            return cssVariable;
        }

        /** @return the token's color for the given theme. */
        public Color color(Theme theme) {
            return theme == Theme.DARK ? dark : light;
        }
    }

    // ---------------------------------------------------------------------
    // Current theme + change notifications.
    //
    // ThemeManager (controls/shell) publishes here on every theme switch.
    // Canvas-rendered views (controls/v2) subscribe HERE instead of on
    // ThemeManager so the v2 layer stays free of shell dependencies.
    // ---------------------------------------------------------------------

    private static volatile Theme currentTheme = Theme.LIGHT;

    private static final CopyOnWriteArrayList<WeakReference<Consumer<Theme>>> THEME_LISTENERS =
            new CopyOnWriteArrayList<>();

    /** @return the last published theme ({@link Theme#LIGHT} until a switch is published). */
    public static Theme currentTheme() {
        return currentTheme;
    }

    /**
     * Publishes a theme switch. Called by {@code ThemeManager.apply}; listeners
     * registered via {@link #addThemeListener} are notified on the caller's thread.
     */
    public static void publishTheme(Theme theme) {
        if (theme == null) {
            return;
        }
        currentTheme = theme;
        for (WeakReference<Consumer<Theme>> ref : THEME_LISTENERS) {
            Consumer<Theme> listener = ref.get();
            if (listener == null) {
                THEME_LISTENERS.remove(ref);
                continue;
            }
            try {
                listener.accept(theme);
            } catch (Throwable t) {
                // a misbehaving listener must not break the theme switch
            }
        }
    }

    /**
     * Registers a weakly-referenced theme listener. The CALLER must keep a strong
     * reference to {@code listener} (e.g. an instance field) — otherwise it is
     * garbage-collected and silently dropped. Weak registration keeps per-document
     * views (grid/diagram canvases) collectable when their tab closes.
     */
    public static void addThemeListener(Consumer<Theme> listener) {
        if (listener != null) {
            THEME_LISTENERS.add(new WeakReference<>(listener));
        }
    }

    /** UI font family (matches the bundled Inter font). */
    public static final String FONT_FAMILY_UI = "Inter";

    /** Monospaced/code font family with fallbacks (JetBrains Mono preferred). */
    public static final String FONT_FAMILY_MONO = "JetBrains Mono";

    /**
     * Spacing scale in pixels (4-pt based). Not a bound Figma variable; an
     * application-wide convention mirrored here for the Java-rendered surfaces.
     */
    public static final int[] SPACING_SCALE = {4, 8, 12, 16, 24, 32, 48};

    /** Corner-radius scale in pixels. */
    public static final int[] RADIUS_SCALE = {4, 8, 12};
}
