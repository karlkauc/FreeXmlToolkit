package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/** Asks which XML files of the project (a glob) a schema validates. */
public class BindSchemaDialog extends Dialog<BindSchemaDialog.Request> {

    /** @param files the glob relative to the project folder */
    public record Request(String files, Path schema) {
    }

    @FXML private TextField filesField;
    @FXML private TextField schemaField;
    @FXML private Label error;

    private final FxtProject project;

    /**
     * @param files  the pattern to start with, or {@code null}
     * @param schema the schema to start with, or {@code null}
     */
    public BindSchemaDialog(FxtProject project, String files, Path schema) {
        this.project = project;
        setTitle("Bind Schema in Project");
        setHeaderText("Validate XML files of \"" + project.name() + "\" with a schema.");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/BindSchemaDialog.fxml"));
        filesField.setText(files == null ? "" : files);
        schemaField.setText(schema == null ? "" : project.relativize(schema));
        filesField.textProperty().addListener((obs, o, n) -> validate());
        schemaField.textProperty().addListener((obs, o, n) -> validate());
        validate();
        setResultConverter(button -> button == ButtonType.OK && validate() == null
                ? new Request(filesField.getText().strip(), schema()) : null);
    }

    /** @return the problem with the input, or {@code null} */
    String validate() {
        String problem = null;
        if (filesField.getText() == null || filesField.getText().isBlank()) {
            problem = "Enter the XML files (a path or pattern).";
        } else if (schema() == null || !Files.isRegularFile(schema())) {
            problem = "Choose an existing schema file.";
        }
        boolean show = problem != null && !schemaField.getText().isBlank();
        error.setText(problem == null ? "" : problem);
        error.setVisible(show);
        error.setManaged(show);
        getDialogPane().lookupButton(ButtonType.OK).setDisable(problem != null);
        return problem;
    }

    private Path schema() {
        String text = schemaField.getText() == null ? "" : schemaField.getText().strip();
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
    public void onBrowseSchema() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Schema");
        chooser.setInitialDirectory(project.root().toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Schema", "*.xsd"));
        File file = FileChooserHelper.showOpenDialog(chooser, getDialogPane().getScene().getWindow());
        if (file != null) {
            schemaField.setText(project.relativize(file.toPath()));
        }
    }
}
