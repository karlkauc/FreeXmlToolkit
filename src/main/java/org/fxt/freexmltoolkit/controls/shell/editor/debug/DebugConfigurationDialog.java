package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.io.File;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.debugger.DebugLaunchConfig;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Edits the debug configuration of a stylesheet ({@link DebugLaunchConfig}). Debug saves it and
 * starts the debugger, Save only saves it. Layout in
 * {@code pages/dialogs/DebugConfigurationDialog.fxml}.
 */
public final class DebugConfigurationDialog extends Dialog<DebugConfigurationDialog.Result> {

    /** The edited configuration and whether to start debugging with it. */
    public record Result(DebugLaunchConfig config, boolean debug) { }

    static final ButtonType DEBUG = new ButtonType("Debug", ButtonBar.ButtonData.OK_DONE);
    static final ButtonType SAVE = new ButtonType("Save", ButtonBar.ButtonData.APPLY);
    private static final String AUTO = "Automatic (xsl:output)";

    @FXML private ToggleGroup inputGroup;
    @FXML private RadioButton runTargetRadio;
    @FXML private RadioButton fileRadio;
    @FXML private TextField fileField;
    @FXML private Button browseButton;
    @FXML private TextArea paramsArea;
    @FXML private ChoiceBox<String> formatChoice;
    @FXML private CheckBox pauseBox;

    public DebugConfigurationDialog(String stylesheetName, DebugLaunchConfig config) {
        setTitle("Debug Configuration");
        setHeaderText("Debug " + stylesheetName);
        IconifyIcon icon = new IconifyIcon("bi-bug");
        icon.setIconSize(32);
        setGraphic(icon);
        getDialogPane().getButtonTypes().addAll(DEBUG, SAVE, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/DebugConfigurationDialog.fxml"));
        try {
            getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        } catch (RuntimeException ignored) {
            // unthemed is fine
        }
        formatChoice.getItems().add(AUTO);
        for (OutputFormat f : OutputFormat.values()) {
            formatChoice.getItems().add(f.name());
        }
        fileField.disableProperty().bind(fileRadio.selectedProperty().not());
        browseButton.disableProperty().bind(fileRadio.selectedProperty().not());
        setConfig(config == null ? DebugLaunchConfig.defaults() : config);
        setResultConverter(button -> button == DEBUG || button == SAVE
                ? new Result(currentConfig(), button == DEBUG) : null);
    }

    /** Shows {@code config} in the controls. */
    void setConfig(DebugLaunchConfig config) {
        boolean file = !config.inputPath().isEmpty();
        fileRadio.setSelected(file);
        runTargetRadio.setSelected(!file);
        fileField.setText(config.inputPath());
        paramsArea.setText(config.parametersText());
        formatChoice.setValue(config.outputFormat().isEmpty() ? AUTO : config.outputFormat());
        pauseBox.setSelected(config.pauseOnEntry());
    }

    /** @return the configuration the controls describe. */
    DebugLaunchConfig currentConfig() {
        String format = formatChoice.getValue() == null || AUTO.equals(formatChoice.getValue())
                ? "" : formatChoice.getValue();
        return new DebugLaunchConfig(fileRadio.isSelected() ? fileField.getText() : "",
                DebugLaunchConfig.parseParameters(paramsArea.getText()), format, pauseBox.isSelected());
    }

    @FXML
    public void onBrowse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Input XML");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XML files", "*.xml"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        String current = fileField.getText();
        if (current != null && !current.isBlank() && new File(current).getParentFile() != null
                && new File(current).getParentFile().isDirectory()) {
            chooser.setInitialDirectory(new File(current).getParentFile());
        }
        File file = chooser.showOpenDialog(getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow());
        if (file != null) {
            fileField.setText(file.getAbsolutePath());
        }
    }

    /** Shows the dialog modally; empty on cancel. */
    public Optional<Result> ask() {
        return showAndWait();
    }
}
