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
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.domain.XmlParserType;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.FileAssociationService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.TemplateFileService;
import org.fxt.freexmltoolkit.service.TemplateRepository;
import org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl;
import org.fxt.freexmltoolkit.util.ProjectLinks;

/**
 * The Settings page, opened as a tab in the main editor area. Surfaces the full
 * {@link PropertiesService} configuration in seven groups of cards (Appearance, Editor,
 * Schemas &amp; Validation, Files &amp; Cache, Network &amp; Security, Privacy, Extensions); the
 * Settings side panel lists the groups ({@link #sections()}), jumps to them
 * ({@link #showSection(String)}) and filters them ({@link #filter(String)}).
 *
 * <p>Values are loaded into the controls and persisted by "Save Settings" in the fixed bar at
 * the bottom, which also shows a hint while there are unsaved changes (decision E5). Theme
 * switches, file-association registration and template edits apply at once and do not count
 * as unsaved changes.
 */
public class SettingsPanel extends VBox {

    private static final org.apache.logging.log4j.Logger logger =
            org.apache.logging.log4j.LogManager.getLogger(SettingsPanel.class);

    /** Group key → side-panel icon (keys are the {@code settings-group-<key>} ids of the FXML). */
    private static final java.util.Map<String, String> GROUP_ICONS = java.util.Map.of(
            "appearance", "bi-palette", "editor", "bi-pencil-square", "schemas", "bi-diagram-3",
            "files", "bi-folder2", "network", "bi-globe", "privacy", "bi-incognito",
            "extensions", "bi-puzzle");
    private static final String GROUP_ID_PREFIX = "settings-group-";

    @FXML private javafx.scene.control.ScrollPane scroll;
    @FXML private VBox page;
    @FXML private VBox groups;
    @FXML private Label noMatch;
    @FXML private Label dirtyHint;
    @FXML private Label policyNotice;
    @FXML private Label saveStatus;
    @FXML private Button saveButton;

    /** Whether a control changed since the last load or save. */
    private final javafx.beans.property.BooleanProperty dirty =
            new javafx.beans.property.SimpleBooleanProperty(this, "dirty", false);
    /** Suppresses the dirty flag while values are filled in by code. */
    private boolean loading;
    private java.util.function.Consumer<String> onSectionShown = key -> { };
    private String shownSection;

    @FXML private ToggleButton light;
    @FXML private ToggleButton dark;
    @FXML private ToggleButton systemTheme;
    @FXML private javafx.scene.layout.FlowPane accentSwatches;
    @FXML private javafx.scene.control.ColorPicker accentPicker;
    @FXML private ComboBox<String> editorFontFamily;
    @FXML private Spinner<Integer> editorFontSize;
    @FXML private Spinner<Double> editorLineSpacing;
    @FXML private CheckBox editorRememberZoom;
    @FXML private Label editorFontPreview;
    /** Set while the editor-font controls show the stored value: their changes are no choice. */
    private boolean showingEditorFont;
    /** True once the installed monospace fonts are in the family list. */
    private boolean editorFontFamiliesListed;
    /** Follows editor-font changes made elsewhere (zoom in an editor). */
    private final javafx.beans.value.ChangeListener<org.fxt.freexmltoolkit.controls.shared.EditorFont.Settings>
            editorFontListener = (obs, old, value) -> showEditorFont(value);
    /** Set while the picker shows the chosen accent: its value change is no choice of its own. */
    private boolean showingAccent;
    /** The accent swatches; exactly one is selected while a preset is the accent. */
    private final javafx.scene.control.ToggleGroup accentGroup = new javafx.scene.control.ToggleGroup();

    // Editor
    @FXML private Spinner<Integer> indentSpaces;
    @FXML private Spinner<Integer> jsonIndent;
    @FXML private CheckBox autoFormat;
    @FXML private CheckBox gridCustomColors;
    @FXML private Label gridColorsTheme;
    @FXML private javafx.scene.control.ColorPicker gridHeaderColor;
    @FXML private javafx.scene.control.ColorPicker gridMissingColor;
    @FXML private javafx.scene.control.ColorPicker gridSelectionColor;
    @FXML private javafx.scene.control.ColorPicker gridFocusColor;
    @FXML private CheckBox xsdPrettyPrint;
    @FXML private CheckBox schematronPretty;
    @FXML private Spinner<Integer> recentFilesLimit;

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
    @FXML private CheckBox htmlActiveContent;
    @FXML private CheckBox xprocUnrestricted;
    @FXML private CheckBox dtdAllowed;

    // Rendering (JavaFX Prism pipeline; applied at next startup)
    @FXML private ComboBox<String> renderingMode;
    @FXML private Label renderingActiveStatus;
    @FXML private Label renderingGpuStatus;
    @FXML private ComboBox<String> maxMemory;
    @FXML private Label memoryStatus;

    // Temp & cache
    @FXML private CheckBox useSystemTemp;
    @FXML private TextField customTempDir;
    @FXML private Label tempStatus;
    @FXML private Button clearTempButton;
    @FXML private Button clearCacheButton;
    @FXML private CheckBox logCleanup;
    @FXML private Spinner<Integer> logMaxAge;
    @FXML private Spinner<Integer> logMaxSize;
    @FXML private Button cleanLogsButton;
    @FXML private Label logStatus;

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
    @FXML private ComboBox<String> proxyMode;
    @FXML private TextField proxyHost;
    @FXML private TextField proxyPort;
    @FXML private TextField proxyBypass;
    @FXML private TextField proxyUser;
    @FXML private javafx.scene.control.PasswordField proxyPassword;
    @FXML private TextField proxyDomain;
    @FXML private CheckBox proxyWindowsSignOn;
    @FXML private Spinner<Integer> connectTimeout;
    @FXML private Spinner<Integer> readTimeout;
    @FXML private TextField proxyTestUrl;
    @FXML private Button proxyTestButton;
    @FXML private Label proxyTestStatus;

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
    @FXML private javafx.scene.control.ComboBox<org.fxt.freexmltoolkit.i18n.I18n.Language> uiLanguage;
    @FXML private VBox pluginRows;
    @FXML private FoProcessorsSettings foProcessors;
    /** One switch per plugin that can be switched (built-in and external, not failed ones). */
    private final java.util.Map<String, CheckBox> pluginSwitches = new java.util.LinkedHashMap<>();

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

        switch (org.fxt.freexmltoolkit.controls.shell.ThemeManager.currentMode()) {
            case DARK -> dark.setSelected(true);
            case SYSTEM -> systemTheme.setSelected(true);
            default -> light.setSelected(true);
        }
        buildAccentSwatches();
        initEditorFontControls();

        parserType.getItems().setAll(XmlParserType.values());
        collectPolicyLocks();
        bindDisable(backupDir, backupSeparateDir.selectedProperty().not());
        bindDisable(customTempDir, useSystemTemp.selectedProperty());
        bindDisable(logMaxAge, logCleanup.selectedProperty().not());
        bindDisable(logMaxSize, logCleanup.selectedProperty().not());
        proxyMode.getItems().setAll(PROXY_MODE_LABELS.values());
        var notManual = javafx.beans.binding.Bindings.notEqual(
                PROXY_MODE_LABELS.get(org.fxt.freexmltoolkit.service.ProxySettings.Mode.MANUAL),
                proxyMode.valueProperty());
        bindDisable(proxyHost, notManual);
        bindDisable(proxyPort, notManual);
        var direct = javafx.beans.binding.Bindings.equal(
                PROXY_MODE_LABELS.get(org.fxt.freexmltoolkit.service.ProxySettings.Mode.NONE),
                proxyMode.valueProperty());
        for (javafx.scene.Node node : new javafx.scene.Node[] {proxyBypass, proxyUser, proxyPassword,
                proxyDomain, proxyWindowsSignOn}) {
            bindDisable(node, direct);
        }
        applyPolicyLocks();

        templatesList.setCellFactory(lv -> templateCell());
        editTemplateButton.disableProperty().bind(
                templatesList.getSelectionModel().selectedItemProperty().isNull());
        deleteTemplateButton.disableProperty().bind(
                templatesList.getSelectionModel().selectedItemProperty().isNull());

