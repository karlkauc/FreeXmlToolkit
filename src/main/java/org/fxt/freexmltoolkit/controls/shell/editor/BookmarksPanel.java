package org.fxt.freexmltoolkit.controls.shell.editor;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.bookmarks.Bookmark;
import org.fxt.freexmltoolkit.service.bookmarks.BookmarkService;

/**
 * The Bookmarks activity side panel: the line bookmarks of all files, grouped by file, with a
 * filter (name, line text, file name) and "only the active document". A click on a bookmark
 * shows it (opening its file when needed); Enter does the same, Delete removes it, F2 names it.
 * The context menu renames, numbers and removes bookmarks or clears a file. The list follows
 * {@link BookmarkService} live.
 */
public class BookmarksPanel extends VBox {

    /** One list row: a file header ({@code bookmark == null}) or a bookmark of that file. */
    record Row(String key, Bookmark bookmark, int count) {
        boolean isHeader() {
            return bookmark == null;
        }
    }

    private final EditorHost editorHost;
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    /** File keys whose files were not found at the last check (shown greyed). */
    private volatile Set<String> missing = Set.of();
    @FXML private ListView<Row> list;
    @FXML private TextField search;
    @FXML private CheckBox onlyActive;

    public BookmarksPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/BookmarksPanel.fxml");
        list.setItems(rows);
        list.setCellFactory(lv -> new BookmarkCell());
        search.textProperty().addListener((obs, o, n) -> refresh());
        onlyActive.selectedProperty().addListener((obs, o, n) -> refresh());
        editorHost.activeTabProperty().addListener((obs, o, n) -> {
            if (onlyActive.isSelected()) {
                refresh();
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 1) {
                Row row = list.getSelectionModel().getSelectedItem();
                if (row != null) {
                    open(row);
                }
            }
        });
        list.setOnKeyPressed(e -> {
            Row row = list.getSelectionModel().getSelectedItem();
            if (row == null) {
                return;
            }
            if (e.getCode() == KeyCode.ENTER) {
                open(row);
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                remove(row);
                e.consume();
            } else if (e.getCode() == KeyCode.F2 && !row.isHeader()) {
                rename(row);
                e.consume();
            }
        });
        ContextMenu menu = new ContextMenu(new MenuItem("…"));
        menu.setOnShowing(e -> menu.getItems().setAll(menuItems(list.getSelectionModel().getSelectedItem())));
        list.setContextMenu(menu);

