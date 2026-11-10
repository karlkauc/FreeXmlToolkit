package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.util.DialogHelper;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

/**
 * Transform panel → Generate stylesheet…: the source is the panel's input document; the user names a target sample
 * (a document with the wanted structure), optionally its XSD, notes in their own words and the XSLT version.
 */
public class GenerateStylesheetDialog extends Dialog<AiActions.StylesheetRequest> {

    @FXML private Label sourceLabel;
    @FXML private TextField targetField;
    @FXML private TextField schemaField;
    @FXML private TextArea notesArea;
    @FXML private ComboBox<String> versionChoice;
    @FXML private Label error;

    private final String source;
    private final String sourceName;
    private final File sourceFile;

    public GenerateStylesheetDialog(String source, String sourceName, File sourceFile) {
        DialogHelper.prepare(this);
        this.source = source;
        this.sourceName = sourceName;
        this.sourceFile = sourceFile;
        setTitle(I18n.t("GenerateStylesheetDialog.title"));
        setHeaderText(I18n.t("GenerateStylesheetDialog.header"));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/GenerateStylesheetDialog.fxml"));
        sourceLabel.setText(sourceName);
        versionChoice.getItems().setAll("3.0", "2.0", "1.0");
        versionChoice.setValue("3.0");
        targetField.textProperty().addListener((obs, o, n) -> validate());
        schemaField.textProperty().addListener((obs, o, n) -> validate());
        validate();
        setResultConverter(button -> button == ButtonType.OK && validate() == null ? request() : null);
    }

    private String validate() {
        String problem = null;
        File target = file(targetField.getText());
        File schema = file(schemaField.getText());
        if (target == null || !target.isFile()) {
            problem = I18n.t("GenerateStylesheetDialog.error.target");
        } else if (target.length() > 4L * 1024 * 1024) {
            problem = I18n.t("GenerateStylesheetDialog.error.large");
        } else if (!schemaField.getText().isBlank() && (schema == null || !schema.isFile())) {
            problem = I18n.t("GenerateStylesheetDialog.error.schema");
        }
        error.setText(problem == null ? "" : problem);
        error.setVisible(problem != null);
        error.setManaged(problem != null);
        var ok = getDialogPane().lookupButton(ButtonType.OK);
        if (ok != null) {
            ok.setDisable(problem != null);
        }
        return problem;
    }

    private AiActions.StylesheetRequest request() {
        File target = file(targetField.getText());
        File schema = file(schemaField.getText());
        String sample;
        try {
            sample = Files.readString(target.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DialogHelper.showError(I18n.t("GenerateStylesheetDialog.title"), e.getMessage(), "");
            return null;
        }
        return new AiActions.StylesheetRequest(source, sourceName, sourceFile, sample, target,
                schemaField.getText().isBlank() ? null : schema, notesArea.getText(), versionChoice.getValue());
    }

    private static File file(String text) {
        return text == null || text.isBlank() ? null : new File(text.strip());
    }

    @FXML
    public void onBrowseTarget() {
        browse(targetField, "*.xml", "*.*");
    }

    @FXML
    public void onBrowseSchema() {
        browse(schemaField, "*.xsd", "*.*");
    }

    private void browse(TextField field, String... patterns) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(String.join(", ", patterns), patterns));
        if (sourceFile != null && sourceFile.getParentFile() != null) {
            chooser.setInitialDirectory(sourceFile.getParentFile());
        }
        File chosen = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (chosen != null) {
            field.setText(chosen.getAbsolutePath());
        }
    }
}
