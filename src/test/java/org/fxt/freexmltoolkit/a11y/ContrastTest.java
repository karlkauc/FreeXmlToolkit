package org.fxt.freexmltoolkit.a11y;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The text colours of the design tokens reach the WCAG 2.1 AA contrast of 4.5:1 on the backgrounds
 * they are used on, in the light and the dark theme.
 */
@DisplayName("WCAG AA contrast of the design tokens")
class ContrastTest {

    /** Text token on background token: the pairs the shell uses for normal-size text. */
    static final String[][] PAIRS = {
            {"text-primary", "bg-surface"}, {"text-primary", "bg-canvas"}, {"text-primary", "bg-subtle"},
            {"text-secondary", "bg-surface"}, {"text-secondary", "bg-canvas"}, {"text-secondary", "bg-surface-2"},
            {"text-muted", "bg-surface"}, {"text-muted", "bg-canvas"}, {"text-muted", "bg-surface-2"},
            {"primary-text", "bg-surface"}, {"primary-text", "bg-canvas"}, {"primary-text", "bg-subtle"},
            {"on-primary", "primary-fill"}, {"on-primary", "primary-strong"},
            {"warning-text", "bg-surface"}, {"warning-text", "bg-canvas"},
            {"danger", "bg-surface"}, {"success-text", "success-bg"}, {"type-frame-fg", "type-frame-bg"},
            // Dialogs: button bar, sections and info boxes sit on surface-2
            {"text-primary", "bg-surface-2"}, {"primary-text", "bg-surface-2"}, {"success-text", "bg-surface-2"},
    };

    @Test
    void textReachesAa() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/css/design-tokens.css"));
        int dark = css.indexOf(".root.fxt-theme-dark");
        Map<String, Map<String, String>> themes = Map.of("light", tokens(css.substring(0, dark)),
                "dark", tokens(css.substring(dark)));
        List<String> failing = new ArrayList<>();
        themes.forEach((theme, tokens) -> {
            for (String[] pair : PAIRS) {
                String fg = tokens.get(pair[0]);
                String bg = tokens.get(pair[1]);
                if (fg == null || bg == null) {
                    failing.add(theme + ": missing " + pair[0] + " or " + pair[1]);
                    continue;
                }
                double ratio = contrast(fg, bg);
                if (ratio < 4.5) {
                    failing.add(String.format("%s: %s %s on %s %s = %.2f", theme, pair[0], fg, pair[1], bg, ratio));
                }
            }
        });
        assertEquals(List.of(), failing);
    }

    private static Map<String, String> tokens(String css) {
        Map<String, String> tokens = new HashMap<>();
        Matcher m = Pattern.compile("-fxt-([\\w-]+):\\s*(#[0-9a-fA-F]{6})").matcher(css);
        while (m.find()) {
            tokens.putIfAbsent(m.group(1), m.group(2));
        }
        return tokens;
    }

    /** WCAG 2.1 contrast ratio of two sRGB colours. */
    static double contrast(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }
}
