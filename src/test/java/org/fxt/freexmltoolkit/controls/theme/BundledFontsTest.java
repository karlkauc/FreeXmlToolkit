package org.fxt.freexmltoolkit.controls.theme;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.io.InputStream;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards that the design-token font assets are bundled on the classpath under
 * {@code /css/fonts/}. {@code FxtGui.loadFontsAsync()} registers these at
 * startup so the {@link DesignTokens#FONT_FAMILY_UI} (Inter) and
 * {@link DesignTokens#FONT_FAMILY_MONO} (JetBrains Mono) tokens actually resolve.
 */
class BundledFontsTest {

    private static final String[] REQUIRED_FONTS = {
            // UI font (Inter)
            "Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
            // Code font (JetBrains Mono)
            "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold",
            "JetBrainsMono-Italic", "JetBrainsMono-BoldItalic"
    };

    @Test
    void requiredTokenFontsArePresentOnClasspath() {
        for (String font : REQUIRED_FONTS) {
            String path = "/css/fonts/" + font + ".ttf";
            try (InputStream in = getClass().getResourceAsStream(path)) {
                assertNotNull(in, () -> "Missing bundled font asset: " + path);
            } catch (Exception e) {
                throw new AssertionError("Could not open bundled font asset: " + path, e);
            }
        }
    }

    @Test
    void bundledMonoFontFamilyMatchesDesignToken() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/css/fonts/JetBrainsMono-Regular.ttf")) {
            assertNotNull(in, "JetBrainsMono-Regular.ttf must be bundled");
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            assertEquals(DesignTokens.FONT_FAMILY_MONO, font.getFamily(Locale.ENGLISH),
                    "Bundled JetBrains Mono family name must match DesignTokens.FONT_FAMILY_MONO");
        }
    }

    @Test
    void bundledUiFontFamilyMatchesDesignToken() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/css/fonts/Inter-Regular.ttf")) {
            assertNotNull(in, "Inter-Regular.ttf must be bundled");
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            assertEquals(DesignTokens.FONT_FAMILY_UI, font.getFamily(Locale.ENGLISH),
                    "Bundled Inter family name must match DesignTokens.FONT_FAMILY_UI");
        }
    }
}
