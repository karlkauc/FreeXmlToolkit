package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Pos;
import javafx.scene.control.ButtonBase;

/**
 * Small layout helpers shared by the activity side panels. The panels are narrow (~250px),
 * so action buttons are laid out full-width in a single column with left-aligned icon + label
 * to keep their text readable instead of ellipsizing to "…".
 */
final class SidePanelLayout {

    private SidePanelLayout() {
    }

    /** Makes an action button fill the side-panel width with a left-aligned icon + label. */
    static <T extends ButtonBase> T fill(T button) {
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        return button;
    }
}
