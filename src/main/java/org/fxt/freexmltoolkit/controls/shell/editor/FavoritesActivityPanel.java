package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;

/**
 * The Favorites activity side panel: lists saved file favorites — grouped by
 * their user folder when folders exist, otherwise by file type — with one
 * type-colored icon per entry, opens one on click, adds the active document,
 * filters via the search field, and offers quick Rename / Move-to-folder /
 * Remove in the context menu. The full management view (rename, folders,
 * search over all favorites) opens in the editor area via "Manage…"
 * ({@link FavoritesManagerView}). Reuses {@link FavoritesService} (the same
 * store as the rest of the app, so favorites are shared).
 */
public class FavoritesActivityPanel extends VBox {

    /** One list row: a favorite, a type-group header, or a folder-group header. */
    record Row(FileFavorite favorite, FileFavorite.FileType header, String folder) {
        static Row of(FileFavorite favorite) {
            return new Row(favorite, null, null);
        }

        static Row header(FileFavorite.FileType type) {
            return new Row(null, type, null);
        }

        static Row folderHeader(String folder) {
            return new Row(null, null, folder);
        }

        boolean isHeader() {
            return favorite == null;
        }

        String headerText() {
            return folder != null ? folder
                    : header != null ? header.getDisplayName() : "";
        }
    }

    private final EditorHost editorHost;
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final ListView<Row> list = new ListView<>(rows);
    private final TextField search = new TextField();

    public FavoritesActivityPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("FAVORITES");
        title.getStyleClass().add("fxt-side-panel-title");

        Button addCurrent = new Button("Add current", icon("bi-star"));
        addCurrent.getStyleClass().add("fxt-tool-button");
        addCurrent.setOnAction(e -> addCurrent());
        Button manage = new Button("Manage…", icon("bi-sliders"));
        manage.setId("favorites-manage");
        manage.getStyleClass().add("fxt-tool-button");
        manage.setOnAction(e -> openManager());
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        search.setId("favorites-search");
        search.setPromptText("Search…");
        search.textProperty().addListener((obs, oldV, newV) -> refresh());

