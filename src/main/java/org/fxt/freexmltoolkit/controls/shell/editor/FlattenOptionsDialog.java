package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.editor.flatten.FlattenOptions;

/**
 * Collects reduction options for schema flattening (see {@link FlattenOptions}).
 * Defaults to a fully reduced, validation-only schema — uncheck everything for a
 * plain flatten. Resolved include directives are always dropped on this path
 * because the flattened output is meant to be standalone.
 */
public class FlattenOptionsDialog extends Dialog<FlattenOptions> {

    @FXML private CheckBox removeAnnotations;
    @FXML private CheckBox removeComments;
    @FXML private CheckBox minify;
    @FXML private CheckBox removeUnusedTypes;
    @FXML private CheckBox trackSourceFiles;

    public FlattenOptionsDialog() {
        setTitle("Flatten Schema");
        setHeaderText("Create a standalone schema, optionally reduced for server-side validation.");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/FlattenOptionsDialog.fxml"));

        setResultConverter(button -> button == ButtonType.OK ? currentOptions() : null);
    }

    /** @return the options reflecting the current control state. */
    public FlattenOptions currentOptions() {
        return new FlattenOptions(removeAnnotations.isSelected(), removeComments.isSelected(),
                minify.isSelected(), removeUnusedTypes.isSelected(), true,
                trackSourceFiles.isSelected());
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
