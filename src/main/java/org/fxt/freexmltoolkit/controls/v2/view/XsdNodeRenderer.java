package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Renders individual XSD nodes as rectangular boxes with expand/collapse controls.
 * Provides XMLSpy-style visual representation of schema elements.
 *
 * @since 2.0
 */
public class XsdNodeRenderer {

    private static final double MIN_NODE_WIDTH = 150;
    private static final double MAX_NODE_WIDTH = 500;  // Increased for longer text
    private static final double NODE_HEIGHT = 50;
    private static final double COMPOSITOR_SIZE = 28;  // Small square for compositors
    private static final double EXPAND_BUTTON_SIZE = 16;
    private static final double HORIZONTAL_SPACING = 40;
    private static final double VERTICAL_SPACING = 20;
    private static final double CORNER_RADIUS = 4;  // XMLSpy uses 4px radius
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
     * Calculates the required width for a node based on its label and detail text.
     */
    public double calculateNodeWidth(VisualNode node) {
        if (isCompositorNode(node)) {
            return COMPOSITOR_SIZE;
        }

        // Measure label text with proper font
        textMeasurer.setFont(nodeFont);
        textMeasurer.setText(node.getLabel() != null ? node.getLabel() : "");
        double labelWidth = textMeasurer.getBoundsInLocal().getWidth();

        // Measure detail text with proper font
        double detailWidth = 0;
        if (node.getDetail() != null && !node.getDetail().isEmpty()) {
            textMeasurer.setFont(detailFont);
            textMeasurer.setText(node.getDetail());
            detailWidth = textMeasurer.getBoundsInLocal().getWidth();
        }

        // Take maximum of both, add padding and expand button space
        double maxTextWidth = Math.max(labelWidth, detailWidth);
        double requiredWidth = maxTextWidth + PADDING + (node.hasChildren() ? EXPAND_BUTTON_SIZE + 10 : 0) + 20;

        // Use minimum width but allow expansion for longer text
        return Math.max(MIN_NODE_WIDTH, Math.min(MAX_NODE_WIDTH, requiredWidth));
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
     * Renders a regular node (element, attribute, etc.)
     */
    private void renderRegularNode(GraphicsContext gc, VisualNode node, double x, double y) {
        // Use the width and height already set during layout
        double width = node.getWidth();
        double height = node.getHeight();

        // Determine gradient fill and border colors based on node type (XMLSpy style)
        LinearGradient fillGradient = getNodeFillGradient(node.getType(), x, y, width, height);
        Color borderColor = getXMLSpyBorderColor(node.getType());

        // Apply visual feedback modifications for hover/selection
        if (node.isHovered()) {
            // Brighten gradient for hover effect
            fillGradient = brightenGradient(fillGradient);
        }

        if (node.isSelected()) {
            // Use brighter gradient for selected nodes
            fillGradient = brightenGradient(fillGradient);
        }

        // Draw node rectangle with gradient fill
        gc.setFill(fillGradient);
        gc.setStroke(borderColor);

        // Determine line width based on state and maxOccurs
        double lineWidth;
        if (node.isSelected()) {
            lineWidth = 4.0;  // Thicker border for selected nodes
        } else if (node.getMaxOccurs() > 1 || node.getMaxOccurs() == Integer.MAX_VALUE || node.getMaxOccurs() == -1) {
            lineWidth = 3.0;  // Thicker border for multiple occurrences (check for both Integer.MAX_VALUE and -1 for UNBOUNDED)
        } else {
            lineWidth = 1.5;  // Normal border for single occurrence
        }
        gc.setLineWidth(lineWidth);

        // Draw filled rectangle
        gc.fillRoundRect(x, y, width, height, CORNER_RADIUS, CORNER_RADIUS);

        // Draw border (dashed if optional, solid otherwise)
        if (node.getMinOccurs() == 0) {
            // Dashed border for optional nodes
            gc.setLineDashes(6, 4);
            gc.strokeRoundRect(x, y, width, height, CORNER_RADIUS, CORNER_RADIUS);
            gc.setLineDashes(null);  // Reset to solid
        } else {
            // Solid border for required nodes
            gc.strokeRoundRect(x, y, width, height, CORNER_RADIUS, CORNER_RADIUS);
        }

        // Draw selection highlight (additional colored border)
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

        // Draw node text with XMLSpy styling
        // Element and attribute names use specific colors
        Color textColor = switch (node.getType()) {
            case ELEMENT -> Color.rgb(44, 90, 160);        // #2c5aa0 - XMLSpy element text
            case ATTRIBUTE -> Color.rgb(139, 105, 20);     // #8b6914 - XMLSpy attribute text
            default -> Color.rgb(51, 51, 51);              // #333333 - Default text
        };

        gc.setFill(textColor);
        gc.setFont(nodeFont);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);

        // Calculate available text width (node width minus padding and expand button)
        double availableWidth = width - 20 - (node.hasChildren() ? EXPAND_BUTTON_SIZE + 10 : 0);
        String displayText = truncateText(node.getLabel(), availableWidth, nodeFont);
        gc.fillText(displayText, x + 10, y + 10);

        // Draw detail text (type, cardinality)
        if (node.getDetail() != null && !node.getDetail().isEmpty()) {
            gc.setFont(detailFont);
            gc.setFill(Color.rgb(108, 117, 125));  // #6c757d - XMLSpy secondary text
            String detailText = truncateText(node.getDetail(), availableWidth, detailFont);
            gc.fillText(detailText, x + 10, y + 28);
        }
        
        // Draw cardinality indicator in top-right corner (before edit mode badge)
        String cardinality = getCardinalityString(node);
        if (!cardinality.isEmpty()) {
            gc.setFont(Font.font("Segoe UI", 9));
            gc.setFill(Color.rgb(67, 56, 202));  // Indigo color for cardinality
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setTextBaseline(VPos.TOP);
            
            // Position in top-right, accounting for edit badge if present
            double cardinalityX = x + width - 10 - (node.isInEditMode() ? 15 : 0);
            gc.fillText(cardinality, cardinalityX, y + 4);
            
            // Reset text alignment
            gc.setTextAlign(TextAlignment.LEFT);
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
                // Add type
                if (element.getType() != null) {
                    detail.append(element.getType());
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

            // Handle XsdAttribute - show type, use, and form
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute attribute) {
                // Add type
                if (attribute.getType() != null) {
                    detail.append(attribute.getType());
                }

                // Add use indicator
                if ("required".equals(attribute.getUse())) {
                    detail.append(" (required)");
                } else if ("prohibited".equals(attribute.getUse())) {
                    detail.append(" (prohibited)");
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
    }
}
