package org.fxt.freexmltoolkit.controls.shell;

import java.util.function.Function;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;

/**
 * Binds the value side of table columns that are declared in FXML: header text, width and
 * order live in the layout file, the value extractor stays in code.
 */
public final class FxmlColumns {

    private FxmlColumns() {
    }

    /** Makes {@code column} a read-only text column showing {@code value}; {@code null} renders empty. */
    public static <T> void text(TableColumn<T, String> column, Function<T, String> value) {
        column.setCellValueFactory(c -> {
            String text = value.apply(c.getValue());
            return new ReadOnlyStringWrapper(text == null ? "" : text);
        });
    }
}
