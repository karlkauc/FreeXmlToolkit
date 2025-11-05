package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Renders individual XSD nodes as rectangular boxes with expand/collapse controls.
 * Provides XMLSpy-style visual representation of schema elements.
 *
 * @since 2.0
 */
public class XsdNodeRenderer {

    private static final double NODE_WIDTH = 180;
    private static final double NODE_HEIGHT = 50;
    private static final double COMPOSITOR_SIZE = 28;  // Small square for compositors
    private static final double EXPAND_BUTTON_SIZE = 16;
    private static final double HORIZONTAL_SPACING = 40;
    private static final double VERTICAL_SPACING = 20;
    private static final double CORNER_RADIUS = 5;

    private final XsdNodeStyler styler;
    private final Font nodeFont;
    private final Font detailFont;

    public XsdNodeRenderer() {
        this.styler = new XsdNodeStyler();
        this.nodeFont = Font.font("Arial", 12);
        this.detailFont = Font.font("Arial", 10);
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
        node.setX(x);
        node.setY(y);
        node.setWidth(NODE_WIDTH);
        node.setHeight(NODE_HEIGHT);

        // Determine colors based on node type
        Color fillColor = getNodeFillColor(node.getType());
        Color borderColor = getNodeBorderColor(node.getType());

        // Draw node rectangle with cardinality-based border style
        gc.setFill(fillColor);
        gc.setStroke(borderColor);

        // Determine line width based on maxOccurs
        double lineWidth;
        if (node.getMaxOccurs() > 1 || node.getMaxOccurs() == Integer.MAX_VALUE) {
            lineWidth = 3.5;  // Thicker border for multiple occurrences
        } else {
            lineWidth = 2;    // Normal border for single occurrence
        }
        gc.setLineWidth(lineWidth);

        // Draw filled rectangle
        gc.fillRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, CORNER_RADIUS, CORNER_RADIUS);

