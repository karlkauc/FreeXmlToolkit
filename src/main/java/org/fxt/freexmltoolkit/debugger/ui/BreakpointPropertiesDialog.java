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

    /**
     * The edited properties: {@code condition} {@code ""} = unconditional; {@code logMessage}
     * {@code ""} = no logging; {@code suspend} = pause at a hit (always true without a log message).
     */
    public record Settings(String condition, HitCondition hit, String logMessage, boolean suspend) {
        public Settings {
            condition = condition == null ? "" : condition.strip();
            hit = hit == null ? HitCondition.ALWAYS : hit;
            logMessage = logMessage == null ? "" : logMessage.strip();
            if (logMessage.isEmpty()) {
                suspend = true;
            }
        }

        /** Condition and hit rule of a pausing breakpoint without logging. */
        public Settings(String condition, HitCondition hit) {
            this(condition, hit, "", true);
        }

        /** The settings of {@code bp}. */
        public static Settings of(org.fxt.freexmltoolkit.debugger.Breakpoint bp) {
            return bp == null ? new Settings("", null)
                    : new Settings(bp.condition(), bp.hit(), bp.logMessage(), bp.suspend());
        }

        /** @return {@code bp} with these settings applied. */
        public org.fxt.freexmltoolkit.debugger.Breakpoint applyTo(org.fxt.freexmltoolkit.debugger.Breakpoint bp) {
            return bp.withCondition(condition).withHit(hit).withLog(logMessage, suspend);
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
    @FXML private TextField logField;
    @FXML private javafx.scene.control.CheckBox suspendBox;
    @FXML private Label errorLabel;

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
        logField.setText(settings.logMessage());
        suspendBox.setSelected(settings.suspend());
        // Only a logpoint can run on without pausing.
        suspendBox.disableProperty().bind(logField.textProperty().isEmpty());
        hitsLabel.setText(hits < 0 ? "Hits are counted while a debug run is active."
                : "Hits in the current run: " + hits);

        // Static check while typing; OK stays closed while the condition or message cannot compile.
        conditionField.textProperty().addListener((obs, o, n) -> validate());
        logField.textProperty().addListener((obs, o, n) -> validate());
        validate();
        javafx.scene.Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (validate() != null) {
                e.consume();
            }
        });

        setResultConverter(button -> button == ButtonType.OK ? currentSettings() : null);
    }

    /**
     * Checks the condition and the log message and shows the first problem below the fields.
     *
     * @return the problem, or {@code null} when both compile
     */
    String validate() {
        String error = org.fxt.freexmltoolkit.debugger.ExpressionCheck.checkXPath(conditionField.getText());
        if (error != null) {
            error = "Condition: " + error;
        } else {
            error = org.fxt.freexmltoolkit.debugger.ExpressionCheck.checkLogMessage(logField.getText());
            if (error != null) {
                error = "Log message: " + error;
            }
        }
        errorLabel.setText(error == null ? "" : error);
        errorLabel.setVisible(error != null);
        errorLabel.setManaged(error != null);
        return error;
    }

    /** The shown error text (tests). */
    String errorText() {
        return errorLabel.isVisible() ? errorLabel.getText() : "";
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
        return new Settings(conditionField.getText(), hit.isSet() ? hit : HitCondition.ALWAYS,
                logField.getText(), suspendBox.isSelected());
    }

    /** Sets the control state (tests). */
    void setControls(String condition, HitCondition.Mode mode, int target) {
        conditionField.setText(condition);
        hitMode.getSelectionModel().select(mode.ordinal());
        hitTarget.getValueFactory().setValue(target);
        hitTarget.getEditor().setText(Integer.toString(target));
    }

    /** Sets the logpoint controls (tests). */
    void setLog(String message, boolean suspend) {
        logField.setText(message);
        suspendBox.setSelected(suspend);
    }

    /** The hit-count hint text (tests). */
    String hitsText() {
        return hitsLabel.getText();
    }
}
