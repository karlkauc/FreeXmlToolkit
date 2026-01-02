package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Renders icon symbols on JavaFX Canvas using geometric shapes.
 *
 * This utility class provides efficient icon rendering for XSD node visualization.
 * Icons are rendered using Canvas primitives (rectangles, circles, lines, polygons)
 * rather than complex SVG path parsing, which provides better performance and
 * simplicity on Canvas.
 *
 * Icons represent Bootstrap Icon semantics through geometric shapes:
 * - Diagrams: geometric patterns representing structure
 * - Files: document-like shapes
 * - Attributes: @ symbol or specialized shapes
 * - Lists/Sequences: linear patterns
 * - Choice: branching patterns
 *
 * @see <a href="https://github.com/twbs/icons">Bootstrap Icons Reference</a>
 */
public class SvgIconRenderer {

    private SvgIconRenderer() {
        // Utility class
    }

    /**
     * Renders an icon symbol on the given GraphicsContext.
     *
     * @param gc the GraphicsContext to draw on
     * @param iconLiteral the icon identifier (e.g., "bi-diagram-3")
     * @param x the x-coordinate for the top-left corner
     * @param y the y-coordinate for the top-left corner
     * @param size the size of the icon (width and height in pixels)
     * @param color the color to render the icon in
     */
    public static void renderIcon(GraphicsContext gc, String iconLiteral,
                                   double x, double y, double size, Color color) {
        if (iconLiteral == null || iconLiteral.isEmpty()) {
            renderPlaceholder(gc, x, y, size, color);
            return;
        }

        gc.save();
        gc.setFill(color);
        gc.setStroke(color);
        gc.setLineWidth(Math.max(0.5, size / 20.0));

        switch (iconLiteral) {
            case "bi-diagram-3":
            case "bi-diagram-2":
                renderDiagram(gc, x, y, size);
                break;
            case "bi-file-earmark-code":
                renderDocument(gc, x, y, size);
                break;
            case "bi-at":
                renderAttribute(gc, x, y, size);
                break;
            case "bi-list-check":
                renderSequence(gc, x, y, size);
                break;
            case "bi-signpost-2":
                renderChoice(gc, x, y, size);
                break;
            case "bi-asterisk":
                renderAll(gc, x, y, size);
                break;
            default:
                renderPlaceholder(gc, x, y, size, color);
        }

        gc.restore();
    }

    /**
     * Renders a diagram icon (box with internal pattern).
     */
    private static void renderDiagram(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.1;
        double innerX = x + margin;
        double innerY = y + margin;
        double innerSize = size - 2 * margin;

        // Main box
        gc.strokeRect(innerX, innerY, innerSize, innerSize);

        // Internal pattern
        double cellSize = innerSize / 3;
        gc.fillRect(innerX + cellSize * 0.2, innerY + cellSize * 0.2, cellSize * 0.6, cellSize * 0.6);
        gc.fillRect(innerX + cellSize * 1.2, innerY + cellSize * 0.2, cellSize * 0.6, cellSize * 0.6);
        gc.fillRect(innerX + cellSize * 0.2, innerY + cellSize * 1.2, cellSize * 0.6, cellSize * 0.6);
    }

    /**
     * Renders a document icon (page with lines).
     */
    private static void renderDocument(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.15;
        double docX = x + margin;
        double docY = y + margin;
        double docWidth = size - 2 * margin;
        double docHeight = size - 2 * margin;

        // Document outline
        gc.strokeRect(docX, docY, docWidth, docHeight);

        // Fold corner
        double cornerSize = size * 0.2;
        gc.strokePolyline(
                new double[]{docX + docWidth - cornerSize, docX + docWidth, docX + docWidth},
                new double[]{docY, docY + cornerSize, docY},
                3
        );

        // Lines (document content)
        double lineSpacing = docHeight / 4;
        double lineStartX = docX + size * 0.1;
        double lineWidth = docWidth * 0.7;

        for (int i = 1; i < 3; i++) {
            gc.strokeLine(lineStartX, docY + lineSpacing * i, lineStartX + lineWidth, docY + lineSpacing * i);
        }
    }

    /**
     * Renders an attribute icon (@ symbol).
     */
    private static void renderAttribute(GraphicsContext gc, double x, double y, double size) {
        double centerX = x + size / 2;
        double centerY = y + size / 2;
        double radius = size * 0.35;
        double innerRadius = radius * 0.5;

        // Outer circle
        gc.strokeOval(centerX - radius, centerY - radius, 2 * radius, 2 * radius);

        // Inner circle
        gc.strokeOval(centerX - innerRadius, centerY - innerRadius, 2 * innerRadius, 2 * innerRadius);

        // @ symbol
        double charRadius = size * 0.15;
        gc.strokeOval(centerX - charRadius, centerY - charRadius, 2 * charRadius, 2 * charRadius);
    }

