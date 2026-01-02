package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Renders individual XSD nodes as rectangular boxes with expand/collapse controls.
 * Provides XMLSpy-style visual representation of schema elements.
 *
 * @since 2.0
 */
public class XsdNodeRenderer {

    private static final double MIN_NODE_WIDTH = 200;  // Minimum width
    private static final double MAX_NODE_WIDTH = 800;  // Increased max width for larger content
    private static final double HEADER_HEIGHT = 36.0;  // Fixed header height
    private static final double MIN_BODY_HEIGHT = 20.0;  // Minimum body height (no content)
    private static final double PROPERTY_LINE_HEIGHT = 20.0;  // Height per property line
    private static final double NODE_HEIGHT = HEADER_HEIGHT + MIN_BODY_HEIGHT;  // 56px minimum
    private static final double COMPOSITOR_SIZE = 28;  // Small square for compositors
    private static final double EXPAND_BUTTON_SIZE = 16;
    private static final double HORIZONTAL_SPACING = 40;
    private static final double VERTICAL_SPACING = 20;
    private static final double CORNER_RADIUS = 6;  // Updated to 6px for modern look
    private static final double PADDING = 30; // Increased padding for better text spacing

    private final XsdNodeStyler styler;
    private final Font nodeFont;
    private final Font detailFont;
    private final javafx.scene.text.Text textMeasurer;

    public XsdNodeRenderer() {
        this.styler = new XsdNodeStyler();
        // XMLSpy uses Segoe UI font family
        this.nodeFont = Font.font("Segoe UI", 11);
        this.detailFont = Font.font("Segoe UI", 10);
        this.textMeasurer = new javafx.scene.text.Text();
    }

    /**
     * Calculates the required width for a node based on all header elements:
     * - Icon box (28px) + padding (8px)
     * - Node name text
     * - Cardinality badge (60px)
     * - Expand button (16px) + spacing (8px) if has children
     * - Plus detail text from body
     */
    public double calculateNodeWidth(VisualNode node) {
        if (isCompositorNode(node)) {
            return COMPOSITOR_SIZE;
        }

        final double ICON_BOX_SIZE = 28.0;      // 24px box + 4px padding on each side
        final double ICON_BOX_SPACING = 8.0;    // Spacing after icon box
        final double BADGE_WIDTH = 60.0;        // Cardinality badge fixed width
        final double BADGE_SPACING = 8.0;       // Spacing before badge
        final double EXPAND_BTN_WIDTH = 16.0;   // Expand button width
        final double EXPAND_BTN_SPACING = 8.0;  // Spacing for expand button

        // Calculate header width components
        double headerWidth = ICON_BOX_SIZE + ICON_BOX_SPACING;  // Icon and spacing

        // Measure label text with proper font
        textMeasurer.setFont(nodeFont);
        textMeasurer.setText(node.getLabel() != null ? node.getLabel() : "");
        double labelWidth = textMeasurer.getBoundsInLocal().getWidth();
        headerWidth += labelWidth;

        // Add cardinality badge width
        String cardinality = node.getCardinalityBadge();
        if (cardinality != null && !cardinality.isEmpty()) {
            headerWidth += BADGE_SPACING + estimateTextWidth(cardinality, Font.font("Consolas", FontWeight.BOLD, 10)) + 12;  // +12 for badge padding
        }

        // Add expand button width if has children
        if (node.hasChildren()) {
            headerWidth += EXPAND_BTN_SPACING + EXPAND_BTN_WIDTH;
        }

        // Add left and right padding
        headerWidth += PADDING + PADDING;

        // Also measure detail text width for body
        double detailWidth = 0;
        if (node.getDetail() != null && !node.getDetail().isEmpty()) {
            textMeasurer.setFont(detailFont);
            textMeasurer.setText(node.getDetail());
            detailWidth = textMeasurer.getBoundsInLocal().getWidth();
            // Add "Type: " prefix width and spacing
            detailWidth += 50;  // Estimated width of "Type: " label + spacing
        }

        // Take maximum of header and detail, use header as minimum
        double requiredWidth = Math.max(headerWidth, detailWidth);

        // Use minimum width but allow expansion for longer text (no hard upper limit)
        return Math.max(MIN_NODE_WIDTH, requiredWidth);
    }

    /**
     * Calculates the required body height based on the content lines to display.
     * For elements/attributes with type info, reserves 2 property lines plus extra padding.
     * For compositors, only 1 line (child count).
     */
    public double calculateBodyHeight(VisualNode node) {
        if (node.getDetail() == null || node.getDetail().isEmpty()) {
            return MIN_BODY_HEIGHT;
        }

        // Elements and attributes need 2 property lines (Type and Occurs/Use) with extra padding
        if (node.getType() == NodeWrapperType.ELEMENT || node.getType() == NodeWrapperType.ATTRIBUTE) {
            return PROPERTY_LINE_HEIGHT * 2 + 10;  // 50px for 2 lines with padding
        }

        // Compositors and other nodes need only 1 line
        return PROPERTY_LINE_HEIGHT;  // 20px for 1 line
    }

    /**
     * Calculates the complete node height (header + body).
     * This is the total height needed to render the node with all its content.
     */
    public double calculateNodeHeight(VisualNode node) {
        double bodyHeight = calculateBodyHeight(node);
        return HEADER_HEIGHT + bodyHeight;
    }

    private boolean isCompositorNode(VisualNode node) {
        return node.getType() == NodeWrapperType.SEQUENCE ||
                node.getType() == NodeWrapperType.CHOICE ||
                node.getType() == NodeWrapperType.ALL;
    }

    /**
     * Renders a visual node for an XSD element.
     *
     * @param gc   Graphics context
     * @param node The visual node to render
     * @param x    X position
     * @param y    Y position
     */
    public void renderNode(GraphicsContext gc, VisualNode node, double x, double y) {
        // Check if this is a compositor node (sequence, choice, all)
        boolean isCompositor = node.getType() == NodeWrapperType.SEQUENCE ||
                node.getType() == NodeWrapperType.CHOICE ||
                node.getType() == NodeWrapperType.ALL;

        if (isCompositor) {
            renderCompositorSymbol(gc, node, x, y);
        } else {
            renderRegularNode(gc, node, x, y);
        }
    }

