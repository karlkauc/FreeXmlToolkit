/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Asks where and under which FMA file name a report is submitted: folder, running number (the next
 * free one proposed) and suffix; shows the resulting name and the result of the checks.
 */
public class PkSubmissionDialog extends Dialog<PkSubmissionDialog.Choice> {

    /** The chosen folder and XML file name (the ZIP gets the same name). */
    public record Choice(Path folder, String xmlName) {
    }

    /** The "Create ZIP" button. */
    static final ButtonType CREATE = new ButtonType(I18n.t("PkSubmissionDialog.create"), ButtonBar.ButtonData.OK_DONE);

    @FXML private TextField folderField;
    @FXML private Spinner<Integer> sequenceSpinner;
    @FXML private TextField suffixField;
    @FXML private Label nameLabel;
    @FXML private Label checksLabel;
    @FXML private Label errorLabel;

    private final PkReport report;

    /**
     * @param report the report
     * @param folder the folder proposed (the report's own folder)
     * @param checks the result of the checks, one line each
     */
    public PkSubmissionDialog(PkReport report, Path folder, String checks) {
        DialogHelper.prepare(this);
        this.report = report;
        setTitle(I18n.t("PkSubmissionDialog.title"));
        setHeaderText(I18n.t("PkSubmissionDialog.header", report.label()));
        getDialogPane().getButtonTypes().addAll(CREATE, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/PkSubmissionDialog.fxml"));
        sequenceSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99_999,
                PkSubmission.nextSequence(folder, report)));
        folderField.setText(folder == null ? "" : folder.toString());
        suffixField.setText(PkSubmission.proposedSuffix(report));
        checksLabel.setText(checks);
        folderField.textProperty().addListener((obs, o, n) -> {
            Path dir = folder();
            if (dir != null && Files.isDirectory(dir)) {
                sequenceSpinner.getValueFactory().setValue(PkSubmission.nextSequence(dir, report));
            }
            update();
        });
        sequenceSpinner.valueProperty().addListener((obs, o, n) -> update());
        suffixField.textProperty().addListener((obs, o, n) -> update());
        update();
        setResultConverter(button -> button == CREATE ? new Choice(folder(), name()) : null);
    }

    private Path folder() {
        String text = folderField.getText() == null ? "" : folderField.getText().strip();
        try {
            return text.isEmpty() ? null : Path.of(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the XML file name for the inputs, or {@code null} */
    String name() {
        if (!PkSubmission.validSuffix(suffixField.getText())) {
            return null;
        }
        Integer sequence = sequenceSpinner.getValue();
        return sequence == null ? null : PkSubmission.fileName(report, sequence, suffixField.getText());
    }

    /** Shows the name and enables "Create ZIP" only for a usable name and folder. */
    private void update() {
        String name = name();
        Path dir = folder();
        String error = null;
        if (!PkSubmission.validSuffix(suffixField.getText())) {
            error = I18n.t("PkSubmissionDialog.bad-suffix");
        } else if (name == null) {
            error = I18n.t("PkSubmissionDialog.no-name");
        } else if (dir == null) {
            error = I18n.t("PkSubmissionDialog.no-folder");
        } else if (Files.exists(dir.resolve(name)) || Files.exists(dir.resolve(name.replaceFirst("\\.xml$", ".zip")))) {
            error = I18n.t("PkSubmissionDialog.exists");
        }
        nameLabel.setText(name == null ? "–" : name.replaceFirst("\\.xml$", ".zip") + "  (" + name + ")");
        errorLabel.setText(error == null ? "" : error);
        var create = getDialogPane().lookupButton(CREATE);
        if (create != null) {
            create.setDisable(error != null);
        }
    }

    @FXML
    public void onBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        Path dir = folder();
        if (dir != null && Files.isDirectory(dir)) {
            chooser.setInitialDirectory(dir.toFile());
        }
        File chosen = FileChooserHelper.showDialog(chooser, getDialogPane().getScene() == null ? null
                : getDialogPane().getScene().getWindow());
        if (chosen != null) {
            folderField.setText(chosen.getAbsolutePath());
        }
    }

    /** Test hooks. */
    TextField folderField() {
        return folderField;
    }

    Spinner<Integer> sequenceSpinner() {
        return sequenceSpinner;
    }

    TextField suffixField() {
        return suffixField;
    }

    String errorText() {
        return errorLabel.getText();
    }
}
