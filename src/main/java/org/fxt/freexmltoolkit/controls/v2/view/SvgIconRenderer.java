package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Renders icon symbols on JavaFX Canvas using SVG path rendering and geometric shapes.
 * <p>
 * This utility class provides efficient icon rendering for XSD node visualization.
 * It first attempts to render icons using real SVG path data from {@link BootstrapIconPaths},
 * parsed and rendered via {@link SvgPathParser}. If no path data is available, it falls back
 * to hand-coded geometric shapes for backwards compatibility.
 * <p>
 * Icons represent Bootstrap Icon semantics:
 * - Diagrams: structure visualization
 * - Files: document-like shapes
 * - Attributes: @ symbol or specialized shapes
 * - Lists/Sequences: linear patterns
 * - Choice: branching patterns
 *
 * @see BootstrapIconPaths
 * @see SvgPathParser
 * @see <a href="https://github.com/twbs/icons">Bootstrap Icons Reference</a>
 */
public class SvgIconRenderer {

    private SvgIconRenderer() {
        // Utility class
    }

    /**
     * Renders an icon symbol on the given GraphicsContext.
     * <p>
     * This method first attempts to render the icon using real SVG path data from
     * {@link BootstrapIconPaths}. If no path data is available, it falls back to
     * hand-coded geometric shape rendering for backwards compatibility.
     *
     * @param gc          the GraphicsContext to draw on
     * @param iconLiteral the icon identifier (e.g., "bi-diagram-3")
     * @param x           the x-coordinate for the top-left corner
     * @param y           the y-coordinate for the top-left corner
     * @param size        the size of the icon (width and height in pixels)
     * @param color       the color to render the icon in
     */
    public static void renderIcon(GraphicsContext gc, String iconLiteral,
                                   double x, double y, double size, Color color) {
        if (iconLiteral == null || iconLiteral.isEmpty()) {
            renderPlaceholder(gc, x, y, size, color);
            return;
        }

        // First, try to render using real SVG path data
        if (renderFromSvgPath(gc, iconLiteral, x, y, size, color)) {
            return;
        }

        // Fall back to geometric shape rendering for icons without path data
        gc.save();
        gc.setFill(color);
        gc.setStroke(color);
        gc.setLineWidth(Math.max(0.5, size / 20.0));

        switch (iconLiteral) {
            case "bi-diagram-3":
            case "bi-diagram-2":
                renderDiagram(gc, x, y, size);
                break;
            case "bi-hdd-stack":
                renderHddStack(gc, x, y, size);
                break;
            case "bi-file-earmark-code":
            case "bi-file-text":
            case "bi-file-earmark":
            case "bi-file-earmark-binary":
            case "bi-chat-left-quote":
            case "bi-chat-quote":
            case "bi-tag":
            case "bi-tag-fill":
            case "bi-subtract":
                renderDocument(gc, x, y, size);
                break;
            case "bi-at":
                renderAttribute(gc, x, y, size);
                break;
            case "bi-list-check":
            case "bi-list-ul":
                renderSequence(gc, x, y, size);
                break;
            case "bi-list-ol":
                renderListOl(gc, x, y, size);
                break;
            case "bi-signpost-2":
            case "bi-signpost-split":
            case "bi-arrows-angle-expand":
                renderChoice(gc, x, y, size);
                break;
            case "bi-asterisk":
            case "bi-collection":
                renderAll(gc, x, y, size);
                break;
            case "bi-folder":
                renderFolder(gc, x, y, size);
                break;
            case "bi-key":
                renderKey(gc, x, y, size);
                break;
            case "bi-globe2":
            case "bi-translate":
                renderGlobe(gc, x, y, size);
                break;
            case "bi-fonts":
            case "bi-input-cursor-text":
                renderFonts(gc, x, y, size);
                break;
            case "bi-hash":
            case "bi-dash-square":
            case "bi-1-circle-fill":
            case "bi-2-circle-fill":
            case "bi-123":
                renderHash(gc, x, y, size);
                break;
            case "bi-percent":
                renderPercent(gc, x, y, size);
                break;
            case "bi-hexagon-fill":
                renderHexagon(gc, x, y, size);
                break;
            case "bi-box":
                renderBox(gc, x, y, size);
                break;
            case "bi-box-seam":
                renderBoxSeam(gc, x, y, size);
                break;
            case "bi-type":
                renderType(gc, x, y, size);
                break;
            case "bi-calculator":
                renderCalculator(gc, x, y, size);
                break;
            case "bi-calendar":
            case "bi-calendar2":
            case "bi-calendar3":
            case "bi-calendar-event":
            case "bi-calendar-date":
                renderCalendar(gc, x, y, size);
                break;
            case "bi-clock":
                renderClock(gc, x, y, size);
                break;
            case "bi-stopwatch":
                renderHourglass(gc, x, y, size);
                break;
            case "bi-hourglass-split":
                renderHourglass(gc, x, y, size);
                break;
            case "bi-toggle-on":
            case "bi-toggle2-on":
                renderToggle(gc, x, y, size);
                break;
            case "bi-shield-check":
                renderShield(gc, x, y, size);
                break;
            case "bi-link":
            case "bi-link-45deg":
                renderLink(gc, x, y, size);
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
     * Renders an HDD stack icon (database).
     */
    private static void renderHddStack(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.2;
        double width = size - 2 * margin;
        double height = (size - 2 * margin) / 3.5;
        double spacing = height * 0.25;
        double startX = x + margin;
        double startY = y + margin;

        for (int i = 0; i < 3; i++) {
            double currY = startY + i * (height + spacing);
            gc.strokeRect(startX, currY, width, height);
            // Light indicator
            gc.fillRect(startX + width * 0.1, currY + height * 0.3, height * 0.4, height * 0.4);
        }
    }

    /**
     * Renders a folder icon.
     */
    private static void renderFolder(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.15;
        double width = size - 2 * margin;
        double height = size * 0.6;
        double startX = x + margin;
        double startY = y + size * 0.3;

        // Folder body
        gc.strokeRect(startX, startY, width, height);

        // Folder tab
        gc.strokePolyline(
                new double[]{startX, startX + width * 0.4, startX + width * 0.5, startX + width, startX + width, startX},
                new double[]{startY, startY, startY - size * 0.1, startY - size * 0.1, startY + height, startY + height},
                6
        );
    }

    /**
     * Renders a key icon.
     */
    private static void renderKey(GraphicsContext gc, double x, double y, double size) {
        double headRadius = size * 0.25;
        double cx = x + size * 0.3;
        double cy = y + size * 0.3;

        // Key head
        gc.strokeOval(cx - headRadius, cy - headRadius, headRadius * 2, headRadius * 2);

        // Key stem (diagonal)
        double stemLen = size * 0.5;
        double endX = cx + stemLen;
        double endY = cy + stemLen;
        gc.strokeLine(cx + headRadius * 0.7, cy + headRadius * 0.7, endX, endY);

        // Teeth
        gc.strokeLine(endX, endY, endX, endY - size * 0.15);
        gc.strokeLine(endX - size * 0.1, endY - size * 0.1, endX - size * 0.1, endY - size * 0.2);
    }

    /**
     * Renders a globe icon.
     */
    private static void renderGlobe(GraphicsContext gc, double x, double y, double size) {
        double radius = size * 0.4;
        double cx = x + size / 2;
        double cy = y + size / 2;

        // Circle
        gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // Equator
        gc.strokeLine(cx - radius, cy, cx + radius, cy);

        // Meridian
        gc.strokeLine(cx, cy - radius, cx, cy + radius);

        // Curves
        gc.strokeOval(cx - radius * 0.5, cy - radius, radius, radius * 2);
        gc.strokeOval(cx - radius, cy - radius * 0.5, radius * 2, radius);
    }

    /**
     * Renders a fonts/text icon ("A").
     */
    private static void renderFonts(GraphicsContext gc, double x, double y, double size) {
        // Draw letter 'A' shape manually
        double width = size * 0.6;
        double height = size * 0.7;
        double left = x + (size - width) / 2;
        double top = y + (size - height) / 2;
        double bottom = top + height;
        double right = left + width;
        double midX = x + size / 2;

        // Legs
        gc.strokeLine(left, bottom, midX, top);
        gc.strokeLine(midX, top, right, bottom);

        // Crossbar
        double barY = top + height * 0.6;
        double barLeft = left + width * 0.25; // approx
        double barRight = right - width * 0.25; // approx
        gc.strokeLine(barLeft, barY, barRight, barY);
    }

    /**
     * Renders a calculator icon.
     */
    private static void renderCalculator(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.15;
        double width = size - 2 * margin;
        double height = size - 2 * margin;
        double startX = x + margin;
        double startY = y + margin;

        // Frame
        gc.strokeRect(startX, startY, width, height);

        // Screen
        gc.strokeRect(startX + width * 0.1, startY + height * 0.1, width * 0.8, height * 0.25);

        // Buttons (grid)
        double btnSize = width * 0.2;
        double gridY = startY + height * 0.45;
        double gridX = startX + width * 0.1;

        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                gc.fillRect(gridX + c * (btnSize * 1.2), gridY + r * (btnSize * 1.2), btnSize, btnSize);
            }
        }
    }

