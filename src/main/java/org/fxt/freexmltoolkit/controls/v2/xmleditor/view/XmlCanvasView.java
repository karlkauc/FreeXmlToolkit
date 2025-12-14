package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;

import java.beans.PropertyChangeEvent;

/**
 * Nested Grid XML View - each element gets its own mini-grid.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>True nested grids - each element has its own grid with columns</li>
 *   <li>Child elements appear as nested grids inside parent</li>
 *   <li>Virtual scrolling - only visible grids are rendered</li>
 *   <li>Expand/collapse individual grids</li>
 *   <li>Inline editing via double-click</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlCanvasView extends Pane {

    // ==================== Context ====================

    private final XmlEditorContext context;

    // ==================== Canvas ====================

    private final Canvas canvas;
    private final GraphicsContext gc;
    private final Pane canvasContainer;
    private final ScrollBar vScrollBar;
    private final ScrollBar hScrollBar;

    // ==================== Grid Data ====================

    private NestedGridNode rootNode;

    // ==================== Layout Constants ====================

    private static final double HEADER_HEIGHT = 28;
    private static final double ROW_HEIGHT = 24;
    private static final double CHILDREN_HEADER_HEIGHT = 20;
    private static final double INDENT = 20;
    private static final double CHILD_SPACING = 8;
    private static final double GRID_PADDING = 8;
    private static final double MIN_GRID_WIDTH = 200;
    private static final double ATTR_NAME_WIDTH = 120;
    private static final double SCROLLBAR_WIDTH = 14;

    // ==================== Scroll State ====================

    private double scrollOffsetX = 0;
    private double scrollOffsetY = 0;
    private double totalHeight = 0;
    private double totalWidth = 0;

    // ==================== State ====================

    private NestedGridNode selectedNode = null;
    private NestedGridNode hoveredNode = null;
    private int hoveredAttributeIndex = -1;
    private boolean hoveredTextContent = false;

    // Table state
    private RepeatingElementsTable selectedTable = null;
    private RepeatingElementsTable hoveredTable = null;
    private int hoveredTableRowIndex = -1;
    private int hoveredTableColumnIndex = -1;

    // ==================== Layout State ====================

    private boolean layoutDirty = true;

    // ==================== Inline Editing ====================

    private TextField editField = null;
    private NestedGridNode editingNode = null;
    private int editingAttributeIndex = -1;
    private boolean editingTextContent = false;

    // ==================== Colors ====================

    // Grid frame
    private static final Color GRID_BORDER = Color.rgb(209, 213, 219);
    private static final Color GRID_HEADER_BG = Color.rgb(243, 244, 246);
    private static final Color GRID_HEADER_TEXT = Color.rgb(55, 65, 81);

    // Depth-based colors (alternating)
    private static final Color[] DEPTH_COLORS = {
        Color.rgb(255, 255, 255),      // Depth 0: White
        Color.rgb(249, 250, 251),      // Depth 1: Very light gray
        Color.rgb(243, 244, 246),      // Depth 2: Light gray
        Color.rgb(237, 238, 240),      // Depth 3: Gray
    };

    // Selection/Hover
    private static final Color SELECTED_BORDER = Color.rgb(59, 130, 246);
    private static final Color SELECTED_BG = Color.rgb(239, 246, 255);
    private static final Color HOVERED_BG = Color.rgb(243, 244, 246);
    private static final Color HOVERED_ROW = Color.rgb(229, 231, 235);

    // Text colors
    private static final Color TEXT_ELEMENT = Color.rgb(37, 99, 235);
    private static final Color TEXT_ATTRIBUTE_NAME = Color.rgb(146, 64, 14);
    private static final Color TEXT_ATTRIBUTE_VALUE = Color.rgb(21, 128, 61);
    private static final Color TEXT_CONTENT = Color.rgb(31, 41, 55);
    private static final Color TEXT_SECONDARY = Color.rgb(107, 114, 128);
    private static final Color TEXT_COMMENT = Color.rgb(13, 148, 136);
    private static final Color TEXT_CDATA = Color.rgb(107, 114, 128);
    private static final Color TEXT_PI = Color.rgb(124, 58, 237);

    // Row separator
    private static final Color ROW_SEPARATOR = Color.rgb(229, 231, 235);

    // Children header
    private static final Color CHILDREN_HEADER_BG = Color.rgb(249, 250, 251);
    private static final Color CHILDREN_HEADER_TEXT = Color.rgb(107, 114, 128);

    // Table colors
    private static final Color TABLE_HEADER_BG = Color.rgb(236, 253, 245);
    private static final Color TABLE_HEADER_TEXT = Color.rgb(5, 150, 105);
    private static final Color TABLE_BORDER = Color.rgb(167, 243, 208);
    private static final Color TABLE_ROW_EVEN = Color.WHITE;
    private static final Color TABLE_ROW_ODD = Color.rgb(249, 250, 251);
    private static final Color TABLE_ROW_HOVER = Color.rgb(236, 253, 245);
    private static final Color TABLE_ROW_SELECTED = Color.rgb(209, 250, 229);

    // ==================== Fonts ====================

    private static final Font HEADER_FONT = Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12);
    private static final Font ROW_FONT = Font.font("Segoe UI", FontWeight.NORMAL, 12);
    private static final Font ROW_FONT_BOLD = Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12);
    private static final Font SMALL_FONT = Font.font("Segoe UI", FontWeight.NORMAL, 10);

    // ==================== Constructor ====================

    public XmlCanvasView(XmlEditorContext context) {
        this.context = context;

        // Create canvas
        this.canvas = new Canvas(800, 600);
        this.gc = canvas.getGraphicsContext2D();

        // Create container
        this.canvasContainer = new Pane();
        canvasContainer.getChildren().add(canvas);

        // Create scroll bars
        this.vScrollBar = new ScrollBar();
        vScrollBar.setOrientation(Orientation.VERTICAL);
        vScrollBar.setMinWidth(SCROLLBAR_WIDTH);
        vScrollBar.setPrefWidth(SCROLLBAR_WIDTH);

        this.hScrollBar = new ScrollBar();
        hScrollBar.setOrientation(Orientation.HORIZONTAL);
        hScrollBar.setMinHeight(SCROLLBAR_WIDTH);
        hScrollBar.setPrefHeight(SCROLLBAR_WIDTH);

        // Layout
        getChildren().addAll(canvasContainer, vScrollBar, hScrollBar);

        // Resize listener
        widthProperty().addListener((obs, oldVal, newVal) -> onResize());
        heightProperty().addListener((obs, oldVal, newVal) -> onResize());

        // Scroll bar listeners
        vScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            scrollOffsetY = newVal.doubleValue();
            render();
        });

        hScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            scrollOffsetX = newVal.doubleValue();
            render();
        });

        // Event handlers
        setupEventHandlers();

        // Listen for document changes
        context.addPropertyChangeListener("document", this::onDocumentChanged);

        // Initial render
        rebuildTree();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0) return;

        // Position canvas
        double canvasW = w - SCROLLBAR_WIDTH;
        double canvasH = h - SCROLLBAR_WIDTH;

        canvas.setWidth(canvasW);
        canvas.setHeight(canvasH);
        canvas.setLayoutX(0);
        canvas.setLayoutY(0);

        canvasContainer.setPrefWidth(canvasW);
        canvasContainer.setPrefHeight(canvasH);

        // Position vertical scrollbar
        vScrollBar.setLayoutX(w - SCROLLBAR_WIDTH);
        vScrollBar.setLayoutY(0);
        vScrollBar.setPrefHeight(h - SCROLLBAR_WIDTH);

        // Position horizontal scrollbar
        hScrollBar.setLayoutX(0);
        hScrollBar.setLayoutY(h - SCROLLBAR_WIDTH);
        hScrollBar.setPrefWidth(w - SCROLLBAR_WIDTH);

        // Recalculate layout and update scroll bars
        ensureLayout();
        updateScrollBars();

        render();
    }

    private void onResize() {
        layoutDirty = true;
        ensureLayout();
        updateScrollBars();
        render();
    }

    private void ensureLayout() {
        if (!layoutDirty || rootNode == null) return;

        // Calculate available width
        double availableWidth = canvas.getWidth() - GRID_PADDING * 2;

        // Bottom-up size calculation
        calculateSizes(rootNode, availableWidth);

        // Top-down position calculation
        positionNodes(rootNode, GRID_PADDING, GRID_PADDING);

        // Store total size
        totalHeight = rootNode.getHeight() + GRID_PADDING * 2;
        totalWidth = rootNode.getWidth() + GRID_PADDING * 2;

        layoutDirty = false;
    }

    private void calculateSizes(NestedGridNode node, double availableWidth) {
        // Calculate children first (bottom-up)
        if (node.isExpanded()) {
            for (NestedGridNode child : node.getChildren()) {
                calculateSizes(child, availableWidth - INDENT);
            }
        }

        // Calculate this node's height
        node.calculateHeight();

        // Calculate width
        node.calculateWidth(availableWidth);
    }

    private static final double COMPACT_CHILD_SPACING = 4;  // Reduced spacing for compact leaf nodes

    private void positionNodes(NestedGridNode node, double x, double y) {
        node.setX(x);
        node.setY(y);

        if (node.isExpanded() && node.hasChildren()) {
            double childY = node.getChildrenStartY();

            // Position repeating element tables first
            for (RepeatingElementsTable table : node.getRepeatingTables()) {
                table.setX(x + INDENT);
                table.setY(childY);
                childY += table.getHeight() + CHILD_SPACING;
            }

            // Position individual children
            for (NestedGridNode child : node.getChildren()) {
                positionNodes(child, x + INDENT, childY);
                // Use reduced spacing for compact leaf nodes
                boolean isCompactLeaf = child.isLeafWithText() && child.getAttributeCells().isEmpty();
                childY += child.getHeight() + (isCompactLeaf ? COMPACT_CHILD_SPACING : CHILD_SPACING);
            }
        }
    }

    private void updateScrollBars() {
        double viewportWidth = canvas.getWidth();
        double viewportHeight = canvas.getHeight();

        // Vertical scroll bar
        if (totalHeight <= viewportHeight) {
            vScrollBar.setDisable(true);
            vScrollBar.setValue(0);
            scrollOffsetY = 0;
        } else {
            vScrollBar.setDisable(false);
            vScrollBar.setMin(0);
            vScrollBar.setMax(totalHeight - viewportHeight);
            vScrollBar.setVisibleAmount(viewportHeight);
            vScrollBar.setBlockIncrement(viewportHeight * 0.9);
            vScrollBar.setUnitIncrement(ROW_HEIGHT);
        }

        // Horizontal scroll bar
        if (totalWidth <= viewportWidth) {
            hScrollBar.setDisable(true);
            hScrollBar.setValue(0);
            scrollOffsetX = 0;
        } else {
            hScrollBar.setDisable(false);
            hScrollBar.setMin(0);
            hScrollBar.setMax(totalWidth - viewportWidth);
            hScrollBar.setVisibleAmount(viewportWidth);
            hScrollBar.setBlockIncrement(100);
            hScrollBar.setUnitIncrement(20);
        }
    }

    // ==================== Event Handlers ====================

    private void setupEventHandlers() {
        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleMouseClick);
        canvas.addEventHandler(MouseEvent.MOUSE_MOVED, this::handleMouseMove);
        canvas.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (hoveredNode != null) {
                hoveredNode.setHovered(false);
                hoveredNode = null;
                hoveredAttributeIndex = -1;
                hoveredTextContent = false;
                render();
            }
        });

        // Mouse wheel scrolling
        canvas.addEventHandler(ScrollEvent.SCROLL, e -> {
            if (totalHeight <= canvas.getHeight()) return;

            double delta = -e.getDeltaY();
            double newOffset = Math.max(0, Math.min(scrollOffsetY + delta, totalHeight - canvas.getHeight()));

            if (newOffset != scrollOffsetY) {
                scrollOffsetY = newOffset;
                vScrollBar.setValue(scrollOffsetY);
                render();
            }
            e.consume();
        });
    }

    private void handleMouseClick(MouseEvent event) {
        double mx = event.getX() + scrollOffsetX;
        double my = event.getY() + scrollOffsetY;

        if (rootNode == null) return;

        // First check if clicking on a table
        RepeatingElementsTable hitTable = findTableAt(rootNode, mx, my);
        if (hitTable != null) {
            handleTableClick(hitTable, mx, my, event.getClickCount());
            return;
        }

        // Find clicked node
        NestedGridNode hitNode = findNodeAt(rootNode, mx, my);

        if (hitNode == null) {
            selectNode(null);
            selectTable(null);
            return;
        }

        // Clear table selection
        selectTable(null);

        // Check for header click (expand/collapse)
        if (hitNode.isHeaderHit(mx, my)) {
            if (hitNode.hasChildren()) {
                hitNode.toggleExpanded();
                layoutDirty = true;
                ensureLayout();
                updateScrollBars();
                render();
            }
            selectNode(hitNode);
            return;
        }

        // Double-click for editing
        if (event.getClickCount() == 2) {
            int attrIndex = hitNode.getAttributeIndexAt(mx, my);
            boolean textHit = hitNode.isTextContentHit(mx, my);

            if (attrIndex >= 0) {
                startEditingAttribute(hitNode, attrIndex);
            } else if (textHit) {
                startEditingTextContent(hitNode);
            }
            return;
        }

        // Single click for selection
        selectNode(hitNode);
    }

    private void handleTableClick(RepeatingElementsTable table, double mx, double my, int clickCount) {
        // Check for header click (expand/collapse)
        if (table.isHeaderHit(mx, my)) {
            table.toggleExpanded();
            layoutDirty = true;
            ensureLayout();
            updateScrollBars();
            render();
            selectTable(table);
            return;
        }

        // Row click
        int rowIndex = table.getRowIndexAt(my);
        if (rowIndex >= 0) {
            table.setSelectedRowIndex(rowIndex);
            selectTable(table);
            selectNode(null);
            render();
        }
    }

    private void handleMouseMove(MouseEvent event) {
        double mx = event.getX() + scrollOffsetX;
        double my = event.getY() + scrollOffsetY;

        if (rootNode == null) return;

        boolean needsRedraw = false;

        // Check for table hover first
        RepeatingElementsTable hitTable = findTableAt(rootNode, mx, my);
        if (hitTable != null) {
            // Clear node hover
            if (hoveredNode != null) {
                hoveredNode.setHovered(false);
                hoveredNode = null;
                needsRedraw = true;
            }

            // Update table hover
            if (hitTable != hoveredTable) {
                if (hoveredTable != null) {
                    hoveredTable.setHovered(false);
                }
                hitTable.setHovered(true);
                hoveredTable = hitTable;
                needsRedraw = true;
            }

            // Track hovered row/column
            int newRowIndex = hitTable.getRowIndexAt(my);
            int newColIndex = hitTable.getColumnIndexAt(mx);

            if (newRowIndex != hoveredTableRowIndex || newColIndex != hoveredTableColumnIndex) {
                hoveredTableRowIndex = newRowIndex;
                hoveredTableColumnIndex = newColIndex;
                hitTable.setHoveredRowIndex(newRowIndex);
                hitTable.setHoveredColumnIndex(newColIndex);
                needsRedraw = true;
            }

            // Cursor
            if (hitTable.isHeaderHit(mx, my)) {
                canvas.setCursor(javafx.scene.Cursor.HAND);
            } else {
                canvas.setCursor(javafx.scene.Cursor.DEFAULT);
            }

            if (needsRedraw) {
                render();
            }
            return;
        }

        // Clear table hover
        if (hoveredTable != null) {
            hoveredTable.setHovered(false);
            hoveredTable = null;
            hoveredTableRowIndex = -1;
            hoveredTableColumnIndex = -1;
            needsRedraw = true;
        }

        // Find hovered node
        NestedGridNode hitNode = findNodeAt(rootNode, mx, my);

        if (hitNode != hoveredNode) {
            if (hoveredNode != null) {
                hoveredNode.setHovered(false);
            }
            if (hitNode != null) {
                hitNode.setHovered(true);
            }
            hoveredNode = hitNode;
            needsRedraw = true;
        }

        // Track hovered attribute/text
        if (hitNode != null) {
            int newAttrIndex = hitNode.getAttributeIndexAt(mx, my);
            boolean newTextHover = hitNode.isTextContentHit(mx, my);

            if (newAttrIndex != hoveredAttributeIndex || newTextHover != hoveredTextContent) {
                hoveredAttributeIndex = newAttrIndex;
                hoveredTextContent = newTextHover;
                needsRedraw = true;
            }

            // Check if header is hovered
            boolean headerHovered = hitNode.isHeaderHit(mx, my);
            if (headerHovered != hitNode.isHeaderHovered()) {
                hitNode.setHeaderHovered(headerHovered);
                needsRedraw = true;
            }

            // Update cursor
            if (headerHovered && hitNode.hasChildren()) {
                canvas.setCursor(javafx.scene.Cursor.HAND);
            } else {
                canvas.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        } else {
            hoveredAttributeIndex = -1;
            hoveredTextContent = false;
            canvas.setCursor(javafx.scene.Cursor.DEFAULT);
        }

        if (needsRedraw) {
            render();
        }
    }

    private NestedGridNode findNodeAt(NestedGridNode node, double mx, double my) {
        if (!node.containsPoint(mx, my)) return null;

        // Check children first (they are rendered on top)
        if (node.isExpanded()) {
            for (NestedGridNode child : node.getChildren()) {
                NestedGridNode found = findNodeAt(child, mx, my);
                if (found != null) return found;
            }
        }

        return node;
    }

    private RepeatingElementsTable findTableAt(NestedGridNode node, double mx, double my) {
        if (!node.containsPoint(mx, my)) return null;

        // Check tables in this node
        if (node.isExpanded()) {
            for (RepeatingElementsTable table : node.getRepeatingTables()) {
                if (table.containsPoint(mx, my)) {
                    return table;
                }
            }

            // Check tables in children
            for (NestedGridNode child : node.getChildren()) {
                RepeatingElementsTable found = findTableAt(child, mx, my);
                if (found != null) return found;
            }
        }

        return null;
    }

    // ==================== Selection ====================

    private void selectNode(NestedGridNode node) {
        if (selectedNode != null) {
            selectedNode.setSelected(false);
        }

        selectedNode = node;

        if (selectedNode != null) {
            selectedNode.setSelected(true);
        }

        render();
    }

    private void selectTable(RepeatingElementsTable table) {
        if (selectedTable != null) {
            selectedTable.setSelected(false);
        }

        selectedTable = table;

        if (selectedTable != null) {
            selectedTable.setSelected(true);
        }

        render();
    }

    // ==================== Tree Building ====================

    private void rebuildTree() {
        if (rootNode != null) {
            rootNode.dispose();
        }

        XmlDocument doc = context.getDocument();
        if (doc == null) {
            rootNode = null;
            totalHeight = 0;
            totalWidth = 0;
            updateScrollBars();
            render();
            return;
        }

        rootNode = NestedGridNode.buildTree(doc);
        layoutDirty = true;
        ensureLayout();
        updateScrollBars();
        render();
    }

    // ==================== Rendering ====================

    public void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        if (w <= 0 || h <= 0) return;

        // Clear canvas
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);

        if (rootNode == null) {
            drawEmptyState();
            return;
        }

        // Viewport boundaries
        double viewportTop = scrollOffsetY;
        double viewportBottom = scrollOffsetY + h;

        // Render visible grids
        gc.save();
        gc.translate(-scrollOffsetX, -scrollOffsetY);
        renderVisible(rootNode, viewportTop, viewportBottom);
        gc.restore();

        // Draw info
        drawInfo();
    }

    private void renderVisible(NestedGridNode node, double viewportTop, double viewportBottom) {
        if (!node.isVisible(viewportTop, viewportBottom)) {
            return; // Skip invisible grids
        }

        renderGrid(node);

        if (node.isExpanded()) {
            // Render repeating element tables
            for (RepeatingElementsTable table : node.getRepeatingTables()) {
                if (table.isVisible(viewportTop, viewportBottom)) {
                    renderTable(table);
                }
            }

            // Render individual children
            for (NestedGridNode child : node.getChildren()) {
                renderVisible(child, viewportTop, viewportBottom);
            }
        }
    }

    private void renderGrid(NestedGridNode node) {
        double x = node.getX();
        double y = node.getY();
        double w = node.getWidth();
        double h = node.getHeight();

        // Compact rendering for leaf elements with only text (no attributes)
        boolean isCompactLeaf = node.isLeafWithText() && node.getAttributeCells().isEmpty();

        // Background based on depth
        Color bgColor = DEPTH_COLORS[node.getDepth() % DEPTH_COLORS.length];
        if (node.isSelected()) {
            bgColor = SELECTED_BG;
        } else if (node.isHovered()) {
            bgColor = HOVERED_BG;
        }

        gc.setFill(bgColor);
        gc.fillRoundRect(x, y, w, h, 6, 6);

        // Border
        gc.setStroke(node.isSelected() ? SELECTED_BORDER : GRID_BORDER);
        gc.setLineWidth(node.isSelected() ? 2 : 1);
        gc.strokeRoundRect(x, y, w, h, 6, 6);

        // Header
        drawGridHeader(node, x, y, w, isCompactLeaf);

        // For compact leaf nodes, we're done - no more content below header
        if (isCompactLeaf) {
            return;
        }

        // Attribute rows
        double rowY = y + HEADER_HEIGHT;
        for (int i = 0; i < node.getAttributeCells().size(); i++) {
            drawAttributeRow(node, i, x, rowY, w);
            rowY += ROW_HEIGHT;
        }

        // Text content - only show as separate row if NOT a leaf with text
        // (leaf text is shown directly in header)
        if (node.hasTextContent() && !node.isLeafWithText()) {
            drawTextContentRow(node, x, rowY, w);
            rowY += ROW_HEIGHT;
        }

        // Children header
        if (node.hasChildren()) {
            drawChildrenHeader(node, x, rowY, w);
        }
    }

    private void drawGridHeader(NestedGridNode node, double x, double y, double w, boolean isCompactLeaf) {
        // Header background - for compact leaf, fill entire rounded rect
        gc.setFill(GRID_HEADER_BG);
        if (isCompactLeaf) {
            // No bottom line for compact leaves - the header IS the entire node
            gc.fillRoundRect(x + 1, y + 1, w - 2, HEADER_HEIGHT - 2, 5, 5);
        } else {
            gc.fillRoundRect(x + 1, y + 1, w - 2, HEADER_HEIGHT - 1, 5, 5);
            // Header bottom line (only for non-compact nodes)
            gc.setStroke(GRID_BORDER);
            gc.setLineWidth(1);
            gc.strokeLine(x, y + HEADER_HEIGHT, x + w, y + HEADER_HEIGHT);
        }

        // Expand/collapse indicator (not for compact leaves)
        double iconX = x + GRID_PADDING;
        double iconY = y + HEADER_HEIGHT / 2;

        if (node.hasChildren() && !isCompactLeaf) {
            gc.setStroke(TEXT_SECONDARY);
            gc.setLineWidth(1.5);

            double size = 4;
            if (node.isExpanded()) {
                // Down arrow (expanded)
                gc.strokeLine(iconX, iconY - size / 2, iconX + size, iconY + size / 2);
                gc.strokeLine(iconX + size, iconY + size / 2, iconX + size * 2, iconY - size / 2);
            } else {
                // Right arrow (collapsed)
                gc.strokeLine(iconX, iconY - size, iconX + size, iconY);
                gc.strokeLine(iconX + size, iconY, iconX, iconY + size);
            }
            iconX += size * 2 + 8;
        }

        // Element icon
        drawElementIcon(node.getNodeType(), iconX, y + (HEADER_HEIGHT - 14) / 2);
        iconX += 18;

        // Element name
        gc.setFont(HEADER_FONT);
        gc.setFill(getElementColor(node.getNodeType()));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);

        // For leaf elements with text, show: elementName = "text content"
        if (node.isLeafWithText()) {
            String displayName = node.getElementName();
            gc.fillText(displayName, iconX, y + HEADER_HEIGHT / 2);

            // Calculate position after element name
            double nameWidth = displayName.length() * 7 + 4;
            double textX = iconX + nameWidth;

            // Draw equals sign
            gc.setFill(TEXT_SECONDARY);
            gc.fillText("=", textX, y + HEADER_HEIGHT / 2);
            textX += 12;

            // Draw text value (truncated if needed)
            gc.setFill(TEXT_CONTENT);
            gc.setFont(ROW_FONT);
            double availableWidth = w - textX + x - GRID_PADDING - 10;
            String textValue = "\"" + truncateText(node.getTextContent(), availableWidth - 20) + "\"";
            gc.fillText(textValue, textX, y + HEADER_HEIGHT / 2);
        } else {
            gc.fillText(node.getElementName(), iconX, y + HEADER_HEIGHT / 2);

            // Children count (on right side)
            if (node.hasChildren()) {
                int totalChildren = node.getChildren().size();
                // Also count elements in repeating tables
                for (RepeatingElementsTable table : node.getRepeatingTables()) {
                    totalChildren += table.getElementCount();
                }
                String childCount = "(" + totalChildren + ")";
                gc.setFont(SMALL_FONT);
                gc.setFill(TEXT_SECONDARY);
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(childCount, x + w - GRID_PADDING, y + HEADER_HEIGHT / 2);
            }
        }
    }

    private void drawAttributeRow(NestedGridNode node, int index, double x, double y, double w) {
        NestedGridNode.AttributeCell cell = node.getAttributeCells().get(index);

        // Row hover highlight
        if (node.isHovered() && hoveredAttributeIndex == index) {
            gc.setFill(HOVERED_ROW);
            gc.fillRect(x + 1, y, w - 2, ROW_HEIGHT);
        }

        // Row separator
        gc.setStroke(ROW_SEPARATOR);
        gc.setLineWidth(0.5);
        gc.strokeLine(x + GRID_PADDING, y + ROW_HEIGHT, x + w - GRID_PADDING, y + ROW_HEIGHT);

        // Attribute name (@name)
        gc.setFont(ROW_FONT);
        gc.setFill(TEXT_ATTRIBUTE_NAME);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("@" + cell.getName(), x + GRID_PADDING, y + ROW_HEIGHT / 2);

        // Attribute value
        gc.setFill(TEXT_ATTRIBUTE_VALUE);
        gc.fillText(truncateText(cell.getValue(), w - ATTR_NAME_WIDTH - GRID_PADDING * 2),
                    x + ATTR_NAME_WIDTH, y + ROW_HEIGHT / 2);
    }

    private void drawTextContentRow(NestedGridNode node, double x, double y, double w) {
        // Row hover highlight
        if (node.isHovered() && hoveredTextContent) {
            gc.setFill(HOVERED_ROW);
            gc.fillRect(x + 1, y, w - 2, ROW_HEIGHT);
        }

        // Row separator
        gc.setStroke(ROW_SEPARATOR);
        gc.setLineWidth(0.5);
        gc.strokeLine(x + GRID_PADDING, y + ROW_HEIGHT, x + w - GRID_PADDING, y + ROW_HEIGHT);

        // Text label
        gc.setFont(ROW_FONT);
        gc.setFill(TEXT_SECONDARY);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("#text", x + GRID_PADDING, y + ROW_HEIGHT / 2);

        // Text content
        gc.setFill(TEXT_CONTENT);
        gc.fillText(truncateText(node.getTextContent(), w - ATTR_NAME_WIDTH - GRID_PADDING * 2),
                    x + ATTR_NAME_WIDTH, y + ROW_HEIGHT / 2);
    }

    private void drawChildrenHeader(NestedGridNode node, double x, double y, double w) {
        // Background
        gc.setFill(CHILDREN_HEADER_BG);
        gc.fillRect(x + 1, y, w - 2, CHILDREN_HEADER_HEIGHT);

        // Text
        gc.setFont(SMALL_FONT);
        gc.setFill(CHILDREN_HEADER_TEXT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);

        String text = node.isExpanded() ? "Children:" : "Children: (collapsed)";
        gc.fillText(text, x + GRID_PADDING, y + CHILDREN_HEADER_HEIGHT / 2);
    }

    private void drawElementIcon(NestedGridNode.NodeType type, double x, double y) {
        gc.setLineWidth(1.5);
        gc.setStroke(getElementColor(type));
        gc.setFill(getElementColor(type));

        double cx = x + 7;
        double cy = y + 7;
        double size = 4;

        switch (type) {
            case ELEMENT:
                // < > brackets
                gc.strokeLine(cx - size, cy, cx - size / 2, cy - size);
                gc.strokeLine(cx - size, cy, cx - size / 2, cy + size);
                gc.strokeLine(cx + size, cy, cx + size / 2, cy - size);
                gc.strokeLine(cx + size, cy, cx + size / 2, cy + size);
                break;

            case TEXT:
                // T icon
                gc.setLineWidth(2);
                gc.strokeLine(cx - size, cy - size, cx + size, cy - size);
                gc.strokeLine(cx, cy - size, cx, cy + size);
                gc.setLineWidth(1.5);
                break;

            case COMMENT:
                // Speech bubble
                gc.strokeRoundRect(cx - size, cy - size * 0.6, size * 2, size * 1.2, 3, 3);
                break;

            case CDATA:
                // Square brackets
                gc.strokeLine(cx - size, cy - size, cx - size + 2, cy - size);
                gc.strokeLine(cx - size, cy - size, cx - size, cy + size);
                gc.strokeLine(cx - size, cy + size, cx - size + 2, cy + size);
                gc.strokeLine(cx + size, cy - size, cx + size - 2, cy - size);
                gc.strokeLine(cx + size, cy - size, cx + size, cy + size);
                gc.strokeLine(cx + size, cy + size, cx + size - 2, cy + size);
                break;

            case PROCESSING_INSTRUCTION:
                // Gear/circle
                gc.strokeOval(cx - size / 2, cy - size / 2, size, size);
                break;

            case DOCUMENT:
                // Document icon
                gc.strokeRect(cx - size * 0.7, cy - size, size * 1.4, size * 2);
                gc.strokeLine(cx - size * 0.3, cy - size * 0.4, cx + size * 0.3, cy - size * 0.4);
                gc.strokeLine(cx - size * 0.3, cy, cx + size * 0.3, cy);
                break;

            default:
                gc.strokeOval(cx - size / 2, cy - size / 2, size, size);
        }
    }

    private void drawEmptyState() {
        gc.setFill(TEXT_SECONDARY);
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("No XML document loaded", canvas.getWidth() / 2, canvas.getHeight() / 2);
    }

    private void drawInfo() {
        if (rootNode == null) return;

        String info = String.format("%.0f x %.0f px", totalWidth, totalHeight);

        gc.setFill(TEXT_SECONDARY);
        gc.setFont(SMALL_FONT);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.fillText(info, canvas.getWidth() - 5, canvas.getHeight() - 3);
    }

    // ==================== Table Rendering ====================

    private void renderTable(RepeatingElementsTable table) {
        double x = table.getX();
        double y = table.getY();
        double w = table.getWidth();
        double h = table.getHeight();

        // Background
        Color bgColor = (table == selectedTable) ? SELECTED_BG : Color.WHITE;
        gc.setFill(bgColor);
        gc.fillRoundRect(x, y, w, h, 6, 6);

        // Border
        gc.setStroke((table == selectedTable) ? SELECTED_BORDER : TABLE_BORDER);
        gc.setLineWidth((table == selectedTable) ? 2 : 1);
        gc.strokeRoundRect(x, y, w, h, 6, 6);

        // Table header with element name and count
        drawTableHeader(table, x, y, w);

        if (!table.isExpanded()) return;

        // Column headers
        double rowY = y + HEADER_HEIGHT;
        drawTableColumnHeaders(table, x, rowY, w);
        rowY += ROW_HEIGHT;

        // Data rows
        for (int i = 0; i < table.getRows().size(); i++) {
            drawTableDataRow(table, i, x, rowY, w);
            rowY += ROW_HEIGHT;
        }
    }

    private void drawTableHeader(RepeatingElementsTable table, double x, double y, double w) {
        // Header background (green tint for tables)
        gc.setFill(TABLE_HEADER_BG);
        gc.fillRoundRect(x + 1, y + 1, w - 2, HEADER_HEIGHT - 1, 5, 5);

        // Header bottom line
        gc.setStroke(TABLE_BORDER);
        gc.setLineWidth(1);
        gc.strokeLine(x, y + HEADER_HEIGHT, x + w, y + HEADER_HEIGHT);

        // Expand/collapse indicator
        double iconX = x + GRID_PADDING;
        double iconY = y + HEADER_HEIGHT / 2;

        gc.setStroke(TABLE_HEADER_TEXT);
        gc.setLineWidth(1.5);

        double size = 4;
        if (table.isExpanded()) {
            // Down arrow (expanded)
            gc.strokeLine(iconX, iconY - size / 2, iconX + size, iconY + size / 2);
            gc.strokeLine(iconX + size, iconY + size / 2, iconX + size * 2, iconY - size / 2);
        } else {
            // Right arrow (collapsed)
            gc.strokeLine(iconX, iconY - size, iconX + size, iconY);
            gc.strokeLine(iconX + size, iconY, iconX, iconY + size);
        }
        iconX += size * 2 + 8;

        // Table icon (grid symbol)
        drawTableIcon(iconX, y + (HEADER_HEIGHT - 14) / 2);
        iconX += 18;

        // Element name with count
        gc.setFont(HEADER_FONT);
        gc.setFill(TABLE_HEADER_TEXT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(table.getElementName() + " (" + table.getElementCount() + "×)", iconX, y + HEADER_HEIGHT / 2);
    }

    private void drawTableIcon(double x, double y) {
        gc.setStroke(TABLE_HEADER_TEXT);
        gc.setLineWidth(1);

        // 3x3 grid
        double size = 12;
        double cellSize = size / 3;

        gc.strokeRect(x, y, size, size);
        gc.strokeLine(x + cellSize, y, x + cellSize, y + size);
        gc.strokeLine(x + cellSize * 2, y, x + cellSize * 2, y + size);
        gc.strokeLine(x, y + cellSize, x + size, y + cellSize);
        gc.strokeLine(x, y + cellSize * 2, x + size, y + cellSize * 2);
    }

    private void drawTableColumnHeaders(RepeatingElementsTable table, double x, double y, double w) {
        // Background
        gc.setFill(Color.rgb(249, 250, 251));
        gc.fillRect(x + 1, y, w - 2, ROW_HEIGHT);

        // Bottom border
        gc.setStroke(TABLE_BORDER);
        gc.setLineWidth(1);
        gc.strokeLine(x, y + ROW_HEIGHT, x + w, y + ROW_HEIGHT);

        // Column headers
        gc.setFont(ROW_FONT_BOLD);
        gc.setFill(TEXT_SECONDARY);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);

        double colX = x + GRID_PADDING;
        for (RepeatingElementsTable.TableColumn col : table.getColumns()) {
            gc.fillText(truncateText(col.getDisplayName(), col.getWidth() - GRID_PADDING * 2),
                        colX, y + ROW_HEIGHT / 2);

            // Column separator
            colX += col.getWidth();
            gc.setStroke(ROW_SEPARATOR);
            gc.setLineWidth(0.5);
            gc.strokeLine(colX, y, colX, y + ROW_HEIGHT);
        }
    }

    private void drawTableDataRow(RepeatingElementsTable table, int rowIndex, double x, double y, double w) {
        RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);

        // Row background
        Color rowBg;
        if (table == selectedTable && table.getSelectedRowIndex() == rowIndex) {
            rowBg = TABLE_ROW_SELECTED;
        } else if (table == hoveredTable && hoveredTableRowIndex == rowIndex) {
            rowBg = TABLE_ROW_HOVER;
        } else {
            rowBg = (rowIndex % 2 == 0) ? TABLE_ROW_EVEN : TABLE_ROW_ODD;
        }

        gc.setFill(rowBg);
        gc.fillRect(x + 1, y, w - 2, ROW_HEIGHT);

        // Bottom border
        gc.setStroke(ROW_SEPARATOR);
        gc.setLineWidth(0.5);
        gc.strokeLine(x + GRID_PADDING, y + ROW_HEIGHT, x + w - GRID_PADDING, y + ROW_HEIGHT);

        // Cell values
        gc.setFont(ROW_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);

        double colX = x + GRID_PADDING;
        for (RepeatingElementsTable.TableColumn col : table.getColumns()) {
            String value = row.getValue(col.getName());

            // Color based on column type
            if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
                gc.setFill(TEXT_ATTRIBUTE_VALUE);
            } else {
                gc.setFill(TEXT_CONTENT);
            }

            gc.fillText(truncateText(value, col.getWidth() - GRID_PADDING * 2),
                        colX, y + ROW_HEIGHT / 2);

            // Column separator
            colX += col.getWidth();
            gc.setStroke(ROW_SEPARATOR);
            gc.setLineWidth(0.5);
            gc.strokeLine(colX, y, colX, y + ROW_HEIGHT);
        }
    }

    // ==================== Helper Methods ====================

    private Color getElementColor(NestedGridNode.NodeType type) {
        switch (type) {
            case ELEMENT: return TEXT_ELEMENT;
            case TEXT: return TEXT_CONTENT;
            case COMMENT: return TEXT_COMMENT;
            case CDATA: return TEXT_CDATA;
            case PROCESSING_INSTRUCTION: return TEXT_PI;
            default: return TEXT_SECONDARY;
        }
    }

    private String truncateText(String text, double maxWidth) {
        if (text == null) return "";

        int maxChars = (int) (maxWidth / 7);
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    // ==================== Inline Editing ====================

    private void startEditingAttribute(NestedGridNode node, int attrIndex) {
        cancelEditing();

        if (attrIndex < 0 || attrIndex >= node.getAttributeCells().size()) return;

        editingNode = node;
        editingAttributeIndex = attrIndex;
        editingTextContent = false;

        NestedGridNode.AttributeCell cell = node.getAttributeCells().get(attrIndex);
        String currentValue = cell.getValue();

        double x = node.getX() + ATTR_NAME_WIDTH - scrollOffsetX;
        double y = node.getY() + HEADER_HEIGHT + attrIndex * ROW_HEIGHT + 2 - scrollOffsetY;
        double width = node.getWidth() - ATTR_NAME_WIDTH - GRID_PADDING;

        createEditField(currentValue, x, y, width);
    }

    private void startEditingTextContent(NestedGridNode node) {
        cancelEditing();

        if (!node.hasTextContent()) return;

        editingNode = node;
        editingAttributeIndex = -1;
        editingTextContent = true;

        String currentValue = node.getTextContent();

        double x = node.getX() + ATTR_NAME_WIDTH - scrollOffsetX;
        double y = node.getY() + HEADER_HEIGHT + node.getAttributeCells().size() * ROW_HEIGHT + 2 - scrollOffsetY;
        double width = node.getWidth() - ATTR_NAME_WIDTH - GRID_PADDING;

        createEditField(currentValue, x, y, width);
    }

    private void createEditField(String currentValue, double x, double y, double width) {
        editField = new TextField(currentValue);
        editField.setLayoutX(x);
        editField.setLayoutY(y);
        editField.setPrefWidth(width);
        editField.setPrefHeight(ROW_HEIGHT - 4);
        editField.setStyle("-fx-font-size: 12px; -fx-font-family: 'Segoe UI'; -fx-padding: 2 4;");

        editField.setOnAction(e -> commitEditing());
        editField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelEditing();
            }
        });
        editField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && editField != null) {
                commitEditing();
            }
        });

        canvasContainer.getChildren().add(editField);
        editField.requestFocus();
        editField.selectAll();
    }

    private void commitEditing() {
        if (editField == null || editingNode == null) {
            cancelEditing();
            return;
        }

        String newValue = editField.getText();
        XmlNode modelNode = editingNode.getModelNode();

        if (editingAttributeIndex >= 0) {
            // Editing attribute
            NestedGridNode.AttributeCell cell = editingNode.getAttributeCells().get(editingAttributeIndex);
            if (modelNode instanceof XmlElement) {
                context.executeCommand(new SetAttributeCommand((XmlElement) modelNode, cell.getName(), newValue));
            }
        } else if (editingTextContent) {
            // Editing text content
            if (modelNode instanceof XmlElement) {
                context.executeCommand(new SetElementTextCommand((XmlElement) modelNode, newValue));
            } else if (modelNode instanceof XmlText) {
                context.executeCommand(new SetTextCommand((XmlText) modelNode, newValue));
            }
        }

        cancelEditing();
        rebuildTree();
    }

    private void cancelEditing() {
        if (editField != null) {
            canvasContainer.getChildren().remove(editField);
            editField = null;
        }
        editingNode = null;
        editingAttributeIndex = -1;
        editingTextContent = false;
    }

    // ==================== Event Handlers ====================

    private void onDocumentChanged(PropertyChangeEvent evt) {
        cancelEditing();
        rebuildTree();
    }

    // ==================== Public API ====================

    public void refresh() {
        rebuildTree();
    }

    public void expandAll() {
        if (rootNode != null) {
            rootNode.expandAll();
            layoutDirty = true;
            ensureLayout();
            updateScrollBars();
            render();
        }
    }

    public void collapseAll() {
        if (rootNode != null) {
            rootNode.collapseAll();
            layoutDirty = true;
            ensureLayout();
            updateScrollBars();
            render();
        }
    }

    public XmlNode getSelectedNode() {
        return selectedNode != null ? selectedNode.getModelNode() : null;
    }

    public void setSelectedNode(XmlNode node) {
        if (rootNode != null && node != null) {
            NestedGridNode found = rootNode.findByModel(node);
            if (found != null) {
                selectNode(found);

                // Scroll to selected node if not visible
                if (!found.isVisible(scrollOffsetY, scrollOffsetY + canvas.getHeight())) {
                    scrollOffsetY = Math.max(0, found.getY() - canvas.getHeight() / 2);
                    vScrollBar.setValue(scrollOffsetY);
                    render();
                }
            }
        }
    }
}