    /**
     * Renders a sequence icon (list with checkmarks).
     */
    private static void renderSequence(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.15;
        double itemX = x + margin;
        double itemY = y + margin;
        double itemSize = (size - 2 * margin) / 3;
        double spacing = itemSize * 0.1;

        // Three items
        for (int i = 0; i < 3; i++) {
            double currentY = itemY + i * (itemSize + spacing);

            // Item box
            gc.strokeRect(itemX, currentY, itemSize * 2, itemSize * 0.8);

            // Checkmark
            double checkX = itemX + itemSize * 1.7;
            double checkY = currentY + itemSize * 0.4;
            double checkSize = itemSize * 0.3;

            gc.strokeLine(checkX, checkY + checkSize * 0.5, checkX + checkSize * 0.3, checkY + checkSize);
            gc.strokeLine(checkX + checkSize * 0.3, checkY + checkSize, checkX + checkSize, checkY);
        }
    }

    /**
     * Renders a choice icon (branching pattern).
     */
    private static void renderChoice(GraphicsContext gc, double x, double y, double size) {
        double centerX = x + size / 2;
        double centerY = y + size / 2;
        double branchSize = size * 0.3;

        // Central point
        gc.fillOval(centerX - size * 0.08, centerY - size * 0.08, size * 0.16, size * 0.16);

        // Four branches (forming a cross/plus pattern)
        double[] branchAngles = {0, Math.PI / 2, Math.PI, 3 * Math.PI / 2};
        for (double angle : branchAngles) {
            double endX = centerX + branchSize * Math.cos(angle);
            double endY = centerY + branchSize * Math.sin(angle);
            gc.strokeLine(centerX, centerY, endX, endY);

            // Branch endpoint
            gc.fillOval(endX - size * 0.08, endY - size * 0.08, size * 0.16, size * 0.16);
        }
    }

    /**
     * Renders an ALL icon (asterisk pattern).
     */
    private static void renderAll(GraphicsContext gc, double x, double y, double size) {
        double centerX = x + size / 2;
        double centerY = y + size / 2;
        double radius = size * 0.3;

        // Central point
        gc.fillOval(centerX - size * 0.06, centerY - size * 0.06, size * 0.12, size * 0.12);

        // Six rays (asterisk/star pattern)
        int rays = 6;
        for (int i = 0; i < rays; i++) {
            double angle = (i * 2.0 * Math.PI) / rays;
            double endX = centerX + radius * Math.cos(angle);
            double endY = centerY + radius * Math.sin(angle);
            gc.strokeLine(centerX, centerY, endX, endY);
        }
    }

    /**
     * Renders a placeholder when icon is not found.
     */
    private static void renderPlaceholder(GraphicsContext gc, double x, double y,
                                         double size, Color color) {
        gc.save();
        gc.setFill(color);
        gc.fillRect(x, y, size, size);
        gc.setFill(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeText("?", x + size / 3, y + size * 2 / 3);
        gc.restore();
    }

    /**
     * Represents a parsed SVG path that can be rendered on Canvas.
     * Uses JavaFX's SVGPath for parsing and renders via custom path implementation.
     */
    private static class SvgPath {
        private final javafx.scene.shape.SVGPath svgPath;
        private final String pathData;

        SvgPath(String pathData) {
            this.pathData = pathData;
            this.svgPath = new javafx.scene.shape.SVGPath();
            this.svgPath.setContent(pathData);
        }

        /**
         * Renders this path on the given GraphicsContext using path tracing.
         * Note: This is a simplified implementation that fills the path outline.
         */
        void render(GraphicsContext gc) {
            try {
                // Get the bounds and use them to render the path
                javafx.geometry.Bounds bounds = svgPath.getBoundsInLocal();

                if (bounds.isEmpty()) {
                    return;
                }

                // Scale path to fit in 16x16 viewport (Bootstrap icon standard size)
                double scale = 16.0 / Math.max(bounds.getWidth(), bounds.getHeight());

                gc.save();

                // Apply transformation
                gc.scale(scale, scale);
                gc.translate(-bounds.getMinX(), -bounds.getMinY());

                // Render by tracing the path outline
                // Since we can't directly render SVGPath on Canvas, we use basic shape approximation
                renderPathApproximation(gc, pathData);

                gc.restore();
            } catch (Exception e) {
                // Fallback to simple rectangle if rendering fails
                gc.fillRect(0, 0, 16, 16);
            }
        }

        /**
         * Approximates path rendering by using basic geometric shapes.
         * This is a simplified approach that works well for icon-sized graphics.
         */
        private void renderPathApproximation(GraphicsContext gc, String pathData) {
            // For now, use a simple implementation that traces the path
            // In a production version, this would use a full SVG path parser
            try {
                // Create a canvas image and render the SVGPath onto it
                javafx.scene.image.WritableImage image = new javafx.scene.image.WritableImage(16, 16);
                javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                params.setFill(javafx.scene.paint.Color.TRANSPARENT);

                // Note: SVGPath rendering requires a Scene, so we use a workaround
                // For simplicity, we'll use a basic approximation for the initial implementation

                // This is a placeholder that draws a simple pattern
                // The actual implementation would benefit from using a proper SVG rendering library
                gc.fillRect(0, 0, 16, 16);

            } catch (Exception e) {
                gc.fillRect(0, 0, 16, 16);
            }
        }
    }
}
