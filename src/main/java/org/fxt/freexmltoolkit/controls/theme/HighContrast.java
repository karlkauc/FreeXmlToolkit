package org.fxt.freexmltoolkit.controls.theme;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.paint.Color;

/**
 * High contrast, on top of the light or dark theme: every text colour is darkened (light) or
 * lightened (dark) until it reaches 7:1 (WCAG AAA) on the backgrounds it is used on, borders reach
 * 4.5:1, and the focus ring takes the strongest text colour. Works on the current token values,
 * so an accent colour and the syntax colours are strengthened too. Pure Java.
 */
public final class HighContrast {

    /** Settings key: {@code true} turns high contrast on. */
    public static final String KEY = "ui.contrast.high";
    /** Text contrast in high contrast (WCAG AAA). */
    public static final double TEXT = 7.0;
    /** Border / component contrast in high contrast. */
    public static final double BORDER = 4.5;

    /** Text tokens, raised to {@link #TEXT}. */
    static final List<DesignTokens.ColorToken> TEXT_TOKENS = List.of(
            DesignTokens.ColorToken.TEXT_PRIMARY, DesignTokens.ColorToken.TEXT_SECONDARY,
            DesignTokens.ColorToken.TEXT_MUTED, DesignTokens.ColorToken.PRIMARY_TEXT,
            DesignTokens.ColorToken.WARNING_TEXT, DesignTokens.ColorToken.SUCCESS_TEXT,
            DesignTokens.ColorToken.CODE_TEXT, DesignTokens.ColorToken.CODE_ELEM,
            DesignTokens.ColorToken.CODE_ATTR, DesignTokens.ColorToken.CODE_VAL,
            DesignTokens.ColorToken.CODE_COMMENT, DesignTokens.ColorToken.CODE_NUMBER,
            DesignTokens.ColorToken.CODE_KEYWORD, DesignTokens.ColorToken.CODE_DECL);
    /** Border-like tokens, raised to {@link #BORDER}. */
    static final List<DesignTokens.ColorToken> BORDER_TOKENS = List.of(
            DesignTokens.ColorToken.BORDER_DEFAULT, DesignTokens.ColorToken.PRIMARY, DesignTokens.ColorToken.CODE_PUNCT);

    private HighContrast() {
    }

    /**
     * @param current the overrides in place so far (accent, syntax), CSS variable → colour
     * @param theme the theme
     * @return the high-contrast values of the text and border tokens, CSS variable → colour
     */
    public static Map<String, Color> overrides(Map<String, Color> current, DesignTokens.Theme theme) {
        Color[] backgrounds = backgrounds(current, theme);
        Map<String, Color> map = new LinkedHashMap<>();
        for (DesignTokens.ColorToken token : TEXT_TOKENS) {
            map.put(token.cssVariable(), raise(valueOf(token, current, theme), TEXT, theme, backgrounds));
        }
        for (DesignTokens.ColorToken token : BORDER_TOKENS) {
            map.put(token.cssVariable(), raise(valueOf(token, current, theme), BORDER, theme, backgrounds));
        }
        return map;
    }

    /** @return the backgrounds texts are drawn on: canvas, surfaces, subtle (selection) */
    static Color[] backgrounds(Map<String, Color> current, DesignTokens.Theme theme) {
        return new Color[] {
            valueOf(DesignTokens.ColorToken.BG_CANVAS, current, theme),
            valueOf(DesignTokens.ColorToken.BG_SURFACE, current, theme),
            valueOf(DesignTokens.ColorToken.BG_SURFACE_2, current, theme),
            valueOf(DesignTokens.ColorToken.BG_SUBTLE, current, theme),
            valueOf(DesignTokens.ColorToken.CODE_CURLINE, current, theme)};
    }

    private static Color valueOf(DesignTokens.ColorToken token, Map<String, Color> current, DesignTokens.Theme theme) {
        Color override = current == null ? null : current.get(token.cssVariable());
        return override != null ? override : token.defaultColor(theme);
    }

    private static Color raise(Color color, double ratio, DesignTokens.Theme theme, Color[] backgrounds) {
        return theme == DesignTokens.Theme.DARK
                ? AccentPalette.lightenUntil(color, ratio, backgrounds)
                : AccentPalette.darkenUntil(color, ratio, backgrounds);
    }
}
