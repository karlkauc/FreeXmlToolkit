package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;

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

    private final CheckBox mandatoryOnly = new CheckBox("Only mandatory elements");
    private final Spinner<Integer> maxOccurrences = new Spinner<>(1, 20, 2);
    private final CheckBox realistic =
            new CheckBox("Realistic values (honor enumerations, patterns, ranges)");

    public SampleXmlOptionsDialog() {
        setTitle("Generate Sample XML");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        maxOccurrences.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));
        grid.add(mandatoryOnly, 0, 0, 2, 1);
        grid.add(new Label("Max. repetitions:"), 0, 1);
        grid.add(maxOccurrences, 1, 1);
        grid.add(realistic, 0, 2, 2, 1);
        getDialogPane().setContent(grid);

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
