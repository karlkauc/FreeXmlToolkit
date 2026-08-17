package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import org.fxt.freexmltoolkit.controls.v2.editor.flatten.FlattenOptions;

/**
 * Collects reduction options for schema flattening (see {@link FlattenOptions}).
 * Defaults to a fully reduced, validation-only schema — uncheck everything for a
 * plain flatten. Resolved include directives are always dropped on this path
 * because the flattened output is meant to be standalone.
 */
public class FlattenOptionsDialog extends Dialog<FlattenOptions> {

    private final CheckBox removeAnnotations =
            new CheckBox("Remove annotations (documentation, appinfo)");
    private final CheckBox removeComments = new CheckBox("Remove XML comments");
    private final CheckBox minify = new CheckBox("Minified output (no indentation)");
    private final CheckBox removeUnusedTypes =
            new CheckBox("Remove unused global types and groups");

    public FlattenOptionsDialog() {
        setTitle("Flatten Schema");
        setHeaderText("Create a standalone schema, optionally reduced for server-side validation.");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        removeAnnotations.setSelected(true);
        removeComments.setSelected(true);
        minify.setSelected(true);
        removeUnusedTypes.setSelected(true);

        Label hint = new Label("Uncheck all options for a plain flatten that keeps documentation.");
        hint.setOpacity(0.7);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));
        grid.add(removeAnnotations, 0, 0);
        grid.add(removeComments, 0, 1);
        grid.add(removeUnusedTypes, 0, 2);
        grid.add(minify, 0, 3);
        grid.add(hint, 0, 4);
        getDialogPane().setContent(grid);

        setResultConverter(button -> button == ButtonType.OK ? currentOptions() : null);
    }

    /** @return the options reflecting the current control state. */
    public FlattenOptions currentOptions() {
        return new FlattenOptions(removeAnnotations.isSelected(), removeComments.isSelected(),
                minify.isSelected(), removeUnusedTypes.isSelected(), true);
    }

    /** Sets the control state (for tests/observers). */
    public void setOptions(boolean annotations, boolean comments, boolean minified, boolean unusedTypes) {
        removeAnnotations.setSelected(annotations);
        removeComments.setSelected(comments);
        minify.setSelected(minified);
        removeUnusedTypes.setSelected(unusedTypes);
    }
}
