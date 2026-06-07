package org.fxt.freexmltoolkit.controls.shell;

import javafx.scene.Scene;

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

    /** Applies the light or dark theme to {@code scene} and persists {@code ui.theme}. */
    public static void apply(Scene scene, boolean dark) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().removeIf(s -> s.contains("light-theme.css") || s.contains("dark-theme.css"));
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
