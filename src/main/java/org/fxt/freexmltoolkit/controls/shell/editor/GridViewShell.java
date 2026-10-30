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
 * <p>The zoom factor, word wrap and the cell-height limit are shared by all grids and
 * persisted through {@link GridZoomPreference} / {@link GridDisplayPreferences}; every newly
 * installed canvas starts at the saved values.</p>
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
        configure(view);
        GridDisplayPreferences.loadColors();
        primary = view;
        secondary = null;
        header = new GridViewHeader(view, subtitle(), this::setSplit, split);
        header.configureSidePanel(hasSidePanel(), sidePanelShown, this::setSidePanelShown);
        layoutRoot = new VBox(header, view);
        getChildren().add(layoutRoot);
        if (split != null || (sidePanelShown && hasSidePanel())) {
            applySplit();
        }
        // Arrow-key navigation should work right away, without a mouse click first.
        javafx.application.Platform.runLater(view::focusCanvas);
    }

    private void configure(GridCanvasView<?> view) {
        view.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        view.setToastContainer(this);
        view.setZoom(GridZoomPreference.load());
        view.zoomProperty().addListener((obs, oldZoom, newZoom) -> GridZoomPreference.save(newZoom.doubleValue()));
        view.setWordWrap(GridDisplayPreferences.loadWordWrap());
        view.setMaxCellLines(GridDisplayPreferences.loadMaxCellLines());
        view.setSiblingGroupSize(GridDisplayPreferences.loadSiblingGroupSize());
        VBox.setVgrow(view, Priority.ALWAYS);
    }

    // ==================== Split view ====================

    private GridCanvasView<?> primary;
    private GridCanvasView<?> secondary;
    private GridViewHeader header;
    private VBox layoutRoot;
    /** The split orientation (kept across context replacements), {@code null} = not split. */
    private javafx.geometry.Orientation split;

    /**
     * Creates a second canvas over the same editor context for the split view (XMLSpy Split
     * View: two independently scrolled views of one document).
     *
     * @return the canvas, or {@code null} when there is no document
     */
    protected abstract GridCanvasView<?> createSplitCanvas();

    /**
     * Splits the grid into two views of the same document (top/bottom or side by side), or
     * back into one. Both views scroll, expand and select independently; an edit in either is
     * shown in both.
     *
     * @param orientation {@link javafx.geometry.Orientation#VERTICAL} = top/bottom,
     *                    {@code HORIZONTAL} = side by side, {@code null} = one view
     */
    public void setSplit(javafx.geometry.Orientation orientation) {
        split = orientation;
        if (primary != null && layoutRoot != null) {
            applySplit();
        }
    }

    /** @return the split orientation, {@code null} when not split */
    public javafx.geometry.Orientation getSplit() {
        return split;
    }

    /** @return the second canvas of the split view, or {@code null} */
    public GridCanvasView<?> getSecondaryCanvas() {
        return split != null ? secondary : null;
    }

    private void applySplit() {
        if (split == null) {
            showContent(primary);
            return;
        }
        if (secondary == null) {
            secondary = createSplitCanvas();
            if (secondary == null) {
                split = null;
                showContent(primary);
                return;
            }
            configure(secondary);
            // An edit in one view is shown in the other (both share the document)
            java.util.function.Consumer<String> original = primary.getOnDocumentModified();
            GridCanvasView<?> first = primary;
            GridCanvasView<?> second = secondary;
            first.setOnDocumentModified(text -> {
                if (original != null) {
                    original.accept(text);
                }
                if (split != null) {
                    second.rebuild();
                }
            });
            second.setOnDocumentModified(text -> {
                if (original != null) {
                    original.accept(text);
                }
                first.rebuild();
            });
        }
        javafx.scene.control.SplitPane pane = new javafx.scene.control.SplitPane(primary, secondary);
        pane.setId("grid-split-pane");
        pane.setOrientation(split);
        pane.setDividerPositions(0.5);
        showContent(pane);
    }

    // ==================== Side panel (XML: entry helpers) ====================

    private boolean sidePanelShown = GridDisplayPreferences.loadEntryHelpers();

    /**
     * @return the panel shown beside the grid when switched on in the header (XML: the entry
     *         helpers), or {@code null} when this view has none; the same instance every call
     */
    protected javafx.scene.Node sidePanel() {
        return null;
    }

    /**
     * @return whether this view offers a side panel; answered without building it, so a hidden panel
     *         costs nothing when the grid opens
     */
    protected boolean hasSidePanel() {
        return false;
    }

    /** Shows or hides the side panel (persisted). */
    public void setSidePanelShown(boolean shown) {
        sidePanelShown = shown;
        GridDisplayPreferences.saveEntryHelpers(shown);
        if (primary != null && layoutRoot != null) {
            applySplit();
        }
    }

    /** @return whether the side panel is switched on */
    public boolean isSidePanelShown() {
        return sidePanelShown && hasSidePanel();
    }

    /** Puts the grid (one canvas or the split pane) below the header, with the side panel beside it. */
    private void showContent(javafx.scene.Node content) {
        javafx.scene.Node panel = sidePanelShown ? sidePanel() : null;
        javafx.scene.Node body = content;
        if (panel != null) {
            javafx.scene.control.SplitPane withPanel = new javafx.scene.control.SplitPane(content, panel);
            withPanel.setId("grid-side-split");
            javafx.scene.control.SplitPane.setResizableWithParent(panel, false);
            withPanel.setDividerPositions(0.72);
            body = withPanel;
        }
        VBox.setVgrow(body, Priority.ALWAYS);
        layoutRoot.getChildren().setAll(header, body);
    }

    /** Rebuilds the second view of a split (after a change made outside the grid). */
    protected void rebuildSecondary() {
        if (split != null && secondary != null) {
            secondary.rebuild();
        }
    }

    /** @return whether a canvas of the given class is currently installed */
    protected boolean showsCanvas(Class<?> canvasClass) {
        return primary != null && canvasClass.isInstance(primary) && layoutRoot != null
                && getChildren().contains(layoutRoot);
    }

    /** Replaces the content with the placeholder message. */
    protected void showPlaceholder(String message) {
        getChildren().clear();
        primary = null;
        secondary = null;
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
