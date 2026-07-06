package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Node;
import javafx.scene.control.TextInputControl;

/**
 * Marks form inputs whose value is missing or invalid for an attempted action,
 * so the user immediately sees <em>what</em> to fill in — the visual counterpart
 * to the inline message set via {@link PanelStatus}.
 *
 * <p>Marking adds the {@code fxt-field-error} style class (red border, defined in
 * {@code unified-shell.css}). For text inputs the mark clears itself as soon as
 * the user edits the field; other nodes (e.g. a source-row label) are cleared
 * explicitly via {@link #clear(Node...)} by the action that set them.</p>
 */
public final class FieldMarker {

    private static final String ERROR = "fxt-field-error";

    private FieldMarker() {
    }

    /**
     * Marks the node as missing/invalid. A {@link TextInputControl} un-marks
     * itself on the next text change.
     *
     * @param node the input to highlight
     */
    public static void mark(Node node) {
        if (!node.getStyleClass().contains(ERROR)) {
            node.getStyleClass().add(ERROR);
        }
        if (node instanceof TextInputControl input) {
            input.textProperty().addListener(new javafx.beans.value.ChangeListener<>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends String> obs,
                                    String oldValue, String newValue) {
                    input.getStyleClass().remove(ERROR);
                    input.textProperty().removeListener(this);
                }
            });
        }
    }

    /**
     * Marks {@code node} when {@code missing} is true, otherwise clears it.
     *
     * @return {@code missing}, so guard checks can accumulate: {@code anyMissing |= markIf(...)}
     */
    public static boolean markIf(Node node, boolean missing) {
        if (missing) {
            mark(node);
        } else {
            clear(node);
        }
        return missing;
    }

    /** Removes the mark from the given nodes. */
    public static void clear(Node... nodes) {
        for (Node node : nodes) {
            node.getStyleClass().remove(ERROR);
        }
    }
}
