package org.fxt.freexmltoolkit.controls.theme;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The user's UI text size and UI font, as a generated stylesheet.
 *
 * <p>JavaFX CSS has no {@code rem} and resolves {@code em} font sizes against the parent's font,
 * so the shell's fixed {@code px} sizes cannot simply be turned into relative ones (nested rules
 * would compound). Instead {@link #css} re-states every font rule of the shell sheets with the
 * same selector, the size scaled and the family replaced. The sheet is placed right after
 * {@code unified-shell.css}: same selectors, later sheet, so each scaled rule wins exactly where
 * its original won. At the default size and font no sheet is needed ({@code null}).
 *
 * <p>Inline styles set in code are not scaled; the code editors take their size from
 * {@code EditorFont}.
 */
public final class Typography {

    /** Settings key of the UI text size ({@link Size#id()}). */
    public static final String SIZE_KEY = "ui.font.size";
    /** Settings key of the density ({@link Density#id()}). */
    public static final String DENSITY_KEY = "ui.density";
    /** Settings key of the UI font family (empty = {@link #DEFAULT_FAMILY}). */
    public static final String FAMILY_KEY = "ui.font.family";
    /** The design's UI font (bundled). */
    public static final String DEFAULT_FAMILY = "Inter";
    /** Base size the shell's px values are designed for. */
    static final double DESIGN_BASE_PX = 13;

    /** The shell stylesheets whose font rules are scaled, in their cascade order. */
    static final List<String> SOURCES = List.of("/css/app-theme.css", "/css/fxt-theme.css",
            "/css/light-theme.css", "/css/unified-shell.css");

    /** The offered text sizes; the px value is the size of normal body text. */
    public enum Size {
        SMALL(12), NORMAL(13), LARGE(14), XLARGE(16);

        private final int px;

        Size(int px) {
            this.px = px;
        }

        /** @return the body text size in px */
        public int px() {
            return px;
        }

        /** @return the factor applied to every font size of the shell */
        public double factor() {
            return px / DESIGN_BASE_PX;
        }

        /** @return the stored id */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** @return the i18n key of the size's name */
        public String labelKey() {
            return "Appearance.fontSize." + id();
        }

        /** @return the size stored as {@code value}, {@link #NORMAL} for null/unknown */
        public static Size fromProperty(String value) {
            if (value != null) {
                for (Size size : values()) {
                    if (size.id().equalsIgnoreCase(value.trim())) {
                        return size;
                    }
                }
            }
            return NORMAL;
        }
    }

    /** How much room the interface leaves around texts: paddings, gaps and (when roomier) heights. */
    public enum Density {
        COMPACT(0.65), NORMAL(1.0), COMFORTABLE(1.35);

        private final double factor;

        Density(double factor) {
            this.factor = factor;
        }

        /** @return the factor applied to paddings and gaps */
        public double factor() {
            return factor;
        }

        /** @return the stored id */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** @return the i18n key of the density's name */
        public String labelKey() {
            return "Appearance.density." + id();
        }

        /** @return the density stored as {@code value}, {@link #NORMAL} for null/unknown */
        public static Density fromProperty(String value) {
            if (value != null) {
                for (Density density : values()) {
                    if (density.id().equalsIgnoreCase(value.trim())) {
                        return density;
                    }
                }
            }
            return NORMAL;
        }
    }

    /** Spacing declarations scaled by the density. */
    private static final List<Pattern> SPACING = java.util.stream.Stream.of(
                    "-fx-padding", "-fx-spacing", "-fx-vgap")
            .map(p -> Pattern.compile("(?<![\\w-])(" + Pattern.quote(p) + ")\\s*:\\s*([^;]+);"))
            .toList();
    /** Height declarations, grown with a roomier density (never shrunk: texts must still fit). */
    private static final List<Pattern> HEIGHTS = java.util.stream.Stream.of(
                    "-fx-min-height", "-fx-pref-height", "-fx-max-height")
            .map(p -> Pattern.compile("(?<![\\w-])(" + Pattern.quote(p) + ")\\s*:\\s*([^;]+);"))
            .toList();
    private static final Pattern LENGTH = Pattern.compile("(-?[0-9]*\\.?[0-9]+)(px|em)?");

    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern RULE = Pattern.compile("([^{}@]+)\\{([^{}]*)\\}");
    /** The declarations scaled with the text: the font size and the fixed tab heights that hold it. */
    private static final List<Pattern> SCALED = java.util.stream.Stream.of(
                    "-fx-font-size", "-fx-tab-min-height", "-fx-tab-max-height")
            .map(p -> Pattern.compile("(" + Pattern.quote(p) + ")\\s*:\\s*([0-9]*\\.?[0-9]+)\\s*(px|pt)?\\s*(?:;|$)"))
            .toList();
    /** Chrome with a fixed geometry that a larger text would not fit (the 56 px activity rail). */
    private static final List<String> FIXED_CHROME = List.of(".fxt-activity-");
    private static final Pattern FAMILY = Pattern.compile("-fx-font-family\\s*:\\s*([^;]+)");

    private static volatile List<String> sourceTexts;

    private Typography() {
    }

    /**
     * Builds the typography stylesheet for the shell sheets.
     *
     * @param size the UI text size
     * @param family the UI font family; null/blank or {@link #DEFAULT_FAMILY} keeps the design's font
     * @param baseFontPx the platform's default font size (unstyled controls), scaled on {@code .root}
     * @return the CSS, or null when nothing differs from the design
     */
    public static String css(Size size, String family, double baseFontPx) {
        return css(size, family, Density.NORMAL, baseFontPx, shellSources());
    }

    /**
     * Builds the typography and density stylesheet for the shell sheets.
     *
     * @param size the UI text size
     * @param family the UI font family (null = the design's)
     * @param density the density
     * @param baseFontPx the platform's default font size
     * @return the CSS, or null when nothing differs from the design
     */
    public static String css(Size size, String family, Density density, double baseFontPx) {
        return css(size, family, density, baseFontPx, shellSources());
    }

    /** {@link #css(Size, String, double)} over given stylesheet texts (for tests). */
    static String css(Size size, String family, double baseFontPx, List<String> sources) {
        return css(size, family, Density.NORMAL, baseFontPx, sources);
    }

    /** {@link #css(Size, String, double)} over given stylesheet texts (for tests). */
    static String css(Size size, String family, Density density, double baseFontPx, List<String> sources) {
        Size s = size == null ? Size.NORMAL : size;
        Density d = density == null ? Density.NORMAL : density;
        String fam = family == null || family.isBlank() || family.trim().equalsIgnoreCase(DEFAULT_FAMILY)
                ? null : family.trim().replace("'", "").replace("\"", "");
        if (s == Size.NORMAL && fam == null && d == Density.NORMAL) {
            return null;
        }
        double factor = s.factor();
        StringBuilder css = new StringBuilder();
        css.append(".root {");
        if (s != Size.NORMAL) {
            css.append(" -fx-font-size: ").append(px(baseFontPx * factor)).append(';');
        }
        if (fam != null) {
            css.append(" -fx-font-family: '").append(fam).append("';");
        }
        css.append(" }\n");
        if (d != Density.NORMAL) {
            // Modena's cells and menu items (their paddings are not in the shell sheets): vertical room only
            double f = d.factor();
            css.append(".list-cell { -fx-padding: ").append(em(0.25 * f)).append(" 0.583em; }\n");
            css.append(".tree-cell { -fx-padding: ").append(em(0.25 * f)).append(" 0.25em; }\n");
            css.append(".table-cell { -fx-padding: ").append(em(0.166667 * f)).append(" 0.166667em; }\n");
            css.append(".menu-item { -fx-padding: ").append(em(0.333333 * f)).append(" 0.41777em; }\n");
        }
        for (String source : sources) {
            Matcher rule = RULE.matcher(COMMENT.matcher(source).replaceAll(""));
            while (rule.find()) {
                String selectors = String.join(" ", rule.group(1).trim().split("\\s+"));
                String body = rule.group(2);
                StringBuilder decls = new StringBuilder();
                if (s != Size.NORMAL && FIXED_CHROME.stream().noneMatch(selectors::contains)) {
                    for (Pattern property : SCALED) {
                        Matcher decl = property.matcher(body);
                        if (decl.find()) {
                            double value = Double.parseDouble(decl.group(2));
                            String unit = decl.group(3) == null ? "px" : decl.group(3);
                            decls.append(' ').append(decl.group(1)).append(": ").append(num(value * factor)).append(unit).append(';');
                        }
                    }
                }
                if (d != Density.NORMAL && FIXED_CHROME.stream().noneMatch(selectors::contains)) {
                    scaleLengths(decls, body, SPACING, d.factor());
                    if (d.factor() > 1) {
                        scaleLengths(decls, body, HEIGHTS, d.factor());
                    }
                }
                Matcher familyMatch = FAMILY.matcher(body);
                if (fam != null && familyMatch.find() && isUiFamily(familyMatch.group(1))) {
                    decls.append(" -fx-font-family: '").append(fam).append("';");
                }
                if (!decls.isEmpty() && !selectors.isEmpty() && !selectors.startsWith("font-face")) {
                    css.append(selectors).append(" {").append(decls).append(" }\n");
                }
            }
        }
        return css.toString();
    }

    /** Appends the declarations of {@code properties} found in {@code body}, every length times {@code factor}. */
    private static void scaleLengths(StringBuilder decls, String body, List<Pattern> properties, double factor) {
        for (Pattern property : properties) {
            Matcher decl = property.matcher(body);
            if (!decl.find()) {
                continue;
            }
            String[] parts = decl.group(2).trim().split("\\s+");
            if (decl.group(1).equals("-fx-padding")) {
                parts = verticalOnly(parts);
                if (parts == null) {
                    continue;
                }
            }
            StringBuilder value = new StringBuilder();
            for (String part : parts) {
                if (part.startsWith("=")) {
                    if (!LENGTH.matcher(part.substring(1)).matches()) {
                        value = null;
                        break;
                    }
                    value.append(value.isEmpty() ? "" : " ").append(part.substring(1));
                    continue;
                }
                Matcher length = LENGTH.matcher(part);
                if (!length.matches()) {
                    value = null; // -Infinity, USE_PREF_SIZE, lookups: leave the declaration alone
                    break;
                }
                double scaled = Double.parseDouble(length.group(1)) * factor;
                boolean em = "em".equals(length.group(2));
                // px to half pixels, em to hundredths
                String number = em ? String.valueOf(Math.round(scaled * 100) / 100.0) : num(Math.round(scaled * 2) / 2.0);
                value.append(value.isEmpty() ? "" : " ").append(number)
                        .append(length.group(2) == null ? "" : length.group(2));
            }
            if (value != null) {
                decls.append(' ').append(decl.group(1)).append(": ").append(value).append(';');
            }
        }
    }

    /**
     * Expands a padding to top / right / bottom / left and marks the horizontal sides as fixed
     * (prefix {@code =}), so only the vertical room changes.
     *
     * @return the four parts, or null for an unexpected form
     */
    private static String[] verticalOnly(String[] parts) {
        String[] four = switch (parts.length) {
            case 1 -> new String[] {parts[0], parts[0], parts[0], parts[0]};
            case 2 -> new String[] {parts[0], parts[1], parts[0], parts[1]};
            case 3 -> new String[] {parts[0], parts[1], parts[2], parts[1]};
            case 4 -> parts.clone();
            default -> null;
        };
        if (four != null) {
            four[1] = "=" + four[1];
            four[3] = "=" + four[3];
        }
        return four;
    }

    /** @return true for the UI font (Inter / system sans), false for monospace code fonts */
    static boolean isUiFamily(String declaration) {
        String lower = declaration.toLowerCase(Locale.ROOT);
        return (lower.contains("inter") || lower.contains("segoe") || lower.contains("sans-serif"))
                && !lower.contains("mono");
    }

    /**
     * An inline font size for nodes outside the shell stylesheets (plain popups), scaled by the
     * stored UI text size. Everything inside a shell window takes a {@code fxt-text-*} class instead.
     *
     * @param px the size at Normal
     * @return {@code -fx-font-size: …px;}
     */
    public static String fontSize(double px) {
        Size size = Size.NORMAL;
        try {
            size = Size.fromProperty(org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).get(SIZE_KEY));
        } catch (RuntimeException e) {
            // no settings (tests): Normal
        }
        return "-fx-font-size: " + px(px * size.factor()) + ";";
    }

    private static String em(double value) {
        return Math.round(value * 1000) / 1000.0 + "em";
    }

    private static String px(double value) {
        return num(value) + "px";
    }

    private static String num(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Double.toString(rounded);
    }

    /** @return the texts of {@link #SOURCES}, read once */
    static List<String> shellSources() {
        List<String> texts = sourceTexts;
        if (texts == null) {
            List<String> list = new ArrayList<>();
            for (String path : SOURCES) {
                try (InputStream in = Typography.class.getResourceAsStream(path)) {
                    if (in != null) {
                        list.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    // a missing sheet has no rules to scale
                }
            }
            texts = List.copyOf(list);
            sourceTexts = texts;
        }
        return texts;
    }
}
