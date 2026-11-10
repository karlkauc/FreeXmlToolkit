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
        return css(size, family, baseFontPx, shellSources());
    }

    /** {@link #css(Size, String, double)} over given stylesheet texts (for tests). */
    static String css(Size size, String family, double baseFontPx, List<String> sources) {
        Size s = size == null ? Size.NORMAL : size;
        String fam = family == null || family.isBlank() || family.trim().equalsIgnoreCase(DEFAULT_FAMILY)
                ? null : family.trim().replace("'", "").replace("\"", "");
        if (s == Size.NORMAL && fam == null) {
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

    /** @return true for the UI font (Inter / system sans), false for monospace code fonts */
    static boolean isUiFamily(String declaration) {
        String lower = declaration.toLowerCase(Locale.ROOT);
        return (lower.contains("inter") || lower.contains("segoe") || lower.contains("sans-serif"))
                && !lower.contains("mono");
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
