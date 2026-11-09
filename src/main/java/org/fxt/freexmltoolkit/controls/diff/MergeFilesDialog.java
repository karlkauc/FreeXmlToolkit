package org.fxt.freexmltoolkit.controls.diff;

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
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/** Asks for the base, left and right file of a three-way merge and an optional result file. */
public class MergeFilesDialog extends Dialog<MergeFilesDialog.Request> {

    /**
     * The chosen files.
     *
     * @param output where Save writes, or {@code null}
     */
    public record Request(Path base, Path left, Path right, Path output) {
    }

    @FXML private TextField baseField;
    @FXML private TextField leftField;
    @FXML private TextField rightField;
    @FXML private TextField resultField;
    @FXML private Label error;

    /** @param left the left file to start with (the active document), or {@code null} */
    public MergeFilesDialog(Path left) {
        DialogHelper.prepare(this);
        setTitle(I18n.t("MergeFilesDialog.merge-files"));
        setHeaderText(I18n.t("MergeFilesDialog.merge-two-changed-versions-of"));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/MergeFilesDialog.fxml"));
        if (left != null) {
            leftField.setText(left.toString());
        }
        for (TextField field : new TextField[] {baseField, leftField, rightField, resultField}) {
            field.textProperty().addListener((obs, o, n) -> validate());
        }
        validate();
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (validate() != null) {
                e.consume();
            }
        });
        setResultConverter(button -> button == ButtonType.OK
                ? new Request(path(baseField), path(leftField), path(rightField), path(resultField)) : null);
    }

    /** @return the problem with the input, or {@code null}; shows it below the fields */
    String validate() {
        String problem = null;
        if (missing(baseField) || missing(leftField) || missing(rightField)) {
            problem = "Choose the base, the left and the right file.";
        } else if (!exists(baseField) || !exists(leftField) || !exists(rightField)) {
            problem = "One of the files does not exist.";
        } else if (!resultField.getText().isBlank() && path(resultField) == null) {
            problem = "The result file name is not valid.";
        }
        boolean show = problem != null && !(missing(baseField) && missing(rightField));
        error.setText(problem == null ? "" : problem);
        error.setVisible(show);
        error.setManaged(show);
        getDialogPane().lookupButton(ButtonType.OK).setDisable(problem != null);
        return problem;
    }

    private static boolean missing(TextField field) {
        return field.getText() == null || field.getText().isBlank();
    }

    private static boolean exists(TextField field) {
        Path path = path(field);
        return path != null && Files.isRegularFile(path);
    }

    private static Path path(TextField field) {
        String text = field.getText() == null ? "" : field.getText().strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    @FXML
    public void onBrowseBase() {
        browse(baseField, "Base (common ancestor)");
    }

    @FXML
    public void onBrowseLeft() {
        browse(leftField, "Left (mine)");
    }

    @FXML
    public void onBrowseRight() {
        browse(rightField, "Right (theirs)");
    }

    @FXML
    public void onBrowseResult() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("MergeFilesDialog.result-file"));
        File file = FileChooserHelper.showSaveDialog(chooser, getDialogPane().getScene().getWindow());
        if (file != null) {
            resultField.setText(file.getAbsolutePath());
        }
    }

    private void browse(TextField field, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        Path current = path(leftField);
        if (current != null && current.toAbsolutePath().getParent() != null
                && Files.isDirectory(current.toAbsolutePath().getParent())) {
            chooser.setInitialDirectory(current.toAbsolutePath().getParent().toFile());
        }
        File file = FileChooserHelper.showOpenDialog(chooser, getDialogPane().getScene().getWindow());
        if (file != null) {
            field.setText(file.getAbsolutePath());
        }
    }

    // test access
    void setPathsForTesting(String base, String left, String right, String result) {
        baseField.setText(base);
        leftField.setText(left);
        rightField.setText(right);
        resultField.setText(result);
    }
}
