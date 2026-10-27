package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * The Explorer activity side panel, laid out after the Figma mockup
 * "Redesign · Unified Editor (Light)" (node 28:48): a header row with flat icon
 * actions (new file, open folder, refresh, ⋮), the OPEN EDITORS list (active
 * document highlighted, dirty dot on the right), the workspace file tree headed
 * by the workspace folder's name, and the RECENT files list. Drives the
 * {@link EditorHost} and reuses {@link PropertiesService} for recent files.
 */
public class ExplorerPanel extends VBox {

    private final EditorHost editorHost;
    private final PropertiesService propertiesService = resolvePropertiesService();

    /** Persisted height (px) of the RECENT pane, set by dragging its grip. */
    static final String FAV_RECENT_HEIGHT_KEY = "explorer.favRecent.height";
    static final String TOOLS_COLLAPSED_KEY = "explorer.tools.collapsed";
    static final double FAV_RECENT_DEFAULT_HEIGHT = 240;
    static final double FAV_RECENT_MIN_HEIGHT = 96;
    static final double FAV_RECENT_MAX_HEIGHT = 2000;
    /** Height kept free above the pane while dragging (header, section headers, a few tree rows). */
    private static final double FAV_RECENT_RESERVED_HEIGHT = 200;
    private final ObservableList<File> recentFiles = FXCollections.observableArrayList();
    @FXML private ListView<File> recentList;
    /** OPEN EDITORS scrolls inside a capped height (about six rows). */
    @FXML private javafx.scene.control.ScrollPane openEditorsScroll;
    /**
     * OPEN EDITORS is a plain VBox of rows, not a virtualized ListView: the list is small, and a
     * ListView populated after construction (via the open-documents listener) while the panel was
     * built hidden failed to render its cell content in the full shell. Rows always render.
     */
    @FXML private VBox openEditorsBox;
    private final WorkspaceTree workspace = new WorkspaceTree(this::openWorkspaceFile);
    private final Label workspaceTitle = new Label("WORKSPACE");
    @FXML private VBox toolsBody;
    @FXML private PanelActionList fileActions;
    @FXML private javafx.scene.layout.Region favRecentGrip;
    @FXML private VBox favRecentPane;
    /** Sticky stylesheet for the Explorer's one-click transform; shared via the recent-XSLT store. */
    private File currentXslt;
    /** Sticky Schematron for the Explorer's one-click validation; shared via the recent-Schematron store. */
    private File currentSchematron;
    /** Shell-wired: (files, schematron) → run validation in the Validation activity. */
    private java.util.function.BiConsumer<java.util.List<File>, File> schematronValidateAction;

    /** Loads the layout from {@code /pages/panels/ExplorerPanel.fxml} and wires the data-driven parts. */
    public ExplorerPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/ExplorerPanel.fxml");

        // --- one-click transform / Schematron validation: a context menu on the selected files
        // (UX plan 3.1/3.2) instead of picker rows in TOOLS.
        workspace.setFileContextMenu(buildFileContextMenu());

