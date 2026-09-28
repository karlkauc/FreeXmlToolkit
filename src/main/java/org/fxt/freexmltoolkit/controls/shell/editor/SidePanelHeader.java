package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The one header row every activity side panel starts with: the upper-case panel title
 * (workflow-coloured via {@code fxt-panel-title}) and optional trailing controls such as the
 * ⋮ overflow menu. Using this factory everywhere keeps title indent, height and colour
 * identical across panels; the {@code fxt-vp-header} rule fixes the row height so panels
 * with and without trailing controls line up.
 */
public final class SidePanelHeader {

    /** Style class of the header row. */
    public static final String STYLE_CLASS = "fxt-vp-header";

    private SidePanelHeader() {
    }

    /**
     * @param title    the panel title, shown as given (callers pass upper case)
     * @param trailing controls right-aligned in the header (e.g. an overflow menu)
     */
    public static HBox create(String title, Node... trailing) {
        Label label = new Label(title);
        // fxt-side-panel-title: the shell convention (and UnifiedShellViewTest) identify the
        // active panel's title by it; fxt-vp-title/fxt-panel-title carry the visual styling.
        label.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title", "fxt-panel-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(label, spacer);
        header.getChildren().addAll(trailing);
        header.getStyleClass().add(STYLE_CLASS);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }
}
