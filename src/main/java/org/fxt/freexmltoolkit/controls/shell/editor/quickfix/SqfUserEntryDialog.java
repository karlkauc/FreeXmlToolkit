package org.fxt.freexmltoolkit.controls.shell.editor.quickfix;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfUserEntry;

/**
 * Prompts the user for the {@code sqf:user-entry} values a quick fix needs:
 * one labeled text field per entry (pre-filled with the evaluated {@code @default}),
 * OK enabled only when every field has a value.
 */
public final class SqfUserEntryDialog {

    private SqfUserEntryDialog() {
    }

    /**
     * Shows the modal prompt. Call on the FX thread.
     *
     * @param fixTitle the fix's title (dialog header)
     * @param entries  the entries to prompt for
     * @param defaults pre-filled values by entry name (may be empty)
     * @return the entered values by name, or empty when cancelled
     */
    public static Optional<Map<String, String>> prompt(String fixTitle, List<SqfUserEntry> entries,
                                                       Map<String, String> defaults) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Quick Fix");
        dialog.setHeaderText(fixTitle);
        dialog.setGraphic(QuickFixIcons.lightbulb(24));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));

        Map<String, TextField> fields = new LinkedHashMap<>();
        int row = 0;
        for (SqfUserEntry entry : entries) {
            Label label = new Label(entry.title());
            TextField field = new TextField(defaults.getOrDefault(entry.name(), ""));
            field.setPrefColumnCount(24);
            if (!entry.description().isBlank()) {
                field.setTooltip(new javafx.scene.control.Tooltip(entry.description()));
            }
            fields.put(entry.name(), field);
            grid.add(label, 0, row);
            grid.add(field, 1, row);
            if (!entry.description().isBlank()) {
                Label hint = new Label(entry.description());
                hint.getStyleClass().add("fxt-placeholder-text");
                hint.setWrapText(true);
                grid.add(hint, 1, ++row);
            }
            row++;
        }
        dialog.getDialogPane().setContent(grid);

        BooleanBinding anyEmpty = Bindings.createBooleanBinding(
                () -> fields.values().stream().anyMatch(f -> f.getText().isBlank()),
                fields.values().stream().map(TextField::textProperty).toArray(javafx.beans.Observable[]::new));
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(anyEmpty);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            Map<String, String> values = new LinkedHashMap<>();
            fields.forEach((name, field) -> values.put(name, field.getText()));
            return values;
        });
        if (!fields.isEmpty()) {
            Platform.runLater(() -> fields.values().iterator().next().requestFocus());
        }
        return dialog.showAndWait();
    }
}
