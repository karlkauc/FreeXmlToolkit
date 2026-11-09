package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/** Asks for the name and the optional output file of a scenario saved from the Transform panel. */
public class ScenarioDialog extends Dialog<ScenarioDialog.Request> {

    /**
     * @param output  the output file, or {@code null}
     * @param perFile whether the scenario runs for the file it is started for ({@code ${file}}) instead of a fixed input
     */
    public record Request(String name, Path output, boolean perFile) {
    }

    @FXML private TextField nameField;
    @FXML private CheckBox perFileBox;
    @FXML private TextField outputField;
    @FXML private Label error;

    private final FxtProject project;

    /**
     * @param name   the name to start with, or {@code null}
     * @param output the output file to start with (project-relative or absolute), or {@code null}
     */
    public ScenarioDialog(FxtProject project, String name, String output) {
        this(project, name, output, false);
    }

    /**
     * @param perFile whether the box "for the file it runs for" starts ticked
     */
    public ScenarioDialog(FxtProject project, String name, String output, boolean perFile) {
        DialogHelper.prepare(this);
        this.project = project;
        setTitle(I18n.t("ScenarioDialog.save-as-scenario"));
        setHeaderText(I18n.t("ScenarioDialog.save-the-transform-settings-as", project.name()));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/ScenarioDialog.fxml"));
        nameField.setText(name == null ? "" : name);
        outputField.setText(output == null ? "" : output);
        perFileBox.setSelected(perFile);
        nameField.textProperty().addListener((obs, o, n) -> validate());
        outputField.textProperty().addListener((obs, o, n) -> validate());
        validate();
        setResultConverter(button -> button == ButtonType.OK && validate() == null
                ? new Request(nameField.getText().strip(), output(), perFileBox.isSelected()) : null);
    }

    /** @return the problem with the input, or {@code null} */
    String validate() {
        String problem = null;
        if (nameField.getText() == null || nameField.getText().isBlank()) {
            problem = "Enter a name.";
        } else if (!outputField.getText().isBlank() && output() == null) {
            problem = "The output file name is not valid.";
        }
        boolean show = problem != null && !nameField.getText().isBlank();
        error.setText(problem == null ? "" : problem);
        error.setVisible(show);
        error.setManaged(show);
        getDialogPane().lookupButton(ButtonType.OK).setDisable(problem != null);
        return problem;
    }

    private Path output() {
        String text = outputField.getText() == null ? "" : outputField.getText().strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            Path path = Path.of(text);
            return path.isAbsolute() ? path : project.resolve(text);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    @FXML
    public void onBrowseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("ScenarioDialog.scenario-output-file"));
        chooser.setInitialDirectory(project.root().toFile());
        File file = FileChooserHelper.showSaveDialog(chooser, getDialogPane().getScene().getWindow());
        if (file != null) {
            outputField.setText(project.relativize(file.toPath()));
        }
    }

    // test access
    void fillForTesting(String name, String output) {
        nameField.setText(name);
        outputField.setText(output);
    }

    void setPerFileForTesting(boolean perFile) {
        perFileBox.setSelected(perFile);
    }
}
