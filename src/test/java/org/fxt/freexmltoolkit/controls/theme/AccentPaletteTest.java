package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

/** Every accent, preset or arbitrary, gives readable primary texts and fills in both themes. */
class AccentPaletteTest {

    private static Color token(DesignTokens.ColorToken token, DesignTokens.Theme theme) {
        return token.defaultColor(theme);
    }

    private static void check(Color accent, List<String> failures) {
        DesignTokens.Theme light = DesignTokens.Theme.LIGHT;
        DesignTokens.Theme dark = DesignTokens.Theme.DARK;
        Map<String, Color> l = AccentPalette.light(accent);
        Map<String, Color> d = AccentPalette.dark(accent);
        String name = AccentPalette.hex(accent);
        for (DesignTokens.ColorToken bg : List.of(DesignTokens.ColorToken.BG_SURFACE, DesignTokens.ColorToken.BG_CANVAS,
                DesignTokens.ColorToken.BG_SURFACE_2)) {
            require(failures, name + " light primary-text on " + bg, l.get("-fxt-primary-text"), token(bg, light), 4.5);
            require(failures, name + " dark primary-text on " + bg, d.get("-fxt-primary-text"), token(bg, dark), 4.5);
            require(failures, name + " light primary on " + bg, l.get("-fxt-primary"), token(bg, light), 3.0);
        }
        require(failures, name + " light primary-text on subtle", l.get("-fxt-primary-text"), l.get("-fxt-bg-subtle"), 4.5);
        require(failures, name + " dark primary-text on subtle", d.get("-fxt-primary-text"), d.get("-fxt-bg-subtle"), 4.5);
        require(failures, name + " light text-primary on subtle", token(DesignTokens.ColorToken.TEXT_PRIMARY, light),
                l.get("-fxt-bg-subtle"), 4.5);
        require(failures, name + " dark text-primary on subtle", token(DesignTokens.ColorToken.TEXT_PRIMARY, dark),
                d.get("-fxt-bg-subtle"), 4.5);
        for (Map<String, Color> theme : List.of(l, d)) {
            require(failures, name + " white on fill", Color.WHITE, theme.get("-fxt-primary-fill"), 4.5);
            require(failures, name + " white on strong", Color.WHITE, theme.get("-fxt-primary-strong"), 4.5);
            require(failures, name + " tool fg on tool bg", theme.get("-fxt-tool-primary-fg"),
                    theme.get("-fxt-tool-primary-bg"), 4.5);
        }
    }

    private static void require(List<String> failures, String what, Color fg, Color bg, double ratio) {
        double actual = AccentPalette.contrast(fg, bg);
        if (actual < ratio) {
            failures.add(String.format("%s: %s on %s = %.2f", what, AccentPalette.hex(fg), AccentPalette.hex(bg), actual));
        }
    }

    @Test
    void everyPresetIsReadableInBothThemes() {
        List<String> failures = new ArrayList<>();
        for (AccentPalette.Preset preset : AccentPalette.PRESETS) {
            check(preset.color(), failures);
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void arbitraryColoursAreAdjustedToBeReadable() {
        List<String> failures = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 300; i++) {
            check(Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)), failures);
        }
        for (String extreme : List.of("#ffffff", "#000000", "#ffff00", "#00ffff", "#808080")) {
            check(Color.web(extreme), failures);
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void theDefaultAccentDerivesNothing() {
        assertNull(AccentPalette.parse(null));
        assertNull(AccentPalette.parse(""));
        assertNull(AccentPalette.parse(AccentPalette.DEFAULT_ID));
        assertNull(AccentPalette.parse("#1373D9"), "the design's own blue as hex");
        assertNull(AccentPalette.parse("not a colour"));
        assertEquals(Color.web("#4f46e5"), AccentPalette.parse("indigo"));
        assertEquals(Color.web("#123456"), AccentPalette.parse("#123456"));
    }

    @Test
    void thePaletteCoversTheAccentBoundVariablesOfDesignTokens() {
        for (Map<String, Color> map : List.of(AccentPalette.light(Color.TEAL), AccentPalette.dark(Color.TEAL))) {
            for (DesignTokens.ColorToken token : List.of(DesignTokens.ColorToken.PRIMARY, DesignTokens.ColorToken.PRIMARY_TEXT,
                    DesignTokens.ColorToken.PRIMARY_FILL, DesignTokens.ColorToken.PRIMARY_STRONG,
                    DesignTokens.ColorToken.BG_SUBTLE)) {
                assertNotNull(map.get(token.cssVariable()), token.name());
            }
        }
    }

    @Test
    void overridesReachTheJavaTokensAndCanBeCleared() {
        try {
            DesignTokens.setOverrides(AccentPalette.light(Color.web("#15803d")), AccentPalette.dark(Color.web("#15803d")));
            Color primary = DesignTokens.ColorToken.PRIMARY.color(DesignTokens.Theme.LIGHT);
            assertTrue(primary.getGreen() > primary.getBlue(), "green accent: " + primary);
            assertEquals(DesignTokens.ColorToken.TEXT_PRIMARY.defaultColor(DesignTokens.Theme.LIGHT),
                    DesignTokens.ColorToken.TEXT_PRIMARY.color(DesignTokens.Theme.LIGHT), "other tokens unchanged");
        } finally {
            DesignTokens.setOverrides(null, null);
        }
        assertEquals(Color.web("#1373d9"), DesignTokens.ColorToken.PRIMARY.color(DesignTokens.Theme.LIGHT));
    }
}
