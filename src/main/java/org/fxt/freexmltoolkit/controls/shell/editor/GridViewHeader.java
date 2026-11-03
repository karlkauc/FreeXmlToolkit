package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
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
 * <p>
 * In a narrow editor column the bar adapts in stages ({@link Mode}) so every action stays
 * reachable: first the subtitle goes and the tool buttons show only their icons, then the tool
 * buttons move into the ⋯ menu. What does not fit even then is clipped.
 */
final class GridViewHeader extends HBox {

    /** How much of the bar is shown, from wide to narrow. */
    enum Mode {
        /** Everything with text. */
        FULL,
        /** No subtitle, icon-only tool buttons. */
        COMPACT,
        /** Title icon, zoom pill and ⋯; the tool buttons are entries of the ⋯ menu. */
        MINIMAL
    }

    /** Room for the zoom percentage growing ("100%" → "1000%") after the widths were measured. */
    private static final double SLACK = 12;

    @FXML private Label title;
    @FXML private Button optimalWidthsButton;
    @FXML private Button expandAllButton;
    @FXML private Button collapseAllButton;
    @FXML private MenuItem overflowOptimalWidths;
    @FXML private CheckMenuItem overflowWordWrap;
    @FXML private MenuItem overflowExpandAll;
    @FXML private MenuItem overflowCollapseAll;
    @FXML private CheckMenuItem overflowSidePanel;
    @FXML private SeparatorMenuItem overflowSeparator;
    private boolean sidePanelSupported;
    private Mode mode = Mode.FULL;
    /** Width each mode needs, measured once per scene / configuration; null = measure again. */
    private double[] modeWidths;

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
    @FXML private ToggleButton sidePanelButton;
    private java.util.function.Consumer<Boolean> sidePanelHandler = shown -> { };
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
        // The buttons keep their size (minWidth USE_PREF), but the strip itself must not: its summed
        // minimum (~760 px) made the whole grid wider than a narrow editor column, so the grid ran
        // under the Properties pane and the right end of its scroll bar was out of reach. The strip
        // shrinks and clips what no longer fits.
        setMinWidth(0);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        sceneProperty().addListener((obs, oldScene, newScene) -> modeWidths = null);
        overflowWordWrap.selectedProperty().bindBidirectional(wordWrapButton.selectedProperty());
        overflowSidePanel.selectedProperty().bindBidirectional(sidePanelButton.selectedProperty());
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

    /**
     * Shows the side-panel toggle (XML: entry helpers) when the view has a side panel.
     *
     * @param supported whether the view has a side panel
     * @param shown     its current state
     * @param handler   receives the new state
     */
    void configureSidePanel(boolean supported, boolean shown, java.util.function.Consumer<Boolean> handler) {
        sidePanelSupported = supported;
        sidePanelButton.setSelected(shown);
        sidePanelHandler = handler;
        modeWidths = null;
        applyMode(mode);
        requestLayout();
    }

    /** @return the stage the bar is shown in (tests) */
    Mode mode() {
        return mode;
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        if (width > 0 && getScene() != null) {
            if (modeWidths == null) {
                modeWidths = measureModes();
            }
            Mode fit = Mode.MINIMAL;
            for (Mode candidate : Mode.values()) {
                if (modeWidths[candidate.ordinal()] + SLACK <= width) {
                    fit = candidate;
                    break;
                }
            }
            if (fit != mode) {
                applyMode(fit);
            }
        }
        super.layoutChildren();
    }

    /**
     * Measures the width every mode needs by showing it once. Runs only after a scene or
     * configuration change, so a resize just compares numbers and never flips the children.
     */
    private double[] measureModes() {
        Mode shown = mode;
        double[] widths = new double[Mode.values().length];
        for (Mode candidate : Mode.values()) {
            applyMode(candidate);
            applyCss();
            double sum = snappedLeftInset() + snappedRightInset();
            int managed = 0;
            for (Node child : getChildren()) {
                if (child.isManaged()) {
                    sum += child.prefWidth(-1);
                    managed++;
                }
            }
            widths[candidate.ordinal()] = sum + Math.max(0, managed - 1) * getSpacing();
        }
        applyMode(shown);
        return widths;
    }

    private void applyMode(Mode next) {
        mode = next;
        boolean full = next == Mode.FULL;
        boolean inBar = next != Mode.MINIMAL;

        subtitle.setVisible(full);
        subtitle.setManaged(full);
        title.setContentDisplay(inBar ? ContentDisplay.LEFT : ContentDisplay.GRAPHIC_ONLY);
        ContentDisplay tools = full ? ContentDisplay.LEFT : ContentDisplay.GRAPHIC_ONLY;
        for (Labeled tool : new Labeled[]{optimalWidthsButton, wordWrapButton, expandAllButton, collapseAllButton}) {
            tool.setContentDisplay(tools);
            tool.setVisible(inBar);
            tool.setManaged(inBar);
        }
        sidePanelButton.setVisible(inBar && sidePanelSupported);
        sidePanelButton.setManaged(inBar && sidePanelSupported);

        for (MenuItem item : new MenuItem[]{overflowOptimalWidths, overflowWordWrap, overflowExpandAll,
                overflowCollapseAll, overflowSeparator}) {
            item.setVisible(!inBar);
        }
        overflowSidePanel.setVisible(!inBar && sidePanelSupported);
    }

    @FXML
    public void onSidePanel() {
        sidePanelHandler.accept(sidePanelButton.isSelected());
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