        // Draw border (dashed if optional, solid otherwise)
        if (node.getMinOccurs() == 0) {
            // Dashed border for optional nodes
            gc.setLineDashes(6, 4);
            gc.strokeRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, CORNER_RADIUS, CORNER_RADIUS);
            gc.setLineDashes(null);  // Reset to solid
        } else {
            // Solid border for required nodes
            gc.strokeRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, CORNER_RADIUS, CORNER_RADIUS);
        }

        // Draw node text
        gc.setFill(Color.BLACK);
        gc.setFont(nodeFont);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);

        String displayText = truncateText(node.getLabel(), NODE_WIDTH - 40);
        gc.fillText(displayText, x + 10, y + 10);

        // Draw detail text (type, cardinality)
        if (node.getDetail() != null && !node.getDetail().isEmpty()) {
            gc.setFont(detailFont);
            gc.setFill(Color.GRAY);
            String detailText = truncateText(node.getDetail(), NODE_WIDTH - 40);
            gc.fillText(detailText, x + 10, y + 28);
        }

        // Draw expand/collapse button if node has children
        if (node.hasChildren()) {
            drawExpandButton(gc, node, x, y);
        }
    }

    /**
     * Renders a small compositor symbol (sequence, choice, all).
     */
    private void renderCompositorSymbol(GraphicsContext gc, VisualNode node, double x, double y) {
        node.setX(x);
        node.setY(y);
        node.setWidth(COMPOSITOR_SIZE);
        node.setHeight(COMPOSITOR_SIZE);

        Color borderColor = getNodeBorderColor(node.getType());

        // Draw small filled circle/diamond based on type
        gc.setStroke(borderColor);
        gc.setLineWidth(2);

        switch (node.getType()) {
            case SEQUENCE -> {
                // Draw rounded square for sequence
                gc.setFill(Color.rgb(207, 250, 254, 0.8));
                gc.fillRoundRect(x, y, COMPOSITOR_SIZE, COMPOSITOR_SIZE, 4, 4);
                gc.strokeRoundRect(x, y, COMPOSITOR_SIZE, COMPOSITOR_SIZE, 4, 4);

                // Draw "SEQ" or lines symbol
                gc.setFill(borderColor);
                gc.setFont(Font.font("Arial", 9));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.fillText("=", x + COMPOSITOR_SIZE / 2, y + COMPOSITOR_SIZE / 2);
            }
            case CHOICE -> {
                // Draw diamond for choice
                gc.setFill(Color.rgb(254, 243, 199, 0.8));
                double centerX = x + COMPOSITOR_SIZE / 2;
                double centerY = y + COMPOSITOR_SIZE / 2;
                double halfSize = COMPOSITOR_SIZE / 2;
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

                // Draw "?" symbol
                gc.setFill(borderColor);
                gc.setFont(Font.font("Arial", 11));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.fillText("?", centerX, centerY);
            }
            case ALL -> {
                // Draw circle for all
                gc.setFill(Color.rgb(237, 233, 254, 0.8));
                gc.fillOval(x, y, COMPOSITOR_SIZE, COMPOSITOR_SIZE);
                gc.strokeOval(x, y, COMPOSITOR_SIZE, COMPOSITOR_SIZE);

                // Draw "*" symbol
                gc.setFill(borderColor);
                gc.setFont(Font.font("Arial", 11));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.fillText("*", x + COMPOSITOR_SIZE / 2, y + COMPOSITOR_SIZE / 2);
            }
        }

        // No expand button for compositor symbols - they auto-expand
    }

    /**
     * Draws the expand/collapse button (+/-).
     */
    private void drawExpandButton(GraphicsContext gc, VisualNode node, double x, double y) {
        double btnX = x + NODE_WIDTH - EXPAND_BUTTON_SIZE - 8;
        double btnY = y + (NODE_HEIGHT - EXPAND_BUTTON_SIZE) / 2;

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
     */
    public void renderConnection(GraphicsContext gc, VisualNode parent, VisualNode child) {
        gc.setStroke(Color.GRAY);
        gc.setLineWidth(1);

        double startX = parent.getX() + parent.getWidth() / 2;
        double startY = parent.getY() + parent.getHeight();

        double endX = child.getX() + child.getWidth() / 2;
        double endY = child.getY();

        // Draw L-shaped connection
        double midY = startY + VERTICAL_SPACING / 2;

        gc.strokeLine(startX, startY, startX, midY);
        gc.strokeLine(startX, midY, endX, midY);
        gc.strokeLine(endX, midY, endX, endY);
    }

    /**
     * Gets the fill color for a node based on its type.
     */
    private Color getNodeFillColor(NodeWrapperType type) {
        return switch (type) {
            case ELEMENT -> Color.rgb(219, 234, 254);        // Light blue
            case ATTRIBUTE -> Color.rgb(254, 226, 226);       // Light red
            case COMPLEX_TYPE -> Color.rgb(243, 232, 255);    // Light purple
            case SIMPLE_TYPE -> Color.rgb(220, 252, 231);     // Light green
            case SCHEMA -> Color.rgb(241, 245, 249);          // Light gray
            case SEQUENCE -> Color.rgb(207, 250, 254);        // Light cyan
            case CHOICE -> Color.rgb(254, 243, 199);          // Light amber
            case ALL -> Color.rgb(237, 233, 254);             // Light violet
            default -> Color.WHITE;
        };
    }

    /**
     * Gets the border color for a node based on its type.
     */
    private Color getNodeBorderColor(NodeWrapperType type) {
        return switch (type) {
            case ELEMENT -> Color.rgb(37, 99, 235);           // Blue
            case ATTRIBUTE -> Color.rgb(220, 38, 38);         // Red
            case COMPLEX_TYPE -> Color.rgb(124, 58, 237);     // Purple
            case SIMPLE_TYPE -> Color.rgb(5, 150, 105);       // Green
            case SCHEMA -> Color.rgb(30, 41, 59);             // Dark gray
            case SEQUENCE -> Color.rgb(8, 145, 178);          // Cyan
            case CHOICE -> Color.rgb(245, 158, 11);           // Amber
            case ALL -> Color.rgb(139, 92, 246);              // Violet
            default -> Color.LIGHTGRAY;
        };
    }

    /**
     * Truncates text to fit within the specified width.
     */
    private String truncateText(String text, double maxWidth) {
        if (text == null) return "";

        // Simple truncation based on character count
        // A more sophisticated version would measure actual text width
        int maxChars = (int) (maxWidth / 7); // Approximation
        if (text.length() > maxChars) {
            return text.substring(0, Math.max(0, maxChars - 3)) + "...";
        }
        return text;
    }

    public double getNodeWidth() {
        return NODE_WIDTH;
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
     */
    public static class VisualNode {
        private final String label;
        private final String detail;
        private final NodeWrapperType type;
        private final Object modelObject;
        private final VisualNode parent;
        private final java.util.List<VisualNode> children = new java.util.ArrayList<>();
        private final int minOccurs;
        private final int maxOccurs;

        private double x, y, width, height;
        private double expandBtnX, expandBtnY, expandBtnW, expandBtnH;
        private boolean expanded = false;

        public VisualNode(String label, String detail, NodeWrapperType type, Object modelObject, VisualNode parent) {
            this(label, detail, type, modelObject, parent, 1, 1);
        }

        public VisualNode(String label, String detail, NodeWrapperType type, Object modelObject, VisualNode parent, int minOccurs, int maxOccurs) {
            this.label = label;
            this.detail = detail;
            this.type = type;
            this.modelObject = modelObject;
            this.parent = parent;
            this.minOccurs = minOccurs;
            this.maxOccurs = maxOccurs;
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
    }
}
