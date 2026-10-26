package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * A small modal dialog around a {@link TypePickerField} for the "Change Type…" context-menu
 * actions of the diagram and the tree.
 */
public final class TypePickerDialog {

    private TypePickerDialog() {
    }

    /**
     * @param owner   the owner window, may be {@code null}
     * @param title   the dialog title
     * @param header  the header text
     * @param current the current type reference, pre-filled and pre-highlighted
     * @param choices supplies the entries to offer
     * @return the entered type reference (trimmed, may be blank) when confirmed
     */
    public static Optional<String> show(Window owner, String title, String header, String current,
                                        Supplier<List<TypeChoice>> choices) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        TypePickerField field = new TypePickerField();
        field.setId("type-picker-dialog-field");
        field.setChoices(choices);
        field.setValue(current);
        field.setPrefWidth(380);

        Label label = new Label("Type");
        label.getStyleClass().add("fxt-type-picker-dialog-label");
        VBox content = new VBox(6, label, field);
        content.setPadding(new Insets(12, 16, 4, 16));

        DialogPane pane = dialog.getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        pane.getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? (field.getEditor().getText() == null ? "" : field.getEditor().getText().trim())
                : null);
        dialog.setOnShown(e -> {
            field.getEditor().requestFocus();
            field.getEditor().selectAll();
        });
        dialog.setOnHiding(e -> field.hidePopup());
        return dialog.showAndWait();
    }
}
