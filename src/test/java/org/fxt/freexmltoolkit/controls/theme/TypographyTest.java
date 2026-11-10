package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The generated typography sheet: scaled font rules with the original selectors. */
class TypographyTest {

    private static final List<String> SAMPLE = List.of("""
            /* a comment { -fx-font-size: 99px; } */
            .fxt-shell { -fx-font-family: "Inter", "Segoe UI", sans-serif; }
            .a, .b:hover > .label {
                -fx-font-size: 13px;
                -fx-text-fill: red;
            }
            .code { -fx-font-family: "JetBrains Mono", monospace; -fx-font-size: 11px; }
            .rel { -fx-font-size: 1.2em; }
            .plain { -fx-font-size: 12; }
            """);

    @Test
    void theDesignDefaultsNeedNoSheet() {
        assertNull(Typography.css(Typography.Size.NORMAL, null, 13, SAMPLE));
        assertNull(Typography.css(Typography.Size.NORMAL, "Inter", 13, SAMPLE));
    }

    @Test
    void sizesAreScaledUnderTheirOwnSelectors() {
        String css = Typography.css(Typography.Size.XLARGE, null, 13, SAMPLE);
        assertTrue(css.contains(".root { -fx-font-size: 16px; }"), css);
        assertTrue(css.contains(".a, .b:hover > .label { -fx-font-size: 16px; }"), css);
        assertTrue(css.contains(".code { -fx-font-size: 13.5px; }"), css);
        assertTrue(css.contains(".plain { -fx-font-size: 14.8px; }"), "no unit = px");
        assertFalse(css.contains(".rel"), "em sizes are relative already");
        assertFalse(css.contains("99px"), "comments are ignored");
        assertFalse(css.contains("-fx-text-fill"), "only font declarations are re-stated");
    }

    @Test
    void theUiFontReplacesInterButNotTheCodeFont() {
        String css = Typography.css(Typography.Size.NORMAL, "Noto Sans", 13, SAMPLE);
        assertTrue(css.contains(".fxt-shell { -fx-font-family: 'Noto Sans'; }"), css);
        assertTrue(css.contains(".root { -fx-font-family: 'Noto Sans'; }"), css);
        assertFalse(css.contains(".code"), "monospace rules keep their font");
        assertFalse(css.contains("-fx-font-size"), "normal size scales nothing");
    }

    @Test
    void theShellSheetsAreReadAndProduceRules() {
        String css = Typography.css(Typography.Size.LARGE, null, 13);
        assertTrue(css.lines().count() > 200, "every px font rule of the shell sheets: " + css.lines().count());
        assertEquals(Typography.Size.LARGE, Typography.Size.fromProperty("large"));
        assertEquals(Typography.Size.NORMAL, Typography.Size.fromProperty("huge"));
    }
}
