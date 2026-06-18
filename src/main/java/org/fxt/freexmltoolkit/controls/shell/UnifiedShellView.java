package org.fxt.freexmltoolkit.controls.shell;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * Skeleton of the new Unified shell (UI rebuild Phase 2): the persistent frame
 * that every activity will plug into.
 * <pre>
 *   ┌──────────────────────────────────────────────────────┐
 *   │  editor toolbar (full window width: New/Open/Save/…)  │
 *   ├────┬──────────────┬───────────────────┬──────────────┤
 *   │ A  │  side panel  │   editor / main   │  inspector   │
 *   │ c  │  (per        │   (file-type      │  (Node&XPath,│
 *   │ t  │   activity)  │    aware host)    │   Type&Facets│
 *   │ .. │              │                   │   …)         │
 *   ├────┴──────────────┴───────────────────┴──────────────┤
 *   │  status bar                                           │
 *   └──────────────────────────────────────────────────────┘
 * </pre>
 * This phase wires the structure, the Activity Bar selection, and the
 * design-token styling. Side panels, the editor host and the inspector are
 * placeholders; later phases fill them with real, migrated functionality
 * (side-by-side with the existing tabs until each reaches parity — decision D3).
 */
public class UnifiedShellView extends BorderPane {

    private final ActivitySelectionModel selectionModel = new ActivitySelectionModel();
    /** Code-built activity rail (needs the selection model); injected into {@link #activityBarHost}. */
    private ActivityBar activityBar;
    /** Cached so the editor toolbar "Validate" action can drive the same panel. */
    private org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel validationPanel;
    /** The Settings page tab in the main editor area (reused across activity selections). */
    private javafx.scene.control.Tab settingsTab;

    /** Editor-level document actions; created in {@link #initialize()} once {@link #editorHost} is injected. */
    private org.fxt.freexmltoolkit.controls.shell.editor.EditorActions editorActions;
    /** Long-lived Query Console (XPath/XQuery); docked as a collapsible bottom strip (created in initialize). */
    private org.fxt.freexmltoolkit.controls.shell.editor.QueryConsole queryConsole;

    // ----- FXML-injected nodes (defined in /pages/shell.fxml; populated by FXMLLoader) -----
    @FXML private StackPane activityBarHost;
    @FXML private StackPane sidePanelHost;
    @FXML private org.fxt.freexmltoolkit.controls.shell.editor.EditorHost editorHost;
    @FXML private org.fxt.freexmltoolkit.controls.unified.UnifiedSearchBar searchBar;
    /** Vertical split: item 0 = work area (grows), item 1 = {@link #queryConsole} when shown. */
    @FXML private SplitPane workSplit;
    @FXML private StackPane leftPanelWrapper;
    @FXML private StackPane inspectorWrapper;
    @FXML private VBox editorCenter;
    @FXML private StackPane viewSwitchHost;
    @FXML private FlowPane toolbarActions;
    @FXML private javafx.scene.control.ToggleButton leftPanelToggle;
    @FXML private javafx.scene.control.ToggleButton inspectorToggle;
    @FXML private javafx.scene.control.Button actionValidate;
    @FXML private javafx.scene.control.Button actionTransform;
    @FXML private javafx.scene.control.Button actionGenerateDocs;
    @FXML private javafx.scene.control.Button actionTypeEditor;
    /** Header breadcrumb of the active file path. */
    @FXML private Label breadcrumb;
    /** Header theme toggle; its icon literal flips sun/moon with the current theme. */
    @FXML private javafx.scene.control.Button themeToggleButton;
    @FXML private IconifyIcon themeToggleIcon;
    @FXML private Label statusPosition;
    @FXML private Label statusChars;
    @FXML private Label statusType;
    @FXML private Label statusSchema;
    @FXML private Label statusFile;
    @FXML private Label statusMemory;

    /** Property keys for the persisted side-panel visibility (editable in Settings). */
    private static final String LEFT_PANEL_KEY = "shell.leftPanel.visible";
    private static final String INSPECTOR_KEY = "shell.inspector.visible";

    /** User collapse state for the two side panels (persisted across restarts; default open). */
    private boolean leftPanelOpen = loadPanelPref(LEFT_PANEL_KEY);
    private boolean inspectorOpen = loadPanelPref(INSPECTOR_KEY);
    /** Set once the user explicitly picks an activity - shows its panel on the dashboard too. */
    private boolean activityChosen;
    /** Recomputes panel/toggle visibility; combines document-open state with collapse state. */
    private Runnable chromeUpdater;

    /** Editor-toolbar buttons (incl. panel toggles) registered for live display updates. */
    private final java.util.List<javafx.scene.control.ButtonBase> toolbarButtons = new java.util.ArrayList<>();

