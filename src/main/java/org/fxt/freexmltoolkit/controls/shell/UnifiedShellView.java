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
 * The Unified shell: the persistent frame that every activity plugs into.
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
 * The chrome (header, editor toolbar, status bar, work-area skeleton) is loaded
 * from {@code /pages/shell.fxml} via the {@code fx:root} pattern; the dynamic,
 * {@code editorHost}-dependent custom controls (Activity Bar, side panels,
 * Inspector, Query Console, view switch) are built and wired in {@link #initialize()}.
 */
public class UnifiedShellView extends BorderPane {

    private final ActivitySelectionModel selectionModel = new ActivitySelectionModel();
    /** Code-built activity rail (needs the selection model); injected into {@link #activityBarHost}. */
    private ActivityBar activityBar;
    /** Cached so the editor toolbar "Validate" action can drive the same panel. */
    private org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel validationPanel;
    /** Cached so Ctrl+Shift+F / Ctrl+Shift+H can focus the same panel's query field. */
    private org.fxt.freexmltoolkit.controls.shell.editor.search.SearchPanel searchPanel;
    /** Cached so the Search panel can default its scope to the Explorer's workspace folder. */
    private org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel explorerPanel;
    /**
     * One cached side panel per activity, so entered state (PDF metadata, signature
     * form, transform parameters, search text, …) survives switching activities and
     * back. Panels observe {@code editorHost} reactively, so a cached instance stays
     * in sync with the active document.
     */
    private final java.util.Map<Activity, javafx.scene.Node> sidePanels =
            new java.util.EnumMap<>(Activity.class);
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
    @FXML private javafx.scene.control.SplitMenuButton actionRun;
    @FXML private javafx.scene.control.MenuItem actionGenerateDocs;
    @FXML private javafx.scene.control.MenuItem actionTypeEditor;
    @FXML private javafx.scene.control.MenuItem actionRunQuery;
    @FXML private javafx.scene.control.MenuItem actionRunTransform;
    @FXML private javafx.scene.control.MenuItem actionRunPipeline;
    /** Target dropdown for query/XSLT documents; visible only when one is active. */
    @FXML private javafx.scene.control.MenuButton queryTargetButton;
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
    @FXML private Label statusLastRun;
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
        // Ctrl+Enter in a query/XSLT editor runs the document like the toolbar's
        // Run Query / Run Transform buttons (dispatched by file type).
        editorHost.setQueryRunHandler(editorActions::runActive);
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
        // Keep the open search bar bound to the right view when the user switches
        // view mode or tab (otherwise it would keep searching the previous view).
        editorHost.activeViewModeProperty().addListener((obs, oldMode, newMode) -> {
            if (searchBar.isVisible()) {
                bindSearchBar();
            }
        });
        editorHost.activeTabProperty().addListener((obs, oldTab, newTab) -> {
            if (searchBar.isVisible()) {
                bindSearchBar();
            }
        });

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
        //     rendered width, not the default 400px wrap; it only wraps as narrow-window
        //     fallback), register buttons for display settings, and type-gate the
        //     document-action buttons. Buttons with an empty FXML text stay icon-only
        //     permanently (variant E: secondary tools carry their name in the tooltip). ---
        toolbarActions.prefWrapLengthProperty().bind(toolbarActions.widthProperty());
        for (javafx.scene.Node node : toolbarActions.getChildren()) {
            // The Target dropdown's label is dynamic ("Target: <doc>") — registering it
            // would clobber that label on every settings refresh.
            if (node instanceof javafx.scene.control.ButtonBase button && node != queryTargetButton) {
                registerToolButton(button, button.getText());
            }
        }
        registerToolButton(leftPanelToggle, "");
        registerToolButton(inspectorToggle, "");
        refreshDocumentActionGating();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshDocumentActionGating());

        // Target dropdown for query/XSLT documents: items are rebuilt from the open
        // documents each time the menu opens; the label follows the active document's
        // stored target (and open-documents changes, e.g. the chosen target closing).
        queryTargetButton.setOnShowing(e -> rebuildQueryTargetMenu());
        editorHost.getOpenDocuments().addListener(
                (javafx.beans.InvalidationListener) obs -> refreshQueryTargetButton());

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

        // After a FundsXML background download completes, surface the new content:
        // the welcome page's quick-access section and the version list appear live.
        org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlDownloadCoordinator.getInstance()
                .addListener(new org.fxt.freexmltoolkit.controls.shell.editor
                        .FundsXmlDownloadCoordinator.Listener() {
                    @Override
                    public void onFinished(
                            org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService.DownloadResult result) {
                        editorHost.refreshWelcome();
                    }
                });

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
        if (key != null && key.startsWith("fundsxml-")) {
            handleFundsXmlWelcomeAction(key);
            return;
        }
        Activity.fromId(key).ifPresent(activity -> {
            selectionModel.select(activity);
            revealSidePanel();
        });
    }

    /** Handles the Welcome page's FundsXML quick-access cards. */
    private void handleFundsXmlWelcomeAction(String key) {
        switch (key) {
            case "fundsxml-open-example" -> org.fxt.freexmltoolkit.service.fundsxml.FundsXmlQuickStart
                    .findStarterSample().ifPresent(editorHost::openFile);
            case "fundsxml-open-schema" -> {
                java.nio.file.Path schema =
                        org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlRunner.activeSchemaFile();
                if (schema != null) {
                    editorHost.openFile(schema);
                }
            }
            case "fundsxml-browse-examples" -> {
                java.nio.file.Path dir =
                        org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlRunner.examplesDir();
                try {
                    if (dir != null && java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(dir.toFile());
                    }
                } catch (Exception e) {
                    org.apache.logging.log4j.LogManager.getLogger(UnifiedShellView.class)
                            .warn("Could not open FundsXML examples folder: {}", e.getMessage());
                }
            }
            default -> { }
        }
    }

    private void handleShortcut(javafx.scene.input.KeyEvent event) {
        // F8 validates the active document (no modifier).
        if (event.getCode() == javafx.scene.input.KeyCode.F8) {
            validateActive();
            event.consume();
            return;
        }
        // Shift+Alt+F formats the active document (VS Code convention;
        // Ctrl+Shift+F is Find in Files since the Search activity exists).
        if (event.getCode() == javafx.scene.input.KeyCode.F
                && event.isShiftDown() && event.isAltDown() && !event.isShortcutDown()) {
            if (editorHost.getActiveDocument().isPresent()) {
                onFormat();
                event.consume();
            }
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
                if (event.isShiftDown()) {
                    // Ctrl+Shift+F — Find in Files (VS Code convention).
                    openSearchPanel(false);
                } else {
                    showSearch(false);
                }
                event.consume();
            }
            case H -> {
                if (event.isShiftDown()) {
                    // Ctrl+Shift+H — Replace in Files.
                    openSearchPanel(true);
                } else {
                    showSearch(true);
                }
                event.consume();
            }
            case X -> {
                if (event.isShiftDown()) {
                    toggleQueryConsole();
                    event.consume();
                }
            }
            case Z -> {
                // Shell-level Undo/Redo so they work regardless of which view (Text/Tree/
                // Graphic) has focus. This only fires when the focused editor did not already
                // consume the event, so there is no double-undo when typing in the text area.
                if (editorHost.getActiveDocument().isPresent()) {
                    if (event.isShiftDown()) {
                        onRedo();
                    } else {
                        onUndo();
                    }
                    event.consume();
                }
            }
            case Y -> {
                if (editorHost.getActiveDocument().isPresent()) {
                    onRedo();
                    event.consume();
                }
            }
            default -> { /* let other editor shortcuts through */ }
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
        if (activity == Activity.SETTINGS) {
            // Settings content lives in a main-area tab; (re)open/focus it on every
            // selection. The side panel only carries a pointer to it.
            openSettingsTab();
        }
        sidePanelHost.getChildren().setAll(sidePanel(activity));
    }

    /** Returns the (cached) side panel for {@code activity}, creating it on first use. */
    private javafx.scene.Node sidePanel(Activity activity) {
        return sidePanels.computeIfAbsent(activity, this::createSidePanel);
    }

    /** Builds the side panel for {@code activity} (called once per activity; then cached). */
    private javafx.scene.Node createSidePanel(Activity activity) {
        return switch (activity) {
            case EXPLORER -> {
                var explorer = new org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel(editorHost);
                explorer.setNewFileAction(this::newDocument);
                explorer.setSchematronValidateAction(this::validateWithSchematron);
                explorerPanel = explorer;
                yield explorer;
            }
            case SEARCH -> searchPanel();
            case SCHEMA -> new org.fxt.freexmltoolkit.controls.shell.editor.TypeLibraryPanel(editorHost);
            case VALIDATION -> validationPanel();
            case TRANSFORM -> new org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel(editorHost);
            case FAVORITES -> new org.fxt.freexmltoolkit.controls.shell.editor.FavoritesActivityPanel(editorHost);
            case PDF_FOP -> new org.fxt.freexmltoolkit.controls.shell.editor.FopPanel(editorHost);
            case SIGNATURE -> new org.fxt.freexmltoolkit.controls.shell.editor.SignaturePanel(editorHost);
            case FUNDSXML -> new org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlPanel(editorHost);
            case HELP -> new org.fxt.freexmltoolkit.controls.shell.editor.HelpPanel();
            case SETTINGS -> hintPanel("SETTINGS", "Settings are edited in the main window.");
            default -> hintPanel(activity.label().toUpperCase(),
                    "'" + activity.label() + "' panel — coming in a later phase.");
        };
    }

    /** A simple titled placeholder side panel (used for Settings' pointer and not-yet-built activities). */
    private static javafx.scene.Node hintPanel(String title, String hint) {
        VBox panel = new VBox();
        panel.getStyleClass().add("fxt-side-panel-content");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("fxt-side-panel-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("fxt-placeholder-text");
        hintLabel.setWrapText(true);
        panel.getChildren().addAll(titleLabel, hintLabel);
        return panel;
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
            activityBar.refreshLabels();
            reloadPanelPrefs();
            applyToolbarDisplaySettings();
            // Turning the developer feature off hides the "last run" status-bar item.
            if (!org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().isEnabled()) {
                statusLastRun.setText("");
            }
        });
        settings.setOnFundsXmlEnabled(() -> {
            var coordinator = org.fxt.freexmltoolkit.controls.shell.editor
                    .FundsXmlDownloadCoordinator.getInstance();
            var service = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService.getInstance();
            if (service.isCacheEmpty()) {
                coordinator.startBackgroundDownload("settings-enable");
            } else {
                coordinator.runRegisterOnly();
            }
        });
        settingsTab = editorHost.openToolTab("Settings", "bi-gear", settings);
    }

    /** @return the cached Search panel (created on first use). */
    private org.fxt.freexmltoolkit.controls.shell.editor.search.SearchPanel searchPanel() {
        if (searchPanel == null) {
            searchPanel = new org.fxt.freexmltoolkit.controls.shell.editor.search.SearchPanel(
                    editorHost, this::currentWorkspaceFolder);
        }
        return searchPanel;
    }

    /**
     * The folder the Search panel should default to: the Explorer's workspace folder
     * when one is open, else the last directory used in a file chooser, else {@code null}.
     */
    private java.nio.file.Path currentWorkspaceFolder() {
        if (explorerPanel != null && explorerPanel.getWorkspaceFolder() != null) {
            return explorerPanel.getWorkspaceFolder();
        }
        try {
            String dir = org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .getLastOpenDirectory();
            if (dir != null && !dir.isBlank()) {
                java.nio.file.Path path = java.nio.file.Path.of(dir);
                if (java.nio.file.Files.isDirectory(path)) {
                    return path;
                }
            }
        } catch (Throwable ignored) {
            // properties service unavailable (e.g. tests)
        }
        return null;
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
    @FXML public void onRunQuery() { editorActions.runActiveQuery(); }
    @FXML public void onRunTransform() { editorActions.runActiveTransform(); }
    @FXML public void onRunPipeline() { editorActions.runActivePipeline(); }

    /**
     * Primary click on the Run split button: dispatches to whichever run action the
     * active document type supports (XQuery → query, XSLT → transform, XProc → pipeline).
     * The button is disabled for non-runnable types, so falling through is a no-op.
     */
    @FXML public void onRunPrimary() {
        var type = editorActions.activeFileType();
        if (org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.applicableFor(
                type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_QUERY)) {
            onRunQuery();
        } else if (org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.applicableFor(
                type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_TRANSFORM)) {
            onRunTransform();
        } else if (org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.applicableFor(
                type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_PIPELINE)) {
            onRunPipeline();
        }
    }
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

    /**
     * Runs the Explorer's one-click Schematron validation in the Validation activity:
     * the active document goes through the panel's single-file flow (problems list,
     * report button), everything else through the batch flow with the given Schematron.
     * The Explorer already bound the Schematron to the active document before calling.
     */
    private void validateWithSchematron(java.util.List<java.io.File> files, java.io.File schematron) {
        selectionModel.select(Activity.VALIDATION);
        var panel = validationPanel();
        boolean activeDocumentOnly = files.size() == 1 && editorHost.getActiveDocument()
                .map(org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument::getPath)
                .map(p -> p.toFile().getAbsoluteFile().equals(files.get(0).getAbsoluteFile()))
                .orElse(false);
        if (activeDocumentOnly) {
            panel.revalidate();
        } else {
            panel.runBatch(files, schematron);
        }
    }

    /**
     * Binds the search bar to whatever the active view offers: the structured view
     * (XSD Tree/Graphic, XML grid) when one is showing, else the text code area.
     *
     * @return true if the bar was bound to a searchable view
     */
    private boolean bindSearchBar() {
        var target = editorHost.getActiveSearchTarget();
        if (target != null) {
            searchBar.setCurrentSearchTarget(target);
            return true;
        }
        var codeArea = editorHost.getActiveCodeArea();
        if (codeArea == null) {
            searchBar.hide();
            return false;
        }
        searchBar.setCurrentCodeArea(codeArea);
        return true;
    }

    /**
     * Shows the find (or find+replace) bar bound to the active view. In structured
     * views (Tree/Graphic) replace is unavailable, so Ctrl+H degrades to find-only.
     */
    private void showSearch(boolean replace) {
        if (!bindSearchBar()) {
            return;
        }
        if (replace && editorHost.getActiveSearchTarget() == null) {
            searchBar.showReplace();
        } else {
            searchBar.show();
        }
    }

    /**
     * Opens the Search activity's side panel in Text mode and focuses its query
     * field, prefilling it with the editor's single-line selection if there is one.
     *
     * @param replace whether to expand the replace row (Ctrl+Shift+H)
     */
    private void openSearchPanel(boolean replace) {
        String prefill = null;
        var codeArea = editorHost.getActiveCodeArea();
        if (codeArea != null) {
            String selected = codeArea.getSelectedText();
            if (selected != null && !selected.isBlank() && !selected.contains("\n")) {
                prefill = selected;
            }
        }
        selectionModel.select(Activity.SEARCH);
        revealSidePanel();
        searchPanel().focusTextSearch(prefill, replace);
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
        // Labels below the icons are the default (Figma "future" layout) — the fallback
        // must match the persisted default, or a settings-service hiccup silently
        // degrades the toolbar to hard-to-read icon-only buttons.
        boolean showLabels = true;
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
        actionRunQuery.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_QUERY));
        actionRunTransform.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_TRANSFORM));
        actionRunPipeline.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_PIPELINE));

        // The Target dropdown is hidden (not just disabled) for other document types:
        // unlike the action buttons, a "Target: …" selector label is meaningless there.
        boolean targetable = org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_QUERY)
                || org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_TRANSFORM)
                || org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_PIPELINE);
        queryTargetButton.setVisible(targetable);
        queryTargetButton.setManaged(targetable);
        refreshQueryTargetButton();

        // The Run split button is only useful for runnable documents (XQuery/XSLT/XProc);
        // its primary click dispatches to whichever run action the active type supports.
        actionRun.setDisable(!targetable);

        // Everything that operates on the active document is disabled when none is open,
        // so the toolbar never offers a no-op click on the empty welcome screen. New/Open
        // and the direction-agnostic Spreadsheet converter stay enabled.
        boolean hasDoc = type != null;
        for (String id : DOC_DEPENDENT_TOOLBAR_IDS) {
            setToolbarButtonDisabled(id, !hasDoc);
        }
    }

    /**
     * Toolbar button ids (from {@code shell.fxml}) whose actions require an open
     * document. Gated on/off together in {@link #refreshDocumentActionGating()}.
     */
    private static final String[] DOC_DEPENDENT_TOOLBAR_IDS = {
            // Save As/All and Minify live inside the Save/Format SplitMenuButtons and are
            // gated together with their parent (a disabled SplitMenuButton disables its menu).
            "action-save",
            "action-undo", "action-redo",
            "action-format",
            "action-insert-template", "action-compare",
            "action-query-console", "action-set-schema"
    };

    /**
     * Rebuilds the Target dropdown's items from the currently open XML-family documents:
     * one radio entry per document (the active query/XSLT document itself excluded), the
     * current file-system target if one is chosen, "Choose XML from file system…" and
     * "Automatic (last active XML document)". Called on every menu opening.
     */
    private void rebuildQueryTargetMenu() {
        queryTargetButton.getItems().clear();
        var queryDoc = editorHost.getActiveDocument().orElse(null);
        if (queryDoc == null) {
            return;
        }
        var current = editorHost.getQueryTarget(queryDoc);
        var group = new javafx.scene.control.ToggleGroup();

        for (var doc : editorHost.getOpenXmlFamilyDocuments()) {
            if (doc == queryDoc) {
                continue;
            }
            var item = new javafx.scene.control.RadioMenuItem(doc.getDisplayName());
            item.setToggleGroup(group);
            var icon = new IconifyIcon(doc.getFileType().icon());
            icon.setIconSize(16);
            // bind, not set: themed CSS -fx-icon-color rules override set() on every pass
            icon.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(
                    javafx.scene.paint.Color.web(doc.getFileType().color())));
            item.setGraphic(icon);
            item.setSelected(current instanceof org.fxt.freexmltoolkit.controls.shell.editor
                    .QueryTarget.OpenDoc od && od.document() == doc);
            item.setOnAction(e -> {
                editorHost.setQueryTarget(queryDoc,
                        new org.fxt.freexmltoolkit.controls.shell.editor.QueryTarget.OpenDoc(doc));
                refreshQueryTargetButton();
            });
            queryTargetButton.getItems().add(item);
        }

        if (current instanceof org.fxt.freexmltoolkit.controls.shell.editor.QueryTarget.FsFile fs) {
            var item = new javafx.scene.control.RadioMenuItem(fs.file().getName());
            item.setToggleGroup(group);
            var icon = new IconifyIcon("bi-file-earmark-code");
            icon.setIconSize(16);
            item.setGraphic(icon);
            item.setSelected(true);
            // Re-selecting keeps the current file target — nothing to change.
            queryTargetButton.getItems().add(item);
        }

        queryTargetButton.getItems().add(new javafx.scene.control.SeparatorMenuItem());

        var browse = new javafx.scene.control.MenuItem("Choose XML from file system…");
        browse.setGraphic(sizedIcon("bi-folder2-open"));
        browse.setOnAction(e -> {
            var chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Choose XML Target");
            chooser.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter("XML files", "*.xml"),
                    new javafx.stage.FileChooser.ExtensionFilter(
                            "XML family", "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch"),
                    new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
            var file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window());
            if (file != null) {
                editorHost.setQueryTarget(queryDoc,
                        new org.fxt.freexmltoolkit.controls.shell.editor.QueryTarget.FsFile(file));
                refreshQueryTargetButton();
            }
        });
        queryTargetButton.getItems().add(browse);

        var automatic = new javafx.scene.control.RadioMenuItem("Automatic (last active XML document)");
        automatic.setToggleGroup(group);
        automatic.setGraphic(sizedIcon("bi-arrow-repeat"));
        automatic.setSelected(current instanceof org.fxt.freexmltoolkit.controls.shell.editor
                .QueryTarget.Automatic);
        automatic.setOnAction(e -> {
            editorHost.setQueryTarget(queryDoc,
                    org.fxt.freexmltoolkit.controls.shell.editor.QueryTarget.AUTOMATIC);
            refreshQueryTargetButton();
        });
        queryTargetButton.getItems().add(automatic);
    }

    /** @return a menu-sized (16px) {@link IconifyIcon} for the given literal. */
    private static IconifyIcon sizedIcon(String literal) {
        var icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return icon;
    }

    /**
     * Updates the Target dropdown's label from the active document's stored target.
     * A chosen open document that was closed in the meantime shows as Automatic again
     * (matching the resolution fallback); a file-system target shows its file name,
     * with the absolute path in the tooltip.
     */
    private void refreshQueryTargetButton() {
        var queryDoc = editorHost.getActiveDocument().orElse(null);
        var target = queryDoc == null
                ? org.fxt.freexmltoolkit.controls.shell.editor.QueryTarget.AUTOMATIC
                : editorHost.getQueryTarget(queryDoc);
        String label = "Automatic";
        String tooltip = "Choose the XML document the query / transform runs against";
        if (target instanceof org.fxt.freexmltoolkit.controls.shell.editor.QueryTarget.OpenDoc od
                && editorHost.getOpenDocuments().contains(od.document())) {
            label = od.document().getDisplayName();
        } else if (target instanceof org.fxt.freexmltoolkit.controls.shell.editor.QueryTarget.FsFile fs) {
            label = fs.file().getName();
            tooltip = fs.file().getAbsolutePath();
        }
        queryTargetButton.setText("Target: " + label);
        queryTargetButton.setTooltip(new javafx.scene.control.Tooltip(tooltip));
    }

    /** Enables/disables a toolbar action button by its {@code shell.fxml} id (no-op if absent). */
    private void setToolbarButtonDisabled(String id, boolean disabled) {
        for (javafx.scene.Node node : toolbarActions.getChildren()) {
            if (node instanceof javafx.scene.control.ButtonBase button && id.equals(button.getId())) {
                button.setDisable(disabled);
                return;
            }
        }
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
        java.io.File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, getScene() != null ? getScene().getWindow() : null);
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
                org.fxt.freexmltoolkit.util.DialogHelper.showActionError("New File",
                        "The template could not be rendered.",
                        "Check the template and any parameter values you entered, then try inserting it again.",
                        org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus.strip(content));
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
                        org.fxt.freexmltoolkit.util.DialogHelper.showActionError("New File",
                                "Could not generate a document from the schema.",
                                org.fxt.freexmltoolkit.util.DialogHelper.Remedies.INVALID_SCHEMA,
                                org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus.strip(xml));
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
                new javafx.stage.FileChooser.ExtensionFilter("XQuery / XPath / XProc",
                        "*.xq", "*.xquery", "*.xqm", "*.xqy", "*.xpath", "*.xpl", "*.xproc"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.openFile(file.toPath());
        }
    }

    /**
     * Opens the given file as a document in the shell's editor host. Public bridge
     * for callers outside the shell (e.g. tests) to hand a file to the shell.
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
            java.io.File out = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, window);
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
            java.io.File in = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, window);
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
            org.fxt.freexmltoolkit.util.DialogHelper.showActionError("Spreadsheet Converter",
                    "The conversion could not be completed.",
                    imported
                            ? "Check that the spreadsheet is a valid .xlsx/.csv file and not open in another program, then try again."
                            : "Check that the document is well-formed and the target file is writable (not open elsewhere), then try again.",
                    org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus.strip(result));
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
                new javafx.stage.FileChooser.ExtensionFilter("XQuery / XPath / XProc",
                        "*.xq", "*.xquery", "*.xqm", "*.xqy", "*.xpath", "*.xpl", "*.xproc"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
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
        java.io.File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
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

        // "Last run" execution-statistics item: collapses out of the bar while empty; fed by
        // the ExecutionStatsService listener (worker thread → runLater), so it only ever shows
        // text when the developer feature is enabled (disabled runs record nothing).
        statusLastRun.managedProperty().bind(statusLastRun.textProperty().isNotEmpty());
        statusLastRun.visibleProperty().bind(statusLastRun.textProperty().isNotEmpty());
        statusLastRun.setCursor(javafx.scene.Cursor.HAND);
        statusLastRun.setTooltip(new javafx.scene.control.Tooltip(
                "Last recorded operation — click for execution statistics"));
        statusLastRun.setOnMouseClicked(e -> editorHost.openExecutionStats());
        org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().addListener(
                stats -> javafx.application.Platform.runLater(
                        () -> statusLastRun.setText(stats.shortLabel())));

        // The XSD indicator tracks the active document's schema-binding lifecycle (detecting /
        // ready / none / error) so the user can tell when IntelliSense is available — but only
        // for schema-aware (XML-family) documents. For JSON / plain text an "XSD" label is
        // meaningless, so it is blanked and the label collapses out of the bar entirely.
        // A listener (not a binding) because text, icon, tooltip and style class change together.
        editorHost.activeSchemaStatusProperty().addListener((obs, o, n) -> updateSchemaStatusIndicator());
        editorHost.activeSchemaProperty().addListener((obs, o, n) -> updateSchemaStatusIndicator());
        editorHost.activeTabProperty().addListener((obs, o, n) -> updateSchemaStatusIndicator());
        updateSchemaStatusIndicator();
        statusSchema.managedProperty().bind(statusSchema.textProperty().isNotEmpty());
        statusSchema.visibleProperty().bind(statusSchema.textProperty().isNotEmpty());
        // The XSD indicator doubles as the binding entry point (VS-Code style): clicking it picks an
        // XSD and binds it to the active document via setSchemaForActiveDocument.
        statusSchema.setCursor(javafx.scene.Cursor.HAND);
        statusSchema.setOnMouseClicked(e -> setSchema());
        FileDropSupport.install(statusSchema,
                org.fxt.freexmltoolkit.service.DragDropService.XSD_EXTENSIONS,
                editorHost::setSchemaForActiveDocument);
    }

    /** All schema-status style classes; removed before the current one is (re-)applied. */
    private static final java.util.List<String> SCHEMA_STATUS_CLASSES = java.util.List.of(
            "fxt-status-schema-loading", "fxt-status-schema-ready",
            "fxt-status-schema-none", "fxt-status-schema-error");

    /** Refreshes the status bar's XSD/IntelliSense indicator from the active document's state. */
    private void updateSchemaStatusIndicator() {
        statusSchema.getStyleClass().removeAll(SCHEMA_STATUS_CLASSES);
        var doc = editorHost.getActiveDocument().orElse(null);
        if (doc == null || !isSchemaAware(doc.getFileType())) {
            statusSchema.setText("");
            statusSchema.setGraphic(null);
            statusSchema.setTooltip(null);
            return;
        }
        var status = editorHost.activeSchemaStatusProperty().get();
        java.io.File xsd = editorHost.activeSchemaProperty().get();
        statusSchema.setText(schemaStatusText(status, xsd));
        IconifyIcon icon = new IconifyIcon(schemaStatusIcon(status));
        icon.setIconSize(12);
        statusSchema.setGraphic(icon);
        statusSchema.getStyleClass().add(schemaStatusStyleClass(status));
        statusSchema.setTooltip(new javafx.scene.control.Tooltip(schemaStatusTooltip(status, xsd)));
    }

    /** @return the indicator label text for a schema status (package-private for unit tests). */
    static String schemaStatusText(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status,
            java.io.File xsd) {
        return switch (status) {
            case LOADING -> "Detecting XSD…";
            case READY -> xsd != null ? "XSD: " + xsd.getName() : "XSD ready";
            case ERROR -> "XSD error";
            case NONE -> "No XSD";
        };
    }

    /** @return the {@code bi-*} icon literal for a schema status (package-private for unit tests). */
    static String schemaStatusIcon(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status) {
        return switch (status) {
            case LOADING -> "bi-hourglass-split";
            case READY -> "bi-check-circle";
            case ERROR -> "bi-exclamation-triangle";
            case NONE -> "bi-slash-circle";
        };
    }

    /** @return the style class carrying the status icon's color on the blue status bar. */
    static String schemaStatusStyleClass(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status) {
        return switch (status) {
            case LOADING -> "fxt-status-schema-loading";
            case READY -> "fxt-status-schema-ready";
            case ERROR -> "fxt-status-schema-error";
            case NONE -> "fxt-status-schema-none";
        };
    }

    /** @return the tooltip explaining IntelliSense availability for a schema status. */
    static String schemaStatusTooltip(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status,
            java.io.File xsd) {
        return switch (status) {
            case LOADING -> "Detecting and parsing the linked XSD schema — IntelliSense will be available shortly";
            case READY -> "IntelliSense is available (schema: " + (xsd != null ? xsd.getName() : "bound")
                    + "). Click to bind a different XSD";
            case ERROR -> "The linked XSD could not be loaded — IntelliSense is unavailable. "
                    + "Click to bind an XSD manually";
            case NONE -> "No XSD schema is linked — IntelliSense is limited. Click to bind an XSD";
        };
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
