package org.fxt.freexmltoolkit.controls.shell;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.theme.AccentPalette;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.HighContrast;
import org.fxt.freexmltoolkit.controls.theme.SyntaxSchemes;
import org.fxt.freexmltoolkit.controls.theme.Typography;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Single source of truth for applying the light/dark theme: swaps the theme
 * stylesheet on the scene, toggles the design-token root style class, and
 * persists the choice. Used by the boot path (FxtGui) and the shell SettingsPanel.
 *
 * <p>The setting {@code ui.theme} is {@code light}, {@code dark} or {@code system}; {@link Mode#SYSTEM}
 * follows the operating system's colour scheme, also while the application runs.
 *
 * <p>The accent colour ({@code ui.accent}: a preset id of {@link AccentPalette} or {@code #rrggbb})
 * overrides the primary-family tokens: {@link DesignTokens#setOverrides} for Java-drawn surfaces
 * and an "appearance" stylesheet (a {@code data:} URI) inserted right after
 * {@code design-tokens.css} wherever that sheet is declared, for CSS.
 */
public final class ThemeManager {

    /** The theme setting: a fixed theme or the operating system's. */
    public enum Mode {
        LIGHT, DARK, SYSTEM;

        /** @return the mode of a stored {@code ui.theme} value (light for unknown values) */
        public static Mode fromProperty(String value) {
            if (value == null) {
                return LIGHT;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "dark" -> DARK;
                case "system" -> SYSTEM;
                default -> LIGHT;
            };
        }

        /** @return the value stored in {@code ui.theme} */
        public String propertyValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Settings key of the accent colour. */
    public static final String ACCENT_KEY = "ui.accent";
    private static final String THEME_KEY = "ui.theme";
    private static final String DATA_CSS_PREFIX = "data:text/css;charset=utf-8;base64,";
    /** First line of the accent sheet; 33 bytes (a multiple of 3), so its base64 is a fixed prefix. */
    private static final String APPEARANCE_HEADER = "/* FreeXmlToolkit appearance  */\n";
    /** First line of the typography sheet; 33 bytes, see {@link #APPEARANCE_HEADER}. */
    private static final String TYPOGRAPHY_HEADER = "/* FreeXmlToolkit typography  */\n";
    private static final String APPEARANCE_SHEET_PREFIX = DATA_CSS_PREFIX + base64(APPEARANCE_HEADER);
    private static final String TYPOGRAPHY_SHEET_PREFIX = DATA_CSS_PREFIX + base64(TYPOGRAPHY_HEADER);

    /** The scene the theme was last applied to with {@link #apply} (the main window), for system-theme changes. */
    private static WeakReference<Scene> mainScene = new WeakReference<>(null);
    private static boolean systemListenerInstalled;
    /** The appearance stylesheet for the current accent, or null for the design's own colours. */
    private static volatile String appearanceSheet;
    private static volatile boolean accentLoaded;
    /** The typography stylesheet for the UI size and font, or null for the design's own. */
    private static volatile String typographySheet;
    private static volatile boolean typographyLoaded;

    private ThemeManager() {
    }

    /** Listeners notified (with the new theme) whenever {@link #apply} switches theme. */
    private static final CopyOnWriteArrayList<Consumer<DesignTokens.Theme>> LISTENERS = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener invoked with the new {@link DesignTokens.Theme} whenever the
     * theme changes. Used by {@code SemanticIcon} to recolour programmatically-tinted
     * icons (which CSS cannot reach) on a light/dark switch.
     */
    public static void addThemeChangeListener(Consumer<DesignTokens.Theme> listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /** Removes a listener registered with {@link #addThemeChangeListener}. */
    public static void removeThemeChangeListener(Consumer<DesignTokens.Theme> listener) {
        LISTENERS.remove(listener);
    }

    /** @return the current {@link DesignTokens.Theme} (from the persisted preference). */
    public static DesignTokens.Theme currentTheme() {
        return currentIsDark() ? DesignTokens.Theme.DARK : DesignTokens.Theme.LIGHT;
    }

    /**
     * Applies the light or dark theme to {@code scene} and persists {@code ui.theme}.
     * <p>Must be called on the JavaFX Application Thread.</p>
     * <p>The theme stylesheet is inserted into the stylesheet list of every parent that
     * declares {@code unified-shell.css} (the shell's base sheets are <em>parent</em>
     * stylesheets, which outrank scene stylesheets — a scene-level theme sheet would lose
     * every equal-specificity tie against them). Inserting immediately before
     * {@code unified-shell.css} lets the theme sheet override {@code app-theme.css} /
     * {@code fxt-theme.css} while the token-based shell rules keep winning their ties.</p>
     */
    public static void apply(Scene scene, boolean dark) {
        applyMode(scene, dark ? Mode.DARK : Mode.LIGHT);
    }

    /**
     * Applies a theme mode to {@code scene} and persists it in {@code ui.theme}. {@link Mode#SYSTEM}
     * shows the operating system's light or dark scheme and keeps following it.
     * <p>Must be called on the JavaFX Application Thread.</p>
     */
    public static void applyMode(Scene scene, Mode mode) {
        if (scene == null || mode == null) {
            return;
        }
        try {
            ServiceRegistry.get(PropertiesService.class).set(THEME_KEY, mode.propertyValue());
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests) — the visual switch is still applied
        }
        mainScene = new WeakReference<>(scene);
        if (mode == Mode.SYSTEM) {
            installSystemListener();
        }
        switchTo(scene, resolve(mode));
    }

    /** Applies the resolved theme to the scene and tells every listener. */
    private static void switchTo(Scene scene, boolean dark) {
        applyTo(scene, dark);
        DesignTokens.Theme theme = dark ? DesignTokens.Theme.DARK : DesignTokens.Theme.LIGHT;
        DesignTokens.publishTheme(theme);
        for (Consumer<DesignTokens.Theme> listener : LISTENERS) {
            try {
                listener.accept(theme);
            } catch (Throwable t) {
                // a misbehaving listener must not break the theme switch
            }
        }
    }

    /**
     * The visual half of {@link #apply}: swaps the theme stylesheet and the
     * {@code fxt-theme-dark/light} root class of {@code scene}, without persisting the choice
     * or notifying listeners. For secondary windows (e.g. the debugger's value viewer) that
     * follow the main window's theme.
     */
    public static void applyTo(Scene scene, boolean dark) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().removeIf(s -> s.endsWith("light-theme.css") || s.endsWith("dark-theme.css"));
        String css = dark ? "/css/dark-theme.css" : "/css/light-theme.css";
        var url = ThemeManager.class.getResource(css);
        String themeSheet = url != null ? url.toExternalForm() : null;
        var root = scene.getRoot();
        boolean inserted = root != null && insertThemeSheet(root, themeSheet);
        if (themeSheet != null && !inserted) {
            // No parent declares the shell sheets (e.g. bare test scenes) — scene level is fine.
            scene.getStylesheets().add(themeSheet);
        }
        if (root != null) {
            root.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
            root.getStyleClass().add(dark ? "fxt-theme-dark" : "fxt-theme-light");
        }
        ensureAccentLoaded();
        syncAppearanceSheet(scene);
    }

    /**
     * Recursively inserts {@code themeSheet} immediately before {@code unified-shell.css}
     * in every parent whose stylesheet list declares it (in the real shell: the
     * {@code tab_unified_shell.fxml} root and the {@code shell.fxml} fx:root — leaf-most
     * parent sheets win ties, so both lists need the theme sheet). Stale theme sheets are
     * removed from every visited parent.
     *
     * @return {@code true} if the sheet was inserted into at least one parent list
     */
    private static boolean insertThemeSheet(Parent parent, String themeSheet) {
        var sheets = parent.getStylesheets();
        boolean inserted = false;
        if (!sheets.isEmpty()) {
            sheets.removeIf(s -> s.endsWith("light-theme.css") || s.endsWith("dark-theme.css"));
            if (themeSheet != null) {
                for (int i = 0; i < sheets.size(); i++) {
                    if (sheets.get(i).endsWith("unified-shell.css")) {
                        sheets.add(i, themeSheet);
                        inserted = true;
                        break;
                    }
                }
            }
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent childParent) {
                inserted |= insertThemeSheet(childParent, themeSheet);
            }
        }
        return inserted;
    }

    /** @return {@code true} if the theme shown is dark (the persisted theme, or the system's for {@link Mode#SYSTEM}). */
    public static boolean currentIsDark() {
        return resolve(currentMode());
    }

    /** @return the persisted theme mode */
    public static Mode currentMode() {
        try {
            return Mode.fromProperty(ServiceRegistry.get(PropertiesService.class).get(THEME_KEY));
        } catch (Throwable t) {
            return Mode.LIGHT;
        }
    }

    /** @return whether the mode shows the dark theme */
    static boolean resolve(Mode mode) {
        return switch (mode) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> systemPrefersDark();
        };
    }

    /** @return whether the operating system uses a dark colour scheme (false when unknown) */
    public static boolean systemPrefersDark() {
        try {
            return Platform.getPreferences().getColorScheme() == javafx.application.ColorScheme.DARK;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Follows the operating system's scheme while the mode is {@link Mode#SYSTEM}. */
    private static void installSystemListener() {
        if (systemListenerInstalled) {
            return;
        }
        try {
            Platform.getPreferences().colorSchemeProperty().addListener((obs, oldScheme, newScheme) -> {
                Scene scene = mainScene.get();
                if (scene != null && currentMode() == Mode.SYSTEM) {
                    switchTo(scene, resolve(Mode.SYSTEM));
                }
            });
            systemListenerInstalled = true;
        } catch (Throwable t) {
            // no platform preferences (headless): the system mode stays on its first resolution
        }
    }

    // ------------------------------------------------------------------ accent colour

    /** @return the stored accent: a preset id or {@code #rrggbb} ({@link AccentPalette#DEFAULT_ID} when unset) */
    public static String currentAccent() {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).get(ACCENT_KEY);
            return value == null || value.isBlank() ? AccentPalette.DEFAULT_ID : value.trim();
        } catch (Throwable t) {
            return AccentPalette.DEFAULT_ID;
        }
    }

    /**
     * Sets the accent colour, persists it and applies it to every open window and Java-drawn view.
     * Must be called on the JavaFX Application Thread.
     *
     * @param accent a preset id of {@link AccentPalette#PRESETS} or {@code #rrggbb}; null or blank = default
     */
    public static void setAccent(String accent) {
        String value = accent == null || accent.isBlank() ? AccentPalette.DEFAULT_ID : accent.trim();
        try {
            ServiceRegistry.get(PropertiesService.class).set(ACCENT_KEY, value);
        } catch (Throwable ignored) {
            // not persisted (tests); still applied
        }
        loadAccent(value);
        applyAppearanceEverywhere();
    }

    /** Puts the rebuilt appearance sheet into every window and repaints the Java-drawn views. */
    private static void applyAppearanceEverywhere() {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window.getScene() != null) {
                syncAppearanceSheet(window.getScene());
            }
        }
        syncAppearanceSheet(mainScene.get()); // also before its window is shown
        DesignTokens.Theme theme = DesignTokens.currentTheme();
        DesignTokens.publishTheme(theme);
        for (Consumer<DesignTokens.Theme> listener : LISTENERS) {
            try {
                listener.accept(theme);
            } catch (Throwable t) {
                // a misbehaving listener must not break the switch
            }
        }
    }

    private static void ensureAccentLoaded() {
        if (!accentLoaded) {
            loadAccent(currentAccent());
        }
    }

    // ------------------------------------------------------------------ syntax colours

    /** @return the stored syntax scheme id */
    public static String currentSyntaxScheme() {
        return SyntaxSchemes.scheme(setting(SyntaxSchemes.SCHEME_KEY)).id();
    }

    /**
     * Sets the syntax scheme (keeping the user's own role colours), persists and applies it.
     * Must be called on the JavaFX Application Thread.
     *
     * @param schemeId a scheme id of {@link SyntaxSchemes#SCHEMES}; null = default
     */
    public static void setSyntaxScheme(String schemeId) {
        store(SyntaxSchemes.SCHEME_KEY, SyntaxSchemes.scheme(schemeId).id());
        loadAccent(currentAccent());
        applyAppearanceEverywhere();
    }

    /**
     * Sets or clears the user's own colour of a syntax role in a theme, persists and applies it.
     *
     * @param role the role
     * @param theme the theme the colour is for
     * @param color the colour, or null to use the scheme's again
     */
    public static void setSyntaxColor(SyntaxSchemes.Role role, DesignTokens.Theme theme, Color color) {
        store(role.key(theme), color == null ? "" : AccentPalette.hex(color));
        loadAccent(currentAccent());
        applyAppearanceEverywhere();
    }

    /** Clears every own role colour of both themes and goes back to the default scheme. */
    public static void resetSyntaxColors() {
        for (SyntaxSchemes.Role role : SyntaxSchemes.Role.values()) {
            store(role.key(DesignTokens.Theme.LIGHT), "");
            store(role.key(DesignTokens.Theme.DARK), "");
        }
        setSyntaxScheme(SyntaxSchemes.DEFAULT_ID);
    }

    // ------------------------------------------------------------------ high contrast

    /** @return true when high contrast is on */
    public static boolean isHighContrast() {
        return "true".equalsIgnoreCase(setting(HighContrast.KEY));
    }

    /**
     * Turns high contrast on or off, persists and applies it everywhere.
     * Must be called on the JavaFX Application Thread.
     */
    public static void setHighContrast(boolean on) {
        store(HighContrast.KEY, Boolean.toString(on));
        loadAccent(currentAccent());
        applyAppearanceEverywhere();
    }

    /** The JavaFX focus ring (Modena's looked-up colours) in the strongest text colour, no faint halo. */
    private static void focusRing(StringBuilder css, Color color) {
        if (color != null) {
            css.append("    -fx-focus-color: ").append(AccentPalette.hex(color)).append(";\n");
            css.append("    -fx-faint-focus-color: transparent;\n");
        }
    }

    /** @return a stored setting, or null (no settings service, e.g. in tests) */
    public static String setting(String key) {
        try {
            return ServiceRegistry.get(PropertiesService.class).get(key);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void store(String key, String value) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(key, value);
        } catch (Throwable ignored) {
            // not persisted (tests); still applied
        }
    }

    /** Derives the token overrides and the appearance stylesheet: accent colour plus syntax colours. */
    private static void loadAccent(String value) {
        accentLoaded = true;
        Color accent = AccentPalette.parse(value);
        Map<String, Color> light = new java.util.LinkedHashMap<>();
        Map<String, Color> dark = new java.util.LinkedHashMap<>();
        if (accent != null) {
            light.putAll(AccentPalette.light(accent));
            dark.putAll(AccentPalette.dark(accent));
        }
        String scheme = setting(SyntaxSchemes.SCHEME_KEY);
        light.putAll(SyntaxSchemes.overrides(scheme, ThemeManager::setting, DesignTokens.Theme.LIGHT));
        dark.putAll(SyntaxSchemes.overrides(scheme, ThemeManager::setting, DesignTokens.Theme.DARK));
        boolean highContrast = isHighContrast();
        if (highContrast) {
            light.putAll(HighContrast.overrides(light, DesignTokens.Theme.LIGHT));
            dark.putAll(HighContrast.overrides(dark, DesignTokens.Theme.DARK));
        }
        if (light.isEmpty() && dark.isEmpty()) {
            DesignTokens.setOverrides(null, null);
            appearanceSheet = null;
            return;
        }
        DesignTokens.setOverrides(light, dark);
        StringBuilder css = new StringBuilder(APPEARANCE_HEADER).append("/* accent ").append(value).append(" */\n.root {\n");
        light.forEach((name, color) -> css.append("    ").append(name).append(": ").append(AccentPalette.hex(color)).append(";\n"));
        if (highContrast) {
            focusRing(css, light.get(DesignTokens.ColorToken.TEXT_PRIMARY.cssVariable()));
        }
        css.append("}\n.root.fxt-theme-dark {\n");
        dark.forEach((name, color) -> css.append("    ").append(name).append(": ").append(AccentPalette.hex(color)).append(";\n"));
        if (highContrast) {
            focusRing(css, dark.get(DesignTokens.ColorToken.TEXT_PRIMARY.cssVariable()));
        }
        css.append("}\n");
        appearanceSheet = DATA_CSS_PREFIX + base64(css.toString());
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ UI text size and font

    /** @return the stored UI text size */
    public static Typography.Size currentFontSize() {
        try {
            return Typography.Size.fromProperty(ServiceRegistry.get(PropertiesService.class).get(Typography.SIZE_KEY));
        } catch (Throwable t) {
            return Typography.Size.NORMAL;
        }
    }

    /** @return the stored UI font family ({@link Typography#DEFAULT_FAMILY} when unset) */
    public static String currentUiFont() {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).get(Typography.FAMILY_KEY);
            return value == null || value.isBlank() ? Typography.DEFAULT_FAMILY : value.trim();
        } catch (Throwable t) {
            return Typography.DEFAULT_FAMILY;
        }
    }

    /**
     * Sets the UI text size and font, persists them and applies them to every open window.
     * Must be called on the JavaFX Application Thread.
     *
     * @param size the text size; null = normal
     * @param family the UI font; null/blank = the design's Inter
     */
    public static void setTypography(Typography.Size size, String family) {
        setTypography(size, family, currentDensity());
    }

    /** @return the stored density */
    public static Typography.Density currentDensity() {
        return Typography.Density.fromProperty(setting(Typography.DENSITY_KEY));
    }

    /**
     * Sets the UI text size, font and density, persists them and applies them to every open window.
     * Must be called on the JavaFX Application Thread.
     */
    public static void setTypography(Typography.Size size, String family, Typography.Density density) {
        Typography.Density d = density == null ? Typography.Density.NORMAL : density;
        store(Typography.DENSITY_KEY, d.id());
        Typography.Size s = size == null ? Typography.Size.NORMAL : size;
        String f = family == null || family.isBlank() ? Typography.DEFAULT_FAMILY : family.trim();
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            props.set(Typography.SIZE_KEY, s.id());
            props.set(Typography.FAMILY_KEY, f.equals(Typography.DEFAULT_FAMILY) ? "" : f);
        } catch (Throwable ignored) {
            // not persisted (tests); still applied
        }
        loadTypography(s, f, d);
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window.getScene() != null) {
                syncAppearanceSheet(window.getScene());
            }
        }
        syncAppearanceSheet(mainScene.get());
    }

    private static void loadTypography(Typography.Size size, String family, Typography.Density density) {
        typographyLoaded = true;
        double base;
        try {
            base = javafx.scene.text.Font.getDefault().getSize();
        } catch (Throwable t) {
            base = 13;
        }
        String css = Typography.css(size, family, density, base);
        typographySheet = css == null ? null : DATA_CSS_PREFIX + base64(TYPOGRAPHY_HEADER + css);
    }

    /** @return the typography stylesheet URL for the UI size and font, or null for the design's */
    public static String typographySheet() {
        if (!typographyLoaded) {
            loadTypography(currentFontSize(), currentUiFont(), currentDensity());
        }
        return typographySheet;
    }

    /** @return the appearance stylesheet URL for the current accent, or null for the design's colours */
    public static String appearanceSheet() {
        ensureAccentLoaded();
        return appearanceSheet;
    }

    /**
     * Puts the current appearance stylesheet right after {@code design-tokens.css} and the
     * typography stylesheet right after {@code unified-shell.css} in every stylesheet list of the
     * scene (scene and parents) that declares them, removing stale ones.
     */
    public static void syncAppearanceSheet(Scene scene) {
        if (scene == null) {
            return;
        }
        String sheet = appearanceSheet();
        String typography = typographySheet();
        syncSheetList(scene.getStylesheets(), sheet, typography);
        if (scene.getRoot() != null) {
            syncParents(scene.getRoot(), sheet, typography);
        }
    }

    /** Same as {@link #syncAppearanceSheet(Scene)} for a node tree not (yet) in a scene, e.g. a popup's content. */
    public static void syncAppearanceSheet(Parent root) {
        if (root != null) {
            syncParents(root, appearanceSheet(), typographySheet());
        }
    }

    private static void syncParents(Parent parent, String sheet, String typography) {
        syncSheetList(parent.getStylesheets(), sheet, typography);
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent childParent) {
                syncParents(childParent, sheet, typography);
            }
        }
    }

    /**
     * Adds the generated sheets to a list holding the shell sheets.
     *
     * @param sheets a stylesheet list (left alone unless it declares the shell sheets)
     * @return the list with the generated sheets in place
     */
    public static List<String> withGeneratedSheets(List<String> sheets) {
        return arranged(sheets, appearanceSheet(), typographySheet());
    }

    private static void syncSheetList(ObservableList<String> sheets, String sheet, String typography) {
        if (sheets.isEmpty()) {
            return;
        }
        List<String> wanted = arranged(sheets, sheet, typography);
        if (!wanted.equals(sheets)) {
            sheets.setAll(wanted);
        }
    }

    private static List<String> arranged(List<String> sheets, String sheet, String typography) {
        List<String> wanted = new java.util.ArrayList<>(sheets);
        wanted.removeIf(s -> s.startsWith(APPEARANCE_SHEET_PREFIX) || s.startsWith(TYPOGRAPHY_SHEET_PREFIX));
        int tokens = indexOfSheet(wanted, "design-tokens.css");
        if (sheet != null && tokens >= 0) {
            wanted.add(tokens + 1, sheet);
        }
        int shell = indexOfSheet(wanted, "unified-shell.css");
        if (typography != null && shell >= 0) {
            wanted.add(shell + 1, typography);
        }
        return wanted;
    }

    private static int indexOfSheet(List<String> sheets, String name) {
        for (int i = 0; i < sheets.size(); i++) {
            if (sheets.get(i).endsWith(name)) {
                return i;
            }
        }
        return -1;
    }
}
