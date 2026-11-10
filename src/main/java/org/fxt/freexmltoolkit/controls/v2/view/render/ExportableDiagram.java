package org.fxt.freexmltoolkit.controls.v2.view.render;

import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;

/**
 * A graphical view that can draw itself for an export (PNG, SVG, PDF, clipboard): the XSD
 * diagram, the XML/JSON grid and the profiler's flame graph. Coordinates are the view's own
 * unzoomed content coordinates; the exporter sets up scaling and tiling.
 *
 * <p>All methods run on the JavaFX application thread.
 */
public interface ExportableDiagram {

    /** @return the bounds of the whole content, or an empty rectangle when there is nothing to draw */
    Rectangle2D exportContentBounds();

    /** @return the part of the content currently on screen */
    Rectangle2D exportVisibleBounds();

    /** @return a short name of what is shown, used as title and default file name */
    String exportTitle();

    /**
     * Draws the part of the content meeting {@code region}. The surface is already transformed so
     * that content coordinates map onto it; the background has been painted. Interaction states
     * (selection, hover, drag feedback) are left out.
     *
     * @param g      the surface to draw on
     * @param region the part to draw (everything outside may be skipped)
     * @param theme  the palette to draw with
     */
    void paintExport(DiagramGraphics g, Rectangle2D region, DesignTokens.Theme theme);

    /**
     * @param theme the palette
     * @return the page colour behind the content in that palette
     */
    Color exportBackground(DesignTokens.Theme theme);

    /** Called after an export so the view can redraw itself (drawing may record hit-test geometry). */
    default void exportFinished() {
    }
}