    /** Node-properties key under which each toolbar button stores its short text label. */
    private static final String TOOL_LABEL_KEY = "fxt.toolLabel";

    /**
     * Loads the shell chrome from {@code /pages/shell.fxml} via the {@code fx:root} pattern (this
     * BorderPane becomes the FXML root and controller). The chrome (header, toolbar, status bar,
     * work-area skeleton) is therefore editable in Scene Builder; the dynamic, editorHost-dependent
     * custom controls are built and wired in {@link #initialize()} after FXML field injection.
     */
    public UnifiedShellView() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/shell.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load /pages/shell.fxml", e);
        }
    }

    /**
     * Wires the shell once the FXML chrome is loaded and {@code @FXML} fields are injected:
     * instantiates the editorHost-dependent custom controls into their placeholders, sets up the
     * panel-visibility chrome logic, listeners, status bar and shortcuts. Invoked automatically by
     * {@link FXMLLoader}.
     */
    @FXML
    public void initialize() {
        // --- Custom controls that need constructor args (not FXML-instantiable) into placeholders ---
        activityBar = new ActivityBar(selectionModel);
        activityBarHost.getChildren().setAll(activityBar);
        editorActions = new org.fxt.freexmltoolkit.controls.shell.editor.EditorActions(editorHost);
        // Route the Welcome/Dashboard "New" card through the guided New File dialog, so all
        // new-file entry points (toolbar, Explorer, Welcome) share one flow.
        editorHost.setNewDocumentHandler(this::newDocument);
        queryConsole = new org.fxt.freexmltoolkit.controls.shell.editor.QueryConsole(editorHost);
        queryConsole.setMinHeight(180);
        queryConsole.setPrefHeight(220);
        viewSwitchHost.getChildren().setAll(editorHost.getViewSwitch());
        // Inspector below the chevron (chevron stays on top of the StackPane).
        inspectorWrapper.getChildren().add(0,
                new org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel(editorHost));
        // PROBLEMS panel below the editor (hides itself while empty).
        editorCenter.getChildren().add(
                new org.fxt.freexmltoolkit.controls.shell.editor.ProblemsPanel(editorHost));
        searchBar.hide(); // shown on Ctrl+F / Ctrl+H

        // --- Panel visibility: dashboard is full-width (both panels hidden until a document opens
        //     or an activity is picked); the user collapse state hides a panel even with a doc open. ---
        chromeUpdater = () -> {
            boolean hasDocument = !editorHost.isEmpty();
            boolean leftArea = hasDocument || activityChosen;
            boolean showLeft = leftArea && leftPanelOpen;
            boolean showRight = hasDocument && inspectorOpen;
            leftPanelWrapper.setVisible(showLeft);
            leftPanelWrapper.setManaged(showLeft);
            inspectorWrapper.setVisible(showRight);
            inspectorWrapper.setManaged(showRight);
            leftPanelToggle.setDisable(!leftArea);
            leftPanelToggle.setSelected(leftPanelOpen);
            inspectorToggle.setDisable(!hasDocument);
            inspectorToggle.setSelected(inspectorOpen);
        };
        chromeUpdater.run();
        editorHost.getOpenDocuments().addListener((javafx.beans.InvalidationListener) obs -> chromeUpdater.run());

        // --- Toolbar: keep the action FlowPane single-row (its preferred height must match the
        //     rendered width, not the default 400px wrap), register buttons for display settings,
        //     and type-gate the document-action buttons. ---
        toolbarActions.prefWrapLengthProperty().bind(toolbarActions.widthProperty());
        for (javafx.scene.Node node : toolbarActions.getChildren()) {
            if (node instanceof javafx.scene.control.Button button) {
                registerToolButton(button, button.getText());
            }
        }
        registerToolButton(leftPanelToggle, "");
        registerToolButton(inspectorToggle, "");
        refreshDocumentActionGating();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshDocumentActionGating());

        // --- Header: initial theme icon + breadcrumb sync ---
        themeToggleIcon.setIconLiteral(ThemeManager.currentIsDark() ? "bi-sun" : "bi-moon");
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> updateBreadcrumb());
        updateBreadcrumb();

        // --- Status bar: memory meter, and the XSD indicator bound to the active document ---
        wireStatusBar();

        // React to activity changes: swap the side panel and reveal the (possibly collapsed) left
        // panel. The initial call does NOT reveal, so a persisted collapsed state survives startup.
        showSidePanelFor(selectionModel.getActive());
        selectionModel.activeProperty().addListener((obs, oldV, newV) -> {
            revealSidePanel();
            showSidePanelFor(newV);
        });
        // A press on the already-active activity fires no model change but should still land there.
        activityBar.setOnUserSelect(this::revealSidePanel);

        // Keep the status bar in sync with the active editor.
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> updateStatusBar());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> updateStatusBar());
        updateStatusBar();

        // File-operation keyboard shortcuts (scoped to the shell).
        addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, this::handleShortcut);
        // Welcome/Dashboard tool cards switch activities (and "open-folder" → Explorer).
        editorHost.setWelcomeActionHandler(this::handleWelcomeAction);

        setOnDragOver(e -> {
            if (e.getDragboard().hasFiles() && acceptsDrop(e.getDragboard().getFiles())) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            e.consume();
        });
        setOnDragDropped(e -> {
            boolean ok = false;
            if (e.getDragboard().hasFiles()) {
                ok = openDroppedFiles(e.getDragboard().getFiles()) > 0;
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    /** @return {@code true} if {@code files} contains at least one XML-family file the shell can open. */
    public static boolean acceptsDrop(java.util.List<java.io.File> files) {
        return files != null && org.fxt.freexmltoolkit.service.DragDropService
                .hasFilesWithExtensions(files, org.fxt.freexmltoolkit.service.DragDropService.ALL_XML_RELATED);
    }

    /** Opens every supported (XML-family) file from {@code files} in the editor host. @return the count opened. */
    public int openDroppedFiles(java.util.List<java.io.File> files) {
        java.util.List<java.io.File> supported = org.fxt.freexmltoolkit.service.DragDropService
                .filterByExtensions(files, org.fxt.freexmltoolkit.service.DragDropService.ALL_XML_RELATED);
        for (java.io.File f : supported) {
            try {
                editorHost.openFile(f.toPath());
            } catch (Exception ex) {
                org.apache.logging.log4j.LogManager.getLogger(UnifiedShellView.class)
                        .warn("Could not open dropped file '{}': {}", f.getName(), ex.getMessage());
            }
        }
        return supported.size();
    }

    /** Routes a Welcome/Dashboard action key to the matching activity. */
    private void handleWelcomeAction(String key) {
        if ("open-folder".equals(key)) {
            selectionModel.select(Activity.EXPLORER);
            revealSidePanel();
            return;
        }
        Activity.fromId(key).ifPresent(activity -> {
            selectionModel.select(activity);
            revealSidePanel();
        });
    }

    private void handleShortcut(javafx.scene.input.KeyEvent event) {
        // F8 validates the active document (no modifier).
        if (event.getCode() == javafx.scene.input.KeyCode.F8) {
            validateActive();
            event.consume();
            return;
        }
        if (!event.isShortcutDown()) {
            return;
        }
        switch (event.getCode()) {
            case N -> {
                if (!event.isShiftDown()) {
                    newDocument();
                    event.consume();
                }
            }
            case O -> {
                openFile();
                event.consume();
            }
            case S -> {
                if (event.isShiftDown()) {
                    saveActiveAs();
                } else {
                    saveActive();
                }
                event.consume();
            }
            case F -> {
                showSearch(false);
                event.consume();
            }
            case H -> {
                showSearch(true);
                event.consume();
            }
            case X -> {
                if (event.isShiftDown()) {
                    toggleQueryConsole();
                    event.consume();
                }
            }
            default -> { /* let other shortcuts (e.g. editor undo/redo) through */ }
        }
    }

    private void updateStatusBar() {
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty()) {
            statusPosition.setText("Ln 1, Col 1");
            statusChars.setText("");
            statusType.setText("");
            statusFile.setText("No file open");
            return;
        }
        int[] pos = editorHost.getActiveCaretLineColumn();
        statusPosition.setText("Ln " + pos[0] + ", Col " + pos[1]);
        var doc = docOpt.get();
        statusType.setText(doc.getFileType().label());
        // Character count (relocated from the per-editor status line, which the shell hides).
        statusChars.setText(editorHost.getActiveText()
                .map(t -> String.format("%,d chars", t.length())).orElse(""));
        statusFile.setText(doc.getPath() != null ? doc.getPath().toString() : doc.getDisplayName());
    }

    /**
     * Toggles the bottom Query Console: shows it (as the split's second item, with the
     * work area taking ~70%) if hidden, hides it (removes it from the split) if shown.
     * On show, focuses the query input for immediate typing.
     */
    public void toggleQueryConsole() {
        if (isQueryConsoleShown()) {
            workSplit.getItems().remove(queryConsole);
        } else {
            workSplit.getItems().add(queryConsole);
            workSplit.setDividerPositions(0.7);
            queryConsole.focusInput();
        }
    }

    /** @return {@code true} while the Query Console is docked (visible) in the bottom split. */
    public boolean isQueryConsoleShown() {
        return workSplit != null && workSplit.getItems().contains(queryConsole);
    }

    /** @return the Query Console instance (long-lived; for tests and toolbar wiring). */
    org.fxt.freexmltoolkit.controls.shell.editor.QueryConsole getQueryConsole() {
        return queryConsole;
    }

    /** Shows/hides the left side panel and persists the choice (default open). */
    public void setLeftPanelVisible(boolean open) {
        leftPanelOpen = open;
        savePanelPref(LEFT_PANEL_KEY, open);
        if (chromeUpdater != null) {
            chromeUpdater.run();
        }
    }

    /**
     * Reveals the left side panel after an explicit activity choice - also from the
     * full-width dashboard, where the panel is hidden while no document is open.
     */
    private void revealSidePanel() {
        activityChosen = true;
        setLeftPanelVisible(true);
    }

    /** Shows/hides the right inspector (Properties) panel and persists the choice (default open). */
    public void setInspectorVisible(boolean open) {
        inspectorOpen = open;
        savePanelPref(INSPECTOR_KEY, open);
        if (chromeUpdater != null) {
            chromeUpdater.run();
        }
    }

    /** @return {@code true} while the left side panel is set to open (regardless of document state). */
    public boolean isLeftPanelOpen() {
        return leftPanelOpen;
    }

    /** @return {@code true} while the inspector (Properties) panel is set to open. */
    public boolean isInspectorOpen() {
        return inspectorOpen;
    }

    /** @return the left side-panel wrapper (for tests/host wiring). */
    Region getLeftPanelWrapper() {
        return leftPanelWrapper;
    }

    /** @return the inspector wrapper (for tests/host wiring). */
    Region getInspectorWrapper() {
        return inspectorWrapper;
    }

    /** Re-reads the persisted panel visibility (e.g. after the Settings panel saves) and applies it live. */
    private void reloadPanelPrefs() {
        leftPanelOpen = loadPanelPref(LEFT_PANEL_KEY);
        inspectorOpen = loadPanelPref(INSPECTOR_KEY);
        if (chromeUpdater != null) {
            chromeUpdater.run();
        }
    }

    /** @return the persisted panel visibility for {@code key}, defaulting to open ({@code true}). */
    private static boolean loadPanelPref(String key) {
        try {
            String value = org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).get(key);
            return value == null || Boolean.parseBoolean(value);
        } catch (Throwable t) {
            return true; // properties service unavailable (e.g. tests) — default to open
        }
    }

    /** Persists the panel visibility for {@code key}; silently ignores an unavailable service. */
    private static void savePanelPref(String key, boolean value) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .set(key, String.valueOf(value));
        } catch (Throwable ignored) {
            // properties service unavailable — nothing to persist
        }
    }

    private void showSidePanelFor(Activity activity) {
        if (activity == Activity.EXPLORER) {
            var explorer = new org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel(editorHost);
            explorer.setNewFileAction(this::newDocument);
            sidePanelHost.getChildren().setAll(explorer);
            return;
        }
        if (activity == Activity.SCHEMA) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.TypeLibraryPanel(editorHost));
            return;
        }
        if (activity == Activity.VALIDATION) {
            sidePanelHost.getChildren().setAll(validationPanel());
            return;
        }
        if (activity == Activity.TRANSFORM) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel(editorHost));
            return;
        }
        if (activity == Activity.FAVORITES) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.FavoritesActivityPanel(editorHost));
            return;
        }
        if (activity == Activity.PDF_FOP) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.FopPanel(editorHost));
            return;
        }
        if (activity == Activity.SIGNATURE) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.SignaturePanel(editorHost));
            return;
        }
        if (activity == Activity.FUNDSXML) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlPanel(editorHost));
            return;
        }
        if (activity == Activity.HELP) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.HelpPanel());
            return;
        }
        if (activity == Activity.SETTINGS) {
            // Settings live in the main editor area (a full Settings page), not in the
            // narrow side panel; the panel only carries a pointer to it.
            openSettingsTab();
            VBox panel = new VBox();
            panel.getStyleClass().add("fxt-side-panel-content");
            Label settingsTitle = new Label("SETTINGS");
            settingsTitle.getStyleClass().add("fxt-side-panel-title");
            Label settingsHint = new Label("Settings are edited in the main window.");
            settingsHint.getStyleClass().add("fxt-placeholder-text");
            settingsHint.setWrapText(true);
            panel.getChildren().addAll(settingsTitle, settingsHint);
            sidePanelHost.getChildren().setAll(panel);
            return;
        }
        VBox panel = new VBox();
        panel.getStyleClass().add("fxt-side-panel-content");

        Label title = new Label(activity.label().toUpperCase());
        title.getStyleClass().add("fxt-side-panel-title");

        Label hint = new Label("'" + activity.label() + "' panel — coming in a later phase.");
        hint.getStyleClass().add("fxt-placeholder-text");
        hint.setWrapText(true);

        panel.getChildren().addAll(title, hint);
        sidePanelHost.getChildren().setAll(panel);
    }

    /**
     * Opens (or re-selects) the Settings page as a tab in the main editor area —
     * the Settings activity's content lives there, not in the narrow side panel.
     */
    private void openSettingsTab() {
        if (settingsTab != null && editorHost.containsTab(settingsTab)) {
            editorHost.selectTab(settingsTab);
            return;
        }
        var settings = new org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel();
        settings.setOnSaved(() -> {
            activityBar.refresh();
            reloadPanelPrefs();
            applyToolbarDisplaySettings();
        });
        settingsTab = editorHost.openToolTab("Settings", "bi-gear", settings);
    }

    /** @return the cached Validation panel (created on first use). */
    private org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel validationPanel() {
        if (validationPanel == null) {
            validationPanel = new org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel(editorHost);
        }
        return validationPanel;
    }

    // ===== @FXML action handlers (onAction targets from shell.fxml). Public for jpackage. =====
    @FXML public void onNew() { newDocument(); }
    @FXML public void onOpen() { openFile(); }
    @FXML public void onSave() { saveActive(); }
    @FXML public void onSaveAs() { saveActiveAs(); }
    @FXML public void onSaveAll() { editorHost.saveAll(); }
    @FXML public void onUndo() { editorHost.undoActive(); }
    @FXML public void onRedo() { editorHost.redoActive(); }
    @FXML public void onFormat() { editorHost.formatActive(); }
    @FXML public void onMinify() { editorHost.minifyActive(); }
    @FXML public void onInsertTemplate() { insertTemplate(); }
    @FXML public void onCompare() { compareWithFile(); }
    @FXML public void onSpreadsheet() { convertSpreadsheet(); }
    @FXML public void onQueryConsole() { toggleQueryConsole(); }
    @FXML public void onTransform() { editorActions.transformActiveWithXslt(window()); }
    @FXML public void onSetSchema() { setSchema(); }
    @FXML public void onGenerateDocs() { editorActions.generateDocsActive(window()); }
    @FXML public void onTypeEditor() { editorActions.openTypeEditorActive(); }
    @FXML public void onValidate() { validateActive(); }
    @FXML public void onNotifications() { /* no-op placeholder (Figma "future" header) */ }
    @FXML public void onHelp() { selectionModel.select(Activity.HELP); }
    @FXML public void onToggleTheme() { toggleTheme(); }
    @FXML public void onSearchPillClicked() { if (!isQueryConsoleShown()) { toggleQueryConsole(); } }
    @FXML public void onToggleLeftPanel() { setLeftPanelVisible(leftPanelToggle.isSelected()); }
    @FXML public void onToggleInspector() { setInspectorVisible(inspectorToggle.isSelected()); }
    @FXML public void onCollapseLeftPanel() { setLeftPanelVisible(false); }
    @FXML public void onCollapseInspector() { setInspectorVisible(false); }

    /**
     * Validates the active document from the editor toolbar: well-formedness only when
     * no XSD is bound, and against the bound XSD when one is. Switches to the Validation
     * activity so the result and any problems are shown.
     */
    private void validateActive() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        selectionModel.select(Activity.VALIDATION);
        validationPanel().revalidate();
    }

    /** Shows the find (or find+replace) bar bound to the active editor. */
    private void showSearch(boolean replace) {
        var codeArea = editorHost.getActiveCodeArea();
        if (codeArea == null) {
            return;
        }
        searchBar.setCurrentCodeArea(codeArea);
        if (replace) {
            searchBar.showReplace();
        } else {
            searchBar.show();
        }
    }

    /** Toggles light/dark theme and flips the header toggle's sun/moon icon literal. */
    private void toggleTheme() {
        boolean dark = !ThemeManager.currentIsDark();
        if (getScene() != null) {
            ThemeManager.apply(getScene(), dark);
        }
        themeToggleIcon.setIconLiteral(dark ? "bi-sun" : "bi-moon");
    }

    /** Sets the breadcrumb to the last path segments of the active document (or the app name). */
    private void updateBreadcrumb() {
        if (breadcrumb == null) {
            return;
        }
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty()) {
            breadcrumb.setText("FreeXmlToolkit");
            return;
        }
        var doc = docOpt.get();
        java.nio.file.Path path = doc.getPath();
        if (path == null) {
            breadcrumb.setText(doc.getDisplayName());
            return;
        }
        int count = path.getNameCount();
        int from = Math.max(0, count - 3);
        java.util.List<String> segments = new java.util.ArrayList<>();
        for (int i = from; i < count; i++) {
            segments.add(path.getName(i).toString());
        }
        breadcrumb.setText(String.join("  ›  ", segments));
    }

    /** Registers a toolbar button (any {@link javafx.scene.control.ButtonBase}) for display updates. */
    private void registerToolButton(javafx.scene.control.ButtonBase button, String label) {
        button.getProperties().put(TOOL_LABEL_KEY, label == null ? "" : label);
        toolbarButtons.add(button);
        applyDisplayTo(button);
    }

    /** Applies the current label-visibility + icon-size settings to one toolbar button. */
    private void applyDisplayTo(javafx.scene.control.ButtonBase button) {
        boolean showLabels = false;
        boolean large = false;
        try {
            var props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(
                    org.fxt.freexmltoolkit.service.PropertiesService.class);
            showLabels = props.isToolbarShowLabels();
            large = "large".equalsIgnoreCase(props.getToolbarIconSize());
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests) — fall back to defaults
        }
        Object stored = button.getProperties().get(TOOL_LABEL_KEY);
        String label = stored == null ? "" : stored.toString();
        button.setText(showLabels && !label.isEmpty() ? label : null);
        if (button.getGraphic() instanceof IconifyIcon icon) {
            icon.setIconSize(ToolbarDisplay.iconSizePx(large));
        }
        button.getStyleClass().removeAll(ToolbarDisplay.SMALL_CLASS, ToolbarDisplay.LARGE_CLASS);
        button.getStyleClass().add(ToolbarDisplay.sizeStyleClass(large));
    }

    /** Re-applies the current toolbar display settings to every registered button (live refresh). */
    private void applyToolbarDisplaySettings() {
        for (javafx.scene.control.ButtonBase button : toolbarButtons) {
            applyDisplayTo(button);
        }
    }

    /**
     * Enables/disables the document-action buttons based on the active document's
     * {@link org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType}. With no
     * active document all four are disabled.
     */
    private void refreshDocumentActionGating() {
        var type = editorActions.activeFileType();
        actionValidate.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.VALIDATE));
        actionTransform.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.TRANSFORM));
        actionGenerateDocs.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.GENERATE_DOCS));
        actionTypeEditor.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.TYPE_EDITOR));
    }

    /** @return the shell's owning window, or {@code null} when not yet shown. */
    private javafx.stage.Window window() {
        return getScene() != null ? getScene().getWindow() : null;
    }

    private void saveActive() {
        editorHost.getActiveDocument().ifPresent(doc -> {
            if (doc.isUntitled()) {
                saveActiveAs();
            } else {
                editorHost.saveActive();
            }
        });
    }

    private void saveActiveAs() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save As");
        java.io.File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.saveActiveAs(file.toPath());
        }
    }

    private void newDocument() {
        var dialog = new org.fxt.freexmltoolkit.controls.shell.editor.NewFileDialog();
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(this::createNewDocument);
    }

    /**
     * Creates a new document according to the choices gathered in {@link
     * org.fxt.freexmltoolkit.controls.shell.editor.NewFileDialog}: a template,
     * a schema-derived skeleton (mandatory nodes), or plain per-type boilerplate.
     * Schema-based generation runs off the UI thread.
     */
    private void createNewDocument(org.fxt.freexmltoolkit.controls.shell.editor.NewFileDialog.Result result) {
        // 1) Template chosen → render it (prompting for parameters when needed).
        if (result.template() != null) {
            var template = result.template();
            java.util.Map<String, String> params = java.util.Map.of();
            if (template.getParameters() != null && !template.getParameters().isEmpty()) {
                var paramDialog = new org.fxt.freexmltoolkit.controls.shell.editor.TemplateParameterDialog(template);
                if (getScene() != null) {
                    paramDialog.initOwner(getScene().getWindow());
                }
                var entered = paramDialog.showAndWait();
                if (entered.isEmpty()) {
                    return; // parameter entry cancelled
                }
                params = entered.get();
            }
            String content = org.fxt.freexmltoolkit.controls.shell.editor.TemplateRunner.render(template, params);
            if (content.startsWith("ERROR:")) {
                org.fxt.freexmltoolkit.util.DialogHelper.showError("New File", "Template could not be rendered", content);
                return;
            }
            org.fxt.freexmltoolkit.service.TemplateRepository.getInstance().recordUsage(template.getId());
            finishNewDocument(content, result);
            return;
        }

        // 2) XML + schema + mandatory nodes → generate skeleton off the UI thread.
        if (result.schema() != null && result.generateMandatory()) {
            java.io.File schema = result.schema();
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                String xml = org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner
                        .generate(schema, true, 2, false);
                javafx.application.Platform.runLater(() -> {
                    if (xml.startsWith("ERROR:")) {
                        org.fxt.freexmltoolkit.util.DialogHelper.showError("New File",
                                "Could not generate document from schema", xml);
                    } else {
                        finishNewDocument(xml, result);
                    }
                });
            });
            return;
        }

        // 3) Plain per-type boilerplate (a schema may still be bound below).
        finishNewDocument(result.type().defaultContent(), result);
    }

    /**
     * Opens the generated content as a new document, binds the chosen schema (XML
     * without a template) and saves it immediately when a location was chosen.
     * Must run on the FX thread.
     */
    private void finishNewDocument(String content, org.fxt.freexmltoolkit.controls.shell.editor.NewFileDialog.Result result) {
        org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType type = result.type();
        String name = "Untitled." + type.primaryExtension();
        if (content == null || content.isBlank()) {
            editorHost.newDocument(type);
        } else {
            editorHost.openGeneratedDocument(content, type, name);
        }
        if (result.schema() != null && result.template() == null) {
            editorHost.setSchemaForActiveDocument(result.schema());
        }
        if (result.saveLocation() != null) {
            editorHost.saveActiveAs(result.saveLocation().toPath());
        }
    }

    private void openFile() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Open File");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("XML / XSD / XSLT / Schematron / JSON",
                        "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron", "*.json"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.openFile(file.toPath());
        }
    }

    /**
     * Opens the given file as a document in the shell's editor host. Public bridge
     * used by {@code MainController} to hand a file (e.g. a {@code .sch} opened via
     * legacy file routing) to the shell after a legacy editor tab has been retired.
     *
     * @param path the file to open (ignored if {@code null})
     */
    public void openFile(java.nio.file.Path path) {
        if (path != null) {
            editorHost.openFile(path);
        }
    }

    /** {@link File} convenience overload of {@link #openFile(java.nio.file.Path)}. */
    public void openFile(java.io.File file) {
        if (file != null) {
            editorHost.openFile(file.toPath());
        }
    }

    /**
     * Opens an XSD file and reveals the named element/type in the Graphic view (bridge for
     * the XML editor's "go to schema definition", replacing the retired legacy XSD editor).
     */
    public void openXsdAndReveal(java.nio.file.Path path, String elementName) {
        if (path != null) {
            editorHost.openXsdAndReveal(path, elementName);
        }
    }

    private void convertSpreadsheet() {
        var dialog = new org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetConverterDialog();
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(this::performConversion);
    }

    private void performConversion(
            org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetConverterDialog.Settings settings) {
        var window = getScene() != null ? getScene().getWindow() : null;
        boolean excel = settings.format()
                == org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetConverterDialog.Format.EXCEL;
        if (settings.direction()
                == org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetConverterDialog.Direction.XML_TO_SPREADSHEET) {
            if (editorHost.getActiveDocument().isEmpty()) {
                return;
            }
            String xml = editorHost.getActiveText().orElse("");
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Save spreadsheet");
            chooser.getExtensionFilters().add(excel
                    ? new javafx.stage.FileChooser.ExtensionFilter("Excel", "*.xlsx")
                    : new javafx.stage.FileChooser.ExtensionFilter("CSV", "*.csv"));
            java.io.File out = chooser.showSaveDialog(window);
            if (out == null) {
                return;
            }
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                String result = excel
                        ? org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .exportToExcel(xml, out, settings.config())
                        : org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .exportToCsv(xml, out, settings.delimiter().config(), settings.config());
                javafx.application.Platform.runLater(() -> reportConversion(result, out, false));
            });
        } else {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Import spreadsheet");
            chooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("Excel / CSV", "*.xlsx", "*.csv"));
            java.io.File in = chooser.showOpenDialog(window);
            if (in == null) {
                return;
            }
            // Infer the actual format from the chosen file's extension.
            boolean isExcel = in.getName().toLowerCase().endsWith(".xlsx");
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                String xml = isExcel
                        ? org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .excelToXml(in, settings.config())
                        : org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .csvToXml(in, settings.delimiter().config(), settings.config());
                javafx.application.Platform.runLater(() -> {
                    if (xml.startsWith("ERROR:")) {
                        reportConversion(xml, in, true);
                    } else {
                        editorHost.openGeneratedDocument(xml,
                                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XML, "Imported.xml");
                    }
                });
            });
        }
    }

    private void reportConversion(String result, java.io.File file, boolean imported) {
        boolean ok = result.startsWith("OK:") || (imported && !result.startsWith("ERROR:"));
        if (ok) {
            org.fxt.freexmltoolkit.util.DialogHelper.showInformation("Spreadsheet Converter",
                    "Conversion finished", "Done: " + file.getAbsolutePath());
        } else {
            org.fxt.freexmltoolkit.util.DialogHelper.showError("Spreadsheet Converter",
                    "Conversion failed", result);
        }
    }

    private void insertTemplate() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        var dialog = new org.fxt.freexmltoolkit.controls.shell.editor.TemplateInsertDialog();
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(template -> {
            // Prompt for parameter values when the template is parameterized.
            java.util.Map<String, String> params = java.util.Map.of();
            if (template.getParameters() != null && !template.getParameters().isEmpty()) {
                var paramDialog = new org.fxt.freexmltoolkit.controls.shell.editor.TemplateParameterDialog(template);
                if (getScene() != null) {
                    paramDialog.initOwner(getScene().getWindow());
                }
                var entered = paramDialog.showAndWait();
                if (entered.isEmpty()) {
                    return; // parameter entry cancelled
                }
                params = entered.get();
            }
            String content = org.fxt.freexmltoolkit.controls.shell.editor.TemplateRunner
                    .render(template, params);
            if (!content.startsWith("ERROR:")) {
                editorHost.insertTextAtCaret(content);
                org.fxt.freexmltoolkit.service.TemplateRepository.getInstance().recordUsage(template.getId());
            }
        });
    }

    private void compareWithFile() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Compare with File…");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("XML / XSD / XSLT / Schematron / JSON",
                        "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron", "*.json"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.openDiffWithFile(file);
        }
    }

    private void setSchema() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select XSD Schema");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("XSD Schema", "*.xsd"));
        java.io.File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.setSchemaForActiveDocument(file);
        }
    }

    /** @return the editor host (for future toolbar / inspector wiring). */
    public org.fxt.freexmltoolkit.controls.shell.editor.EditorHost getEditorHost() {
        return editorHost;
    }

    /**
     * Wires the FXML status bar: the memory meter (tooltip / GC-on-click / 2s refresh) and the
     * XSD indicator bound to the active document's schema (blanked + collapsed for non-XML types;
     * clickable to bind an XSD). The labels themselves live in {@code shell.fxml}.
     */
    private void wireStatusBar() {
        statusMemory.setTooltip(new javafx.scene.control.Tooltip("JVM heap (used / max) — click to run GC"));
        statusMemory.setOnMouseClicked(e -> {
            System.gc();
            statusMemory.setText(memoryText());
        });
        statusMemory.setText(memoryText());
        javafx.animation.Timeline memoryTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2),
                        e -> statusMemory.setText(memoryText())));
        memoryTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        memoryTimer.play();

        // The bound XSD tracks the active document's schema — but only for schema-aware (XML-family)
        // documents. For JSON / plain text an "XSD" label is meaningless, so it is blanked and the
        // label collapses out of the bar entirely.
        statusSchema.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> {
                    var doc = editorHost.getActiveDocument().orElse(null);
                    if (doc == null || !isSchemaAware(doc.getFileType())) {
                        return "";
                    }
                    java.io.File xsd = editorHost.activeSchemaProperty().get();
                    return xsd != null ? "XSD: " + xsd.getName() : "No XSD";
                },
                editorHost.activeSchemaProperty(), editorHost.activeTabProperty()));
        statusSchema.managedProperty().bind(statusSchema.textProperty().isNotEmpty());
        statusSchema.visibleProperty().bind(statusSchema.textProperty().isNotEmpty());
        // The XSD indicator doubles as the binding entry point (VS-Code style): clicking it picks an
        // XSD and binds it to the active document via setSchemaForActiveDocument.
        statusSchema.setTooltip(new javafx.scene.control.Tooltip(
                "Click to bind an XSD schema to this document (IntelliSense & validation)"));
        statusSchema.setCursor(javafx.scene.Cursor.HAND);
        statusSchema.setOnMouseClicked(e -> setSchema());
    }

    /**
     * @return {@code true} when the file type can carry an XSD schema (XML family); {@code false}
     *         for JSON / plain text, where the status bar's "XSD" indicator is meaningless.
     */
    private static boolean isSchemaAware(org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType type) {
        return switch (type) {
            case XML, XSD, XSLT, SCHEMATRON -> true;
            default -> false;
        };
    }

    /** @return the JVM heap usage formatted as {@code "used / max MB"}. */
    static String memoryText() {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        return usedMb + " / " + maxMb + " MB";
    }

    /** @return the shell's activity selection model (for future host wiring). */
    public ActivitySelectionModel getSelectionModel() {
        return selectionModel;
    }
}
