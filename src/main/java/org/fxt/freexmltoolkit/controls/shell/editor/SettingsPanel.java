package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
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
import org.fxt.freexmltoolkit.util.ProjectLinks;

/**
 * The Settings page, opened as a tab in the main editor area (the Settings
 * activity's side panel only shows a pointer to it). Surfaces the full {@link PropertiesService} configuration in
 * sections — Theme, Editor (XML/JSON indent, auto-format, pretty-print), XSD (auto-save, backups),
 * Parser (XML parser engine, XSLT extensions), Temp &amp; Cache (system/custom temp folder + clear),
 * General (update check, small icons) and HTTP Proxy — loading the saved values into the controls
 * and persisting them on "Save Settings". Theme switches apply live.
 */
public class SettingsPanel extends VBox {

    @FXML private ToggleButton light;
    @FXML private ToggleButton dark;

    // Editor
    @FXML private Spinner<Integer> indentSpaces;
    @FXML private Spinner<Integer> jsonIndent;
    @FXML private CheckBox autoFormat;
    @FXML private CheckBox xsdPrettyPrint;
    @FXML private CheckBox schematronPretty;

    // XSD
    @FXML private CheckBox xsdAutoSave;
    @FXML private Spinner<Integer> xsdAutoSaveInterval;
    @FXML private CheckBox xsdBackup;
    @FXML private Spinner<Integer> xsdBackupVersions;
    @FXML private CheckBox backupSeparateDir;
    @FXML private TextField backupDir;

    // Parser
    @FXML private ComboBox<XmlParserType> parserType;
    @FXML private CheckBox xsltExtensions;

    // Rendering (JavaFX Prism pipeline; applied at next startup)
    @FXML private ComboBox<String> renderingMode;
    @FXML private Label renderingActiveStatus;
    @FXML private Label renderingGpuStatus;

    // Temp & cache
    @FXML private CheckBox useSystemTemp;
    @FXML private TextField customTempDir;
    @FXML private Label tempStatus;

    // General
    @FXML private CheckBox updateCheck;
    @FXML private CheckBox smallIcons;
    @FXML private CheckBox toolbarLabels;
    @FXML private CheckBox activityBarLabels;
    @FXML private ToggleButton toolbarIconSmall;
    @FXML private ToggleButton toolbarIconLarge;
    @FXML private CheckBox showLeftPanel;
    @FXML private CheckBox showInspector;

    // Proxy
    @FXML private CheckBox useSystemProxy;
    @FXML private TextField proxyHost;
    @FXML private TextField proxyPort;

    // User info
    @FXML private TextField userName;
    @FXML private TextField userEmail;
    @FXML private TextField userCompany;

    // Security
    @FXML private CheckBox trustAllCerts;

    // Usage statistics
    @FXML private CheckBox trackingEnabled;
    @FXML private Label usageStatus;
    // Anonymous telemetry (opt-out, see docs/telemetry.md)
    @FXML private CheckBox telemetryUsage;
    @FXML private CheckBox telemetryErrors;

    // Developer
    @FXML private CheckBox execStatsEnabled;
    
    // FundsXML extension
    @FXML private CheckBox fundsXmlEnabled;

    // File associations
    @FXML private CheckBox assocXml;
    @FXML private CheckBox assocXsd;
    @FXML private CheckBox assocXslt;
    @FXML private CheckBox assocSch;
    @FXML private CheckBox assocJson;
    @FXML private Button assocRegister;
    @FXML private Button assocUnregister;
    @FXML private Label assocStatus;

    // Templates
    @FXML private TextField templatesDir;
    @FXML private ListView<XmlTemplate> templatesList;

    // Schema Library
    @FXML private CheckBox schemaLibraryAutoBind;

    @FXML private Label libraryFile;
    @FXML private Button editTemplateButton;
    @FXML private Button deleteTemplateButton;
    @FXML private Button sponsorButton;

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