        // --- RECENT -----------------------------------------------------------
        recentList.setItems(recentFiles);
        recentList.setCellFactory(lv -> new RecentFileCell());
        recentList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.isFile()) {
                java.io.File file = newV;
                javafx.application.Platform.runLater(() -> editorHost.openFile(file.toPath()));
            }
        });

        // --- collapsible section headers, each in front of the content it toggles ----
        // Panel schema (UX plan 2.1/2.2): the files the panel is opened for come first
        // (OPEN EDITORS, WORKSPACE); the TOOLS sit below them, collapsed by default.
        HBox openHeader = SidePanelLayout.sectionHeader(new Label("OPEN EDITORS"), openEditorsScroll, openEditorsBox);
        openHeader.setId("explorer-open-editors-header");
        workspaceTitle.getStyleClass().add("fxt-sp-section-label");
        workspace.setOnOpenFolder(this::onChooseFolder);
        HBox workspaceHeader = SidePanelLayout.sectionHeader(workspaceTitle, workspace);
        workspaceHeader.setId("explorer-workspace-header");
        VBox.setVgrow(workspace, Priority.ALWAYS);
        HBox toolsHeader = SidePanelLayout.sectionHeader(loadToolsCollapsed(), new Label("TOOLS"), toolsBody);
        toolsHeader.setId("explorer-tools-header");
        toolsBody.visibleProperty().addListener((obs, oldV, visible) -> saveToolsCollapsed(!visible));
        getChildren().add(getChildren().indexOf(openEditorsScroll), openHeader);
        getChildren().addAll(getChildren().indexOf(openEditorsScroll) + 1, java.util.List.of(workspaceHeader, workspace));
        getChildren().add(getChildren().indexOf(toolsBody), toolsHeader);

        // RECENT pinned to the bottom, resizable by dragging the grip above it. Favorites are
        // managed in the Favorites activity and used via the star menus (UX plan 3.4). A fixed,
        // user-chosen height: the workspace tree above (vgrow) gives way first, so the lists are
        // never squeezed to an unreadable sliver on shorter (e.g. scaled HiDPI) screens.
        favRecentPane.setPrefHeight(clampFavRecentHeight(loadFavRecentHeight()));
        installResizeGrip(favRecentGrip, favRecentPane);

        // Refresh RECENT as documents open and keep OPEN EDITORS in sync (rebuilds the rows;
        // also re-styles the active row when the active tab changes).
        refreshRecent();
        editorHost.getOpenDocuments().addListener((javafx.collections.ListChangeListener<OpenDocument>) c -> {
            java.util.List<OpenDocument> added = new java.util.ArrayList<>();
            while (c.next()) {
                if (c.wasAdded()) {
                    added.addAll(c.getAddedSubList());
                }
            }
            syncOpenEditors();
            // EditorHost records opened files; this list only shows them.
            if (added.stream().anyMatch(doc -> doc.getPath() != null)) {
                refreshRecent();
            }
        });
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> syncOpenEditors());
        syncOpenEditors();

        // The recent/favorites ListViews are built while the side panel is hidden (welcome mode,
        // width 0); refresh them once the panel gets a real width so their cells render correctly.
        widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Number> obs,
                    Number oldW, Number newW) {
                if (newW != null && newW.doubleValue() > 1.0) {
                    widthProperty().removeListener(this);
                    recentList.refresh();
                }
            }
        });
    }

    @FXML
    public void onNewFile() {
        newFile();
    }

    @FXML
    public void onRefreshWorkspace() {
        workspace.refresh();
    }

    // Every press of a FAVORITES | RECENT tab is routed through an explicit selection so a tab
    // can never be toggled off (a ToggleGroup would otherwise allow deselecting the active tab,
    // leaving none).

    /**
     * Makes {@code grip} (the thin strip above {@code pane}) draggable: dragging it up/down
     * changes the pane's height (clamped so the rest of the explorer stays usable); the height
     * is persisted on release.
     */
    private void installResizeGrip(javafx.scene.layout.Region grip, javafx.scene.layout.Region pane) {
        double[] start = new double[2]; // screenY, height at press
        grip.setOnMousePressed(e -> {
            start[0] = e.getScreenY();
            start[1] = pane.getHeight();
            e.consume();
        });
        grip.setOnMouseDragged(e -> {
            // Leave room for the header, section headers and a few workspace rows.
            double max = Math.max(FAV_RECENT_MIN_HEIGHT, getHeight() - FAV_RECENT_RESERVED_HEIGHT);
            double height = Math.min(max, clampFavRecentHeight(start[1] - (e.getScreenY() - start[0])));
            pane.setPrefHeight(height);
            e.consume();
        });
        grip.setOnMouseReleased(e -> {
            saveFavRecentHeight(pane.getPrefHeight());
            e.consume();
        });
    }

    static double clampFavRecentHeight(double height) {
        return Math.max(FAV_RECENT_MIN_HEIGHT, Math.min(FAV_RECENT_MAX_HEIGHT, height));
    }

    /** @return whether TOOLS starts collapsed (the default; the user's last choice is kept). */
    private boolean loadToolsCollapsed() {
        if (propertiesService != null) {
            try {
                String value = propertiesService.get(TOOLS_COLLAPSED_KEY);
                if (value != null) {
                    return Boolean.parseBoolean(value);
                }
            } catch (RuntimeException ignored) {
                // unreadable - fall back to the default
            }
        }
        return true;
    }

    private void saveToolsCollapsed(boolean collapsed) {
        if (propertiesService != null) {
            try {
                propertiesService.set(TOOLS_COLLAPSED_KEY, String.valueOf(collapsed));
            } catch (RuntimeException ignored) {
                // properties unavailable - keep the state for this session only
            }
        }
    }

    private double loadFavRecentHeight() {
        if (propertiesService != null) {
            try {
                String value = propertiesService.get(FAV_RECENT_HEIGHT_KEY);
                if (value != null) {
                    return Double.parseDouble(value);
                }
            } catch (RuntimeException ignored) {
                // malformed value — fall back to the default
            }
        }
        return FAV_RECENT_DEFAULT_HEIGHT;
    }

    private void saveFavRecentHeight(double height) {
        if (propertiesService != null) {
            try {
                propertiesService.set(FAV_RECENT_HEIGHT_KEY, String.valueOf(Math.round(height)));
            } catch (RuntimeException ignored) {
                // properties unavailable — keep the height for this session only
            }
        }
    }

    /** Rebuilds the OPEN EDITORS rows from the host's open documents (active row highlighted). */
    private void syncOpenEditors() {
        OpenDocument active = editorHost.getActiveDocument().orElse(null);
        openEditorsBox.getChildren().clear();
        for (OpenDocument doc : editorHost.getOpenDocuments()) {
            openEditorsBox.getChildren().add(openEditorRow(doc, doc == active));
        }
    }

    /** A single OPEN EDITORS row: type-colored icon · name · trailing dirty dot; click to select. */
    private javafx.scene.Node openEditorRow(OpenDocument doc, boolean active) {
        IconifyIcon icon = icon(doc.getFileType().icon(), 15);
        bindIconColor(icon, doc.getFileType().color());

        Label name = new Label(doc.getDisplayName());
        name.getStyleClass().add(active ? "fxt-open-name-active" : "fxt-open-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Region dot = new Region();
        dot.getStyleClass().add("fxt-dirty-dot");
        boolean dirty = doc.dirtyProperty().get();
        dot.setVisible(dirty);
        dot.setManaged(dirty);

        HBox row = new HBox(8, icon, name, dot);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("fxt-open-row");
        if (active) {
            row.getStyleClass().add("active");
        }
        row.setOnMouseClicked(e -> editorHost.selectDocument(doc));
        return row;
    }

    /** Opens a file from the workspace tree and reveals the Open Editors entry. */
    private void openWorkspaceFile(Path path) {
        editorHost.openFile(path);
    }

    // ----- one-click XSLT transform --------------------------------------------

    /** Sets the active stylesheet and records it as the most recent one. */
    /** A stylesheet picked here (recent, dialog) becomes the current one and is loaded into the editor, behind the active tab. */
    void pickStylesheet(File file) {
        setCurrentXslt(file);
        editorHost.openFile(file.toPath(), false);
    }

    /** A Schematron picked here (recent, favorite, dialog) becomes the current one and is loaded behind the active tab. */
    void pickSchematron(File file) {
        useSchematron(file);
        editorHost.openFile(file.toPath(), false);
    }

    private void setCurrentXslt(File file) {
        currentXslt = file;
        if (file != null && propertiesService != null) {
            propertiesService.addRecentXsltFile(file);
        }
    }

    /** Opens a file chooser to pick a stylesheet, then makes it the current one. */
    private void chooseStylesheet() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose XSLT stylesheet");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XSLT", "*.xsl", "*.xslt"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            pickStylesheet(file);
        }
    }

    /**
     * Resolves the stylesheet to use: the explicitly chosen one, otherwise the head of the
     * shared recent-XSLT list (so it stays in sync with the Transform panel).
     */
    private File currentStylesheet() {
        if (currentXslt != null && currentXslt.isFile()) {
            return currentXslt;
        }
        java.util.List<File> recent = recentXsltFiles();
        return recent.isEmpty() ? null : recent.get(0);
    }

    private java.util.List<File> recentXsltFiles() {
        if (propertiesService == null) {
            return java.util.List.of();
        }
        try {
            return propertiesService.getRecentXsltFiles();
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }

    /**
     * Transforms the selected XML file(s) with the current stylesheet: a single file goes to
     * the docked preview, several files to the batch transform tool. Falls back to the active
     * editor document when nothing is selected in the tree.
     */
    private void runExplorerTransform() {
        File xslt = currentStylesheet();
        if (xslt == null) {
            chooseStylesheet();
            xslt = currentStylesheet();
            if (xslt == null) {
                return;
            }
        }
        java.util.List<File> xmls = selectedXmlFiles();
        if (xmls.isEmpty()) {
            editorHost.transformOutputPanel().showError("Select an XML file to transform.");
            return;
        }
        if (xmls.size() == 1) {
            editorHost.runXsltPreview(xmls.get(0), xslt);
        } else {
            editorHost.openBatchTransform(xmls, xslt);
        }
    }

    private static boolean isXmlFile(Path path) {
        String name = path.getFileName() != null ? path.getFileName().toString() : "";
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".xml");
    }

    /** @return the tree-selected XML files, falling back to the active XML document. */
    private java.util.List<File> selectedXmlFiles() {
        java.util.List<File> xmls = new java.util.ArrayList<>();
        for (Path p : workspace.getSelectedFiles()) {
            if (isXmlFile(p)) {
                xmls.add(p.toFile());
            }
        }
        if (xmls.isEmpty()) {
            // Fall back to the active editor document if it is an XML file.
            editorHost.getActiveDocument()
                    .map(OpenDocument::getPath)
                    .filter(p -> p != null && isXmlFile(p))
                    .ifPresent(p -> xmls.add(p.toFile()));
        }
        return xmls;
    }

    // ----- one-click Schematron validation --------------------------------------

    /** @return the labels of the TOOLS action rows in display order (for tests/observers). */
    public java.util.List<String> toolLabels() {
        return fileActions.labels();
    }

    /**
     * Makes {@code file} the sticky Schematron: records it as most recent and binds it
     * to the active document (so the Validation activity, the PROBLEMS view and live
     * validation pick it up). Also the programmatic entry point for the shell and tests.
     */
    public void useSchematron(File file) {
        currentSchematron = file;
        if (file != null) {
            if (propertiesService != null) {
                propertiesService.addRecentSchematronFile(file);
            }
            editorHost.setActiveSchematron(file);
        }
    }

    /** Clears the recent-Schematron store and resets the sticky choice. */
    public void clearRecentSchematron() {
        if (propertiesService != null) {
            propertiesService.clearRecentSchematronFiles();
        }
        currentSchematron = null;
    }

    /** Opens a file chooser to pick a Schematron, then makes it the current one. */
    private void chooseSchematronFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Schematron");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Schematron", "*.sch", "*.schematron"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser,
                getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            pickSchematron(file);
        }
    }

    /**
     * Resolves the Schematron to use: the explicitly chosen one, otherwise the head
     * of the shared recent-Schematron list (mirrors {@link #currentStylesheet()}).
     */
    private File currentSchematronFile() {
        if (currentSchematron != null && currentSchematron.isFile()) {
            return currentSchematron;
        }
        java.util.List<File> recent = recentSchematronFiles();
        return recent.isEmpty() ? null : recent.get(0);
    }

    private java.util.List<File> recentSchematronFiles() {
        if (propertiesService == null) {
            return java.util.List.of();
        }
        try {
            return propertiesService.getRecentSchematronFiles();
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }

    /**
     * Validates the selected XML file(s) with the current Schematron via the
     * shell-wired {@link #schematronValidateAction}. Falls back to the active editor
     * document when nothing is selected in the tree; opens the chooser first when no
     * Schematron is set yet.
     */
    private void runExplorerValidation() {
        File schematron = currentSchematronFile();
        if (schematron == null) {
            chooseSchematronFile();
            schematron = currentSchematronFile();
            if (schematron == null) {
                return;
            }
        }
        java.util.List<File> xmls = selectedXmlFiles();
        if (xmls.isEmpty()) {
            editorHost.transformOutputPanel().showError("Select an XML file to validate.");
            return;
        }
        editorHost.setActiveSchematron(schematron);
        if (schematronValidateAction != null) {
            schematronValidateAction.accept(xmls, schematron);
        }
    }

    /**
     * Wires the Explorer's one-click Schematron validation to the shell: the callback
     * receives the XML files to validate and the Schematron to use, and is expected
     * to show the result in the Validation activity.
     *
     * @param action the action to run (ignored if {@code null})
     */
    public void setSchematronValidateAction(
            java.util.function.BiConsumer<java.util.List<File>, File> action) {
        if (action != null) {
            this.schematronValidateAction = action;
        }
    }

    /** @return the "Validate with Other Schematron" submenu texts (rebuilt) — for tests/observers. */
    public java.util.List<String> schematronMenuItemTexts() {
        return schematronSubmenu().getItems().stream().map(MenuItem::getText).toList();
    }

    // ----- context menu on the selected files (UX plan 3.1/3.2) -------------------------------

    /**
     * Builds the workspace context menu. It acts on the tree selection (falling back to the
     * active XML document) and is rebuilt on every show, so the "Transform with …" and
     * "Validate with …" entries always name the stylesheet / Schematron that will be used.
     */
    private javafx.scene.control.ContextMenu buildFileContextMenu() {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        menu.setOnShowing(e -> menu.getItems().setAll(fileContextMenuItems()));
        return menu;
    }

    /** @return the context-menu entries for the current selection (rebuilt each time). */
    java.util.List<MenuItem> fileContextMenuItems() {
        boolean hasXml = !selectedXmlFiles().isEmpty();
        java.util.List<MenuItem> items = new java.util.ArrayList<>();

        File xslt = currentStylesheet();
        MenuItem transform = menuItem(xslt != null ? "Transform with " + xslt.getName() : "Transform with XSLT…",
                this::runTransformOnSelection);
        transform.setId("explorer-ctx-transform");
        transform.setGraphic(org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(icon("bi-arrow-repeat", 16),
                org.fxt.freexmltoolkit.controls.theme.ActionColor.TOOL));
        transform.setDisable(!hasXml);
        javafx.scene.control.Menu otherXslt = new javafx.scene.control.Menu("Transform with Other Stylesheet");
        otherXslt.setId("explorer-ctx-transform-with");
        otherXslt.setGraphic(icon("bi-file-earmark-code", 16));
        for (File file : recentXsltFiles()) {
            MenuItem recent = menuItem(file.getName(), () -> {
                pickStylesheet(file);
                runTransformOnSelection();
            });
            recent.setGraphic(icon("bi-file-earmark-code", 16));
            otherXslt.getItems().add(recent);
        }
        MenuItem chooseXslt = menuItem("Choose stylesheet…", () -> {
            chooseStylesheet();
            if (currentXslt != null) {
                runTransformOnSelection();
            }
        });
        chooseXslt.setGraphic(icon("bi-folder2-open", 16));
        otherXslt.getItems().add(chooseXslt);
        otherXslt.setDisable(!hasXml);

        File schematron = currentSchematronFile();
        MenuItem validate = menuItem(schematron != null ? "Validate with " + schematron.getName()
                : "Validate with Schematron…", this::runValidationOnSelection);
        validate.setId("explorer-ctx-validate");
        validate.setGraphic(org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(icon("bi-ui-checks-grid", 16),
                org.fxt.freexmltoolkit.controls.theme.ActionColor.CREATE));
        validate.setDisable(!hasXml);
        javafx.scene.control.Menu otherSch = schematronSubmenu();
        otherSch.setDisable(!hasXml);

        MenuItem open = menuItem("Open", () -> workspace.getSelectedFiles().forEach(this::openWorkspaceFile));
        open.setId("explorer-ctx-open");
        open.setGraphic(icon("bi-box-arrow-up-right", 16));
        open.setDisable(workspace.getSelectedFiles().isEmpty());

        items.add(open);
        items.add(new javafx.scene.control.SeparatorMenuItem());
        items.addAll(java.util.List.of(transform, otherXslt, validate, otherSch));
        return items;
    }

    /** The "Validate with Other Schematron" submenu: recents · Favorites · Choose… · Clear recent. */
    private javafx.scene.control.Menu schematronSubmenu() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Validate with Other Schematron");
        menu.setId("explorer-ctx-validate-with");
        menu.setGraphic(icon("bi-ui-checks-grid", 16));
        for (File file : recentSchematronFiles()) {
            MenuItem recent = menuItem(file.getName(), () -> {
                pickSchematron(file);
                runValidationOnSelection();
            });
            recent.setGraphic(icon("bi-ui-checks-grid", 16));
            menu.getItems().add(recent);
        }
        if (!menu.getItems().isEmpty()) {
            menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        }
        menu.getItems().add(FavoritesMenu.submenu("Favorites",
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.SCHEMATRON, file -> {
                    pickSchematron(file);
                    runValidationOnSelection();
                }));
        MenuItem choose = menuItem("Choose Schematron…", () -> {
            chooseSchematronFile();
            if (currentSchematron != null) {
                runValidationOnSelection();
            }
        });
        choose.setGraphic(icon("bi-folder2-open", 16));
        MenuItem clear = menuItem("Clear recent", this::clearRecentSchematron);
        clear.setGraphic(icon("bi-eraser", 16));
        menu.getItems().addAll(choose, clear);
        return menu;
    }

    /** Transforms the selected XML file(s) with the current stylesheet (context menu, tests). */
    public void runTransformOnSelection() {
        runExplorerTransform();
    }

    /** Validates the selected XML file(s) with the current Schematron (context menu, tests). */
    public void runValidationOnSelection() {
        runExplorerValidation();
    }

    /** @return the name of the Schematron the next "Validate with …" uses, or {@code null} (tests). */
    public String currentSchematronName() {
        File file = currentSchematronFile();
        return file != null ? file.getName() : null;
    }

    /** Overridable "New file" action; {@code null} falls back to a blank XML document. */
    private Runnable newFileAction;

    /**
     * Routes the Explorer's "New file" button through a custom action (e.g. the shell's
     * guided {@link NewFileDialog}) so there is a single new-file entry point.
     *
     * @param action the action to run (ignored if {@code null})
     */
    public void setNewFileAction(Runnable action) {
        if (action != null) {
            this.newFileAction = action;
        }
    }

    private void newFile() {
        if (newFileAction != null) {
            newFileAction.run();
        } else {
            editorHost.newDocument(EditorFileType.XML);
        }
    }

    /** @return the current workspace root folder, or {@code null} if none is open. */
    public Path getWorkspaceFolder() {
        return workspace.getRootFolder();
    }

    /** Sets the workspace root and shows its folder name as the section title. */
    public void setWorkspaceFolder(Path folder) {
        workspace.setRootFolder(folder);
        Path name = folder != null ? folder.getFileName() : null;
        workspaceTitle.setText(name != null
                ? name.toString().toUpperCase(java.util.Locale.ROOT) : "WORKSPACE");
    }

    /**
     * Selects {@code file} in the workspace tree; a file outside the current workspace makes its
     * folder the workspace first.
     */
    public void revealFile(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path root = getWorkspaceFolder();
        if ((root == null || !absolute.startsWith(root.toAbsolutePath().normalize()))
                && absolute.getParent() != null) {
            setWorkspaceFolder(absolute.getParent());
            root = getWorkspaceFolder();
        }
        if (root != null) {
            // The tree items carry paths in the root's own form (possibly relative).
            workspace.selectPath(root.resolve(root.toAbsolutePath().normalize().relativize(absolute)));
        }
    }

    /** @return the workspace section title currently shown (for tests/observers). */
    public String getWorkspaceTitle() {
        return workspaceTitle.getText();
    }

    /** @return all ⋮-menu item texts (for tests/observers). */

    @FXML
    public void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Folder");
        File dir = org.fxt.freexmltoolkit.util.FileChooserHelper.showDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (dir != null) {
            setWorkspaceFolder(dir.toPath());
        }
    }

    @FXML
    public void onOpenFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open File");
        chooser.getExtensionFilters().addAll(EditorFileType.fileChooserFilters());
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.openFile(file.toPath());
        }
    }

    /** Clears the recent-files store and the RECENT list. */
    @FXML
    public void clearRecent() {
        if (propertiesService != null) {
            propertiesService.clearLastOpenFiles();
        }
        refreshRecent();
    }

    private void refreshRecent() {
        if (propertiesService != null) {
            recentList.getSelectionModel().clearSelection();
            recentFiles.setAll(propertiesService.getLastOpenFiles());
        }
    }

    private static PropertiesService resolvePropertiesService() {
        try {
            return ServiceRegistry.get(PropertiesService.class);
        } catch (Throwable t) {
            return null; // not available (e.g. in isolated tests)
        }
    }

    private static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }

    /**
     * Binds (not sets) the icon color to a per-type hex value. Binding is required so the
     * list's CSS {@code -fx-icon-color} rule cannot override the color on the next CSS pass.
     */
    private static void bindIconColor(IconifyIcon icon, String hex) {
        if (hex == null) {
            return;
        }
        try {
            icon.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(
                    javafx.scene.paint.Color.web(hex)));
        } catch (Exception ignored) {
            // unparsable color: keep the default icon color
        }
    }

    /** Renders a recent file with its file-type icon and name. */
    private static final class RecentFileCell extends ListCell<File> {
        private RecentFileCell() {
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(File item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.getName());
            EditorFileType type = EditorFileType.fromFileName(item.getName());
            IconifyIcon icon = new IconifyIcon(type.icon());
            icon.setIconSize(15);
            bindIconColor(icon, type.color());
            setGraphic(icon);
        }
    }

}
