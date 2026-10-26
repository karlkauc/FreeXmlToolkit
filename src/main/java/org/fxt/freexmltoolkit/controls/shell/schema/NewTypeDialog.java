package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.Optional;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddGlobalTypeCommand.Kind;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Asks for the kind and name of a new top-level type. The name is checked while typing
 * ({@link XsdEditRules#addGlobalType}); OK stays closed while it is invalid or taken.
 *
 * <p>Tests install {@link #setResponder} so no modal dialog opens.
 */
public class NewTypeDialog extends Dialog<NewTypeDialog.NewType> {

    /** The user's answer. */
    public record NewType(Kind kind, String name) {
    }

    private static Function<Kind, Optional<NewType>> responder;

    @FXML private ToggleGroup kindGroup;
    @FXML private RadioButton complexKind;
    @FXML private RadioButton simpleKind;
    @FXML private TextField nameField;
    @FXML private Label errorLabel;
    @FXML private Label hintLabel;

    private final XsdSchema schema;
    private String suggestedName;

    /**
     * @param schema the schema the type goes into
     * @param kind   the preselected kind
     */
    public NewTypeDialog(XsdSchema schema, Kind kind) {
        this.schema = schema;
        setTitle("New Type");
        setHeaderText("Declare a new top-level type in this schema.");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/NewTypeDialog.fxml"));
        getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());

        (kind == Kind.SIMPLE ? simpleKind : complexKind).setSelected(true);
        suggestedName = suggestion(kind);
        nameField.setText(suggestedName);
        updateHint();
        kindGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            // Follow the kind with the suggestion while the user has not typed a name of their own.
            if (nameField.getText().equals(suggestedName)) {
                suggestedName = suggestion(selectedKind());
                nameField.setText(suggestedName);
            }
            updateHint();
        });
        nameField.textProperty().addListener((obs, o, n) -> validate());
        validate();
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (validate() != null) {
                e.consume();
            }
        });
        setOnShown(e -> Platform.runLater(() -> {
            nameField.requestFocus();
            nameField.selectAll();
        }));
        setResultConverter(button -> button == ButtonType.OK
                ? new NewType(selectedKind(), nameField.getText().trim()) : null);
    }

    /** @param testResponder answers instead of the dialog ({@code null} = dialog) */
    public static void setResponder(Function<Kind, Optional<NewType>> testResponder) {
        responder = testResponder;
    }

    /**
     * Shows the dialog (or asks the test responder).
     *
     * @param owner  the owner window, may be {@code null}
     * @param schema the schema the type goes into
     * @param kind   the preselected kind
     * @return the kind and name, empty when cancelled
     */
    public static Optional<NewType> ask(Window owner, XsdSchema schema, Kind kind) {
        if (responder != null) {
            return responder.apply(kind);
        }
        NewTypeDialog dialog = new NewTypeDialog(schema, kind);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog.showAndWait();
    }

    /**
     * Shows the first problem of the entered name below the field.
     *
     * @return the problem, or {@code null} when the name can be used
     */
    String validate() {
        EditCheck check = XsdEditRules.addGlobalType(schema, nameField.getText());
        String error = check.isAllowed() ? null : check.reason();
        errorLabel.setText(error == null ? "" : error);
        errorLabel.setVisible(error != null);
        errorLabel.setManaged(error != null);
        return error;
    }

    private Kind selectedKind() {
        return simpleKind.isSelected() ? Kind.SIMPLE : Kind.COMPLEX;
    }

    private void updateHint() {
        hintLabel.setText(selectedKind() == Kind.COMPLEX
                ? "Starts with an empty sequence: add child elements and attributes in the type editor."
                : "Starts as a restriction of xs:string: set the base type and facets in the type editor.");
    }

    private String suggestion(Kind kind) {
        return XsdEditRules.uniqueTypeName(schema, kind == Kind.SIMPLE ? "NewSimpleType" : "NewComplexType");
    }
}
