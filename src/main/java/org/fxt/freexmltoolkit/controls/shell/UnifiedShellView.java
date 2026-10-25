package org.fxt.freexmltoolkit.controls.shell;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
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
    /** Menu bar (needs this shell for its actions); injected into {@link #menuBarHost}. */
    private ShellMenuBar menuBar;
    /** Cached so the editor toolbar "Validate" action can drive the same panel. */
    private org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel validationPanel;
    /** Cached so Ctrl+Shift+F / Ctrl+Shift+H can focus the same panel's query field. */
    private org.fxt.freexmltoolkit.controls.shell.editor.search.SearchPanel searchPanel;
    /** Cached so the Search panel can default its scope to the Explorer's workspace folder. */
    private org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel explorerPanel;
    /** Cached so {@link #showSchemaCache()} can select the panel's Cache tab. */
    /** The Schema Library page (a main-area tab, like Settings) and its tab. */
    private org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryView schemaLibraryView;
    private javafx.scene.control.Tab schemaLibraryTab;
    /** The Signature page (a main-area tab), its tab and the side panel picking its action. */
    private org.fxt.freexmltoolkit.controls.shell.editor.SignatureView signaturePage;
    private javafx.scene.control.Tab signatureTab;
    private org.fxt.freexmltoolkit.controls.shell.editor.PageNavigator signatureNavigator;
    /** The Schema Library activity's side panel: the page's sections. */
    private org.fxt.freexmltoolkit.controls.shell.editor.PageNavigator schemaLibraryNavigator;
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
    /** The Settings page shown in {@link #settingsTab}, and the side panel listing its groups. */
    private org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel settingsPage;
    private org.fxt.freexmltoolkit.controls.shell.editor.PageNavigator settingsNavigator;

    /** Editor-level document actions; created in {@link #initialize()} once {@link #editorHost} is injected. */
    private org.fxt.freexmltoolkit.controls.shell.editor.EditorActions editorActions;
    /** Long-lived Query Console (XPath/XQuery); docked as a collapsible bottom strip (created in initialize). */
    private org.fxt.freexmltoolkit.controls.shell.editor.QueryConsole queryConsole;

    // ----- FXML-injected nodes (defined in /pages/shell.fxml; populated by FXMLLoader) -----
    /** Full-width host of the code-built {@link ShellMenuBar} (empty on macOS: system menu bar). */
    @FXML private StackPane menuBarHost;
    /** The header's Help button; anchors the Help popup ({@link #showHelpMenu()}). */
    @FXML private Button helpButton;
    private javafx.scene.control.ContextMenu helpPopup;
    @FXML private StackPane activityBarHost;
    @FXML private StackPane sidePanelHost;
    @FXML private org.fxt.freexmltoolkit.controls.shell.editor.EditorHost editorHost;
    @FXML private org.fxt.freexmltoolkit.controls.unified.UnifiedSearchBar searchBar;
    /** Vertical split: item 0 = work area (grows), item 1 = {@link #queryConsole} when shown. */
    @FXML private SplitPane workSplit;
    /** Horizontal split: side panel | editor column | inspector. Collapsed side columns are removed as items. */
    @FXML private SplitPane workArea;
    @FXML private StackPane leftPanelWrapper;
    @FXML private StackPane inspectorWrapper;
    @FXML private VBox editorCenter;
    @FXML private StackPane viewSwitchHost;
    @FXML private FlowPane toolbarActions;
    @FXML private javafx.scene.control.ToggleButton leftPanelToggle;
    @FXML private javafx.scene.control.ToggleButton inspectorToggle;
    @FXML private javafx.scene.control.Button actionValidate;
    @FXML private javafx.scene.control.SplitMenuButton actionTransform;
    @FXML private javafx.scene.control.SplitMenuButton actionRun;
    @FXML private javafx.scene.control.MenuItem actionGenerateDocs;
    @FXML private javafx.scene.control.MenuItem actionTypeEditor;
    @FXML private javafx.scene.control.MenuItem actionGenerateSample;
    @FXML private javafx.scene.control.MenuItem actionGenerateXsd;
    @FXML private javafx.scene.control.MenuItem actionRunQuery;
    @FXML private javafx.scene.control.MenuItem actionRunTransform;
    @FXML private javafx.scene.control.MenuItem actionDebugTransform;
    @FXML private javafx.scene.control.MenuItem actionDebugQuery;
    @FXML private javafx.scene.control.MenuItem actionDebugSchematron;
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
    /** Property keys for the persisted side-panel widths (in pixels; set by dragging the dividers). */
    private static final String LEFT_WIDTH_KEY = "shell.leftPanel.width";
    private static final String INSPECTOR_WIDTH_KEY = "shell.inspector.width";
    private static final double LEFT_WIDTH_DEFAULT = 260;
    private static final double INSPECTOR_WIDTH_DEFAULT = 288;
    /** Narrowest draggable/persistable panel width; matches the wrappers' {@code minWidth} in shell.fxml. */
    private static final double MIN_PANEL_WIDTH = 200;

    /** User collapse state for the two side panels (persisted across restarts; default open). */
    private boolean leftPanelOpen = loadPanelPref(LEFT_PANEL_KEY);
    private boolean inspectorOpen = loadPanelPref(INSPECTOR_KEY);
    /** Suppresses width saves while a divider is being positioned programmatically. */
    private boolean restoringDividers;
    /** Debounces the width persistence so a divider drag writes the properties file once, not per pixel. */
    private final javafx.animation.PauseTransition widthSaveDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
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
        FxmlViews.load(this, "/pages/shell.fxml");
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
        menuBar = new ShellMenuBar(this);
        menuBarHost.getChildren().setAll(menuBar);
        viewSwitchHost.getChildren().setAll(editorHost.getViewSwitch());
        // Inspector below the chevron (chevron stays on top of the StackPane).
        inspectorWrapper.getChildren().add(0,
                new org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel(editorHost));
        // PROBLEMS panel below the editor (hides itself while empty).
        editorCenter.getChildren().add(
                new org.fxt.freexmltoolkit.controls.shell.editor.ProblemsPanel(editorHost));
        searchBar.hide(); // shown on Ctrl+F / Ctrl+H
        editorHost.queryConsoleRequestsProperty().addListener((obs, oldV, newV) -> openQueryConsole());
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
        //     or an activity is picked); the user collapse state hides a panel even with a doc open.
        //     The inspector only has something to show for a document tab: tool tabs (Settings,
        //     Sign, reports, …) hide it without touching the user's choice, so it comes back
        //     as it was when a document tab is selected again (UX plan 4.5). ---
        chromeUpdater = () -> {
            boolean hasDocument = !editorHost.isEmpty();
            boolean documentTab = editorHost.getActiveDocument().isPresent();
            boolean leftArea = hasDocument || activityChosen;
            boolean showLeft = leftArea && leftPanelOpen;
            boolean showRight = documentTab && inspectorOpen;
            syncWorkItem(leftPanelWrapper, showLeft, true, LEFT_WIDTH_KEY, LEFT_WIDTH_DEFAULT);
            syncWorkItem(inspectorWrapper, showRight, false, INSPECTOR_WIDTH_KEY, INSPECTOR_WIDTH_DEFAULT);
            leftPanelToggle.setDisable(!leftArea);
            leftPanelToggle.setSelected(leftPanelOpen);
            inspectorToggle.setDisable(!documentTab);
            inspectorToggle.setSelected(inspectorOpen);
        };
        // Apply the persisted panel widths to the FXML-declared items before the first layout,
        // so the startup divider positions come out right even without a show transition.
        leftPanelWrapper.setPrefWidth(loadWidthPref(LEFT_WIDTH_KEY, LEFT_WIDTH_DEFAULT));
        inspectorWrapper.setPrefWidth(loadWidthPref(INSPECTOR_WIDTH_KEY, INSPECTOR_WIDTH_DEFAULT));
        widthSaveDebounce.setOnFinished(e -> {
            saveWidthPref(LEFT_WIDTH_KEY, leftPanelWrapper);
            saveWidthPref(INSPECTOR_WIDTH_KEY, inspectorWrapper);
        });
        leftPanelWrapper.widthProperty().addListener(obs -> queueWidthSave());
        inspectorWrapper.widthProperty().addListener(obs -> queueWidthSave());
        chromeUpdater.run();
        editorHost.getOpenDocuments().addListener((javafx.beans.InvalidationListener) obs -> chromeUpdater.run());
        editorHost.activeTabProperty().addListener((obs, oldTab, newTab) -> chromeUpdater.run());

        // --- Toolbar: keep the action FlowPane single-row (its preferred height must match the
        //     rendered width, not the default 400px wrap; it only wraps as narrow-window
        //     fallback), register buttons for display settings, and type-gate the
        //     document-action buttons. Buttons with an empty FXML text stay icon-only
        //     permanently (variant E: secondary tools carry their name in the tooltip). ---
        toolbarActions.prefWrapLengthProperty().bind(toolbarActions.widthProperty());
        for (javafx.scene.Node node : toolbarActions.getChildren()) {
            // The Input dropdown's label is dynamic ("Input: <doc>") — registering it
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
            if (newV != null) {
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.activityOpened(newV.id());
            }
        });
        // A press on the already-active activity fires no model change but should still land there.
        activityBar.setOnUserSelect(this::revealSidePanel);

        // Keep the status bar in sync with the active editor.
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> updateStatusBar());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> updateStatusBar());
        updateStatusBar();

        // File-operation keyboard shortcuts (scoped to the shell).
        addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, this::handleShortcutTracked);
        // Window-level chords (Quit, tab cycling, Ctrl+F4) run as a filter: they must win over
        // whatever control has the focus, and a focused editor would otherwise see them first.
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::handleGlobalShortcut);
        // Welcome/Dashboard tool cards switch activities (and "open-folder" → Explorer).
        editorHost.setWelcomeActionHandler(this::handleWelcomeAction);
        // A FundsXML document while the extension is off: offer it once (UX plan 5.5).
        editorHost.setDocumentLoadedHandler((doc, content) -> {
            if (doc == validateWhenLoaded) {
                validateWhenLoaded = null;
                validateActive();
            }
            if (org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.shouldOffer(content)) {
                showFundsXmlHint();
            }
        });

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

    /**
     * @return {@code true} if {@code files} contains at least one file of a type the editor host
     *         can open (XML family, JSON, HTML, query documents — see
     *         {@link org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType#openableExtensions()})
     */
    public static boolean acceptsDrop(java.util.List<java.io.File> files) {
        return files != null && org.fxt.freexmltoolkit.service.DragDropService.hasFilesWithExtensions(files,
                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.openableExtensions());
    }

    /** Opens every file of a supported editor type from {@code files} in the editor host. @return the count opened. */
    public int openDroppedFiles(java.util.List<java.io.File> files) {
        java.util.List<java.io.File> supported = org.fxt.freexmltoolkit.service.DragDropService.filterByExtensions(
                files, org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.openableExtensions());
        if (!supported.isEmpty()) {
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.command("open",
                    org.fxt.freexmltoolkit.service.telemetry.UsageEvents.VIA_DROP);
        }
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
        org.fxt.freexmltoolkit.service.telemetry.UsageEvents.command(key,
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.VIA_WELCOME);
        if ("open-folder".equals(key)) {
            selectionModel.select(Activity.EXPLORER);
            revealSidePanel();
            return;
        }
        if (key != null && key.startsWith("fundsxml-")) {
            handleFundsXmlWelcomeAction(key);
            return;
        }
        if ("open-example".equals(key)) {
            openExample();
            return;
        }
        if (key != null && key.startsWith("task-")) {
            runWelcomeTask(key);
            return;
        }
        Activity.fromId(key).ifPresent(this::showActivity);
    }

    /**
     * Picks the file a welcome task needs; replaceable in tests (the real one shows a file
     * chooser). Returns whether a document is active afterwards.
     */
    private java.util.function.BooleanSupplier welcomeFilePicker = () -> {
        editorHost.openFileChooser();
        return editorHost.getActiveDocument().isPresent();
    };

    /** Test seam: replaces the file chooser of the welcome tasks. */
    void setWelcomeFilePicker(java.util.function.BooleanSupplier picker) {
        this.welcomeFilePicker = picker;
    }

    /**
     * Runs a task row of the Welcome page (UX plan 5.2): tasks that work on a file first ask for
     * one, then open the panel (or console) where the task continues; cancelling the file
     * chooser does nothing.
     */
    private void runWelcomeTask(String key) {
        switch (key) {
            case "task-validate" -> pickFileThen(() -> {
                showActivity(Activity.VALIDATION);
                validateOnceLoaded();
            });
            case "task-transform" -> pickFileThen(() -> showActivity(Activity.TRANSFORM));
            case "task-query" -> pickFileThen(this::openQueryConsole);
            case "task-schema" -> pickFileThen(() -> showActivity(Activity.SCHEMA));
            case "task-pdf" -> pickFileThen(() -> showActivity(Activity.PDF_FOP));
            case "task-sign" -> showActivity(Activity.SIGNATURE);
            default -> { }
        }
    }

    /** The document the Validate task waits for (validated as soon as its file is read), or null. */
    private org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument validateWhenLoaded;

    /** Validates the active document now, or as soon as its file has been read. */
    private void validateOnceLoaded() {
        if (editorHost.isActiveDocumentLoading()) {
            validateWhenLoaded = editorHost.getActiveDocument().orElse(null);
        } else {
            validateActive();
        }
    }

    private void pickFileThen(Runnable next) {
        if (welcomeFilePicker.getAsBoolean()) {
            next.run();
        }
    }

    /**
     * Welcome "Open an Example" (UX plan 5.1): copies the bundled order example (XML + XSD +
     * XSLT) to the user folder off the UI thread and opens the XML; its schema binds through
     * {@code xsi:noNamespaceSchemaLocation}, so validation works right away.
     */
    private void openExample() {
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                java.nio.file.Path document = org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles
                        .install(org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles.defaultTarget());
                javafx.application.Platform.runLater(() -> editorHost.openFile(document));
            } catch (java.io.IOException e) {
                javafx.application.Platform.runLater(() -> org.fxt.freexmltoolkit.util.DialogHelper.showError(
                        "Open an Example", "The example could not be created",
                        e.getMessage()));
            }
        });
    }

    /** Downloads the FundsXML content (or registers the cached one) after the extension was turned on. */
    private void fetchFundsXmlContent(String trigger) {
        var coordinator = org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlDownloadCoordinator.getInstance();
        var service = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService.getInstance();
        if (service.isCacheEmpty()) {
            coordinator.startBackgroundDownload(trigger);
        } else {
            coordinator.runRegisterOnly();
        }
    }

    /** Shows the one-time FundsXML hint above the editor (no-op while one is showing). */
    void showFundsXmlHint() {
        if (editorCenter.lookup("#fundsxml-hint") != null) {
            return;
        }
        org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.markShown();
        var bar = new org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHintBar(
                this::enableFundsXmlFromHint, () -> { });
        editorCenter.getChildren().add(editorCenter.getChildren().indexOf(editorHost), bar);
    }

    /**
     * "Enable FundsXML extensions" in the hint: the same as ticking the box in Settings and saving
     * it - the flag is stored, the rail gets its FundsXML button and the content is fetched.
     */
    private void enableFundsXmlFromHint() {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .set(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys.ENABLED, "true");
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger(UnifiedShellView.class)
                    .warn("Could not enable the FundsXML extension: {}", t.getMessage());
            return;
        }
        if (settingsPage != null) {
            settingsPage.markFundsXmlEnabled(); // an open Settings page must not save it off again
        }
        activityBar.refresh();
        fundsXmlContentFetcher.accept("hint-enable");
    }

    /** Fetches the FundsXML content after the hint turned the extension on; replaceable in tests. */
    private java.util.function.Consumer<String> fundsXmlContentFetcher = this::fetchFundsXmlContent;

    /** Test seam: replaces the FundsXML content download started by the hint. */
    void setFundsXmlContentFetcher(java.util.function.Consumer<String> fetcher) {
        this.fundsXmlContentFetcher = fetcher;
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
                if (dir != null) {
                    org.fxt.freexmltoolkit.util.DesktopActions.open(dir.toFile(), e ->
                            org.apache.logging.log4j.LogManager.getLogger(UnifiedShellView.class)
                                    .warn("Could not open FundsXML examples folder: {}", e.getMessage()));
                }
            }
            default -> { }
        }
    }

    /**
     * True while a keyboard shortcut or menu item is dispatched, so toolbar handlers it reuses
     * are not counted as clicks.
     */
    private boolean dispatchingShortcut;

    /**
     * Runs a menu-bar action and counts it as a menu command (not as the toolbar click the
     * reused handler would otherwise log).
     *
     * @param command fixed command id, e.g. {@code save}
     * @param action  the action to run
     */
    public void runFromMenu(String command, Runnable action) {
        boolean nested = dispatchingShortcut;
        dispatchingShortcut = true;
        try {
            action.run();
        } finally {
            dispatchingShortcut = nested;
        }
        org.fxt.freexmltoolkit.service.telemetry.UsageEvents.command(command,
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.VIA_MENU);
    }

    /** @return the shell's menu bar (in the window on Windows/Linux, the system menu bar on macOS). */
    public ShellMenuBar getMenuBar() {
        return menuBar;
    }

    /**
     * Shows an activity like a press on its Activity Bar button: selects it and reveals its side
     * panel (Settings opens its main-area page instead).
     */
    public void showActivity(Activity activity) {
        if (activity == Activity.SETTINGS) {
            openSettings();
            return;
        }
        selectionModel.select(activity);
        revealSidePanel();
    }

    /**
     * Chords that belong to the window, not to a view: Ctrl/Cmd+Q quits (on macOS the native
     * Quit item is the same action — the consumed key never reaches the system menu, so it does
     * not run twice), Ctrl+Tab / Ctrl+Shift+Tab cycle the editor tabs, and Ctrl+F4 closes the
     * active tab on Windows/Linux (the MDI convention; on macOS Cmd+W is the only close chord).
     */
    private void handleGlobalShortcut(javafx.scene.input.KeyEvent event) {
        if (event.isAltDown()) {
            return;
        }
        if (event.getCode() == javafx.scene.input.KeyCode.Q && event.isShortcutDown() && !event.isShiftDown()) {
            runFromShortcut(event, "exit", this::requestExit);
        } else if (event.getCode() == javafx.scene.input.KeyCode.TAB && event.isControlDown()) {
            runFromShortcut(event, event.isShiftDown() ? "previous_tab" : "next_tab",
                    () -> editorHost.selectAdjacentTab(event.isShiftDown() ? -1 : 1));
        } else if (event.getCode() == javafx.scene.input.KeyCode.F4 && event.isShortcutDown()
                && !event.isShiftDown() && !ShellMenuBar.MAC) {
            runFromShortcut(event, "close_tab", editorHost::closeActiveTab);
        }
    }

    private void runFromShortcut(javafx.scene.input.KeyEvent event, String command, Runnable action) {
        event.consume();
        boolean nested = dispatchingShortcut;
        dispatchingShortcut = true;
        try {
            action.run();
        } finally {
            dispatchingShortcut = nested;
        }
        org.fxt.freexmltoolkit.service.telemetry.UsageEvents.command(command,
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.VIA_SHORTCUT);
    }

    /**
     * Asks about unsaved documents before the application closes; called from the window's
     * close-request handler. {@code false} means the user cancelled (or a save failed).
     */
    public boolean confirmExit() {
        return editorHost.confirmExit();
    }

    /**
     * Asks the window to close, exactly like its title-bar button: File → Exit, Ctrl/Cmd+Q and
     * the macOS Quit item all end here, so the application's close handling applies once.
     */
    public void requestExit() {
        javafx.stage.Window window = getScene() != null ? getScene().getWindow() : null;
        if (window != null) {
            window.fireEvent(new javafx.stage.WindowEvent(window, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
        }
    }

    private void handleShortcutTracked(javafx.scene.input.KeyEvent event) {
        dispatchingShortcut = true;
        try {
            handleShortcut(event);
        } finally {
            dispatchingShortcut = false;
        }
        if (event.isConsumed()) {
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.command(shortcutName(event),
                    org.fxt.freexmltoolkit.service.telemetry.UsageEvents.VIA_SHORTCUT);
        }
    }

    /** Normalized key combination, e.g. {@code mod+shift+f} (mod = Ctrl, or Cmd on macOS). */
    static String shortcutName(javafx.scene.input.KeyEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.isShortcutDown()) {
            sb.append("mod+");
        }
        if (event.isAltDown()) {
            sb.append("alt+");
        }
        if (event.isShiftDown()) {
            sb.append("shift+");
        }
        return sb.append(event.getCode().name().toLowerCase(java.util.Locale.ROOT)).toString();
    }

    /** Counts a toolbar click (not when a shortcut reuses the handler). */
    private void toolbar(String command) {
        if (!dispatchingShortcut) {
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.command(command,
                    org.fxt.freexmltoolkit.service.telemetry.UsageEvents.VIA_TOOLBAR);
        }
    }

    private void handleShortcut(javafx.scene.input.KeyEvent event) {
        // While an XSLT debug session is paused, F5/F7/F8/Shift+F8 drive the debugger.
        if (editorHost.handleDebugShortcut(event)) {
            event.consume();
            return;
        }
        // Ctrl/Cmd+Shift+F5 repeats the last debug run.
        if (event.getCode() == javafx.scene.input.KeyCode.F5 && event.isShiftDown()
                && event.isShortcutDown() && !event.isAltDown()) {
            rerunLastDebug();
            event.consume();
            return;
        }
        // Shift+F5 starts the XSLT debugger (Debug with XSLT…).
        if (event.getCode() == javafx.scene.input.KeyCode.F5 && event.isShiftDown()
                && !event.isShortcutDown() && !event.isAltDown()) {
            onDebugXslt();
            event.consume();
            return;
        }
        // Ctrl/Cmd+F10: Run to Cursor (starts the debugger to the caret line when none runs).
        if (event.getCode() == javafx.scene.input.KeyCode.F10 && event.isShortcutDown()
                && !event.isShiftDown() && !event.isAltDown()) {
            if (editorHost.canRunToCursor()) {
                runToCursor();
                event.consume();
            }
            return;
        }
        // Function keys (no modifier) - all listed in the Keyboard Shortcuts dialog.
        switch (event.getCode()) {
            case F1 -> {
                // F1 shows the Help menu under the header's Help button.
                showHelpMenu();
                event.consume();
                return;
            }
            case F5 -> {
                // F5 "executes" the active document: runnable types (XQuery/XPath/XSLT/XProc)
                // go through the toolbar's Run action, everything else is validated.
                executeActive();
                event.consume();
                return;
            }
            case F8 -> {
                // F8 validates the active document.
                validateActive();
                event.consume();
                return;
            }
            case F9 -> {
                // F9 toggles the breakpoint on the caret line of a saved XSLT document.
                if (editorHost.toggleBreakpointAtCaret()) {
                    event.consume();
                }
                return;
            }
            case F11 -> {
                toggleFullScreen();
                event.consume();
                return;
            }
            default -> { /* fall through to the modifier shortcuts below */ }
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
                if (ShellMenuBar.MAC && event.isControlDown()) {
                    // Ctrl+Cmd+F — the macOS full-screen shortcut.
                    toggleFullScreen();
                } else if (ShellMenuBar.MAC && event.isAltDown()) {
                    // Alt+Cmd+F — Find & Replace on macOS, where Cmd+H hides the application.
                    showSearch(true);
                } else if (event.isShiftDown()) {
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
                    event.consume();
                } else if (!ShellMenuBar.MAC) {
                    // Not on macOS: Cmd+H belongs to the system ("Hide FreeXmlToolkit").
                    showSearch(true);
                    event.consume();
                }
            }
            case W -> {
                if (!event.isShiftDown()) {
                    editorHost.closeActiveTab();
                    event.consume();
                }
            }
            case COMMA -> {
                // Ctrl/Cmd+, opens the settings (macOS "Settings…" convention).
                openSettings();
                event.consume();
            }
            case X -> {
                if (event.isShiftDown()) {
                    toggleQueryConsole();
                    event.consume();
                }
            }
            case K -> {
                // Ctrl+K mirrors the header search pill (labelled "Ctrl K"): open the
                // Query Console and focus its input, or just focus it when already shown.
                if (!event.isShiftDown()) {
                    openQueryConsole();
                    event.consume();
                }
            }
            case D -> {
                // Ctrl+D adds the active document to favorites, Ctrl+Shift+D toggles the
                // Favorites side panel (both listed in the Keyboard Shortcuts dialog).
                if (event.isShiftDown()) {
                    toggleFavoritesPanel();
                } else {
                    addActiveDocumentToFavorites();
                }
                event.consume();
            }
            case L -> {
                if (!event.isShiftDown()) {
                    toggleLeftPanel();
                    event.consume();
                }
            }
            case P -> {
                if (event.isShiftDown()) {
                    setInspectorVisible(!isInspectorOpen());
                    event.consume();
                }
            }
            case E -> {
                if (!event.isShiftDown()) {
                    convertSpreadsheet();
                    event.consume();
                }
            }
            case T -> {
                if (!event.isShiftDown()) {
                    insertTemplate();
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
        int length = editorHost.getActiveTextLength();
        statusChars.setText(length >= 0 ? String.format("%,d chars", length) : "");
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

    /** Shows the Query Console (if hidden) and focuses its query input; used by Ctrl+K and the header search pill. */
    public void openQueryConsole() {
        if (isQueryConsoleShown()) {
            queryConsole.focusInput();
        } else {
            toggleQueryConsole();
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

    /**
     * Ctrl+L: shows or hides the left side panel (the active activity's panel) - also from the
     * full-width dashboard, where it is hidden until an activity is chosen.
     */
    public void toggleLeftPanel() {
        if (isLeftPanelOpen() && activityChosen) {
            setLeftPanelVisible(false);
        } else {
            revealSidePanel();
        }
    }

    /**
     * Ctrl+Shift+D: shows the Favorites side panel, or collapses the side panel when Favorites
     * is already the visible activity.
     */
    public void toggleFavoritesPanel() {
        if (selectionModel.getActive() == Activity.FAVORITES && isLeftPanelOpen() && activityChosen) {
            setLeftPanelVisible(false);
            return;
        }
        selectionModel.select(Activity.FAVORITES);
        revealSidePanel();
    }

    /**
     * Ctrl+D: adds the active document to favorites and refreshes the Favorites panel.
     *
     * @return {@code true} if a saved document was active and added; {@code false} for no
     * document or an unsaved buffer
     */
    public boolean addActiveDocumentToFavorites() {
        java.util.Optional<org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument> doc =
                editorHost.getActiveDocument().filter(d -> d.getPath() != null);
        if (doc.isEmpty()) {
            return false;
        }
        org.fxt.freexmltoolkit.service.FavoritesService.getInstance().addFavorite(doc.get().getPath().toFile());
        if (sidePanels.get(Activity.FAVORITES)
                instanceof org.fxt.freexmltoolkit.controls.shell.editor.FavoritesActivityPanel favorites) {
            favorites.refresh();
        }
        return true;
    }

    /** Shows the Schema Library page with its Cache tab selected. */
    public void showSchemaCache() {
        selectionModel.select(Activity.SCHEMA_LIBRARY);
        revealSidePanel();
        openSchemaLibraryTab().showCacheTab();
    }

    /**
     * Opens (or re-selects) the Signature page as a tab in the main editor area; the document row
     * follows the active editor document on every open.
     *
     * @return the page
     */
    private org.fxt.freexmltoolkit.controls.shell.editor.SignatureView openSignaturePage() {
        if (signatureTab != null && editorHost.containsTab(signatureTab)) {
            signaturePage.useActiveDocument();
            editorHost.selectTab(signatureTab);
            return signaturePage;
        }
        signaturePage = new org.fxt.freexmltoolkit.controls.shell.editor.SignatureView(editorHost);
        if (signatureNavigator != null) {
            signaturePage.setOnActionShown(signatureNavigator::setCurrent);
        }
        signatureTab = editorHost.openToolTab("Signature", "bi-shield-lock", signaturePage);
        return signaturePage;
    }

    /**
     * Opens (or re-selects) the Schema Library page as a tab in the main editor area: its
     * tables need the editor's width, the side panel only lists the page's sections.
     *
     * @return the page
     */
    private org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryView openSchemaLibraryTab() {
        if (schemaLibraryTab != null && editorHost.containsTab(schemaLibraryTab)) {
            editorHost.selectTab(schemaLibraryTab);
            return schemaLibraryView;
        }
        schemaLibraryView = new org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryView(editorHost);
        if (schemaLibraryNavigator != null) {
            schemaLibraryView.setOnSectionShown(schemaLibraryNavigator::setCurrent);
        }
        schemaLibraryTab = editorHost.openToolTab("Schema Library", "bi-collection", schemaLibraryView);
        return schemaLibraryView;
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

    /** @return the horizontal work split (side panel | editor | inspector) — for tests. */
    SplitPane getWorkArea() {
        return workArea;
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

    /**
     * Shows or hides a side column of the horizontal work split. SplitPane does not reclaim
     * the space of merely unmanaged items, so hiding removes the item from the split; the
     * visible/managed flags are kept in sync for layout-state observers. On show, the persisted
     * pixel width is applied as {@code prefWidth} before insertion (the skin derives the new
     * divider position from it) and re-asserted after the next layout pass.
     */
    private void syncWorkItem(Region node, boolean show, boolean atStart, String key, double defaultWidth) {
        node.setVisible(show);
        node.setManaged(show);
        boolean present = workArea.getItems().contains(node);
        if (!show) {
            if (present) {
                workArea.getItems().remove(node);
            }
        } else if (!present) {
            node.setPrefWidth(loadWidthPref(key, defaultWidth));
            workArea.getItems().add(atStart ? 0 : workArea.getItems().size(), node);
            restoreDivider(node, atStart, key, defaultWidth);
        }
    }

    /**
     * Re-asserts the persisted pixel width of a just-shown side column by positioning its
     * divider. Divider positions set before the skin has laid out are clamped/overwritten,
     * hence the {@code runLater} plus a one-shot retry while the split has no width yet
     * (startup). The divider index is derived from the live item list — with the left panel
     * collapsed, the inspector's divider is index 0, not 1.
     */
    private void restoreDivider(Region node, boolean atStart, String key, double defaultWidth) {
        javafx.application.Platform.runLater(() -> {
            double total = workArea.getWidth();
            if (total <= 0) {
                workArea.widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>() {
                    @Override
                    public void changed(javafx.beans.value.ObservableValue<? extends Number> obs,
                                        Number oldValue, Number newValue) {
                        workArea.widthProperty().removeListener(this);
                        restoreDivider(node, atStart, key, defaultWidth);
                    }
                });
                return;
            }
            int index = workArea.getItems().indexOf(node);
            if (index < 0) {
                return; // hidden again before the restore ran
            }
            double width = loadWidthPref(key, defaultWidth);
            restoringDividers = true;
            try {
                workArea.setDividerPosition(atStart ? index : index - 1,
                        atStart ? width / total : 1 - width / total);
            } finally {
                restoringDividers = false;
            }
        });
    }

    /** Restarts the debounced width save (no-op while a divider is being restored programmatically). */
    private void queueWidthSave() {
        if (!restoringDividers) {
            widthSaveDebounce.playFromStart();
        }
    }

    /**
     * Persists the current width of {@code node} under {@code key} — only while it is a live
     * item of the work split with a sane width, so transient/collapsed states are never saved.
     */
    private void saveWidthPref(String key, Region node) {
        double width = node.getWidth();
        if (!workArea.getItems().contains(node) || width < MIN_PANEL_WIDTH) {
            return;
        }
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .set(key, String.valueOf(Math.round(width)));
        } catch (Throwable ignored) {
            // properties service unavailable — nothing to persist
        }
    }

    /** @return the persisted panel width for {@code key} (clamped to the minimum), or {@code defaultWidth}. */
    private static double loadWidthPref(String key, double defaultWidth) {
        try {
            String value = org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).get(key);
            if (value != null) {
                return Math.max(MIN_PANEL_WIDTH, Double.parseDouble(value));
            }
        } catch (Throwable ignored) {
            // properties service unavailable or malformed value — fall back to the default
        }
        return defaultWidth;
    }

    /**
     * Status-bar "last run" badge (spec 2026-09-26 §2): chip in the colours of the workflow that ran,
     * result icon in the semantic colour (success/danger wins over the workflow colour).
     * Package-private so the shell test can feed a synthetic run.
     */
    void showLastRunBadge(org.fxt.freexmltoolkit.service.ExecutionStats stats) {
        if (stats == null) {
            return;
        }
        var workflow = org.fxt.freexmltoolkit.controls.theme.Workflow.forOperationType(
                stats.type() == null ? null : stats.type().name());
        org.fxt.freexmltoolkit.controls.theme.WorkflowStyle.apply(statusLastRun, workflow);
        IconifyIcon result = new IconifyIcon(stats.success() ? "bi-check-circle" : "bi-x-circle");
        result.setIconSize(11);
        org.fxt.freexmltoolkit.controls.theme.SemanticIcon.bind(result, stats.success()
                ? org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.SUCCESS
                : org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.DANGER);
        statusLastRun.setGraphic(result);
        statusLastRun.setText(stats.shortLabel());
    }

    private void showSidePanelFor(Activity activity) {
        if (activity == Activity.SETTINGS) {
            // Settings content lives in a main-area tab; (re)open/focus it on every
            // selection. The side panel only carries a pointer to it.
            openSettingsTab();
        } else if (activity == Activity.SCHEMA_LIBRARY) {
            // Same for the Schema Library: a page in the main area, its sections in the panel.
            openSchemaLibraryTab();
        } else if (activity == Activity.SIGNATURE) {
            // And for Signature: one page with the form of the action picked in the panel.
            openSignaturePage();
        }
        // Workflow colour scope for the panel's title, primary action and row hover (spec 2026-09-26 §2).
        org.fxt.freexmltoolkit.controls.theme.WorkflowStyle.apply(sidePanelHost,
                activity == null ? null : activity.workflow());
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
            case SCHEMA_LIBRARY -> {
                var nav = new org.fxt.freexmltoolkit.controls.shell.editor.PageNavigator("library", "SCHEMA LIBRARY",
                        "The library opens as a page in the main window.",
                        org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryView.SECTIONS,
                        key -> openSchemaLibraryTab().showSection(key), false);
                schemaLibraryNavigator = nav;
                if (schemaLibraryView != null) {
                    schemaLibraryView.setOnSectionShown(nav::setCurrent);
                }
                yield nav;
            }
            case VALIDATION -> validationPanel();
            case TRANSFORM -> new org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel(editorHost);
            case FAVORITES -> new org.fxt.freexmltoolkit.controls.shell.editor.FavoritesActivityPanel(editorHost);
            case PDF_FOP -> new org.fxt.freexmltoolkit.controls.shell.editor.FopPanel(editorHost);
            case SIGNATURE -> {
                // The panel only picks the action; keystore and form are on the Signature page.
                var nav = new org.fxt.freexmltoolkit.controls.shell.editor.PageNavigator("sig", "SIGNATURE",
                        null, org.fxt.freexmltoolkit.controls.shell.editor.SignatureView.SECTIONS,
                        key -> openSignaturePage().selectAction(
                                org.fxt.freexmltoolkit.controls.shell.editor.SignatureView.Action.fromKey(key)),
                        false);
                signatureNavigator = nav;
                if (signaturePage != null) {
                    signaturePage.setOnActionShown(nav::setCurrent);
                }
                yield nav;
            }
            case FUNDSXML -> new org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlPanel(editorHost);
            case SETTINGS -> {
                // The groups of the Settings page, with a search that filters page and list.
                var page = openSettingsTab();
                var nav = new org.fxt.freexmltoolkit.controls.shell.editor.PageNavigator("settings", "SETTINGS",
                        null, page.sections(), key -> openSettingsTab().showSection(key), true);
                nav.setOnFilter(query -> openSettingsTab().filter(query));
                page.setOnSectionShown(nav::setCurrent);
                settingsNavigator = nav;
                yield nav;
            }
        };
    }

    /**
     * Opens the Settings page (selects the Settings activity and focuses its main-area tab).
     * Used e.g. by the first-start telemetry notice's "Settings…" button.
     */
    public void openSettings() {
        selectionModel.select(Activity.SETTINGS);
        openSettingsTab();
    }

    /**
     * Opens (or re-selects) the Settings page as a tab in the main editor area —
     * the Settings activity's content lives there, not in the narrow side panel.
     */
    private org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel openSettingsTab() {
        if (settingsTab != null && editorHost.containsTab(settingsTab)) {
            editorHost.selectTab(settingsTab);
            return settingsPage;
        }
        var settings = new org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel();
        settingsPage = settings;
        if (settingsNavigator != null) {
            // A reopened page starts with the side panel's current search and mark.
            var filter = settingsNavigator.filterField();
            settings.filter(filter != null ? filter.getText() : "");
            settings.setOnSectionShown(settingsNavigator::setCurrent);
        }
        settings.setManageSchemaCacheAction(this::showSchemaCache);
        settings.setOnSaved(() -> {
            activityBar.refreshLabels();
            reloadPanelPrefs();
            applyToolbarDisplaySettings();
            // Turning the developer feature off hides the "last run" status-bar item.
            if (!org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().isEnabled()) {
                statusLastRun.setText("");
                statusLastRun.setGraphic(null);
            }
        });
        settings.setOnFundsXmlEnabled(() -> fetchFundsXmlContent("settings-enable"));
        settingsTab = editorHost.openToolTab("Settings", "bi-gear", settings);
        return settings;
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
    @FXML public void onNew() { toolbar("new"); newDocument(); }
    @FXML public void onOpen() { toolbar("open"); openFile(); }
    @FXML public void onSave() { toolbar("save"); saveActive(); }
    @FXML public void onSaveAs() { toolbar("save_as"); saveActiveAs(); }
    @FXML public void onSaveAll() { toolbar("save_all"); editorHost.saveAll(); }
    @FXML public void onUndo() { toolbar("undo"); editorHost.undoActive(); }
    @FXML public void onRedo() { toolbar("redo"); editorHost.redoActive(); }
    @FXML public void onFormat() { toolbar("format"); editorHost.formatActive(); }
    @FXML public void onMinify() { toolbar("minify"); editorHost.minifyActive(); }
    // Menu-bar only (Edit → Insert Template…, Tools → Compare / Spreadsheet Converter; UX plan 5.4).
    public void onInsertTemplate() { toolbar("insert_template"); insertTemplate(); }
    public void onCompare() { toolbar("compare"); compareWithFile(); }
    public void onSpreadsheet() { toolbar("spreadsheet"); convertSpreadsheet(); }
    @FXML public void onQueryConsole() { toolbar("query_console"); toggleQueryConsole(); }
    /**
     * Toolbar / Tools → Transform with XSLT…: one way to transform (UX plan 3.2) - opens the
     * Transform panel on the active document, asks for a stylesheet if none is set yet, and
     * runs it there (parameters, output method, OUTPUT dock with Save are all available).
     */
    @FXML public void onTransform() {
        toolbar("transform");
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        var panel = (org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel) sidePanel(Activity.TRANSFORM);
        selectionModel.select(Activity.TRANSFORM);
        setLeftPanelVisible(true);
        panel.setInputOverride(null); // the active document
        if (!panel.hasStylesheet()) {
            panel.onChooseXslt();
        }
        if (panel.hasStylesheet()) {
            panel.transform();
        }
    }
    /**
     * Toolbar arrow / Tools → Debug with XSLT… / Shift+F5: the one way to start the XSLT
     * debugger. With the PDF/FOP activity in front it debugs that panel's XSL-FO stylesheet;
     * with an XSLT document active it debugs that document against the Run target; otherwise
     * it opens the Transform panel on the active document (asking for a stylesheet if none is
     * set) and starts the debugger there.
     */
    /**
     * Run to Cursor: in a running or paused debug session, runs to the caret line of the active
     * stylesheet; otherwise starts the debugger for it (like Run ▾ → Debug Transform) and runs
     * to the caret line instead of pausing on entry.
     */
    public void runToCursor() {
        if (editorHost.runToCursor()) {
            return;
        }
        if (editorActions.activeFileType() == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XSLT
                && editorHost.prepareDebugToCursor()) {
            try {
                editorActions.debugActiveTransform();
            } finally {
                editorHost.setDebugRunToOnStart(null, 0);
            }
        }
    }

    /** Re-run Last Debug (Ctrl/Cmd+Shift+F5): the last debug run again, its input read anew. */
    public void rerunLastDebug() {
        String error = editorHost.rerunLastDebug();
        if (error != null) {
            editorHost.transformOutputPanel().showError(error);
        }
    }

    /**
     * Tools → Debug Configuration…: edits how the active (saved) stylesheet is debugged —
     * input, parameters, output format, pause on entry — saves it per stylesheet and, on
     * Debug, starts the debugger with it.
     */
    public void editDebugConfiguration() {
        var doc = editorHost.getActiveDocument().orElse(null);
        if (doc == null || doc.getPath() == null
                || doc.getFileType() != org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XSLT) {
            return;
        }
        String key = doc.getPath().toAbsolutePath().toString();
        var config = org.fxt.freexmltoolkit.debugger.DebugLaunchConfig.load(key)
                .orElse(org.fxt.freexmltoolkit.debugger.DebugLaunchConfig.defaults());
        var dialog = new org.fxt.freexmltoolkit.controls.shell.editor.debug.DebugConfigurationDialog(
                doc.getDisplayName(), config);
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.ask().ifPresent(result -> {
            result.config().save(key);
            if (result.debug()) {
                String error = editorHost.debugWithConfig(doc.getPath().toFile(), result.config());
                if (error != null) {
                    editorHost.transformOutputPanel().showError(error);
                }
            }
        });
    }

    @FXML public void onDebugXslt() {
        toolbar("debug_xslt");
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        if (selectionModel.getActive() == Activity.PDF_FOP
                && sidePanels.get(Activity.PDF_FOP) instanceof org.fxt.freexmltoolkit.controls.shell.editor.FopPanel fop
                && fop.hasStylesheet()) {
            fop.startDebug();
            return;
        }
        if (editorActions.activeFileType() == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XSLT
                || editorActions.activeFileType() == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XQUERY
                || editorActions.activeFileType() == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.SCHEMATRON) {
            editorActions.debugActiveTransform(); // XQuery → debugActiveQuery, Schematron → debugActiveSchematron
            return;
        }
        var panel = (org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel) sidePanel(Activity.TRANSFORM);
        selectionModel.select(Activity.TRANSFORM);
        setLeftPanelVisible(true);
        panel.setInputOverride(null); // the active document
        if (!panel.hasStylesheet()) {
            panel.onChooseXslt();
        }
        if (panel.hasStylesheet()) {
            panel.startDebug();
        }
    }

    /** Run menu → Debug Transform: debugs the active XSLT document against the Run target. */
    @FXML public void onDebugTransform() {
        toolbar("debug_transform");
        editorActions.debugActiveTransform();
    }

    /** Run menu → Debug Query: debugs the active XQuery against the Run target. */
    @FXML public void onDebugQuery() {
        toolbar("debug_query");
        editorActions.debugActiveQuery();
    }

    /** Run menu → Debug Schematron: debugs the active Schematron (compiled to XSLT) against the Run target. */
    @FXML public void onDebugSchematron() {
        toolbar("debug_schematron");
        editorActions.debugActiveSchematron();
    }

    @FXML public void onRunQuery() { editorActions.runActiveQuery(); }
    @FXML public void onRunTransform() { editorActions.runActiveTransform(); }
    @FXML public void onRunPipeline() { editorActions.runActivePipeline(); }

    /**
     * Primary click on the Run split button: dispatches to whichever run action the
     * active document type supports (XQuery → query, XSLT → transform, XProc → pipeline).
     * The button is disabled for non-runnable types, so falling through is a no-op.
     */
    @FXML public void onRunPrimary() {
        toolbar("run");
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
        } else if (type == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.SCHEMATRON) {
            onDebugSchematron(); // a Schematron's only run here: debugging it against the target
        }
    }
    @FXML public void onSetSchema() { toolbar("set_schema"); setSchema(); }
    @FXML public void onGenerateDocs() { toolbar("generate_docs"); editorActions.generateDocsActive(window()); }
    @FXML public void onTypeEditor() { toolbar("type_editor"); editorActions.openTypeEditorActive(); }
    /** Schema ▾ / Tools → Generate Sample XML… for the active XSD (same as the Schema panel tool). */
    @FXML public void onGenerateSample() { toolbar("generate_sample"); schemaTools().generateSampleXmlForActive(); }

    /** Schema ▾ → Generate XSD from XML: infers a schema from the active XML document. */
    @FXML public void onGenerateXsdFromXml() { toolbar("generate_xsd"); schemaTools().generateXsdFromActive(); }

    /** Schema ▾ → Generate XSD from XML Files…: infers one schema from several XML files. */
    @FXML public void onGenerateXsdBatch() { toolbar("generate_xsd_batch"); schemaTools().generateXsdBatch(); }

    /** The Schema activity panel, whose tools the toolbar reuses (created on first use). */
    private org.fxt.freexmltoolkit.controls.shell.editor.TypeLibraryPanel schemaTools() {
        return (org.fxt.freexmltoolkit.controls.shell.editor.TypeLibraryPanel) sidePanel(Activity.SCHEMA);
    }
    @FXML public void onValidate() { toolbar("validate"); validateActive(); }
    @FXML public void onHelp() { toolbar("help"); showHelpMenu(); }

    /**
     * Shows the Help menu (a copy of the menu bar's Help menu) under the header's Help button;
     * a second call while it is open closes it.
     */
    public void showHelpMenu() {
        if (helpPopup != null && helpPopup.isShowing()) {
            helpPopup.hide();
            return;
        }
        helpPopup = menuBar.createHelpPopup();
        helpPopup.show(helpButton, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /** @return the Help popup last shown by {@link #showHelpMenu()}, or {@code null} (for tests) */
    javafx.scene.control.ContextMenu helpPopup() {
        return helpPopup;
    }
    @FXML public void onToggleTheme() { toolbar("toggle_theme"); toggleTheme(); }
    @FXML public void onSearchPillClicked() { toolbar("search_pill"); openQueryConsole(); }
    @FXML public void onToggleLeftPanel() { setLeftPanelVisible(leftPanelToggle.isSelected()); }
    @FXML public void onToggleInspector() { setInspectorVisible(inspectorToggle.isSelected()); }
    @FXML public void onCollapseLeftPanel() { setLeftPanelVisible(false); }
    @FXML public void onCollapseInspector() { setInspectorVisible(false); }

    /**
     * F5 / Tools → Run: runs the active document when its type is runnable (the Run split
     * button's primary action). For other documents it does nothing - validating is F8,
     * one key per task instead of a silent switch (UX plan 3.1).
     */
    public void executeActive() {
        if (editorHost.getActiveDocument().isEmpty() || actionRun.isDisabled()) {
            return;
        }
        onRunPrimary();
    }

    /** @return whether the active document can be run (query / XSLT / XProc), as the toolbar's Run button */
    public boolean isActiveDocumentRunnable() {
        return editorHost.getActiveDocument().isPresent() && !actionRun.isDisabled();
    }

    /** F11: toggles the shell window's full-screen mode. */
    public void toggleFullScreen() {
        if (window() instanceof javafx.stage.Stage stage) {
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

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
    public void showSearch(boolean replace) {
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
    public void openSearchPanel(boolean replace) {
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
        actionGenerateSample.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.TYPE_EDITOR));
        actionTypeEditor.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.TYPE_EDITOR));
        actionGenerateXsd.setDisable(type != org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XML);
        actionRunQuery.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_QUERY));
        actionRunTransform.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_TRANSFORM));
        actionDebugTransform.setDisable(actionRunTransform.isDisable());
        actionDebugQuery.setDisable(type != org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XQUERY);
        boolean schematron = type == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.SCHEMATRON;
        actionDebugSchematron.setDisable(!schematron);
        actionRunPipeline.setDisable(!org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_PIPELINE));

        // The Target dropdown is hidden (not just disabled) for other document types:
        // unlike the action buttons, an "Input: …" selector label is meaningless there.
        boolean targetable = org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_QUERY)
                || org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_TRANSFORM)
                || org.fxt.freexmltoolkit.controls.shell.editor.EditorActions
                .applicableFor(type, org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction.RUN_PIPELINE)
                || schematron; // the XML a Schematron is debugged against
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
        // Without a document (welcome page, Settings and other tool tabs) the document groups
        // are hidden instead of shown disabled (UX plan 5.3); the Target dropdown keeps its own
        // type-based visibility above.
        for (javafx.scene.Node node : toolbarActions.getChildren()) {
            if (node == queryTargetButton
                    || node.getId() != null && ALWAYS_SHOWN_TOOLBAR_IDS.contains(node.getId())) {
                continue;
            }
            node.setVisible(hasDoc);
            node.setManaged(hasDoc);
        }
        viewSwitchHost.setVisible(hasDoc);
        viewSwitchHost.setManaged(hasDoc);
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
            "action-query-console", "action-set-schema"
    };

    /** Toolbar ids that stay visible without a document (UX plan 5.3: everything else is hidden). */
    private static final java.util.Set<String> ALWAYS_SHOWN_TOOLBAR_IDS = java.util.Set.of("action-new", "action-open");

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
                editorHost.openFile(file.toPath(), false); // loaded into the editor, behind the query
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
        queryTargetButton.setText("Input: " + label);
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
        var doc = editorHost.getActiveDocument();
        if (doc.isEmpty()) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save As");
        doc.get().getFileType().configureSaveChooser(chooser, doc.get().getDisplayName());
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
                long t0 = System.nanoTime();
                String xml = org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner
                        .generate(schema, true, 2, false);
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.sampleGenerated(
                        "skeleton", 1, t0, !xml.startsWith("ERROR:"));
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
            editorHost.openGeneratedDocument(content, type, name,
                    org.fxt.freexmltoolkit.service.telemetry.UsageEvents.SOURCE_NEW);
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
                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.fileChooserFilters());
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

    /** A spreadsheet conversion is running in the background (FX thread only). */
    private boolean spreadsheetConversionRunning;

    private void convertSpreadsheet() {
        if (spreadsheetConversionRunning) {
            org.fxt.freexmltoolkit.util.DialogHelper.showInformation("Spreadsheet Converter",
                    "A conversion is still running", "Wait until it has finished, then start the next one.");
            return;
        }
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
            String format = excel ? (out.getName().toLowerCase().endsWith(".xls") ? "xls" : "xlsx") : "csv";
            Runnable done = beginSpreadsheetConversion("Exporting spreadsheet…");
            long start = System.nanoTime();
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                var result = excel
                        ? org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .exportToExcel(xml, out, settings.config())
                        : org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .exportToCsv(xml, out, settings.delimiter().config(), settings.config());
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.spreadsheetConverted("to_spreadsheet", format,
                        result.telemetryStatus(), start, xml.length(), errorCodeOf(result));
                javafx.application.Platform.runLater(() -> {
                    done.run();
                    reportConversion(result, out, false);
                });
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
            Runnable done = beginSpreadsheetConversion("Importing spreadsheet…");
            long start = System.nanoTime();
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                var result = isExcel
                        ? org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .excelToXml(in, settings.config())
                        : org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .csvToXml(in, settings.delimiter().config(), settings.config());
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.spreadsheetConverted("to_xml",
                        isExcel ? "xlsx" : "csv", result.telemetryStatus(), start, -1, errorCodeOf(result));
                javafx.application.Platform.runLater(() -> {
                    done.run();
                    if (result.isError()) {
                        reportConversion(result, in, true);
                    } else {
                        editorHost.openGeneratedDocument(result.text(),
                                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XML, "Imported.xml");
                    }
                });
            });
        }
    }

    /**
     * Marks a conversion as running: blocks a second one and shows a progress chip in the status
     * bar (a large document takes a while, and nothing else told the user it was still working).
     *
     * @return restores the status bar and releases the block; call on the FX thread
     */
    private Runnable beginSpreadsheetConversion(String label) {
        spreadsheetConversionRunning = true;
        String previousText = statusLastRun.getText();
        javafx.scene.Node previousGraphic = statusLastRun.getGraphic();
        var progress = new javafx.scene.control.ProgressIndicator();
        progress.setPrefSize(12, 12);
        progress.setMaxSize(12, 12);
        statusLastRun.setGraphic(progress);
        statusLastRun.setText(label);
        return () -> {
            spreadsheetConversionRunning = false;
            if (statusLastRun.getGraphic() == progress) { // not replaced by a run badge meanwhile
                statusLastRun.setGraphic(previousGraphic);
                statusLastRun.setText(previousText);
            }
        };
    }

    private static String errorCodeOf(org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner.Result result) {
        return result.isError() ? result.error().getClass().getSimpleName() : null;
    }

    private void reportConversion(org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner.Result result,
                                  java.io.File file, boolean imported) {
        if (!result.isError()) {
            org.fxt.freexmltoolkit.util.DialogHelper.showInformation("Spreadsheet Converter",
                    "Conversion finished", "Done: " + file.getAbsolutePath());
        } else if (result.error() instanceof org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowLimitExceededException limit) {
            org.fxt.freexmltoolkit.util.DialogHelper.showWarning("Spreadsheet Converter",
                    "The document has too many rows for one spreadsheet sheet.",
                    String.format("The export needs %,d rows, but a sheet holds at most %,d."
                            + "%n%nExport as CSV instead - it has no row limit.", limit.getRows(), limit.getMaxRows()));
        } else if (result.error() instanceof org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner.InsufficientMemoryException) {
            org.fxt.freexmltoolkit.util.DialogHelper.showWarning("Spreadsheet Converter",
                    "The document is too large to convert with the available memory.",
                    result.error().getMessage() + "."
                            + "\n\nRaise \"Maximum memory\" in Settings > Memory, close other large documents, or export as CSV.");
        } else if (!imported && result.error() instanceof org.xml.sax.SAXParseException parse) {
            // A malformed document is an input problem, not a failure of the converter: say where
            // it is instead of raising an error dialog (and an error report) with a stack trace.
            org.fxt.freexmltoolkit.util.DialogHelper.showWarning("Spreadsheet Converter",
                    "The document is not well-formed XML, so it cannot be converted.",
                    "Line " + parse.getLineNumber() + ", column " + parse.getColumnNumber() + ": "
                            + parse.getMessage()
                            + "\n\nFix the document (the Validation panel lists the problem), then convert again.");
        } else {
            org.fxt.freexmltoolkit.util.DialogHelper.showActionError("Spreadsheet Converter",
                    "The conversion could not be completed.",
                    imported
                            ? "Check that the spreadsheet is a valid .xlsx/.csv file and not open in another program, then try again."
                            : "Check that the document is well-formed and the target file is writable (not open elsewhere), then try again.",
                    // the exception (not just its message): class + stack reach the details and the error report
                    result.error());
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
                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.fileChooserFilters());
        java.io.File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.openDiffWithFile(file);
        }
    }

    private void setSchema() {
        var doc = editorHost.getActiveDocument().orElse(null);
        if (doc == null) {
            return;
        }
        boolean json = doc.getFileType() == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.JSON;
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(json ? "Select JSON Schema" : "Select XSD Schema");
        chooser.getExtensionFilters().add(json
                ? new javafx.stage.FileChooser.ExtensionFilter("JSON Schema", "*.json")
                : new javafx.stage.FileChooser.ExtensionFilter("XSD Schema", "*.xsd"));
        java.io.File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.setSchemaForActiveDocument(file);
            editorHost.openFile(file.toPath(), false); // loaded into the editor, behind the document
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
        statusLastRun.getStyleClass().add("fxt-status-badge");
        org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().addListener(
                stats -> javafx.application.Platform.runLater(() -> showLastRunBadge(stats)));

        // The schema indicator tracks the active document's schema-binding lifecycle (detecting /
        // ready / none / error) — XSD for the XML family (IntelliSense + validation), JSON Schema
        // for JSON documents (validation). For plain text a schema label is meaningless, so it is
        // blanked and the label collapses out of the bar entirely.
        // A listener (not a binding) because text, icon, tooltip and style class change together.
        editorHost.activeSchemaStatusProperty().addListener((obs, o, n) -> updateSchemaStatusIndicator());
        editorHost.activeSchemaProperty().addListener((obs, o, n) -> updateSchemaStatusIndicator());
        editorHost.activeSchemaSourceProperty().addListener((obs, o, n) -> updateSchemaStatusIndicator());
        editorHost.activeTabProperty().addListener((obs, o, n) -> updateSchemaStatusIndicator());
        updateSchemaStatusIndicator();
        statusSchema.managedProperty().bind(statusSchema.textProperty().isNotEmpty());
        statusSchema.visibleProperty().bind(statusSchema.textProperty().isNotEmpty());
        // The schema indicator doubles as the binding entry point (VS-Code style): clicking it
        // picks a schema (XSD or JSON Schema, by document type) and binds it via
        // setSchemaForActiveDocument. The drop target is installed once with the union of both
        // extensions (FileDropSupport has no uninstall); the consumer binds only when the dropped
        // kind matches the active document's kind and silently ignores the mismatch.
        statusSchema.setOnMouseClicked(e -> {
            // An XSD document is a schema itself; its indicator only reports the version.
            if (!isActiveDocumentXsd()) {
                setSchema();
            }
        });
        java.util.List<String> schemaDropExtensions = java.util.stream.Stream.concat(
                org.fxt.freexmltoolkit.service.DragDropService.XSD_EXTENSIONS.stream(),
                org.fxt.freexmltoolkit.service.DragDropService.JSON_SCHEMA_EXTENSIONS.stream()).toList();
        FileDropSupport.install(statusSchema, schemaDropExtensions, file -> {
            var doc = editorHost.getActiveDocument().orElse(null);
            if (doc == null) {
                return;
            }
            boolean jsonDoc = doc.getFileType() == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.JSON;
            boolean jsonSchema = file.getName().toLowerCase().endsWith(".json");
            if (jsonDoc == jsonSchema) {
                editorHost.setSchemaForActiveDocument(file);
                editorHost.openFile(file.toPath(), false); // loaded into the editor, behind the document
            }
        });
    }

    /** All schema-status style classes; removed before the current one is (re-)applied. */
    private static final java.util.List<String> SCHEMA_STATUS_CLASSES = java.util.List.of(
            "fxt-status-schema-loading", "fxt-status-schema-ready",
            "fxt-status-schema-none", "fxt-status-schema-error");

    /** Refreshes the status bar's schema indicator from the active document's state. */
    private void updateSchemaStatusIndicator() {
        statusSchema.getStyleClass().removeAll(SCHEMA_STATUS_CLASSES);
        var doc = editorHost.getActiveDocument().orElse(null);
        if (doc == null || !isSchemaAware(doc.getFileType())) {
            statusSchema.setText("");
            statusSchema.setGraphic(null);
            statusSchema.setTooltip(null);
            return;
        }
        if (doc.getFileType() == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XSD) {
            // The document IS a schema: "No XSD" would be misleading, show its XSD version instead.
            statusSchema.setCursor(javafx.scene.Cursor.DEFAULT);
            statusSchema.setText(xsdDocumentStatusText(editorHost.getActiveText().orElse("")));
            IconifyIcon icon = new IconifyIcon("bi-diagram-3");
            icon.setIconSize(12);
            statusSchema.setGraphic(icon);
            statusSchema.getStyleClass().add("fxt-status-schema-ready");
            statusSchema.setTooltip(new javafx.scene.control.Tooltip(
                    "This document is an XSD schema. The version comes from vc:minVersion on xs:schema;"
                            + " without it the schema is treated as XSD 1.0 (1.1 features still validate)."));
            return;
        }
        statusSchema.setCursor(javafx.scene.Cursor.HAND);
        String kind = schemaKindLabel(doc.getFileType());
        var status = editorHost.activeSchemaStatusProperty().get();
        java.io.File schema = editorHost.activeSchemaProperty().get();
        var source = editorHost.activeSchemaSourceProperty().get();
        String detail = editorHost.activeSchemaSourceDetailProperty().get();
        statusSchema.setText(schemaStatusText(status, schema, kind, source));
        IconifyIcon icon = new IconifyIcon(schemaStatusIcon(status, source));
        icon.setIconSize(12);
        statusSchema.setGraphic(icon);
        statusSchema.getStyleClass().add(schemaStatusStyleClass(status));
        statusSchema.setTooltip(new javafx.scene.control.Tooltip(schemaStatusTooltip(status, schema, kind, source, detail)));
    }

    /** Matches {@code vc:minVersion="1.1"} (any prefix) on the schema root. */
    private static final java.util.regex.Pattern XSD_MIN_VERSION =
            java.util.regex.Pattern.compile("\\b[\\w.-]*:?minVersion\\s*=\\s*[\"'](1\\.[01])[\"']");

    /**
     * @return the indicator text for an XSD document: its XSD version from {@code vc:minVersion}
     *         ({@code "XSD 1.1 schema"}), else {@code "XSD 1.0 schema"} (package-private for unit tests)
     */
    static String xsdDocumentStatusText(String schemaText) {
        String head = schemaText.length() > 8192 ? schemaText.substring(0, 8192) : schemaText;
        java.util.regex.Matcher m = XSD_MIN_VERSION.matcher(head);
        return "XSD " + (m.find() ? m.group(1) : "1.0") + " schema";
    }

    private boolean isActiveDocumentXsd() {
        return editorHost.getActiveDocument()
                .map(d -> d.getFileType() == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XSD)
                .orElse(false);
    }

    /** @return the indicator's schema-kind label for a file type (package-private for unit tests). */
    static String schemaKindLabel(org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType type) {
        return type == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.JSON ? "JSON Schema" : "XSD";
    }

    /** @return the indicator label text for a schema status (package-private for unit tests). */
    static String schemaStatusText(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status,
            java.io.File xsd) {
        return schemaStatusText(status, xsd, "XSD");
    }

    /** Kind-aware variant: {@code kind} is {@code "XSD"} or {@code "JSON Schema"}. */
    static String schemaStatusText(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status,
            java.io.File schema, String kind) {
        return schemaStatusText(status, schema, kind,
                org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource.DECLARED);
    }

    /**
     * Source-aware variant: a READY binding resolved through a catalog, a Schema Library
     * mapping, or a manual pick gets a {@code (catalog)}/{@code (library)}/{@code (manual)}
     * suffix so the user can tell a declared binding from a resolved one at a glance.
     */
    static String schemaStatusText(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status,
            java.io.File schema, String kind,
            org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource source) {
        String text = switch (status) {
            case LOADING -> "Detecting " + kind + "…";
            case READY -> schema != null ? kind + ": " + schema.getName() : kind + " ready";
            case ERROR -> kind + " error";
            case NONE -> "No " + kind;
        };
        if (status == org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus.READY && schema != null) {
            text += schemaSourceSuffix(source);
        }
        return text;
    }

    /** @return the {@code " (catalog)"}/{@code " (library)"}/{@code " (manual)"} status-text suffix, or {@code ""}. */
    private static String schemaSourceSuffix(
            org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource source) {
        return switch (source) {
            case CATALOG -> " (catalog)";
            case LIBRARY -> " (library)";
            case MANUAL -> " (manual)";
            case DECLARED, NONE -> "";
        };
    }

    /** @return the {@code bi-*} icon literal for a schema status (package-private for unit tests). */
    static String schemaStatusIcon(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status) {
        return schemaStatusIcon(status, org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource.DECLARED);
    }

    /**
     * Source-aware variant: a READY binding resolved through a catalog or the Schema Library
     * gets its own icon ({@code bi-journal-bookmark} / {@code bi-collection}) instead of the
     * plain checkmark, so the resolution path is visible without opening the tooltip.
     */
    static String schemaStatusIcon(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status,
            org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource source) {
        if (status == org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus.READY) {
            if (source == org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource.CATALOG) {
                return "bi-journal-bookmark";
            }
            if (source == org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource.LIBRARY) {
                return "bi-collection";
            }
        }
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
        return schemaStatusTooltip(status, xsd, "XSD");
    }

    /**
     * Kind-aware variant: XSD tooltips explain IntelliSense availability, JSON Schema
     * tooltips explain validation (JSON has no schema-driven IntelliSense).
     */
    static String schemaStatusTooltip(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status,
            java.io.File schema, String kind) {
        return schemaStatusTooltip(status, schema, kind,
                org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource.DECLARED, null);
    }

    /**
     * Source-aware variant: a READY binding resolved through a catalog, a Schema Library
     * mapping, or a manual pick gets an extra explanatory line plus the absolute schema
     * path appended to the base tooltip.
     *
     * @param source the binding's resolution path
     * @param detail for {@link org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource#CATALOG}
     *               the resolved catalog target, for
     *               {@link org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource#LIBRARY}
     *               the matched namespace/root element; ignored otherwise, may be {@code null}
     */
    static String schemaStatusTooltip(org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus status,
            java.io.File schema, String kind,
            org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource source, String detail) {
        String tooltip;
        if ("JSON Schema".equals(kind)) {
            tooltip = switch (status) {
                case LOADING -> "Detecting the declared JSON Schema — schema validation will be available shortly";
                case READY -> "Schema validation is active (schema: " + (schema != null ? schema.getName() : "bound")
                        + "). Click to bind a different JSON Schema";
                case ERROR -> "The declared JSON Schema could not be loaded — validation is well-formedness only. "
                        + "Click to bind a JSON Schema manually";
                case NONE -> "No JSON Schema is bound — validation is well-formedness only. "
                        + "Click to bind a JSON Schema";
            };
        } else {
            tooltip = switch (status) {
                case LOADING -> "Detecting and parsing the linked XSD schema — IntelliSense will be available shortly";
                case READY -> "IntelliSense is available (schema: " + (schema != null ? schema.getName() : "bound")
                        + "). Click to bind a different XSD";
                case ERROR -> "The linked XSD could not be loaded — IntelliSense is unavailable. "
                        + "Click to bind an XSD manually";
                case NONE -> "No XSD schema is linked — IntelliSense is limited. Click to bind an XSD";
            };
        }
        if (status == org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus.READY && schema != null) {
            String sourceLine = schemaSourceTooltipLine(source, detail);
            if (sourceLine != null) {
                tooltip += "\n" + sourceLine + "\nPath: " + schema.getAbsolutePath();
            }
        }
        return tooltip;
    }

    /** @return the extra tooltip line explaining how a READY binding was resolved, or {@code null} for none. */
    private static String schemaSourceTooltipLine(
            org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource source, String detail) {
        return switch (source) {
            case CATALOG -> "Resolved through an XML catalog: " + (detail != null ? detail : "unknown target");
            case LIBRARY -> "Mapped by the Schema Library"
                    + (detail != null && !detail.isBlank() ? " (" + detail + ")" : "");
            case MANUAL -> "Bound manually";
            case DECLARED, NONE -> null;
        };
    }

    /**
     * @return {@code true} when the file type can carry a schema binding — an XSD for the
     *         XML family, a JSON Schema for JSON; {@code false} for plain text, where the
     *         status bar's schema indicator is meaningless.
     */
    private static boolean isSchemaAware(org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType type) {
        return switch (type) {
            case XML, XSD, XSLT, SCHEMATRON, JSON -> true;
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