    /**
     * Renders an ordered list icon.
     */
    private static void renderListOl(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.15;
        double itemX = x + margin;
        double itemY = y + margin;
        double itemSize = (size - 2 * margin) / 3;
        double spacing = itemSize * 0.1;

        for (int i = 0; i < 3; i++) {
            double currentY = itemY + i * (itemSize + spacing);

            // Number (dot)
            gc.fillOval(itemX, currentY + itemSize * 0.2, itemSize * 0.4, itemSize * 0.4);

            // Line
            double lineX = itemX + itemSize;
            gc.strokeLine(lineX, currentY + itemSize * 0.4, lineX + itemSize * 1.5, currentY + itemSize * 0.4);
        }
    }

    /**
     * Renders a box with seam icon (package).
     */
    private static void renderBoxSeam(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.15;
        double width = size - 2 * margin;
        double height = size - 2 * margin;
        double startX = x + margin;
        double startY = y + margin;

        // Box outline
        gc.strokeRect(startX, startY, width, height);

        // Vertical seam
        gc.strokeLine(startX + width / 2, startY, startX + width / 2, startY + height);

        // Horizontal tape (top part)
        gc.strokeLine(startX, startY + height * 0.4, startX + width, startY + height * 0.4);

        // Flaps (diagonal lines for depth illusion)
        gc.strokeLine(startX, startY, startX + width * 0.2, startY + height * 0.2);
        gc.strokeLine(startX + width, startY, startX + width * 0.8, startY + height * 0.2);
    }

