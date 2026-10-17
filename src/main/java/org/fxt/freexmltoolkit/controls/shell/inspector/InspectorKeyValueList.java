package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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
 * on hover when deleting is wired; a focused row is deleted with Delete and edited with Enter.
 * Long values (patterns, URIs) wrap instead of being cut off, and the tooltip always carries the
 * full value. Read-only entries (e.g. facets inherited from a named type) are shown in italics, with
 * their origin in the tooltip, and cannot be edited or deleted.
 *
 * @param <T> the model item behind an entry (facet, attribute name, …)
 */
public class InspectorKeyValueList<T> extends VBox {

    /**
     * One row: the model item, its name and value, whether it is read-only, a tooltip note, an
     * optional small badge after the name (e.g. the schema type of an attribute) and optional value
     * choices (e.g. a schema enumeration — the editor becomes a drop-down).
     */
    public record Entry<T>(T item, String name, String value, boolean readOnly, String tooltip,
                           String badge, List<String> choices) {
        public Entry(T item, String name, String value) {
            this(item, name, value, false, null, null, List.of());
        }

        public Entry(T item, String name, String value, boolean readOnly, String tooltip) {
            this(item, name, value, readOnly, tooltip, null, List.of());
        }

        public Entry {
            choices = choices == null ? List.of() : List.copyOf(choices);
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
        value.setWrapText(true);
        value.setMaxWidth(Double.MAX_VALUE);
        value.setMinWidth(0);
        HBox.setHgrow(value, Priority.ALWAYS);

        HBox row = new HBox(12, name);
        if (entry.badge() != null && !entry.badge().isBlank()) {
            Label badge = new Label(entry.badge());
            badge.getStyleClass().add("fxt-inspector-kv-badge");
            badge.setMinWidth(Region.USE_PREF_SIZE);
            row.getChildren().add(badge);
        }
        row.getChildren().add(value);
        row.getStyleClass().add("fxt-inspector-kv-row");
        if (first) {
            row.getStyleClass().add("fxt-inspector-kv-row-first");
        }
        if (entry.readOnly()) {
            row.getStyleClass().add("fxt-inspector-kv-row-readonly");
        }
        String tip = tooltipText(entry);
        if (!tip.isBlank()) {
            Tooltip.install(row, new Tooltip(tip));
        }
        if (!entry.readOnly()) {
            if (onValueCommit != null) {
                value.getStyleClass().add("fxt-inspector-kv-editable");
                value.setOnMouseClicked(e -> startEdit(row, value, entry.value(), entry.choices(),
                        text -> onValueCommit.accept(entry, text)));
            }
            if (onNameCommit != null) {
                name.getStyleClass().add("fxt-inspector-kv-editable");
                name.setOnMouseClicked(e -> startEdit(row, name, entry.name(), List.of(),
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
                delete.visibleProperty().bind(row.hoverProperty().or(row.focusedProperty()));
                row.getChildren().add(delete);
            }
            // Keyboard path: Tab reaches the row, Enter edits the value, Delete removes it.
            row.setFocusTraversable(onDelete != null || onValueCommit != null);
            row.setOnKeyPressed(e -> {
                if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) && onDelete != null) {
                    onDelete.accept(entry);
                    e.consume();
                } else if (e.getCode() == KeyCode.ENTER && onValueCommit != null) {
                    startEdit(row, value, entry.value(), entry.choices(), text -> onValueCommit.accept(entry, text));
                    e.consume();
                }
            });
        }
        return row;
    }

    /** "value" or "value — note" (e.g. where an inherited facet comes from). */
    static String tooltipText(Entry<?> entry) {
        String value = entry.value() == null ? "" : entry.value();
        String note = entry.tooltip() == null ? "" : entry.tooltip();
        if (value.isBlank()) {
            return note;
        }
        return note.isBlank() ? value : value + " — " + note;
    }

    /**
     * Swaps {@code label} for a text field — or, with {@code choices}, an editable drop-down that
     * offers them; Enter / picking / focus loss commits, Esc restores the label.
     */
    private void startEdit(HBox row, Label label, String original, List<String> choices, Consumer<String> commit) {
        if (!choices.isEmpty()) {
            startChoiceEdit(row, label, original, choices, commit);
            return;
        }
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

    private void startChoiceEdit(HBox row, Label label, String original, List<String> choices,
            Consumer<String> commit) {
        int index = row.getChildren().indexOf(label);
        if (index < 0) {
            return;
        }
        String start = original == null ? "" : original;
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().setAll(choices);
        combo.setEditable(true);
        combo.setValue(start);
        combo.getStyleClass().addAll("fxt-inspector-edit", "fxt-inspector-edit-boxed", "fxt-inspector-kv-editor");
        combo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(combo, Priority.ALWAYS);
        boolean[] done = {false};
        Runnable finish = () -> {
            if (!done[0]) {
                done[0] = true;
                int at = row.getChildren().indexOf(combo);
                if (at >= 0) {
                    row.getChildren().set(at, label);
                }
            }
        };
        java.util.function.Supplier<String> text = () -> {
            String typed = combo.getEditor().getText();
            return typed != null ? typed : combo.getValue() == null ? "" : combo.getValue();
        };
        Runnable accept = () -> {
            if (done[0]) {
                return;
            }
            String value = text.get();
            finish.run();
            if (!value.equals(start)) {
                commit.accept(value);
            }
        };
        // A pick from the list commits at once; typing commits on Enter or focus loss.
        combo.setOnAction(e -> {
            if (combo.getValue() != null) {
                combo.getEditor().setText(combo.getValue());
            }
            if (!combo.isShowing()) {
                accept.run();
            }
        });
        combo.setOnHidden(e -> {
            if (combo.getValue() != null && !combo.getValue().equals(start)) {
                combo.getEditor().setText(combo.getValue());
                accept.run();
            }
        });
        combo.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                finish.run();
                e.consume();
            }
        });
        combo.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow && !combo.isShowing()) {
                accept.run();
            }
        });
        row.getChildren().set(index, combo);
        combo.requestFocus();
        combo.getEditor().selectAll();
    }
}
