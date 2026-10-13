package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;

/**
 * The header bar of a {@link GridViewShell}: "Grid view" title, subtitle, the zoom pill
 * ({@code [ − 100% + ]}) and the Expand-all / Collapse-all buttons, all acting on one
 * {@link GridCanvasView}. The layout lives in {@code /pages/views/GridViewHeader.fxml}.
 */
final class GridViewHeader extends HBox {

    private final GridCanvasView<?> view;
    @FXML private Label subtitle;
    @FXML private Label zoomLabel;

    GridViewHeader(GridCanvasView<?> view, String subtitleText) {
        this.view = view;
        FxmlViews.load(this, "/pages/views/GridViewHeader.fxml");
        subtitle.setText(subtitleText);
        zoomLabel.setText(percent(view.getZoom()));
        view.zoomProperty().addListener((obs, oldZoom, newZoom) -> zoomLabel.setText(percent(newZoom.doubleValue())));
    }

    @FXML
    public void onZoomOut() {
        view.zoomOut();
    }

    @FXML
    public void onZoomReset() {
        view.zoomReset();
    }

    @FXML
    public void onZoomIn() {
        view.zoomIn();
    }

    @FXML
    public void onExpandAll() {
        view.expandAll();
    }

    @FXML
    public void onCollapseAll() {
        view.collapseAll();
    }

    private static String percent(double zoom) {
        return String.format(Locale.ROOT, "%.0f%%", zoom * 100);
    }
}
