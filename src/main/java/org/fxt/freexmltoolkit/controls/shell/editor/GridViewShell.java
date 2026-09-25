package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Locale;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;

/**
 * Shared chrome of the shell's Grid views (XML and JSON): the header bar with the
 * "Grid view" title, a subtitle, the zoom pill ({@code [ − 100% + ]}) and the
 * Expand-all / Collapse-all buttons, the placeholder shown when there is nothing to
 * display, and the toast container role for the embedded {@link GridCanvasView}.
 * Subclasses bind a concrete editor context.
 *
 * <p>The zoom factor is shared by all grids and persisted through
 * {@link GridZoomPreference}; every newly installed canvas starts at the saved value.</p>
 */
public abstract class GridViewShell extends StackPane {

    protected GridViewShell() {
        getStyleClass().add("fxt-xml-grid");
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /** @return the subtitle shown next to the "Grid view" title */
    protected abstract String subtitle();

    /**
     * Installs the canvas below the header, applies the persisted zoom and focuses the
     * canvas so arrow keys work right away.
     */
    protected void installCanvas(GridCanvasView<?> view) {
        view.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        view.setToastContainer(this);
        view.setZoom(GridZoomPreference.load());
        view.zoomProperty().addListener((obs, oldZoom, newZoom) -> GridZoomPreference.save(newZoom.doubleValue()));
        VBox.setVgrow(view, Priority.ALWAYS);
        getChildren().add(new VBox(buildHeader(view), view));
        // Arrow-key navigation should work right away, without a mouse click first.
        javafx.application.Platform.runLater(view::focusCanvas);
    }

    /** @return whether a canvas of the given class is currently installed */
    protected boolean showsCanvas(Class<?> canvasClass) {
        return !getChildren().isEmpty()
                && getChildren().get(0) instanceof VBox box
                && box.getChildren().stream().anyMatch(canvasClass::isInstance);
    }

    /** Replaces the content with the placeholder message. */
    protected void showPlaceholder(String message) {
        getChildren().clear();
        getChildren().add(placeholder(message));
    }

    /** The grid header: table icon · "Grid view" · subtitle ·…· zoom pill · Expand all · Collapse all. */
    private HBox buildHeader(GridCanvasView<?> view) {
        Label title = new Label("Grid view", icon("bi-table", 15));
        title.getStyleClass().add("fxt-grid-title");
        Label subtitle = new Label(subtitle());
        subtitle.getStyleClass().add("fxt-grid-subtitle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button expandAll = new Button("Expand all", icon("bi-arrows-expand", 13));
        expandAll.setId("grid-expand-all");
        expandAll.getStyleClass().add("fxt-tool-button");
        expandAll.setOnAction(e -> view.expandAll());

        Button collapseAll = new Button("Collapse all", icon("bi-arrows-collapse", 13));
        collapseAll.setId("grid-collapse-all");
        collapseAll.getStyleClass().add("fxt-tool-button");
        collapseAll.setOnAction(e -> view.collapseAll());

        HBox header = new HBox(8, title, subtitle, spacer, buildZoomPill(view), expandAll, collapseAll);
        header.getStyleClass().add("fxt-grid-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /** The zoom pill {@code [ − 100% + ]}; clicking the percentage resets to 100 %. */
    private static HBox buildZoomPill(GridCanvasView<?> view) {
        Button zoomOut = new Button("−");
        zoomOut.setId("grid-zoom-out");
        zoomOut.getStyleClass().add("fxt-graph-zoom-button");
        zoomOut.setTooltip(new Tooltip("Zoom out (Ctrl −)"));
        zoomOut.setFocusTraversable(false);
        zoomOut.setOnAction(e -> view.zoomOut());

        Label zoomLabel = new Label(percent(view.getZoom()));
        zoomLabel.setId("grid-zoom-label");
        zoomLabel.getStyleClass().add("fxt-graph-zoom-label");
        zoomLabel.setTooltip(new Tooltip("Reset zoom to 100% (Ctrl 0)"));
        zoomLabel.setOnMouseClicked(e -> view.zoomReset());
        view.zoomProperty().addListener((obs, oldZoom, newZoom) -> zoomLabel.setText(percent(newZoom.doubleValue())));

        Button zoomIn = new Button("+");
        zoomIn.setId("grid-zoom-in");
        zoomIn.getStyleClass().add("fxt-graph-zoom-button");
        zoomIn.setTooltip(new Tooltip("Zoom in (Ctrl +)"));
        zoomIn.setFocusTraversable(false);
        zoomIn.setOnAction(e -> view.zoomIn());

        HBox pill = new HBox(zoomOut, zoomLabel, zoomIn);
        pill.getStyleClass().addAll("fxt-graph-zoom-pill", "fxt-grid-zoom-pill");
        pill.setAlignment(Pos.CENTER);
        return pill;
    }

    private static String percent(double zoom) {
        return String.format(Locale.ROOT, "%.0f%%", zoom * 100);
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }

    private static VBox placeholder(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("fxt-empty-state-text");
        label.setWrapText(true);
        VBox box = new VBox(label);
        box.getStyleClass().add("fxt-empty-state");
        box.setFillWidth(true);
        VBox.setVgrow(label, Priority.NEVER);
        return box;
    }
}