        initFileAssociationControls();
        libraryFile.setText(I18n.t("SettingsPanel.library-file", org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared().getStorageFile()));
        sponsorButton.setGraphic(ProjectLinks.sponsorIcon(14));

        maxMemory.getItems().setAll(memoryChoices());
        uiLanguage.getItems().setAll(org.fxt.freexmltoolkit.i18n.I18n.Language.values());
        uiLanguage.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(org.fxt.freexmltoolkit.i18n.I18n.Language language) {
                return language == null ? "" : language == org.fxt.freexmltoolkit.i18n.I18n.Language.SYSTEM
                        ? org.fxt.freexmltoolkit.i18n.I18n.t("SettingsPanel.language.system") : language.label();
            }

            @Override
            public org.fxt.freexmltoolkit.i18n.I18n.Language fromString(String text) {
                return null;
            }
        });
        buildPluginRows();
        loadSettings();
        refreshRenderingStatus();
        refreshMemoryStatus();
        maxMemory.valueProperty().addListener((obs, old, value) -> refreshMemoryStatus());
        refreshLogStatus(null);
        refreshFolderUsage();

        trackChanges(groups);
        dirtyHint.visibleProperty().bind(dirty);
        scroll.vvalueProperty().addListener(obs -> reportShownSection());
        page.heightProperty().addListener(obs -> reportShownSection());
    }

    // ----- groups, side-panel navigation and filter --------------------------

    /**
     * @return the page's groups for the side panel, in page order; the keywords are all texts of
     *         the group's cards, so the panel's filter finds a group by any setting inside it
     */
    public java.util.List<PageNavigator.Entry> sections() {
        java.util.List<PageNavigator.Entry> sections = new java.util.ArrayList<>();
        for (VBox group : groupNodes()) {
            String key = group.getId().substring(GROUP_ID_PREFIX.length());
            String title = ((Label) group.getChildren().getFirst()).getText();
            StringBuilder words = new StringBuilder();
            for (javafx.scene.Node card : cards(group)) {
                collectTexts(card, words);
            }
            sections.add(new PageNavigator.Entry(key, title, GROUP_ICONS.getOrDefault(key, "bi-sliders"),
                    words.toString()));
        }
        return sections;
    }

    /** Scrolls the group with the given key to the top of the page (as far as the page allows). */
    public void showSection(String key) {
        javafx.scene.Node group = groups.lookup("#" + GROUP_ID_PREFIX + key);
        if (group == null) {
            return;
        }
        page.applyCss();
        page.layout();
        double max = page.getHeight() - scroll.getViewportBounds().getHeight();
        double y = groups.getBoundsInParent().getMinY() + group.getBoundsInParent().getMinY();
        scroll.setVvalue(max <= 0 ? 0 : Math.min(1, Math.max(0, y / max)));
        // The last groups may not reach the top: the chosen one is still the one shown.
        setShownSection(key);
    }

    /** Reports the key of the group at the top of the page now and whenever it changes. */
    public void setOnSectionShown(java.util.function.Consumer<String> handler) {
        this.onSectionShown = handler != null ? handler : key -> { };
        reportShownSection();
        if (shownSection != null) {
            onSectionShown.accept(shownSection);
        }
    }

    /**
     * Shows only the cards whose texts contain the query (case-insensitive) and hides groups
     * without a match; an empty query shows everything.
     */
    public void filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        boolean any = false;
        for (VBox group : groupNodes()) {
            boolean groupMatch = false;
            for (javafx.scene.Node card : cards(group)) {
                StringBuilder words = new StringBuilder();
                collectTexts(card, words);
                boolean match = q.isEmpty() || words.toString().toLowerCase(java.util.Locale.ROOT).contains(q);
                card.setVisible(match);
                card.setManaged(match);
                groupMatch |= match;
            }
            group.setVisible(groupMatch);
            group.setManaged(groupMatch);
            any |= groupMatch;
        }
        noMatch.setVisible(!any);
        noMatch.setManaged(!any);
        scroll.setVvalue(0);
    }

    /** @return whether a control was changed since the settings were loaded or saved */
    public boolean isDirty() {
        return dirty.get();
    }

    private java.util.List<VBox> groupNodes() {
        java.util.List<VBox> list = new java.util.ArrayList<>();
        for (javafx.scene.Node node : groups.getChildren()) {
            if (node instanceof VBox group && group.getId() != null && group.getId().startsWith(GROUP_ID_PREFIX)) {
                list.add(group);
            }
        }
        return list;
    }

    /** @return the cards of a group (the children of its card grid) */
    private static java.util.List<javafx.scene.Node> cards(VBox group) {
        for (javafx.scene.Node node : group.getChildren()) {
            if (node instanceof SettingsCardGrid grid) {
                return grid.getChildren();
            }
        }
        return java.util.List.of();
    }

    /** Appends every label, button, check box text and field prompt below {@code node}. */
    private static void collectTexts(javafx.scene.Node node, StringBuilder into) {
        if (node instanceof javafx.scene.control.Labeled labeled && labeled.getText() != null) {
            into.append(labeled.getText()).append(' ');
        } else if (node instanceof javafx.scene.control.TextInputControl input && input.getPromptText() != null) {
            into.append(input.getPromptText()).append(' ');
        }
        if (node instanceof SettingsCard card) {
            into.append(card.getTitle()).append(' ');
        }
        // Not into controls: their skin children are not part of the page's content.
        if (node instanceof javafx.scene.Parent parent && !(node instanceof javafx.scene.control.Control)) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                collectTexts(child, into);
            }
        }
    }

    private void reportShownSection() {
        java.util.List<VBox> visible = groupNodes().stream().filter(javafx.scene.Node::isVisible).toList();
        if (visible.isEmpty()) {
            return;
        }
        double max = page.getHeight() - scroll.getViewportBounds().getHeight();
        double top = Math.max(0, max) * scroll.getVvalue();
        String key = visible.getFirst().getId();
        for (VBox group : visible) {
            // Before the first layout every group sits at y = 0; only laid-out groups count,
            // otherwise the last group would be reported for a page scrolled to the top.
            if (group.getHeight() > 0
                    && groups.getBoundsInParent().getMinY() + group.getBoundsInParent().getMinY() <= top + 24) {
                key = group.getId();
            }
        }
        setShownSection(key.substring(GROUP_ID_PREFIX.length()));
    }

    private void setShownSection(String key) {
        if (!key.equals(shownSection)) {
            shownSection = key;
            onSectionShown.accept(key);
        }
    }

    // ----- unsaved changes ------------------------------------------------------

    /**
     * Marks the page dirty when a setting control changes. Skips the controls that apply at
     * once (theme, file associations: they are not saved by "Save Settings").
     */
    private void trackChanges(javafx.scene.Parent root) {
        // Theme and accent apply at once and are stored right away: no "unsaved changes"
        java.util.Set<javafx.scene.Node> immediate = new java.util.HashSet<>(
                java.util.List.of(light, dark, systemTheme, accentSwatches, accentPicker,
                        editorFontFamily, editorFontSize, editorLineSpacing, editorRememberZoom));
        immediate.addAll(associationCheckBoxes().keySet());
        javafx.beans.InvalidationListener mark = obs -> {
            if (!loading) {
                dirty.set(true);
                saveStatus.setText("");
            }
        };
        for (javafx.scene.Node node : root.getChildrenUnmodifiable()) {
            if (immediate.contains(node)) {
                continue;
            }
            switch (node) {
                case CheckBox box -> box.selectedProperty().addListener(mark);
                case ToggleButton toggle -> toggle.selectedProperty().addListener(mark);
                case TextField field -> field.textProperty().addListener(mark);
                case Spinner<?> spinner -> spinner.valueProperty().addListener(mark);
                case ComboBox<?> combo -> combo.valueProperty().addListener(mark);
                case javafx.scene.control.ColorPicker picker -> picker.valueProperty().addListener(mark);
                case javafx.scene.control.Control _ -> { } // other controls: do not descend into their skin
                case javafx.scene.Parent parent -> trackChanges(parent);
                default -> { }
            }
        }
    }

    /**
     * Resets the preferences to their built-in defaults after a confirmation. The values only
     * show in the controls; "Save Settings" persists them. Personal data and opt-outs stay:
     * user info, templates, file associations, privacy and extension switches.
     */
    @FXML
    public void onResetDefaults() {
        if (org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(I18n.t("SettingsPanel.reset-settings"),
                I18n.t("SettingsPanel.reset-the-settings-to-their"),
                I18n.t("SettingsPanel.appearance-editor-schema-temp-folder"))) {
            resetToDefaults();
        }
    }

    /** Puts the built-in defaults into the controls (not persisted until saved). */
    void resetToDefaults() {
        // Settings the administrator locked keep their value
        java.util.List<Runnable> keepLocked = new java.util.ArrayList<>();
        for (javafx.scene.Node node : policyLocked) {
            keepLocked.add(restorerOf(node));
        }
        resetToBuiltInDefaults();
        keepLocked.forEach(Runnable::run);
    }

    /** @return an action putting the control's current value back */
    @SuppressWarnings("unchecked")
    private static Runnable restorerOf(javafx.scene.Node node) {
        if (node instanceof javafx.scene.control.CheckBox box) {
            boolean selected = box.isSelected();
            return () -> box.setSelected(selected);
        }
        if (node instanceof ToggleButton toggle) {
            boolean selected = toggle.isSelected();
            return () -> toggle.setSelected(selected);
        }
        if (node instanceof TextField field) {
            String text = field.getText();
            return () -> field.setText(text);
        }
        if (node instanceof Spinner<?> spinner) {
            Object value = spinner.getValue();
            return () -> ((Spinner<Object>) spinner).getValueFactory().setValue(value);
        }
        if (node instanceof ComboBox<?> combo) {
            Object value = combo.getValue();
            return () -> ((ComboBox<Object>) combo).setValue(value);
        }
        return () -> { };
    }

    private void resetToBuiltInDefaults() {
        smallIcons.setSelected(false);
        toolbarLabels.setSelected(true);
        activityBarLabels.setSelected(true);
        toolbarIconSmall.setSelected(true);
        showLeftPanel.setSelected(true);
        showInspector.setSelected(true);
        renderingMode.setValue("Auto");
        maxMemory.setValue(memoryLabel(0));
        indentSpaces.getValueFactory().setValue(4);
        jsonIndent.getValueFactory().setValue(2);
        autoFormat.setSelected(false);
        gridCustomColors.setSelected(false);
        fillGridColorPickers(true);
        xsdPrettyPrint.setSelected(true);
        schematronPretty.setSelected(false);
        recentFilesLimit.getValueFactory().setValue(20);
        xsdAutoSave.setSelected(false);
        xsdAutoSaveInterval.getValueFactory().setValue(5);
        xsdBackup.setSelected(true);
        xsdBackupVersions.getValueFactory().setValue(3);
        backupSeparateDir.setSelected(false);
        parserType.setValue(XmlParserType.XERCES);
        xsltExtensions.setSelected(false);
        htmlActiveContent.setSelected(false);
        xprocUnrestricted.setSelected(false);
        dtdAllowed.setSelected(true);
        schemaLibraryAutoBind.setSelected(true);
        useSystemTemp.setSelected(true);
        logCleanup.setSelected(true);
        logMaxAge.getValueFactory().setValue(14);
        logMaxSize.getValueFactory().setValue(100);
        // the administrator's proxy defaults, if any, else the built-in ones
        showProxySettings(org.fxt.freexmltoolkit.service.ProxySettings.from(
                org.fxt.freexmltoolkit.service.AdminPolicy.current().overlay()));
        trustAllCerts.setSelected(false);
        updateCheck.setSelected(true);
        saveStatus.setText(I18n.t("SettingsPanel.defaults-restored-click-save-settings"));
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
    public void onThemeSystem() {
        applyThemeMode(org.fxt.freexmltoolkit.controls.shell.ThemeManager.Mode.SYSTEM);
    }

    /** A colour picked in the custom accent picker becomes the accent. */
    @FXML
    public void onAccentCustom() {
        javafx.scene.paint.Color color = accentPicker.getValue();
        if (color != null && !showingAccent) {
            applyAccent(org.fxt.freexmltoolkit.controls.theme.AccentPalette.hex(color));
        }
    }

    /** One round swatch per accent preset, the stored accent selected. */
    private void buildAccentSwatches() {
        String current = org.fxt.freexmltoolkit.controls.shell.ThemeManager.currentAccent();
        for (var preset : org.fxt.freexmltoolkit.controls.theme.AccentPalette.PRESETS) {
            ToggleButton swatch = new ToggleButton();
            swatch.setId("settings-accent-" + preset.id());
            swatch.getStyleClass().add("fxt-accent-swatch");
            swatch.setToggleGroup(accentGroup);
            javafx.scene.layout.Region dot = new javafx.scene.layout.Region();
            dot.getStyleClass().add("fxt-accent-swatch-dot");
            dot.setStyle("-fx-background-color: " + preset.hex() + ";");
            swatch.setGraphic(dot);
            String name = I18n.t(preset.labelKey());
            swatch.setAccessibleText(name);
            swatch.setTooltip(new javafx.scene.control.Tooltip(name));
            swatch.setSelected(preset.id().equalsIgnoreCase(current));
            swatch.setOnAction(e -> {
                swatch.setSelected(true); // a click on the selected swatch keeps it
                applyAccent(preset.id());
            });
            accentSwatches.getChildren().add(swatch);
        }
        showAccentInPicker(current);
    }

    /** Shows the accent's base colour in the own-colour picker without choosing it. */
    private void showAccentInPicker(String accent) {
        javafx.scene.paint.Color color = org.fxt.freexmltoolkit.controls.theme.AccentPalette.parse(accent);
        showingAccent = true;
        try {
            accentPicker.setValue(color != null ? color
                    : org.fxt.freexmltoolkit.controls.theme.AccentPalette.PRESETS.getFirst().color());
        } finally {
            showingAccent = false;
        }
    }

    /** Applies and persists an accent (preset id or {@code #rrggbb}) and marks its swatch (for tests/observers). */
    public void applyAccent(String accent) {
        org.fxt.freexmltoolkit.controls.shell.ThemeManager.setAccent(accent);
        showAccentInPicker(accent);
        for (javafx.scene.Node node : accentSwatches.getChildren()) {
            if (node instanceof ToggleButton swatch) {
                swatch.setSelected(swatch.getId().equals("settings-accent-" + accent));
            }
        }
    }

    // ----- editor font ------------------------------------------------------------

    /** Fills the editor-font card and applies every change at once (like theme and accent). */
    private void initEditorFontControls() {
        var font = org.fxt.freexmltoolkit.controls.shared.EditorFont.current();
        // The installed monospace fonts are measured when the list first opens, not with the page
        editorFontFamily.getItems().setAll(new java.util.LinkedHashSet<>(java.util.List.of(
                org.fxt.freexmltoolkit.controls.shared.EditorFont.DEFAULT_FAMILY, font.family())));
        editorFontFamily.setOnShowing(event -> fillEditorFontFamilies());
        showEditorFont(font);
        editorFontFamily.valueProperty().addListener((obs, old, value) -> applyEditorFontFromControls());
        editorFontSize.valueProperty().addListener((obs, old, value) -> applyEditorFontFromControls());
        editorLineSpacing.valueProperty().addListener((obs, old, value) -> applyEditorFontFromControls());
        editorRememberZoom.selectedProperty().addListener((obs, old, value) -> applyEditorFontFromControls());
        org.fxt.freexmltoolkit.controls.shared.EditorFont.settingsProperty().addListener(
                new javafx.beans.value.WeakChangeListener<>(editorFontListener));
    }

    /** Lists the installed monospace fonts (once), keeping the chosen family. */
    private void fillEditorFontFamilies() {
        if (editorFontFamiliesListed) {
            return;
        }
        editorFontFamiliesListed = true;
        java.util.LinkedHashSet<String> families = new java.util.LinkedHashSet<>(
                org.fxt.freexmltoolkit.controls.shared.EditorFont.monospaceFamilies());
        String chosen = editorFontFamily.getValue();
        if (chosen != null && !chosen.isBlank()) {
            families.add(chosen);
        }
        showingEditorFont = true;
        try {
            editorFontFamily.getItems().setAll(families);
            editorFontFamily.setValue(chosen);
        } finally {
            showingEditorFont = false;
        }
    }

    /** Shows an editor-font setting in the card without applying it again. */
    private void showEditorFont(org.fxt.freexmltoolkit.controls.shared.EditorFont.Settings font) {
        showingEditorFont = true;
        try {
            editorFontFamily.setValue(font.family());
            editorFontSize.getValueFactory().setValue(Math.clamp(font.size(),
                    org.fxt.freexmltoolkit.controls.shared.EditorFont.MIN_SETTING_SIZE,
                    org.fxt.freexmltoolkit.controls.shared.EditorFont.MAX_SETTING_SIZE));
            editorLineSpacing.getValueFactory().setValue(Math.round(font.lineSpacing() * 10) / 10.0);
            editorRememberZoom.setSelected(font.rememberZoom());
        } finally {
            showingEditorFont = false;
        }
        editorFontPreview.setStyle(org.fxt.freexmltoolkit.controls.shared.EditorFont.inlineStyle(font, font.size(), "pt"));
    }

    private void applyEditorFontFromControls() {
        if (showingEditorFont) {
            return;
        }
        String family = editorFontFamily.getValue();
        Integer size = editorFontSize.getValue();
        Double spacing = editorLineSpacing.getValue();
        applyEditorFont(new org.fxt.freexmltoolkit.controls.shared.EditorFont.Settings(family,
                size == null ? org.fxt.freexmltoolkit.controls.shared.EditorFont.DEFAULT_SIZE : size,
                spacing == null ? org.fxt.freexmltoolkit.controls.shared.EditorFont.DEFAULT_LINE_SPACING : spacing,
                editorRememberZoom.isSelected()));
    }

    /** Applies and persists an editor font, every editor follows (for tests/observers). */
    public void applyEditorFont(org.fxt.freexmltoolkit.controls.shared.EditorFont.Settings font) {
        org.fxt.freexmltoolkit.controls.shared.EditorFont.update(font);
        showEditorFont(org.fxt.freexmltoolkit.controls.shared.EditorFont.current());
    }

    /** Puts the editor font back to the built-in default (JetBrains Mono 11 pt, single spacing). */
    @FXML
    public void onEditorFontReset() {
        applyEditorFont(org.fxt.freexmltoolkit.controls.shared.EditorFont.Settings.defaults());
    }

    /** @return the accent swatches (for tests) */
    java.util.List<ToggleButton> accentSwatchButtons() {
        return accentSwatches.getChildren().stream().filter(ToggleButton.class::isInstance)
                .map(ToggleButton.class::cast).toList();
    }

    @FXML
    public void onClearTemp() {
        clearInBackground(this::clearTempFolder, "SettingsPanel.clearedFiles");
    }

    @FXML
    public void onClearCache() {
        if (org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(I18n.t("SettingsPanel.clear-cache"),
                I18n.t("SettingsPanel.clear-the-local-cache-folder"),
                I18n.t("SettingsPanel.this-deletes-all-cached-files", cacheFolder().getAbsolutePath()))) {
            clearInBackground(this::clearCacheFolder, "SettingsPanel.clearedCachedFiles");
        }
    }

    /** Deletes off the FX thread (thousands of cached schemas took seconds) and reports the count. */
    private void clearInBackground(java.util.function.IntSupplier clear, String resultKey) {
        tempStatus.setText(I18n.t("SettingsPanel.clearing"));
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            int cleared = clear.getAsInt();
            javafx.application.Platform.runLater(() -> {
                tempStatus.setText(I18n.t(resultKey, cleared));
                refreshFolderUsage();
            });
        });
    }

    /** What a clear button would delete: number of files and their total size. */
    record FolderUsage(int files, long bytes) {
        static final FolderUsage NONE = new FolderUsage(0, 0);
    }

    /** Shows on both clear buttons how much they would free, counted off the FX thread. */
    private void refreshFolderUsage() {
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            FolderUsage temp = tempFolderUsage();
            FolderUsage cache = usageOf(cacheFolder(), true);
            javafx.application.Platform.runLater(() -> {
                clearTempButton.setText(clearLabel("Clear Temp Folder", temp));
                clearCacheButton.setText(clearLabel("Clear Cache Folder", cache));
            });
        });
    }

    /** @return the button text with the space in use, e.g. "Clear Cache Folder · 12.3 MB (45 files)" */
    static String clearLabel(String action, FolderUsage usage) {
        if (usage.files() == 0) {
            return action + " \u00b7 empty";
        }
        return action + " \u00b7 " + formatSize(usage.bytes()) + " (" + usage.files()
                + (usage.files() == 1 ? " file)" : " files)");
    }

    /** @return {@code bytes} as B, KB, MB or GB with one decimal */
    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    /** Files directly inside the effective temp folder: what {@link #clearTempFolder()} deletes. */
    private static FolderUsage tempFolderUsage() {
        try {
            String folder = ServiceRegistry.get(PropertiesService.class).getTempFolder();
            return folder == null || folder.isBlank() ? FolderUsage.NONE : usageOf(new File(folder), false);
        } catch (RuntimeException e) {
            return FolderUsage.NONE;
        }
    }

    /**
     * Counts the files in {@code dir} — directly inside it, or {@code recursive}ly as
     * {@link #deleteContents} deletes them. Symbolic links are not followed.
     */
    static FolderUsage usageOf(File dir, boolean recursive) {
        if (dir == null || !dir.isDirectory()) {
            return FolderUsage.NONE;
        }
        int[] files = {0};
        long[] bytes = {0};
        try (java.util.stream.Stream<java.nio.file.Path> paths = recursive
                ? java.nio.file.Files.walk(dir.toPath())
                : java.nio.file.Files.list(dir.toPath())) {
            paths.filter(java.nio.file.Files::isRegularFile).forEach(path -> {
                try {
                    bytes[0] += java.nio.file.Files.size(path);
                    files[0]++;
                } catch (java.io.IOException | SecurityException ignored) {
                    // vanished or unreadable: not counted
                }
            });
        } catch (java.io.IOException | java.io.UncheckedIOException | SecurityException e) {
            logger.debug("Could not measure {}: {}", dir, e.toString());
        }
        return new FolderUsage(files[0], bytes[0]);
    }

    /** Confirms "Clean Up Now" for a log folder; replaceable by tests. */
    static java.util.function.Predicate<java.nio.file.Path> logCleanupConfirmer = dir ->
            org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(I18n.t("SettingsPanel.clean-up-log-files"),
                    I18n.t("SettingsPanel.delete-old-log-files-now"),
                    I18n.t("SettingsPanel.log-files-beyond-the-limits", dir));

    /** The log folder the page works on; replaceable by tests. */
    static java.util.function.Supplier<java.nio.file.Path> logDirectory =
            org.fxt.freexmltoolkit.service.LogCleanupService::logDirectory;

    @FXML
    public void onCleanLogsNow() {
        java.nio.file.Path dir = logDirectory.get();
        int maxAge = logMaxAge.getValue();
        int maxSize = logMaxSize.getValue();
        if (maxAge <= 0 && maxSize <= 0) {
            logStatus.setText(I18n.t("SettingsPanel.set-a-number-of-days"));
            return;
        }
        if (!logCleanupConfirmer.test(dir)) {
            return;
        }
        cleanLogsButton.setDisable(true);
        logStatus.setText(I18n.t("SettingsPanel.cleaning-up"));
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var result = org.fxt.freexmltoolkit.service.LogCleanupService.clean(dir, maxAge, maxSize);
            javafx.application.Platform.runLater(() -> {
                cleanLogsButton.setDisable(false);
                refreshLogStatus("Deleted " + result.files() + " file(s), "
                        + formatMegabytes(result.bytes()) + " freed. ");
            });
        });
    }

    /** One row per plugin: a switch (name, version, origin) and its description or error. */
    private void buildPluginRows() {
        var manager = org.fxt.freexmltoolkit.plugin.PluginManager.getInstance();
        var policy = org.fxt.freexmltoolkit.service.AdminPolicy.current();
        pluginRows.getChildren().clear();
        pluginSwitches.clear();
        for (var info : manager.plugins()) {
            String title = info.name() + (info.version() == null || info.version().isBlank() ? "" : " " + info.version())
                    + (info.origin() == org.fxt.freexmltoolkit.plugin.PluginManager.Origin.EXTERNAL
                            ? " (" + (info.jar() != null ? info.jar().getFileName() : "external") + ")" : " (built-in)");
            CheckBox box = new CheckBox(title);
            box.setId("settings-plugin-" + info.id());
            box.setSelected(info.state() != org.fxt.freexmltoolkit.plugin.PluginManager.State.DISABLED);
            String detail = info.state() == org.fxt.freexmltoolkit.plugin.PluginManager.State.FAILED
                    ? "Failed: " + info.error() : info.description();
            if (info.state() == org.fxt.freexmltoolkit.plugin.PluginManager.State.FAILED) {
                box.setSelected(false);
                box.setDisable(true);
            } else if (policy.isLocked(org.fxt.freexmltoolkit.plugin.PluginManager.enabledKey(info.id()))) {
                box.setDisable(true);
                box.getStyleClass().add("fxt-policy-locked");
            } else {
                pluginSwitches.put(info.id(), box);
            }
            Label description = new Label(detail == null ? "" : detail);
            description.setWrapText(true);
            description.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            description.getStyleClass().add("fxt-placeholder-text");
            pluginRows.getChildren().add(new VBox(2, box, description));
        }
        if (pluginRows.getChildren().isEmpty()) {
            Label none = new Label(I18n.t("SettingsPanel.no-plugins-found"));
            none.getStyleClass().add("fxt-placeholder-text");
            pluginRows.getChildren().add(none);
        }
    }

    @FXML
    public void onOpenPluginsFolder() {
        java.nio.file.Path dir = org.fxt.freexmltoolkit.plugin.PluginManager.pluginsFolder();
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            saveStatus.setText(I18n.t("SettingsPanel.could-not-create", dir));
            return;
        }
        org.fxt.freexmltoolkit.util.DesktopActions.open(dir.toFile(), e -> saveStatus.setText(I18n.t("SettingsPanel.could-not-open", dir)));
    }

    // test access
    java.util.Map<String, CheckBox> pluginSwitchesForTesting() {
        return pluginSwitches;
    }

    @FXML
    public void onOpenLogFolder() {
        java.nio.file.Path dir = logDirectory.get();
        org.fxt.freexmltoolkit.util.DesktopActions.open(dir.toFile(),
                e -> logStatus.setText(I18n.t("SettingsPanel.could-not-open", dir)));
    }

    /** Shows the log folder with its file count and size, counted off the FX thread, after {@code prefix}. */
    private void refreshLogStatus(String prefix) {
        java.nio.file.Path dir = logDirectory.get();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var usage = org.fxt.freexmltoolkit.service.LogCleanupService.usage(dir);
            javafx.application.Platform.runLater(() -> logStatus.setText((prefix != null ? prefix : "")
                    + "Log folder: " + dir + " (" + usage.files() + " file(s), "
                    + formatMegabytes(usage.bytes()) + ")"));
        });
    }

    static String formatMegabytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ----- HTTP proxy -----------------------------------------------------------

    /** The proxy modes in the order the combo box lists them. */
    static final java.util.Map<org.fxt.freexmltoolkit.service.ProxySettings.Mode, String> PROXY_MODE_LABELS;

    static {
        var labels = new java.util.EnumMap<org.fxt.freexmltoolkit.service.ProxySettings.Mode, String>(
                org.fxt.freexmltoolkit.service.ProxySettings.Mode.class);
        labels.put(org.fxt.freexmltoolkit.service.ProxySettings.Mode.SYSTEM, "System proxy (automatic)");
        labels.put(org.fxt.freexmltoolkit.service.ProxySettings.Mode.MANUAL, "Manual proxy");
        labels.put(org.fxt.freexmltoolkit.service.ProxySettings.Mode.NONE, "No proxy (direct connection)");
        PROXY_MODE_LABELS = java.util.Collections.unmodifiableMap(labels);
    }

    /** The controls whose setting the administrator locked ({@link org.fxt.freexmltoolkit.service.AdminPolicy}). */
    private final java.util.Set<javafx.scene.Node> policyLocked = new java.util.HashSet<>();

    /** The settings keys each control edits, for the administrator's locks. */
    java.util.Map<javafx.scene.Node, String[]> policyKeys() {
        var keys = new java.util.LinkedHashMap<javafx.scene.Node, String[]>();
        keys.put(light, new String[] {"ui.theme"});
        keys.put(dark, new String[] {"ui.theme"});
        keys.put(systemTheme, new String[] {"ui.theme"});
        keys.put(accentSwatches, new String[] {org.fxt.freexmltoolkit.controls.shell.ThemeManager.ACCENT_KEY});
        keys.put(accentPicker, new String[] {org.fxt.freexmltoolkit.controls.shell.ThemeManager.ACCENT_KEY});
        keys.put(editorFontFamily, new String[] {org.fxt.freexmltoolkit.controls.shared.EditorFont.FAMILY_KEY});
        keys.put(editorFontSize, new String[] {org.fxt.freexmltoolkit.controls.shared.EditorFont.SIZE_KEY});
        keys.put(editorLineSpacing, new String[] {org.fxt.freexmltoolkit.controls.shared.EditorFont.LINE_SPACING_KEY});
        keys.put(editorRememberZoom, new String[] {org.fxt.freexmltoolkit.controls.shared.EditorFont.REMEMBER_ZOOM_KEY});
        keys.put(indentSpaces, new String[] {"xml.indent.spaces"});
        keys.put(jsonIndent, new String[] {"json.indent.spaces"});
        keys.put(autoFormat, new String[] {"xml.autoformat.after.loading"});
        keys.put(xsdPrettyPrint, new String[] {"xsd.prettyPrint.onSave"});
        keys.put(schematronPretty, new String[] {"schematron.prettyPrint.onLoad"});
        keys.put(recentFilesLimit, new String[] {"recentFiles.limit"});
        keys.put(xsdAutoSave, new String[] {"xsd.autoSave.enabled"});
        keys.put(xsdAutoSaveInterval, new String[] {"xsd.autoSave.interval"});
        keys.put(xsdBackup, new String[] {"xsd.backup.enabled"});
        keys.put(xsdBackupVersions, new String[] {"xsd.backup.versions"});
        keys.put(backupSeparateDir, new String[] {"backup.use.separate.directory"});
        keys.put(backupDir, new String[] {"backup.directory"});
        keys.put(parserType, new String[] {"xml.parser.type"});
        keys.put(xsltExtensions, new String[] {"security.xslt.allow.extensions"});
        keys.put(htmlActiveContent, new String[] {org.fxt.freexmltoolkit.controls.shell.SafeWebView.ALLOW_ACTIVE_CONTENT});
        keys.put(xprocUnrestricted, new String[] {XProcSecurity.UNRESTRICTED});
        keys.put(dtdAllowed, new String[] {org.fxt.freexmltoolkit.service.grammar.GrammarPolicy.DTD_ALLOWED});
        keys.put(renderingMode, new String[] {"rendering.mode"});
        keys.put(maxMemory, new String[] {org.fxt.freexmltoolkit.app.MemoryLimit.PROPERTY});
        keys.put(useSystemTemp, new String[] {"useSystemTempFolder"});
        keys.put(customTempDir, new String[] {"customTempFolder"});
        keys.put(logCleanup, new String[] {PropertiesService.LOG_CLEANUP_ENABLED});
        keys.put(logMaxAge, new String[] {PropertiesService.LOG_CLEANUP_MAX_AGE_DAYS});
        keys.put(logMaxSize, new String[] {PropertiesService.LOG_CLEANUP_MAX_SIZE_MB});
        keys.put(updateCheck, new String[] {"update.check.enabled"});
        keys.put(smallIcons, new String[] {"ui.use.small.icons"});
        keys.put(toolbarLabels, new String[] {"toolbar.show.labels"});
        keys.put(activityBarLabels, new String[] {"activitybar.show.labels"});
        keys.put(toolbarIconSmall, new String[] {"toolbar.icon.size"});
        keys.put(toolbarIconLarge, new String[] {"toolbar.icon.size"});
        keys.put(showLeftPanel, new String[] {"shell.leftPanel.visible"});
        keys.put(showInspector, new String[] {"shell.inspector.visible"});
        keys.put(proxyMode, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_USE_SYSTEM,
                org.fxt.freexmltoolkit.service.ProxySettings.KEY_MANUAL});
        keys.put(proxyHost, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_HOST});
        keys.put(proxyPort, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_PORT});
        keys.put(proxyBypass, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_NON_PROXY_HOSTS});
        keys.put(proxyUser, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_USER});
        keys.put(proxyPassword, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_PASSWORD});
        keys.put(proxyDomain, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_DOMAIN});
        keys.put(proxyWindowsSignOn, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_WINDOWS_SIGN_ON});
        keys.put(connectTimeout, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_CONNECT_TIMEOUT});
        keys.put(readTimeout, new String[] {org.fxt.freexmltoolkit.service.ProxySettings.KEY_READ_TIMEOUT});
        keys.put(userName, new String[] {"user.name"});
        keys.put(userEmail, new String[] {"user.email"});
        keys.put(userCompany, new String[] {"user.company"});
        keys.put(trustAllCerts, new String[] {"ssl.trustAllCerts"});
        keys.put(telemetryUsage, new String[] {PropertiesService.TELEMETRY_USAGE_ENABLED});
        keys.put(telemetryErrors, new String[] {PropertiesService.TELEMETRY_ERRORS_ENABLED});
        keys.put(execStatsEnabled, new String[] {org.fxt.freexmltoolkit.service.DeveloperPropertyKeys.EXECUTION_STATS_ENABLED});
        keys.put(fundsXmlEnabled, new String[] {org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED});
        keys.put(templatesDir, new String[] {"templates.directory"});
        keys.put(schemaLibraryAutoBind, new String[] {"schemaLibrary.autoBind.enabled"});
        keys.put(uiLanguage, new String[] {org.fxt.freexmltoolkit.i18n.I18n.SETTING});
        keys.put(foProcessors, new String[] {org.fxt.freexmltoolkit.service.fo.FoProcessorStore.KEY_PROCESSORS,
                org.fxt.freexmltoolkit.service.fo.FoProcessorStore.KEY_DEFAULT});
        return keys;
    }

    /** Finds the controls whose setting the administrator locked. */
    private void collectPolicyLocks() {
        var policy = org.fxt.freexmltoolkit.service.AdminPolicy.current();
        policyKeys().forEach((node, keys) -> {
            if (policy.isAnyLocked(keys)) {
                policyLocked.add(node);
            }
        });
    }

    /** Binds a control's disabled state, unless the administrator locked its setting (then it stays disabled). */
    private void bindDisable(javafx.scene.Node node, javafx.beans.value.ObservableValue<Boolean> condition) {
        if (!policyLocked.contains(node)) {
            node.disableProperty().bind(condition);
        }
    }

    /** Disables the locked controls and explains why above the groups. */
    private void applyPolicyLocks() {
        for (javafx.scene.Node node : policyLocked) {
            node.setDisable(true);
            node.getStyleClass().add("fxt-policy-locked");
        }
        var policy = org.fxt.freexmltoolkit.service.AdminPolicy.current();
        boolean updatesOff = !policy.updatesAllowed();
        if (!policyLocked.isEmpty() || updatesOff) {
            policyNotice.setText(I18n.t("SettingsPanel.some-settings-are-managed-by", (updatesOff ? I18n.t("SettingsPanel.updatesByAdmin") : ""), policy.policyFile()));
            policyNotice.setVisible(true);
            policyNotice.setManaged(true);
        }
    }

    /** @return whether the administrator locked the setting of this control (tests) */
    boolean isPolicyLocked(javafx.scene.Node node) {
        return policyLocked.contains(node);
    }

    private void showProxySettings(org.fxt.freexmltoolkit.service.ProxySettings settings) {
        proxyMode.setValue(PROXY_MODE_LABELS.get(settings.mode()));
        proxyHost.setText(settings.host());
        proxyPort.setText(settings.port() > 0 ? String.valueOf(settings.port()) : "");
        proxyBypass.setText(String.join(", ", settings.nonProxyHosts()));
        proxyUser.setText(settings.user());
        proxyPassword.setText(settings.password());
        proxyDomain.setText(settings.domain());
        proxyWindowsSignOn.setSelected(settings.windowsSignOn());
        connectTimeout.getValueFactory().setValue(settings.connectTimeoutMs() / 1000);
        readTimeout.getValueFactory().setValue(settings.readTimeoutMs() / 1000);
    }

    /** @return the proxy settings as the form shows them (saved or not) */
    org.fxt.freexmltoolkit.service.ProxySettings proxySettingsFromForm() {
        var mode = org.fxt.freexmltoolkit.service.ProxySettings.Mode.SYSTEM;
        for (var entry : PROXY_MODE_LABELS.entrySet()) {
            if (entry.getValue().equals(proxyMode.getValue())) {
                mode = entry.getKey();
            }
        }
        int port;
        try {
            port = Integer.parseInt(proxyPort.getText().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        return new org.fxt.freexmltoolkit.service.ProxySettings(mode, proxyHost.getText(), port,
                org.fxt.freexmltoolkit.service.ProxySettings.parseHostList(proxyBypass.getText()),
                proxyUser.getText(), proxyPassword.getText(), proxyDomain.getText(),
                proxyWindowsSignOn.isSelected(), connectTimeout.getValue() * 1000, readTimeout.getValue() * 1000);
    }

    /** Tests the connection with the values in the form (not the saved ones), off the FX thread. */
    @FXML
    public void onTestConnection() {
        java.net.URI uri;
        try {
            uri = java.net.URI.create(proxyTestUrl.getText().trim());
            if (uri.getScheme() == null || !uri.getScheme().toLowerCase(java.util.Locale.ROOT).startsWith("http")) {
                throw new IllegalArgumentException("not an http(s) URL");
            }
        } catch (IllegalArgumentException e) {
            proxyTestStatus.setText(I18n.t("SettingsPanel.enter-an-http-or-https"));
            return;
        }
        java.util.Properties test = new java.util.Properties();
        proxySettingsFromForm().writeTo(test);
        test.setProperty("ssl.trustAllCerts", String.valueOf(trustAllCerts.isSelected()));
        proxyTestButton.setDisable(true);
        proxyTestStatus.setText(I18n.t("SettingsPanel.connecting-to", uri.getHost()));
        java.net.URI target = uri;
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var result = org.fxt.freexmltoolkit.service.ConnectionServiceImpl.getInstance()
                    .testHttpRequest(target, test);
            javafx.application.Platform.runLater(() -> {
                proxyTestButton.setDisable(false);
                proxyTestStatus.setText(describeTest(result));
            });
        });
    }

    /** @return a one-line verdict on a connection test */
    static String describeTest(org.fxt.freexmltoolkit.domain.ConnectionResult result) {
        int status = result.httpStatus();
        if (status == 0) {
            String why = result.resultBody() == null || result.resultBody().isBlank() ? "no answer" : result.resultBody();
            return "Failed: " + why;
        }
        if (status == 407) {
            return "Failed: the proxy asks for authentication (HTTP 407) — check user, password and domain.";
        }
        String verdict = status < 400 ? "Connected" : "Reached the server, but it answered";
        return verdict + ": HTTP " + status + " in " + result.duration() + " ms";
    }

    @FXML
    public void onClearStatistics() {
        if (org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(I18n.t("SettingsPanel.clear-statistics"),
                I18n.t("SettingsPanel.clear-all-progress-data"),
                I18n.t("SettingsPanel.this-will-permanently-delete-all"))) {
            UsageTrackingServiceImpl.getInstance().clearStatistics();
            usageStatus.setText(I18n.t("SettingsPanel.usage-statistics-cleared"));
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
        dirty.set(false);
        saveStatus.setText(I18n.t("SettingsPanel.settings-saved"));
        refreshFolderUsage(); // the temp folder may have changed
        org.fxt.freexmltoolkit.service.ConnectionServiceImpl.getInstance().applyGlobalSettings();
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
                org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("SettingsPanel.templates"),
                        I18n.t("SettingsPanel.the-template-could-not-be"),
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
        if (org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(I18n.t("SettingsPanel.delete-template"),
                I18n.t("SettingsPanel.delete-template-2", selected.getName()),
                I18n.t("SettingsPanel.this-permanently-removes-the-template"))) {
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
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(t, "settings.templates"); // absent registry in tests is not reported by the no-op service
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

    // ----- grid table colours ---------------------------------------------------

    /** @return the "custom cell colours" switch (test hook) */
    CheckBox gridCustomColorsForTest() {
        return gridCustomColors;
    }

    /** @return the table-header colour picker (test hook) */
    javafx.scene.control.ColorPicker gridHeaderColorForTest() {
        return gridHeaderColor;
    }

    private java.util.Map<org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.Slot,
            javafx.scene.control.ColorPicker> gridColorPickers() {
        var slots = org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.Slot.class;
        java.util.Map<org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.Slot,
                javafx.scene.control.ColorPicker> map = new java.util.EnumMap<>(slots);
        map.put(org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.Slot.TABLE_HEADER, gridHeaderColor);
        map.put(org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.Slot.MISSING_CELL, gridMissingColor);
        map.put(org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.Slot.SELECTION, gridSelectionColor);
        map.put(org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.Slot.FOCUS, gridFocusColor);
        return map;
    }

    /** Fills the pickers with the current theme's colours (the defaults when {@code defaults}). */
    private void fillGridColorPickers(boolean defaults) {
        var theme = org.fxt.freexmltoolkit.controls.theme.DesignTokens.currentTheme();
        gridColorsTheme.setText(I18n.t("SettingsPanel.colours-for-the-theme-switch", (theme == org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme.DARK
                ? I18n.t("SettingsPanel.themeDark") : I18n.t("SettingsPanel.themeLight"))));
        gridColorPickers().forEach((slot, picker) -> picker.setValue(defaults
                ? org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.defaultColor(theme, slot)
                : org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.resolve(theme, slot)));
    }

    private void loadGridColors(PropertiesService props) {
        var theme = org.fxt.freexmltoolkit.controls.theme.DesignTokens.currentTheme();
        String themeKey = org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.key(theme);
        boolean any = false;
        for (var slot : org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.Slot.values()) {
            String hex = props.getGridColor(org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.key(slot), themeKey);
            javafx.scene.paint.Color color = null;
            if (hex != null) {
                try {
                    color = javafx.scene.paint.Color.web(hex);
                    any = true;
                } catch (IllegalArgumentException ignored) {
                    // invalid stored value: default
                }
            }
            org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.setOverride(theme, slot, color);
        }
        gridCustomColors.setSelected(any);
        fillGridColorPickers(false);
        gridColorPickers().values().forEach(p -> p.disableProperty().bind(gridCustomColors.selectedProperty().not()));
    }

    /** Stores the pickers' colours for the current theme (or clears them) and repaints the grids. */
    private void saveGridColors(PropertiesService props) {
        var theme = org.fxt.freexmltoolkit.controls.theme.DesignTokens.currentTheme();
        String themeKey = org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.key(theme);
        gridColorPickers().forEach((slot, picker) -> {
            javafx.scene.paint.Color color = gridCustomColors.isSelected() ? picker.getValue() : null;
            if (color != null && color.equals(org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.defaultColor(theme, slot))) {
                color = null; // the default needs no override
            }
            props.setGridColor(org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.key(slot), themeKey,
                    color == null ? null : org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.toHex(color));
            org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors.setOverride(theme, slot, color);
        });
    }

    /** Loads the persisted settings into the controls. */
    private void loadSettings() {
        loading = true;
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            indentSpaces.getValueFactory().setValue(props.getXmlIndentSpaces());
            jsonIndent.getValueFactory().setValue(props.getJsonIndentSpaces());
            autoFormat.setSelected(props.isXmlAutoFormatAfterLoading());
            loadGridColors(props);
            xsdPrettyPrint.setSelected(props.isXsdPrettyPrintOnSave());
            schematronPretty.setSelected(props.isSchematronPrettyPrintOnLoad());
            recentFilesLimit.getValueFactory().setValue(props.getRecentFilesLimit());
            xsdAutoSave.setSelected(props.isXsdAutoSaveEnabled());
            xsdAutoSaveInterval.getValueFactory().setValue(props.getXsdAutoSaveInterval());
            xsdBackup.setSelected(props.isXsdBackupEnabled());
            xsdBackupVersions.getValueFactory().setValue(props.getXsdBackupVersions());
            backupSeparateDir.setSelected(props.isBackupUseSeparateDirectory());
            backupDir.setText(orEmpty(props.getBackupDirectory()));
            parserType.setValue(props.getXmlParserType());
            xsltExtensions.setSelected(props.isXsltExtensionsAllowed());
            htmlActiveContent.setSelected(Boolean.parseBoolean(
                    props.get(org.fxt.freexmltoolkit.controls.shell.SafeWebView.ALLOW_ACTIVE_CONTENT)));
            xprocUnrestricted.setSelected(Boolean.parseBoolean(props.get(XProcSecurity.UNRESTRICTED)));
            dtdAllowed.setSelected(!"false".equalsIgnoreCase(props.get(org.fxt.freexmltoolkit.service.grammar.GrammarPolicy.DTD_ALLOWED)));
            renderingMode.setValue(renderingModeLabel(props.getRenderingMode()));
            maxMemory.setValue(memoryLabel(org.fxt.freexmltoolkit.app.MemoryLimit.configuredMb(
                    props.get(org.fxt.freexmltoolkit.app.MemoryLimit.PROPERTY))));
            useSystemTemp.setSelected(props.isUseSystemTempFolder());
            logCleanup.setSelected(props.isLogCleanupEnabled());
            logMaxAge.getValueFactory().setValue(props.getLogCleanupMaxAgeDays());
            logMaxSize.getValueFactory().setValue(props.getLogCleanupMaxSizeMb());
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
            showProxySettings(org.fxt.freexmltoolkit.service.ProxySettings.from(props.loadProperties()));
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
            uiLanguage.setValue(org.fxt.freexmltoolkit.i18n.I18n.Language.fromCode(
                    props.get(org.fxt.freexmltoolkit.i18n.I18n.SETTING)));
            fundsXmlEnabled.setSelected(Boolean.parseBoolean(
                    props.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED) == null
                            ? "false"
                            : props.get(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED)));
            templatesDir.setText(orEmpty(props.getTemplatesDirectory()));
            schemaLibraryAutoBind.setSelected(props.isSchemaLibraryAutoBindEnabled());
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(t, "settings.load"); // absent registry in tests is not reported by the no-op service
            // properties service unavailable (e.g. tests) — controls keep their defaults
        } finally {
            loading = false;
        }
        reloadTemplatesList();
    }

    /**
     * Refreshes the read-only rendering status: the actually active Prism pipeline
     * (read now, since the toolkit is up) and the detected GPU (queried off the UI thread).
     */
    private void refreshRenderingStatus() {
        String active = org.fxt.freexmltoolkit.util.RenderingStatus.activePipelineDescription();
        renderingActiveStatus.setText(org.fxt.freexmltoolkit.i18n.I18n.t("SettingsPanel.activePipeline", active != null ? active : "?"));
        renderingGpuStatus.setText(org.fxt.freexmltoolkit.i18n.I18n.t("SettingsPanel.detectedGpu", "…"));

        Thread t = new Thread(() -> {
            java.util.List<String> gpus =
                    org.fxt.freexmltoolkit.util.RenderingPipelineDetector.detectAdapterNames();
            String text = gpus.isEmpty()
                    ? org.fxt.freexmltoolkit.i18n.I18n.t("SettingsPanel.detectedGpu", "?")
                    : org.fxt.freexmltoolkit.i18n.I18n.t("SettingsPanel.detectedGpu", String.join(", ", gpus));
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
            saveGridColors(props);
            props.setXsdPrettyPrintOnSave(xsdPrettyPrint.isSelected());
            props.setSchematronPrettyPrintOnLoad(schematronPretty.isSelected());
            props.setRecentFilesLimit(recentFilesLimit.getValue());
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
            props.set(org.fxt.freexmltoolkit.controls.shell.SafeWebView.ALLOW_ACTIVE_CONTENT,
                    String.valueOf(htmlActiveContent.isSelected()));
            props.set(XProcSecurity.UNRESTRICTED, String.valueOf(xprocUnrestricted.isSelected()));
            props.set(org.fxt.freexmltoolkit.service.grammar.GrammarPolicy.DTD_ALLOWED, String.valueOf(dtdAllowed.isSelected()));
            props.setRenderingMode(renderingModeValue(renderingMode.getValue()));
            props.set(org.fxt.freexmltoolkit.app.MemoryLimit.PROPERTY, String.valueOf(memoryValue(maxMemory.getValue())));
            props.setUseSystemTempFolder(useSystemTemp.isSelected());
            props.setLogCleanupEnabled(logCleanup.isSelected());
            props.setLogCleanupMaxAgeDays(logMaxAge.getValue());
            props.setLogCleanupMaxSizeMb(logMaxSize.getValue());
            props.setCustomTempFolder(customTempDir.getText());
            props.setUpdateCheckEnabled(updateCheck.isSelected());
            props.setUseSmallIcons(smallIcons.isSelected());
            props.setToolbarShowLabels(toolbarLabels.isSelected());
            props.setActivityBarShowLabels(activityBarLabels.isSelected());
            props.setToolbarIconSize(toolbarIconLarge.isSelected() ? "large" : "small");
            props.set("shell.leftPanel.visible", String.valueOf(showLeftPanel.isSelected()));
            props.set("shell.inspector.visible", String.valueOf(showInspector.isSelected()));
            java.util.Properties proxy = new java.util.Properties();
            proxySettingsFromForm().writeTo(proxy);
            for (String key : proxy.stringPropertyNames()) {
                props.set(key, proxy.getProperty(key));
            }
            props.set("user.name", userName.getText().trim());
            props.set("user.email", userEmail.getText().trim());
            props.set("user.company", userCompany.getText().trim());
            props.set("ssl.trustAllCerts", String.valueOf(trustAllCerts.isSelected()));
            UsageTrackingServiceImpl.getInstance()
                    .setTrackingEnabled(trackingEnabled.isSelected());
            saveTelemetrySettings(props);
            props.set(org.fxt.freexmltoolkit.service.DeveloperPropertyKeys.EXECUTION_STATS_ENABLED,
                    String.valueOf(execStatsEnabled.isSelected()));
            if (uiLanguage.getValue() != null) {
                props.set(org.fxt.freexmltoolkit.i18n.I18n.SETTING, uiLanguage.getValue().code());
            }
            pluginSwitches.forEach((id, box) -> props.set(
                    org.fxt.freexmltoolkit.plugin.PluginManager.enabledKey(id), String.valueOf(box.isSelected())));
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
        } catch (Throwable t) {
            // Reported with its stack (where = dialog.Settings) and told to the user: a settings
            // save that fails silently looks like a save that worked.
            org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("SettingsPanel.settings"),
                    I18n.t("SettingsPanel.the-settings-could-not-be"),
                    I18n.t("SettingsPanel.check-that-the-settings-file"), t);
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
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(t, "settings.telemetry_save"); // absent registry in tests is not reported by the no-op service
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
            assocStatus.setText(I18n.t("SettingsPanel.file-association-service-unavailable"));
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
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(t, "settings.associations.load"); // absent registry in tests is not reported by the no-op service
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
            assocStatus.setText(I18n.t("SettingsPanel.select-at-least-one-file"));
            return;
        }
        FileAssociationService svc = ServiceRegistry.get(FileAssociationService.class);
        try {
            PropertiesService props = ServiceRegistry.get(PropertiesService.class);
            props.set("fileAssociations.selected", types.stream()
                    .map(Enum::name).reduce((a, b) -> a + "," + b).orElse(""));
        } catch (Throwable t) {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(t, "settings.associations.save"); // absent registry in tests is not reported by the no-op service
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
                    org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("SettingsPanel.file-associations"),
                            result.message(),
                            I18n.t("SettingsPanel.check-the-details-below-and"),
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

    /**
     * Re-reads the extension switches (FundsXML) after a plugin changed them elsewhere (the FundsXML
     * hint), without marking the page dirty, so a later save keeps the new value.
     */
    public void reloadExtensionToggles() {
        boolean wasLoading = loading;
        loading = true;
        try {
            fundsXmlEnabled.setSelected(FundsXmlRunner.isEnabled());
        } finally {
            loading = wasLoading;
        }
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

    /** The labels of the Maximum memory choices: Automatic, then the fixed limits. */
    private static java.util.List<String> memoryChoices() {
        java.util.List<String> labels = new java.util.ArrayList<>();
        labels.add(memoryLabel(0));
        for (long mb : org.fxt.freexmltoolkit.app.MemoryLimit.choicesMb(
                org.fxt.freexmltoolkit.app.MemoryLimit.physicalMemoryMb())) {
            labels.add(memoryLabel(mb));
        }
        return labels;
    }

    /** @return the label of a limit in MB ({@code 0} = Automatic), e.g. {@code 8 GB} */
    static String memoryLabel(long mb) {
        if (mb <= 0) {
            return org.fxt.freexmltoolkit.i18n.I18n.t("Shell.queryTarget.automatic");
        }
        return mb % 1024 == 0 ? (mb / 1024) + " GB" : mb + " MB";
    }

    /** @return the limit in MB of a label from {@link #memoryLabel}, {@code 0} for Automatic */
    static long memoryValue(String label) {
        if (label == null) {
            return 0;
        }
        String l = label.strip();
        try {
            if (l.endsWith(" GB")) {
                return Long.parseLong(l.substring(0, l.length() - 3)) * 1024;
            }
            if (l.endsWith(" MB")) {
                return Long.parseLong(l.substring(0, l.length() - 3));
            }
        } catch (NumberFormatException e) {
            return 0;
        }
        return 0;
    }

    /** Shows the limit this session runs with, the installed memory and whether a restart is due. */
    private void refreshMemoryStatus() {
        long currentMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long physicalMb = org.fxt.freexmltoolkit.app.MemoryLimit.physicalMemoryMb();
        StringBuilder text = new StringBuilder("Current limit: ").append(formatMb(currentMb));
        if (physicalMb > 0) {
            text.append(" of ").append(formatMb(physicalMb)).append(" installed");
        }
        long chosen = memoryValue(maxMemory.getValue());
        if (chosen > 0 && Math.abs(currentMb - chosen) > chosen / 10) {
            text.append(System.getProperty("jpackage.app-path") != null
                    ? ". Restart to apply " + memoryLabel(chosen) + "."
                    : ". Applies to the installed application only (development run).");
        }
        memoryStatus.setText(text.toString());
    }

    private static String formatMb(long mb) {
        return mb >= 1024 ? String.format(java.util.Locale.ROOT, "%.1f GB", mb / 1024.0) : mb + " MB";
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
        applyThemeMode(darkTheme ? org.fxt.freexmltoolkit.controls.shell.ThemeManager.Mode.DARK
                : org.fxt.freexmltoolkit.controls.shell.ThemeManager.Mode.LIGHT);
    }

    /** Applies a theme mode (light, dark, system) to the scene and persists it (for tests/observers). */
    public void applyThemeMode(org.fxt.freexmltoolkit.controls.shell.ThemeManager.Mode mode) {
        (switch (mode) {
            case DARK -> dark;
            case SYSTEM -> systemTheme;
            default -> light;
        }).setSelected(true);
        org.fxt.freexmltoolkit.controls.shell.ThemeManager.applyMode(getScene(), mode);
    }

    /** @return {@code true} if Dark is currently selected (for tests/observers). */
    public boolean isDarkSelected() {
        return dark.isSelected();
    }
}
