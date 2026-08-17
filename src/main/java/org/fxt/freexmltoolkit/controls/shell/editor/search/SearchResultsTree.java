package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;

/**
 * Grouped file → matches result tree shared by the Text and XPath search modes.
 * Rows carry checkboxes (tri-state on file rows via {@link CheckBoxTreeItem})
 * so a replace can exclude individual matches; selecting a match row navigates
 * to it via the {@link #setOnNavigate(Consumer)} callback.
 */
public class SearchResultsTree extends TreeView<SearchResultsTree.Row> {

    /** A row in the tree: either a file header or one match. */
    public sealed interface Row permits FileRow, MatchRow, NoteRow {
    }

    /** File header row: match count badge, or an error message for unsearchable files. */
    public record FileRow(Path file, int matchCount, String error) implements Row {
    }

    /**
     * One match: char offsets {@code [start, end)} into the searched text of
     * {@code file}, its 1-based line, the line text for display, the matched
     * substring (drift re-verification on navigate), and an optional payload
     * the owning pane attaches (e.g. a planner handle).
     */
    public record MatchRow(Path file, int lineNumber, String lineText,
                           int start, int end, String matched, Object payload) implements Row {
    }

    /** Informational child row, e.g. "matches truncated". */
    public record NoteRow(String text) implements Row {
    }

    private Consumer<MatchRow> onNavigate;

    public SearchResultsTree() {
        getStyleClass().add("fxt-search-results");
        setShowRoot(false);
        setRoot(new CheckBoxTreeItem<>(null));
        setCellFactory(tv -> new ResultCell());
        getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.getValue() instanceof MatchRow match && onNavigate != null) {
                onNavigate.accept(match);
            }
        });
    }

    /** Sets the callback fired when the user selects a match row. */
    public void setOnNavigate(Consumer<MatchRow> onNavigate) {
        this.onNavigate = onNavigate;
    }

    /**
     * Replaces the tree content. Each entry becomes a checked (pre-selected)
     * expanded file node with its match children.
     */
    public void setResults(List<FileEntry> entries) {
        // Clear the selection first: replacing items under a selected row triggers
        // the JavaFX ListViewBehavior IndexOutOfBounds (see CLAUDE.md gotchas).
        getSelectionModel().clearSelection();
        CheckBoxTreeItem<Row> root = new CheckBoxTreeItem<>(null);
        for (FileEntry entry : entries) {
            CheckBoxTreeItem<Row> fileItem = new CheckBoxTreeItem<>(
                    new FileRow(entry.file(), entry.matches().size(), entry.error()));
            for (MatchRow match : entry.matches()) {
                CheckBoxTreeItem<Row> matchItem = new CheckBoxTreeItem<>(match);
                matchItem.setSelected(true);
                fileItem.getChildren().add(matchItem);
            }
            if (entry.truncated()) {
                // Plain TreeItem: a note must not participate in the checkbox tri-state.
                fileItem.getChildren().add(new TreeItem<>(
                        new NoteRow("More matches not shown (limit reached)")));
            }
            if (entry.note() != null) {
                fileItem.getChildren().add(new TreeItem<>(new NoteRow(entry.note())));
            }
            fileItem.setExpanded(entries.size() <= 10);
            root.getChildren().add(fileItem);
        }
        setRoot(root);
    }

    /** One file's worth of results, as produced by the owning pane. */
    public record FileEntry(Path file, String error, boolean truncated, String note,
                            List<MatchRow> matches) {

        public FileEntry(Path file, String error, boolean truncated, List<MatchRow> matches) {
            this(file, error, truncated, null, matches);
        }
    }

    /** Clears all results. */
    public void clearResults() {
        getSelectionModel().clearSelection();
        setRoot(new CheckBoxTreeItem<>(null));
    }

    /** @return the match rows whose checkbox is still checked (replace scope). */
    public List<MatchRow> getCheckedMatches() {
        List<MatchRow> checked = new ArrayList<>();
        collectChecked(getRoot(), checked);
        return checked;
    }

    private static void collectChecked(TreeItem<Row> item, List<MatchRow> out) {
        if (item instanceof CheckBoxTreeItem<Row> cb
                && cb.getValue() instanceof MatchRow match && cb.isSelected()) {
            out.add(match);
        }
        for (TreeItem<Row> child : item.getChildren()) {
            collectChecked(child, out);
        }
    }

    // ---------------------------------------------------------------------

    /** Custom cell: checkbox + type icon + name + badge (files), line + text (matches). */
    private final class ResultCell extends TreeCell<Row> {

        private ResultCell() {
            setPrefWidth(0); // ellipsize instead of forcing a horizontal scrollbar
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(null);
            switch (row) {
                case FileRow file -> setGraphic(fileRow(file));
                case MatchRow match -> setGraphic(matchRow(match));
                case NoteRow note -> {
                    Label label = new Label(note.text());
                    label.getStyleClass().add("fxt-placeholder-text");
                    setGraphic(label);
                }
            }
        }

        private HBox fileRow(FileRow file) {
            String name = file.file().getFileName().toString();
            EditorFileType type = EditorFileType.fromFileName(name);
            IconifyIcon icon = new IconifyIcon(type.icon());
            icon.setIconSize(14);
            icon.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(
                    javafx.scene.paint.Color.web(type.color())));
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("fxt-search-file-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label badge;
            if (file.error() != null) {
                badge = new Label("!");
                badge.getStyleClass().add("fxt-badge-danger");
                nameLabel.setTooltip(new javafx.scene.control.Tooltip(file.error()));
            } else {
                badge = new Label(String.valueOf(file.matchCount()));
                badge.getStyleClass().add("fxt-search-count-badge");
            }
            HBox box = new HBox(6, checkbox(), icon, nameLabel, spacer, badge);
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        }

        private HBox matchRow(MatchRow match) {
            Label line = new Label(String.valueOf(match.lineNumber()));
            line.getStyleClass().add("fxt-search-line-number");
            Label text = new Label(match.lineText().strip());
            text.getStyleClass().add("fxt-search-line-text");
            HBox box = new HBox(6, checkbox(), line, text);
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        }

        /** A checkbox bound to the row's {@link CheckBoxTreeItem} tri-state selection. */
        private CheckBox checkbox() {
            CheckBox box = new CheckBox();
            if (getTreeItem() instanceof CheckBoxTreeItem<Row> item) {
                box.selectedProperty().bindBidirectional(item.selectedProperty());
                box.indeterminateProperty().bindBidirectional(item.indeterminateProperty());
            } else {
                box.setVisible(false);
                box.setManaged(false);
            }
            return box;
        }
    }
}