        list.getStyleClass().add("fxt-open-editors");
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPlaceholder(new Label("No favorites"));
        list.setCellFactory(lv -> new FavoriteCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isHeader() && newV.favorite().getFilePath() != null) {
                // Defer opening to the next pulse so it does not run *inside* the
                // ListView's selection-change processing (re-entering the
                // ListViewBehavior listener triggered an IndexOutOfBoundsException).
                String path = newV.favorite().getFilePath();
                Platform.runLater(() -> {
                    FavoritesService.getInstance().recordAccess(path);
                    editorHost.openFile(Path.of(path));
                });
            }
        });

        // Dropping files onto the panel adds them as favorites.
        setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            e.consume();
        });
        setOnDragDropped(e -> {
            boolean ok = e.getDragboard().hasFiles() && addFiles(e.getDragboard().getFiles()) > 0;
            e.setDropCompleted(ok);
            e.consume();
        });

        ContextMenu menu = new ContextMenu();
        MenuItem open = new MenuItem("Open", icon("bi-folder2-open"));
        open.setOnAction(e -> selectedFavorite().ifPresent(f -> {
            FavoritesService.getInstance().recordAccess(f.getFilePath());
            editorHost.openFile(Path.of(f.getFilePath()));
        }));
        MenuItem rename = new MenuItem("Rename…", icon("bi-pencil"));
        rename.setOnAction(e -> selectedFavorite().ifPresent(f -> {
            TextInputDialog dialog = new TextInputDialog(displayName(f));
            dialog.setTitle("Rename Favorite");
            dialog.setHeaderText(null);
            dialog.setContentText("New name:");
            dialog.showAndWait().map(String::strip).filter(s -> !s.isEmpty())
                    .ifPresent(newName -> renameFavorite(f, newName));
        }));
        Menu moveTo = new Menu("Move to folder", icon("bi-folder-symlink"));
        MenuItem remove = new MenuItem("Remove", icon("bi-trash"));
        remove.setOnAction(e -> selectedFavorite().ifPresent(f -> {
            FavoritesService.getInstance().removeFavorite(f);
            refresh();
        }));
        menu.getItems().addAll(open, rename, moveTo, new SeparatorMenuItem(), remove);
        // The folder list is dynamic - rebuild the submenu each time the menu opens.
        menu.setOnShowing(e -> rebuildMoveToMenu(moveTo));
        list.setContextMenu(menu);

        getChildren().addAll(title, new HBox(6, addCurrent, actionSpacer, manage), search, list);
        refresh();
    }

    /** Adds the active document to favorites (no-op for untitled). */
    public void addCurrent() {
        editorHost.getActiveDocument()
                .filter(doc -> doc.getPath() != null)
                .ifPresent(doc -> {
                    FavoritesService.getInstance().addFavorite(doc.getPath().toFile());
                    refresh();
                });
    }

    /**
     * Adds the given files as favorites (used by drag &amp; drop).
     *
     * @return how many files were added
     */
    int addFiles(java.util.List<java.io.File> files) {
        int added = 0;
        for (java.io.File file : files) {
            if (file != null && file.isFile()) {
                FavoritesService.getInstance().addFavorite(file);
                added++;
            }
        }
        if (added > 0) {
            refresh();
        }
        return added;
    }

    /** Opens (or focuses) the full management view in the editor area. */
    void openManager() {
        editorHost.openOrFocusToolTab("Favorites", "bi-star",
                () -> new FavoritesManagerView(editorHost));
    }

    /** Renames a favorite (display name + legacy alias) and persists it. */
    void renameFavorite(FileFavorite favorite, String newName) {
        favorite.setName(newName);
        favorite.setAlias(newName);
        FavoritesService.getInstance().updateFavorite(favorite);
        refresh();
    }

    /** Moves a favorite into {@code folder} ({@code null} = no folder) and persists it. */
    void moveToFolder(FileFavorite favorite, String folder) {
        FavoritesService.getInstance().moveFavoriteToFolder(favorite.getId(),
                folder == null || folder.isBlank() ? null : folder);
        refresh();
    }

    /** Sets the search filter (also used by tests). */
    void setSearch(String text) {
        search.setText(text != null ? text : "");
    }

    private java.util.Optional<FileFavorite> selectedFavorite() {
        Row selected = list.getSelectionModel().getSelectedItem();
        return selected != null && !selected.isHeader()
                ? java.util.Optional.of(selected.favorite()) : java.util.Optional.empty();
    }

    /** Rebuilds "Move to folder": existing folders + New folder… + no-folder. */
    private void rebuildMoveToMenu(Menu moveTo) {
        moveTo.getItems().clear();
        java.util.TreeSet<String> folders = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            FavoritesService.getInstance().getAllFolders().forEach(f -> {
                if (f != null && !f.isBlank() && !"Uncategorized".equals(f)) {
                    folders.add(f);
                }
            });
        } catch (Throwable ignored) {
            // no store (tests)
        }
        for (String folder : folders) {
            MenuItem item = new MenuItem(folder, icon("bi-folder"));
            item.setOnAction(e -> selectedFavorite().ifPresent(f -> moveToFolder(f, folder)));
            moveTo.getItems().add(item);
        }
        if (!folders.isEmpty()) {
            moveTo.getItems().add(new SeparatorMenuItem());
        }
        MenuItem noFolder = new MenuItem("(No folder)");
        noFolder.setOnAction(e -> selectedFavorite().ifPresent(f -> moveToFolder(f, null)));
        MenuItem newFolder = new MenuItem("New folder…", icon("bi-folder-plus"));
        newFolder.setOnAction(e -> selectedFavorite().ifPresent(f -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("New Folder");
            dialog.setHeaderText(null);
            dialog.setContentText("Folder name:");
            dialog.showAndWait().map(String::strip).filter(s -> !s.isEmpty())
                    .ifPresent(name -> moveToFolder(f, name));
        }));
        moveTo.getItems().addAll(noFolder, newFolder);
    }

    /** @return the number of favorites currently listed (group headers excluded). */
    public int getFavoriteCount() {
        return (int) rows.stream().filter(r -> !r.isHeader()).count();
    }

    /** @return the group-header texts in display order (for tests/observers). */
    public java.util.List<String> groupHeaderTexts() {
        return rows.stream().filter(Row::isHeader).map(Row::headerText).toList();
    }

    /** @return the row index of the first favorite (non-header) row, or -1 (for tests). */
    int firstFavoriteRowIndex() {
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).isHeader()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Rebuilds the rows, honouring the search filter: grouped by the user folder
     * as soon as any favorite has one (the rest gathers under "Uncategorized",
     * last), otherwise by file type (enum order). Favorites sort by name.
     */
    private void refresh() {
        list.getSelectionModel().clearSelection();
        String query = search.getText() != null ? search.getText().strip().toLowerCase(Locale.ROOT) : "";
        List<FileFavorite> all = FavoritesService.getInstance().getAllFavorites().stream()
                .filter(f -> query.isEmpty()
                        || displayName(f).toLowerCase(Locale.ROOT).contains(query)
                        || (f.getFilePath() != null && f.getFilePath().toLowerCase(Locale.ROOT).contains(query)))
                .sorted(Comparator.comparing(FavoritesActivityPanel::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        java.util.List<Row> next = new java.util.ArrayList<>();

        boolean hasFolders = all.stream()
                .anyMatch(f -> f.getFolderName() != null && !f.getFolderName().isBlank());
        if (hasFolders) {
            TreeMap<String, List<FileFavorite>> byFolder = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            List<FileFavorite> uncategorized = new java.util.ArrayList<>();
            for (FileFavorite favorite : all) {
                if (favorite.getFolderName() != null && !favorite.getFolderName().isBlank()) {
                    byFolder.computeIfAbsent(favorite.getFolderName(), k -> new java.util.ArrayList<>())
                            .add(favorite);
                } else {
                    uncategorized.add(favorite);
                }
            }
            byFolder.forEach((folder, group) -> {
                next.add(Row.folderHeader(folder));
                group.forEach(f -> next.add(Row.of(f)));
            });
            if (!uncategorized.isEmpty()) {
                next.add(Row.folderHeader("Uncategorized"));
                uncategorized.forEach(f -> next.add(Row.of(f)));
            }
        } else {
            for (FileFavorite.FileType type : FileFavorite.FileType.values()) {
                List<FileFavorite> group = all.stream().filter(f -> f.getFileType() == type).toList();
                if (group.isEmpty()) {
                    continue;
                }
                next.add(Row.header(type));
                group.forEach(f -> next.add(Row.of(f)));
            }
        }
        rows.setAll(next);
    }

    private static String displayName(FileFavorite favorite) {
        return favorite.getName() != null && !favorite.getName().isBlank()
                ? favorite.getName() : favorite.getFileName();
    }

    private IconifyIcon icon(String literal) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return icon;
    }

    /** Renders a group header (folder or type), or a favorite with its type-colored icon. */
    private static final class FavoriteCell extends ListCell<Row> {
        @Override
        protected void updateItem(Row item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().remove("fxt-favorites-group");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (item.isHeader()) {
                setText(item.headerText().toUpperCase(Locale.ROOT));
                setGraphic(item.folder() != null ? headerIcon() : null);
                getStyleClass().add("fxt-favorites-group");
                return;
            }
            FileFavorite favorite = item.favorite();
            setText(displayName(favorite));
            FileFavorite.FileType type = favorite.getFileType();
            IconifyIcon icon = new IconifyIcon(type.getIconLiteral());
            icon.setIconSize(14);
            try {
                // Bind (not set): the list's CSS -fx-icon-color rule must not
                // override the per-type color (CSS skips bound properties).
                icon.iconColorProperty().bind(
                        new javafx.beans.property.SimpleObjectProperty<>(Color.web(type.getDefaultColor())));
            } catch (IllegalArgumentException ignored) {
                // unparsable color: keep the default icon color
            }
            setGraphic(icon);
        }

        private static IconifyIcon headerIcon() {
            IconifyIcon icon = new IconifyIcon("bi-folder");
            icon.setIconSize(12);
            return icon;
        }
    }
}
