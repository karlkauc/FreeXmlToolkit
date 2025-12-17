package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

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
    private static final double CHILDREN_HEADER_HEIGHT = 0;  // No children header - saves screen space
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
    private boolean editingElementName = false;

    // Table cell editing state
    private RepeatingElementsTable editingTable = null;
    private int editingTableRowIndex = -1;
    private String editingTableColumnName = null;

    // ==================== Context Menu ====================

    private final XmlGridContextMenu contextMenu;

    // ==================== Highlight State ====================

    private NestedGridNode highlightedNode = null;
    private static final Color HIGHLIGHT_COLOR = Color.rgb(254, 240, 138);  // Yellow highlight

    // ==================== Status Bar & Toast ====================

    private XmlStatusBar statusBar;
    private StackPane toastContainer;

    // ==================== Document Change Callback ====================

    private java.util.function.Consumer<String> onDocumentModified;

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

        // Create context menu
        contextMenu = new XmlGridContextMenu(context, this::refresh);

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

    /**
     * Calculates sizes recursively for a standalone subtree (used for expanded table cells).
     */
    private void calculateSizesRecursively(NestedGridNode node, double availableWidth) {
        calculateSizes(node, availableWidth);
    }

    /**
     * Positions nodes recursively for a standalone subtree (used for expanded table cells).
     */
    private void positionNodesRecursively(NestedGridNode node, double x, double y) {
        positionNodes(node, x, y);
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

        // Keyboard shortcuts
        canvas.setFocusTraversable(true);
        canvas.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyPress);

        // Request focus when clicked
        canvas.setOnMousePressed(e -> {
            if (!canvas.isFocused()) {
                canvas.requestFocus();
            }
        });
    }

    private void handleKeyPress(KeyEvent event) {
        // If editing, don't handle navigation keys
        if (editField != null) return;

        XmlNode selected = getSelectedNode();

        // Context menu shortcuts (Delete, F2, Ctrl+C/X/V/D)
        if (selected != null && contextMenu != null) {
            contextMenu.handleKeyPress(event, selected);
        }

        // Keyboard navigation
        handleKeyNavigation(event);
    }

    /**
     * Handle keyboard navigation between elements.
     */
    private void handleKeyNavigation(KeyEvent event) {
        if (rootNode == null) return;

        switch (event.getCode()) {
            case UP -> {
                selectPreviousSibling();
                event.consume();
            }
            case DOWN -> {
                selectNextSibling();
                event.consume();
            }
            case LEFT -> {
                if (selectedNode != null && selectedNode.isExpanded() && selectedNode.hasChildren()) {
                    // Collapse if expanded
                    selectedNode.setExpanded(false);
                    layoutDirty = true;
                    ensureLayout();
                    updateScrollBars();
                    render();
                } else {
                    // Select parent
                    selectParent();
                }
                event.consume();
            }
            case RIGHT -> {
                if (selectedNode != null && !selectedNode.isExpanded() && selectedNode.hasChildren()) {
                    // Expand if collapsed
                    selectedNode.setExpanded(true);
                    layoutDirty = true;
                    ensureLayout();
                    updateScrollBars();
                    render();
                } else if (selectedNode != null && selectedNode.isExpanded() && selectedNode.hasChildren()) {
                    // Select first child
                    selectFirstChild();
                }
                event.consume();
            }
            case ENTER -> {
                if (selectedNode != null) {
                    // If has children, toggle expand
                    if (selectedNode.hasChildren()) {
                        selectedNode.toggleExpanded();
                        layoutDirty = true;
                        ensureLayout();
                        updateScrollBars();
                        render();
                    } else {
                        // Start editing
                        startEditingElementName(selectedNode);
                    }
                }
                event.consume();
            }
            case HOME -> {
                selectFirstNode();
                event.consume();
            }
            case END -> {
                selectLastVisibleNode();
                event.consume();
            }
            default -> { /* Not a navigation key */ }
        }
    }

    /**
     * Select the previous sibling or parent's previous sibling.
     */
    private void selectPreviousSibling() {
        if (selectedNode == null) {
            selectFirstNode();
            return;
        }

        NestedGridNode parent = selectedNode.getParent();
        if (parent == null) return;

        java.util.List<NestedGridNode> siblings = parent.getChildren();
        int index = siblings.indexOf(selectedNode);

        if (index > 0) {
            // Select previous sibling (or last expanded child of previous)
            NestedGridNode prev = siblings.get(index - 1);
            while (prev.isExpanded() && prev.hasChildren()) {
                prev = prev.getChildren().get(prev.getChildren().size() - 1);
            }
            selectNode(prev);
            ensureNodeVisible(prev);
        } else {
            // No previous sibling, select parent
            if (parent != rootNode) {
                selectNode(parent);
                ensureNodeVisible(parent);
            }
        }
    }

    /**
     * Select the next sibling or first child.
     */
    private void selectNextSibling() {
        if (selectedNode == null) {
            selectFirstNode();
            return;
        }

        // If expanded and has children, select first child
        if (selectedNode.isExpanded() && selectedNode.hasChildren()) {
            NestedGridNode firstChild = selectedNode.getChildren().get(0);
            selectNode(firstChild);
            ensureNodeVisible(firstChild);
            return;
        }

        // Find next sibling or parent's next sibling
        NestedGridNode current = selectedNode;
        while (current != null && current != rootNode) {
            NestedGridNode parent = current.getParent();
            if (parent == null) break;

            java.util.List<NestedGridNode> siblings = parent.getChildren();
            int index = siblings.indexOf(current);

            if (index < siblings.size() - 1) {
                NestedGridNode next = siblings.get(index + 1);
                selectNode(next);
                ensureNodeVisible(next);
                return;
            }

            // Move up to parent to check its next sibling
            current = parent;
        }
    }

    /**
     * Select the parent node.
     */
    private void selectParent() {
        if (selectedNode == null) return;

        NestedGridNode parent = selectedNode.getParent();
        if (parent != null && parent != rootNode) {
            selectNode(parent);
            ensureNodeVisible(parent);
        }
    }

    /**
     * Select the first child of the current node.
     */
    private void selectFirstChild() {
        if (selectedNode == null || !selectedNode.isExpanded() || !selectedNode.hasChildren()) return;

        NestedGridNode firstChild = selectedNode.getChildren().get(0);
        selectNode(firstChild);
        ensureNodeVisible(firstChild);
    }

    /**
     * Select the first visible node.
     */
    private void selectFirstNode() {
        if (rootNode == null) return;

        if (rootNode.getChildren().isEmpty()) {
            selectNode(rootNode);
        } else {
            selectNode(rootNode.getChildren().get(0));
        }
        ensureNodeVisible(selectedNode);
    }

    /**
     * Select the last visible node.
     */
    private void selectLastVisibleNode() {
        if (rootNode == null) return;

        NestedGridNode last = findLastVisibleNode(rootNode);
        if (last != null) {
            selectNode(last);
            ensureNodeVisible(last);
        }
    }

    private NestedGridNode findLastVisibleNode(NestedGridNode node) {
        if (node.isExpanded() && node.hasChildren()) {
            return findLastVisibleNode(node.getChildren().get(node.getChildren().size() - 1));
        }
        return node;
    }

    private void handleMouseClick(MouseEvent event) {
        double mx = event.getX() + scrollOffsetX;
        double my = event.getY() + scrollOffsetY;

        if (rootNode == null) return;

        // Handle right-click for context menu
        if (event.getButton() == MouseButton.SECONDARY) {
            handleContextMenu(event, mx, my);
            return;
        }

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

        // Double-click for editing - check attribute/text FIRST before header
        if (event.getClickCount() == 2) {
            int attrIndex = hitNode.getAttributeIndexAt(mx, my);
            boolean textHit = hitNode.isTextContentHit(mx, my);

            if (attrIndex >= 0) {
                startEditingAttribute(hitNode, attrIndex);
                return;
            } else if (textHit) {
                startEditingTextContent(hitNode);
                return;
            } else if (hitNode.isHeaderHit(mx, my)) {
                // For leaf elements with inline text: edit text content instead of element name
                if (hitNode.isLeafWithText() && hitNode.hasTextContent()) {
                    startEditingTextContent(hitNode);
                } else {
                    // Double-click on header = edit element name
                    startEditingElementName(hitNode);
                }
                return;
            }
        }

        // Check for header click (expand/collapse) - single click only
        if (hitNode.isHeaderHit(mx, my)) {
            // Single click = expand/collapse
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

        // Single click for selection
        selectNode(hitNode);
    }

    private void handleContextMenu(MouseEvent event, double mx, double my) {
        // Check for table FIRST (tables have priority over regular nodes in grid view)
        RepeatingElementsTable hitTable = findTableAt(rootNode, mx, my);
        if (hitTable != null && hitTable.isExpanded()) {
            int rowIndex = hitTable.getRowIndexAt(my);
            int colIndex = hitTable.getColumnIndexAt(mx);

            // Check if we're in the data area (not header)
            if (rowIndex >= 0 && rowIndex < hitTable.getRows().size() && colIndex >= 0) {
                XmlElement element = hitTable.getRows().get(rowIndex).getElement();
                context.getSelectionModel().setSelectedNode(element);
                selectTable(hitTable);
                selectNode(null);

                // Pass table cell context to context menu
                String columnName = null;
                if (colIndex < hitTable.getColumns().size()) {
                    columnName = hitTable.getColumn(colIndex).getName();
                }

                contextMenu.show(canvas, event.getScreenX(), event.getScreenY(), element,
                        hitTable, rowIndex, columnName);
                return;
            }
        }

        // If no table cell was hit, check for regular node
        NestedGridNode hitNode = findNodeAt(rootNode, mx, my);
        if (hitNode != null) {
            selectNode(hitNode);
            selectTable(null);

            // Update selection in context
            context.getSelectionModel().setSelectedNode(hitNode.getModelNode());

            // Show context menu without table context
            contextMenu.show(canvas, event.getScreenX(), event.getScreenY(), hitNode.getModelNode());
        }
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

        // Check if click is on an expanded child grid (or any of its children)
        NestedGridNode clickedNode = findNodeInExpandedChildGrids(table, mx, my);
        if (clickedNode != null) {
            // Handle click on nested grid - same as regular node click
            handleNestedGridClick(clickedNode, mx, my, clickCount);
            return;
        }

        // Row/cell click
        int rowIndex = table.getRowIndexAtY(my);
        int colIndex = table.getColumnIndexAt(mx);

        if (rowIndex >= 0 && colIndex >= 0) {
            RepeatingElementsTable.TableColumn col = table.getColumn(colIndex);
            RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);

            // Check if clicked on a complex cell
            if (col != null && row.hasComplexChild(col.getName())) {
                // Toggle cell expansion
                table.toggleCellExpansion(rowIndex, col.getName());
                table.calculateHeight();
                layoutDirty = true;
                ensureLayout();
                updateScrollBars();
                render();
                return;
            }

            // Double-click for inline editing of simple cells
            if (clickCount == 2 && col != null && !row.hasComplexChild(col.getName())) {
                startEditingTableCell(table, rowIndex, col.getName());
                return;
            }

            // Normal row selection
            table.setSelectedRowIndex(rowIndex);
            selectTable(table);
            selectNode(null);
            render();
        } else if (rowIndex >= 0) {
            // Click in row but not on a specific column
            table.setSelectedRowIndex(rowIndex);
            selectTable(table);
            selectNode(null);
            render();
        }
    }

    /**
     * Find the deepest nested node at the given point within expanded child grids.
     * Searches recursively through all expanded rows and columns.
     */
    private NestedGridNode findNodeInExpandedChildGrids(RepeatingElementsTable table, double mx, double my) {
        for (RepeatingElementsTable.TableRow row : table.getRows()) {
            for (String colName : row.getExpandedColumns()) {
                NestedGridNode childGrid = row.getExpandedChildGrids().get(colName);
                if (childGrid != null) {
                    // Check if point is within this child grid or any of its descendants
                    NestedGridNode hit = findNodeAtPoint(childGrid, mx, my);
                    if (hit != null) {
                        return hit;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Recursively find the deepest node at a given point.
     */
    private NestedGridNode findNodeAtPoint(NestedGridNode node, double mx, double my) {
        if (!isPointInNode(node, mx, my)) {
            return null;
        }

        // Check tables in this node first
        for (RepeatingElementsTable table : node.getRepeatingTables()) {
            if (table.containsPoint(mx, my)) {
                // Check for nested child grids in this table
                NestedGridNode nested = findNodeInExpandedChildGrids(table, mx, my);
                if (nested != null) {
                    return nested;
                }
            }
        }

        // Check children recursively (find deepest match)
        if (node.isExpanded()) {
            for (NestedGridNode child : node.getChildren()) {
                NestedGridNode hit = findNodeAtPoint(child, mx, my);
                if (hit != null) {
                    return hit;
                }
            }
        }

        // This node is the best match
        return node;
    }

    private boolean isPointInNode(NestedGridNode node, double mx, double my) {
        double x = node.getX();
        double y = node.getY();
        double w = node.getWidth();
        double h = node.getHeight();
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /**
     * Handle click on a nested grid node - same behavior as main grid nodes.
     */
    private void handleNestedGridClick(NestedGridNode node, double mx, double my, int clickCount) {
        double localX = mx - node.getX();
        double localY = my - node.getY();

        // Check if click is on header (expand/collapse area)
        boolean headerClick = localY <= NestedGridNode.HEADER_HEIGHT;
        boolean expandButtonClick = headerClick && localX <= 20;  // First 20px for expand/collapse button

        if (expandButtonClick && node.hasExpandableContent()) {
            // Toggle expand/collapse
            node.setExpanded(!node.isExpanded());
            layoutDirty = true;
            ensureLayout();
            updateScrollBars();
            render();
        } else {
            // Header click or single click - select the node
            selectNode(node);
            selectTable(null);
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

            // Cursor - HAND for header and complex cells
            if (hitTable.isHeaderHit(mx, my)) {
                canvas.setCursor(javafx.scene.Cursor.HAND);
            } else if (newRowIndex >= 0 && newColIndex >= 0) {
                // Check if hovering over a complex cell
                RepeatingElementsTable.TableColumn col = hitTable.getColumn(newColIndex);
                RepeatingElementsTable.TableRow row = hitTable.getRows().get(newRowIndex);
                if (col != null && row.hasComplexChild(col.getName())) {
                    canvas.setCursor(javafx.scene.Cursor.HAND);
                } else {
                    canvas.setCursor(javafx.scene.Cursor.DEFAULT);
                }
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
            // Update selection model
            context.getSelectionModel().setSelectedNode(node.getModelNode());
        }

        // Update status bar
        updateStatusBar();

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

    /**
     * Renders a grid node and all its children recursively.
     * Used for rendering expanded child grids in table cells.
     * This doesn't do viewport culling - it renders everything.
     */
    private void renderGridRecursively(NestedGridNode node) {
        // First render this node
        renderGrid(node);

        // If expanded, render children
        if (node.isExpanded()) {
            // Render any repeating tables
            for (RepeatingElementsTable table : node.getRepeatingTables()) {
                renderTable(table);
            }

            // Render child nodes
            for (NestedGridNode child : node.getChildren()) {
                renderGridRecursively(child);
            }
        }
    }

    private void renderGrid(NestedGridNode node) {
        // If skipOwnHeader is true, don't render this node's header/content
        // Just let the children be rendered by renderGridRecursively
        if (node.isSkipOwnHeader()) {
            return;
        }

        double x = node.getX();
        double y = node.getY();
        double w = node.getWidth();
        double h = node.getHeight();

        // Compact rendering for leaf elements with only text (no attributes)
        boolean isCompactLeaf = node.isLeafWithText() && node.getAttributeCells().isEmpty();

        // Background based on depth
        Color bgColor = DEPTH_COLORS[node.getDepth() % DEPTH_COLORS.length];
        if (node == highlightedNode) {
            bgColor = HIGHLIGHT_COLOR;  // Yellow highlight for undo/redo feedback
        } else if (node.isSelected()) {
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

        // Children header - REMOVED: No longer displayed to save screen space
        // if (node.hasChildren()) {
        //     drawChildrenHeader(node, x, rowY, w);
        // }
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

        // Use calculated column widths for proper alignment
        double nameColWidth = node.getCalculatedNameColumnWidth();
        double valueColWidth = node.getCalculatedValueColumnWidth();

        // Attribute name (@name)
        gc.setFont(ROW_FONT);
        gc.setFill(TEXT_ATTRIBUTE_NAME);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("@" + cell.getName(), x + GRID_PADDING, y + ROW_HEIGHT / 2);

        // Attribute value - no truncation needed if column width is calculated correctly
        gc.setFill(TEXT_ATTRIBUTE_VALUE);
        gc.fillText(truncateText(cell.getValue(), valueColWidth - GRID_PADDING),
                    x + nameColWidth, y + ROW_HEIGHT / 2);
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

        // Use calculated column widths for proper alignment
        double nameColWidth = node.getCalculatedNameColumnWidth();
        double valueColWidth = node.getCalculatedValueColumnWidth();

        // Text label
        gc.setFont(ROW_FONT);
        gc.setFill(TEXT_SECONDARY);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("#text", x + GRID_PADDING, y + ROW_HEIGHT / 2);

        // Text content - no truncation needed if column width is calculated correctly
        gc.setFill(TEXT_CONTENT);
        gc.fillText(truncateText(node.getTextContent(), valueColWidth - GRID_PADDING),
                    x + nameColWidth, y + ROW_HEIGHT / 2);
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

        // Data rows - each row may have variable height due to expanded cells
        for (int i = 0; i < table.getRows().size(); i++) {
            RepeatingElementsTable.TableRow row = table.getRows().get(i);
            double rowHeight = calculateRowHeight(table, row);

            // Draw the row with variable height
            drawTableDataRowWithExpandedCells(table, row, i, x, rowY, w, rowHeight);

            rowY += rowHeight;
        }
    }

    /**
     * Calculate the height of a row, accounting for expanded child grids in cells.
     */
    private double calculateRowHeight(RepeatingElementsTable table, RepeatingElementsTable.TableRow row) {
        double maxCellHeight = ROW_HEIGHT;

        for (String colName : row.getExpandedColumns()) {
            NestedGridNode childGrid = row.getExpandedChildGrids().get(colName);
            if (childGrid != null) {
                double colW = table.getColumnWidth(colName);
                double childW = colW - 4;

                // Calculate size with column width constraint
                calculateSizesRecursively(childGrid, childW);

                // Cell height = text row + child grid + padding
                double cellHeight = ROW_HEIGHT + childGrid.getHeight() + 4;
                maxCellHeight = Math.max(maxCellHeight, cellHeight);
            }
        }

        return maxCellHeight;
    }

    /**
     * Draw a table data row with expanded child grids rendered inside their cells.
     */
    private void drawTableDataRowWithExpandedCells(RepeatingElementsTable table,
                                                    RepeatingElementsTable.TableRow row,
                                                    int rowIndex, double x, double y,
                                                    double w, double rowHeight) {
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
        gc.fillRect(x + 1, y, w - 2, rowHeight);

        // Bottom border
        gc.setStroke(ROW_SEPARATOR);
        gc.setLineWidth(0.5);
        gc.strokeLine(x + GRID_PADDING, y + rowHeight, x + w - GRID_PADDING, y + rowHeight);

        // Draw each cell
        double colX = x + GRID_PADDING;
        for (RepeatingElementsTable.TableColumn col : table.getColumns()) {
            String colName = col.getName();
            String value = row.getValue(colName);
            boolean isComplex = row.hasComplexChild(colName);
            boolean isExpanded = row.isColumnExpanded(colName);

            // Draw cell content
            drawTableCell(col, colX, y, value, isComplex, isExpanded);

            // Draw expanded child grid inside the cell (below the text)
            if (isExpanded) {
                NestedGridNode childGrid = row.getExpandedChildGrids().get(colName);
                if (childGrid != null) {
                    double childX = colX + 2;
                    double childY = y + ROW_HEIGHT;  // Below the text
                    double childW = col.getWidth() - 4;

                    // Size already calculated in calculateRowHeight
                    positionNodesRecursively(childGrid, childX, childY);
                    renderGridRecursively(childGrid);
                }
            }

            // Column separator (full height of the row)
            colX += col.getWidth();
            gc.setStroke(ROW_SEPARATOR);
            gc.setLineWidth(0.5);
            gc.strokeLine(colX, y, colX, y + rowHeight);
        }
    }

    /**
     * Draw a single table cell's text content.
     */
    private void drawTableCell(RepeatingElementsTable.TableColumn col, double colX, double y,
                               String value, boolean isComplex, boolean isExpanded) {
        double textStartX = colX;

        // Draw expand/collapse indicator for complex cells
        if (isComplex) {
            double iconX = colX + 2;
            double iconY = y + ROW_HEIGHT / 2;
            double iconSize = 3;

            gc.setStroke(Color.rgb(59, 130, 246));  // Blue
            gc.setLineWidth(1.5);

            if (isExpanded) {
                // Down arrow (expanded)
                gc.strokeLine(iconX, iconY - iconSize, iconX + iconSize, iconY);
                gc.strokeLine(iconX + iconSize, iconY, iconX + iconSize * 2, iconY - iconSize);
            } else {
                // Right arrow (collapsed)
                gc.strokeLine(iconX, iconY - iconSize, iconX + iconSize, iconY);
                gc.strokeLine(iconX, iconY + iconSize, iconX + iconSize, iconY);
            }

            textStartX = colX + iconSize * 2 + 6;
        }

        // Set text style
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);

        if (isComplex) {
            gc.setFill(Color.rgb(59, 130, 246)); // Blue
            gc.setFont(Font.font("System", FontWeight.NORMAL, 12));
        } else if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
            gc.setFill(TEXT_ATTRIBUTE_VALUE);
            gc.setFont(ROW_FONT);
        } else {
            gc.setFill(TEXT_CONTENT);
            gc.setFont(ROW_FONT);
        }

        // Truncate and draw text
        double availableWidth = col.getWidth() - (textStartX - colX) - GRID_PADDING;
        gc.fillText(truncateText(value, availableWidth), textStartX, y + ROW_HEIGHT / 2);
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
        editingElementName = false;

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
        editingElementName = false;

        String currentValue = node.getTextContent();

        double x, y, width;

        // For leaf elements with inline text, position in header after "elementName = "
        if (node.isLeafWithText()) {
            // Calculate position after element name and "=" sign
            String elementName = node.getElementName();
            double textOffset = GRID_PADDING + 20 + gc.getFont().getSize() * elementName.length() * 0.6 + 30; // Approximate width
            x = node.getX() + textOffset - scrollOffsetX;
            y = node.getY() + 2 - scrollOffsetY;
            width = node.getWidth() - textOffset - GRID_PADDING;
        } else {
            // Standard text row below attributes
            x = node.getX() + ATTR_NAME_WIDTH - scrollOffsetX;
            y = node.getY() + HEADER_HEIGHT + node.getAttributeCells().size() * ROW_HEIGHT + 2 - scrollOffsetY;
            width = node.getWidth() - ATTR_NAME_WIDTH - GRID_PADDING;
        }

        createEditField(currentValue, x, y, width);
    }

    private void startEditingElementName(NestedGridNode node) {
        cancelEditing();

        // Only elements can have their name edited
        XmlNode modelNode = node.getModelNode();
        if (!(modelNode instanceof XmlElement)) return;

        editingNode = node;
        editingAttributeIndex = -1;
        editingTextContent = false;
        editingElementName = true;

        String currentValue = node.getElementName();

        // Position the edit field in the header
        double x = node.getX() + GRID_PADDING + 40 - scrollOffsetX;  // After expand icon
        double y = node.getY() + 2 - scrollOffsetY;
        double width = node.getWidth() - 60;

        createEditField(currentValue, x, y, width);
    }

    private void startEditingTableCell(RepeatingElementsTable table, int rowIndex, String columnName) {
        cancelEditing();

        if (rowIndex < 0 || rowIndex >= table.getRows().size()) return;

        RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);
        RepeatingElementsTable.TableColumn col = null;
        for (RepeatingElementsTable.TableColumn c : table.getColumns()) {
            if (c.getName().equals(columnName)) {
                col = c;
                break;
            }
        }
        if (col == null) return;

        // Don't edit complex cells (they have children)
        if (row.hasComplexChild(columnName)) return;

        editingTable = table;
        editingTableRowIndex = rowIndex;
        editingTableColumnName = columnName;
        editingNode = null;  // Clear node editing state

        String currentValue = row.getValue(columnName);

        // Calculate cell position
        double cellX = table.getColumnX(columnName) - scrollOffsetX;
        double cellY = table.getRowY(rowIndex) + 2 - scrollOffsetY;
        double cellWidth = table.getColumnWidth(columnName) - 4;

        createEditField(currentValue, cellX, cellY, cellWidth);
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
        if (editField == null) {
            cancelEditing();
            return;
        }

        String newValue = editField.getText();

        // Handle table cell editing
        if (editingTable != null && editingTableRowIndex >= 0 && editingTableColumnName != null) {
            commitTableCellEditing(newValue);
            cancelEditing();
            rebuildTree();
            notifyDocumentModified();
            return;
        }

        // Handle node editing
        if (editingNode == null) {
            cancelEditing();
            return;
        }

        XmlNode modelNode = editingNode.getModelNode();

        if (editingElementName) {
            // Editing element name
            if (modelNode instanceof XmlElement && !newValue.trim().isEmpty()) {
                context.executeCommand(new RenameNodeCommand((XmlElement) modelNode, newValue.trim()));
            }
        } else if (editingAttributeIndex >= 0) {
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
        notifyDocumentModified();
    }

    private void commitTableCellEditing(String newValue) {
        RepeatingElementsTable.TableRow row = editingTable.getRows().get(editingTableRowIndex);
        XmlElement rowElement = row.getElement();

        // Find the column type to determine how to save
        RepeatingElementsTable.TableColumn col = null;
        for (RepeatingElementsTable.TableColumn c : editingTable.getColumns()) {
            if (c.getName().equals(editingTableColumnName)) {
                col = c;
                break;
            }
        }

        if (col == null) return;

        if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
            // Edit attribute value
            context.executeCommand(new SetAttributeCommand(rowElement, editingTableColumnName, newValue));
        } else if (col.getType() == RepeatingElementsTable.ColumnType.CHILD_ELEMENT) {
            // Edit child element text content
            for (XmlNode child : rowElement.getChildren()) {
                if (child instanceof XmlElement childElement) {
                    if (childElement.getName().equals(editingTableColumnName)) {
                        context.executeCommand(new SetElementTextCommand(childElement, newValue));
                        break;
                    }
                }
            }
        } else if (col.getType() == RepeatingElementsTable.ColumnType.TEXT_CONTENT) {
            // Edit direct text content
            context.executeCommand(new SetElementTextCommand(rowElement, newValue));
        }
    }

    private void cancelEditing() {
        if (editField != null) {
            canvasContainer.getChildren().remove(editField);
            editField = null;
        }
        editingNode = null;
        editingAttributeIndex = -1;
        editingTextContent = false;
        editingElementName = false;
        // Clear table editing state
        editingTable = null;
        editingTableRowIndex = -1;
        editingTableColumnName = null;
    }

    // ==================== Event Handlers ====================

    private void onDocumentChanged(PropertyChangeEvent evt) {
        cancelEditing();
        rebuildTree();
    }

    // ==================== Scroll-to-Selection ====================

    /**
     * Ensures the given node is visible in the viewport.
     * Smoothly scrolls if the node is outside the visible area.
     */
    private void ensureNodeVisible(NestedGridNode node) {
        if (node == null) return;

        double nodeTop = node.getY();
        double nodeBottom = nodeTop + node.getHeight();
        double viewTop = scrollOffsetY;
        double viewBottom = viewTop + canvas.getHeight();

        if (nodeTop < viewTop) {
            // Node is above viewport - scroll up
            animateScrollTo(nodeTop - 20);  // 20px buffer
        } else if (nodeBottom > viewBottom) {
            // Node is below viewport - scroll down
            animateScrollTo(nodeBottom - canvas.getHeight() + 20);
        }
    }

    /**
     * Smoothly animate scroll to target Y position.
     */
    private void animateScrollTo(double targetY) {
        targetY = Math.max(0, Math.min(targetY, totalHeight - canvas.getHeight()));

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(vScrollBar.valueProperty(), scrollOffsetY)),
            new KeyFrame(Duration.millis(200), new KeyValue(vScrollBar.valueProperty(), targetY))
        );
        timeline.play();
    }

    // ==================== Highlight ====================

    /**
     * Flash highlight a node (used after undo/redo to show what changed).
     */
    public void flashHighlight(NestedGridNode node) {
        if (node == null) return;

        highlightedNode = node;
        render();

        // Clear highlight after 1.5 seconds
        PauseTransition pause = new PauseTransition(Duration.millis(1500));
        pause.setOnFinished(e -> {
            highlightedNode = null;
            render();
        });
        pause.play();
    }

    /**
     * Flash highlight a node by its model node.
     */
    public void flashHighlightByModel(XmlNode modelNode) {
        if (rootNode != null && modelNode != null) {
            NestedGridNode found = rootNode.findByModel(modelNode);
            if (found != null) {
                ensureNodeVisible(found);
                flashHighlight(found);
            }
        }
    }

    // ==================== Toast Notifications ====================

    /**
     * Show a toast notification.
     */
    public void showToast(String message, ToastNotification.Type type) {
        if (toastContainer != null) {
            ToastNotification.show(toastContainer, message, type);
        } else {
            // Fallback: show in canvas container
            ToastNotification.show(canvasContainer, message, type);
        }
    }

    /**
     * Set the container for toast notifications.
     */
    public void setToastContainer(StackPane container) {
        this.toastContainer = container;
    }

    // ==================== Status Bar ====================

    /**
     * Set the status bar to update.
     */
    public void setStatusBar(XmlStatusBar statusBar) {
        this.statusBar = statusBar;
        if (statusBar != null) {
            statusBar.setOnBreadcrumbClick(this::navigateToNode);
        }
    }

    /**
     * Sets a callback that is called when the document is modified.
     * The callback receives the serialized XML string.
     *
     * @param callback the callback to invoke on document changes
     */
    public void setOnDocumentModified(java.util.function.Consumer<String> callback) {
        this.onDocumentModified = callback;
    }

    /**
     * Notifies the callback that the document was modified.
     */
    private void notifyDocumentModified() {
        if (onDocumentModified != null) {
            String xml = context.serializeToString();
            onDocumentModified.accept(xml);
        }
    }

    /**
     * Navigate to a node (used by breadcrumb clicks).
     */
    private void navigateToNode(XmlNode node) {
        if (rootNode != null && node != null) {
            NestedGridNode found = rootNode.findByModel(node);
            if (found != null) {
                selectNode(found);
                ensureNodeVisible(found);
            }
        }
    }

    /**
     * Update the status bar with current selection info.
     */
    private void updateStatusBar() {
        if (statusBar == null) return;

        // Update breadcrumb
        XmlNode selected = getSelectedNode();
        statusBar.updateBreadcrumb(selected);

        // Update element count
        int count = countElements(rootNode);
        statusBar.updateElementCount(count);

        // Update dirty flag
        statusBar.setDirty(context.isDirty());
    }

    private int countElements(NestedGridNode node) {
        if (node == null) return 0;
        int count = 1;  // This node
        for (NestedGridNode child : node.getChildren()) {
            count += countElements(child);
        }
        for (RepeatingElementsTable table : node.getRepeatingTables()) {
            count += table.getElementCount();
        }
        return count;
    }

    // ==================== Public API ====================

    public void refresh() {
        rebuildTree();
        notifyDocumentModified();
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
