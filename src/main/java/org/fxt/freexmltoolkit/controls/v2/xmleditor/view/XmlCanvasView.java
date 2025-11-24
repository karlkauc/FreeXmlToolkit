package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;

import java.beans.PropertyChangeEvent;
import java.util.*;

/**
 * Canvas-based XML view that renders the XML tree structure with embedded tables
 * for repeating elements, similar to XMLSpy.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Custom rendering using Canvas and GraphicsContext</li>
 *   <li>Tree structure with expand/collapse</li>
 *   <li>Embedded grid tables for repeating elements</li>
 *   <li>Mouse interaction (click, expand/collapse)</li>
 *   <li>Scrolling support</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlCanvasView extends BorderPane {

    private final XmlEditorContext context;
    private final Canvas canvas;
    private final GraphicsContext gc;
    private final ScrollBar scrollBar;

    // Layout constants - XMLSpy Grid View style
    private static final double INDENT = 20.0;
    private static final double LINE_HEIGHT = 24.0;
    private static final double FONT_SIZE = 11.0;
    private static final double TABLE_PADDING = 5.0;
    private static final double TABLE_ROW_HEIGHT = 20.0;
    private static final double TABLE_HEADER_HEIGHT = 22.0;
    private static final double TABLE_CELL_PADDING = 4.0;
    private static final double ICON_SIZE = 9.0;

    // Grid column widths
    private static final double COLUMN_ELEMENT_WIDTH = 300.0;
    private static final double COLUMN_ATTRIBUTES_WIDTH = 250.0;
    private static final double COLUMN_CONTENT_WIDTH = 300.0;
    private static final double ELEMENT_BLOCK_PADDING = 6.0;
    private static final double ELEMENT_BLOCK_HEIGHT = 18.0;
    private static final double CORNER_RADIUS = 4.0;

    // Colors - XMLSpy Grid View style
    private static final Color BG_COLOR = Color.WHITE;
    private static final Color GRID_LINE_COLOR = Color.rgb(220, 220, 220);
    private static final Color HIERARCHY_LINE_COLOR = Color.rgb(180, 180, 180);
    private static final Color TEXT_COLOR = Color.rgb(0, 0, 0);

    // Element block colors (rounded colored blocks)
    private static final Color ELEMENT_BLOCK_BG = Color.rgb(230, 240, 255);
    private static final Color ELEMENT_BLOCK_BORDER = Color.rgb(100, 140, 200);
    private static final Color ELEMENT_TEXT_COLOR = Color.rgb(0, 60, 140);

    private static final Color ATTRIBUTE_COLOR = Color.rgb(128, 0, 128);
    private static final Color CONTENT_COLOR = Color.rgb(0, 0, 0);
    private static final Color EXPAND_ICON_COLOR = Color.rgb(100, 100, 100);

    // Row background colors (alternating)
    private static final Color ROW_BG_ODD = Color.WHITE;
    private static final Color ROW_BG_EVEN = Color.rgb(250, 250, 250);

    // Table colors
    private static final Color TABLE_BORDER_COLOR = Color.rgb(200, 200, 200);
    private static final Color TABLE_HEADER_BG = Color.rgb(235, 235, 235);
    private static final Color TABLE_HEADER_TEXT = Color.rgb(50, 50, 50);
    private static final Color TABLE_ROW_ODD = Color.WHITE;
    private static final Color TABLE_ROW_EVEN = Color.rgb(248, 248, 248);
    private static final Color TABLE_GRID_COLOR = Color.rgb(220, 220, 220);

    // State
    private final Map<XmlNode, Boolean> expandedNodes = new HashMap<>();
    private final Map<XmlNode, ToggleIconBounds> iconBounds = new HashMap<>();
    private double scrollY = 0.0;
    private double contentHeight = 0.0;

    // Helper class to track icon bounds for click detection
    private static class ToggleIconBounds {
        final double x, y, size;

        ToggleIconBounds(double x, double y, double size) {
            this.x = x;
            this.y = y;
            this.size = size;
        }

        boolean contains(double clickX, double clickY) {
            return clickX >= x - size / 2 && clickX <= x + size / 2 &&
                    clickY >= y - size / 2 && clickY <= y + size / 2;
        }
    }

    // ==================== Constructor ====================

    public XmlCanvasView(XmlEditorContext context) {
        this.context = context;
        this.canvas = new Canvas(800, 600);
        this.gc = canvas.getGraphicsContext2D();
        this.scrollBar = new ScrollBar();

        setupCanvas();
        setupScrollBar();
        setupListeners();

        setCenter(canvas);
        setRight(scrollBar);
    }

    // ==================== Setup Methods ====================

    private void setupCanvas() {
        // Bind canvas size to parent size
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        // Redraw when size changes
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> render());
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> render());

        // Mouse events
        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleMouseClick);
        canvas.addEventHandler(MouseEvent.MOUSE_MOVED, this::handleMouseMove);

        // Scroll wheel support
        canvas.addEventHandler(ScrollEvent.SCROLL, this::handleScroll);

        // Initial render
        render();
    }

    private void setupScrollBar() {
        // Configure vertical scrollbar
        scrollBar.setOrientation(javafx.geometry.Orientation.VERTICAL);
        scrollBar.setMin(0);
        scrollBar.setValue(0);
        scrollBar.setVisibleAmount(100);

        // Listen to scrollbar value changes
        scrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            scrollY = newVal.doubleValue();
            render();
        });
    }

    private void setupListeners() {
        // Listen to document changes
        context.addPropertyChangeListener("document", this::onDocumentChanged);
    }

    // ==================== Rendering ====================

    /**
     * Main render method that draws the entire XML tree in Grid View style.
     */
    public void render() {
        // Clear canvas
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        XmlDocument doc = context.getDocument();
        if (doc == null) {
            return;
        }

        // Reset content height and icon bounds
        contentHeight = 0.0;
        iconBounds.clear();

        // Setup font - Arial like XMLSpy
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, FONT_SIZE));

        // Draw grid header (column titles)
        double headerY = 0.0;
        drawGridHeader(headerY);

        // Start rendering content below header
        double y = TABLE_HEADER_HEIGHT - scrollY;

        // Draw grid column lines (vertical separators)
        drawGridColumnLines();

        // Render document nodes
        int rowIndex = 0;
        for (XmlNode child : doc.getChildren()) {
            if (child instanceof XmlElement) {
                y = renderNode((XmlElement) child, 0, y, 0, rowIndex);
                rowIndex++;
            }
        }

        // Update content height
        contentHeight = y + scrollY;

        // Update scrollbar range
        updateScrollBar();
    }

    /**
     * Draws the grid header with column titles.
     */
    private void drawGridHeader(double y) {
        // Header background
        gc.setFill(TABLE_HEADER_BG);
        gc.fillRect(0, y, canvas.getWidth(), TABLE_HEADER_HEIGHT);

        // Header border
        gc.setStroke(GRID_LINE_COLOR);
        gc.setLineWidth(1.0);
        gc.strokeLine(0, y + TABLE_HEADER_HEIGHT, canvas.getWidth(), y + TABLE_HEADER_HEIGHT);

        // Column titles
        gc.setFill(TABLE_HEADER_TEXT);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, FONT_SIZE));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);

        // Element column
        gc.fillText("Element", 10, y + TABLE_HEADER_HEIGHT / 2);

        // Attributes column
        gc.fillText("Attributes", COLUMN_ELEMENT_WIDTH + 10, y + TABLE_HEADER_HEIGHT / 2);

        // Content column
        gc.fillText("Content", COLUMN_ELEMENT_WIDTH + COLUMN_ATTRIBUTES_WIDTH + 10, y + TABLE_HEADER_HEIGHT / 2);

        // Reset font to normal
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, FONT_SIZE));
    }

    /**
     * Draws vertical grid column lines.
     */
    private void drawGridColumnLines() {
        gc.setStroke(GRID_LINE_COLOR);
        gc.setLineWidth(1.0);

        // Line between Element and Attributes columns
        double x1 = COLUMN_ELEMENT_WIDTH;
        gc.strokeLine(x1, 0, x1, canvas.getHeight());

        // Line between Attributes and Content columns
        double x2 = COLUMN_ELEMENT_WIDTH + COLUMN_ATTRIBUTES_WIDTH;
        gc.strokeLine(x2, 0, x2, canvas.getHeight());
    }

    /**
     * Renders a single node and its children.
     * Uses viewport culling for performance - only renders visible nodes.
     *
     * @param node     the node to render
     * @param level    the indentation level
     * @param y        the y-coordinate
     * @param parentX  the x-coordinate of the parent
     * @param rowIndex the row index for alternating colors
     * @return the new y-coordinate after rendering
     */
    private double renderNode(XmlElement node, int level, double y, double parentX, int rowIndex) {
        double viewportTop = 0;
        double viewportBottom = canvas.getHeight();

        // Check if node is expanded
        boolean isExpanded = expandedNodes.getOrDefault(node, false);

        // Calculate if this node should render as table
        boolean isTable = shouldRenderAsTable(node);

        // FAST PATH: If node is completely above viewport, calculate height without rendering
        if (y + LINE_HEIGHT < viewportTop) {
            if (isTable && isExpanded) {
                // Calculate table height without rendering
                return y + calculateTableHeight(node);
            } else {
                // Just skip this line
                double nodeHeight = LINE_HEIGHT;
                // If expanded, also skip all children
                if (isExpanded && node.getChildCount() > 0) {
                    nodeHeight += calculateSubtreeHeight(node, level + 1);
                }
                return y + nodeHeight;
            }
        }

        // FAST PATH: If node is completely below viewport, don't render anything
        // But still need to calculate position for scrollbar
        if (y > viewportBottom) {
            if (isTable && isExpanded) {
                return y + calculateTableHeight(node);
            } else {
                double nodeHeight = LINE_HEIGHT;
                if (isExpanded && node.getChildCount() > 0) {
                    nodeHeight += calculateSubtreeHeight(node, level + 1);
                }
                return y + nodeHeight;
            }
        }

        // RENDER PATH: Node is visible, render it
        if (isTable) {
            y = renderTable(node, level, y);
        } else {
            y = renderTreeNode(node, level, y, parentX, rowIndex);
        }

        return y;
    }

    /**
     * Calculates the height of a subtree without rendering it.
     * Used for viewport culling optimization.
     */
    private double calculateSubtreeHeight(XmlElement node, int level) {
        double height = 0;
        boolean isExpanded = expandedNodes.getOrDefault(node, false);

        for (XmlNode child : node.getChildren()) {
            if (child instanceof XmlElement) {
                XmlElement childElement = (XmlElement) child;

                // Add height for this child
                height += LINE_HEIGHT;

                // If child is expanded and has children, recursively calculate
                if (isExpanded && childElement.getChildCount() > 0) {
                    boolean childExpanded = expandedNodes.getOrDefault(childElement, false);
                    if (childExpanded) {
                        if (shouldRenderAsTable(childElement)) {
                            height += calculateTableHeight(childElement);
                        } else {
                            height += calculateSubtreeHeight(childElement, level + 1);
                        }
                    }
                }
            }
        }

        return height;
    }

    /**
     * Calculates the height of a table without rendering it.
     * Used for viewport culling optimization.
     */
    private double calculateTableHeight(XmlElement node) {
        // Find repeating element info
        Map<String, List<XmlElement>> repeatingElements = new HashMap<>();
        for (XmlNode child : node.getChildren()) {
            if (child instanceof XmlElement) {
                XmlElement element = (XmlElement) child;
                String name = element.getName();
                repeatingElements.computeIfAbsent(name, k -> new ArrayList<>()).add(element);
            }
        }

        // Find first repeating element with >= 2 occurrences
        for (List<XmlElement> elements : repeatingElements.values()) {
            if (elements.size() >= 2) {
                // Calculate table height: header + rows + padding
                int rowCount = elements.size();
                return TABLE_HEADER_HEIGHT + (rowCount * TABLE_ROW_HEIGHT) + (TABLE_PADDING * 2);
            }
        }

        // Fallback - shouldn't happen
        return LINE_HEIGHT;
    }

    /**
     * Renders a tree node in Grid View layout with 3 columns: Element | Attributes | Content.
     */
    private double renderTreeNode(XmlElement node, int level, double y, double parentX, int rowIndex) {
        // Draw row background (alternating colors)
        Color rowBg = (rowIndex % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
        gc.setFill(rowBg);
        gc.fillRect(0, y, canvas.getWidth(), LINE_HEIGHT);

        // ========== COLUMN 1: ELEMENT ==========
        double x = level * INDENT + 10;

        // Draw hierarchy tree lines
        gc.setStroke(HIERARCHY_LINE_COLOR);
        gc.setLineWidth(1.0);
        if (level > 0) {
            gc.strokeLine(parentX, y - LINE_HEIGHT / 2, x - 5, y + LINE_HEIGHT / 2);
        }

        // Draw expand/collapse icon if node has children
        boolean hasChildren = node.getChildCount() > 0;
        boolean isExpanded = expandedNodes.getOrDefault(node, false);

        if (hasChildren) {
            double iconX = x;
            double iconY = y + LINE_HEIGHT / 2;

            // Store icon bounds for click detection
            iconBounds.put(node, new ToggleIconBounds(iconX, iconY, ICON_SIZE));

            // Draw triangle icon (XMLSpy style)
            gc.setFill(EXPAND_ICON_COLOR);
            if (isExpanded) {
                // Down-pointing triangle ▼
                double[] xPoints = {iconX - ICON_SIZE / 2, iconX + ICON_SIZE / 2, iconX};
                double[] yPoints = {iconY - ICON_SIZE / 3, iconY - ICON_SIZE / 3, iconY + ICON_SIZE / 2};
                gc.fillPolygon(xPoints, yPoints, 3);
            } else {
                // Right-pointing triangle ▶
                double[] xPoints = {iconX - ICON_SIZE / 3, iconX + ICON_SIZE / 2, iconX - ICON_SIZE / 3};
                double[] yPoints = {iconY - ICON_SIZE / 2, iconY, iconY + ICON_SIZE / 2};
                gc.fillPolygon(xPoints, yPoints, 3);
            }

            x += ICON_SIZE + 5;
        }

        // Draw element name as rounded colored block
        String elementName = node.getName();
        double textWidth = estimateTextWidth(elementName, FONT_SIZE);
        double blockWidth = textWidth + ELEMENT_BLOCK_PADDING * 2;
        double blockHeight = ELEMENT_BLOCK_HEIGHT;
        double blockY = y + (LINE_HEIGHT - blockHeight) / 2;

        // Draw rounded rectangle background
        gc.setFill(ELEMENT_BLOCK_BG);
        roundRect(gc, x, blockY, blockWidth, blockHeight, CORNER_RADIUS);

        // Draw border
        gc.setStroke(ELEMENT_BLOCK_BORDER);
        gc.setLineWidth(1.0);
        strokeRoundRect(gc, x, blockY, blockWidth, blockHeight, CORNER_RADIUS);

        // Draw element name text
        gc.setFill(ELEMENT_TEXT_COLOR);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(elementName, x + ELEMENT_BLOCK_PADDING, y + LINE_HEIGHT / 2);

        // ========== COLUMN 2: ATTRIBUTES ==========
        double attrX = COLUMN_ELEMENT_WIDTH + 10;
        if (!node.getAttributes().isEmpty()) {
            gc.setFill(ATTRIBUTE_COLOR);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.CENTER);

            StringBuilder attrText = new StringBuilder();
            for (Map.Entry<String, String> attr : node.getAttributes().entrySet()) {
                if (attrText.length() > 0) attrText.append(" ");
                attrText.append(attr.getKey()).append("=\"").append(attr.getValue()).append("\"");
            }

            gc.fillText(attrText.toString(), attrX, y + LINE_HEIGHT / 2);
        }

        // ========== COLUMN 3: CONTENT ==========
        double contentX = COLUMN_ELEMENT_WIDTH + COLUMN_ATTRIBUTES_WIDTH + 10;

        // Find text content (skip if has element children)
        String textContent = getTextContent(node);
        if (textContent != null && !textContent.isEmpty()) {
            gc.setFill(CONTENT_COLOR);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.CENTER);

            // Truncate if too long
            String displayText = textContent;
            if (displayText.length() > 50) {
                displayText = displayText.substring(0, 47) + "...";
            }

            gc.fillText(displayText, contentX, y + LINE_HEIGHT / 2);
        }

        y += LINE_HEIGHT;

        // Render children if expanded
        int childRowIndex = rowIndex + 1;
        if (isExpanded && hasChildren) {
            for (XmlNode child : node.getChildren()) {
                if (child instanceof XmlElement) {
                    y = renderNode((XmlElement) child, level + 1, y, x - 5, childRowIndex);
                    childRowIndex++;
                }
            }
        }

        return y;
    }

    /**
     * Gets text content from element (only if no element children).
     */
    private String getTextContent(XmlElement element) {
        boolean hasElementChildren = false;
        StringBuilder text = new StringBuilder();

        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement) {
                hasElementChildren = true;
                break;
            } else if (child instanceof XmlText) {
                text.append(((XmlText) child).getText());
            }
        }

        return hasElementChildren ? null : text.toString().trim();
    }

    /**
     * Estimates text width for rendering.
     */
    private double estimateTextWidth(String text, double fontSize) {
        return text.length() * fontSize * 0.6;
    }

    /**
     * Draws a filled rounded rectangle.
     */
    private void roundRect(GraphicsContext gc, double x, double y, double width, double height, double radius) {
        gc.beginPath();
        gc.moveTo(x + radius, y);
        gc.lineTo(x + width - radius, y);
        gc.quadraticCurveTo(x + width, y, x + width, y + radius);
        gc.lineTo(x + width, y + height - radius);
        gc.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
        gc.lineTo(x + radius, y + height);
        gc.quadraticCurveTo(x, y + height, x, y + height - radius);
        gc.lineTo(x, y + radius);
        gc.quadraticCurveTo(x, y, x + radius, y);
        gc.closePath();
        gc.fill();
    }

    /**
     * Strokes a rounded rectangle.
     */
    private void strokeRoundRect(GraphicsContext gc, double x, double y, double width, double height, double radius) {
        gc.beginPath();
        gc.moveTo(x + radius, y);
        gc.lineTo(x + width - radius, y);
        gc.quadraticCurveTo(x + width, y, x + width, y + radius);
        gc.lineTo(x + width, y + height - radius);
        gc.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
        gc.lineTo(x + radius, y + height);
        gc.quadraticCurveTo(x, y + height, x, y + height - radius);
        gc.lineTo(x, y + radius);
        gc.quadraticCurveTo(x, y, x + radius, y);
        gc.closePath();
        gc.stroke();
    }

    /**
     * Renders a text node.
     */
    private double renderTextNode(XmlText textNode, int level, double y) {
        double x = level * INDENT + 20;

        String text = textNode.getText().trim();
        if (!text.isEmpty()) {
            gc.setFill(TEXT_COLOR);
            gc.fillText(text, x, y + 2);
            y += LINE_HEIGHT;
        }

        return y;
    }

    /**
     * Renders repeating elements as a table.
     */
    private double renderTable(XmlElement parentNode, int level, double y) {
        double x = level * INDENT + 20;

        // Get repeating elements
        List<XmlElement> repeatingElements = getRepeatingElements(parentNode);
        if (repeatingElements.isEmpty()) {
            return y;
        }

        String elementName = repeatingElements.get(0).getName();
        boolean isExpanded = expandedNodes.getOrDefault(parentNode, false);

        // Draw expand/collapse icon for table
        double iconX = x;
        double iconY = y + LINE_HEIGHT / 2;

        // Store icon bounds for click detection
        iconBounds.put(parentNode, new ToggleIconBounds(iconX, iconY, ICON_SIZE));

        // Draw triangle icon (XMLSpy style)
        gc.setFill(EXPAND_ICON_COLOR);
        if (isExpanded) {
            // Down-pointing triangle ▼
            double[] xPoints = {iconX - ICON_SIZE / 2, iconX + ICON_SIZE / 2, iconX};
            double[] yPoints = {iconY - ICON_SIZE / 3, iconY - ICON_SIZE / 3, iconY + ICON_SIZE / 2};
            gc.fillPolygon(xPoints, yPoints, 3);
        } else {
            // Right-pointing triangle ▶
            double[] xPoints = {iconX - ICON_SIZE / 3, iconX + ICON_SIZE / 2, iconX - ICON_SIZE / 3};
            double[] yPoints = {iconY - ICON_SIZE / 2, iconY, iconY + ICON_SIZE / 2};
            gc.fillPolygon(xPoints, yPoints, 3);
        }

        // Draw parent element name
        gc.setFill(ELEMENT_TEXT_COLOR);
        gc.fillText("<" + parentNode.getName() + ">", x + ICON_SIZE + 5, y + 2);
        y += LINE_HEIGHT;

        // Only render table if expanded
        if (!isExpanded) {
            return y;
        }

        // Collect all column names
        Set<String> columnNamesSet = new LinkedHashSet<>();
        for (XmlElement element : repeatingElements) {
            for (XmlNode child : element.getChildren()) {
                if (child instanceof XmlElement) {
                    columnNamesSet.add(((XmlElement) child).getName());
                }
            }
        }
        List<String> columnNames = new ArrayList<>(columnNamesSet);

        // Calculate column widths
        double rowNumberWidth = 40;
        double columnWidth = 150;
        double tableWidth = rowNumberWidth + columnNames.size() * columnWidth;

        // Draw table border
        double tableX = x + INDENT;
        double tableY = y;
        gc.setStroke(TABLE_BORDER_COLOR);
        gc.setLineWidth(1.0);

        // Draw header
        double headerY = tableY;
        gc.setFill(TABLE_HEADER_BG);
        gc.fillRect(tableX, headerY, tableWidth, TABLE_HEADER_HEIGHT);

        // Draw header text
        gc.setFill(TABLE_HEADER_TEXT);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, FONT_SIZE));

        // Row number header
        gc.fillText("#", tableX + TABLE_CELL_PADDING, headerY + TABLE_CELL_PADDING + 2);

        // Column headers
        double colX = tableX + rowNumberWidth;
        for (String columnName : columnNames) {
            gc.fillText(columnName, colX + TABLE_CELL_PADDING, headerY + TABLE_CELL_PADDING + 2);
            colX += columnWidth;
        }

        // Draw header border
        gc.setStroke(TABLE_BORDER_COLOR);
        gc.strokeRect(tableX, headerY, tableWidth, TABLE_HEADER_HEIGHT);

        // Draw vertical grid lines in header
        colX = tableX + rowNumberWidth;
        for (int i = 0; i < columnNames.size(); i++) {
            gc.strokeLine(colX, headerY, colX, headerY + TABLE_HEADER_HEIGHT);
            colX += columnWidth;
        }

        // Draw data rows
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, FONT_SIZE));
        double rowY = headerY + TABLE_HEADER_HEIGHT;
        int rowNumber = 1;

        for (XmlElement element : repeatingElements) {
            // Alternating row colors
            gc.setFill(rowNumber % 2 == 1 ? TABLE_ROW_ODD : TABLE_ROW_EVEN);
            gc.fillRect(tableX, rowY, tableWidth, TABLE_ROW_HEIGHT);

            // Draw row number
            gc.setFill(TEXT_COLOR);
            gc.fillText(String.valueOf(rowNumber), tableX + TABLE_CELL_PADDING, rowY + TABLE_CELL_PADDING);

            // Draw cell data
            colX = tableX + rowNumberWidth;
            for (String columnName : columnNames) {
                // Find child element with this name
                String cellValue = "";
                for (XmlNode child : element.getChildren()) {
                    if (child instanceof XmlElement && ((XmlElement) child).getName().equals(columnName)) {
                        cellValue = ((XmlElement) child).getTextContent();
                        break;
                    }
                }

                gc.fillText(cellValue != null ? cellValue : "", colX + TABLE_CELL_PADDING, rowY + TABLE_CELL_PADDING);
                colX += columnWidth;
            }

            // Draw row border
            gc.setStroke(TABLE_BORDER_COLOR);
            gc.strokeRect(tableX, rowY, tableWidth, TABLE_ROW_HEIGHT);

            rowY += TABLE_ROW_HEIGHT;
            rowNumber++;
        }

        return rowY + TABLE_PADDING;
    }

    /**
     * Checks if a node should be rendered as a table.
     */
    private boolean shouldRenderAsTable(XmlElement node) {
        List<XmlElement> children = getElementChildren(node);

        if (children.size() < 2) {
            return false;
        }

        // Check if all children have the same name
        String firstName = children.get(0).getName();
        for (XmlElement child : children) {
            if (!child.getName().equals(firstName)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets repeating element children.
     */
    private List<XmlElement> getRepeatingElements(XmlElement node) {
        return getElementChildren(node);
    }

    /**
     * Gets element children only.
     */
    private List<XmlElement> getElementChildren(XmlElement node) {
        List<XmlElement> elements = new ArrayList<>();
        for (XmlNode child : node.getChildren()) {
            if (child instanceof XmlElement) {
                elements.add((XmlElement) child);
            }
        }
        return elements;
    }

    // ==================== Mouse Interaction ====================

    private void handleMouseClick(MouseEvent event) {
        double clickX = event.getX();
        double clickY = event.getY() + scrollY;

        // Check if click is on any expand/collapse icon
        for (Map.Entry<XmlNode, ToggleIconBounds> entry : iconBounds.entrySet()) {
            ToggleIconBounds bounds = entry.getValue();
            if (bounds.contains(clickX, clickY)) {
                // Toggle the expanded state
                XmlNode node = entry.getKey();
                boolean currentState = expandedNodes.getOrDefault(node, false);
                expandedNodes.put(node, !currentState);
                render();
                return;
            }
        }
    }

    private void handleMouseMove(MouseEvent event) {
        // TODO: Implement hover effects
    }

    /**
     * Handles mouse wheel scrolling.
     */
    private void handleScroll(ScrollEvent event) {
        double deltaY = event.getDeltaY();
        double scrollAmount = deltaY * 0.5; // Scroll speed multiplier

        // Calculate new scroll position
        double newScrollY = scrollY - scrollAmount;
        double maxScroll = Math.max(0, contentHeight - canvas.getHeight());

        // Clamp scroll position
        newScrollY = Math.max(0, Math.min(newScrollY, maxScroll));

        // Update scrollbar value (which will trigger render)
        scrollBar.setValue(newScrollY);

        event.consume();
    }

    /**
     * Updates scrollbar range based on content height.
     */
    private void updateScrollBar() {
        double viewportHeight = canvas.getHeight();
        double maxScroll = Math.max(0, contentHeight - viewportHeight);

        // Update scrollbar max value
        scrollBar.setMax(maxScroll);

        // Update visible amount (thumb size)
        if (contentHeight > 0) {
            double visibleRatio = viewportHeight / contentHeight;
            scrollBar.setVisibleAmount(maxScroll * visibleRatio);
        }

        // Ensure current scroll position is within bounds
        if (scrollY > maxScroll) {
            scrollBar.setValue(maxScroll);
        }
    }

    // ==================== Event Handlers ====================

    private void onDocumentChanged(PropertyChangeEvent evt) {
        expandedNodes.clear();
        render();
    }

    // ==================== Public API ====================

    public void refresh() {
        render();
    }

    public void expandAll() {
        XmlDocument doc = context.getDocument();
        if (doc != null) {
            expandAllNodes(doc.getChildren());
        }
        render();
    }

    private void expandAllNodes(List<XmlNode> nodes) {
        for (XmlNode node : nodes) {
            if (node instanceof XmlElement) {
                XmlElement element = (XmlElement) node;
                if (element.getChildCount() > 0) {
                    expandedNodes.put(element, true);
                    expandAllNodes(element.getChildren());
                }
            }
        }
    }

    public void collapseAll() {
        expandedNodes.clear();
        render();
    }
}
