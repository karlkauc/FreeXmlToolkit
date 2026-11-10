package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

/** High contrast: every text token reaches 7:1, borders 4.5:1, with and without an accent. */
class HighContrastTest {

    @Test
    void textsReachAaaAndBordersAaInBothThemes() {
        for (String accent : new String[] {null, "orange", "#ffee00"}) {
            Color base = AccentPalette.parse(accent);
            for (DesignTokens.Theme theme : DesignTokens.Theme.values()) {
                Map<String, Color> current = base == null ? Map.of()
                        : theme == DesignTokens.Theme.DARK ? AccentPalette.dark(base) : AccentPalette.light(base);
                Map<String, Color> high = HighContrast.overrides(current, theme);
                java.util.Map<String, Color> merged = new java.util.HashMap<>(current);
                merged.putAll(high);
                Color[] backgrounds = HighContrast.backgrounds(merged, theme);
                check(HighContrast.TEXT_TOKENS, high, backgrounds, HighContrast.TEXT, accent, theme);
                check(HighContrast.BORDER_TOKENS, high, backgrounds, HighContrast.BORDER, accent, theme);
            }
        }
    }

    private static void check(java.util.List<DesignTokens.ColorToken> tokens, Map<String, Color> high,
            Color[] backgrounds, double ratio, String accent, DesignTokens.Theme theme) {
        for (DesignTokens.ColorToken token : tokens) {
            Color color = high.get(token.cssVariable());
            for (Color background : backgrounds) {
                double contrast = AccentPalette.contrast(color, background);
                assertTrue(contrast >= ratio - 1e-6, accent + "/" + theme + " " + token.cssVariable() + " "
                        + AccentPalette.hex(color) + " on " + AccentPalette.hex(background) + " = " + contrast);
            }
        }
    }
}
