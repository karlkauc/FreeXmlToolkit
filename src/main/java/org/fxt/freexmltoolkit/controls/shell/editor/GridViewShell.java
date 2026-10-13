package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;

/**
 * Shared chrome of the shell's Grid views (XML and JSON): the header bar
 * ({@link GridViewHeader}), the placeholder shown when there is nothing to
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
        getChildren().add(new VBox(new GridViewHeader(view, subtitle()), view));
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
