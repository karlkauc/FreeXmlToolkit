package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

/**
 * Editable table cells of the Schema Overview panes: a text field and an editable combo box
 * whose suggestions depend on the row. Edits commit on Enter, on choosing a suggestion and on
 * focus loss; Escape cancels.
 *
 * @since 2.6
 */
final class OverviewCells {

    private OverviewCells() {
    }

    /** A cell edited with a text field, for rows the predicate allows. */
    static <R> TableCell<R, String> text(Predicate<R> editable) {
        return new TableCell<>() {
            private TextField field;

            @Override
            public void startEdit() {
                R row = getTableRow() != null ? getTableRow().getItem() : null;
                if (row == null || !editable.test(row)) {
                    return;
                }
                super.startEdit();
                if (!isEditing()) {
                    return;
                }
                field = new TextField(getItem());
                field.setOnAction(e -> commitEdit(field.getText()));
                field.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                    }
                });
                field.focusedProperty().addListener((obs, was, focused) -> {
                    if (!focused && isEditing()) {
                        commitEdit(field.getText());
                    }
                });
                setText(null);
                setGraphic(field);
                field.requestFocus();
                field.selectAll();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setGraphic(null);
                if (!empty && isEditing() && field != null) {
                    setText(null);
                    setGraphic(field);
                }
            }
        };
    }

    /**
     * A cell edited with an editable combo box.
     *
     * @param editable    which rows can be edited
     * @param suggestions the values offered for a row
     * @param free        whether values outside the suggestions may be typed
     */
    static <R> TableCell<R, String> combo(Predicate<R> editable, Function<R, List<String>> suggestions, boolean free) {
        return new TableCell<>() {
            private ComboBox<String> combo;
            private boolean committing;

            @Override
            public void startEdit() {
                R row = getTableRow() != null ? getTableRow().getItem() : null;
                if (row == null || !editable.test(row)) {
                    return;
                }
                super.startEdit();
                if (!isEditing()) {
                    return;
                }
                combo = new ComboBox<>();
                combo.getItems().setAll(suggestions.apply(row));
                combo.setEditable(free);
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.getStyleClass().add("fxt-neutral-combo");
                combo.setValue(getItem());
                combo.setOnAction(e -> commit());
                combo.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                    }
                });
                if (free) {
                    combo.getEditor().focusedProperty().addListener((obs, was, focused) -> {
                        if (!focused && isEditing()) {
                            commit();
                        }
                    });
                }
                setText(null);
                setGraphic(combo);
                combo.requestFocus();
                if (free) {
                    combo.getEditor().selectAll();
                } else {
                    combo.show();
                }
            }

            private void commit() {
                if (committing || !isEditing()) {
                    return;
                }
                committing = true;
                try {
                    String value = combo.isEditable() ? combo.getEditor().getText() : combo.getValue();
                    commitEdit(value == null ? "" : value);
                } finally {
                    committing = false;
                }
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setGraphic(null);
                if (!empty && isEditing() && combo != null) {
                    setText(null);
                    setGraphic(combo);
                }
            }
        };
    }
}
