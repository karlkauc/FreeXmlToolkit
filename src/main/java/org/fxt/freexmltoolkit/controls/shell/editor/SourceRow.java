package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * A bound-source row shared by the Validation, Transform, FOP and Signature panels:
 * file-type icon · file name · (extras such as a favourite star) · a visible "Change"
 * button. Keeps the {@code fxt-vp-source-row} style class so the drag-and-drop feedback
 * and click-to-open behaviour of the existing rows are untouched.
 */
final class SourceRow extends HBox {

    private final Button change;

    SourceRow(String iconLiteral, Label nameLabel, Runnable changeAction, Node... extras) {
        super(8);
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(15);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        change = PanelActionList.inlineRow("Change", "bi-folder2-open", changeAction);
        getChildren().addAll(icon, nameLabel, spacer);
        getChildren().addAll(extras);
        getChildren().add(change);
        getStyleClass().add("fxt-vp-source-row");
        setAlignment(Pos.CENTER_LEFT);
    }

    /** @return the "Change" button (for tests and for callers that want to disable it) */
    Button changeButton() {
        return change;
    }
}
