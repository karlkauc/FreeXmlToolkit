package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * The Settings activity side panel: light/dark theme switch (design-token
 * driven). Toggling swaps the {@code fxt-theme-dark} root style class — so the
 * token-based shell re-themes live — and persists the choice via
 * {@link PropertiesService} ({@code ui.theme}, the existing key).
 */
public class SettingsPanel extends VBox {

    private final ToggleButton light = themeButton("Light", "bi-sun");
    private final ToggleButton dark = themeButton("Dark", "bi-moon");

    public SettingsPanel() {
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("SETTINGS");
        title.getStyleClass().add("fxt-side-panel-title");

        Label themeLabel = new Label("THEME");
        themeLabel.getStyleClass().add("fxt-side-panel-title");

        ToggleGroup group = new ToggleGroup();
        light.setToggleGroup(group);
        dark.setToggleGroup(group);
        boolean isDark = DesignTokens.Theme.fromProperty(currentThemeProperty()) == DesignTokens.Theme.DARK;
        (isDark ? dark : light).setSelected(true);

        light.setOnAction(e -> applyTheme(false));
        dark.setOnAction(e -> applyTheme(true));

        getChildren().addAll(title, themeLabel, new HBox(6, light, dark));
    }

    /** Applies the theme to the scene and persists it (for tests/observers). */
    public void applyTheme(boolean darkTheme) {
        (darkTheme ? dark : light).setSelected(true);
        if (getScene() != null && getScene().getRoot() != null) {
            var root = getScene().getRoot();
            root.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
            root.getStyleClass().add(darkTheme ? "fxt-theme-dark" : "fxt-theme-light");
        }
        try {
            ServiceRegistry.get(PropertiesService.class).set("ui.theme", darkTheme ? "dark" : "light");
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests) — visual switch still applied
        }
    }

    /** @return {@code true} if Dark is currently selected (for tests/observers). */
    public boolean isDarkSelected() {
        return dark.isSelected();
    }

    private String currentThemeProperty() {
        try {
            return ServiceRegistry.get(PropertiesService.class).get("ui.theme");
        } catch (Throwable t) {
            return null;
        }
    }

    private ToggleButton themeButton(String text, String icon) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        ToggleButton button = new ToggleButton(text, graphic);
        button.getStyleClass().add("fxt-view-seg");
        button.setFocusTraversable(false);
        return button;
    }
}