    /**
     * Renders the header section of a node (36px height with icon, name, and cardinality badge).
     *
     * @param gc        Graphics context
     * @param node      The visual node
     * @param x         X position
     * @param y         Y position
     * @param width     Node width
     */
    private void renderNodeHeader(GraphicsContext gc, VisualNode node, double x, double y, double width) {
        final double ICON_BOX_SIZE = 24.0;
        final double ICON_BOX_PADDING = 4.0;
        final double ICON_SIZE = 16.0;
        final double PADDING = 8.0;
        final double HEADER_CORNER_RADIUS = 4.0;

        // Validate dimensions
        if (width <= 0 || width > 2000) {
            return;  // Skip invalid width
        }

        // Get colors and properties for this node
        Color headerColor = node.getHeaderColor();
        Color borderColor = node.getBorderColor();
        Color iconBgColor = node.getIconBackgroundColor();
        String iconLiteral = node.getIconLiteral();
        String cardinityBadge = node.getCardinalityBadge();

        // Apply fallback colors if null
        if (headerColor == null) {
            headerColor = Color.LIGHTGRAY;
        }
        if (borderColor == null) {
            borderColor = Color.GRAY;
        }
        if (iconBgColor == null) {
            iconBgColor = headerColor;
        }

        // Create gradient background for header
        LinearGradient headerGradient = createHeaderGradient(headerColor, y, HEADER_HEIGHT);

        // Draw header background
        gc.setFill(headerGradient);
        gc.setStroke(borderColor);

        // Determine line width: thicker (3.0) for unbounded/multiple, normal (1.5) for single
        double lineWidth = (node.maxOccurs > 1 || node.maxOccurs == -1) ? 3.0 : 1.5;
        gc.setLineWidth(lineWidth);

        // Set line dashes for optional fields: dashed if minOccurs == 0, solid otherwise
        if (node.minOccurs == 0) {
            gc.setLineDashes(6, 4);  // Dashed pattern
        }

        gc.fillRoundRect(x, y, width, HEADER_HEIGHT, HEADER_CORNER_RADIUS, HEADER_CORNER_RADIUS);

        // Don't stroke the bottom (it connects to body)
        gc.setLineWidth(lineWidth);
        gc.strokeLine(x, y, x + width, y);  // Top
        gc.strokeLine(x, y, x, y + HEADER_HEIGHT);  // Left
        gc.strokeLine(x + width, y, x + width, y + HEADER_HEIGHT);  // Right

        // Reset line dashes
        gc.setLineDashes();

        // Draw icon box (left side)
        double iconBoxX = x + PADDING;
        double iconBoxY = y + (HEADER_HEIGHT - ICON_BOX_SIZE) / 2;

        gc.setFill(iconBgColor);
        gc.setStroke(borderColor);
        gc.setLineWidth(1.0);
        gc.fillRoundRect(iconBoxX, iconBoxY, ICON_BOX_SIZE, ICON_BOX_SIZE, 4, 4);

        // Render the icon inside the box
        SvgIconRenderer.renderIcon(gc, iconLiteral,
                iconBoxX + ICON_BOX_PADDING,
                iconBoxY + ICON_BOX_PADDING,
                ICON_SIZE,
                Color.WHITE);

        // Draw node name (center area)
        double nameX = iconBoxX + ICON_BOX_SIZE + PADDING;
        double nameY = y + HEADER_HEIGHT / 2;  // Vertically center in header

        // Darker version of header color for text
        Color textColor = headerColor.interpolate(Color.BLACK, 0.3);
        gc.setFill(textColor);
        gc.setFont(Font.font("Roboto", FontWeight.BOLD, 13));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);

        // Display the full node name without truncation - let node width accommodate it
        gc.fillText(node.getLabel(), nameX, nameY);

