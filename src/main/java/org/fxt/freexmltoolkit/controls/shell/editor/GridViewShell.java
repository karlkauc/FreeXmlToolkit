package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;

/**
 * Shared chrome of the shell's Grid views (XML and JSON): the header bar with the
 * "Grid view" title, a subtitle and the Collapse-all button, the placeholder shown when
 * there is nothing to display, and the toast container role for the embedded
 * {@link GridCanvasView}. Subclasses bind a concrete editor context.
 */
public abstract class GridViewShell extends StackPane {

    protected GridViewShell() {
        getStyleClass().add("fxt-xml-grid");
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /** @return the subtitle shown next to the "Grid view" title */
    protected abstract String subtitle();

    /** Installs the canvas below the header and focuses it so arrow keys work right away. */
    protected void installCanvas(GridCanvasView<?> view) {
        view.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        view.setToastContainer(this);
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

    /** The mockup's grid header: table icon · "Grid view" · subtitle ·…· Collapse all. */
    private HBox buildHeader(GridCanvasView<?> view) {
        Label title = new Label("Grid view", icon("bi-table", 15));
        title.getStyleClass().add("fxt-grid-title");
        Label subtitle = new Label(subtitle());
        subtitle.getStyleClass().add("fxt-grid-subtitle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button collapseAll = new Button("Collapse all", icon("bi-arrows-collapse", 13));
        collapseAll.setId("grid-collapse-all");
        collapseAll.getStyleClass().add("fxt-tool-button");
        collapseAll.setOnAction(e -> view.collapseAll());
        HBox header = new HBox(8, title, subtitle, spacer, collapseAll);
        header.getStyleClass().add("fxt-grid-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
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
