package org.fxt.freexmltoolkit.controls.shared;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Parent;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * The user's code-editor font: family, size, line spacing and whether Ctrl+wheel zoom is kept.
 * One application-wide value ({@link #settingsProperty()}) that every code editor follows live
 * (XML/XSD/XSLT/XQuery editor, JSON editor, query console, output panels).
 *
 * <p>The size is a point size for the document editors; the smaller code panels
 * ({@link CodeAreaFontZoom}) use the same number in px, as they always did.
 */
public final class EditorFont {

    private static final Logger logger = LogManager.getLogger(EditorFont.class);

    /** Settings key of the font family (empty = {@link #DEFAULT_FAMILY}). */
    public static final String FAMILY_KEY = "editor.font.family";
    /** Settings key of the size (points). */
    public static final String SIZE_KEY = "editor.font.size";
    /** Settings key of the line spacing factor (1.0–2.0). */
    public static final String LINE_SPACING_KEY = "editor.lineSpacing";
    /** Settings key: does Ctrl+wheel zoom change (and keep) the editor size. */
    public static final String REMEMBER_ZOOM_KEY = "editor.zoom.remember";

    /** The bundled monospace font. */
    public static final String DEFAULT_FAMILY = "JetBrains Mono";
    public static final int DEFAULT_SIZE = 11;
    public static final double DEFAULT_LINE_SPACING = 1.0;
    /** Range offered on the Settings page. */
    public static final int MIN_SETTING_SIZE = 8;
    public static final int MAX_SETTING_SIZE = 32;
    /** Range reachable by zooming. */
    public static final int MIN_SIZE = 6;
    public static final int MAX_SIZE = 72;
    public static final double MIN_LINE_SPACING = 1.0;
    public static final double MAX_LINE_SPACING = 2.0;

    /**
     * Families tried after the chosen one, so a missing font still gives a monospace face. JavaFX
     * CSS takes a single family (a comma list breaks the whole inline style), so the first
     * installed one is resolved here.
     */
    private static final List<String> FALLBACKS = List.of(DEFAULT_FAMILY, "Consolas", "Menlo", "Monaco",
            "DejaVu Sans Mono", "Monospaced");

    /** Node property holding the line-spacing stylesheet an area got, to replace it on change. */
    private static final String SHEET_KEY = "fxt.editorFont.sheet";

    /** One editor-font setting. */
    public record Settings(String family, int size, double lineSpacing, boolean rememberZoom) {

        /** Normalises the values (blank family → default, size and spacing clamped). */
        public Settings {
            family = family == null || family.isBlank() ? DEFAULT_FAMILY : family.trim();
            size = Math.clamp(size, MIN_SIZE, MAX_SIZE);
            lineSpacing = Math.clamp(Double.isNaN(lineSpacing) ? DEFAULT_LINE_SPACING : lineSpacing,
                    MIN_LINE_SPACING, MAX_LINE_SPACING);
        }

        /** @return the built-in defaults */
        public static Settings defaults() {
            return new Settings(DEFAULT_FAMILY, DEFAULT_SIZE, DEFAULT_LINE_SPACING, true);
        }

        /** @return a copy with another size */
        public Settings withSize(int newSize) {
            return new Settings(family, newSize, lineSpacing, rememberZoom);
        }

        /** @return the CSS font family: the chosen one if installed, else the first installed fallback */
        public String cssFamily() {
            return "'" + resolveFamily(family).replace("'", "") + "'";
        }
    }

    private static final ReadOnlyObjectWrapper<Settings> SETTINGS = new ReadOnlyObjectWrapper<>(load());
    private static PauseTransition pendingSave;
    private static List<String> monospaceFamilies;

    private EditorFont() {
    }

    /** @return the current setting, observable; changes arrive on the FX thread */
    public static ReadOnlyObjectProperty<Settings> settingsProperty() {
        return SETTINGS.getReadOnlyProperty();
    }

    /** @return the current setting */
    public static Settings current() {
        return SETTINGS.get();
    }

    /**
     * Applies and stores a new setting: every editor follows at once.
     *
     * @param settings the new setting
     */
    public static void update(Settings settings) {
        Settings value = settings == null ? Settings.defaults() : settings;
        SETTINGS.set(value);
        persist(value);
    }

    /**
     * A zoom step in an editor: with "remember zoom" on, the size becomes the setting of every
     * editor and is stored (once the wheel rests).
     *
     * @param size the zoomed size
     * @return true when the zoom changed the setting, false when the editor must zoom on its own
     */
    public static boolean zoomTo(int size) {
        Settings value = current();
        if (!value.rememberZoom()) {
            return false;
        }
        Settings zoomed = value.withSize(size);
        if (zoomed.size() != value.size()) {
            SETTINGS.set(zoomed);
            persistLater();
        }
        return true;
    }

    /** Re-reads the stored values (after "Reset to defaults" or an import). */
    public static void reload() {
        SETTINGS.set(load());
    }

    // ------------------------------------------------------------------ CSS

    /**
     * @param settings the editor font
     * @param size font size
     * @param unit {@code pt} or {@code px}
     * @return an inline style with the setting's family and the given size
     */
    public static String inlineStyle(Settings settings, int size, String unit) {
        return "-fx-font-size: " + size + unit + "; -fx-font-family: " + settings.cssFamily() + ";";
    }

    /**
     * Gives a RichTextFX area the line spacing: extra padding above and below every paragraph
     * (so the gutter moves with the text) and the same spacing between wrapped lines.
     *
     * @param area the code area (or any parent holding {@code .paragraph-box} nodes)
     * @param sizePx the area's font size in px
     * @param factor the line spacing factor
     */
    public static void applyLineSpacing(Parent area, double sizePx, double factor) {
        Object old = area.getProperties().remove(SHEET_KEY);
        if (old instanceof String sheet) {
            area.getStylesheets().remove(sheet);
        }
        double extra = Math.max(0, (factor - 1.0) * sizePx * 1.2);
        if (extra < 0.5) {
            return;
        }
        double half = extra / 2;
        String css = String.format(Locale.ROOT,
                ".paragraph-box { -fx-padding: %.1fpx 0 %.1fpx 0; } .paragraph-text { -fx-line-spacing: %.1fpx; }",
                half, half, extra);
        String sheet = "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
        area.getProperties().put(SHEET_KEY, sheet);
        area.getStylesheets().add(sheet);
    }

    /** @return points as px (96 dpi) */
    public static double ptToPx(double pt) {
        return pt * 4.0 / 3.0;
    }

    // ------------------------------------------------------------------ installed fonts

    /**
     * @param family the wanted family
     * @return {@code family} when installed, else the first installed fallback ("Monospaced", the
     *         logical JavaFX font, always exists)
     */
    public static String resolveFamily(String family) {
        java.util.Set<String> installed = installedFamilies();
        if (installed.isEmpty() || installed.contains(family.toLowerCase(Locale.ROOT))) {
            return family;
        }
        for (String fallback : FALLBACKS) {
            if (installed.contains(fallback.toLowerCase(Locale.ROOT))) {
                return fallback;
            }
        }
        return "Monospaced";
    }

    private static java.util.Set<String> installedFamilies;

    private static synchronized java.util.Set<String> installedFamilies() {
        if (installedFamilies == null) {
            try {
                java.util.Set<String> names = new java.util.HashSet<>();
                for (String name : Font.getFamilies()) {
                    names.add(name.toLowerCase(Locale.ROOT));
                }
                installedFamilies = names;
            } catch (RuntimeException | Error e) {
                return java.util.Set.of();
            }
        }
        return installedFamilies;
    }

    /**
     * @return the installed monospace families, the bundled default first; measured once (FX thread)
     */
    public static synchronized List<String> monospaceFamilies() {
        if (monospaceFamilies == null) {
            TreeSet<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String family : Font.getFamilies()) {
                if (isMonospace(family)) {
                    found.add(family);
                }
            }
            found.remove(DEFAULT_FAMILY);
            List<String> list = new ArrayList<>();
            list.add(DEFAULT_FAMILY);
            list.addAll(found);
            monospaceFamilies = List.copyOf(list);
        }
        return monospaceFamilies;
    }

    /** @return true when narrow and wide letters have the same advance in {@code family} */
    static boolean isMonospace(String family) {
        try {
            Font font = Font.font(family, 12);
            if (!font.getFamily().equalsIgnoreCase(family)) {
                return false;
            }
            Text narrow = new Text("iiiii");
            narrow.setFont(font);
            Text wide = new Text("WWWWW");
            wide.setFont(font);
            double a = narrow.getLayoutBounds().getWidth();
            double b = wide.getLayoutBounds().getWidth();
            return a > 0 && Math.abs(a - b) < 0.5;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ storage

    private static Settings load() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            return new Settings(props.get(FAMILY_KEY),
                    parseInt(props.get(SIZE_KEY), DEFAULT_SIZE),
                    parseDouble(props.get(LINE_SPACING_KEY), DEFAULT_LINE_SPACING),
                    !"false".equalsIgnoreCase(props.get(REMEMBER_ZOOM_KEY)));
        } catch (RuntimeException e) {
            logger.debug("Editor font settings not available, using defaults: {}", e.getMessage());
            return Settings.defaults();
        }
    }

    private static void persist(Settings value) {
        if (pendingSave != null) {
            pendingSave.stop();
        }
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            props.set(FAMILY_KEY, value.family().equals(DEFAULT_FAMILY) ? "" : value.family());
            props.set(SIZE_KEY, Integer.toString(value.size()));
            props.set(LINE_SPACING_KEY, String.format(Locale.ROOT, "%.2f", value.lineSpacing()));
            props.set(REMEMBER_ZOOM_KEY, Boolean.toString(value.rememberZoom()));
        } catch (RuntimeException e) {
            logger.warn("Could not store the editor font: {}", e.getMessage());
        }
    }

    /** Stores a zoomed size once the wheel has rested, not on every notch. */
    private static void persistLater() {
        if (pendingSave == null) {
            pendingSave = new PauseTransition(Duration.millis(600));
        }
        pendingSave.setOnFinished(e -> persistSize(current()));
        pendingSave.playFromStart();
    }

    private static void persistSize(Settings value) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(SIZE_KEY, Integer.toString(value.size()));
        } catch (RuntimeException e) {
            logger.warn("Could not store the editor font size: {}", e.getMessage());
        }
    }

    private static int parseInt(String text, int fallback) {
        try {
            return text == null || text.isBlank() ? fallback : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return text == null || text.isBlank() ? fallback : Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
