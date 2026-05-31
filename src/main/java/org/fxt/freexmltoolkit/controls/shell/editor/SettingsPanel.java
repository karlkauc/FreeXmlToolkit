package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
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
 * driven, persisted as {@code ui.theme}); editor formatting (indent spaces,
 * auto-format on load); and HTTP proxy (system vs. manual host/port). Editor and
 * proxy settings persist via {@link PropertiesService} on "Save Settings".
 */
public class SettingsPanel extends VBox {

    private final ToggleButton light = themeButton("Light", "bi-sun");
    private final ToggleButton dark = themeButton("Dark", "bi-moon");
    private final Spinner<Integer> indentSpaces = new Spinner<>(1, 8, 2);
    private final CheckBox autoFormat = new CheckBox("Auto-format after loading");
    private final CheckBox useSystemProxy = new CheckBox("Use system proxy");
    private final TextField proxyHost = new TextField();
    private final TextField proxyPort = new TextField();

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

        Label editorLabel = new Label("EDITOR");
        editorLabel.getStyleClass().add("fxt-side-panel-title");
        indentSpaces.setEditable(true);
        indentSpaces.setPrefWidth(80);

        Label proxyLabel = new Label("HTTP PROXY");
        proxyLabel.getStyleClass().add("fxt-side-panel-title");
        proxyHost.setPromptText("host");
        proxyPort.setPromptText("port");
        proxyHost.disableProperty().bind(useSystemProxy.selectedProperty());
        proxyPort.disableProperty().bind(useSystemProxy.selectedProperty());

        Button save = new Button("Save Settings", iconGraphic("bi-save"));
        save.getStyleClass().add("fxt-tool-button");
        save.setOnAction(e -> saveSettings());

        loadSettings();

        getChildren().addAll(title, themeLabel, new HBox(6, light, dark),
                editorLabel, new HBox(6, new Label("Indent:"), indentSpaces), autoFormat,
                proxyLabel, useSystemProxy, proxyHost, proxyPort,
                save);
    }

    /** Loads the current editor + proxy settings into the controls. */
    private void loadSettings() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            indentSpaces.getValueFactory().setValue(props.getXmlIndentSpaces());
            autoFormat.setSelected(props.isXmlAutoFormatAfterLoading());
            useSystemProxy.setSelected(!"false".equalsIgnoreCase(orEmpty(props.get("useSystemProxy"))));
            proxyHost.setText(orEmpty(props.get("http.proxy.host")));
            proxyPort.setText(orEmpty(props.get("http.proxy.port")));
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests) — controls keep their defaults
        }
    }

    /** Persists the editor + proxy settings. */
    public void saveSettings() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            props.setXmlIndentSpaces(indentSpaces.getValue());
            props.setXmlAutoFormatAfterLoading(autoFormat.isSelected());
            props.set("useSystemProxy", String.valueOf(useSystemProxy.isSelected()));
            props.set("manualProxy", String.valueOf(!useSystemProxy.isSelected()));
            props.set("http.proxy.host", proxyHost.getText());
            props.set("http.proxy.port", proxyPort.getText());
        } catch (Throwable ignored) {
            // properties service unavailable — nothing to persist
        }
    }

    /** @return the indent-spaces control value (for tests/observers). */
    public int getIndentValue() {
        return indentSpaces.getValue();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private IconifyIcon iconGraphic(String literal) {
        IconifyIcon graphic = new IconifyIcon(literal);
        graphic.setIconSize(16);
        return graphic;
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
