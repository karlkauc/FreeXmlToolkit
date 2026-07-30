package org.fxt.freexmltoolkit.controls.shell;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Single source of truth for applying the light/dark theme: swaps the theme
 * stylesheet on the scene, toggles the design-token root style class, and
 * persists the choice. Used by the boot path (FxtGui) and the shell SettingsPanel.
 */
public final class ThemeManager {

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
        try {
            ServiceRegistry.get(PropertiesService.class).set("ui.theme", dark ? "dark" : "light");
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests) — the visual switch is still applied
        }
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

    /** @return {@code true} if the persisted theme is dark. */
    public static boolean currentIsDark() {
        try {
            return "dark".equals(ServiceRegistry.get(PropertiesService.class).get("ui.theme"));
        } catch (Throwable t) {
            return false;
        }
    }
}
