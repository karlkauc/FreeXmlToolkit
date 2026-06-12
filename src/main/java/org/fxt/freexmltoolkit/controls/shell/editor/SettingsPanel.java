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
import org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl;

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
    private final CheckBox showLeftPanel = new CheckBox("Show left side panel");
    private final CheckBox showInspector = new CheckBox("Show Properties (inspector) panel");

    // Proxy
    private final CheckBox useSystemProxy = new CheckBox("Use system proxy");
    private final TextField proxyHost = new TextField();
    private final TextField proxyPort = new TextField();

    // User info
    private final TextField userName = new TextField();
    private final TextField userEmail = new TextField();
    private final TextField userCompany = new TextField();

    // Security
    private final CheckBox trustAllCerts = new CheckBox("Trust all certificates");

    // Usage statistics
    private final CheckBox trackingEnabled = new CheckBox("Enable usage tracking");
    private final Label usageStatus = new Label();

    // FundsXML extension
    private final CheckBox fundsXmlEnabled = new CheckBox("Enable FundsXML extensions");

    /** Optional hook invoked after {@link #saveSettings()} (e.g. to refresh the activity bar). */
    private Runnable onSaved;

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
        usageStatus.getStyleClass().add("fxt-placeholder-text");

        proxyHost.setPromptText("host");
        proxyPort.setPromptText("port");
        proxyHost.disableProperty().bind(useSystemProxy.selectedProperty());
        proxyPort.disableProperty().bind(useSystemProxy.selectedProperty());

        Button clearTemp = new Button("Clear Temp Folder", iconGraphic("bi-trash"));
        clearTemp.getStyleClass().add("fxt-tool-button");
        clearTemp.setOnAction(e -> tempStatus.setText("Cleared " + clearTempFolder() + " file(s)."));

        Button clearCache = new Button("Clear Cache Folder", iconGraphic("bi-trash"));
        clearCache.getStyleClass().add("fxt-tool-button");
        clearCache.setOnAction(e -> {
            if (org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation("Clear Cache",
                    "Clear the local cache folder?",
                    "This deletes all cached files (downloaded schemas etc.) under\n"
                            + cacheFolder().getAbsolutePath() + "\n\nThis action cannot be undone.")) {
                tempStatus.setText("Cleared " + clearCacheFolder() + " cached file(s).");
            }
        });

        userName.setPromptText("name");
        userEmail.setPromptText("email");
        userCompany.setPromptText("company");

        Button clearStats = new Button("Clear statistics", iconGraphic("bi-trash"));
        clearStats.getStyleClass().add("fxt-tool-button");
        clearStats.setOnAction(e -> {
            if (org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation("Clear Statistics",
                    "Clear all progress data?",
                    "This will permanently delete all your usage statistics. "
                            + "This action cannot be undone.")) {
                UsageTrackingServiceImpl.getInstance().clearStatistics();
                usageStatus.setText("Usage statistics cleared.");
            }
        });

        Button save = new Button("Save Settings", iconGraphic("bi-save"));
        save.getStyleClass().add("fxt-tool-button");
        save.setOnAction(e -> {
            saveSettings();
            tempStatus.setText("Settings saved.");
        });

        loadSettings();

        // Section cards, color-coded by topic (the panel now lives in the main editor
        // area as a Settings page, so there is room for a two-column card layout).
        javafx.scene.layout.FlowPane cards = new javafx.scene.layout.FlowPane(16, 16,
                card("THEME", "bi-palette", "#3B5BDB",
                        new HBox(6, light, dark)),
                card("EDITOR", "bi-pencil-square", "#17a2b8",
                        labeled("XML indent:", indentSpaces), labeled("JSON indent:", jsonIndent),
                        autoFormat, xsdPrettyPrint, schematronPretty),
                card("XSD", "bi-diagram-3", "#6f42c1",
                        xsdAutoSave, labeled("Interval (min):", xsdAutoSaveInterval),
                        xsdBackup, labeled("Keep versions:", xsdBackupVersions),
                        backupSeparateDir, browseRow(backupDir, this::chooseBackupDir)),
                card("PARSER", "bi-cpu", "#fd7e14",
                        labeled("XML parser:", parserType), xsltExtensions),
                card("TEMP & CACHE", "bi-trash", "#ffc107",
                        useSystemTemp, browseRow(customTempDir, this::chooseTempDir),
                        fill(clearTemp), fill(clearCache), tempStatus),
                card("GENERAL", "bi-sliders", "#007bff",
                        updateCheck, smallIcons, showLeftPanel, showInspector),
                card("USER INFO", "bi-person", "#28a745",
                        labeled("Name:", userName), labeled("Email:", userEmail),
                        labeled("Company:", userCompany)),
                card("SECURITY", "bi-shield-lock", "#dc3545",
                        trustAllCerts),
                card("USAGE STATISTICS", "bi-graph-up", "#6c757d",
                        trackingEnabled, fill(clearStats), usageStatus),
                card("FUNDSXML", "bi-file-earmark-code", "#20c997",
                        fundsXmlEnabled),
                card("HTTP PROXY", "bi-globe", "#6610f2",
                        useSystemProxy, proxyHost, proxyPort));
        cards.setPrefWrapLength(820);

        save.getStyleClass().add("fxt-primary-button");
        VBox content = new VBox(16, title, cards, fill(save));
        content.getStyleClass().add("fxt-settings-page");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("fxt-settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    /** A color-coded settings card: tinted icon tile + title, then the section's controls. */
    private static VBox card(String titleText, String iconLiteral, String color, Region... controls) {
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(15);
        icon.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(
                javafx.scene.paint.Color.web(color)));
        javafx.scene.layout.StackPane tile = new javafx.scene.layout.StackPane(icon);
        tile.getStyleClass().add("fxt-settings-card-icon");
        tile.setStyle("-fx-background-color: " + color + "22;"); // ~13% alpha tint

        Label label = new Label(titleText);
        label.getStyleClass().add("fxt-settings-card-title");
        HBox header = new HBox(10, tile, label);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, header);
        card.getStyleClass().add("fxt-settings-card");
        card.setStyle("-fx-border-color: " + color + "55 transparent transparent transparent;"
                + "-fx-border-width: 3 0 0 0;");
        card.getChildren().addAll(controls);
        card.setPrefWidth(390);
        return card;
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

    /** @return the application cache folder ({@code ~/.freeXmlToolkit/cache}), e.g. for downloaded schemas. */
    public static File cacheFolder() {
        return new File(System.getProperty("user.home"),
                ".freeXmlToolkit" + File.separator + "cache");
    }

    /** Recursively deletes everything inside the cache folder. @return the number of files deleted. */
    public int clearCacheFolder() {
        return deleteContents(cacheFolder());
    }

    static int deleteContents(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                deleted += deleteContents(file);
                file.delete();
            } else if (file.delete()) {
                deleted++;
            }
        }
        return deleted;
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
            // Side-panel visibility (shared with UnifiedShellView; default open).
            showLeftPanel.setSelected(!"false".equalsIgnoreCase(orEmpty(props.get("shell.leftPanel.visible"))));
            showInspector.setSelected(!"false".equalsIgnoreCase(orEmpty(props.get("shell.inspector.visible"))));
            useSystemProxy.setSelected(!"false".equalsIgnoreCase(orEmpty(props.get("useSystemProxy"))));
            proxyHost.setText(orEmpty(props.get("http.proxy.host")));
            proxyPort.setText(orEmpty(props.get("http.proxy.port")));
            userName.setText(props.get("user.name") == null ? "" : props.get("user.name"));
            userEmail.setText(props.get("user.email") == null ? "" : props.get("user.email"));
            userCompany.setText(props.get("user.company") == null ? "" : props.get("user.company"));
            trustAllCerts.setSelected(Boolean.parseBoolean(
                    props.get("ssl.trustAllCerts") == null ? "false" : props.get("ssl.trustAllCerts")));
            trackingEnabled.setSelected(
                    UsageTrackingServiceImpl.getInstance().isTrackingEnabled());
            fundsXmlEnabled.setSelected(Boolean.parseBoolean(
                    props.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED) == null
                            ? "false"
                            : props.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED)));
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
            props.set("shell.leftPanel.visible", String.valueOf(showLeftPanel.isSelected()));
            props.set("shell.inspector.visible", String.valueOf(showInspector.isSelected()));
            props.set("useSystemProxy", String.valueOf(useSystemProxy.isSelected()));
            props.set("manualProxy", String.valueOf(!useSystemProxy.isSelected()));
            props.set("http.proxy.host", proxyHost.getText());
            props.set("http.proxy.port", proxyPort.getText());
            props.set("user.name", userName.getText().trim());
            props.set("user.email", userEmail.getText().trim());
            props.set("user.company", userCompany.getText().trim());
            props.set("ssl.trustAllCerts", String.valueOf(trustAllCerts.isSelected()));
            UsageTrackingServiceImpl.getInstance()
                    .setTrackingEnabled(trackingEnabled.isSelected());
            props.set(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED,
                    String.valueOf(fundsXmlEnabled.isSelected()));
            // Only notify once every write above succeeded.
            if (onSaved != null) {
                onSaved.run();
            }
        } catch (Throwable ignored) {
            // properties service unavailable — nothing to persist
        }
    }

    /** Sets a callback invoked after settings are persisted (e.g. to refresh the activity bar). */
    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
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

    public void setUserName(String v) {
        userName.setText(v);
    }

    public void setUserEmail(String v) {
        userEmail.setText(v);
    }

    public void setUserCompany(String v) {
        userCompany.setText(v);
    }

    public String getUserName() {
        return userName.getText();
    }

    public boolean isTrustAllCertsSelected() {
        return trustAllCerts.isSelected();
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
        org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(getScene(), darkTheme);
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
