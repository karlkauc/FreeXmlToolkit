package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The one header row every activity side panel starts with: the upper-case panel title
 * (workflow-coloured via {@code fxt-panel-title}) and optional trailing controls such as the
 * ⋮ overflow menu. Using this control everywhere keeps title indent, height and colour
 * identical across panels; the {@code fxt-vp-header} rule fixes the row height so panels
 * with and without trailing controls line up.
 *
 * <p>FXML: {@code <SidePanelHeader title="PDF / FOP"/>}; child nodes become the trailing
 * controls.
 */
public final class SidePanelHeader extends HBox {

    /** Style class of the header row. */
    public static final String STYLE_CLASS = "fxt-vp-header";

    private final Label label = new Label();

    public SidePanelHeader() {
        // fxt-side-panel-title: the shell convention (and UnifiedShellViewTest) identify the
        // active panel's title by it; fxt-vp-title/fxt-panel-title carry the visual styling.
        label.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title", "fxt-panel-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(label, spacer);
        getStyleClass().add(STYLE_CLASS);
        setAlignment(Pos.CENTER_LEFT);
    }

    /**
     * @param title    the panel title, shown as given (callers pass upper case)
     * @param trailing controls right-aligned in the header (e.g. an overflow menu)
     */
    public static HBox create(String title, Node... trailing) {
        SidePanelHeader header = new SidePanelHeader();
        header.setTitle(title);
        header.getChildren().addAll(trailing);
        return header;
    }

    /** The panel title, shown as given (upper case by convention). */
    public StringProperty titleProperty() {
        return label.textProperty();
    }

    public String getTitle() {
        return label.getText();
    }

    public void setTitle(String title) {
        label.setText(title);
    }
}
