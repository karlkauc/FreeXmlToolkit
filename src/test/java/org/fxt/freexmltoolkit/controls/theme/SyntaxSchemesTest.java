package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

/** Syntax schemes: readable on the editor background, own colours win, defaults change nothing. */
class SyntaxSchemesTest {

    private static final Color LIGHT_BG = Color.web("#ffffff");
    private static final Color DARK_BG = Color.web("#161b22");

    @Test
    void theDefaultSchemeOverridesNothing() {
        for (DesignTokens.Theme theme : DesignTokens.Theme.values()) {
            assertTrue(SyntaxSchemes.overrides(null, key -> null, theme).isEmpty());
            assertTrue(SyntaxSchemes.overrides("default", key -> null, theme).isEmpty());
        }
    }

    @Test
    void everySchemeIsReadableInBothThemes() {
        for (SyntaxSchemes.Scheme scheme : SyntaxSchemes.SCHEMES) {
            for (DesignTokens.Theme theme : DesignTokens.Theme.values()) {
                Color bg = theme == DesignTokens.Theme.DARK ? DARK_BG : LIGHT_BG;
                Map<SyntaxSchemes.Role, Color> colors = SyntaxSchemes.effective(scheme.id(), key -> null, theme);
                assertEquals(SyntaxSchemes.Role.values().length, colors.size());
                if (scheme.id().equals(SyntaxSchemes.DEFAULT_ID)) {
                    continue; // the design's own palette is guarded by ContrastTest
                }
                colors.forEach((role, color) -> {
                    double min = role == SyntaxSchemes.Role.PUNCTUATION ? 3.0 : 4.5;
                    assertTrue(AccentPalette.contrast(color, bg) >= min - 1e-6,
                            scheme.id() + "/" + theme + "/" + role + " " + AccentPalette.hex(color));
                });
            }
        }
    }

    @Test
    void anOwnColourWinsAndIsMadeReadable() {
        String key = SyntaxSchemes.Role.ELEMENT.key(DesignTokens.Theme.LIGHT);
        assertEquals("syntax.color.element.light", key);
        Map<String, Color> overrides = SyntaxSchemes.overrides("github",
                k -> k.equals(key) ? "#ffff00" : null, DesignTokens.Theme.LIGHT);
        Color element = overrides.get("-fxt-code-elem");
        assertTrue(AccentPalette.contrast(element, LIGHT_BG) >= 4.5, "yellow on white is darkened");
        assertTrue(element.getRed() > element.getBlue(), "and stays yellowish");
        assertEquals(SyntaxSchemes.scheme("github").colors(DesignTokens.Theme.LIGHT).get(SyntaxSchemes.Role.VALUE),
                overrides.get("-fxt-code-val"), "the scheme's other colours are readable already");
    }

    @Test
    void unknownSchemesFallBackToTheDefault() {
        assertEquals(SyntaxSchemes.DEFAULT_ID, SyntaxSchemes.scheme("nope").id());
        assertEquals("monokai", SyntaxSchemes.scheme("MONOKAI").id());
    }

    @Test
    void oxygenAndXmlSpySchemesExist() {
        assertEquals("oxygen", SyntaxSchemes.scheme("oXygen").id());
        assertEquals("xmlspy", SyntaxSchemes.scheme("XMLSpy").id());
        assertEquals(javafx.scene.paint.Color.web("#000096"),
                SyntaxSchemes.scheme("oxygen").light().get(SyntaxSchemes.Role.ELEMENT));
        assertEquals(javafx.scene.paint.Color.web("#FF0000"),
                SyntaxSchemes.scheme("xmlspy").light().get(SyntaxSchemes.Role.ATTRIBUTE));
    }
}