        // follows the service as long as the panel exists (the listener lets go of a discarded panel)
        WeakReference<BookmarksPanel> self = new WeakReference<>(this);
        BookmarkService service = BookmarkService.getInstance();
        Consumer<String> listener = new Consumer<>() {
            @Override
            public void accept(String key) {
                BookmarksPanel panel = self.get();
                if (panel == null) {
                    service.removeListener(this);
                } else if (Platform.isFxApplicationThread()) {
                    panel.refresh();
                } else {
                    Platform.runLater(panel::refresh);
                }
            }
        };
        service.addListener(listener);
        refresh();
        checkMissingFiles();
    }

    // ----- actions ------------------------------------------------------------------------------

    /** Sets or removes the bookmark on the caret line of the active document. */
    @FXML
    public void toggleAtCaret() {
        editorHost.toggleBookmarkAtCaret();
    }

    /** Forgets the bookmarks of files that no longer exist. */
    @FXML
    public void removeMissing() {
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            BookmarkService.getInstance().removeMissingFiles();
            Platform.runLater(this::checkMissingFiles);
        });
    }

    /** Removes the bookmarks of every file, after asking. */
    @FXML
    public void clearAll() {
        int n = BookmarkService.getInstance().count();
        if (n > 0 && editorHost.bookmarks.confirm(I18n.t("Bookmarks.clear-all-header", n))) {
            BookmarkService.getInstance().clearAll();
        }
    }

    private void open(Row row) {
        if (row.isHeader()) {
            List<Bookmark> marks = BookmarkService.getInstance().forKey(row.key());
            if (!marks.isEmpty()) {
                editorHost.openBookmark(row.key(), marks.getFirst().line());
            }
        } else {
            editorHost.openBookmark(row.key(), row.bookmark().line());
        }
    }

    private void rename(Row row) {
        Bookmark b = row.bookmark();
        editorHost.bookmarks.promptName(I18n.t("Bookmarks.rename-header", b.line()), b.name())
                .ifPresent(name -> BookmarkService.getInstance().rename(row.key(), b.line(), name));
    }

    private void remove(Row row) {
        if (row.isHeader()) {
            clearFile(row.key());
        } else {
            BookmarkService.getInstance().remove(row.key(), row.bookmark().line());
        }
    }

    private void clearFile(String key) {
        int n = BookmarkService.getInstance().forKey(key).size();
        if (n > 0 && editorHost.bookmarks.confirm(I18n.t("Bookmarks.clear-header", n, fileName(key)))) {
            BookmarkService.getInstance().clear(key);
        }
    }

    private List<MenuItem> menuItems(Row row) {
        if (row == null) {
            MenuItem none = new MenuItem(I18n.t("BookmarksPanel.select-first"));
            none.setDisable(true);
            return List.of(none);
        }
        MenuItem open = item(I18n.t("BookmarksPanel.open"), "bi-box-arrow-up-right", ActionColor.NAVIGATE);
        open.setOnAction(e -> open(row));
        if (row.isHeader()) {
            MenuItem clear = item(I18n.t("Bookmarks.clear-document"), "bi-trash", ActionColor.DELETE);
            clear.setOnAction(e -> clearFile(row.key()));
            return List.of(open, new SeparatorMenuItem(), clear);
        }
        Bookmark b = row.bookmark();
        MenuItem rename = item(I18n.t("Bookmarks.rename"), "bi-pencil", ActionColor.MODIFY);
        rename.setOnAction(e -> rename(row));
        Menu number = new Menu(I18n.t("Bookmarks.number"), icon("bi-123", ActionColor.STRUCTURE));
        MenuItem noNumber = new MenuItem(I18n.t("Bookmarks.number-none"));
        noNumber.setDisable(b.number() == 0);
        noNumber.setOnAction(e -> BookmarkService.getInstance().setNumber(row.key(), b.line(), 0, null));
        number.getItems().add(noNumber);
        for (int n = 1; n <= 9; n++) {
            int digit = n;
            String taken = BookmarkService.getInstance().byNumber(n).map(l -> " — " + l.bookmark().label()).orElse("");
            MenuItem item = new MenuItem(n + taken);
            item.setOnAction(e -> BookmarkService.getInstance().setNumber(row.key(), b.line(), digit, null));
            number.getItems().add(item);
        }
        MenuItem remove = item(I18n.t("Bookmarks.remove"), "bi-bookmark-x", ActionColor.DELETE);
        remove.setOnAction(e -> remove(row));
        return List.of(open, rename, number, new SeparatorMenuItem(), remove);
    }

    // ----- rows ---------------------------------------------------------------------------------

    /** Rebuilds the rows from the service, honouring the filter and "only the active document". */
    public void refresh() {
        Row selected = list.getSelectionModel().getSelectedItem();
        list.getSelectionModel().clearSelection();
        String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        String activeKey = editorHost.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                ? EditorBookmarks.keyOf(et) : null;
        BookmarkService service = BookmarkService.getInstance();
        List<Row> next = new ArrayList<>();
        for (String key : service.keys()) {
            if (onlyActive.isSelected() && !key.equals(activeKey)) {
                continue;
            }
            boolean fileMatches = !query.isEmpty() && fileName(key).toLowerCase(Locale.ROOT).contains(query);
            List<Bookmark> marks = service.forKey(key).stream()
                    .filter(b -> query.isEmpty() || fileMatches || matches(b, query))
                    .toList();
            if (marks.isEmpty()) {
                continue;
            }
            next.add(new Row(key, null, marks.size()));
            marks.forEach(b -> next.add(new Row(key, b, 0)));
        }
        rows.setAll(next);
        if (selected != null) {
            for (Row r : next) {
                if (r.key().equals(selected.key()) && (r.isHeader() ? selected.isHeader()
                        : !selected.isHeader() && r.bookmark().line() == selected.bookmark().line())) {
                    list.getSelectionModel().select(r);
                    break;
                }
            }
        }
    }

    private static boolean matches(Bookmark b, String query) {
        return (b.name() != null && b.name().toLowerCase(Locale.ROOT).contains(query))
                || b.lineText().toLowerCase(Locale.ROOT).contains(query)
                || String.valueOf(b.line()).equals(query);
    }

    /** Looks up which files are gone, off the FX thread (they may be on a slow network drive). */
    private void checkMissingFiles() {
        List<String> keys = BookmarkService.getInstance().keys();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            Set<String> gone = new HashSet<>();
            for (String k : keys) {
                if (!Files.exists(Path.of(k))) {
                    gone.add(k);
                }
            }
            if (!gone.equals(missing)) {
                missing = Set.copyOf(gone);
                Platform.runLater(list::refresh);
            }
        });
    }

    /** @return the rows shown (tests). */
    List<Row> rows() {
        return List.copyOf(rows);
    }

    /** Sets the filter (tests). */
    void setFilter(String text) {
        search.setText(text == null ? "" : text);
    }

    /** Opens a row as a click would (tests). */
    void openRow(Row row) {
        open(row);
    }

    static String fileName(String key) {
        Path p = Path.of(key);
        return p.getFileName() == null ? key : p.getFileName().toString();
    }

    private static MenuItem item(String text, String literal, ActionColor color) {
        return new MenuItem(text, icon(literal, color));
    }

    private static IconifyIcon icon(String literal, ActionColor color) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        return SemanticIcon.paint(icon, color);
    }

    /** A file header (name, count; greyed when the file is gone) or a bookmark (marker, name, line, text). */
    private final class BookmarkCell extends ListCell<Row> {
        BookmarkCell() {
            setPrefWidth(0); // as wide as the list: long line texts end with "…" instead of scrolling sideways
        }

        @Override
        protected void updateItem(Row item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("fxt-bookmarks-file", "fxt-bookmarks-missing");
            setText(null);
            setTooltip(null);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            if (item.isHeader()) {
                getStyleClass().add("fxt-bookmarks-file");
                boolean gone = missing.contains(item.key());
                if (gone) {
                    getStyleClass().add("fxt-bookmarks-missing");
                }
                IconifyIcon icon = new IconifyIcon(gone ? "bi-file-earmark-x" : "bi-file-earmark-text");
                icon.setIconSize(14);
                Label name = new Label(fileName(item.key()));
                name.getStyleClass().add("fxt-bookmarks-file-name");
                name.setMinWidth(0);
                Label count = new Label(String.valueOf(item.count()));
                count.getStyleClass().add("fxt-bookmarks-count");
                count.setMinWidth(javafx.scene.control.Control.USE_PREF_SIZE);
                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);
                HBox box = new HBox(6, icon, name, gap, count);
                box.setMinWidth(0);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
                setTooltip(new Tooltip(item.key() + (gone ? "\n" + I18n.t("BookmarksPanel.file-missing") : "")));
                return;
            }
            Bookmark b = item.bookmark();
            Node marker = marker(b);
            Label title = new Label(b.name() != null ? b.name() : I18n.t("Bookmarks.line", b.line()));
            title.getStyleClass().add("fxt-bookmarks-title");
            Label detail = new Label((b.name() != null ? I18n.t("Bookmarks.line", b.line()) + " · " : "")
                    + (b.lineText().isEmpty() ? I18n.t("BookmarksPanel.empty-line") : b.lineText()));
            detail.getStyleClass().addAll("fxt-bookmarks-detail", "fxt-mono-text");
            title.setMinWidth(0);
            detail.setMinWidth(0);
            VBox text = new VBox(1, title, detail);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox box = new HBox(6, marker, text);
            box.setMinWidth(0);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new javafx.geometry.Insets(0, 0, 0, 14));
            setGraphic(box);
            setTooltip(new Tooltip(BookmarkGutter.tooltip(b).lines().limit(2)
                    .reduce((a, c) -> a + "\n" + c).orElse("") + "\n" + b.lineText()));
        }

        private static Node marker(Bookmark b) {
            if (b.number() > 0) {
                Label badge = new Label(String.valueOf(b.number()));
                badge.getStyleClass().add("fxt-bookmark-badge");
                if (b.orphaned()) {
                    badge.getStyleClass().add("fxt-bookmark-badge-orphaned");
                }
                StackPane pane = new StackPane(badge);
                pane.setMinWidth(14);
                return pane;
            }
            IconifyIcon icon = new IconifyIcon("bi-bookmark-fill");
            icon.setIconSize(12);
            // bound: the list's CSS icon colour would repaint a set colour
            return SemanticIcon.bind(icon, b.orphaned() ? DesignTokens.ColorToken.WARNING : DesignTokens.ColorToken.INDIGO);
        }
    }
}
