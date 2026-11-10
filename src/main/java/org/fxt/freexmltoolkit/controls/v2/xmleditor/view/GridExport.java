package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.v2.view.render.DiagramGraphics;
import org.fxt.freexmltoolkit.controls.v2.view.render.ExportableDiagram;

/**
 * The XML/JSON grid as an {@link ExportableDiagram}: all rows as currently expanded (open
 * tables, opened cells, column widths and filters as shown), drawn by the view's own
 * {@link GridPainter} in content coordinates.
 *
 * @param <N> the model node type
 */
final class GridExport<N> implements ExportableDiagram {

    private final GridCanvasView<N> view;

    GridExport(GridCanvasView<N> view) {
        this.view = view;
    }

    @Override
    public Rectangle2D exportContentBounds() {
        if (view.visibleRows.isEmpty() || view.totalWidth <= 0 || view.totalHeight <= 0) {
            return Rectangle2D.EMPTY;
        }
        return new Rectangle2D(0, 0, view.totalWidth, view.totalHeight);
    }

    @Override
    public Rectangle2D exportVisibleBounds() {
        double width = Math.max(1, view.viewportWidthModel());
        double height = Math.max(1, view.viewportHeightModel());
        return new Rectangle2D(view.scrollOffsetX, view.scrollOffsetY, width, height);
    }

    @Override
    public String exportTitle() {
        if (view.visibleRows.isEmpty()) {
            return "grid";
        }
        String label = view.visibleRows.getFirst().getLabel();
        return label == null || label.isBlank() ? "grid" : label;
    }

    @Override
    public void paintExport(DiagramGraphics g, Rectangle2D region, DesignTokens.Theme theme) {
        view.painter.renderExport(g, region, theme);
    }

    @Override
    public Color exportBackground(DesignTokens.Theme theme) {
        return GridPainter.pageBackground(theme);
    }

    @Override
    public void exportFinished() {
        view.render();
    }
}
