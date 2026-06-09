package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.function.Function;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;

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
}