        // Draw cardinality badge (right side)
        if (cardinityBadge != null && !cardinityBadge.isEmpty()) {
            double badgeHeight = 20.0;
            double badgePadding = 6.0;
            double badgeWidth = estimateTextWidth(cardinityBadge, Font.font("Consolas", FontWeight.BOLD, 10)) + badgePadding * 2;
            double badgeX = x + width - badgeWidth - PADDING;
            double badgeY = y + (HEADER_HEIGHT - badgeHeight) / 2;

            // Draw badge background (white)
            gc.setFill(Color.WHITE);
            gc.setStroke(borderColor);
            gc.setLineWidth(1.0);
            gc.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 4, 4);
            gc.strokeRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 4, 4);

            // Draw badge text (cardinality)
            gc.setFill(borderColor);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText(cardinityBadge, badgeX + badgeWidth / 2, badgeY + badgeHeight / 2);
        }
    }

    /**
     * Estimates text width for layout purposes.
     */
    private double estimateTextWidth(String text, Font font) {
        textMeasurer.setFont(font);
        textMeasurer.setText(text);
        return textMeasurer.getBoundsInLocal().getWidth();
    }

    /**
     * Renders connection point circles on the node edges.
     *
     * @param gc     Graphics context
     * @param node   The visual node
     * @param x      X position of node
     * @param y      Y position of node
     * @param width  Node width
     * @param height Node height
     */
    private void renderConnectionPoints(GraphicsContext gc, VisualNode node, double x, double y, double width, double height) {
        final double CIRCLE_DIAMETER = 12.0;
        final double CIRCLE_RADIUS = CIRCLE_DIAMETER / 2.0;

        // Validate dimensions
        if (width <= 0 || height <= 0) {
            return;
        }

        Color borderColor = node.getBorderColor();
        if (borderColor == null) {
            borderColor = Color.GRAY;
        }

        // Vertical center of the node
        double centerY = y + height / 2.0;

        // Left connection point (if node has parent)
        if (node.getParent() != null) {
            double leftCircleX = x - CIRCLE_RADIUS;
            double leftCircleY = centerY - CIRCLE_RADIUS;

            try {
                gc.setFill(Color.WHITE);
                gc.setStroke(borderColor);
                gc.setLineWidth(2.0);
                gc.fillOval(leftCircleX, leftCircleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
                gc.strokeOval(leftCircleX, leftCircleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
            } catch (Exception e) {
                // Skip on error
            }
        }

        // Right connection point (if node has children or can be expanded)
        if (node.hasChildren()) {
            double rightCircleX = x + width - CIRCLE_RADIUS;
            double rightCircleY = centerY - CIRCLE_RADIUS;

            try {
                gc.setFill(Color.WHITE);
                gc.setStroke(borderColor);
                gc.setLineWidth(2.0);
                gc.fillOval(rightCircleX, rightCircleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
                gc.strokeOval(rightCircleX, rightCircleY, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
            } catch (Exception e) {
                // Skip on error
            }
        }
    }

    /**
     * Renders the body section of a node (white background with property information).
     *
     * @param gc         Graphics context
     * @param node       The visual node
     * @param x          X position
     * @param y          Y position (should be below the header)
     * @param width      Node width
     * @param bodyHeight Body height
     */
    private void renderNodeBody(GraphicsContext gc, VisualNode node, double x, double y, double width, double bodyHeight) {
        final double PADDING = 8.0;
        final double BODY_CORNER_RADIUS = 4.0;
        final double PROPERTY_LINE_HEIGHT = 20.0;

        // Validate dimensions
        if (width <= 0 || bodyHeight <= 0) {
            return;  // Skip if dimensions are invalid
        }

        // Get border color for consistency
        Color borderColor = node.getBorderColor();
        if (borderColor == null) {
            borderColor = Color.GRAY;  // Fallback color
        }

        // Draw body background (white)
        gc.setFill(Color.WHITE);
        gc.setStroke(borderColor);

        // Determine line width: thicker (3.0) for unbounded/multiple, normal (1.5) for single
        double lineWidth = (node.maxOccurs > 1 || node.maxOccurs == -1) ? 3.0 : 1.5;
        gc.setLineWidth(lineWidth);

        // Set line dashes for optional fields: dashed if minOccurs == 0, solid otherwise
        if (node.minOccurs == 0) {
            gc.setLineDashes(6, 4);  // Dashed pattern
        }

        gc.fillRoundRect(x, y, width, bodyHeight, BODY_CORNER_RADIUS, BODY_CORNER_RADIUS);

        // Only stroke sides and bottom (top connects to header)
        gc.setLineWidth(lineWidth);
        gc.strokeLine(x, y + bodyHeight, x + width, y + bodyHeight);  // Bottom
        gc.strokeLine(x, y, x, y + bodyHeight);  // Left
        gc.strokeLine(x + width, y, x + width, y + bodyHeight);  // Right

        // Reset line dashes
        gc.setLineDashes();

        // Draw detail/type information in the body
        if (node.getDetail() != null && !node.getDetail().isEmpty()) {
            double propertyY = y + PADDING;

            // Label: "Type"
            gc.setFill(Color.rgb(108, 117, 125));  // Gray for labels
            gc.setFont(Font.font("Segoe UI", 11));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.TOP);
            gc.fillText("Type:", x + PADDING, propertyY);

            // Value: actual type (display fully without truncation)
            gc.setFill(Color.rgb(33, 37, 41));  // Dark text for values
            gc.setFont(Font.font("Consolas", 11));
            // Display detail text without truncation - let node width accommodate it
            gc.fillText(node.getDetail(), x + PADDING + 50, propertyY);

            // Additional property line: Occurs (for elements)
            if (node.getType() == NodeWrapperType.ELEMENT || node.getType() == NodeWrapperType.ATTRIBUTE) {
                double secondLineY = propertyY + PROPERTY_LINE_HEIGHT;

                String occursLabel = node.getType() == NodeWrapperType.ATTRIBUTE ? "Use:" : "Occurs:";
                String occursValue = node.getMinOccurs() > 0 ? "required" : "optional";

                // Label
                gc.setFill(Color.rgb(108, 117, 125));
                gc.setFont(Font.font("Segoe UI", 11));
                gc.fillText(occursLabel, x + PADDING, secondLineY);

                // Value
                gc.setFill(Color.rgb(33, 37, 41));
                gc.setFont(Font.font("Consolas", 11));
                gc.fillText(occursValue, x + PADDING + 50, secondLineY);
            }
        }
    }

    /**
     * Renders a regular node (element, attribute, etc.)
     */
    private void renderRegularNode(GraphicsContext gc, VisualNode node, double x, double y) {
        // Use the width and height already set during layout
        double width = node.getWidth();
        double height = node.getHeight();

        // Ensure valid dimensions (prevent negative or zero values)
        if (width <= 0 || height <= 0) {
            return;  // Skip rendering invalid nodes
        }

        // Calculate body height dynamically based on content
        double bodyHeight = calculateBodyHeight(node);
        double actualHeight = HEADER_HEIGHT + bodyHeight;
        final double BODY_HEIGHT = bodyHeight;

        // Render the header section (colored background with icon, name, cardinality)
        if (node.getHeaderColor() != null && node.getBorderColor() != null) {
            try {
                renderNodeHeader(gc, node, x, y, width);
            } catch (Exception e) {
                // Skip header rendering on error and continue with visual states
            }
        }

        // Render the body section (white background with type/detail info)
        if (BODY_HEIGHT > 0 && node.getBorderColor() != null) {
            try {
                renderNodeBody(gc, node, x, y + HEADER_HEIGHT, width, BODY_HEIGHT);
            } catch (Exception e) {
                // Skip body rendering on error and continue
            }
        }

        // Render connection point circles on node edges
        if (node.getBorderColor() != null) {
            try {
                renderConnectionPoints(gc, node, x, y, width, actualHeight);
            } catch (Exception e) {
                // Skip connection points on error
            }
        }

        // Apply visual state indicators over both header and body
        Color borderColor = node.getBorderColor();

        // Draw selection highlight (outer border)
        if (node.isSelected()) {
            gc.setStroke(Color.rgb(59, 130, 246));  // Blue highlight
            gc.setLineWidth(3);
            gc.strokeRoundRect(x - 2, y - 2, width + 4, height + 4, CORNER_RADIUS + 2, CORNER_RADIUS + 2);
        }

        // Draw focus indicator (dashed outline)
        if (node.isFocused()) {
            gc.setStroke(Color.rgb(139, 92, 246));  // Purple for focus
            gc.setLineWidth(2);
            gc.setLineDashes(4, 4);
            gc.strokeRoundRect(x - 3, y - 3, width + 6, height + 6, CORNER_RADIUS + 3, CORNER_RADIUS + 3);
            gc.setLineDashes(null);  // Reset to solid
        }

        // Draw drop target indicator (green highlight)
        if (node.isDropTarget()) {
            gc.setStroke(Color.rgb(34, 197, 94));  // Green for drop target
            gc.setLineWidth(3);
            gc.strokeRoundRect(x - 4, y - 4, width + 8, height + 8, CORNER_RADIUS + 4, CORNER_RADIUS + 4);
        }

        // Draw dragging indicator (semi-transparent overlay)
        if (node.isDragging()) {
            gc.setGlobalAlpha(0.5);
            gc.setFill(Color.rgb(147, 197, 253));  // Light blue overlay
            gc.fillRoundRect(x, y, width, height, CORNER_RADIUS, CORNER_RADIUS);
            gc.setGlobalAlpha(1.0);  // Reset
        }

        // Draw edit mode indicator (small badge in top-right corner)
        if (node.isInEditMode()) {
            double badgeSize = 8;
            double badgeX = x + width - badgeSize - 4;
            double badgeY = y + 4;

            // Draw badge circle
            gc.setFill(Color.rgb(234, 179, 8));  // Amber color
            gc.fillOval(badgeX, badgeY, badgeSize, badgeSize);

            // Draw pencil symbol
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            double pencilX = badgeX + badgeSize / 2;
            double pencilY = badgeY + badgeSize / 2;
            gc.strokeLine(pencilX - 2, pencilY + 2, pencilX + 2, pencilY - 2);
        }

        // Draw include file indicator (small colored badge in bottom-left corner)
        if (node.isFromInclude()) {
            double badgeSize = 10;
            double badgeX = x + 4;
            double badgeY = y + height - badgeSize - 4;

            // Draw badge background (cyan/teal color for include indicator)
            gc.setFill(Color.rgb(14, 165, 233));  // sky-500 color
            gc.fillRoundRect(badgeX, badgeY, badgeSize, badgeSize, 3, 3);

            // Draw "I" symbol for include
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 7));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText("I", badgeX + badgeSize / 2, badgeY + badgeSize / 2);

            // Reset text alignment
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.TOP);
        }

        // Draw expand/collapse button if node has children
        if (node.hasChildren()) {
            drawExpandButton(gc, node, x, y, width, height);
        }
    }

    /**
     * Renders a small compositor symbol (sequence, choice, all).
     */
    private void renderCompositorSymbol(GraphicsContext gc, VisualNode node, double x, double y) {
        // Use the width and height already set during layout (should be COMPOSITOR_SIZE)
        double size = node.getWidth();

        Color borderColor = getXMLSpyBorderColor(node.getType());

        // Apply same line width logic as regular nodes
        double lineWidth;
        if (node.isSelected()) {
            lineWidth = 4.0;  // Thicker border for selected nodes
        } else if (node.getMaxOccurs() > 1 || node.getMaxOccurs() == Integer.MAX_VALUE || node.getMaxOccurs() == -1) {
            lineWidth = 3.0;  // Thicker border for multiple occurrences
        } else {
            lineWidth = 1.5;  // Normal border for single occurrence
        }

        // Draw small filled circle/diamond based on type
        gc.setStroke(borderColor);
        gc.setLineWidth(lineWidth);

        switch (node.getType()) {
            case SEQUENCE -> {
                // Draw rounded square for sequence
                gc.setFill(Color.rgb(207, 250, 254, 0.8));
                gc.fillRoundRect(x, y, size, size, 4, 4);
                gc.strokeRoundRect(x, y, size, size, 4, 4);

                // Draw "SEQ" or lines symbol with cardinality
                gc.setFill(borderColor);
                gc.setFont(Font.font("Segoe UI", 7));  // Smaller font for cardinality
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                
                String cardinalityText = getCardinalityString(node);
                if (!cardinalityText.isEmpty()) {
                    gc.fillText(cardinalityText, x + size / 2, y + size / 2);
                } else {
                    gc.fillText("=", x + size / 2, y + size / 2);
                }
            }
            case CHOICE -> {
                // Draw diamond for choice
                gc.setFill(Color.rgb(254, 243, 199, 0.8));
                double centerX = x + size / 2;
                double centerY = y + size / 2;
                double halfSize = size / 2;
                gc.fillPolygon(
                        new double[]{centerX, centerX + halfSize, centerX, centerX - halfSize},
                        new double[]{centerY - halfSize, centerY, centerY + halfSize, centerY},
                        4
                );
                gc.strokePolygon(
                        new double[]{centerX, centerX + halfSize, centerX, centerX - halfSize},
                        new double[]{centerY - halfSize, centerY, centerY + halfSize, centerY},
                        4
                );

                // Draw "?" symbol with cardinality
                gc.setFill(borderColor);
                gc.setFont(Font.font("Segoe UI", 7));  // Smaller font for cardinality
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                
                String cardinalityText = getCardinalityString(node);
                if (!cardinalityText.isEmpty()) {
                    gc.fillText(cardinalityText, centerX, centerY);
                } else {
                    gc.fillText("?", centerX, centerY);
                }
            }
            case ALL -> {
                // Draw circle for all
                gc.setFill(Color.rgb(237, 233, 254, 0.8));
                gc.fillOval(x, y, size, size);
                gc.strokeOval(x, y, size, size);

                // Draw "*" symbol with cardinality
                gc.setFill(borderColor);
                gc.setFont(Font.font("Segoe UI", 7));  // Smaller font for cardinality
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);

                String cardinalityText = getCardinalityString(node);
                if (!cardinalityText.isEmpty()) {
                    gc.fillText(cardinalityText, x + size / 2, y + size / 2);
                } else {
                    gc.fillText("*", x + size / 2, y + size / 2);
                }
            }
        }

        // Draw drop target indicator (green highlight)
        if (node.isDropTarget()) {
            gc.setStroke(Color.rgb(34, 197, 94));  // Green for drop target
            gc.setLineWidth(3);
            gc.strokeOval(x - 4, y - 4, size + 8, size + 8);
        }

        // Draw dragging indicator (semi-transparent overlay)
        if (node.isDragging()) {
            gc.setGlobalAlpha(0.5);
            gc.setFill(Color.rgb(147, 197, 253));  // Light blue overlay
            gc.fillOval(x, y, size, size);
            gc.setGlobalAlpha(1.0);  // Reset
        }

        // Draw cardinality indicator next to compositor symbol if it's not default (1..1)\n        String cardinality = getCardinalityString(node);\n        if (!cardinality.isEmpty() && cardinality.length() > 3) { // Only show if it doesn't fit in symbol\n            gc.setFont(Font.font(\"Segoe UI\", 8));\n            gc.setFill(Color.rgb(67, 56, 202));  // Indigo color for cardinality\n            gc.setTextAlign(TextAlignment.LEFT);\n            gc.setTextBaseline(VPos.CENTER);\n            \n            // Position to the right of the compositor symbol\n            double cardinalityX = x + size + 4;\n            double cardinalityY = y + size / 2;\n            gc.fillText(cardinality, cardinalityX, cardinalityY);\n        }\n        \n        // Apply selection highlight for compositor nodes\n        if (node.isSelected()) {\n            gc.setStroke(Color.rgb(59, 130, 246));  // Blue highlight\n            gc.setLineWidth(3);\n            switch (node.getType()) {\n                case SEQUENCE -> {\n                    gc.strokeRoundRect(x - 2, y - 2, size + 4, size + 4, 6, 6);\n                }\n                case CHOICE -> {\n                    double centerX = x + size / 2;\n                    double centerY = y + size / 2;\n                    double halfSize = (size + 4) / 2;\n                    gc.strokePolygon(\n                            new double[]{centerX, centerX + halfSize, centerX, centerX - halfSize},\n                            new double[]{centerY - halfSize, centerY, centerY + halfSize, centerY},\n                            4\n                    );\n                }\n                case ALL -> {\n                    gc.strokeOval(x - 2, y - 2, size + 4, size + 4);\n                }\n            }\n        }\n        \n        // Apply focus indicator for compositor nodes\n        if (node.isFocused()) {\n            gc.setStroke(Color.rgb(139, 92, 246));  // Purple for focus\n            gc.setLineWidth(2);\n            gc.setLineDashes(4, 4);\n            switch (node.getType()) {\n                case SEQUENCE -> {\n                    gc.strokeRoundRect(x - 3, y - 3, size + 6, size + 6, 7, 7);\n                }\n                case CHOICE -> {\n                    double centerX = x + size / 2;\n                    double centerY = y + size / 2;\n                    double halfSize = (size + 6) / 2;\n                    gc.strokePolygon(\n                            new double[]{centerX, centerX + halfSize, centerX, centerX - halfSize},\n                            new double[]{centerY - halfSize, centerY, centerY + halfSize, centerY},\n                            4\n                    );\n                }\n                case ALL -> {\n                    gc.strokeOval(x - 3, y - 3, size + 6, size + 6);\n                }\n            }\n            gc.setLineDashes(null);  // Reset to solid\n        }

        // No expand button for compositor symbols - they auto-expand
    }

    /**
     * Draws the expand/collapse button (+/-).
     */
    private void drawExpandButton(GraphicsContext gc, VisualNode node, double x, double y, double nodeWidth, double nodeHeight) {
        double btnX = x + nodeWidth - EXPAND_BUTTON_SIZE - 8;
        double btnY = y + (nodeHeight - EXPAND_BUTTON_SIZE) / 2;

        node.setExpandButtonBounds(btnX, btnY, EXPAND_BUTTON_SIZE, EXPAND_BUTTON_SIZE);

        // Draw button background
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.DARKGRAY);
        gc.setLineWidth(1);
        gc.fillRect(btnX, btnY, EXPAND_BUTTON_SIZE, EXPAND_BUTTON_SIZE);
        gc.strokeRect(btnX, btnY, EXPAND_BUTTON_SIZE, EXPAND_BUTTON_SIZE);

        // Draw +/- symbol
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);

        double centerX = btnX + EXPAND_BUTTON_SIZE / 2;
        double centerY = btnY + EXPAND_BUTTON_SIZE / 2;

        // Horizontal line (always present)
        gc.strokeLine(centerX - 5, centerY, centerX + 5, centerY);

        // Vertical line (only if collapsed)
        if (!node.isExpanded()) {
            gc.strokeLine(centerX, centerY - 5, centerX, centerY + 5);
        }
    }

    /**
     * Draws a connection line between parent and child nodes.
     * Creates an L-shaped connector from the right side of parent to the left side of child.
     */
    public void renderConnection(GraphicsContext gc, VisualNode parent, VisualNode child) {
        gc.setStroke(Color.GRAY);
        gc.setLineWidth(1);

        // Start at right side of parent node (vertical center)
        double startX = parent.getX() + parent.getWidth();
        double startY = parent.getY() + parent.getHeight() / 2;

        // End at left side of child node (vertical center)
        double endX = child.getX();
        double endY = child.getY() + child.getHeight() / 2;

        // Draw L-shaped connection: horizontal → vertical → horizontal
        double midX = startX + (endX - startX) / 2;

        // Three line segments forming an L-shape
        gc.strokeLine(startX, startY, midX, startY);    // Horizontal from parent to midpoint
        gc.strokeLine(midX, startY, midX, endY);        // Vertical from parent level to child level
        gc.strokeLine(midX, endY, endX, endY);          // Horizontal from midpoint to child
    }

    /**
     * Gets the fill gradient for a node based on its type (XMLSpy style).
     * XMLSpy uses vertical gradients from white to a light color.
     */
    private LinearGradient getNodeFillGradient(NodeWrapperType type, double x, double y, double width, double height) {
        Stop[] stops = switch (type) {
            case ELEMENT -> new Stop[]{
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.rgb(240, 248, 255))  // #f0f8ff - XMLSpy element background
            };
            case ATTRIBUTE -> new Stop[]{
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.rgb(255, 254, 247))  // #fffef7 - XMLSpy attribute background
            };
            case COMPLEX_TYPE, SCHEMA -> new Stop[]{
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.rgb(248, 249, 250))  // #f8f9fa - XMLSpy structural background
            };
            case SIMPLE_TYPE -> new Stop[]{
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.rgb(220, 252, 231))  // Light green
            };
            case SEQUENCE -> new Stop[]{
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.rgb(207, 250, 254))  // Light cyan
            };
            case CHOICE -> new Stop[]{
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.rgb(254, 243, 199))  // Light amber
            };
            case ALL -> new Stop[]{
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.rgb(237, 233, 254))  // Light violet
            };
            default -> new Stop[]{
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.WHITE)
            };
        };

        // Create vertical gradient (top to bottom)
        return new LinearGradient(0, y, 0, y + height, false, CycleMethod.NO_CYCLE, stops);
    }

    /**
     * Gets the border color for a node based on its type (XMLSpy style).
     */
    private Color getXMLSpyBorderColor(NodeWrapperType type) {
        return switch (type) {
            case ELEMENT -> Color.rgb(74, 144, 226);          // #4a90e2 - XMLSpy element border
            case ATTRIBUTE -> Color.rgb(212, 161, 71);        // #d4a147 - XMLSpy attribute border
            case COMPLEX_TYPE, SCHEMA -> Color.rgb(108, 117, 125);  // #6c757d - XMLSpy structural border
            case SIMPLE_TYPE -> Color.rgb(5, 150, 105);       // Green
            case SEQUENCE -> Color.rgb(8, 145, 178);          // Cyan
            case CHOICE -> Color.rgb(245, 158, 11);           // Amber
            case ALL -> Color.rgb(139, 92, 246);              // Violet
            default -> Color.LIGHTGRAY;
        };
    }

    /**
     * Brightens a gradient for hover/selection effects.
     */
    private LinearGradient brightenGradient(LinearGradient gradient) {
        Stop[] oldStops = gradient.getStops().toArray(new Stop[0]);
        Stop[] newStops = new Stop[oldStops.length];

        for (int i = 0; i < oldStops.length; i++) {
            Color oldColor = oldStops[i].getColor();
            Color newColor = oldColor.brighter();
            newStops[i] = new Stop(oldStops[i].getOffset(), newColor);
        }

        return new LinearGradient(
                gradient.getStartX(), gradient.getStartY(),
                gradient.getEndX(), gradient.getEndY(),
                gradient.isProportional(), gradient.getCycleMethod(), newStops
        );
    }

    /**
     * Gets the header background color for a node type.
     * Returns semantic colors from STYLE_GUIDE.jsonc.
     */
    private Color getHeaderColor(NodeWrapperType type) {
        return switch (type) {
            case SCHEMA -> Color.rgb(111, 66, 193);            // #6f42c1 - Purple
            case ELEMENT -> Color.rgb(0, 123, 255);             // #007bff - Blue
            case ATTRIBUTE -> Color.rgb(32, 201, 151);          // #20c997 - Teal
            case COMPLEX_TYPE -> Color.rgb(108, 117, 125);      // #6c757d - Gray
            case SIMPLE_TYPE -> Color.rgb(40, 167, 69);         // #28a745 - Green
            case SEQUENCE -> Color.rgb(23, 162, 184);           // #17a2b8 - Cyan
            case CHOICE -> Color.rgb(253, 126, 20);             // #fd7e14 - Orange
            case ALL -> Color.rgb(111, 66, 193);                // #6f42c1 - Purple
            default -> Color.LIGHTGRAY;
        };
    }

    /**
     * Creates a header gradient from a base color.
     * Returns a gradient from lighter tint to darker tint.
     */
    private LinearGradient createHeaderGradient(Color baseColor, double y, double height) {
        // Create a subtle gradient that's lighter and more modern
        Color lighter = baseColor.interpolate(Color.WHITE, 0.55);  // Much lighter tint (55% towards white)
        Color darker = baseColor.interpolate(Color.WHITE, 0.35);   // Slightly darker but still light (35% towards white)

        return new LinearGradient(0, y, 0, y + height, false, CycleMethod.NO_CYCLE,
                new Stop(0, lighter),
                new Stop(1, darker)
        );
    }

    /**
     * Gets the icon literal for rendering the node icon.
     * Returns Bootstrap Icons names (e.g., "bi-diagram-3", "bi-file-earmark-code").
     */
    private String getIconLiteral(NodeWrapperType type) {
        return switch (type) {
            case SCHEMA -> "bi-diagram-3";
            case ELEMENT -> "bi-file-earmark-code";
            case ATTRIBUTE -> "bi-at";
            case COMPLEX_TYPE -> "bi-diagram-2";
            case SIMPLE_TYPE -> "bi-diagram-3";
            case SEQUENCE -> "bi-list-check";
            case CHOICE -> "bi-signpost-2";
            case ALL -> "bi-asterisk";
            default -> "bi-diagram-2";
        };
    }

    /**
     * Formats cardinality as a badge string.
     * Examples: "ROOT", "1..1", "0..*", "2..5", "required", "optional"
     */
    private String formatCardinalityBadge(NodeWrapperType type, int minOccurs, int maxOccurs) {
        // Special case for SCHEMA - show "ROOT"
        if (type == NodeWrapperType.SCHEMA) {
            return "ROOT";
        }

        // Format minOccurs..maxOccurs
        String maxStr = maxOccurs == Integer.MAX_VALUE || maxOccurs == -1 ? "*" : String.valueOf(maxOccurs);
        return minOccurs + ".." + maxStr;
    }

    /**
     * Truncates text to fit within the specified width using accurate text measurement.
     */
    private String truncateText(String text, double maxWidth, Font font) {
        if (text == null || text.isEmpty()) return "";
        
        // Measure actual text width
        textMeasurer.setFont(font);
        textMeasurer.setText(text);
        double textWidth = textMeasurer.getBoundsInLocal().getWidth();
        
        // If text fits, return as is
        if (textWidth <= maxWidth) {
            return text;
        }
        
        // Text doesn't fit, truncate with ellipsis
        String ellipsis = "...";
        textMeasurer.setText(ellipsis);
        double ellipsisWidth = textMeasurer.getBoundsInLocal().getWidth();
        double availableWidth = maxWidth - ellipsisWidth;
        
        if (availableWidth <= 0) {
            return ellipsis;
        }
        
        // Binary search for the right length
        int left = 0;
        int right = text.length();
        String bestFit = "";
        
        while (left <= right) {
            int mid = (left + right) / 2;
            String candidate = text.substring(0, mid);
            textMeasurer.setText(candidate);
            double candidateWidth = textMeasurer.getBoundsInLocal().getWidth();
            
            if (candidateWidth <= availableWidth) {
                bestFit = candidate;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return bestFit.isEmpty() ? ellipsis : bestFit + ellipsis;
    }

    public double getNodeWidth() {
        return MIN_NODE_WIDTH;  // Return minimum width for backwards compatibility
    }

    public double getNodeHeight() {
        return NODE_HEIGHT;
    }

    public double getHorizontalSpacing() {
        return HORIZONTAL_SPACING;
    }

    public double getVerticalSpacing() {
        return VERTICAL_SPACING;
    }

    public double getCompositorSize() {
        return COMPOSITOR_SIZE;
    }

    /**
     * Generates the cardinality string for display based on minOccurs and maxOccurs.
     * 
     * Examples:
     * - minOccurs=1, maxOccurs=1 -> "" (no display, default)
     * - minOccurs=0, maxOccurs=1 -> "0..1"
     * - minOccurs=1, maxOccurs=unbounded -> "1..*"
     * - minOccurs=0, maxOccurs=unbounded -> "0..*" 
     * - minOccurs=2, maxOccurs=5 -> "2..5"
     */
    private String getCardinalityString(VisualNode node) {
        int minOccurs = node.getMinOccurs();
        int maxOccurs = node.getMaxOccurs();
        
        // Default case (1..1) - don't display
        if (minOccurs == 1 && maxOccurs == 1) {
            return "";
        }
        
        // Build cardinality string
        StringBuilder cardinality = new StringBuilder();
        cardinality.append(minOccurs);
        
        // Handle unbounded (can be Integer.MAX_VALUE or -1)
        if (maxOccurs == Integer.MAX_VALUE || maxOccurs == -1) {
            cardinality.append("..*");
        } else if (maxOccurs != minOccurs) {
            cardinality.append("..").append(maxOccurs);
        }
        // If min equals max (and not 1..1), just show the number
        
        return cardinality.toString();
    }

    /**
     * Represents node type for rendering.
     */
    public enum NodeWrapperType {
        SCHEMA,
        ELEMENT,
        ATTRIBUTE,
        COMPLEX_TYPE,
        SIMPLE_TYPE,
        GROUP,
        ENUMERATION,
        SEQUENCE,      // xs:sequence compositor
        CHOICE,        // xs:choice compositor
        ALL            // xs:all compositor
    }

    /**
     * Visual representation of a node in the graph.
     * Now supports dynamic updates from the model layer.
     */
    public static class VisualNode {
        // Mutable fields - can be updated when model changes
        private String label;
        private String detail;
        private int minOccurs;
        private int maxOccurs;

        // Immutable fields
        private final NodeWrapperType type;
        private final Object modelObject;  // Can be XsdNode (new) or XsdModel objects (old)
        private final VisualNode parent;
        private final java.util.List<VisualNode> children = new java.util.ArrayList<>();

        // Mutable callback - can be updated when tree is rebuilt
        private Runnable onModelChangeCallback;

        private double x, y, width, height;
        private double expandBtnX, expandBtnY, expandBtnW, expandBtnH;
        private boolean expanded = false;

        // Visual feedback states
        private boolean selected = false;
        private boolean hovered = false;
        private boolean focused = false;
        private boolean inEditMode = false;
        private boolean dragging = false;
        private boolean dropTarget = false;

        // New properties for improved visual representation
        private String iconLiteral;             // Bootstrap icon code (e.g., "bi-diagram-3")
        private Color headerColor;              // Header background color
        private Color borderColor;              // Border and connection point color
        private Color iconBackgroundColor;      // Icon box background color
        private String cardinalityBadge;        // Formatted cardinality (e.g., "1..1", "0..*", "ROOT")
        private boolean showConnectionPoints;   // Whether to show connection circles

        // PropertyChangeListener for model updates
        private final java.beans.PropertyChangeListener modelListener;

        public VisualNode(String label, String detail, NodeWrapperType type, Object modelObject, VisualNode parent) {
            this(label, detail, type, modelObject, parent, 1, 1, null);
        }

        public VisualNode(String label, String detail, NodeWrapperType type, Object modelObject, VisualNode parent, int minOccurs, int maxOccurs) {
            this(label, detail, type, modelObject, parent, minOccurs, maxOccurs, null);
        }

        public VisualNode(String label, String detail, NodeWrapperType type, Object modelObject, VisualNode parent, int minOccurs, int maxOccurs, Runnable onModelChangeCallback) {
            this.label = label;
            this.detail = detail;
            this.type = type;
            this.modelObject = modelObject;
            this.parent = parent;
            this.minOccurs = minOccurs;
            this.maxOccurs = maxOccurs;
            this.onModelChangeCallback = onModelChangeCallback;

            // Setup PropertyChangeListener for model updates (only for XsdNode objects)
            this.modelListener = evt -> {
                // Auto-update this VisualNode from model when model changes
                // Use Platform.runLater to ensure UI updates happen on JavaFX Application Thread
                javafx.application.Platform.runLater(() -> {
                    updateFromModel();
                    // Also trigger callback for view refresh if provided
                    if (onModelChangeCallback != null) {
                        onModelChangeCallback.run();
                    }
                });
            };

            // Register listener with model if it's an XsdNode
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                xsdNode.addPropertyChangeListener(modelListener);
                // Initialize detail string from model immediately
                updateFromModel();
            }

            // Initialize visual properties based on node type
            initializeVisualProperties();
        }

        /**
         * Initializes visual properties (colors, icons, cardinality badge) based on node type.
         * This method is called once during construction and can be called to refresh visual properties.
         */
        public void initializeVisualProperties() {
            // Set icon literal based on type and content
            this.iconLiteral = determineIconLiteral();


            // Set header color (semantic colors from style guide)
            this.headerColor = switch (type) {
                case SCHEMA -> Color.rgb(111, 66, 193);            // #6f42c1 - Purple
                case ELEMENT -> Color.rgb(0, 123, 255);             // #007bff - Blue
                case ATTRIBUTE -> Color.rgb(32, 201, 151);          // #20c997 - Teal
                case COMPLEX_TYPE -> Color.rgb(108, 117, 125);      // #6c757d - Gray
                case SIMPLE_TYPE -> Color.rgb(40, 167, 69);         // #28a745 - Green
                case SEQUENCE -> Color.rgb(23, 162, 184);           // #17a2b8 - Cyan
                case CHOICE -> Color.rgb(253, 126, 20);             // #fd7e14 - Orange
                case ALL -> Color.rgb(111, 66, 193);                // #6f42c1 - Purple
                default -> Color.LIGHTGRAY;
            };

            // Icon background color is the same as header color
            this.iconBackgroundColor = this.headerColor;

            // Border color matches header color
            this.borderColor = this.headerColor;

            // Set cardinality badge
            this.cardinalityBadge = formatCardinalityBadge();

            // Connection points are always shown
            this.showConnectionPoints = true;
        }

        /**
         * Formats the cardinality badge text.
         */
        private String formatCardinalityBadge() {
            // Special case for SCHEMA - show "ROOT"
            if (type == NodeWrapperType.SCHEMA) {
                return "ROOT";
            }

            // Format minOccurs..maxOccurs
            String maxStr = maxOccurs == Integer.MAX_VALUE || maxOccurs == -1 ? "*" : String.valueOf(maxOccurs);
            return minOccurs + ".." + maxStr;
        }

        /**
         * Determines the appropriate icon literal based on node type and datatype.
         * Uses different icons for primitive types vs complex types.
         */
        private String determineIconLiteral() {
            switch (type) {
                case SCHEMA:
                    return "bi-diagram-3";  // Database/schema symbol
                case ELEMENT:
                    // Try to detect element datatype from detail string
                    if (detail != null && !detail.isEmpty()) {
                        return getDataTypeIcon(detail);
                    }
                    return "bi-file-text";  // Generic document
                case ATTRIBUTE:
                    return "bi-at";  // @ symbol
                case COMPLEX_TYPE:
                    return "bi-diagram-2";  // Complex structure
                case SIMPLE_TYPE:
                    // Try to detect simple type from detail
                    if (detail != null && !detail.isEmpty()) {
                        return getDataTypeIcon(detail);
                    }
                    return "bi-diagram-3";  // Type symbol
                case SEQUENCE:
                    return "bi-list-check";  // Ordered list
                case CHOICE:
                    return "bi-signpost-2";  // Branch/choice
                case ALL:
                    return "bi-asterisk";  // All/any
                case GROUP:
                    return "bi-diagram-3";  // Grouping
                case ENUMERATION:
                    return "bi-list-check";  // Enumeration/list
                default:
                    return "bi-question-circle";  // Unknown type
            }
        }

        /**
         * Maps specific XSD datatype strings to unique Bootstrap icons.
         * Each common datatype has its own distinct icon for visual differentiation.
         */
        private String getDataTypeIcon(String datatype) {
            if (datatype == null || datatype.isEmpty()) {
                return "bi-diagram-3";  // Default type
            }

            String lower = datatype.toLowerCase();

            // Exact datatype matches (highest priority)
            // String types - different icons for variations
            // IMPORTANT: Check longer/more specific patterns first!
            if (lower.endsWith("string")) {
                return "bi-file-text";  // xs:string
            }
            if (lower.endsWith("token")) {
                return "bi-chat-left-quote";  // xs:token (validated icon)
            }
            if (lower.endsWith("ncname")) {
                return "bi-tag";  // xs:NCName (check before "name"!)
            }
            if (lower.endsWith("name")) {
                return "bi-tag-fill";  // xs:Name (check after "ncname"!)
            }
            if (lower.endsWith(":id") || lower.equals("id")) {
                return "bi-shield-check";  // xs:ID (validated icon)
            }

            // Integer types - distinct icons for each
            if (lower.endsWith("integer")) {
                return "bi-hash";  // xs:integer
            }
            if (lower.endsWith(":int") || lower.equals("int")) {
                return "bi-1-circle-fill";  // xs:int (validated icon)
            }
            if (lower.endsWith("long")) {
                return "bi-2-circle-fill";  // xs:long (validated icon)
            }
            if (lower.endsWith("short")) {
                return "bi-dash-square";  // xs:short
            }
            if (lower.endsWith("byte")) {
                return "bi-subtract";  // xs:byte
            }

            // Decimal/Float types - distinct icons
            if (lower.endsWith("decimal")) {
                return "bi-percent";  // xs:decimal
            }
            if (lower.endsWith("float")) {
                return "bi-hexagon-fill";  // xs:float (validated icon)
            }
            if (lower.endsWith("double")) {
                return "bi-box";  // xs:double
            }

            // Date/Time types - each its own icon
            // IMPORTANT: Check longer/more specific patterns first!
            // e.g., "datetime" must come before "date" because "datetime".endsWith("date") == true
            if (lower.endsWith("datetime")) {
                return "bi-calendar-event";  // xs:dateTime (check before "date"!)
            }
            if (lower.endsWith("duration")) {
                return "bi-hourglass-split";  // xs:duration
            }
            if (lower.endsWith("gmonth")) {
                return "bi-calendar3";  // xs:gMonth
            }
            if (lower.endsWith("gyear")) {
                return "bi-calendar2";  // xs:gYear (validated icon)
            }
            if (lower.endsWith("date")) {
                return "bi-calendar";  // xs:date (check after "datetime"!)
            }
            if (lower.endsWith("time")) {
                return "bi-clock";  // xs:time
            }

            // Boolean
            if (lower.endsWith("boolean")) {
                return "bi-toggle-on";  // xs:boolean
            }

            // Binary/encoded types - distinct icons
            if (lower.endsWith("base64binary")) {
                return "bi-lock";  // xs:base64Binary
            }
            if (lower.endsWith("hexbinary")) {
                return "bi-shield-lock";  // xs:hexBinary
            }

            // URI/URL types
            if (lower.endsWith("uri") || lower.endsWith("anyuri")) {
                return "bi-link-45deg";  // xs:anyURI
            }
            if (lower.endsWith("qname")) {
                return "bi-question-square";  // xs:QName
            }

            // List types
            if (lower.endsWith("list")) {
                return "bi-list";  // List type
            }

            // Union types
            if (lower.endsWith("union")) {
                return "bi-diagram-2";  // Union type
            }

            // Default for unknown types
            return "bi-diagram-3";  // Default generic type
        }

        /**
         * Updates this VisualNode's display properties from the underlying XsdNode model.
         * Called automatically when the model fires PropertyChangeEvents.
         * <p>
         * This method enables in-place updates instead of rebuilding the entire visual tree,
         * making the editor more efficient and preserving UI state (selection, expansion).
         */
        private void updateFromModel() {
            if (!(modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode)) {
                return;  // Not an XsdNode, cannot update
            }

            // Update name/label
            this.label = xsdNode.getName() != null ? xsdNode.getName() : "(unnamed)";

            // Update cardinality
            this.minOccurs = xsdNode.getMinOccurs();
            this.maxOccurs = xsdNode.getMaxOccurs();

            // Rebuild detail string based on node type
            this.detail = buildDetailString(xsdNode);

            // Re-initialize visual properties (icon, colors, etc.) based on updated detail
            initializeVisualProperties();
        }

        /**
         * Builds the detail string for display based on the node type.
         * <p>
         * Detail formats:
         * - XsdElement: "xs:string [N] [A] [F] [Q]" (type + flags: nillable, abstract, fixed, qualified)
         * - XsdAttribute: "xs:int (required) [Q]" (type + use + qualified)
         * - Compositor: "3 items" (child count)
         * - Other nodes: "" (empty)
         * <p>
         * Flags:
         * - [N] = nillable
         * - [A] = abstract
         * - [F] = fixed value set
         * - [Q] = qualified form
         * - [U] = unqualified form
         * - [Doc] = has documentation
         * - [App] = has appinfo
         *
         * @param xsdNode the XSD node
         * @return the detail string
         */
        private String buildDetailString(org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            StringBuilder detail = new StringBuilder();

            // Handle XsdElement - show type and constraints
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
                // Add type (either explicit type or base type from inline simpleType restriction)
                String effectiveType = getEffectiveType(element);
                if (effectiveType != null) {
                    detail.append(effectiveType);
                }

                // Add constraint flags
                if (element.isNillable()) {
                    detail.append(" [N]");
                }
                if (element.isAbstract()) {
                    detail.append(" [A]");
                }
                if (element.getFixed() != null && !element.getFixed().isEmpty()) {
                    detail.append(" [F]");
                }

                // Add form flag
                if ("qualified".equals(element.getForm())) {
                    detail.append(" [Q]");
                } else if ("unqualified".equals(element.getForm())) {
                    detail.append(" [U]");
                }

                return detail.toString().trim();
            }

            // Handle XsdAttribute - show type and form (use indicator is shown in body)
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute attribute) {
                // Add type only - use indicator is already shown in the "Use:" row in the body
                if (attribute.getType() != null) {
                    detail.append(attribute.getType());
                }

                // Add form flag
                if ("qualified".equals(attribute.getForm())) {
                    detail.append(" [Q]");
                } else if ("unqualified".equals(attribute.getForm())) {
                    detail.append(" [U]");
                }

                return detail.toString().trim();
            }

            // Handle Compositor nodes (sequence, choice, all) - show child count
            if (type == NodeWrapperType.SEQUENCE || type == NodeWrapperType.CHOICE || type == NodeWrapperType.ALL) {
                return xsdNode.getChildren().size() + " items";
            }

            // Default: empty detail
            return "";
        }

        /**
         * Gets the effective type of an element.
         * For elements with explicit type reference, returns that type.
         * For elements with inline simpleType and restriction, returns the base type from the restriction.
         *
         * @param element the XSD element
         * @return the effective type, or null if not found
         */
        private String getEffectiveType(org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            // First check for explicit type reference
            String explicitType = element.getType();
            if (explicitType != null && !explicitType.isEmpty()) {
                return explicitType;
            }

            // Check for inline complexType with simpleContent
            for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : element.getChildren()) {
                if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType) {
                    // Look for simpleContent in complexType
                    for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode complexChild : complexType.getChildren()) {
                        if (complexChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleContent simpleContent) {
                            // Look for extension or restriction in simpleContent
                            for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode contentChild : simpleContent.getChildren()) {
                                String baseType = extractBaseType(contentChild);
                                if (baseType != null) {
                                    return baseType;
                                }
                            }
                        }
                    }
                }
            }

            // Check for inline simpleType with restriction
            for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : element.getChildren()) {
                if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType simpleType) {
                    // Look for restriction in simpleType children
                    for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode restrictionChild : simpleType.getChildren()) {
                        String baseType = extractBaseType(restrictionChild);
                        if (baseType != null) {
                            return baseType;
                        }
                    }
                }
            }

            return null;
        }

        /**
         * Extracts the base type from an XsdExtension or XsdRestriction node.
         *
         * @param node the node to extract base type from
         * @return the base type, or null if not found
         */
        private String extractBaseType(org.fxt.freexmltoolkit.controls.v2.model.XsdNode node) {
            // Check if it's an extension or restriction
            if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction restriction) {
                String base = restriction.getBase();
                if (base != null && !base.isEmpty()) {
                    return base;
                }
            } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdExtension extension) {
                String base = extension.getBase();
                if (base != null && !base.isEmpty()) {
                    return base;
                }
            }
            return null;
        }

        public void addChild(VisualNode child) {
            children.add(child);
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }

        public boolean containsPoint(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }

        public boolean expandButtonContainsPoint(double px, double py) {
            return hasChildren() && px >= expandBtnX && px <= expandBtnX + expandBtnW
                    && py >= expandBtnY && py <= expandBtnY + expandBtnH;
        }

        public void toggleExpanded() {
            expanded = !expanded;
        }

        // Getters and setters
        public String getLabel() {
            return label;
        }

        public String getDetail() {
            return detail;
        }

        public NodeWrapperType getType() {
            return type;
        }

        /**
         * Gets the model node if the model object is an XsdNode.
         *
         * @return the XsdNode, or null if model object is not an XsdNode
         */
        public org.fxt.freexmltoolkit.controls.v2.model.XsdNode getModelNode() {
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                return xsdNode;
            }
            return null;
        }

        public Object getModelObject() {
            return modelObject;
        }

        public VisualNode getParent() {
            return parent;
        }

        public java.util.List<VisualNode> getChildren() {
            return children;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        public boolean isExpanded() {
            return expanded;
        }

        public int getMinOccurs() {
            return minOccurs;
        }

        public int getMaxOccurs() {
            return maxOccurs;
        }

        // Getters for new visual properties
        public String getIconLiteral() {
            return iconLiteral;
        }

        public Color getHeaderColor() {
            return headerColor;
        }

        public Color getBorderColor() {
            return borderColor;
        }

        public Color getIconBackgroundColor() {
            return iconBackgroundColor;
        }

        public String getCardinalityBadge() {
            return cardinalityBadge;
        }

        public boolean isShowConnectionPoints() {
            return showConnectionPoints;
        }

        public void setX(double x) {
            this.x = x;
        }

        public void setY(double y) {
            this.y = y;
        }

        public void setWidth(double w) {
            this.width = w;
        }

        public void setHeight(double h) {
            this.height = h;
        }

        public void setExpanded(boolean e) {
            this.expanded = e;
        }

        public void setExpandButtonBounds(double x, double y, double w, double h) {
            this.expandBtnX = x;
            this.expandBtnY = y;
            this.expandBtnW = w;
            this.expandBtnH = h;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public boolean isHovered() {
            return hovered;
        }

        public void setHovered(boolean hovered) {
            this.hovered = hovered;
        }

        public boolean isFocused() {
            return focused;
        }

        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        public boolean isInEditMode() {
            return inEditMode;
        }

        public void setInEditMode(boolean inEditMode) {
            this.inEditMode = inEditMode;
        }

        public boolean isDragging() {
            return dragging;
        }

        public void setDragging(boolean dragging) {
            this.dragging = dragging;
        }

        public boolean isDropTarget() {
            return dropTarget;
        }

        public void setDropTarget(boolean dropTarget) {
            this.dropTarget = dropTarget;
        }

        /**
         * Sets the callback to be invoked when the underlying model changes.
         * This allows updating the callback after tree rebuild operations.
         *
         * @param callback the callback to invoke on model changes
         * @since 2.0
         */
        public void setOnModelChangeCallback(Runnable callback) {
            this.onModelChangeCallback = callback;
        }

        /**
         * Checks if this node is from an included file.
         * Uses the sourceInfo from the underlying XsdNode model.
         *
         * @return true if the node is from an included file, false otherwise
         * @since 2.0
         */
        public boolean isFromInclude() {
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                var sourceInfo = xsdNode.getSourceInfo();
                return sourceInfo != null && sourceInfo.isFromInclude();
            }
            return false;
        }

        /**
         * Gets the source file name if available.
         *
         * @return the source file name, or null if not from an include
         * @since 2.0
         */
        public String getSourceFileName() {
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                var sourceInfo = xsdNode.getSourceInfo();
                if (sourceInfo != null) {
                    return sourceInfo.getFileName();
                }
            }
            return null;
        }
    }
}
