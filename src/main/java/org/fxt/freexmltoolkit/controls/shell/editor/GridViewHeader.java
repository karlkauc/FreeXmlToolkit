package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;

/**
 * The header bar of a {@link GridViewShell}: "Grid view" title, subtitle, the zoom pill
 * ({@code [ − 100% + ]}), Optimal widths, the Word-wrap toggle, Expand-all / Collapse-all and
 * the ⋯ menu with the cell-height limit, all acting on one {@link GridCanvasView}. The layout
 * lives in {@code /pages/views/GridViewHeader.fxml}; word wrap and the cell-height limit are
 * persisted through {@link GridDisplayPreferences}.
 */
final class GridViewHeader extends HBox {

    private final GridCanvasView<?> view;
    @FXML private Label subtitle;
    @FXML private Label zoomLabel;
    @FXML private ToggleButton wordWrapButton;
    @FXML private RadioMenuItem cellLinesOff;
    @FXML private RadioMenuItem cellLines3;
    @FXML private RadioMenuItem cellLines6;
    @FXML private RadioMenuItem cellLines12;
    @FXML private RadioMenuItem siblingGroupsOff;
    @FXML private RadioMenuItem siblingGroups100;
    @FXML private RadioMenuItem siblingGroups1000;
    @FXML private RadioMenuItem siblingGroups10000;
    @FXML private RadioMenuItem splitOff;
    @FXML private RadioMenuItem splitTopBottom;
    @FXML private RadioMenuItem splitSideBySide;
    private final java.util.function.Consumer<javafx.geometry.Orientation> splitHandler;

    GridViewHeader(GridCanvasView<?> view, String subtitleText) {
        this(view, subtitleText, orientation -> { }, null);
    }

    GridViewHeader(GridCanvasView<?> view, String subtitleText,
                   java.util.function.Consumer<javafx.geometry.Orientation> splitHandler,
                   javafx.geometry.Orientation currentSplit) {
        this.view = view;
        this.splitHandler = splitHandler;
        FxmlViews.load(this, "/pages/views/GridViewHeader.fxml");
        subtitle.setText(subtitleText);
        zoomLabel.setText(percent(view.getZoom()));
        view.zoomProperty().addListener((obs, oldZoom, newZoom) -> zoomLabel.setText(percent(newZoom.doubleValue())));
        wordWrapButton.setSelected(view.isWordWrap());
        selectCellLines(view.getMaxCellLines());
        (currentSplit == null ? splitOff
                : currentSplit == javafx.geometry.Orientation.VERTICAL ? splitTopBottom : splitSideBySide)
                .setSelected(true);
        (switch (view.getSiblingGroupSize()) {
            case 100 -> siblingGroups100;
            case 1000 -> siblingGroups1000;
            case 10000 -> siblingGroups10000;
            default -> siblingGroupsOff;
        }).setSelected(true);
    }

    private void selectCellLines(int lines) {
        RadioMenuItem item = switch (lines) {
            case 3 -> cellLines3;
            case 6 -> cellLines6;
            case 12 -> cellLines12;
            default -> cellLinesOff;
        };
        item.setSelected(true);
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
    public void onOptimalWidths() {
        view.optimalWidths();
    }

    @FXML
    public void onWordWrap() {
        boolean wrap = wordWrapButton.isSelected();
        view.setWordWrap(wrap);
        GridDisplayPreferences.saveWordWrap(wrap);
    }

    @FXML
    public void onExpandAll() {
        view.expandAll();
    }

    @FXML
    public void onCollapseAll() {
        view.collapseAll();
    }

    @FXML
    public void onCellLinesOff() {
        applyCellLines(0);
    }

    @FXML
    public void onCellLines3() {
        applyCellLines(3);
    }

    @FXML
    public void onCellLines6() {
        applyCellLines(6);
    }

    @FXML
    public void onCellLines12() {
        applyCellLines(12);
    }

    @FXML
    public void onSiblingGroupsOff() {
        applySiblingGroups(0);
    }

    @FXML
    public void onSiblingGroups100() {
        applySiblingGroups(100);
    }

    @FXML
    public void onSiblingGroups1000() {
        applySiblingGroups(1000);
    }

    @FXML
    public void onSiblingGroups10000() {
        applySiblingGroups(10000);
    }

    @FXML
    public void onSplitOff() {
        splitHandler.accept(null);
    }

    @FXML
    public void onSplitTopBottom() {
        splitHandler.accept(javafx.geometry.Orientation.VERTICAL);
    }

    @FXML
    public void onSplitSideBySide() {
        splitHandler.accept(javafx.geometry.Orientation.HORIZONTAL);
    }

    private void applySiblingGroups(int size) {
        view.setSiblingGroupSize(size);
        GridDisplayPreferences.saveSiblingGroupSize(size);
    }

    private void applyCellLines(int lines) {
        view.setMaxCellLines(lines);
        GridDisplayPreferences.saveMaxCellLines(lines);
    }

    private static String percent(double zoom) {
        return String.format(Locale.ROOT, "%.0f%%", zoom * 100);
    }
}
