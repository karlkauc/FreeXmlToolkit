package org.fxt.freexmltoolkit.controls.shell;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
     */
    public static void apply(Scene scene, boolean dark) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().removeIf(s -> s.endsWith("light-theme.css") || s.endsWith("dark-theme.css"));
        String css = dark ? "/css/dark-theme.css" : "/css/light-theme.css";
        var url = ThemeManager.class.getResource(css);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
        var root = scene.getRoot();
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
        for (Consumer<DesignTokens.Theme> listener : LISTENERS) {
            try {
                listener.accept(theme);
            } catch (Throwable t) {
                // a misbehaving listener must not break the theme switch
            }
        }
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
