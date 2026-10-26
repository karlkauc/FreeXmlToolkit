package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeCardinalityCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.NewAttributeSpec;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.NewElementSpec;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Asks for all properties of a new element or attribute: name, type, cardinality (element) or
 * use (attribute), documentation, and under "More" default/fixed, form, nillable, abstract and
 * substitution group. The input is checked while typing ({@link XsdEditRules#newElement} /
 * {@link XsdEditRules#newAttribute}); OK stays closed while it would not be valid XSD.
 *
 * <p>Tests install {@link #setElementResponder} / {@link #setAttributeResponder} so no modal
 * dialog opens.
 */
public class NewNodeDialog extends Dialog<ButtonType> {

    private static final String SCHEMA_DEFAULT_FORM = "(schema default)";

    private static BiFunction<XsdNode, String, Optional<NewElementSpec>> elementResponder;
    private static BiFunction<XsdNode, String, Optional<NewAttributeSpec>> attributeResponder;

    @FXML private TextField nameField;
    @FXML private TypePickerField typeField;
    @FXML private Label occursLabel;
    @FXML private HBox occursRow;
    @FXML private Spinner<Integer> minSpinner;
    @FXML private Spinner<Integer> maxSpinner;
    @FXML private CheckBox unboundedCheck;
    @FXML private Label useLabel;
    @FXML private ComboBox<String> useCombo;
    @FXML private TextArea documentationArea;
    @FXML private TitledPane moreSection;
    @FXML private TextField defaultField;
    @FXML private TextField fixedField;
    @FXML private ComboBox<String> formCombo;
    @FXML private HBox flagsRow;
    @FXML private CheckBox nillableCheck;
    @FXML private CheckBox abstractCheck;
    @FXML private Label substitutionLabel;
    @FXML private TextField substitutionField;
    @FXML private Label errorLabel;

    private final XsdNode parent;
    private final boolean attribute;
    private final boolean global;

    /**
     * @param parent      the node the new element/attribute goes under
     * @param attribute   {@code true} for an attribute, {@code false} for an element
     * @param schema      supplies the schema for the type picker; may return {@code null}
     * @param defaultName the pre-filled name
     */
    public NewNodeDialog(XsdNode parent, boolean attribute, Supplier<XsdSchema> schema, String defaultName) {
        this.parent = parent;
        this.attribute = attribute;
        this.global = parent instanceof XsdSchema;
        String what = attribute ? "Attribute" : global ? "Root Element" : "Element";
        setTitle("Add " + what);
        String target = parent == null || parent.getName() == null || parent.getName().isBlank()
                ? "" : " to '" + parent.getName() + "'";
        setHeaderText(global ? "Add a top-level element to the schema."
                : "Add " + (attribute ? "an attribute" : "an element") + target + ".");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/NewNodeDialog.fxml"));
        getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());

        nameField.setText(defaultName);
        typeField.setChoices(() -> TypeChoices.of(schema == null ? null : schema.get()));
        if (attribute) {
            // Attributes carry text only: no complex types.
            typeField.setChoiceFilter(c -> c.kind() != TypeChoice.Kind.COMPLEX);
        }
        typeField.setPromptText("none");
        useCombo.getItems().setAll("optional", "required", "prohibited");
        useCombo.getSelectionModel().select("optional");
        formCombo.getItems().setAll(SCHEMA_DEFAULT_FORM, "qualified", "unqualified");
        formCombo.getSelectionModel().select(SCHEMA_DEFAULT_FORM);

        show(!attribute && !global, occursLabel, occursRow);
        show(attribute, useLabel, useCombo);
        show(!attribute, flagsRow);
        abstractCheck.setVisible(global);
        abstractCheck.setManaged(global);
        show(!attribute && global, substitutionLabel, substitutionField);
        maxSpinner.disableProperty().bind(unboundedCheck.selectedProperty());

        Runnable revalidate = this::validate;
        nameField.textProperty().addListener((o, ov, nv) -> revalidate.run());
        minSpinner.valueProperty().addListener((o, ov, nv) -> revalidate.run());
        maxSpinner.valueProperty().addListener((o, ov, nv) -> revalidate.run());
        unboundedCheck.selectedProperty().addListener((o, ov, nv) -> revalidate.run());
        useCombo.valueProperty().addListener((o, ov, nv) -> revalidate.run());
        defaultField.textProperty().addListener((o, ov, nv) -> revalidate.run());
        fixedField.textProperty().addListener((o, ov, nv) -> revalidate.run());
        abstractCheck.selectedProperty().addListener((o, ov, nv) -> revalidate.run());
        substitutionField.textProperty().addListener((o, ov, nv) -> revalidate.run());
        // Typed spinner text counts only after commit: commit it on every keystroke.
        commitWhileTyping(minSpinner);
        commitWhileTyping(maxSpinner);
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
        setOnHiding(e -> typeField.hidePopup());
    }

    /** @param responder answers element requests instead of the dialog ({@code null} = dialog) */
    public static void setElementResponder(BiFunction<XsdNode, String, Optional<NewElementSpec>> responder) {
        elementResponder = responder;
    }

    /** @param responder answers attribute requests instead of the dialog ({@code null} = dialog) */
    public static void setAttributeResponder(BiFunction<XsdNode, String, Optional<NewAttributeSpec>> responder) {
        attributeResponder = responder;
    }

    /**
     * Asks for a new element under {@code parent} (a top-level one when it is the schema).
     *
     * @param owner       the owner window, may be {@code null}
     * @param parent      the parent node
     * @param schema      supplies the schema for the type picker
     * @param defaultName the pre-filled name
     * @return the entered properties, empty when cancelled
     */
    public static Optional<NewElementSpec> askElement(Window owner, XsdNode parent, Supplier<XsdSchema> schema,
                                                      String defaultName) {
        if (elementResponder != null) {
            return elementResponder.apply(parent, defaultName);
        }
        NewNodeDialog dialog = new NewNodeDialog(parent, false, schema, defaultName);
        return dialog.showFor(owner).map(b -> dialog.elementSpec());
    }

    /**
     * Asks for a new attribute under {@code parent}.
     *
     * @return the entered properties, empty when cancelled
     * @see #askElement
     */
    public static Optional<NewAttributeSpec> askAttribute(Window owner, XsdNode parent, Supplier<XsdSchema> schema,
                                                          String defaultName) {
        if (attributeResponder != null) {
            return attributeResponder.apply(parent, defaultName);
        }
        NewNodeDialog dialog = new NewNodeDialog(parent, true, schema, defaultName);
        return dialog.showFor(owner).map(b -> dialog.attributeSpec());
    }

    private Optional<ButtonType> showFor(Window owner) {
        if (owner != null) {
            initOwner(owner);
        }
        return showAndWait().filter(ButtonType.OK::equals);
    }

    /** @return the element described by the current input */
    NewElementSpec elementSpec() {
        int min = global ? 1 : value(minSpinner);
        int max = global ? 1 : unboundedCheck.isSelected() ? ChangeCardinalityCommand.UNBOUNDED : value(maxSpinner);
        return new NewElementSpec(nameField.getText().trim(), type(), min, max, text(documentationArea.getText()),
                nillableCheck.isSelected(), global && abstractCheck.isSelected(), defaultField.getText(),
                fixedField.getText(), form(), global ? text(substitutionField.getText()) : null);
    }

    /** @return the attribute described by the current input */
    NewAttributeSpec attributeSpec() {
        return new NewAttributeSpec(nameField.getText().trim(), type(), useCombo.getValue(),
                text(documentationArea.getText()), defaultField.getText(), fixedField.getText(), form());
    }

    /**
     * Shows the first problem of the input below the fields; a problem in "More" opens it.
     *
     * @return the problem, or {@code null} when the input can be used
     */
    String validate() {
        EditCheck check = attribute
                ? XsdEditRules.newAttribute(parent, attributeSpec())
                : XsdEditRules.newElement(parent, elementSpec());
        String error = check.isAllowed() ? null : check.reason();
        errorLabel.setText(error == null ? "" : error);
        errorLabel.setVisible(error != null);
        errorLabel.setManaged(error != null);
        return error;
    }

    private String type() {
        return text(typeField.getEditor().getText());
    }

    private String form() {
        String f = formCombo.getValue();
        return f == null || SCHEMA_DEFAULT_FORM.equals(f) ? null : f;
    }

    private static String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static int value(Spinner<Integer> spinner) {
        Integer v = spinner.getValue();
        return v == null ? 0 : v;
    }

    private static void show(boolean visible, Node... nodes) {
        for (Node n : nodes) {
            n.setVisible(visible);
            n.setManaged(visible);
        }
    }

    private static void commitWhileTyping(Spinner<Integer> spinner) {
        spinner.getEditor().textProperty().addListener((o, ov, nv) -> {
            if (nv != null && nv.matches("\\d{1,6}")) {
                spinner.getValueFactory().setValue(Integer.parseInt(nv));
            }
        });
    }
}
