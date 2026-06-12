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
    /** Smart collection: favorites sorted by their last-accessed timestamp. */
    static final String RECENTLY_USED = "Recently Used";
    /** Smart collection: favorites sorted by how often they were opened. */
    static final String MOST_POPULAR = "Most Popular";

    private final EditorHost editorHost;
    private final TextField search = new TextField();
    private final ListView<String> folderList = new ListView<>();
    private final ObservableList<FileFavorite> tableItems = FXCollections.observableArrayList();
    private final TableView<FileFavorite> table = new TableView<>(tableItems);
    private final ObservableList<String> folderOptions = FXCollections.observableArrayList();
    private final Label detailPath = detailValue();
    private final Label detailType = detailValue();
    private final Label detailAdded = detailValue();
    private final Label detailUsage = detailValue();
    private final javafx.scene.control.TextArea notesArea = new javafx.scene.control.TextArea();
    private FileFavorite detailFavorite;
    private boolean populatingDetails;

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
        Button cleanupButton = toolButton("Clean up", "bi-eraser", "favmgr-cleanup", this::cleanup);
        cleanupButton.setTooltip(new javafx.scene.control.Tooltip(
                "Remove favorites whose files no longer exist"));
        HBox header = new HBox(12, new VBox(2, title, subtitle), headerSpacer, search, cleanupButton);
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

        // --- right: DETAILS + NOTES for the selected favorite ----------------------
        Label detailsLabel = new Label("DETAILS");
        detailsLabel.getStyleClass().add("fxt-sp-section-label");
        Label notesLabel = new Label("NOTES");
        notesLabel.getStyleClass().add("fxt-sp-section-label");
        notesArea.setId("favmgr-notes");
        notesArea.setPromptText("Your notes for this favorite…");
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(7);
        notesArea.setDisable(true);
        // Persist on focus loss (typing must not trigger a store write per key).
        notesArea.focusedProperty().addListener((obs, oldV, focused) -> {
            if (!focused) {
                commitNotes();
            }
        });
        VBox details = new VBox(6, detailsLabel,
                detailRow("Path", detailPath), detailRow("Type", detailType),
                detailRow("Added", detailAdded), detailRow("Usage", detailUsage),
                notesLabel, notesArea);
        details.setPrefWidth(280);
        details.setPadding(new Insets(0, 0, 0, 16));
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            commitNotes();
            populateDetails(newV);
        });
        populateDetails(null);

        BorderPane content = new BorderPane(table);
        content.setLeft(left);
        content.setRight(details);
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
        return name != null && !ALL.equals(name) && !UNCATEGORIZED.equals(name)
                && !RECENTLY_USED.equals(name) && !MOST_POPULAR.equals(name);
    }

    private void openSelected() {
        FileFavorite selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getFilePath() != null) {
            File file = new File(selected.getFilePath());
            if (file.isFile()) {
                FavoritesService.getInstance().recordAccess(selected.getFilePath());
                editorHost.openFile(file.toPath());
            }
        }
    }

    /** Persists {@code notes} on the favorite (also driven directly by tests). */
    void saveNotes(FileFavorite favorite, String notes) {
        if (favorite == null) {
            return;
        }
        favorite.setNotes(notes != null && !notes.isBlank() ? notes : null);
        FavoritesService.getInstance().updateFavorite(favorite);
    }

    /** Removes favorites whose files no longer exist (the store's cleanup). */
    void cleanup() {
        try {
            FavoritesService.getInstance().cleanupNonExistentFiles();
        } catch (Throwable ignored) {
            // no store (tests)
        }
        refresh();
    }

    /** Writes the notes editor back to the store when they changed. */
    private void commitNotes() {
        if (detailFavorite != null && !populatingDetails
                && !java.util.Objects.equals(emptyToNull(notesArea.getText()),
                        emptyToNull(detailFavorite.getNotes()))) {
            saveNotes(detailFavorite, notesArea.getText());
        }
    }

    private static String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    /** Fills the DETAILS pane + notes editor for the selected favorite. */
    private void populateDetails(FileFavorite favorite) {
        populatingDetails = true;
        try {
            detailFavorite = favorite;
            if (favorite == null) {
                detailPath.setText("–");
                detailType.setText("–");
                detailAdded.setText("–");
                detailUsage.setText("–");
                notesArea.setText("");
                notesArea.setDisable(true);
                return;
            }
            detailPath.setText(favorite.getFilePath() != null ? favorite.getFilePath() : "–");
            detailType.setText(favorite.getFileType() != null
                    ? favorite.getFileType().getDisplayName() : "–");
            detailAdded.setText(formatDate(favorite.getAddedDate()));
            detailUsage.setText(favorite.getAccessCount() > 0
                    ? favorite.getAccessCount() + "× · last " + formatDate(favorite.getLastAccessed())
                    : "never opened");
            notesArea.setText(favorite.getNotes() != null ? favorite.getNotes() : "");
            notesArea.setDisable(false);
        } finally {
            populatingDetails = false;
        }
    }

    private static String formatDate(java.time.LocalDateTime date) {
        return date != null
                ? date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "–";
    }

    private static Label detailValue() {
        Label label = new Label("–");
        label.getStyleClass().add("fxt-favmgr-detail");
        label.setWrapText(true);
        return label;
    }

    private static VBox detailRow(String key, Label value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("fxt-sig-field-label");
        return new VBox(1, keyLabel, value);
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
        entries.add(RECENTLY_USED);
        entries.add(MOST_POPULAR);
        entries.add(UNCATEGORIZED);
        entries.addAll(folders);
        folderList.getItems().setAll(entries);
        folderList.getSelectionModel().select(entries.contains(keep) ? keep : ALL);

        // Move targets: real folders + Uncategorized (no smart collections).
        List<String> targets = new ArrayList<>();
        targets.add(UNCATEGORIZED);
        targets.addAll(folders);
        folderOptions.setAll(targets);
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
            if (RECENTLY_USED.equals(folder) || MOST_POPULAR.equals(folder)) {
                // Only favorites that were actually opened (getLastAccessed falls
                // back to the added date, so the access count is the real signal).
                if (favorite.getAccessCount() <= 0) {
                    continue;
                }
            } else if (!ALL.equals(folder)) {
                String inFolder = favorite.getFolderName() != null && !favorite.getFolderName().isBlank()
                        ? favorite.getFolderName() : UNCATEGORIZED;
                if (!inFolder.equals(folder)) {
                    continue;
                }
            }
            if (!query.isEmpty()
                    && !displayName(favorite).toLowerCase().contains(query)
                    && (favorite.getFilePath() == null
                        || !favorite.getFilePath().toLowerCase().contains(query))) {
                continue;
            }
            shown.add(favorite);
        }
        if (RECENTLY_USED.equals(folder)) {
            shown.sort(java.util.Comparator.comparing(FileFavorite::getLastAccessed,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        } else if (MOST_POPULAR.equals(folder)) {
            shown.sort(java.util.Comparator.comparingInt(FileFavorite::getAccessCount).reversed());
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
