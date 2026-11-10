package org.fxt.freexmltoolkit.controls.theme;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import javafx.scene.paint.Color;

/**
 * Syntax colour schemes for the code editors, the query results, the diff view and the grid's
 * values: every syntax role is a {@code -fxt-code-*} token ({@link Role}), a scheme replaces a set
 * of them per theme, and the user can still replace single roles.
 *
 * <p>Colours of a scheme or chosen by the user are adjusted until they keep their contrast on the
 * editor background (text 4.5:1, punctuation 3:1); the design's own colours ({@link #DEFAULT_ID})
 * stay as they are. Pure Java, no JavaFX toolkit needed.
 */
public final class SyntaxSchemes {

    /** Settings key of the scheme id. */
    public static final String SCHEME_KEY = "syntax.scheme";
    /** Id of the design's own colours. */
    public static final String DEFAULT_ID = "default";

    /** A syntax role: its token and its settings id. */
    public enum Role {
        TEXT(DesignTokens.ColorToken.CODE_TEXT, AccentPalette.TEXT_CONTRAST),
        ELEMENT(DesignTokens.ColorToken.CODE_ELEM, AccentPalette.TEXT_CONTRAST),
        ATTRIBUTE(DesignTokens.ColorToken.CODE_ATTR, AccentPalette.TEXT_CONTRAST),
        VALUE(DesignTokens.ColorToken.CODE_VAL, AccentPalette.TEXT_CONTRAST),
        PUNCTUATION(DesignTokens.ColorToken.CODE_PUNCT, AccentPalette.UI_CONTRAST),
        COMMENT(DesignTokens.ColorToken.CODE_COMMENT, AccentPalette.TEXT_CONTRAST),
        NUMBER(DesignTokens.ColorToken.CODE_NUMBER, AccentPalette.TEXT_CONTRAST),
        KEYWORD(DesignTokens.ColorToken.CODE_KEYWORD, AccentPalette.TEXT_CONTRAST),
        DECLARATION(DesignTokens.ColorToken.CODE_DECL, AccentPalette.TEXT_CONTRAST);

        private final DesignTokens.ColorToken token;
        private final double minContrast;

        Role(DesignTokens.ColorToken token, double minContrast) {
            this.token = token;
            this.minContrast = minContrast;
        }

        /** @return the design token of the role */
        public DesignTokens.ColorToken token() {
            return token;
        }

        /** @return the stored id, e.g. {@code element} */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** @return the i18n key of the role's name */
        public String labelKey() {
            return "Appearance.syntax.role." + id();
        }

        /** @return the settings key of the user's own colour for the role in a theme */
        public String key(DesignTokens.Theme theme) {
            return "syntax.color." + id() + "." + (theme == DesignTokens.Theme.DARK ? "dark" : "light");
        }
    }

    /** A scheme: id plus colours per role and theme (roles not listed keep the design's colour). */
    public record Scheme(String id, Map<Role, Color> light, Map<Role, Color> dark) {

        /** @return the i18n key of the scheme's name */
        public String labelKey() {
            return "Appearance.syntax.scheme." + id;
        }

        /** @return the scheme's colours for a theme */
        public Map<Role, Color> colors(DesignTokens.Theme theme) {
            return theme == DesignTokens.Theme.DARK ? dark : light;
        }
    }

