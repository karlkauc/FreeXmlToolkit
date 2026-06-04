package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.XmlParserType;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * The Settings activity side panel. Surfaces the full {@link PropertiesService} configuration in
 * sections — Theme, Editor (XML/JSON indent, auto-format, pretty-print), XSD (auto-save, backups),
 * Parser (XML parser engine, XSLT extensions), Temp &amp; Cache (system/custom temp folder + clear),
 * General (update check, small icons) and HTTP Proxy — loading the saved values into the controls
 * and persisting them on "Save Settings". Theme switches apply live.
 */
public class SettingsPanel extends VBox {

    private final ToggleButton light = themeButton("Light", "bi-sun");
    private final ToggleButton dark = themeButton("Dark", "bi-moon");

    // Editor
    private final Spinner<Integer> indentSpaces = new Spinner<>(1, 8, 2);
    private final Spinner<Integer> jsonIndent = new Spinner<>(1, 8, 2);
    private final CheckBox autoFormat = new CheckBox("Auto-format after loading");
    private final CheckBox xsdPrettyPrint = new CheckBox("Pretty-print XSD on save");
    private final CheckBox schematronPretty = new CheckBox("Pretty-print Schematron on load");

    // XSD
    private final CheckBox xsdAutoSave = new CheckBox("Auto-save");
    private final Spinner<Integer> xsdAutoSaveInterval = new Spinner<>(1, 120, 5);
    private final CheckBox xsdBackup = new CheckBox("Create backups on save");
    private final Spinner<Integer> xsdBackupVersions = new Spinner<>(1, 50, 5);
    private final CheckBox backupSeparateDir = new CheckBox("Use a separate backup directory");
    private final TextField backupDir = new TextField();

    // Parser
    private final ComboBox<XmlParserType> parserType = new ComboBox<>();
    private final CheckBox xsltExtensions = new CheckBox("Allow XSLT extension functions");

    // Temp & cache
    private final CheckBox useSystemTemp = new CheckBox("Use system temp folder");
    private final TextField customTempDir = new TextField();
    private final Label tempStatus = new Label();

    // General
    private final CheckBox updateCheck = new CheckBox("Check for updates on startup");
    private final CheckBox smallIcons = new CheckBox("Use small icons");

    // Proxy
    private final CheckBox useSystemProxy = new CheckBox("Use system proxy");
    private final TextField proxyHost = new TextField();
    private final TextField proxyPort = new TextField();

    public SettingsPanel() {
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("SETTINGS");
        title.getStyleClass().add("fxt-side-panel-title");

        ToggleGroup themeGroup = new ToggleGroup();
        light.setToggleGroup(themeGroup);
        dark.setToggleGroup(themeGroup);
        boolean isDark = DesignTokens.Theme.fromProperty(currentThemeProperty()) == DesignTokens.Theme.DARK;
        (isDark ? dark : light).setSelected(true);
        light.setOnAction(e -> applyTheme(false));
        dark.setOnAction(e -> applyTheme(true));

        indentSpaces.setEditable(true);
        jsonIndent.setEditable(true);
        xsdAutoSaveInterval.setEditable(true);
        xsdBackupVersions.setEditable(true);
        parserType.getItems().setAll(XmlParserType.values());
        backupDir.setPromptText("backup directory");
        backupDir.disableProperty().bind(backupSeparateDir.selectedProperty().not());
        customTempDir.setPromptText("custom temp folder");
        customTempDir.disableProperty().bind(useSystemTemp.selectedProperty());
        tempStatus.getStyleClass().add("fxt-placeholder-text");

        proxyHost.setPromptText("host");
        proxyPort.setPromptText("port");
        proxyHost.disableProperty().bind(useSystemProxy.selectedProperty());
        proxyPort.disableProperty().bind(useSystemProxy.selectedProperty());

        Button clearTemp = new Button("Clear Temp Folder", iconGraphic("bi-trash"));
        clearTemp.getStyleClass().add("fxt-tool-button");
        clearTemp.setOnAction(e -> tempStatus.setText("Cleared " + clearTempFolder() + " file(s)."));

        Button save = new Button("Save Settings", iconGraphic("bi-save"));
        save.getStyleClass().add("fxt-tool-button");
        save.setOnAction(e -> {
            saveSettings();
            tempStatus.setText("Settings saved.");
        });

        loadSettings();

        VBox content = new VBox(8,
                title,
                section("THEME"), new HBox(6, light, dark),
                section("EDITOR"),
                labeled("XML indent:", indentSpaces), labeled("JSON indent:", jsonIndent),
                autoFormat, xsdPrettyPrint, schematronPretty,
                section("XSD"),
                xsdAutoSave, labeled("Interval (min):", xsdAutoSaveInterval),
                xsdBackup, labeled("Keep versions:", xsdBackupVersions),
                backupSeparateDir, browseRow(backupDir, this::chooseBackupDir),
                section("PARSER"),
                labeled("XML parser:", parserType), xsltExtensions,
                section("TEMP & CACHE"),
                useSystemTemp, browseRow(customTempDir, this::chooseTempDir),
                fill(clearTemp), tempStatus,
                section("GENERAL"),
                updateCheck, smallIcons,
                section("HTTP PROXY"),
                useSystemProxy, proxyHost, proxyPort,
                fill(save));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("fxt-settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-side-panel-title");
        return label;
    }

