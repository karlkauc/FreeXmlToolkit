package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Predicate;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs;
import org.fxt.freexmltoolkit.controls.v2.editor.flatten.FlattenOptions;

/**
 * Collects the output file and the reduction options for schema flattening (see
 * {@link FlattenOptions}). The output defaults to {@code <name>_flattened.xsd} next to the
 * source schema; an empty field opens the result as an unsaved tab. The options default to a
 * fully reduced, validation-only schema — uncheck everything for a plain flatten. Resolved
 * include directives are always dropped on this path because the flattened output is meant to
 * be standalone.
 */
public class FlattenOptionsDialog extends Dialog<FlattenOptionsDialog.Request> {

    /**
     * The user's answer.
     *
     * @param options    the reduction options
     * @param outputFile the file to write, or {@code null} to open the result unsaved
     */
    public record Request(FlattenOptions options, Path outputFile) {
    }

    /** Test hook: answers the overwrite question instead of a dialog ({@code null} = dialog). */
    private static Predicate<Path> overwriteConfirmer;

    @FXML private TextField outputField;
    @FXML private Label outputError;
    @FXML private CheckBox removeAnnotations;
    @FXML private CheckBox removeComments;
    @FXML private CheckBox minify;
    @FXML private CheckBox removeUnusedTypes;
    @FXML private CheckBox trackSourceFiles;

    private final Path sourceFile;

    /** A dialog without a source file (the output field starts empty). */
    public FlattenOptionsDialog() {
        this(null);
    }

    /**
     * @param sourceFile the schema being flattened, or {@code null} for an unsaved document
     */
    public FlattenOptionsDialog(Path sourceFile) {
        this.sourceFile = sourceFile;
        setTitle("Flatten Schema");
        setHeaderText("Create a standalone schema, optionally reduced for server-side validation.");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/FlattenOptionsDialog.fxml"));

        if (sourceFile != null) {
            outputField.setText(defaultOutput(sourceFile).toString());
        }
        outputField.textProperty().addListener((obs, o, n) -> validate());
        validate();
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (validate() != null || !confirmOverwrite()) {
                e.consume();
            }
        });

        setResultConverter(button -> button == ButtonType.OK ? new Request(currentOptions(), outputFile()) : null);
    }

    /**
     * @return {@code <name>_flattened.xsd} in the folder of {@code source} ({@code FundsXML.xsd}
     *         → {@code FundsXML_flattened.xsd})
     */
    public static Path defaultOutput(Path source) {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return source.resolveSibling(base + "_flattened.xsd");
    }

    /** @param confirmer answers "overwrite the existing file?" in tests ({@code null} = dialog) */
    public static void setOverwriteConfirmer(Predicate<Path> confirmer) {
        overwriteConfirmer = confirmer;
    }

    /** @return the options reflecting the current control state. */
    public FlattenOptions currentOptions() {
        return new FlattenOptions(removeAnnotations.isSelected(), removeComments.isSelected(),
                minify.isSelected(), removeUnusedTypes.isSelected(), true,
                trackSourceFiles.isSelected());
    }

    /** @return the entered output file, or {@code null} when the field is empty or not a path */
    public Path outputFile() {
        String text = outputField.getText() == null ? "" : outputField.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Path.of(text).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** Sets the output field (for tests/observers). */
    public void setOutput(String path) {
        outputField.setText(path);
    }

    /**
     * Shows the first problem of the output file below the field.
     *
     * @return the problem, or {@code null} when the file can be used (an empty field is fine)
     */
    String validate() {
        String error = outputProblem();
        outputError.setText(error == null ? "" : error);
        outputError.setVisible(error != null);
        outputError.setManaged(error != null);
        return error;
    }

    private String outputProblem() {
        String text = outputField.getText() == null ? "" : outputField.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        Path output = outputFile();
        if (output == null || output.getFileName() == null) {
            return "'" + text + "' is not a valid file path.";
        }
        if (Files.isDirectory(output)) {
            return "Enter a file name, not a folder.";
        }
        if (output.getParent() == null || !Files.isDirectory(output.getParent())) {
            return "The folder " + output.getParent() + " does not exist.";
        }
        if (sourceFile != null && output.equals(sourceFile.toAbsolutePath().normalize())) {
            return "Choose another file: the original schema must not be overwritten.";
        }
        return null;
    }

    private boolean confirmOverwrite() {
        Path output = outputFile();
        if (output == null || !Files.exists(output)) {
            return true;
        }
        if (overwriteConfirmer != null) {
            return overwriteConfirmer.test(output);
        }
        return EditRuleDialogs.confirm(getDialogPane().getScene() != null ? getDialogPane().getScene().getWindow() : null,
                "Flatten Schema", "The file already exists",
                output.getFileName() + " already exists in " + output.getParent() + ". Overwrite it?", "Overwrite");
    }

    @FXML
    public void onBrowse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Flattened Schema");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Schema (*.xsd)", "*.xsd"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        Path current = outputFile();
        if (current == null && sourceFile != null) {
            current = defaultOutput(sourceFile);
        }
        if (current != null) {
            if (current.getParent() != null && Files.isDirectory(current.getParent())) {
                chooser.setInitialDirectory(current.getParent().toFile());
            }
            chooser.setInitialFileName(current.getFileName().toString());
        }
        File chosen = chooser.showSaveDialog(getDialogPane().getScene().getWindow());
        if (chosen != null) {
            outputField.setText(chosen.getAbsolutePath());
        }
    }

    /** Sets the control state (for tests/observers), leaving source-file tracking off. */
    public void setOptions(boolean annotations, boolean comments, boolean minified, boolean unusedTypes) {
        setOptions(annotations, comments, minified, unusedTypes, false);
    }

    /** Sets the control state (for tests/observers). */
    public void setOptions(boolean annotations, boolean comments, boolean minified, boolean unusedTypes,
                           boolean sourceFiles) {
        removeAnnotations.setSelected(annotations);
        removeComments.setSelected(comments);
        minify.setSelected(minified);
        removeUnusedTypes.setSelected(unusedTypes);
        trackSourceFiles.setSelected(sourceFiles);
    }
}