    /** The offered schemes; the first is the design's own. */
    public static final List<Scheme> SCHEMES = List.of(
            new Scheme(DEFAULT_ID, Map.of(), Map.of()),
            new Scheme("github",
                    roles("#1f2328", "#116329", "#0550ae", "#0a3069", "#6e7781", "#6e7781", "#0550ae", "#cf222e", "#8250df"),
                    roles("#e6edf3", "#7ee787", "#79c0ff", "#a5d6ff", "#8b949e", "#8b949e", "#79c0ff", "#ff7b72", "#d2a8ff")),
            new Scheme("solarized",
                    roles("#586e75", "#268bd2", "#b58900", "#2aa198", "#93a1a1", "#93a1a1", "#d33682", "#859900", "#6c71c4"),
                    roles("#93a1a1", "#268bd2", "#b58900", "#2aa198", "#657b83", "#657b83", "#d33682", "#859900", "#6c71c4")),
            new Scheme("monokai",
                    roles("#272822", "#f92672", "#a6e22e", "#e6db74", "#75715e", "#75715e", "#ae81ff", "#66d9ef", "#fd971f"),
                    roles("#f8f8f2", "#f92672", "#a6e22e", "#e6db74", "#a59f85", "#75715e", "#ae81ff", "#66d9ef", "#fd971f")),
            new Scheme("high-contrast",
                    roles("#000000", "#0030a0", "#8a1c00", "#005a00", "#000000", "#4a4a4a", "#7a0099", "#a00050", "#5a3a00"),
                    roles("#ffffff", "#8cc8ff", "#ffb070", "#8cff9c", "#ffffff", "#c8c8c8", "#e0a0ff", "#ff9cc8", "#ffe08c")));

    /** Editor backgrounds of each theme: surface and the caret line tint. */
    private static final Color[] LIGHT_BACKGROUNDS = {Color.web("#ffffff"), Color.web("#f2f6ff")};
    private static final Color[] DARK_BACKGROUNDS = {Color.web("#161b22"), Color.web("#1b2438")};

    private SyntaxSchemes() {
    }

    /** @return the scheme with the id, the default one for null/unknown */
    public static Scheme scheme(String id) {
        if (id != null) {
            for (Scheme scheme : SCHEMES) {
                if (scheme.id().equalsIgnoreCase(id.trim())) {
                    return scheme;
                }
            }
        }
        return SCHEMES.getFirst();
    }

    /**
     * The token overrides for a scheme plus the user's own role colours.
     *
     * @param schemeId the scheme
     * @param settings reads a settings key (the user's own colours, {@code #rrggbb} or empty)
     * @param theme the theme
     * @return CSS variable → colour, only for roles that differ from the design
     */
    public static Map<String, Color> overrides(String schemeId, Function<String, String> settings,
            DesignTokens.Theme theme) {
        Scheme scheme = scheme(schemeId);
        Map<String, Color> map = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            Color color = scheme.colors(theme).get(role);
            String own = settings == null ? null : settings.apply(role.key(theme));
            if (own != null && own.trim().matches("#[0-9a-fA-F]{6}")) {
                color = Color.web(own.trim());
            }
            if (color != null) {
                map.put(role.token().cssVariable(), readable(color, role, theme));
            }
        }
        return map;
    }

    /**
     * @return the colour of every role in a theme: the scheme / own colour, else the design's
     */
    public static Map<Role, Color> effective(String schemeId, Function<String, String> settings,
            DesignTokens.Theme theme) {
        Map<String, Color> overrides = overrides(schemeId, settings, theme);
        Map<Role, Color> map = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            Color override = overrides.get(role.token().cssVariable());
            map.put(role, override != null ? override : role.token().defaultColor(theme));
        }
        return map;
    }

    /** Adjusts a colour until it keeps the role's contrast on the editor backgrounds of the theme. */
    static Color readable(Color color, Role role, DesignTokens.Theme theme) {
        return theme == DesignTokens.Theme.DARK
                ? AccentPalette.lightenUntil(color, role.minContrast, DARK_BACKGROUNDS)
                : AccentPalette.darkenUntil(color, role.minContrast, LIGHT_BACKGROUNDS);
    }

    private static Map<Role, Color> roles(String... hex) {
        Map<Role, Color> map = new EnumMap<>(Role.class);
        Role[] values = Role.values();
        for (int i = 0; i < values.length; i++) {
            map.put(values[i], Color.web(hex[i]));
        }
        return Map.copyOf(map);
    }
}
