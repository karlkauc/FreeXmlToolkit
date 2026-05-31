package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DesignTokens}, the Java mirror of the Figma design tokens
 * (Figma file "FreeXmlToolkit — UI Modernization", key oqJVcInD6RgKaQ4dYmMWYh).
 * These tokens are the single source of truth shared between the CSS
 * ({@code design-tokens.css}) and any programmatic theming (e.g. the future
 * virtualized renderer).
 */
class DesignTokensTest {

    @Test
    void everyColorTokenHasBothLightAndDarkValue() {
        for (DesignTokens.ColorToken token : DesignTokens.ColorToken.values()) {
            assertNotNull(token.color(DesignTokens.Theme.LIGHT),
                    () -> token + " is missing a LIGHT color");
            assertNotNull(token.color(DesignTokens.Theme.DARK),
                    () -> token + " is missing a DARK color");
        }
    }

    @Test
    void primaryAndAccentMatchFigmaTokens() {
        assertEquals(Color.web("#3b5bdb"),
                DesignTokens.ColorToken.PRIMARY.color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#5c7cfa"),
                DesignTokens.ColorToken.PRIMARY.color(DesignTokens.Theme.DARK));
        assertEquals(Color.web("#f08c2e"),
                DesignTokens.ColorToken.ACCENT.color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#f59f46"),
                DesignTokens.ColorToken.ACCENT.color(DesignTokens.Theme.DARK));
    }

    @Test
    void canvasAndSurfaceMatchFigmaTokens() {
        assertEquals(Color.web("#f5f7fa"),
                DesignTokens.ColorToken.BG_CANVAS.color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#0f1419"),
                DesignTokens.ColorToken.BG_CANVAS.color(DesignTokens.Theme.DARK));
        assertEquals(Color.web("#ffffff"),
                DesignTokens.ColorToken.BG_SURFACE.color(DesignTokens.Theme.LIGHT));
        assertEquals(Color.web("#161b22"),
                DesignTokens.ColorToken.BG_SURFACE.color(DesignTokens.Theme.DARK));
    }

    @Test
    void cssVariableNamesUseFxtPrefix() {
        for (DesignTokens.ColorToken token : DesignTokens.ColorToken.values()) {
            String var = token.cssVariable();
            assertNotNull(var, () -> token + " has no CSS variable name");
            assertTrue(var.startsWith("-fxt-"),
                    () -> token + " CSS variable '" + var + "' must start with -fxt-");
        }
    }

    @Test
    void cssVariableNamesAreUnique() {
        long distinct = java.util.Arrays.stream(DesignTokens.ColorToken.values())
                .map(DesignTokens.ColorToken::cssVariable)
                .distinct().count();
        assertEquals(DesignTokens.ColorToken.values().length, distinct,
                "CSS variable names must be unique across tokens");
    }

    @Test
    void themeResolvesFromPropertyAndDefaultsToLight() {
        assertEquals(DesignTokens.Theme.DARK, DesignTokens.Theme.fromProperty("dark"));
        assertEquals(DesignTokens.Theme.LIGHT, DesignTokens.Theme.fromProperty("light"));
        assertEquals(DesignTokens.Theme.LIGHT, DesignTokens.Theme.fromProperty(null));
        assertEquals(DesignTokens.Theme.LIGHT, DesignTokens.Theme.fromProperty("anything-else"));
    }

    @Test
    void typographyFamiliesAreDefinedAndDistinct() {
        assertNotNull(DesignTokens.FONT_FAMILY_UI);
        assertNotNull(DesignTokens.FONT_FAMILY_MONO);
        assertFalse(DesignTokens.FONT_FAMILY_UI.isBlank());
        assertFalse(DesignTokens.FONT_FAMILY_MONO.isBlank());
        assertNotEquals(DesignTokens.FONT_FAMILY_UI, DesignTokens.FONT_FAMILY_MONO);
    }

    @Test
    void spacingScaleIsPositiveAndAscending() {
        int[] scale = DesignTokens.SPACING_SCALE;
        assertTrue(scale.length >= 4, "expected a reasonable spacing scale");
        for (int i = 0; i < scale.length; i++) {
            assertTrue(scale[i] > 0, "spacing values must be positive");
            if (i > 0) {
                assertTrue(scale[i] > scale[i - 1], "spacing scale must be strictly ascending");
            }
        }
    }
}