    /**
     * Renders a Type icon (letter T).
     */
    private static void renderType(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.2;
        double width = size - 2 * margin;
        double height = size - 2 * margin;
        double startX = x + margin;
        double startY = y + margin;

        // Top bar
        gc.strokeLine(startX, startY, startX + width, startY);
        // Serifs top left
        gc.strokeLine(startX, startY, startX, startY + height * 0.2);
        // Serifs top right
        gc.strokeLine(startX + width, startY, startX + width, startY + height * 0.2);

        // Vertical stem
        double midX = startX + width / 2;
        gc.strokeLine(midX, startY, midX, startY + height);

        // Bottom serif
        gc.strokeLine(midX - width * 0.2, startY + height, midX + width * 0.2, startY + height);
    }

    /**
     * Renders a link/chain icon.
     */
    private static void renderLink(GraphicsContext gc, double x, double y, double size) {
        double w = size * 0.4;
        double h = size * 0.25;
        double cx = x + size / 2;
        double cy = y + size / 2;

        // Left link
        gc.strokeOval(cx - w, cy - h / 2, w, h);
        // Right link
        gc.strokeOval(cx, cy - h / 2, w, h);
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
     * Attempts to render an icon using real SVG path data.
     *
     * @param gc          the GraphicsContext to draw on
     * @param iconLiteral the icon identifier
     * @param x           the x-coordinate
     * @param y           the y-coordinate
     * @param size        the desired size
     * @param color       the color to render in
     * @return true if the icon was rendered from SVG path data, false if no path data available
     */
    private static boolean renderFromSvgPath(GraphicsContext gc, String iconLiteral,
                                              double x, double y, double size, Color color) {
        String pathData = BootstrapIconPaths.getPath(iconLiteral);
        if (pathData == null) {
            return false;
        }

        // Use SvgPathParser to render the path with fill mode (Bootstrap Icons are typically filled)
        SvgPathParser.renderPath(gc, pathData, x, y, size, color, true, BootstrapIconPaths.VIEWBOX_SIZE);
        return true;
    }

    /**
     * Renders a hash/number-like icon (used for numeric datatypes).
     */
    private static void renderHash(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.25;
        double startX = x + margin;
        double startY = y + margin;
        double endX = x + size - margin;
        double endY = y + size - margin;

        gc.strokeLine(startX, startY, startX, endY);
        gc.strokeLine(endX, startY, endX, endY);
        gc.strokeLine(startX, startY, endX, startY);
        gc.strokeLine(startX, endY, endX, endY);
    }

    /**
     * Renders a percent icon (used for decimals).
     */
    private static void renderPercent(GraphicsContext gc, double x, double y, double size) {
        double circleRadius = size * 0.12;
        double offset = size * 0.15;
        gc.strokeLine(x + offset, y + size - offset, x + size - offset, y + offset);
        gc.strokeOval(x + offset * 0.8, y + offset * 0.8, circleRadius * 2, circleRadius * 2);
        gc.strokeOval(x + size - offset * 1.8 - circleRadius * 2, y + size - offset * 1.8 - circleRadius * 2,
                circleRadius * 2, circleRadius * 2);
    }

    /**
     * Renders a hexagon icon (used for float).
     */
    private static void renderHexagon(GraphicsContext gc, double x, double y, double size) {
        double r = size * 0.4;
        double cx = x + size / 2;
        double cy = y + size / 2;
        double[] xs = new double[6];
        double[] ys = new double[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 6 + i * Math.PI / 3;
            xs[i] = cx + r * Math.cos(angle);
            ys[i] = cy + r * Math.sin(angle);
        }
        gc.strokePolygon(xs, ys, 6);
    }

    /**
     * Renders a simple box (used for double).
     */
    private static void renderBox(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.2;
        gc.strokeRect(x + margin, y + margin, size - 2 * margin, size - 2 * margin);
    }

    /**
     * Renders a calendar icon (used for date/datetime).
     */
    private static void renderCalendar(GraphicsContext gc, double x, double y, double size) {
        double margin = size * 0.15;
        double header = size * 0.25;
        gc.strokeRect(x + margin, y + margin, size - 2 * margin, size - 2 * margin);
        gc.strokeLine(x + margin, y + margin + header, x + size - margin, y + margin + header);
        // binding rings
        double ringRadius = size * 0.05;
        double ringY = y + margin;
        gc.strokeOval(x + size * 0.35 - ringRadius, ringY - ringRadius, ringRadius * 2, ringRadius * 2);
        gc.strokeOval(x + size * 0.65 - ringRadius, ringY - ringRadius, ringRadius * 2, ringRadius * 2);
    }

    /**
     * Renders a clock icon (used for time).
     */
    private static void renderClock(GraphicsContext gc, double x, double y, double size) {
        double radius = size * 0.35;
        double cx = x + size / 2;
        double cy = y + size / 2;
        gc.strokeOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
        gc.strokeLine(cx, cy, cx, cy - radius * 0.6);
        gc.strokeLine(cx, cy, cx + radius * 0.4, cy);
    }

    /**
     * Renders an hourglass icon (used for duration).
     */
    private static void renderHourglass(GraphicsContext gc, double x, double y, double size) {
        double top = y + size * 0.2;
        double bottom = y + size - size * 0.2;
        double left = x + size * 0.25;
        double right = x + size - size * 0.25;
        double midY = y + size / 2;
        gc.strokePolygon(
                new double[]{left, right, left, right},
                new double[]{top, top, midY, midY},
                4);
        gc.strokePolygon(
                new double[]{left, right, left, right},
                new double[]{bottom, bottom, midY, midY},
                4);
    }

    /**
     * Renders a toggle icon (used for boolean).
     */
    private static void renderToggle(GraphicsContext gc, double x, double y, double size) {
        double radius = size * 0.25;
        double trackHeight = radius * 2;
        double trackWidth = size - radius;
        double trackX = x + size * 0.1;
        double trackY = y + (size - trackHeight) / 2;
        gc.strokeRoundRect(trackX, trackY, trackWidth, trackHeight, trackHeight, trackHeight);
        gc.fillOval(trackX + trackWidth - radius * 1.8, trackY + (trackHeight - radius * 2) / 2, radius * 2, radius * 2);
    }

    /**
     * Renders a shield icon (used for ID types).
     */
    private static void renderShield(GraphicsContext gc, double x, double y, double size) {
        double top = y + size * 0.15;
        double width = size * 0.6;
        double height = size * 0.7;
        double left = x + (size - width) / 2;
        gc.strokePolygon(
                new double[]{left, left + width, left + width / 2},
                new double[]{top, top, top + height},
                3);
    }
}