    private static HBox labeled(String text, Region control) {
        HBox row = new HBox(6, new Label(text), control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static HBox browseRow(TextField field, Runnable onBrowse) {
        HBox.setHgrow(field, Priority.ALWAYS);
        Button browse = new Button("…");
        browse.setOnAction(e -> onBrowse.run());
        HBox row = new HBox(6, field, browse);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Region fill(Region node) {
        node.setMaxWidth(Double.MAX_VALUE);
        return node;
    }

    private void chooseBackupDir() {
        chooseDirInto(backupDir);
    }

    private void chooseTempDir() {
        chooseDirInto(customTempDir);
    }

    private void chooseDirInto(TextField field) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        File dir = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (dir != null) {
            field.setText(dir.getAbsolutePath());
        }
    }

    /** Deletes the files directly inside the effective temp folder. @return the number deleted. */
    public int clearTempFolder() {
        try {
            String folder = ServiceRegistry.get(PropertiesService.class).getTempFolder();
            if (folder == null || folder.isBlank()) {
                return 0;
            }
            File dir = new File(folder);
            File[] files = dir.isDirectory() ? dir.listFiles() : null;
            if (files == null) {
                return 0;
            }
            int deleted = 0;
            for (File file : files) {
                if (file.isFile() && file.delete()) {
                    deleted++;
                }
            }
            return deleted;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Loads the persisted settings into the controls. */
    private void loadSettings() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            indentSpaces.getValueFactory().setValue(props.getXmlIndentSpaces());
            jsonIndent.getValueFactory().setValue(props.getJsonIndentSpaces());
            autoFormat.setSelected(props.isXmlAutoFormatAfterLoading());
            xsdPrettyPrint.setSelected(props.isXsdPrettyPrintOnSave());
            schematronPretty.setSelected(props.isSchematronPrettyPrintOnLoad());
            xsdAutoSave.setSelected(props.isXsdAutoSaveEnabled());
            xsdAutoSaveInterval.getValueFactory().setValue(props.getXsdAutoSaveInterval());
            xsdBackup.setSelected(props.isXsdBackupEnabled());
            xsdBackupVersions.getValueFactory().setValue(props.getXsdBackupVersions());
            backupSeparateDir.setSelected(props.isBackupUseSeparateDirectory());
            backupDir.setText(orEmpty(props.getBackupDirectory()));
            parserType.setValue(props.getXmlParserType());
            xsltExtensions.setSelected(props.isXsltExtensionsAllowed());
            useSystemTemp.setSelected(props.isUseSystemTempFolder());
            customTempDir.setText(orEmpty(props.getCustomTempFolder()));
            updateCheck.setSelected(props.isUpdateCheckEnabled());
            smallIcons.setSelected(props.isUseSmallIcons());
            useSystemProxy.setSelected(!"false".equalsIgnoreCase(orEmpty(props.get("useSystemProxy"))));
            proxyHost.setText(orEmpty(props.get("http.proxy.host")));
            proxyPort.setText(orEmpty(props.get("http.proxy.port")));
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests) — controls keep their defaults
        }
    }

    /** Persists all settings. */
    public void saveSettings() {
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            props.setXmlIndentSpaces(indentSpaces.getValue());
            props.setJsonIndentSpaces(jsonIndent.getValue());
            props.setXmlAutoFormatAfterLoading(autoFormat.isSelected());
            props.setXsdPrettyPrintOnSave(xsdPrettyPrint.isSelected());
            props.setSchematronPrettyPrintOnLoad(schematronPretty.isSelected());
            props.setXsdAutoSaveEnabled(xsdAutoSave.isSelected());
            props.setXsdAutoSaveInterval(xsdAutoSaveInterval.getValue());
            props.setXsdBackupEnabled(xsdBackup.isSelected());
            props.setXsdBackupVersions(xsdBackupVersions.getValue());
            props.setBackupUseSeparateDirectory(backupSeparateDir.isSelected());
            props.setBackupDirectory(backupDir.getText());
            if (parserType.getValue() != null) {
                props.setXmlParserType(parserType.getValue());
            }
            props.setXsltExtensionsAllowed(xsltExtensions.isSelected());
            props.setUseSystemTempFolder(useSystemTemp.isSelected());
            props.setCustomTempFolder(customTempDir.getText());
            props.setUpdateCheckEnabled(updateCheck.isSelected());
            props.setUseSmallIcons(smallIcons.isSelected());
            props.set("useSystemProxy", String.valueOf(useSystemProxy.isSelected()));
            props.set("manualProxy", String.valueOf(!useSystemProxy.isSelected()));
            props.set("http.proxy.host", proxyHost.getText());
            props.set("http.proxy.port", proxyPort.getText());
        } catch (Throwable ignored) {
            // properties service unavailable — nothing to persist
        }
    }

    // ----- test/observer accessors ----------------------------------------

    /** @return the indent-spaces control value (for tests/observers). */
    public int getIndentValue() {
        return indentSpaces.getValue();
    }

    public XmlParserType getParserType() {
        return parserType.getValue();
    }

    public void setParserType(XmlParserType type) {
        parserType.setValue(type);
    }

    public boolean isXsdBackupSelected() {
        return xsdBackup.isSelected();
    }

    public void setXsdBackupSelected(boolean selected) {
        xsdBackup.setSelected(selected);
    }

    public boolean isUseSystemTempSelected() {
        return useSystemTemp.isSelected();
    }

    public String getCustomTempText() {
        return customTempDir.getText();
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
