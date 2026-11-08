package org.fxt.freexmltoolkit.controls.v2.view;

import org.fxt.freexmltoolkit.i18n.I18n;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * The zoom control at the bottom-right of the diagram (Figma 07.6b):
 * {@code [⊟] 100% [⊞]}; a click on the percentage resets to 100%.
 */
final class XsdGraphZoomControl extends HBox {

    private final Label percent = new Label("100%");

    XsdGraphZoomControl(Runnable zoomOut, Runnable zoomIn, Runnable reset) {
        getStyleClass().add("fxt-graph-zoom-control");
        setId("graph-zoom-control");
        setAlignment(Pos.CENTER);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        percent.getStyleClass().add("fxt-graph-zoom-percent");
        percent.setId("graph-zoom-label");
        percent.setTooltip(new Tooltip(I18n.t("XsdGraphZoomControl.reset-zoom-to-100-ctrl")));
        percent.setOnMouseClicked(e -> reset.run());

        getChildren().addAll(
                iconButton("bi-dash-square", "graph-zoom-out", "Zoom Out (Ctrl -)", zoomOut),
                percent,
                iconButton("bi-plus-square", "graph-zoom-in", "Zoom In (Ctrl +)", zoomIn));
    }

    private static Button iconButton(String literal, String id, String tooltip, Runnable action) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(15);
        Button button = new Button();
        button.setGraphic(icon);
        button.setId(id);
        button.getStyleClass().add("fxt-graph-zoom-icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return button;
    }

    /** @return the percentage label the view updates on every zoom change */
    Label percentLabel() {
        return percent;
    }
}