    /** Loads the layout from {@code /pages/views/SettingsPanel.fxml} and fills in the saved values. */
    public SettingsPanel() {
        FxmlViews.load(this, "/pages/views/SettingsPanel.fxml");

        boolean isDark = DesignTokens.Theme.fromProperty(currentThemeProperty()) == DesignTokens.Theme.DARK;
        (isDark ? dark : light).setSelected(true);

        parserType.getItems().setAll(XmlParserType.values());
        backupDir.disableProperty().bind(backupSeparateDir.selectedProperty().not());
        customTempDir.disableProperty().bind(useSystemTemp.selectedProperty());
        proxyHost.disableProperty().bind(useSystemProxy.selectedProperty());
        proxyPort.disableProperty().bind(useSystemProxy.selectedProperty());

        templatesList.setCellFactory(lv -> templateCell());
        editTemplateButton.disableProperty().bind(
                templatesList.getSelectionModel().selectedItemProperty().isNull());
        deleteTemplateButton.disableProperty().bind(
                templatesList.getSelectionModel().selectedItemProperty().isNull());

        initFileAssociationControls();
        libraryFile.setText("Library file: "
                + org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared().getStorageFile());
        sponsorButton.setGraphic(ProjectLinks.sponsorIcon(14));

        loadSettings();
        refreshRenderingStatus();
    }

    @FXML
    public void onThemeLight() {
        applyTheme(false);
    }

    @FXML
    public void onThemeDark() {
        applyTheme(true);
    }

    @FXML
    public void onClearTemp() {
        tempStatus.setText("Cleared " + clearTempFolder() + " file(s).");
    }

    @FXML
    public void onClearCache() {
        if (org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation("Clear Cache",
                "Clear the local cache folder?",
                "This deletes all cached files (downloaded schemas etc.) under\n"
                        + cacheFolder().getAbsolutePath() + "\n\nThis action cannot be undone.")) {
            tempStatus.setText("Cleared " + clearCacheFolder() + " cached file(s).");
        }
    }

    @FXML
    public void onClearStatistics() {
        if (org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation("Clear Statistics",
                "Clear all progress data?",
                "This will permanently delete all your usage statistics. "
                        + "This action cannot be undone.")) {
            UsageTrackingServiceImpl.getInstance().clearStatistics();
            usageStatus.setText("Usage statistics cleared.");
        }
    }

    @FXML
    public void onWhatIsSent() {
        org.fxt.freexmltoolkit.controls.dialogs.TelemetryInfoDialog.show(
                getScene() != null ? getScene().getWindow() : null);
    }

    @FXML
    public void onNewTemplate() {
        editTemplate(null);
    }

    @FXML
    public void onEditTemplate() {
        editTemplate(templatesList.getSelectionModel().getSelectedItem());
    }

    @FXML
    public void onDeleteTemplate() {
        deleteSelectedTemplate();
    }

    @FXML
    public void onRegisterAssociations() {
        runAssociationAction(true);
    }

    @FXML
    public void onUnregisterAssociations() {
        runAssociationAction(false);
    }

    @FXML
    public void onManageSchemaCache() {
        manageSchemaCacheAction.run();
    }

    @FXML
    public void onSponsor() {
        ProjectLinks.openInBrowser(ProjectLinks.SPONSORS_URL);
    }

    @FXML
    public void onSave() {
        saveSettings();
        tempStatus.setText("Settings saved.");
    }

    @FXML
    public void onBrowseBackupDir() {
        chooseDirInto(backupDir);
    }

    @FXML
    public void onBrowseTempDir() {
        chooseDirInto(customTempDir);
    }

    @FXML
    public void onBrowseTemplatesDir() {
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
            telemetryUsage.setSelected(props.isTelemetryUsageEnabled());
            telemetryErrors.setSelected(props.isTelemetryErrorsEnabled());
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
            saveTelemetrySettings(props);
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

    /**
     * Persists the two telemetry toggles. The running service is informed too, so that turning
     * a toggle off immediately drops the queued events of that kind.
     */
    private void saveTelemetrySettings(PropertiesService props) {
        props.setTelemetryUsageEnabled(telemetryUsage.isSelected());
        props.setTelemetryErrorsEnabled(telemetryErrors.isSelected());
        try {
            var telemetry = org.fxt.freexmltoolkit.service.telemetry.Telemetry.get();
            if (telemetry.isActive()) {
                telemetry.setUsageEnabled(telemetryUsage.isSelected());
                telemetry.setErrorReportingEnabled(telemetryErrors.isSelected());
            }
        } catch (Throwable ignored) {
            // telemetry unavailable — the persisted properties are authoritative
        }
    }

    /** @return the "Send anonymous usage statistics" checkbox (for tests/observers). */
    public CheckBox getTelemetryUsageCheckBox() {
        return telemetryUsage;
    }

    /** @return the "Send anonymous error reports" checkbox (for tests/observers). */
    public CheckBox getTelemetryErrorsCheckBox() {
        return telemetryErrors;
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
}
