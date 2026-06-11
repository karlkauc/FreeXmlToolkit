package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.Comparator;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;

/**
 * The Favorites activity side panel: lists saved file favorites grouped by file
 * type (XML, XSD, …) with one type-colored icon per entry, opens one on click,
 * and adds the active document. Reuses {@link FavoritesService} (the same store
 * as the rest of the app, so favorites are shared).
 */
public class FavoritesActivityPanel extends VBox {

    /** One list row: either a favorite, or a type-group header ({@code favorite == null}). */
    record Row(FileFavorite favorite, FileFavorite.FileType header) {
        static Row of(FileFavorite favorite) {
            return new Row(favorite, null);
        }

        static Row header(FileFavorite.FileType type) {
            return new Row(null, type);
        }

        boolean isHeader() {
            return favorite == null;
        }
    }

    private final EditorHost editorHost;
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final ListView<Row> list = new ListView<>(rows);

    public FavoritesActivityPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("FAVORITES");
        title.getStyleClass().add("fxt-side-panel-title");

        Button addCurrent = new Button("Add current", icon("bi-star"));
        addCurrent.getStyleClass().add("fxt-tool-button");
        addCurrent.setOnAction(e -> addCurrent());

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
                Platform.runLater(() -> editorHost.openFile(Path.of(path)));
            }
        });

        ContextMenu menu = new ContextMenu();
        MenuItem remove = new MenuItem("Remove", icon("bi-trash"));
        remove.setOnAction(e -> {
            Row selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.isHeader()) {
                FavoritesService.getInstance().removeFavorite(selected.favorite());
                refresh();
            }
        });
        menu.getItems().add(remove);
        list.setContextMenu(menu);

        getChildren().addAll(title, new HBox(addCurrent), list);
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

    /** @return the number of favorites currently listed (group headers excluded). */
    public int getFavoriteCount() {
        return (int) rows.stream().filter(r -> !r.isHeader()).count();
    }

    /** @return the group-header texts in display order (for tests/observers). */
    public java.util.List<String> groupHeaderTexts() {
        return rows.stream().filter(Row::isHeader)
                .map(r -> r.header().getDisplayName()).toList();
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

    /** Rebuilds the rows: one header per file type (enum order), favorites sorted by name. */
    private void refresh() {
        list.getSelectionModel().clearSelection();
        var byType = FavoritesService.getInstance().getAllFavorites().stream()
                .collect(java.util.stream.Collectors.groupingBy(FileFavorite::getFileType));
        java.util.List<Row> next = new java.util.ArrayList<>();
        for (FileFavorite.FileType type : FileFavorite.FileType.values()) {
            var group = byType.get(type);
            if (group == null || group.isEmpty()) {
                continue;
            }
            group.sort(Comparator.comparing(
                    f -> f.getName() != null ? f.getName() : f.getFileName(),
                    String.CASE_INSENSITIVE_ORDER));
            next.add(Row.header(type));
            group.forEach(f -> next.add(Row.of(f)));
        }
        rows.setAll(next);
    }

    private IconifyIcon icon(String literal) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return icon;
    }

    /** Renders a type-group header, or a favorite with its type-colored file icon. */
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
                setText(item.header().getDisplayName().toUpperCase(java.util.Locale.ROOT));
                setGraphic(null);
                getStyleClass().add("fxt-favorites-group");
                return;
            }
            FileFavorite favorite = item.favorite();
            setText(favorite.getName() != null ? favorite.getName() : favorite.getFileName());
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
    }
}
