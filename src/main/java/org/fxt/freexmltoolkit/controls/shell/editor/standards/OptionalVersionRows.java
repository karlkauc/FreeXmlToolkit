package org.fxt.freexmltoolkit.controls.shell.editor.standards;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import org.fxt.freexmltoolkit.service.standards.StandardVersion;

/**
 * Optional version fields of the project dialogs (binding, scenario): a combo of the standard's values with an
 * empty first entry ("not set"), added as a labelled row at the end of a dialog's grid and shown only in the
 * expert mode.
 */
public final class OptionalVersionRows {

    private OptionalVersionRows() {
    }

    /** A combo of {@code values} after an empty entry, showing {@code current} (blank / unknown = empty). */
    public static ComboBox<String> combo(List<String> values, String current) {
        List<String> items = new ArrayList<>();
        items.add("");
        items.addAll(values);
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().setAll(items);
        String value = current == null ? "" : current.strip();
        combo.setValue(items.contains(value) ? value : "");
        return combo;
    }

    /** The {@link StandardVersion#cliValue()}s of a standard. */
    public static <E extends Enum<E> & StandardVersion> List<String> values(Class<E> type) {
        List<String> values = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            values.add(constant.cliValue());
        }
        return values;
    }

    /** Appends "label | combo" as the grid's next row, visible only in the expert mode. */
    public static void addRow(GridPane grid, String labelText, ComboBox<String> combo, String id) {
        int row = grid.getChildren().stream()
                .mapToInt(n -> {
                    Integer r = GridPane.getRowIndex(n);
                    return r == null ? 0 : r;
                }).max().orElse(-1) + 1;
        Label label = new Label(labelText);
        label.setLabelFor(combo);
        combo.setId(id);
        grid.add(label, 0, row);
        grid.add(combo, 1, row);
        StandardsUi.showInExpertMode(label);
        StandardsUi.showInExpertMode(combo);
    }

    /** The combo's value, {@code null} when empty. */
    public static String value(ComboBox<String> combo) {
        String v = combo.getValue();
        return v == null || v.isBlank() ? null : v;
    }
}
