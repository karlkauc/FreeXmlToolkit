package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;

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

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;

/**
 * The Favorites activity side panel: lists saved file favorites, opens one on
 * click, and adds the active document. Reuses {@link FavoritesService} (the same
 * store as the rest of the app, so favorites are shared).
 */
public class FavoritesActivityPanel extends VBox {

    private final EditorHost editorHost;
    private final ObservableList<FileFavorite> favorites = FXCollections.observableArrayList();

    public FavoritesActivityPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("FAVORITES");
        title.getStyleClass().add("fxt-side-panel-title");

        Button addCurrent = new Button("Add current", icon("bi-star"));
        addCurrent.getStyleClass().add("fxt-tool-button");
        addCurrent.setOnAction(e -> addCurrent());

        ListView<FileFavorite> list = new ListView<>(favorites);
        list.getStyleClass().add("fxt-open-editors");
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPlaceholder(new Label("No favorites"));
        list.setCellFactory(lv -> new FavoriteCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.getFilePath() != null) {
                editorHost.openFile(Path.of(newV.getFilePath()));
            }
        });

        ContextMenu menu = new ContextMenu();
        MenuItem remove = new MenuItem("Remove", icon("bi-trash"));
        remove.setOnAction(e -> {
            FileFavorite selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                FavoritesService.getInstance().removeFavorite(selected);
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

    /** @return the number of favorites currently listed (for tests/observers). */
    public int getFavoriteCount() {
        return favorites.size();
    }

    private void refresh() {
        favorites.setAll(FavoritesService.getInstance().getAllFavorites());
    }

    private IconifyIcon icon(String literal) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return icon;
    }

    /** Renders a favorite with a star icon and its name. */
    private static final class FavoriteCell extends ListCell<FileFavorite> {
        @Override
        protected void updateItem(FileFavorite item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.getName() != null ? item.getName() : item.getFileName());
            IconifyIcon icon = new IconifyIcon("bi-star-fill");
            icon.setIconSize(13);
            setGraphic(icon);
        }
    }
}
