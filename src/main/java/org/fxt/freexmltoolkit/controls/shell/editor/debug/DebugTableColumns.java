package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.function.Consumer;
import java.util.function.Function;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;

/** Shared factory for the read-only string {@link TableColumn}s used by the debug views. */
public final class DebugTableColumns {

    private DebugTableColumns() {
    }

    /**
     * Builds a read-only string column with the given header, value extractor, and preferred
     * width (ignored when {@code prefWidth <= 0}).
     */
    public static <T> TableColumn<T, String> col(String title,
            Function<T, String> value, double prefWidth) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }

    /**
     * Like {@link #col(String, Function, double)} but every cell shows its full text as a
     * tooltip — for value columns whose content (sequences, nodes) is wider than the column.
     */
    public static <T> TableColumn<T, String> colWithTooltip(String title,
            Function<T, String> value, double prefWidth) {
        TableColumn<T, String> column = col(title, value, prefWidth);
        column.setCellFactory(c -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(empty ? null : item);
                    setTooltip(null);
                } else {
                    setText(item);
                    tip.setText(item);
                    setTooltip(tip);
                }
            }
        });
        return column;
    }

    /**
     * A value column whose non-empty cells open the row's full value on a primary single click
     * ({@code onOpen}); the cells look clickable (style class {@code fxt-debug-value-cell}) and
     * their tooltip shows the cell text plus the hint.
     */
    public static <T> TableColumn<T, String> valueColumn(String title,
            Function<T, String> value, double prefWidth, Consumer<T> onOpen) {
        return valueColumn(title, value, prefWidth, onOpen, row -> false);
    }

    /** Like {@link #valueColumn(String, Function, double, Consumer)}; rows {@code changed} accepts get {@code fxt-debug-changed}. */
    public static <T> TableColumn<T, String> valueColumn(String title,
            Function<T, String> value, double prefWidth, Consumer<T> onOpen, java.util.function.Predicate<T> changed) {
        TableColumn<T, String> column = col(title, value, prefWidth);
        column.setCellFactory(c -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();

            {
                setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1 && !isEmpty()
                            && getItem() != null && !getItem().isBlank() && getTableRow() != null
                            && getTableRow().getItem() != null) {
                        onOpen.accept(getTableRow().getItem());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("fxt-debug-value-cell", "fxt-debug-changed");
                T row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row != null && changed.test(row)) {
                    getStyleClass().add("fxt-debug-changed");
                }
                if (empty || item == null || item.isBlank()) {
                    setText(empty ? null : item);
                    setTooltip(null);
                } else {
                    setText(item);
                    String preview = item.length() > 400 ? item.substring(0, 400) + "…" : item;
                    tip.setText(preview + "\n\nClick to open the full value");
                    setTooltip(tip);
                    getStyleClass().add("fxt-debug-value-cell");
                }
            }
        });
        return column;
    }
}
