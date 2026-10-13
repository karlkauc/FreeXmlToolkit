package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;

/**
 * Collects options for sample-XML generation (the common generation-profile
 * knobs): include only mandatory elements, and the maximum repetition of
 * repeatable elements. Gathers settings only — generation stays in
 * {@link SampleXmlRunner}.
 */
public class SampleXmlOptionsDialog extends Dialog<SampleXmlOptionsDialog.Options> {

    /** The chosen generation options. */
    public record Options(boolean mandatoryOnly, int maxOccurrences, boolean realistic) {
    }

    @FXML private CheckBox mandatoryOnly;
    @FXML private Spinner<Integer> maxOccurrences;
    @FXML private CheckBox realistic;

    public SampleXmlOptionsDialog() {
        setTitle("Generate Sample XML");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/SampleXmlOptionsDialog.fxml"));

        setResultConverter(button -> button == ButtonType.OK ? currentOptions() : null);
    }

    /** @return the options reflecting the current control state. */
    public Options currentOptions() {
        return new Options(mandatoryOnly.isSelected(), maxOccurrences.getValue(), realistic.isSelected());
    }

    /** Sets the control state (for tests/observers). */
    public void setOptions(boolean onlyMandatory, int max) {
        mandatoryOnly.setSelected(onlyMandatory);
        maxOccurrences.getValueFactory().setValue(max);
    }
}
