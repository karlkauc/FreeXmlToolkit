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
    private final ObservableList<OpenDocument> openItems = FXCollections.observableArrayList();
    private final ListView<OpenDocument> openList = new ListView<>(openItems);
    private final WorkspaceTree workspace = new WorkspaceTree(this::openWorkspaceFile);
    private final Label workspaceTitle = new Label("WORKSPACE");
    private final MenuButton overflowMenu = new MenuButton();
    /** Guards the OPEN-EDITORS selection listener against host-driven sync loops. */
    private boolean syncingSelection;

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
        HBox header = new HBox(4, title, headerSpacer,
                flatAction("explorer-new-file", "bi-file-earmark-plus", "New file", this::newFile),
                flatAction("explorer-open-folder", "bi-folder-plus", "Open folder…", this::chooseFolder),
                flatAction("explorer-refresh", "bi-arrow-clockwise", "Refresh workspace",
                        workspace::refresh),
                overflowMenu);
        header.getStyleClass().add("fxt-sp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // --- OPEN EDITORS ----------------------------------------------------
        // Backed by a private copy (not the live list) and synced via
        // clearSelection + setAll, to avoid a JavaFX ListViewBehavior crash that
        // can occur when the bound items list is mutated while a row is selected.
        openList.getStyleClass().addAll("fxt-open-editors", "fxt-explorer-list");
        openList.setCellFactory(lv -> new OpenDocumentCell());
        // Fixed cell height so the list can size itself to its rows exactly
        // (no clipped row / stray scrollbar from estimated heights).
        openList.setFixedCellSize(28);
        // Depend on the list itself (not Bindings.size(...)): a lazy size binding
        // that is never read stops firing after its first invalidation.
        openList.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.min(170.0, Math.max(1, openItems.size()) * 28.0 + 2), openItems));
        openList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !syncingSelection) {
                // Defer out of the selection-change processing to avoid re-entering
                // the ListViewBehavior listener (IndexOutOfBoundsException).
                javafx.application.Platform.runLater(() -> editorHost.selectDocument(newV));
            }
        });

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

        getChildren().addAll(header,
                sectionHeader(new Label("OPEN EDITORS")), openList,
                sectionHeader(workspaceTitle), workspace,
                sectionHeader(new Label("RECENT")), recentList);

        // Track recent files as documents open, keep the Open Editors list in
        // sync (selection cleared before each replace — see above) and mirror
        // the host's active document as the highlighted/selected row.
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
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> syncActiveRow());
        syncOpenEditors();
    }

    /** Replaces the OPEN EDITORS items (selection cleared first) and re-selects the active doc. */
    private void syncOpenEditors() {
        syncingSelection = true;
        try {
            openList.getSelectionModel().clearSelection();
            openItems.setAll(editorHost.getOpenDocuments());
        } finally {
            syncingSelection = false;
        }
        syncActiveRow();
    }

    /** Selects (and restyles) the row of the host's active document. */
    private void syncActiveRow() {
        OpenDocument active = editorHost.getActiveDocument().orElse(null);
        syncingSelection = true;
        try {
            if (active == null) {
                openList.getSelectionModel().clearSelection();
            } else {
                int index = openItems.indexOf(active);
                if (index >= 0) {
                    openList.getSelectionModel().select(index);
                }
            }
        } finally {
            syncingSelection = false;
        }
        openList.refresh();
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

    /** A section header: chevron + small bold label (shared style with the Validation panel). */
    private static HBox sectionHeader(Label label) {
        IconifyIcon chevron = new IconifyIcon("bi-chevron-down");
        chevron.setIconSize(11);
        HBox header = new HBox(6, chevron, label);
        header.getStyleClass().add("fxt-sp-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
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
     * Renders an open document: file-type icon · name (primary + semibold when
     * active) · dirty dot on the right (per the mockup).
     */
    private final class OpenDocumentCell extends ListCell<OpenDocument> {
        private OpenDocumentCell() {
            // Follow the ListView width (ellipsize) instead of forcing a horizontal scrollbar.
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(OpenDocument item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().remove("active");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean active = editorHost.getActiveDocument().map(d -> d == item).orElse(false);
            if (active) {
                getStyleClass().add("active");
            }
            IconifyIcon icon = icon(item.getFileType().icon(), 15);

            Label name = new Label();
            name.getStyleClass().add(active ? "fxt-open-name-active" : "fxt-open-name");
            name.textProperty().bind(item.displayNameProperty());
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);

            Region dot = new Region();
            dot.getStyleClass().add("fxt-dirty-dot");
            dot.visibleProperty().bind(item.dirtyProperty());
            dot.managedProperty().bind(item.dirtyProperty());

            HBox row = new HBox(8, icon, name, dot);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(row);
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
            IconifyIcon icon = new IconifyIcon(EditorFileType.fromFileName(item.getName()).icon());
            icon.setIconSize(15);
            setGraphic(icon);
        }
    }
}
