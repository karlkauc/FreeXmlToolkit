package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;

/**
 * The full favorites management view, shown as a tool tab in the main editor
 * area (the side panels stay lean): rename favorites inline, group them into
 * folders (create/rename/delete, move via the Folder column), search, open and
 * remove. All changes go through the shared {@link FavoritesService} store, so
 * the Favorites/Explorer side panels pick them up on their next refresh.
 */
public class FavoritesManagerView extends BorderPane {

    static final String ALL = "All";
    static final String UNCATEGORIZED = "Uncategorized";

    private final EditorHost editorHost;
    private final TextField search = new TextField();
    private final ListView<String> folderList = new ListView<>();
    private final ObservableList<FileFavorite> tableItems = FXCollections.observableArrayList();
    private final TableView<FileFavorite> table = new TableView<>(tableItems);
    private final ObservableList<String> folderOptions = FXCollections.observableArrayList();

    public FavoritesManagerView(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-favmgr");

        // --- header: title + search --------------------------------------------
        Label title = new Label("Manage Favorites", icon("bi-star", 18));
        title.getStyleClass().add("fxt-favmgr-title");
        Label subtitle = new Label("Rename favorites, group them into folders, search.");
        subtitle.getStyleClass().add("fxt-favmgr-subtitle");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        search.setId("favmgr-search");
        search.setPromptText("Search favorites…");
        search.textProperty().addListener((obs, oldV, newV) -> refreshTable());
        HBox header = new HBox(12, new VBox(2, title, subtitle), headerSpacer, search);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("fxt-favmgr-header");

        // --- left: folders -------------------------------------------------------
        Label foldersLabel = new Label("FOLDERS");
        foldersLabel.getStyleClass().add("fxt-sp-section-label");
        folderList.setId("favmgr-folders");
        folderList.setPrefWidth(200);
        folderList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldV, newV) -> refreshTable());
        Button newFolder = toolButton("New…", "bi-folder-plus", "favmgr-folder-new", () ->
                prompt("New Folder", "Folder name:", "").ifPresent(this::createFolder));
        Button renameFolder = toolButton("Rename…", "bi-pencil", "favmgr-folder-rename", () -> {
            String selected = selectedFolder();
            if (isRealFolder(selected)) {
                prompt("Rename Folder", "New name:", selected)
                        .ifPresent(newName -> renameFolder(selected, newName));
            }
        });
        Button deleteFolder = toolButton("Delete", "bi-x-circle", "favmgr-folder-delete", () -> {
            String selected = selectedFolder();
            if (isRealFolder(selected)) {
                deleteFolder(selected);
            }
        });
        HBox folderActions = new HBox(6, newFolder, renameFolder, deleteFolder);
        VBox left = new VBox(8, foldersLabel, folderList, folderActions);
        VBox.setVgrow(folderList, Priority.ALWAYS);
        left.setPadding(new Insets(0, 16, 0, 0));

        // --- center: favorites table ---------------------------------------------
        table.setId("favmgr-table");
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No favorites"));

        TableColumn<FileFavorite, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                displayName(d.getValue())));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> renameFavorite(e.getRowValue(), e.getNewValue()));
        nameCol.setPrefWidth(220);

        TableColumn<FileFavorite, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getFileType() != null ? d.getValue().getFileType().getDisplayName() : ""));
        typeCol.setEditable(false);
        typeCol.setPrefWidth(110);

        TableColumn<FileFavorite, String> folderCol = new TableColumn<>("Folder");
        folderCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getFolderName() != null && !d.getValue().getFolderName().isBlank()
                        ? d.getValue().getFolderName() : UNCATEGORIZED));
        folderCol.setCellFactory(ComboBoxTableCell.forTableColumn(folderOptions));
        folderCol.setOnEditCommit(e -> moveToFolder(e.getRowValue(), e.getNewValue()));
        folderCol.setPrefWidth(150);

        TableColumn<FileFavorite, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getFilePath()));
        pathCol.setEditable(false);

        table.getColumns().setAll(List.of(nameCol, typeCol, folderCol, pathCol));

        ContextMenu rowMenu = new ContextMenu();
        MenuItem open = new MenuItem("Open", icon("bi-folder2-open", 16));
        open.setOnAction(e -> openSelected());
        MenuItem remove = new MenuItem("Remove from favorites", icon("bi-x-circle", 16));
        remove.setOnAction(e -> removeSelected());
        rowMenu.getItems().addAll(open, new SeparatorMenuItem(), remove);
        table.setContextMenu(rowMenu);
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openSelected();
            }
        });

        BorderPane content = new BorderPane(table);
        content.setLeft(left);
        content.setPadding(new Insets(16, 0, 0, 0));

        BorderPane card = new BorderPane(content);
        card.setTop(header);
        card.getStyleClass().add("fxt-favmgr-card");

        setCenter(card);
        BorderPane.setMargin(card, new Insets(24));
        refresh();
    }

    // ----- actions (package-private: also driven directly by tests) -----------

    /** Renames a favorite (display name + legacy alias) and persists it. */
    void renameFavorite(FileFavorite favorite, String newName) {
        if (favorite == null || newName == null || newName.isBlank()) {
            refreshTable();
            return;
        }
        favorite.setName(newName.strip());
        favorite.setAlias(newName.strip());
        FavoritesService.getInstance().updateFavorite(favorite);
        refresh();
    }

    /** Moves a favorite into {@code folder} ({@link #UNCATEGORIZED} = no folder). */
    void moveToFolder(FileFavorite favorite, String folder) {
        if (favorite == null) {
            return;
        }
        String target = (folder == null || folder.isBlank() || UNCATEGORIZED.equals(folder))
                ? null : folder.strip();
        FavoritesService.getInstance().moveFavoriteToFolder(favorite.getId(), target);
        refresh();
    }

    void createFolder(String name) {
        if (name != null && !name.isBlank()) {
            FavoritesService.getInstance().createFolder(name.strip());
            refresh();
            folderList.getSelectionModel().select(name.strip());
        }
    }

    void renameFolder(String oldName, String newName) {
        if (isRealFolder(oldName) && newName != null && !newName.isBlank()) {
            FavoritesService.getInstance().renameFolder(oldName, newName.strip());
            refresh();
        }
    }

    /** Deletes a folder; its favorites move to {@link #UNCATEGORIZED} (nothing is lost). */
    void deleteFolder(String name) {
        if (isRealFolder(name)) {
            FavoritesService.getInstance().deleteFolder(name);
            refresh();
        }
    }

    void setSearch(String text) {
        search.setText(text != null ? text : "");
    }

    /** @return the favorites currently shown (after folder + search filtering). */
    List<FileFavorite> visibleFavorites() {
        return new ArrayList<>(tableItems);
    }

    String selectedFolder() {
        String selected = folderList.getSelectionModel().getSelectedItem();
        return selected != null ? selected : ALL;
    }

    /** Selects a folder in the FOLDERS list (also used by tests). */
    void selectFolder(String name) {
        folderList.getSelectionModel().select(name);
    }

    /** @return the FOLDERS list entries in display order (for tests/observers). */
    List<String> folderEntries() {
        return new ArrayList<>(folderList.getItems());
    }

    // ----- internals -----------------------------------------------------------

    private static boolean isRealFolder(String name) {
        return name != null && !ALL.equals(name) && !UNCATEGORIZED.equals(name);
    }

    private void openSelected() {
        FileFavorite selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getFilePath() != null) {
            File file = new File(selected.getFilePath());
            if (file.isFile()) {
                editorHost.openFile(file.toPath());
            }
        }
    }

    private void removeSelected() {
        FileFavorite selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            FavoritesService.getInstance().removeFavorite(selected);
            refresh();
        }
    }

    /** Reloads folders + table from the store, preserving the folder selection. */
    void refresh() {
        String keep = folderList.getSelectionModel().getSelectedItem();
        TreeSet<String> folders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            for (String folder : FavoritesService.getInstance().getAllFolders()) {
                if (folder != null && !folder.isBlank()) {
                    folders.add(folder);
                }
            }
        } catch (Throwable ignored) {
            // no favorites store (tests) - empty view
        }
        folders.remove(UNCATEGORIZED);

        List<String> entries = new ArrayList<>();
        entries.add(ALL);
        entries.add(UNCATEGORIZED);
        entries.addAll(folders);
        folderList.getItems().setAll(entries);
        folderList.getSelectionModel().select(entries.contains(keep) ? keep : ALL);

        folderOptions.setAll(entries.subList(1, entries.size())); // without "All"
        refreshTable();
    }

    private void refreshTable() {
        String folder = selectedFolder();
        String query = search.getText() != null ? search.getText().strip().toLowerCase() : "";
        List<FileFavorite> all;
        try {
            all = FavoritesService.getInstance().getAllFavorites();
        } catch (Throwable t) {
            all = List.of();
        }
        List<FileFavorite> shown = new ArrayList<>();
        for (FileFavorite favorite : all) {
            String inFolder = favorite.getFolderName() != null && !favorite.getFolderName().isBlank()
                    ? favorite.getFolderName() : UNCATEGORIZED;
            if (!ALL.equals(folder) && !inFolder.equals(folder)) {
                continue;
            }
            if (!query.isEmpty()
                    && !displayName(favorite).toLowerCase().contains(query)
                    && (favorite.getFilePath() == null
                        || !favorite.getFilePath().toLowerCase().contains(query))) {
                continue;
            }
            shown.add(favorite);
        }
        tableItems.setAll(shown);
    }

    private static String displayName(FileFavorite favorite) {
        return favorite.getName() != null && !favorite.getName().isBlank()
                ? favorite.getName() : new File(favorite.getFilePath()).getName();
    }

    private java.util.Optional<String> prompt(String titleText, String label, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(titleText);
        dialog.setHeaderText(null);
        dialog.setContentText(label);
        return dialog.showAndWait().map(String::strip).filter(s -> !s.isEmpty());
    }

    private Button toolButton(String text, String iconLiteral, String id, Runnable action) {
        Button button = new Button(text, icon(iconLiteral, 14));
        button.setId(id);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
