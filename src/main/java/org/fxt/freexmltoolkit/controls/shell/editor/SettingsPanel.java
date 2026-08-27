package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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
import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.domain.XmlParserType;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.FileAssociationService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.TemplateFileService;
import org.fxt.freexmltoolkit.service.TemplateRepository;
import org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl;

/**
 * The Settings page, opened as a tab in the main editor area (the Settings
 * activity's side panel only shows a pointer to it). Surfaces the full {@link PropertiesService} configuration in
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

    // Rendering (JavaFX Prism pipeline; applied at next startup)
    private final ComboBox<String> renderingMode = new ComboBox<>();
    private final Label renderingActiveStatus = new Label();
    private final Label renderingGpuStatus = new Label();

    // Temp & cache
    private final CheckBox useSystemTemp = new CheckBox("Use system temp folder");
    private final TextField customTempDir = new TextField();
    private final Label tempStatus = new Label();

    // General
    private final CheckBox updateCheck = new CheckBox("Check for updates on startup");
    private final CheckBox smallIcons = new CheckBox("Use small icons");
    private final CheckBox toolbarLabels = new CheckBox("Show toolbar button labels");
    private final CheckBox activityBarLabels = new CheckBox("Show activity bar labels");
    private final ToggleButton toolbarIconSmall = new ToggleButton("Small");
    private final ToggleButton toolbarIconLarge = new ToggleButton("Large");
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

    // Developer
    private final CheckBox execStatsEnabled = new CheckBox("Record execution statistics");
    private final Label execStatsHint = new Label(
            "Collects duration, CPU and memory per XSLT/XQuery/validation run. "
                    + "View them in the \"Execution Statistics\" tool tab.");

    // FundsXML extension
    private final CheckBox fundsXmlEnabled = new CheckBox("Enable FundsXML extensions");

    // File associations
    private final CheckBox assocXml = new CheckBox("XML (.xml)");
    private final CheckBox assocXsd = new CheckBox("XSD Schema (.xsd)");
    private final CheckBox assocXslt = new CheckBox("XSLT Stylesheet (.xsl, .xslt)");
    private final CheckBox assocSch = new CheckBox("Schematron (.sch, .schematron)");
    private final CheckBox assocJson = new CheckBox("JSON (.json)");
    private final Button assocRegister = new Button("Register", iconGraphic("bi-box-arrow-in-down"));
    private final Button assocUnregister = new Button("Unregister", iconGraphic("bi-x-circle"));
    private final Label assocStatus = new Label();

    // Templates
    private final TextField templatesDir = new TextField();
    private final ListView<XmlTemplate> templatesList = new ListView<>();

    // Schema Library
    private final CheckBox schemaLibraryAutoBind = new CheckBox("Use the Schema Library to bind schemas automatically");
    private final javafx.scene.control.Hyperlink manageSchemaCache = new javafx.scene.control.Hyperlink("Manage schema cache…");

    /** Optional hook invoked after {@link #saveSettings()} (e.g. to refresh the activity bar). */
    private Runnable onSaved;

    /** Optional hook invoked when saving flips the FundsXML extension from off to on. */
    private Runnable onFundsXmlEnabled;

    /** Optional hook invoked when the "Manage schema cache…" link is activated. */
    private Runnable manageSchemaCacheAction = () -> { };

    /** Sets the callback invoked when a "Manage schema cache…" link is activated. */
    public void setManageSchemaCacheAction(Runnable action) {
        this.manageSchemaCacheAction = action == null ? () -> { } : action;
    }

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

        ToggleGroup toolbarIconSizeGroup = new ToggleGroup();
        toolbarIconSmall.setToggleGroup(toolbarIconSizeGroup);
        toolbarIconLarge.setToggleGroup(toolbarIconSizeGroup);

        indentSpaces.setEditable(true);
        jsonIndent.setEditable(true);
        xsdAutoSaveInterval.setEditable(true);
        xsdBackupVersions.setEditable(true);
        parserType.getItems().setAll(XmlParserType.values());
        renderingMode.getItems().setAll("Auto", "Hardware", "Software");
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

        templatesDir.setPromptText("templates directory (leave empty for default)");
        templatesList.setPrefHeight(140);
        templatesList.setCellFactory(lv -> templateCell());
        Button newTemplate = new Button("New", iconGraphic("bi-plus-lg"));
        Button editTemplate = new Button("Edit", iconGraphic("bi-pencil"));
        Button deleteTemplate = new Button("Delete", iconGraphic("bi-trash"));
        for (Button b : new Button[]{newTemplate, editTemplate, deleteTemplate}) {
            b.getStyleClass().add("fxt-tool-button");
        }
        editTemplate.disableProperty().bind(
                templatesList.getSelectionModel().selectedItemProperty().isNull());
        deleteTemplate.disableProperty().bind(
                templatesList.getSelectionModel().selectedItemProperty().isNull());
        newTemplate.setOnAction(e -> editTemplate(null));
        editTemplate.setOnAction(e -> editTemplate(templatesList.getSelectionModel().getSelectedItem()));
        deleteTemplate.setOnAction(e -> deleteSelectedTemplate());
        HBox templateButtons = new HBox(6, newTemplate, editTemplate, deleteTemplate);
        templateButtons.setAlignment(Pos.CENTER_LEFT);

        Label renderingHint = new Label(
                "Auto picks GPU rendering on dedicated graphics, software otherwise. "
                        + "Takes effect after restart.");
        renderingHint.setWrapText(true);
        renderingHint.getStyleClass().add("fxt-placeholder-text");
        renderingActiveStatus.setWrapText(true);
        renderingGpuStatus.setWrapText(true);
        renderingGpuStatus.getStyleClass().add("fxt-placeholder-text");

        Label assocHint = new Label("Make FreeXmlToolkit the default application for the "
                + "selected file types (current user only).");
        assocHint.setWrapText(true);
        assocHint.getStyleClass().add("fxt-placeholder-text");
        execStatsHint.setWrapText(true);
        execStatsHint.getStyleClass().add("fxt-placeholder-text");
        assocStatus.setWrapText(true);
        assocStatus.getStyleClass().add("fxt-placeholder-text");
        assocRegister.getStyleClass().add("fxt-tool-button");
        assocUnregister.getStyleClass().add("fxt-tool-button");
        assocRegister.setOnAction(e -> runAssociationAction(true));
        assocUnregister.setOnAction(e -> runAssociationAction(false));
        HBox assocButtons = new HBox(6, assocRegister, assocUnregister);
        assocButtons.setAlignment(Pos.CENTER_LEFT);
        initFileAssociationControls();

        schemaLibraryAutoBind.setId("settings-schema-library-autobind");
        schemaLibraryAutoBind.setWrapText(true);
        manageSchemaCache.setId("settings-manage-schema-cache");
        manageSchemaCache.setGraphic(iconGraphic("bi-collection"));
        manageSchemaCache.setOnAction(e -> manageSchemaCacheAction.run());
        Label libraryFile = new Label("Library file: "
                + org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared().getStorageFile());
        libraryFile.setWrapText(true);
        libraryFile.getStyleClass().add("fxt-lib-status");

        javafx.scene.control.Hyperlink manageSchemaCache2 = new javafx.scene.control.Hyperlink("Manage schema cache…");
        manageSchemaCache2.setId("settings-manage-schema-cache-2");
        manageSchemaCache2.setGraphic(iconGraphic("bi-collection"));
        manageSchemaCache2.setOnAction(e -> manageSchemaCacheAction.run());

        Button save = new Button("Save Settings", iconGraphic("bi-save"));
        save.getStyleClass().add("fxt-tool-button");
        save.setOnAction(e -> {
            saveSettings();
            tempStatus.setText("Settings saved.");
        });

        loadSettings();
        refreshRenderingStatus();

        // Section cards, color-coded by topic (the panel now lives in the main editor
        // area as a Settings page, so there is room for a two-column card layout).
        javafx.scene.layout.FlowPane cards = new javafx.scene.layout.FlowPane(16, 16,
                card("THEME", "bi-palette", "#1373D9",
                        new HBox(6, light, dark)),
                card("EDITOR", "bi-pencil-square", "#17a2b8",
                        labeled("XML indent:", indentSpaces), labeled("JSON indent:", jsonIndent),
                        autoFormat, xsdPrettyPrint, schematronPretty),
                card("XSD", "bi-diagram-3", "#6f42c1",
                        xsdAutoSave, labeled("Interval (min):", xsdAutoSaveInterval),
                        xsdBackup, labeled("Keep versions:", xsdBackupVersions),
                        backupSeparateDir, browseRow(backupDir, this::chooseBackupDir)),
                card("SCHEMA LIBRARY", "bi-collection", "#20c997",
                        schemaLibraryAutoBind, libraryFile, manageSchemaCache),
                card("PARSER", "bi-cpu", "#fd7e14",
                        labeled("XML parser:", parserType), xsltExtensions),
                card("RENDERING", "bi-gpu-card", "#e83e8c",
                        labeled("Mode:", renderingMode),
                        renderingActiveStatus, renderingGpuStatus, renderingHint),
                card("TEMP & CACHE", "bi-trash", "#ffc107",
                        useSystemTemp, browseRow(customTempDir, this::chooseTempDir),
                        fill(clearTemp), fill(clearCache), tempStatus, manageSchemaCache2),
                card("GENERAL", "bi-sliders", "#007bff",
                        updateCheck, smallIcons, toolbarLabels, activityBarLabels,
                        labeled("Toolbar icons:", new HBox(6, toolbarIconSmall, toolbarIconLarge)),
                        showLeftPanel, showInspector),
                card("FILE ASSOCIATIONS", "bi-file-earmark-check", "#0dcaf0",
                        assocHint, assocXml, assocXsd, assocXslt, assocSch, assocJson,
                        assocButtons, assocStatus),
                card("USER INFO", "bi-person", "#28a745",
                        labeled("Name:", userName), labeled("Email:", userEmail),
                        labeled("Company:", userCompany)),
                card("SECURITY", "bi-shield-lock", "#dc3545",
                        trustAllCerts),
                card("USAGE STATISTICS", "bi-graph-up", "#6c757d",
                        trackingEnabled, fill(clearStats), usageStatus),
                card("DEVELOPER", "bi-speedometer2", "#495057",
                        execStatsEnabled, execStatsHint),
                card("FUNDSXML", "bi-file-earmark-code", "#20c997",
                        fundsXmlEnabled),
                card("TEMPLATES", "bi-file-earmark-plus", "#0d6efd",
                        new Label("Templates directory:"),
                        browseRow(templatesDir, this::chooseTemplatesDir),
                        new Label("Your templates:"),
                        fill(templatesList), templateButtons),
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

    private void chooseTemplatesDir() {
        chooseDirInto(templatesDir);
    }

    private void chooseDirInto(TextField field) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        File dir = org.fxt.freexmltoolkit.util.FileChooserHelper.showDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (dir != null) {
            field.setText(dir.getAbsolutePath());
        }
    }

    /** Opens the create/edit dialog and persists the result to the templates directory. */
    private void editTemplate(XmlTemplate existing) {
        TemplateEditDialog dialog = new TemplateEditDialog(existing);
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(template -> {
            try {
                TemplateRepository.getInstance().saveTemplateToFile(template);
            } catch (Exception ex) {
                org.fxt.freexmltoolkit.util.DialogHelper.showActionError("Templates",
                        "The template could not be saved.",
                        org.fxt.freexmltoolkit.util.DialogHelper.Remedies.FILE_UNWRITABLE, ex);
            }
            reloadTemplatesList();
        });
    }

    private void deleteSelectedTemplate() {
        XmlTemplate selected = templatesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation("Delete Template",
                "Delete template '" + selected.getName() + "'?",
                "This permanently removes the template file. This action cannot be undone.")) {
            TemplateRepository.getInstance().removeTemplate(selected.getId(), true);
            reloadTemplatesList();
        }
    }

    /**
     * Persists the templates directory and, when set, points the live
     * {@link TemplateFileService} at it and reloads the repository so the change
     * takes effect without a restart.
     */
    private void applyTemplatesDirectory(PropertiesService props) {
        String dir = templatesDir.getText() == null ? "" : templatesDir.getText().trim();
        props.setTemplatesDirectory(dir);
        if (!dir.isBlank()) {
            boolean changed = TemplateFileService.getInstance()
                    .setTemplatesDirectory(java.nio.file.Paths.get(dir));
            if (changed) {
                TemplateRepository.getInstance().refreshTemplatesFromDirectory();
                reloadTemplatesList();
            }
        }
    }

    /** Reloads the list with the user's (non-built-in) templates. */
    private void reloadTemplatesList() {
        try {
            var userTemplates = TemplateRepository.getInstance().getAllTemplates().stream()
                    .filter(t -> !t.isBuiltIn())
                    .sorted(java.util.Comparator.comparing(
                            t -> t.getName() == null ? "" : t.getName(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
            templatesList.getItems().setAll(userTemplates);
        } catch (Throwable ignored) {
            // repository unavailable (e.g. tests)
        }
    }

    private static ListCell<XmlTemplate> templateCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(XmlTemplate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String cat = item.getCategory();
                    setText(item.getName() + (cat == null || cat.isBlank() ? "" : "  (" + cat + ")"));
                }
            }
        };
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
            renderingMode.setValue(renderingModeLabel(props.getRenderingMode()));
            useSystemTemp.setSelected(props.isUseSystemTempFolder());
            customTempDir.setText(orEmpty(props.getCustomTempFolder()));
            updateCheck.setSelected(props.isUpdateCheckEnabled());
            smallIcons.setSelected(props.isUseSmallIcons());
            toolbarLabels.setSelected(props.isToolbarShowLabels());
            activityBarLabels.setSelected(props.isActivityBarShowLabels());
            boolean toolbarLarge = "large".equalsIgnoreCase(props.getToolbarIconSize());
            (toolbarLarge ? toolbarIconLarge : toolbarIconSmall).setSelected(true);
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
            execStatsEnabled.setSelected(Boolean.parseBoolean(orEmpty(
                    props.get(org.fxt.freexmltoolkit.service.DeveloperPropertyKeys.EXECUTION_STATS_ENABLED))));
            fundsXmlEnabled.setSelected(Boolean.parseBoolean(
                    props.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED) == null
                            ? "false"
                            : props.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED)));
            templatesDir.setText(orEmpty(props.getTemplatesDirectory()));
            schemaLibraryAutoBind.setSelected(props.isSchemaLibraryAutoBindEnabled());
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests) — controls keep their defaults
        }
        reloadTemplatesList();
    }

    /**
     * Refreshes the read-only rendering status: the actually active Prism pipeline
     * (read now, since the toolkit is up) and the detected GPU (queried off the UI thread).
     */
    private void refreshRenderingStatus() {
        String active = org.fxt.freexmltoolkit.util.RenderingStatus.activePipelineDescription();
        renderingActiveStatus.setText("Active pipeline: " + (active != null ? active : "unknown"));
        renderingGpuStatus.setText("Detected GPU: …");

        Thread t = new Thread(() -> {
            java.util.List<String> gpus =
                    org.fxt.freexmltoolkit.util.RenderingPipelineDetector.detectAdapterNames();
            String text = gpus.isEmpty()
                    ? "Detected GPU: unknown"
                    : "Detected GPU: " + String.join(", ", gpus);
            javafx.application.Platform.runLater(() -> renderingGpuStatus.setText(text));
        }, "rendering-gpu-detect");
        t.setDaemon(true);
        t.start();
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
            props.setRenderingMode(renderingModeValue(renderingMode.getValue()));
            props.setUseSystemTempFolder(useSystemTemp.isSelected());
            props.setCustomTempFolder(customTempDir.getText());
            props.setUpdateCheckEnabled(updateCheck.isSelected());
            props.setUseSmallIcons(smallIcons.isSelected());
            props.setToolbarShowLabels(toolbarLabels.isSelected());
            props.setActivityBarShowLabels(activityBarLabels.isSelected());
            props.setToolbarIconSize(toolbarIconLarge.isSelected() ? "large" : "small");
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
            props.set(org.fxt.freexmltoolkit.service.DeveloperPropertyKeys.EXECUTION_STATS_ENABLED,
                    String.valueOf(execStatsEnabled.isSelected()));
            boolean fundsXmlWasEnabled = Boolean.parseBoolean(props.get(
                    org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED));
            props.set(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED,
                    String.valueOf(fundsXmlEnabled.isSelected()));
            applyTemplatesDirectory(props);
            props.setSchemaLibraryAutoBindEnabled(schemaLibraryAutoBind.isSelected());
            // Only notify once every write above succeeded.
            if (onSaved != null) {
                onSaved.run();
            }
            if (!fundsXmlWasEnabled && fundsXmlEnabled.isSelected() && onFundsXmlEnabled != null) {
                onFundsXmlEnabled.run();
            }
        } catch (Throwable ignored) {
            // properties service unavailable — nothing to persist
        }
    }

    /** Sets a callback invoked after settings are persisted (e.g. to refresh the activity bar). */
    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    /** Checkbox → file type mapping of the FILE ASSOCIATIONS card. */
    private java.util.Map<CheckBox, UnifiedEditorFileType> associationCheckBoxes() {
        java.util.Map<CheckBox, UnifiedEditorFileType> map = new java.util.LinkedHashMap<>();
        map.put(assocXml, UnifiedEditorFileType.XML);
        map.put(assocXsd, UnifiedEditorFileType.XSD);
        map.put(assocXslt, UnifiedEditorFileType.XSLT);
        map.put(assocSch, UnifiedEditorFileType.SCHEMATRON);
        map.put(assocJson, UnifiedEditorFileType.JSON);
        return map;
    }

    /**
     * Initializes the FILE ASSOCIATIONS card: disables it with a hint when no installed
     * launcher exists (IDE / gradle run), otherwise loads the persisted type selection
     * and refreshes the registration status off the UI thread.
     */
    private void initFileAssociationControls() {
        FileAssociationService svc;
        try {
            svc = ServiceRegistry.get(FileAssociationService.class);
        } catch (Throwable t) {
            setAssociationControlsDisabled(true);
            assocStatus.setText("File association service unavailable.");
            return;
        }
        if (!svc.isSupported()) {
            setAssociationControlsDisabled(true);
            assocStatus.setText(svc.getUnsupportedReason());
            return;
        }
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            String selected = orEmpty(props.get("fileAssociations.selected"));
            if (!selected.isBlank()) {
                java.util.Set<String> names = java.util.Set.of(selected.split(","));
                associationCheckBoxes().forEach((cb, type) -> cb.setSelected(names.contains(type.name())));
            } else {
                associationCheckBoxes().keySet().forEach(cb -> cb.setSelected(true));
            }
        } catch (Throwable ignored) {
            associationCheckBoxes().keySet().forEach(cb -> cb.setSelected(true));
        }
        refreshAssociationStatus(svc);
    }

    private void setAssociationControlsDisabled(boolean disabled) {
        associationCheckBoxes().keySet().forEach(cb -> cb.setDisable(disabled));
        assocRegister.setDisable(disabled);
        assocUnregister.setDisable(disabled);
    }

    /** Runs register/unregister off the UI thread and reports the result in the card. */
    private void runAssociationAction(boolean register) {
        java.util.Set<UnifiedEditorFileType> types = java.util.EnumSet.noneOf(UnifiedEditorFileType.class);
        associationCheckBoxes().forEach((cb, type) -> {
            if (cb.isSelected()) {
                types.add(type);
            }
        });
        if (types.isEmpty()) {
            assocStatus.setText("Select at least one file type.");
            return;
        }
        FileAssociationService svc = ServiceRegistry.get(FileAssociationService.class);
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            props.set("fileAssociations.selected", types.stream()
                    .map(Enum::name).reduce((a, b) -> a + "," + b).orElse(""));
        } catch (Throwable ignored) {
            // persistence unavailable — the action itself still works
        }
        setAssociationControlsDisabled(true);
        assocStatus.setText(register ? "Registering…" : "Unregistering…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            FileAssociationResult result = register ? svc.register(types) : svc.unregister(types);
            javafx.application.Platform.runLater(() -> {
                setAssociationControlsDisabled(false);
                // Keep the result message visible — it may carry follow-up instructions
                // (e.g. the Windows Settings confirmation step).
                assocStatus.setText(result.message());
                if (!result.success()) {
                    org.fxt.freexmltoolkit.util.DialogHelper.showActionError("File Associations",
                            result.message(),
                            "Check the details below and try again.",
                            String.join("\n", result.errors()));
                }
            });
        });
    }

    /** Summarizes the per-type registration state into the status label (off-thread query). */
    private void refreshAssociationStatus(FileAssociationService svc) {
        var checkBoxes = associationCheckBoxes();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            java.util.List<String> asDefault = new java.util.ArrayList<>();
            java.util.List<String> registered = new java.util.ArrayList<>();
            for (UnifiedEditorFileType type : checkBoxes.values()) {
                String ext = svc.extensionsFor(type).stream().sorted().findFirst().orElse(null);
                if (ext == null) {
                    continue;
                }
                switch (svc.getRegistrationState(ext)) {
                    case DEFAULT -> asDefault.add("." + ext);
                    case REGISTERED -> registered.add("." + ext);
                    default -> {
                    }
                }
            }
            StringBuilder summary = new StringBuilder();
            if (!asDefault.isEmpty()) {
                summary.append("Default for: ").append(String.join(", ", asDefault));
            }
            if (!registered.isEmpty()) {
                if (!summary.isEmpty()) {
                    summary.append(" — ");
                }
                summary.append("Registered: ").append(String.join(", ", registered));
            }
            if (summary.isEmpty()) {
                summary.append("Not registered.");
            }
            String text = summary.toString();
            javafx.application.Platform.runLater(() -> assocStatus.setText(text));
        });
    }

    /**
     * Sets a callback invoked (after {@link #setOnSaved}) when a save turns the
     * FundsXML extension on — i.e. only on a false → true transition, not on every
     * save while the feature stays enabled.
     */
    public void setOnFundsXmlEnabled(Runnable onFundsXmlEnabled) {
        this.onFundsXmlEnabled = onFundsXmlEnabled;
    }

    /** Test seam: drives the FundsXML enable checkbox. */
    void setFundsXmlEnabledSelected(boolean selected) {
        fundsXmlEnabled.setSelected(selected);
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

    /** @return the "Record execution statistics" checkbox (for tests/observers). */
    public CheckBox getExecStatsCheckBox() {
        return execStatsEnabled;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Maps a stored rendering mode (AUTO/HARDWARE/SOFTWARE) to its display label. */
    private static String renderingModeLabel(String stored) {
        if (stored == null) {
            return "Auto";
        }
        return switch (stored.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "HARDWARE" -> "Hardware";
            case "SOFTWARE" -> "Software";
            default -> "Auto";
        };
    }

    /** Maps a display label (Auto/Hardware/Software) back to the stored rendering mode. */
    private static String renderingModeValue(String label) {
        if (label == null) {
            return "AUTO";
        }
        return switch (label.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "hardware" -> "HARDWARE";
            case "software" -> "SOFTWARE";
            default -> "AUTO";
        };
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
