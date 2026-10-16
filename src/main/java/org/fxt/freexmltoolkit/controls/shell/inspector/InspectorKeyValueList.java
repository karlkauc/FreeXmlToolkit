package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * The inspector's framed name/value list (Figma "FACETS" box): one row per entry with the name in
 * the code-attribute colour on the left and the value in the code-value colour on the right.
 * <p>
 * Editing happens in place: a click on a value (or, when renaming is wired, on a name) swaps the
 * label for a text field — Enter or leaving the field commits, Esc cancels. A trash button appears
 * on hover when deleting is wired. Read-only entries (e.g. facets inherited from a named type) are
 * shown in italics with a tooltip and cannot be edited or deleted.
 *
 * @param <T> the model item behind an entry (facet, attribute name, …)
 */
public class InspectorKeyValueList<T> extends VBox {

    /** One row: the model item, its name and value, and whether it is read-only. */
    public record Entry<T>(T item, String name, String value, boolean readOnly, String tooltip) {
        public Entry(T item, String name, String value) {
            this(item, name, value, false, null);
        }
    }

    private final List<Entry<T>> entries = new ArrayList<>();
    private final Label placeholder = new Label();
    private BiConsumer<Entry<T>, String> onValueCommit;
    private BiConsumer<Entry<T>, String> onNameCommit;
    private Consumer<Entry<T>> onDelete;

    public InspectorKeyValueList() {
        getStyleClass().add("fxt-inspector-codebox");
        placeholder.getStyleClass().add("fxt-inspector-kv-placeholder");
        setPlaceholderText("None");
        getChildren().setAll(placeholder);
    }

    /** @param text shown inside the box while the list is empty */
    public void setPlaceholderText(String text) {
        placeholder.setText(text);
    }

    /** @param handler receives an edited value (only for editable entries) */
    public void setOnValueCommit(BiConsumer<Entry<T>, String> handler) {
        this.onValueCommit = handler;
    }

    /** @param handler receives an edited name; without it names are not editable */
    public void setOnNameCommit(BiConsumer<Entry<T>, String> handler) {
        this.onNameCommit = handler;
    }

    /** @param handler deletes an entry; without it no trash button is shown */
    public void setOnDelete(Consumer<Entry<T>> handler) {
        this.onDelete = handler;
    }

    /** Replaces the rows. */
    public void setItems(List<Entry<T>> items) {
        entries.clear();
        entries.addAll(items);
        List<Node> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            rows.add(row(entries.get(i), i == 0));
        }
        if (rows.isEmpty()) {
            getChildren().setAll(placeholder);
        } else {
            getChildren().setAll(rows);
        }
    }

    /** @return the entries shown */
    public List<Entry<T>> getItems() {
        return List.copyOf(entries);
    }

    /** @return the number of rows */
    public int size() {
        return entries.size();
    }

    /** @return the number of read-only rows */
    public int readOnlyCount() {
        return (int) entries.stream().filter(Entry::readOnly).count();
    }

    private HBox row(Entry<T> entry, boolean first) {
        Label name = new Label(entry.name() == null ? "" : entry.name());
        name.getStyleClass().add("fxt-inspector-kv-name");
        name.setMinWidth(Region.USE_PREF_SIZE);

        Label value = new Label(entry.value() == null ? "" : entry.value());
        value.getStyleClass().add("fxt-inspector-kv-value");
        value.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        value.setMaxWidth(Double.MAX_VALUE);
        value.setMinWidth(0);
        HBox.setHgrow(value, Priority.ALWAYS);

        HBox row = new HBox(12, name, value);
        row.getStyleClass().add("fxt-inspector-kv-row");
        if (first) {
            row.getStyleClass().add("fxt-inspector-kv-row-first");
        }
        if (entry.readOnly()) {
            row.getStyleClass().add("fxt-inspector-kv-row-readonly");
        }
        String tip = entry.tooltip() != null ? entry.tooltip() : entry.value();
        if (tip != null && !tip.isBlank()) {
            Tooltip.install(row, new Tooltip(tip));
        }
        if (!entry.readOnly()) {
            if (onValueCommit != null) {
                value.getStyleClass().add("fxt-inspector-kv-editable");
                value.setOnMouseClicked(e -> startEdit(row, value, entry.value(),
                        text -> onValueCommit.accept(entry, text)));
            }
            if (onNameCommit != null) {
                name.getStyleClass().add("fxt-inspector-kv-editable");
                name.setOnMouseClicked(e -> startEdit(row, name, entry.name(),
                        text -> onNameCommit.accept(entry, text)));
            }
            if (onDelete != null) {
                Button delete = new Button();
                IconifyIcon icon = new IconifyIcon("bi-trash");
                icon.setIconSize(12);
                delete.setGraphic(icon);
                delete.getStyleClass().addAll("fxt-inspector-icon-button", "fxt-inspector-kv-delete");
                delete.setTooltip(new Tooltip("Delete " + entry.name()));
                delete.setOnAction(e -> onDelete.accept(entry));
                // Shown on hover only, but always keeps its space so rows do not jump.
                delete.visibleProperty().bind(row.hoverProperty());
                row.getChildren().add(delete);
            }
        }
        return row;
    }

    /** Swaps {@code label} for a text field; Enter / focus loss commits, Esc restores the label. */
    private void startEdit(HBox row, Label label, String original, Consumer<String> commit) {
        int index = row.getChildren().indexOf(label);
        if (index < 0) {
            return;
        }
        TextField field = new TextField(original == null ? "" : original);
        field.getStyleClass().addAll("fxt-inspector-edit", "fxt-inspector-kv-editor");
        HBox.setHgrow(field, Priority.ALWAYS);
        boolean[] done = {false};
        Runnable finish = () -> {
            if (!done[0]) {
                done[0] = true;
                row.getChildren().set(row.getChildren().indexOf(field), label);
            }
        };
        field.setOnAction(e -> {
            String text = field.getText();
            finish.run();
            if (!text.equals(original)) {
                commit.accept(text);
            }
        });
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                finish.run();
                e.consume();
            }
        });
        field.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow && !done[0]) {
                String text = field.getText();
                finish.run();
                if (!text.equals(original)) {
                    commit.accept(text);
                }
            }
        });
        row.getChildren().set(index, field);
        field.requestFocus();
        field.selectAll();
    }
}
