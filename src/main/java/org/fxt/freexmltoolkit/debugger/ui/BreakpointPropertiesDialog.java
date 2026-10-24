package org.fxt.freexmltoolkit.debugger.ui;

import java.util.List;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.debugger.HitCondition;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Edits a breakpoint's condition (XPath) and hit-count rule. Shared by the editor gutter's
 * context menu and the debugger's Breakpoints panel. Layout in
 * {@code pages/dialogs/BreakpointPropertiesDialog.fxml}.
 */
public final class BreakpointPropertiesDialog extends Dialog<BreakpointPropertiesDialog.Settings> {

    /** The edited properties: {@code condition} {@code ""} = unconditional. */
    public record Settings(String condition, HitCondition hit) {
        public Settings {
            condition = condition == null ? "" : condition.strip();
            hit = hit == null ? HitCondition.ALWAYS : hit;
        }
    }

    /** Asks for new settings; returns empty when the user cancels. */
    @FunctionalInterface
    public interface Prompt {
        Optional<Settings> ask(Window owner, String location, Settings current, int hits);
    }

    /** The default prompt: this dialog, themed, modal to {@code owner}. */
    public static final Prompt DEFAULT = (owner, location, current, hits) -> {
        BreakpointPropertiesDialog dialog = new BreakpointPropertiesDialog(location, current, hits);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog.showAndWait();
    };

    private static final List<String> MODES = List.of(
            "every hit", "exactly the N-th hit", "the N-th hit and later", "every N-th hit");

    @FXML private TextField conditionField;
    @FXML private ComboBox<String> hitMode;
    @FXML private Spinner<Integer> hitTarget;
    @FXML private Label hitsLabel;

    /**
     * @param location e.g. "main.xsl, line 12"
     * @param current  the breakpoint's current settings
     * @param hits     hits of the current run, or -1 when no run is active
     */
    public BreakpointPropertiesDialog(String location, Settings current, int hits) {
        setTitle("Breakpoint Properties");
        setHeaderText("Breakpoint at " + location);
        IconifyIcon icon = new IconifyIcon("bi-sliders");
        icon.setIconSize(32);
        setGraphic(icon);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/BreakpointPropertiesDialog.fxml"));
        try {
            getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        } catch (RuntimeException ignored) {
            // unthemed is fine
        }

        hitMode.getItems().setAll(MODES);
        hitTarget.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1_000_000, 1));
        hitMode.valueProperty().addListener((obs, old, mode) -> hitTarget.setDisable(MODES.indexOf(mode) <= 0));
        Settings settings = current == null ? new Settings("", HitCondition.ALWAYS) : current;
        conditionField.setText(settings.condition());
        hitMode.getSelectionModel().select(settings.hit().mode().ordinal());
        hitTarget.getValueFactory().setValue(settings.hit().target());
        hitTarget.setDisable(!settings.hit().isSet());
        hitsLabel.setText(hits < 0 ? "Hits are counted while a debug run is active."
                : "Hits in the current run: " + hits);

        setResultConverter(button -> button == ButtonType.OK ? currentSettings() : null);
    }

    /** @return the settings reflecting the current control state. */
    public Settings currentSettings() {
        // Commit a typed spinner value that has not been confirmed with Enter yet.
        try {
            hitTarget.getValueFactory().setValue(Integer.parseInt(hitTarget.getEditor().getText().strip()));
        } catch (RuntimeException ignored) {
            // keep the last valid value
        }
        int mode = Math.max(0, hitMode.getSelectionModel().getSelectedIndex());
        HitCondition hit = new HitCondition(HitCondition.Mode.values()[mode], hitTarget.getValue());
        return new Settings(conditionField.getText(), hit.isSet() ? hit : HitCondition.ALWAYS);
    }

    /** Sets the control state (tests). */
    void setControls(String condition, HitCondition.Mode mode, int target) {
        conditionField.setText(condition);
        hitMode.getSelectionModel().select(mode.ordinal());
        hitTarget.getValueFactory().setValue(target);
        hitTarget.getEditor().setText(Integer.toString(target));
    }

    /** The hit-count hint text (tests). */
    String hitsText() {
        return hitsLabel.getText();
    }
}
