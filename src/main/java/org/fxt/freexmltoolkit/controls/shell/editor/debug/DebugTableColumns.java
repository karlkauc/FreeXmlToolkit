package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.function.Function;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;

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
}
