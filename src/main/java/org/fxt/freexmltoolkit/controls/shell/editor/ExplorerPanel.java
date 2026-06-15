package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
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
    private final ObservableList<File> recentFiles = FXCollections.observableArrayList();
    private final ListView<File> recentList = new ListView<>(recentFiles);
    private final ObservableList<org.fxt.freexmltoolkit.domain.FileFavorite> favorites =
            FXCollections.observableArrayList();
    private final ListView<org.fxt.freexmltoolkit.domain.FileFavorite> favoritesList =
            new ListView<>(favorites);
    /**
     * OPEN EDITORS is a plain VBox of rows, not a virtualized ListView: the list is small, and a
     * ListView populated after construction (via the open-documents listener) while the panel was
     * built hidden failed to render its cell content in the full shell. Rows always render.
     */
    private final VBox openEditorsBox = new VBox();
    private final WorkspaceTree workspace = new WorkspaceTree(this::openWorkspaceFile);
    private final Label workspaceTitle = new Label("WORKSPACE");
    private final MenuButton overflowMenu = new MenuButton();

    public ExplorerPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-explorer-panel");

        // --- header: EXPLORER ... [new file][open folder][refresh][⋮] -------
        Label title = new Label("EXPLORER");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-sp-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        overflowMenu.setId("explorer-overflow");
        overflowMenu.setGraphic(icon("bi-three-dots-vertical", 14));
        overflowMenu.getStyleClass().add("fxt-sp-overflow");
        overflowMenu.getItems().addAll(
                menuItem("Open file…", this::openFile),
                menuItem("Clear recent", this::clearRecent));
        HBox header = new HBox(10, title, headerSpacer,
                flatAction("explorer-new-file", "bi-file-earmark-plus", "New file", this::newFile),
                flatAction("explorer-open-folder", "bi-folder-plus", "Open folder…", this::chooseFolder),
                flatAction("explorer-refresh", "bi-arrow-clockwise", "Refresh workspace",
                        workspace::refresh),
                overflowMenu);
        header.getStyleClass().add("fxt-sp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // --- OPEN EDITORS (VBox of rows) -------------------------------------
        openEditorsBox.getStyleClass().add("fxt-open-editors-box");

        // --- workspace tree ---------------------------------------------------
        workspaceTitle.getStyleClass().add("fxt-sp-section-label");
        VBox.setVgrow(workspace, Priority.ALWAYS);

        // --- RECENT -----------------------------------------------------------
        recentList.getStyleClass().addAll("fxt-open-editors", "fxt-explorer-list");
        recentList.setCellFactory(lv -> new RecentFileCell());
        recentList.setFixedCellSize(28);
        recentList.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.min(170.0, Math.max(1, recentFiles.size()) * 28.0 + 2), recentFiles));
        recentList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.isFile()) {
                java.io.File file = newV;
                javafx.application.Platform.runLater(() -> editorHost.openFile(file.toPath()));
            }
        });

        // --- FAVORITES ----------------------------------------------------------
        favoritesList.setId("explorer-favorites-list");
        favoritesList.getStyleClass().addAll("fxt-open-editors", "fxt-explorer-list");
        favoritesList.setCellFactory(lv -> new FavoriteCell());
        favoritesList.setFixedCellSize(28);
        favoritesList.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.min(170.0, Math.max(1, favorites.size()) * 28.0 + 2), favorites));
        favoritesList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                String favoritePath = newV.getFilePath();
                java.io.File file = new java.io.File(favoritePath);
                if (file.isFile()) {
                    javafx.application.Platform.runLater(() -> {
                        org.fxt.freexmltoolkit.service.FavoritesService.getInstance()
                                .recordAccess(favoritePath);
                        editorHost.openFile(file.toPath());
                    });
                }
            }
        });
        javafx.scene.control.ContextMenu favoritesMenu = new javafx.scene.control.ContextMenu();
        MenuItem removeFavorite = new MenuItem("Remove from favorites", icon("bi-x-circle", 16));
        removeFavorite.setOnAction(e -> removeSelectedFavorite());
        favoritesMenu.getItems().add(removeFavorite);
        favoritesList.setContextMenu(favoritesMenu);

        HBox openHeader = sectionHeader(new Label("OPEN EDITORS"), openEditorsBox);
        openHeader.setId("explorer-open-editors-header");
        HBox workspaceHeader = sectionHeader(workspaceTitle, workspace);
        workspaceHeader.setId("explorer-workspace-header");

        // FAVORITES | RECENT as a side-by-side tab control pinned to the bottom (Figma "future").
        javafx.scene.layout.VBox favRecentPane = buildFavoritesRecentTabs();

        getChildren().addAll(header,
                openHeader, openEditorsBox,
                workspaceHeader, workspace,
                favRecentPane);
        refreshFavorites();

        // Track recent files as documents open and keep OPEN EDITORS in sync (rebuilds the rows;
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
            added.forEach(this::rememberRecent);
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
                    favoritesList.refresh();
                }
            }
        });
    }

    /**
     * Builds the bottom FAVORITES | RECENT control: two side-by-side tabs (active tab
     * underlined in primary blue) over a stack that swaps the favorites / recent lists
     * (Figma "future" layout).
     */
    private javafx.scene.layout.VBox buildFavoritesRecentTabs() {
        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.ToggleButton favTab = new javafx.scene.control.ToggleButton("FAVORITES");
        javafx.scene.control.ToggleButton recentTab = new javafx.scene.control.ToggleButton("RECENT");
        for (javafx.scene.control.ToggleButton tab : new javafx.scene.control.ToggleButton[]{favTab, recentTab}) {
            tab.setToggleGroup(group);
            tab.getStyleClass().add("fxt-fav-tab");
            tab.setFocusTraversable(false);
            tab.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(tab, Priority.ALWAYS);
        }
        favTab.setId("explorer-tab-favorites");
        recentTab.setId("explorer-tab-recent");
        HBox tabs = new HBox(favTab, recentTab);
        tabs.getStyleClass().add("fxt-fav-tabs");

        javafx.scene.layout.StackPane content = new javafx.scene.layout.StackPane(favoritesList, recentList);
        Runnable sync = () -> {
            boolean fav = favTab.isSelected();
            favoritesList.setVisible(fav);
            favoritesList.setManaged(fav);
            recentList.setVisible(!fav);
            recentList.setManaged(!fav);
        };
        // Route every press through an explicit selection so a tab can never be toggled off
        // (a ToggleGroup would otherwise allow deselecting the active tab, leaving none).
        favTab.setOnAction(e -> {
            favTab.setSelected(true);
            sync.run();
        });
        recentTab.setOnAction(e -> {
            recentTab.setSelected(true);
            sync.run();
        });
        favTab.setSelected(true);
        sync.run();

        return new javafx.scene.layout.VBox(tabs, content);
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

    private void newFile() {
        editorHost.newDocument(EditorFileType.XML);
    }

    /** Sets the workspace root and shows its folder name as the section title. */
    public void setWorkspaceFolder(Path folder) {
        workspace.setRootFolder(folder);
        Path name = folder != null ? folder.getFileName() : null;
        workspaceTitle.setText(name != null
                ? name.toString().toUpperCase(java.util.Locale.ROOT) : "WORKSPACE");
    }

    /** @return the workspace section title currently shown (for tests/observers). */
    public String getWorkspaceTitle() {
        return workspaceTitle.getText();
    }

    /** @return all ⋮-menu item texts (for tests/observers). */
    public java.util.List<String> overflowMenuItemTexts() {
        return overflowMenu.getItems().stream().map(MenuItem::getText).toList();
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Folder");
        File dir = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (dir != null) {
            setWorkspaceFolder(dir.toPath());
        }
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML / XSD / XSLT / Schematron / JSON",
                        "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron", "*.json"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.openFile(file.toPath());
        }
    }

    /** Clears the recent-files store and the RECENT list. */
    public void clearRecent() {
        if (propertiesService != null) {
            propertiesService.clearLastOpenFiles();
        }
        refreshRecent();
    }

    private void rememberRecent(OpenDocument doc) {
        if (propertiesService == null || doc.getPath() == null) {
            return;
        }
        propertiesService.addLastOpenFile(doc.getPath().toFile());
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

    /**
     * A collapsible section header: chevron + small bold label (shared style with the
     * Validation panel). Clicking the header hides/shows {@code content} and flips the chevron.
     */
    private static HBox sectionHeader(Label label, javafx.scene.Node content) {
        return SidePanelLayout.sectionHeader(label, content);
    }

    /** A flat 14px header icon action (no button chrome, per the mockup). */
    private static Button flatAction(String id, String iconLiteral, String tooltip, Runnable action) {
        Button button = new Button();
        button.setId(id);
        button.getStyleClass().add("fxt-sp-action");
        button.setGraphic(icon(iconLiteral, 14));
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
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

    /**
     * Removes the selected entry from the favorites store - the file on disk is
     * untouched. Wired to the FAVORITES list's context menu.
     */
    void removeSelectedFavorite() {
        var selected = favoritesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            org.fxt.freexmltoolkit.service.FavoritesService.getInstance().removeFavorite(selected);
        } catch (Throwable t) {
            // no favorites store (tests) - just refresh the view
        }
        refreshFavorites();
    }

    /** Reloads the FAVORITES list from the favorites store (best-effort). */
    private void refreshFavorites() {
        try {
            favoritesList.getSelectionModel().clearSelection();
            favorites.setAll(org.fxt.freexmltoolkit.service.FavoritesService.getInstance().getAllFavorites());
        } catch (Throwable t) {
            favorites.clear(); // no favorites store (tests) - section just stays empty
        }
    }

    /** A favorite: type-colored file icon + display name (same look as RECENT). */
    private static final class FavoriteCell extends ListCell<org.fxt.freexmltoolkit.domain.FileFavorite> {
        private FavoriteCell() {
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(org.fxt.freexmltoolkit.domain.FileFavorite item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String name = item.getName() != null && !item.getName().isBlank()
                    ? item.getName() : new File(item.getFilePath()).getName();
            setText(name);
            var type = item.getFileType();
            IconifyIcon icon = new IconifyIcon(type != null ? type.getIconLiteral() : "bi-file-earmark");
            icon.setIconSize(14);
            if (type != null) {
                try {
                    // Bind (not set): the list's CSS -fx-icon-color rule must not
                    // override the per-type color on the next CSS pass.
                    icon.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(
                            javafx.scene.paint.Color.web(type.getDefaultColor())));
                } catch (Exception ignored) {
                    // unparsable color: keep the default icon color
                }
            }
            setGraphic(icon);
        }
    }
}
