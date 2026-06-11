package org.fxt.freexmltoolkit.controls.shell;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * Skeleton of the new Unified shell (UI rebuild Phase 2): the persistent frame
 * that every activity will plug into.
 * <pre>
 *   ┌────┬──────────────┬───────────────────┬──────────────┐
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

    private static final double SIDE_PANEL_WIDTH = 260;

    private final ActivitySelectionModel selectionModel = new ActivitySelectionModel();
    private final ActivityBar activityBar = new ActivityBar(selectionModel);
    private final StackPane sidePanelHost = new StackPane();
    /** Cached so the editor toolbar "Validate" action can drive the same panel. */
    private org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel validationPanel;
    /** The Settings page tab in the main editor area (reused across activity selections). */
    private javafx.scene.control.Tab settingsTab;
    private final org.fxt.freexmltoolkit.controls.shell.editor.EditorHost editorHost =
            new org.fxt.freexmltoolkit.controls.shell.editor.EditorHost();
    private final org.fxt.freexmltoolkit.controls.unified.UnifiedSearchBar searchBar =
            new org.fxt.freexmltoolkit.controls.unified.UnifiedSearchBar();

    /** Editor-level document actions (Validate / Transform / Generate Docs / Open Type Editor). */
    private final org.fxt.freexmltoolkit.controls.shell.editor.EditorActions editorActions =
            new org.fxt.freexmltoolkit.controls.shell.editor.EditorActions(editorHost);

    /** Long-lived Query Console (XPath/XQuery against the active document); docked as a collapsible bottom strip. */
    private final org.fxt.freexmltoolkit.controls.shell.editor.QueryConsole queryConsole =
            new org.fxt.freexmltoolkit.controls.shell.editor.QueryConsole(editorHost);
    /** Vertical split: item 0 = work area (grows), item 1 = {@link #queryConsole} when shown. */
    private SplitPane workSplit;

    /** Property keys for the persisted side-panel visibility (editable in Settings). */
    private static final String LEFT_PANEL_KEY = "shell.leftPanel.visible";
    private static final String INSPECTOR_KEY = "shell.inspector.visible";

    /** User collapse state for the two side panels (persisted across restarts; default open). */
    private boolean leftPanelOpen = loadPanelPref(LEFT_PANEL_KEY);
    private boolean inspectorOpen = loadPanelPref(INSPECTOR_KEY);
    /** Wrappers added to the work HBox (each carries an in-edge collapse chevron). */
    private StackPane leftPanelWrapper;
    private StackPane inspectorWrapper;
    /** Toolbar toggles that re-open a collapsed panel (same mechanism for both sides). */
    private javafx.scene.control.ToggleButton leftPanelToggle;
    private javafx.scene.control.ToggleButton inspectorToggle;
    /** Recomputes panel/toggle visibility; combines document-open state with collapse state. */
    private Runnable chromeUpdater;

    /** Type-gated document-action toolbar buttons (created in {@link #buildEditorToolbar()}). */
    private javafx.scene.control.Button actionValidate;
    private javafx.scene.control.Button actionTransform;
    private javafx.scene.control.Button actionGenerateDocs;
    private javafx.scene.control.Button actionTypeEditor;

    private final Label statusPosition = statusLabel("Ln 1, Col 1");
    private final Label statusChars = statusLabel("");
    private final Label statusType = statusLabel("");
    private final Label statusSchema = statusLabel("No XSD");
    private final Label statusFile = statusLabel("No file open");
    private final Label statusMemory = statusLabel("");

    public UnifiedShellView() {
        getStyleClass().add("fxt-shell");

        setLeft(activityBar);
        setCenter(buildCenter());
        setBottom(buildStatusBar());

        // React to activity changes: swap the side panel. Choosing an activity also reveals
        // the (possibly collapsed) left panel — selecting one whose panel stays hidden would
        // be pointless. The initial call above does NOT reveal, so a persisted collapsed
        // state survives startup.
        showSidePanelFor(selectionModel.getActive());
        selectionModel.activeProperty().addListener((obs, oldV, newV) -> {
            setLeftPanelVisible(true);
            showSidePanelFor(newV);
        });

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
            return;
        }
        Activity.fromId(key).ifPresent(selectionModel::select);
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
     * Builds the shell center: a vertical {@link SplitPane} whose top item is the
     * work area (activity side panel | editor | inspector) and whose bottom item is
     * the collapsible {@link #queryConsole}. The console is removed from the split's
     * items while hidden, so the default layout is unchanged on startup.
     *
     * @return the center region for the shell {@link BorderPane}
     */
    private Region buildCenter() {
        Region work = buildWorkArea();

        queryConsole.setMinHeight(180);
        queryConsole.setPrefHeight(220);

        workSplit = new SplitPane(work);
        workSplit.setOrientation(Orientation.VERTICAL);
        workSplit.getStyleClass().add("fxt-work-split");
        // The work area should keep the extra space when the window resizes.
        SplitPane.setResizableWithParent(work, true);
        return workSplit;
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

    private Region buildWorkArea() {
        sidePanelHost.getStyleClass().add("fxt-side-panel");
        sidePanelHost.setPrefWidth(SIDE_PANEL_WIDTH);
        sidePanelHost.setMinWidth(SIDE_PANEL_WIDTH);

        Region editorCenter = buildEditorCenter();
        HBox.setHgrow(editorCenter, Priority.ALWAYS);

        Region inspector = new org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel(editorHost);

        // Wrap both side panels so each carries a discreet in-edge collapse chevron
        // (left: "<<", right: ">>") — same visual mechanism on both sides.
        leftPanelWrapper = wrapCollapsible(sidePanelHost, true);
        inspectorWrapper = wrapCollapsible(inspector, false);

        HBox work = new HBox(leftPanelWrapper, editorCenter, inspectorWrapper);
        work.getStyleClass().add("fxt-work-area");

        // Visibility combines two concerns:
        //  - the Welcome / Dashboard is full-width (Figma): while no document is open, both
        //    side panels are hidden so the dashboard uses the whole work area;
        //  - the user collapse state (persisted) hides a panel even when a document is open.
        chromeUpdater = () -> {
            boolean hasDocument = !editorHost.isEmpty();
            boolean showLeft = hasDocument && leftPanelOpen;
            boolean showRight = hasDocument && inspectorOpen;
            leftPanelWrapper.setVisible(showLeft);
            leftPanelWrapper.setManaged(showLeft);
            inspectorWrapper.setVisible(showRight);
            inspectorWrapper.setManaged(showRight);
            // The re-open toggles are only meaningful while a document is open.
            if (leftPanelToggle != null) {
                leftPanelToggle.setDisable(!hasDocument);
                leftPanelToggle.setSelected(leftPanelOpen);
            }
            if (inspectorToggle != null) {
                inspectorToggle.setDisable(!hasDocument);
                inspectorToggle.setSelected(inspectorOpen);
            }
        };
        chromeUpdater.run();
        editorHost.getOpenDocuments().addListener(
                (javafx.collections.ListChangeListener<org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument>)
                        c -> chromeUpdater.run());
        return work;
    }

    /**
     * Wraps a side-panel region in a {@link StackPane} that overlays a discreet collapse
     * chevron in the panel's inner corner. The wrapped content keeps its own preferred
     * width and layout; only the chevron is added.
     *
     * @param content  the panel content (keeps its own width)
     * @param leftSide {@code true} for the left side panel (chevron "<<", top-right),
     *                 {@code false} for the inspector (chevron ">>", top-left)
     * @return the wrapper to place in the work {@link HBox}
     */
    private StackPane wrapCollapsible(Region content, boolean leftSide) {
        javafx.scene.control.Button chevron = new javafx.scene.control.Button();
        chevron.getStyleClass().addAll("fxt-tool-button", "fxt-panel-collapse");
        IconifyIcon icon = new IconifyIcon(leftSide ? "bi-chevron-double-left" : "bi-chevron-double-right");
        icon.setIconSize(14);
        chevron.setGraphic(icon);
        chevron.setFocusTraversable(false);
        chevron.setTooltip(new javafx.scene.control.Tooltip(
                leftSide ? "Collapse the side panel" : "Collapse the Properties panel"));
        chevron.setOnAction(e -> {
            if (leftSide) {
                setLeftPanelVisible(false);
            } else {
                setInspectorVisible(false);
            }
        });

        StackPane wrapper = new StackPane(content, chevron);
        StackPane.setAlignment(chevron, leftSide ? Pos.TOP_RIGHT : Pos.TOP_LEFT);
        StackPane.setMargin(chevron, new javafx.geometry.Insets(4));
        return wrapper;
    }

    /** Shows/hides the left side panel and persists the choice (default open). */
    public void setLeftPanelVisible(boolean open) {
        leftPanelOpen = open;
        savePanelPref(LEFT_PANEL_KEY, open);
        if (chromeUpdater != null) {
            chromeUpdater.run();
        }
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
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel(editorHost));
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
     * The editor center: the editor toolbar plus the {@link EditorHost}. The host
     * shows its own welcome empty-state (quick actions + recent files) while no
     * document is open, so the toolbar stays available at all times.
     */
    private Region buildEditorCenter() {
        Region toolbar = buildEditorToolbar();
        searchBar.hide(); // shown on Ctrl+F / Ctrl+H
        // PROBLEMS panel below the editor (Figma node 42:3); hides itself while empty.
        var problemsPanel = new org.fxt.freexmltoolkit.controls.shell.editor.ProblemsPanel(editorHost);
        VBox editorArea = new VBox(searchBar, toolbar, editorHost, problemsPanel);
        VBox.setVgrow(editorHost, Priority.ALWAYS);
        editorArea.getStyleClass().addAll("fxt-editor-area", "fxt-editor-center");
        return editorArea;
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

    private Region buildEditorToolbar() {
        IconifyIcon validateIcon = new IconifyIcon("bi-check2-circle");
        validateIcon.setIconSize(15);
        javafx.scene.control.Button validate = new javafx.scene.control.Button("Validate", validateIcon);
        validate.setId("doc-action-validate");
        validate.getStyleClass().addAll("fxt-tool-button", "fxt-validate-button");
        validate.setTooltip(new javafx.scene.control.Tooltip(
                "Validate the document (well-formedness, or against the bound XSD) — F8"));
        validate.setOnAction(e -> validateActive());
        actionValidate = validate;

        // Editor-level document actions (type-gated): run per-document operations without
        // switching the Activity Bar; results open as the shell's standard tool tabs.
        actionTransform = documentActionButton("doc-action-transform", "bi-arrow-left-right",
                "Transform with XSLT… (choose a stylesheet)",
                () -> editorActions.transformActiveWithXslt(window()));
        actionGenerateDocs = documentActionButton("doc-action-generate-docs", "bi-file-earmark-text",
                "Generate Documentation… (HTML / PDF / Word) for the active XSD",
                () -> editorActions.generateDocsActive(window()));
        actionTypeEditor = documentActionButton("doc-action-type-editor", "bi-braces-asterisk",
                "Open Type Editor… (pick a named type from the active XSD)",
                editorActions::openTypeEditorActive);

        // The icon actions live in a wrapping FlowPane that spans the full toolbar width
        // (between the left panel toggle and the view switch): when the editor area is narrow
        // they wrap onto a second row so EVERY action stays visible and directly clickable (no
        // overflow chevron that can hide trailing buttons — Spreadsheet, Query Console, …).
        // The prominent Validate button is the band's first item, so it wraps with the rest
        // instead of leaving an empty vertical margin beside a taller wrapped flow.
        javafx.scene.layout.FlowPane actions = new javafx.scene.layout.FlowPane(
                validate,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                toolButton("action-new", "bi-file-earmark-plus", "New (Ctrl+N)", this::newDocument),
                toolButton("action-open", "bi-folder2-open", "Open (Ctrl+O)", this::openFile),
                toolButton("action-save", "bi-save", "Save (Ctrl+S)", this::saveActive),
                toolButton("action-save-as", "bi-save2", "Save As (Ctrl+Shift+S)", this::saveActiveAs),
                toolButton("action-save-all", "bi-files", "Save All", editorHost::saveAll),
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                toolButton("action-undo", "bi-arrow-counterclockwise", "Undo (Ctrl+Z)", editorHost::undoActive),
                toolButton("action-redo", "bi-arrow-clockwise", "Redo (Ctrl+Y)", editorHost::redoActive),
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                toolButton("action-format", "bi-text-indent-left", "Format", editorHost::formatActive),
                toolButton("action-minify", "bi-arrows-collapse", "Minify", editorHost::minifyActive),
                toolButton("action-insert-template", "bi-puzzle", "Insert Template…", this::insertTemplate),
                toolButton("action-compare", "bi-layout-split", "Compare with File…", this::compareWithFile),
                toolButton("action-spreadsheet", "bi-table",
                        "Spreadsheet Converter… (Excel / CSV ↔ XML)", this::convertSpreadsheet),
                toolButton("action-query-console", "bi-terminal",
                        "Query Console (XPath/XQuery)  Ctrl+Shift+X", this::toggleQueryConsole),
                actionTransform, actionGenerateDocs, actionTypeEditor,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                toolButton("action-set-schema", "bi-diagram-3",
                        "Set XSD Schema… (IntelliSense & validation)", this::setSchema));
        actions.getStyleClass().add("fxt-editor-actionbar");
        actions.setHgap(2);
        actions.setVgap(2);
        actions.setRowValignment(javafx.geometry.VPos.CENTER);
        actions.setMinWidth(0);
        // Take the slack between the left controls and the right cluster, wrapping within it.
        HBox.setHgrow(actions, Priority.ALWAYS);

        // Type-gate the document-actions group against the active document's file type.
        refreshDocumentActionGating();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshDocumentActionGating());

        // Re-open toggles for the collapsed side panels — same mechanism on both sides
        // (recognition value). They mirror the panels' open state and sit at the toolbar edges.
        leftPanelToggle = panelToggle("toggle-left-panel", "bi-chevron-double-right",
                "Show / hide the side panel", true);
        inspectorToggle = panelToggle("toggle-inspector", "bi-chevron-double-left",
                "Show / hide the Properties panel", false);
        // Sync the toggles' selected/disabled state with the current panel/document state.
        if (chromeUpdater != null) {
            chromeUpdater.run();
        }

        // Layout: left panel toggle | full-width action flow (grows) | inspector toggle. The
        // view-mode switch now lives on the tab header (see EditorHost), and identity/status
        // (file type, bound XSD) lives in the bottom status bar, so the toolbar is a pure action
        // band that uses the entire available width — single-row at the 1512 px MacBook width.
        HBox bar = new HBox(4, leftPanelToggle, actions, inspectorToggle);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("fxt-editor-toolbar");
        return bar;
    }

    private void newDocument() {
        editorHost.newDocument(org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XML);
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
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(ok
                ? javafx.scene.control.Alert.AlertType.INFORMATION
                : javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Spreadsheet Converter");
        alert.setHeaderText(null);
        alert.setContentText(ok ? "Done: " + file.getAbsolutePath() : result);
        alert.showAndWait();
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

    private javafx.scene.control.Button toolButton(String icon, String tooltip, Runnable action) {
        javafx.scene.control.Button button = new javafx.scene.control.Button();
        button.getStyleClass().add("fxt-tool-button");
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        button.setGraphic(graphic);
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }

    /** {@link #toolButton(String, String, Runnable)} with a stable {@code id} for tests/automation. */
    private javafx.scene.control.Button toolButton(String id, String icon, String tooltip, Runnable action) {
        javafx.scene.control.Button button = toolButton(icon, tooltip, action);
        button.setId(id);
        return button;
    }

    /**
     * Builds a side-panel re-open toggle button (identical style for both sides). The toggle
     * reflects the panel's open state; its action shows/hides the matching panel.
     *
     * @param id       stable id for tests
     * @param icon     the chevron literal (reveal direction: left panel ">>", inspector "<<")
     * @param tooltip  the button tooltip
     * @param leftSide {@code true} drives the left side panel, {@code false} the inspector
     * @return the configured toggle button
     */
    private javafx.scene.control.ToggleButton panelToggle(
            String id, String icon, String tooltip, boolean leftSide) {
        javafx.scene.control.ToggleButton button = new javafx.scene.control.ToggleButton();
        button.setId(id);
        button.getStyleClass().addAll("fxt-tool-button", "fxt-panel-toggle");
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        button.setGraphic(graphic);
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(e -> {
            if (leftSide) {
                setLeftPanelVisible(button.isSelected());
            } else {
                setInspectorVisible(button.isSelected());
            }
        });
        return button;
    }

    /** Builds a type-gated document-action toolbar button with a stable {@code id} for tests. */
    private javafx.scene.control.Button documentActionButton(
            String id, String icon, String tooltip, Runnable action) {
        javafx.scene.control.Button button = toolButton(icon, tooltip, action);
        button.setId(id);
        return button;
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

    /** @return the editor host (for future toolbar / inspector wiring). */
    public org.fxt.freexmltoolkit.controls.shell.editor.EditorHost getEditorHost() {
        return editorHost;
    }

    private Region buildStatusBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("fxt-status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

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

        // The bound XSD (relocated from the editor toolbar) tracks the active document's schema —
        // but only for schema-aware (XML-family) documents. For JSON / plain text an "XSD" label
        // is meaningless, so it is blanked and the label collapses out of the bar entirely.
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
        // The XSD indicator doubles as the binding entry point (VS-Code style): clicking
        // it picks an XSD and binds it to the active document via setSchemaForActiveDocument.
        statusSchema.setId("status-schema");
        statusSchema.setTooltip(new javafx.scene.control.Tooltip(
                "Click to bind an XSD schema to this document (IntelliSense & validation)"));
        statusSchema.setCursor(javafx.scene.Cursor.HAND);
        statusSchema.setOnMouseClicked(e -> setSchema());

        bar.getChildren().addAll(
                statusPosition,
                statusChars,
                statusType,
                statusSchema,
                statusLabel("UTF-8"),
                spacer,
                statusMemory,
                statusFile);
        return bar;
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

    private Label statusLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-status-item");
        return label;
    }

    /** @return the shell's activity selection model (for future host wiring). */
    public ActivitySelectionModel getSelectionModel() {
        return selectionModel;
    }
}
