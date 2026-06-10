package org.fxt.freexmltoolkit.controls.theme;

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
        PRIMARY("-fxt-primary", "#3b5bdb", "#5c7cfa"),
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
