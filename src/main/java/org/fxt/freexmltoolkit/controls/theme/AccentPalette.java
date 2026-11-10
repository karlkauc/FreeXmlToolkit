package org.fxt.freexmltoolkit.controls.theme;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.scene.paint.Color;

/**
 * Derives the primary-colour tokens of both themes from one accent colour, so the user can pick
 * the application's highlight colour (buttons, selection, focus, links, active tabs) without
 * breaking contrast: every derived text or text background is adjusted until it meets WCAG AA
 * (4.5:1) against the backgrounds it is used on.
 *
 * <p>The default accent ({@link #DEFAULT_ID}) derives nothing: the tokens keep the values of
 * {@code design-tokens.css}. Pure Java, no JavaFX toolkit needed.
 */
public final class AccentPalette {

    /** Id of the built-in accent (the design's blue). */
    public static final String DEFAULT_ID = "blue";

    /** Minimum contrast for text (WCAG AA, normal text). */
    static final double TEXT_CONTRAST = 4.5;
    /** Minimum contrast for UI components such as borders and focus rings (WCAG 1.4.11). */
    static final double UI_CONTRAST = 3.0;

    /** A selectable accent: id (stored in the settings), label key and base colour. */
    public record Preset(String id, String hex) {
        /** @return the base colour */
        public Color color() {
            return Color.web(hex);
        }

        /** @return the i18n key of the preset's name */
        public String labelKey() {
            return "Appearance.accent." + id;
        }
    }

    /** The offered presets; the first is the design's own blue. */
    public static final List<Preset> PRESETS = List.of(
            new Preset(DEFAULT_ID, "#1373d9"),
            new Preset("indigo", "#4f46e5"),
            new Preset("violet", "#7c3aed"),
            new Preset("green", "#15803d"),
            new Preset("teal", "#0f766e"),
            new Preset("orange", "#c2410c"),
            new Preset("red", "#c81e3c"),
            new Preset("graphite", "#4b5563"));

    private static final Color WHITE = Color.WHITE;
    private static final Color LIGHT_SURFACE = Color.web("#ffffff");
    private static final Color LIGHT_CANVAS = Color.web("#f5f7fa");
    private static final Color LIGHT_SURFACE_2 = Color.web("#f2f4f8");
    private static final Color DARK_CANVAS = Color.web("#0f1419");
    private static final Color DARK_SURFACE = Color.web("#161b22");
    private static final Color DARK_SURFACE_2 = Color.web("#1c232c");

    private AccentPalette() {
    }

    /**
     * @param value a stored accent: a preset id, a {@code #rrggbb} colour, or null/blank
     * @return the accent colour, or null for the default accent
     */
    public static Color parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        for (Preset preset : PRESETS) {
            if (preset.id().equals(trimmed)) {
                return preset.id().equals(DEFAULT_ID) ? null : preset.color();
            }
        }
        if (trimmed.matches("#[0-9a-f]{6}")) {
            Color color = Color.web(trimmed);
            return color.equals(PRESETS.getFirst().color()) ? null : color;
        }
        return null;
    }

    /**
     * Derives the light-theme values of the accent-bound CSS variables.
     *
     * @param accent the accent colour
     * @return CSS variable → colour, in a stable order
     */
    public static Map<String, Color> light(Color accent) {
        Color base = opaque(accent);
        Color primary = darkenUntil(base, UI_CONTRAST, LIGHT_SURFACE, LIGHT_CANVAS, LIGHT_SURFACE_2);
        Color text = darkenUntil(base, TEXT_CONTRAST, LIGHT_SURFACE, LIGHT_CANVAS, LIGHT_SURFACE_2,
                mix(base, LIGHT_SURFACE, 0.9));
        Color fill = darkenUntil(base, TEXT_CONTRAST, WHITE);
        Color strong = darkenUntil(fill.deriveColor(0, 1, 0.92, 1), TEXT_CONTRAST, WHITE);
        Color subtle = mix(base, LIGHT_SURFACE, 0.9);
        Map<String, Color> map = new LinkedHashMap<>();
        map.put("-fxt-primary", primary);
        map.put("-fxt-primary-text", text);
        map.put("-fxt-primary-fill", fill);
        map.put("-fxt-primary-strong", strong);
        map.put("-fxt-bg-subtle", subtle);
        map.put("-fxt-tool-primary-bg", mix(base, LIGHT_SURFACE, 0.92));
        map.put("-fxt-tool-primary-border", mix(base, LIGHT_SURFACE, 0.72));
        map.put("-fxt-tool-primary-fg", text);
        return map;
    }

    /**
     * Derives the dark-theme values of the accent-bound CSS variables.
     *
     * @param accent the accent colour
     * @return CSS variable → colour, in a stable order
     */
    public static Map<String, Color> dark(Color accent) {
        Color base = opaque(accent);
        Color subtle = mix(base, DARK_SURFACE, 0.72);
        Color toolBg = mix(base, DARK_SURFACE, 0.82);
        Color primary = lightenUntil(base, TEXT_CONTRAST, DARK_CANVAS, DARK_SURFACE, DARK_SURFACE_2, subtle);
        Color fill = darkenUntil(base, TEXT_CONTRAST, WHITE);
        Color strong = darkenUntil(mix(base, DARK_CANVAS, 0.45), TEXT_CONTRAST, WHITE);
        Map<String, Color> map = new LinkedHashMap<>();
        map.put("-fxt-primary", primary);
        map.put("-fxt-primary-text", primary);
        map.put("-fxt-primary-fill", fill);
        map.put("-fxt-primary-strong", strong);
        map.put("-fxt-bg-subtle", subtle);
        map.put("-fxt-tool-primary-bg", toolBg);
        map.put("-fxt-tool-primary-border", mix(base, DARK_SURFACE, 0.55));
        map.put("-fxt-tool-primary-fg", lightenUntil(base, TEXT_CONTRAST, toolBg, DARK_SURFACE));
        return map;
    }

    // ------------------------------------------------------------------ colour maths

    /** @return the WCAG contrast ratio of two opaque colours (1–21) */
    public static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** @return the WCAG relative luminance of a colour */
    public static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** @return {@code a * (1 - t) + b * t} per channel */
    static Color mix(Color a, Color b, double t) {
        return Color.color(a.getRed() + (b.getRed() - a.getRed()) * t,
                a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t);
    }

    private static Color opaque(Color c) {
        return Color.color(c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Lowers the brightness in small steps until the colour reaches the contrast on every background. */
    static Color darkenUntil(Color start, double ratio, Color... backgrounds) {
        Color c = opaque(start);
        for (int i = 0; i < 200 && !meets(c, ratio, backgrounds); i++) {
            c = mix(c, Color.BLACK, 0.03);
        }
        return c;
    }

    /** Raises the lightness in small steps until the colour reaches the contrast on every background. */
    static Color lightenUntil(Color start, double ratio, Color... backgrounds) {
        Color c = opaque(start);
        for (int i = 0; i < 200 && !meets(c, ratio, backgrounds); i++) {
            c = mix(c, Color.WHITE, 0.03);
        }
        return c;
    }

    private static boolean meets(Color c, double ratio, Color... backgrounds) {
        for (Color background : backgrounds) {
            if (contrast(c, background) < ratio) {
                return false;
            }
        }
        return true;
    }

    /** @return the colour as {@code #rrggbb} */
    public static String hex(Color c) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", Math.round(c.getRed() * 255),
                Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }
}
