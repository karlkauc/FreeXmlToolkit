package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.i18n.I18n;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

import org.apache.xerces.util.XMLChar;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Format;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Mapping;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Options;

/**
 * Asks for the mapping and options of a conversion between XML and JSON or YAML
 * ({@link org.fxt.freexmltoolkit.service.DataFormatConverter}). Starts from the given options (the
 * last ones used) and returns the chosen ones.
 */
public class DataConversionDialog extends Dialog<Options> {

    @FXML private ToggleGroup mappingGroup;
    @FXML private RadioButton structuredMapping;
    @FXML private RadioButton w3cMapping;
    @FXML private Label structuredHint;
    @FXML private Label w3cHint;
    @FXML private CheckBox typedValues;
    @FXML private HBox rootNameRow;
    @FXML private TextField rootName;
    @FXML private Label rootNameError;
    @FXML private Label noOptions;

    private final Format target;

    /**
     * @param source  the format of the document
     * @param target  the format to convert to
     * @param initial the options to start from
     */
    public DataConversionDialog(Format source, Format target, Options initial) {
        this.target = target;
        boolean toJson = target != Format.XML;   // JSON or YAML: the XML → JSON mapping
        setTitle(I18n.t("DataConversionDialog.convert-to", target.label()));
        setHeaderText(I18n.t("DataConversionDialog.convert-this-document-to-the", source.name(), target.name()));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/DataConversionDialog.fxml"));

        if (toJson) {
            structuredHint.setText("Elements become properties, attributes \"@name\", text next to them "
                    + "\"#text\"; repeated elements become arrays. Comments are dropped.");
            w3cHint.setText(I18n.t("DataConversionDialog.for-xml-in-the-w3c"));
        } else {
            structuredHint.setText("Properties become elements, \"@name\" properties attributes, \"#text\" text; "
                    + "array items repeat their element. Names that are no XML names are adapted.");
            w3cHint.setText("Writes <map>, <array>, <string>, <number>, <boolean> and <null> elements "
                    + "with the property names in key attributes; converts back without loss.");
        }
        structuredMapping.setSelected(initial.mapping() == Mapping.STRUCTURED);
        w3cMapping.setSelected(initial.mapping() == Mapping.W3C);
        typedValues.setSelected(initial.typedValues());
        rootName.setText(initial.rootName());

        mappingGroup.selectedToggleProperty().addListener((obs, o, n) -> updateOptions());
        rootName.textProperty().addListener((obs, o, n) -> validate());
        updateOptions();
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (!validate()) {
                e.consume();
            }
        });
        setResultConverter(button -> button == ButtonType.OK ? options(initial) : null);
    }

    /** @return the options chosen in the dialog, other values (indent) taken from {@code base} */
    Options options(Options base) {
        return base.withMapping(w3cMapping.isSelected() ? Mapping.W3C : Mapping.STRUCTURED)
                .withTypedValues(typedValues.isSelected())
                .withRootName(rootName.getText());
    }

    private void updateOptions() {
        boolean structured = !w3cMapping.isSelected();
        show(typedValues, structured && target != Format.XML);
        show(rootNameRow, structured && target == Format.XML);
        show(noOptions, !structured);
        validate();
    }

    /** @return whether the input can be used; shows the error for an invalid root name */
    private boolean validate() {
        String name = rootName.getText() == null ? "" : rootName.getText().trim();
        boolean invalid = rootNameRow.isVisible() && !name.isEmpty() && !XMLChar.isValidName(name);
        rootNameError.setText(invalid ? "'" + name + "' is not a valid XML element name." : "");
        show(rootNameError, invalid);
        return !invalid;
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    // test access
    RadioButton w3cMappingButton() {
        return w3cMapping;
    }

    TextField rootNameField() {
        return rootName;
    }

    CheckBox typedValuesBox() {
        return typedValues;
    }

    boolean isRootNameErrorShown() {
        return rootNameError.isVisible();
    }
}
